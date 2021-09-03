package me.jddev0.startup;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import me.jddev0.module.graphics.LangShellWindow;
import me.jddev0.module.graphics.TerminalWindow;
import me.jddev0.module.io.Lang;
import me.jddev0.module.io.LangParser;
import me.jddev0.module.io.LangPlatformAPI;
import me.jddev0.module.io.ReaderActionObject;
import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;

public class Startup {
	private static boolean is4k;
	private static LangPlatformAPI langPlatformAPI = new LangPlatformAPI();
	
	public static void main(String[] args) {
		if(args.length > 0 && (!args[0].startsWith("-") || args[0].startsWith("--") || args[0].startsWith("-h"))) {
			if(args[0].startsWith("-h")) {
				printHelp();
				return;
			}
			
			if(args[0].startsWith("--")) {
				if(!args[0].equals("--help"))
					System.err.printf("Unknown COMMAND \"%s\"\n", args[0]);
				
				printHelp();
				return;
			}
			
			String langFile = args[0];
			boolean printTranslations = false;
			
			for(int i = 1;i < args.length;i++) {
				String arg = args[i];
				if(arg.equals("-printTranslations")) {
					printTranslations = true;
				}else {
					System.err.printf("Unknown EXECUTION_ARG \"%s\"\n", arg);
					
					printHelp();
					return;
				}
			}
			
			executeLangFile(langFile, printTranslations);
			return;
		}
		
		//Check if main monitor has a screen size larger than 1440p
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		is4k = dim.height > 1440;
		
		TerminalWindow termWin = new TerminalWindow(getFontSize());
		TerminalIO term = new TerminalIO(new File("assets/log.txt"));
		term.addCommand("executeLang", input -> {
			if(input.length != 1) {
				term.logf(Level.ERROR, "To many arguments: %d/1!\n", Startup.class, input.length);
				
				return;
			}
			
			File lang = new File(input[0]);
			if(!lang.exists()) {
				term.logf(Level.ERROR, "The lang file %s wasn't found!\n", Startup.class, input[0]);
				
				return;
			}
			
			try {
				term.logln(Level.DEBUG, "------------- Start of Lang --------------", Startup.class);
				Map<String, String> translations = Lang.getTranslationMap(input[0], true, term, langPlatformAPI);
				term.logln(Level.DEBUG, "-------------- Translations --------------", Startup.class);
				translations.forEach((key, value) -> {
					term.logln(Level.DEBUG, key + " = " + value, Startup.class);
				});
				term.logln(Level.DEBUG, "-------------- End of Lang ---------------", Startup.class);
			}catch(IOException e) {
				term.logStackTrace(e, Startup.class);
			}
		}).addCommand("printAST", input -> {
			if(input.length != 1) {
				term.logf(Level.ERROR, "To many arguments: %d/1!\n", Startup.class, input.length);
				
				return;
			}
			
			File lang = new File(input[0]);
			if(!lang.exists()) {
				term.logf(Level.ERROR, "The lang file %s wasn't found!\n", Startup.class, input[0]);
				
				return;
			}
			
			try {
				System.out.println(new LangParser().parseLines(new BufferedReader(new FileReader(lang))));
			}catch(IOException e) {
				term.logStackTrace(e, Startup.class);
			}
		}).addCommand("startShell", input -> {
			if(input.length != 0) {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
				
				return;
			}
			
			LangShellWindow langShellWin = new LangShellWindow(termWin, term, getFontSize());
			langShellWin.setVisible(true);
		}).addCommand("toggle4k", input -> {
			if(input.length != 0) {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
				
				return;
			}
			
			is4k = !is4k;
			termWin.setFontSize(getFontSize());
		}).addCommand("printHelp", input -> {
			printHelp();
		}).addCommand("exit", input -> {
			if(input.length != 0) {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
				
				return;
			}
			
			System.exit(0);
		}).addCommand("commands", input -> {
			if(input.length != 0) {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
				
				return;
			}
			
			String tmp = "\nCommands: {\n";
			for(String str:term.getCommands().keySet()) {
				tmp += "     " + str + "\n";
			}
			tmp += "}";
			
			term.logln(Level.INFO, tmp, Startup.class);
		});
		
		termWin.setTerminalIO(term);
		termWin.setVisible(true);
		
		if(args.length > 0) {
			if(args[0].length() < 2) {
				System.err.printf("Unknown COMMAND \"%s\"\n", args[0]);
				
				printHelp();
				
				System.exit(0);
				return;
			}
			String command = args[0].substring(1);
			
			String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
			ReaderActionObject commandFunction = term.getCommands().get(command);
			if(commandFunction == null) {
				System.err.printf("Unknown COMMAND \"%s\"\n", args[0]);
				
				printHelp();
				
				System.exit(0);
				return;
			}
			commandFunction.action(commandArgs);
		}
	}
	
	private static int getFontSize() {
		return is4k?24:12;
	}
	
	private static void printHelp() {
		System.out.println("Usage: lang COMMAND [ARGs]... | lang FILE [EXECUTION_ARGs]...");
		System.out.println("Interprets a Lang file");
		System.out.println();
		System.out.println("COMMANDs");
		System.out.println("--------");
		System.out.println("    -executeLang -FILE      Executes a Lang file in the \"TermIO-Control\" window");
		System.out.println("    -printAST -FILE         Prints an AST of a Lang file to standard output");
		System.out.println("    -startShell             Opens the \"LangShell\" window");
		System.out.println("    -toogle4k               Changes the fontSize");
		System.out.println("    -printHelp              Prints this help page");
		System.out.println("    -exit                   Exits the \"TermIO-Control\" window");
		System.out.println("    -commands               Lists all \"TermIO-Control\" window commands");
		System.out.println("");
		System.out.println("    -h, --help              Prints this help page");
		System.out.println();
		System.out.println("EXECUTION_ARGs");
		System.out.println("--------------");
		System.out.println("    -printTranslations      Prints all Translations after execution of Lang file finished to standard output");
	}
	
	private static void executeLangFile(String langFile, boolean printTranslations) {
		File lang = new File(langFile);
		if(!lang.exists()) {
			System.err.printf("The lang file %s wasn't found!\n", langFile);
			
			return;
		}
		
		try {
			Map<String, String> translations = Lang.getTranslationMap(langFile, true, null, langPlatformAPI);
			if(printTranslations) {
				System.out.println("-------------- Translations --------------");
				translations.forEach((key, value) -> System.out.printf("%s = %s\n", key, value));
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
}