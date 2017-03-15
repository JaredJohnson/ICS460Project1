package edu.metrostate;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Receiver implements Runnable {
	public final static int DEFAULT_PORT = 5002;
	private int bufferSize; // in bytes
	private final int port;
	private final Logger logger = Logger.getLogger(Receiver.class.getCanonicalName());
	private volatile boolean isShutDown = false;
	
	public Receiver (int port, int bufferSize) {
		this.bufferSize = bufferSize;
		this.port = port;
	}
	public Receiver (int port) {
		this(port, 8192);
	}
	public Receiver () {
		this(DEFAULT_PORT);
	}
	
	@Override
	public void run() {
		byte[] buffer = new byte[1024];
		try (DatagramSocket socket = new DatagramSocket (port)) {
			socket.setSoTimeout(100000); // check every 10 seconds for shutdown
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
					
					writeToOutputFile(incomingPacket);
					
					// Simulate lossy network
					Packet ack = new Packet((short) 0, (short) 8, ackno, 0, null);
					String ackCondition = ack.simLossyNetwork(ack);
					
					// Not corrupted and expected
					if (incomingPacket.getCksum() == 0 && incomingPacket.getSeqno() == ackno) {
						sendAck(socket, incoming, ackno);
						System.out.print(String.format("[%-7s] %-7s %s %s\n",
								"RECV: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[RECV]" , ackCondition));
						ackno++;
					}
					// Corrupted packet
					if (incomingPacket.getCksum() == 1) { // Don't send ack
						System.out.print(String.format("[%-7s] %-7s %s %s\n",
								"RECV: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[CRPT]" , ackCondition));
					}
					// Duplicate (a resend)
					if (incomingPacket.getSeqno() == Sender.seqno) {
						System.out.print(String.format("[%-10s] %-7s %s %s\n",
								"DUPL: ", "seqno: [" + incomingPacket.getSeqno() + "]", 
								"[!Seq]" , ackCondition));
						sendAck(socket, incoming, ackno++);
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
	
	public void sendAck(DatagramSocket socket, DatagramPacket packet, int ackno) throws IOException {
		Packet ack = new Packet((short) 0, (short) 8, ackno, 0, null);
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

	public static void main(String[] args) {
		Receiver server = new Receiver();
		Thread t = new Thread (server);
		System.out.println("Receiver is waiting patiently for some packet action.......");
		t.start();
	}
}
