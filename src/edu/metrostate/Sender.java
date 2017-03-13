package edu.metrostate;

import java.io.*;
import java.net.*;

public class Sender {
	public final static String SIZE = "-s";
	public final static String TIMEOUT_INTERVAL = "-t";
	public final static String WINDOW_SIZE = "-w";
	public final static String CORRUPT_DATAGRAMS = "-d";
	public final static int PORT = 5002;
	public static int timeout = 10000;
	public static int window = 1;
	public static double corruptDatagramsPercent = 0.25;

	public static void main(String[] args) {
		
		Packet packet = new Packet();

		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) {
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
				}
			}
		}
		try {
			InetAddress ia = InetAddress.getByName(hostname);
			DatagramSocket socket = new DatagramSocket();
			socket.setSoTimeout(timeout);
			SenderThread sender = new SenderThread(socket, ia, PORT);
			sender.start();
			Thread receiver = new ReceiverThread(socket);
			receiver.start();
		} catch (UnknownHostException ex) {
			System.err.println(ex);
		} catch (SocketException ex) {
			System.err.println(ex);
		}
	}
} // end class UDPEchoClient

class SenderThread extends Thread {
	private InetAddress server;
	private DatagramSocket socket;
	private int port;
	private volatile boolean stopped = false;
	
	SenderThread(DatagramSocket socket, InetAddress address, int port) {
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
			BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				if (stopped) {
					return;
				}
				String theLine = userInput.readLine();
				if (theLine.equals(".")) {
					break;
				}
				byte[] data = theLine.getBytes("UTF-8");
				DatagramPacket output = new DatagramPacket(data, data.length, server, port);
				socket.send(output);
				Thread.yield();
			}
		} catch (IOException ex) {
			System.err.println(ex);
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
	short len;	//16-bit 2-byte
	int ackno;	//32-bit 4-byte
	int seqno ; 	//32-bit 4-byte Data packet Only
	byte data[] = new byte[500]; //0-500 bytes. Data packet only. Variable
}


