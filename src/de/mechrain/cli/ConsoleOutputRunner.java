package de.mechrain.cli;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.logging.log4j.core.LogEvent;

public class ConsoleOutputRunner implements Runnable {
	
	private static final int MAX_MESSAGES = 10_000;
	
	private final InputStream is;
	private final MechRainTerminal terminal;
	private final LogConfig logConfig;
	
	private final Deque<LogMessage> logMessages = new ConcurrentLinkedDeque<>();
	
	private boolean updateConsole = true;
	
	public ConsoleOutputRunner(final InputStream is, final MechRainTerminal terminal, final LogConfig logConfig) {
		this.is = is;
		this.terminal = terminal;
		this.logConfig = logConfig;
	}

	public void setUpdateConsole(boolean updateConsole) {
		this.updateConsole = updateConsole;
	}
	
	public void showBuffer() {
		final int logMsgCount = logMessages.size();
		terminal.printInfo(logMsgCount + "/" + MAX_MESSAGES + " " + (((float)logMsgCount / MAX_MESSAGES) * 100) + "%");
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
					} else {
						terminal.printError("Not a log event! " + object.getClass().getName());
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
	}

}
