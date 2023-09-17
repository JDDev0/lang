package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraintException;
import me.jddev0.module.lang.DataObject.ErrorObject;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.StructObject;
import me.jddev0.module.lang.LangFunction.AllowedTypes;
import me.jddev0.module.lang.LangFunction.LangParameter;
import me.jddev0.module.lang.LangFunction.LangParameter.NumberValue;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangInterpreter.StackElement;
import me.jddev0.module.lang.LangUtils.InvalidTranslationTemplateSyntaxException;
import me.jddev0.module.lang.regex.InvalidPaternSyntaxException;
import me.jddev0.module.lang.regex.LangRegEx;

import static me.jddev0.module.lang.LangFunction.*;
import static me.jddev0.module.lang.LangFunction.LangParameter.*;

/**
 * Lang-Module<br>
 * Lang predefined and linker functions for the LangInterpreter
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangPredefinedFunctions {
	//Error string formats
	private static final String TOO_MANY_ARGUMENTS_FORMAT = "Too many arguments (%s needed)";
	private static final String NOT_ENOUGH_ARGUMENTS_FORMAT = "Not enough arguments (%s needed)";
	private static final String ARGUMENT_TYPE_FORMAT = "Argument %smust be of type %s";
	
	private final LangInterpreter interpreter;
	
	public LangPredefinedFunctions(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	private DataObject throwErrorOnNullOrErrorTypeHelper(DataObject dataObject, final int SCOPE_ID) {
		if(dataObject == null)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		
		if(dataObject.getType() == DataType.ERROR)
			return interpreter.setErrnoErrorObject(dataObject.getError().getInterprettingError(), dataObject.getError().getMessage(), SCOPE_ID);
		
		return dataObject;
	}
	private DataObject throwErrorOnNullHelper(DataObject dataObject, final int SCOPE_ID) {
		if(dataObject == null)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		
		return dataObject;
	}
	
	private DataObject requireArgumentCount(List<DataObject> combinedArgumentList, int argCount, final int SCOPE_ID) {
		if(combinedArgumentList.size() < argCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, argCount), SCOPE_ID);
		if(combinedArgumentList.size() > argCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, argCount), SCOPE_ID);
		
		return null;
	}
	private DataObject requireArgumentCount(List<DataObject> combinedArgumentList, int minArgCount, int maxArgCount, final int SCOPE_ID) {
		if(combinedArgumentList.size() < minArgCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgCount + " to " + maxArgCount), SCOPE_ID);
		if(combinedArgumentList.size() > maxArgCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, minArgCount + " to " + maxArgCount), SCOPE_ID);
		
		return null;
	}
	private DataObject requireArgumentCountAndType(List<DataObject> combinedArgumentList, List<DataType> requiredArgumentTypes, final int SCOPE_ID) {
		int argCount = requiredArgumentTypes.size();
		if(combinedArgumentList.size() < argCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, argCount), SCOPE_ID);
		if(combinedArgumentList.size() > argCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, argCount), SCOPE_ID);
		
		for(int i = 0;i < argCount;i++) {
			DataType type = requiredArgumentTypes.get(i);
			
			if(combinedArgumentList.get(i).getType() != type)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", type), SCOPE_ID);
		}
		
		return null;
	}
	
	private DataObject unaryOperationHelper(List<DataObject> argumentList, Function<DataObject, DataObject> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		if(combinedArgumentList.size() > 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		DataObject dataObject = combinedArgumentList.get(0);
		return operation.apply(dataObject);
	}
	private DataObject binaryOperationHelper(List<DataObject> argumentList, BiFunction<DataObject, DataObject, DataObject> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		if(combinedArgumentList.size() > 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		DataObject leftDataObject = combinedArgumentList.get(0);
		DataObject rightDataObject = combinedArgumentList.get(1);
		return operation.apply(leftDataObject, rightDataObject);
	}
	
	private DataObject unaryFromBooleanValueInvertedOperationHelper(List<DataObject> argumentList, Function<DataObject, Boolean> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		if(combinedArgumentList.size() > 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		DataObject dataObject = combinedArgumentList.get(0);
		return new DataObject().setBoolean(!operation.apply(dataObject));
	}
	private DataObject binaryFromBooleanValueOperationHelper(List<DataObject> argumentList, BiFunction<DataObject, DataObject, Boolean> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		if(combinedArgumentList.size() > 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		DataObject leftDataObject = combinedArgumentList.get(0);
		DataObject rightDataObject = combinedArgumentList.get(1);
		return new DataObject().setBoolean(operation.apply(leftDataObject, rightDataObject));
	}
	private DataObject binaryFromBooleanValueInvertedOperationHelper(List<DataObject> argumentList, BiFunction<DataObject, DataObject, Boolean> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		if(combinedArgumentList.size() > 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		DataObject leftDataObject = combinedArgumentList.get(0);
		DataObject rightDataObject = combinedArgumentList.get(1);
		return new DataObject().setBoolean(!operation.apply(leftDataObject, rightDataObject));
	}
	
	private DataObject unaryMathOperationHelper(List<DataObject> argumentList, Function<Number, DataObject> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		if(combinedArgumentList.size() > 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		DataObject numberObject = combinedArgumentList.get(0);
		Number number = numberObject.toNumber();
		if(number == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
		
		return operation.apply(number);
	}
	private DataObject binaryMathOperationHelper(List<DataObject> argumentList, BiFunction<Number, Number, DataObject> operation, final int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		if(combinedArgumentList.size() > 2)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		DataObject leftNumberObject = combinedArgumentList.get(0);
		DataObject rightNumberObject = combinedArgumentList.get(1);
		Number leftNumber = leftNumberObject.toNumber();
		if(leftNumber == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, "Left operand is no number", SCOPE_ID);
		
		Number rightNumber = rightNumberObject.toNumber();
		if(rightNumber == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, "Right operand is no number", SCOPE_ID);
		
		return operation.apply(leftNumber, rightNumber);
	}
	
	/**
	 * Combinator function with 1 argument
	 * Argument count will not be checked
	 */
	@FunctionalInterface
	private static interface Combinator1ArgFunction extends BiFunction<List<DataObject>, Integer, DataObject> {
		DataObject callFuncFixedArgs(DataObject a, final int SCOPE_ID);
		
		@Override
		default DataObject apply(List<DataObject> args, final Integer SCOPE_ID) {
			return callFuncFixedArgs(args.get(0), SCOPE_ID);
		}
	}
	/**
	 * Combinator function with 2 arguments
	 * Argument count will not be checked
	 */
	@FunctionalInterface
	private static interface Combinator2ArgFunction extends BiFunction<List<DataObject>, Integer, DataObject> {
		DataObject callFuncFixedArgs(DataObject a, DataObject b, final int SCOPE_ID);
		
		@Override
		default DataObject apply(List<DataObject> args, final Integer SCOPE_ID) {
			return callFuncFixedArgs(args.get(0), args.get(1), SCOPE_ID);
		}
	}
	/**
	 * Combinator function with 3 arguments
	 * Argument count will not be checked
	 */
	@FunctionalInterface
	private static interface Combinator3ArgFunction extends BiFunction<List<DataObject>, Integer, DataObject> {
		DataObject callFuncFixedArgs(DataObject a, DataObject b, DataObject c, final int SCOPE_ID);
		
		@Override
		default DataObject apply(List<DataObject> args, final Integer SCOPE_ID) {
			return callFuncFixedArgs(args.get(0), args.get(1), args.get(2), SCOPE_ID);
		}
	}
	/**
	 * Combinator function with 4 arguments
	 * Argument count will not be checked
	 */
	@FunctionalInterface
	private static interface Combinator4ArgFunction extends BiFunction<List<DataObject>, Integer, DataObject> {
		DataObject callFuncFixedArgs(DataObject a, DataObject b, DataObject c, DataObject d, final int SCOPE_ID);
		
		@Override
		default DataObject apply(List<DataObject> args, final Integer SCOPE_ID) {
			return callFuncFixedArgs(args.get(0), args.get(1), args.get(2), args.get(3), SCOPE_ID);
		}
	}
	/**
	 * Combinator function with 5 arguments
	 * Argument count will not be checked
	 */
	@FunctionalInterface
	private static interface Combinator5ArgFunction extends BiFunction<List<DataObject>, Integer, DataObject> {
		DataObject callFuncFixedArgs(DataObject a, DataObject b, DataObject c, DataObject d, DataObject e, final int SCOPE_ID);
		
		@Override
		default DataObject apply(List<DataObject> args, final Integer SCOPE_ID) {
			return callFuncFixedArgs(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4), SCOPE_ID);
		}
	}
	/**
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private void putCombinatorFunctionExternalFunctionObjectHelper(Map<String, LangPredefinedFunctionObject> funcs,
			String combinatorFunctionName, int argumentCount, int[] functionPointerIndices,
			BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc) {
		funcs.put(combinatorFunctionName, combinatorFunctionExternalFunctionObjectHelper(argumentCount, functionPointerIndices,
				combinatorFunc, combinatorFunctionName));
	}
	/**
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private LangPredefinedFunctionObject combinatorFunctionExternalFunctionObjectHelper(int argumentCount, int[] functionPointerIndices,
	BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc, String combinatorFunctionName) {
		return (argumentList, SCOPE_ID) -> {
			return combinatorFunctionHelper(argumentList, argumentCount, functionPointerIndices, combinatorFunc,
					combinatorFunctionName, SCOPE_ID);
		};
	}
	/**
	 * @param argumentList separated arguments with ARGUMENT_SEPARATORs
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private DataObject combinatorFunctionHelper(List<DataObject> argumentList, int argumentCount, int[] functionPointerIndices,
			BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc, String combinatorFunctionName, final int SCOPE_ID) {
		return combinatorFunctionRecursionHelper(LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList), argumentCount,
				functionPointerIndices, combinatorFunc, combinatorFunctionName, SCOPE_ID);
	}
	/**
	 * @param outerArgs Combined arguments without ARGUMENT_SEPARATORs
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private DataObject combinatorFunctionRecursionHelper(List<DataObject> outerArgs, int argumentCount, int[] functionPointerIndices,
			BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc, String combinatorFunctionName, final int SCOPE_ID) {
		if(outerArgs.size() > argumentCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, argumentCount), SCOPE_ID);
		
		for(int i:functionPointerIndices) {
			if(outerArgs.size() > i && outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
		}
		List<DataObject> outerArgsCopy = outerArgs.stream().map(DataObject::new).collect(Collectors.toList());
		
		LangExternalFunctionObject func = (LangExternalFunctionObject)(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
			List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
			innerArgs = innerArgs.stream().map(DataObject::new).collect(Collectors.toList());
			
			List<DataObject> args = new LinkedList<>();
			args.addAll(outerArgsCopy);
			args.addAll(innerArgs);
			
			if(args.size() > argumentCount)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, argumentCount), INNER_SCOPE_ID);
			
			if(args.size() < argumentCount)
				return combinatorFunctionRecursionHelper(args, argumentCount, functionPointerIndices, combinatorFunc, combinatorFunctionName, INNER_SCOPE_ID);
			
			for(int i:functionPointerIndices) {
				if(args.size() > i && args.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
			}
			
			return combinatorFunc.apply(args, INNER_SCOPE_ID);
		};
		if(argumentCount > outerArgs.size()) {
			String funcNames = outerArgs.stream().map(dataObject -> {
				if(dataObject.getType() != DataType.FUNCTION_POINTER)
					return "<arg>";
				
				String functionName = dataObject.getFunctionPointer().getFunctionName();
				return functionName == null?dataObject.getVariableName():functionName;
			}).collect(Collectors.joining(", "));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<" + combinatorFunctionName + "-func(" + funcNames + ")>", func));
		}else {
			return func.callFunc(interpreter, new ArrayList<>(), SCOPE_ID);
		}
	}
	
	public void addPredefinedFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		//Add non-static @LangNativeFunction functions
		//TODO
		
		//Add static @LangNativeFunction functions
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedResetFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedErrorFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedLangFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedSystemFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedIOFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedNumberFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedCharacterFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedTextFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedCombinatorFunctions.class));
		funcs.putAll(LangNativeFunction.getLangFunctionsOfClass(interpreter, LangPredefinedPairStructFunctions.class));
		
		//Add non @LangNativeFunction functions
		addPredefinedConversionFunctions(funcs);
		addPredefinedOperationFunctions(funcs);
		addPredefinedMathFunctions(funcs);
		addPredefinedCombinatorFunctions(funcs);
		addPredefinedFuncPtrFunctions(funcs);
		addPredefinedByteBufferFunctions(funcs);
		addPredefinedArrayFunctions(funcs);
		addPredefinedListFunctions(funcs);
		addPredefinedStructFunctions(funcs);
		addPredefinedComplexStructFunctions(funcs);
		addPredefinedModuleFunctions(funcs);
		addPredefinedLangTestFunctions(funcs);
	}
	private void addPredefinedConversionFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("text", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			String value = dataObject.toText();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject(value), SCOPE_ID);
		});
		funcs.put("char", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Character value = dataObject.toChar();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setChar(value), SCOPE_ID);
		});
		funcs.put("int", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Integer value = dataObject.toInt();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setInt(value), SCOPE_ID);
		});
		funcs.put("long", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Long value = dataObject.toLong();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setLong(value), SCOPE_ID);
		});
		funcs.put("float", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Float value = dataObject.toFloat();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setFloat(value), SCOPE_ID);
		});
		funcs.put("double", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Double value = dataObject.toDouble();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setDouble(value), SCOPE_ID);
		});
		funcs.put("byteBuffer", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			byte[] value = dataObject.toByteBuffer();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setByteBuffer(value), SCOPE_ID);
		});
		funcs.put("array", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			DataObject[] value = dataObject.toArray();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setArray(value), SCOPE_ID);
		});
		funcs.put("list", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			List<DataObject> value = dataObject.toList();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setList(new LinkedList<>(value)), SCOPE_ID);
		});
		funcs.put("bool", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			return new DataObject().setBoolean(dataObject.toBoolean());
		});
		funcs.put("number", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			dataObject = dataObject.convertToNumberAndCreateNewDataObject();
			return throwErrorOnNullOrErrorTypeHelper(dataObject.getType() == DataType.NULL?null:dataObject, SCOPE_ID);
		});
	}
	private void addPredefinedOperationFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		//General operator functions
		funcs.put("len", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opLen(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("deepCopy", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opDeepCopy(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("concat", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opConcat(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("spaceship", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opSpaceship(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("elvis", (argumentList, SCOPE_ID) -> binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> {
			return leftSideOperand.getBoolean()?leftSideOperand:rightSideOperand;
		}, SCOPE_ID));
		funcs.put("nullCoalescing", (argumentList, SCOPE_ID) -> binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> {
			return (leftSideOperand.getType() != DataType.NULL && leftSideOperand.getType() != DataType.VOID)?leftSideOperand:rightSideOperand;
		}, SCOPE_ID));
		funcs.put("inlineIf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			return combinedArgumentList.get(combinedArgumentList.get(0).getBoolean()?1:2);
		});
		
		//Math operator functions
		funcs.put("inc", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opInc(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("dec", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opDec(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("pos", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opPos(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("inv", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opInv(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("add", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opAdd(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("sub", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opSub(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("mul", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opMul(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("pow", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opPow(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("div", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opDiv(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("truncDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opTruncDiv(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("floorDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opFloorDiv(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("ceilDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opCeilDiv(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("mod", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opMod(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("and", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opAnd(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("or", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opOr(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("xor", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opXor(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("not", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opNot(operand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("lshift", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opLshift(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("rshift", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opRshift(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("rzshift", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opRzshift(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("cast", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opCast(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("getItem", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opGetItem(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("optionalGetItem", (argumentList, SCOPE_ID) -> throwErrorOnNullHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opOptionalGetItem(leftSideOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("setItem", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject leftSideOperand = combinedArgumentList.get(0);
			DataObject middleOperand = combinedArgumentList.get(1);
			DataObject rightSideOperand = combinedArgumentList.get(2);
			
			return throwErrorOnNullHelper(interpreter.operators.
					opSetItem(leftSideOperand, middleOperand, rightSideOperand, -1, SCOPE_ID), SCOPE_ID);
		});
		
		//Condition operator functions
		funcs.put("conNot", (argumentList, SCOPE_ID) -> unaryFromBooleanValueInvertedOperationHelper(argumentList, DataObject::toBoolean, SCOPE_ID));
		funcs.put("conAnd", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, (leftSideOperand, rightSideOperand) ->
			leftSideOperand.toBoolean() && rightSideOperand.toBoolean(), SCOPE_ID));
		funcs.put("conOr", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, (leftSideOperand, rightSideOperand) ->
			leftSideOperand.toBoolean() || rightSideOperand.toBoolean(), SCOPE_ID));
		funcs.put("conEquals", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, DataObject::isEquals, SCOPE_ID));
		funcs.put("conNotEquals", (argumentList, SCOPE_ID) -> binaryFromBooleanValueInvertedOperationHelper(argumentList, DataObject::isEquals, SCOPE_ID));
		funcs.put("conStrictEquals", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, DataObject::isStrictEquals, SCOPE_ID));
		funcs.put("conStrictNotEquals", (argumentList, SCOPE_ID) -> binaryFromBooleanValueInvertedOperationHelper(argumentList, DataObject::isStrictEquals, SCOPE_ID));
		funcs.put("conLessThan", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, DataObject::isLessThan, SCOPE_ID));
		funcs.put("conGreaterThan", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, DataObject::isGreaterThan, SCOPE_ID));
		funcs.put("conLessThanOrEquals", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, DataObject::isLessThanOrEquals, SCOPE_ID));
		funcs.put("conGreaterThanOrEquals", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, DataObject::isGreaterThanOrEquals, SCOPE_ID));
	}
	private void addPredefinedMathFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("rand", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 0), SCOPE_ID);
			
			return new DataObject().setInt(interpreter.RAN.nextInt(interpreter.data.get(SCOPE_ID).var.get("$LANG_RAND_MAX").getInt()));
		});
		funcs.put("randi", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 0), SCOPE_ID);
			
			return new DataObject().setInt(interpreter.RAN.nextInt());
		});
		funcs.put("randl", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 0), SCOPE_ID);
			
			return new DataObject().setLong(interpreter.RAN.nextLong());
		});
		funcs.put("randf", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 0), SCOPE_ID);
			
			return new DataObject().setFloat(interpreter.RAN.nextFloat());
		});
		funcs.put("randd", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 0), SCOPE_ID);
			
			return new DataObject().setDouble(interpreter.RAN.nextDouble());
		});
		funcs.put("randb", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 0), SCOPE_ID);
			
			return new DataObject().setBoolean(interpreter.RAN.nextBoolean());
		});
		funcs.put("randRange", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject boundObject = combinedArgumentList.get(0);
			Number boundNumber = boundObject.toNumber();
			if(boundNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			int bound = boundNumber.intValue();
			if(bound <= 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Bound must be positive", SCOPE_ID);
			
			return new DataObject().setInt(interpreter.RAN.nextInt(bound));
		});
		funcs.put("randChoice", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() == 0)
				return null;
			
			DataObject firstArgument = combinedArgumentList.get(0);
			if(firstArgument.getType() == DataType.ARRAY) {
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of a composite type"), SCOPE_ID);
				
				DataObject[] arr = firstArgument.getArray();
				return arr.length == 0?null:arr[interpreter.RAN.nextInt(arr.length)];
			}
			
			if(firstArgument.getType() == DataType.LIST) {
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of a composite type"), SCOPE_ID);
				
				List<DataObject> list = firstArgument.getList();
				return list.size() == 0?null:list.get(interpreter.RAN.nextInt(list.size()));
			}
			
			if(firstArgument.getType() == DataType.STRUCT) {
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of a composite type"), SCOPE_ID);
				
				StructObject struct = firstArgument.getStruct();
				String[] memberNames = struct.getMemberNames();
				
				if(struct.isDefinition())
					return memberNames.length == 0?null:new DataObject(memberNames[interpreter.RAN.nextInt(memberNames.length)]);
				
				return memberNames.length == 0?null:new DataObject(struct.getMember(memberNames[interpreter.RAN.nextInt(memberNames.length)]));
			}
			
			return combinedArgumentList.size() == 0?null:combinedArgumentList.get(interpreter.RAN.nextInt(combinedArgumentList.size()));
		});
		funcs.put("setSeed", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject seedObject = combinedArgumentList.get(0);
			Number seedNumber = seedObject.toNumber();
			if(seedNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			interpreter.RAN.setSeed(seedNumber.longValue());
			
			return null;
		});
		funcs.put("inci", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue() + 1);
			}, SCOPE_ID);
		});
		funcs.put("deci", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue() - 1);
			}, SCOPE_ID);
		});
		funcs.put("invi", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(-number.intValue());
			}, SCOPE_ID);
		});
		funcs.put("addi", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			int sum = 0;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				sum += number.intValue();
			}
			
			return new DataObject().setInt(sum);
		});
		funcs.put("subi", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() - rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("muli", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			int prod = 1;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				prod *= number.intValue();
			}
			
			return new DataObject().setInt(prod);
		});
		funcs.put("divi", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, SCOPE_ID);
				
				return new DataObject().setInt(leftNumber.intValue() / rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("modi", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, SCOPE_ID);
				
				return new DataObject().setInt(leftNumber.intValue() % rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("andi", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() & rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("ori", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() | rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("xori", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() ^ rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("noti", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(~number.intValue());
			}, SCOPE_ID);
		});
		funcs.put("lshifti", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() << rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("rshifti", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() >> rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("rzshifti", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setInt(leftNumber.intValue() >>> rightNumber.intValue());
			}, SCOPE_ID);
		});
		funcs.put("incl", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue() + 1);
			}, SCOPE_ID);
		});
		funcs.put("decl", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue() - 1);
			}, SCOPE_ID);
		});
		funcs.put("invl", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(-number.longValue());
			}, SCOPE_ID);
		});
		funcs.put("addl", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			long sum = 0L;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				sum += number.longValue();
			}
			
			return new DataObject().setLong(sum);
		});
		funcs.put("subl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() - rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("mull", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			long prod = 1L;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				prod *= number.longValue();
			}
			
			return new DataObject().setLong(prod);
		});
		funcs.put("divl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, SCOPE_ID);
				
				return new DataObject().setLong(leftNumber.longValue() / rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("modl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				if(rightNumber.intValue() == 0)
					return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, SCOPE_ID);
				
				return new DataObject().setLong(leftNumber.longValue() % rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("andl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() & rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("orl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() | rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("xorl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() ^ rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("notl", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(~number.longValue());
			}, SCOPE_ID);
		});
		funcs.put("lshiftl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() << rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("rshiftl",(argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() >> rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("rzshiftl", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setLong(leftNumber.longValue() >>> rightNumber.longValue());
			}, SCOPE_ID);
		});
		funcs.put("incf", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(number.floatValue() + 1.f);
			}, SCOPE_ID);
		});
		funcs.put("decf", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(number.floatValue() - 1.f);
			}, SCOPE_ID);
		});
		funcs.put("invf", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(-number.floatValue());
			}, SCOPE_ID);
		});
		funcs.put("addf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			float sum = 0.f;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				sum += number.floatValue();
			}
			
			return new DataObject().setFloat(sum);
		});
		funcs.put("subf", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setFloat(leftNumber.floatValue() - rightNumber.floatValue());
			}, SCOPE_ID);
		});
		funcs.put("mulf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			float prod = 1.f;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				prod *= number.floatValue();
			}
			
			return new DataObject().setFloat(prod);
		});
		funcs.put("divf", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setFloat(leftNumber.floatValue() / rightNumber.floatValue());
			}, SCOPE_ID);
		});
		funcs.put("incd", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(number.doubleValue() + 1.d);
			}, SCOPE_ID);
		});
		funcs.put("decd", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(number.doubleValue() - 1.d);
			}, SCOPE_ID);
		});
		funcs.put("invd", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(-number.doubleValue());
			}, SCOPE_ID);
		});
		funcs.put("addd", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			double sum = 0.d;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				sum += number.doubleValue();
			}
			
			return new DataObject().setDouble(sum);
		});
		funcs.put("subd", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(leftNumber.doubleValue() - rightNumber.doubleValue());
			}, SCOPE_ID);
		});
		funcs.put("muld", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			double prod = 1.d;
			
			for(DataObject numberObject:combinedArgumentList) {
				Number number = numberObject.toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				prod *= number.doubleValue();
			}
			
			return new DataObject().setDouble(prod);
		});
		funcs.put("divd", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(leftNumber.doubleValue() / rightNumber.doubleValue());
			}, SCOPE_ID);
		});
		funcs.put("sqrt", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.sqrt(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("cbrt", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.cbrt(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("hypot", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(Math.hypot(leftNumber.doubleValue(), rightNumber.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("toRadians", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.toRadians(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("toDegrees", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.toDegrees(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("sin", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.sin(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("cos", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.cos(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("tan", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.tan(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("asin", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.asin(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("acos", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.acos(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("atan", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.atan(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("atan2", (argumentList, SCOPE_ID) -> {
			return binaryMathOperationHelper(argumentList, (leftNumber, rightNumber) -> {
				return new DataObject().setDouble(Math.atan2(leftNumber.doubleValue(), rightNumber.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("sinh", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.sinh(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("cosh", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.cosh(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("tanh", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.tanh(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("exp", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.exp(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("loge", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.log(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("log10", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Math.log10(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("round", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong((Math.signum(number.doubleValue()) < 0?-1:1) * Math.round(Math.abs(number.doubleValue())));
			}, SCOPE_ID);
		});
		funcs.put("ceil", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong((long)Math.ceil(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("floor", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong((long)Math.floor(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("abs", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject numberObject = combinedArgumentList.get(0);
			numberObject = numberObject.convertToNumberAndCreateNewDataObject();
			switch(numberObject.getType()) {
				case INT:
					return new DataObject().setInt(Math.abs(numberObject.getInt()));
				case LONG:
					return new DataObject().setLong(Math.abs(numberObject.getLong()));
				case FLOAT:
					return new DataObject().setFloat(Math.abs(numberObject.getFloat()));
				case DOUBLE:
					return new DataObject().setDouble(Math.abs(numberObject.getDouble()));
				
				case CHAR:
				case TEXT:
				case BYTE_BUFFER:
				case ARRAY:
				case LIST:
				case ERROR:
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case STRUCT:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
				case TYPE:
					break;
			}
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument must be a number", SCOPE_ID);
		});
		funcs.put("min", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject min = combinedArgumentList.get(0);
			for(int i = 1;i < combinedArgumentList.size();i++) {
				DataObject dataObject = combinedArgumentList.get(i);
				if(dataObject.isLessThan(min))
					min = dataObject;
			}
			
			return min;
		});
		funcs.put("max", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject max = combinedArgumentList.get(0);
			for(int i = 1;i < combinedArgumentList.size();i++) {
				DataObject dataObject = combinedArgumentList.get(i);
				if(dataObject.isGreaterThan(max))
					max = dataObject;
			}
			
			return max;
		});
	}
	private void addPredefinedCombinatorFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combU", 5, new int[] {0, 1, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					e
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combUE", 5, new int[] {0, 1, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combUX", 5, new int[] {0, 3, 4}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject dFunc = d.getFunctionPointer();
			FunctionPointerObject eFunc = e.getFunctionPointer();
			
			DataObject retD = interpreter.callFunctionPointer(dFunc, d.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			
			DataObject retE = interpreter.callFunctionPointer(eFunc, e.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retD == null?new DataObject().setVoid():retD,
							retE == null?new DataObject().setVoid():retE
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combV1", 3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combV", 3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combV3", 4, new int[] {3}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combW", 2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, b
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combW3", 2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, b, b
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combW4", 2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, b, b, b
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combWB", 3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combWX", 2, new int[] {1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, a
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX2", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX3", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							c
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX4", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							d
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX5", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX6", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX7", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX8", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, c
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combX9", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, d
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combXA", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d, c
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combXB", 4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, d
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combXC", 5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, e
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, e
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combXD", 5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							e, c
					)
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							e, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combXE", 5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							e, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							c
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combXF", 5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, e
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							c
					)
			), SCOPE_ID);
		});
		putCombinatorFunctionExternalFunctionObjectHelper(funcs, "combY", 1, new int[] {0}, (Combinator1ArgFunction)(f, SCOPE_ID) -> {
			FunctionPointerObject fFunc = f.getFunctionPointer();
			
			LangPredefinedFunctionObject anonFunc = combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(x, INNER_SCOPE_ID) -> {
				FunctionPointerObject xFunc = x.getFunctionPointer();
				
				LangExternalFunctionObject func = (LangExternalFunctionObject)(interpreter, argumentList, INNER_INNER_SCOPE_ID) -> {
					DataObject retX = interpreter.callFunctionPointer(xFunc, x.getVariableName(), Arrays.asList(
							x
					), INNER_INNER_SCOPE_ID);
					
					DataObject retF = interpreter.callFunctionPointer(fFunc, f.getVariableName(), Arrays.asList(
							retX == null?new DataObject().setVoid():retX
					), INNER_INNER_SCOPE_ID);
					if(retF == null || retF.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.FUNCTION_POINTER)
								+ "\nThe implementation of the function provided to \"func.combY\" is incorrect!", INNER_INNER_SCOPE_ID);
					FunctionPointerObject retFFunc = retF.getFunctionPointer();
					
					return interpreter.callFunctionPointer(retFFunc, retF.getVariableName(), argumentList, INNER_INNER_SCOPE_ID);
				};
				
				return new DataObject().setFunctionPointer(new FunctionPointerObject("<combY:anon:inner-func(" + xFunc + ")>", func));
			}, "combY:anon");
			
			DataObject retAnonFunc1 = anonFunc.callFunc(new LinkedList<>(), SCOPE_ID);
			FunctionPointerObject retAnonFunc1Func = retAnonFunc1.getFunctionPointer();
			
			DataObject retAnonFunc2 = anonFunc.callFunc(new LinkedList<>(), SCOPE_ID); //Will always return a DataObject of type FUNCTION_POINTER
			
			return interpreter.callFunctionPointer(retAnonFunc1Func, retAnonFunc1.getFunctionPointer().getFunctionName(), Arrays.asList(
					retAnonFunc2
			), SCOPE_ID);
		});
	}
	private void addPredefinedFuncPtrFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("argCnt0", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = new DataObject(combinedArgumentList.get(0));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<argCnt0(" + funcPointerObject.getFunctionPointer() + ")>",
			(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 0, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
		funcs.put("argCnt1", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = new DataObject(combinedArgumentList.get(0));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<argCnt1(" + funcPointerObject.getFunctionPointer() + ")>",
			(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 1, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
		funcs.put("argCnt2", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = new DataObject(combinedArgumentList.get(0));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<argCnt2(" + funcPointerObject.getFunctionPointer() + ")>",
			(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 2, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
		funcs.put("argCnt3", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = new DataObject(combinedArgumentList.get(0));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<argCnt3(" + funcPointerObject.getFunctionPointer() + ")>",
			(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 3, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
		funcs.put("argCnt4", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = new DataObject(combinedArgumentList.get(0));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<argCnt4(" + funcPointerObject.getFunctionPointer() + ")>",
			(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 4, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
		funcs.put("argCnt5", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = new DataObject(combinedArgumentList.get(0));
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject("<argCnt5(" + funcPointerObject.getFunctionPointer() + ")>",
			(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 5, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
	}
	private void addPredefinedByteBufferFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("byteBufferCreate", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject lengthObject = combinedArgumentList.get(0);
			Number lengthNumber = lengthObject.toNumber();
			if(lengthNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LENGTH_NAN, SCOPE_ID);
			int length = lengthNumber.intValue();
			
			if(length < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_ARRAY_LEN, SCOPE_ID);
			
			return new DataObject().setByteBuffer(new byte[length]);
		});
		funcs.put("byteBufferOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> elements = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			byte[] byteBuf = new byte[elements.size()];
			for(int i = 0;i < byteBuf.length;i++) {
				Number number = elements.get(i).toNumber();
				if(number == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument " + (i + 1) + " must be a number", SCOPE_ID);
				
				byteBuf[i] = number.byteValue();
			}
			
			return new DataObject().setByteBuffer(byteBuf);
		});
		funcs.put("byteBufferPartialCopy", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject byteBufferObject = combinedArgumentList.get(0);
			DataObject fromIndexObject = combinedArgumentList.get(1);
			DataObject toIndexObject = combinedArgumentList.get(2);
			
			if(byteBufferObject.getType() != DataType.BYTE_BUFFER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.BYTE_BUFFER), SCOPE_ID);
			
			Number fromIndexNumber = fromIndexObject.toNumber();
			if(fromIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int fromIndex = fromIndexNumber.intValue();
			
			Number toIndexNumber = toIndexObject.toNumber();
			if(toIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int toIndex = toIndexNumber.intValue();
			
			byte[] byteBuf = byteBufferObject.getByteBuffer();
			if(fromIndex < 0)
				fromIndex += byteBuf.length;
			
			if(fromIndex < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(fromIndex >= byteBuf.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			if(toIndex < 0)
				toIndex += byteBuf.length;
			
			if(toIndex < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(toIndex >= byteBuf.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			if(toIndex < fromIndex)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "toIndex must be greater than or equals fromIndex", SCOPE_ID);
			
			byte[] byteBufPartialCopy = new byte[toIndex - fromIndex + 1];
			System.arraycopy(byteBuf, fromIndex, byteBufPartialCopy, 0, byteBufPartialCopy.length);
			
			return new DataObject().setByteBuffer(byteBufPartialCopy);
		});
		funcs.put("byteBufferSet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject byteBufferObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(byteBufferObject.getType() != DataType.BYTE_BUFFER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.BYTE_BUFFER), SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			Number valueNumber = valueObject.toNumber();
			if(valueNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			byte value = valueNumber.byteValue();
			
			byte[] byteBuf = byteBufferObject.getByteBuffer();
			if(index < 0)
				index += byteBuf.length;
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= byteBuf.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			byteBuf[index] = value;
			
			return null;
		});
		funcs.put("byteBufferGet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject byteBufferObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			
			if(byteBufferObject.getType() != DataType.BYTE_BUFFER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.BYTE_BUFFER), SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			byte[] byteBuf = byteBufferObject.getByteBuffer();
			if(index < 0)
				index += byteBuf.length;
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= byteBuf.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setInt(byteBuf[index]);
		});
		funcs.put("byteBufferLength", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject byteBufferObject = combinedArgumentList.get(0);
			
			if(byteBufferObject.getType() != DataType.BYTE_BUFFER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.BYTE_BUFFER), SCOPE_ID);
			
			return new DataObject().setInt(byteBufferObject.getByteBuffer().length);
		});
	}
	private void addPredefinedArrayFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("arrayCreate", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject lengthObject = combinedArgumentList.get(0);
			
			Number lengthNumber = lengthObject.toNumber();
			if(lengthNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LENGTH_NAN, SCOPE_ID);
			int length = lengthNumber.intValue();
			
			if(length < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_ARRAY_LEN, SCOPE_ID);
			
			DataObject[] arr = new DataObject[length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject();
			
			return new DataObject().setArray(arr);
		});
		funcs.put("arrayOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> elements = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			elements = elements.stream().map(DataObject::new).collect(Collectors.toList());
			
			return new DataObject().setArray(elements.toArray(new DataObject[0]));
		});
		funcs.put("arrayGenerateFrom", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			DataObject countObject = combinedArgumentList.get(1);
			Number countNumber = countObject.toNumber();
			if(countNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "number"), SCOPE_ID);
			
			List<DataObject> elements = IntStream.range(0, countNumber.intValue()).mapToObj(i -> {
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						new DataObject().setInt(i)
				), SCOPE_ID));
			}).collect(Collectors.toList());
			return new DataObject().setArray(elements.toArray(new DataObject[0]));
		});
		funcs.put("arrayZip", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			int len = -1;
			List<DataObject[]> arrays = new LinkedList<>();
			for(int i = 0;i < combinedArgumentList.size();i++) {
				DataObject arg = combinedArgumentList.get(i);
				if(arg.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.ARRAY), SCOPE_ID);
				
				arrays.add(arg.getArray());
				
				int lenTest = arg.getArray().length;
				if(len == -1) {
					len = lenTest;
					
					continue;
				}
				
				if(len != lenTest)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The size of argument[" + (i + 1) + "] must be " + len, SCOPE_ID);
			}
			
			DataObject[] zippedArray = new DataObject[len];
			for(int i = 0;i < len;i++) {
				DataObject[] arr = new DataObject[combinedArgumentList.size()];
				for(int j = 0;j < arr.length;j++)
					arr[j] = arrays.get(j)[i];
				
				zippedArray[i] = new DataObject().setArray(arr);
			}
			
			return new DataObject().setArray(zippedArray);
		});
		funcs.put("arraySet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(index < 0)
				index += arr.length;
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			arr[index] = new DataObject(valueObject);
			
			return null;
		});
		funcs.put("arraySetAll", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or (len + 1)"), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.remove(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			if(arr.length == 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Array must not be empty", SCOPE_ID);
			
			if(combinedArgumentList.size() == 1) { //arraySetAll with one value
				DataObject valueObject = combinedArgumentList.get(0);
				for(int i = 0;i < arr.length;i++)
					arr[i] = new DataObject(valueObject);
				
				return null;
			}
			
			if(combinedArgumentList.size() < arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or (len + 1)"), SCOPE_ID);
			if(combinedArgumentList.size() > arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or (len + 1)"), SCOPE_ID);
			
			Iterator<DataObject> combinedArgumentIterator = combinedArgumentList.iterator();
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(combinedArgumentIterator.next());
			
			return null;
		});
		funcs.put("arrayGet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(index < 0)
				index += arr.length;
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return arr[index];
		});
		funcs.put("arrayGetAll", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			return new DataObject(Arrays.stream(arrPointerObject.getArray()).map(DataObject::getText).collect(Collectors.joining(", ")));
		});
		funcs.put("arrayRead", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "len + 1"), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.remove(0);
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			if(combinedArgumentList.size() < arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "len + 1"), SCOPE_ID);
			if(combinedArgumentList.size() > arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "len + 1"), SCOPE_ID);
			
			for(int i = 0;i < combinedArgumentList.size();i++)
				if(combinedArgumentList.get(i).getType() != DataType.VAR_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.VAR_POINTER), SCOPE_ID);
			
			for(int i = 0;i < combinedArgumentList.size();i++) {
				DataObject dereferencedPointer = combinedArgumentList.get(i).getVarPointer().getVar();
				if(!dereferencedPointer.getTypeConstraint().isTypeAllowed(arr[i].getType()))
					return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, "The dereferenced pointer (arguments [" + (i + 1) + "]) does not allow the type " +
					arr[i].getType(), SCOPE_ID);
				
				dereferencedPointer.setData(arr[i]);
			}
			
			return null;
		});
		funcs.put("arrayFill", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(valueObject);
			
			return null;
		});
		funcs.put("arrayFillFrom", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject startIndexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			Number startIndexNumber = startIndexObject.toNumber();
			if(startIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int startIndex = startIndexNumber.intValue();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(startIndex < 0)
				startIndex += arr.length;
			
			if(startIndex < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(startIndex >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			for(int i = startIndex;i < arr.length;i++)
				arr[i] = new DataObject(valueObject);
			
			return null;
		});
		funcs.put("arrayFillTo", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject endIndexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			Number endIndexNumber = endIndexObject.toNumber();
			if(endIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int endIndex = endIndexNumber.intValue();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(endIndex < 0)
				endIndex += arr.length;
			
			if(endIndex < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(endIndex >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			for(int i = 0;i <= endIndex;i++)
				arr[i] = new DataObject(valueObject);
			
			return null;
		});
		funcs.put("arrayCountOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			long count = Arrays.stream(arr).filter(ele -> ele.isStrictEquals(elementObject)).count();
			return new DataObject().setLong(count);
		});
		funcs.put("arrayIndexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++)
				if(arr[i].isStrictEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayLastIndexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = arr.length - 1;i >= 0;i--)
				if(arr[i].isStrictEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayCountLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			long count = Arrays.stream(arr).filter(ele -> ele.isEquals(elementObject)).count();
			return new DataObject().setLong(count);
		});
		funcs.put("arrayIndexLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++)
				if(arr[i].isEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayLastIndexLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = arr.length - 1;i >= 0;i--)
				if(arr[i].isEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayLength", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			return new DataObject().setInt(arrPointerObject.getArray().length);
		});
		funcs.put("arrayDistinctValuesOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			List<DataObject> distinctValues = new LinkedList<>();
			for(DataObject ele:arrPointerObject.getArray()) {
				boolean flag = true;
				for(DataObject distinctEle:distinctValues) {
					if(ele.isStrictEquals(distinctEle)) {
						flag = false;
						break;
					}
				}
				
				if(flag)
					distinctValues.add(ele);
			}
			
			return new DataObject().setArray(distinctValues.toArray(new DataObject[0]));
		});
		funcs.put("arrayDistinctValuesLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			List<DataObject> distinctValues = new LinkedList<>();
			for(DataObject ele:arrPointerObject.getArray()) {
				boolean flag = true;
				for(DataObject distinctEle:distinctValues) {
					if(ele.isEquals(distinctEle)) {
						flag = false;
						break;
					}
				}
				
				if(flag)
					distinctValues.add(ele);
			}
			
			return new DataObject().setArray(distinctValues.toArray(new DataObject[0]));
		});
		funcs.put("arraySorted", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			
			List<DataObject> elements = Arrays.stream(arr).sorted((a, b) -> {
				DataObject retObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								a, b
						)
				), SCOPE_ID);
				Number retNumber = retObject.toNumber();
				if(retNumber == null) {
					interpreter.setErrno(InterpretingError.NO_NUM, SCOPE_ID);
					
					return 0;
				}
				
				return retNumber.intValue();
			}).collect(Collectors.toList());
			return new DataObject().setList(new LinkedList<>(elements));
		});
		funcs.put("arrayFiltered", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			
			DataObject funcPointerObject = combinedArgumentList.get(1);
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			List<DataObject> elements = Arrays.stream(arr).filter(dataObject -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						dataObject
				), SCOPE_ID).getBoolean();
			}).collect(Collectors.toList());
			return new DataObject().setArray(elements.toArray(new DataObject[0]));
		});
		funcs.put("arrayFilteredCount", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			
			DataObject funcPointerObject = combinedArgumentList.get(1);
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			int count = (int)Arrays.stream(arr).filter(dataObject -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						dataObject
				), SCOPE_ID).getBoolean();
			}).count();
			return new DataObject().setInt(count);
		});
		funcs.put("arrayMap", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			for(int i = 0;i < arr.length;i++) {
				arr[i] = new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						arr[i]
				), SCOPE_ID));
			}
			
			return null;
		});
		funcs.put("arrayMapToNew", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] newArr = new DataObject[arr.length];
			for(int i = 0;i < arr.length;i++) {
				newArr[i] = new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						arr[i]
				), SCOPE_ID));
			}
			
			return new DataObject().setArray(newArr);
		});
		funcs.put("arrayMapToOne", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject currentValueObject = combinedArgumentList.size() == 3?combinedArgumentList.get(1):null;
			DataObject funcPointerObject = combinedArgumentList.get(combinedArgumentList.size() == 3?2:1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr) {
				if(currentValueObject == null) { //Set first element as currentValue if non was provided
					currentValueObject = ele;
					
					continue;
				}
				
				currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								currentValueObject,
								ele
						)
				), SCOPE_ID);
			}
			
			return currentValueObject;
		});
		funcs.put("arrayReduce", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject currentValueObject = combinedArgumentList.size() == 3?combinedArgumentList.get(1):null;
			DataObject funcPointerObject = combinedArgumentList.get(combinedArgumentList.size() == 3?2:1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr) {
				if(currentValueObject == null) { //Set first element as currentValue if non was provided
					currentValueObject = ele;
					
					continue;
				}
				
				currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								currentValueObject,
								ele
						)
				), SCOPE_ID);
			}
			
			return currentValueObject;
		});
		funcs.put("arrayReduceColumn", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject currentValueStartObject = combinedArgumentList.size() == 3?combinedArgumentList.get(1):null;
			DataObject funcPointerObject = combinedArgumentList.get(combinedArgumentList.size() == 3?2:1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arrayOfArrays = arrPointerObject.getArray();
			
			int len = -1;
			List<DataObject[]> arrays = new LinkedList<>();
			for(int i = 0;i < arrayOfArrays.length;i++) {
				DataObject arg = arrayOfArrays[i];
				if(arg.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1[" + i + "] ", DataType.ARRAY), SCOPE_ID);
				
				arrays.add(arg.getArray());
				
				int lenTest = arg.getArray().length;
				if(len == -1) {
					len = lenTest;
					
					continue;
				}
				
				if(len != lenTest)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The size of the element [" + i + "] of array must be " + len, SCOPE_ID);
			}
			
			if(arrays.size() == 0)
				return new DataObject().setArray(new DataObject[0]);
			
			DataObject[] reducedArrays = new DataObject[len];
			for(int i = 0;i < len;i++) {
				DataObject currentValueObject = currentValueStartObject == null?null:new DataObject(currentValueStartObject);
				
				for(DataObject[] arr:arrays) {
					DataObject ele = arr[i];
					
					if(currentValueObject == null) { //Set first element as currentValue if non was provided
						currentValueObject = ele;
						
						continue;
					}
					
					currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									currentValueObject,
									ele
							)
					), SCOPE_ID);
				}
				
				reducedArrays[i] = currentValueObject == null?new DataObject().setVoid():currentValueObject;
			}
			
			return new DataObject().setArray(reducedArrays);
		});
		funcs.put("arrayForEach", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			DataObject isBreakableObject = combinedArgumentList.size() > 2?combinedArgumentList.get(2):null;
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			boolean isBreakable = isBreakableObject != null && isBreakableObject.getBoolean();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(isBreakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> innerCombinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(args);
					DataObject innerError;
					if((innerError = requireArgumentCount(innerCombinedArgumentList, 0, INNER_SCOPE_ID)) != null)
						return innerError;
					
					shouldBreak[0] = true;
					
					return null;
				}));
				
				for(DataObject ele:arr) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									ele,
									breakFunc
							)
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				for(DataObject ele:arr) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
							ele
					), SCOPE_ID);
				}
			}
			
			return null;
		});
		funcs.put("arrayEnumerate", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			DataObject isBreakableObject = combinedArgumentList.size() > 2?combinedArgumentList.get(2):null;
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			boolean isBreakable = isBreakableObject != null && isBreakableObject.getBoolean();
			
			DataObject[] arr = arrPointerObject.getArray();
			
			if(isBreakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> innerCombinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(args);
					DataObject innerError;
					if((innerError = requireArgumentCount(innerCombinedArgumentList, 0, INNER_SCOPE_ID)) != null)
						return innerError;
					
					shouldBreak[0] = true;
					
					return null;
				}));
				for(int i = 0;i < arr.length;i++) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									new DataObject().setInt(i),
									arr[i],
									breakFunc
							)
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				for(int i = 0;i < arr.length;i++) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									new DataObject().setInt(i),
									arr[i]
							)
					), SCOPE_ID);
				}
			}
			
			return null;
		});
		funcs.put("arrayMatchEvery", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			return new DataObject().setBoolean(Arrays.stream(arr).allMatch(ele -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("arrayMatchAny", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			return new DataObject().setBoolean(Arrays.stream(arr).anyMatch(ele -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),Arrays.asList(
						ele
				), SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("arrayMatchNon", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			return new DataObject().setBoolean(Arrays.stream(arr).noneMatch(ele -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("arrayCombine", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArrays = new LinkedList<>();
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			for(DataObject arrayPointerObject:combinedArgumentList) {
				if(arrayPointerObject.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
				
				for(DataObject ele:arrayPointerObject.getArray())
					combinedArrays.add(ele);
			}
			
			return new DataObject().setArray(combinedArrays.toArray(new DataObject[0]));
		});
		funcs.put("arrayPermutations", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			DataObject countObject = null;
			if(combinedArgumentList.size() == 2)
				countObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			Number countNumber = arr.length;
			if(countObject != null) {
				countNumber = countObject.toNumber();
				if(countNumber == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "number"), SCOPE_ID);
			}
			int count = countNumber.intValue();
			
			if(count < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 2 must not be less than 0!", SCOPE_ID);
			
			if(count > arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 2 must not be greater than the array!", SCOPE_ID);
			
			if(arr.length == 0 || count == 0)
				return new DataObject().setArray(new DataObject[0]);
			
			List<DataObject> permutations = new LinkedList<>();
			int[] indices = new int[count];
			int currentPermutationIndex = count - 1;
			for(int i = 0;i < count;i++)
				indices[i] = i;
			
			outer:
			while(true) {
				DataObject[] permutationArr = new DataObject[count];
				for(int i = 0;i < count;i++)
					permutationArr[i] = arr[indices[i]];
				permutations.add(new DataObject().setArray(permutationArr));
				
				List<Integer> usedIndices = new LinkedList<>();
				for(int i = 0;i < currentPermutationIndex;i++)
					usedIndices.add(indices[i]);
				
				for(;currentPermutationIndex < count;currentPermutationIndex++) {
					int index = indices[currentPermutationIndex] + 1;
					while(usedIndices.contains(index))
						index++;
					
					if(index == arr.length) {
						if(!usedIndices.isEmpty())
							usedIndices.remove(usedIndices.size() - 1);
						
						indices[currentPermutationIndex] = -1;
						currentPermutationIndex -= 2; //Will be incremented in for loop
						if(currentPermutationIndex < -1)
							break outer;
						
						continue;
					}
					
					indices[currentPermutationIndex] = index;
					
					usedIndices.add(index);
				}
				currentPermutationIndex = count - 1;
			}
			
			return new DataObject().setArray(permutations.toArray(new DataObject[0]));
		});
		funcs.put("arrayPermutationsForEach", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			DataObject countObject = null;
			if(combinedArgumentList.size() == 3)
				countObject = combinedArgumentList.get(2);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			Number countNumber = arr.length;
			if(countObject != null) {
				countNumber = countObject.toNumber();
				if(countNumber == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "number"), SCOPE_ID);
			}
			int count = countNumber.intValue();
			
			if(count < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 3 must not be less than 0!", SCOPE_ID);
			
			if(count > arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 3 must not be greater than the array!", SCOPE_ID);
			
			if(arr.length == 0 || count == 0)
				return null;
			
			int[] indices = new int[count];
			int currentPermutationIndex = count - 1;
			for(int i = 0;i < count;i++)
				indices[i] = i;
			
			int permutationNumber = 0;
			
			outer:
			while(true) {
				DataObject[] permutationArr = new DataObject[count];
				for(int i = 0;i < count;i++)
					permutationArr[i] = arr[indices[i]];
				
				if(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								new DataObject().setArray(permutationArr),
								new DataObject().setInt(permutationNumber++)
						)
				), SCOPE_ID).getBoolean())
					return null;
				
				List<Integer> usedIndices = new LinkedList<>();
				for(int i = 0;i < currentPermutationIndex;i++)
					usedIndices.add(indices[i]);
				
				for(;currentPermutationIndex < count;currentPermutationIndex++) {
					int index = indices[currentPermutationIndex] + 1;
					while(usedIndices.contains(index))
						index++;
					
					if(index == arr.length) {
						if(!usedIndices.isEmpty())
							usedIndices.remove(usedIndices.size() - 1);
						
						indices[currentPermutationIndex] = -1;
						currentPermutationIndex -= 2; //Will be incremented in for loop
						if(currentPermutationIndex < -1)
							break outer;
						
						continue;
					}
					
					indices[currentPermutationIndex] = index;
					
					usedIndices.add(index);
				}
				currentPermutationIndex = count - 1;
			}
			
			return null;
		});
		funcs.put("arrayReset", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr)
				ele.setNull();
			
			return null;
		});
	}
	private void addPredefinedListFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("listOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> elements = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			elements = elements.stream().map(DataObject::new).collect(Collectors.toList());
			
			return new DataObject().setList(new LinkedList<>(elements));
		});
		funcs.put("listGenerateFrom", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			DataObject countObject = combinedArgumentList.get(1);
			Number countNumber = countObject.toNumber();
			if(countNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "number"), SCOPE_ID);
			
			List<DataObject> elements = IntStream.range(0, countNumber.intValue()).mapToObj(i -> {
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						new DataObject().setInt(i)
				), SCOPE_ID));
			}).collect(Collectors.toList());
			return new DataObject().setList(new LinkedList<>(elements));
		});
		funcs.put("listZip", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			int len = -1;
			List<Iterator<DataObject>> listIters = new LinkedList<>();
			for(int i = 0;i < combinedArgumentList.size();i++) {
				DataObject arg = combinedArgumentList.get(i);
				if(arg.getType() != DataType.LIST)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.LIST), SCOPE_ID);
				
				listIters.add(arg.getList().iterator());
				
				int lenTest = arg.getList().size();
				if(len == -1) {
					len = lenTest;
					
					continue;
				}
				
				if(len != lenTest)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The size of argument[" + (i + 1) + "] must be " + len, SCOPE_ID);
			}
			
			LinkedList<DataObject> zippedList = new LinkedList<>();
			for(int i = 0;i < len;i++) {
				DataObject[] arr = new DataObject[combinedArgumentList.size()];
				for(int j = 0;j < arr.length;j++) {
					Iterator<DataObject> iter = listIters.get(j);
					if(!iter.hasNext())
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The size of argument[" + (j + 1) + "] must be " + len, SCOPE_ID);
					
					arr[j] = iter.next();
				}
				
				zippedList.add(new DataObject().setArray(arr));
			}
			
			return new DataObject().setList(zippedList);
		});
		funcs.put("listAdd", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			listObject.getList().add(new DataObject(valueObject));
			return null;
		});
		funcs.put("listSet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			List<DataObject> list = listObject.getList();
			if(index < 0)
				index += list.size();
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= list.size())
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			listObject.getList().set(index, new DataObject(valueObject));
			
			return null;
		});
		funcs.put("listShift", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> list = listObject.getList();
			if(list.size() == 0)
				return null;
			
			return list.pollFirst();
		});
		funcs.put("listUnshift", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> list = listObject.getList();
			list.addFirst(valueObject);
			return null;
		});
		funcs.put("listPeekFirst", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> list = listObject.getList();
			if(list.size() == 0)
				return null;
			
			return list.peekFirst();
		});
		funcs.put("listPop", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> list = listObject.getList();
			if(list.size() == 0)
				return null;
			
			return list.pollLast();
		});
		funcs.put("listPush", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> list = listObject.getList();
			list.addLast(valueObject);
			return null;
		});
		funcs.put("listPeekLast", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> list = listObject.getList();
			if(list.size() == 0)
				return null;
			
			return list.peekLast();
		});
		funcs.put("listRemove", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = 0;i < list.size();i++) {
				DataObject dataObject = list.get(i);
				if(dataObject.isStrictEquals(valueObject)) {
					list.remove(i);
					
					return dataObject;
				}
			}
			
			return null;
		});
		funcs.put("listRemoveLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = 0;i < list.size();i++) {
				DataObject dataObject = list.get(i);
				if(dataObject.isEquals(valueObject)) {
					list.remove(i);
					
					return dataObject;
				}
			}
			
			return null;
		});
		funcs.put("listRemoveAt", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			List<DataObject> list = listObject.getList();
			if(index < 0)
				index += list.size();
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= list.size())
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return list.remove(index);
		});
		funcs.put("listGet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			List<DataObject> list = listObject.getList();
			if(index < 0)
				index += list.size();
			
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= list.size())
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return list.get(index);
		});
		funcs.put("listGetAll", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			return new DataObject(listObject.getList().stream().map(DataObject::getText).collect(Collectors.joining(", ")));
		});
		funcs.put("listFill", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject valueObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = 0;i < list.size();i++)
				list.set(i, new DataObject(valueObject));
			
			return null;
		});
		funcs.put("listFillFrom", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject startIndexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			Number startIndexNumber = startIndexObject.toNumber();
			if(startIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int startIndex = startIndexNumber.intValue();
			
			List<DataObject> list = listObject.getList();
			if(startIndex < 0)
				startIndex += list.size();
			
			if(startIndex < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(startIndex >= list.size())
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			for(int i = startIndex;i < list.size();i++)
				list.set(i, new DataObject(valueObject));
			
			return null;
		});
		funcs.put("listFillTo", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject endIndexObject = combinedArgumentList.get(1);
			DataObject valueObject = combinedArgumentList.get(2);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			Number endIndexNumber = endIndexObject.toNumber();
			if(endIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int endIndex = endIndexNumber.intValue();
			
			List<DataObject> list = listObject.getList();
			if(endIndex < 0)
				endIndex += list.size();
			
			if(endIndex < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(endIndex >= list.size())
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			for(int i = 0;i <= endIndex;i++)
				list.set(i, new DataObject(valueObject));
			
			return null;
		});
		funcs.put("listCountOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			long count = list.stream().filter(ele -> ele.isStrictEquals(elementObject)).count();
			return new DataObject().setLong(count);
		});
		funcs.put("listIndexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = 0;i < list.size();i++)
				if(list.get(i).isStrictEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("listLastIndexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = list.size() - 1;i >= 0;i--)
				if(list.get(i).isStrictEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("listCountLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			long count = list.stream().filter(ele -> ele.isEquals(elementObject)).count();
			return new DataObject().setLong(count);
		});
		funcs.put("listIndexLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = list.size() - 1;i >= 0;i--)
				if(list.get(i).isEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("listLastIndexLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject elementObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = list.size() - 1;i >= 0;i--)
				if(list.get(i).isEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("listLength", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			return new DataObject().setInt(listObject.getList().size());
		});
		funcs.put("listDistinctValuesOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> distinctValues = new LinkedList<>();
			for(DataObject ele:listObject.getList()) {
				boolean flag = true;
				for(DataObject distinctEle:distinctValues) {
					if(ele.isStrictEquals(distinctEle)) {
						flag = false;
						break;
					}
				}
				
				if(flag)
					distinctValues.add(ele);
			}
			
			return new DataObject().setList(distinctValues);
		});
		funcs.put("listDistinctValuesLike", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			LinkedList<DataObject> distinctValues = new LinkedList<>();
			for(DataObject ele:listObject.getList()) {
				boolean flag = true;
				for(DataObject distinctEle:distinctValues) {
					if(ele.isEquals(distinctEle)) {
						flag = false;
						break;
					}
				}
				
				if(flag)
					distinctValues.add(ele);
			}
			
			return new DataObject().setList(distinctValues);
		});
		funcs.put("listSorted", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			List<DataObject> elements = list.stream().sorted((a, b) -> {
				DataObject retObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								a, b
						)
				), SCOPE_ID);
				Number retNumber = retObject.toNumber();
				if(retNumber == null) {
					interpreter.setErrno(InterpretingError.NO_NUM, SCOPE_ID);
					
					return 0;
				}
				
				return retNumber.intValue();
			}).collect(Collectors.toList());
			return new DataObject().setList(new LinkedList<>(elements));
		});
		funcs.put("listFiltered", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			List<DataObject> elements = list.stream().filter(dataObject -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						dataObject
				), SCOPE_ID).getBoolean();
			}).collect(Collectors.toList());
			return new DataObject().setList(new LinkedList<>(elements));
		});
		funcs.put("listFilteredCount", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			int count = (int)list.stream().filter(dataObject -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						dataObject
				), SCOPE_ID).getBoolean();
			}).count();
			return new DataObject().setInt(count);
		});
		funcs.put("listMap", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(int i = 0;i < list.size();i++) {
				list.set(i, new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						list.get(i)
				), SCOPE_ID)));
			}
			
			return null;
		});
		funcs.put("listMapToNew", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			LinkedList<DataObject> newList = new LinkedList<>();
			for(int i = 0;i < list.size();i++) {
				newList.set(i, new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						list.get(i)
				), SCOPE_ID)));
			}
			
			return new DataObject().setList(newList);
		});
		funcs.put("listReduce", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject currentValueObject = combinedArgumentList.size() == 3?combinedArgumentList.get(1):null;
			DataObject funcPointerObject = combinedArgumentList.get(combinedArgumentList.size() == 3?2:1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			for(DataObject ele:list) {
				if(currentValueObject == null) { //Set first element as currentValue if non was provided
					currentValueObject = ele;
					
					continue;
				}
				
				currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								currentValueObject,
								ele
						)
				), SCOPE_ID);
			}
			
			return currentValueObject;
		});
		funcs.put("listReduceColumn", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject currentValueStartObject = combinedArgumentList.size() == 3?combinedArgumentList.get(1):null;
			DataObject funcPointerObject = combinedArgumentList.get(combinedArgumentList.size() == 3?2:1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			LinkedList<DataObject> listOfLists = listObject.getList();
			
			int len = -1;
			List<LinkedList<DataObject>> lists = new LinkedList<>();
			for(int i = 0;i < listOfLists.size();i++) {
				DataObject arg = listOfLists.get(i);
				if(arg.getType() != DataType.LIST)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1[" + i + "] ", DataType.LIST), SCOPE_ID);
				
				lists.add(arg.getList());
				
				int lenTest = arg.getList().size();
				if(len == -1) {
					len = lenTest;
					
					continue;
				}
				
				if(len != lenTest)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The size of the element [" + i + "] of list must be " + len, SCOPE_ID);
			}
			
			if(lists.size() == 0)
				return new DataObject().setList(new LinkedList<>());
			
			LinkedList<DataObject> reduceedLists = new LinkedList<>();
			for(int i = 0;i < len;i++) {
				DataObject currentValueObject = currentValueStartObject == null?null:new DataObject(currentValueStartObject);
				
				for(LinkedList<DataObject> list:lists) {
					DataObject ele = list.get(i);
					
					if(currentValueObject == null) { //Set first element as currentValue if non was provided
						currentValueObject = ele;
						
						continue;
					}
					
					currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									currentValueObject,
									ele
							)
					), SCOPE_ID);
				}
				
				reduceedLists.add(currentValueObject == null?new DataObject().setVoid():currentValueObject);
			}
			
			return new DataObject().setList(reduceedLists);
		});
		funcs.put("listForEach", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			DataObject isBreakableObject = combinedArgumentList.size() > 2?combinedArgumentList.get(2):null;
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			boolean isBreakable = isBreakableObject != null && isBreakableObject.getBoolean();
			
			List<DataObject> list = listObject.getList();
			
			if(isBreakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> innerCombinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(args);
					DataObject innerError;
					if((innerError = requireArgumentCount(innerCombinedArgumentList, 0, INNER_SCOPE_ID)) != null)
						return innerError;
					
					shouldBreak[0] = true;
					
					return null;
				}));
				
				for(int i = 0;i < list.size();i++) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									list.get(i),
									breakFunc
							)
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				for(int i = 0;i < list.size();i++) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
							list.get(i)
					), SCOPE_ID);
				}
			}
			
			return null;
		});
		funcs.put("listEnumerate", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			DataObject isBreakableObject = combinedArgumentList.size() > 2?combinedArgumentList.get(2):null;
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			boolean isBreakable = isBreakableObject != null && isBreakableObject.getBoolean();
			
			List<DataObject> list = listObject.getList();
			if(isBreakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> innerCombinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(args);
					DataObject innerError;
					if((innerError = requireArgumentCount(innerCombinedArgumentList, 0, INNER_SCOPE_ID)) != null)
						return innerError;
					
					shouldBreak[0] = true;
					
					return null;
				}));
				for(int i = 0;i < list.size();i++) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									new DataObject().setInt(i),
									list.get(i),
									breakFunc
							)
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				for(int i = 0;i < list.size();i++) {
					interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
					LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
									new DataObject().setInt(i),
									list.get(i)
							)
					), SCOPE_ID);
				}
			}
			
			return null;
		});
		funcs.put("listMatchEvery", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			return new DataObject().setBoolean(list.stream().allMatch(ele -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("listMatchAny", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			return new DataObject().setBoolean(list.stream().anyMatch(ele -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("listMatchNon", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.LIST), SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			List<DataObject> list = listObject.getList();
			return new DataObject().setBoolean(list.stream().noneMatch(ele -> {
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("listCombine", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			LinkedList<DataObject> combinedLists = new LinkedList<>();
			
			for(DataObject listObject:combinedArgumentList) {
				if(listObject.getType() != DataType.LIST)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.LIST), SCOPE_ID);
				
				combinedLists.addAll(listObject.getList());
			}
			
			return new DataObject().setList(combinedLists);
		});
		funcs.put("listClear", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject listObject = combinedArgumentList.get(0);
			
			if(listObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.LIST), SCOPE_ID);
			
			listObject.getList().clear();
			
			return null;
		});
	}
	private void addPredefinedStructFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("structCreate", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setStruct(new StructObject(struct));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject structObject = combinedArgumentList.get(0);
			
			if(structObject.getType() != DataType.STRUCT)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.STRUCT), SCOPE_ID);
			
			combinedArgumentList.remove(0);
			
			String[] memberNames = structObject.getStruct().getMemberNames();
			if(combinedArgumentList.size() != memberNames.length) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The array length is not equals to the count of member names (" + memberNames.length + ")", SCOPE_ID);
			}
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setStruct(new StructObject(struct, combinedArgumentList.toArray(new DataObject[0])));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structSet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			DataObject memberNameObject = combinedArgumentList.get(1);
			DataObject memberObject = combinedArgumentList.get(2);
			
			if(structObject.getType() != DataType.STRUCT)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.STRUCT), SCOPE_ID);
			
			if(memberNameObject.getType() != DataType.TEXT)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.TEXT), SCOPE_ID);
			
			StructObject struct = structObject.getStruct();
			
			String memberName = memberNameObject.getText();
			
			try {
				struct.setMember(memberName, memberObject);
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("structSetAll", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject structObject = combinedArgumentList.get(0);
			
			if(structObject.getType() != DataType.STRUCT)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.STRUCT), SCOPE_ID);
			
			String[] memberNames = structObject.getStruct().getMemberNames();
			if(combinedArgumentList.size() - 1 != memberNames.length) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The array length is not equals to the count of member names (" + memberNames.length + ")", SCOPE_ID);
			}
			
			StructObject struct = structObject.getStruct();
			
			int i = -1;
			try {
				for(i = 0;i < memberNames.length;i++)
					struct.setMember(memberNames[i], combinedArgumentList.get(i + 1));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, (i == -1?"":"Argument " + (i + 2) + ": ") + e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("structGet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT, DataType.TEXT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			DataObject memberNameObject = combinedArgumentList.get(1);
			
			StructObject struct = structObject.getStruct();
			
			String memberName = memberNameObject.getText();
			
			try {
				return new DataObject(struct.getMember(memberName));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structGetAll", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setArray(Arrays.stream(struct.getMemberNames()).
						map(memberName -> new DataObject(struct.getMember(memberName))).toArray(DataObject[]::new));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structGetMemberNames", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setArray(Arrays.stream(struct.getMemberNames()).map(DataObject::new).toArray(DataObject[]::new));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structGetMemberCount", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setInt(struct.getMemberNames().length);
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structIsDefinition", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setBoolean(struct.isDefinition());
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structIsInstance", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				return new DataObject().setBoolean(!struct.isDefinition());
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("structDefinitionTypeOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject structObject = combinedArgumentList.get(0);
			
			StructObject struct = structObject.getStruct();
			
			try {
				if(struct.isDefinition())
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The struct may not be a definition struct", SCOPE_ID);
				
				return new DataObject().setStruct(struct.getStructBaseDefinition());
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
	}
	private void addPredefinedComplexStructFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("complex", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject realObject = combinedArgumentList.get(0);
			DataObject imagObject = combinedArgumentList.get(1);
			
			Number realNumber = realObject.toNumber();
			Number imagNumber = imagObject.toNumber();
			
			if(realNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "number"), SCOPE_ID);
			
			if(imagNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "number"), SCOPE_ID);
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(realNumber.doubleValue(), imagNumber.doubleValue()));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("creal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructObject = combinedArgumentList.get(0);
			
			StructObject complexStruct = complexStructObject.getStruct();
			
			if(complexStruct.isDefinition() || !complexStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "&Complex"), SCOPE_ID);
			
			try {
				return new DataObject(complexStruct.getMember("$real"));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("cimag", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructObject = combinedArgumentList.get(0);
			
			StructObject complexStruct = complexStructObject.getStruct();
			
			if(complexStruct.isDefinition() || !complexStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "&Complex"), SCOPE_ID);
			
			try {
				return new DataObject(complexStruct.getMember("$imag"));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("cabs", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructObject = combinedArgumentList.get(0);
			
			StructObject complexStruct = complexStructObject.getStruct();
			
			if(complexStruct.isDefinition() || !complexStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "&Complex"), SCOPE_ID);
			
			try {
				return new DataObject().setDouble(Math.hypot(complexStruct.getMember("$real").getDouble(),
						complexStruct.getMember("$imag").getDouble()));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("conj", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructObject = combinedArgumentList.get(0);
			
			StructObject complexStruct = complexStructObject.getStruct();
			
			if(complexStruct.isDefinition() || !complexStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "&Complex"), SCOPE_ID);
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(complexStruct.getMember("$real").getDouble(),
						-complexStruct.getMember("$imag").getDouble()));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("cinv", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructObject = combinedArgumentList.get(0);
			
			StructObject complexStruct = complexStructObject.getStruct();
			
			if(complexStruct.isDefinition() || !complexStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", "&Complex"), SCOPE_ID);
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(-complexStruct.getMember("$real").getDouble(),
						-complexStruct.getMember("$imag").getDouble()));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("cadd", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT, DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructAObject = combinedArgumentList.get(0);
			DataObject complexStructBObject = combinedArgumentList.get(1);
			
			StructObject complexAStruct = complexStructAObject.getStruct();
			StructObject complexBStruct = complexStructBObject.getStruct();
			
			if(complexAStruct.isDefinition() || !complexAStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "&Complex"), SCOPE_ID);
			
			if(complexBStruct.isDefinition() || !complexBStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "&Complex"), SCOPE_ID);
			
			double realA = complexAStruct.getMember("$real").getDouble();
			double imagA = complexAStruct.getMember("$imag").getDouble();
			
			double realB = complexBStruct.getMember("$real").getDouble();
			double imagB = complexBStruct.getMember("$imag").getDouble();
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(realA + realB, imagA + imagB));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("csub", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT, DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructAObject = combinedArgumentList.get(0);
			DataObject complexStructBObject = combinedArgumentList.get(1);
			
			StructObject complexAStruct = complexStructAObject.getStruct();
			StructObject complexBStruct = complexStructBObject.getStruct();
			
			if(complexAStruct.isDefinition() || !complexAStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "&Complex"), SCOPE_ID);
			
			if(complexBStruct.isDefinition() || !complexBStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "&Complex"), SCOPE_ID);
			
			double realA = complexAStruct.getMember("$real").getDouble();
			double imagA = complexAStruct.getMember("$imag").getDouble();
			
			double realB = complexBStruct.getMember("$real").getDouble();
			double imagB = complexBStruct.getMember("$imag").getDouble();
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(realA - realB, imagA - imagB));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("cmul", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT, DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructAObject = combinedArgumentList.get(0);
			DataObject complexStructBObject = combinedArgumentList.get(1);
			
			StructObject complexAStruct = complexStructAObject.getStruct();
			StructObject complexBStruct = complexStructBObject.getStruct();
			
			if(complexAStruct.isDefinition() || !complexAStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "&Complex"), SCOPE_ID);
			
			if(complexBStruct.isDefinition() || !complexBStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "&Complex"), SCOPE_ID);
			
			double realA = complexAStruct.getMember("$real").getDouble();
			double imagA = complexAStruct.getMember("$imag").getDouble();
			
			double realB = complexBStruct.getMember("$real").getDouble();
			double imagB = complexBStruct.getMember("$imag").getDouble();
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(realA * realB - imagA * imagB, realA * imagB + imagA * realB));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("cdiv", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.STRUCT, DataType.STRUCT), SCOPE_ID)) != null)
				return error;
			
			DataObject complexStructAObject = combinedArgumentList.get(0);
			DataObject complexStructBObject = combinedArgumentList.get(1);
			
			StructObject complexAStruct = complexStructAObject.getStruct();
			StructObject complexBStruct = complexStructBObject.getStruct();
			
			if(complexAStruct.isDefinition() || !complexAStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "&Complex"), SCOPE_ID);
			
			if(complexBStruct.isDefinition() || !complexBStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_COMPLEX))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "&Complex"), SCOPE_ID);
			
			double realA = complexAStruct.getMember("$real").getDouble();
			double imagA = complexAStruct.getMember("$imag").getDouble();
			
			double realB = complexBStruct.getMember("$real").getDouble();
			double imagB = complexBStruct.getMember("$imag").getDouble();
			
			double realNumerator = realA * realB + imagA * imagB;
			double imagNumerator = imagA * realB - realA * imagB;
			
			double denominator = realB * realB + imagB * imagB;
			
			try {
				return new DataObject().setStruct(LangCompositeTypes.createComplex(realNumerator / denominator, imagNumerator / denominator));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		});
	}
	private void addPredefinedModuleFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("getLoadedModules", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			return new DataObject().setArray(interpreter.modules.keySet().stream().map(DataObject::new).toArray(DataObject[]::new));
		});
		funcs.put("getModuleVariableNames", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject moduleNameObject = combinedArgumentList.get(0);
			
			String moduleName = moduleNameObject.getText();
			
			for(int i = 0;i < moduleName.length();i++) {
				char c = moduleName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			LangModule module = interpreter.modules.get(moduleName);
			if(module == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" was not found", SCOPE_ID);
			
			return new DataObject().setArray(module.getExportedVariables().keySet().stream().map(DataObject::new).toArray(DataObject[]::new));
		});
		funcs.put("getModuleFunctionNames", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject moduleNameObject = combinedArgumentList.get(0);
			
			String moduleName = moduleNameObject.getText();
			
			for(int i = 0;i < moduleName.length();i++) {
				char c = moduleName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			LangModule module = interpreter.modules.get(moduleName);
			if(module == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" was not found", SCOPE_ID);
			
			return new DataObject().setArray(module.getExportedFunctions().stream().map(DataObject::new).toArray(DataObject[]::new));
		});
		funcs.put("getModuleVariable", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject moduleNameObject = combinedArgumentList.get(0);
			DataObject variableNameObject = combinedArgumentList.get(1);
			
			String moduleName = moduleNameObject.getText();
			String variableName = variableNameObject.getText();
			
			for(int i = 0;i < moduleName.length();i++) {
				char c = moduleName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			if(!variableName.startsWith("$") && !variableName.startsWith("&") && !variableName.startsWith("fp."))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name must start with \"$\", \"&\", or \"fp.\"", SCOPE_ID);
			
			int variablePrefixLen = (variableName.charAt(0) == '$' || variableName.charAt(0) == '&')?1:3;
			
			for(int i = variablePrefixLen;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			LangModule module = interpreter.modules.get(moduleName);
			if(module == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" was not found", SCOPE_ID);
			
			DataObject variable = module.getExportedVariables().get(variableName);
			if(variable == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The variable \"" + variableName + "\" was not found in the module \""
						+ moduleName + "\"", SCOPE_ID);
			
			return variable;
		});
		funcs.put("getModuleVariableNormal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject moduleNameObject = combinedArgumentList.get(0);
			DataObject variableNameObject = combinedArgumentList.get(1);
			
			String moduleName = moduleNameObject.getText();
			String variableName = variableNameObject.getText();
			
			for(int i = 0;i < moduleName.length();i++) {
				char c = moduleName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			for(int i = 0;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			variableName = "$" + variableName;
			
			LangModule module = interpreter.modules.get(moduleName);
			if(module == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" was not found", SCOPE_ID);
			
			DataObject variable = module.getExportedVariables().get(variableName);
			if(variable == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The variable \"" + variableName + "\" was not found in the module \""
						+ moduleName + "\"", SCOPE_ID);
			
			return variable;
		});
		funcs.put("getModuleVariableComposite", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject moduleNameObject = combinedArgumentList.get(0);
			DataObject variableNameObject = combinedArgumentList.get(1);
			
			String moduleName = moduleNameObject.getText();
			String variableName = variableNameObject.getText();
			
			for(int i = 0;i < moduleName.length();i++) {
				char c = moduleName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			for(int i = 0;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			variableName = "&" + variableName;
			
			LangModule module = interpreter.modules.get(moduleName);
			if(module == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" was not found", SCOPE_ID);
			
			DataObject variable = module.getExportedVariables().get(variableName);
			if(variable == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The variable \"" + variableName + "\" was not found in the module \""
						+ moduleName + "\"", SCOPE_ID);
			
			return variable;
		});
		funcs.put("getModuleVariableFunctionPointer", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject moduleNameObject = combinedArgumentList.get(0);
			DataObject variableNameObject = combinedArgumentList.get(1);
			
			String moduleName = moduleNameObject.getText();
			String variableName = variableNameObject.getText();
			
			for(int i = 0;i < moduleName.length();i++) {
				char c = moduleName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			for(int i = 0;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			variableName = "fp." + variableName;
			
			LangModule module = interpreter.modules.get(moduleName);
			if(module == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" was not found", SCOPE_ID);
			
			DataObject variable = module.getExportedVariables().get(variableName);
			if(variable == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The variable \"" + variableName + "\" was not found in the module \""
						+ moduleName + "\"", SCOPE_ID);
			
			return variable;
		});
		funcs.put("moduleExportFunction", (argumentList, SCOPE_ID) -> {
			LangModule module = interpreter.getCurrentCallStackElement().getModule();
			if(module == null || !module.isLoad())
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleExportFunction\" can only be used inside a module which "
						+ "is in the \"load\" state", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject functionNameObject = combinedArgumentList.get(0);
			DataObject fpObject = combinedArgumentList.get(1);
			if(fpObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			String functionName = functionNameObject.getText();
			for(int i = 0;i < functionName.length();i++) {
				char c = functionName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The function name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			module.getExportedFunctions().add(functionName);
			
			FunctionPointerObject fp = fpObject.getFunctionPointer();
			
			interpreter.funcs.put(functionName, (innerArgumentList, INNER_SCOPE_ID) -> {
				return interpreter.callFunctionPointer(fp, functionName, innerArgumentList, INNER_SCOPE_ID);
			});
			
			return null;
		});
		funcs.put("moduleExportLinkerFunction", (argumentList, SCOPE_ID) -> {
			LangModule module = interpreter.getCurrentCallStackElement().getModule();
			if(module == null || !module.isLoad())
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleExportLinkerFunction\" can only be used inside a module which "
						+ "is in the \"load\" state", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject functionNameObject = combinedArgumentList.get(0);
			DataObject fpObject = combinedArgumentList.get(1);
			if(fpObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			String functionName = functionNameObject.getText();
			for(int i = 0;i < functionName.length();i++) {
				char c = functionName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The function name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			module.getExportedFunctions().add(functionName);
			
			FunctionPointerObject fp = fpObject.getFunctionPointer();
			
			interpreter.funcs.put(functionName, new LangPredefinedFunctionObject() {
				@Override
				public DataObject callFunc(List<DataObject> innerArgumentList, final int INNER_SCOPE_ID) {
					return interpreter.callFunctionPointer(fp, functionName, innerArgumentList, INNER_SCOPE_ID);
				}
				
				@Override
				public boolean isLinkerFunction() {
					return true;
				}
			});
			
			return null;
		});
		funcs.put("moduleExportNormalVariable", (argumentList, SCOPE_ID) -> {
			LangModule module = interpreter.getCurrentCallStackElement().getModule();
			if(module == null || !module.isLoad())
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleExportNormalVariable\" can only be used inside a module which "
						+ "is in the \"load\" state", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject variableNameObject = combinedArgumentList.get(0);
			DataObject variableObject = combinedArgumentList.get(1);
			DataObject finalDataObject = combinedArgumentList.size() >= 3?combinedArgumentList.get(2):null;
			
			String variableName = variableNameObject.getText();
			for(int i = 0;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			if(variableName.startsWith("LANG"))
				throw new RuntimeException("The variable name may not start with LANG");
			
			variableName = "$" + variableName;
			module.getExportedVariables().put(variableName, new DataObject(variableObject).setFinalData(finalDataObject == null?false:finalDataObject.getBoolean()).setVariableName(variableName));
			
			return null;
		});
		funcs.put("moduleExportCompositeVariable", (argumentList, SCOPE_ID) -> {
			LangModule module = interpreter.getCurrentCallStackElement().getModule();
			if(module == null || !module.isLoad())
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleExportCompositeVariable\" can only be used inside a module which "
						+ "is in the \"load\" state", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject variableNameObject = combinedArgumentList.get(0);
			DataObject variableObject = combinedArgumentList.get(1);
			DataObject finalDataObject = combinedArgumentList.size() >= 3?combinedArgumentList.get(2):null;
			
			if(!DataObject.CONSTRAINT_COMPOSITE.isTypeAllowed(variableObject.getType()))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "composite"), SCOPE_ID);
			
			String variableName = variableNameObject.getText();
			for(int i = 0;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			if(variableName.startsWith("LANG"))
				throw new RuntimeException("The variable name may not start with LANG");
			
			variableName = "&" + variableName;
			module.getExportedVariables().put(variableName, new DataObject(variableObject).setFinalData(finalDataObject == null?false:finalDataObject.getBoolean()).setVariableName(variableName));
			
			return null;
		});
		funcs.put("moduleExportFunctionPointerVariable", (argumentList, SCOPE_ID) -> {
			LangModule module = interpreter.getCurrentCallStackElement().getModule();
			if(module == null || !module.isLoad())
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleExportFunctionPointerVariable\" can only be used inside a module which "
						+ "is in the \"load\" state", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject variableNameObject = combinedArgumentList.get(0);
			DataObject variableObject = combinedArgumentList.get(1);
			DataObject finalDataObject = combinedArgumentList.size() >= 3?combinedArgumentList.get(2):null;
			
			if(variableObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.FUNCTION_POINTER), SCOPE_ID);
			
			String variableName = variableNameObject.getText();
			for(int i = 0;i < variableName.length();i++) {
				char c = variableName.charAt(i);
				if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
					continue;
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The variable name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
			}
			
			if(variableName.startsWith("LANG"))
				throw new RuntimeException("The variable name may not start with LANG");
			
			variableName = "fp." + variableName;
			module.getExportedVariables().put(variableName, new DataObject(variableObject).setFinalData(finalDataObject == null?false:finalDataObject.getBoolean()).setVariableName(variableName));
			
			return null;
		});
	}
	private void addPredefinedLangTestFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("testUnit", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			interpreter.langTestStore.addUnit(textObject.getText());
			
			return null;
		});
		funcs.put("testSubUnit", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			try {
				interpreter.langTestStore.addSubUnit(textObject.getText());
			}catch(IllegalStateException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("testAssertError", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject errorObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			InterpretingError langErrno = interpreter.getAndClearErrnoErrorObject(SCOPE_ID);
			InterpretingError expectedError = errorObject.getError().getInterprettingError();
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultError(langErrno == expectedError,
					interpreter.printStackTrace(-1), messageObject == null?null:messageObject.getText(), langErrno,
							expectedError));
			
			return null;
		});
		funcs.put("testAssertEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultEquals(
					actualValueObject.isEquals(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertNotEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotEquals(
					!actualValueObject.isEquals(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertLessThan", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThan(
					actualValueObject.isLessThan(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertLessThanOrEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThanOrEquals(
					actualValueObject.isLessThanOrEquals(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertGreaterThan", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThan(
					actualValueObject.isGreaterThan(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertGreaterThanOrEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThanOrEquals(
					actualValueObject.isGreaterThanOrEquals(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertStrictEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictEquals(
					actualValueObject.isStrictEquals(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertStrictNotEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictNotEquals(
					!actualValueObject.isStrictEquals(expectedValueObject), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("testAssertTranslationValueEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueEquals(
					translationValue != null && translationValue.equals(expectedValueObject.getText()),
					interpreter.printStackTrace(-1), messageObject == null?null:messageObject.getText(),
							translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("testAssertTranslationValueNotEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueNotEquals(
					translationValue != null && !translationValue.equals(expectedValueObject.getText()),
					interpreter.printStackTrace(-1), messageObject == null?null:messageObject.getText(),
							translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("testAssertTranslationKeyFound", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyFound(
					translationValue != null, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("testAssertTranslationKeyNotFound", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyNotFound(
					translationValue == null, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("testAssertTypeEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedTypeObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(expectedTypeObject.getType() != DataType.TYPE)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			DataType expectedType = expectedTypeObject.getTypeValue();
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTypeEquals(
					actualValueObject.getType() == expectedType, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedType));
			
			return null;
		});
		funcs.put("testAssertTypeNotEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedTypeObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(expectedTypeObject.getType() != DataType.TYPE)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			DataType expectedType = expectedTypeObject.getTypeValue();
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTypeNotEquals(
					actualValueObject.getType() != expectedType, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedType));
			
			return null;
		});
		funcs.put("testAssertNull", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNull(
					actualValueObject.getType() == DataType.NULL, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertNotNull", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotNull(
					actualValueObject.getType() != DataType.NULL, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertVoid", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultVoid(
					actualValueObject.getType() == DataType.VOID, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertNotVoid", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotVoid(
					actualValueObject.getType() != DataType.VOID, interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertFinal", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultFinal(actualValueObject.isFinalData(),
					interpreter.printStackTrace(-1), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("testAssertNotFinal", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotFinal(
					!actualValueObject.isFinalData(), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertStatic", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStatic(
					actualValueObject.isStaticData(), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertNotStatic", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotStatic(
					!actualValueObject.isStaticData(), interpreter.printStackTrace(-1),
					messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("testAssertThrow", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject expectedThrowObject= combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(expectedThrowObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			InterpretingError expectedError = expectedThrowObject.getError().getInterprettingError();
			
			interpreter.langTestExpectedReturnValueScopeID = SCOPE_ID;
			interpreter.langTestExpectedThrowValue = expectedError;
			interpreter.langTestMessageForLastTestResult = messageObject == null?null:messageObject.getText();
			
			return null;
		});
		funcs.put("testAssertReturn", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject expectedReturnObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestExpectedReturnValueScopeID = SCOPE_ID;
			interpreter.langTestExpectedReturnValue = expectedReturnObject;
			interpreter.langTestMessageForLastTestResult = messageObject == null?null:messageObject.getText();
			
			return null;
		});
		funcs.put("testAssertNoReturn", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject messageObject = combinedArgumentList.size() < 1?null:combinedArgumentList.get(0);
			
			interpreter.langTestExpectedReturnValueScopeID = SCOPE_ID;
			interpreter.langTestExpectedNoReturnValue = true;
			interpreter.langTestMessageForLastTestResult = messageObject == null?null:messageObject.getText();
			
			return null;
		});
		funcs.put("testAssertFail", (argumentList, SCOPE_ID) -> {
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			if(messageObject == null) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "Too few arguments (1 needed)", SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultFail(interpreter.printStackTrace(-1),
					messageObject.getText()));
			
			return null;
		});
		funcs.put("testClearAllTranslations", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			new HashSet<>(interpreter.data.get(SCOPE_ID).lang.keySet()).forEach(translationKey -> {
				if(!translationKey.startsWith("lang."))
					interpreter.data.get(SCOPE_ID).lang.remove(translationKey);
			});
			
			return null;
		});
		funcs.put("testPrintResults", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			if(interpreter.term == null)
				System.out.println(interpreter.langTestStore.printResults());
			else
				interpreter.langTestStore.printResultsToTerminal(interpreter.term);
			
			return null;
		});
	}
	
	private DataObject executeLinkerFunction(List<DataObject> argumentList, Consumer<Integer> function, int SCOPE_ID) {
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < 1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		DataObject langFileNameObject = combinedArgumentList.remove(0);
		if(langFileNameObject.getType() != DataType.TEXT)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		
		String[] langArgs = combinedArgumentList.stream().map(DataObject::getText).collect(Collectors.toList()).toArray(new String[0]);
		String langFileName = langFileNameObject.getText();
		if(!langFileName.endsWith(".lang"))
			return interpreter.setErrnoErrorObject(InterpretingError.NO_LANG_FILE, SCOPE_ID);
		
		String absolutePath;
		
		LangModule module = interpreter.getCurrentCallStackElement().getModule();
		boolean insideModule = module != null;
		if(insideModule) {
			absolutePath = LangModuleManager.getModuleFilePath(module, interpreter.getCurrentCallStackElement().getLangPath(), langFileName);
		}else {
			if(new File(langFileName).isAbsolute())
				absolutePath = langFileName;
			else
				absolutePath = interpreter.getCurrentCallStackElement().getLangPath() + File.separator + langFileName;
		}
		
		final int NEW_SCOPE_ID = SCOPE_ID + 1;
		
		String langPathTmp = absolutePath;
		if(insideModule) {
			langPathTmp = absolutePath.substring(0, absolutePath.lastIndexOf('/'));
			
			//Update call stack
			interpreter.pushStackElement(new StackElement("<module:" + module.getFile() + "[" + module.getLangModuleConfiguration().getName() + "]>" + langPathTmp,
					langFileName.substring(langFileName.lastIndexOf('/') + 1), null, module), -1);
		}else {
			langPathTmp = interpreter.langPlatformAPI.getLangPath(langPathTmp);
			
			//Update call stack
			interpreter.pushStackElement(new StackElement(langPathTmp, interpreter.langPlatformAPI.getLangFileName(langFileName), null, null), -1);
		}
		
		//Create an empty data map
		interpreter.createDataMap(NEW_SCOPE_ID, langArgs);
		
		DataObject retTmp;
		
		int originalLineNumber = interpreter.getParserLineNumber();
		try(BufferedReader reader = insideModule?LangModuleManager.readModuleLangFile(module, absolutePath):interpreter.langPlatformAPI.getLangReader(absolutePath)) {
			interpreter.resetParserPositionVars();
			interpreter.interpretLines(reader, NEW_SCOPE_ID);
			
			function.accept(NEW_SCOPE_ID);
		}catch(IOException e) {
			interpreter.data.remove(NEW_SCOPE_ID);
			return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
		}finally {
			interpreter.setParserLineNumber(originalLineNumber);
			
			//Remove data map
			interpreter.data.remove(NEW_SCOPE_ID);
			
			//Get returned value from executed Lang file
			retTmp = interpreter.getAndResetReturnValue(SCOPE_ID);
			
			//Update call stack
			interpreter.popStackElement();
		}
		
		return retTmp;
	}
	
	public void addLinkerFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("bindLibrary", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				return executeLinkerFunction(argumentList, NEW_SCOPE_ID -> {
					//Copy all vars, arrPtrs and funcPtrs
					interpreter.data.get(NEW_SCOPE_ID).var.forEach((name, val) -> {
						DataObject oldData = interpreter.data.get(SCOPE_ID).var.get(name);
						if(oldData == null || (!oldData.isFinalData() && !oldData.isLangVar())) { //No LANG data vars and no final data
							interpreter.data.get(SCOPE_ID).var.put(name, val);
						}
					});
				}, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("link", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				return executeLinkerFunction(argumentList, NEW_SCOPE_ID -> {
					//Copy linked translation map (not "lang.* = *") to the "link caller"'s translation map
					interpreter.data.get(NEW_SCOPE_ID).lang.forEach((k, v) -> {
						if(!k.startsWith("lang.")) {
							interpreter.data.get(SCOPE_ID).lang.put(k, v); //Copy to "old" SCOPE_ID
						}
					});
				}, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("include", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				return executeLinkerFunction(argumentList, NEW_SCOPE_ID -> {
					//Copy linked translation map (not "lang.* = *") to the "link caller"'s translation map
					interpreter.data.get(NEW_SCOPE_ID).lang.forEach((k, v) -> {
						if(!k.startsWith("lang.")) {
							interpreter.data.get(SCOPE_ID).lang.put(k, v); //Copy to "old" SCOPE_ID
						}
					});
					
					//Copy all vars, arrPtrs and funcPtrs
					interpreter.data.get(NEW_SCOPE_ID).var.forEach((name, val) -> {
						DataObject oldData = interpreter.data.get(SCOPE_ID).var.get(name);
						if(oldData == null || (!oldData.isFinalData() && !oldData.isLangVar())) { //No LANG data vars and no final data
							interpreter.data.get(SCOPE_ID).var.put(name, val);
						}
					});
				}, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("loadModule", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				if(combinedArgumentList.size() < 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
				DataObject moduleFileObject = combinedArgumentList.remove(0);
				String moduleFile = moduleFileObject.getText();
				
				if(!moduleFile.endsWith(".lm"))
					return interpreter.setErrnoErrorObject(InterpretingError.NO_LANG_FILE, "Modules must have a file extension of\".lm\"", SCOPE_ID);
				
				if(!new File(moduleFile).isAbsolute())
					moduleFile = interpreter.getCurrentCallStackElement().getLangPath() + File.separator + moduleFile;
				
				return interpreter.moduleManager.load(moduleFile, argumentList, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("unloadModule", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				if(combinedArgumentList.size() < 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
				DataObject moduleNameObject = combinedArgumentList.remove(0);
				String moduleName = moduleNameObject.getText();
				for(int i = 0;i < moduleName.length();i++) {
					char c = moduleName.charAt(i);
					if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
						continue;
					
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The module name may only contain alphanumeric characters and underscore (_)", SCOPE_ID);
				}
				
				return interpreter.moduleManager.unload(moduleName, argumentList, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("moduleLoadNative", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				LangModule module = interpreter.getCurrentCallStackElement().getModule();
				if(module == null || !module.isLoad())
					return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleLoadNative\" can only be used inside a module which "
							+ "is in the \"load\" state", SCOPE_ID);
				
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				if(combinedArgumentList.size() < 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
				DataObject entryPointObject = combinedArgumentList.remove(0);
				String entryPoint = entryPointObject.getText();
				
				return interpreter.moduleManager.loadNative(entryPoint, module, argumentList, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
		funcs.put("moduleUnloadNative", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				LangModule module = interpreter.getCurrentCallStackElement().getModule();
				if(module == null || module.isLoad())
					return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleUnloadNative\" can only be used inside a module which "
							+ "is in the \"unload\" state", SCOPE_ID);
				
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				if(combinedArgumentList.size() < 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
				DataObject entryPointObject = combinedArgumentList.remove(0);
				String entryPoint = entryPointObject.getText();
				return interpreter.moduleManager.unloadNative(entryPoint, module, argumentList, SCOPE_ID);
			}
			
			@Override
			public boolean isLinkerFunction() {
				return true;
			}
		});
	}
	
	public static final class LangPredefinedResetFunctions {
		private LangPredefinedResetFunctions() {}
		
		@LangFunction("freeVar")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject freeVarFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$pointer") @AllowedTypes(DataObject.DataType.VAR_POINTER) DataObject pointerObject) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			if(dereferencedVarPointer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			if(dereferencedVarPointer.isFinalData() || dereferencedVarPointer.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			interpreter.data.get(SCOPE_ID).var.remove(variableName);
			
			return null;
		}
		
		@LangFunction("freeAllVars")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject freeAllVarsFunction(LangInterpreter interpreter, int SCOPE_ID) {
			interpreter.resetVars(SCOPE_ID);
			
			return null;
		}
		
		@LangFunction("resetErrno")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject resetErrnoFunction(LangInterpreter interpreter, int SCOPE_ID) {
			interpreter.getAndClearErrnoErrorObject(SCOPE_ID);
			
			return null;
		}
	}
	
	public static final class LangPredefinedErrorFunctions {
		private LangPredefinedErrorFunctions() {}
		
		@LangFunction("getErrorText")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject getErrorTextFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return new DataObject(interpreter.getAndClearErrnoErrorObject(SCOPE_ID).getErrorText());
		}
		
		@LangFunction("errorText")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject errorTextFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$error") @AllowedTypes(DataObject.DataType.ERROR) DataObject errorObject) {
			return new DataObject(errorObject.getError().getErrtxt());
		}
		
		@LangFunction("errorCode")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject errorCodeFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$error") @AllowedTypes(DataObject.DataType.ERROR) DataObject errorObject) {
			return new DataObject().setInt(errorObject.getError().getErrno());
		}
		
		@LangFunction("errorMessage")
		@AllowedTypes({DataObject.DataType.NULL, DataObject.DataType.TEXT})
		public static DataObject errorMessageFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$error") @AllowedTypes(DataObject.DataType.ERROR) DataObject errorObject) {
			String msg = errorObject.getError().getMessage();
			return msg == null?new DataObject().setNull():new DataObject().setText(msg);
		}
		
		@LangFunction("withErrorMessage")
		@AllowedTypes(DataObject.DataType.ERROR)
		public static DataObject withErrorMessageFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$error") @AllowedTypes(DataObject.DataType.ERROR) DataObject errorObject,
				@LangParameter("$text") DataObject textObject) {
			return new DataObject().setError(new ErrorObject(errorObject.getError().getInterprettingError(), textObject.getText()));
		}
	}
	
	public static final class LangPredefinedLangFunctions {
		private LangPredefinedLangFunctions() {}
		
		@LangFunction("isLangVersionNewer")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isLangVersionNewerFunction(LangInterpreter interpreter, int SCOPE_ID) {
			String langVer = interpreter.data.get(SCOPE_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			Integer compVer = LangUtils.compareVersions(LangInterpreter.VERSION, langVer);
			if(compVer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LANG_VER_ERROR, "lang.version has an invalid format", SCOPE_ID);
			return new DataObject().setBoolean(compVer > 0);
		}
		
		@LangFunction("isLangVersionOlder")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isLangVersionOlderFunction(LangInterpreter interpreter, int SCOPE_ID) {
			String langVer = interpreter.data.get(SCOPE_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			Integer compVer = LangUtils.compareVersions(LangInterpreter.VERSION, langVer);
			if(compVer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LANG_VER_ERROR, "lang.version has an invalid format", SCOPE_ID);
			return new DataObject().setBoolean(compVer < 0);
		}
	}
	
	public static final class LangPredefinedSystemFunctions {
		private LangPredefinedSystemFunctions() {}
		
		@LangFunction("sleep")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject sleepFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$milliSeconds") @NumberValue Number milliSeconds) {
			if(milliSeconds.longValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$milliSeconds\") must be >= 0", SCOPE_ID);
			
			try {
				Thread.sleep(milliSeconds.longValue());
			}catch(InterruptedException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		}
		
		@LangFunction("nanoTime")
		@AllowedTypes(DataObject.DataType.LONG)
		public static DataObject nanoTimeFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return new DataObject().setLong(System.nanoTime());
		}
		
		@LangFunction("currentTimeMillis")
		@AllowedTypes(DataObject.DataType.LONG)
		public static DataObject currentTimeMillisFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return new DataObject().setLong(System.currentTimeMillis());
		}
		
		@LangFunction("currentUnixTime")
		@AllowedTypes(DataObject.DataType.LONG)
		public static DataObject currentUnixTimeFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return new DataObject().setLong(Instant.now().getEpochSecond());
		}
		
		@LangFunction(value="repeat", hasInfo=true)
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject repeatFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("fp.loop") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject loopFunctionObject,
				@LangParameter("$repeatCount") @NumberValue Number repeatCountNumber) {
			return repeatFunction(interpreter, SCOPE_ID, loopFunctionObject, repeatCountNumber, false);
		}
		@LangFunction("repeat")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject repeatFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("fp.loop") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject loopFunctionObject,
				@LangParameter("$repeatCount") @NumberValue Number repeatCountNumber,
				@LangParameter("$breakable") @BooleanValue boolean breakable) {
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			
			long repeatCount = repeatCountNumber.longValue();
			if(repeatCount < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_REPEAT_COUNT, SCOPE_ID);
			
			if(breakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject(LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("break")
					@AllowedTypes(DataObject.DataType.VOID)
					public DataObject breakFunction(int SCOPE_ID) {
						shouldBreak[0] = true;
						
						return null;
					}
				})));
				
				for(int i = 0;i < repeatCount;i++) {
					interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
							Arrays.asList(
								new DataObject().setInt(i),
								breakFunc
							)
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				for(int i = 0;i < repeatCount;i++) {
					interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), Arrays.asList(
							new DataObject().setInt(i)
					), SCOPE_ID);
				}
			}
			
			return null;
		}
		
		@LangFunction(value="repeatWhile", hasInfo=true)
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject repeatWhileFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("fp.loop") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject loopFunctionObject,
				@LangParameter("fp.check") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject checkFunctionObject) {
			return repeatWhileFunction(interpreter, SCOPE_ID, loopFunctionObject, checkFunctionObject, false);
		}
		@LangFunction("repeatWhile")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject repeatWhileFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("fp.loop") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject loopFunctionObject,
				@LangParameter("fp.check") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject checkFunctionObject,
				@LangParameter("$breakable") @BooleanValue boolean breakable) {
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			if(breakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject(LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("break")
					@AllowedTypes(DataObject.DataType.VOID)
					public DataObject breakFunction(int SCOPE_ID) {
						shouldBreak[0] = true;
						
						return null;
					}
				})));
				
				while(interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean()) {
					interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), Arrays.asList(
						breakFunc
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				while(interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean())
					interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID);
			}
			
			return null;
		}
		
		@LangFunction(value="repeatUntil", hasInfo=true)
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject repeatUntilFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("fp.loop") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject loopFunctionObject,
				@LangParameter("fp.check") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject checkFunctionObject) {
			return repeatUntilFunction(interpreter, SCOPE_ID, loopFunctionObject, checkFunctionObject, false);
		}
		@LangFunction("repeatUntil")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject repeatUntilFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("fp.loop") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject loopFunctionObject,
				@LangParameter("fp.check") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject checkFunctionObject,
				@LangParameter("$breakable") @BooleanValue boolean breakable) {
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			if(breakable) {
				boolean[] shouldBreak = new boolean[] {false};
				
				DataObject breakFunc = new DataObject().setFunctionPointer(new FunctionPointerObject(LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("break")
					@AllowedTypes(DataObject.DataType.VOID)
					public DataObject breakFunction(int SCOPE_ID) {
						shouldBreak[0] = true;
						
						return null;
					}
				})));
				
				while(!interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean()) {
					interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), Arrays.asList(
						breakFunc
					), SCOPE_ID);
					
					if(shouldBreak[0])
						break;
				}
			}else {
				while(!interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean())
					interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID);
			}
			
			return null;
		}
		
		@LangFunction("getTranslationValue")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject getTranslationValueFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$translationKey") @VarArgs DataObject translationKeyObject) {
			String translationValue = interpreter.data.get(SCOPE_ID).lang.get(translationKeyObject.getText());
			if(translationValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
			
			return new DataObject(translationValue);
		}
		
		@LangFunction("getTranslationValueTemplatePluralization")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject getTranslationValueTemplatePluralizationFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$count") @NumberValue Number count,
				@LangParameter("$translationKey") @VarArgs DataObject translationKeyObject) {
			if(count.intValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$count\") must be >= 0", SCOPE_ID);
			
			String translationValue = interpreter.data.get(SCOPE_ID).lang.get(translationKeyObject.getText());
			if(translationValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
			
			try {
				return new DataObject(LangUtils.formatTranslationTemplatePluralization(translationValue, count.intValue()));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_TEMPLATE_SYNTAX, "Invalid count range", SCOPE_ID);
			}catch(InvalidTranslationTemplateSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_TEMPLATE_SYNTAX, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("makeFinal")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject makeFinalFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$ptr") @AllowedTypes(DataType.VAR_POINTER) DataObject pointerObject) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Anonymous values can not be modified", SCOPE_ID);
			
			if(dereferencedVarPointer.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			dereferencedVarPointer.setFinalData(true);
			
			return null;
		}
		
		@LangFunction("asFinal")
		public static DataObject asFinalFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") DataObject valueObject) {
			return new DataObject(valueObject).setCopyStaticAndFinalModifiers(true).setFinalData(true);
		}
		
		@LangFunction("isFinal")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isFinalFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") @CallByPointer DataObject pointerObject) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			
			return new DataObject().setBoolean(dereferencedVarPointer.isFinalData());
		}
		
		@LangFunction("makeStatic")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject makeStaticFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$ptr") @AllowedTypes(DataType.VAR_POINTER) DataObject pointerObject) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Anonymous values can not be modified", SCOPE_ID);
			
			if(dereferencedVarPointer.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			dereferencedVarPointer.setStaticData(true);
			
			return null;
		}
		
		@LangFunction("asStatic")
		public static DataObject asStaticFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") DataObject valueObject) {
			return new DataObject(valueObject).setCopyStaticAndFinalModifiers(true).setStaticData(true);
		}
		
		@LangFunction("isStatic")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isStaticFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") @CallByPointer DataObject pointerObject) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			
			return new DataObject().setBoolean(dereferencedVarPointer.isStaticData());
		}
		
		@LangFunction("constrainVariableAllowedTypes")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject constrainVariableAllowedTypesFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$ptr") @AllowedTypes(DataType.VAR_POINTER) DataObject pointerObject,
				@LangParameter("&types") @AllowedTypes(DataType.TYPE) @VarArgs List<DataObject> typeObjects) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Anonymous values can not be modified", SCOPE_ID);
			
			if(dereferencedVarPointer.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			List<DataType> types = typeObjects.stream().map(DataObject::getTypeValue).collect(Collectors.toList());
			
			try {
				dereferencedVarPointer.setTypeConstraint(DataObject.DataTypeConstraint.fromAllowedTypes(types));
			}catch(DataObject.DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		}
		
		@LangFunction("constrainVariableNotAllowedTypes")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject constrainVariableNotAllowedTypesFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$ptr") @AllowedTypes(DataType.VAR_POINTER) DataObject pointerObject,
				@LangParameter("&types") @AllowedTypes(DataType.TYPE) @VarArgs List<DataObject> typeObjects) {
			DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Anonymous values can not be modified", SCOPE_ID);
			
			if(dereferencedVarPointer.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			List<DataType> types = typeObjects.stream().map(DataObject::getTypeValue).collect(Collectors.toList());
			
			try {
				dereferencedVarPointer.setTypeConstraint(DataObject.DataTypeConstraint.fromNotAllowedTypes(types));
			}catch(DataObject.DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		}
		
		@LangFunction("pointerTo")
		@AllowedTypes(DataObject.DataType.VAR_POINTER)
		public static DataObject pointerToFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") @CallByPointer DataObject pointerObject) {
			return new DataObject(pointerObject);
		}
		
		@LangFunction("exec")
		public static DataObject execFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			//Update call stack
			StackElement currentStackElement = interpreter.getCurrentCallStackElement();
			interpreter.pushStackElement(new StackElement(currentStackElement.getLangPath(),
					currentStackElement.getLangFile(), "<exec-code>", currentStackElement.getModule()), -1);
			
			int originalLineNumber = interpreter.getParserLineNumber();
			try(BufferedReader lines = new BufferedReader(new StringReader(textObject.getText()))) {
				interpreter.resetParserPositionVars();
				interpreter.interpretLines(lines, SCOPE_ID);
				
				return interpreter.getAndResetReturnValue(SCOPE_ID);
			}catch(IOException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, e.getMessage(), SCOPE_ID);
			}finally {
				interpreter.setParserLineNumber(originalLineNumber);
				
				//Update call stack
				interpreter.popStackElement();
			}
		}
		
		@LangFunction("isTerminalAvailable")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isTerminalAvailableFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return new DataObject().setBoolean(interpreter.term != null);
		}
		
		@LangFunction(value="isInstanceOf", hasInfo=true)
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isInstanceOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") DataObject valueObject,
				@LangParameter("$type") @AllowedTypes(DataObject.DataType.TYPE) DataObject typeObject) {
			return new DataObject().setBoolean(valueObject.getType() == typeObject.getTypeValue());
		}
		@LangFunction("isInstanceOf")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isInstanceOfWithStructParametersFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") @AllowedTypes(DataObject.DataType.STRUCT) DataObject valueObject,
				@LangParameter("$type") @AllowedTypes(DataObject.DataType.STRUCT) DataObject typeObject) {
			StructObject valueStruct = valueObject.getStruct();
			StructObject typeStruct = typeObject.getStruct();
			
			if(valueStruct.isDefinition())
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$value\") must not be a definition struct", SCOPE_ID);
			
			if(!typeStruct.isDefinition())
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$type\") be a definition struct", SCOPE_ID);
			
			return new DataObject().setBoolean(valueStruct.getStructBaseDefinition().equals(typeStruct));
		}
		
		@LangFunction("typeOf")
		@AllowedTypes(DataObject.DataType.TYPE)
		public static DataObject typeOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$value") DataObject valueObject) {
			return new DataObject().setTypeValue(valueObject.getType());
		}
		
		@LangFunction("getCurrentStackTraceElement")
		@AllowedTypes(DataObject.DataType.STRUCT)
		public static DataObject getCurrentStackTraceElementFunction(LangInterpreter interpreter, int SCOPE_ID) {
			StackElement currentStackElement = interpreter.getCallStackElements().get(interpreter.getCallStackElements().size() - 1);
			
			String modulePath = null;
			String moduleFile = null;
			if(currentStackElement.module != null) {
				String prefix = "<module:" + currentStackElement.module.getFile() + "[" + currentStackElement.module.getLangModuleConfiguration().getName() + "]>";
				
				modulePath = currentStackElement.getLangPath().substring(prefix.length());
				if(!modulePath.startsWith("/"))
					modulePath = "/" + modulePath;
				
				moduleFile = currentStackElement.getLangFile();
			}
			
			return new DataObject().setStruct(LangCompositeTypes.createStackTraceElement(currentStackElement.getLangPath(),
					currentStackElement.getLangFile(), currentStackElement.getLineNumber(), currentStackElement.getLangFunctionName(),
					modulePath, moduleFile));
		}
		
		@LangFunction("getStackTraceElements")
		@AllowedTypes(DataObject.DataType.ARRAY)
		public static DataObject getStackTraceElementsElementFunction(LangInterpreter interpreter, int SCOPE_ID) {
			List<StackElement> stackTraceElements = interpreter.getCallStackElements();
			
			return new DataObject().setArray(stackTraceElements.stream().map(ele -> {
				String modulePath = null;
				String moduleFile = null;
				if(ele.module != null) {
					String prefix = "<module:" + ele.module.getFile() + "[" + ele.module.getLangModuleConfiguration().getName() + "]>";
					
					modulePath = ele.getLangPath().substring(prefix.length());
					if(!modulePath.startsWith("/"))
						modulePath = "/" + modulePath;
					
					moduleFile = ele.getLangFile();
				}
				
				return new DataObject().setStruct(LangCompositeTypes.createStackTraceElement(ele.getLangPath(),
						ele.getLangFile(), ele.getLineNumber(), ele.getLangFunctionName(), modulePath, moduleFile));
			}).toArray(DataObject[]::new));
		}
		
		@LangFunction("getStackTrace")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject getStackTraceFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return new DataObject(interpreter.printStackTrace(-1));
		}
	}
	
	public static final class LangPredefinedIOFunctions {
		private LangPredefinedIOFunctions() {}
		
		@LangFunction("readTerminal")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject readTerminalFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$message") @VarArgs DataObject messageObject) {
			if(interpreter.term == null && !interpreter.executionFlags.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, SCOPE_ID);
			
			String message = messageObject.getText();
			
			if(interpreter.term == null) {
				interpreter.setErrno(InterpretingError.NO_TERMINAL_WARNING, SCOPE_ID);
				
				if(!message.isEmpty())
					System.out.println(message);
				System.out.print("Input: ");
				
				try {
					BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
					String line = reader.readLine();
					if(line == null)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
					return new DataObject(line);
				}catch(IOException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
				}
			}else {
				try {
					return new DataObject(interpreter.langPlatformAPI.showInputDialog(message));
				}catch(Exception e) {
					return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, SCOPE_ID);
				}
			}
		}
		
		@LangFunction("printTerminal")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject printTerminalFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$logLevel") @NumberValue Number logLevelNumber,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			if(interpreter.term == null && !interpreter.executionFlags.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, SCOPE_ID);
			
			int logLevel = logLevelNumber.intValue();
			Level level = null;
			for(Level lvl:Level.values()) {
				if(lvl.getLevel() == logLevel) {
					level = lvl;
					
					break;
				}
			}
			if(level == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_LOG_LEVEL, SCOPE_ID);
			
			String text = textObject.getText();
			
			if(interpreter.term == null) {
				interpreter.setErrno(InterpretingError.NO_TERMINAL_WARNING, SCOPE_ID);
				
				@SuppressWarnings("resource")
				PrintStream stream = logLevel > 3?System.err:System.out; //Write to standard error if the log level is WARNING or higher
				stream.printf("[%-8s]: ", level.getLevelName());
				stream.println(text);
				return null;
			}else {
				interpreter.term.logln(level, "[From Lang file]: " + text, LangInterpreter.class);
			}
			
			return null;
		}
		
		@LangFunction("printError")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject printErrorFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			if(interpreter.term == null && !interpreter.executionFlags.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, SCOPE_ID);
			
			InterpretingError error = interpreter.getAndClearErrnoErrorObject(SCOPE_ID);
			int errno = error.getErrorCode();
			Level level = null;
			if(errno > 0)
				level = Level.ERROR;
			else if(errno < 0)
				level = Level.WARNING;
			else
				level = Level.INFO;
			
			String text = textObject.getText();
			
			if(interpreter.term == null) {
				@SuppressWarnings("resource")
				PrintStream stream = level.getLevel() > 3?System.err:System.out; //Write to standard error if the log level is WARNING or higher
				stream.printf("[%-8s]: ", level.getLevelName());
				stream.println((text.isEmpty()?"":(text + ": ")) + error.getErrorText());
			}else {
				interpreter.term.logln(level, "[From Lang file]: " + (text.isEmpty()?"":(text + ": ")) + error.getErrorText(), LangInterpreter.class);
			}
			
			return null;
		}
		
		@LangFunction(value="input", hasInfo=true)
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject inputFunction(LangInterpreter interpreter, int SCOPE_ID) {
			return inputFunction(interpreter, SCOPE_ID, null);
		}
		@LangFunction("input")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject inputFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$maxCharCount") @NumberValue Number maxCharCount) {
			if(maxCharCount != null && maxCharCount.intValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$maxCharCount\") must be >= 0", SCOPE_ID);
			
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				if(maxCharCount == null) {
					String line = reader.readLine();
					if(line == null)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
					return new DataObject(line);
				}else {
					char[] buf = new char[maxCharCount.intValue()];
					int count = reader.read(buf);
					if(count == -1)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
					return new DataObject(new String(buf));
				}
			}catch(IOException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("print")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject printFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			System.out.print(textObject.getText());
			
			return null;
		}
		
		@LangFunction("println")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject printlnFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			System.out.println(textObject.getText());
			
			return null;
		}
		
		@LangFunction("printf")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject printfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$format") DataObject formatObject,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			DataObject out = interpreter.formatText(formatObject.getText(), args, SCOPE_ID);
			if(out.getType() == DataType.ERROR)
				return out;
			
			System.out.print(out.getText());
			
			return null;
		}
		
		@LangFunction("error")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject errorFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			System.err.print(textObject.getText());
			
			return null;
		}
		
		@LangFunction("errorln")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject errorlnFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			System.err.println(textObject.getText());
			
			return null;
		}
		
		@LangFunction("errorf")
		@AllowedTypes(DataObject.DataType.VOID)
		public static DataObject errorfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$format") DataObject formatObject,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			DataObject out = interpreter.formatText(formatObject.getText(), args, SCOPE_ID);
			if(out.getType() == DataType.ERROR)
				return out;
			
			System.err.print(out.getText());
			
			return null;
		}
	}
	
	public static final class LangPredefinedNumberFunctions {
		private LangPredefinedNumberFunctions() {}
		
		@LangFunction("binToDec")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject binToDecFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$bin") DataObject binObject) {
			String binString = binObject.getText();
			if(!binString.startsWith("0b") && !binString.startsWith("0B"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_BIN_NUM, "Wrong prefix (Should be 0b or 0B)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(binString.substring(2), 2));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_BIN_NUM, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("octToDec")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject octToDecFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$oct") DataObject octObject) {
			String octString = octObject.getText();
			if(!octString.startsWith("0o") && !octString.startsWith("0O"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_OCT_NUM, "Wrong prefix (Should be 0o or 0O)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(octString.substring(2), 8));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_OCT_NUM, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("hexToDec")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject hexToDecFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$hex") DataObject hexObject) {
			String hexString = hexObject.getText();
			if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, "Wrong prefix (Should be 0x or 0X)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("toNumberBase")
		@AllowedTypes({DataObject.DataType.INT, DataObject.DataType.LONG})
		public static DataObject toNumberBaseFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") DataObject numberObject,
				@LangParameter("$base") @NumberValue Number base) {
			String numberString = numberObject.getText();
			
			if(base.intValue() < 2 || base.intValue() > 36)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_NUMBER_BASE,
						"Argument 2 (\"$base\") must be between 2 (inclusive) and 36 (inclusive)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(numberString, base.intValue()));
			}catch(NumberFormatException e1) {
				try {
					return new DataObject().setLong(Long.parseLong(numberString, base.intValue()));
				}catch(NumberFormatException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.NO_BASE_N_NUM,
							"Argument 1 (\"$number\" = \"" + numberString + "\") is not in base \"" + base.intValue() + "\"", SCOPE_ID);
				}
			}
		}
		
		@LangFunction("toTextBase")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject toTextBaseFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number,
				@LangParameter("$base") @NumberValue Number base) {
			if(base.intValue() < 2 || base.intValue() > 36)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_NUMBER_BASE,
						"Argument 2 (\"$base\") must be between 2 (inclusive) and 36 (inclusive)", SCOPE_ID);
			
			int numberInt = number.intValue();
			long numberLong = number.longValue();
			try {
				if(numberLong < 0?(numberLong < numberInt):(numberLong > numberInt))
					return new DataObject().setText(Long.toString(number.longValue(), base.intValue()).toUpperCase());
				else
					return new DataObject().setText(Integer.toString(number.intValue(), base.intValue()).toUpperCase());
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$number\") is invalid: " + e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("toIntBits")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject toIntBitsFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setInt(Float.floatToRawIntBits(number.floatValue()));
		}
		
		@LangFunction("toFloatBits")
		@AllowedTypes(DataObject.DataType.FLOAT)
		public static DataObject toFloatBitsFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setFloat(Float.intBitsToFloat(number.intValue()));
		}
		
		@LangFunction("toLongBits")
		@AllowedTypes(DataObject.DataType.LONG)
		public static DataObject toLongBitsFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setLong(Double.doubleToRawLongBits(number.doubleValue()));
		}
		
		@LangFunction("toDoubleBits")
		@AllowedTypes(DataObject.DataType.DOUBLE)
		public static DataObject toDoubleBitsFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setDouble(Double.longBitsToDouble(number.longValue()));
		}
		
		@LangFunction("toInt")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject toIntFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setInt(number.intValue());
		}
		
		@LangFunction("toLong")
		@AllowedTypes(DataObject.DataType.LONG)
		public static DataObject toLongFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setLong(number.longValue());
		}
		
		@LangFunction("toFloat")
		@AllowedTypes(DataObject.DataType.FLOAT)
		public static DataObject toFloatFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setFloat(number.floatValue());
		}
		
		@LangFunction("toDouble")
		@AllowedTypes(DataObject.DataType.DOUBLE)
		public static DataObject toDoubleFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setDouble(number.doubleValue());
		}
		
		@LangFunction("toNumber")
		@AllowedTypes({DataObject.DataType.INT, DataObject.DataType.LONG, DataObject.DataType.FLOAT, DataObject.DataType.DOUBLE})
		public static DataObject toNumberFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") DataObject numberObject) {
			numberObject = numberObject.convertToNumberAndCreateNewDataObject();
			if(numberObject.getType() == DataType.NULL)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$number\") can not be converted to a number value", SCOPE_ID);
			
			return numberObject;
		}
		
		@LangFunction("ttoi")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject ttoiFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject) {
			String str = textObject.getText();
			try {
				return new DataObject().setInt(Integer.parseInt(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to an INT value", SCOPE_ID);
			}
		}
		
		@LangFunction("ttol")
		@AllowedTypes(DataObject.DataType.LONG)
		public static DataObject ttolFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject) {
			String str = textObject.getText();
			try {
				return new DataObject().setLong(Long.parseLong(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a LONG value", SCOPE_ID);
			}
		}
		
		@LangFunction("ttof")
		@AllowedTypes(DataObject.DataType.FLOAT)
		public static DataObject ttofFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject) {
			String str = textObject.getText();
			
			if(str.isEmpty())
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a FLOAT value", SCOPE_ID);
			
			char lastChar = str.charAt(str.length() - 1);
			if(str.trim().length() != str.length() || lastChar == 'f' || lastChar == 'F' || lastChar == 'd' ||
					lastChar == 'D' || str.contains("x") || str.contains("X"))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a FLOAT value", SCOPE_ID);
			
			try {
				return new DataObject().setFloat(Float.parseFloat(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a FLOAT value", SCOPE_ID);
			}
		}
		
		@LangFunction("ttod")
		@AllowedTypes(DataObject.DataType.DOUBLE)
		public static DataObject ttodFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject) {
			String str = textObject.getText();
			
			if(str.isEmpty())
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a DOUBLE value", SCOPE_ID);
			
			char lastChar = str.charAt(str.length() - 1);
			if(str.trim().length() != str.length() || lastChar == 'f' || lastChar == 'F' || lastChar == 'd' ||
					lastChar == 'D' || str.contains("x") || str.contains("X"))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a DOUBLE value", SCOPE_ID);
			
			try {
				return new DataObject().setDouble(Double.parseDouble(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$text\") can not be converted to a DOUBLE value", SCOPE_ID);
			}
		}
		
		@LangFunction("isNaN")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isNaNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			if(number instanceof Float)
				return new DataObject().setBoolean(Float.isNaN(number.floatValue()));
			
			if(number instanceof Double)
				return new DataObject().setBoolean(Double.isNaN(number.doubleValue()));
			
			return new DataObject().setBoolean(false);
		}
		
		@LangFunction("isInfinite")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isInfiniteFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			if(number instanceof Float)
				return new DataObject().setBoolean(Float.isInfinite(number.floatValue()));
			
			if(number instanceof Double)
				return new DataObject().setBoolean(Double.isInfinite(number.doubleValue()));
			
			return new DataObject().setBoolean(false);
		}
		
		@LangFunction("isFinite")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isFiniteFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			if(number instanceof Float)
				return new DataObject().setBoolean(Float.isFinite(number.floatValue()));
			
			if(number instanceof Double)
				return new DataObject().setBoolean(Double.isFinite(number.doubleValue()));
			
			return new DataObject().setBoolean(false);
		}
		
		@LangFunction("isEven")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isEvenFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setBoolean(number.longValue() % 2 == 0);
		}
		
		@LangFunction("isOdd")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isOddFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$number") @NumberValue Number number) {
			return new DataObject().setBoolean(number.longValue() % 2 == 1);
		}
	}
	
	public static final class LangPredefinedCharacterFunctions {
		private LangPredefinedCharacterFunctions() {}
		
		@LangFunction("toValue")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject toValueFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$char") @AllowedTypes(DataObject.DataType.CHAR) DataObject charObject) {
			return new DataObject().setInt(charObject.getChar());
		}
		
		@LangFunction("toChar")
		@AllowedTypes(DataObject.DataType.CHAR)
		public static DataObject toCharFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$asciiValue") @NumberValue Number asciiValue) {
			return new DataObject().setChar((char)asciiValue.intValue());
		}
		
		@LangFunction("ttoc")
		@AllowedTypes(DataObject.DataType.CHAR)
		public static DataObject ttocFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject) {
			String str = textObject.getText();
			if(str.length() == 1)
				return new DataObject().setChar(str.charAt(0));
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format("Argument 1 (\"%s\") must be of length 1", textObject.getVariableName()), SCOPE_ID);
		}
	}
	
	public static final class LangPredefinedTextFunctions {
		private LangPredefinedTextFunctions() {}
		
		@LangFunction("strlen")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject strlenFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setInt(textObject.getText().length());
		}
		
		@LangFunction("isEmpty")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isEmptyFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setBoolean(textObject.getText().isEmpty());
		}
		
		@LangFunction("isNotEmpty")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isNotEmptyFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setBoolean(!textObject.getText().isEmpty());
		}
		
		@LangFunction("isBlank")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isBlankFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setBoolean(textObject.getText().trim().isEmpty());
		}
		
		@LangFunction("isNotBlank")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject isNotBlankFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setBoolean(!textObject.getText().trim().isEmpty());
		}
		
		@LangFunction("toUpper")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject toUpperFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setText(textObject.getText().toUpperCase());
		}
		
		@LangFunction("toLower")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject toLowerFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setText(textObject.getText().toLowerCase());
		}
		
		@LangFunction("trim")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject trimFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			return new DataObject().setText(textObject.getText().trim());
		}
		
		@LangFunction("replace")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject replaceFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$regex") DataObject regexObject,
				@LangParameter("$replacement") @VarArgs DataObject replacementObject) {
			try {
				return new DataObject(LangRegEx.replace(textObject.getText(), regexObject.getText(), replacementObject.getText()));
			}catch(InvalidPaternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction(value="substring", hasInfo=true)
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject substringFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$startIndex") @NumberValue Number startIndex) {
			return substringFunction(interpreter, SCOPE_ID, textObject, startIndex, null);
		}
		@LangFunction("substring")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject substringFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$startIndex") @NumberValue Number startIndex,
				@LangParameter("$endIndex") @NumberValue Number endIndex) {
			try {
				if(endIndex == null)
					return new DataObject(textObject.getText().substring(startIndex.intValue()));
				
				return new DataObject(textObject.getText().substring(startIndex.intValue(), endIndex.intValue()));
			}catch(StringIndexOutOfBoundsException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			}
		}
		
		@LangFunction("charAt")
		@AllowedTypes(DataObject.DataType.CHAR)
		public static DataObject charAtFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$index") @NumberValue Number indexNumber) {
			String txt = textObject.getText();
			int len = txt.length();
			
			int index = indexNumber.intValue();
			if(index < 0)
				index += len;
			
			if(index < 0 || index >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setChar(txt.charAt(index));
		}
		
		@LangFunction("lpad")
		@LangInfo("Adds padding to the left of the $text value if needed")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject lpadFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$paddingText") DataObject paddingTextObject,
				@LangParameter("$len") @NumberValue Number lenNum) {
			int len = lenNum.intValue();
			
			String text = textObject.getText();
			String paddingText = paddingTextObject.getText();
			
			if(paddingText.length() == 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The padding text must not be empty", SCOPE_ID);
			
			if(text.length() >= len)
				return new DataObject(textObject);
			
			StringBuilder builder = new StringBuilder(text);
			while(builder.length() < len)
				builder.insert(0, paddingText);
			
			if(builder.length() > len)
				builder.delete(0, builder.length() - len);
			
			return new DataObject(builder.toString());
		}
		
		@LangFunction("rpad")
		@LangInfo("Adds padding to the right of the $text value if needed")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject rpadFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$paddingText") DataObject paddingTextObject,
				@LangParameter("$len") @NumberValue Number lenNum) {
			int len = lenNum.intValue();
			
			String text = textObject.getText();
			String paddingText = paddingTextObject.getText();
			
			if(paddingText.length() == 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The padding text must not be empty", SCOPE_ID);
			
			if(text.length() >= len)
				return new DataObject(textObject);
			
			StringBuilder builder = new StringBuilder(text);
			while(builder.length() < len)
				builder.append(paddingText);
			
			if(builder.length() >= len)
				builder.delete(len, builder.length());
			
			return new DataObject(builder.toString());
		}
		
		@LangFunction("format")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject formatFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$format") DataObject formatObject,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			return interpreter.formatText(formatObject.getText(), args, SCOPE_ID);
		}
		
		@LangFunction("formatTemplatePluralization")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject formatTemplatePluralizationFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$count") @NumberValue Number count,
				@LangParameter("$translationValue") @VarArgs DataObject translationValueObject) {
			if(count.intValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Count must be >= 0", SCOPE_ID);
			
			String translationValue = translationValueObject.getText();
			
			try {
				return new DataObject(LangUtils.formatTranslationTemplatePluralization(translationValue, count.intValue()));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_TEMPLATE_SYNTAX, "Invalid count range", SCOPE_ID);
			}catch(InvalidTranslationTemplateSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_TEMPLATE_SYNTAX, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("contains")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject containsFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$haystack") DataObject haystackObject,
				@LangParameter("$needle") DataObject needleObject) {
			return new DataObject().setBoolean(haystackObject.getText().contains(needleObject.getText()));
		}
		
		@LangFunction(value="indexOf", hasInfo=true)
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject indexOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$searchText") DataObject searchTextObject) {
			return new DataObject().setInt(textObject.getText().indexOf(searchTextObject.getText()));
		}
		@LangFunction("indexOf")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject indexOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$searchText") DataObject searchTextObject,
				@LangParameter("$fromIndex") @NumberValue Number fromIndexNumber) {
			String txt = textObject.getText();
			int len = txt.length();
			int fromIndex = fromIndexNumber.intValue();
			if(fromIndex < 0)
				fromIndex += len;
			
			if(fromIndex < 0 || fromIndex >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setInt(textObject.getText().indexOf(searchTextObject.getText(), fromIndex));
		}
		
		@LangFunction(value="lastIndexOf", hasInfo=true)
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject lastIndexOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$searchText") DataObject searchTextObject) {
			return new DataObject().setInt(textObject.getText().lastIndexOf(searchTextObject.getText()));
		}
		@LangFunction("lastIndexOf")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject lastIndexOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$searchText") DataObject searchTextObject,
				@LangParameter("$toIndex") @NumberValue Number toIndexNumber) {
			String txt = textObject.getText();
			int len = txt.length();
			int toIndex = toIndexNumber.intValue();
			if(toIndex < 0)
				toIndex += len;
			
			if(toIndex < 0 || toIndex >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setInt(textObject.getText().lastIndexOf(searchTextObject.getText(), toIndex));
		}
		
		@LangFunction("startsWith")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject startsWithFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$prefix") DataObject prefixObject) {
			return new DataObject().setBoolean(textObject.getText().startsWith(prefixObject.getText()));
		}
		
		@LangFunction("endsWith")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject endsWithFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$suffix") DataObject suffixObject) {
			return new DataObject().setBoolean(textObject.getText().endsWith(suffixObject.getText()));
		}
		
		@LangFunction("matches")
		@AllowedTypes(DataObject.DataType.INT)
		public static DataObject matchesFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$regex") DataObject regexObject) {
			try {
				return new DataObject().setBoolean(LangRegEx.matches(textObject.getText(), regexObject.getText()));
			}catch(InvalidPaternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("repeatText")
		@AllowedTypes(DataObject.DataType.TEXT)
		public static DataObject repeatTextFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$count") @NumberValue Number count,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			if(count.intValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Count must be >= 0", SCOPE_ID);
			
			String text = textObject.getText();
			
			StringBuilder builder = new StringBuilder();
			for(int i = 0;i < count.intValue();i++)
				builder.append(text);
			
			return new DataObject(builder.toString());
		}
		
		@LangFunction("charsOf")
		@AllowedTypes(DataObject.DataType.ARRAY)
		public static DataObject charsOfFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject) {
			String text = textObject.getText();
			char[] chars = text.toCharArray();
			DataObject[] arr = new DataObject[chars.length];
			
			for(int i = 0;i < chars.length;i++)
				arr[i] = new DataObject().setChar(chars[i]);
			
			return new DataObject().setArray(arr);
		}
		
		@LangFunction("join")
		@AllowedTypes(DataObject.DataType.ARRAY)
		public static DataObject joinFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") @VarArgs DataObject textObject,
				@LangParameter("&collection") @AllowedTypes({DataObject.DataType.ARRAY, DataObject.DataType.LIST}) DataObject collectionObject) {
			String text = textObject.getText();
			Stream<DataObject> dataObjectStream = collectionObject.getType() == DataType.ARRAY?Arrays.stream(collectionObject.getArray()):collectionObject.getList().stream();
			
			return new DataObject(dataObjectStream.map(DataObject::getText).collect(Collectors.joining(text)));
		}
		
		@LangFunction(value="split", hasInfo=true)
		@AllowedTypes(DataObject.DataType.ARRAY)
		public static DataObject splitFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$regex") DataObject regexObject) {
			return splitFunction(interpreter, SCOPE_ID, textObject, regexObject, null);
		}
		@LangFunction("split")
		@AllowedTypes(DataObject.DataType.ARRAY)
		public static DataObject splitFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$text") DataObject textObject,
				@LangParameter("$regex") DataObject regexObject,
				@LangParameter("$maxSplitCount") @NumberValue Number maxSplitCount) {
			String[] arrTmp;
			try {
				if(maxSplitCount == null) {
					arrTmp = LangRegEx.split(textObject.getText(), regexObject.getText());
				}else {
					arrTmp = LangRegEx.split(textObject.getText(), regexObject.getText(), maxSplitCount.intValue());
				}
			}catch(InvalidPaternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), SCOPE_ID);
			}
			
			DataObject[] arr = new DataObject[arrTmp.length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(arrTmp[i]);
			
			return new DataObject().setArray(arr);
		}
	}
	
	public static final class LangPredefinedCombinatorFunctions {
		private LangPredefinedCombinatorFunctions() {}
		
		@LangFunction("combA0")
		@CombinatorFunction
		@LangInfo("Combinator execution: a()")
		public static DataObject combA0Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combA")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b)")
		public static DataObject combAFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}
		
		@LangFunction("combA2")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c)")
		public static DataObject combA2Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combA3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c, d)")
		public static DataObject combA3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combA4")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c, d, e)")
		public static DataObject combA4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d, e
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combAE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a()")
		public static DataObject combAEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combAN")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(args[0], args[1], ...)")
		public static DataObject combANFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = LangUtils.separateArgumentsWithArgumentSeparators(args);
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combAV")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(args[0], args[1], ...)")
		public static DataObject combAVFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("&args") @AllowedTypes(DataObject.DataType.ARRAY) DataObject args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = LangUtils.separateArgumentsWithArgumentSeparators(Arrays.asList(args));
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combAX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, d, c)")
		public static DataObject combAXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, d, c
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combAZ")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(..., args[1], args[0])")
		public static DataObject combAZFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i >= 0;i--)
				argsA.add(args.get(i));
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combB0")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b())")
		public static DataObject combB0Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combB")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c))")
		public static DataObject combBFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combB2")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c), b(d))")
		public static DataObject combB2Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combB3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c), b(d), b(e))")
		public static DataObject combB3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			DataObject retB3 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					e
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2,
							retB3 == null?new DataObject().setVoid():retB3
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combBE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b())")
		public static DataObject combBEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combBN")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(args[0]), b(args[1]), ...)")
		public static DataObject combBNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 0;i < args.size();i++) {
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
						args.get(i)
				), SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combBV")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(args[0]), b(args[1]), ...)")
		public static DataObject combBVFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("&args") @AllowedTypes(DataObject.DataType.ARRAY) DataObject args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(DataObject ele:args.getArray()) {
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combBX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c, d))")
		public static DataObject combBXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combBZ")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(..., b(args[1]), b(args[0]))")
		public static DataObject combBZFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i >= 0;i--) {
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
						args.get(i)
				), SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combC0")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b)")
		public static DataObject combC0Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}
		
		@LangFunction("combC1")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c)")
		public static DataObject combC1Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
		}
		
		@LangFunction("combC")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c, b)")
		public static DataObject combCFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, b
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combC3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(d, c, b)")
		public static DataObject combC3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, b
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combC4")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(e, d, c, b)")
		public static DataObject combC4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							e, d, c, b
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combCE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a()")
		public static DataObject combCEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combCX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c, d, b)")
		public static DataObject combCXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d, b
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combD")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c(d))")
		public static DataObject combDFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combDE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c())")
		public static DataObject combDEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retC == null?new DataObject().setVoid():retC
			), SCOPE_ID);
		}
		
		@LangFunction("combE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c(d, e))")
		public static DataObject combEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, e
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combEE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c())")
		public static DataObject combEEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combEX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c, b(d, e))")
		public static DataObject combEXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, e
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combF1")
		@CombinatorFunction
		@LangInfo("Combinator execution: c(b)")
		public static DataObject combF1Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c) {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}
		
		@LangFunction("combF")
		@CombinatorFunction
		@LangInfo("Combinator execution: c(b, a)")
		public static DataObject combFFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c) {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combF3")
		@CombinatorFunction
		@LangInfo("Combinator execution: d(c, b, a)")
		public static DataObject combF3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject d) {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, b, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combF4")
		@CombinatorFunction
		@LangInfo("Combinator execution: e(d, c, b, a)")
		public static DataObject combF4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject e) {
			FunctionPointerObject eFunc = e.getFunctionPointer();
			
			return interpreter.callFunctionPointer(eFunc, e.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, b, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combFE")
		@CombinatorFunction
		@LangInfo("Combinator execution: c()")
		public static DataObject combFEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c) {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combG")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(d, b(c))")
		public static DataObject combGFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combGE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(d, b())")
		public static DataObject combGEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combGX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(d), c)")
		public static DataObject combGXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							c
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combH")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, c, b)")
		public static DataObject combHFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, b
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combHB")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c), b(d), b(c))")
		public static DataObject combHBFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			DataObject retB3 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retB2 == null?new DataObject().setVoid():retB2,
							retB3 == null?new DataObject().setVoid():retB3
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combHE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(), c(), b())")
		public static DataObject combHEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB1 == null?new DataObject().setVoid():retB1,
							retC == null?new DataObject().setVoid():retC,
							retB2 == null?new DataObject().setVoid():retB2
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combHX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c, b, c)")
		public static DataObject combHXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, b, c
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combI")
		@CombinatorFunction
		@LangInfo("Combinator execution: a")
		public static DataObject combIFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a) {
			return new DataObject(a);
		}
		
		@LangFunction("combJ")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, a(d, c))")
		public static DataObject combJFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retA2 == null?new DataObject().setVoid():retA2
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combJX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, a(c, d))")
		public static DataObject combJXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retA2 == null?new DataObject().setVoid():retA2
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combJE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b, a())")
		public static DataObject combJEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retA2 == null?new DataObject().setVoid():retA2
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combK")
		@CombinatorFunction
		@LangInfo("Combinator execution: a")
		public static DataObject combKFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b) {
			return new DataObject(a);
		}
		
		@LangFunction("combK3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a")
		public static DataObject combK3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			return new DataObject(a);
		}
		
		@LangFunction("combK4")
		@CombinatorFunction
		@LangInfo("Combinator execution: a")
		public static DataObject combK4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			return new DataObject(a);
		}
		
		@LangFunction("combK5")
		@CombinatorFunction
		@LangInfo("Combinator execution: a")
		public static DataObject combK5Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			return new DataObject(a);
		}
		
		@LangFunction("combKD")
		@CombinatorFunction
		@LangInfo("Combinator execution: d")
		public static DataObject combKDFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			return new DataObject(d);
		}
		
		@LangFunction("combKE")
		@CombinatorFunction
		@LangInfo("Combinator execution: e")
		public static DataObject combKEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			return new DataObject(e);
		}
		
		@LangFunction("combKI")
		@CombinatorFunction
		@LangInfo("Combinator execution: b")
		public static DataObject combKIFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b) {
			return new DataObject(b);
		}
		
		@LangFunction("combKX")
		@CombinatorFunction
		@LangInfo("Combinator execution: c")
		public static DataObject combKXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			return new DataObject(c);
		}
		
		@LangFunction("combL")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(b))")
		public static DataObject combLFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combL2")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(b, c))")
		public static DataObject combL2Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combL3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(b, c, d))")
		public static DataObject combL3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combL4")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(b, c, d, e))")
		public static DataObject combL4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d, e
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combM")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(a)")
		public static DataObject combMFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
		}
		
		@LangFunction("combM2")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(a, b)")
		public static DataObject combM2Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combM3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(a, b, c)")
		public static DataObject combM3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combM4")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(a, b, c, d)")
		public static DataObject combM4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c, d
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combM5")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(a, b, c, d, e)")
		public static DataObject combM5Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c, d, e
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combN0")
		@CombinatorFunction
		@LangInfo("Combinator execution: a()()")
		public static DataObject combN0Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a() must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combN1")
		@CombinatorFunction
		@LangInfo("Combinator execution: a()(c)")
		public static DataObject combN1Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a() must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
		}
		
		@LangFunction("combN")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b)(c)")
		public static DataObject combNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
		}
		
		@LangFunction("combN3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b)(c)(d)")
		public static DataObject combN3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b)(c) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
		}
		
		@LangFunction("combN4")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b)(c)(d)(e)")
		public static DataObject combN4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b)(c) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b)(c)(d) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					e
			), SCOPE_ID);
		}
		
		@LangFunction("combNE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a()()")
		public static DataObject combNEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a() must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combNN")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(args[0])(args[1])(...)")
		public static DataObject combNNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			DataObject ret = new DataObject(a);
			for(int i = 0;i < args.size();i++) {
				DataObject n = args.get(i);
				
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The return value after iteration " + (i + 1) + " must be of type " +
							DataType.FUNCTION_POINTER, SCOPE_ID);
				
				ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
						n
				), SCOPE_ID);
				ret = ret == null?new DataObject().setVoid():ret;
			}
			
			return ret;
		}
		
		@LangFunction("combNM")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(a)(a)")
		public static DataObject combNMFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(a) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
		}
		
		@LangFunction("combNV")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(args[0])(args[1])(...)")
		public static DataObject combNVFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("&args") @AllowedTypes(DataObject.DataType.ARRAY) DataObject args) {
			DataObject ret = new DataObject(a);
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The return value after iteration " + (i + 1) + " must be of type " +
							DataType.FUNCTION_POINTER, SCOPE_ID);
				
				ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
						n
				), SCOPE_ID);
				ret = ret == null?new DataObject().setVoid():ret;
			}
			
			return ret;
		}
		
		@LangFunction("combNW")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b)(b)")
		public static DataObject combNWFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}
		
		@LangFunction("combNX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c)(b)")
		public static DataObject combNXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a(b) must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}
		
		@LangFunction("combNZ")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(...)(args[1])(args[0])")
		public static DataObject combNZFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("&args") @VarArgs List<DataObject> args) {
			DataObject ret = new DataObject(a);
			for(int i = args.size() - 1;i >= 0;i--) {
				DataObject n = args.get(i);
				
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The return value after iteration " + (i + 1) + " must be of type " +
							DataType.FUNCTION_POINTER, SCOPE_ID);
				
				ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
						n
				), SCOPE_ID);
				ret = ret == null?new DataObject().setVoid():ret;
			}
			
			return ret;
		}
		
		@LangFunction("combO")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a(b))")
		public static DataObject combOFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}
		
		@LangFunction("combO2")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a(b, c))")
		public static DataObject combO2Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}
		
		@LangFunction("combO3")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a(b, c, d))")
		public static DataObject combO3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}
		
		@LangFunction("combO4")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a(b, c, d, e))")
		public static DataObject combO4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d, e
					)
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}
		
		@LangFunction("combP")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(d), c(d))")
		public static DataObject combPFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combP3")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(e), c(e), d(e))")
		public static DataObject combP3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					e
			), SCOPE_ID);
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					e
			), SCOPE_ID);
			
			DataObject retD = interpreter.callFunctionPointer(dFunc, d.getVariableName(), Arrays.asList(
					e
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							retC == null?new DataObject().setVoid():retC,
							retD == null?new DataObject().setVoid():retD
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combPE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(), c())")
		public static DataObject combPEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combPN")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(args[0](b), args[1](b), ...)")
		public static DataObject combPNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("&args") @AllowedTypes(DataType.FUNCTION_POINTER) @VarArgs List<DataObject> args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 0;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						b
				), SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combPV")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(args[0](b), args[1](b), ...)")
		public static DataObject combPVFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("&args") @AllowedTypes(DataObject.DataType.ARRAY) DataObject args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(n.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
							"Value at index " + i + " of Argument 3 (\"&args\") must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						b
				), SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combPX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c(d), b(d))")
		public static DataObject combPXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					d
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retC == null?new DataObject().setVoid():retC,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combPZ")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(..., args[1](b), args[0](b))")
		public static DataObject combPZFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") DataObject b,
				@LangParameter("&args") @AllowedTypes(DataType.FUNCTION_POINTER) @VarArgs List<DataObject> args) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i >= 0;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						b
				), SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}
		
		@LangFunction("combQ")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a(c))")
		public static DataObject combQFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}
		
		@LangFunction("combQE")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a())")
		public static DataObject combQEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}
		
		@LangFunction("combQN")
		@CombinatorFunction
		@LangInfo("Combinator execution: ...(args[1](args[0](a)))")
		public static DataObject combQNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("&args") @AllowedTypes(DataType.FUNCTION_POINTER) @VarArgs List<DataObject> args) {
			DataObject ret = new DataObject(a);
			for(int i = 0;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}
		
		@LangFunction("combQV")
		@CombinatorFunction
		@LangInfo("Combinator execution: ...(args[1](args[0](a)))")
		public static DataObject combQVFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("&args") @AllowedTypes(DataObject.DataType.ARRAY) DataObject args) {
			DataObject ret = new DataObject(a);
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(n.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
							"Value at index " + i + " of Argument 2 (\"&args\") must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}
		
		@LangFunction("combQX")
		@CombinatorFunction
		@LangInfo("Combinator execution: c(b(a))")
		public static DataObject combQXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject c) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}
		
		@LangFunction("combQZ")
		@CombinatorFunction
		@LangInfo("Combinator execution: args[0](args[1](...(a)))")
		public static DataObject combQZFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("&args") @AllowedTypes(DataType.FUNCTION_POINTER) @VarArgs List<DataObject> args) {
			DataObject ret = new DataObject(a);
			for(int i = args.size() - 1;i >= 0;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}
		
		@LangFunction("combR0")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a)")
		public static DataObject combR0Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
		}
		
		@LangFunction("combR1")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(c)")
		public static DataObject combR1Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
		}
		
		@LangFunction("combR")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(c, a)")
		public static DataObject combRFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combR3")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(d, c, a)")
		public static DataObject combR3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combR4")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(e, d, c, a)")
		public static DataObject combR4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							e, d, c, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combRE")
		@CombinatorFunction
		@LangInfo("Combinator execution: b()")
		public static DataObject combREFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combRX")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a, c)")
		public static DataObject combRXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, c
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combS")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c, b(c))")
		public static DataObject combSFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								c,
								retB == null?new DataObject().setVoid():retB
						)
			), SCOPE_ID);
		}
		
		@LangFunction("combSE")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(c, b())")
		public static DataObject combSEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								c,
								retB == null?new DataObject().setVoid():retB
						)
			), SCOPE_ID);
		}
		
		@LangFunction("combSX")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c), c)")
		public static DataObject combSXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								retB == null?new DataObject().setVoid():retB,
								c
						)
			), SCOPE_ID);
		}
		
		@LangFunction("combT")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a)")
		public static DataObject combTFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
		}
		
		@LangFunction("combT3")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a, c, d)")
		public static DataObject combT3Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, c, d
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combT4")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(a, c, d, e)")
		public static DataObject combT4Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d,
				@LangParameter("$e") DataObject e) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, c, d, e
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combTE")
		@CombinatorFunction
		@LangInfo("Combinator execution: b()")
		public static DataObject combTEFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}
		
		@LangFunction("combTN")
		@CombinatorFunction
		@LangInfo("Combinator execution: ...(args[1](args[0](z)))")
		public static DataObject combTNFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("&args") @AllowedTypes(DataType.FUNCTION_POINTER) @VarArgs List<DataObject> args,
				@LangParameter("$z") DataObject z) {
			DataObject ret = new DataObject(z);
			for(int i = 0;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}
		
		@LangFunction("combTV")
		@CombinatorFunction
		@LangInfo("Combinator execution: ...(args[1](args[0](z)))")
		public static DataObject combTVFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("&args") @AllowedTypes(DataObject.DataType.ARRAY) DataObject args,
				@LangParameter("$z") DataObject z) {
			DataObject ret = new DataObject(z);
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(n.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
							"Value at index " + i + " of Argument 2 (\"&args\") must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}
		
		@LangFunction("combTX")
		@CombinatorFunction
		@LangInfo("Combinator execution: b(d, c, a)")
		public static DataObject combTXFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, a
					)
			), SCOPE_ID);
		}
		
		@LangFunction("combTZ")
		@CombinatorFunction
		@LangInfo("Combinator execution: args[0](args[1](...(z)))")
		public static DataObject combTZFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("&args") @AllowedTypes(DataType.FUNCTION_POINTER) @VarArgs List<DataObject> args,
				@LangParameter("$z") DataObject z) {
			DataObject ret = new DataObject(z);
			for(int i = args.size() - 1;i >= 0;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}
		
		@LangFunction("combX1")
		@CombinatorFunction
		@LangInfo("Combinator execution: a(b(c), d)")
		public static DataObject combX1Function(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$a") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject a,
				@LangParameter("$b") @AllowedTypes(DataObject.DataType.FUNCTION_POINTER) DataObject b,
				@LangParameter("$c") DataObject c,
				@LangParameter("$d") DataObject d) {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							retB == null?new DataObject().setVoid():retB,
							d
					)
			), SCOPE_ID);
		}
	}
	
	public static final class LangPredefinedPairStructFunctions {
		private LangPredefinedPairStructFunctions() {}
		
		@LangFunction("pair")
		@AllowedTypes(DataObject.DataType.STRUCT)
		public static DataObject pairFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("$first") DataObject firstObject,
				@LangParameter("$second") DataObject secondObject) {
			try {
				return new DataObject().setStruct(LangCompositeTypes.createPair(firstObject, secondObject));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("pfirst")
		@LangInfo("Returns the first value of &pair")
		public static DataObject pfirstFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("&pair") @AllowedTypes(DataObject.DataType.STRUCT) DataObject pairStructObject) {
			StructObject pairStruct = pairStructObject.getStruct();
			
			if(pairStruct.isDefinition() || !pairStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_PAIR))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format("Argument 1 (\"%s\") must be of type \"&Pair\"",
						pairStructObject.getVariableName()), SCOPE_ID);
			
			try {
				return new DataObject(pairStruct.getMember("$first"));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		}
		
		@LangFunction("psecond")
		@LangInfo("Returns the second value of &pair")
		public static DataObject psecondFunction(LangInterpreter interpreter, int SCOPE_ID,
				@LangParameter("&pair") @AllowedTypes(DataObject.DataType.STRUCT) DataObject pairStructObject) {
			StructObject pairStruct = pairStructObject.getStruct();
			
			if(pairStruct.isDefinition() || !pairStruct.getStructBaseDefinition().equals(LangCompositeTypes.STRUCT_PAIR))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format("Argument 1 (\"%s\") must be of type \"&Pair\"",
						pairStructObject.getVariableName()), SCOPE_ID);
			
			try {
				return new DataObject(pairStruct.getMember("$second"));
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), SCOPE_ID);
			}
		}
	}
}