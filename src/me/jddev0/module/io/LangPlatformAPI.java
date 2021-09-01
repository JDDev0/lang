package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

/**
 * IO-Module<br>
 * Platform dependent code (e.g. Android would use a different LangPlatformAPI class)
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class LangPlatformAPI {
	public LangPlatformAPI() {}
	
	//File methods
	public List<String> getLangFiles(String langPath) {
		List<String> files = new LinkedList<>();
		
		String[] in = new File(langPath).list();
		if(in != null) {
			for(String str:in) {
				File f = new File(langPath, str);
				if(!f.isDirectory() && f.getName().toLowerCase().endsWith(".lang")) {
					files.add(f.getPath());
				}
			}
		}
		
		return files;
	}
	
	public String getLangPath(String langFile) {
		langFile = new File(langFile).getAbsolutePath();
		return langFile.substring(0, langFile.lastIndexOf(File.separator)); //Remove ending ("/*.lang") for $LANG_PATH
	}
	
	public BufferedReader getLangReader(String langFile) throws IOException {
		return new BufferedReader(new FileReader(new File(langFile)));
	}
	
	public boolean writeLangFile(File langFile, Map<String, String> translationMap, TerminalIO term) {
		try {
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(langFile), StandardCharsets.UTF_8));
			
			for(String str:translationMap.keySet()) {
				String value = translationMap.get(str);
				//For multiline
				value = value.replace("\n", "\\n");
				
				w.write(str + " = " + value);
				w.newLine();
			}
			
			w.close();
		}catch (IOException e) {
			term.logStackTrace(e, Lang.class);
			
			return false;
		}
		
		return true;
	}
	
	//Compiler methods
	public String showInputDialog(String text) throws Exception {
		String input = JOptionPane.showInputDialog(null, text, "Lang input", JOptionPane.PLAIN_MESSAGE);
		
		if(input == null)
			return "";
		else
			return input;
	}
}