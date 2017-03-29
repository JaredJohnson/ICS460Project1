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
	public static float corruptDatagramsRatio = 0.10f;
	public static Packet timeoutPacket;
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
	DatagramSocket socket;
	public static float corruptDatagramsRatio;
	private int port;
	public static int seqno = 0;
	public static boolean resend = false;
	static Object monitor = new Object();
    volatile boolean stopped = false;    
	
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
				try {
					// Read text from buffer into char[] and convert to byte[]
					char[] c = new char[Sender.size];
					int i = file.read(c, 0, Sender.size-12);
					if (i == -1) { // End of file
						halt();
						break; 
					}
					byte[] data = new String(c).getBytes("UTF-8");
					// Create next packet
					seqno++;
					Packet packet = new Packet((short) 0, (short) Sender.size, seqno, seqno, data);

					sendPacket(packet, "SENDing: ");
					if (resend == false) {
						waitForAck();
					}
					while (resend == true) {
						packet.setCksum((short) 0);
						System.out.println(packet.getCurrentTime() + 
								" [Timeout]: seqno: " + packet.getSeqno());
						resend = false;
						sendPacket(packet, "ReSend: ");
						waitForAck();
					}
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	
	public void sendPacket(Packet packet, String status) throws IOException, InterruptedException {
		// Simulate lossy network
		String condition = packet.simLossyNetwork(packet);
		// Convert packet to bytes
		byte[] data = packet.convertToBytes(packet);
		// Print status
		System.out.print(String.format("%s [%-7s] %-10s %s %s %s\n",
				packet.getCurrentTime(), status, "seqno: [" + packet.getSeqno() + "]", 
				"[" + ((packet.getSeqno()*Sender.size)-(Sender.size-1)) + " : ", 
				(packet.getSeqno()*Sender.size) + "]", condition));
		// Send packet
		DatagramPacket output = new DatagramPacket(data, data.length, server, port);
		if (condition == "DLYD") { 
			socket.send(output);
			resend = true;
		}
		else if (condition == "DROP") { 
			resend = true;
		} 
		else { // SENT or ERRR
			socket.send(output);
		}
	}
	
	public synchronized void waitForAck() {
		synchronized(monitor) {
			try {
				monitor.wait();
			} catch(InterruptedException e) {
			}
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
			DatagramPacket input = new DatagramPacket(buffer, buffer.length);
			try { 
				// Receive ack datagram
				socket.receive(input);
				// Convert bytes back to Packet object
				Packet ack = new Packet();
				ack = ack.convertToPacket(input.getData());
				
				// Not corrupt ack
				if (ack.getCksum() != 1) {
					// Expected ack -- Send next packet
					if (ack.getAckno() == SenderThread.seqno) {
						System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
								(ack.getAckno()));
						SenderThread.resend = false;
						wakeSender();
					} else { // Duplicate ack -- don't do anything
						System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
								(ack.getAckno()) + "[DuplAck]");
					}
				} else { // Corrupted ack -- Don't send next packet (Wait for timeout)
						System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
								(ack.getAckno()) + " [ErrAck]");
					}
			} catch (IOException ex) { // TIMEOUT: Resend Packet
					SenderThread.resend = true;
					wakeSender();
			}
		}
	}
	private void wakeSender() {
		synchronized(SenderThread.monitor) {
			SenderThread.monitor.notify();
		}
	}
} // end class ReceiverThread



