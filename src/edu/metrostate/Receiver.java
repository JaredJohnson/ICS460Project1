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
	public static int window = 1;
	public final static String CORRUPT_DATAGRAMS = "-d";
	public static float corruptDatagramsRatio = 0.25f;
	private int bufferSize; // in bytes
	private static int port = 5002;
	private final InetAddress address;
	private final Logger logger = Logger.getLogger(Receiver.class.getCanonicalName());
	private volatile boolean isShutDown = false;
	public static int ackno = 1;

	public Receiver (InetAddress address, int port) {
		this.address = address;
		this.port = port;
	}
	
	public static void main(String[] args) throws UnknownHostException {
		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) { // Take in any arguments
			for(int i = 0; i < args.length; i+= 2) {
				String argument = args[i];

				if (argument.equals(WINDOW_SIZE)) {
					window = Integer.parseInt(args[i+1]);
				}
				else if (argument.equals(CORRUPT_DATAGRAMS)) { 
					corruptDatagramsRatio = Float.parseFloat(args[i+1]);
				}
				else if (argument.contains(".")) {
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
			while (true) {
				if (isShutDown) {
					System.exit(0);
				}
				DatagramPacket incoming = new DatagramPacket (buffer, buffer.length);
				try {
					socket.receive(incoming);
					// Convert datagram data back into packet object
					Packet incomingPacket = new Packet();
					incomingPacket = incomingPacket.convertToPacket(incoming.getData());
					if (incoming.getLength() == 0) {
						shutDown();
						continue;
					}
					
					Packet ack = new Packet((short) 0, (short) 8, ackno);
					
					if (incomingPacket.getCksum() == 0) {
						sendAck(socket, incoming, incomingPacket, ack);
						
					} else { // Corrupted packet - Don't send ack
						System.out.print(String.format("%s [%-7s] %-7s %s %s\n",
								incomingPacket.getCurrentTime(), "RECV: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[CRPT]" , "No ack sent"));
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
	
	public void sendAck(DatagramSocket socket, DatagramPacket packet, Packet incoming, Packet ack) throws IOException, InterruptedException {
		// Simulate lossy network
		String ackCondition = ack.simLossyNetwork(ack);
		byte[] data = ack.convertToBytes(ack);
		DatagramPacket outgoing = new DatagramPacket (data, data.length, 
				packet.getAddress(), packet.getPort());
		
		// Not corrupted and expected
		if (incoming.getSeqno() == ackno) {
			writeToOutputFile(incoming);
			ackno++;
			System.out.print(String.format("%s [%-7s] %-7s %s %s\n",
					incoming.getCurrentTime(), "RECV: ", "seqno: [" + incoming.getSeqno() + "]", 
					"[RECV]" , ackCondition));
		} else { // It's a Resend! -- just send another ack 
			System.out.print(String.format("%s [%-7s] %-7s %s %s\n",
					incoming.getCurrentTime(),"DUPL: ", "seqno: [" + incoming.getSeqno() + "]", 
					"[!Seq]" , ackCondition));
		}
			if (ackCondition != "DROP") { 
				socket.send(outgoing);
			}
			
	}
	
	public void writeToOutputFile(Packet packet) throws IOException {
		FileOutputStream out = new FileOutputStream("output.txt", true);
        out.write(packet.getData(), 0, packet.getData().length);
        out.close();
	}
}
