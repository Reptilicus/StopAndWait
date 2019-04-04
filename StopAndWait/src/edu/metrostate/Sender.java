package edu.metrostate;

import java.io.*;


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * A program to act as a UDP sender
 * 
 * @author Gabriel
 * @author Dan
 * @author James
 * 
 * @see <a href="https://github.com/michaelknigge/pcldumper/blob/master/pcldumper/src/main/java/de/textmode/pcldumper/Main.java">CLIReference</a>
 * 
 */
public class Sender {
	
	private final static int DEFAULT_PACKET_SIZE = 512;
	private final int MAX_ACK_SIZE = 8;						//ACK packet shouldn't be bigger than 8 bytes
	private final int PACKET_SIZE;
	private final int PORT;
	private final File FILE;
	private final int DROP_PERCENTAGE;
	private final int TIMEOUT;
	private final boolean HAS_DROP_RATE;
	private final boolean HAS_TIMEOUT;
	private final boolean HAS_PACKET_SIZE;
	private final String RECEIVER_ADDRESS;
	private Packet[] packetArray;
	private int curSeqno = 0;
	private boolean error = false;
	private final InetAddress destAddress;
	private final DatagramSocket socket;
    private final SketchyServerSocket sketchySocket;
	
	private final static String USAGE = 
			"edu.metrostate.Sender [OPTION]... [FILE] [RECEIVER_IP_ADDRESS] [RECEIVER_PORT]";
	private final static String HEADER = 
			"\nSender breaks a file into packets and sends via UDP to a receiver.\n\n";
	private final static String FOOTER = 
			"\nUnsupported - Use at your own risk.";
	
	public Sender(CommandLine line) throws Exception {
		String settings = "";
		
		HAS_DROP_RATE = line.hasOption("d");
		HAS_TIMEOUT = line.hasOption("t");
		HAS_PACKET_SIZE = line.hasOption("s");
		
		if (HAS_PACKET_SIZE) {
			PACKET_SIZE = Integer.parseInt(line.getOptionValue("s"));
		} else {
			PACKET_SIZE = DEFAULT_PACKET_SIZE;
		}
		settings = settings.concat("\nPacket Size: " + PACKET_SIZE);
		
		if (HAS_TIMEOUT) {
			TIMEOUT = Integer.parseInt(line.getOptionValue("t"));
		} else {
			TIMEOUT = 2000;
		}
		settings = settings.concat("\nTimeout: " + TIMEOUT);
		
		if (HAS_DROP_RATE) {
			DROP_PERCENTAGE = Integer.parseInt(line.getOptionValue("d"));
		} else {
			DROP_PERCENTAGE = 0;
		}
		settings = settings.concat("\nDrop Rate: " + DROP_PERCENTAGE + "%");

		String[] reqArgs = line.getArgs();
		
		FILE = new File(reqArgs[0]);
		settings = settings.concat("\nFile: " + FILE);

		RECEIVER_ADDRESS = reqArgs[1];
		settings = settings.concat("\nReceiver Address: " + RECEIVER_ADDRESS);

		PORT = Integer.parseInt(reqArgs[2]);
		settings = settings.concat("\nPort: " + PORT + "\n");
		
		System.out.println(settings);
		
		destAddress = InetAddress.getByName(RECEIVER_ADDRESS);
		socket = new DatagramSocket();
        sketchySocket = new SketchyServerSocket(socket, DROP_PERCENTAGE);
		socket.setSoTimeout(TIMEOUT);
		
		calculateNumPackets();
		createPacketArray();
	}
	
	/**
	 * Runs as a command line argument to send a file via UDP given a file path and an address/port to send to.
	 * @param args
	 */
	public static void main(String args[]) {
		
    	final CommandLineParser parser = new DefaultParser();
    	
    	final Options options = new Options();
    	options.addOption("t", "timeout", true, "the timeout interval");
    	options.addOption("s", "size", true, "the size of the packet up to 512 bytes");
    	options.addOption("d", "drop", true, "the percentage (0-100) of datagrams to corrupt, delay, or drop");
    	options.addOption("h", "help", false, "shows this help");
    	
    	try {
    		final CommandLine line = parser.parse(options, args);
    		final String[] reqArgs = line.getArgs();
    		
    		if (line.hasOption("help") || reqArgs.length < 3) {
    			showHelpAndExit(options);
    		}
    		
    		try {
    			System.out.println("Creating new Sender...");
    			Sender sender = new Sender(line);
    			System.out.println("Sender creation successful");
    			System.out.println("Begin sending...");
    			sender.send();
    			
    		} catch (Exception e) {
				printError(e.getMessage());
				e.printStackTrace();
			}
    	} catch (final ParseException e) {
            System.err.println(e.getMessage());
            System.err.println();
            showHelpAndExit(options);
    	}
    	
	}
		
