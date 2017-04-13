package edu.metrostate;

import java.io.*;
import java.net.*;

public class Sender {
	public final static String SIZE = "-s";
	public static int size = 1024;
	public final static String TIMEOUT_INTERVAL = "-t";
	public static int timeout = 2000;
	public final static String WINDOW_SIZE = "-w";
	public static int window = 1;
	public final static String CORRUPT_DATAGRAMS = "-d";
	public static float corruptDatagramsRatio = 0.25f;
	public static int port = 5002;
	

	public static void main(String[] args) {
		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) { // Take in any arguments
			for(int i = 0; i < args.length; i+= 2) {
				String argument = args[i];
				
				if (argument.equals(SIZE)) {
					size = Integer.parseInt(args[i+1]);
				}
				else if (argument.equals(TIMEOUT_INTERVAL)) { 
					timeout = Integer.parseInt(args[i+1]);
				}
				else if (argument.equals(WINDOW_SIZE)) {
					window = Integer.parseInt(args[i+1]);
				}
				else if (argument.equals(CORRUPT_DATAGRAMS)) { 
					corruptDatagramsRatio = Float.parseFloat(args[i+1]);
				}
				else if (argument.contains(".")) {
					hostname = argument;
					i--;
				} else {
					port = Integer.parseInt(argument);
					i--;
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
	
	public void halt(DatagramPacket exitPacket) throws IOException {
		socket.send(exitPacket);
		this.stopped = true;
	}
	
	@Override
	public void run() {
		try {
			BufferedReader file = new BufferedReader(new InputStreamReader(
					Sender.class.getResourceAsStream(
							"PHIL301_ThoughtPaper1.txt"), "UTF-8"));
			while (true) {
				if (stopped) {
					System.exit(0);
				}
				try {
					Thread.sleep(100);
					// Read text from buffer into char[] and convert to byte[]
					char[] c = new char[Sender.size];
					int i = file.read(c, 0, Sender.size-12);
					if (i == -1) { // End of file
						byte[] exit = new byte[0]; // Tell receiver to shut down with empty data[]
						DatagramPacket exitPacket = new DatagramPacket(exit, exit.length, server, port);
						halt(exitPacket);
						continue;
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
						packet.setCksum((short) 0);// CRPT packet caught at rcvr
						System.out.println(packet.getCurrentTime() + 
								" [Timeout]: seqno: " + packet.getSeqno());
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
		if (condition == "SENT" || condition == "ERRR") { 
			socket.send(output);
			resend = false;
		} else { // DLYD or DROP
			Thread.sleep(Sender.timeout);
			resend = true;
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
				Thread.sleep(100);
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
					} else { // Duplicate ack
						int dupCount = ack.getAckno()-SenderThread.seqno;
						System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
								(ack.getAckno()-dupCount) + "[DuplAck]");
						SenderThread.resend = false;
						wakeSender();
					}
				} else { // Corrupted ack -- Don't send next packet (Wait for timeout)
						System.out.println(ack.getCurrentTime() + " [AckRcvd]: " + 
								(ack.getAckno()) + " [ErrAck]");
					}
			} catch (IOException ex) { // TIMEOUT: Resend Packet
					SenderThread.resend = true;
					wakeSender();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	private void wakeSender() {
		synchronized(SenderThread.monitor) {
			SenderThread.monitor.notify();
		}
	}
} // end class ReceiverThread



