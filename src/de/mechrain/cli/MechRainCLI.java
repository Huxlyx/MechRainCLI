package de.mechrain.cli;

import static de.mechrain.cli.MechRainTerminal.CLEAR;
import static de.mechrain.cli.MechRainTerminal.CONFIG;
import static de.mechrain.cli.MechRainTerminal.DUMP;
import static de.mechrain.cli.MechRainTerminal.FILTER;
import static de.mechrain.cli.MechRainTerminal.RECONNECT;
import static de.mechrain.cli.MechRainTerminal.SET;
import static de.mechrain.cli.MechRainTerminal.SHOW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.spi.StandardLevel;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import de.mechrain.cli.LogConfig.FilterBy;

public class MechRainCLI implements Callable<Integer> {

	int port = 5000;
	boolean reconnect;

	static long start;
	
	private final MechRainTerminal terminal;
	
	public MechRainCLI(final MechRainTerminal terminal) {
		this.terminal = terminal;
	}

	@Override
	public Integer call() throws Exception {
		final LogConfig config = new LogConfig();
		do {
			final Socket socket = connect(port);
			if (socket == null) {
				if (reconnect) {
					System.out.println("Re-attempting");
					continue;
				} else {
					System.err.println("Could not get connection");
					return 7;
				}
			} else {
				terminal.clear();
				terminal.printHeader();
				terminal.printInfo("Connection established (took " + (System.currentTimeMillis() - start) + "ms) ");
			}

			final InputStream inputStream = socket.getInputStream();
			final OutputStream outputStream = socket.getOutputStream();
			final ConsoleOutputRunner outputRunner = new ConsoleOutputRunner(inputStream, outputStream, terminal, config);
			final Thread cliThread = new Thread(outputRunner);
			cliThread.start();
			
			boolean running = true;
			while (running) {
				terminal.maybeWaitForNonInteractive();
				final String line = terminal.readLine("MechRain> ");

				final String[] splits = line.split(" ");

				switch (terminal.getMode()) {
				case GENERAL:
					running = handleGeneral(splits, outputRunner, config, socket);
					break;
				case DEVICE:
					handleDevice(splits, outputRunner);
					break;
				}
			}
		}
		while (reconnect);
		return 1;
	}

	private boolean handleGeneral(final String[] splits, final ConsoleOutputRunner outputRunner, final LogConfig config, final Socket socket) throws IOException {
		boolean redraw = false;
		boolean running = true;
		switch (splits[0].toLowerCase()) {
		case CLEAR:
			if (splits.length == 2 && splits[1].equalsIgnoreCase("buffer")) {
				outputRunner.clearBuffer();
			} else {
				terminal.clear();
			}
			break;
		case CONFIG:
			if (splits.length != 3) {
				terminal.printError("expected 'device' and number");
				return true;
			}
			outputRunner.configDevice(splits[2]);
			break;
		case DUMP:
			if (splits.length != 2) {
				terminal.printError("expected 2 arguments but got " + splits.length);
				return true;
			}
			outputRunner.dumpToFile(splits[1]);
			break;
		case FILTER:
			if (splits.length < 2) {
				terminal.printError("expected at least 2 arguments but got " + splits.length);
				return true;
			}
			switch (splits[1].toLowerCase()) {
			case "logname":
				if (splits.length < 3) {
					terminal.printError("expected at least 3 arguments but got " + splits.length);
					return true;
				}
				config.setFilterBy(FilterBy.LOG_NAME);
				config.setFilterString(splits[2]);
				redraw = true;
				break;
			case "text":
				if (splits.length < 3) {
					terminal.printError("expected at least 3 arguments but got " + splits.length);
					return true;
				}
				config.setFilterBy(FilterBy.TEXT);
				config.setFilterString(splits[2]);
				redraw = true;
				break;
			case "off":
				config.setFilterBy(FilterBy.DONT);
				redraw = true;
				break;
			default:
				terminal.printError("Unkown filter option " + splits[1]);
				break;
			}
			break;
		case SHOW:
			if (splits.length != 2) {
				terminal.printError("expected 2 arguments but got " + splits.length);
				return true;
			}
			switch (splits[1].toLowerCase()) {
			case "buffer":
				outputRunner.showBuffer();
				break;
			case "devices":
				outputRunner.showDevices();
				break;
			case "diagram":
				showDiagram();
				break;
			default:
				terminal.printError("Unkown show option " + splits[1]);
				break;
			}
			break;
		case SET:
			if (splits.length != 3) {
				terminal.printError("expected 3 arguments but got " + splits.length);
				return true;
			}
			switch (splits[1].toLowerCase()) {
			case "level":
				switch (splits[2].toLowerCase()) {
				case "off":
					config.setFilterLevel(StandardLevel.OFF);
					redraw = true;
					break;
				case "err":
					config.setFilterLevel(StandardLevel.ERROR);
					redraw = true;
					break;
				case "warn":
					config.setFilterLevel(StandardLevel.WARN);
					redraw = true;
					break;
				case "info":
					config.setFilterLevel(StandardLevel.INFO);
					redraw = true;
					break;
				case "debug":
					config.setFilterLevel(StandardLevel.DEBUG);
					redraw = true;
					break;
				case "trace":
					config.setFilterLevel(StandardLevel.TRACE);
					redraw = true;
					break;
				default:
					terminal.printError("Unkown level '" + splits[2] + "'");
					redraw = false;
					break;
				}
				break;
			case "time":
				switch (splits[2].toLowerCase()) {
				case "on":
					config.setShowTime(true);
					break;
				case "off":
					config.setShowTime(false);
					break;
				default:
					terminal.printError("Expected 'on' or 'off' but got " + splits[2]);
					return true;
				}
				redraw = true;
				break;
			case "logname":
				switch (splits[2].toLowerCase()) {
				case "on":
					config.setShowLoggerName(true);
					break;
				case "off":
					config.setShowLoggerName(false);
					break;
				default:
					terminal.printError("Expected 'on' or 'off' but got " + splits[2]);
					return true;
				}
				redraw = true;
				break;
			}
			break;
		case RECONNECT:
			socket.close();
			running = false;
			reconnect = true;
			start = System.currentTimeMillis();
			break;
		case "switch":
			terminal.switchReader();
			break;
		default:
			terminal.printError("Unkown option " + splits[0]);
			break;
		}

		if (redraw) {
			try {
				outputRunner.setUpdateConsole(false);
				outputRunner.redraw();
			} finally {
				outputRunner.setUpdateConsole(true);
			}
		}
		
		return running;
	}

