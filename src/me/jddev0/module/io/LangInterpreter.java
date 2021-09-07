package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import me.jddev0.module.io.LangParser.AbstractSyntaxTree;
import me.jddev0.module.io.LangParser.AbstractSyntaxTree.*;
import me.jddev0.module.io.TerminalIO.Level;

/**
 * IO-Module<br>
 * Lang interpreter for interpreting AST created by LangParser
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangInterpreter {
	private static final String VERSION = "v1.0.0";
	private static final Random RAN = new Random();
	
	private final LangParser parser = new LangParser();
	
	private String langPath;
	private TerminalIO term;
	private LangPlatformAPI langPlatformAPI;
	
	//Fields for return node
	/**
	 * Will be set to true for returning a value
	 */
	private boolean stopParsingFlag;
	private DataObject returnedValue;
	/**
	 * <DATA_ID (of function), <to, from>><br>
	 * Data tmp for "func.copyAfterFP"
	 */
	private Map<Integer, Map<String, String>> copyAfterFP = new HashMap<>();
	
	//Execution flags
	/**
	 * Allow terminal function to redirect to standard input, output, or error if no terminal is available
	 */
	private boolean allowTermRedirect = true;
	
	//DATA
	private Map<Integer, Data> data = new HashMap<>();
	
	//Predefined functions & linker functions (= Predefined functions)
	private Map<String, LangPredefinedFunctionObject> funcs = new HashMap<>();
	//Predefined functions
	private String getArgumentListAsString(List<DataObject> argumentList, boolean returnEmptyStringForEmptyArgumentList) {
		DataObject dataObject = combineDataObjects(argumentList);
		if(dataObject == null)
			return returnEmptyStringForEmptyArgumentList?"":null;
		
		return dataObject.getText();
	}
	private DataObject unaryMathOperationHelper(List<DataObject> argumentList, Function<Number, DataObject> operation, final int DATA_ID) {
		DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 1 argument
			return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
		
		Number number = numberObject.getNumber();
		if(number == null)
			return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
		
		return operation.apply(number);
	}
	private DataObject binaryMathOperationHelper(List<DataObject> argumentList, BiFunction<Number, Number, DataObject> operation, final int DATA_ID) {
		DataObject leftNumberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		DataObject rightNumberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 2 arguments
			return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
		
		Number leftNumber = leftNumberObject.getNumber();
		if(leftNumber == null)
			return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
		
		Number rightNumber = rightNumberObject.getNumber();
		if(rightNumber == null)
			return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
		
		return operation.apply(leftNumber, rightNumber);
	}
	{
		//Reset Functions
		funcs.put("clearVar", (argumentList, DATA_ID) -> {
			DataObject pointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
			DataObject dereferencedVarPointer = null;
			switch(pointerObject.getType()) {
				case VAR_POINTER:
					if(pointerObject.getVariableName() == null)
						dereferencedVarPointer = pointerObject.getVarPointer().getVar();
					break;
				
				case FUNCTION_POINTER:
				case ARRAY:
					dereferencedVarPointer = pointerObject;
					break;
				
				case ARGUMENT_SEPARATOR:
				case CHAR:
				case DOUBLE:
				case ERROR:
				case FLOAT:
				case INT:
				case LONG:
				case NULL:
				case TEXT:
				case VOID:
					break;
			}
			if(dereferencedVarPointer == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			if(dereferencedVarPointer.isFinalData() || variableName.startsWith("$LANG_") || variableName.startsWith("&LANG_"))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			data.get(DATA_ID).var.remove(variableName);
			dereferencedVarPointer.setVariableName(null);
			
			return null;
		});
		funcs.put("clearAllVars", (argumentList, DATA_ID) -> {
			resetVars(DATA_ID);
			return null;
		});
		funcs.put("clearAllArrays", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				new HashSet<>(data.get(DATA_ID).var.entrySet()).forEach(entry -> {
					if(entry.getValue().getType() == DataType.ARRAY)
						data.get(DATA_ID).var.remove(entry.getKey());
				});
				return null;
			}
			
			@Override
			public boolean isDeprecated() {
				return true;
			}
			
			@Override
			public String getDeprecatedRemoveVersion() {
				return "v1.2.0";
			}
			
			@Override
			public String getDeprecatedReplacementFunction() {
				return "func.clearAllVars";
			}
		});
		
		//Error functions
		funcs.put("getErrorString", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				return new DataObject().setText(getAndClearErrnoErrorObject(DATA_ID).getErrorText());
			}
			
			@Override
			public boolean isDeprecated() {
				return true;
			}
			
			@Override
			public String getDeprecatedRemoveVersion() {
				return "v1.2.0";
			}
			
			@Override
			public String getDeprecatedReplacementFunction() {
				return "func.getErrorText";
			}
		});
		funcs.put("getErrorText", (argumentList, DATA_ID) -> new DataObject().setText(getAndClearErrnoErrorObject(DATA_ID).getErrorText()));
		
		//Compiler function
		funcs.put("isCompilerVersionNewer", (argumentList, DATA_ID) -> {
			String langVer = data.get(DATA_ID).lang.getOrDefault("lang.version", VERSION); //If lang.version = null -> return false
			return new DataObject().setBoolean(VERSION.compareTo(langVer) > 0);
		});
		funcs.put("isCompilerVersionOlder", (argumentList, DATA_ID) -> {
			String langVer = data.get(DATA_ID).lang.getOrDefault("lang.version", VERSION); //If lang.version = null -> return false
			return new DataObject().setBoolean(VERSION.compareTo(langVer) < 0);
		});
		
		//System Functions
		funcs.put("sleep", (argumentList, DATA_ID) -> {
			DataObject dataObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			Number number = dataObject.getNumber();
			if(number == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			try {
				Thread.sleep(number.longValue());
			}catch(InterruptedException e) {
				return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
			
			return null;
		});
		funcs.put("currentTimeMillis", (argumentList, DATA_ID) -> new DataObject().setLong(System.currentTimeMillis()));
		funcs.put("repeat", (argumentList, DATA_ID) -> {
			DataObject loopFunctionObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject repeatCountObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR_LOOP, DATA_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			
			Number repeatCountNumber = repeatCountObject.getNumber();
			if(repeatCountNumber == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			long repeatCount = repeatCountNumber.longValue();
			if(repeatCount < 0)
				return setErrnoErrorObject(InterpretingError.NEGATIVE_REPEAT_COUNT, DATA_ID);
			
			for(int i = 0;i < repeatCount;i++) {
				List<DataObject> loopFuncArgumentList = new ArrayList<>();
				loopFuncArgumentList.add(new DataObject().setInt(i));
				callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), loopFuncArgumentList, DATA_ID);
			}
			
			return null;
		});
		funcs.put("repeatWhile", (argumentList, DATA_ID) -> {
			DataObject loopFunctionObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject checkFunctionObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER || checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR_LOOP, DATA_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID).getBoolean())
				callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID);
			
			return null;
		});
		funcs.put("repeatUntil", (argumentList, DATA_ID) -> {
			DataObject loopFunctionObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject checkFunctionObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER || checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR_LOOP, DATA_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(!callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID).getBoolean())
				callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID);
			
			return null;
		});
		funcs.put("getLangRequest", (argumentList, DATA_ID) -> {
			DataObject langRequestObject = combineDataObjects(argumentList);
			if(langRequestObject == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String langValue = data.get(DATA_ID).lang.get(langRequestObject.getText());
			if(langValue == null)
				return setErrnoErrorObject(InterpretingError.LANG_REQ_NOT_FOUND, DATA_ID);
			
			return new DataObject(langValue);
		});
		funcs.put("makeFinal", (argumentList, DATA_ID) -> {
			DataObject dataObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(dataObject.getVariableName() == null && dataObject.getType() == DataType.VAR_POINTER) {
				dataObject = dataObject.getVarPointer().getVar();
				
				if(dataObject == null)
					return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			}
			
			if(dataObject.getVariableName() != null && dataObject.getVariableName().matches("(\\$|&)LANG_.*"))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			dataObject.setFinalData(true);
			
			return null;
		});
		funcs.put("condition", (argumentList, DATA_ID) -> {
			DataObject condition = combineDataObjects(argumentList);
			if(condition == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			try {
				return new DataObject().setBoolean(interpretCondition(parser.parseCondition(condition.getText()), DATA_ID));
			}catch(IOException e) {
				return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
		});
		funcs.put("exec", (argumentList, DATA_ID) -> {
			DataObject text = combineDataObjects(argumentList);
			if(text == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			BufferedReader lines = new BufferedReader(new StringReader(text.getText()));
			
			final int NEW_DATA_ID = DATA_ID + 1;
			//Add variables and local variables
			createDataMap(NEW_DATA_ID);
			//Create clean data map without coping from caller's data map
			
			//Initialize copyAfterFP
			copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
			try {
				interpretLines(lines, NEW_DATA_ID);
			}catch(IOException e) {
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				//Clear copyAfterFP
				copyAfterFP.remove(NEW_DATA_ID);
				
				return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
			
			//Add lang after call
			data.get(DATA_ID).lang.putAll(data.get(NEW_DATA_ID).lang);
			
			executeAndClearCopyAfterFP(DATA_ID, NEW_DATA_ID);
			
			//Remove data map
			data.remove(NEW_DATA_ID);
			
			return getAndResetReturnValue();
		});
		funcs.put("isTerminalAvailable", (argumentList, DATA_ID) -> new DataObject().setBoolean(term != null));
		
		//IO Functions
		funcs.put("readTerminal", (argumentList, DATA_ID) -> {
			if(term == null && !allowTermRedirect)
				return setErrnoErrorObject(InterpretingError.NO_TERMINAL, DATA_ID);
			
			DataObject messageObject = combineDataObjects(argumentList);
			String message = messageObject == null?"":messageObject.getText();
			
			if(term == null) {
				setErrno(InterpretingError.NO_TERMINAL_WARNING, DATA_ID);
				
				if(!message.isEmpty())
					System.out.println(message);
				System.out.print("Input: ");
				
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
					String line = reader.readLine();
					if(line == null)
						return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
					return new DataObject(line);
				}catch(IOException e) {
					return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
				}
			}else {
				try {
					return new DataObject().setText(langPlatformAPI.showInputDialog(message));
				}catch(Exception e) {
					return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, DATA_ID);
				}
			}
		});
		funcs.put("printTerminal", (argumentList, DATA_ID) -> {
			if(term == null && !allowTermRedirect)
				return setErrnoErrorObject(InterpretingError.NO_TERMINAL, DATA_ID);
			
			DataObject logLevelObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = combineDataObjects(argumentList);
			if(messageObject == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			Number logLevelNumber = logLevelObject.getNumber();
			if(logLevelNumber == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			int logLevel = logLevelNumber.intValue();
			
			Level level = null;
			for(Level lvl:Level.values()) {
				if(lvl.getLevel() == logLevel) {
					level = lvl;
					
					break;
				}
			}
			if(level == null)
				return setErrnoErrorObject(InterpretingError.INVALID_LOG_LEVEL, DATA_ID);
			
			if(term == null) {
				setErrno(InterpretingError.NO_TERMINAL_WARNING, DATA_ID);
				
				@SuppressWarnings("resource")
				PrintStream stream = logLevel > 3?System.err:System.out; //Write to standard error if the log level is WARNING or higher
				stream.printf("[%-8s]: ", level.getLevelName());
				stream.println(messageObject.getText());
				return null;
			}else {
				term.logln(level, "[From lang file]: " + messageObject.getText(), LangInterpreter.class);
			}
			
			return null;
		});
		funcs.put("printError", (argumentList, DATA_ID) -> {
			if(term == null && !allowTermRedirect)
				return setErrnoErrorObject(InterpretingError.NO_TERMINAL, DATA_ID);
			
			InterpretingError error = getAndClearErrnoErrorObject(DATA_ID);
			int errno = error.getErrorCode();
			Level level = null;
			if(errno > 0)
				level = Level.ERROR;
			else if(errno < 0)
				level = Level.WARNING;
			else
				level = Level.INFO;
			
			DataObject messageObject = combineDataObjects(argumentList);
			if(term == null) {
				@SuppressWarnings("resource")
				PrintStream stream = level.getLevel() > 3?System.err:System.out; //Write to standard error if the log level is WARNING or higher
				stream.printf("[%-8s]: ", level.getLevelName());
				stream.println(((messageObject == null || messageObject.getType() == DataType.VOID)?"":(messageObject.getText() + ": ")) + error.getErrorText());
			}else {
				term.logln(level, "[From lang file]: " + ((messageObject == null || messageObject.getType() == DataType.VOID)?
				"":(messageObject.getText() + ": ")) + error.getErrorText(), LangInterpreter.class);
			}
			return null;
		});
		funcs.put("input", (argumentList, DATA_ID) -> {
			Number maxCount = null;
			
			if(!argumentList.isEmpty()) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(argumentList.size() > 0) //Not 0 or 1 arguments
					return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				maxCount = numberObject.getNumber();
				if(maxCount == null)
					return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			}
			
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				if(maxCount == null) {
					String line = reader.readLine();
					if(line == null)
						return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
					return new DataObject(line);
				}else {
					char[] buf = new char[maxCount.intValue()];
					int count = reader.read(buf);
					if(count == -1)
						return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
					return new DataObject(new String(buf));
				}
			}catch(IOException e) {
				return setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
		});
		funcs.put("print", (argumentList, DATA_ID) -> {
			DataObject textObject = combineDataObjects(argumentList);
			if(textObject == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			System.out.print(textObject.getText());
			return null;
		});
		funcs.put("println", (argumentList, DATA_ID) -> {
			DataObject textObject = combineDataObjects(argumentList);
			if(textObject == null)
				System.out.println();
			else
				System.out.println(textObject.getText());
			return null;
		});
		funcs.put("error", (argumentList, DATA_ID) -> {
			DataObject textObject = combineDataObjects(argumentList);
			if(textObject == null)
				System.err.println();
			else
				System.err.println(textObject.getText());
			return null;
		});
		funcs.put("errorln", (argumentList, DATA_ID) -> {
			DataObject textObject = combineDataObjects(argumentList);
			if(textObject == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			System.err.println(textObject.getText());
			return null;
		});
		
		//Number functions
		funcs.put("hexToDez", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				DataObject hexObject = combineDataObjects(argumentList);
				if(hexObject == null)
					return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				
				String hexString = hexObject.getText();
				if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
					return setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
				
				try {
					return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
				}catch(NumberFormatException e) {
					return setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
				}
			}
			
			@Override
			public boolean isDeprecated() {
				return true;
			}
			
			@Override
			public String getDeprecatedRemoveVersion() {
				return "v1.2.0";
			}
			
			@Override
			public String getDeprecatedReplacementFunction() {
				return "func.hexToDec";
			}
		});
		funcs.put("hexToDec", (argumentList, DATA_ID) -> {
			DataObject hexObject = combineDataObjects(argumentList);
			if(hexObject == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String hexString = hexObject.getText();
			if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
				return setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
			}catch(NumberFormatException e) {
				return setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
			}
		});
		
		//Character functions
		funcs.put("toValue", (argumentList, DATA_ID) -> {
			DataObject charObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(charObject.getType() != DataType.CHAR)
				return setErrnoErrorObject(InterpretingError.NO_CHAR, DATA_ID);
			
			return new DataObject().setInt(charObject.getChar());
		});
		funcs.put("toChar", (argumentList, DATA_ID) -> {
			DataObject asciiValueObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			Number asciiValue = asciiValueObject.getNumber();
			if(asciiValue == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			return new DataObject().setChar((char)asciiValue.intValue());
		});
		
		//Text functions
		funcs.put("strlen", (argumentList, DATA_ID) -> new DataObject().setInt(getArgumentListAsString(argumentList, true).length()));
		funcs.put("toUpper", (argumentList, DATA_ID) -> new DataObject(getArgumentListAsString(argumentList, true).toUpperCase()));
		funcs.put("toLower", (argumentList, DATA_ID) -> new DataObject(getArgumentListAsString(argumentList, true).toLowerCase()));
		funcs.put("trim", (argumentList, DATA_ID) -> new DataObject(getArgumentListAsString(argumentList, true).trim()));
		funcs.put("replace", (argumentList, DATA_ID) -> {
			DataObject textObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject regexObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			String replacement = getArgumentListAsString(argumentList, false);
			if(replacement == null) //Not 3 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			return new DataObject(textObject.getText().replaceAll(regexObject.getText(), replacement));
		});
		funcs.put("substring", (argumentList, DATA_ID) -> {
			DataObject textObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject startIndexObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject endIndexObject;
			//3rd argument is optional
			if(argumentList.isEmpty())
				endIndexObject = null;
			else
				endIndexObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			Number startIndex = startIndexObject.getNumber();
			if(startIndex == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			try {
				if(endIndexObject == null) {
					return new DataObject(textObject.getText().substring(startIndex.intValue()));
				}else {
					Number endIndex = endIndexObject.getNumber();
					if(endIndex == null)
						return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
					
					return new DataObject(textObject.getText().substring(startIndex.intValue(), endIndex.intValue()));
				}
			}catch(StringIndexOutOfBoundsException e) {
				return setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			}
		});
		funcs.put("split", (argumentList, DATA_ID) -> {
			DataObject arrayPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject textObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject regexObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject maxSplitCountObject;
			//4th argument is optional
			if(argumentList.isEmpty())
				maxSplitCountObject = null;
			else
				maxSplitCountObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
			if(argumentList.size() > 0) //Not 3 or 4 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String[] arrTmp;
			
			if(maxSplitCountObject == null) {
				arrTmp = textObject.getText().split(regexObject.getText());
			}else {
				Number maxSplitCount = maxSplitCountObject.getNumber();
				if(maxSplitCount == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				arrTmp = textObject.getText().split(regexObject.getText(), maxSplitCount.intValue());
			}
			
			String arrPtr = arrayPointerObject.getType() == DataType.ARRAY?arrayPointerObject.getVariableName():arrayPointerObject.getText();
			
			AbstractSyntaxTree ast = new AbstractSyntaxTree();
			List<Node> argumentListArrayMake = new LinkedList<>();
			argumentListArrayMake.add(new TextValueNode(arrPtr));
			argumentListArrayMake.add(new ArgumentSeparatorNode(","));
			argumentListArrayMake.add(new IntValueNode(arrTmp.length));
			ast.addChild(new FunctionCallNode(argumentListArrayMake, "func.arrayMake"));
			
			List<Node> argumentListArraySetAll = new LinkedList<>();
			argumentListArraySetAll.add(new VariableNameNode(arrPtr));
			argumentListArraySetAll.add(new ArgumentSeparatorNode(","));
			for(String ele:arrTmp) {
				argumentListArraySetAll.add(new TextValueNode(ele));
				argumentListArraySetAll.add(new ArgumentSeparatorNode(","));
			}
			argumentListArraySetAll.remove(argumentListArraySetAll.size() - 1); //Remove leading argument separator node
			ast.addChild(new FunctionCallNode(argumentListArraySetAll, "func.arraySetAll"));
			
			interpretAST(ast, DATA_ID);
			
			InterpretingError err;
			if((err = getAndClearErrnoErrorObject(DATA_ID)) != InterpretingError.NO_ERROR) {
				setErrno(err, DATA_ID);
				
				return new DataObject().setError(new ErrorObject(err));
			}
			
			return null;
		});
		
		//Math functions
		funcs.put("rand", (argumentList, DATA_ID) -> new DataObject().setInt(RAN.nextInt(data.get(DATA_ID).var.get("$LANG_RAND_MAX").getInt())));
		funcs.put("addi", (argumentList, DATA_ID) -> {
			int sum = 0;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				sum += number.intValue();
			}
			
			return new DataObject().setInt(sum);
		});
		funcs.put("subi", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() - rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("muli", (argumentList, DATA_ID) -> {
			int prod = 1;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.intValue();
			}
			
			return new DataObject().setInt(prod);
		});
		funcs.put("divi", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setInt(leftNumber.intValue() / rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("modi", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setInt(leftNumber.intValue() % rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("andi", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() & rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("ori", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() | rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("xori", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() ^ rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("noti", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(~number.intValue());
			}, DATA_ID);
		});
		funcs.put("lshifti", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() << rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("rshifti", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() >> rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("rzshifti", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() >>> rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("addl", (argumentList, DATA_ID) -> {
			long sum = 0L;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				sum += number.longValue();
			}
			
			return new DataObject().setLong(sum);
		});
		funcs.put("subl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() - rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("mull", (argumentList, DATA_ID) -> {
			long prod = 1L;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.longValue();
			}
			
			return new DataObject().setLong(prod);
		});
		funcs.put("divl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setLong(leftNumber.longValue() / rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("modl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setLong(leftNumber.longValue() % rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("andl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() & rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("orl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() | rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("xorl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() ^ rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("notl", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(~number.longValue());
			}, DATA_ID);
		});
		funcs.put("lshiftl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() << rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("rshiftl",(argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() >> rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("rzshiftl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() >>> rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("addd", (argumentList, DATA_ID) -> {
			double sum = 0.d;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				sum += number.doubleValue();
			}
			
			return new DataObject().setDouble(sum);
		});
		funcs.put("subd", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(leftNumber.doubleValue() - rightNumber.doubleValue());
			}, DATA_ID);
		});
		funcs.put("muld", (argumentList, DATA_ID) -> {
			double prod = 1.d;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.doubleValue();
			}
			
			return new DataObject().setDouble(prod);
		});
		funcs.put("divd", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.doubleValue() == 0.d)
					return setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setDouble(leftNumber.doubleValue() / rightNumber.doubleValue());
			}, DATA_ID);
		});
		funcs.put("pow", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(Math.pow(leftNumber.doubleValue(), rightNumber.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("sqrt", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.sqrt(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("dtoi", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue());
			}, DATA_ID);
		});
		funcs.put("dtol", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue());
			}, DATA_ID);
		});
		funcs.put("toNumber", (argumentList, DATA_ID) -> {
			DataObject dataObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			switch(dataObject.getType()) {
				case TEXT:
					String txt = dataObject.getText();
					
					//INT
					try {
						return new DataObject().setInt(Integer.parseInt(txt));
					}catch(NumberFormatException ignore) {}
					
					//LONG
					try {
						return new DataObject().setLong(Long.parseLong(txt));
					}catch(NumberFormatException ignore) {}
					
					//FLOAT
					try {
						Float floatNumber = Float.parseFloat(txt);
						if(floatNumber != Float.POSITIVE_INFINITY && floatNumber != Float.NEGATIVE_INFINITY) {
							return new DataObject().setFloat(floatNumber);
						}
					}catch(NumberFormatException ignore) {}
					
					//DOUBLE
					try {
						return new DataObject().setDouble(Double.parseDouble(txt));
					}catch(NumberFormatException ignore) {}
					
					//CHAR
					if(txt.length() == 1)
						new DataObject().setInt(txt.charAt(0));
					
					return null;
					
				case ARRAY:
					return new DataObject().setInt(dataObject.getArray().length);
				
				case CHAR:
					return new DataObject().setInt(dataObject.getChar());
					
				case ERROR:
					return new DataObject().setInt(dataObject.getError().getErrno());
				
				case INT:
				case LONG:
				case FLOAT:
				case DOUBLE:
					return dataObject;
				
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case VOID:
				case NULL:
				case ARGUMENT_SEPARATOR:
					return null;
			}
			
			return null;
		});
		funcs.put("ceil", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong((long)Math.ceil(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("floor", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong((long)Math.floor(number.doubleValue()));
			}, DATA_ID);
		});
		
		//FuncPtr functions
		funcs.put("copyAfterFP", (argumentList, DATA_ID) -> {
			DataObject toPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject fromPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String to = null;
			switch(toPointerObject.getType()) {
				case ARRAY:
				case FUNCTION_POINTER:
					to = toPointerObject.getVariableName();
					break;
				
				case VAR_POINTER:
					DataObject toVar = toPointerObject.getVarPointer().getVar();
					if(toVar == null)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
						
					to = toVar.getVariableName();
					break;
					
				case TEXT:
					to = toPointerObject.getText();
					if(to.contains("[") && to.contains("]")) //"Dereference Pointer":  e.g.: "$[abc]" -> "$abc"
						to = to.substring(0, to.indexOf('[')) + to.substring(to.indexOf('[') + 1, to.lastIndexOf(']'));
					break;
				
				case ARGUMENT_SEPARATOR:
				case CHAR:
				case DOUBLE:
				case ERROR:
				case FLOAT:
				case INT:
				case LONG:
				case NULL:
				case VOID:
					break;
			}
			if(to == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			String from = null;
			switch(fromPointerObject.getType()) {
				case ARRAY:
				case FUNCTION_POINTER:
					from = fromPointerObject.getVariableName();
					break;
				
				case VAR_POINTER:
					DataObject fromVar = fromPointerObject.getVarPointer().getVar();
					if(fromVar == null)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
						
					from = fromVar.getVariableName();
					break;
					
				case TEXT:
					from = fromPointerObject.getText();
					if(from.contains("[") && from.contains("]")) //"Dereference Pointer":  e.g.: "$[abc]" -> "$abc"
						from = from.substring(0, from.indexOf('[')) + from.substring(from.indexOf('[') + 1, from.lastIndexOf(']'));
					break;
				
				case ARGUMENT_SEPARATOR:
				case CHAR:
				case DOUBLE:
				case ERROR:
				case FLOAT:
				case INT:
				case LONG:
				case NULL:
				case VOID:
					break;
			}
			if(from == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			copyAfterFP.get(DATA_ID).put(to, from);
			
			return null;
		});
		
		//Array functions
		funcs.put("arrayMake", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = null;
			DataObject lengthObject = null;
			
			DataObject dataObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) {
				arrPointerObject = dataObject;
				
				lengthObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
				
				if(argumentList.size() > 0) //Not 1 or 2 arguments
					return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			}else {
				lengthObject = dataObject;
			}
			
			String arrPtr;
			if(arrPointerObject == null || arrPointerObject.getType() == DataType.ARRAY) {
				arrPtr = null;
			}else if(arrPointerObject.getType() == DataType.TEXT) {
				arrPtr = arrPointerObject.getText();
				if(!arrPtr.startsWith("&"))
					return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			}else {
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			}
			
			Number lenghtNumber = lengthObject.getNumber();
			if(lenghtNumber == null)
				return setErrnoErrorObject(InterpretingError.LENGTH_NAN, DATA_ID);
			int length = lenghtNumber.intValue();
			
			if(length < 0)
				return setErrnoErrorObject(InterpretingError.NEGATIVE_ARRAY_LEN, DATA_ID);
			else if(length == 0)
				return setErrnoErrorObject(InterpretingError.EMPTY_ARRAY, DATA_ID);
			
			DataObject oldData = arrPtr == null?arrPointerObject:data.get(DATA_ID).var.get(arrPtr);
			if((oldData != null && oldData.isFinalData()) || (arrPtr != null && arrPtr.startsWith("&LANG_")))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			DataObject[] arr = new DataObject[length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject().setNull();
			if(oldData != null)
				oldData.setArray(arr);
			else if(arrPointerObject == null)
				return new DataObject().setArray(arr);
			else
				data.get(DATA_ID).var.put(arrPtr, new DataObject().setArray(arr).setVariableName(arrPtr));
			
			return null;
		});
		funcs.put("arraySet", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject valueObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 3 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			Number indexNumber = indexObject.getNumber();
			if(indexNumber == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			int index = indexNumber.intValue();
			
			if(index < 0)
				return setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			else if(index >= arr.length)
				return setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			
			arr[index].setData(new DataObject(valueObject));
			
			return null;
		});
		funcs.put("arraySetAll", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() == 0) //Not enough arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			DataObject valueObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() == 0) { //arraySetAll with one value
				for(int i = 0;i < arr.length;i++)
					arr[i] = new DataObject(valueObject);
				
				return null;
			}
			
			if(arr.length == 0) //Too many arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			arr[0] = valueObject;
			for(int i = 1;i < arr.length;i++) {
				arr[i] = new DataObject(getNextArgumentAndRemoveUsedDataObjects(argumentList, true));
				
				if(argumentList.size() == 0 && i != arr.length - 1) //Not enough arguments
					return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			}
			
			if(argumentList.size() > 0) //Too many arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			return null;
		});
		funcs.put("arrayGet", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			Number indexNumber = indexObject.getNumber();
			if(indexNumber == null)
				return setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			int index = indexNumber.intValue();
			
			if(index < 0)
				return setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			else if(index >= arr.length)
				return setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			
			return arr[index];
		});
		funcs.put("arrayGetAll", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			StringBuilder builder = new StringBuilder();
			
			for(DataObject ele:arr) {
				builder.append(ele.getText());
				builder.append(", ");
			}
			if(builder.length() > 0) //Remove last ", " only if at least 1 element is in array
				builder.delete(builder.length() - 2, builder.length()); //Remove leading ", "
			return new DataObject(builder.toString());
		});
		funcs.put("arrayLength", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			return new DataObject().setInt(arr.length);
		});
		funcs.put("arrayForEach", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
			
			for(DataObject ele:arr) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(ele);
				callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, DATA_ID);
			}
			
			return null;
		});
		funcs.put("randChoice", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(arrPointerObject.getType() == DataType.ARRAY) {
				if(argumentList.size() > 0) //Not 1 argument
					return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				
				DataObject[] arr = arrPointerObject.getArray();
				if(arr == null)
					return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
				
				return arr.length == 0?null:arr[RAN.nextInt(arr.length)];
			}

			//No array Pointer
			List<DataObject> dataObjects = new LinkedList<>();
			dataObjects.add(arrPointerObject);
			
			//Remove argument separator object
			if(argumentList.size() > 0)
				argumentList.remove(0);
			
			while(argumentList.size() > 0)
				dataObjects.add(getNextArgumentAndRemoveUsedDataObjects(argumentList, true));
			
			return dataObjects.size() == 0?null:dataObjects.get(RAN.nextInt(dataObjects.size()));
		});
		funcs.put("arrayDelete", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			for(DataObject ele:arr)
				ele.setNull();
			
			return null;
		});
		funcs.put("arrayClear", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			String variableName = arrPointerObject.getVariableName();
			if(variableName == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			data.get(DATA_ID).var.remove(variableName);
			return null;
		});
	}
	//Linker functions
	private DataObject executeLinkerFunction(List<DataObject> argumentList, Consumer<Integer> function, int DATA_ID) {
		DataObject langFileNameObject = getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(langFileNameObject.getType() != DataType.TEXT)
			return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
		
		String langFileName = langFileNameObject.getText();
		if(!langFileName.endsWith(".lang"))
			return setErrnoErrorObject(InterpretingError.NO_LANG_FILE, DATA_ID);
		
		String absolutePath;
		if(new File(langFileName).isAbsolute())
			absolutePath = langFileName;
		else
			absolutePath = langPath + File.separator + langFileName;
		
		final int NEW_DATA_ID = DATA_ID + 1;
		
		String langPathTmp = absolutePath;
		langPathTmp = langPlatformAPI.getLangPath(langPathTmp);
		
		//Change lang path for createDataMap
		String oldLangPath = langPath;
		langPath = langPathTmp;
		createDataMap(NEW_DATA_ID);
		
		try(BufferedReader reader = langPlatformAPI.getLangReader(absolutePath)) {
			interpretLines(reader, NEW_DATA_ID);
		}catch(IOException e) {
			if(term == null)
				e.printStackTrace();
			else
				term.logStackTrace(e, LangInterpreter.class);
			
			data.remove(NEW_DATA_ID);
			return setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, DATA_ID);
		}
		
		function.accept(NEW_DATA_ID);
		
		//Remove data map
		data.remove(NEW_DATA_ID);
		
		//Set lang path to old lang path
		langPath = oldLangPath;
		
		//Get returned value from executed lang file
		return getAndResetReturnValue();
	}
	{
		funcs.put("bindLibrary", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				return executeLinkerFunction(argumentList, NEW_DATA_ID -> {
					//Copy all vars, arrPtrs and funcPtrs
					data.get(NEW_DATA_ID).var.forEach((name, val) -> {
						DataObject oldData = data.get(DATA_ID).var.get(name);
						if(!name.startsWith("$LANG_") && !name.startsWith("&LANG_") && (oldData == null || !oldData.isFinalData())) { //No LANG data vars and no final data
							data.get(DATA_ID).var.put(name, val);
						}
					});
				}, DATA_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("link", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				return executeLinkerFunction(argumentList, NEW_DATA_ID -> {
					//Copy linked translation map (not "lang.* = *") to the "link caller"'s translation map
					data.get(NEW_DATA_ID).lang.forEach((k, v) -> {
						if(!k.startsWith("lang.")) {
							data.get(DATA_ID).lang.put(k, v); //Copy to "old" DATA_ID
						}
					});
				}, DATA_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
	}
	
	public LangInterpreter(String langPath, TerminalIO term, LangPlatformAPI langPlatformAPI) {
		this.langPath = langPath;
		this.term = term;
		this.langPlatformAPI = langPlatformAPI;
		
		createDataMap(0);
	}
	
	public LangParser.AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
		return parser.parseLines(lines);
	}
	
	public void interpretAST(LangParser.AbstractSyntaxTree ast) {
		interpretAST(ast, 0);
	}
	
	public void interpretLines(BufferedReader lines) throws IOException {
		interpretLines(lines, 0);
	}
	
	public Map<Integer, Data> getData() {
		return new HashMap<>(data);
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject combineDataObjects(List<DataObject> dataObjects) {
		dataObjects = new LinkedList<>(dataObjects);
		dataObjects.removeIf(Objects::isNull);
		
		if(dataObjects.size() == 0)
			return null;
		
		if(dataObjects.size() == 1)
			return dataObjects.get(0);
		
		//Remove all void objects
		dataObjects.removeIf(dataObject -> dataObject.getType() == DataType.VOID);
		
		//Return a single void object if every data object was a void object
		if(dataObjects.size() == 0)
			return new DataObject().setVoid();
		
		if(dataObjects.size() == 1)
			return dataObjects.get(0);
		
		//Combine everything to a single text object
		final StringBuilder builder = new StringBuilder();
		dataObjects.forEach(builder::append);
		return new DataObject(builder.toString());
	}
	
	private boolean interpretCondition(ConditionNode node, final int DATA_ID) {
		return interpretConditionNode(node, DATA_ID).getBoolean();
	}
	
	private void interpretLines(BufferedReader lines, final int DATA_ID) throws IOException {
		interpretAST(parseLines(lines), DATA_ID);
	}
	
	private void interpretAST(LangParser.AbstractSyntaxTree ast, final int DATA_ID) {
		for(Node node:ast) {
			if(stopParsingFlag)
				return;
			
			interpretNode(node, DATA_ID);
		}
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretNode(Node node, final int DATA_ID) {
		try {
			switch(node.getNodeType()) {
				case UNPROCESSED_VARIABLE_NAME:
					return interpretNode(processUnprocessedVariableNameNode((UnprocessedVariableNameNode)node, DATA_ID), DATA_ID);
					
				case FUNCTION_CALL_PREVIOUS_NODE_VALUE:
					return interpretNode(processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)node, null, DATA_ID), DATA_ID);
				
				case LIST:
					//Interpret a group of nodes
					return interpretListNode((ListNode)node, DATA_ID);
				
				case CHAR_VALUE:
				case TEXT_VALUE:
				case INT_VALUE:
				case LONG_VALUE:
				case FLOAT_VALUE:
				case DOUBLE_VALUE:
				case NULL_VALUE:
				case VOID_VALUE:
					return interpretValueNode((ValueNode)node, DATA_ID);
				
				case PARSING_ERROR:
					return interpretParsingErrorNode((ParsingErrorNode)node, DATA_ID);
				
				case IF_STATEMENT:
					return new DataObject().setBoolean(interpretIfStatementNode((IfStatementNode)node, DATA_ID));
				
				case IF_STATEMENT_PART_ELSE:
				case IF_STATEMENT_PART_IF:
					return new DataObject().setBoolean(interpretIfStatementPartNode((IfStatementPartNode)node, DATA_ID));
				
				case CONDITION:
					return interpretConditionNode((ConditionNode)node, DATA_ID);
				
				case RETURN:
					interpretReturnNode((ReturnNode)node, DATA_ID);
					return null;
				
				case ASSIGNMENT:
					return interpretAssignmentNode((AssignmentNode)node, DATA_ID);
				
				case VARIABLE_NAME:
					return interpretVariableNameNode((VariableNameNode)node, DATA_ID);
				
				case ESCAPE_SEQUENCE:
					return interpretEscapeSequenceNode((EscapeSequenceNode)node, DATA_ID);
				
				case ARGUMENT_SEPARATOR:
					return interpretArgumentSeparatotNode((ArgumentSeparatorNode)node, DATA_ID);
				
				case FUNCTION_CALL:
					return interpretFunctionCallNode((FunctionCallNode)node, DATA_ID);
				
				case FUNCTION_DEFINITION:
					return interpretFunctionDefinitionNode((FunctionDefinitionNode)node, DATA_ID);
				
				case GENERAL:
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return null;
	}
	
	private int getIndexOfMatchingBracket(String string, int startIndex, int endIndex, char openedBracket, char closedBracket) {
		int bracketCount = 0;
		for(int i = startIndex;i < endIndex && i < string.length();i++) {
			char c = string.charAt(i);
			
			//Ignore escaped chars
			if(c == '\\') {
				i++;
				
				continue;
			}
			
			if(c == openedBracket) {
				bracketCount++;
			}else if(c == closedBracket) {
				bracketCount--;
				
				if(bracketCount == 0)
					return i;
			}
		}
		
		return -1;
	}
	/**
	 * @param variablePrefixAppendAfterSearch If no part of the variable name matched an existing variable, the variable prefix will be added to the returned TextValueNode<br>
	 *                                             (e.g. "func.abc" ("func." is not part of the variableNames in the set))
	 * @param supportsPointerReferencing If true, this node will return pointer reference as VariableNameNode<br>
	 *                                   (e.g. $[abc] is not in variableNames, but $abc is -> $[abc] will return a VariableNameNode)
	 */
	private Node convertVariableNameToVariableNameNodeOrComposition(String variableName, Set<String> variableNames,
	String variablePrefixAppendAfterSearch, final boolean supportsPointerReferencing) {
		Optional<String> optionalReturnedVariableName = variableNames.stream().filter(varName -> {
			return variableName.startsWith(varName);
		}).sorted((s0, s1) -> { //Sort keySet from large to small length (e.g.: $abcd and $abc and $ab)
			if(s0.length() == s1.length())
				return 0;
			
			return (s0.length() < s1.length())?1:-1;
		}).findFirst();
		
		if(!optionalReturnedVariableName.isPresent()) {
			if(supportsPointerReferencing && variableName.contains("[") && variableName.contains("]")) { //Check dereferenced variable name
				int indexOpeningBracket = variableName.indexOf("[");
				int indexMatchingBracket = getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
				if(indexMatchingBracket != -1) {
					String modifiedVariableName = variableName;
					String text = null;
					if(indexMatchingBracket != modifiedVariableName.length() - 1) {
						text = modifiedVariableName.substring(indexMatchingBracket + 1);
						modifiedVariableName = modifiedVariableName.substring(0, indexMatchingBracket + 1);
					}
					
					Node returnedNode = convertVariableNameToVariableNameNodeOrComposition(modifiedVariableName.substring(0, indexOpeningBracket) +
					modifiedVariableName.substring(indexOpeningBracket + 1, indexMatchingBracket), variableNames, "", supportsPointerReferencing);
					
					switch(returnedNode.getNodeType()) {
						case VARIABLE_NAME: //Variable was found without additional text -> valid pointer reference
							if(text == null)
								return new VariableNameNode(variablePrefixAppendAfterSearch + variableName);
							
							//Variable composition
							List<Node> nodes = new ArrayList<>();
							nodes.add(new VariableNameNode(variablePrefixAppendAfterSearch + modifiedVariableName));
							nodes.add(new TextValueNode(text));
							return new ListNode(nodes);
						
						case LIST: //Variable was found with additional text -> no valid pointer reference
						case TEXT_VALUE: //Variable was not found
						default: //Default should never be reached
							return new TextValueNode(variablePrefixAppendAfterSearch + variableName);
					}
				}
			}
			
			return new TextValueNode(variablePrefixAppendAfterSearch + variableName);
		}
		
		String returendVariableName = optionalReturnedVariableName.get();
		if(returendVariableName.length() == variableName.length())
			return new VariableNameNode(variablePrefixAppendAfterSearch + variableName);
		
		//Variable composition
		List<Node> nodes = new ArrayList<>();
		nodes.add(new VariableNameNode(variablePrefixAppendAfterSearch + returendVariableName)); //Add matching part of variable as VariableNameNode
		nodes.add(new TextValueNode(variableName.substring(returendVariableName.length()))); //Add composition part as TextValueNode
		return new ListNode(nodes);
	}
	private Node processUnprocessedVariableNameNode(UnprocessedVariableNameNode node, final int DATA_ID) {
		String variableName = node.getVariableName();
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp."))
			return convertVariableNameToVariableNameNodeOrComposition(variableName, data.get(DATA_ID).var.keySet(), "", variableName.startsWith("$"));
		
		final boolean isLinkerFunction;
		if(variableName.startsWith("func.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(5);
		}else if(variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(7);
		}else {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			return new TextValueNode(variableName);
		}
		
		return convertVariableNameToVariableNameNodeOrComposition(variableName, funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).map(Entry<String, LangPredefinedFunctionObject>::getKey).collect(Collectors.toSet()), isLinkerFunction?"linker.":"func.", false);
	}
	
	private Node processFunctionCallPreviousNodeValueNode(FunctionCallPreviousNodeValueNode node, DataObject previousValue, final int DATA_ID) {
		if(previousValue != null && previousValue.getType() == DataType.FUNCTION_POINTER)
			return node;
		
		//Previous node value wasn't a function -> return children of node in between "(" and ")" as ListNode
		List<Node> nodes = new ArrayList<>();
		nodes.add(new TextValueNode("("));
		nodes.addAll(node.getChildren());
		nodes.add(new TextValueNode(")"));
		return new ListNode(nodes);
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretListNode(ListNode node, final int DATA_ID) {
		List<DataObject> dataObjects = new LinkedList<>();
		DataObject previousDataObject = null;
		for(Node childNode:node.getChildren()) {
			if(childNode.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
				try {
					Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)childNode, previousDataObject, DATA_ID);
					if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
						dataObjects.remove(dataObjects.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
						dataObjects.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, DATA_ID));
					}else {
						dataObjects.add(interpretNode(ret, DATA_ID));
					}
				}catch(ClassCastException e) {
					dataObjects.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID));
				}
				
				previousDataObject = dataObjects.get(dataObjects.size() - 1);
				
				continue;
			}
			
			DataObject ret = interpretNode(childNode, DATA_ID);
			if(ret != null)
				dataObjects.add(ret);
			
			previousDataObject = ret;
		}
		
		return combineDataObjects(dataObjects);
	}
	
	private DataObject interpretValueNode(ValueNode node, final int DATA_ID) {
		try {
			switch(node.getNodeType()) {
				case CHAR_VALUE:
					return new DataObject().setChar(((CharValueNode)node).getChar());
				case TEXT_VALUE:
					return new DataObject().setText(((TextValueNode)node).getText());
				case INT_VALUE:
					return new DataObject().setInt(((IntValueNode)node).getInt());
				case LONG_VALUE:
					return new DataObject().setLong(((LongValueNode)node).getLong());
				case FLOAT_VALUE:
					return new DataObject().setFloat(((FloatValueNode)node).getFloat());
				case DOUBLE_VALUE:
					return new DataObject().setDouble(((DoubleValueNode)node).getDouble());
				case NULL_VALUE:
					return new DataObject().setNull();
				case VOID_VALUE:
					return new DataObject().setVoid();
				
				default:
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return new DataObject().setError(new ErrorObject(InterpretingError.INVALID_AST_NODE));
	}
	
	private DataObject interpretParsingErrorNode(ParsingErrorNode node, final int DATA_ID) {
		InterpretingError error = null;
		
		switch(node.getError()) {
			case BRACKET_MISMATCH:
				error = InterpretingError.BRACKET_MISMATCH;
				break;
			
			case CONDITION_MISSING:
				error = InterpretingError.IF_CONDITION_MISSING;
				break;
			
			case EOF:
				error = InterpretingError.EOF;
				break;
		}
		
		if(error == null)
			error = InterpretingError.INVALID_AST_NODE;
		return new DataObject().setError(new ErrorObject(error));
	}
	
	/**
	 * @return Returns true if any condition was true and if any block was executed
	 */
	private boolean interpretIfStatementNode(IfStatementNode node, final int DATA_ID) {
		List<IfStatementPartNode> ifPartNodes = node.getIfStatementPartNodes();
		if(ifPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			return false;
		}
		
		for(IfStatementPartNode ifPartNode:ifPartNodes)
			if(interpretIfStatementPartNode(ifPartNode, DATA_ID))
				return true;
		
		return false;
	}
	
	/**
	 * @return Returns true if condition was true and if block was executed
	 */
	private boolean interpretIfStatementPartNode(IfStatementPartNode node, final int DATA_ID) {
		try {
			switch(node.getNodeType()) {
				case IF_STATEMENT_PART_IF:
					if(!interpretConditionNode(((IfStatementPartIfNode)node).getCondition(), DATA_ID).getBoolean())
						return false;
				case IF_STATEMENT_PART_ELSE:
					interpretAST(node.getIfBody(), DATA_ID);
					return true;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return false;
	}
	
	private DataObject interpretConditionNode(ConditionNode node, final int DATA_ID) {
		boolean conditionOuput = false;
		DataObject leftSideOperand = interpretNode(node.getLeftSideOperand(), DATA_ID);
		DataObject rightSideOperand = node.getOperator().isUnary()?null:interpretNode(node.getRightSideOperand(), DATA_ID);
		if(leftSideOperand == null || (!node.getOperator().isUnary() && rightSideOperand == null))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		switch(node.getOperator()) {
			//Unary (Logical operators)
			case NON:
			case NOT:
				conditionOuput = leftSideOperand.getBoolean();
				
				if(node.getOperator() == ConditionNode.Operator.NOT)
					conditionOuput = !conditionOuput;
				break;
			
			//Binary (Logical operators)
			case AND:
				conditionOuput = leftSideOperand.getBoolean() && rightSideOperand.getBoolean();
				break;
			case OR:
				conditionOuput = leftSideOperand.getBoolean() || rightSideOperand.getBoolean();
				break;
			
			//Binary (Comparison operators)
			case EQUALS:
			case NOT_EQUALS:
				conditionOuput = leftSideOperand.isEquals(rightSideOperand);
				
				if(node.getOperator() == ConditionNode.Operator.NOT_EQUALS)
					conditionOuput = !conditionOuput;
				break;
			case LESS_THAN:
				conditionOuput = leftSideOperand.isLessThan(rightSideOperand);
				break;
			case GREATER_THAN:
				conditionOuput = leftSideOperand.isGreaterThan(rightSideOperand);
				break;
			case LESS_THAN_OR_EQUALS:
				conditionOuput = leftSideOperand.isLessThanOrEquals(rightSideOperand);
				break;
			case GREATER_THAN_OR_EQUALS:
				conditionOuput = leftSideOperand.isGreaterThanOrEquals(rightSideOperand);
				break;
		}
		
		return new DataObject().setBoolean(conditionOuput);
	}
	
	private void interpretReturnNode(ReturnNode node, final int DATA_ID) {
		Node returnValueNode = node.getReturnValue();
		
		returnedValue = returnValueNode == null?new DataObject().setVoid():interpretNode(returnValueNode, DATA_ID);
		stopParsingFlag = true;
	}
	
	private void interpretLangDataAndCompilerFlags(String langDataCompilerFlag, DataObject value) {
		switch(langDataCompilerFlag) {
			//Data
			case "lang.version":
				String langVer = value.getText();
				if(!langVer.equals(VERSION)) {
					if(term == null) {
						if(VERSION.compareTo(langVer) > 0)
							System.err.println("Lang file's version is older than this version! Maybe the lang file won't be compile right!");
						else
							System.err.println("Lang file's version is newer than this version! Maybe the lang file won't be compile right!");
					}else {
						if(VERSION.compareTo(langVer) > 0)
							term.logln(Level.WARNING, "Lang file's version is older than this version! Maybe the lang file won't be compile right!", LangInterpreter.class);
						else
							term.logln(Level.ERROR, "Lang file's version is newer than this version! Maybe the lang file won't be compile right!", LangInterpreter.class);
					}
				}
				break;
			
			case "lang.name":
				//Nothing do to
				break;
			
			//Flags
			case "lang.allowTermRedirect":
				Number number = value.getNumber();
				if(number == null) {
					if(term == null)
						System.err.println("Invalid Data Type for lang.allowTermRedirect flag!");
					else
						term.logln(Level.ERROR, "Invalid Data Type for lang.allowTermRedirect flag!", LangInterpreter.class);
					
					return;
				}
				allowTermRedirect = number.intValue() != 0;
				break;
		}
	}
	private DataObject interpretAssignmentNode(AssignmentNode node, final int DATA_ID) {
		DataObject rvalue = interpretNode(node.getRvalue(), DATA_ID);
		
		Node lvalueNode = node.getLvalue();
		try {
			switch(lvalueNode.getNodeType()) {
				//Variable assignment
				case UNPROCESSED_VARIABLE_NAME:
					UnprocessedVariableNameNode variableNameNode = (UnprocessedVariableNameNode)lvalueNode;
					String variableName = variableNameNode.getVariableName();
					if(variableName.matches("(\\$|&|fp\\.|func\\.|linker\\.)\\w+") || variableName.matches("\\$\\[+\\w+\\]+")) {
						int indexOpeningBracket = variableName.indexOf("[");
						int indexMatchingBracket = indexOpeningBracket == -1?-1:getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
						if(indexOpeningBracket == -1 || indexMatchingBracket == variableName.length() - 1) {
							DataObject lvalue = getOrCreateDataObjectFromVariableName(variableName, false, true, DATA_ID);
							if(lvalue != null) {
								if(lvalue.getVariableName() == null || !lvalue.getVariableName().equals(variableName))
									return lvalue; //Forward error from getOrCreateDataObjectFromVariableName()
								
								if(lvalue.isFinalData() || lvalue.getVariableName().startsWith("$LANG_") || lvalue.getVariableName().startsWith("&LANG_"))
									return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
								
								lvalue.setData(rvalue);
								break;
							}
						}
					}
					//Fall through to "Lang translation" if variableName is not valid
				
				//Lang translation
				case ASSIGNMENT:
				case CHAR_VALUE:
				case CONDITION:
				case DOUBLE_VALUE:
				case ESCAPE_SEQUENCE:
				case FLOAT_VALUE:
				case FUNCTION_CALL:
				case FUNCTION_CALL_PREVIOUS_NODE_VALUE:
				case FUNCTION_DEFINITION:
				case IF_STATEMENT:
				case IF_STATEMENT_PART_ELSE:
				case IF_STATEMENT_PART_IF:
				case INT_VALUE:
				case LIST:
				case LONG_VALUE:
				case NULL_VALUE:
				case PARSING_ERROR:
				case RETURN:
				case TEXT_VALUE:
				case VARIABLE_NAME:
				case VOID_VALUE:
					String langRequest = interpretNode(lvalueNode, DATA_ID).getText();
					if(langRequest.startsWith("lang."))
						interpretLangDataAndCompilerFlags(langRequest, rvalue);
					
					data.get(DATA_ID).lang.put(langRequest, rvalue.getText());
					break;
					
				case GENERAL:
				case ARGUMENT_SEPARATOR:
					return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}catch(ClassCastException e) {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return rvalue;
	}
	
	/**
	 * Will create a variable if doesn't exist or returns an error object, or returns null if shouldCreateDataObject is set to false and variable doesn't exist
	 * @param supportsPointerReferencing If true, this node will return pointer reference as DataObject<br>
	 *                                   (e.g. $[abc] is not in variableNames, but $abc is -> $[abc] will return a DataObject)
	 */
	private DataObject getOrCreateDataObjectFromVariableName(String variableName, boolean supportsPointerReferencing,
	boolean shouldCreateDataObject, final int DATA_ID) {
		DataObject ret = data.get(DATA_ID).var.get(variableName);
		if(ret != null)
			return ret;
		
		if(supportsPointerReferencing && variableName.contains("[") && variableName.contains("]")) { //Check dereferenced variable name
			int indexOpeningBracket = variableName.indexOf("[");
			int indexMatchingBracket = getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
			if(indexMatchingBracket != variableName.length() - 1)
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			String dereferencedVariableName = variableName.substring(0, indexOpeningBracket) + variableName.substring(indexOpeningBracket + 1, indexMatchingBracket);
			DataObject dereferencedVariable = getOrCreateDataObjectFromVariableName(dereferencedVariableName, supportsPointerReferencing, false, DATA_ID);
			if(dereferencedVariable != null)
				return new DataObject().setVarPointer(new VarPointerObject(dereferencedVariable));
			
			//VarPointer redirection (e.g.: create "$[...]" as variable) -> at method end
		}
		
		if(!shouldCreateDataObject)
			return null;
		
		//Variable creation if possible
		if(variableName.matches("(\\$|&)LANG_.*"))
			return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
		
		DataObject dataObject = new DataObject().setVariableName(variableName);
		data.get(DATA_ID).var.put(variableName, dataObject);
		return dataObject;
	}
	/**
	 * Will create a variable if doesn't exist or returns an error object
	 */
	private DataObject interpretVariableNameNode(VariableNameNode node, final int DATA_ID) {
		String variableName = node.getVariableName();
		
		if(!variableName.matches("(\\$|&|fp\\.|func\\.|linker\\.)\\w+") && !variableName.matches("\\$\\[+\\w+\\]+"))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp."))
			return getOrCreateDataObjectFromVariableName(variableName, variableName.startsWith("$"), true, DATA_ID);
		
		final boolean isLinkerFunction;
		if(variableName.startsWith("func.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(5);
		}else if(variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(7);
		}else {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		final String variableNameCopy = variableName;
		Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).filter(entry -> {
			return variableNameCopy.equals(entry.getKey());
		}).findFirst();
		
		if(!ret.isPresent())
			return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, DATA_ID);
		
		return new DataObject().setFunctionPointer(new FunctionPointerObject(ret.get().getValue())).setVariableName(node.getVariableName());
	}
	
	/**
	 * @return Will return null for ("\!" escape sequence)
	 */
	private DataObject interpretEscapeSequenceNode(EscapeSequenceNode node, final int DATA_ID) {
		switch(node.getEscapeSequenceChar()) {
			case '0':
				return new DataObject().setChar('\0');
			case 'n':
				return new DataObject().setChar('\n');
			case 'r':
				return new DataObject().setChar('\r');
			case 'f':
				return new DataObject().setChar('\f');
			case 's':
				return new DataObject().setChar(' ');
			case 'b':
				return new DataObject().setChar('\b');
			case 't':
				return new DataObject().setChar('\t');
			case '$':
				return new DataObject().setChar('$');
			case '&':
				return new DataObject().setChar('&');
			case '#':
				return new DataObject().setChar('#');
			case ',':
				return new DataObject().setChar(',');
			case '(':
				return new DataObject().setChar('(');
			case ')':
				return new DataObject().setChar(')');
			case '{':
				return new DataObject().setChar('{');
			case '}':
				return new DataObject().setChar('}');
			case '!':
				return null;
			case '\\':
				return new DataObject().setChar('\\');
			
			//If no escape sequence: Remove "\" anyway
			default:
				return new DataObject().setChar(node.getEscapeSequenceChar());
		}
	}
	
	private DataObject interpretArgumentSeparatotNode(ArgumentSeparatorNode node, final int DATA_ID) {
		return new DataObject().setArgumentSeparator(node.getOriginalText());
	}
	
	private DataObject getAndResetReturnValue() {
		DataObject retTmp = returnedValue;
		returnedValue = null;
		stopParsingFlag = false;
		return retTmp;
	}
	private void executeAndClearCopyAfterFP(final int DATA_ID_TO, final int DATA_ID_FROM) {
		//Add copyValue after call
		copyAfterFP.get(DATA_ID_FROM).forEach((to, from) -> {
			if(from != null && to != null) {
				DataObject valFrom = data.get(DATA_ID_FROM).var.get(from);
				if(valFrom != null && valFrom.getType() != DataType.NULL) {
					if(to.startsWith("fp.") || to.startsWith("$") || to.startsWith("&")) {
						DataObject dataTo = data.get(DATA_ID_TO).var.get(to);
						 //$LANG and final vars can't be change
						if(to.startsWith("$LANG_") || to.startsWith("&LANG_") || (dataTo != null && dataTo.isFinalData())) {
							setErrno(InterpretingError.FINAL_VAR_CHANGE, DATA_ID_TO);
							return;
						}
						
						if(valFrom.getType() != DataType.NULL &&
						((to.startsWith("&") && valFrom.getType() != DataType.ARRAY) ||
						(to.startsWith("fp.") && valFrom.getType() != DataType.FUNCTION_POINTER))) {
							setErrno(InterpretingError.INVALID_ARR_PTR, DATA_ID_TO);
							return;
						}
						
						data.get(DATA_ID_TO).var.put(to, valFrom);
						return;
					}
				}
			}
			
			setErrno(InterpretingError.INVALID_ARGUMENTS, DATA_ID_TO);
		});
		
		//Clear copyAfterFP
		copyAfterFP.remove(DATA_ID_FROM);
	}
	private DataObject getNextArgumentAndRemoveUsedDataObjects(List<DataObject> argumentList, boolean removeArumentSpearator) {
		List<DataObject> argumentTmpList = new LinkedList<>();
		while(argumentList.size() > 0 && argumentList.get(0).getType() != DataType.ARGUMENT_SEPARATOR)
			argumentTmpList.add(argumentList.remove(0));
		
		if(argumentTmpList.isEmpty())
			argumentTmpList.add(new DataObject().setVoid());
		
		if(removeArumentSpearator && argumentList.size() > 0)
			argumentList.remove(0); //Remove ARGUMENT_SEPARATOR
		
		return combineDataObjects(argumentTmpList);
	}
	private DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int DATA_ID) {
		switch(fp.getFunctionPointerType()) {
			case FunctionPointerObject.NORMAL:
				List<VariableNameNode> parameterList = fp.getParameterList();
				LangParser.AbstractSyntaxTree functionBody = fp.getFunctionBody();
				
				final int NEW_DATA_ID = DATA_ID + 1;
				
				//Add variables and local variables
				createDataMap(NEW_DATA_ID);
				//Copies must not be final
				data.get(DATA_ID).var.forEach((key, val) -> {
					if(!key.startsWith("$LANG_") && !key.startsWith("&LANG_"))
						data.get(NEW_DATA_ID).var.put(key, new DataObject(val).setVariableName(val.getVariableName()));
				});
				//Initialize copyAfterFP
				copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
				
				//Set arguments
				DataObject lastDataObject = null;
				Iterator<VariableNameNode> parameterListIterator = parameterList.iterator();
				while(parameterListIterator.hasNext()) {
					VariableNameNode parameter = parameterListIterator.next();
					String variableName = parameter.getVariableName();
					if(!parameterListIterator.hasNext() && !variableName.matches("(\\$|&)LANG_.*") &&
					variableName.matches("(\\$|&)\\w+\\.\\.\\.")) {
						//Varargs (only the last parameter can be a varargs parameter)
						variableName = variableName.substring(0, variableName.length() - 3); //Remove "..."
						if(variableName.startsWith("$")) {
							//Text varargs
							DataObject dataObject = combineDataObjects(argumentValueList);
							data.get(NEW_DATA_ID).var.put(variableName, (dataObject != null?new DataObject(dataObject):
							new DataObject().setVoid()).setVariableName(variableName));
						}else {
							//Array varargs
							List<DataObject> varArgsTmpList = new LinkedList<>();
							while(argumentValueList.size() > 0)
								varArgsTmpList.add(getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true));
							
							data.get(NEW_DATA_ID).var.put(variableName, new DataObject().setArray(varArgsTmpList.
							toArray(new DataObject[0])).setVariableName(variableName));
						}
						
						break;
					}
					
					if(!variableName.matches("(\\$|&|fp\\.)\\w+") || variableName.matches("(\\$|&)LANG_.*")) {
						setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
						
						continue;
					}
					
					if(argumentValueList.size() > 0)
						lastDataObject = getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true);
					else if(lastDataObject == null)
						lastDataObject = new DataObject().setVoid();
					data.get(NEW_DATA_ID).var.put(variableName, new DataObject(lastDataObject).setVariableName(variableName));
				}
				
				//Call function
				interpretAST(functionBody, NEW_DATA_ID);
				
				//Add lang after call
				data.get(DATA_ID).lang.putAll(data.get(NEW_DATA_ID).lang);
				
				executeAndClearCopyAfterFP(DATA_ID, NEW_DATA_ID);
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				DataObject retTmp = getAndResetReturnValue();
				return retTmp == null?new DataObject().setVoid():retTmp;
			
			case FunctionPointerObject.PREDEFINED:
				LangPredefinedFunctionObject function = fp.getPredefinedFunction();
				if(function.isDeprecated()) {
					if(term == null)
						System.err.printf("Use of deprecated function \"%s\", this function won't be supported in \"%s\"!\n%s", functionName, function.
						getDeprecatedRemoveVersion() == null?"the future":function.getDeprecatedRemoveVersion(), (function.
						getDeprecatedReplacementFunction() == null?"":("Use \"" + function.getDeprecatedReplacementFunction() + "\" instead!\n")));
					else
						term.logf(Level.WARNING, "Use of deprecated function \"%s\", this function won't be supported in \"%s\"!\n%s", LangInterpreter.class,
						functionName, function.getDeprecatedRemoveVersion() == null?"the future":function.getDeprecatedRemoveVersion(),
						(function.getDeprecatedReplacementFunction() == null?"":("Use \"" + function.getDeprecatedReplacementFunction() + "\" instead!\n")));
				}
				DataObject ret = function.callFunc(argumentValueList, DATA_ID);
				if(function.isDeprecated())
					setErrno(InterpretingError.DEPRECATED_FUNC_CALL, DATA_ID);
				return ret == null?new DataObject().setVoid():ret;
			
			case FunctionPointerObject.EXTERNAL:
				ret = fp.getExternalFunction().callFunc(argumentValueList, DATA_ID);
				return ret == null?new DataObject().setVoid():ret;
			
			default:
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
		}
	}
	private DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList, final int DATA_ID) {
		List<DataObject> argumentValueList = new LinkedList<>();
		DataObject previousDataObject = null;
		for(Node argument:argumentList) {
			if(argument.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
				try {
					Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)argument, previousDataObject, DATA_ID);
					if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
						argumentValueList.remove(argumentValueList.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
						argumentValueList.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, DATA_ID));
					}else {
						argumentValueList.add(interpretNode(ret, DATA_ID));
					}
				}catch(ClassCastException e) {
					argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID));
				}
				
				previousDataObject = argumentValueList.get(argumentValueList.size() - 1);
				
				continue;
			}
			
			DataObject argumentValue = interpretNode(argument, DATA_ID);
			if(argumentValue == null) {
				previousDataObject = null;
				
				continue;
			}
			
			argumentValueList.add(argumentValue);
			previousDataObject = argumentValue;
		}
		
		return callFunctionPointer(fp, functionName, argumentValueList, DATA_ID);
	}
	/**
	 * @return Will return void data for non return value functions
	 */
	private DataObject interpretFunctionCallNode(FunctionCallNode node, final int DATA_ID) {
		String functionName = node.getFunctionName();
		FunctionPointerObject fp;
		if(functionName.matches("(func\\.|linker\\.)\\w+")) {
			final boolean isLinkerFunction;
			if(functionName.startsWith("func.")) {
				isLinkerFunction = false;
				
				functionName = functionName.substring(5);
			}else if(functionName.startsWith("linker.")) {
				isLinkerFunction = true;
				
				functionName = functionName.substring(7);
			}else {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
			
			final String functionNameCopy = functionName;
			Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
				return entry.getValue().isLinkerFunction() == isLinkerFunction;
			}).filter(entry -> {
				return functionNameCopy.equals(entry.getKey());
			}).findFirst();
			
			if(!ret.isPresent())
				return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, DATA_ID);
			
			fp = new FunctionPointerObject(ret.get().getValue());
		}else if(functionName.matches("fp\\.\\w+")) {
			DataObject ret = data.get(DATA_ID).var.get(functionName);
			if(ret == null || ret.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
			
			fp = ret.getFunctionPointer();
		}else {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return interpretFunctionPointer(fp, functionName, node.getChildren(), DATA_ID);
	}
	
	private DataObject interpretFunctionCallPreviousNodeValueNode(FunctionCallPreviousNodeValueNode node, DataObject previousValue, final int DATA_ID) {
		if(previousValue == null || previousValue.getType() != DataType.FUNCTION_POINTER)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		return interpretFunctionPointer(previousValue.getFunctionPointer(), previousValue.getVariableName(), node.getChildren(), DATA_ID);
	}
	
	private DataObject interpretFunctionDefinitionNode(FunctionDefinitionNode node, final int DATA_ID) {
		List<VariableNameNode> parameterList = new ArrayList<>();
		List<Node> children = node.getChildren();
		Iterator<Node> childrenIterator = children.listIterator();
		while(childrenIterator.hasNext()) {
			Node child = childrenIterator.next();
			try {
				if(child.getNodeType() != NodeType.VARIABLE_NAME) {
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
					
					continue;
				}
				
				VariableNameNode parameter = (VariableNameNode)child;
				String variableName = parameter.getVariableName();
				if(!childrenIterator.hasNext() && !variableName.matches("(\\$|&)LANG_.*") &&
				variableName.matches("(\\$|&)\\w+\\.\\.\\.")) {
					//Varargs (only the last parameter can be a varargs parameter)
					parameterList.add(parameter);
					break;
				}
				
				if(!variableName.matches("(\\$|&|fp\\.)\\w+") || variableName.matches("(\\$|&)LANG_.*")) {
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
					
					continue;
				}
				parameterList.add(parameter);
			}catch(ClassCastException e) {
				setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}
		
		return new DataObject().setFunctionPointer(new FunctionPointerObject(parameterList, node.getFunctionBody()));
	}
	
	private void createDataMap(final int DATA_ID) {
		data.put(DATA_ID, new Data());
		
		resetVarsAndFuncPtrs(DATA_ID);
	}
	private void resetVarsAndFuncPtrs(final int DATA_ID) {
		data.get(DATA_ID).var.clear();
		
		//Final vars
		data.get(DATA_ID).var.put("$LANG_COMPILER_VERSION", new DataObject(VERSION, true));
		data.get(DATA_ID).var.put("$LANG_PATH", new DataObject(langPath, true));
		data.get(DATA_ID).var.put("$LANG_RAND_MAX", new DataObject().setInt(Integer.MAX_VALUE).setFinalData(true));
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Set $LANG_ERRNO
	}
	private void resetVars(final int DATA_ID) {
		String[] keys = data.get(DATA_ID).var.keySet().toArray(new String[0]);
		for(int i = data.get(DATA_ID).var.size() - 1;i > -1;i--) {
			if((keys[i].startsWith("$") && !keys[i].startsWith("$LANG_")) ||
			(keys[i].startsWith("&") && !keys[i].startsWith("&LANG_"))) {
				data.get(DATA_ID).var.remove(keys[i]);
			}
		}
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Set $LANG_ERRNO
	}
	
	private void setErrno(InterpretingError error, final int DATA_ID) {
		data.get(DATA_ID).var.computeIfAbsent("$LANG_ERRNO", key -> new DataObject());
		
		data.get(DATA_ID).var.get("$LANG_ERRNO").setInt(error.getErrorCode());
	}
	
	private DataObject setErrnoErrorObject(InterpretingError error, final int DATA_ID) {
		setErrno(error, DATA_ID);
		
		return new DataObject().setError(new ErrorObject(error));
	}
	private InterpretingError getAndClearErrnoErrorObject(final int DATA_ID) {
		int errno = data.get(DATA_ID).var.get("$LANG_ERRNO").getInt();
		
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Reset errno
		
		return InterpretingError.getErrorFromErrorCode(errno);
	}
	
	//Classes for variable data
	public static final class FunctionPointerObject {
		/**
		 * Normal function pointer
		 */
		public static final int NORMAL = 0;
		/**
		 * Pointer to a predefined function
		 */
		public static final int PREDEFINED = 1;
		/**
		 * Function which is defined in the language
		 */
		public static final int EXTERNAL = 2;
		
		private final List<VariableNameNode> parameterList;
		private final LangParser.AbstractSyntaxTree functionBody;
		private final LangPredefinedFunctionObject predefinedFunction;
		private final LangExternalFunctionObject externalFunction;
		private final int functionPointerType;
		
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(List<VariableNameNode> parameterList, LangParser.AbstractSyntaxTree functionBody) {
			this.parameterList = new ArrayList<>(parameterList);
			this.functionBody = functionBody;
			this.predefinedFunction = null;
			this.externalFunction = null;
			this.functionPointerType = NORMAL;
		}
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(LangPredefinedFunctionObject predefinedFunction) {
			this.parameterList = null;
			this.functionBody = null;
			this.predefinedFunction = predefinedFunction;
			this.externalFunction = null;
			this.functionPointerType = PREDEFINED;
		}
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(LangExternalFunctionObject externalFunction) {
			this.parameterList = null;
			this.functionBody = null;
			this.predefinedFunction = null;
			this.externalFunction = externalFunction;
			this.functionPointerType = PREDEFINED;
		}
		
		public List<VariableNameNode> getParameterList() {
			return parameterList;
		}
		
		public LangParser.AbstractSyntaxTree getFunctionBody() {
			return functionBody;
		}
		
		public LangPredefinedFunctionObject getPredefinedFunction() {
			return predefinedFunction;
		}
		
		public LangExternalFunctionObject getExternalFunction() {
			return externalFunction;
		}
		
		public int getFunctionPointerType() {
			return functionPointerType;
		}
		
		@Override
		public String toString() {
			switch(functionPointerType) {
				case NORMAL:
					return "<Normal FP>";
				case PREDEFINED:
					return "<Predefined Function>";
				case EXTERNAL:
					return "<External Function>";
				default:
					return "Error";
			}
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof FunctionPointerObject))
				return false;
			
			FunctionPointerObject that = (FunctionPointerObject)obj;
			return this.functionPointerType == that.functionPointerType && this.parameterList.equals(that.parameterList) &&
			this.functionBody.equals(that.functionBody) && this.predefinedFunction.equals(that.predefinedFunction) &&
			this.externalFunction.equals(externalFunction);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(functionPointerType, parameterList, functionBody, predefinedFunction, externalFunction);
		}
	}
	public static final class VarPointerObject {
		private final DataObject var;
		
		public VarPointerObject(DataObject var) {
			this.var = var;
		}
		
		public DataObject getVar() {
			return var;
		}
		
		@Override
		public String toString() {
			return "VP -> {" + var + "}";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof VarPointerObject))
				return false;
			
			VarPointerObject that = (VarPointerObject)obj;
			return this.var.equals(that.var);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(var);
		}
	}
	public static final class ErrorObject {
		private final InterpretingError err;
		
		public ErrorObject(InterpretingError err) {
			this.err = err;
		}
		
		public int getErrno() {
			return err.getErrorCode();
		}
		
		public String getErrmsg() {
			return err.getErrorText();
		}
		
		@Override
		public String toString() {
			return "Error";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof ErrorObject))
				return false;
			
			ErrorObject that = (ErrorObject)obj;
			return this.err.equals(that.err);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(err);
		}
	}
	public static enum DataType {
		TEXT, CHAR, INT, LONG, FLOAT, DOUBLE, ARRAY, VAR_POINTER, FUNCTION_POINTER, ERROR, NULL, VOID, ARGUMENT_SEPARATOR;
	}
	public static final class DataObject {
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
		private ErrorObject error;
		
		/**
		 * Variable name of the DataObject (null for anonymous variable)
		 */
		private String variableName;
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
		 * This method <b>ignores</b> the final state of the data object<br>
		 * This method will not change variableName nor finalData
		 */
		void setData(DataObject dataObject) {
			this.type = dataObject.type;
			this.txt = dataObject.txt;
			this.arr = dataObject.arr; //Array: copy reference only
			this.vp = dataObject.vp;
			this.fp = dataObject.fp;
			this.intValue = dataObject.intValue;
			this.longValue = dataObject.longValue;
			this.floatValue = dataObject.floatValue;
			this.doubleValue = dataObject.doubleValue;
			this.charValue = dataObject.charValue;
			this.error = dataObject.error;
		}
		
		/**
		 * This method <b>ignores</b> the final state of the data object<br>
		 * This method will not change variableName nor finalData
		 */
		private void resetValue() {
			this.type = null;
			this.txt = null;
			this.arr = null;
			this.vp = null;
			this.fp = null;
			this.intValue = 0;
			this.longValue = 0;
			this.floatValue = 0;
			this.doubleValue = 0;
			this.charValue = 0;
			this.error = null;
		}
		
		DataObject setArgumentSeparator(String txt) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.ARGUMENT_SEPARATOR;
			this.txt = txt;
			
			return this;
		}
		
		public DataObject setText(String txt) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.TEXT;
			this.txt = txt;
			
			return this;
		}
		
		public String getText() {
			switch(type) {
				case TEXT:
				case ARGUMENT_SEPARATOR:
					return txt;
				case ARRAY:
					return Arrays.toString(arr);
				case VAR_POINTER:
					if(variableName != null)
						return variableName;
					
					return vp.toString();
				case FUNCTION_POINTER:
					if(variableName != null)
						return variableName;
					
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
				case ERROR:
					return error.toString();
			}
			
			return null;
		}
		
		public DataObject setArray(DataObject[] arr) {
			if(finalData)
				return this;
			
			resetValue();
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
			
			resetValue();
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
			
			resetValue();
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
			
			resetValue();
			this.type = DataType.NULL;
			
			return this;
		}
		
		public DataObject setVoid() {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.VOID;
			
			return this;
		}
		
		public DataObject setInt(int intValue) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.INT;
			this.intValue = intValue;
			
			return this;
		}
		
		public int getInt() {
			return intValue;
		}
		
		/**
		 * Sets data to INT = 1 if boolean value is true else INT = 0
		 */
		public DataObject setBoolean(boolean booleanValue) {
			return setInt(booleanValue?1:0);
		}
		
		public boolean getBoolean() {
			switch(type) {
				case TEXT:
					return txt != null && !txt.isEmpty();
				case CHAR:
					return charValue != 0;
				case INT:
					return intValue != 0;
				case LONG:
					return longValue != 0;
				case FLOAT:
					return floatValue != 0;
				case DOUBLE:
					return doubleValue != 0;
				case ARRAY:
					return arr.length > 0;
				case VAR_POINTER:
					return vp != null;
				case FUNCTION_POINTER:
					return fp != null;
				case ERROR:
					return error != null && error.getErrno() != 0;
				
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return false;
			}
			
			return false;
		}
		
		public DataObject setLong(long longValue) {
			if(finalData)
				return this;
			
			resetValue();
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
			
			resetValue();
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
			
			resetValue();
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
			
			resetValue();
			this.type = DataType.CHAR;
			this.charValue = charValue;
			
			return this;
		}
		
		public char getChar() {
			return charValue;
		}
		
		public DataObject setError(ErrorObject error) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.ERROR;
			this.error = error;
			
			return this;
		}
		
		public ErrorObject getError() {
			return error;
		}
		
		public DataObject setVariableName(String variableName) {
			this.variableName = variableName;
			
			return this;
		}
		
		public String getVariableName() {
			return variableName;
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
		
		public Number getNumber() {
			switch(type) {
				case TEXT:
					//INT
					try {
						return Integer.parseInt(txt);
					}catch(NumberFormatException ignore) {}
					
					//LONG
					try {
						return Long.parseLong(txt);
					}catch(NumberFormatException ignore) {}
					
					//FLOAT
					try {
						return Float.parseFloat(txt);
					}catch(NumberFormatException ignore) {}
					
					//DOUBLE
					try {
						return Double.parseDouble(txt);
					}catch(NumberFormatException ignore) {}
					
					//CHAR
					if(txt.length() == 1)
						return (int)txt.charAt(0);
					
					return null;
				case CHAR:
					return (int)charValue;
				case INT:
					return intValue;
				case LONG:
					return longValue;
				case FLOAT:
					return floatValue;
				case DOUBLE:
					return doubleValue;
				case ERROR:
					return error.getErrno();
				case ARRAY:
					return arr.length;
				
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return null;
			}
			
			return null;
		}
		
		//Comparison functions for conditions
		public boolean isEquals(DataObject other) {
			if(other == null)
				return false;
			
			Number number = other.getNumber();
			switch(type) {
				case TEXT:
					if(other.type == DataType.TEXT)
						return txt.equals(other.txt);
					
					return number != null && other.isEquals(this);
				
				case CHAR:
					return number != null && charValue == number.intValue();
				
				case INT:
					return number != null && intValue == number.intValue();
				
				case LONG:
					return number != null && longValue == number.longValue();
				
				case FLOAT:
					return number != null && floatValue == number.floatValue();
				
				case DOUBLE:
					return number != null && doubleValue == number.doubleValue();
				
				case ARRAY:
					return Objects.deepEquals(arr, other.arr) || (number != null && arr.length == number.intValue());
				
				case VAR_POINTER:
					return vp.equals(other.vp);
				
				case FUNCTION_POINTER:
					return fp.equals(other.fp);
				
				case ERROR:
					switch(other.type) {
						case TEXT:
							return error.getErrmsg().equals(other.txt) || (number != null && error.getErrno() == number.intValue());
						
						case CHAR:
						case INT:
						case LONG:
						case FLOAT:
						case DOUBLE:
						case ARRAY:
							return number != null && error.getErrno() == number.intValue();
						
						case ERROR:
							return error.equals(other.error);
						
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
				
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return type == other.type;
			}
			
			return false;
		}
		public boolean isLessThan(DataObject other) {
			if(other == null)
				return false;
			
			Number number = other.getNumber();
			switch(type) {
				case TEXT:
					if(other.type == DataType.TEXT)
						return txt.compareTo(other.txt) < 0;
					
					Number thisNumber = getNumber();
					if(thisNumber == null)
						return false;
					
					switch(other.type) {
						case CHAR:
						case INT:
						case ERROR:
						case ARRAY:
							return number != null && thisNumber.intValue() < number.intValue();
						case LONG:
							return number != null && thisNumber.longValue() < number.longValue();
						case FLOAT:
							return number != null && thisNumber.floatValue() < number.floatValue();
						case DOUBLE:
							return number != null && thisNumber.doubleValue() < number.doubleValue();
							
						case TEXT:
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
				
				case CHAR:
					return number != null && charValue < number.intValue();
				
				case INT:
					return number != null && intValue < number.intValue();
				
				case LONG:
					return number != null && longValue < number.longValue();
				
				case FLOAT:
					return number != null && floatValue < number.floatValue();
				
				case DOUBLE:
					return number != null && doubleValue < number.doubleValue();
				
				case ARRAY:
					return number != null && arr.length < number.intValue();
				
				case ERROR:
					switch(other.type) {
						case TEXT:
							return number != null && error.getErrno() < number.intValue() || error.getErrmsg().compareTo(other.txt) < 0;
						
						case CHAR:
						case INT:
						case LONG:
						case FLOAT:
						case DOUBLE:
						case ARRAY:
						case ERROR:
							return number != null && error.getErrno() < number.intValue();
						
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
					
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return false;
			}
			
			return false;
		}
		public boolean isGreaterThan(DataObject other) {
			if(other == null)
				return false;
			
			Number number = other.getNumber();
			switch(type) {
				case TEXT:
					if(other.type == DataType.TEXT)
						return txt.compareTo(other.txt) > 0;
					
					Number thisNumber = getNumber();
					if(thisNumber == null)
						return false;
					
					switch(other.type) {
						case CHAR:
						case INT:
						case ERROR:
						case ARRAY:
							return number != null && thisNumber.intValue() > number.intValue();
						case LONG:
							return number != null && thisNumber.longValue() > number.longValue();
						case FLOAT:
							return number != null && thisNumber.floatValue() > number.floatValue();
						case DOUBLE:
							return number != null && thisNumber.doubleValue() > number.doubleValue();
							
						case TEXT:
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
				
				case CHAR:
					return number != null && charValue > number.intValue();
				
				case INT:
					return number != null && intValue > number.intValue();
				
				case LONG:
					return number != null && longValue > number.longValue();
				
				case FLOAT:
					return number != null && floatValue > number.floatValue();
				
				case DOUBLE:
					return number != null && doubleValue > number.doubleValue();
				
				case ARRAY:
					return number != null && arr.length > number.intValue();
				
				case ERROR:
					switch(other.type) {
						case TEXT:
							return number != null && error.getErrno() > number.intValue() || error.getErrmsg().compareTo(other.txt) > 0;
						
						case CHAR:
						case INT:
						case LONG:
						case FLOAT:
						case DOUBLE:
						case ARRAY:
						case ERROR:
							return number != null && error.getErrno() > number.intValue();
						
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
					
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return false;
			}
			
			return false;
		}
		public boolean isLessThanOrEquals(DataObject other) {
			return isLessThan(other) || isEquals(other);
		}
		public boolean isGreaterThanOrEquals(DataObject other) {
			return isGreaterThan(other) || isEquals(other);
		}
		
		@Override
		public String toString() {
			return getText();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof DataObject))
				return false;
			
			DataObject that = (DataObject)obj;
			return this.type.equals(that.type) && Objects.equals(this.txt, that.txt) && Objects.deepEquals(this.arr, that.arr) &&
			Objects.equals(this.vp, that.vp) && Objects.equals(this.fp, that.fp) && this.intValue == that.intValue &&
			this.longValue == that.longValue && this.floatValue == that.floatValue && this.doubleValue == that.doubleValue &&
			this.charValue == that.charValue && Objects.equals(this.error, that.error);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(type, txt, arr, vp, fp, intValue, longValue, floatValue, doubleValue, charValue, error);
		}
	}
	public static final class Data {
		public final Map<String, String> lang = new HashMap<>();
		public final Map<String, DataObject> var = new HashMap<>();
	}
	
	public static enum InterpretingError {
		NO_ERROR              ( 0, "No Error"),
		
		//ERRORS
		FINAL_VAR_CHANGE      ( 1, "LANG or final vars mustn't be changed"),
		TO_MANY_INNER_LINKS   ( 2, "To many inner links"),
		NO_LANG_FILE          ( 3, "No .lang-File"),
		FILE_NOT_FOUND        ( 4, "File not found"),
		INVALID_FUNC_PTR      ( 5, "FuncPtr is invalid"),
		STACK_OVERFLOW        ( 6, "Stack overflow"),
		NO_TERMINAL           ( 7, "No terminal available"),
		INVALID_ARG_COUNT     ( 8, "Invalid argument count"),
		INVALID_LOG_LEVEL     ( 9, "Invalid log level"),
		INVALID_ARR_PTR       (10, "Invalid array pointer"),
		NO_HEX_NUM            (11, "No hex num"),
		NO_CHAR               (12, "No char"),
		NO_NUM                (13, "No number"),
		DIV_BY_ZERO           (14, "Dividing by 0"),
		NEGATIVE_ARRAY_LEN    (15, "Negative array length"),
		EMPTY_ARRAY           (16, "Empty array"),
		LENGTH_NAN            (17, "Length NAN"),
		INDEX_OUT_OF_BOUNDS   (18, "Array index out of bounds"),
		ARG_COUNT_NOT_ARR_LEN (19, "Argument count is not array length"),
		INVALID_FUNC_PTR_LOOP (20, "Invalid function pointer"),
		INVALID_ARGUMENTS     (21, "Invalid arguments"),
		FUNCTION_NOT_FOUND    (22, "Function not found"),
		EOF                   (23, "End of file was reached early"),
		SYSTEM_ERROR          (24, "System Error"),
		NEGATIVE_REPEAT_COUNT (25, "Negative repeat count"),
		LANG_REQ_NOT_FOUND    (26, "Lang request doesn't exist"),
		FUNCTION_NOT_SUPPORTED(27, "Function not supported"),
		BRACKET_MISMATCH      (28, "Bracket mismatch"),
		IF_CONDITION_MISSING  (29, "If statement condition missing"),
		INVALID_AST_NODE      (30, "Invalid AST node or AST node order"),
		
		//WARNINGS
		DEPRECATED_FUNC_CALL  (-1, "A deprecated predefined function was called"),
		NO_TERMINAL_WARNING   (-2, "No terminal available");
		
		private final int errorCode;
		private final String errorText;
		
		private InterpretingError(int errorCode, String errorText) {
			this.errorCode = errorCode;
			this.errorText = errorText;
		}
		
		public int getErrorCode() {
			return errorCode;
		}
		
		public String getErrorText() {
			return errorText;
		}
		
		public static InterpretingError getErrorFromErrorCode(int errorCode) {
			for(InterpretingError error:values())
				if(error.getErrorCode() == errorCode)
					return error;
			
			return InterpretingError.NO_ERROR;
		}
	}
	
	/**
	 * Class for communication between the LangInterpreter and Java
	 */
	public static final class LangInterpreterInterface {
		private final LangInterpreter interpreter;
		
		public LangInterpreterInterface(LangInterpreter interpreter) {
			this.interpreter = interpreter;
		}
		
		public Map<Integer, Data> getData() {
			return interpreter.getData();
		}
		public Data getData(final int DATA_ID) {
			return interpreter.getData().get(DATA_ID);
		}
		
		public Map<String, String> getTranslationMap(final int DATA_ID) {
			Data data = getData(DATA_ID);
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
		
		public Map<String, DataObject> getVarMap(final int DATA_ID) {
			Data data = getData(DATA_ID);
			if(data == null)
				return null;
			
			return data.var;
		}
		public DataObject getVar(final int DATA_ID, String varName) {
			Map<String, DataObject> vars = getVarMap(DATA_ID);
			if(vars == null)
				return null;
			
			return vars.get(varName);
		}
		public void setVar(final int DATA_ID, String varName, DataObject data) {
			setVar(DATA_ID, varName, data, false);
		}
		public void setVar(final int DATA_ID, String varName, DataObject data, boolean ignoreFinal) {
			Map<String, DataObject> vars = getVarMap(DATA_ID);
			if(vars != null) {
				DataObject oldData = vars.get(varName);
				if(oldData == null)
					vars.put(varName, data);
				else if(ignoreFinal || !oldData.isFinalData())
					oldData.setData(data);
			}
		}
		public void setVar(final int DATA_ID, String varName, String text) {
			setVar(DATA_ID, varName, text, false);
		}
		public void setVar(final int DATA_ID, String varName, String text, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject(text), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, DataObject[] arr) {
			setVar(DATA_ID, varName, arr, false);
		}
		public void setVar(final int DATA_ID, String varName, DataObject[] arr, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject().setArray(arr), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, LangExternalFunctionObject function) {
			setVar(DATA_ID, varName, function, false);
		}
		public void setVar(final int DATA_ID, String varName, LangExternalFunctionObject function, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject().setFunctionPointer(new FunctionPointerObject(function)), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, InterpretingError error) {
			setVar(DATA_ID, varName, error, false);
		}
		public void setVar(final int DATA_ID, String varName, InterpretingError error, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject().setError(new ErrorObject(error)), false);
		}
		
		/**
		 * Creates an function which is accessible globally in the Compiler (= in all DATA_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]" or with "liner.[funcName]" and can't be removed nor changed by the lang file
		 */
		public void addPredefinedFunction(String funcName, LangPredefinedFunctionObject function) {
			interpreter.funcs.put(funcName, function);
		}
		public Map<String, LangPredefinedFunctionObject> getPredefinedFunctions() {
			return interpreter.funcs;
		}
		
		public void exec(final int DATA_ID, BufferedReader lines) throws IOException {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretLines(lines, DATA_ID);
		}
		public void exec(final int DATA_ID, String lines) throws IOException {
			exec(DATA_ID, new BufferedReader(new StringReader(lines)));
		}
		
		public DataObject getAndResetReturnValue() {
			return interpreter.getAndResetReturnValue();
		}
		
		public LangParser.AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
			return interpreter.parseLines(lines);
		}
		
		public void interpretAST(final int DATA_ID, LangParser.AbstractSyntaxTree ast) {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretAST(ast, DATA_ID);
		}
		public DataObject inerpretNode(final int DATA_ID, Node node) {
			return interpreter.interpretNode(node, DATA_ID);
		}
		public DataObject interpretFunctionCallNode(final int DATA_ID, FunctionCallNode node, String funcArgs) {
			return interpreter.interpretFunctionCallNode(node, DATA_ID);
		}
		public DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList, final int DATA_ID) {
			return interpreter.interpretFunctionPointer(fp, functionName, argumentList, DATA_ID);
		}
		
		public DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int DATA_ID) {
			return interpreter.callFunctionPointer(fp, functionName, argumentValueList, DATA_ID);
		}
		
		public DataObject getNextArgumentAndRemoveUsedDataObjects(List<DataObject> argumentList, boolean removeArumentSpearator) {
			return interpreter.getNextArgumentAndRemoveUsedDataObjects(argumentList, removeArumentSpearator);
		}
		public DataObject combineDataObjects(List<DataObject> dataObjects) {
			return interpreter.combineDataObjects(dataObjects);
		}
		
		public void setErrno(InterpretingError error, final int DATA_ID) {
			interpreter.setErrno(error, DATA_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, final int DATA_ID) {
			return interpreter.setErrnoErrorObject(error, DATA_ID);
		}
		public InterpretingError getAndClearErrnoErrorObject(final int DATA_ID) {
			return interpreter.getAndClearErrnoErrorObject(DATA_ID);
		}
	}
}