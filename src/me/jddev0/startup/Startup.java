package me.jddev0.startup;

import java.io.File;
import java.util.Map;

import me.jddev0.module.graphics.LangShellWindow;
import me.jddev0.module.graphics.TerminalWindow;
import me.jddev0.module.io.Lang;
import me.jddev0.module.io.LangPlatformAPI;
import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;

public class Startup {
	public static void main(String[] args) {
		LangPlatformAPI langPlatformAPI = new LangPlatformAPI();
		TerminalWindow termWin = new TerminalWindow();
		TerminalIO term = new TerminalIO(new File("assets/log.txt"));
		term.addCommand("executeLang", input -> {
			if(input.length == 1) {
				File lang = new File(input[0]);
				if(lang.exists()) {
					try {
						term.logln(Level.DEBUG, "------------- Start of Lang --------------", Startup.class);
						Map<String, String> translations = Lang.getTranslationMap(input[0], true, term, langPlatformAPI);
						term.logln(Level.DEBUG, "-------------- Translations --------------", Startup.class);
						translations.forEach((key, value) -> {
							term.logln(Level.DEBUG, key + " = " + value, Startup.class);
						});
						term.logln(Level.DEBUG, "-------------- End of Lang ---------------", Startup.class);
					}catch(Exception e) {
						term.logStackTrace(e, Startup.class);
					}
				}else {
					term.logf(Level.ERROR, "The lang file %s wasn't found!\n", Startup.class, input[0]);
				}
			}else {
				term.logf(Level.ERROR, "To many arguments: %d/1!\n", Startup.class, input.length);
			}
		}).addCommand("startShell", input -> {
			if(input.length == 0) {
				LangShellWindow langShellWin = new LangShellWindow(termWin, term);
				langShellWin.setVisible(true);
			}else {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
			}
		}).addCommand("exit", input -> {
			if(input.length == 0) {
				System.exit(0);
			}else {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
			}
		}).addCommand("commands", input -> {
			if(input.length == 0) {
				String tmp = "\nCommands: {\n";
				for(String str:term.getCommands().keySet()) {
					tmp += "     " + str + "\n";
				}
				tmp += "}";
				
				term.logln(Level.INFO, tmp, Startup.class);
			}else {
				term.logf(Level.ERROR, "To many arguments: %d/0!\n", Startup.class, input.length);
			}
		});
		
		termWin.setTerminalIO(term);
		termWin.setVisible(true);
		
		if(args.length == 1) {
			if(args[0].equals("-startShell")) {
				LangShellWindow langShellWin = new LangShellWindow(termWin, term);
				langShellWin.setVisible(true);
			}else {
				term.getCommands().get("executeLang").action(args);
			}
		}
	}
}