	private void handleDevice(final String[] splits, final ConsoleOutputRunner outputRunner) {
		switch (splits[0].toLowerCase()) {
		case "add":
			if (splits.length != 2) {
				terminal.printError("expected 2 arguments but got " + splits.length);
				return;
			}
			switch (splits[1].toLowerCase()) {
			case "sink":
				outputRunner.addSink();
				break;
			case "task":
				outputRunner.addTask();
				break;
			default:
				terminal.printError("Expected 'on' or 'off' but got " + splits[2]);
				return;
			} 
			break;
		case "remove":
			break;	
		case "save":
			break;
		default:
			terminal.printError("Unkown option " + splits[0]);
			break;
		}
	}

	private void showDiagram() {
		final AttributedStringBuilder asb = new AttributedStringBuilder();
		asb.style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN));
		asb.append("          *+----------------------+           \n");
		asb.append("           |[3V3]            [GND]|           \n");
		asb.append("           |[EN ]            [G23]|           \n");
		asb.append("     [IN0 ]|[G36]  +------+  [G22]|[SHT-SCL]  \n");
		asb.append("     [IN1 ]|[G39]  |ESP-32|  [G1 ]|           \n");
		asb.append("     [IN2 ]|[G34]  +------+  [G3 ]|           \n");
		asb.append("     [IN3 ]|[G35]            [G21]|[SHT-SDA]  \n");
		asb.append("     [IN4 ]|[G32]            [GND]|           \n");
		asb.append("     [IN5 ]|[G33]            [G19]|           \n");
		asb.append("     [IN6 ]|[G25]            [G18]|           \n");
		asb.append("     [IN7 ]|[G26]            [G5 ]|           \n");
		asb.append("     [OUT0]|[G27]            [G17]|[PWM (CO2)]\n");
		asb.append("     [OUT1]|[G14]            [G16]|           \n");
		asb.append("     [OUT2]|[G12]            [G4 ]|           \n");
		asb.append("     [OUT3]|[G13]            [G0 ]|           \n");
		asb.append("           |[G9 ]            [G2 ]|           \n");
		asb.append("           |[G10]            [G15]|           \n");
		asb.append("           |[G11]            [G8 ]|           \n");
		asb.append("           |[GND]            [G7 ]|           \n");
		asb.append("           |[5V ]            [G6 ]|           \n");
		asb.append("           +----------------------+           \n");
		asb.style(AttributedStyle.DEFAULT);
		terminal.printAbove(asb);
	}

	private Socket connect(final int udpPort) throws IOException {
		try (final DatagramSocket socket = new DatagramSocket(9999)) {
			socket.setBroadcast(true);
			socket.setSoTimeout(5_000);

			terminal.write("Waiting for connection");

			while (true) {
				try {
					final byte[] payload = "CLI-HELLO".getBytes(StandardCharsets.UTF_8);
					final DatagramPacket broadcast = new DatagramPacket(payload, payload.length, InetAddress.getByName("255.255.255.255"), udpPort);
					socket.send(broadcast);

					final byte[] buf = new byte[256];
					final DatagramPacket response = new DatagramPacket(buf, buf.length);
					socket.receive(response);

					final String responseString = new String(response.getData(), 0, response.getLength());
					final String port = responseString.substring(responseString.lastIndexOf("PORT=") + 5);
					final Socket result = new Socket(response.getAddress(), Integer.valueOf(port));

					return result;
				} catch (final SocketTimeoutException e) {
					terminal.write(".");
				}
			}
		}
	}


	public static void main(String[] args) throws Exception {
		start = System.currentTimeMillis();
		final MechRainTerminal terminal = new MechRainTerminal();
		MechRainCLI cli = new MechRainCLI(terminal);
		cli.call();
		System.exit(1);
	}
}
