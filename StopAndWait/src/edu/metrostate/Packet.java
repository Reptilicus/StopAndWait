package edu.metrostate;

public class Packet {
	short cksum;
	short len;
	int ackno;
	int seqno;
	byte[] data;
	byte[] packets;
	
	/*
	 * Creates a data packet, calculates cksum and len, and creates packet
	 */
	public Packet(byte[] data, int seqno) {
		//TODO
		
	}
	
	/*
	 * Creates an ACK packet
	 */
	public Packet(int ackno) {
		//TODO
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
		return new byte[10];
	}
	

}
