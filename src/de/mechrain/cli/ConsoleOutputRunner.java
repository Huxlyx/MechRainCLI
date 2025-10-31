package de.mechrain.cli;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.lang3.StringUtils;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.Language;
import org.apache.fory.exception.DeserializationException;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import de.mechrain.cmdline.beans.AddSinkRequest;
import de.mechrain.cmdline.beans.AddTaskRequest;
import de.mechrain.cmdline.beans.ConfigDeviceRequest;
import de.mechrain.cmdline.beans.ConsoleRequest;
import de.mechrain.cmdline.beans.ConsoleResponse;
import de.mechrain.cmdline.beans.DeviceListRequest;
import de.mechrain.cmdline.beans.DeviceListResponse;
import de.mechrain.cmdline.beans.DeviceListResponse.DeviceData;
import de.mechrain.cmdline.beans.DeviceResetRequest;
import de.mechrain.cmdline.beans.SetDescriptionRequest;
import de.mechrain.cmdline.beans.SetIdRequest;
import de.mechrain.cmdline.beans.SwitchToNonInteractiveRequest;
import de.mechrain.log.LogEvent;

public class ConsoleOutputRunner implements Runnable {
	
	private static final int MAX_MESSAGES = 10_000;
	
	private final InputStream is;
	private final DataOutputStream dos;
	private final MechRainTerminal terminal;
	private final LogConfig logConfig;

	private final ThreadSafeFory fory;
	
	private final Deque<LogMessage> logMessages = new ConcurrentLinkedDeque<>();
	
	private boolean updateConsole = true;
	
	public ConsoleOutputRunner(final InputStream is, final OutputStream os, final MechRainTerminal terminal, final LogConfig logConfig) throws IOException {
		this.is = is;
		this.dos = new DataOutputStream(os);
		this.terminal = terminal;
		this.logConfig = logConfig;
		this.fory = Fory.builder()
				.withLanguage(Language.JAVA)
				.requireClassRegistration(false)
				.buildThreadSafeFory();
		fory.register(AddSinkRequest.class);
		fory.register(AddTaskRequest.class);
		fory.register(SetIdRequest.class);
		fory.register(SetDescriptionRequest.class);
		fory.register(DeviceResetRequest.class);
		fory.register(ConsoleRequest.class);
		fory.register(ConsoleResponse.class);
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
			final byte[] data = fory.serialize(DeviceListRequest.INSTANCE);
			dos.writeInt(data.length);
			dos.write(data);
		} catch (final IOException e) {
			terminal.printError("Could not send device list request. " + e.getMessage());
		}
	}

	public void configDevice(final String id) {
		try {
			final int deviceId = Integer.parseInt(id);
			final ConfigDeviceRequest request = new ConfigDeviceRequest();
			request.setDeviceId(deviceId);
			final byte[] data = fory.serialize(request);
			dos.writeInt(data.length);
			dos.write(data);
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
			final byte[] data = fory.serialize(request);
			dos.writeInt(data.length);
			dos.write(data);
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add sink request. " + e.getMessage());
		}
	}
	
	public void addTask() {
		try {
			final AddTaskRequest request = new AddTaskRequest();
			final byte[] data = fory.serialize(request);
			dos.writeInt(data.length);
			dos.write(data);
			terminal.setInteractive(true);
		} catch (final IOException e) {
			terminal.printError("Could not send add task request. " + e.getMessage());
		}
	}

	public void setDeviceId(int id) {
		try {
			final SetIdRequest request = new SetIdRequest(id);
			final byte[] data = fory.serialize(request);
			dos.writeInt(data.length);
			dos.write(data);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}
	
	public void setDeviceDescription(final String description) {
		try {
			final SetDescriptionRequest request = new SetDescriptionRequest(description);
			final byte[] data = fory.serialize(request);
			dos.writeInt(data.length);
			dos.write(data);
		} catch (final IOException e) {
			terminal.printError("Could not send set task request. " + e.getMessage());
		}
	}
	
	public void resetDevice() {
		try {
			final DeviceResetRequest request = new DeviceResetRequest();
			final byte[] data = fory.serialize(request);
			dos.writeInt(data.length);
			dos.write(data);
		} catch (final IOException e) {
			terminal.printError("Could not reset device. " + e.getMessage());
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
		try (final DataInputStream dis = new DataInputStream(is)) {
			boolean connected = true;
			while (connected) {
				try {
					final int len = dis.readInt();
					final byte[] data = new byte[len];
					dis.readFully(data);
					final Object object = fory.deserialize(data);
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
						final List<DeviceData> devices = new ArrayList<>(devListResponse.getDeviceList());
						devices.sort(new DeviceDataComparator());
						final AttributedStringBuilder deviceTable = new AttributedStringBuilder();
						deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE));
						deviceTable.append(StringUtils.center("Device", 10)).append('|').append(StringUtils.center("Description", 40)).append('|').append(StringUtils.center("Status", 15)).append('\n');
						deviceTable.append(StringUtils.repeat('-', 66)).append('\n');
						for (final DeviceData device : devices) {
							final String description = device.getDescription();
							if (device.isConnected()) {
								deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
								deviceTable.append(StringUtils.rightPad("Device " + device.getId(), 10)).append('|');
								deviceTable.append(StringUtils.rightPad(description != null ? description : " ", 40)).append('|');
								deviceTable.append(StringUtils.center("connected", 15)).append('\n');
							} else {
								deviceTable.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
								deviceTable.append(StringUtils.rightPad("Device " + device.getId(), 10)).append('|');
								deviceTable.append(StringUtils.rightPad(description != null ? description : " ", 40)).append('|');
								deviceTable.append(StringUtils.center("disconnected", 15)).append('\n');;
							}
						}
						terminal.printAbove(deviceTable);
					} else if (object instanceof ConsoleRequest consoleRequest) {
						final String response = terminal.readLine(consoleRequest.getRequest() + '>');
						final ConsoleResponse consoleResponse = new ConsoleResponse();
						consoleResponse.setResponse(response);
						final byte[] outData = fory.serialize(consoleResponse);
						dos.writeInt(outData.length);
						dos.write(outData);
					} else if (object instanceof SwitchToNonInteractiveRequest) {
						terminal.setInteractive(false);
						terminal.switchReader();
					} else {
						terminal.printError("Unhandled object " + object.getClass().getName());
					}
				} catch (final DeserializationException e) {
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
	
	static class DeviceDataComparator implements Comparator<DeviceData> {
		@Override
		public int compare(final DeviceData device1, final DeviceData device2) {
			return Integer.compare(device1.getId(), device2.getId());
		}
	}
}
