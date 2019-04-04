package edu.metrostate;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SketchyServerSocket {
	private final DatagramSocket serverSocket;
	private final int DropPerc;
	private final double ERROR_RATE = 0.5;
	
	public SketchyServerSocket(DatagramSocket socket, int DropPercentage) {
		serverSocket = socket;
		DropPerc = DropPercentage;
	}
	
	/*
	 * Returns 0 if send was successful, 1 if packet was dropped, and 2 if packet was corrupted
	 */
	public int send(Packet packet, InetAddress address, int port) throws IOException {
		if (fault()) {
			if (error()) {
				packet.error();
				sendAsDatagramPacket(packet, address, port);
				packet.fixError();
				return 2;
			} else {
				return 1;
			}
		} else {
			sendAsDatagramPacket(packet, address, port);
			return 0;
		}
	}
	
	private boolean fault() {
		return Math.random() * 100 < DropPerc;
	}
	
	private boolean error() {
		return Math.random() < ERROR_RATE;
	}
	
	private void sendAsDatagramPacket(Packet packet, InetAddress address, int port) throws IOException {
		DatagramPacket dPacket = new DatagramPacket(packet.toByteArray(), (int)packet.getLen(), address, port);
		serverSocket.send(dPacket);
	}
	
	public DatagramSocket getSocket() {
		return serverSocket;
	}
}
