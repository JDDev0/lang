package me.jddev0.platform.desktop.swing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import at.jddev0.io.TerminalIO;
import at.jddev0.lang.ILangPlatformAPI;

/**
 * Lang-Module<br>
 * Platform dependent code for Java Desktop with AWT
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class LangPlatformAPI implements ILangPlatformAPI {
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
		File containingFolder = new File(langFile).getParentFile();
		if(containingFolder == null)
			containingFolder = new File("./");
		try {
			return containingFolder.getCanonicalPath();
		}catch(IOException e) {
			return containingFolder.getAbsolutePath();
		}
	}
	public String getLangFileName(String langFile) {
		return new File(langFile).getName();
	}
	
	public BufferedReader getLangReader(String langFile) throws IOException {
		return new BufferedReader(new FileReader(new File(langFile)));
	}
	
	public InputStream getInputStream(String langFile) throws IOException {
		return new FileInputStream(new File(langFile));
	}
	
	public boolean writeLangFile(File langFile, Map<String, String> translationMap, TerminalIO term) {
		try {
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(langFile), StandardCharsets.UTF_8));
			
			for(String langRequest:translationMap.keySet()) {
				String value = translationMap.get(langRequest);
				//For multiline
				value = value.replace("\n", "\\n");
				
				w.write(langRequest + " = " + value);
				w.newLine();
			}
			
			w.close();
		}catch (IOException e) {
			term.logStackTrace(e, LangPlatformAPI.class);
			
			return false;
		}
		
		return true;
	}
	
	//Function helper methods
	public String showInputDialog(String text) throws Exception {
		String input = JOptionPane.showInputDialog(null, text, "Lang input", JOptionPane.PLAIN_MESSAGE);
		
		if(input == null)
			return "";
		else
			return input;
	}
}