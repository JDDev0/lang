package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiFunction;

import javax.swing.JOptionPane;

import me.jddev0.module.io.TerminalIO.Level;

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
 * \n -> newLine<br>
 * \s -> space (only in function "printTerminal" and for "langValue")<br>
 * \$ -> $<br>
 * \# -> #<br>
 * <br>
 * <b>--- Lang data and compiler args ---</b><br>
 * needed = (*)lang.xxxx<br>
 * not needed = (+)lang.xxx<br>
 * <br>
 * (*)lang.name //Name of lang file<br>
 * (*)lang.version //Version of lang file<br>
 * <br>
 * <b>--- Lang Types ---</b><br>
 * null //Null-Pointer<br>
 * <br>
 * Test //Text<br>
 * c //char (Text with length of 1)<br>
 * <br>
 * 42 //int, long, double<br>
 * 42.42 ; 42. ; .42 ; 42e2 ; 42e-2 ; 42E2 ; 42E-2 //double<br>
 * <br>
 * FILE //Text to location of file:<br>
 * //<b>important</b>: file separator ('/') will be changed by file separator of OS<br>
 * //absolute: (e.g.: /home/user/"File name")(e.g.: C:/"File name")<br>
 * //relative: (e.g.: "file name")(e.g.: ../"File name")(e.g.: "Folder name"/"File name")<br>
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
 * con.if($y) //if (0 -> false, 1 -> true, or condition)<br>
 * con.elif($x) //Not needed, else if<br>
 * con.else //Not needed, else<br>
 * con.endif //End of if<br>
 * <br>
 * <b>--- Lang If Conditions ---</b><br>
 * For all types:<br>
 * "$x == $y" -> $x is equal $y<br>
 * "$x != $y" -> $x is not equal $y<br>
 * <br>
 * For int, long, double:<br>
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
 * <b>Returns "0" if no error, "-1" if error</b><br>
 * [int]linker.link(FILE)<br>
 * [int]linker.bindLibrary(FILE)<br>
 * <br>
 * <b>--- Lang Functions ---</b><br>
 * <b>Reset Functions</b><br>
 * [void]func.clearVar(varPtr)<br>
 * [void]func.clearVar(arrPtr)<br>
 * [void]func.clearVar(funcPtr)<br>
 * [void]func.clearAllVars(void)<br>
 * [void]func.clearAllArrays(void) //Deprecated: use func.clearAllVars instead<br>
 * <br><b>Error functions</b><br>
 * [Text]func.getErrorString(void)<br>
 * <br><b>Compiler function</b><br>
 * [int]func.isCompilerVersionNewer(void)<br>
 * [int]func.isCompilerVersionOlder(void)<br>
 * <br><b>System Functions</b><br>
 * [void]func.sleep(int)<br>
 * [void]func.repeat(funcPtr, int)<br>
 * [void]func.repeatWhile(funcPtr, funcPtr) //Calls first function pointer while second function pointer returns true<br>
 * [void]func.repeatUntil(funcPtr, funcPtr) //Calls first function pointer while second function pointer returns false<br>
 * [Text]func.getLangRequest(Text)<br>
 * [void]func.makeFinal(varPtr)<br>
 * [void]func.makeFinal(arrPtr) //Content in final array can still be changed<br>
 * [void]func.makeFinal(funcPtr)<br>
 * [int]func.condition(IfCondition) //Returns 1 if the condition is true else 0<br>
 * [long]func.currentTimeMillis(void)<br>
 * <br><b>IO Functions</b><br>
 * [Text]func.readTerminal(Text)<br>
 * [void]func.printTerminal(int, Text)<br>
 * [void]func.printError([Text]) //"func.printTerminal" with "func.getErrorString"<br>
 * <br><b>Num Functions</b><br>
 * [int]func.hexToDez(Text)<br>
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
 * <br><i>Round functions</i><br>
 * [long]func.ceil(double)<br>
 * [long]func.floor(double)<br>
 * <br><b>FuncPtr functions</b><br>
 * [void]func.copyAfterFP(varPtr, varPtr) //Caller.varPtr (1st arg) = FuncPtr.varPtr (2nd arg)<br>
 * [void]func.copyAfterFP(arrPtr, arrPtr) //"[void]func.copyAfterFP(varPtr, varPtr)" with arrPtrs<br>
 * [void]func.copyAfterFP(funcPtr, funcPtr) //"[void]func.copyAfterFP(varPtr, varPtr)" with funcPtrs<br>
 * <br><b>Array functions</b><br>
 * [void]func.arrayMake(arrPtr, int)<br>
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
 * @version v0.2.0
 */
public class Lang {
	private static final String VERSION = "v0.2.0";
	private static final Random RAN = new Random();
	
	private static String oldFile;
	private static String pathLangFile; //$LANG_PATH
	
	//Lang tmp
	private static Map<String, String> lang = new HashMap<>(); //ID, data
	
	private Lang() {}
	
	/**
	 * @return Returns all available lang files
	 */
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
	 * @return Returns all translations of <b>langFile</b>
	 */
	public static Map<String, String> getTranslationMap(String langFile, boolean reload, TerminalIO term) throws Exception {
		synchronized(lang) {
			if(langFile.equals(oldFile)) {
				if(lang.containsKey("lang.name") && !reload) {
					return new HashMap<>(lang);
				}
			}else {
				lang.clear(); //Remove old data
				oldFile = langFile;
			}
			
			//Set path for Compiler
			langFile = new File(langFile).getAbsolutePath();
			pathLangFile = langFile.substring(0, langFile.lastIndexOf(File.separator)); //Remove ending ("/*.lang") for $LANG_PATH
			
			//Create new compiler instance
			Compiler comp = new Compiler(pathLangFile, term);
			
			BufferedReader reader = new BufferedReader(new FileReader(new File(langFile)));
			try {
				comp.compileLangFile(reader, 0); //Compile lang file
			}catch(Exception e) {
				reader.close();
				
				throw e;
			}
			reader.close();
			
			//Copy lang
			lang = comp.getData().get(0).lang;
			
			return new HashMap<>(lang);
		}
	}
	
