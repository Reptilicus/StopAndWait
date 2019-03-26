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
	private final int MAX_ACK_SIZE = 9;						//ACK packet shouldn't be bigger than 8 bytes
	private final int PACKET_SIZE;
	private final int PORT;
	private final File FILE;
	private final int DROP_RATE;
	private final int TIMEOUT;
	private final boolean HAS_DROP_RATE;
	private final boolean HAS_TIMEOUT;
	private final boolean HAS_PACKET_SIZE;
	private final String RECEIVER_ADDRESS;
	private Packet[] packetArray;
	
	private final static String USAGE = 
			"edu.metrostate.Sender [OPTION]... [FILE] [RECEIVER_IP_ADDRESS] [RECEIVER_PORT]";
	private final static String HEADER = 
			"\nSender breaks a file into packets and sends via UDP to a receiver.\n\n";
	private final static String FOOTER = 
			"\nUnsupported - Use at your own risk.";
	
	public Sender(CommandLine line) {
		HAS_DROP_RATE = line.hasOption("d");
		HAS_TIMEOUT = line.hasOption("t");
		HAS_PACKET_SIZE = line.hasOption("s");
		
		if (HAS_PACKET_SIZE) {
			PACKET_SIZE = Integer.parseInt(line.getOptionValue("s"));
		} else {
			PACKET_SIZE = DEFAULT_PACKET_SIZE;
		}
		
		if (HAS_TIMEOUT) {
			TIMEOUT = Integer.parseInt(line.getOptionValue("t"));
		} else {
			TIMEOUT = 2000;
		}
		
		if (HAS_DROP_RATE) {
			DROP_RATE = Integer.parseInt(line.getOptionValue("d"));
		} else {
			DROP_RATE = 0;
		}

		String[] reqArgs = line.getArgs();
		FILE = new File(reqArgs[0]);
		RECEIVER_ADDRESS = reqArgs[1];
		PORT = Integer.parseInt(reqArgs[2]);
		
		calculateNumPackets();
		createPacketArray();
	}
	
	/**
	 * Runs as a command line argument to send a file via UDP given a file path and an address to send to.
	 * @param args
	 */
	public static void main(String args[]) {
		
    	final CommandLineParser parser = new DefaultParser();
    	
    	final Options options = new Options();
    	options.addOption("t", "timeout", true, "the timeout interval");
    	options.addOption("s", "size", true, "the size of the packet");
    	options.addOption("d", "drop", true, "the percentage of datagrams to corrupt, delay, or drop");
    	options.addOption("h", "help", false, "shows this help");
    	
    	try {
    		final CommandLine line = parser.parse(options, args);
    		final String[] reqArgs = line.getArgs();
    		
    		if (line.hasOption("help") || reqArgs.length < 3) {
    			showHelpAndExit(options);
    		}
    		
    		try {
    			Sender sender = new Sender(line);
    			sender.send();
    			
    		} /*catch (final FileNotFoundException e) {
                printError(e.getMessage());
            } catch (final IOException e) {
                printError(e.getMessage());
            }*/ catch (Exception e) {
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
		DatagramPacket senderPacket;
		
		try {
	        
			DatagramSocket socket = new DatagramSocket();
			socket.setSoTimeout(TIMEOUT);
			//Get the file and destination address from the command line argument
			InetAddress destAddress = InetAddress.getByName(RECEIVER_ADDRESS);
			
			//Create a packet to send at the end to show that all packets have been sent (and hopefully received)
			final DatagramPacket CLOSE = new DatagramPacket(new byte[0], 0, destAddress, PORT);
			
			
			//Until there are no more bytes to be read, read in file data and send it as a packet
			System.out.println("Input: ");
			System.out.println("cksum: " + packetArray[0].getCksum());
			System.out.println("len: " + packetArray[0].getLen());
			System.out.println("ackno: " + packetArray[0].getAckno());
			System.out.println("seqno: " + packetArray[0].getSeqno());
			
			Packet packet = new Packet(packetArray[0].getData());
			System.out.println("Output");
			System.out.println("cksum: " + packet.getCksum());
			System.out.println("len: " + packet.getLen());
			System.out.println("ackno: " + packet.getAckno());
			System.out.println("seqno: " + packet.getSeqno());
			
			

 			for (int i = 0; i < packetArray.length &&  packetArray[i] != null;) {
				if (isDropped()) {
					if (Math.random() > 0.5) {	
						packetArray[i].error();
					}
				}
				senderPacket = new DatagramPacket(packetArray[i].toByteArray(), packetArray[i].getLen(), destAddress, PORT);
				System.out.println("Sending out good packet of length: " + packetArray[i].getLen());
				socket.send(senderPacket);

				if (ackReceived(socket)) {
					i++;
				}

			}
			
			//Send CLOSE
			socket.send(CLOSE);
			socket.close();
			
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
			
			//Until there are no more bytes to be read, read in file data and send it as a packet
			do {
				packetArray[i] = new Packet(data, i);
				byteBuff.clear();
				bytesRead = bis.read(data);
				i++;
			} while (bytesRead != -1);
			
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
		int numPackets = (int)FILE.length() / (PACKET_SIZE - 12) + 1;
		packetArray = new Packet[numPackets];
	}
	
	private boolean isDropped() {
		return Math.random() < DROP_RATE;
	}
	
	private boolean ackReceived(DatagramSocket socket) {
		try {
	        byte[] receiveData = new byte[MAX_ACK_SIZE];
			DatagramPacket ack = new DatagramPacket(receiveData, receiveData.length);
			socket.receive(ack);
		} catch (SocketTimeoutException e) {
			return false;
		} catch (IOException io) {
			printError(io.getMessage());
			io.printStackTrace();
		}
		return true;
	}
	
	
}
