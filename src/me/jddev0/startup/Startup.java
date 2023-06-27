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
import me.jddev0.module.io.ReaderActionObject;
import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.DataObject;
import me.jddev0.module.lang.Lang;
import me.jddev0.module.lang.LangInterpreter;
import me.jddev0.module.lang.LangInterpreter.LangInterpreterInterface;
import me.jddev0.platform.desktop.swing.LangPlatformAPI;
import me.jddev0.module.lang.LangParser;
import me.jddev0.module.lang.ILangPlatformAPI;

public class Startup {
	private static boolean is4k;
	private static final ILangPlatformAPI langPlatformAPI = new LangPlatformAPI();
	
	public static void main(String[] args) {
		if(args.length > 0 && (!args[0].startsWith("-") || args[0].equals("-e") || args[0].startsWith("--") || args[0].startsWith("-h"))) {
			if(args[0].startsWith("-h")) {
				printHelp();
				
				return;
			}
			
			if(args[0].startsWith("--")) {
				if(!args[0].equals("--help"))
					System.err.printf("Unknown COMMAND \"%s\"\n", args[0]);
				
				printHelp();
				
				if(!args[0].equals("--help"))
					System.exit(1);
				
				return;
			}
			
			boolean langFileExecution = !args[0].equals("-e");
			if(!langFileExecution && args.length < 2) {
				System.err.println("CODE argument for \"-e\" is missing");
				
				printHelp();
				
				System.exit(1);
				return;
			}
			
			int executionArgsStartIndex = langFileExecution?1:2;
			boolean printTranslations = false;
			boolean printReturnedValue = false;
			boolean warnings = false;
			String[] langArgs = null;
			
			for(int i = executionArgsStartIndex;i < args.length;i++) {
				String arg = args[i];
				if(arg.equals("-printTranslations")) {
					printTranslations = true;
				}else if(arg.equals("-printReturnedValue")) {
					printReturnedValue = true;
				}else if(arg.equals("-warnings")) {
					warnings = true;
				}else if(arg.equals("-langArgs") || arg.equals("--")) {
					langArgs = Arrays.copyOfRange(args, i + 1, args.length);
					break;
				}else {
					System.err.printf("Unknown EXECUTION_ARG \"%s\"\n", arg);
					
					printHelp();
					
					System.exit(1);
					return;
				}
			}
			
			if(langFileExecution)
				executeLangFile(args[0], printTranslations, printReturnedValue, warnings, langArgs);
			else
				executeLangCode(args[1], printTranslations, printReturnedValue, warnings, langArgs);
			
			return;
		}
		
		//Check if main monitor has a screen size larger than 1440p
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		is4k = dim.height > 1440;
		
		boolean log = false;
		if(args.length > 0 && args[0].equals("-log")) {
			args = Arrays.copyOfRange(args, 1, args.length);
			
			log = true;
		}
		
		TerminalWindow termWin = new TerminalWindow(getFontSize());
		TerminalIO term = new TerminalIO(log?new File("log.txt"):null);
		term.addCommand("executeLang", input -> {
			if(input.length < 1) {
				term.logf(Level.ERROR, "To few arguments: %d/1+!\n", Startup.class, input.length);
				
				return;
			}
			
			boolean warnings = input[0].equals("warnings");
			if(warnings && input.length < 2) {
				term.logf(Level.ERROR, "To few arguments for -warnings option: %d/2+!\n", Startup.class, input.length);
				
				return;
			}
			LangInterpreter.ExecutionFlags.ErrorOutputFlag errorOutput = warnings?LangInterpreter.ExecutionFlags.ErrorOutputFlag.ALL:null;
			
			int langFileIndex = warnings?1:0;
			File lang = new File(input[langFileIndex]);
			if(!lang.exists()) {
				term.logf(Level.ERROR, "The Lang file %s wasn't found!\n", Startup.class, input[0]);
				
				return;
			}
			
			String[] langArgs = Arrays.copyOfRange(input, langFileIndex + 1, input.length);
			try {
				term.logln(Level.DEBUG, "------------- Start of Lang --------------", Startup.class);
				LangInterpreterInterface lii = Lang.createInterpreterInterface(input[langFileIndex], false, term, langPlatformAPI, errorOutput, langArgs);
				Map<String, String> translations = lii.getTranslationMap(0);
				term.logln(Level.DEBUG, "-------------- Translations --------------", Startup.class);
				translations.forEach((key, value) -> {
					term.logln(Level.DEBUG, key + " = " + value, Startup.class);
				});
				boolean isThrowValue = lii.isReturnedValueThrowValue();
				DataObject retValue = lii.getAndResetReturnValue();
				if(isThrowValue) {
					term.logln(Level.DEBUG, "------------- Throwed value --------------", Startup.class);
					term.logf(Level.DEBUG, "Error code: \"%d\"\nError message: \"%s\"\n", Startup.class, retValue.getError().getErrno(), retValue.getError().getErrtxt());
				}else {
					term.logln(Level.DEBUG, "------------- Returned Value -------------", Startup.class);
					if(retValue == null)
						term.logln(Level.DEBUG, "No returned value", Startup.class);
					else
						term.logf(Level.DEBUG, "Returned Value: \"%s\"\n", Startup.class, retValue.getText());
				}
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
				term.logf(Level.ERROR, "The Lang file %s wasn't found!\n", Startup.class, input[0]);
				
				return;
			}
			
			try(BufferedReader reader = new BufferedReader(new FileReader(lang))) {
				System.out.println(new LangParser().parseLines(reader));
			}catch(IOException e) {
				term.logStackTrace(e, Startup.class);
			}
		}).addCommand("startShell", input -> {
			LangShellWindow langShellWin = new LangShellWindow(termWin, term, getFontSize(), input);
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
		}).addCommand("clear", input -> {
			termWin.clearOutput();
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
				
				System.exit(1);
				return;
			}
			String command = args[0].substring(1);
			
			String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
			ReaderActionObject commandFunction = term.getCommands().get(command);
			if(commandFunction == null) {
				System.err.printf("Unknown COMMAND \"%s\"\n", args[0]);
				
				printHelp();
				
				System.exit(1);
				return;
			}
			commandFunction.action(commandArgs);
		}
	}
	
