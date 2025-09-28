package de.mechrain.cli;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.logging.log4j.core.LogEvent;

import de.mechrain.cmdline.beans.AddSinkRequest;
import de.mechrain.cmdline.beans.AddTaskRequest;
import de.mechrain.cmdline.beans.ConfigDeviceRequest;
import de.mechrain.cmdline.beans.ConsoleRequest;
import de.mechrain.cmdline.beans.ConsoleResponse;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.device.Device;

public class ConsoleOutputRunner implements Runnable {
	
	private static final int MAX_MESSAGES = 10_000;
	
	private final InputStream is;
	private final ObjectOutputStream os;
	private final MechRainTerminal terminal;
	private final LogConfig logConfig;
	
	private final Deque<LogMessage> logMessages = new ConcurrentLinkedDeque<>();
	
	private boolean updateConsole = true;
	
	public ConsoleOutputRunner(final InputStream is, final OutputStream os, final MechRainTerminal terminal, final LogConfig logConfig) throws IOException {
		this.is = is;
		this.os = new ObjectOutputStream(os);
		this.terminal = terminal;
		this.logConfig = logConfig;
	}

	public void setUpdateConsole(boolean updateConsole) {
		this.updateConsole = updateConsole;
	}
	
	public void showBuffer() {
		final int logMsgCount = logMessages.size();
		terminal.printInfo(logMsgCount + "/" + MAX_MESSAGES + ' ' + (((float)logMsgCount / MAX_MESSAGES) * 100) + "%");
	}
	
	public void showDevices() {
		try {
			os.writeObject(DeviceListRequest.INSTANCE);
			os.reset();
		} catch (final IOException e) {
			terminal.printError("Could not send device list request. " + e.getMessage());
		}
	}

	public void configDevice(final String id) {
		try {
			final int deviceId = Integer.parseInt(id);
			final ConfigDeviceRequest request = new ConfigDeviceRequest();
			request.setDeviceId(deviceId);
			os.writeObject(request);
			os.reset();
			terminal.switchReader();
		} catch (final NumberFormatException e) {
			terminal.printError("Invalid device id " + id + " expected a number. " + e.getMessage());
		} catch (final IOException e) {
			terminal.printError("Could not send config device request. " + e.getMessage());
		}
	}
	
	public void addSink() {
		try {
			final AddSinkRequest request = new AddSinkRequest();
			os.writeObject(request);
			os.reset();
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add sink request. " + e.getMessage());
		}
	}
	
	public void addTask() {
		try {
			final AddTaskRequest request = new AddTaskRequest();
			os.writeObject(request);
			os.reset();
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add task request. " + e.getMessage());
		}
	}
	
	public void clearBuffer() {
		logMessages.clear();
	}
	
	private boolean shouldOutput(final LogMessage msg) {
		if (msg.getLevel().intLevel() > logConfig.getFilterLevel().intLevel()) {
			return false;
		}

		switch (logConfig.getFilterBy()) {
		case DONT:
			return true;
		case LOG_NAME:
			return msg.getLoggerName().contains(logConfig.getFilterString());
		case TEXT:
			return msg.getText().contains(logConfig.getFilterString());
		default:
			return true;
		}
	}
	
	public void redraw() {
		for (final Iterator<LogMessage> iterator = logMessages.iterator(); iterator.hasNext();) {
			final LogMessage msg = iterator.next();
			if (shouldOutput(msg)) {
				msg.toConsoleOutput(terminal, logConfig);
			}
		}
	}
	
	public void dumpToFile(final String fileName) {
		final Path path = Paths.get(fileName);
		
		if (path.toFile().exists()) {
			final String line = terminal.readLine("Override? (yes/no)> ");
			if ( ! line.equalsIgnoreCase("yes")) {
				return;
			}
		}
		
		int entries = 0;
		final long start = System.currentTimeMillis();
		try (final FileOutputStream fos = new FileOutputStream(path.toFile())) {
			for (final Iterator<LogMessage> iterator = logMessages.iterator(); iterator.hasNext();) {
				final LogMessage msg = iterator.next();
				if (shouldOutput(msg)) {
					msg.toLogOutput(fos, logConfig);
					++entries;
				}
			}
			terminal.printInfo("wrote " + entries + " log entries in " + (System.currentTimeMillis() - start) + "ms");
		} catch (final IOException e) {
			terminal.printError("Could not dump log " + e.getMessage());
		}
	}

	@Override
	public void run() {
		try (final ObjectInputStream ois = new ObjectInputStream(is)) {
			boolean connected = true;
			while (connected) {
				try {
					final Object object = ois.readObject();
					if (object instanceof LogEvent event) {
						final LogMessage msg = new LogMessage(event);
						if (logMessages.size() > MAX_MESSAGES) {
							logMessages.removeFirst();
						}
						logMessages.add(msg);
						if (updateConsole && shouldOutput(msg)) {
							msg.toConsoleOutput(terminal, logConfig);
						}
					} else if (object instanceof DeviceListResponse devListResponse) {
						final List<Device> devices = new ArrayList<>(devListResponse.getDeviceList());
						devices.sort(new DeviceComparator());
						for (final Device device : devices) {
							if (device.isConnected()) {
								terminal.printInfo(device.toString());
							} else {
								terminal.printWarning(device.toString());
							}
						}
					} else if (object instanceof ConsoleRequest consoleRequest) {
						final String response = terminal.readLine(consoleRequest.getRequest() + '>');
						final ConsoleResponse consoleResponse = new ConsoleResponse();
						consoleResponse.setResponse(response);
						os.writeObject(consoleResponse);
						os.reset();
					} else if (object instanceof SwitchToNonInteractiveRequest) {
						terminal.setInteractive(false);
						terminal.switchReader();
					} else {
						terminal.printError("Unhandled object " + object.getClass().getName());
					}
				} catch (final IOException | ClassNotFoundException e) {
					terminal.printError("Connection lost " + e.getMessage());
					connected = false;
					break;
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		terminal.printWarning("Output runner stopped");
		terminal.setInteractive(false);
	}
	
	static class DeviceComparator implements Comparator<Device> {
		@Override
		public int compare(final Device device1, final Device device2) {
			return Integer.compare(device1.getId(), device2.getId());
		}
	}
}
