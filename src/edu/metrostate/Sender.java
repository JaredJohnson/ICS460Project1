package edu.metrostate;

import java.io.*;
import java.net.*;
import java.sql.Timestamp;
import java.util.Date;

public class Sender {
	public final static String SIZE = "-s";
	public static int size = 512;
	public final static String TIMEOUT_INTERVAL = "-t";
	public static int timeout = 20000;
	public final static String WINDOW_SIZE = "-w";
	public final static String CORRUPT_DATAGRAMS = "-d";
	public static float corruptDatagramsRatio = 0;
	public static Packet timeoutPacket;
	public static int port = 5002;
	public static int window = 1;
	public static int stop = 1;
	public static int wait = 1;

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
//					port = Integer.parseInt(argument);
//					i -= 1;
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
	private static int byteCount = 1;
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
			int seqno = 0;
			BufferedReader file = new BufferedReader(new InputStreamReader(
					Sender.class.getResourceAsStream(
							"ICS460-Projects1-and-2.txt"), "UTF-8"));
			while (true) {
				if (stopped) {
					return;
				}
				try {
					// Create next packet
					Packet packet = new Packet((short) Sender.size, seqno++, seqno++);
					// Read text from buffer into char[] and convert to byte[]
					char[] c = new char[Sender.size];
					int i = file.read(c, 0, Sender.size-12);
					packet.setData(new String(c).getBytes("UTF-8"));
					if (i == -1) { // End of file
						break; 
					}
					// Set static packet in case of timeout
					Sender.timeoutPacket = packet;

					sendPacket(packet, "SENDing: ");
					halt();

				} catch (SocketTimeoutException ex) { // If no ack - resend
					System.out.println(Sender.timeoutPacket.getCurrentTime() + 
							" [Timeout]: seqno: " + Sender.timeoutPacket.getSeqno());
					sendPacket(Sender.timeoutPacket, "ReSend: ");
					halt();
				}
			}
		} catch (IOException ex) {
			System.err.println(ex);
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
				"[" + ((packet.getSeqno()*Sender.size)-Sender.size) + " : ", 
				(packet.getSeqno()*Sender.size) + "]", condition));
		// Send packet
		DatagramPacket output = new DatagramPacket(data, data.length, server, port);
		Thread.sleep(1000); // Slow down to human time
		socket.send(output);
	}
} // end class SenderThread

class ReceiverThread extends Thread {
	private DatagramSocket socket;
	private volatile boolean stopped = false;
	public Sender sender;
	
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
			try { // Receive ack datagram
				socket.receive(dp);
				// Convert bytes back to Packet object
				Packet ack = new Packet();
				ack = ack.convertToPacket(dp.getData());
				
				// Corrupted ack
				if (ack.getCksum() == 1) { // Don't send next packet (Wait for timeout)
					System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
										(ack.getAckno()) + " [ErrAck]");
					Thread.sleep(Sender.timeout);
				}
				// Expected ack
				if (ack.getAckno() == Sender.stop) { // Send next packet
					System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
										(ack.getAckno()));
					Sender.stop++;
				}
				// Duplicate ack
				if (ack.getAckno() == Sender.stop-1) {
					System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
										(ack.getAckno()) + "[DuplAck]");
				}
			} catch (IOException ex) {
				System.err.println(ex);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
} // end class ReceiverThread



