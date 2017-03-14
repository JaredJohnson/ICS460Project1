package edu.metrostate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Random;

public class Packet {
	private short cksum; //16-bit 2-byte
	private short len = 512;	//16-bit 2-byte
	private int ackno;	//32-bit 4-byte
	private int seqno = 0; 	//32-bit 4-byte Data packet Only
	private byte data[] = new byte[500]; //0-500 bytes. Data packet only. Variable
	
	public Packet(short cksum, short len, int ackno, int seqno, byte[] data) {
		this.cksum = cksum;
		this.len = len;
		this.ackno = ackno;
		this.seqno = seqno;
		this.data = data;
	}

	public Packet() {
		// TODO Auto-generated constructor stub
	}
	
	public String simLossyNetwork(Packet packet) throws InterruptedException {
		final int CORRUPT = 1;
		final int DELAY = 2;
		final int DROP = 3;
		Random number = new Random();
		
		if (number.nextFloat() < Sender.corruptDatagramsRatio) { // Corrupt
			int random = number.nextInt(3);
			switch(random) {
				case CORRUPT: packet.cksum = 1;
				return "ERRR";
				case DELAY: Thread.sleep(number.nextInt(5));
				return "DLYD";
				case DROP:
				return "DROP";
			}
		} 
		return "SENT";
	}
	
	public byte[] convertToBytes(Packet packet) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(6400);
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(packet);
		byte[] data = baos.toByteArray();
		return data;
	}
	
	public Packet convertToPacket(byte[] data) throws ClassNotFoundException, IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(data);
	    ObjectInputStream is = new ObjectInputStream(in);
	    return (Packet) is.readObject();
	}

	public short getCksum() {
		return cksum;
	}

	public void setCksum(short cksum) {
		this.cksum = cksum;
	}

	public short getLen() {
		return len;
	}

	public void setLen(short len) {
		this.len = len;
	}

	public int getAckno() {
		return ackno;
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
	
	
}