	private static int getFontSize() {
		return is4k?24:12;
	}
	
	private static void printHelp() {
		System.out.println("Standard Lang version v1.0.0-dev");
		System.out.println("================================");
		System.out.println("Interprets Lang code & files");
		System.out.println();
		System.out.println("Usage: lang COMMAND [ARGs]... | lang -log COMMAND [ARGs]... | lang -e CODE [EXECUTION_ARGs]... [LANG_ARGs]... | lang FILE [EXECUTION_ARGs]... [LANG_ARGs]...");
		System.out.println();
		System.out.println("COMMANDs");
		System.out.println("--------");
		System.out.println("    -executeLang -FILE                Executes a Lang file in the \"TermIO-Control\" window");
		System.out.println("    -executeLang -warnings -FILE      Executes a Lang file in the \"TermIO-Control\" window with warnings output");
		System.out.println("    -printAST -FILE                   Prints the AST of a Lang file to standard output");
		System.out.println("    -startShell                       Opens the \"LangShell\" (REPL) window");
		System.out.println("    -toogle4k                         Changes the fontSize");
		System.out.println("    -printHelp                        Prints this help page");
		System.out.println("    -clear                            Clears the output of the \"TermIO-Control\" window");
		System.out.println("    -exit                             Exits the \"TermIO-Control\" window");
		System.out.println("    -commands                         Lists all \"TermIO-Control\" window commands");
		System.out.println("");
		System.out.println("    -log                              Enables log file output");
		System.out.println("");
		System.out.println("    -h, --help                        Prints this help page");
		System.out.println();
		System.out.println("IN-LINE CODE");
		System.out.println("------------");
		System.out.println("    -e CODE                           Executes CODE without the Lang Terminal directly in the OS shell");
		System.out.println();
		System.out.println("EXECUTION_ARGs");
		System.out.println("--------------");
		System.out.println("    -printTranslations                Prints all Translations after the execution of the Lang file finished to standard output");
		System.out.println("    -printReturnedValue               Prints the returned or thrown value of the Lang file if any");
		System.out.println("    -warnings                         Enables the output of warnings which occur");
		System.out.println("    -langArgs                         Indicates the start of the Lang args arguments (Everything after this argument will be interpreted as Lang args)");
		System.out.println("    --                                Alias for \"-langArgs\"");
	}
	
	private static void executeLangCode(String langCode, boolean printTranslations, boolean printReturnedValue, boolean warnings, String[] langArgs) {
		try {
			LangInterpreterInterface lii = Lang.createInterpreterInterface(null, langPlatformAPI, langArgs);
			if(warnings)
				lii.setErrorOutputFlag(LangInterpreter.ExecutionFlags.ErrorOutputFlag.ALL);
			
			lii.exec(0, langCode);
			printPostExecutionOutput(lii, printTranslations, printReturnedValue);
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void executeLangFile(String langFile, boolean printTranslations, boolean printReturnedValue, boolean warnings, String[] langArgs) {
		File lang = new File(langFile);
		if(!lang.exists()) {
			System.err.printf("The Lang file %s wasn't found!\n", langFile);
			
			return;
		}
		
		LangInterpreter.ExecutionFlags.ErrorOutputFlag errorOutput = warnings?LangInterpreter.ExecutionFlags.ErrorOutputFlag.ALL:null;
		try {
			LangInterpreterInterface lii = Lang.createInterpreterInterface(langFile, false, null, langPlatformAPI, errorOutput, langArgs);
			printPostExecutionOutput(lii, printTranslations, printReturnedValue);
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void printPostExecutionOutput(LangInterpreterInterface lii, boolean printTranslations, boolean printReturnedValue) {
		Map<String, String> translations = lii.getTranslationMap(0);
		if(printTranslations) {
			System.out.println("-------------- Translations --------------");
			translations.forEach((key, value) -> System.out.printf("%s = %s\n", key, value));
		}
		if(printReturnedValue) {
			boolean isThrowValue = lii.isReturnedValueThrowValue();
			DataObject retValue = lii.getAndResetReturnValue();
			if(isThrowValue) {
				System.out.println("------------- Throwed value --------------");
				System.out.printf("Error code: \"%d\"\nError message: \"%s\"\n", retValue.getError().getErrno(), retValue.getError().getErrtxt());
			}else {
				System.out.println("------------- Returned Value -------------");
				if(retValue == null)
					System.out.println("No returned value");
				else
					System.out.printf("Returned Value: \"%s\"\n", retValue.getText());
			}
		}
	}
}