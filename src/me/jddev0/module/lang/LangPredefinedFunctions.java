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
import java.util.HashMap;
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
import me.jddev0.module.lang.DataObject.ErrorObject;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.VarPointerObject;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangInterpreter.StackElement;
import me.jddev0.module.lang.LangUtils.InvalidTranslationTemplateSyntaxException;
import me.jddev0.module.lang.regex.InvalidPaternSyntaxException;
import me.jddev0.module.lang.regex.LangRegEx;

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
	
	private String getArgumentListAsString(List<DataObject> argumentList, boolean returnEmptyStringForEmptyArgumentList) {
		DataObject dataObject = LangUtils.combineDataObjects(argumentList);
		if(dataObject == null)
			return returnEmptyStringForEmptyArgumentList?"":null;
		
		return dataObject.getText();
	}
	
	private DataObject throwErrorOnNullOrErrorTypeHelper(DataObject dataObject, final int SCOPE_ID) {
		if(dataObject == null)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		
		if(dataObject.getType() == DataType.ERROR)
			return interpreter.setErrnoErrorObject(dataObject.getError().getInterprettingError(), dataObject.getError().getMessage(), SCOPE_ID);
		
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
	private LangPredefinedFunctionObject combinatorFunctionExternalFunctionObjectHelper(int argumentCount, int[] functionPointerIndices,
	BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc) {
		return (argumentList, SCOPE_ID) -> {
			return combinatorFunctionHelper(argumentList, argumentCount, functionPointerIndices, combinatorFunc, SCOPE_ID);
		};
	}
	/**
	 * @param argumentList separated arguments with ARGUMENT_SEPARATORs
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private DataObject combinatorFunctionHelper(List<DataObject> argumentList, int argumentCount, int[] functionPointerIndices, BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc,
			final int SCOPE_ID) {
		return combinatorFunctionRecursionHelper(LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList), argumentCount, functionPointerIndices, combinatorFunc, SCOPE_ID);
	}
	/**
	 * @param outerArgs Combined arguments without ARGUMENT_SEPARATORs
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private DataObject combinatorFunctionRecursionHelper(List<DataObject> outerArgs, int argumentCount, int[] functionPointerIndices,
			BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc, final int SCOPE_ID) {
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
				return combinatorFunctionRecursionHelper(args, argumentCount, functionPointerIndices, combinatorFunc, INNER_SCOPE_ID);
			
			for(int i:functionPointerIndices) {
				if(args.size() > i && args.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
			}
			
			return combinatorFunc.apply(args, INNER_SCOPE_ID);
		};
		if(argumentCount > outerArgs.size())
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		else
			return func.callFunc(interpreter, new ArrayList<>(), SCOPE_ID);
	}
	/**
	 * @param minimalArgumentsAtEnd If true the last arguments will be used for minimalArgumentCount and functionPointerIndices
	 *        checks and the funcArgs check will check all arguments previous to the minimal
	 * @param funcArgs If true every argument with an index >= minimalArgumentCount must be of type FUNCTION_POINTER
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private LangPredefinedFunctionObject combinatorFunctionInfiniteExternalFunctionObjectHelper(int minimalArgumentCount, int[] functionPointerIndices, boolean minimalArgumentsAtEnd,
	boolean funcArgs, BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc) {
		return (argumentList, SCOPE_ID) -> {
			return combinatorFunctionInfiniteHelper(argumentList, minimalArgumentCount, functionPointerIndices, minimalArgumentsAtEnd, funcArgs, combinatorFunc, SCOPE_ID);
		};
	}
	/**
	 * @param minimalArgumentsAtEnd If true the last arguments will be used for minimalArgumentCount and functionPointerIndices
	 *        checks and the funcArgs check will check all arguments previous to the minimal
	 * @param funcArgs If true every argument with an index >= minimalArgumentCount must be of type FUNCTION_POINTER
	 * @param argumentList separated arguments with ARGUMENT_SEPARATORs
	 * @param combinatorFunc Will be called with combined arguments without ARGUMENT_SEPARATORs
	 */
	private DataObject combinatorFunctionInfiniteHelper(List<DataObject> argumentList, int minimalArgumentCount, int[] functionPointerIndices, boolean minimalArgumentsAtEnd,
	boolean funcArgs, BiFunction<List<DataObject>, Integer, DataObject> combinatorFunc, final int SCOPE_ID) {
		List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		
		if(!minimalArgumentsAtEnd) {
			for(int i:functionPointerIndices) {
				if(outerArgs.size() > i && outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
			}
		}
		
		if(funcArgs) {
			if(minimalArgumentsAtEnd) {
				for(int i = 0;i < outerArgs.size() - minimalArgumentCount;i++) {
					if(outerArgs.size() > i && outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
				}
			}else {
				for(int i = minimalArgumentCount;i < outerArgs.size();i++) {
					if(outerArgs.size() > i && outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
				}
			}
		}
		
		final int minArgsLeft = minimalArgumentCount - outerArgs.size();
		
		List<DataObject> outerArgsCopy = outerArgs.stream().map(DataObject::new).collect(Collectors.toList());
		
		LangExternalFunctionObject func = (LangExternalFunctionObject)(interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
			List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
			innerArgs = innerArgs.stream().map(DataObject::new).collect(Collectors.toList());
			if(innerArgs.size() < minArgsLeft)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minimalArgumentCount), INNER_SCOPE_ID);
			
			List<DataObject> args = new LinkedList<>();
			args.addAll(outerArgsCopy);
			args.addAll(innerArgs);
			
			for(int i:functionPointerIndices) {
				if(minimalArgumentsAtEnd)
					i = args.size() - i - minimalArgumentCount;
				
				if(args.size() > i && args.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
			}
			
			if(funcArgs) {
				if(minimalArgumentsAtEnd) {
					for(int i = 0;i < args.size() - minimalArgumentCount;i++) {
						if(args.size() > i && args.get(i).getType() != DataType.FUNCTION_POINTER)
							return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
					}
				}else {
					for(int i = minimalArgumentCount;i < args.size();i++) {
						if(args.size() > i && args.get(i).getType() != DataType.FUNCTION_POINTER)
							return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", DataType.FUNCTION_POINTER), SCOPE_ID);
					}
				}
			}
			
			return combinatorFunc.apply(args, INNER_SCOPE_ID);
		};
		return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
	}
	
	public void addPredefinedFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		addPredefinedResetFunctions(funcs);
		addPredefinedErrorFunctions(funcs);
		addPredefinedLangFunctions(funcs);
		addPredefinedSystemFunctions(funcs);
		addPredefinedIOFunctions(funcs);
		addPredefinedNumberFunctions(funcs);
		addPredefinedCharacterFunctions(funcs);
		addPredefinedTextFunctions(funcs);
		addPredefinedConversionFunctions(funcs);
		addPredefinedOperationFunctions(funcs);
		addPredefinedMathFunctions(funcs);
		addPredefinedCombinatorFunctions(funcs);
		addPredefinedFuncPtrFunctions(funcs);
		addPredefinedArrayFunctions(funcs);
		addPredefinedListFunctions(funcs);
		addPredefinedModuleFunctions(funcs);
		addPredefinedLangTestFunctions(funcs);
	}
	private void addPredefinedResetFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("clearVar", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				DataObject error;
				if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
					return error;
				
				DataObject pointerObject = combinedArgumentList.get(0);
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
					case LIST:
					case NULL:
					case TEXT:
					case VOID:
					case TYPE:
						break;
				}
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
				return "func.freeVar";
			}
		});
		funcs.put("clearAllVars", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				DataObject error;
				if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
					return error;
				
				interpreter.resetVars(SCOPE_ID);
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
				return "func.freeAllVars";
			}
		});
		funcs.put("freeVar", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject pointerObject = combinedArgumentList.get(0);
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
				case LIST:
				case NULL:
				case TEXT:
				case VOID:
				case TYPE:
					break;
			}
			if(dereferencedVarPointer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			String variableName = dereferencedVarPointer.getVariableName();
			if(variableName == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			if(dereferencedVarPointer.isFinalData() || dereferencedVarPointer.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			interpreter.data.get(SCOPE_ID).var.remove(variableName);
			
			return null;
		});
		funcs.put("freeAllVars", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			interpreter.resetVars(SCOPE_ID);
			return null;
		});
		funcs.put("clearAllArrays", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				DataObject error;
				if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
					return error;
				
				new HashSet<>(interpreter.data.get(SCOPE_ID).var.entrySet()).forEach(entry -> {
					if(entry.getValue().getType() == DataType.ARRAY && !entry.getValue().isLangVar())
						interpreter.data.get(SCOPE_ID).var.remove(entry.getKey());
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
				return "func.freeAllVars";
			}
		});
	}
	private void addPredefinedErrorFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("getErrorString", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				DataObject error;
				if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
					return error;
				
				return new DataObject().setText(interpreter.getAndClearErrnoErrorObject(SCOPE_ID).getErrorText());
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
		funcs.put("getErrorText", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			return new DataObject(interpreter.getAndClearErrnoErrorObject(SCOPE_ID).getErrorText());
		});
		funcs.put("errorText", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.ERROR), SCOPE_ID)) != null)
				return error;
			
			DataObject errorObject = combinedArgumentList.get(0);
			return new DataObject(errorObject.getError().getErrtxt());
		});
		funcs.put("errorCode", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.ERROR), SCOPE_ID)) != null)
				return error;
			
			DataObject errorObject = combinedArgumentList.get(0);
			return new DataObject().setInt(errorObject.getError().getErrno());
		});
		funcs.put("errorMessage", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.ERROR), SCOPE_ID)) != null)
				return error;
			
			DataObject errorObject = combinedArgumentList.get(0);
			String msg = errorObject.getError().getMessage();
			return msg == null?new DataObject().setNull():new DataObject().setText(msg);
		});
		funcs.put("withErrorMessage", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject errorObject = combinedArgumentList.get(0);
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.ERROR), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(1);
			
			return new DataObject().setError(new ErrorObject(errorObject.getError().getInterprettingError(), textObject.getText()));
		});
	}
	private void addPredefinedLangFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("isLangVersionNewer", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			String langVer = interpreter.data.get(SCOPE_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			Integer compVer = LangUtils.compareVersions(LangInterpreter.VERSION, langVer);
			if(compVer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LANG_VER_ERROR, "lang.version has an invalid format", SCOPE_ID);
			return new DataObject().setBoolean(compVer > 0);
		});
		funcs.put("isLangVersionOlder", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			String langVer = interpreter.data.get(SCOPE_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			Integer compVer = LangUtils.compareVersions(LangInterpreter.VERSION, langVer);
			if(compVer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LANG_VER_ERROR, "lang.version has an invalid format", SCOPE_ID);
			return new DataObject().setBoolean(compVer < 0);
		});
	}
	private void addPredefinedSystemFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("sleep", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			Number number = dataObject.toNumber();
			if(number == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			try {
				Thread.sleep(number.longValue());
			}catch(InterruptedException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("nanoTime", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			return new DataObject().setLong(System.nanoTime());
		});
		funcs.put("currentTimeMillis", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			return new DataObject().setLong(System.currentTimeMillis());
		});
		funcs.put("currentUnixTime", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			return new DataObject().setLong(Instant.now().getEpochSecond());
		});
		funcs.put("repeat", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject loopFunctionObject = combinedArgumentList.get(0);
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Loop function pointer is invalid", SCOPE_ID);
			
			DataObject repeatCountObject = combinedArgumentList.get(1);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			
			Number repeatCountNumber = repeatCountObject.toNumber();
			if(repeatCountNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			long repeatCount = repeatCountNumber.longValue();
			if(repeatCount < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_REPEAT_COUNT, SCOPE_ID);
			
			for(int i = 0;i < repeatCount;i++) {
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), Arrays.asList(
						new DataObject().setInt(i)
				), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("repeatWhile", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject loopFunctionObject = combinedArgumentList.get(0);
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Loop function pointer is invalid", SCOPE_ID);
			
			DataObject checkFunctionObject = combinedArgumentList.get(1);
			if(checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Check function pointer is invalid", SCOPE_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean())
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID);
			
			return null;
		});
		funcs.put("repeatUntil", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject loopFunctionObject = combinedArgumentList.get(0);
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Loop function pointer is invalid", SCOPE_ID);
			
			DataObject checkFunctionObject = combinedArgumentList.get(1);
			if(checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Check function pointer is invalid", SCOPE_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(!interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean())
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID);
			
			return null;
		});
		funcs.put("getLangRequest", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, int SCOPE_ID) {
				DataObject langRequestObject = LangUtils.combineDataObjects(argumentList);
				if(langRequestObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
				
				String langValue = interpreter.data.get(SCOPE_ID).lang.get(langRequestObject.getText());
				if(langValue == null)
					return interpreter.setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
				
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
		funcs.put("getTranslationValue", (argumentList, SCOPE_ID) -> {
			DataObject translationKeyObject = LangUtils.combineDataObjects(argumentList);
			if(translationKeyObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			String translationValue = interpreter.data.get(SCOPE_ID).lang.get(translationKeyObject.getText());
			if(translationValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
			
			return new DataObject(translationValue);
		});
		funcs.put("getTranslationValueTemplatePluralization", (argumentList, SCOPE_ID) -> {
			if(LangUtils.countDataObjects(argumentList) < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject countObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject translationKeyObject = LangUtils.combineDataObjects(argumentList);
			
			Number count = countObject.toNumber();
			if(count == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			if(count.intValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Count must be >= 0", SCOPE_ID);
			
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
		});
		funcs.put("makeFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			if(dataObject.getVariableName() == null && dataObject.getType() == DataType.VAR_POINTER) {
				dataObject = dataObject.getVarPointer().getVar();
				
				if(dataObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
			
			if(dataObject.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			dataObject.setFinalData(true);
			
			return null;
		});
		funcs.put("asFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject(dataObject).setCopyStaticAndFinalModifiers(true).setFinalData(true);
		});
		funcs.put("isFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject().setBoolean(dataObject.isFinalData());
		});
		funcs.put("makeStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			if(dataObject.getVariableName() == null && dataObject.getType() == DataType.VAR_POINTER) {
				dataObject = dataObject.getVarPointer().getVar();
				
				if(dataObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
			
			if(dataObject.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			dataObject.setStaticData(true);
			
			return null;
		});
		funcs.put("asStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject(dataObject).setCopyStaticAndFinalModifiers(true).setStaticData(true);
		});
		funcs.put("isStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject().setBoolean(dataObject.isStaticData());
		});
		funcs.put("constrainVariableAllowedTypes", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.remove(0);
			
			if(dataObject.getType() != DataType.VAR_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.VAR_POINTER), SCOPE_ID);
			
			dataObject = dataObject.getVarPointer().getVar();
			
			if(dataObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			if(dataObject.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			List<DataType> types = new LinkedList<>();
			
			int argumentIndex = 1;
			for(DataObject typeObject:combinedArgumentList) {
				argumentIndex++;
				
				if(typeObject.getType() != DataType.TYPE)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, argumentIndex + " ", DataType.TYPE), SCOPE_ID);
				
				types.add(typeObject.getTypeValue());
			}
			
			try {
				dataObject.setTypeConstraint(DataObject.DataTypeConstraint.fromAllowedTypes(types));
			}catch(DataObject.DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("constrainVariableNotAllowedTypes", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.remove(0);
			
			if(dataObject.getType() != DataType.VAR_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.VAR_POINTER), SCOPE_ID);
			
			dataObject = dataObject.getVarPointer().getVar();
			
			if(dataObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			if(dataObject.isLangVar())
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			List<DataType> types = new LinkedList<>();
			
			int argumentIndex = 1;
			for(DataObject typeObject:combinedArgumentList) {
				argumentIndex++;
				
				if(typeObject.getType() != DataType.TYPE)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, argumentIndex + " ", DataType.TYPE), SCOPE_ID);
				
				types.add(typeObject.getTypeValue());
			}
			
			try {
				dataObject.setTypeConstraint(DataObject.DataTypeConstraint.fromNotAllowedTypes(types));
			}catch(DataObject.DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("pointerTo", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject().setVarPointer(new VarPointerObject(dataObject));
		});
		funcs.put("condition", new LangPredefinedFunctionObject() {
			public DataObject callFunc(List<DataObject> argumentList, int SCOPE_ID) {
				DataObject condition = LangUtils.combineDataObjects(argumentList);
				if(condition == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
				
				try {
					return new DataObject().setBoolean(interpreter.interpretCondition(interpreter.parser.parseCondition(condition.getText()), SCOPE_ID));
				}catch(IOException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
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
				return "parser.con";
			}
		});
		funcs.put("exec", (argumentList, SCOPE_ID) -> {
			DataObject text = LangUtils.combineDataObjects(argumentList);
			if(text == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			final int NEW_SCOPE_ID = SCOPE_ID + 1;
			
			//Update call stack
			StackElement currentStackElement = interpreter.getCurrentCallStackElement();
			interpreter.pushStackElement(new StackElement(currentStackElement.getLangPath(), currentStackElement.getLangFile(), "func.exec", currentStackElement.getModule()));
			
			try(BufferedReader lines = new BufferedReader(new StringReader(text.getText()))) {
				//Add variables and local variables
				interpreter.createDataMap(NEW_SCOPE_ID);
				//Copies must not be final
				interpreter.data.get(SCOPE_ID).var.forEach((key, val) -> {
					if(!val.isLangVar())
						interpreter.data.get(NEW_SCOPE_ID).var.put(key, new DataObject(val).setVariableName(val.getVariableName()));
					
					if(val.isStaticData()) //Static lang vars should also be copied
						interpreter.data.get(NEW_SCOPE_ID).var.put(key, val);
				});
				//Initialize copyAfterFP
				interpreter.copyAfterFP.put(NEW_SCOPE_ID, new HashMap<String, String>());
				interpreter.interpretLines(lines, NEW_SCOPE_ID);
			}catch(IOException e) {
				//Remove data map
				interpreter.data.remove(NEW_SCOPE_ID);
				
				//Clear copyAfterFP
				interpreter.copyAfterFP.remove(NEW_SCOPE_ID);
				
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, e.getMessage(), SCOPE_ID);
			}finally {
				//Update call stack
				interpreter.popStackElement();
			}
			
			//Add lang after call
			interpreter.data.get(SCOPE_ID).lang.putAll(interpreter.data.get(NEW_SCOPE_ID).lang);
			
			interpreter.executeAndClearCopyAfterFP(SCOPE_ID, NEW_SCOPE_ID);
			
			//Remove data map
			interpreter.data.remove(NEW_SCOPE_ID);
			
			return interpreter.getAndResetReturnValue(SCOPE_ID);
		});
		funcs.put("isTerminalAvailable", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 0, SCOPE_ID)) != null)
				return error;
			
			return new DataObject().setBoolean(interpreter.term != null);
		});
		funcs.put("isInstanceOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			DataObject dataTypeObject = combinedArgumentList.get(1);
			if(dataTypeObject.getType() != DataType.TYPE)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.TYPE), SCOPE_ID);
			
			return new DataObject().setBoolean(dataObject.getType() == dataTypeObject.getTypeValue());
		});
		funcs.put("typeOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject().setTypeValue(dataObject.getType());
		});
	}
	private void addPredefinedIOFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("readTerminal", (argumentList, SCOPE_ID) -> {
			if(interpreter.term == null && !interpreter.executionFlags.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, SCOPE_ID);
			
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			String message = messageObject == null?"":messageObject.getText();
			
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
		});
		funcs.put("printTerminal", (argumentList, SCOPE_ID) -> {
			if(interpreter.term == null && !interpreter.executionFlags.allowTermRedirect)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_TERMINAL, SCOPE_ID);
			
			if(LangUtils.countDataObjects(argumentList) < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject logLevelObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			if(messageObject == null)
				messageObject = new DataObject().setVoid();
			
			Number logLevelNumber = logLevelObject.toNumber();
			if(logLevelNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
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
			
			if(interpreter.term == null) {
				interpreter.setErrno(InterpretingError.NO_TERMINAL_WARNING, SCOPE_ID);
				
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
		funcs.put("printError", (argumentList, SCOPE_ID) -> {
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
		funcs.put("input", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Number maxCount = null;
			if(combinedArgumentList.size() > 0) {
				DataObject numberObject = combinedArgumentList.get(0);
				maxCount = numberObject.toNumber();
				if(maxCount == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			}
			
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				if(maxCount == null) {
					String line = reader.readLine();
					if(line == null)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
					return new DataObject(line);
				}else {
					char[] buf = new char[maxCount.intValue()];
					int count = reader.read(buf);
					if(count == -1)
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, SCOPE_ID);
					return new DataObject(new String(buf));
				}
			}catch(IOException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("print", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			System.out.print(textObject.getText());
			return null;
		});
		funcs.put("println", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				System.out.println();
			else
				System.out.println(textObject.getText());
			return null;
		});
		funcs.put("printf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject formatObject = combinedArgumentList.remove(0);
			DataObject out = interpreter.formatText(formatObject.getText(), combinedArgumentList, SCOPE_ID);
			if(out.getType() == DataType.ERROR)
				return out;
			
			System.out.print(out.getText());
			return null;
		});
		funcs.put("error", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			System.err.print(textObject.getText());
			return null;
		});
		funcs.put("errorln", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				System.err.println();
			else
				System.err.println(textObject.getText());
			return null;
		});
		funcs.put("errorf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject formatObject = combinedArgumentList.remove(0);
			DataObject out = interpreter.formatText(formatObject.getText(), combinedArgumentList, SCOPE_ID);
			if(out.getType() == DataType.ERROR)
				return out;
			
			System.err.print(out.getText());
			return null;
		});
	}
	private void addPredefinedNumberFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("binToDec", (argumentList, SCOPE_ID) -> {
			DataObject binObject = LangUtils.combineDataObjects(argumentList);
			if(binObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			String binString = binObject.getText();
			if(!binString.startsWith("0b") && !binString.startsWith("0B"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_BIN_NUM, "Wrong prefix (Should be 0b or 0B)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(binString.substring(2), 2));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_BIN_NUM, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("octToDec", (argumentList, SCOPE_ID) -> {
			DataObject octObject = LangUtils.combineDataObjects(argumentList);
			if(octObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			String octString = octObject.getText();
			if(!octString.startsWith("0o") && !octString.startsWith("0O"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_OCT_NUM, "Wrong prefix (Should be 0o or 0O)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(octString.substring(2), 8));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_OCT_NUM, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("hexToDez", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				DataObject hexObject = LangUtils.combineDataObjects(argumentList);
				if(hexObject == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
				
				String hexString = hexObject.getText();
				if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
					return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, "Wrong prefix (Should be 0x or 0X)", SCOPE_ID);
				
				try {
					return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
				}catch(NumberFormatException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, e.getMessage(), SCOPE_ID);
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
		funcs.put("hexToDec", (argumentList, SCOPE_ID) -> {
			DataObject hexObject = LangUtils.combineDataObjects(argumentList);
			if(hexObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			String hexString = hexObject.getText();
			if(!hexString.startsWith("0x") && !hexString.startsWith("0X"))
				return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, "Wrong prefix (Should be 0x or 0X)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(hexString.substring(2), 16));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.NO_HEX_NUM, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("toNumberBase", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject numberObject = combinedArgumentList.get(0);
			DataObject baseObject = combinedArgumentList.get(1);
			
			String numberString = numberObject.getText();
			Number base = baseObject.toNumber();
			if(base == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_NUMBER_BASE, "Base must be a number", SCOPE_ID);
			
			if(base.intValue() < 2 || base.intValue() > 36)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_NUMBER_BASE, "Base must be between 2 (inclusive) and 36 (inclusive)", SCOPE_ID);
			
			try {
				return new DataObject().setInt(Integer.parseInt(numberString, base.intValue()));
			}catch(NumberFormatException e1) {
				try {
					return new DataObject().setLong(Long.parseLong(numberString, base.intValue()));
				}catch(NumberFormatException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.NO_BASE_N_NUM, "The text \"" + numberString + "\" is not in base \"" + base.intValue() + "\"", SCOPE_ID);
				}
			}
		});
		funcs.put("toTextBase", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject numberObject = combinedArgumentList.get(0);
			DataObject baseObject = combinedArgumentList.get(1);
			
			Number number = numberObject.toNumber();
			Number base = baseObject.toNumber();
			if(number == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, "Number must be a number", SCOPE_ID);
			
			if(base == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_NUMBER_BASE, "Base must be a number", SCOPE_ID);
			
			if(base.intValue() < 2 || base.intValue() > 36)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_NUMBER_BASE, "Base must be between 2 (inclusive) and 36 (inclusive)", SCOPE_ID);
			
			int numberInt = number.intValue();
			long numberLong = number.longValue();
			try {
				if(numberLong < 0?(numberLong < numberInt):(numberLong > numberInt))
					return new DataObject().setText(Long.toString(number.longValue(), base.intValue()).toUpperCase());
				else
					return new DataObject().setText(Integer.toString(number.intValue(), base.intValue()).toUpperCase());
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("toIntBits", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(Float.floatToRawIntBits(number.floatValue()));
			}, SCOPE_ID);
		});
		funcs.put("toFloatBits", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(Float.intBitsToFloat(number.intValue()));
			}, SCOPE_ID);
		});
		funcs.put("toLongBits", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(Double.doubleToRawLongBits(number.doubleValue()));
			}, SCOPE_ID);
		});
		funcs.put("toDoubleBits", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(Double.longBitsToDouble(number.longValue()));
			}, SCOPE_ID);
		});
		funcs.put("toInt", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setInt(number.intValue());
			}, SCOPE_ID);
		});
		funcs.put("toLong", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setLong(number.longValue());
			}, SCOPE_ID);
		});
		funcs.put("toFloat", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setFloat(number.floatValue());
			}, SCOPE_ID);
		});
		funcs.put("toDouble", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setDouble(number.doubleValue());
			}, SCOPE_ID);
		});
		funcs.put("toNumber", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			dataObject = dataObject.convertToNumberAndCreateNewDataObject();
			if(dataObject.getType() == DataType.NULL)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			return dataObject;
		});
		funcs.put("ttoi", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			try {
				return new DataObject().setInt(Integer.parseInt(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("ttol", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			try {
				return new DataObject().setLong(Long.parseLong(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("ttof", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			if(LangPatterns.matches(str, LangPatterns.PARSING_INVALID_FLOATING_POINT_NUMBER_ALLOW_NaN_INFINITY_OR_LEADING_OR_TRAILING_WHITESPACES))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			try {
				return new DataObject().setFloat(Float.parseFloat(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("ttod", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			if(LangPatterns.matches(str, LangPatterns.PARSING_INVALID_FLOATING_POINT_NUMBER_ALLOW_NaN_INFINITY_OR_LEADING_OR_TRAILING_WHITESPACES))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			try {
				return new DataObject().setDouble(Double.parseDouble(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("isNaN", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				if(number instanceof Float) {
					return new DataObject().setBoolean(Float.isNaN(number.floatValue()));
				}
				
				if(number instanceof Double) {
					return new DataObject().setBoolean(Double.isNaN(number.doubleValue()));
				}
				
				return new DataObject().setBoolean(false);
			}, SCOPE_ID);
		});
		funcs.put("isInfinite", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				if(number instanceof Float) {
					return new DataObject().setBoolean(Float.isInfinite(number.floatValue()));
				}
				
				if(number instanceof Double) {
					return new DataObject().setBoolean(Double.isInfinite(number.doubleValue()));
				}
				
				return new DataObject().setBoolean(false);
			}, SCOPE_ID);
		});
		funcs.put("isFinite", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				if(number instanceof Float) {
					return new DataObject().setBoolean(Float.isFinite(number.floatValue()));
				}
				
				if(number instanceof Double) {
					return new DataObject().setBoolean(Double.isFinite(number.doubleValue()));
				}
				
				return new DataObject().setBoolean(true);
			}, SCOPE_ID);
		});
		funcs.put("isEven", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setBoolean(number.longValue() % 2 == 0);
			}, SCOPE_ID);
		});
		funcs.put("isOdd", (argumentList, SCOPE_ID) -> {
			return unaryMathOperationHelper(argumentList, number -> {
				return new DataObject().setBoolean(number.longValue() % 2 == 1);
			}, SCOPE_ID);
		});
	}
	private void addPredefinedCharacterFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("toValue", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject charObject = combinedArgumentList.get(0);
			
			if(charObject.getType() != DataType.CHAR)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_CHAR, SCOPE_ID);
			
			return new DataObject().setInt(charObject.getChar());
		});
		funcs.put("toChar", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject asciiValueObject = combinedArgumentList.get(0);
			
			Number asciiValue = asciiValueObject.toNumber();
			if(asciiValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			return new DataObject().setChar((char)asciiValue.intValue());
		});
		funcs.put("ttoc", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			if(str.length() == 1)
				return new DataObject().setChar(str.charAt(0));
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		});
	}
	private void addPredefinedTextFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("strlen", (argumentList, SCOPE_ID) -> new DataObject().setInt(getArgumentListAsString(argumentList, true).length()));
		funcs.put("isEmpty", (argumentList, SCOPE_ID) -> new DataObject().setBoolean(getArgumentListAsString(argumentList, true).isEmpty()));
		funcs.put("isNotEmpty", (argumentList, SCOPE_ID) -> new DataObject().setBoolean(!getArgumentListAsString(argumentList, true).isEmpty()));
		funcs.put("isBlank", (argumentList, SCOPE_ID) -> new DataObject().setBoolean(getArgumentListAsString(argumentList, true).trim().isEmpty()));
		funcs.put("isNotBlank", (argumentList, SCOPE_ID) -> new DataObject().setBoolean(!getArgumentListAsString(argumentList, true).trim().isEmpty()));
		funcs.put("toUpper", (argumentList, SCOPE_ID) -> new DataObject(getArgumentListAsString(argumentList, true).toUpperCase()));
		funcs.put("toLower", (argumentList, SCOPE_ID) -> new DataObject(getArgumentListAsString(argumentList, true).toLowerCase()));
		funcs.put("trim", (argumentList, SCOPE_ID) -> new DataObject(getArgumentListAsString(argumentList, true).trim()));
		funcs.put("replace", (argumentList, SCOPE_ID) -> {
			if(LangUtils.countDataObjects(argumentList) < 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject regexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject replacement = LangUtils.combineDataObjects(argumentList);
			if(replacement == null)
				replacement = new DataObject().setVoid();
			
			try {
				return new DataObject(LangRegEx.replace(textObject.getText(), regexObject.getText(), replacement.getText()));
			}catch(InvalidPaternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("substring", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject startIndexObject = combinedArgumentList.get(1);
			DataObject endIndexObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			Number startIndex = startIndexObject.toNumber();
			if(startIndex == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, "startIndex is no number", SCOPE_ID);
			
			try {
				if(endIndexObject == null) {
					return new DataObject(textObject.getText().substring(startIndex.intValue()));
				}else {
					Number endIndex = endIndexObject.toNumber();
					if(endIndex == null)
						return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, "endIndex is no number", SCOPE_ID);
					
					return new DataObject(textObject.getText().substring(startIndex.intValue(), endIndex.intValue()));
				}
			}catch(StringIndexOutOfBoundsException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			}
		});
		funcs.put("charAt", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject indexObject = combinedArgumentList.get(1);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			String txt = textObject.getText();
			int len = txt.length();
			int index = indexNumber.intValue();
			if(index < 0)
				index += len;
			
			if(index < 0 || index >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setChar(txt.charAt(index));
		});
		funcs.put("lpad", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject paddingTextObject = combinedArgumentList.get(1);
			DataObject lenObject = combinedArgumentList.get(2);
			Number lenNum = lenObject.toNumber();
			if(lenNum == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
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
		});
		funcs.put("rpad", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject paddingTextObject = combinedArgumentList.get(1);
			DataObject lenObject = combinedArgumentList.get(2);
			Number lenNum = lenObject.toNumber();
			if(lenNum == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
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
		});
		funcs.put("format", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject formatObject = combinedArgumentList.remove(0);
			return interpreter.formatText(formatObject.getText(), combinedArgumentList, SCOPE_ID);
		});
		funcs.put("formatTranslationTemplatePluralization", (argumentList, SCOPE_ID) -> {
			if(LangUtils.countDataObjects(argumentList) < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject countObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject translationValueObject = LangUtils.combineDataObjects(argumentList);
			
			Number count = countObject.toNumber();
			if(count == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
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
		});
		funcs.put("contains", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject containTextObject = combinedArgumentList.get(1);
			return new DataObject().setBoolean(textObject.getText().contains(containTextObject.getText()));
		});
		funcs.put("indexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject searchTextObject = combinedArgumentList.get(1);
			DataObject fromIndexObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			if(fromIndexObject == null)
				return new DataObject().setInt(textObject.getText().indexOf(searchTextObject.getText()));
			
			Number fromIndexNumber = fromIndexObject.toNumber();
			if(fromIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			String txt = textObject.getText();
			int len = txt.length();
			int fromIndex = fromIndexNumber.intValue();
			if(fromIndex < 0)
				fromIndex += len;
			
			if(fromIndex < 0 || fromIndex >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setInt(textObject.getText().indexOf(searchTextObject.getText(), fromIndex));
		});
		funcs.put("lastIndexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject searchTextObject = combinedArgumentList.get(1);
			DataObject toIndexObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			if(toIndexObject == null)
				return new DataObject().setInt(textObject.getText().lastIndexOf(searchTextObject.getText()));
			
			Number toIndexNumber = toIndexObject.toNumber();
			if(toIndexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			String txt = textObject.getText();
			int len = txt.length();
			int toIndex = toIndexNumber.intValue();
			if(toIndex < 0)
				toIndex += len;
			
			if(toIndex < 0 || toIndex >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setInt(textObject.getText().lastIndexOf(searchTextObject.getText(), toIndex));
		});
		funcs.put("startsWith", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject startsWithTextObject = combinedArgumentList.get(1);
			return new DataObject().setBoolean(textObject.getText().startsWith(startsWithTextObject.getText()));
		});
		funcs.put("endsWith", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject endsWithTextObject = combinedArgumentList.get(1);
			return new DataObject().setBoolean(textObject.getText().endsWith(endsWithTextObject.getText()));
		});
		funcs.put("matches", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject matchTextObject = combinedArgumentList.get(1);
			try {
				return new DataObject().setBoolean(LangRegEx.matches(textObject.getText(), matchTextObject.getText()));
			}catch(InvalidPaternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("repeatText", (argumentList, SCOPE_ID) -> {
			if(LangUtils.countDataObjects(argumentList) < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject countObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				textObject = new DataObject().setVoid();
			
			Number count = countObject.toNumber();
			if(count == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Count must be a number", SCOPE_ID);
			if(count.intValue() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Count must be >= 0", SCOPE_ID);
			
			String text = textObject.getText();
			
			StringBuilder builder = new StringBuilder();
			for(int i = 0;i < count.intValue();i++)
				builder.append(text);
			
			return new DataObject(builder.toString());
		});
		funcs.put("charsOf", (argumentList, SCOPE_ID) -> {
			String text = getArgumentListAsString(argumentList, true);
			char[] chars = text.toCharArray();
			DataObject[] arr = new DataObject[chars.length];
			
			for(int i = 0;i < chars.length;i++)
				arr[i] = new DataObject().setChar(chars[i]);
			
			return new DataObject().setArray(arr);
		});
		funcs.put("join", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject collectionObject = combinedArgumentList.get(1);
			if(collectionObject.getType() != DataType.ARRAY && collectionObject.getType() != DataType.LIST)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.ARRAY + " or " + DataType.LIST), SCOPE_ID);
			
			String text = textObject.getText();
			Stream<DataObject> dataObjectStream = collectionObject.getType() == DataType.ARRAY?Arrays.stream(collectionObject.getArray()):collectionObject.getList().stream();
			
			return new DataObject(dataObjectStream.map(DataObject::getText).collect(Collectors.joining(text)));
		});
		funcs.put("split", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 4, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject;
			DataObject textObject;
			DataObject regexObject;
			DataObject maxSplitCountObject;
			if(combinedArgumentList.size() == 2) {
				arrPointerObject = new DataObject().setNull();
				textObject = combinedArgumentList.get(0);
				regexObject = combinedArgumentList.get(1);
				maxSplitCountObject = null;
			}else if(combinedArgumentList.size() == 4) {
				arrPointerObject = combinedArgumentList.get(0);
				textObject = combinedArgumentList.get(1);
				regexObject = combinedArgumentList.get(2);
				maxSplitCountObject = combinedArgumentList.get(3);
			}else {
				DataObject firstObject = combinedArgumentList.get(0);
				
				if(firstObject.getType() == DataType.NULL || firstObject.getType() == DataType.ARRAY) {
					arrPointerObject = firstObject;
					textObject = combinedArgumentList.get(1);
					regexObject = combinedArgumentList.get(2);
					maxSplitCountObject = null;
				}else {
					arrPointerObject = new DataObject().setNull();
					textObject = firstObject;
					regexObject = combinedArgumentList.get(1);
					maxSplitCountObject = combinedArgumentList.get(2);
				}
			}
			
			String[] arrTmp;
			try {
				if(maxSplitCountObject == null) {
					arrTmp = LangRegEx.split(textObject.getText(), regexObject.getText());
				}else {
					Number maxSplitCount = maxSplitCountObject.toNumber();
					if(maxSplitCount == null)
						return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
					
					arrTmp = LangRegEx.split(textObject.getText(), regexObject.getText(), maxSplitCount.intValue());
				}
			}catch(InvalidPaternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), SCOPE_ID);
			}
			
			if(arrPointerObject.getType() != DataType.NULL && arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(arrPointerObject.getType() == DataType.ARRAY && (arrPointerObject.isFinalData() || arrPointerObject.isLangVar()))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			DataObject[] arr = new DataObject[arrTmp.length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(arrTmp[i]);
			
			if(arrPointerObject.getType() == DataType.NULL && arrPointerObject.getVariableName() == null)
				return new DataObject().setArray(arr);
			
			arrPointerObject.setArray(arr);
			return arrPointerObject;
		});
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
		funcs.put("len", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opLen(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("deepCopy", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opDeepCopy(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("concat", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opConcat(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("spaceship", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opSpaceship(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
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
		funcs.put("inc", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opInc(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("dec", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opDec(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("pos", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opPos(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("inv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opInv(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("add", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opAdd(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("sub", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opSub(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("mul", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opMul(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("pow", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opPow(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("div", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opDiv(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("truncDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opTruncDiv(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("floorDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opFloorDiv(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("ceilDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opCeilDiv(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("mod", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opMod(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("and", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opAnd(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("or", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opOr(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("xor", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opXor(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("not", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, operand -> interpreter.operators.
				opNot(operand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("lshift", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opLshift(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("rshift", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opRshift(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("rzshift", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opRzshift(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		funcs.put("getItem", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (leftSideOperand, rightSideOperand) -> interpreter.operators.
				opGetItem(leftSideOperand, rightSideOperand, SCOPE_ID), SCOPE_ID), SCOPE_ID));
		
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
				return new DataObject().setVoid();
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			if(arrPointerObject.getType() == DataType.ARRAY) {
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of a collection"), SCOPE_ID);
				
				DataObject[] arr = arrPointerObject.getArray();
				return arr.length == 0?null:arr[interpreter.RAN.nextInt(arr.length)];
			}
			if(arrPointerObject.getType() == DataType.LIST) {
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of a collection"), SCOPE_ID);
				
				List<DataObject> list = arrPointerObject.getList();
				return list.size() == 0?null:list.get(interpreter.RAN.nextInt(list.size()));
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
		funcs.put("dtoi", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, int SCOPE_ID) {
				return unaryMathOperationHelper(argumentList, number -> {
					return new DataObject().setInt(number.intValue());
				}, SCOPE_ID);
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
			public DataObject callFunc(List<DataObject> argumentList, int SCOPE_ID) {
				return unaryMathOperationHelper(argumentList, number -> {
					return new DataObject().setLong(number.longValue());
				}, SCOPE_ID);
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
				case ARRAY:
				case LIST:
				case ERROR:
				case VAR_POINTER:
				case FUNCTION_POINTER:
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
		funcs.put("combA0", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}));
		funcs.put("combA", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}));
		funcs.put("combA2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c
					)
			), SCOPE_ID);
		}));
		funcs.put("combA3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d
					)
			), SCOPE_ID);
		}));
		funcs.put("combA4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, d, e
					)
			), SCOPE_ID);
		}));
		funcs.put("combAE", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}));
		funcs.put("combAN", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {0}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.addAll(args);
			argsA.remove(0);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combAV", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, args, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			if(args.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.ARRAY), SCOPE_ID);
			
			List<DataObject> argsA = new LinkedList<>();
			for(DataObject ele:args.getArray())
				argsA.add(ele);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combAX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, d, c
					)
			), SCOPE_ID);
		}));
		funcs.put("combAZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {0}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i > 0;i--)
				argsA.add(args.get(i));
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combB0", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}));
		funcs.put("combB", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}));
		funcs.put("combB2", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combB3", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combBE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}));
		funcs.put("combBN", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0, 1}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 2;i < args.size();i++) {
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
						args.get(i)
				), SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combBV", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, args, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			if(args.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "3 ", DataType.ARRAY), SCOPE_ID);
			
			List<DataObject> argsA = new LinkedList<>();
			for(DataObject ele:args.getArray()) {
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combBX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combBZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0, 1}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i > 1;i--) {
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
						args.get(i)
				), SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combC0", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}));
		funcs.put("combC1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
		}));
		funcs.put("combC", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, b
					)
			), SCOPE_ID);
		}));
		funcs.put("combC3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, b
					)
			), SCOPE_ID);
		}));
		funcs.put("combC4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							e, d, c, b
					)
			), SCOPE_ID);
		}));
		funcs.put("combCE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}));
		funcs.put("combCX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, d, b
					)
			), SCOPE_ID);
		}));
		funcs.put("combD", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combDE", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retC == null?new DataObject().setVoid():retC
			), SCOPE_ID);
		}));
		funcs.put("combE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combEE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retC == null?new DataObject().setVoid():retC
					)
			), SCOPE_ID);
		}));
		funcs.put("combEX", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combF1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
		}));
		funcs.put("combF", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, a
					)
			), SCOPE_ID);
		}));
		funcs.put("combF3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {3}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, b, a
					)
			), SCOPE_ID);
		}));
		funcs.put("combF4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {4}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject eFunc = e.getFunctionPointer();
			
			return interpreter.callFunctionPointer(eFunc, e.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d, c, b, a
					)
			), SCOPE_ID);
		}));
		funcs.put("combFE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}));
		funcs.put("combG", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combGE", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							d,
							retB == null?new DataObject().setVoid():retB
					)
			), SCOPE_ID);
		}));
		funcs.put("combGX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combH", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b, c, b
					)
			), SCOPE_ID);
		}));
		funcs.put("combHB", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combHE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1, 2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
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
		}));
		funcs.put("combHX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							c, b, c
					)
			), SCOPE_ID);
		}));
		funcs.put("combI", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combJ", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combJX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combJE", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							b,
							retA2 == null?new DataObject().setVoid():retA2
					)
			), SCOPE_ID);
		}));
		funcs.put("combK", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combK3", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combK4", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combK5", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combKD", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			return d;
		}));
		funcs.put("combKE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			return e;
		}));
		funcs.put("combKI", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			return b;
		}));
		funcs.put("combKX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			return c;
		}));
		funcs.put("combL", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}));
		funcs.put("combL2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
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
		}));
		funcs.put("combL3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combL4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combM", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
		}));
		funcs.put("combM2", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b
					)
			), SCOPE_ID);
		}));
		funcs.put("combM3", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c
					)
			), SCOPE_ID);
		}));
		funcs.put("combM4", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c, d
					)
			), SCOPE_ID);
		}));
		funcs.put("combM5", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(
							a, b, c, d, e
					)
			), SCOPE_ID);
		}));
		funcs.put("combN0", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a() must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}));
		funcs.put("combN1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a() must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
		}));
		funcs.put("combN", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
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
		}));
		funcs.put("combN3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combN4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combNE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			DataObject ret = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			ret = ret == null?new DataObject().setVoid():ret;
			
			if(ret.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The value returned by a() must be of type " + DataType.FUNCTION_POINTER, SCOPE_ID);
			
			return interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), new LinkedList<>(), SCOPE_ID);
		}));
		funcs.put("combNN", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {0}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			DataObject ret = a;
			for(int i = 1;i < args.size();i++) {
				DataObject n = args.get(i);
				
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The return value after iteration " + i + " must be of type " +
							DataType.FUNCTION_POINTER, SCOPE_ID);
				
				ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
						n
				), SCOPE_ID);
				ret = ret == null?new DataObject().setVoid():ret;
			}
			
			return ret;
		}));
		funcs.put("combNM", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
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
		}));
		funcs.put("combNV", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, args, SCOPE_ID) -> {
			if(args.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "3 ", DataType.ARRAY), SCOPE_ID);
			
			DataObject ret = a;
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
		}));
		funcs.put("combNW", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
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
		}));
		funcs.put("combNX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
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
		}));
		funcs.put("combNZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {0}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			DataObject ret = a;
			for(int i = args.size() - 1;i > 0;i--) {
				DataObject n = args.get(i);
				
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "The return value after iteration " + i + " must be of type " +
							DataType.FUNCTION_POINTER, SCOPE_ID);
				
				ret = interpreter.callFunctionPointer(ret.getFunctionPointer(), ret.getVariableName(), Arrays.asList(
						n
				), SCOPE_ID);
				ret = ret == null?new DataObject().setVoid():ret;
			}
			
			return ret;
		}));
		funcs.put("combO", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					b
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}));
		funcs.put("combO2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
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
		}));
		funcs.put("combO3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combO4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combP", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combP3", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1, 2, 3}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
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
		}));
		funcs.put("combPE", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combPN", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 2;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						b
				), SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combPV", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, args, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			if(args.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "3 ", DataType.ARRAY), SCOPE_ID);
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(n.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "3[" + i + "] ", DataType.FUNCTION_POINTER), SCOPE_ID);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						b
				), SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combPX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
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
		}));
		funcs.put("combPZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i > 1;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						b
				), SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combQ", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), Arrays.asList(
					c
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}));
		funcs.put("combQE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), new LinkedList<>(), SCOPE_ID);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					retA == null?new DataObject().setVoid():retA
			), SCOPE_ID);
		}));
		funcs.put("combQN", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			DataObject ret = a;
			for(int i = 1;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combQV", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(a, args, SCOPE_ID) -> {
			if(args.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.ARRAY), SCOPE_ID);
			
			DataObject ret = a;
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(n.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2[" + i + "] ", DataType.FUNCTION_POINTER), SCOPE_ID);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combQX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1, 2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), Arrays.asList(
					a
			), SCOPE_ID);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), Arrays.asList(
					retB == null?new DataObject().setVoid():retB
			), SCOPE_ID);
		}));
		funcs.put("combQZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			DataObject ret = a;
			for(int i = args.size() - 1;i > 0;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), Arrays.asList(
						ret
				), SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combR0", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combR1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combR", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(a);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combR3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(c);
			argsB.add(a);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combR4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(e);
			argsB.add(d);
			argsB.add(c);
			argsB.add(a);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combRE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combRX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(c);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combS", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combSE", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combSX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combT", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combT3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(c);
			argsB.add(d);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combT4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(c);
			argsB.add(d);
			argsB.add(e);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combTE", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combTN", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {}, true, true, (args, SCOPE_ID) -> {
			DataObject z = args.get(args.size() - 1);
			
			DataObject ret = z;
			for(int i = 0;i < args.size() - 1;i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(ret);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combTV", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(args, z, SCOPE_ID) -> {
			if(args.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", DataType.ARRAY), SCOPE_ID);
			
			DataObject ret = z;
			for(int i = 0;i < args.getArray().length;i++) {
				DataObject n = args.getArray()[i];
				
				if(n.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1[" + i + "] ", DataType.FUNCTION_POINTER), SCOPE_ID);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(ret);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combTX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(c);
			argsB.add(a);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combTZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {}, true, true, (args, SCOPE_ID) -> {
			DataObject z = args.get(args.size() - 1);
			
			DataObject ret = z;
			for(int i = args.size() - 2;i > -1;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(ret);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combU", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(e);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combUE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combUX", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 3, 4}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject dFunc = d.getFunctionPointer();
			FunctionPointerObject eFunc = e.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(b);
			DataObject retD = interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, SCOPE_ID);
			argsA.add(retD == null?new DataObject().setVoid():retD);
			List<DataObject> argsE = new LinkedList<>();
			argsE.add(c);
			DataObject retE = interpreter.callFunctionPointer(eFunc, e.getVariableName(), argsE, SCOPE_ID);
			argsA.add(retE == null?new DataObject().setVoid():retE);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combV1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(a);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
		}));
		funcs.put("combV", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(a);
			argsC.add(b);
			argsC = LangUtils.separateArgumentsWithArgumentSeparators(argsC);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
		}));
		funcs.put("combV3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {3}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(a);
			argsD.add(b);
			argsD.add(c);
			argsD = LangUtils.separateArgumentsWithArgumentSeparators(argsD);
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, SCOPE_ID);
		}));
		funcs.put("combW", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combW3", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(b);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combW4", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(b);
			argsA.add(b);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combWB", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(c);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combWX", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(a);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combX1", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(d);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX2", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(d);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX4", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(c);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(d);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX5", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(c);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX6", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(d);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(d);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX7", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(d);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(c);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX8", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(c);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(d);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combX9", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(d);
			argsB1.add(d);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(c);
			argsB2.add(c);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combXA", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(d);
			argsB1.add(c);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(c);
			argsB2.add(d);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combXB", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(d);
			argsB1.add(c);
			argsB1.add(d);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(c);
			argsB2.add(d);
			argsB2.add(c);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combXC", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(e);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(e);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combXD", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(e);
			argsB1.add(c);
			argsB1 = LangUtils.separateArgumentsWithArgumentSeparators(argsB1);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(e);
			argsB2.add(d);
			argsB2 = LangUtils.separateArgumentsWithArgumentSeparators(argsB2);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combXE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(e);
			argsB.add(d);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combXF", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(e);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combY", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(f, SCOPE_ID) -> {
			FunctionPointerObject fFunc = f.getFunctionPointer();
			
			LangPredefinedFunctionObject anonFunc = combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(x, INNER_SCOPE_ID) -> {
				FunctionPointerObject xFunc = x.getFunctionPointer();
				
				LangExternalFunctionObject func = (LangExternalFunctionObject)(interpreter, argumentList, INNER_INNER_SCOPE_ID) -> {
					List<DataObject> argsF = new LinkedList<>();
					List<DataObject> argsX = new LinkedList<>();
					argsX.add(x);
					DataObject retX = interpreter.callFunctionPointer(xFunc, x.getVariableName(), argsX, INNER_INNER_SCOPE_ID);
					argsF.add(retX == null?new DataObject().setVoid():retX);
					
					DataObject retF = interpreter.callFunctionPointer(fFunc, f.getVariableName(), argsF, INNER_INNER_SCOPE_ID);
					if(retF == null || retF.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.FUNCTION_POINTER)
								+ "\nThe implementation of the function provided to \"func.combY\" is incorrect!", INNER_INNER_SCOPE_ID);
					FunctionPointerObject retFFunc = retF.getFunctionPointer();
					
					return interpreter.callFunctionPointer(retFFunc, retF.getVariableName(), argumentList, INNER_INNER_SCOPE_ID);
				};
				
				return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
			});
			
			List<DataObject> argsFunc1 = new LinkedList<>();
			DataObject retAnonFunc1 = anonFunc.callFunc(argsFunc1, SCOPE_ID);
			FunctionPointerObject retAnonFunc1Func = retAnonFunc1.getFunctionPointer();
			
			List<DataObject> argsRetAnonFunc1 = new LinkedList<>();
			List<DataObject> argsAnonFunc2 = new LinkedList<>();
			argsRetAnonFunc1.add(anonFunc.callFunc(argsAnonFunc2, SCOPE_ID)); //Will always return a DataObject of type FUNCTION_POINTER
			
			return interpreter.callFunctionPointer(retAnonFunc1Func, retAnonFunc1.getVariableName(), argsRetAnonFunc1, SCOPE_ID);
		}));
	}
	private void addPredefinedFuncPtrFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("argCnt0", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCountAndType(combinedArgumentList, Arrays.asList(DataType.FUNCTION_POINTER), SCOPE_ID)) != null)
				return error;
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
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
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
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
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
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
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
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
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
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
			
			DataObject funcPointerObject = combinedArgumentList.get(0);
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> combinedInnerArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				DataObject innerError;
				if((innerError = requireArgumentCount(combinedInnerArgumentList, 5, INNER_SCOPE_ID)) != null)
					return innerError;
				
				return new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(combinedInnerArgumentList), INNER_SCOPE_ID));
			}));
		});
		funcs.put("copyAfterFP", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject toPointerObject = combinedArgumentList.get(0);
			DataObject fromPointerObject = combinedArgumentList.get(1);
			
			if(interpreter.copyAfterFP.get(SCOPE_ID) == null)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "func.copyAfterFP can not be used outside of functions or func.exec", SCOPE_ID);
			
			String to = null;
			switch(toPointerObject.getType()) {
				case ARRAY:
				case FUNCTION_POINTER:
					to = toPointerObject.getVariableName();
					break;
				
				case VAR_POINTER:
					DataObject toVar = toPointerObject.getVarPointer().getVar();
					if(toVar == null)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "to pointer is invalid", SCOPE_ID);
						
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
				case LIST:
				case NULL:
				case VOID:
				case TYPE:
					break;
			}
			if(to == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "to pointer is invalid", SCOPE_ID);
			
			String from = null;
			switch(fromPointerObject.getType()) {
				case ARRAY:
				case FUNCTION_POINTER:
					from = fromPointerObject.getVariableName();
					break;
				
				case VAR_POINTER:
					DataObject fromVar = fromPointerObject.getVarPointer().getVar();
					if(fromVar == null)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "from pointer is invalid", SCOPE_ID);
						
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
				case LIST:
				case NULL:
				case VOID:
				case TYPE:
					break;
			}
			if(from == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "from pointer is invalid", SCOPE_ID);
			
			interpreter.copyAfterFP.get(SCOPE_ID).put(to, from);
			interpreter.setErrno(InterpretingError.USE_OF_COPY_AFTER_FP, SCOPE_ID);
			
			return null;
		});
	}
	private void addPredefinedArrayFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("arrayMake", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.size() == 1?null:combinedArgumentList.get(0);
			DataObject lengthObject = combinedArgumentList.get(combinedArgumentList.size() == 1?0:1);
			
			String arrPtr;
			if(arrPointerObject == null || arrPointerObject.getType() == DataType.ARRAY) {
				arrPtr = null;
			}else if(arrPointerObject.getType() == DataType.TEXT) {
				arrPtr = arrPointerObject.getText();
				if(!LangPatterns.matches(arrPtr, LangPatterns.VAR_NAME_ARRAY))
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			}else {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			}
			
			Number lengthNumber = lengthObject.toNumber();
			if(lengthNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LENGTH_NAN, SCOPE_ID);
			int length = lengthNumber.intValue();
			
			if(length < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.NEGATIVE_ARRAY_LEN, SCOPE_ID);
			
			DataObject oldData = arrPtr == null?arrPointerObject:interpreter.data.get(SCOPE_ID).var.get(arrPtr);
			if((oldData != null && (oldData.isFinalData() || (oldData.getVariableName() != null && oldData.getVariableName().startsWith("&LANG_")))) ||
			(arrPtr != null && arrPtr.startsWith("&LANG_")))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
			DataObject[] arr = new DataObject[length];
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(); //Null data object
			if(oldData != null) {
				oldData.setArray(arr);
			}else if(arrPointerObject == null) {
				return new DataObject().setArray(arr);
			}else {
				interpreter.setErrnoErrorObject(InterpretingError.DEPRECATED_FUNC_CALL, "Implicit variable creation is deprecated and will be removed in v1.2.0.", SCOPE_ID);
				interpreter.data.get(SCOPE_ID).var.put(arrPtr, new DataObject().setArray(arr).setVariableName(arrPtr));
			}
			
			return null;
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
			if((error = requireArgumentCount(combinedArgumentList, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr) {
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						ele
				), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("arrayEnumerate", (argumentList, SCOPE_ID) -> {
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
			for(int i = 0;i < arr.length;i++) {
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								new DataObject().setInt(i),
								arr[i]
						)
				), SCOPE_ID);
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 2 must not be less than 0!", SCOPE_ID);
			
			if(count > arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 2 must not be greater than the array!", SCOPE_ID);
			
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
		funcs.put("arrayDelete", (argumentList, SCOPE_ID) -> {
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
		funcs.put("arrayClear", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				DataObject error;
				if((error = requireArgumentCount(combinedArgumentList, 1, SCOPE_ID)) != null)
					return error;
				
				DataObject arrPointerObject = combinedArgumentList.get(0);
				
				if(arrPointerObject.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
				
				if(arrPointerObject.isFinalData() || arrPointerObject.isLangVar())
					return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
				
				String variableName = arrPointerObject.getVariableName();
				if(variableName == null)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
				
				interpreter.data.get(SCOPE_ID).var.remove(variableName);
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
				return "func.freeVar";
			}
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
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), Arrays.asList(
						list.get(i)
				), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("listEnumerate", (argumentList, SCOPE_ID) -> {
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
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(),
				LangUtils.separateArgumentsWithArgumentSeparators(
						Arrays.asList(
								new DataObject().setInt(i),
								list.get(i)
						)
				), SCOPE_ID);
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
		funcs.put("getModuleVariableCollection", (argumentList, SCOPE_ID) -> {
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
		funcs.put("moduleExportCollectionVariable", (argumentList, SCOPE_ID) -> {
			LangModule module = interpreter.getCurrentCallStackElement().getModule();
			if(module == null || !module.isLoad())
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "\"moduleExportCollectionVariable\" can only be used inside a module which "
						+ "is in the \"load\" state", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject variableNameObject = combinedArgumentList.get(0);
			DataObject variableObject = combinedArgumentList.get(1);
			DataObject finalDataObject = combinedArgumentList.size() >= 3?combinedArgumentList.get(2):null;
			
			if(!DataObject.CONSTRAINT_COLLECTION.isTypeAllowed(variableObject.getType()))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "collection"), SCOPE_ID);
			
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
		funcs.put("langTestUnit", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			interpreter.langTestStore.addUnit(textObject.getText());
			
			return null;
		});
		funcs.put("langTestSubUnit", (argumentList, SCOPE_ID) -> {
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
		funcs.put("langTestAssertError", (argumentList, SCOPE_ID) -> {
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
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultError(langErrno == expectedError, messageObject == null?null:messageObject.getText(), langErrno, expectedError));
			
			return null;
		});
		funcs.put("langTestAssertEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultEquals(actualValueObject.isEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotEquals(!actualValueObject.isEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertLessThan", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThan(actualValueObject.isLessThan(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertLessThanOrEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThanOrEquals(actualValueObject.isLessThanOrEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertGreaterThan", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThan(actualValueObject.isGreaterThan(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertGreaterThanOrEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThanOrEquals(actualValueObject.isGreaterThanOrEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject,
					expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStrictEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictEquals(actualValueObject.isStrictEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStrictNotEquals", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 2, 3, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictNotEquals(!actualValueObject.isStrictEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertTranslationValueEquals", (argumentList, SCOPE_ID) -> {
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
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueEquals(translationValue != null && translationValue.equals(expectedValueObject.getText()),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("langTestAssertTranslationValueNotEquals", (argumentList, SCOPE_ID) -> {
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
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueNotEquals(translationValue != null && !translationValue.equals(expectedValueObject.getText()),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("langTestAssertTranslationKeyFound", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyFound(translationValue != null, messageObject == null?null:messageObject.getText(),
					translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("langTestAssertTranslationKeyNotFound", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyNotFound(translationValue == null, messageObject == null?null:messageObject.getText(),
					translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("langTestAssertNull", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNull(actualValueObject.getType() == DataType.NULL, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotNull", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotNull(actualValueObject.getType() != DataType.NULL, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertVoid", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultVoid(actualValueObject.getType() == DataType.VOID, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotVoid", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotVoid(actualValueObject.getType() != DataType.VOID, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertFinal", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultFinal(actualValueObject.isFinalData(), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotFinal", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotFinal(!actualValueObject.isFinalData(), messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStatic", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStatic(actualValueObject.isStaticData(), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotStatic", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			DataObject error;
			if((error = requireArgumentCount(combinedArgumentList, 1, 2, SCOPE_ID)) != null)
				return error;
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotStatic(!actualValueObject.isStaticData(), messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertThrow", (argumentList, SCOPE_ID) -> {
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
		funcs.put("langTestAssertReturn", (argumentList, SCOPE_ID) -> {
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
		funcs.put("langTestAssertNoReturn", (argumentList, SCOPE_ID) -> {
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
		funcs.put("langTestAssertFail", (argumentList, SCOPE_ID) -> {
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			if(messageObject == null) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "Too few arguments (1 needed)", SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultFail(messageObject.getText()));
			
			return null;
		});
		funcs.put("langTestClearAllTranslations", (argumentList, SCOPE_ID) -> {
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			new HashSet<>(interpreter.data.get(SCOPE_ID).lang.keySet()).forEach(translationKey -> {
				if(!translationKey.startsWith("lang."))
					interpreter.data.get(SCOPE_ID).lang.remove(translationKey);
			});
			
			return null;
		});
		funcs.put("langTestPrintResults", (argumentList, SCOPE_ID) -> {
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
					langFileName.substring(langFileName.lastIndexOf('/') + 1), null, module));
		}else {
			langPathTmp = interpreter.langPlatformAPI.getLangPath(langPathTmp);
			
			//Update call stack
			interpreter.pushStackElement(new StackElement(langPathTmp, interpreter.langPlatformAPI.getLangFileName(langFileName), null, null));
		}
		
		//Create an empty data map
		interpreter.createDataMap(NEW_SCOPE_ID, langArgs);
		if(insideModule) {
			try(BufferedReader reader = LangModuleManager.readModuleLangFile(module, absolutePath)) {
				interpreter.interpretLines(reader, NEW_SCOPE_ID);
			}catch(IOException e) {
				interpreter.data.remove(NEW_SCOPE_ID);
				return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
			}finally {
				//Update call stack
				interpreter.popStackElement();
			}
		}else {
			try(BufferedReader reader = interpreter.langPlatformAPI.getLangReader(absolutePath)) {
				interpreter.interpretLines(reader, NEW_SCOPE_ID);
			}catch(IOException e) {
				interpreter.data.remove(NEW_SCOPE_ID);
				return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
			}finally {
				//Update call stack
				interpreter.popStackElement();
			}
		}
		
		function.accept(NEW_SCOPE_ID);
		
		//Remove data map
		interpreter.data.remove(NEW_SCOPE_ID);
		
		//Get returned value from executed lang file
		return interpreter.getAndResetReturnValue(SCOPE_ID);
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
}