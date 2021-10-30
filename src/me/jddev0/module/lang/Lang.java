package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.jddev0.module.io.TerminalIO;

/**
 * Lang-Module<br>
 * Read and write Lang-Files in <b>UTF-8</b>
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class Lang {
	//Lang cache
	private final static Map<String, String> LANG_CACHE = new HashMap<>(); //translation key = translation value
	private static String lastCachedLangFileName;
	
	private Lang() {}
	
	/**
	 * @return Returns all available lang files
	 */
	public static List<String> getLangFiles(String langPath, LangPlatformAPI langPlatformAPI) {
		return langPlatformAPI.getLangFiles(langPath);
	}
	
	/**
	 * Without interpreter: Only lang translations will be read without any other features (Used for reading written lang file)<br>
	 * Call getCached... methods afterwards for retrieving certain lang translations
	 * @return Returns all translations of <b>langFile</b>
	 */
	public static Map<String, String> getTranslationMapWithoutInterpreter(String langFile, boolean reloadNotFromChache, TerminalIO term, LangPlatformAPI langPlatformAPI) throws IOException {
		synchronized(LANG_CACHE) {
			if(langFile.equals(lastCachedLangFileName) && !reloadNotFromChache) {
				return new HashMap<>(LANG_CACHE);
			}else {
				LANG_CACHE.clear();
				lastCachedLangFileName = langFile;
			}
			
			BufferedReader reader = langPlatformAPI.getLangReader(langFile);
			//Cache lang translations
			reader.lines().forEach(line -> {
				if(!line.contains(" = "))
					return;
				
				String[] langTranslation = line.split(" = ", 2);
				LANG_CACHE.put(langTranslation[0], langTranslation[1].replace("\\n", "\n"));
			});
			reader.close();
			return new HashMap<>(LANG_CACHE);
		}
	}
	
	/**
	 * @return Returns all translations of <b>langFile</b>
	 */
	public static Map<String, String> getTranslationMap(String langFile, boolean reloadNotFromChache, TerminalIO term, LangPlatformAPI langPlatformAPI) throws IOException {
		return getTranslationMap(langFile, reloadNotFromChache, term, langPlatformAPI, null);
	}

	/**
	 * @return Returns all translations of <b>langFile</b>
	 */
	public static Map<String, String> getTranslationMap(String langFile, boolean reloadNotFromChache, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) throws IOException {
		synchronized(LANG_CACHE) {
			if(langFile.equals(lastCachedLangFileName) && !reloadNotFromChache) {
				return new HashMap<>(LANG_CACHE);
			}else {
				LANG_CACHE.clear();
				lastCachedLangFileName = langFile;
			}
			
			//Set path for Interpreter
			String pathLangFile = langPlatformAPI.getLangPath(langFile);
			
			//Create new Interpreter instance
			LangInterpreter interpreter = new LangInterpreter(pathLangFile, term, langPlatformAPI, langArgs);
			
			BufferedReader reader = langPlatformAPI.getLangReader(langFile);
			try {
				interpreter.interpretLines(reader);
			}catch(IOException e) {
				reader.close();
				
				throw e;
			}
			reader.close();
			
			//Cache lang translations
			LANG_CACHE.putAll(interpreter.getData().get(0).lang);
			return new HashMap<>(LANG_CACHE);
		}
	}
	
	/**
	 * @return Returns translation <b>key</b> of <b>langFile</b><br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	public static String getTranslation(String langFile, String key, LangPlatformAPI langPlatformAPI) throws IOException {
		synchronized(LANG_CACHE) {
			if(getTranslationMap(langFile, false, null, langPlatformAPI).get(key) == null)
				return key;
			
			return getTranslationMap(langFile, false, null, langPlatformAPI).get(key);
		}
	}
	
	/**
	 * @return Returns translation <b>key</b> of <b>langFile</b><br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	public static String getTranslationFormat(String langFile, String key, LangPlatformAPI langPlatformAPI, Object... args) throws IOException {
		synchronized(LANG_CACHE) {
			if(getTranslation(langFile, key, langPlatformAPI) == null)
				return key;
			
			try {
				return String.format(getTranslation(langFile, key, langPlatformAPI), args);
			}catch(Exception e) {
				return getTranslation(langFile, key, langPlatformAPI);
			}
		}
	}
	
	/**
	 * @return Returns language name of <b>langFile</b><br>
	 */
	public static String getLangName(String langFile, LangPlatformAPI langPlatformAPI) throws IOException {
		return getTranslation(langFile, "lang.name", langPlatformAPI);
	}
	
	/**
	 * @return Returns language version of <b>langFile</b><br>
	 */
	public static String getLangVersion(String langFile, LangPlatformAPI langPlatformAPI) throws IOException {
		return getTranslation(langFile, "lang.version", langPlatformAPI);
	}
	
	/**
	 * Writes all translations of <b>translationMap</b> to <b>langFile</b>
	 * 
	 * @return Returns true if successful, false otherwise
	 */
	public static boolean write(File langFile, Map<String, String> translationMap, TerminalIO term, LangPlatformAPI langPlatformAPI) {
		synchronized(LANG_CACHE) {
			LANG_CACHE.clear();
			
			return langPlatformAPI.writeLangFile(langFile, translationMap, term);
		}
	}
	
	/**
	 * @return Returns all translations from the cach
	 */
	public static Map<String, String> getCachedTranslationMap() {
		synchronized(LANG_CACHE) {
			return new HashMap<>(LANG_CACHE);
		}
	}
	
	/**
	 * @return Returns translation <b>key</b> from the cache<br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	public static String getCachedTranslation(String key) {
		synchronized(LANG_CACHE) {
			if(getCachedTranslationMap().get(key) == null)
				return key;
			
			return getCachedTranslationMap().get(key);
		}
	}
	
	/**
	 * @return Returns translation <b>key</b> from the cache<br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	public static String getCachedTranslationFormat(String key, Object... args) {
		synchronized(LANG_CACHE) {
			if(getCachedTranslation(key) == null)
				return key;
			
			try {
				return String.format(getCachedTranslation(key), args);
			}catch(Exception e) {
				return getCachedTranslation(key);
			}
		}
	}
	
	/**
	 * @return Returns language name from cache<br>
	 */
	public static String getCachedLangName() {
		return getCachedTranslation("lang.name");
	}
	
	/**
	 * @return Returns language version from cache<br>
	 */
	public static String getCachedLangVersion() {
		return getCachedTranslation("lang.version");
	}
	
	/**
	 * Writes all translations from cache to <b>langFile</b>
	 * 
	 * @return Returns true if successful, false otherwise
	 */
	public static boolean writeCache(File langFile, TerminalIO term, LangPlatformAPI langPlatformAPI) {
		return langPlatformAPI.writeLangFile(langFile, getCachedTranslationMap(), term);
	}
	
	/**
	 * Clears the lang translation cache
	 */
	public static void clearCache() {
		synchronized(LANG_CACHE) {
			LANG_CACHE.clear();
			lastCachedLangFileName = null;
		}
	}
	
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, boolean writeToCache,
	TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) throws IOException {
		if(writeToCache) {
			synchronized(LANG_CACHE) {
				LANG_CACHE.clear();
				lastCachedLangFileName = langFile;
			}
		}
		
		String pathLangFile = langPlatformAPI.getLangPath(langFile);
		
		LangInterpreter interpreter = new LangInterpreter(pathLangFile, term, langPlatformAPI, langArgs);
		
		BufferedReader reader = langPlatformAPI.getLangReader(langFile);
		try {
			interpreter.interpretLines(reader);
		}catch(IOException e) {
			reader.close();
			
			throw e;
		}
		reader.close();
		
		if(writeToCache) {
			synchronized(LANG_CACHE) {
				//Cache lang translations
				LANG_CACHE.putAll(interpreter.getData().get(0).lang);
			}
		}
		
		return new LangInterpreter.LangInterpreterInterface(interpreter);
	}
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, boolean writeToCache, TerminalIO term, LangPlatformAPI langPlatformAPI) throws IOException {
		return createInterpreterInterface(langFile, writeToCache, term, langPlatformAPI, null);
	}
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) throws IOException {
		return createInterpreterInterface(langFile, false, term, langPlatformAPI, langArgs);
	}
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI) throws IOException {
		return createInterpreterInterface(langFile, false, term, langPlatformAPI);
	}
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) {
		return new LangInterpreter.LangInterpreterInterface(new LangInterpreter(new File("").getAbsolutePath(), term, langPlatformAPI, langArgs));
	}
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(TerminalIO term, LangPlatformAPI langPlatformAPI) {
		return createInterpreterInterface(term, langPlatformAPI, null);
	}
}