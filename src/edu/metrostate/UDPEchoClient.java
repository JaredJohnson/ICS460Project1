package edu.metrostate;

import java.io.*;
import java.net.*;

public class UDPEchoClient {
	public final static int PORT = 5002;

	public static void main(String[] args) {
		
		/**
		 * TEST PUSH
		 */
		String hostname = "localhost"; // translates to 127.0.0.1
		if (args.length > 0) {
			hostname = args[0];
		}
		try {
			InetAddress ia = InetAddress.getByName(hostname);
			DatagramSocket socket = new DatagramSocket();
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
	} // end class ReceiverThread
}

