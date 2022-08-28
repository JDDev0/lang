package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import me.jddev0.module.io.TerminalIO;

/**
 * Lang-Module<br>
 * Read and write Lang-Files in <b>UTF-8</b>
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class Lang {
	//Lang translation cache
	/**
	 * Content: translation key = translation value
	 */
	private final static Map<String, String> LANG_CACHE = new HashMap<>();
	/**
	 * The name of the lang file which was cached previously
	 */
	private static String lastCachedLangFileName;
	
	private Lang() {}
	
	/**
	 * @return Returns the <b>translationValue</b> formatted with {@link String#format(String, Object...) String.format(String, Object...)}<br>
	 * The translationKey will be returned if translationValue = null
	 */
	private static String formatTranslation(String translationKey, String translationValue, Object... args) {
		if(translationValue == null)
			return translationKey;
		
		try {
			return String.format(translationValue, args);
		}catch(Exception e) {
			return translationValue;
		}
	}
	
	/**
	 * @return Returns the <b>translationValue</b> formatted with {@link LangUtils#formatTranslationTemplate(String, Map) LangUtils.formatTranslationTemplate(String, Map)}<br>
	 * The translationKey will be returned if translationValue = null
	 */
	private static String formatTemplateTranslation(String translationKey, String translationValue, Map<String, String> templateMap) {
		if(translationValue == null)
			return translationKey;
		
		try {
			return LangUtils.formatTranslationTemplate(translationValue, templateMap);
		}catch(Exception e) {
			return translationValue;
		}
	}
	
	/**
	 * @return Returns the <b>translationValue</b> formatted with
	 * {@link LangUtils#formatTranslationTemplatePluralization(String, int) LangUtils.formatTranslationTemplatePluralization(String, int)}<br>
	 * The translationKey will be returned if translationValue = null
	 */
	private static String formatPluralizationTemplateTranslation(String translationKey, String translationValue, int count) {
		if(translationValue == null)
			return translationKey;
		
		try {
			return LangUtils.formatTranslationTemplatePluralization(translationValue, count);
		}catch(Exception e) {
			return translationValue;
		}
	}
	
	/**
	 * @return Returns the <b>translationValue</b> formatted with
	 * {@link LangUtils#formatTranslationTemplatePluralization(String, int, Map) LangUtils.formatTranslationTemplatePluralization(String, int, Map)}<br>
	 * The translationKey will be returned if translationValue = null
	 */
	private static String formatPluralizationTemplateTranslation(String translationKey, String translationValue, int count, Map<String, String> templateMap) {
		if(translationValue == null)
			return translationKey;
		
		try {
			return LangUtils.formatTranslationTemplatePluralization(translationValue, count, templateMap);
		}catch(Exception e) {
			return translationValue;
		}
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> of <b>langFile</b><br>
	 * Null will be returned if the translationKey does not exist
	 */
	private static String getRawTranslation(String langFile, String translationKey, LangPlatformAPI langPlatformAPI) throws IOException {
		return getTranslationMap(langFile, false, null, langPlatformAPI).get(translationKey);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> from the lang translation cache<br>
	 * Null will be returned if the translationKey does not exist
	 */
	private static String getCachedRawTranslation(String translationKey) {
		return getCachedTranslationMap().get(translationKey);
	}
	
	/**
	 * @return Returns all available lang files in the folder <b>langPath</b>
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
			if(langFile.equals(lastCachedLangFileName) && !reloadNotFromChache)
				return new HashMap<>(LANG_CACHE);
			
			clearCache();
			
			try(BufferedReader reader = langPlatformAPI.getLangReader(langFile)) {
				//Cache lang translations
				reader.lines().filter(line -> line.contains(" = ")).forEach(line -> {
					String[] translation = line.split(" = ", 2);
					LANG_CACHE.put(translation[0], translation[1].replace("\\n", "\n"));
				});
			}
			lastCachedLangFileName = langFile;
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
			if(langFile.equals(lastCachedLangFileName) && !reloadNotFromChache)
				return new HashMap<>(LANG_CACHE);
			
			clearCache();
			
			//Set path for Interpreter
			String pathLangFile = langPlatformAPI.getLangPath(langFile);
			
			//Create new Interpreter instance
			LangInterpreter interpreter = new LangInterpreter(pathLangFile, langPlatformAPI.getLangFileName(langFile), term, langPlatformAPI, langArgs);
			
			try(BufferedReader reader = langPlatformAPI.getLangReader(langFile)) {
				interpreter.interpretLines(reader);
			}
			
			//Cache lang translations
			LANG_CACHE.putAll(interpreter.getData().get(0).lang);
			lastCachedLangFileName = langFile;
			return new HashMap<>(LANG_CACHE);
		}
	}
	
	/**
	 * Call getCached... methods afterwards for retrieving certain lang translations
	 * @return Returns all translations of <b>langFile</b> with a timeout
	 * @param timeout Will cancel the execution of the lang file after timeout milliseconds
	 * @throws StoppedException if the execution of the lang file was force stopped
	 */
	public static Map<String, String> getTranslationMapTimeout(String langFile, boolean reloadNotFromChache, int timeout, TerminalIO term, LangPlatformAPI langPlatformAPI)
	throws IOException, LangInterpreter.StoppedException {
		return getTranslationMapTimeout(langFile, reloadNotFromChache, timeout, term, langPlatformAPI, null);
	}

	/**
	 * Call getCached... methods afterwards for retrieving certain lang translations
	 * @return Returns all translations of <b>langFile</b> with a timeout
	 * @param timeout Will cancel the execution of the lang file after timeout milliseconds
	 * @throws StoppedException if the execution of the lang file was force stopped
	 */
	public static Map<String, String> getTranslationMapTimeout(String langFile, boolean reloadNotFromChache, int timeout, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs)
	throws IOException, LangInterpreter.StoppedException {
		synchronized(LANG_CACHE) {
			if(langFile.equals(lastCachedLangFileName) && !reloadNotFromChache)
				return new HashMap<>(LANG_CACHE);
			
			clearCache();
			
			//Set path for Interpreter
			String pathLangFile = langPlatformAPI.getLangPath(langFile);
			
			//Create new Interpreter instance
			LangInterpreter interpreter = new LangInterpreter(pathLangFile, langPlatformAPI.getLangFileName(langFile), term, langPlatformAPI, langArgs);
			
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					interpreter.forceStop();
				}
			}, timeout);
			try(BufferedReader reader = langPlatformAPI.getLangReader(langFile)) {
				interpreter.interpretLines(reader);
				timer.cancel();
			}
			
			//Cache lang translations
			LANG_CACHE.putAll(interpreter.getData().get(0).lang);
			lastCachedLangFileName = langFile;
			return new HashMap<>(LANG_CACHE);
		}
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> of <b>langFile</b><br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getTranslation(String langFile, String translationKey, LangPlatformAPI langPlatformAPI) throws IOException {
		String translationValue = getRawTranslation(langFile, translationKey, langPlatformAPI);
		return translationValue == null?translationKey:translationValue;
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> of <b>langFile</b> formatted with {@link String#format(String, Object...) String.format(String, Object...)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getTranslationFormat(String langFile, String translationKey, LangPlatformAPI langPlatformAPI, Object... args) throws IOException {
		return formatTranslation(translationKey, getRawTranslation(langFile, translationKey, langPlatformAPI), args);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> of <b>langFile</b> formatted with
	 * {@link LangUtils#formatTranslationTemplate(String, Map) LangUtils.formatTranslationTemplate(String, Map)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getTranslationTemplate(String langFile, String translationKey, LangPlatformAPI langPlatformAPI, Map<String, String> templateMap) throws IOException {
		return formatTemplateTranslation(translationKey, getRawTranslation(langFile, translationKey, langPlatformAPI), templateMap);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> of <b>langFile</b> formatted with
	 * {@link LangUtils#formatTranslationTemplatePluralization(String, int) LangUtils.formatTranslationTemplatePluralization(String, int)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getTranslationTemplatePluralization(String langFile, String translationKey, LangPlatformAPI langPlatformAPI, int count) throws IOException {
		return formatPluralizationTemplateTranslation(translationKey, getRawTranslation(langFile, translationKey, langPlatformAPI), count);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> of <b>langFile</b> formatted with
	 * {@link LangUtils#formatTranslationTemplatePluralization(String, int, Map) LangUtils.formatTranslationTemplatePluralization(String, int, Map)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getTranslationTemplatePluralization(String langFile, String translationKey, LangPlatformAPI langPlatformAPI, int count, Map<String, String> templateMap) throws IOException {
		return formatPluralizationTemplateTranslation(translationKey, getRawTranslation(langFile, translationKey, langPlatformAPI), count, templateMap);
	}
	
	/**
	 * @return Returns the lang file's of <b>langFile</b><br>
	 */
	public static String getLangName(String langFile, LangPlatformAPI langPlatformAPI) throws IOException {
		return getTranslation(langFile, "lang.name", langPlatformAPI);
	}
	
	/**
	 * @return Returns the lang file's of <b>langFile</b><br>
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
			clearCache();
			
			return langPlatformAPI.writeLangFile(langFile, translationMap, term);
		}
	}
	
	/**
	 * @return Returns all translations from the cache
	 */
	public static Map<String, String> getCachedTranslationMap() {
		synchronized(LANG_CACHE) {
			return new HashMap<>(LANG_CACHE);
		}
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> from the lang translation cache<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getCachedTranslation(String translationKey) {
		String translationValue = getCachedRawTranslation(translationKey);
		return translationValue == null?translationKey:translationValue;
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> from the lang translation cache formatted with {@link String#format(String, Object...) String.format(String, Object...)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getCachedTranslationFormat(String translationKey, Object... args) {
		return formatTranslation(translationKey, getCachedRawTranslation(translationKey), args);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> from the lang translation cache formatted with
	 * {@link LangUtils#formatTranslationTemplate(String, Map) LangUtils.formatTranslationTemplate(String, Map)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getCachedTranslationTemplate(String translationKey, Map<String, String> templateMap) {
		return formatTemplateTranslation(translationKey, getCachedRawTranslation(translationKey), templateMap);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> from the lang translation cache formatted with
	 * {@link LangUtils#formatTranslationTemplatePluralization(String, int) LangUtils.formatTranslationTemplatePluralization(String, int)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getCachedTranslationTemplatePluralization(String translationKey, int count) {
		return formatPluralizationTemplateTranslation(translationKey, getCachedRawTranslation(translationKey), count);
	}
	
	/**
	 * @return Returns the <b>translationValue</b> for <b>translationKey</b> from the lang translation cache formatted with
	 * {@link LangUtils#formatTranslationTemplatePluralization(String, int, Map) LangUtils.formatTranslationTemplatePluralization(String, int, Map)}<br>
	 * The translation key will be returned if the translationKey does not exist
	 */
	public static String getCachedTranslationTemplatePluralization(String translationKey, int count, Map<String, String> templateMap) {
		return formatPluralizationTemplateTranslation(translationKey, getCachedRawTranslation(translationKey), count, templateMap);
	}
	
	/**
	 * @return Returns the lang file's name from the lang translation cache<br>
	 */
	public static String getCachedLangName() {
		return getCachedTranslation("lang.name");
	}
	
	/**
	 * @return Returns the lang file's version from the lang translation cache<br>
	 */
	public static String getCachedLangVersion() {
		return getCachedTranslation("lang.version");
	}
	
	/**
	 * Writes all translations from the lang translation cache to <b>langFile</b>
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
	
	/**
	 * Will execute the lang file
	 * @return The LII for the lang interpreter which was used to execute the lang file
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, boolean writeToCache,
	TerminalIO term, LangPlatformAPI langPlatformAPI, LangInterpreter.ExecutionFlags.ErrorOutputFlag errorOutput, String[] langArgs) throws IOException {
		if(writeToCache)
			clearCache();
		
		String pathLangFile = langPlatformAPI.getLangPath(langFile);
		
		LangInterpreter interpreter = new LangInterpreter(pathLangFile, langPlatformAPI.getLangFileName(langFile), term, langPlatformAPI, langArgs);
		if(errorOutput != null)
			interpreter.executionFlags.errorOutput = errorOutput;
		
		try(BufferedReader reader = langPlatformAPI.getLangReader(langFile)) {
			interpreter.interpretLines(reader);
		}
		
		if(writeToCache) {
			synchronized(LANG_CACHE) {
				LANG_CACHE.clear();
				LANG_CACHE.putAll(interpreter.getData().get(0).lang);
				lastCachedLangFileName = langFile;
			}
		}
		
		return new LangInterpreter.LangInterpreterInterface(interpreter);
	}
	/**
	 * Will execute the lang file without warnings output
	 * @return The LII for the lang interpreter which was used to execute the lang file
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, boolean writeToCache,
	TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) throws IOException {
		return createInterpreterInterface(langFile, writeToCache, term, langPlatformAPI, null, langArgs);
	}
	/**
	 * Will execute the lang file without warnings output and lang args
	 * @return The LII for the lang interpreter which was used to execute the lang file
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, boolean writeToCache, TerminalIO term, LangPlatformAPI langPlatformAPI) throws IOException {
		return createInterpreterInterface(langFile, writeToCache, term, langPlatformAPI, null);
	}
	/**
	 * Will execute the lang file without warnings output and the use of the lang translation cache
	 * @return The LII for the lang interpreter which was used to execute the lang file
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) throws IOException {
		return createInterpreterInterface(langFile, false, term, langPlatformAPI, langArgs);
	}
	/**
	 * Will execute the lang file without warnings output, lang args, and the use of the lang translation cache
	 * @return The LII for the lang interpreter which was used to execute the lang file
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI) throws IOException {
		return createInterpreterInterface(langFile, false, term, langPlatformAPI);
	}
	/**
	 * Will create a new lang interpreter without warnings output and the use of the lang translation cache
	 * @return The LII for the lang interpreter which was created
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) {
		return new LangInterpreter.LangInterpreterInterface(new LangInterpreter(new File("").getAbsolutePath(), term, langPlatformAPI, langArgs));
	}
	/**
	 * Will create a new lang interpreter without warnings output, lang args, and the use of the lang translation cache
	 * @return The LII for the lang interpreter which was created
	 */
	public static LangInterpreter.LangInterpreterInterface createInterpreterInterface(TerminalIO term, LangPlatformAPI langPlatformAPI) {
		return createInterpreterInterface(term, langPlatformAPI, null);
	}
}