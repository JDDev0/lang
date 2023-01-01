package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import me.jddev0.module.io.TerminalIO;

/**
 * Lang-Module<br>
 * Platform dependent code
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public interface ILangPlatformAPI {
	/**
	 * @param langPath Path to the folder
	 * @return Return all files inside the folder located at langPath
	 */
	List<String> getLangFiles(String langPath);
	
	/**
	 * @param langFile Path to the file
	 * @return Return the canonical path of the file located at langFile
	 */
	String getLangPath(String langFile);
	
	/**
	 * @param langFile Path to the file
	 * @return Return the file name of the file located at langFile
	 */
	String getLangFileName(String langFile);
	
	/**
	 * @param langFile Path to the file
	 * @return Return a reader for the file
	 * @throws IOException
	 */
	BufferedReader getLangReader(String langFile) throws IOException;
	
	/**
	 * @param langFile Path to the file
	 * @return Return an input stream for the file
	 * @throws IOException
	 */
	InputStream getInputStream(String langFile) throws IOException;
	
	/**
	 * @param langFile Path to the file
	 * @param translationMap The Map of all translations
	 * @param term A TerminalIO instance for error logging
	 * @return Return true if successful else false (Return false if not implemented)
	 */
	boolean writeLangFile(File langFile, Map<String, String> translationMap, TerminalIO term);
	
	//Function helper methods
	
	/**
	 * @param text The text prompt to be shown to the user
	 * @return Return the value inputed by the user
	 * @throws Exception Throw any exception if not implemented or if any other error occurred
	 */
	String showInputDialog(String text) throws Exception;
}