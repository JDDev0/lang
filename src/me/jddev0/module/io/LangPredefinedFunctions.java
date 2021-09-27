package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import me.jddev0.module.io.LangInterpreter.DataObject;
import me.jddev0.module.io.LangInterpreter.DataType;
import me.jddev0.module.io.LangInterpreter.FunctionPointerObject;
import me.jddev0.module.io.LangInterpreter.InterpretingError;
import me.jddev0.module.io.TerminalIO.Level;

/**
 * IO-Module<br>
 * Lang predefined and linker functions for the LangInterpreter
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangPredefinedFunctions {
	private final LangInterpreter interpreter;

	public LangPredefinedFunctions(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	private String getArgumentListAsString(List<DataObject> argumentList, boolean returnEmptyStringForEmptyArgumentList) {
		DataObject dataObject = LangUtils.combineDataObjects(argumentList);
		if(dataObject == null)
			return returnEmptyStringForEmptyArgumentList?"":null;
		
		return dataObject.getText();
	}
	private DataObject unaryMathOperationHelper(List<DataObject> argumentList, Function<Number, DataObject> operation, final int DATA_ID) {
		DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 1 argument
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
		
		Number number = numberObject.getNumber();
		if(number == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
		
		return operation.apply(number);
	}
	private DataObject binaryMathOperationHelper(List<DataObject> argumentList, BiFunction<Number, Number, DataObject> operation, final int DATA_ID) {
		DataObject leftNumberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		DataObject rightNumberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 2 arguments
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
		
		Number leftNumber = leftNumberObject.getNumber();
		if(leftNumber == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
		
		Number rightNumber = rightNumberObject.getNumber();
		if(rightNumber == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
		
		return operation.apply(leftNumber, rightNumber);
	}
	
	public void addPredefinedFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		addPredefinedResetFunctions(funcs);
		addPredefinedErrorFunctions(funcs);
		addPredefinedCompilerFunctions(funcs);
		addPredefinedSystemFunctions(funcs);
		addPredefinedIOFunctions(funcs);
		addPredefinedNumberFunctions(funcs);
		addPredefinedCharacterFunctions(funcs);
		addPredefinedTextFunctions(funcs);
		addPredefinedMathFunctions(funcs);
		addPredefinedFuncPtrFunctions(funcs);
		addPredefinedArrayFunctions(funcs);
	}
	private void addPredefinedResetFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("clearVar", (argumentList, DATA_ID) -> {
			DataObject pointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			if(dereferencedVarPointer.isFinalData() || variableName.startsWith("$LANG_") || variableName.startsWith("&LANG_"))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			interpreter.data.get(DATA_ID).var.remove(variableName);
			dereferencedVarPointer.setVariableName(null);
			
			return null;
		});
		funcs.put("clearAllVars", (argumentList, DATA_ID) -> {
			interpreter.resetVars(DATA_ID);
			return null;
		});
		funcs.put("clearAllArrays", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				new HashSet<>(interpreter.data.get(DATA_ID).var.entrySet()).forEach(entry -> {
					if(entry.getValue().getType() == DataType.ARRAY)
						interpreter.data.get(DATA_ID).var.remove(entry.getKey());
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
	}
	private void addPredefinedErrorFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("getErrorString", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				return new DataObject().setText(interpreter.getAndClearErrnoErrorObject(DATA_ID).getErrorText());
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
		funcs.put("getErrorText", (argumentList, DATA_ID) -> new DataObject().setText(interpreter.getAndClearErrnoErrorObject(DATA_ID).getErrorText()));
	}
	private void addPredefinedCompilerFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("isCompilerVersionNewer", (argumentList, DATA_ID) -> {
			String langVer = interpreter.data.get(DATA_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			return new DataObject().setBoolean(LangInterpreter.VERSION.compareTo(langVer) > 0);
		});
		funcs.put("isCompilerVersionOlder", (argumentList, DATA_ID) -> {
			String langVer = interpreter.data.get(DATA_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			return new DataObject().setBoolean(LangInterpreter.VERSION.compareTo(langVer) < 0);
		});
	}
	private void addPredefinedSystemFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("sleep", (argumentList, DATA_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			Number number = dataObject.getNumber();
			if(number == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			try {
				Thread.sleep(number.longValue());
			}catch(InterruptedException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
			
			return null;
		});
		funcs.put("currentTimeMillis", (argumentList, DATA_ID) -> new DataObject().setLong(System.currentTimeMillis()));
		funcs.put("repeat", (argumentList, DATA_ID) -> {
			DataObject loopFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject repeatCountObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR_LOOP, DATA_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			
			Number repeatCountNumber = repeatCountObject.getNumber();
			if(repeatCountNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			long repeatCount = repeatCountNumber.longValue();
			if(repeatCount < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_REPEAT_COUNT, DATA_ID);
			
			for(int i = 0;i < repeatCount;i++) {
				List<DataObject> loopFuncArgumentList = new ArrayList<>();
				loopFuncArgumentList.add(new DataObject().setInt(i));
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), loopFuncArgumentList, DATA_ID);
			}
			
			return null;
		});
		funcs.put("repeatWhile", (argumentList, DATA_ID) -> {
			DataObject loopFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject checkFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER || checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR_LOOP, DATA_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID).getBoolean())
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID);
			
			return null;
		});
		funcs.put("repeatUntil", (argumentList, DATA_ID) -> {
			DataObject loopFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject checkFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER || checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR_LOOP, DATA_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(!interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID).getBoolean())
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), DATA_ID);
			
			return null;
		});
		funcs.put("getLangRequest", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, int DATA_ID) {
				DataObject langRequestObject = LangUtils.combineDataObjects(argumentList);
				if(langRequestObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				
				String langValue = interpreter.data.get(DATA_ID).lang.get(langRequestObject.getText());
				if(langValue == null)
					return interpreter.setErrnoErrorObject(InterpretingError.LANG_REQ_NOT_FOUND, DATA_ID);
				
				return new DataObject(langValue);
			}
			
			public boolean isDeprecated() {
				return true;
			}
			
			@Override
			public String getDeprecatedRemoveVersion() {
				return "v1.2.0";
			}
			
			@Override
			public String getDeprecatedReplacementFunction() {
				return "func.getTranslationValue";
			}
		});
		funcs.put("getTranslationValue", (argumentList, DATA_ID) -> {
			DataObject translationKeyObject = LangUtils.combineDataObjects(argumentList);
			if(translationKeyObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String langValue = interpreter.data.get(DATA_ID).lang.get(translationKeyObject.getText());
			if(langValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LANG_REQ_NOT_FOUND, DATA_ID);
			
			return new DataObject(langValue);
		});
		funcs.put("makeFinal", (argumentList, DATA_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(dataObject.getVariableName() == null && dataObject.getType() == DataType.VAR_POINTER) {
				dataObject = dataObject.getVarPointer().getVar();
				
				if(dataObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			}
			
			if(dataObject.getVariableName() != null && dataObject.getVariableName().matches("(\\$|&)LANG_.*"))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			dataObject.setFinalData(true);
			
			return null;
		});
		funcs.put("condition", new LangPredefinedFunctionObject() {
			public DataObject callFunc(List<DataObject> argumentList, int DATA_ID) {
				DataObject condition = LangUtils.combineDataObjects(argumentList);
				if(condition == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				
				try {
					return new DataObject().setBoolean(interpreter.interpretCondition(interpreter.parser.parseCondition(condition.getText()), DATA_ID));
				}catch(IOException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
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
				return "con.condition";
			}
		});
		funcs.put("exec", (argumentList, DATA_ID) -> {
			DataObject text = LangUtils.combineDataObjects(argumentList);
			if(text == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			BufferedReader lines = new BufferedReader(new StringReader(text.getText()));
			
			final int NEW_DATA_ID = DATA_ID + 1;
			//Add variables and local variables
			interpreter.createDataMap(NEW_DATA_ID);
			//Create clean data map without coping from caller's data map
			
			//Initialize copyAfterFP
			interpreter.copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
			try {
				interpreter.interpretLines(lines, NEW_DATA_ID);
			}catch(IOException e) {
				//Remove data map
				interpreter.data.remove(NEW_DATA_ID);
				
				//Clear copyAfterFP
				interpreter.copyAfterFP.remove(NEW_DATA_ID);
				
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
			
			//Add lang after call
			interpreter.data.get(DATA_ID).lang.putAll(interpreter.data.get(NEW_DATA_ID).lang);
			
			interpreter.executeAndClearCopyAfterFP(DATA_ID, NEW_DATA_ID);
			
			//Remove data map
			interpreter.data.remove(NEW_DATA_ID);
			
			return interpreter.getAndResetReturnValue();
		});
		funcs.put("isTerminalAvailable", (argumentList, DATA_ID) -> new DataObject().setBoolean(interpreter.term != null));
	}
	private void addPredefinedIOFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("readTerminal", (argumentList, DATA_ID) -> {
			if(interpreter.term == null && !interpreter.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, DATA_ID);
			
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			String message = messageObject == null?"":messageObject.getText();
			
			if(interpreter.term == null) {
				interpreter.setErrno(InterpretingError.NO_TERMINAL_WARNING, DATA_ID);
				
				if(!message.isEmpty())
					System.out.println(message);
				System.out.print("Input: ");
				
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
					String line = reader.readLine();
					if(line == null)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
					return new DataObject(line);
				}catch(IOException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
				}
			}else {
				try {
					return new DataObject().setText(interpreter.langPlatformAPI.showInputDialog(message));
				}catch(Exception e) {
					return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, DATA_ID);
				}
			}
		});
		funcs.put("printTerminal", (argumentList, DATA_ID) -> {
			if(interpreter.term == null && !interpreter.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, DATA_ID);
			
			DataObject logLevelObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			if(messageObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			Number logLevelNumber = logLevelObject.getNumber();
			if(logLevelNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			int logLevel = logLevelNumber.intValue();
			
			Level level = null;
			for(Level lvl:Level.values()) {
				if(lvl.getLevel() == logLevel) {
					level = lvl;
					
					break;
				}
			}
			if(level == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_LOG_LEVEL, DATA_ID);
			
			if(interpreter.term == null) {
				interpreter.setErrno(InterpretingError.NO_TERMINAL_WARNING, DATA_ID);
				
				@SuppressWarnings("resource")
				PrintStream stream = logLevel > 3?System.err:System.out; //Write to standard error if the log level is WARNING or higher
				stream.printf("[%-8s]: ", level.getLevelName());
				stream.println(messageObject.getText());
				return null;
			}else {
				interpreter.term.logln(level, "[From lang file]: " + messageObject.getText(), LangInterpreter.class);
			}
			
			return null;
		});
		funcs.put("printError", (argumentList, DATA_ID) -> {
			if(interpreter.term == null && !interpreter.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, DATA_ID);
			
			InterpretingError error = interpreter.getAndClearErrnoErrorObject(DATA_ID);
			int errno = error.getErrorCode();
			Level level = null;
			if(errno > 0)
				level = Level.ERROR;
			else if(errno < 0)
				level = Level.WARNING;
			else
				level = Level.INFO;
			
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			if(interpreter.term == null) {
				@SuppressWarnings("resource")
				PrintStream stream = level.getLevel() > 3?System.err:System.out; //Write to standard error if the log level is WARNING or higher
				stream.printf("[%-8s]: ", level.getLevelName());
				stream.println(((messageObject == null || messageObject.getType() == DataType.VOID)?"":(messageObject.getText() + ": ")) + error.getErrorText());
			}else {
				interpreter.term.logln(level, "[From lang file]: " + ((messageObject == null || messageObject.getType() == DataType.VOID)?
				"":(messageObject.getText() + ": ")) + error.getErrorText(), LangInterpreter.class);
			}
			return null;
		});
		funcs.put("input", (argumentList, DATA_ID) -> {
			Number maxCount = null;
			
			if(!argumentList.isEmpty()) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(argumentList.size() > 0) //Not 0 or 1 arguments
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				maxCount = numberObject.getNumber();
				if(maxCount == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			}
			
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				if(maxCount == null) {
					String line = reader.readLine();
					if(line == null)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
					return new DataObject(line);
				}else {
					char[] buf = new char[maxCount.intValue()];
					int count = reader.read(buf);
					if(count == -1)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
					return new DataObject(new String(buf));
				}
			}catch(IOException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, DATA_ID);
			}
		});
		funcs.put("print", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			System.out.print(textObject.getText());
			return null;
		});
		funcs.put("println", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				System.out.println();
			else
				System.out.println(textObject.getText());
			return null;
		});
		funcs.put("error", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				System.err.println();
			else
				System.err.println(textObject.getText());
			return null;
		});
		funcs.put("errorln", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			System.err.println(textObject.getText());
			return null;
		});
	}
	private void addPredefinedNumberFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("hexToDez", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				DataObject hexObject = LangUtils.combineDataObjects(argumentList);
				if(hexObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				
				String hexString = hexObject.getText();
				if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
					return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
				
				try {
					return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
				}catch(NumberFormatException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
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
			DataObject hexObject = LangUtils.combineDataObjects(argumentList);
			if(hexObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String hexString = hexObject.getText();
			if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, DATA_ID);
			}
		});
		funcs.put("toIntBits", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(Float.floatToRawIntBits(number.floatValue()));
			}, DATA_ID);
		});
		funcs.put("toFloatBits", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(Float.intBitsToFloat(number.intValue()));
			}, DATA_ID);
		});
		funcs.put("toLongBits", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(Double.doubleToRawLongBits(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("toDoubleBits", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Double.longBitsToDouble(number.longValue()));
			}, DATA_ID);
		});
		funcs.put("toInt", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue());
			}, DATA_ID);
		});
		funcs.put("toLong", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue());
			}, DATA_ID);
		});
		funcs.put("toFloat", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(number.floatValue());
			}, DATA_ID);
		});
		funcs.put("toDouble", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(number.doubleValue());
			}, DATA_ID);
		});
		funcs.put("toNumber", (argumentList, DATA_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			dataObject = dataObject.convertToNumberAndCreateNewDataObject();
			if(dataObject.getType() == DataType.NULL)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);;
			
			return dataObject;
		});
		funcs.put("ttoi", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String str = textObject.getText();
			try {
				return new DataObject().setInt(Integer.parseInt(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			}
		});
		funcs.put("ttol", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String str = textObject.getText();
			try {
				return new DataObject().setLong(Long.parseLong(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			}
		});
		funcs.put("ttof", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String str = textObject.getText();
			try {
				return new DataObject().setFloat(Float.parseFloat(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			}
		});
		funcs.put("ttod", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String str = textObject.getText();
			try {
				return new DataObject().setDouble(Double.parseDouble(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			}
		});
	}
	private void addPredefinedCharacterFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("toValue", (argumentList, DATA_ID) -> {
			DataObject charObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(charObject.getType() != DataType.CHAR)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_CHAR, DATA_ID);
			
			return new DataObject().setInt(charObject.getChar());
		});
		funcs.put("toChar", (argumentList, DATA_ID) -> {
			DataObject asciiValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			Number asciiValue = asciiValueObject.getNumber();
			if(asciiValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			return new DataObject().setChar((char)asciiValue.intValue());
		});
		funcs.put("ttoc", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String str = textObject.getText();
			if(str.length() == 1)
				return new DataObject().setChar(str.charAt(0));
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
		});
	}
	private void addPredefinedTextFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("strlen", (argumentList, DATA_ID) -> new DataObject().setInt(getArgumentListAsString(argumentList, true).length()));
		funcs.put("toUpper", (argumentList, DATA_ID) -> new DataObject(getArgumentListAsString(argumentList, true).toUpperCase()));
		funcs.put("toLower", (argumentList, DATA_ID) -> new DataObject(getArgumentListAsString(argumentList, true).toLowerCase()));
		funcs.put("trim", (argumentList, DATA_ID) -> new DataObject(getArgumentListAsString(argumentList, true).trim()));
		funcs.put("replace", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject regexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			String replacement = getArgumentListAsString(argumentList, false);
			if(replacement == null) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			return new DataObject(textObject.getText().replaceAll(regexObject.getText(), replacement));
		});
		funcs.put("substring", (argumentList, DATA_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject startIndexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject endIndexObject;
			//3rd argument is optional
			if(argumentList.isEmpty())
				endIndexObject = null;
			else
				endIndexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			Number startIndex = startIndexObject.getNumber();
			if(startIndex == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			
			try {
				if(endIndexObject == null) {
					return new DataObject(textObject.getText().substring(startIndex.intValue()));
				}else {
					Number endIndex = endIndexObject.getNumber();
					if(endIndex == null)
						return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
					
					return new DataObject(textObject.getText().substring(startIndex.intValue(), endIndex.intValue()));
				}
			}catch(StringIndexOutOfBoundsException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			}
		});
		funcs.put("split", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject regexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject maxSplitCountObject;
			//4th argument is optional
			if(argumentList.isEmpty())
				maxSplitCountObject = null;
			else
				maxSplitCountObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
			if(argumentList.size() > 0) //Not 3 or 4 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String[] arrTmp;
			
			if(maxSplitCountObject == null) {
				arrTmp = textObject.getText().split(regexObject.getText());
			}else {
				Number maxSplitCount = maxSplitCountObject.getNumber();
				if(maxSplitCount == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				arrTmp = textObject.getText().split(regexObject.getText(), maxSplitCount.intValue());
			}
			
			String arrPtr;
			if(arrPointerObject.getType() == DataType.NULL || arrPointerObject.getType() == DataType.ARRAY) {
				arrPtr = null;
			}else if(arrPointerObject.getType() == DataType.TEXT) {
				arrPtr = arrPointerObject.getText();
				if(!arrPtr.matches("&\\w+"))
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			}else {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			}
			
			DataObject oldData = arrPtr == null?arrPointerObject:interpreter.data.get(DATA_ID).var.get(arrPtr);
			if((oldData != null && (oldData.isFinalData() || (oldData.getVariableName() != null && oldData.getVariableName().startsWith("&LANG_")))) ||
			(arrPtr != null && arrPtr.startsWith("&LANG_")))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			DataObject[] arr = new DataObject[arrTmp.length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(arrTmp[i]);
			
			if(arrPointerObject.getType() == DataType.NULL && arrPointerObject.getVariableName() == null) {
				return new DataObject().setArray(arr);
			}else if(oldData != null) {
				oldData.setArray(arr);
				return oldData;
			}else {
				arrPointerObject = new DataObject().setArray(arr).setVariableName(arrPtr);
				interpreter.data.get(DATA_ID).var.put(arrPtr, arrPointerObject);
				return arrPointerObject;
			}
		});
	}
	private void addPredefinedMathFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("rand", (argumentList, DATA_ID) -> new DataObject().setInt(LangInterpreter.RAN.nextInt(interpreter.data.get(DATA_ID).var.get("$LANG_RAND_MAX").getInt())));
		funcs.put("inci", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue() + 1);
			}, DATA_ID);
		});
		funcs.put("deci", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue() - 1);
			}, DATA_ID);
		});
		funcs.put("invi", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(-number.intValue());
			}, DATA_ID);
		});
		funcs.put("addi", (argumentList, DATA_ID) -> {
			int sum = 0;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
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
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.intValue();
			}
			
			return new DataObject().setInt(prod);
		});
		funcs.put("divi", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setInt(leftNumber.intValue() / rightNumber.intValue());
			}, DATA_ID);
		});
		funcs.put("modi", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
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
		funcs.put("incl", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue() + 1);
			}, DATA_ID);
		});
		funcs.put("decl", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue() - 1);
			}, DATA_ID);
		});
		funcs.put("invl", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(-number.longValue());
			}, DATA_ID);
		});
		funcs.put("addl", (argumentList, DATA_ID) -> {
			long sum = 0L;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
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
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.longValue();
			}
			
			return new DataObject().setLong(prod);
		});
		funcs.put("divl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setLong(leftNumber.longValue() / rightNumber.longValue());
			}, DATA_ID);
		});
		funcs.put("modl", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
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
		funcs.put("incf", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(number.floatValue() + 1.f);
			}, DATA_ID);
		});
		funcs.put("decf", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(number.floatValue() - 1.f);
			}, DATA_ID);
		});
		funcs.put("invf", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(-number.floatValue());
			}, DATA_ID);
		});
		funcs.put("addf", (argumentList, DATA_ID) -> {
			float sum = 0.f;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				sum += number.floatValue();
			}
			
			return new DataObject().setFloat(sum);
		});
		funcs.put("subf", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setFloat(leftNumber.floatValue() - rightNumber.floatValue());
			}, DATA_ID);
		});
		funcs.put("mulf", (argumentList, DATA_ID) -> {
			float prod = 1.f;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.floatValue();
			}
			
			return new DataObject().setFloat(prod);
		});
		funcs.put("divf", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.floatValue() == 0.f)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
				return new DataObject().setFloat(leftNumber.floatValue() / rightNumber.floatValue());
			}, DATA_ID);
		});
		funcs.put("incd", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(number.doubleValue() + 1.d);
			}, DATA_ID);
		});
		funcs.put("decd", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(number.doubleValue() - 1.d);
			}, DATA_ID);
		});
		funcs.put("invd", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(-number.doubleValue());
			}, DATA_ID);
		});
		funcs.put("addd", (argumentList, DATA_ID) -> {
			double sum = 0.d;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
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
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				Number number = numberObject.getNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
				
				prod *= number.doubleValue();
			}
			
			return new DataObject().setDouble(prod);
		});
		funcs.put("divd", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.doubleValue() == 0.d)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, DATA_ID);
				
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
		funcs.put("cbrt", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.cbrt(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("hypot", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(Math.hypot(leftNumber.doubleValue(), rightNumber.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("toRadians", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.toRadians(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("toDegrees", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.toDegrees(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("sin", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.sin(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("cos", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.cos(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("tan", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.tan(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("asin", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.asin(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("acos", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.acos(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("atan", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.atan(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("atan2", (argumentList, DATA_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(Math.atan2(leftNumber.doubleValue(), rightNumber.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("sinh", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.sinh(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("cosh", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.cosh(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("tanh", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.tanh(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("exp", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.exp(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("loge", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.log(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("log10", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.log10(number.doubleValue()));
			}, DATA_ID);
		});
		funcs.put("dtoi", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, int DATA_ID) {
				return unaryMathOperationHelper(argumentList, number -> {
					return new DataObject().setInt(number.intValue());
				}, DATA_ID);
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
				return "func.toInt";
			}
		});
		funcs.put("dtol", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, int DATA_ID) {
				return unaryMathOperationHelper(argumentList, number -> {
					return new DataObject().setLong(number.longValue());
				}, DATA_ID);
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
				return "func.toLong";
			}
		});
		funcs.put("round", (argumentList, DATA_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong((Math.signum(number.doubleValue()) < 0?-1:1)*Math.round(Math.abs(number.doubleValue())));
			}, DATA_ID);
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
	}
	private void addPredefinedFuncPtrFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("copyAfterFP", (argumentList, DATA_ID) -> {
			DataObject toPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject fromPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			String to = null;
			switch(toPointerObject.getType()) {
				case ARRAY:
				case FUNCTION_POINTER:
					to = toPointerObject.getVariableName();
					break;
				
				case VAR_POINTER:
					DataObject toVar = toPointerObject.getVarPointer().getVar();
					if(toVar == null)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
						
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			String from = null;
			switch(fromPointerObject.getType()) {
				case ARRAY:
				case FUNCTION_POINTER:
					from = fromPointerObject.getVariableName();
					break;
				
				case VAR_POINTER:
					DataObject fromVar = fromPointerObject.getVarPointer().getVar();
					if(fromVar == null)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
						
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			interpreter.copyAfterFP.get(DATA_ID).put(to, from);
			
			return null;
		});
	}
	private void addPredefinedArrayFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("arrayMake", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = null;
			DataObject lengthObject = null;
			
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) {
				arrPointerObject = dataObject;
				
				lengthObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
				
				if(argumentList.size() > 0) //Not 1 or 2 arguments
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			}else {
				lengthObject = dataObject;
			}
			
			String arrPtr;
			if(arrPointerObject == null || arrPointerObject.getType() == DataType.ARRAY) {
				arrPtr = null;
			}else if(arrPointerObject.getType() == DataType.TEXT) {
				arrPtr = arrPointerObject.getText();
				if(!arrPtr.matches("&\\w+"))
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			}else {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			}
			
			Number lenghtNumber = lengthObject.getNumber();
			if(lenghtNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LENGTH_NAN, DATA_ID);
			int length = lenghtNumber.intValue();
			
			if(length < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_ARRAY_LEN, DATA_ID);
			
			DataObject oldData = arrPtr == null?arrPointerObject:interpreter.data.get(DATA_ID).var.get(arrPtr);
			if((oldData != null && (oldData.isFinalData() || (oldData.getVariableName() != null && oldData.getVariableName().startsWith("&LANG_")))) ||
			(arrPtr != null && arrPtr.startsWith("&LANG_")))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			
			DataObject[] arr = new DataObject[length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject().setNull();
			if(oldData != null)
				oldData.setArray(arr);
			else if(arrPointerObject == null)
				return new DataObject().setArray(arr);
			else
				interpreter.data.get(DATA_ID).var.put(arrPtr, new DataObject().setArray(arr).setVariableName(arrPtr));
			
			return null;
		});
		funcs.put("arraySet", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject valueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			Number indexNumber = indexObject.getNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			int index = indexNumber.intValue();
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			else if(index >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			
			arr[index].setData(new DataObject(valueObject));
			
			return null;
		});
		funcs.put("arraySetAll", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() == 0) //Not enough arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			DataObject valueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() == 0) { //arraySetAll with one value
				for(int i = 0;i < arr.length;i++)
					arr[i] = new DataObject(valueObject);
				
				return null;
			}
			
			if(arr.length == 0) //Too many arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			arr[0] = valueObject;
			for(int i = 1;i < arr.length;i++) {
				arr[i] = new DataObject(LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true));
				
				if(argumentList.size() == 0 && i != arr.length - 1) //Not enough arguments
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			}
			
			if(argumentList.size() > 0) //Too many arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			return null;
		});
		funcs.put("arrayGet", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			Number indexNumber = indexObject.getNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, DATA_ID);
			int index = indexNumber.intValue();
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			else if(index >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, DATA_ID);
			
			return arr[index];
		});
		funcs.put("arrayGetAll", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
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
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			return new DataObject().setInt(arr.length);
		});
		funcs.put("arrayForEach", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
			
			for(DataObject ele:arr) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(ele);
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, DATA_ID);
			}
			
			return null;
		});
		funcs.put("randChoice", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(arrPointerObject.getType() == DataType.ARRAY) {
				if(argumentList.size() > 0) //Not 1 argument
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
				
				DataObject[] arr = arrPointerObject.getArray();
				if(arr == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
				
				return arr.length == 0?null:arr[LangInterpreter.RAN.nextInt(arr.length)];
			}
			
			//No array Pointer
			List<DataObject> dataObjects = new LinkedList<>();
			dataObjects.add(arrPointerObject);
			
			//Remove argument separator object
			if(argumentList.size() > 0)
				argumentList.remove(0);
			
			while(argumentList.size() > 0)
				dataObjects.add(LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true));
			
			return dataObjects.size() == 0?null:dataObjects.get(LangInterpreter.RAN.nextInt(dataObjects.size()));
		});
		funcs.put("arrayCombine", (argumentList, DATA_ID) -> {
			List<DataObject> combinedArrays = new LinkedList<>();
			
			while(argumentList.size() > 0) {
				DataObject arrayPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(arrayPointerObject.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
				
				for(DataObject ele:arrayPointerObject.getArray())
					combinedArrays.add(ele);
			}
			
			return new DataObject().setArray(combinedArrays.toArray(new DataObject[0]));
		});
		funcs.put("arrayDelete", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			for(DataObject ele:arr)
				ele.setNull();
			
			return null;
		});
		funcs.put("arrayClear", (argumentList, DATA_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			if(arrPointerObject.getVariableName() != null && arrPointerObject.getVariableName().startsWith("&LANG_"))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
			DataObject[] arr = arrPointerObject.getArray();
			if(arr == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, DATA_ID);
			
			String variableName = arrPointerObject.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
			
			interpreter.data.get(DATA_ID).var.remove(variableName);
			return null;
		});
	}
	
	private DataObject executeLinkerFunction(List<DataObject> argumentList, Consumer<Integer> function, int DATA_ID) {
		DataObject langFileNameObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(langFileNameObject.getType() != DataType.TEXT)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, DATA_ID);
		
		String langFileName = langFileNameObject.getText();
		if(!langFileName.endsWith(".lang"))
			return interpreter.setErrnoErrorObject(InterpretingError.NO_LANG_FILE, DATA_ID);
		
		String absolutePath;
		if(new File(langFileName).isAbsolute())
			absolutePath = langFileName;
		else
			absolutePath = interpreter.langPath + File.separator + langFileName;
		
		final int NEW_DATA_ID = DATA_ID + 1;
		
		String langPathTmp = absolutePath;
		langPathTmp = interpreter.langPlatformAPI.getLangPath(langPathTmp);
		
		//Change lang path for createDataMap
		String oldLangPath = interpreter.langPath;
		interpreter.langPath = langPathTmp;
		interpreter.createDataMap(NEW_DATA_ID);
		
		try(BufferedReader reader = interpreter.langPlatformAPI.getLangReader(absolutePath)) {
			interpreter.interpretLines(reader, NEW_DATA_ID);
		}catch(IOException e) {
			if(interpreter.term == null)
				e.printStackTrace();
			else
				interpreter.term.logStackTrace(e, LangInterpreter.class);
			
			interpreter.data.remove(NEW_DATA_ID);
			return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, DATA_ID);
		}
		
		function.accept(NEW_DATA_ID);
		
		//Remove data map
		interpreter.data.remove(NEW_DATA_ID);
		
		//Set lang path to old lang path
		interpreter.langPath = oldLangPath;
		
		//Get returned value from executed lang file
		return interpreter.getAndResetReturnValue();
	}
	
	public void addLinkerFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("bindLibrary", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int DATA_ID) {
				return executeLinkerFunction(argumentList, NEW_DATA_ID -> {
					//Copy all vars, arrPtrs and funcPtrs
					interpreter.data.get(NEW_DATA_ID).var.forEach((name, val) -> {
						DataObject oldData = interpreter.data.get(DATA_ID).var.get(name);
						if(!name.startsWith("$LANG_") && !name.startsWith("&LANG_") && (oldData == null || !oldData.isFinalData())) { //No LANG data vars and no final data
							interpreter.data.get(DATA_ID).var.put(name, val);
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
					interpreter.data.get(NEW_DATA_ID).lang.forEach((k, v) -> {
						if(!k.startsWith("lang.")) {
							interpreter.data.get(DATA_ID).lang.put(k, v); //Copy to "old" DATA_ID
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
}