	private final void send() {
		try {

			
			//Create a packet to send at the end to show that all packets have been sent (and hopefully received)
			final DatagramPacket CLOSE = new DatagramPacket(new byte[0], 0, destAddress, PORT);

 			while (curSeqno < packetArray.length && packetArray[curSeqno] != null) {
 				
 				sendPacket(packetArray[curSeqno], sketchySocket);
 				
			}
			
			//Send CLOSE
 			System.out.println("Sending CLOSE packet...");
			socket.send(CLOSE);
			
 			System.out.println("Closing connection...");
			socket.close();
 			System.out.println("Connection closed.");
			
		} catch (IOException ex) {
			ex.printStackTrace();
		} 
	}
	
	private static final void showHelpAndExit(Options pOptions) {
		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(USAGE, HEADER, pOptions, FOOTER);
		System.exit(1);
	}
	
	private static final void printError(final String error) {
        System.err.println();
        System.err.println("**********************************************************************");
        System.err.println();
        System.err.println(error);
        System.err.println();
        System.err.println("**********************************************************************");
        System.err.println();
	}
	
	/*
	 * Breaks up the bytes from the file into packets and pushes them into the packet array
	 */
	private void createPacketArray() {
		int i = 0;
		try {
			byte[] data = new byte[PACKET_SIZE - 12];
			ByteBuffer byteBuff = ByteBuffer.wrap(data);
			
			
			//Create a BufferedInputStream around a FileInputStream to pull the bytes from the file
			FileInputStream fis = new FileInputStream(FILE);
			BufferedInputStream bis = new BufferedInputStream(fis);
			
			//Read in the first data.length bytes
			int bytesRead = bis.read(data);
			
			//Until there are no more bytes to be read, read in file data, break it up and pack it into packets
			while (bytesRead != -1) {
				if (bytesRead < PACKET_SIZE - 12) {
					byte[] part = new byte[bytesRead];
					
					for (int j = 0; j < part.length; j++) {
						part[j] = data[j];
					}
					
					packetArray[i] = new Packet(part, i);
				} else {
				packetArray[i] = new Packet(data, i);
				}
				
				byteBuff.clear();
				bytesRead = bis.read(data);
				i++;
			}
			
			bis.close();
		} catch (Exception e) {	
			printError(e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	/*
	 * Calculates the number of packets needed to send the file and initializes the packet array
	 */
	private void calculateNumPackets() {
		int numPackets = (int)Math.ceil(FILE.length() / (double)(PACKET_SIZE - 12));
		packetArray = new Packet[numPackets];
	}
	
	private boolean ackReceived(DatagramSocket socket) {
		try {
	        byte[] receiveData = new byte[MAX_ACK_SIZE];
			DatagramPacket ack = new DatagramPacket(receiveData, receiveData.length);
			System.out.println("Waiting for ACK...");
			socket.receive(ack);
			Packet ackPacket = new Packet(ack.getData(), (short) ack.getLength());
			
			if (!ackPacket.isValidPacket()) {
				printAckStatus(ackPacket.getAckno(), "ErrAck");
				error = true;
				return false;
			} else if (curSeqno != ackPacket.getAckno()) {
				printAckStatus(ackPacket.getAckno(), "DuplAck");
				error = true;
				return false;
			} else {
				printAckStatus(ackPacket.getAckno(), "MoveWnd");
				return true;
			}
			
		} catch (SocketTimeoutException e) {
			System.out.println("TIMEOUT " + curSeqno);
			error = true;
			return false;
			
		} catch (IOException io) {
			printError(io.getMessage());
			io.printStackTrace();
		}
		return true;
	}
	
	private void printPacketStatus(String status1, String status2) {
		System.out.println(status1 + " " + curSeqno + " " + curSeqno * PACKET_SIZE + 
				":" + ((curSeqno * PACKET_SIZE) + packetArray[curSeqno].getData().length) + " " +
				System.currentTimeMillis() + " " + status2);
	}
	
	private void printAckStatus(int ackno, String status) {
		System.out.println("AckRcvd " + ackno + " " + status);
	}
	
	private void sendPacket(Packet packet, SketchyServerSocket sketchySocket) throws IOException {
		int result = sketchySocket.send(packetArray[curSeqno], destAddress, PORT);
		
		if (result == 1) {
			if (error) {
				printPacketStatus("ReSend.", "DROP");
				error = false;
			} else {
				printPacketStatus("SENDing", "DROP");
			}
		} else if (result == 2) {
			if (error) {
				printPacketStatus("ReSend.", "ERR");
				error = false;
			} else {
				printPacketStatus("SENDing", "ERR");
			}
		} else {
			if (error) {
				printPacketStatus("ReSend.", "SENT");
				error = false;
			} else {
				printPacketStatus("SENDing", "SENT");
			}
		
			if (ackReceived(sketchySocket.getSocket())) {
				curSeqno++;
			} 
		}
	}
	
	
}
