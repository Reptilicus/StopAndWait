package edu.metrostate;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/*
 * Acts as a receiver for a file sent over UDP
 * @author Dan
 * @author James
 * @author Gabriel
 * 
 * @see <a href="https://github.com/michaelknigge/pcldumper/blob/master/pcldumper/src/main/java/de/textmode/pcldumper/Main.java">CLIReference</a>
 */

public class Receiver {

	private static int MAX_PACKET_SIZE = 512;
	private final int PORT;
	private final String FILE_PATH;
	private final int DROP_PERCENTAGE;
	private final boolean HAS_DROP_RATE;
	private int curAckno;
	private Packet receivedPacket;
	private SketchyServerSocket sketchySocket;
	private boolean error = false;
	
	private final static String USAGE = 
			"edu.metrostate.Receiver [OPTION]... [FILE] [RECEIVER_IP_ADDRESS] [RECEIVER_PORT]";
	private final static String HEADER = 
			"\nReceiver receives packets and assembles them into a file.\n\n";
	private final static String FOOTER = 
			"\nUnsupported - Use at your own risk.";
	
	public Receiver(CommandLine line) {
		String settings = "";
		
		HAS_DROP_RATE = line.hasOption("d");
		
		if (HAS_DROP_RATE) {
			DROP_PERCENTAGE = Integer.parseInt(line.getOptionValue("d"));

		} else {
			DROP_PERCENTAGE = 0;
		}
		settings = settings.concat("\nDrop Rate: " + DROP_PERCENTAGE + "%");
		
		String[] reqArgs = line.getArgs();
		FILE_PATH = reqArgs[0];
		settings = settings.concat("\nFile Path: " + FILE_PATH);
		
		PORT = Integer.parseInt(reqArgs[2]);
		settings = settings.concat("\nPort: " + PORT + "\n");
		
		System.out.println(settings);
	}
	
	/*
	 * Receives a file over UDP and stores it in a given destination file path
	 * 
	 * @param args	the command line arguments
	 */
    public static void main(final String args[]) throws Exception {
    	
    	final CommandLineParser parser = new DefaultParser();
    	
    	final Options options = new Options();
    	options.addOption("d", "drop", true, "the percentage (0-100) of datagrams to corrupt, delay, or drop");
    	options.addOption("h", "help", false, "shows this help");
    	
    	//Set up commandline
    	try {
    		final CommandLine line = parser.parse(options, args);
    		final String[] reqArgs = line.getArgs();
    		
    		if (line.hasOption("help") || reqArgs.length < 3) {
    			showHelpAndExit(options);
    		}
    		
    		//Create a new receiver and receive
    		try {
    			Receiver receiver = new Receiver(line);
    			receiver.receive();
    		} catch (final FileNotFoundException e) {
                printError(e.getMessage());
            } catch (final IOException e) {
                printError(e.getMessage());
            }
    	} catch (final ParseException e) {
            System.err.println(e.getMessage());
            System.err.println();
            showHelpAndExit(options);
    	}

    }
    	
	
	/*
	 * 
	 */
	private void receive() throws Exception {
		
		//Create a DatagramSocket to listen on port and a byte array to hold received packets
        byte[] buffer = new byte[MAX_PACKET_SIZE];
		DatagramSocket serverSocket = new DatagramSocket(PORT);
		sketchySocket = new SketchyServerSocket(serverSocket, DROP_PERCENTAGE);
		Packet ack = new Packet(0);
        int fileSize = 0;
        File file = null;
        
        try {
        	
            //Create a new file object from the given file path, if it doesn't exist, create it.
            file = new File(FILE_PATH);
            if (!file.exists()) {
                file.createNewFile();
            }
            
        	//Create a FileOutputStream to write received packets to the destination file
            FileOutputStream out = new FileOutputStream(file);
            DatagramPacket received;

            boolean firstTime = true;
            
            //While the CLOSE packet has not been sent, receive the packet and write it to the file.
            do {
            	//Set up receive packets
                received = new DatagramPacket(buffer, buffer.length);
                if (firstTime) {
                	System.out.println("Listening.");
                	System.out.println("Waiting for packet 0");
                    serverSocket.receive(received);
                    buffer = new byte[received.getLength()];
                	firstTime = false;
                } else {
                	System.out.println("Waiting for packet " + curAckno);
                	serverSocket.receive(received);
                }
                
                receivedPacket = new Packet(received.getData(), (short) received.getLength());

                //Check if the received packet has a cksum of 0 (valid packet) and, if so, send an ack packet back and write
                if (received.getLength() != 0) {
                	if (!receivedPacket.isValidPacket()) {
                		printReceivedStatus("RECV","CRPT");
                		error = true;
                	} else if (curAckno != receivedPacket.getSeqno()) {
                		printReceivedStatus("DUPL","!Seq");
                		error = true;
                		curAckno = receivedPacket.getAckno();
	                    ack.setAckno(curAckno);
	                    sendAck(ack, received.getAddress(), received.getPort());
	                    curAckno++;
                	} else { 
	                	//Set the ack packet number to the received packets ack number
	                    ack.setAckno(receivedPacket.getAckno());
	                    
	                	//Send the ackPacket
	                    sendAck(ack, received.getAddress(), received.getPort());
	
	                    //Write the data from the packet to the file
	                    out.write(receivedPacket.getData(), 0, receivedPacket.getData().length);
	                    out.flush();
	
	                    fileSize += received.getLength();
	                    
	                    curAckno++;
                	} 
                }

            } while (received.getLength() != 0);
            System.out.println("Received CLOSE Packet.");

            //Close file output buffer
            System.out.println("Closing file stream...");
            out.close();
            System.out.println("File stream closed.");
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Closing socket...");
        serverSocket.close();
        System.out.println("Socket Closed.");



        System.out.println("File Size: " + fileSize / 1000 + " KB\nSaved to " + file.getPath());
    }
    
	/*
	 * Shows the help info and exits the program
	 */
	private static final void showHelpAndExit(final Options pOptions) {
		final HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(USAGE, HEADER, pOptions, FOOTER);
		System.exit(1);
	}
	
	/*
	 * Makes errors look pretty
	 */
	private static final void printError(final String error) {
        System.err.println();
        System.err.println("**********************************************************************");
        System.err.println();
        System.err.println(error);
        System.err.println();
        System.err.println("**********************************************************************");
        System.err.println();
	}	
	
	private void printAckStatus(String status1, String status2) {
		System.out.println(status1 + " " + curAckno + " " + System.currentTimeMillis() + " " + status2);
	}
	
	private void printReceivedStatus(String status1, String status2) {
		System.out.println(status1 + " " + System.currentTimeMillis() + " " + receivedPacket.getSeqno() + " " + status2);
	}
	
	private void sendAck(Packet packet, InetAddress address, int port) {
			int result;
			try {
				result = sketchySocket.send(packet, address, port);
	
				if (result == 1) {
					if (error) {
						printAckStatus("ReSend.", "DROP");
					} else {
						printAckStatus("SENDing", "DROP");
					}
					} else if (result == 2) {
					if (error) {
						printAckStatus("ReSend.", "ERR");
					} else {
						printAckStatus("SENDing", "ERR");
					}
					} else {
					if (error) {
						printAckStatus("ReSend.", "SENT");
						error = false;
					} else {
						printAckStatus("SENDing", "SENT");
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
}