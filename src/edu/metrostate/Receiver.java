package edu.metrostate;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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

	private static int PACKET_SIZE = 512;
	private final int PORT;
	private final String FILE_PATH;
	private final int DROP_RATE;
	private final boolean HAS_DROP_RATE;
	
	private final static String USAGE = 
			"edu.metrostate.Receiver [OPTION]... [FILE] [RECEIVER_IP_ADDRESS] [RECEIVER_PORT]";
	private final static String HEADER = 
			"\nReceiver receives packets and assembles them into a file.\n\n";
	private final static String FOOTER = 
			"\nUnsupported - Use at your own risk.";
	
	public Receiver(CommandLine line) {
		HAS_DROP_RATE = line.hasOption("d");
		
		if (HAS_DROP_RATE) {
			DROP_RATE = Integer.parseInt(line.getOptionValue("d"));
		} else {
			DROP_RATE = 0;
		}

		String[] reqArgs = line.getArgs();
		FILE_PATH = reqArgs[0];
		PORT = Integer.parseInt(reqArgs[2]);

	}
	/*
	 * Receives a file over UDP and stores it in a given destination file path
	 * 
	 * @param args	the command line arguments
	 */
    public static void main(final String args[]) throws Exception {
    	
    	final CommandLineParser parser = new DefaultParser();
    	
    	final Options options = new Options();
    	options.addOption("d", "drop", true, "the percentage of datagrams to corrupt, delay, or drop");
    	options.addOption("h", "help", false, "shows this help");
    	
    	try {
    		final CommandLine line = parser.parse(options, args);
    		final String[] reqArgs = line.getArgs();
    		
    		if (line.hasOption("help") || reqArgs.length < 3) {
    			showHelpAndExit(options);
    		}
    		
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
    	
    	
    	
	private void receive() throws Exception {
		
		//Create a DatagramSocket to listen on port and a byte array to hold received packets
        byte[] receiveData = new byte[PACKET_SIZE];
		DatagramSocket serverSocket = new DatagramSocket(PORT);

        try {
        	
            //Create a new file object from the given file path, if it doesn't exist, create it.
            File file = new File(FILE_PATH);
            if (!file.exists()) {
                file.createNewFile();
            }
            
        	//Create a FileOutputStream to write received packets to the destination file
            FileOutputStream out = new FileOutputStream(file);
            DatagramPacket receivePacket;
            System.out.println("Listening.");
            int fileSize = 0;
            int i = 0;
            int counter = 0;
            
            //While the CLOSE packet has not been sent, receive the packet and write it to the file.
            do {
                receivePacket = new DatagramPacket(receiveData, receiveData.length);
                serverSocket.receive(receivePacket);
                System.out.println("PACKET " + i++ + " LENGTH: " + receivePacket.getLength() + " OFFSET: " + (counter += receivePacket.getLength()));
                Packet receive = new Packet(receivePacket.getData());
                out.write(receive.getData(), 0, receive.getData().length);

                System.out.println("Len: " + receive.getLen());
                System.out.println("Ackno: " + receive.getAckno());
                System.out.println("SeqNo: " + receive.getSeqno());
                fileSize += receivePacket.getLength();
                out.flush();
            } while (receivePacket.getLength() != 0);
            
            System.out.println("File Size: " + fileSize / 1000 + " KB\nSaved to " + file.getPath());
            out.close();
            
        } catch (IOException e) {
            e.printStackTrace();
        }

        serverSocket.close();

    }
    
	private static final void showHelpAndExit(final Options pOptions) {
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
	
	public int getDROP_RATE() {
		return DROP_RATE;
	}
}