	/**
	 * @return Returns translation <b>key</b> of <b>langFile</b><br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	public static String getTranslation(String langFile, String key) throws Exception {
		synchronized(lang) {
			if(getTranslationMap(langFile, false, null).get(key) == null) {
				return key;
			}
			
			return getTranslationMap(langFile, false, null).get(key);
		}
	}
	
	/**
	 * @return Returns translation <b>key</b> of <b>langFile</b><br>
	 * If key wasn't found -> <code>return key;</code>
	 */
	public static String getTranslationFormat(String langFile, String key, Object... args) throws Exception {
		synchronized(lang) {
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
	 * @return Returns language name of <b>langFile</b><br>
	 * <code>return getTranslation(langFile, "lang.name");</code>
	 */
	public static String getLangName(String langFile) throws Exception {
		synchronized(lang) {
			return getTranslation(langFile, "lang.name");
		}
	}
	
	/**
	 * @return Returns language version of <b>langFile</b><br>
	 * <code>return getTranslation(langFile, "lang.name");</code>
	 */
	public static String getLangVersion(String langFile) throws Exception {
		synchronized(lang) {
			return getTranslation(langFile, "lang.version");
		}
	}
	
	/**
	 * Writes all translations of <b>translationMap</b> in <b>langFile</b>
	 * 
	 * @return Returns true if successful, false otherwise
	 */
	public static boolean write(File langFile, Map<String, String> translationMap, TerminalIO term) {
		synchronized(lang) {
			lang.clear();
			
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
	
	public static LangCompilerInterface createCompilerInterface(String langFile, TerminalIO term) throws Exception {
		langFile = new File(langFile).getAbsolutePath();
		String pathLangFile = langFile.substring(0, langFile.lastIndexOf(File.separator));
		
		Compiler comp = new Compiler(pathLangFile, term);
		
		BufferedReader reader = new BufferedReader(new FileReader(new File(langFile)));
		try {
			comp.compileLangFile(reader, 0); //Compile lang file
		}catch(Exception e) {
			reader.close();
			
			throw e;
		}
		reader.close();
		
		return new LangCompilerInterface(comp);
	}
	public static LangCompilerInterface createCompilerInterface(TerminalIO term) {
		return new LangCompilerInterface(new Compiler(new File("").getAbsolutePath(), term));
	}
	
	//Classes for compiling lang file
	public static class LangCompilerInterface {
		private Compiler comp;
		
		private LangCompilerInterface(Compiler comp) {
			this.comp = comp;
		}
		
		public Map<Integer, Compiler.Data> getData() {
			return comp.getData();
		}
		public Compiler.Data getData(final int DATA_ID) {
			return comp.getData().get(DATA_ID);
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
			
			return data.varTmp;
		}
		public Compiler.DataObject getVar(final int DATA_ID, String varName) {
			Map<String, Compiler.DataObject> vars = getVarMap(DATA_ID);
			if(vars == null)
				return null;
			
			return vars.get(varName);
		}
		
		private void setVar(final int DATA_ID, String varName, Compiler.DataObject data, boolean ignoreFinal) {
			Map<String, Compiler.DataObject> vars = getVarMap(DATA_ID);
			if(vars != null) {
				if(ignoreFinal) {
					vars.put(varName, data);
				}else {
					Compiler.DataObject oldData = vars.get(varName);
					if(oldData == null)
						vars.put(varName, data);
					else if(!oldData.isFinalData())
						oldData.setData(data);
				}
			}
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
			if(errno < 0 || errno >= Compiler.ERROR_STRINGS.length)
				return;
			
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
		 * Creates an function which is accessible globaly in the Compiler (= in all DATA_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]" and can't be removed nor changed by the lang file
		 */
		public void addPredefinedFunction(String funcName, BiFunction<String, Integer, String> function) {
			comp.funcs.put(funcName, (lines, arg, DATA_ID) -> function.apply(arg, DATA_ID));
		}
		
		public void exec(final int DATA_ID, BufferedReader lines) throws Exception {
			comp.compileLangFile(lines, DATA_ID);
		}
		public void exec(final int DATA_ID, String lines) throws Exception {
			exec(DATA_ID, new BufferedReader(new StringReader(lines)));
		}
		public String execLine(final int DATA_ID, String line) {
			return comp.compileLine(new BufferedReader(new StringReader(line)), line, DATA_ID);
		}
		public String callFunction(final int DATA_ID, String funcName, String funcArgs) {
			return execLine(DATA_ID, funcName + ".(" + funcArgs + ")");
		}
	}
	private static class Compiler {
		private final static String[] ERROR_STRINGS = new String[] {
			"No error" /*errno = 0*/, "$LANG or final var mustn't be changed", "To many inner links", "No .lang-File", "File not found", "FuncPtr is invalid", "Stack overflow",
			"No terminal available", "Invalid argument count", "Invalid log level", "Invalid array pointer", "No hex num", "No char", "No num", "Dividing by 0", "Negative array length",
			"Empty array", "Length NAN", "Array out of bounds", "Argument count is not array length", "Invalid function pointer", "Invalid arguments", "Function not found", "EOF", "System Error",
			"Negative repeat count", "Lang request doesn't exist", "Function not supported", "Bracket count mismatch"
		};
		
		private String langPath;
		private TerminalIO term;
		private LinkerParser linkerParser = new LinkerParser();
		private IfParser ifParser = new IfParser();
		private VarParser varParser = new VarParser();
		private FuncParser funcParser = new FuncParser();
		
		//DATA
		private Map<Integer, Data> data = new HashMap<>();
		
		//INIT funcs
		private Map<String, LangFunctionObject> funcs = new HashMap<>();
		{
			//Reset Functions
			funcs.put("clearVar", (lines, arg, DATA_ID) -> {
				Compiler.DataObject dataObject = data.get(DATA_ID).varTmp.get(arg.trim());
				if(dataObject == null) {
					setErrno(21, DATA_ID);
					
					return "Error";
				}
				
				if(dataObject.isFinalData()) {
					setErrno(1, DATA_ID);
					
					return "Error";
				}
				
				if(dataObject.getType().equals(Compiler.DataType.CLASS)) {
					String line = arg.trim() + "[DELETE]";
					compileLine(new BufferedReader(new StringReader(line)), line, DATA_ID);
				}else {
					data.get(DATA_ID).varTmp.remove(arg.trim());
				}
				
				return "";
			});
			funcs.put("clearAllVars", (lines, arg, DATA_ID) -> {
				resetVars(DATA_ID);
				
				return "";
			});
			funcs.put("clearAllArrays", (lines, arg, DATA_ID) -> {
				new HashSet<String>(data.get(DATA_ID).varTmp.keySet()).forEach(key -> {
					if(key.startsWith("&"))
						data.get(DATA_ID).varTmp.remove(key);
				});
				term.logln(Level.WARNING, "Use of deprecated function \"clearAllArays\", this function won't be supported in future releases! Use \"clearAllVars\" instead!", Compiler.class);
				
				return "";
			});
			
			//Error functions
			funcs.put("getErrorString", (lines, arg, DATA_ID) -> {
				int err = getAndClearErrno(DATA_ID); //Reset and return error
				
				return ERROR_STRINGS[err];
			});
			
			//Compiler function
			funcs.put("isCompilerVersionNewer", (lines, arg, DATA_ID) -> {
				String langVer = data.get(DATA_ID).lang.getOrDefault("lang.version", VERSION); //If lang.version = null -> return false
				
				return (VERSION.compareTo(langVer) > 0)?"1":"0";
			});
			funcs.put("isCompilerVersionOlder", (lines, arg, DATA_ID) -> {
				String langVer = data.get(DATA_ID).lang.getOrDefault("lang.version", VERSION); //If lang.version = null -> return false
				
				return (VERSION.compareTo(langVer) < 0)?"1":"0";
			});
			
			//System Functions
			funcs.put("sleep", (lines, arg, DATA_ID) -> {
				try {
					int sleepTime = Integer.parseInt(arg.trim());
					
					try {
						Thread.sleep(sleepTime);
					}catch(InterruptedException e) {
						setErrno(24, DATA_ID);
						
						return "Error";
					}
				}catch(NumberFormatException e) {
					setErrno(9, DATA_ID);
					
					return "Error";
				}
				
				return "";
			});
			funcs.put("repeat", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				try {
					String funcPtr = funcArgs[0].trim();
					if(!funcPtr.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(funcPtr)) {
						setErrno(20, DATA_ID);
						
						return "Error";
					}
					
					int times = Integer.parseInt(funcArgs[1].trim());
					if(times < 0) {
						setErrno(25, DATA_ID);
						
						return "Error";
					}
					
					for(int i = 0;i < times;i++) {
						funcParser.compileFunc(funcPtr, "" + i, DATA_ID);
					}
				}catch(NumberFormatException e) {
					setErrno(9, DATA_ID);
					
					return "Error";
				}
				
				return "";
			});
			funcs.put("repeatWhile", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				try {
					String execFunc = funcArgs[0].trim();
					String checkFunc = funcArgs[1].trim();
					if(!execFunc.startsWith("fp.") || !checkFunc.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(execFunc) || !data.get(DATA_ID).varTmp.containsKey(checkFunc)) {
						setErrno(20, DATA_ID);
						
						return "Error";
					}
					
					while(true) {
						String check = funcParser.compileFunc(checkFunc, "", DATA_ID);
						try {
							if(Integer.parseInt(check) == 0)
								break;
						}catch(NumberFormatException e) {
							break;
						}
						funcParser.compileFunc(execFunc, "", DATA_ID);
					}
				}catch(NumberFormatException e) {
					setErrno(9, DATA_ID);
					
					return "Error";
				}
				
				return "";
			});
			funcs.put("repeatUntil", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				try {
					String execFunc = funcArgs[0].trim();
					String checkFunc = funcArgs[1].trim();
					if(!execFunc.startsWith("fp.") || !checkFunc.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(execFunc) || !data.get(DATA_ID).varTmp.containsKey(checkFunc)) {
						setErrno(20, DATA_ID);
						
						return "Error";
					}
					
					while(true) {
						String check = funcParser.compileFunc(checkFunc, "", DATA_ID);
						try {
							if(Integer.parseInt(check) != 0)
								break;
						}catch(NumberFormatException e) {
							break;
						}
						funcParser.compileFunc(execFunc, "", DATA_ID);
					}
				}catch(NumberFormatException e) {
					setErrno(9, DATA_ID);
					
					return "Error";
				}
				
				return "";
			});
			funcs.put("getLangRequest", (lines, arg, DATA_ID) -> {
				String ret = data.get(DATA_ID).lang.get(arg.trim());
				if(ret == null) {
					setErrno(26, DATA_ID);
					
					return "Error";
				}
				
				return ret;
			});
			funcs.put("makeFinal", (lines, arg, DATA_ID) -> {
				DataObject dataObject = data.get(DATA_ID).varTmp.get(arg.trim());
				if(dataObject == null || dataObject.isFinalData() || arg.trim().startsWith("$LANG_")) {
					setErrno(21, DATA_ID);
					
					return "Error";
				}
				
				dataObject.setFinalData(true);
				
				return "";
			});
			funcs.put("condition", (lines, arg, DATA_ID) -> ifParser.checkIf(arg)?"1":"0");
			funcs.put("currentTimeMillis", (lines, arg, DATA_ID) -> System.currentTimeMillis() + "");
			
			//IO Functions
			funcs.put("readTerminal", (lines, arg, DATA_ID) -> {
				String input = JOptionPane.showInputDialog(null, arg, "Lang input", JOptionPane.PLAIN_MESSAGE);
				
				if(input == null)
					return "";
				else
					return input;
			});
			funcs.put("printTerminal", (lines, arg, DATA_ID) -> {
				if(term == null) {
					setErrno(7, DATA_ID);
					
					return "Error";
				}
				Level lvl = null;
				
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				try {
					int logLevel = Integer.parseInt(funcArgs[0].trim());
					
					for(Level l:Level.values()) {
						if(l.getLevel() == logLevel) {
							lvl = l;
							
							break;
						}
					}
					
					if(lvl == null) { //If invalid log level...
						throw new NumberFormatException(); //...return error
					}
				}catch(NumberFormatException e) {
					setErrno(9, DATA_ID);
					
					return "Error";
				}
				
				term.logln(lvl, "[From lang file]: " + funcArgs[1].trim().replaceAll("\\\\s", " "), Lang.class);
				
				return "";
			});
			funcs.put("printError", (lines, arg, DATA_ID) -> {
				String comp = "func.printTerminal(" + Level.ERROR.getLevel() + ", ";
				if(arg.trim().length() > 0) { //If argCount != 0
					 comp += arg.trim() + ": ";
				}
				comp += "func.getErrorString())";
				try {
					compileLangFile(new BufferedReader(new StringReader(comp)), DATA_ID); //Compile like "perror"(C) -> (Text + ": " + Error-String)
				}catch(NullPointerException e) {
					setErrno(21, DATA_ID);
					
					return "Error";
				}catch(Exception e) {}
				
				return "";
			});
			
			//Num Functions
			funcs.put("hexToDez", (lines, arg, DATA_ID) -> {
				try {
					int hex = Integer.parseInt(arg.trim().substring(2), 16);
					
					return hex + "";
				}catch(NumberFormatException e) {
					setErrno(11, DATA_ID);
					
					return "Error";
				}
			});
			
			//Character functions
			funcs.put("toValue", (lines, arg, DATA_ID) -> {
				if(arg.trim().length() != 1) {
					setErrno(12, DATA_ID);
					
					return "Error";
				}
				
				return (int)arg.trim().charAt(0) + "";
			});
			funcs.put("toChar", (lines, arg, DATA_ID) -> {
				try {
					int c = Integer.parseInt(arg.trim());
					
					return (char)c + "";
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}
			});
			
			//String functions
			funcs.put("strlen", (lines, arg, DATA_ID) -> arg.trim().length() + "");
			funcs.put("toUpper", (lines, arg, DATA_ID) -> arg.toUpperCase());
			funcs.put("toLower", (lines, arg, DATA_ID) -> arg.toLowerCase());
			funcs.put("trim", (lines, arg, DATA_ID) -> arg.trim());
			funcs.put("replace", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 3);
				if(funcArgs.length != 3) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				return funcArgs[0].trim().replaceAll(funcArgs[1].trim(), funcArgs[2].trim());
			});
			funcs.put("substring", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 3);
				if(funcArgs.length < 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				try {
					int start = Integer.parseInt(funcArgs[1].trim());
					
					if(funcArgs.length == 2) {
						return funcArgs[0].trim().substring(start);
					}else {
						int end = Integer.parseInt(funcArgs[2].trim());
						return funcArgs[0].trim().substring(start, end);
					}
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}catch(StringIndexOutOfBoundsException e) {
					setErrno(18, DATA_ID);
					
					return "Error";
				}
			});
			funcs.put("split", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 4);
				if(funcArgs.length < 3) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				String arrPtr = funcArgs[0].trim();
				String str = funcArgs[1].trim();
				String splitStr = funcArgs[2].trim();
				
				String[] arrTmp;
				if(funcArgs.length == 3) {
					arrTmp = str.split(splitStr);
				}else {
					try{
						arrTmp = str.split(splitStr, Integer.parseInt(funcArgs[3].trim()));
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				String comp = "func.arrayMake(" + arrPtr + ", " + arrTmp.length + ")\nfunc.arraySetAll(" + arrPtr;
				for(String s:arrTmp) {
					comp += ", " + s;
				}
				comp += ")";
				try {
					compileLangFile(new BufferedReader(new StringReader(comp)), DATA_ID);
				}catch(NullPointerException e) {
					setErrno(21, DATA_ID);
					
					return "Error";
				}catch(Exception e) {}
				
				int err;
				if((err = getAndClearErrno(DATA_ID)) != 0) {
					setErrno(err, DATA_ID);
					
					return "Error";
				}
				
				return "";
			});
			
			//Math functions
			funcs.put("rand", (lines, arg, DATA_ID) -> {
				return "" + RAN.nextInt(Integer.MAX_VALUE);
			});
			funcs.put("addi", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",");
				int sum = 0;
				for(int i = 0;i < funcArgs.length;i++) {
					funcArgs[i] = funcArgs[i].trim();
					try {
						sum += Integer.parseInt(funcArgs[i].trim());
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				return sum + "";
			});
			funcs.put("subi", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return Integer.parseInt(funcArgs[0].trim()) - Integer.parseInt(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("muli", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",");
				int prod = 1;
				for(int i = 0;i < funcArgs.length;i++) {
					funcArgs[i] = funcArgs[i].trim();
					try {
						prod *= Integer.parseInt(funcArgs[i].trim());
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				return prod + "";
			});
			funcs.put("divi", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						if(Integer.parseInt(funcArgs[1].trim()) == 0) {
							setErrno(14, DATA_ID);
							
							return "Error";
						}
						return Integer.parseInt(funcArgs[0].trim())/Integer.parseInt(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("modi", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						if(Integer.parseInt(funcArgs[1].trim()) == 0) {
							setErrno(14, DATA_ID);
							
							return "Error";
						}
						return Integer.parseInt(funcArgs[0].trim())%Integer.parseInt(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("andi", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Integer.parseInt(funcArgs[0].trim()) & Integer.parseInt(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("ori", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Integer.parseInt(funcArgs[0].trim()) | Integer.parseInt(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("xori", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Integer.parseInt(funcArgs[0].trim()) ^ Integer.parseInt(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("noti", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 1) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return ~Integer.parseInt(funcArgs[0].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("lshifti", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Integer.parseInt(funcArgs[0].trim()) << Integer.parseInt(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("rshifti", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Integer.parseInt(funcArgs[0].trim()) >> Integer.parseInt(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("rzshifti", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Integer.parseInt(funcArgs[0].trim()) >>> Integer.parseInt(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("addl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",");
				long sum = 0L;
				for(int i = 0;i < funcArgs.length;i++) {
					funcArgs[i] = funcArgs[i].trim();
					try {
						sum += Long.parseLong(funcArgs[i].trim());
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				return sum + "";
			});
			funcs.put("subl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return Long.parseLong(funcArgs[0].trim())-Long.parseLong(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("mull", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",");
				long prod = 1L;
				for(int i = 0;i < funcArgs.length;i++) {
					funcArgs[i] = funcArgs[i].trim();
					try {
						prod *= Long.parseLong(funcArgs[i].trim());
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				return prod + "";
			});
			funcs.put("divl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						if(Long.parseLong(funcArgs[1].trim()) == 0) {
							setErrno(14, DATA_ID);
							
							return "Error";
						}
						return Long.parseLong(funcArgs[0].trim())/Long.parseLong(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("modl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						if(Long.parseLong(funcArgs[1].trim()) == 0) {
							setErrno(14, DATA_ID);
							
							return "Error";
						}
						return Long.parseLong(funcArgs[0].trim()) % Long.parseLong(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("andl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Long.parseLong(funcArgs[0].trim()) & Long.parseLong(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("orl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Long.parseLong(funcArgs[0].trim()) | Long.parseLong(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("xorl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Long.parseLong(funcArgs[0].trim()) ^ Long.parseLong(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("notl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 1) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return ~Long.parseLong(funcArgs[0].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("lshiftl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Long.parseLong(funcArgs[0].trim()) << Long.parseLong(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("rshiftl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Long.parseLong(funcArgs[0].trim()) >> Long.parseLong(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("rzshiftl", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return (Long.parseLong(funcArgs[0].trim()) >>> Long.parseLong(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("addd", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",");
				double sum = 0.;
				for(int i = 0;i < funcArgs.length;i++) {
					funcArgs[i] = funcArgs[i].trim();
					try {
						sum += Double.parseDouble(funcArgs[i].trim());
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				return sum + "";
			});
			funcs.put("subd", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						return Double.parseDouble(funcArgs[0].trim())-Double.parseDouble(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("muld", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",");
				double prod = 1.;
				for(int i = 0;i < funcArgs.length;i++) {
					funcArgs[i] = funcArgs[i].trim();
					try {
						prod *= Double.parseDouble(funcArgs[i].trim());
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
				
				return prod + "";
			});
			funcs.put("divd", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						if(Double.parseDouble(funcArgs[1].trim()) == 0.) {
							setErrno(14, DATA_ID);
							
							return "Error";
						}
						return Double.parseDouble(funcArgs[0].trim())/Double.parseDouble(funcArgs[1].trim()) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("pow", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}else {
					try {
						if(Double.parseDouble(funcArgs[1].trim()) == 0.) {
							return "1";
						}
						return Math.pow(Double.parseDouble(funcArgs[0].trim()), Double.parseDouble(funcArgs[1].trim())) + "";
					}catch(NumberFormatException e) {
						setErrno(13, DATA_ID);
						
						return "Error";
					}
				}
			});
			funcs.put("sqrt", (lines, arg, DATA_ID) -> {
				try {
					return Math.sqrt(Double.parseDouble(arg.trim())) + "";
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}
			});
			funcs.put("dtoi", (lines, arg, DATA_ID) -> {
				try {
					return (int)Double.parseDouble(arg.trim()) + "";
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}
			});
			funcs.put("dtol", (lines, arg, DATA_ID) -> {
				try {
					return (long)Double.parseDouble(arg.trim()) + "";
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}
			});
			funcs.put("ceil", (lines, arg, DATA_ID) -> {
				try {
					return (long)Math.ceil(Double.parseDouble(arg.trim())) + "";
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}
			});
			funcs.put("floor", (lines, arg, DATA_ID) -> {
				try {
					return (long)Math.floor(Double.parseDouble(arg.trim())) + "";
				}catch(NumberFormatException e) {
					setErrno(13, DATA_ID);
					
					return "Error";
				}
			});
			
			//FuncPtr functions
			funcs.put("copyAfterFP", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				String to = funcArgs[0].trim();
				String from = funcArgs[1].trim();
				
				funcParser.copyAfterFP.get(DATA_ID).put(to, from);
				
				return "";
			});
			
			//Array functions
			funcs.put("arrayMake", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2); //arrPtr, length
				funcArgs[0] = funcArgs[0].trim();
				if(!funcArgs[0].startsWith("&")) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				try {
					int lenght = Integer.parseInt(funcArgs[1].trim());
					
					if(lenght < 0) {
						setErrno(15, DATA_ID);
						
						return "Error";
					}else if(lenght == 0) {
						setErrno(16, DATA_ID);
						
						return "Error";
					}
					
					DataObject oldData = data.get(DATA_ID).varTmp.get(funcArgs[0]);
					if(oldData != null && oldData.isFinalData()) {
						setErrno(1, DATA_ID);
						
						return "Error";
					}
					DataObject[] arr = new DataObject[lenght];
					if(oldData != null)
						oldData.setArray(arr);
					else
						data.get(DATA_ID).varTmp.put(funcArgs[0], new DataObject().setArray(arr));
					
					for(int i = 0;i < arr.length;i++)
						arr[i] = new DataObject().setNull();
				}catch(NumberFormatException e) {
					setErrno(17, DATA_ID);
					
					return "Error";
				}
				
				return ""; //No return func
			});
			funcs.put("arraySet", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 3); //arrPtr, index, value
				funcArgs[0] = funcArgs[0].trim();
				if(!funcArgs[0].startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(funcArgs[0])) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				DataObject[] arr = data.get(DATA_ID).varTmp.get(funcArgs[0]).getArray();
				
				try {
					int index = Integer.parseInt(funcArgs[1].trim());
					
					if(index < 0) {
						setErrno(15, DATA_ID);
						
						return "Error";
					}else if(index >= arr.length) {
						setErrno(18, DATA_ID);
						
						return "Error";
					}
					
					arr[index].setText(funcArgs[2].trim());
				}catch(NumberFormatException e) {
					setErrno(17, DATA_ID);
					
					return "Error";
				}
				
				return ""; //No return func
			});
			funcs.put("arraySetAll", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(","); //arrPtr, value, ...
				funcArgs[0] = funcArgs[0].trim();
				if(!funcArgs[0].startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(funcArgs[0])) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				funcArgs[1] = funcArgs[1].trim();
				DataObject[] arr = data.get(DATA_ID).varTmp.get(funcArgs[0]).getArray();
				
				if(funcArgs.length == 2) {
					for(int i = 0;i < arr.length;i++) {
						arr[i].setText(funcArgs[1].trim());
					}
				}else if(funcArgs.length == arr.length+1) {
					for(int i = 0;i < arr.length;i++) {
						arr[i].setText(funcArgs[i + 1].trim());
					}
				}else {
					setErrno(19, DATA_ID);
					
					return "Error";
				}
				
				return ""; //No return func
			});
			funcs.put("arrayGet", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2); //arrPtr, index
				funcArgs[0] = funcArgs[0].trim();
				if(!funcArgs[0].startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(funcArgs[0])) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				DataObject[] arr = data.get(DATA_ID).varTmp.get(funcArgs[0]).getArray();
				int index = Integer.parseInt(funcArgs[1].trim());
				
				try {
					if(index < 0 || index >= arr.length) {
						setErrno(18, DATA_ID);
						
						return "Error";
					}
				}catch(NumberFormatException e) {
					setErrno(17, DATA_ID);
					
					return "Error";
				}
				
				return arr[index].getText();
			});
			funcs.put("arrayGetAll", (lines, arg, DATA_ID) -> {
				String tmp = "";
				
				arg = arg.trim();
				if(!arg.startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(arg)) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				for(DataObject val:data.get(DATA_ID).varTmp.get(arg).getArray()) {
					tmp += val + ", ";
				}
				
				return tmp.substring(0, tmp.lastIndexOf(", "));
			});
			funcs.put("arrayLength", (lines, arg, DATA_ID) -> {
				arg = arg.trim();
				if(!arg.startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(arg)) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				return data.get(DATA_ID).varTmp.get(arg).getArray().length + "";
			});
			funcs.put("arrayForEach", (lines, arg, DATA_ID) -> {
				String[] funcArgs = arg.split(",", 2);
				if(funcArgs.length != 2) {
					setErrno(8, DATA_ID);
					
					return "Error";
				}
				
				String arrName = funcArgs[0].trim();
				if(!arrName.startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(arrName)) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				String funcPtr = funcArgs[1].trim();
				if(!funcPtr.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(funcPtr)) {
					setErrno(20, DATA_ID);
					
					return "Error";
				}
				
				DataObject[] arr = data.get(DATA_ID).varTmp.get(arrName).getArray();
				for(DataObject element:arr) {
					funcParser.compileFunc(funcPtr, element.getText(), DATA_ID);
				}
				
				return "";
			});
			funcs.put("randChoice", (lines, arg, DATA_ID) -> {
				arg = arg.trim();
				if(!arg.startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(arg)) {
					//No array Pointer
					String[] funcArgs = arg.split(",");
					if(funcArgs.length < 1) {
						setErrno(8, DATA_ID);
						
						return "Error";
					}else if(funcArgs.length == 1) {
						return funcArgs[0];
					}
					return funcArgs[RAN.nextInt(funcArgs.length)];
				}
				
				DataObject[] arr = data.get(DATA_ID).varTmp.get(arg).getArray();
				
				if(arr.length < 1) {
					setErrno(16, DATA_ID);
					
					return "Error";
				}else if(arr.length == 1) {
					return arr[0].getText();
				}
				return arr[RAN.nextInt(arr.length)].getText();
			});
			funcs.put("arrayDelete", (lines, arg, DATA_ID) -> {
				arg = arg.trim();
				if(!arg.startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(arg)) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				
				DataObject[] arr = data.get(DATA_ID).varTmp.get(arg).getArray();
				for(DataObject element:arr)
					element.setNull();
				
				return ""; //No return func
			});
			funcs.put("arrayClear", (lines, arg, DATA_ID) -> {
				arg = arg.trim();
				if(!arg.startsWith("&") || !data.get(DATA_ID).varTmp.containsKey(arg)) {
					setErrno(10, DATA_ID);
					
					return "Error";
				}
				data.get(DATA_ID).varTmp.remove(arg);
				
				return ""; //No return func
			});
		}
		
		public Compiler(String langPath, TerminalIO term) {
			this.langPath = langPath;
			this.term = term;
			
			createDataMap(0);
		}
		
		public void createDataMap(final int DATA_ID) {
			data.put(DATA_ID, new Data());
			
			resetVarsAndFuncPtrs(DATA_ID);
		}
		
		public void resetVarsAndFuncPtrs(final int DATA_ID) {
			data.get(DATA_ID).varTmp.clear();
			
			//Final vars
			data.get(DATA_ID).varTmp.put("$LANG_COMPILER_VERSION", new DataObject(VERSION, true));
			data.get(DATA_ID).varTmp.put("$LANG_PATH", new DataObject(langPath, true));
			data.get(DATA_ID).varTmp.put("$LANG_RAND_MAX", new DataObject("" + (Integer.MAX_VALUE - 1), true));
			
			//Not final vars
			setErrno(0, DATA_ID); //Set $LANG_ERRNO
		}
		public void resetVars(final int DATA_ID) {
			String[] keys = data.get(DATA_ID).varTmp.keySet().toArray(new String[0]);
			for(int i = data.get(DATA_ID).varTmp.size() - 1;i > -1;i--) {
				if(keys[i].startsWith("$") && !keys[i].startsWith("$LANG_")) {
					data.get(DATA_ID).varTmp.remove(keys[i]);
				}
			}
			
			//Not final vars
			setErrno(0, DATA_ID); //Set $LANG_ERRNO
		}
		
		public void setErrno(int errno, final int DATA_ID) {
			data.get(DATA_ID).varTmp.computeIfAbsent("$LANG_ERRNO", key -> new DataObject());
			
			data.get(DATA_ID).varTmp.get("$LANG_ERRNO").setText("" + errno);
		}
		public DataObject setErrnoErrorObject(int errno, final int DATA_ID) {
			setErrno(errno, DATA_ID);
			
			return new DataObject().setError(new ErrorObject(errno));
		}
		public int getAndClearErrno(final int DATA_ID) {
			int ret = Integer.parseInt(data.get(DATA_ID).varTmp.get("$LANG_ERRNO").getText());
			
			setErrno(0, DATA_ID); //Reset errno
			
			return ret;
		}
		
		public Map<Integer, Data> getData() {
			return data;
		}
		
		public void compileLangFile(BufferedReader lines, final int DATA_ID) throws Exception {
			while(lines.ready()) {
				String str = lines.readLine();
				if(str == null)
					break;
				
				if(str.trim().length() > 0 && !str.trim().isEmpty()) {
					str = str.replaceAll("^\\s*", ""); //Remove whitespaces at the beginning
					
					//Lang data and compiler args
					if(str.startsWith("lang.")) {
						if(str.startsWith("lang.version = ")) {
							compileLine(lines, str, DATA_ID);
							String langVer = data.get(DATA_ID).lang.get("lang.version");
							
							if(!langVer.equals(VERSION)) {
								if(term != null) {
									if(VERSION.compareTo(langVer) > 0) {
										term.logln(Level.WARNING, "Lang file's version is older than this version! Maybe the lang file won't be compile right!",
										Compiler.class);
									}else {
										term.logln(Level.ERROR, "Lang file's version is newer than this version! Maybe the lang file won't be compile right!",
										Compiler.class);
									}
								}
							}
							
							continue;
						}
					}
					
					//If, elif, else, endif
					if(str.startsWith("con.")) {
						ifParser.executeIf(lines, str, DATA_ID);
						
						continue; //Compile next Line
					}
					
					compileLine(lines, str, DATA_ID);
				}
			}
		}
		
		public String compileLine(BufferedReader lines, String line, final int DATA_ID) {
			line = line.replaceAll("^\\s*", ""); //Remove whitespaces at the beginning
			
			//Comments
			if(line.startsWith("#"))
				return "";
			line = line.split("(?<!\\\\)#")[0]; //Splits at #, but not at \# (RegEx look behind)
			line = line.replace("\\#", "#");
			
			//For newLine
			line = line.replace("\\n", "\n");
			
			//Save funcPtr
			if(line.startsWith("fp.") && line.contains(" = ")) {
				funcParser.saveFuncPtr(lines, line, DATA_ID);
				
				return "";
			}
			
			//Var
			if(line.contains("$")) {
				line = varParser.replaceVarsWithValue(lines, line, DATA_ID);
				
				if(line == null)
					return "";
			}
			
			//Execute Functions and FuncPtr
			if(line.contains("func.") || line.contains("fp.")) { //... .funcName(params/nothing) ...
				line = funcParser.executeFunc(lines, line, DATA_ID);
			}
			
			//Linker
			if(line.contains("linker.")) {
				line = linkerParser.compileLine(line, DATA_ID);
			}
			
			//FuncPtr return
			if(line.trim().startsWith("return")) {
				if(line.trim().matches("return .*")) {
					funcParser.funcReturnTmp = line.substring(7).trim(); //return func value
				}else {
					funcParser.funcReturnTmp = "";
				}
				
				//Go to end of stream (return)
				try {
					while(lines.ready()) {
						if(lines.readLine() == null) {
							break;
						}
					}
				}catch(IOException e) {
					term.logStackTrace(e, Compiler.class);
				}
				
				return "";
			}
			
			if(line.contains(" = ")) {
				String[] string = line.split(" = ", 2);
				data.get(DATA_ID).lang.put(string[0], string[1].replaceAll("\\\\s", " ")); //Put lang key and lang value in lang map
			}
			
			return line;
		}
		
		//Classes for variable data
		public static class FunctionPointerObject {
			/**
			 * Normal function pointer
			 */
			public static int NORMAL = 0;
			/**
			 * Pointer to a predefined function
			 */
			public static int PREDEFINED = 1;
			/**
			 * Function which is defined in the language, were the Compiler/Interpreter is defined
			 */
			public static int EXTERNAL = 2;
			
			private final String head;
			private final String body;
			private final BiFunction<String, Integer, String> externalFunction;
			private final int functionPointerType;
			
			public FunctionPointerObject(String head, String body) {
				this.head = head;
				this.body = body;
				this.externalFunction = null;
				this.functionPointerType = NORMAL;
			}
			/**
			 * For pointer to predefined function
			 */
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
		public static class ClassObject {
			private final Map<String, DataObject> attributes = new HashMap<>();
			private final String className;
			private final String packageName;
			private final ClassObject superClass;
			private final boolean classDefinition; //Is true if the class object is only an class definition else it is an actual instance of the class
			
			public ClassObject(String className, String packageName, ClassObject superClass, boolean classDefinition) {
				this.className = className;
				this.packageName = packageName;
				this.superClass = superClass;
				this.classDefinition = classDefinition;
			}
			
			public void setAttribute(String name, DataObject data) {
				attributes.put(name, data);
			}
			public DataObject getAttribute(String name) {
				return attributes.get(name);
			}
			
			public Map<String, DataObject> getAttributes() {
				return attributes;
			}
			
			public String getPackageName() {
				return packageName;
			}
			
			public ClassObject getSuperClass() {
				return superClass;
			}
			
			public boolean isClassDefinition() {
				return classDefinition;
			}
			
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
		public static class ErrorObject {
			private final int err;
			
			public ErrorObject(int err) {
				this.err = err;
			}
			
			public int getErrno() {
				return err;
			}
			
			public String getErrmsg() {
				return ERROR_STRINGS[err];
			}
			
			@Override
			public String toString() {
				return "Error";
			}
		}
		public static enum DataType {
			TEXT, ARRAY, FUNCTION_POINTER, CLASS, ERROR, NULL, VOID;
		}
		public static class DataObject {
			private DataType type;
			private String txt;
			private DataObject[] arr;
			private FunctionPointerObject fp;
			private ClassObject classObject;
			private ErrorObject error;
			private boolean finalData;
			
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
				this.fp = dataObject.fp;
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
					case FUNCTION_POINTER:
						return fp.toString();
					case CLASS:
						return classObject.toString();
					case ERROR:
						return error.toString();
					case NULL:
						return "null";
					case VOID:
						return "";
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
			
			public DataObject setClassObject(ClassObject classObject) {
				if(finalData)
					return this;
				
				this.type = DataType.CLASS;
				this.classObject = classObject;
				
				return this;
			}
			
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
			
			public DataObject setFinalData(boolean finalData) {
				this.finalData = finalData;
				
				return this;
			}
			
			public boolean isFinalData() {
				return finalData;
			}
			
			public DataType getType() {
				return type;
			}
			
			@Override
			public String toString() {
				return getText();
			}
		}
		public static class CombinedDataObject {
			private List<DataObject> dataObjects;
			
			public CombinedDataObject() {
				dataObjects = new LinkedList<>();
			}
			public CombinedDataObject(List<DataObject> dataObjects) {
				this.dataObjects = new LinkedList<>(dataObjects);
			}
			public CombinedDataObject(CombinedDataObject combinedDataObject) {
				dataObjects = new LinkedList<>(combinedDataObject.dataObjects);
			}
			
			public void addDataObject(DataObject object) {
				dataObjects.add(object);
			}
			public void addDataObject(int index, DataObject object) {
				dataObjects.add(index, object);
			}
			
			public void remove(int index) {
				dataObjects.remove(index);
			}
			public void clear() {
				dataObjects.clear();
			}
			
			public DataObject get(int index) {
				return dataObjects.get(index);
			}
			
			public int size() {
				return dataObjects.size();
			}
			
			public CombinedDataObject subList(int fromIndex, int toIndex) {
				return new CombinedDataObject(dataObjects.subList(fromIndex, toIndex));
			}
			
			public DataObject[] toArray() {
				return dataObjects.toArray(new DataObject[dataObjects.size()]);
			}
			
			@Override
			public String toString() {
				StringBuilder build = new StringBuilder();
				dataObjects.forEach(build::append);
				return build.toString();
			}
		}
		public static class Data {
			public final Map<String, String> lang = new HashMap<>();
			public final Map<String, DataObject> varTmp = new HashMap<>();
		}
		
		//Classes for compiling lang file
		private class LinkerParser {
			private LinkerParser() {}
			
			private String compileLine(String line, final int DATA_ID) {
				int indexStart, indexEnd, indexEndForLine;
				
				while(line.contains("linker.")) {
					indexStart = line.indexOf("linker.");
					String tmp = line.substring(indexStart + 7);
					indexEndForLine = indexStart + 7;
					
					indexEnd = tmp.indexOf(')'); //First ')' after "linker."
					tmp = tmp.substring(0, indexEnd);
					
					indexEndForLine += indexEnd + 1; //"tmp.indexOf(')')" -> after "linker.*(*)"
					
					//Linker functions
					if(tmp.startsWith("link(")) {
						tmp = tmp.substring(tmp.indexOf('(') + 1); //After "linker.*("
						
						if(tmp.endsWith(".lang")) {
							String absolutePath;
							if(new File(tmp).isAbsolute())
								absolutePath = tmp;
							else
								absolutePath = langPath + File.separator + tmp;
							
							tmp = linkLangFile(absolutePath, DATA_ID);
						}else { //No .lang file
							setErrno(3, DATA_ID);
							
							tmp = "-1";
						}
					}else if(tmp.startsWith("bindLibrary(")) {
						tmp = tmp.substring(tmp.indexOf('(') + 1); //After "linker.*("
						
						if(tmp.endsWith(".lang")) {
							String absolutePath;
							if(new File(tmp).isAbsolute())
								absolutePath = tmp;
							else
								absolutePath = langPath + File.separator + tmp;
							
							tmp = bindLibraryLangFile(absolutePath, DATA_ID);
						}else { //No .lang file
							setErrno(3, DATA_ID);
							
							tmp = "-1";
						}
					}
					
					line = line.substring(0, indexStart) + tmp + line.substring(indexEndForLine);
				}
				
				return line;
			}
			
			private String linkLangFile(String linkLangFile, final int DATA_ID) {
				final int NEW_DATA_ID = DATA_ID + 1;
				
				String ret = "0";
				
				String langPathTmp = linkLangFile;
				langPathTmp = langPathTmp.substring(0, langPathTmp.lastIndexOf(File.separator)); //Remove ending ("/*.lang") for $LANG_PATH
				
				//Change lang path for createDataMap
				String oldLangPath = langPath;
				langPath = langPathTmp;
				createDataMap(NEW_DATA_ID);
				
				try {
					BufferedReader reader = new BufferedReader(new FileReader(new File(linkLangFile)));
					try {
						compileLangFile(reader, NEW_DATA_ID);
					}catch(Exception e) {
						setErrno(4, DATA_ID);
						ret = "-1";
					}finally {
						reader.close();
					}
				}catch(IOException e) {
					setErrno(4, DATA_ID);
					ret = "-1";
				}
				
				if(ret.equals("0")) { //If no error
					data.get(NEW_DATA_ID).lang.forEach((k, v) -> { //Copy linked translation map (not "lang.* = *") to the "link caller"'s translation map
						if(!k.startsWith("lang.")) {
							data.get(DATA_ID).lang.put(k, v); //Copy to "old" DATA_ID
						}
					});
				}
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				//Set lang path to old lang path
				langPath = oldLangPath;
				return ret;
			}
			
			private String bindLibraryLangFile(String linkLangFile, final int DATA_ID) {
				final int NEW_DATA_ID = DATA_ID + 1;
				
				String ret = "0";
				
				String langPathTmp = linkLangFile;
				langPathTmp = langPathTmp.substring(0, langPathTmp.lastIndexOf(File.separator)); //Remove ending ("/*.lang") for $LANG_PATH
				
				//Change lang path for createDataMap
				String oldLangPath = langPath;
				langPath = langPathTmp;
				createDataMap(NEW_DATA_ID);
				
				try {
					BufferedReader reader = new BufferedReader(new FileReader(new File(linkLangFile)));
					try {
						compileLangFile(reader, NEW_DATA_ID);
					}catch(Exception e) {
						setErrno(4, DATA_ID);
						ret = "-1";
					}finally {
						reader.close();
					}
				}catch(IOException e) {
					setErrno(4, DATA_ID);
					ret = "-1";
				}
				
				if(ret.equals("0")) { //If no error
					//Copy all vars, arrPtrs and funcPtrs
					data.get(NEW_DATA_ID).varTmp.forEach((name, val) -> {
						DataObject oldData = data.get(DATA_ID).varTmp.get(name);
						if(!name.startsWith("$LANG") && (oldData == null || !oldData.isFinalData())) { //No LANG data vars and no final data
							data.get(DATA_ID).varTmp.put(name, val);
						}
					});
				}
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				//Set lang path to old lang path
				langPath = oldLangPath;
				return ret;
			}
		}
		private class IfParser {
			private IfParser() {}
			
			private boolean checkIf(String ifCondition) {
				ifCondition = ifCondition.replaceAll("\\s*", ""); //Remove Whitespace
				
				//Replace brackets with 0 or 1
				if(ifCondition.contains("(") && ifCondition.contains(")")) {
					int bracketsCount = 0, indexTmp;
					for(int i = 0;i < ifCondition.length();i++) {
						char c = ifCondition.charAt(i);
						
						if(c == '(') {
							boolean invert = i > 0 && ifCondition.charAt(i - 1) == '!';
							indexTmp = i;
							while(true) { //While brackets count != 0 and not first run
								c = ifCondition.charAt(i++); //Get char at indexEnd (increase later)
								if(c == '(') { //If char == '(' -> bracketsCount++;
									bracketsCount++;
								}else if(c == ')') { //Else if char == ')' -> bracketsCount--;
									bracketsCount--;
									if(bracketsCount == 0) { //When all brackets have been closed -> check condition
										boolean b = checkIf(ifCondition.substring(indexTmp+1, i-1)); //Check all between '(' and ')'
										if(invert) {
											b = !b;
											indexTmp--; //Include ! for replace
										}
										
										ifCondition = ifCondition.substring(0, indexTmp) + (b?1:0) + ifCondition.substring(i); //Replace
										
										i = indexTmp; //Set i to old pos.
										
										break;
									}
								}
							}
						}
					}
				}
				
				//Replace AND with 0 or 1
				if(ifCondition.contains("&&")) {
					boolean leftSide = checkIf(ifCondition.substring(0, ifCondition.indexOf("&&")));
					boolean rightSide = checkIf(ifCondition.substring(ifCondition.indexOf("&&") + 2));
					
					return leftSide && rightSide;
				}
				
				//Replace OR with 0 or 1
				if(ifCondition.contains("||")) {
					boolean leftSide = checkIf(ifCondition.substring(0, ifCondition.indexOf("||")));
					boolean rightSide = checkIf(ifCondition.substring(ifCondition.indexOf("||") + 2));
					
					return leftSide || rightSide;
				}
				
				if(ifCondition.contains("=") || ifCondition.contains("<") || ifCondition.contains(">") || ifCondition.contains("!")) {
					char tmp;
					int invert;
					for(int i = 0;i < ifCondition.length();i++) {
						char c = ifCondition.charAt(i);
						
						if(c == '=' && ((tmp = ifCondition.charAt(i-1)) == '=' || tmp == '!')) { //Replace "==" and "!=" with 0 or 1
							invert = (tmp == '!')?1:0; //Uses "xor" after
							
							int strIndexTmpLeft = i, strIndexTmpRight = i+1;
							String strLeft = "", strRight = "";
							while(true) {
								try {
									strRight = ifCondition.substring(i+1, ++strIndexTmpRight);
									if(strRight.endsWith("=") || strRight.endsWith("<") || strRight.endsWith(">") || strRight.endsWith("!")) {
										strRight = strRight.substring(0, strRight.length()-1);
										
										break;
									}
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									break;
								}
							}
							while(true) {
								try {
									strLeft = ifCondition.substring(--strIndexTmpLeft, i-1);
									if(strRight.startsWith("=") || strRight.startsWith("<") || strRight.startsWith(">") || strRight.startsWith("!")) {
										strRight = strRight.substring(1);
										
										break;
									}
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									break;
								}
							}
							
							ifCondition = ifCondition.substring(0, strIndexTmpLeft+1) + (((strRight.equals(strLeft))?1:0)^invert) + ifCondition.substring(strIndexTmpRight-1); //Replace
							i = strIndexTmpLeft;
							
							continue;
						}
						
						if(c == '<') { //Replace "<" and "<=" with 0 or 1
							int x = (ifCondition.charAt(i+1) == '=')?2:1;
							
							int numIndexTmpLeft = i, numIndexTmpRight = i+x, numLeft = 0, numRight = 0;
							while(true) {
								try {
									numRight = Integer.parseInt(ifCondition.substring(i+x, ++numIndexTmpRight));
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									if(numIndexTmpLeft == i+x+1) { //No number (char)
										numRight = ifCondition.charAt(i+x);
									}
									break;
								}
							}
							while(true) {
								try {
									numLeft = Integer.parseInt(ifCondition.substring(--numIndexTmpLeft, i));
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									if(numIndexTmpLeft == i-1) { //No number (char)
										numRight = ifCondition.charAt(i-1);
									}
									break;
								}
							}
							
							//numLeft "<" or "<=" numRight
							if(x == 2) {
								numRight = (numLeft <= numRight)?1:0;
							}else {
								numRight = (numLeft < numRight)?1:0;
							}
							
							ifCondition = ifCondition.substring(0, numIndexTmpLeft+1) + numRight + ifCondition.substring(numIndexTmpRight-1); //Replace
							i = numIndexTmpLeft;
							
							continue;
						}
						
						if(c == '>') { //Replace ">" and ">=" with 0 or 1
							int x = (ifCondition.charAt(i+1) == '=')?2:1;
							
							int numIndexTmpLeft = i, numIndexTmpRight = i+x, numLeft = 0, numRight = 0;
							while(true) {
								try {
									numRight = Integer.parseInt(ifCondition.substring(i+x, ++numIndexTmpRight));
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									if(numIndexTmpLeft == i+x+1) { //No number (char)
										numRight = ifCondition.charAt(i+x);
									}
									break;
								}
							}
							while(true) {
								try {
									numLeft = Integer.parseInt(ifCondition.substring(--numIndexTmpLeft, i));
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									if(numIndexTmpLeft == i-1) { //No number (char)
										numRight = ifCondition.charAt(i-1);
									}
									
									break;
								}
							}
							
							//numLeft ">" or ">=" numRight
							if(x == 2) {
								numRight = (numLeft >= numRight)?1:0;
							}else {
								numRight = (numLeft > numRight)?1:0;
							}
							
							ifCondition = ifCondition.substring(0, numIndexTmpLeft+1) + numRight + ifCondition.substring(numIndexTmpRight-1); //Replace
							i = numIndexTmpLeft;
						}
					}
				}
				
				if(ifCondition.contains("!")) {
					for(int i = 0;i < ifCondition.length();i++) {
						char c = ifCondition.charAt(i);
						
						if(c == '!') { //Replace "not" with 0 or 1
							int numIndexTmp = i+1, num = 1; //If only "not" (e.g.: if(!)) -> return !1 => return 0 => return false
							while(true) {
								try {
									num = Integer.parseInt(ifCondition.substring(i+1, ++numIndexTmp));
								}catch(NumberFormatException|StringIndexOutOfBoundsException e) {
									break;
								}
							}
							
							num = checkIf(num + "")?0:1; //Convert output of checkIf for "not"
							
							ifCondition = ifCondition.substring(0, i) + num + ifCondition.substring(numIndexTmp-1); //Replace
							
							continue;
						}
					}
				}
				
				//For end
				try {
					return Integer.parseInt(ifCondition) != 0;
				}catch(NumberFormatException e) {
					return false;
				}
			}
			
			public void executeIf(BufferedReader lines, String line, final int DATA_ID) throws Exception {
				line = line.trim().substring(4); //Remove whitespace and "con."
				
				if(line.startsWith("if(")) {
					line = compileLine(lines, line, DATA_ID);
					if(line.isEmpty())
						line = "0";
					else
						line = line.substring(3, line.lastIndexOf(')'));
					
					String tmp = lines.readLine();
					if(checkIf(line)) { //True
						while(true) {
							if(tmp == null) { //"return" of FuncPtr
								return;
							}
							
							if(tmp.trim().startsWith("con.")) { //If line startsWith "con."
								tmp = compileLine(lines, tmp, DATA_ID);
								if(tmp.isEmpty())
									continue;
								
								if(tmp.trim().substring(4).startsWith("if")) {
									executeIf(lines, tmp, DATA_ID); //Execute inner if
								}
								
								tmp = tmp.trim().substring(4);
								if(tmp.startsWith("endif") || tmp.startsWith("elif") || tmp.startsWith("else")) { //Go to end of if (after "endif")
									while(!tmp.startsWith("endif")) { //Go to "endif"
										try {
											tmp = lines.readLine().trim().substring(4); //Remove "con."
										}catch(Exception e) {
											if(!lines.ready()) {
												return;
											}else {
												tmp = ""; //If line hasn't five chars
											}
										}
									}
									return;
								}
							}else {
								compileLine(lines, tmp, DATA_ID); //Compile lines
							}
							
							if(lines.ready()) {
								tmp = lines.readLine();
							}else {
								return;
							}
						}
					}else { //False
						while(true) {
							if(tmp.trim().startsWith("con.")) { //If line startsWith "con."
								tmp = compileLine(lines, tmp, DATA_ID);
								if(tmp.isEmpty())
									continue;
								
								tmp = tmp.trim().substring(4);
								if(tmp.startsWith("endif")) {
									return;
								}else if(tmp.startsWith("elif")) {
									executeIf(lines, "con." + tmp.substring(2), DATA_ID); //Execute "elif" as "if"
									
									return;
								}else if(tmp.startsWith("else")) {
									executeIf(lines, "con.if(1)", DATA_ID); //Execute "else" as "if(1)"
									
									return;
								}else if(tmp.startsWith("if")) {
									executeIf(lines, "con." + tmp, DATA_ID); //Execute inner if statement
								}
							}
							
							if(lines.ready()) {
								tmp = lines.readLine();
							}else {
								return;
							}
						}
					}
				}
			}
		}
		private class VarParser {
			private VarParser() {}
			
			/**
			 * @return the modified line<br>if null -> continue
			 */
			public String replaceVarsWithValue(BufferedReader lines, String line, final int DATA_ID) {
				line = line.trim();
				
				//If not tmp contains " = " -> var is null
				if(line.startsWith("$")) { //Set var
					//If tmp contains a mapping to a value for var
					if(line.matches("\\$LANG_.*")) { //Illegal var
						setErrno(1, DATA_ID);
						
						return null;
					}
					
					if(line.contains(" = ")) {
						String[] string = line.split(" = ", 2); //Split tmp to var name and var value
						
						string[1] = replaceVarsWithValue(string[1], DATA_ID);
						string[1] = replaceAllVarPtrsWithVar(string[1]);
						if((string[1].contains("fp.") || string[1].contains("func.")) && string[1].contains("(") && string[1].contains(")"))
							string[1] = funcParser.executeFunc(lines, string[1], DATA_ID);
						if(string[1].contains("linker.")) //If string[1] contains a linker function
							string[1] = linkerParser.compileLine(string[1], DATA_ID); //Execute linker functions
						
						//Put var name and var value to the var tmp map
						DataObject oldValue = data.get(DATA_ID).varTmp.get(string[0]);
						if(oldValue != null && oldValue.isFinalData()) {
							setErrno(1, DATA_ID);
							
							return null;
						}
						if(oldValue != null)
							oldValue.setText(string[1]);
						else
							data.get(DATA_ID).varTmp.put(string[0], new DataObject(string[1]));
					}else {
						DataObject oldValue = data.get(DATA_ID).varTmp.get(line);
						if(oldValue != null && oldValue.isFinalData()) {
							setErrno(1, DATA_ID);
							
							return null;
						}
						if(oldValue != null)
							oldValue.setNull();
						else
							data.get(DATA_ID).varTmp.put(line, new DataObject().setNull());
					}
					
					return null;
				}else { //Get var
					line = replaceVarsWithValue(line, DATA_ID);
					line = replaceAllVarPtrsWithVar(line);
					return line;
				}
			}
			
			private String replaceVarsWithValue(String line, final int DATA_ID) {
				//Replace "%$" with "$" for start
				if(line.startsWith("%$"))
					line = line.substring(1);
				
				//[line, returnValue]
				String[] lineCopy = new String[] {line, ""};
				boolean[] varNotReplacedFlag = new boolean[1];
				while(lineCopy[0].length() > 0) {
					int indexStartVar = lineCopy[0].indexOf('$');
					//Contains no "$"
					if(indexStartVar == -1) {
						lineCopy[1] += lineCopy[0];
						break;
					}
					
					lineCopy[1] += lineCopy[0].substring(0, indexStartVar);
					//Escaped "$" -> Remove "$" from line
					if(lineCopy[1].endsWith("\\")) {
						indexStartVar++;
						lineCopy[1] = lineCopy[1].substring(0, lineCopy[1].length() - 1) + "$";
					}
					lineCopy[0] = lineCopy[0].substring(indexStartVar);
					
					if(lineCopy[0].startsWith("$")) {
						varNotReplacedFlag[0] = true;
						data.get(DATA_ID).varTmp.keySet().stream().filter(varName -> {
							return lineCopy[0].startsWith(varName);
						}).sorted((s0, s1) -> { //Sort keySet from large to small length (e.g.: $ab and $abc)
							if(s0.length() == s1.length())
								return 0;
							
							return (s0.length() < s1.length())?1:-1;
						}).findFirst().ifPresent(varName -> {
							varNotReplacedFlag[0] = false;
							
							lineCopy[1] += data.get(DATA_ID).varTmp.get(varName);
							lineCopy[0] = lineCopy[0].substring(varName.length());
						});
						
						//Skip "$" if no var was found
						if(varNotReplacedFlag[0]) {
							lineCopy[0] = lineCopy[0].substring(1);
							lineCopy[1] += "$";
						}
					}
				}
				
				return lineCopy[1];
			}
			
			private String replaceAllVarPtrsWithVar(String line) {
				StringBuilder newLine = new StringBuilder();
				
				while(!line.isEmpty()) {
					char c = line.charAt(0);
					newLine.append(c);
					line = line.substring(1);
					
					if(c == '$' && !line.isEmpty()) {
						if(line.charAt(0) == '[' && line.contains("]")) {
							line = line.substring(1); //Remove '['
							
							while((c = line.charAt(0)) != ']') { //Add all to ']' to "newLine"
								newLine.append(c);
								line = line.substring(1);
							}
							
							line = line.substring(1); //Remove ']'
						}
					}
				}
				
				return newLine.toString();
			}
		}
		private class FuncParser {
			public Map<Integer, Map<String, String>> copyAfterFP = new HashMap<>(); //<DATA_ID (of function), <to, from>>
			
			private String funcReturnTmp = "";
			
			private FuncParser() {}
			
			public void saveFuncPtr(BufferedReader lines, String line, final int DATA_ID) {
				StringBuilder build = new StringBuilder();
				String tmp;
				
				String funcName = line.split(" = ")[0].trim();
				if(!funcName.startsWith("fp.")) {
					setErrno(5, DATA_ID);
					
					return;
				}
				
				String funcHead = line.split(" = ")[1].trim();
				FunctionPointerObject fp = null;
				
				if(line.contains(") -> {")) { //FuncPtr definition
					funcHead = funcHead.substring(funcHead.indexOf('(') + 1);
					funcHead = funcHead.substring(0, funcHead.indexOf(')'));
					
					try {
						int bracketsCount = 1; //First '{' is in head
						//For funcPtr in funcPtr
						while(true) { //While brackets count != 0
							tmp = lines.readLine();
							if(tmp == null) {
								setErrno(23, DATA_ID);
								
								return;
							}
							if(tmp.trim().endsWith("{")) { //If last char == '{' -> bracketsCount++;
								bracketsCount++;
							}else if(tmp.trim().startsWith("}")) { //Else if first char == '}' -> bracketsCount--;
								bracketsCount--;
								if(bracketsCount == 0) //When all brackets have been closed -> break
									break;
							}
							build.append(tmp.trim());
							build.append("\n");
						}
						
						if(build.length() > 0) //Fix for empty function body
							build.deleteCharAt(build.length() - 1); //Remove "tail '\n'"
						fp = new FunctionPointerObject(funcHead, build.toString());
					}catch(IOException e) {
						term.logStackTrace(e, FuncParser.class);
					}
				}else if(line.contains(") -> ")) { //One-line function definition
					funcHead = funcHead.substring(funcHead.indexOf('(') + 1);
					funcHead = funcHead.substring(0, funcHead.indexOf(')'));
					
					fp = new FunctionPointerObject(funcHead, line.split("\\) -> ", 2)[1].trim());
				}else if(funcHead.startsWith("func.")) { //"Copy" predefined function
					fp = new FunctionPointerObject(funcHead);
				}else { //Copy funcPtr
					if(!(funcHead.startsWith("fp.") && data.get(DATA_ID).varTmp.containsKey(funcHead))) {
						setErrno(5, DATA_ID);
						
						return;
					}
					
					fp = data.get(DATA_ID).varTmp.get(funcHead).getFunctionPointer();
				}
				
				DataObject oldValue = data.get(DATA_ID).varTmp.get(funcName);
				if(oldValue != null && oldValue.isFinalData()) {
					setErrno(1, DATA_ID);
					
					return;
				}
				if(oldValue != null)
					oldValue.setFunctionPointer(fp);
				else
					data.get(DATA_ID).varTmp.put(funcName, new DataObject().setFunctionPointer(fp));
			}
			
			public String executeFunc(BufferedReader lines, String line, final int DATA_ID) {
				String lineCopy = line; //Copy pointer to line
				String tmp; //String tmp for brackets count
				int bracketsCount, indexStart, indexEnd, oldIndexStart = 0;
				
				//while line contains functions
				while(lineCopy.matches(".*(fp|func)\\.\\w*\\(.*\\).*")) {
					//Reset brackets count
					bracketsCount = 0;
					
					//Set indexStart to start of first "fp." or "func." and cut 0 to indexStart of lineCopy
					int indexStartFP = lineCopy.indexOf("fp.");
					if(indexStartFP == -1)
						indexStartFP = Integer.MAX_VALUE;
					int indexStartFunc = lineCopy.indexOf("func.");
					if(indexStartFunc == -1)
						indexStartFunc = Integer.MAX_VALUE;
					indexStart = Math.min(indexStartFP, indexStartFunc);
					
					lineCopy = lineCopy.substring(indexStart);
					indexStart += oldIndexStart;
					indexEnd = 0; //Reset indexEnd
					
					while(true) { //While brackets count != 0 and not first run
						char c = lineCopy.charAt(indexEnd++); //Get char at indexEnd (increase later)
						if(c == '(') { //If char == '(' -> bracketsCount++;
							bracketsCount++;
						}else if(c == ')') { //Else if char == ')' -> bracketsCount--;
							bracketsCount--;
							if(bracketsCount == 0) //When all brackets have been closed -> break
								break;
						}
						
						//If brackets are messed up (More "(" than ")") -> throw error
						if(indexEnd == lineCopy.length()) {
							setErrno(28, DATA_ID);
							
							return "Error";
						}
					}
					tmp = lineCopy.substring(0, indexEnd); //Copies function arguments
					
					//Replace func with solution of func
					line = line.substring(0, indexStart) + callFunc(lines, tmp, DATA_ID) + line.substring(indexEnd + indexStart);
					
					lineCopy = lineCopy.substring(indexEnd); //Go to the end of the function
					oldIndexStart = line.indexOf(lineCopy); //Gets indexStart of line
				}
				
				if(line.matches(".*(fp|func)\\.\\w*\\(.*\\).*")) //If a function returns a funcPtr -> call it
					line = executeFunc(lines, line, DATA_ID);
				
				return line;
			}
			
			private String prepareFunc(BufferedReader lines, String func, final int DATA_ID) {
				String retTmp = "";
				func = func.substring(func.indexOf('(')+1); //Gets start of arguments
				
				if(!func.contains("fp.") && !func.contains("func.")) { //No nested function
					int lastIndex = func.lastIndexOf(')');
					
					retTmp = func.substring(0, lastIndex); //retTmp is start to end of arguments
					func = func.substring(lastIndex); //Cuts all before the function end
				}
				
				while(func.contains("fp.") || func.contains("func.")) { //While function contain nested functions
					int indexStartFP = func.indexOf("fp.");
					if(indexStartFP == -1)
						indexStartFP = Integer.MAX_VALUE;
					int indexStartFunc = func.indexOf("func.");
					if(indexStartFunc == -1)
						indexStartFunc = Integer.MAX_VALUE;
					int index = Math.min(indexStartFP, indexStartFunc); //Gets index of first "fp." or "func."
					retTmp += func.substring(0, index); //Adds everything between start and "fp." or "func." to retTmp
					func = func.substring(index); //Cuts everything before "fp." or "func."
					
					if(!func.matches("(fp|func)\\.\\w*\\(.*")) { //FuncPtr without call
						 //Remove first char from func string and add to retTmp -> won't be recognized as func anymore
						retTmp += func.substring(0, 1);
						func = func.substring(1);
						continue;
					}
					
					String funcCopy = func.substring(func.indexOf('('));
					int lastIndex = 0, openCount = 0;
					for(lastIndex = 0;lastIndex < funcCopy.length();lastIndex++) {
						char c = funcCopy.charAt(lastIndex);
						if(c == '(')
							openCount++;
						
						if(c == ')')
							openCount--;
						
						if(openCount == 0)
							break;
					}
					lastIndex += func.indexOf('(') + 1;
					
					retTmp += callFunc(lines, func.substring(0, lastIndex), DATA_ID); //Adds result of nested function to retTmp
					func = func.substring(lastIndex); //Cuts everything before end of nested function
				}
				
				//Adds everything after function to retTmp
				retTmp += func.substring(0, func.lastIndexOf(')'));
				return retTmp;
			}
			
			private String callFunc(BufferedReader lines, String func, final int DATA_ID) {
				String retTmp = "";
				
				String funcName = func.substring(0, func.indexOf('(')); //Get name of function
				
				String prefix = func.substring(0, func.indexOf('.'));
				func = func.substring(func.indexOf(prefix) + prefix.length() + 1); //Cuts everything before "fp." or "func."
				
				String args = prepareFunc(lines, func, DATA_ID); //Prepare function
				retTmp += compileFunc(funcName, args, DATA_ID); //Call Function
				
				return retTmp;
			}
			
			private String compileFunc(String funcName, String funcArgs, final int DATA_ID) {
				if(funcName.startsWith("func.")) {
					funcName = funcName.substring(funcName.indexOf("func.") + 5); //Cuts everything before "func."
					if(funcs.containsKey(funcName)) { //If function exists...
						return funcs.get(funcName).callFunc(null, funcArgs, DATA_ID); //Call Function
					}else {
						setErrno(22, DATA_ID);
						
						return "";
					}
				}else if(funcName.startsWith("fp.")) {
					if(!data.get(DATA_ID).varTmp.containsKey(funcName)) {
						setErrno(5, DATA_ID);
						
						return "";
					}
					
					FunctionPointerObject func = data.get(DATA_ID).varTmp.get(funcName).getFunctionPointer();
					String funcHead = func.getHead();
					String funcBody = func.getBody();
					BiFunction<String, Integer, String> externalFunction = func.getExternalFunction();
					int functionPointerType = func.getFunctionPointerType();
					
					//Set function arguments
					if(functionPointerType == FunctionPointerObject.EXTERNAL) {
						funcReturnTmp = externalFunction.apply(funcArgs, DATA_ID);
					}else if(functionPointerType == FunctionPointerObject.PREDEFINED) {
						funcReturnTmp = compileFunc(funcBody, funcArgs, DATA_ID);
					}else if(functionPointerType == FunctionPointerObject.NORMAL) {
						final int NEW_DATA_ID = DATA_ID + 1;
						
						BufferedReader function = new BufferedReader(new StringReader(funcBody));
						
						//Add variables and local variables
						createDataMap(NEW_DATA_ID);
						//Copies must not be final
						data.get(DATA_ID).varTmp.forEach((key, val) -> {
							if(!key.startsWith("$LANG_"))
								data.get(NEW_DATA_ID).varTmp.put(key, new DataObject(val).setFinalData(false));
						});
						//Initialize copyAfterFP
						copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
						
						String[] funcVars = funcHead.split(",");
						String tmp = funcArgs;
						for(String var:funcVars) {
							var = var.trim();
							
							int index = tmp.indexOf(",");
							String val = tmp.substring(0, (index == -1)?tmp.length():index);
							
							val = val.trim();
							
							if(var.startsWith("$")) {
								data.get(NEW_DATA_ID).varTmp.put(var, new DataObject(val).setFinalData(false)); //Copy params to func as local params
							}else if(var.startsWith("fp.") || var.startsWith("&")) {
								DataObject dataFromCaller = data.get(DATA_ID).varTmp.get(val);
								if(dataFromCaller == null)
									dataFromCaller = new DataObject().setNull();
								data.get(NEW_DATA_ID).varTmp.put(var, new DataObject(dataFromCaller).setFinalData(false));
							}
							
							index = tmp.indexOf(",");
							if(index != -1) {
								tmp = tmp.substring(index + 1).trim();
							}
						}
						
						//Call function
						try {
							compileLangFile(function, NEW_DATA_ID);
						}catch(IOException e) {
							term.logStackTrace(e, FuncParser.class);
						}catch(Exception e) {}
						
						//Add lang after call
						data.get(DATA_ID).lang.putAll(data.get(NEW_DATA_ID).lang);
						
						//Add copyValue after call
						copyAfterFP.get(NEW_DATA_ID).forEach((to, from) -> {
							if(from != null && to != null) {
								DataObject valFrom = data.get(NEW_DATA_ID).varTmp.get(from);
								if(valFrom != null && valFrom.getType() != DataType.NULL) { //var and funcPtr
									if(to.startsWith("fp.") || to.startsWith("$") || to.startsWith("&")) {
										DataObject dataTo = data.get(DATA_ID).varTmp.get(to);
										 //$LANG and final vars can't be change
										if(to.startsWith("$LANG") || (dataTo != null && dataTo.isFinalData())) {
											setErrno(1, DATA_ID);
											
											return;
										}
										
										data.get(DATA_ID).varTmp.put(to, valFrom);
										
										return;
									}
								}
							}
							
							setErrno(21, DATA_ID);
						});
						
						//Clear copyValue
						copyAfterFP.remove(NEW_DATA_ID);
						
						//Remove data map
						data.remove(NEW_DATA_ID);
					}
					
					String retTmp = funcReturnTmp; //Get func return or "" (empty)
					funcReturnTmp = ""; //Reset func return for non return funcs
					return retTmp;
				}
				
				return "";
			}
		}
	}
}