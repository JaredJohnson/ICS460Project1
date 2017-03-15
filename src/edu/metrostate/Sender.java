package edu.metrostate;

import java.io.*;
import java.net.*;

public class Sender {
	public final static String SIZE = "-s";
	public static int size = 512;
	public final static String TIMEOUT_INTERVAL = "-t";
	public static int timeout = 2000;
	public final static String WINDOW_SIZE = "-w";
	public final static String CORRUPT_DATAGRAMS = "-d";
	public static float corruptDatagramsRatio = 0.25f;
	public static int port = 5002;
	public static int window = 1;

	public static void main(String[] args) {
		
		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) { // Take in any arguments
			for(int i = 0; i < args.length; i+= 2) {
				String argument = args[i];
				int value = Integer.parseInt(args[i+1]);
				
				switch (argument) {
					case SIZE: size = value;
					break;
					case TIMEOUT_INTERVAL: timeout = value;
					break;
					case WINDOW_SIZE: window = value;
					break;
					case CORRUPT_DATAGRAMS: corruptDatagramsRatio = value;
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
			SenderThread sender = new SenderThread(socket, ia, port);
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
	public static float corruptDatagramsRatio;
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
			BufferedReader file = new BufferedReader(new InputStreamReader(
					Sender.class.getResourceAsStream(
							"ICS460-Projects1-and-2.txt"), "UTF-8"));
			while (true) {
				if (stopped) {
					return;
				}
				Packet packet = new Packet();
				// Read text from buffer into char[] and convert to byte[]
				char[] c = new char[Sender.size];
				int i = file.read(c, 0, Sender.size-12);
				packet.setData(new String(c).getBytes("UTF-8"));
				if (i == -1) { // End of file
					break;
				}
				// Simulate lossy network
				String condition = packet.simLossyNetwork(packet);
				// Increment sequence number
				packet.setSeqno(packet.getSeqno()+1);
				// Convert packet to bytes
				byte[] data = packet.convertToBytes(packet);
				DatagramPacket output = new DatagramPacket(data, Sender.size, server, port);
				System.out.println("[SENDing]: ");
				socket.send(output);
				Thread.sleep(1000); // Slow down to human time
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
		byte[] buffer = new byte[Sender.size];
		while (true) {
			if (stopped) {
				return;
			}
			DatagramPacket dp = new DatagramPacket(buffer, buffer.length);
			try { // Receive datagram
				socket.receive(dp);
				// Convert bytes back to Packet object
				Packet ack = new Packet();
				ack = ack.convertToPacket(dp.getData());
				System.out.println("[AckRcvd]: " + (ack.getAckno()-1));
				Thread.yield();
			} catch (IOException | ClassNotFoundException ex) {
				System.err.println(ex);
			}
		}
	}
} // end class ReceiverThread



