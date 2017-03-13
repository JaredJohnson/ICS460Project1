package edu.metrostate;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Sender {
	public final static String SIZE = "-s";
	public final static String TIMEOUT_INTERVAL = "-t";
	public static int timeout = 10000;
	public final static String WINDOW_SIZE = "-w";
	public final static String CORRUPT_DATAGRAMS = "-d";
	public static double corruptDatagramsPercent = 0.25;
	public static int port = 5002;
	public static int window = 1;

	public static void main(String[] args) {
		
		Packet packet = new Packet();
		
		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) { // Take in any arguments
			for(int i = 0; i < args.length; i+= 2) {
				String argument = args[i];
				int value = Integer.parseInt(args[i+1]);
				
				switch (argument) {
					case SIZE: packet.len = (short) value;
					break;
					case TIMEOUT_INTERVAL: timeout = value;
					break;
					case WINDOW_SIZE: window = value;
					break;
					case CORRUPT_DATAGRAMS: corruptDatagramsPercent = value;
					break;
				}// Now check if arg is ip addr or rec port
				if (argument.contains(".")) {
					hostname = argument;
					i -= 1;
				} else {
					port = Integer.parseInt(argument);
					i -= 1;
				}
			}
		}
		try {
			InetAddress ia = InetAddress.getByName(hostname);
			DatagramSocket socket = new DatagramSocket();
			socket.setSoTimeout(timeout);
			SenderThread sender = new SenderThread(packet, socket, ia, port);
			sender.start();
			Thread receiver = new ReceiverThread(socket);
			receiver.start();
		} catch (UnknownHostException ex) {
			System.err.println(ex);
		} catch (SocketException ex) {
			System.err.println(ex);
		}
	}
} // end class Sender

class SenderThread extends Thread {
	private InetAddress server;
	private DatagramSocket socket;
	private Packet packet;
	private int port;
	private volatile boolean stopped = false;
	
	SenderThread(Packet packet, DatagramSocket socket, InetAddress address, int port) {
		this.packet = packet;
		this.server = address;
		this.port = port;
		this.socket = socket;
		this.socket.connect(server, port);
	}
	
	public void halt() {
		this.stopped = true;
	}
	
	@Override
	public void run() {
		try {
			BufferedReader file = new BufferedReader(new InputStreamReader(
					Sender.class.getResourceAsStream(
							"ICS460-Projects1-and-2.txt"), "UTF-8"));
			while (true) {
				if (stopped) {
					return;
				}
				// Read text from buffer into char[] and convert to byte[]
				char[] c = new char[packet.len];
				int i = file.read(c, 0, packet.len-12);
				packet.data = new String(c).getBytes("UTF-8");
				if (i == -1) { // End of file
					break;
				}
				DatagramPacket output = new DatagramPacket(packet.data, packet.len, server, port);
				socket.send(output);
				Thread.sleep(1000);
				Thread.yield();
			}
		} catch (IOException ex) {
			System.err.println(ex);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
} // end class SenderThread

class ReceiverThread extends Thread {
	private DatagramSocket socket;
	private volatile boolean stopped = false;
	
	ReceiverThread(DatagramSocket socket) {
		this.socket = socket;
	}
	
	public void halt() {
		this.stopped = true;
	}
	
	@Override
	public void run() {
		byte[] buffer = new byte[65507];
		while (true) {
			if (stopped) {
				return;
			}
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(dp);
				String s = new String(dp.getData(), 0, dp.getLength(), "UTF-8");
				System.out.println(s);
				Thread.yield();
			} catch (IOException ex) {
				System.err.println(ex);
			}
		}
	}
		

} // end class ReceiverThread

class Packet {
	short cksum; //16-bit 2-byte
	short len = 512;	//16-bit 2-byte
	int ackno;	//32-bit 4-byte
	int seqno ; 	//32-bit 4-byte Data packet Only
	byte data[] = new byte[500]; //0-500 bytes. Data packet only. Variable
	
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
	
	
}


