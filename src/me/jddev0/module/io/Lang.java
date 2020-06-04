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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
 * [void]func.clearAllVars(void)<br>
 * [void]func.clearAllArrays(void)<br>
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
	private static final Random ran = new Random();
	
	private static String oldFile;
	private static String pathLangFile; //$LANG_PATH
	
	//Error Strings
	private static String[] errorStrings = new String[] {
		"No error" /*errno = 0*/, "$LANG or final var can't be changed by lang file", "To many inner links", "No .lang-File", "File not found",
		"FuncPtr is invalid", "Stack overflow", "No terminal available", "Invalid argument count", "Invalid log level", "Invalid array pointer", "No hex num",
		"No char", "No num", "Dividing by 0", "Negative array length", "Empty array", "Length NAN", "Array out of bounds", "Argument count is not array length",
		"Invalid function pointer", "Invalid arguments", "Function not found", "EOF", "System Error", "Negative repeat count", "Lang request doesn't exist",
		"Function not supported"
	};
	
	//DATA
	private static Map<Integer, Compiler.Data> data = new HashMap<>();
	
	//Lang tmp
	private static Map<String, String> lang = new HashMap<>(); //ID, data
	
	//INIT funcs
	private static Map<String, LangFunctionObject> funcs = new HashMap<>();
	static {
		//Reset Functions
		funcs.put("clearAllVars", (lines, arg, DATA_ID) -> {
			Compiler.resetVars(DATA_ID);
			
			return "";
		});
		funcs.put("clearAllArrays", (lines, arg, DATA_ID) -> {
			data.get(DATA_ID).varArrayTmp.clear();
			
			return "";
		});
		
		//Error functions
		funcs.put("getErrorString", (lines, arg, DATA_ID) -> {
			int err = Compiler.getAndClearErrno(DATA_ID); //Reset and return error
			
			return errorStrings[err];
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
					Compiler.setErrno(24, DATA_ID);
					
					return "Error";
				}
			}catch(NumberFormatException e) {
				Compiler.setErrno(9, DATA_ID);
				
				return "Error";
			}
			
			return "";
		});
		funcs.put("repeat", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}
			
			try {
				String funcPtr = funcArgs[0].trim();
				if(!funcPtr.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(funcPtr)) {
					Compiler.setErrno(20, DATA_ID);
					
					return "Error";
				}
				
				int times = Integer.parseInt(funcArgs[1].trim());
				if(times < 0) {
					Compiler.setErrno(25, DATA_ID);
					
					return "Error";
				}
				
				for(int i = 0;i < times;i++) {
					Compiler.FuncPtr.compileFunc(funcPtr, "" + i, DATA_ID);
				}
			}catch(NumberFormatException e) {
				Compiler.setErrno(9, DATA_ID);
				
				return "Error";
			}
			
			return "";
		});
		funcs.put("repeatWhile", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}
			
			try {
				String execFunc = funcArgs[0].trim();
				String checkFunc = funcArgs[1].trim();
				if(!execFunc.startsWith("fp.") || !checkFunc.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(execFunc) || !data.get(DATA_ID).varTmp.containsKey(checkFunc)) {
					Compiler.setErrno(20, DATA_ID);
					
					return "Error";
				}
				
				while(true) {
					String check = Compiler.FuncPtr.compileFunc(checkFunc, "", DATA_ID);
					try {
						if(Integer.parseInt(check) == 0)
							break;
					}catch(NumberFormatException e) {
						break;
					}
					Compiler.FuncPtr.compileFunc(execFunc, "", DATA_ID);
				}
			}catch(NumberFormatException e) {
				Compiler.setErrno(9, DATA_ID);
				
				return "Error";
			}
			
			return "";
		});
		funcs.put("repeatUntil", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}
			
			try {
				String execFunc = funcArgs[0].trim();
				String checkFunc = funcArgs[1].trim();
				if(!execFunc.startsWith("fp.") || !checkFunc.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(execFunc) || !data.get(DATA_ID).varTmp.containsKey(checkFunc)) {
					Compiler.setErrno(20, DATA_ID);
					
					return "Error";
				}
				
				while(true) {
					String check = Compiler.FuncPtr.compileFunc(checkFunc, "", DATA_ID);
					try {
						if(Integer.parseInt(check) != 0)
							break;
					}catch(NumberFormatException e) {
						break;
					}
					Compiler.FuncPtr.compileFunc(execFunc, "", DATA_ID);
				}
			}catch(NumberFormatException e) {
				Compiler.setErrno(9, DATA_ID);
				
				return "Error";
			}
			
			return "";
		});
		funcs.put("getLangRequest", (lines, arg, DATA_ID) -> {
			String ret = data.get(DATA_ID).lang.get(arg.trim());
			if(ret == null) {
				Compiler.setErrno(26, DATA_ID);
				
				return "Error";
			}
			
			return ret;
		});
		funcs.put("makeFinal", (lines, arg, DATA_ID) -> {
			Compiler.DataObject dataObject = data.get(DATA_ID).varTmp.get(arg.trim());
			if(dataObject == null || dataObject.isFinalData() || arg.trim().startsWith("$LANG_")) {
				Compiler.setErrno(21, DATA_ID);
				
				return "Error";
			}
			
			dataObject.setFinalData(true);
			
			return "";
		});
		funcs.put("condition", (lines, arg, DATA_ID) -> Compiler.If.checkIf(arg)?"1":"0");
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
			TerminalIO term = Compiler.term;
			if(term == null) {
				Compiler.setErrno(7, DATA_ID);
				
				return "Error";
			}
			Level lvl = null;
			
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
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
				Compiler.setErrno(9, DATA_ID);
				
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
				Compiler.compileLangFile(new BufferedReader(new StringReader(comp)), DATA_ID); //Compile like "perror"(C) -> (Text + ": " + Error-String)
			}catch(NullPointerException e) {
				Compiler.setErrno(21, DATA_ID);
				
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
				Compiler.setErrno(11, DATA_ID);
				
				return "Error";
			}
		});
		
		//Character functions
		funcs.put("toValue", (lines, arg, DATA_ID) -> {
			if(arg.trim().length() != 1) {
				Compiler.setErrno(12, DATA_ID);
				
				return "Error";
			}
			
			return (int)arg.trim().charAt(0) + "";
		});
		funcs.put("toChar", (lines, arg, DATA_ID) -> {
			try {
				int c = Integer.parseInt(arg.trim());
				
				return (char)c + "";
			}catch(NumberFormatException e) {
				Compiler.setErrno(13, DATA_ID);
				
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
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}
			
			return funcArgs[0].trim().replaceAll(funcArgs[1].trim(), funcArgs[2].trim());
		});
		funcs.put("substring", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 3);
			if(funcArgs.length < 2) {
				Compiler.setErrno(8, DATA_ID);
				
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
				Compiler.setErrno(13, DATA_ID);
				
				return "Error";
			}catch(StringIndexOutOfBoundsException e) {
				Compiler.setErrno(18, DATA_ID);
				
				return "Error";
			}
		});
		funcs.put("split", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 4);
			if(funcArgs.length < 3) {
				Compiler.setErrno(8, DATA_ID);
				
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
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			String comp = "func.arrayMake(" + arrPtr + ", " + arrTmp.length + ")\nfunc.arraySetAll(" + arrPtr;
			for(String s:arrTmp) {
				comp += ", " + s;
			}
			comp += ")";
			try {
				Compiler.compileLangFile(new BufferedReader(new StringReader(comp)), DATA_ID);
			}catch(NullPointerException e) {
				Compiler.setErrno(21, DATA_ID);
				
				return "Error";
			}catch(Exception e) {}
			
			int err;
			if((err = Compiler.getAndClearErrno(DATA_ID)) != 0) {
				Compiler.setErrno(err, DATA_ID);
				
				return "Error";
			}
			
			return "";
		});
		
		//Math functions
		funcs.put("rand", (lines, arg, DATA_ID) -> {
			return "" + ran.nextInt(Integer.MAX_VALUE);
		});
		funcs.put("addi", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",");
			int sum = 0;
			for(int i = 0;i < funcArgs.length;i++) {
				funcArgs[i] = funcArgs[i].trim();
				try {
					sum += Integer.parseInt(funcArgs[i].trim());
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			return sum + "";
		});
		funcs.put("subi", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return Integer.parseInt(funcArgs[0].trim()) - Integer.parseInt(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
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
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			return prod + "";
		});
		funcs.put("divi", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					if(Integer.parseInt(funcArgs[1].trim()) == 0) {
						Compiler.setErrno(14, DATA_ID);
						
						return "Error";
					}
					return Integer.parseInt(funcArgs[0].trim())/Integer.parseInt(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("modi", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					if(Integer.parseInt(funcArgs[1].trim()) == 0) {
						Compiler.setErrno(14, DATA_ID);
						
						return "Error";
					}
					return Integer.parseInt(funcArgs[0].trim())%Integer.parseInt(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("andi", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Integer.parseInt(funcArgs[0].trim()) & Integer.parseInt(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("ori", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Integer.parseInt(funcArgs[0].trim()) | Integer.parseInt(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("xori", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Integer.parseInt(funcArgs[0].trim()) ^ Integer.parseInt(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("noti", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 1) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return ~Integer.parseInt(funcArgs[0].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("lshifti", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Integer.parseInt(funcArgs[0].trim()) << Integer.parseInt(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("rshifti", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Integer.parseInt(funcArgs[0].trim()) >> Integer.parseInt(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("rzshifti", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Integer.parseInt(funcArgs[0].trim()) >>> Integer.parseInt(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
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
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			return sum + "";
		});
		funcs.put("subl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return Long.parseLong(funcArgs[0].trim())-Long.parseLong(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
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
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			return prod + "";
		});
		funcs.put("divl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					if(Long.parseLong(funcArgs[1].trim()) == 0) {
						Compiler.setErrno(14, DATA_ID);
						
						return "Error";
					}
					return Long.parseLong(funcArgs[0].trim())/Long.parseLong(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("modl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					if(Long.parseLong(funcArgs[1].trim()) == 0) {
						Compiler.setErrno(14, DATA_ID);
						
						return "Error";
					}
					return Long.parseLong(funcArgs[0].trim()) % Long.parseLong(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("andl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Long.parseLong(funcArgs[0].trim()) & Long.parseLong(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("orl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Long.parseLong(funcArgs[0].trim()) | Long.parseLong(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("xorl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Long.parseLong(funcArgs[0].trim()) ^ Long.parseLong(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("notl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 1) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return ~Long.parseLong(funcArgs[0].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("lshiftl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Long.parseLong(funcArgs[0].trim()) << Long.parseLong(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("rshiftl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Long.parseLong(funcArgs[0].trim()) >> Long.parseLong(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("rzshiftl", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return (Long.parseLong(funcArgs[0].trim()) >>> Long.parseLong(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
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
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			return sum + "";
		});
		funcs.put("subd", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					return Double.parseDouble(funcArgs[0].trim())-Double.parseDouble(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
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
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
			
			return prod + "";
		});
		funcs.put("divd", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					if(Double.parseDouble(funcArgs[1].trim()) == 0.) {
						Compiler.setErrno(14, DATA_ID);
						
						return "Error";
					}
					return Double.parseDouble(funcArgs[0].trim())/Double.parseDouble(funcArgs[1].trim()) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("pow", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}else {
				try {
					if(Double.parseDouble(funcArgs[1].trim()) == 0.) {
						return "1";
					}
					return Math.pow(Double.parseDouble(funcArgs[0].trim()), Double.parseDouble(funcArgs[1].trim())) + "";
				}catch(NumberFormatException e) {
					Compiler.setErrno(13, DATA_ID);
					
					return "Error";
				}
			}
		});
		funcs.put("sqrt", (lines, arg, DATA_ID) -> {
			try {
				return Math.sqrt(Double.parseDouble(arg.trim())) + "";
			}catch(NumberFormatException e) {
				Compiler.setErrno(13, DATA_ID);
				
				return "Error";
			}
		});
		funcs.put("dtoi", (lines, arg, DATA_ID) -> {
			try {
				return (int)Double.parseDouble(arg.trim()) + "";
			}catch(NumberFormatException e) {
				Compiler.setErrno(13, DATA_ID);
				
				return "Error";
			}
		});
		funcs.put("dtol", (lines, arg, DATA_ID) -> {
			try {
				return (long)Double.parseDouble(arg.trim()) + "";
			}catch(NumberFormatException e) {
				Compiler.setErrno(13, DATA_ID);
				
				return "Error";
			}
		});
		funcs.put("ceil", (lines, arg, DATA_ID) -> {
			try {
				return (long)Math.ceil(Double.parseDouble(arg.trim())) + "";
			}catch(NumberFormatException e) {
				Compiler.setErrno(13, DATA_ID);
				
				return "Error";
			}
		});
		funcs.put("floor", (lines, arg, DATA_ID) -> {
			try {
				return (long)Math.floor(Double.parseDouble(arg.trim())) + "";
			}catch(NumberFormatException e) {
				Compiler.setErrno(13, DATA_ID);
				
				return "Error";
			}
		});
		
		//FuncPtr functions
		funcs.put("copyAfterFP", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}
			
			String to = funcArgs[0].trim();
			String from = funcArgs[1].trim();
			
			Compiler.FuncPtr.copyAfterFP.get(DATA_ID).put(to, from);
			
			return "";
		});
		
		//Array functions
		funcs.put("arrayMake", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2); //arrPtr, length
			funcArgs[0] = funcArgs[0].trim();
			if(!funcArgs[0].startsWith("&")) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			try {
				int lenght = Integer.parseInt(funcArgs[1].trim());
				
				if(lenght < 0) {
					Compiler.setErrno(15, DATA_ID);
					
					return "Error";
				}else if(lenght == 0) {
					Compiler.setErrno(16, DATA_ID);
					
					return "Error";
				}
				
				data.get(DATA_ID).varArrayTmp.put(funcArgs[0], new String[lenght]);
				String[] arrPtr = data.get(DATA_ID).varArrayTmp.get(funcArgs[0]);
				Arrays.fill(arrPtr, "null");
			}catch(NumberFormatException e) {
				Compiler.setErrno(17, DATA_ID);
				
				return "Error";
			}
			
			return ""; //No return func
		});
		funcs.put("arraySet", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 3); //arrPtr, index, value
			funcArgs[0] = funcArgs[0].trim();
			if(!funcArgs[0].startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(funcArgs[0])) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			String[] arr = data.get(DATA_ID).varArrayTmp.get(funcArgs[0]);
			int index = Integer.parseInt(funcArgs[1].trim());
			
			try {
				if(index < 0) {
					Compiler.setErrno(15, DATA_ID);
					
					return "Error";
				}else if(index >= arr.length) {
					Compiler.setErrno(18, DATA_ID);
					
					return "Error";
				}
				
				arr[index] = funcArgs[2].trim();
			}catch(NumberFormatException e) {
				Compiler.setErrno(17, DATA_ID);
				
				return "Error";
			}
			
			return ""; //No return func
		});
		funcs.put("arraySetAll", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(","); //arrPtr, value, ...
			funcArgs[0] = funcArgs[0].trim();
			if(!funcArgs[0].startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(funcArgs[0])) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			funcArgs[1] = funcArgs[1].trim();
			String[] arr = data.get(DATA_ID).varArrayTmp.get(funcArgs[0]);
			
			if(funcArgs.length == 2) {
				for(int i = 0;i < arr.length;i++) {
					arr[i] = funcArgs[1].trim();
				}
			}else if(funcArgs.length == arr.length+1) {
				for(int i = 0;i < arr.length;i++) {
					arr[i] = funcArgs[i + 1].trim();
				}
			}else {
				Compiler.setErrno(19, DATA_ID);
				
				return "Error";
			}
			
			return ""; //No return func
		});
		funcs.put("arrayGet", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2); //arrPtr, index
			funcArgs[0] = funcArgs[0].trim();
			if(!funcArgs[0].startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(funcArgs[0])) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			String[] arr = data.get(DATA_ID).varArrayTmp.get(funcArgs[0]);
			int index = Integer.parseInt(funcArgs[1].trim());
			
			try {
				if(index < 0 || index >= arr.length) {
					Compiler.setErrno(18, DATA_ID);
					
					return "Error";
				}
			}catch(NumberFormatException e) {
				Compiler.setErrno(17, DATA_ID);
				
				return "Error";
			}
			
			return arr[index];
		});
		funcs.put("arrayGetAll", (lines, arg, DATA_ID) -> {
			String tmp = "";
			
			arg = arg.trim();
			if(!arg.startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(arg)) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			for(String val:data.get(DATA_ID).varArrayTmp.get(arg)) {
				tmp += val + ", ";
			}
			
			return tmp.substring(0, tmp.lastIndexOf(", "));
		});
		funcs.put("arrayLength", (lines, arg, DATA_ID) -> {
			arg = arg.trim();
			if(!arg.startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(arg)) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			return data.get(DATA_ID).varArrayTmp.get(arg).length + "";
		});
		funcs.put("arrayForEach", (lines, arg, DATA_ID) -> {
			String[] funcArgs = arg.split(",", 2);
			if(funcArgs.length != 2) {
				Compiler.setErrno(8, DATA_ID);
				
				return "Error";
			}
			
			String arrName = funcArgs[0].trim();
			if(!arrName.startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(arrName)) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			String funcPtr = funcArgs[1].trim();
			if(!funcPtr.startsWith("fp.") || !data.get(DATA_ID).varTmp.containsKey(funcPtr)) {
				Compiler.setErrno(20, DATA_ID);
				
				return "Error";
			}
			
			String[] arr = data.get(DATA_ID).varArrayTmp.get(arrName);
			for(String element:arr) {
				Compiler.FuncPtr.compileFunc(funcPtr, element, DATA_ID);
			}
			
			return "";
		});
		funcs.put("randChoice", (lines, arg, DATA_ID) -> {
			arg = arg.trim();
			if(!arg.startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(arg)) {
				//No array Pointer
				String[] funcArgs = arg.split(",");
				if(funcArgs.length < 1) {
					Compiler.setErrno(8, DATA_ID);
					
					return "Error";
				}else if(funcArgs.length == 1) {
					return funcArgs[0];
				}
				return funcArgs[ran.nextInt(funcArgs.length)];
			}
			
			String[] arrPtr = data.get(DATA_ID).varArrayTmp.get(arg);
			
			if(arrPtr.length < 1) {
				Compiler.setErrno(16, DATA_ID);
				
				return "Error";
			}else if(arrPtr.length == 1) {
				return arrPtr[0];
			}
			return arrPtr[ran.nextInt(arrPtr.length)];
		});
		funcs.put("arrayDelete", (lines, arg, DATA_ID) -> {
			arg = arg.trim();
			if(!arg.startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(arg)) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			
			String[] arr = data.get(DATA_ID).varArrayTmp.get(arg);
			Arrays.fill(arr, "null");
			
			return ""; //No return func
		});
		funcs.put("arrayClear", (lines, arg, DATA_ID) -> {
			arg = arg.trim();
			if(!arg.startsWith("&") || !data.get(DATA_ID).varArrayTmp.containsKey(arg)) {
				Compiler.setErrno(10, DATA_ID);
				
				return "Error";
			}
			data.get(DATA_ID).varArrayTmp.remove(arg);
			
			return ""; //No return func
		});
	}
	
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
			
			//Set TerminalIO for (error) output
			Compiler.setTerminalIO(term);
			
			//Set data for LANG_* vars
			{
				pathLangFile = langFile.substring(0, langFile.lastIndexOf('/')); //Remove ending ("/*.lang") for $LANG_PATH
			}
			
			//Create new data map with ID 0
			Compiler.createDataMap(0);
			
			BufferedReader reader = new BufferedReader(new FileReader(new File(langFile)));
			try {
				Compiler.compileLangFile(reader, 0); //Compile lang file
			}catch(Exception e) {
				reader.close();
				
				throw e;
			}
			reader.close();
			
			//Copy lang
			lang = data.get(0).lang;
			
			//Clear data
			data.clear();
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
	
	//Class for compiling lang file
	private static class Compiler {
		private static TerminalIO term;
		
		private Compiler() {}
		
		public static void setTerminalIO(TerminalIO term) {
			Compiler.term = term;
		}

		public static void createDataMap(final int DATA_ID) {
			data.put(DATA_ID, new Data());
			
			resetVarsAndFuncPtrs(DATA_ID);
		}
		
		public static void resetVarsAndFuncPtrs(final int DATA_ID) {
			data.get(DATA_ID).varTmp.clear();
			
			//Final vars
			data.get(DATA_ID).varTmp.put("$LANG_COMPILER_VERSION", new DataObject(VERSION, true));
			data.get(DATA_ID).varTmp.put("$LANG_PATH", new DataObject(pathLangFile, true));
			data.get(DATA_ID).varTmp.put("$LANG_RAND_MAX", new DataObject("" + (Integer.MAX_VALUE - 1), true));
			
			//Not final vars
			setErrno(0, DATA_ID); //Set $LANG_ERRNO
		}
		public static void resetVars(final int DATA_ID) {
			String[] keys = data.get(DATA_ID).varTmp.keySet().toArray(new String[0]);
			for(int i = data.get(DATA_ID).varTmp.size() - 1;i > -1;i--) {
				if(keys[i].startsWith("$") && !keys[i].startsWith("$LANG_")) {
					data.get(DATA_ID).varTmp.remove(keys[i]);
				}
			}
			
			//Not final vars
			setErrno(0, DATA_ID); //Set $LANG_ERRNO
		}
		
		public static void setErrno(int errno, final int DATA_ID) {
			data.get(DATA_ID).varTmp.computeIfAbsent("$LANG_ERRNO", key -> new DataObject());
			
			data.get(DATA_ID).varTmp.get("$LANG_ERRNO").setText("" + errno);
		}
		public static int getAndClearErrno(final int DATA_ID) {
			int ret = Integer.parseInt(data.get(DATA_ID).varTmp.get("$LANG_ERRNO").getText());
			
			setErrno(0, DATA_ID); //Reset errno
			
			return ret;
		}
		
		/**
		 * Method for compiling lang files
		 */
		public static void compileLangFile(BufferedReader lines, final int DATA_ID) throws Exception {
			while(lines.ready()) {
				String str = lines.readLine();
				if(str == null) {
					break;
				}
				
				if(str.trim().length() > 0 && !str.trim().isEmpty()) {
					str = str.replaceAll("^\\s*", ""); //Remove whitespaces at the beginning
					
					//Lang data and compiler args
					if(str.startsWith("lang.")) {
						if(str.startsWith("lang.version = ")) {
							Compiler.compileLine(lines, str, DATA_ID);
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
						If.executeIf(lines, str, DATA_ID);
						
						continue; //Compile next Line
					}
					
					Compiler.compileLine(lines, str, DATA_ID);
				}
			}
		}
		
		public static void compileLine(BufferedReader lines, String line, final int DATA_ID) {
			//Comments
			line = line.replace("\\#", "\\NoCommentTmp");
			if(line.contains("#"))
				if(line.startsWith("#"))
					return;
				else
					line = line.substring(0, line.indexOf("#"));
			line = line.replace("\\NoCommentTmp", "#");
			
			//For newLine
			line = line.replace("\\n", "\n");
			
			//Save funcPtr
			if(line.startsWith("fp.") && line.contains(" = ")) {
				FuncPtr.saveFuncPtr(lines, line, DATA_ID);
				
				return;
			}
			
			//Var
			if(line.contains("$")) {
				line = Var.replaceVarsWithValue(lines, line, DATA_ID);
				
				if(line == null)
					return;
			}
			
			//Execute FuncPtr
			if(line.contains("fp.")) { //... .funcName(params/nothing) ...
				line = FuncPtr.executeFunc(lines, line, DATA_ID);
			}
			
			//Functions
			if(line.contains("func.") && line.contains("(") && line.contains(")")) {
				line = Func.replaceFuncsWithValue(lines, line, DATA_ID);
			}
			
			//Linker
			if(line.contains("linker.")) {
				line = Linker.compileLine(line, DATA_ID);
			}
			
			//FuncPtr return
			if(line.trim().startsWith("return")) {
				if(line.trim().matches("return .*")) {
					FuncPtr.funcReturnTmp = line.substring(7).trim(); //return func value
				}else {
					FuncPtr.funcReturnTmp = "";
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
				
				return;
			}
			
			if(line.contains(" = ")) {
				String[] string = line.split(" = ", 2);
				data.get(DATA_ID).lang.put(string[0], string[1].replaceAll("\\\\s", " ")); //Put lang key and lang value in lang map
			}
		}
		/**
		 * @return the modified line<br>if null -> continue
		 */
		public static String compileLineForIf(BufferedReader lines, String line, final int DATA_ID) {
			//Comments
			line = line.replace("\\#", "\\NoCommentTmp");
			if(line.contains("#"))
				if(line.startsWith("#"))
					return "0";
				else
					line = line.substring(0, line.indexOf("#"));
			line = line.replace("\\NoCommentTmp", "#");
			
			//For newLine
			line = line.replace("\\n", "\n");
			
			//Save funcPtr
			if(line.startsWith("fp.") && line.contains(" = ")) {
				FuncPtr.saveFuncPtr(lines, line, DATA_ID);
				
				return "0";
			}
			
			//Var
			if(line.contains("$")) {
				line = Var.replaceVarsWithValue(lines, line, DATA_ID);
				
				if(line == null)
					return "0";
			}
			
			if(line.contains(" = ")) {
				return "0";
			}
			
			//Execute FuncPtr
			if(line.contains("fp.")) { //... .funcName(params/nothing) ...
				line = FuncPtr.executeFunc(lines, line, DATA_ID);
			}
			
			//Functions
			if(line.contains("func.") && line.contains("(") && line.contains(")")) {
				//Argument functions
				line = Func.replaceFuncsWithValue(lines, line, DATA_ID);
			}
			
			//Linker
			if(line.contains("linker.")) {
				line = Linker.compileLine(line, DATA_ID);
			}
			
			//FuncPtr return
			if(line.trim().startsWith("return")) {
				if(line.trim().matches("return .*")) {
					FuncPtr.funcReturnTmp = line.substring(7).trim(); //return func value
				}else {
					FuncPtr.funcReturnTmp = "";
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
				
				return "0";
			}
			
			return (line == null)?"0":line; //If line is null -> return "false"
		}
		
		//Classes for variable data
		private static class ErrorObject {
			private final int err;
			
			public ErrorObject(int err) {
				this.err = err;
			}
			
			public int getErrno() {
				return err;
			}
			
			public String getErrmsg() {
				return errorStrings[err];
			}
			
			@Override
			public String toString() {
				return errorStrings[err] + " (" + err + ")";
			}
		}
		private static enum DataType {
			TEXT, FUNCTION_POINTER, ERROR, NULL, VOID;
		}
		private static class DataObject {
			private DataType type;
			private String txt;
			private ErrorObject error;
			private boolean finalData;
			
			public DataObject(DataObject dataObject) {
				this.type = dataObject.type;
				this.txt = dataObject.txt;
				this.finalData = dataObject.finalData;
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
					case FUNCTION_POINTER:
						return txt;
					case ERROR:
						return error.toString();
					case NULL:
						return "null";
					case VOID:
						return "";
				}
				
				return null;
			}
			
			public DataObject setFunctionPointer(String txt) {
				if(finalData)
					return this;
				
				this.type = DataType.FUNCTION_POINTER;
				this.txt = txt;
				
				return this;
			}
			
			public DataObject setError(ErrorObject error) {
				if(finalData)
					return this;
				
				this.type = DataType.ERROR;
				this.error = error;
				
				return this;
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
		private static class Data {
			public Map<String, String> lang = new HashMap<>();
			public Map<String, DataObject> varTmp = new HashMap<>();
			public Map<String, String[]> varArrayTmp = new HashMap<>();
		}
		
		//Classes for compiling lang file
		
		//Class for linker
		private static class Linker {
			private Linker() {}
			
			private static String compileLine(String line, final int DATA_ID) {
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
							tmp = linkLangFile(data.get(DATA_ID).varTmp.get("$LANG_PATH") + "/" + tmp, DATA_ID);
						}else { //No .lang file
							setErrno(3, DATA_ID);
							
							tmp = "-1";
						}
					}else if(tmp.startsWith("bindLibrary(")) {
						tmp = tmp.substring(tmp.indexOf('(') + 1); //After "linker.*("

						if(tmp.endsWith(".lang")) {
							tmp = bindLibraryLangFile(data.get(DATA_ID).varTmp.get("$LANG_PATH") + "/" + tmp, DATA_ID);
						}else { //No .lang file
							setErrno(3, DATA_ID);
							
							tmp = "-1";
						}
					}
					
					line = line.substring(0, indexStart) + tmp + line.substring(indexEndForLine);
				}
				
				return line;
			}
			
			private static String linkLangFile(String linkLangFile, final int DATA_ID) {
				final int NEW_DATA_ID = DATA_ID + 1;
				
				String ret = "0";
				
				Compiler.createDataMap(NEW_DATA_ID);

				String langPathTmp = linkLangFile;
				langPathTmp = langPathTmp.substring(0, langPathTmp.lastIndexOf(File.separator)); //Remove ending ("/*.lang") for $LANG_PATH
				data.get(NEW_DATA_ID).varTmp.put("$LANG_PATH", new DataObject(langPathTmp, true)); //Set lang path to "new" lang path
				
				try {
					BufferedReader reader = new BufferedReader(new FileReader(new File(linkLangFile)));
					try {
						Compiler.compileLangFile(reader, NEW_DATA_ID);
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
				return ret;
			}
			
			private static String bindLibraryLangFile(String linkLangFile, final int DATA_ID) {
				final int NEW_DATA_ID = DATA_ID + 1;
				
				String ret = "0";
				
				Compiler.createDataMap(NEW_DATA_ID);

				String langPathTmp = linkLangFile;
				langPathTmp = langPathTmp.substring(0, langPathTmp.lastIndexOf(File.separator)); //Remove ending ("/*.lang") for $LANG_PATH
				data.get(NEW_DATA_ID).varTmp.put("$LANG_PATH", new DataObject(langPathTmp, true)); //Set lang path to "new" lang path
				
				try {
					BufferedReader reader = new BufferedReader(new FileReader(new File(linkLangFile)));
					try {
						Compiler.compileLangFile(reader, NEW_DATA_ID);
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
						if(!name.startsWith("$LANG")) { //No LANG data vars
							data.get(DATA_ID).varTmp.put(name, val);
						}
					});
					data.get(NEW_DATA_ID).varArrayTmp.forEach((name, arr) -> {
						data.get(DATA_ID).varArrayTmp.put(name, arr);
					});
				}
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				return ret;
			}
		}
		
		//Class for executing if
		private static class If {
			private If() {}
			
			private static boolean checkIf(String ifCondition) {
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
			
			public static void executeIf(BufferedReader lines, String line, final int DATA_ID) throws Exception {
				line = line.trim().substring(4); //Remove whitespace and "con."
				
				if(line.startsWith("if(")) {
					line = Compiler.compileLineForIf(lines, line, DATA_ID);
					line = line.substring(3, line.lastIndexOf(')'));
					
					String tmp = lines.readLine();
					if(checkIf(line)) { //True
						while(true) {
							if(tmp == null) { //"return" of FuncPtr
								return;
							}
							
							if(tmp.trim().startsWith("con.")) { //If line startsWith "con."
								tmp = Compiler.compileLineForIf(lines, tmp, DATA_ID); //Compile lines
								
								if(tmp.trim().substring(4).startsWith("if")) {
									If.executeIf(lines, tmp, DATA_ID); //Execute inner if
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
								Compiler.compileLine(lines, tmp, DATA_ID); //Compile lines
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
								tmp = Compiler.compileLineForIf(lines, tmp, DATA_ID); //Compile lines
								
								tmp = tmp.trim().substring(4);
								if(tmp.startsWith("endif")) {
									return;
								}else if(tmp.startsWith("elif")) {
									If.executeIf(lines, "con." + tmp.substring(2), DATA_ID); //Execute "elif" as "if"
									
									return;
								}else if(tmp.startsWith("else")) {
									If.executeIf(lines, "con.if(1)", DATA_ID); //Execute "else" as "if(1)"
									
									return;
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
		
		//Class for replacing vars with value
		private static class Var {
			private Var() {}
			
			/**
			 * @return the modified line<br>if null -> continue
			 */
			public static String replaceVarsWithValue(BufferedReader lines, String line, final int DATA_ID) {
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
						string[1] = string[1].replace("\\$", "$NULL"); //Replace "\$" with "$NULL"
						
						String[] copy = {string[1]};
						string[1] = "";
						while(copy[0].length() > 0) {
							int indexVarStart = copy[0].indexOf('$');
							if(indexVarStart == 0) {
								boolean[] noVarFound = {true};
								data.get(DATA_ID).varTmp.keySet().stream().filter(varName -> {
									return varName.startsWith("$") && copy[0].startsWith(varName);
								}).sorted((s0, s1) -> { //Sort keySet from large to small length (e.g.: $ab and $abc)
									if(s0.length() == s1.length())
										return 0;
									
									return (s0.length() < s1.length())?1:-1;
								}).limit(1).forEach(varName -> {
									noVarFound[0] = false;
									
									string[1] += data.get(DATA_ID).varTmp.get(varName);
									copy[0] = copy[0].substring(varName.length());
								});
								
								if(noVarFound[0]) {
									string[1] += "$";
									copy[0] = copy[0].substring(1);
								}
							}else if(indexVarStart == -1) {
								string[1] += copy[0];
								
								break;
							}else {
								string[1] += copy[0].substring(0, indexVarStart);
								copy[0] = copy[0].substring(indexVarStart);
							}
						}
						
						string[1] = string[1].replace("$NULL", "$"); //Replace all "$NULL"s in string[1] with "$"s
						if(string[1].contains("fp.") && string[1].contains("(") && string[1].contains(")"))
							string[1] = FuncPtr.executeFunc(lines, string[1], DATA_ID);
						if(string[1].contains("func.")) //If string[1] contains a function
							string[1] = Func.replaceFuncsWithValue(lines, string[1], DATA_ID); //Execute function
						if(string[1].contains("linker.")) //If string[1] contains a linker function
							string[1] = Linker.compileLine(string[1], DATA_ID); //Execute linker functions
						
						string[1] = VarPtr.replaceAllVarPtrsWithVar(string[1]); //Replace all varPtrs
						
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
					//Replace "%$" with "$" for start
					if(line.startsWith("%$")) {
						line = line.substring(1);
					}
					
					//Replace var names with var value
					String[] string = line.split(" = ", 2);
					//Reset line for later
					line = "";
					String[] val = new String[string.length];
					//Get value and replace "\$" with "$NULL"
					for(int i = 0;i < val.length;i++) {
						val[i] = string[i].replace("\\$", "$NULL");
						
						String[] copy = {val[i]};
						val[i] = "";
						while(copy[0].length() > 0) {
							int indexVarStart = copy[0].indexOf('$');
							if(indexVarStart == 0) {
								boolean[] noVarFound = {true};
								final int index = i;
								data.get(DATA_ID).varTmp.keySet().stream().filter(varName -> {
									return varName.startsWith("$") && copy[0].startsWith(varName);
								}).sorted((s0, s1) -> { //Sort keySet from large to small length (e.g.: $ab and $abc)
									if(s0.length() == s1.length())
										return 0;
									
									return (s0.length() < s1.length())?1:-1;
								}).limit(1).forEach(varName -> {
									noVarFound[0] = false;
									
									val[index] += data.get(DATA_ID).varTmp.get(varName);
									copy[0] = copy[0].substring(varName.length());
								});
								
								if(noVarFound[0]) {
									val[i] += "$";
									copy[0] = copy[0].substring(1);
								}
							}else if(indexVarStart == -1) {
								val[i] += copy[0];
								
								break;
							}else {
								val[i] += copy[0].substring(0, indexVarStart);
								copy[0] = copy[0].substring(indexVarStart);
							}
						}
						
						val[i] = VarPtr.replaceAllVarPtrsWithVar(val[i]); //Replace all varPtrs
						
						line += val[i].replace("$NULL", "$"); //Replace "$NULL" with "$"
						if(i != val.length - 1)
							line += " = "; //Add " = ", because of "split"
					}
					
					return line;
				}
			}
			
			//Class for replacing varPtrs with var
			private static class VarPtr {
				private VarPtr() {}
				
				private static String replaceAllVarPtrsWithVar(String line) {
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
		}
		
		//Class for replacing funcs with value
		private static class Func {
			private Func() {}
			
			/**
			 * @return the modified line
			 */
			public static String replaceFuncsWithValue(BufferedReader lines, String line, final int DATA_ID) {
				String lineCopy = line; //Copy pointer to line
				String tmp; //String tmp for brackets count
				int bracketsCount, indexStart, indexEnd, oldIndexStart = 0;
				
				//while line contains functions
				while(lineCopy.contains("func.") && lineCopy.contains("(") && lineCopy.contains(")")) {
					//Reset brackets count
					bracketsCount = 0;
					
					//Set indexStart to start of first "func." and cut 0 to indexStart of lineCopy
					lineCopy = lineCopy.substring(indexStart = lineCopy.indexOf("func."));
					indexStart += oldIndexStart; //Add oldIndexStart for line
					indexEnd = 0; //Reset indexEnd
					
					while(true) { //While brackets count != 0 and not first run
						char c = lineCopy.charAt(indexEnd++); //Get char at indexEnd (increase later)
						if(c == '(') { //If char == '(' -> bracketsCount++;
							bracketsCount++;
						}else if(c == ')') { //Else if char == ')' -> bracketsCount--;
							bracketsCount--;
							if(bracketsCount == 0) break; //When all brackets have been closed -> break
						}
					}
					tmp = lineCopy.substring(0, indexEnd); //Copies function arguments
					
					//Replace func with return value of func
					line = line.substring(0, indexStart) + callFunc(lines, tmp, DATA_ID) + line.substring(indexEnd + indexStart);
					
					lineCopy = lineCopy.substring(indexEnd); //Go to the end of the function
					oldIndexStart = line.indexOf(lineCopy); //Gets indexStart of line
				}
				
				return line;
			}
			
			private static String prepareFunc(BufferedReader lines, String func, final int DATA_ID) {
				String retTmp = "";
				func = func.substring(func.indexOf('(') + 1); //Gets start of arguments
				
				if(!func.contains("func.")) { //No nested function
					int lastIndex;
					
					//e.g.: "func.x(T (X) T)" -> without: "T (X", with: "T (X) T"
					if(func.contains("(")) {
						int openCount = 0;
						String funcCopy = func.substring(func.indexOf('('));
						for(lastIndex = 0;lastIndex < funcCopy.length();lastIndex++) {
							char c = funcCopy.charAt(lastIndex);
							if(c == '(')
								openCount++;
							
							if(c == ')')
								openCount--;
							
							if(openCount == 0) {
								break;
							}
						}
						lastIndex += func.indexOf('(') + 1;
					}else {
						lastIndex = func.indexOf(')');
					}
					
					retTmp = func.substring(0, lastIndex); //retTmp is start to end of arguments
					func = func.substring(lastIndex); //Cuts all before the function end
				}
				
				while(func.contains("func.")) { //While function contain nested functions
					int index = func.indexOf("func."); //Gets index of first "func."
					retTmp += func.substring(0, index); //Adds everything between start and "func." to retTmp
					func = func.substring(index); //Cuts everything before "func."
					
					int lastIndex, openCount = 0;
					String funcCopy = func.substring(func.indexOf('('));
					for(lastIndex = 0;lastIndex < funcCopy.length();lastIndex++) {
						char c = funcCopy.charAt(lastIndex);
						if(c == '(')
							openCount++;
						
						if(c == ')')
							openCount--;
						
						if(openCount == 0) {
							break;
						}
					}
					lastIndex += func.indexOf('(') + 1;
					
					retTmp += callFunc(lines, func.substring(0, lastIndex), DATA_ID); //Adds result of nested function to retTmp
					func = func.substring(lastIndex); //Cuts everything before end of nested function
				}
				
				//Adds everything after function to retTmp
				retTmp += func.substring(0, func.lastIndexOf(')'));
				return retTmp;
			}
			
			private static String callFunc(BufferedReader lines, String func, final int DATA_ID) {
				String retTmp = "";
				
				func = func.substring(func.indexOf("func.") + 5); //Cuts everything before "func."
				
				String funcName = func.substring(0, func.indexOf('(')); //Get name of function
				if(funcs.containsKey(funcName)) { //If function exists...
					String args = prepareFunc(lines, func, DATA_ID); //Prepare function
					retTmp += funcs.get(funcName).callFunc(lines, args, DATA_ID); //Call Function
				}else {
					setErrno(22, DATA_ID);
				}
				
				return retTmp;
			}
		}
		
		//Class for replacing funcPtrs with value
		private static class FuncPtr {
			public static Map<Integer, Map<String, String>> copyAfterFP = new HashMap<>(); //<DATA_ID (of function), <to, from>>
			
			private static String funcReturnTmp = "";
			
			private FuncPtr() {}
			
			public static void saveFuncPtr(BufferedReader lines, String line, final int DATA_ID) {
				StringBuilder build = new StringBuilder();
				String tmp;
				
				String funcName = line.split(" = ")[0].trim();
				if(!funcName.startsWith("fp.")) {
					setErrno(5, DATA_ID);
					
					return;
				}
				
				String funcHead = line.split(" = ")[1].trim();
				
				if(line.contains(") -> {")) { //FuncPtr definition
					funcHead = funcHead.substring(funcHead.indexOf('(') + 1);
					funcHead = funcHead.substring(0, funcHead.indexOf(')'));
					
					build.append(funcHead);
					build.append("\n");
					
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
						
						build.deleteCharAt(build.length() - 1); //Remove "tail '\n'"
					}catch(IOException e) {
						term.logStackTrace(e, FuncPtr.class);
					}
				}else { //Copy funcPtr
					if(!(funcHead.startsWith("fp.") && data.get(DATA_ID).varTmp.containsKey(funcHead))) {
						setErrno(5, DATA_ID);
						
						return;
					}
					
					build.append(data.get(DATA_ID).varTmp.get(funcHead));
				}
				
				DataObject oldValue = data.get(DATA_ID).varTmp.get(funcName);
				if(oldValue != null && oldValue.isFinalData()) {
					setErrno(1, DATA_ID);
					
					return;
				}
				if(oldValue != null)
					oldValue.setFunctionPointer(build.toString());
				else
					data.get(DATA_ID).varTmp.put(funcName, new DataObject().setFunctionPointer(build.toString()));
			}
			
			public static String executeFunc(BufferedReader lines, String line, final int DATA_ID) {
				String lineCopy = line; //Copy pointer to line
				String tmp; //String tmp for brackets count
				int bracketsCount, indexStart, indexEnd, oldIndexStart = 0;
				
				//while line contains functions
				while(lineCopy.matches(".*fp\\.\\w*\\(.*\\).*")) {
					//Reset brackets count
					bracketsCount = 0;
					
					//Set indexStart to start of first "fp." and cut 0 to indexStart of lineCopy
					lineCopy = lineCopy.substring(indexStart = lineCopy.indexOf("fp."));
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
					}
					tmp = lineCopy.substring(0, indexEnd); //Copies function arguments
					
					//Replace func with solution of func
					line = line.substring(0, indexStart) + callFunc(lines, tmp, DATA_ID) + line.substring(indexEnd + indexStart);
					
					lineCopy = lineCopy.substring(indexEnd); //Go to the end of the function
					oldIndexStart = line.indexOf(lineCopy); //Gets indexStart of line
				}
				
				if(line.matches(".*fp\\.\\w*\\(.*\\).*")) //If a function returns a funcPtr -> call it
					line = executeFunc(lines, line, DATA_ID);
				
				return line;
			}
			
			private static String prepareFunc(BufferedReader lines, String func, final int DATA_ID) {
				String retTmp = "";
				func = func.substring(func.indexOf('(')+1); //Gets start of arguments
				
				if(!func.contains("fp.")) { //No nested function
					int lastIndex = func.lastIndexOf(')');
					
					retTmp = func.substring(0, lastIndex); //retTmp is start to end of arguments
					func = func.substring(lastIndex); //Cuts all before the function end
				}
				
				while(func.contains("fp.")) { //While function contain nested functions
					int index = func.indexOf("fp."); //Gets index of first "fp."
					retTmp += func.substring(0, index); //Adds everything between start and "fp." to retTmp
					func = func.substring(index); //Cuts everything before "fp."
					
					int lastIndex = 0, openCount = 0;
					if(func.indexOf('(') == -1) { //FuncPtr without call
						if(func.contains(",")) {
							lastIndex += func.indexOf(",");
						}else {
							lastIndex += func.indexOf(')');
						}
						
						retTmp += func.substring(0, lastIndex); //Adds result of nested function to retTmp
						func = func.substring(lastIndex); //Cuts everything before end of nested function
						continue;
					}
					String funcCopy = func.substring(func.indexOf('('));
					for(lastIndex = 0;lastIndex < funcCopy.length();lastIndex++) {
						char c = funcCopy.charAt(lastIndex);
						if(c == '(')
							openCount++;
						
						if(c == ')')
							openCount--;
						
						if(openCount == 0) {
							break;
						}
					}
					lastIndex += func.indexOf('(') + 1;
					
					retTmp += callFunc(lines, func.substring(0, lastIndex), DATA_ID); //Adds result of nested function to retTmp
					func = func.substring(lastIndex); //Cuts everything before end of nested function
				}
				
				//Adds everything after function to retTmp
				retTmp += func.substring(0, func.lastIndexOf(')'));
				retTmp = Func.replaceFuncsWithValue(new BufferedReader(new StringReader(retTmp)), retTmp, DATA_ID);
				return retTmp;
			}
			
			private static String callFunc(BufferedReader lines, String func, final int DATA_ID) {
				String retTmp = "";
				
				String funcName = func.substring(0, func.indexOf('(')); //Get name of function
				
				func = func.substring(func.indexOf("fp.") + 3); //Cuts everything before "fp."
				
				String args = prepareFunc(lines, func, DATA_ID); //Prepare function
				retTmp += compileFunc(funcName, args, DATA_ID); //Call Function
				
				return retTmp;
			}
			
			private static String compileFunc(String funcName, String funcArgs, final int DATA_ID) {
				if(!data.get(DATA_ID).varTmp.containsKey(funcName)) {
					setErrno(5, DATA_ID);
					
					return "";
				}
				
				final int NEW_DATA_ID = DATA_ID + 1;
				
				String func = data.get(DATA_ID).varTmp.get(funcName).getText();
				String funcHead = func.split("\n", 2)[0]; //Head
				String funcBody = func.split("\n", 2)[1]; //Body
				
				BufferedReader function = new BufferedReader(new StringReader(funcBody));
				
				//Add variables and local variables
				createDataMap(NEW_DATA_ID);
				data.get(NEW_DATA_ID).varArrayTmp.putAll(data.get(DATA_ID).varArrayTmp);
				//Copies must not be final
				data.get(DATA_ID).varTmp.forEach((key, val) -> {
					if(!key.startsWith("$LANG_"))
						data.get(NEW_DATA_ID).varTmp.put(key, new DataObject(val).setFinalData(false));
				});
				
				//Initialize copyAfterFP
				Compiler.FuncPtr.copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
				
				//Set function arguments
				String[] funcVars = funcHead.split(",");
				String tmp = funcArgs;
				for(String var:funcVars) {
					var = var.trim();
					
					int index = tmp.indexOf(",");
					String val = tmp.substring(0, (index == -1)?tmp.length():index);
					
					val = val.trim();
					
					if(var.startsWith("$")) {
						data.get(NEW_DATA_ID).varTmp.put(var, new DataObject(val).setFinalData(false)); //Copy params to func as local params
					}else if(var.startsWith("fp.")) {
						data.get(NEW_DATA_ID).varTmp.put(var, new DataObject(data.get(DATA_ID).varTmp.get(val)).setFinalData(false));
					}else if(var.startsWith("&")) {
						data.get(NEW_DATA_ID).varArrayTmp.put(var, data.get(DATA_ID).varArrayTmp.get(val)); //Copy array to func as local params
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
					term.logStackTrace(e, FuncPtr.class);
				}catch(Exception e) {}
				
				//Add lang after call
				data.get(DATA_ID).lang.putAll(data.get(NEW_DATA_ID).lang);
				
				//Add copyValue after call
				copyAfterFP.get(NEW_DATA_ID).forEach((to, from) -> {
					if(from != null && to != null) {
						DataObject valFrom = data.get(NEW_DATA_ID).varTmp.get(from);
						if(valFrom != null && valFrom.getType() != DataType.NULL) { //var and funcPtr
							if(to.startsWith("fp.") || to.startsWith("$")) {
								DataObject dataTo = data.get(DATA_ID).varTmp.get(to);
								 //$LANG and final vars can't be change
								if(to.startsWith("$LANG") || (dataTo != null && dataTo.isFinalData())) {
									Compiler.setErrno(1, DATA_ID);
									
									return;
								}
								
								data.get(DATA_ID).varTmp.put(to, valFrom);
								
								return;
							}
						}else { //arrPtr
							String[] arr = data.get(NEW_DATA_ID).varArrayTmp.get(from);
							if(arr != null) {
								if(to.startsWith("&")) {
									data.get(DATA_ID).varArrayTmp.put(to, arr);
									
									return;
								}
							}
						}
					}
					
					Compiler.setErrno(21, NEW_DATA_ID);
				});
				
				//Clear copyValue
				copyAfterFP.remove(NEW_DATA_ID);
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				String retTmp = funcReturnTmp; //Get func return or "" (empty)
				funcReturnTmp = ""; //Reset func return for non return funcs
				return retTmp;
			}
		}
	}
}