package me.jddev0.module.io;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * IO-Module<br>
 * Write Log-Files in <b>UTF-8</b>
 * 
 * @author JDDev0
 * @version v0.1
 */
public class TerminalIO {
	//Map for saving commands
	private Map<String, ReaderActionObject> actions = new HashMap<String, ReaderActionObject>();
	private SimpleDateFormat form = new SimpleDateFormat("dd.MM.yyyy|HH:mm:ss");
	private BufferedWriter writer;
	private Scanner s;
	//Standard level: all levels will be logged
	private int lvl = -1;
	
	/**
	 * @param logFile File for logging
	 */
	public TerminalIO(File logFile) {
		this(logFile, true);
	}
	/**
	 * @param logFile File for logging
	 */
	public TerminalIO(File logFile, boolean enableCommandInput) {
		try {
			//Writer for logFile
			if(logFile != null)
				writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile), "UTF-8"));
		}catch(Exception e) {
			this.logStackTrace(e, TerminalIO.class);
		}
		
		logln(Level.INFO, "TermIO started!", TerminalIO.class);
		
		if(enableCommandInput) {
			//Thread for reading System.in and execute commands
			Thread t = new Thread(() -> {
				s = new Scanner(System.in);
				
				while(true) {
					String[] strs = s.nextLine().split(" -");
					
					if(actions.get(strs[0]) != null) {
						String[] args = new String[strs.length-1];
						for(int i = 1;i < strs.length;i++) {
							args[i-1] = strs[i];
						}
						
						actions.get(strs[0]).action(args);
					}else {
						logln(Level.ERROR, "Command \"" + strs[0] + "\" was not found!", TerminalIO.class);
					}
				}
			});
			t.setName("TerminalIO read");
			t.start();
		}
	}
	
	/**
	 * @param command Name of the command
	 * @param action Action of the command
	 * @return this
	 */
	public TerminalIO addCommand(String command, ReaderActionObject action) {
		actions.put(command, action);
		
		return this;
	}
	
	/**
	 * @return Returns all commands that are registered.
	 */
	public Map<String, ReaderActionObject> getCommands() {
		return actions;
	}
	
	/**
	 * @return Returns the current level.
	 */
	public int getLevel() {
		return lvl;
	}
	
	/**
	 * @param lvl Any messages except user level messages with level lower than lvl will be discarded.
	 * @return this
	 */
	public TerminalIO setLevel(Level lvl) {
		this.lvl = lvl.getLevel();
		
		return this;
	}
	
	/**
	 * @param lvl Any messages except user level messages with level lower than lvl will be discarded.
	 * @return this
	 */
	public TerminalIO setLevel(int lvl) {
		this.lvl = lvl;
		
		return this;
	}
	
	/**
	 * @param lvl Checks if level of lvl is ok.
	 * @return Returns true for message level that are ok otherwise false.
	 */
	private boolean checkLevel(Level lvl) {
		return Level.USER == lvl || lvl.getLevel() >= this.lvl;
	}
	
	/**
	 * @param lvl The level of the message
	 * @param txt The message
	 * @param caller The caller class
	 * @param newLine Auto new Line
	 */
	private void log(Level lvl, String txt, Class<?> caller, boolean newLine) {
		//Checks if lvl is ok
		if(!checkLevel(lvl))
			return;
		
		//Creates Message in the form of "[level][date][callerClass] message"
		String log = "";
		log += "[" + lvl.getLevelName() + "]";
		log += "[" + form.format(new Date()) + "]";
		log += "[Msg len: " + ((txt == null?4:txt.length()) + (newLine?1:0)) + "]";
		log += "[From: " + caller.getName() + "]: ";
		log += txt;
		log += newLine?"\n":""; //Adds a new line if newLine is true
		
		//Prints the log on the standard output
		System.out.print(log);
		
		if(writer != null) {
			try {
				//Writes in the log in logFile
				writer.write(log);
				writer.flush();
			}catch(IOException e) {
				//Doesn't use the logStackTrace method to avoid a stack overflow
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Logs with format without new line
	 * 
	 * @param lvl The level of the message
	 * @param format The format
	 * @param caller The caller class
	 * @param args The arguments for the format
	 */
	public void logf(Level lvl, String format, Class<?> caller, Object... args) {
		log(lvl, String.format(format, args), caller);
	}
	
	/**
	 * Logs without new Line
	 * 
	 * @param lvl The level of the message
	 * @param txt The message
	 * @param caller The caller class
	 */
	public void log(Level lvl, String txt, Class<?> caller) {
		log(lvl, txt, caller, false);
	}
	
	/**
	 * Logs with new Line
	 * 
	 * @param lvl The level of the message
	 * @param txt The message
	 * @param caller The caller class
	 */
	public void logln(Level lvl, String txt, Class<?> caller) {
		log(lvl, txt, caller, true);
	}
	
	/**
	 * Logs stackTrace of Throwable
	 * If thrown is an error, the log level is Level.CRITICAL.
	 * If thrown is an exception, the log level is Level.ERROR.
	 * 
	 * @param thrown The throwable to log
	 * @param caller The caller class
	 */
	public void logStackTrace(Throwable thrown, Class<?> caller) {
		if(thrown instanceof Error)
			log(Level.CRITICAL, "", caller);
		else if(thrown instanceof Exception)
			log(Level.ERROR, "", caller);
		
		thrown.printStackTrace(System.out);
		if(writer != null)
			thrown.printStackTrace(new PrintWriter(writer));
	}
	
	/**
	 * Closes the logger
	 */
	public void close() {
		if(writer != null) {
			try {
				writer.close();
			}catch(IOException e) {
				logStackTrace(e, TerminalIO.class);
			}
		}
	}
	
	/**
	 * Level enum for TerminalIO
	 * 
	 * @author JDDev0
	 * @version v0.1
	 */
	public static enum Level {
		//Levels
		NOTSET(-1, "Not Set"), USER(0, "User"), DEBUG(1, "Debug"), CONFIG(2, "Config"), INFO(3, "Info"), WARNING(4, "Warning"),
		ERROR(5, "Error"), CRITICAL(6, "Critical");
		
		private final int lvl;
		private final String name;
		
		private Level(int lvl, String name) {
			this.lvl = lvl;
			this.name = name;
		}
		
		//Returns level ID
		public int getLevel() {
			return lvl;
		}
		
		//Returns level name for logs
		public String getLevelName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getLevelName();
		}
		
		public boolean equals(String str) {
			return this.toString().equals(str);
		}
	}
}