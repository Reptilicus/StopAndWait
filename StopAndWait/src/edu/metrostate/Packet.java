package edu.metrostate;

import java.nio.ByteBuffer;

public class Packet {
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
	/*
	 * Takes a byte array and unpacks it into a packet object
	 */
	public Packet(byte[] pPacket, short length) {
		this.packet = pPacket;
		this.len = length;
		unpackPacket();
	}

	/*
	 * Returns the packet as a byte array
	 */
	public byte[] toByteArray() {
		return packet;
	}
	
	/*
	 * Packs the packet
	 */
	private void craftPacket() {
		//If it's a data packet
	    if (data != null) {
			packet = new byte[data.length + 12];
		    packCksum();
		    packLen();
		    packSeqno();
		    packAckno();
		    packData();

	    } else {
	    	packet = new byte[8];
	    	packCksum();
	    	packLen();
	    	packAckno();
	    } // Otherwise it's an ack packet
	}
	
	/*
	 * Unpacks the packet
	 */
	private void unpackPacket() {

    	unpackCksum();
    	unpackLen();
    	unpackAckno();
    
	    if (len > 8) {
	    	unpackSeqno();
	    	unpackData();
	    }
	}
	
	/*
	 * Returns the packed byte array
	 */
	public byte[] getPacket() {
		return packet;
	}
	
	/*
	 * Oh no! An error occurred
	 */
	public void error() {
		cksum = 1;
		packCksum();
	}
	
	/*
	 * Is this a valid packet?
	 */
	public boolean isValidPacket() {
		return cksum == 0;
	}
	
	/*
	 * Error correction
	 */
	public void fixError() {
		cksum = 0;
		packCksum();

	}
	
	/*
	 * Packs the checksum into the packet
	 */
	private void packCksum() {
	    packet[0] = (byte) ((cksum >> 8) & 0xff);
	    packet[1] = (byte) (cksum & 0xff);
	}
	
	/*
	 * Packs the length into the packet
	 */
	private void packLen() {
	    packet[2] = (byte) ((len >> 8) & 0xff);
	    packet[3] = (byte) (len & 0xff);
	}
	
	/*
	 * Packs the Ack number into the packet
	 */
	private void packAckno() {
	    packet[4] = (byte) ((ackno & 0xFF000000) >> 24);
	    packet[5] = (byte) ((ackno & 0x00FF0000) >> 16);
	    packet[6] = (byte) ((ackno & 0x0000FF00) >> 8);
	    packet[7] = (byte) ((ackno & 0x000000FF) >> 0);
	}
	
	/*
	 * Packs the sequence number into the packet
	 */
	private void packSeqno() {
	    packet[8] = (byte) ((seqno & 0xFF000000) >> 24);
	    packet[9] = (byte) ((seqno & 0x00FF0000) >> 16);
	    packet[10] = (byte) ((seqno & 0x0000FF00) >> 8);
	    packet[11] = (byte) ((seqno & 0x000000FF) >> 0);
	}
	
	/*
	 * Packs the payload into the packet
	 */
	private void packData() {
		for (int i = 0; i < data.length; i++) {
			packet[i + 12] = data[i];
		}
	}
	
	/*
	 * Unpacks the checksum from the packet
	 */
	private void unpackCksum() {
		byte[] bCksum = new byte[2];
    	for (int i = 0; i < 2; i++) {
    		bCksum[i] = packet[i];
    	}
    	cksum = (short) ((bCksum[1] & 0xFF) << 8 | (bCksum[0] & 0xFF));
	}
	
	/*
	 * Unpacks the length from the packet
	 */
	private void unpackLen() {
		byte[] bLen = new byte[2];
    	for (int i = 0; i < 2; i++) {
    		bLen[i] = packet[i + 2];
    	}
    	len = ByteBuffer.wrap(bLen).getShort();
	}
	
	/*
	 * Unpacks the Ack number from the packet
	 */
	private void unpackAckno() {
    	byte[] bAckno = new byte[4];
    	for (int i = 0; i < 4; i++) {
    		bAckno[i] = packet[i + 4];
    	}
    	ackno = ByteBuffer.wrap(bAckno).getInt();
	}
	
	/*
	 * Unpacks the sequence number from the packet
	 */
	private void unpackSeqno() {
		data = new byte[len - 12];
    	byte[] bSeqno = new byte[4];
    	for (int i = 0; i < 4; i++) {
    		bSeqno[i] = packet[i + 8];
    	}
    	seqno = ByteBuffer.wrap(bSeqno).getInt();
	}
	
	/*
	 * Unpacks the payload from the packet
	 */
	private void unpackData() {
		for (int i = 0; i + 12 < len; i++) {
			 data[i] = packet[i + 12];
		}
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
		packAckno();
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
}
