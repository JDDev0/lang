package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import me.jddev0.module.lang.AbstractSyntaxTree;
import me.jddev0.module.lang.LangExternalFunctionObject;
import me.jddev0.module.lang.LangInterpreter;
import me.jddev0.module.lang.LangPlatformAPI;
import me.jddev0.module.lang.LangPredefinedFunctionObject;
import me.jddev0.module.lang.LangUtils;
import me.jddev0.module.lang.AbstractSyntaxTree.AssignmentNode;
import me.jddev0.module.lang.AbstractSyntaxTree.FunctionDefinitionNode;
import me.jddev0.module.lang.AbstractSyntaxTree.Node;
import me.jddev0.module.lang.AbstractSyntaxTree.NodeType;
import me.jddev0.module.lang.AbstractSyntaxTree.ParsingErrorNode;
import me.jddev0.module.lang.AbstractSyntaxTree.VariableNameNode;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangParser.ParsingError;

@Deprecated
/**
 * IO-Module<br>
 * Read and write Lang-Files in <b>UTF-8</b>
 * <br>
 * <br>
 * <b>--- Lang Syntax ---</b><br>
 * # Comment //Comment<br>
 * <br>
 * langRequest = langValue //Lang request<br>
 * <br>
 * $varName //New Var<br>
 * $varName = varVal //Set var<br>
 * %$varName = Something //langRequest with value of "$varName"<br>
 * <br>
 * func.funcName() //Empty Function<br>
 * func.funcName(args) //Not Empty Function<br>
 * <br>
 * func.arrayFunc(arrPtr, args) //Array Function<br>
 * <br>
 * <b>--- Escape Characters ---</b><br>
 * \0 -> ASCII NUL<br>
 * \n -> newLine<br>
 * \r -> carriage return<br>
 * \f -> form feed<br>
 * \s -> space<br>
 * \b -> backspace<br>
 * \t -> tab<br>
 * \$ -> $<br>
 * \& -> &<br>
 * \# -> #<br>
 * \( -> (<br>
 * \) -> )<br>
 * \{ -> {<br>
 * \} -> }<br>
 * \! -> forced node split<br>
 * \\ -> \<br>
 * <br>
 * <b>--- Lang data and compiler flags ---</b><br>
 * <b>Needed</b>:<br>
 * needed: *<br>
 * not needed: +<br>
 * <b>Type</b>:<br>
 * data: d<br>
 * flag: f<br>
 * <br>
 * (*, Type) lang.xxx = [DATA-TYPE] // No default value<br>
 * (+, Type) lang.xxx = [DATA-TYPE] {Default-Value}<br>
 * <br>
 * (*, d) lang.name = [Text] //Name of lang file<br>
 * (*, d) lang.version = [Text] //Version of lang file<br>
 * (+, f) lang.allowTermRedirect = [int] {1} //Allow redirection of terminal to standard input, output, or error if no terminal is available<br>
 * <br>
 * <b>--- Lang Types ---</b><br>
 * null //Null-Pointer<br>
 * <br>
 * Test //Text<br>
 * c //char (Text with length of 1)<br>
 * <br>
 * 42 //int, long, float, double<br>
 * 42.42 ; 42. ; .42 ; 42e2 ; 42e-2 ; 42E2 ; 42E-2 //float, double<br>
 * <br>
 * FILE //Text to location of file:<br>
 * //<b>important</b>: file separator ('/') will be changed by file separator of OS<br>
 * //absolute: (e.g.: /home/user/"File name")(e.g.: C:/"File name")<br>
 * //relative: (e.g.: "File name")(e.g.: ../"File name")(e.g.: "Folder name"/"File name")<br>
 * <br>
 * $varName //var<br>
 * $[varName] //varPtr (Pointer to var)<br>
 * $[[varName]] //Pointer to varPtr<br>
 * $[[[varName]]] //Pointer to pointer to varPtr<br>
 * ...<br>
 * <b>Manipulate varPtr</b><br>
 * e.g.: CODE START<br>
 * $a = 0<br>
 * $[a] = 1<br>
 * CODE END<br>
 * "$a" -> "0" -> "0"<br>
 * "$[a]" -> "1" -> "1"<br>
 * "$[[a]]" -> "$[a]" -> "1"<br>
 * "$[[[a]]]" -> "$[[a]]" -> "$[a]"<br>
 * <br>
 * e.g.: CODE START<br>
 * $b = 0<br>
 * $[[b]] = 1<br>
 * CODE END<br>
 * "$b" -> "0" -> "0"<br>
 * "$[b]" -> "$b" -> "0"<br>
 * "$[[b]]" -> "1" -> "1"<br>
 * "$[[[b]]]" -> "$[[b]]" -> "1"<br>
 * <br>
 * e.g.: CODE START<br>
 * $c = 0<br>
 * $[[c]] = 1<br>
 * $[[[c]]] = 2<br>
 * CODE END<br>
 * "$c" -> "0" -> "0"<br>
 * "$[c]" -> "$c" -> "0"<br>
 * "$[[c]]" -> "1" -> "1"<br>
 * "$[[[c]]]" -> "2" -> "2"<br>
 * <br>
 * &arrayName //arrPtr (Pointer to array)<br>
 * <br>
 * func.funcName(...) //func<br>
 * fp.funcName(...) //funcPtr (Pointer to own function (Doesn't work with "func.funcName(...)"))<br>
 * <br>
 * <b>--- FuncPtr Syntax ---</b><br>
 * <b>Call:</b><br>
 * fp.funcName() //No params<br>
 * fp.funcName(param1, param2) //Params<br>
 * <br>
 * <b>Copy:</b><br>
 * fp.funcName<br>
 * <br>
 * <b>Definition:</b><br>
 * fp.funcName = () -> { //Head<br>
 * "CODE" //Body<br>
 * } //End<br>
 * <br>
 * Empty funcPtr:<br>
 * CODE START<br>
 * fp.funcName = () -> {<br>
 * <br>
 * }<br>
 * CODE END<br>
 * <br>
 * Head:<br>
 * fp.funcName = () -> { //No params<br>
 * fp.funcName = (params) -> { //Params<br>
 * <br>
 * Params:<br>
 * $varName //var<br>
 * &arrName //arrPtr<br>
 * fp.funcName //funcPtr<br>
 * <br>
 * e.g.: fp.funcName = ($sizeNew, &arrTo, &arrFrom) -> { //", " for new param<br>
 * <br>
 * Body:<br>
 * return val //Return val and ends function<br>
 * return //Return nothing and ends function<br>
 * <br>
 * //"return" is not needed (After last function line end of the function, like "return" without val)<br>
 * <br>
 * <b>--- Lang Vars ---</b><br>
 * <b>Final vars (Not changeable)</b><br>
 * $LANG_COMPILER_VERSION //Version of the compiler as "Text"<br>
 * $LANG_PATH //Path to lang file as "Text" (No "FILE" -> file separator depends on OS)<br>
 * $LANG_RAND_MAX //Max value of random function as "int"<br>
 * <br>
 * <b>Not final vars (Changeable)</b><br>
 * $LANG_ERRNO //Number of last error (0 = no error)<br>
 * <br>
 * <b>--- Lang If ---</b><br>
 * con.if(CONDITION) //if CONDITION is true<br>
 * con.elif(CONDITION) //Not needed, else if<br>
 * con.else //Not needed, else<br>
 * con.endif //End of if<br>
 * <br>
 * <b>--- Lang CONDITIONS ---</b><br>
 * For all types:<br>
 * "$x == $y" -> $x is equal $y<br>
 * "$x != $y" -> $x is not equal $y<br>
 * <br>
 * For int, float, long, double:<br>
 * "$x < $y" -> $x is less than $y <br>
 * "$x <= $y" -> $x is less or equal $y <br>
 * "$x > $y" -> $x is greater than $y <br>
 * "$x >= $y" -> $x is greater or equal $y <br>
 * <br>
 * For conditions:<br>
 * "!($x)" -> not $x<br>
 * "($x) && ($y)" -> $x and $y<br>
 * "($x) || ($y)" -> $x or $y<br>
 * <br>
 * <b>--- For Linker/Functions !NO SYNTAX!---</b><br>
 * [return]func/linker.funcName(args)<br>
 * [return]func/linker.funcName(void) -> no args<br>
 * [void]func/linker.funcName(args) -> no return<br>
 * <br>
 * <b>VAR -> COUNT<b><br>
 * type -> 1<br>
 * type[x] -> x<br>
 * [type] -> 0 or 1<br>
 * (type) -> 0 to n<br>
 * <br>
 * <b>--- Lang Linker ---</b><br>
 * [any]linker.link(FILE)<br>
 * [any]linker.bindLibrary(FILE)<br>
 * <br>
 * <b>--- Lang Functions ---</b><br>
 * <b>Reset Functions</b><br>
 * [void]func.clearVar(varPtr)<br>
 * [void]func.clearVar(arrPtr)<br>
 * [void]func.clearVar(funcPtr)<br>
 * [void]func.clearAllVars(void)<br>
 * [void]func.clearAllArrays(void) //Deprecated [Will be removed in v1.2.0]: use func.clearAllVars instead<br>
 * <br><b>Error functions</b><br>
 * [Text]func.getErrorString(void) //Deprecated [Will be removed in v1.2.0]: use func.getErrorText instead<br>
 * [Text]func.getErrorText(void)<br>
 * <br><b>Compiler function</b><br>
 * [int]func.isCompilerVersionNewer(void)<br>
 * [int]func.isCompilerVersionOlder(void)<br>
 * <br><b>System Functions</b><br>
 * [void]func.sleep(int)<br>
 * [long]func.currentTimeMillis(void)<br>
 * [void]func.repeat(funcPtr, int)<br>
 * [void]func.repeatWhile(funcPtr, funcPtr) //Calls first function pointer while second function pointer returns true<br>
 * [void]func.repeatUntil(funcPtr, funcPtr) //Calls first function pointer while second function pointer returns false<br>
 * [Text]func.getLangRequest(Text)<br>
 * [void]func.makeFinal(varPtr)<br>
 * [void]func.makeFinal(arrPtr) //Content in final array can still be changed<br>
 * [void]func.makeFinal(funcPtr)<br>
 * [int]func.condition(CONDITION) //Returns 1 if the condition is true else 0<br>
 * [any]func.exec(TEXT) //Returns the value of an interpreted return statement if any<br>
 * [int]func.isTerminalAvailable(void) //Returns 1 if a terminal is available else 0<br>
 * <br><b>IO Functions</b><br>
 * [Text]func.readTerminal(Text)<br>
 * [void]func.printTerminal(int, Text)<br>
 * [void]func.printError([Text]) //"func.printTerminal" with "func.getErrorText"<br>
 * [Text]func.input([int]) //1st parameter: max Text length<br>
 * [void]func.print(Text)<br>
 * [void]func.println([Text])<br>
 * [void]func.error(Text)<br>
 * [void]func.errorln([Text])<br>
 * <br><b>Num Functions</b><br>
 * [int]func.hexToDec(Text) //Deprecated [Will be removed in v1.2.0]: use func.hexToDec instead<br>
 * [int]func.hexToDec(Text)<br>
 * <br><b>Character functions</b><br>
 * [int]func.toValue(char)<br>
 * [char]func.toChar(int)<br>
 * <br><b>String functions</b><br>
 * [int]func.strlen(Text)<br>
 * [Text]func.toUpper(Text)<br>
 * [Text]func.toLower(Text)<br>
 * [Text]func.trim(Text)<br>
 * [Text]func.replace(Text, Text, Text) //(Input, RegEx, Replacement)<br>
 * [Text]func.substring(Text, int, [int])<br>
 * [void]func.split(arrPtr, Text, Text, [int])<br>
 * <br><b>Math functions</b><br>
 * <i>Utility functions</i><br>
 * [int]func.rand(void)<br>
 * <br><i>Integer functions</i><br>
 * [int]func.addi(int, (int))<br>
 * [int]func.subi(int, int)<br>
 * [int]func.muli(int, (int))<br>
 * [int]func.divi(int, int)<br>
 * [int]func.modi(int, int)<br>
 * [int]func.andi(int, int)<br>
 * [int]func.ori(int, int)<br>
 * [int]func.xori(int, int)<br>
 * [int]func.noti(int)<br>
 * [int]func.lshifti(int, int)<br>
 * [int]func.rshifti(int, int)<br>
 * [int]func.rzshifti(int, int)<br>
 * <br><i>Long functions</i><br>
 * [long]func.addl(long, (long))<br>
 * [long]func.subl(long, long)<br>
 * [long]func.mull(long, (long))<br>
 * [long]func.divl(long, long)<br>
 * [long]func.modl(long, long)<br>
 * [long]func.orl(long, long)<br>
 * [long]func.xorl(long, long)<br>
 * [long]func.notl(long)<br>
 * [long]func.lshiftl(long, long)<br>
 * [long]func.rshiftl(long, long)<br>
 * [long]func.rzshiftl(long, long)<br>
 * <br><i>Double functions</i><br>
 * [double]func.addd(double, (double))<br>
 * [double]func.subd(double, double)<br>
 * [double]func.muld(double, (double))<br>
 * [double]func.divd(double, double)<br>
 * [double]func.pow(double, double)<br>
 * [double]func.sqrt(double)<br>
 * <br><i>Convert functions</i><br>
 * [int]func.dtoi(double)<br>
 * [long]func.dtol(double)<br>
 * [num]func.toNumber(any)<br>
 * <br><i>Round functions</i><br>
 * [long]func.ceil(double)<br>
 * [long]func.floor(double)<br>
 * <br><b>FuncPtr functions</b><br>
 * [void]func.copyAfterFP(varPtr, varPtr) //Caller.varPtr (1st arg) = FuncPtr.varPtr (2nd arg)<br>
 * [void]func.copyAfterFP(arrPtr, arrPtr) //"[void]func.copyAfterFP(varPtr, varPtr)" with arrPtrs<br>
 * [void]func.copyAfterFP(funcPtr, funcPtr) //"[void]func.copyAfterFP(varPtr, varPtr)" with funcPtrs<br>
 * <br><b>Array functions</b><br>
 * [void]func.arrayMake(arrPtr, int)<br>
 * [arrPtr]func.arrayMake(int)<br>
 * [void]func.arraySet(arrPtr, int, Text)<br>
 * [void]func.arraySetAll(arrPtr, Text)<br>
 * [void]func.arraySetAll(arrPtr, Text[len]) //len = length of arrPtr<br>
 * [Text]func.arrayGet(arrPtr, int)<br>
 * [Text]func.arrayGetAll(arrPtr)<br>
 * [int]func.arrayLength(arrPtr)<br>
 * [void]func.arrayForEach(arrPtr, funcPtr)<br>
 * [Text]func.randChoice(arrPtr)<br>
 * [Text]func.randChoice(Text, (Text))<br>
 * [void]func.arrayDelete(arrPtr) //Delete Array Values<br>
 * [void]func.arrayClear(arrPtr) //Free arrPtr<br>
 * 
 * @author JDDev0
 * @version v1.0.0
 * @deprecated Will be removed in v1.2.0
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
	 * Call getCached... methods afterwards for retrieving certain lang translation
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
	
	//DEPRACTED methods and classes
	/**
	 * @deprecated Will be removed in v1.2.0
	 * @return Returns all available lang files
	 */
	@Deprecated
	public static List<String> getLangFiles(String langPath) {
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
	/**
	 * @deprecated Will be removed in v1.2.0
	 * @return Returns all translations of <b>langFile</b>
	 */
	@Deprecated
	public static Map<String, String> getTranslationMap(String langFile, boolean reload, TerminalIO term) throws Exception {
		return getTranslationMap(langFile, reload, term, new LangPlatformAPI());
	}
	/**
	 * @deprecated Will be removed in v1.2.0
	 * @return Returns translation <b>key</b> of <b>langFile</b><br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	@Deprecated
	public static String getTranslation(String langFile, String key) throws Exception {
		synchronized(LANG_CACHE) {
			if(getTranslationMap(langFile, false, null).get(key) == null) {
				return key;
			}
			
			return getTranslationMap(langFile, false, null).get(key);
		}
	}
	/**
	 * @deprecated Will be removed in v1.2.0
	 * @return Returns translation <b>key</b> of <b>langFile</b><br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	@Deprecated
	public static String getTranslationFormat(String langFile, String key, Object... args) throws Exception {
		synchronized(LANG_CACHE) {
			if(getTranslation(langFile, key) == null) {
				return key;
			}
			
			try {
				return String.format(getTranslation(langFile, key), args);
			}catch(Exception e) {
				return getTranslation(langFile, key);
			}
		}
	}
	/**
	 * @deprecated Will be removed in v1.2.0
	 * @return Returns language name of <b>langFile</b><br>
	 * <code>return getTranslation(langFile, "lang.name");</code>
	 */
	@Deprecated
	public static String getLangName(String langFile) throws Exception {
		synchronized(LANG_CACHE) {
			return getTranslation(langFile, "lang.name");
		}
	}
	/**
	 * @deprecated Will be removed in v1.2.0
	 * @return Returns language version of <b>langFile</b><br>
	 * <code>return getTranslation(langFile, "lang.name");</code>
	 */
	@Deprecated
	public static String getLangVersion(String langFile) throws Exception {
		synchronized(LANG_CACHE) {
			return getTranslation(langFile, "lang.version");
		}
	}
	/**
	 * @deprecated Will be removed in v1.2.0
	 * Writes all translations of <b>translationMap</b> in <b>langFile</b>
	 * 
	 * @return Returns true if successful, false otherwise
	 */
	@Deprecated
	public static boolean write(File langFile, Map<String, String> translationMap, TerminalIO term) {
		synchronized(LANG_CACHE) {
			LANG_CACHE.clear();
			
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
	}
	/**
	 * @deprecated Will be removed in v1.1.0
	 */
	@Deprecated
	public static LangCompilerInterface createCompilerInterface(String langFile, TerminalIO term) throws Exception {
		return createCompilerInterface(langFile, term, new LangPlatformAPI());
	}
	/**
	 * @deprecated Will be removed in v1.1.0
	 */
	@Deprecated
	public static LangCompilerInterface createCompilerInterface(String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI) throws Exception {
		return new LangCompilerInterface(createInterpreterInterface(langFile, term, langPlatformAPI));
	}
	/**
	 * @deprecated Will be removed in v1.1.0
	 */
	@Deprecated
	public static LangCompilerInterface createCompilerInterface(TerminalIO term) {
		return createCompilerInterface(term, new LangPlatformAPI());
	}
	/**
	 * @deprecated Will be removed in v1.1.0
	 */
	@Deprecated
	public static LangCompilerInterface createCompilerInterface(TerminalIO term, LangPlatformAPI langPlatformAPI) {
		return new LangCompilerInterface(createInterpreterInterface(term, langPlatformAPI));
	}
	
	
	//Classes for compiling lang file
	/**
	 * @deprecated Will be removed in v1.1.0
	 */
	@Deprecated
	public static class LangCompilerInterface {
		private LangInterpreter.LangInterpreterInterface lii;
		
		private LangCompilerInterface(LangInterpreter.LangInterpreterInterface lii) {
			this.lii = lii;
		}
		
		public Map<Integer, Compiler.Data> getData() {
			HashMap<Integer, Compiler.Data> convertedDataMap = new HashMap<>();
			
			Map<Integer, LangInterpreter.Data> dataMap = lii.getData();
			dataMap.forEach((DATA_ID, data) -> {
				Compiler.Data convertedData = new Compiler.Data();
				convertedData.lang = data.lang;
				convertedData.var = new HashMap<>();
				data.var.forEach((varName, varData) -> convertedData.var.put(varName, Compiler.DataObject.convert(varData, lii)));
				
				convertedDataMap.put(DATA_ID, convertedData);
			});
			
			return convertedDataMap;
		}
		public Compiler.Data getData(final int DATA_ID) {
			return getData().get(DATA_ID);
		}
		
		public Map<String, String> getTranslationMap(final int DATA_ID) {
			Compiler.Data data = getData(DATA_ID);
			if(data == null)
				return null;
			
			return data.lang;
		}
		public String getTranslation(final int DATA_ID, String key) {
			Map<String, String> translations = getTranslationMap(DATA_ID);
			if(translations == null)
				return null;
			
			return translations.get(key);
		}
		
		public void setTranslation(final int DATA_ID, String key, String value) {
			Map<String, String> translations = getTranslationMap(DATA_ID);
			if(translations != null)
				translations.put(key, value);
		}
		
		public Map<String, Compiler.DataObject> getVarMap(final int DATA_ID) {
			Compiler.Data data = getData(DATA_ID);
			if(data == null)
				return null;
			
			return data.var;
		}
		public Compiler.DataObject getVar(final int DATA_ID, String varName) {
			Map<String, Compiler.DataObject> vars = getVarMap(DATA_ID);
			if(vars == null)
				return null;
			
			return vars.get(varName);
		}
		
		private void setVar(final int DATA_ID, String varName, Compiler.DataObject data, boolean ignoreFinal) {
			lii.setVar(DATA_ID, varName, data.convert(lii), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, String text) {
			setVar(DATA_ID, varName, text, false);
		}
		public void setVar(final int DATA_ID, String varName, String text, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new Compiler.DataObject(text), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, Compiler.DataObject[] arr) {
			setVar(DATA_ID, varName, arr, false);
		}
		public void setVar(final int DATA_ID, String varName, Compiler.DataObject[] arr, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new Compiler.DataObject().setArray(arr), ignoreFinal);
		}
		/**
		 * @param function Call: function(String funcArgs, int DATA_ID): String
		 */
		public void setVar(final int DATA_ID, String varName, BiFunction<String, Integer, String> function) {
			setVar(DATA_ID, varName, function, false);
		}
		/**
		 * @param function Call: function(String funcArgs, int DATA_ID): String
		 */
		public void setVar(final int DATA_ID, String varName, BiFunction<String, Integer, String> function, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new Compiler.DataObject().setFunctionPointer(new Compiler.FunctionPointerObject(function)), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, int errno) {
			setVar(DATA_ID, varName, errno, false);
		}
		public void setVar(final int DATA_ID, String varName, int errno, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new Compiler.DataObject().setError(new Compiler.ErrorObject(errno)), false);
		}
		/**
		 * @param voidNull Sets the var to null if voidNull else void
		 */
		public void setVar(final int DATA_ID, String varName, boolean voidNull) {
			setVar(DATA_ID, varName, voidNull, false);
		}
		/**
		 * @param voidNull Sets the var to null if voidNull else void
		 */
		public void setVar(final int DATA_ID, String varName, boolean voidNull, boolean ignoreFinal) {
			Compiler.DataObject dataObject = new Compiler.DataObject();
			if(voidNull)
				dataObject.setNull();
			else
				dataObject.setVoid();
			
			setVar(DATA_ID, varName, dataObject, ignoreFinal);
		}
		
		/**
		 * Creates an function which is accessible globally in the Compiler (= in all DATA_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]" and can't be removed nor changed by the lang file
		 */
		public void addPredefinedFunction(String funcName, BiFunction<String, Integer, String> function) {
			lii.addPredefinedFunction(funcName, new Compiler.DataObject().setFunctionPointer(new Lang.Compiler.FunctionPointerObject(function)).convert(lii).
			getFunctionPointer().getPredefinedFunction());
		}
		
		public void exec(final int DATA_ID, BufferedReader lines) throws IOException {
			lii.exec(DATA_ID, lines);
		}
		public void exec(final int DATA_ID, String lines) throws IOException {
			exec(DATA_ID, new BufferedReader(new StringReader(lines)));
		}
		public String execLine(final int DATA_ID, String line) {
			try {
				exec(DATA_ID, line);
			}catch(IOException e) {
				return "Error";
			}
			
			return "";
		}
		public String callFunction(final int DATA_ID, String funcName, String funcArgs) {
			List<Node> argumentList = new LinkedList<>();
			String code = "func.abc(" + funcArgs + ")";
			try {
				AbstractSyntaxTree ast = lii.parseLines(new BufferedReader(new StringReader(code)));
				argumentList.addAll(ast.getChildren().get(0).getChildren());
			}catch(Exception e) {
				argumentList.add(new ParsingErrorNode(ParsingError.EOF));
			}
			
			LangInterpreter.FunctionPointerObject fp;
			if(funcName.startsWith("func.") || funcName.startsWith("linker.")) {
				boolean isLinkerFunction = funcName.startsWith("l");
				
				funcName = funcName.substring(funcName.indexOf('.') + 1);
				String funcNameCopy = funcName;
				Optional<LangPredefinedFunctionObject> predefinedFunction = lii.getPredefinedFunctions().entrySet().stream().filter(entry -> {
					return entry.getValue().isLinkerFunction() == isLinkerFunction;
				}).filter(entry -> {
					return entry.getKey().equals(funcNameCopy);
				}).map(Map.Entry<String, LangPredefinedFunctionObject>::getValue).findFirst();
				fp = new LangInterpreter.FunctionPointerObject(predefinedFunction.orElse(null));
			}else {
				LangInterpreter.DataObject dataObject = lii.getData(DATA_ID).var.get(funcName);
				fp = dataObject == null?null:dataObject.getFunctionPointer();
			}
			
			return lii.interpretFunctionPointer(fp, funcName, argumentList, DATA_ID).getText();
		}
	}
	/**
	 * @deprecated Will be removed in v1.1.0
	 */
	@Deprecated
	private static class Compiler {
		private Compiler() {}
		
		//Classes for variable data
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static class FunctionPointerObject {
			/**
			 * Normal function pointer
			 */
			public static final int NORMAL = 0;
			/**
			 * Pointer to a predefined function
			 */
			public static final int PREDEFINED = 1;
			/**
			 * Function which is defined in the language, were the Compiler/Interpreter is defined
			 */
			public static final int EXTERNAL = 2;
			/**
			 * Pointer to a linker function
			 */
			public static final int LINKER = 3;
			
			private final String head;
			private final String body;
			private final BiFunction<String, Integer, String> externalFunction;
			private final int functionPointerType;
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@Deprecated
			public FunctionPointerObject(String langFuncObjectName) {
				this.head = "";
				this.body = langFuncObjectName;
				this.externalFunction = null;
				this.functionPointerType = PREDEFINED;
			}
			/**
			 * For pointer to external function
			 */
			public FunctionPointerObject(BiFunction<String, Integer, String> externalFunction) {
				this.head = "";
				this.body = "";
				this.externalFunction = externalFunction;
				this.functionPointerType = EXTERNAL;
			}
			
			public String getBody() {
				return body;
			}
			
			public String getHead() {
				return head;
			}
			
			public BiFunction<String, Integer, String> getExternalFunction() {
				return externalFunction;
			}
			
			public int getFunctionPointerType() {
				return functionPointerType;
			}
			
			@Override
			public String toString() {
				return head + "\n" + body;
			}
		}
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static class VarPointerObject {
			private final String varName;
			private final DataObject var;
			
			public VarPointerObject(String varName, DataObject var) {
				this.varName = varName;
				this.var = var;
			}
			
			public String getVarName() {
				return varName;
			}
			
			public DataObject getVar() {
				return var;
			}
			
			@Override
			public String toString() {
				return varName;
			}
		}
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static class ClassObject {
			private final Map<String, DataObject> attributes = new HashMap<>();
			private final String className;
			private final String packageName;
			private final ClassObject superClass;
			private final boolean classDefinition; //Is true if the class object is only an class definition else it is an actual instance of the class
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public ClassObject(String className, String packageName, ClassObject superClass, boolean classDefinition) {
				this.className = className;
				this.packageName = packageName;
				this.superClass = superClass;
				this.classDefinition = classDefinition;
			}

			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public void setAttribute(String name, DataObject data) {
				attributes.put(name, data);
			}
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public DataObject getAttribute(String name) {
				return attributes.get(name);
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public Map<String, DataObject> getAttributes() {
				return attributes;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public String getPackageName() {
				return packageName;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public ClassObject getSuperClass() {
				return superClass;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public boolean isClassDefinition() {
				return classDefinition;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public boolean isInstanceOf(ClassObject classObject) {
				if(this.equals(classObject))
					return true;
				
				if(superClass == null)
					return classObject.superClass == null;
				
				return superClass.isInstanceOf(classObject);
			}
			
			@Override
			public String toString() {
				return "Class";
			}
			
			@Override
			public boolean equals(Object obj) {
				if(obj == null)
					return false;
				
				if(this == obj)
					return true;
				
				if(obj instanceof ClassObject) {
					ClassObject that = (ClassObject)obj;
					
					return Objects.equals(this.className, that.className) && Objects.equals(this.packageName, that.packageName);
				}
				
				return false;
			}
		}
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static class ErrorObject {
			private final int err;
			
			public ErrorObject(int err) {
				this.err = err;
			}
			
			public int getErrno() {
				return err;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public String getErrmsg() {
				return InterpretingError.getErrorFromErrorCode(err).getErrorText();
			}
			
			@Override
			public String toString() {
				return "Error";
			}
		}
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static enum DataType {
			TEXT, ARRAY, VAR_POINTER, FUNCTION_POINTER, VOID, NULL, INT, LONG, DOUBLE, FLOAT, CHAR, CLASS, ERROR;
		}
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static class DataObject {
			private DataType type;
			private String txt;
			private DataObject[] arr;
			private VarPointerObject vp;
			private FunctionPointerObject fp;
			private int intValue;
			private long longValue;
			private float floatValue;
			private double doubleValue;
			private char charValue;
			private ClassObject classObject;
			private ErrorObject error;
			private boolean finalData;
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public DataObject(DataObject dataObject) {
				setData(dataObject);
			}
			
			public DataObject() {
				this("");
			}
			public DataObject(String txt) {
				this(txt, false);
			}
			public DataObject(String txt, boolean finalData) {
				setText(txt);
				setFinalData(finalData);
			}
			
			/**
			 * This method <b>ignores</b> the final state of the data object
			 */
			private void setData(DataObject dataObject) {
				this.type = dataObject.type;
				this.txt = dataObject.txt;
				//Array won't be copied accurate, because function pointer should be able to change array data from inside
				this.arr = dataObject.arr;
				this.vp = dataObject.vp;
				this.fp = dataObject.fp;
				this.intValue = dataObject.intValue;
				this.longValue = dataObject.longValue;
				this.floatValue = dataObject.floatValue;
				this.doubleValue = dataObject.doubleValue;
				this.charValue = dataObject.charValue;
				//Class won't be copied accurate, because function pointer should be able to change class data from inside
				this.classObject = dataObject.classObject;
				this.error = dataObject.error;
				this.finalData = dataObject.finalData;
			}
			
			public DataObject setText(String txt) {
				if(finalData)
					return this;
				
				this.type = DataType.TEXT;
				this.txt = txt;
				
				return this;
			}
			
			public String getText() {
				switch(type) {
					case TEXT:
						return txt;
					case ARRAY:
						return Arrays.toString(arr);
					case VAR_POINTER:
						return vp.toString();
					case FUNCTION_POINTER:
						return fp.toString();
					case VOID:
						return "";
					case NULL:
						return "null";
					case INT:
						return intValue + "";
					case LONG:
						return longValue + "";
					case FLOAT:
						return floatValue + "";
					case DOUBLE:
						return doubleValue + "";
					case CHAR:
						return charValue + "";
					case CLASS:
						return classObject.toString();
					case ERROR:
						return error.toString();
				}
				
				return null;
			}
			
			public DataObject setArray(DataObject[] arr) {
				if(finalData)
					return this;
				
				this.type = DataType.ARRAY;
				this.arr = arr;
				
				return this;
			}
			
			public DataObject[] getArray() {
				return arr;
			}
			
			public DataObject setVarPointer(VarPointerObject vp) {
				if(finalData)
					return this;
				
				this.type = DataType.VAR_POINTER;
				this.vp = vp;
				
				return this;
			}
			
			public VarPointerObject getVarPointer() {
				return vp;
			}
			
			public DataObject setFunctionPointer(FunctionPointerObject fp) {
				if(finalData)
					return this;
				
				this.type = DataType.FUNCTION_POINTER;
				this.fp = fp;
				
				return this;
			}
			
			public FunctionPointerObject getFunctionPointer() {
				return fp;
			}
			
			public DataObject setNull() {
				if(finalData)
					return this;
				
				this.type = DataType.NULL;
				
				return this;
			}
			
			public DataObject setVoid() {
				if(finalData)
					return this;
				
				this.type = DataType.VOID;
				
				return this;
			}
			
			public DataObject setInt(int intValue) {
				if(finalData)
					return this;
				
				this.type = DataType.INT;
				this.intValue = intValue;
				
				return this;
			}
			
			public int getInt() {
				return intValue;
			}
			
			public DataObject setLong(long longValue) {
				if(finalData)
					return this;
				
				this.type = DataType.LONG;
				this.longValue = longValue;
				
				return this;
			}
			
			public long getLong() {
				return longValue;
			}
			
			public DataObject setFloat(float floatValue) {
				if(finalData)
					return this;
				
				this.type = DataType.FLOAT;
				this.floatValue = floatValue;
				
				return this;
			}
			
			public float getFloat() {
				return floatValue;
			}
			
			public DataObject setDouble(double doubleValue) {
				if(finalData)
					return this;
				
				this.type = DataType.DOUBLE;
				this.doubleValue = doubleValue;
				
				return this;
			}
			
			public double getDouble() {
				return doubleValue;
			}
			
			public DataObject setChar(char charValue) {
				if(finalData)
					return this;
				
				this.type = DataType.CHAR;
				this.charValue = charValue;
				
				return this;
			}
			
			public char getChar() {
				return charValue;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public DataObject setClassObject(ClassObject classObject) {
				if(finalData)
					return this;
				
				this.type = DataType.CLASS;
				this.classObject = classObject;
				
				return this;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public ClassObject getClassObject() {
				return classObject;
			}
			
			public DataObject setError(ErrorObject error) {
				if(finalData)
					return this;
				
				this.type = DataType.ERROR;
				this.error = error;
				
				return this;
			}
			
			public ErrorObject getError() {
				return error;
			}
			
			public DataObject setFinalData(boolean finalData) {
				this.finalData = finalData;
				
				return this;
			}
			
			public boolean isFinalData() {
				return finalData;
			}
			
			/**
			 * @deprecated Will be removed in v1.1.0
			 * For pointer to predefined function
			 */
			@SuppressWarnings("unused")
			@Deprecated
			public DataType getType() {
				return type;
			}
			
			public LangInterpreter.DataObject convert(LangInterpreter.LangInterpreterInterface lii) {
				LangInterpreter.DataObject convertedDataObject = new LangInterpreter.DataObject();
				switch(type) {
					case ARRAY:
						DataObject[] arr = getArray();
						LangInterpreter.DataObject[] convertedArr = new LangInterpreter.DataObject[arr.length];
						for(int i = 0;i < arr.length;i++)
							convertedArr[i] = arr[i] == null?null:arr[i].convert(lii);
						convertedDataObject.setArray(convertedArr);
						break;
					case CHAR:
						convertedDataObject.setChar(getChar());
						break;
					case DOUBLE:
						convertedDataObject.setDouble(getDouble());
						break;
					case ERROR:
						ErrorObject err = getError();
						LangInterpreter.ErrorObject convertedErr = new LangInterpreter.ErrorObject(InterpretingError.getErrorFromErrorCode(err.getErrno()));
						convertedDataObject.setError(convertedErr);
						break;
					case FLOAT:
						convertedDataObject.setFloat(getFloat());
						break;
					case FUNCTION_POINTER:
						FunctionPointerObject fp = getFunctionPointer();
						LangInterpreter.FunctionPointerObject convertedFP = null;
						switch(fp.getFunctionPointerType()) {
							case FunctionPointerObject.NORMAL:
								convertedFP = new LangInterpreter.FunctionPointerObject((LangPredefinedFunctionObject)(argumentList, DATA_ID) -> {
									String function = "fp.abc = (" + fp.getHead() + ") -> {\n" + fp.getBody() + "}";
									try {
										AbstractSyntaxTree ast = lii.parseLines(new BufferedReader(new StringReader(function)));
										FunctionDefinitionNode functionDefinitionNode = (FunctionDefinitionNode)((AssignmentNode)(ast.getChildren().get(0))).getRvalue();
										AbstractSyntaxTree functionBody = functionDefinitionNode.getFunctionBody();
										
										List<VariableNameNode> parameterList = new ArrayList<>();
										List<Node> children = functionDefinitionNode.getChildren();
										for(Node child:children) {
											if(child.getNodeType() != NodeType.VARIABLE_NAME)
												continue;
											
											VariableNameNode parameter = (VariableNameNode)child;
											if(!parameter.getVariableName().matches("(\\$|&|fp\\.)\\w+") || parameter.getVariableName().matches("(\\$|&)LANG_.*")) 
												continue;
											
											parameterList.add(parameter);
										}
										
										return lii.callFunctionPointer(new LangInterpreter.FunctionPointerObject(parameterList, functionBody), null, argumentList, DATA_ID);
									}catch(ClassCastException|IOException e) {
										return new LangInterpreter.DataObject().setError(new LangInterpreter.ErrorObject(InterpretingError.INVALID_AST_NODE));
									}
								});
								break;
							case FunctionPointerObject.PREDEFINED:
							case FunctionPointerObject.LINKER:
								String funcName = getFunctionPointer().getBody();
								if(funcName.startsWith("func.") || funcName.startsWith("linker.")) {
									boolean isLinkerFunction = funcName.startsWith("l");
									
									funcName = funcName.substring(funcName.indexOf('.') + 1);
									String funcNameCopy = funcName;
									Optional<LangPredefinedFunctionObject> predefinedFunction = lii.getPredefinedFunctions().entrySet().stream().filter(entry -> {
										return entry.getValue().isLinkerFunction() == isLinkerFunction;
									}).filter(entry -> {
										return entry.getKey().equals(funcNameCopy);
									}).map(Map.Entry<String, LangPredefinedFunctionObject>::getValue).findFirst();
									convertedFP = new LangInterpreter.FunctionPointerObject(predefinedFunction.orElse(null));
								}
								break;
							case FunctionPointerObject.EXTERNAL:
								LangExternalFunctionObject convertedExternalFunction = (argumentList, DATA_ID) -> {
									String args = LangUtils.combineDataObjects(argumentList).getText();
									return new LangInterpreter.DataObject(fp.getExternalFunction().apply(args, DATA_ID));
								};
								
								convertedFP = new LangInterpreter.FunctionPointerObject(convertedExternalFunction);
								break;
						}
						convertedDataObject.setFunctionPointer(convertedFP);
						break;
					case INT:
						convertedDataObject.setInt(getInt());
						break;
					case LONG:
						convertedDataObject.setLong(getLong());
						break;
					case NULL:
						convertedDataObject.setNull();
						break;
					case TEXT:
					case CLASS:
						convertedDataObject.setText(getText());
						break;
					case VAR_POINTER:
						DataObject var = getVarPointer().getVar();
						LangInterpreter.VarPointerObject convertedVarPtr = new LangInterpreter.VarPointerObject(var == null?null:var.convert(lii).setVariableName(getVarPointer().getVarName()));
						convertedDataObject.setVarPointer(convertedVarPtr);
						break;
					case VOID:
						convertedDataObject.setVoid();
						break;
				}
				
				return convertedDataObject.setFinalData(isFinalData());
			}
			
			public static DataObject convert(LangInterpreter.DataObject dataObject, LangInterpreter.LangInterpreterInterface lii) {
				if(dataObject == null)
					return null;
				
				DataObject convertedDataObject = new DataObject();
				
				switch(dataObject.getType()) {
					case ARRAY:
						LangInterpreter.DataObject[] arr = dataObject.getArray();
						DataObject[] convertedArr = new DataObject[arr.length];
						for(int i = 0;i < arr.length;i++)
							convertedArr[i] = convert(arr[i], lii);
						convertedDataObject.setArray(convertedArr);
						break;
					case CHAR:
						convertedDataObject.setChar(dataObject.getChar());
						break;
					case DOUBLE:
						convertedDataObject.setDouble(dataObject.getDouble());
						break;
					case ERROR:
						LangInterpreter.ErrorObject err = dataObject.getError();
						ErrorObject convertedErr = new ErrorObject(err.getErrno());
						convertedDataObject.setError(convertedErr);
						break;
					case FLOAT:
						convertedDataObject.setFloat(dataObject.getFloat());
						break;
					case FUNCTION_POINTER:
						LangInterpreter.FunctionPointerObject fp = dataObject.getFunctionPointer();
						FunctionPointerObject convertedFP = null;
						switch(fp.getFunctionPointerType()) {
							case LangInterpreter.FunctionPointerObject.NORMAL:
							case LangInterpreter.FunctionPointerObject.PREDEFINED:
								convertedFP = new FunctionPointerObject(dataObject.getVariableName());
								break;
							case LangInterpreter.FunctionPointerObject.EXTERNAL:
								BiFunction<String, Integer, String> convertedExternalFunction = (args, DATA_ID) -> {
									List<Node> argumentList = new LinkedList<>();
									String code = "func.abc(" + args + ")";
									try {
										AbstractSyntaxTree ast = lii.parseLines(new BufferedReader(new StringReader(code)));
										argumentList.addAll(ast.getChildren().get(0).getChildren());
									}catch(Exception e) {
										argumentList.add(new ParsingErrorNode(ParsingError.EOF));
									}
									
									LangInterpreter.DataObject ret = lii.interpretFunctionPointer(fp, dataObject.getVariableName(), argumentList, DATA_ID);
									return ret == null?"":ret.getText();
								};
								
								convertedFP = new FunctionPointerObject(convertedExternalFunction);
								break;
						}
						convertedDataObject.setFunctionPointer(convertedFP);
						break;
					case INT:
						convertedDataObject.setInt(dataObject.getInt());
						break;
					case LONG:
						convertedDataObject.setLong(dataObject.getLong());
						break;
					case NULL:
						convertedDataObject.setNull();
						break;
					case TEXT:
					case ARGUMENT_SEPARATOR:
						convertedDataObject.setText(dataObject.getText());
						break;
					case VAR_POINTER:
						LangInterpreter.DataObject var = dataObject.getVarPointer().getVar();
						VarPointerObject convertedVarPtr = new VarPointerObject(var == null?null:var.getVariableName(), var == null?null:convert(var, lii));
						convertedDataObject.setVarPointer(convertedVarPtr);
						break;
					case VOID:
						convertedDataObject.setVoid();
						break;
				}
				
				return convertedDataObject.setFinalData(dataObject.isFinalData());
			}
			
			@Override
			public String toString() {
				return getText();
			}
		}
		/**
		 * @deprecated Will be removed in v1.1.0
		 */
		@Deprecated
		public static class Data {
			public Map<String, String> lang;
			public Map<String, DataObject> var;
		}
	}
}