package de.mechrain.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.spi.StandardLevel;

public class LogMessage {

	private final StandardLevel level;
	private final long timestamp;
	private final String text;
	private final String loggerName;
	
	public LogMessage(final LogEvent logEvent) {
		this.level = StandardLevel.getStandardLevel(logEvent.getLevel().intLevel());
		this.timestamp = logEvent.getTimeMillis();
		this.text = logEvent.getMessage().getFormattedMessage();
		this.loggerName = logEvent.getLoggerName();
	}

	public StandardLevel getLevel() {
		return level;
	}
	
	public String getLoggerName() {
		return loggerName;
	}
	
	public String getText() {
		return text;
	}

	public void toConsoleOutput(final MechRainTerminal terminal, final LogConfig config) {
        final StringBuilder sb = new StringBuilder(text.length() + 20);
        if (config.isShowTime()) {
        	sb.append(new Date(timestamp).toInstant().atZone(config.getZoneId()).format(config.getTimeFormatter()).toString()).append(' ');
        }
        if (config.isShowLoggerName()) {
        	sb.append(loggerName).append(' ');
        }
        
        final String msg = sb.append(text).toString();
        
		switch (level) {
			case ERROR:
			case FATAL:
				terminal.printError(msg);
				break;
			case WARN:
				terminal.printWarning(msg);
				break;
			case INFO:
				terminal.printInfo(msg);
				break;
			case DEBUG:
				terminal.printDebug(msg);
				break;
			case TRACE:
				terminal.printTrace(msg);
				break;
			default:
				break;
		}
	}
	
	public void toLogOutput(final OutputStream os, final LogConfig config) throws IOException {
		switch (level) {
		case ERROR:
		case FATAL:
			os.write("[ERR] ".getBytes(StandardCharsets.ISO_8859_1));
			break;
		case WARN:
			os.write("[WRN] ".getBytes(StandardCharsets.ISO_8859_1));
			break;
		case INFO:
			os.write("[INF] ".getBytes(StandardCharsets.ISO_8859_1));
			break;
		case DEBUG:
			os.write("[DBG] ".getBytes(StandardCharsets.ISO_8859_1));
			break;
		case TRACE:
			os.write("[TRC] ".getBytes(StandardCharsets.ISO_8859_1));
			break;
		default:
			os.write("[???] ".getBytes(StandardCharsets.ISO_8859_1));
			break;
		
		}
		
		if (config.isShowTime()) {
			os.write((new Date(timestamp).toInstant().atZone(config.getZoneId()).format(config.getTimeFormatter()).toString() + ' ').getBytes(StandardCharsets.ISO_8859_1));
		}
		if (config.isShowLoggerName()) {
			os.write((loggerName + ' ').getBytes(StandardCharsets.ISO_8859_1));
		}
		os.write(text.getBytes(StandardCharsets.ISO_8859_1));
		os.write("\n".getBytes(StandardCharsets.ISO_8859_1));
	}

}
