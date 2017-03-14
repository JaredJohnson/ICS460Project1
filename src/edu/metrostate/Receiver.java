package edu.metrostate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
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
		byte[] buffer = new byte[Sender.size];
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
					Packet ack = new Packet((short) 0, (short) 8, ackno++, 0, null);
					byte[] data = ack.convertToBytes(ack);
					this.respond(socket, incoming, data);
				} catch (SocketTimeoutException ex) {
					if (isShutDown) {
						return;
					}
				} catch (IOException ex) {
					logger.log(Level.WARNING, ex.getMessage(), ex);
				}
			} // end while
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Could not bind to port: " + port, ex);
		}
	}
	
	public void shutDown() {
		this.isShutDown = true;
	}
	
	public void respond(DatagramSocket socket, DatagramPacket packet, byte[] data) throws IOException {
		DatagramPacket outgoing = new DatagramPacket (data, packet.getLength(), 
				packet.getAddress(), packet.getPort());
		socket.send(outgoing);
	}

	public static void main(String[] args) {
		Receiver server = new Receiver();
		Thread t = new Thread (server);
		t.start();
	}
}
