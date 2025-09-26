package de.mechrain.cli;

import static org.jline.builtins.Completers.TreeCompleter.node;

import java.io.IOException;

import org.fusesource.jansi.AnsiConsole;
import org.jline.builtins.Completers.TreeCompleter;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

public class MechRainTerminal {
	
	public static final String CLEAR = "clear";
	public static final String DUMP = "dump";
	public static final String FILTER = "filter";
	public static final String RECONNECT = "reconnect";
	public static final String SHOW = "show";
	public static final String SET = "set";

	private final Completer generalCompleter = new TreeCompleter(
			node(CLEAR,
					node("buffer")),
			node(DUMP),
			node(FILTER,
					node("logName"),
					node("text"),
					node("off")),
			node(RECONNECT),
			node(SHOW,
					node("buffer"),
					node("devices"),
					node("diagram")),
			node(SET,
					node("level", 
							node("off", "err", "warn", "info", "debug", "trace")),
					node("time", 
							node("off", "on")),
					node("logName", 
							node("off", "on"))),
			node("switch")
		);

	private final Completer testCompleter = new TreeCompleter(
			node("foo",
					node("foo1")),
			node("bar"),
			node("baz",
					node("baz1"),
					node("baz2"),
					node("baz3"))
		);
	
	private final Terminal terminal;
	private final LineReader generalReader;
	private final LineReader testReader;
	
	private LineReader activeReader;
	
	public MechRainTerminal() throws IOException {
		AnsiConsole.systemInstall();
		this.terminal = TerminalBuilder.builder()
				.system(true).provider("jni")
				.build();
		this.generalReader = LineReaderBuilder.builder()
				.terminal(terminal)
				.completer(generalCompleter)
				.build();
		this.testReader = LineReaderBuilder.builder()
				.terminal(terminal)
				.completer(testCompleter)
				.build();
		
		this.activeReader = generalReader;
	}

	public void printHeader() {
		terminal.writer().println();
		terminal.writer().println();
		terminal.puts(Capability.set_a_foreground, 4);
		terminal.writer().println(" ░███     ░███                       ░██        ░█████████             ░██           ");
		terminal.writer().println(" ░████   ░████                       ░██        ░██     ░██                          ");
		terminal.writer().println(" ░██░██ ░██░██  ░███████   ░███████  ░████████  ░██     ░██  ░██████   ░██░████████  ");
		terminal.writer().println(" ░██ ░████ ░██ ░██    ░██ ░██    ░██ ░██    ░██ ░█████████        ░██  ░██░██    ░██ ");
		terminal.writer().println(" ░██  ░██  ░██ ░█████████ ░██        ░██    ░██ ░██   ░██    ░███████  ░██░██    ░██ ");
		terminal.writer().println(" ░██       ░██ ░██        ░██    ░██ ░██    ░██ ░██    ░██  ░██   ░██  ░██░██    ░██ ");
		terminal.writer().println(" ░██       ░██  ░███████   ░███████  ░██    ░██ ░██     ░██  ░█████░██ ░██░██    ░██ ");
		terminal.writer().println();
		terminal.writer().println();
	    terminal.puts(Capability.orig_pair);
	}

	public void clear() {
		terminal.puts(Capability.clear_screen);
		terminal.puts(Capability.clr_eol);
		terminal.puts(Capability.cursor_home);
		
		terminal.writer().println("cleared");
		terminal.flush();
	}
	
	public void switchReader() {
		if (activeReader == generalReader) {
			activeReader = testReader;
		} else {
			activeReader = generalReader;
		}
	}
	
	public String readLine(final String prompt) {
		return activeReader.readLine(prompt);
	}
	
	public void printError(final String error) {
		printAbove(AttributedStyle.BOLD, AttributedStyle.RED, error);
	}
	
	public void printWarning(final String warning) {
		printAbove(AttributedStyle.BOLD, AttributedStyle.YELLOW, warning);
	}
	
	public void printInfo(final String info) {
		printAbove(AttributedStyle.DEFAULT, AttributedStyle.GREEN, info);
	}
	
	public void printDebug(final String debug) {
		printAbove(AttributedStyle.DEFAULT, AttributedStyle.CYAN, debug);
	}
	
	public void printTrace(final String trace) {
		printAbove(AttributedStyle.DEFAULT, AttributedStyle.BLUE, trace);
	}
	
	private void printAbove(final AttributedStyle style, final int color, final String text) {
		final AttributedStringBuilder asb = new AttributedStringBuilder();
		asb.style(style.foreground(color));
		asb.append(text);
		asb.style(AttributedStyle.DEFAULT);
		activeReader.printAbove(asb.toAnsi(terminal));
	}
	
	public void printAbove(final AttributedStringBuilder asb) {
		activeReader.printAbove(asb.toAnsi(terminal));
	}
	
	public void write(final String msg) {
		terminal.writer().write(msg);
	}

}
