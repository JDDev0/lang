package me.jddev0.startup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import me.jddev0.module.graphics.LangShellWindow;
import me.jddev0.module.graphics.TerminalWindow;
import me.jddev0.module.io.Lang;
import me.jddev0.module.io.LangParser;
import me.jddev0.module.io.LangPlatformAPI;
import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;

public class Startup {
	private static boolean is4k = false;
	
	private static int getFontSize() {
		return is4k?24:12;
	}
	
	public static void main(String[] args) {
		LangPlatformAPI langPlatformAPI = new LangPlatformAPI();
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
		
		if(args.length == 1) {
			if(args[0].equals("-startShell")) {
				LangShellWindow langShellWin = new LangShellWindow(termWin, term, getFontSize());
				langShellWin.setVisible(true);
			}else {
				term.getCommands().get("executeLang").action(args);
			}
		}
	}
}