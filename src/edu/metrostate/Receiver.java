package edu.metrostate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Receiver implements Runnable {
	public final static String WINDOW_SIZE = "-w";
	public final static String CORRUPT_DATAGRAMS = "-d";
	public static int window = 1;
	public static float corruptDatagramsRatio = 0.25f;
	private int bufferSize; // in bytes
	private static int port = 5002;
	private final InetAddress address;
	private final Logger logger = Logger.getLogger(Receiver.class.getCanonicalName());
	private volatile boolean isShutDown = false;

	public Receiver (InetAddress address, int port) {
		this.address = address;
		this.port = port;
	}
	
	public static void main(String[] args) throws UnknownHostException {
		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) { // Take in any arguments
			for(int i = 0; i < args.length; i+= 2) {
				String argument = args[i];
				int value = Integer.parseInt(args[i+1]);
				
				switch (argument) {
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
		InetAddress ia = InetAddress.getByName(hostname);
		Receiver server = new Receiver(ia, port);
		Thread t = new Thread (server);
		System.out.println("Receiver is waiting patiently for some packet action.......");
		t.start();
	}
	
	@Override
	public void run() {
		byte[] buffer = new byte[65507];
		try (DatagramSocket socket = new DatagramSocket (port)) {
			socket.setSoTimeout(10000); // check every 10 seconds for shutdown
			int ackno = 1;
			while (true) {
				if (isShutDown) {
					return;
				}
				
				DatagramPacket incoming = new DatagramPacket (buffer, buffer.length);
				try {
					socket.receive(incoming);
					// Convert datagram data back into packet object
					Packet incomingPacket = new Packet();
					incomingPacket = incomingPacket.convertToPacket(incoming.getData());
					
					// Corrupted packet - Don't send ack
					if (incomingPacket.getCksum() == 1) {
						System.out.print(String.format("%s [%-7s] %-7s %s %s\n",
								incomingPacket.getCurrentTime(), "RECV: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[CRPT]" , "No ack sent"));
						// Sender should timeout and resend
						Thread.sleep(Sender.timeout);
					}
					// Otherwise we're sending an ack
					// Simulate lossy network
					Packet ack = new Packet((short) 0, (short) 8, ackno);
					String ackCondition = ack.simLossyNetwork(ack);
					
					// Not corrupted and expected
					if (incomingPacket.getSeqno() == ackno) {
						writeToOutputFile(incomingPacket);
						System.out.print(String.format("%s [%-7s] %-7s %s %s\n",
								incomingPacket.getCurrentTime(), "RECV: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[RECV]" , ackCondition));
						sendAck(socket, incoming, ack);
						ackno++;
					}
					
					// Not corrupted and a duplicate (a resend) and expected
					if (incomingPacket.getSeqno() == ackno-1) {
						System.out.print(String.format("%s [%-7s] %-7s %s %s\n",
								incomingPacket.getCurrentTime(),"DUPL: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[!Seq]" , ackCondition));
						sendAck(socket, incoming, ack);
					}
				} catch (SocketTimeoutException ex) {
					if (isShutDown) {
						return;
					}
				} catch (IOException ex) {
					logger.log(Level.WARNING, ex.getMessage(), ex);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} // end while
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Could not bind to port: " + port, ex);
		}
	}
	
	public void shutDown() {
		this.isShutDown = true;
	}
	
	public void sendAck(DatagramSocket socket, DatagramPacket packet, Packet ack) throws IOException {
		byte[] data = ack.convertToBytes(ack);
		DatagramPacket outgoing = new DatagramPacket (data, data.length, 
				packet.getAddress(), packet.getPort());
		socket.send(outgoing);
	}
	
	public void writeToOutputFile(Packet packet) throws IOException {
		FileOutputStream out = new FileOutputStream("output.txt", true);
        out.write(packet.getData(), 0, packet.getData().length);
        out.close();
	}
}
