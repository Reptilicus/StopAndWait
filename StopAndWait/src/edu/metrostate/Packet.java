package edu.metrostate;

import java.io.Serializable;
import java.nio.ByteBuffer;

public class Packet implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1872859865829053617L;
	private final static int MAX_PACKET_LENGTH = 512;
	private short cksum;
	private short len;
	private int ackno;
	private int seqno;
	private byte[] data;
	private byte[] packet;
	
	/*
	 * Creates a data packet, calculates cksum and len, and creates packet
	 */
	public Packet(byte[] pData, int pSeqno) {
		this.data = pData;
		this.seqno = pSeqno;
		this.ackno = pSeqno;
		this.cksum = 0;
		this.len = (short)(data.length + 12);
		
		craftPacket();
	}
	
	/*
	 * Creates an ACK packet
	 */
	public Packet(int pAckno) {
		this.ackno = pAckno;
		this.cksum = 0;
		this.len = (short)(8);
		
		craftPacket();
		
	}
	
	public Packet(byte[] pPacket) {
		this.packet = pPacket;
		splitPacket();
	}
	
	
	public short getCksum() {
		return cksum;
	}

	public int getAckno() {
		return ackno;
	}
	
	public short getLen() {
		return len;
	}

	public void setAckno(int ackno) {
		this.ackno = ackno;
	}

	public int getSeqno() {
		return seqno;
	}

	public void setSeqno(int seqno) {
		this.seqno = seqno;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	/*
	 * Returns the packet as a byte array
	 */
	public byte[] toByteArray() {
		return packet;
	}
	
	private void craftPacket() {
		packet = new byte[MAX_PACKET_LENGTH + 1];
	    packet[0] = (byte) (cksum & 0xff);
	    packet[1] = (byte) ((cksum >> 8) & 0xff);
	    packet[2] = (byte) (len & 0xff);
	    packet[3] = (byte) ((len >>> 8) & 0xff);
	    packet[4] = (byte) ((ackno & 0xFF000000) >> 24);
	    packet[5] = (byte) ((ackno & 0x00FF0000) >> 16);
	    packet[6] = (byte) ((ackno & 0x0000FF00) >> 8);
	    packet[7] = (byte) ((ackno & 0x000000FF) >> 0);
	    
	    if (data.length > 0) {
		    packet[8] = (byte) ((seqno & 0xFF000000) >> 24);
		    packet[9] = (byte) ((seqno & 0x00FF0000) >> 16);
		    packet[10] = (byte) ((seqno & 0x0000FF00) >> 8);
		    packet[11] = (byte) ((seqno & 0x000000FF) >> 0);
		    
			for (int i = 0; i < data.length; i++) {
				packet[i + 12] = data[i];
			}
	    }
	}
	
	private void splitPacket() {
		data = new byte[MAX_PACKET_LENGTH - 11];
		
		
		byte[] bCksum = new byte[2];
    	for (int i = 0; i < 2; i++) {
    		bCksum[i] = packet[i];
    	}
    	cksum = (short) ((bCksum[1] & 0xFF) << 8 | (bCksum[0] & 0xFF));
    	
		byte[] bLen = new byte[2];
    	for (int i = 0; i < 2; i++) {
    		bLen[i] = packet[i + 2];
    	}
    	len = ByteBuffer.wrap(bLen).getShort();
    	
    	byte[] bAckno = new byte[4];
    	for (int i = 0; i < 4; i++) {
    		bAckno[i] = packet[i + 4];
    	}
    	ackno = ByteBuffer.wrap(bAckno).getInt();
	    
	    if (packet.length > 8) {
	    	
	    	byte[] bSeqno = new byte[4];
	    	for (int i = 0; i < 4; i++) {
	    		bSeqno[i] = packet[i + 8];
	    	}
	    	seqno = ByteBuffer.wrap(bSeqno).getInt();
		    
			for (int i = 0; i + 12 < packet.length; i++) {
				 data[i] = packet[i + 12];
			}
	    }
	}
	
	public void error() {
		cksum = 1;
	    packet[0] = (byte) (cksum & 0xff);
	    packet[1] = (byte) ((cksum >>> 8) & 0xff);
	}
	

}
