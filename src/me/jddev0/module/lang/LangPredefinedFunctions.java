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
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.VarPointerObject;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangInterpreter.StackElement;
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
	private static final String ARGUMENT_TYPE_FORMAT = "Argument %smust be from type %s";
	
	//Return values for format sequence errors
	private static final int FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE = -1;
	private static final int FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS = -2;
	private static final int FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT = -3;
	private static final int FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND = -4;
	private static final int FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS = -5;
	
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
	
	private List<DataObject> getAllArguments(List<DataObject> argumentList) {
		List<DataObject> arguments = new LinkedList<>();
		
		while(!argumentList.isEmpty())
			arguments.add(LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true));
		
		return arguments;
	}
	
	private DataObject throwErrorOnNullOrErrorTypeHelper(DataObject dataObject, final int SCOPE_ID) {
		if(dataObject == null)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		
		if(dataObject.getType() == DataType.ERROR)
			return interpreter.setErrnoErrorObject(dataObject.getError().getInterprettingError(), dataObject.getError().getMessage(), SCOPE_ID);
		
		return dataObject;
	}
	
	private DataObject unaryOperationHelper(List<DataObject> argumentList, Function<DataObject, DataObject> operation, final int SCOPE_ID) {
		DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 1 argument
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		return operation.apply(dataObject);
	}
	private DataObject binaryOperationHelper(List<DataObject> argumentList, BiFunction<DataObject, DataObject, DataObject> operation, final int SCOPE_ID) {
		DataObject leftDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		DataObject rightDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 2 arguments
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		return operation.apply(leftDataObject, rightDataObject);
	}
	
	private DataObject unaryFromBooleanValueInvertedOperationHelper(List<DataObject> argumentList, Function<DataObject, Boolean> operation, final int SCOPE_ID) {
		DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 1 argument
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		return new DataObject().setBoolean(!operation.apply(dataObject));
	}
	private DataObject binaryFromBooleanValueOperationHelper(List<DataObject> argumentList, BiFunction<DataObject, DataObject, Boolean> operation, final int SCOPE_ID) {
		DataObject leftDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		DataObject rightDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 2 arguments
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		return new DataObject().setBoolean(operation.apply(leftDataObject, rightDataObject));
	}
	private DataObject binaryFromBooleanValueInvertedOperationHelper(List<DataObject> argumentList, BiFunction<DataObject, DataObject, Boolean> operation, final int SCOPE_ID) {
		DataObject leftDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		DataObject rightDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 2 arguments
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
		return new DataObject().setBoolean(!operation.apply(leftDataObject, rightDataObject));
	}
	
	private DataObject unaryMathOperationHelper(List<DataObject> argumentList, Function<Number, DataObject> operation, final int SCOPE_ID) {
		DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 1 argument
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		Number number = numberObject.toNumber();
		if(number == null)
			return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
		
		return operation.apply(number);
	}
	private DataObject binaryMathOperationHelper(List<DataObject> argumentList, BiFunction<Number, Number, DataObject> operation, final int SCOPE_ID) {
		DataObject leftNumberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		DataObject rightNumberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 2 arguments
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
		
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
	
	/**
	 * @param argumentList The argument list without argument separators of the function call without the format argument (= argument at index 0). Used data objects will be removed from the list
	 * @param fullArgumentList The argument list of the function call where every argument are already combined to single values without argument separators with the format argument
	 * (= argument at index 0). This list will not be modified and is used for value referencing by index
	 * 
	 * @return The count of chars used for the format sequence
	 * Will return any of
	 * <ul>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE}</li>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS}</li>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND}</li>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS}</li>
	 * </ul>
	 * for errors
	 */
	private int interpretNextFormatSequence(String format, StringBuilder builder, List<DataObject> argumentList, List<DataObject> fullArgumentList, final int SCOPE_ID) {
		char[] posibleFormats = {'b', 'c', 'd', 'f', 'n', 'o', 's', 't', 'x', '?'};
		int[] indices = new int[posibleFormats.length];
		for(int i = 0;i < posibleFormats.length;i++)
			indices[i] = format.indexOf(posibleFormats[i]);
		
		int minEndIndex = Integer.MAX_VALUE;
		for(int index:indices) {
			if(index == -1)
				continue;
			
			if(index < minEndIndex)
				minEndIndex = index;
		}
		
		if(minEndIndex == Integer.MAX_VALUE)
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
		
		String fullFormat = format.substring(0, minEndIndex + 1);
		char formatType = fullFormat.charAt(fullFormat.length() - 1);
		
		//Parsing format arguments
		Integer valueSpecifiedIndex = null;
		if(fullFormat.charAt(0) == '[') {
			int valueSpecifiedIndexEndIndex = fullFormat.indexOf(']');
			if(valueSpecifiedIndexEndIndex < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
			
			String valueSpecifiedIndexString = fullFormat.substring(1, valueSpecifiedIndexEndIndex);
			fullFormat = fullFormat.substring(valueSpecifiedIndexEndIndex + 1);
			
			String number = "";
			while(!valueSpecifiedIndexString.isEmpty()) {
				if(valueSpecifiedIndexString.charAt(0) < '0' || valueSpecifiedIndexString.charAt(0) > '9')
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				number += valueSpecifiedIndexString.charAt(0);
				valueSpecifiedIndexString = valueSpecifiedIndexString.substring(1);
			}
			valueSpecifiedIndex = Integer.parseInt(number);
			if(valueSpecifiedIndex >= fullArgumentList.size())
				return FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS;
		}
		boolean leftJustify = fullFormat.charAt(0) == '-';
		if(leftJustify)
			fullFormat = fullFormat.substring(1);
		boolean forceSign = fullFormat.charAt(0) == '+';
		if(forceSign)
			fullFormat = fullFormat.substring(1);
		boolean signSpace = !forceSign && fullFormat.charAt(0) == ' ';
		if(signSpace)
			fullFormat = fullFormat.substring(1);
		boolean leadingZeros = fullFormat.charAt(0) == '0';
		if(leadingZeros)
			fullFormat = fullFormat.substring(1);
		boolean sizeInArgument = fullFormat.charAt(0) == '*';
		if(sizeInArgument)
			fullFormat = fullFormat.substring(1);
		Integer sizeArgumentIndex = null;
		if(sizeInArgument && fullFormat.charAt(0) == '[') {
			int sizeArgumentIndexEndIndex = fullFormat.indexOf(']');
			if(sizeArgumentIndexEndIndex < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
			
			String sizeArgumentIndexString = fullFormat.substring(1, sizeArgumentIndexEndIndex);
			fullFormat = fullFormat.substring(sizeArgumentIndexEndIndex + 1);
			
			String number = "";
			while(!sizeArgumentIndexString.isEmpty()) {
				if(sizeArgumentIndexString.charAt(0) < '0' || sizeArgumentIndexString.charAt(0) > '9')
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				number += sizeArgumentIndexString.charAt(0);
				sizeArgumentIndexString = sizeArgumentIndexString.substring(1);
			}
			sizeArgumentIndex = Integer.parseInt(number);
			if(sizeArgumentIndex >= fullArgumentList.size())
				return  FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS;
		}
		Integer size = null;
		if(fullFormat.charAt(0) > '0' && fullFormat.charAt(0) <= '9') {
			String number = "";
			while(fullFormat.charAt(0) >= '0' && fullFormat.charAt(0) <= '9') {
				number += fullFormat.charAt(0);
				fullFormat = fullFormat.substring(1);
			}
			size = Integer.parseInt(number);
		}
		boolean decimalPlaces = fullFormat.charAt(0) == '.';
		boolean decimalPlacesInArgument = false;
		Integer decimalPlacesCountIndex = null;
		Integer decimalPlacesCount = null;
		if(decimalPlaces) {
			fullFormat = fullFormat.substring(1);
			decimalPlacesInArgument = fullFormat.charAt(0) == '*';
			if(decimalPlacesInArgument)
				fullFormat = fullFormat.substring(1);
			if(decimalPlacesInArgument && fullFormat.charAt(0) == '[') {
				int decimalPlacesCountIndexEndIndex = fullFormat.indexOf(']');
				if(decimalPlacesCountIndexEndIndex < 0)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				String decimalPlacesCountIndexString = fullFormat.substring(1, decimalPlacesCountIndexEndIndex);
				fullFormat = fullFormat.substring(decimalPlacesCountIndexEndIndex + 1);
				
				String number = "";
				while(!decimalPlacesCountIndexString.isEmpty()) {
					if(decimalPlacesCountIndexString.charAt(0) < '0' || decimalPlacesCountIndexString.charAt(0) > '9')
						return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
					
					number += decimalPlacesCountIndexString.charAt(0);
					decimalPlacesCountIndexString = decimalPlacesCountIndexString.substring(1);
				}
				decimalPlacesCountIndex = Integer.parseInt(number);
				if(decimalPlacesCountIndex >= fullArgumentList.size())
					return FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS;
			}
			if(fullFormat.charAt(0) > '0' && fullFormat.charAt(0) <= '9') {
				String number = "";
				while(fullFormat.charAt(0) >= '0' && fullFormat.charAt(0) <= '9') {
					number += fullFormat.charAt(0);
					fullFormat = fullFormat.substring(1);
				}
				decimalPlacesCount = Integer.parseInt(number);
			}
		}
		
		if(fullFormat.charAt(0) != formatType)
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE; //Invalid characters
		if((sizeInArgument && size != null) || (decimalPlacesInArgument && decimalPlacesCount != null) || (leftJustify && leadingZeros))
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE; //Invalid format argument combinations
		if(leftJustify && (!sizeInArgument && size == null))
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE; //Missing size format argument for leftJustify
		switch(formatType) { //Invalid arguments for formatType
			case 'f':
				break;
			
			case 'n':
				if(valueSpecifiedIndex != null || sizeInArgument || size != null)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				//Fall-trough
			case 'c':
			case 's':
			case 't':
			case '?':
				if(forceSign || signSpace || leadingZeros)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				//Fall-trough
			case 'b':
			case 'd':
			case 'o':
			case 'x':
				if(decimalPlaces)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				break;
			
		}
		
		//Get size from arguments
		if(sizeInArgument) {
			if(sizeArgumentIndex == null && argumentList.isEmpty())
				return FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT;
			DataObject dataObject = sizeArgumentIndex == null?argumentList.remove(0):fullArgumentList.get(sizeArgumentIndex);
			Number number = dataObject.toNumber();
			if(number == null)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
			
			size = number.intValue();
			if(size < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
		}
		if(decimalPlacesInArgument) {
			if(decimalPlacesCountIndex == null && argumentList.isEmpty())
				return FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT;
			DataObject dataObject = decimalPlacesCountIndex == null?argumentList.remove(0):fullArgumentList.get(decimalPlacesCountIndex);
			Number number = dataObject.toNumber();
			if(number == null)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
			
			decimalPlacesCount = number.intValue();
			if(decimalPlacesCount < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
		}
		
		//Format argument
		String output = null;
		if(formatType != 'n' && valueSpecifiedIndex == null && argumentList.isEmpty())
			return FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT;
		DataObject dataObject = formatType == 'n'?null:(valueSpecifiedIndex == null?argumentList.remove(0):fullArgumentList.get(valueSpecifiedIndex));
		switch(formatType) {
			case 'd':
				Number number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = number.longValue() + "";
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'b':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = Long.toString(number.longValue(), 2).toUpperCase();
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'o':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = Long.toString(number.longValue(), 8).toUpperCase();
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'x':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = Long.toString(number.longValue(), 16).toUpperCase();
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'f':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				double value = number.doubleValue();
				if(Double.isNaN(value)) {
					output = "NaN";
					forceSign = false;
					leadingZeros = false;
				}else if(Double.isInfinite(value)) {
					output = (value == Double.NEGATIVE_INFINITY?"-":"") + "Infinity";
					leadingZeros = false;
					if(forceSign && output.charAt(0) != '-')
						output = "+" + output;
					
					if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
						output = " " + output;
				}else {
					output = String.format(Locale.ENGLISH, "%" + (decimalPlacesCount == null?"":("." + decimalPlacesCount)) + "f", value);
					if(forceSign && output.charAt(0) != '-')
						output = "+" + output;
					
					if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
						output = " " + output;
				}
				
				break;
				
			case 'c':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = "" + (char)number.intValue();
				
				break;
				
			case 's':
				output = dataObject.getText();
				
				break;
				
			case 't':
				String translationKey = dataObject.getText();
				output = interpreter.getData().get(SCOPE_ID).lang.get(translationKey);
				if(output == null)
					return FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND;
				
				break;
				
			case '?':
				output = dataObject.getBoolean()?"true":"false";
				
				break;
				
			case 'n':
				output = System.getProperty("line.separator");
				
				break;
		}
		
		if(output != null) {
			if(size == null) {
				builder.append(output);
			}else {
				if(leftJustify) {
					while(output.length() < size)
						output = output + " ";
				}else if(leadingZeros) {
					char signOutput = 0;
					if(output.charAt(0) == '+' || output.charAt(0) == '-' || output.charAt(0) == ' ') {
						signOutput = output.charAt(0);
						output = output.substring(1);
					}
					
					int paddingSize = size - (signOutput == 0?0:1);
					while(output.length() < paddingSize)
						output = "0" + output;
					
					if(signOutput != 0)
						output = signOutput + output;
				}else {
					while(output.length() < size)
						output = " " + output;
				}
				
				builder.append(output);
			}
		}
		
		return minEndIndex + 1;
	}
	private DataObject formatText(String format, List<DataObject> argumentList, final int SCOPE_ID) {
		StringBuilder builder = new StringBuilder();
		List<DataObject> fullArgumentList = new LinkedList<>(argumentList);
		fullArgumentList.add(0, new DataObject(format));
		
		int i = 0;
		while(i < format.length()) {
			char c = format.charAt(i);
			if(c == '%') {
				if(++i == format.length())
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FORMAT, SCOPE_ID);
				
				c = format.charAt(i);
				if(c == '%') {
					builder.append(c);
					
					i++;
					continue;
				}
				
				int charCountUsed = interpretNextFormatSequence(format.substring(i), builder, argumentList, fullArgumentList, SCOPE_ID);
				if(charCountUsed == FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FORMAT, SCOPE_ID);
				else if(charCountUsed == FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
				else if(charCountUsed == FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
				else if(charCountUsed == FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND)
					return interpreter.setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
				else if(charCountUsed == FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS)
					return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
				
				i += charCountUsed;
				
				continue;
			}
			
			builder.append(c);
			
			i++;
		}
		
		return new DataObject(builder.toString());
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
		addPredefinedLangTestFunctions(funcs);
	}
	private void addPredefinedResetFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("clearVar", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
				if(combinedArgumentList.size() < 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			interpreter.resetVars(SCOPE_ID);
			return null;
		});
		funcs.put("clearAllArrays", new LangPredefinedFunctionObject() {
			@Override
			public DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID) {
				new HashSet<>(interpreter.data.get(SCOPE_ID).var.entrySet()).forEach(entry -> {
					if(entry.getValue().getType() == DataType.ARRAY)
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
		funcs.put("getErrorText", (argumentList, SCOPE_ID) -> new DataObject(interpreter.getAndClearErrnoErrorObject(SCOPE_ID).getErrorText()));
		funcs.put("errorText", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject errorObject = combinedArgumentList.get(0);
			
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.ERROR), SCOPE_ID);
			
			return new DataObject(errorObject.getError().getErrtxt());
		});
		funcs.put("errorCode", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject errorObject = combinedArgumentList.get(0);
			
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.ERROR), SCOPE_ID);
			
			return new DataObject().setInt(errorObject.getError().getErrno());
		});
	}
	private void addPredefinedLangFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("isLangVersionNewer", (argumentList, SCOPE_ID) -> {
			String langVer = interpreter.data.get(SCOPE_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			return new DataObject().setBoolean(LangInterpreter.VERSION.compareTo(langVer) > 0);
		});
		funcs.put("isLangVersionOlder", (argumentList, SCOPE_ID) -> {
			String langVer = interpreter.data.get(SCOPE_ID).lang.getOrDefault("lang.version", LangInterpreter.VERSION); //If lang.version = null -> return false
			return new DataObject().setBoolean(LangInterpreter.VERSION.compareTo(langVer) < 0);
		});
	}
	private void addPredefinedSystemFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("sleep", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
		funcs.put("nanoTime", (argumentList, SCOPE_ID) -> new DataObject().setLong(System.nanoTime()));
		funcs.put("currentTimeMillis", (argumentList, SCOPE_ID) -> new DataObject().setLong(System.currentTimeMillis()));
		funcs.put("currentUnixTime", (argumentList, SCOPE_ID) -> new DataObject().setLong(Instant.now().getEpochSecond()));
		funcs.put("repeat", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
				List<DataObject> loopFuncArgumentList = new ArrayList<>();
				loopFuncArgumentList.add(new DataObject().setInt(i));
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), loopFuncArgumentList, SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("repeatWhile", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			
			String langValue = interpreter.data.get(SCOPE_ID).lang.get(translationKeyObject.getText());
			if(langValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
			
			return new DataObject(langValue);
		});
		funcs.put("makeFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject(dataObject).setCopyStaticAndFinalModifiers(true).setFinalData(true);
		});
		funcs.put("isFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject().setBoolean(dataObject.isFinalData());
		});
		funcs.put("makeStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			return new DataObject(dataObject).setCopyStaticAndFinalModifiers(true).setStaticData(true);
		});
		funcs.put("isStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			interpreter.pushStackElement(new StackElement(currentStackElement.getLangPath(), currentStackElement.getLangFile(), "func.exec"));
			
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
		funcs.put("isTerminalAvailable", (argumentList, SCOPE_ID) -> new DataObject().setBoolean(interpreter.term != null));
		funcs.put("isInstanceOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			DataObject dataTypeObject = combinedArgumentList.get(1);
			if(dataTypeObject.getType() != DataType.TYPE)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "2 ", DataType.TYPE), SCOPE_ID);
			
			return new DataObject().setBoolean(dataObject.getType() == dataTypeObject.getTypeValue());
		});
		funcs.put("typeOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject out = formatText(formatObject.getText(), combinedArgumentList, SCOPE_ID);
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
			DataObject out = formatText(formatObject.getText(), combinedArgumentList, SCOPE_ID);
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			dataObject = dataObject.convertToNumberAndCreateNewDataObject();
			if(dataObject.getType() == DataType.NULL)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			return dataObject;
		});
		funcs.put("ttoi", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			if(LangPatterns.matches(str, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE) || LangPatterns.matches(str, LangPatterns.PARSING_INVALID_FLOATING_POINT_NUMBER_ALLOW_NaN_INFINITY))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			try {
				return new DataObject().setFloat(Float.parseFloat(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("ttod", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			if(LangPatterns.matches(str, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE) || LangPatterns.matches(str, LangPatterns.PARSING_INVALID_FLOATING_POINT_NUMBER_ALLOW_NaN_INFINITY))
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
	}
	private void addPredefinedCharacterFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("toValue", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject charObject = combinedArgumentList.get(0);
			
			if(charObject.getType() != DataType.CHAR)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_CHAR, SCOPE_ID);
			
			return new DataObject().setInt(charObject.getChar());
		});
		funcs.put("toChar", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject asciiValueObject = combinedArgumentList.get(0);
			
			Number asciiValue = asciiValueObject.toNumber();
			if(asciiValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			return new DataObject().setChar((char)asciiValue.intValue());
		});
		funcs.put("ttoc", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			
			String str = textObject.getText();
			if(str.length() == 1)
				return new DataObject().setChar(str.charAt(0));
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		});
	}
	private void addPredefinedTextFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("strlen", (argumentList, SCOPE_ID) -> new DataObject().setInt(getArgumentListAsString(argumentList, true).length()));
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
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
			return formatText(formatObject.getText(), combinedArgumentList, SCOPE_ID);
		});
		funcs.put("contains", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject containTextObject = combinedArgumentList.get(1);
			return new DataObject().setBoolean(textObject.getText().contains(containTextObject.getText()));
		});
		funcs.put("indexOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject startsWithTextObject = combinedArgumentList.get(1);
			return new DataObject().setBoolean(textObject.getText().startsWith(startsWithTextObject.getText()));
		});
		funcs.put("endsWith", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject endsWithTextObject = combinedArgumentList.get(1);
			return new DataObject().setBoolean(textObject.getText().endsWith(endsWithTextObject.getText()));
		});
		funcs.put("matches", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject textObject = combinedArgumentList.get(0);
			DataObject arrPointerObject = combinedArgumentList.get(1);
			if(arrPointerObject.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			String text = textObject.getText();
			DataObject[] arr = arrPointerObject.getArray();
			
			return new DataObject(Arrays.stream(arr).map(DataObject::getText).collect(Collectors.joining(text)));
		});
		funcs.put("split", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "3 or 4"), SCOPE_ID);
			if(combinedArgumentList.size() > 4)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "3 or 4"), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject textObject = combinedArgumentList.get(1);
			DataObject regexObject = combinedArgumentList.get(2);
			DataObject maxSplitCountObject = combinedArgumentList.size() < 4?null:combinedArgumentList.get(3);
			
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
			
			String arrPtr;
			if(arrPointerObject.getType() == DataType.NULL || arrPointerObject.getType() == DataType.ARRAY) {
				arrPtr = null;
			}else if(arrPointerObject.getType() == DataType.TEXT) {
				arrPtr = arrPointerObject.getText();
				if(!LangPatterns.matches(arrPtr, LangPatterns.VAR_NAME_ARRAY))
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			}else {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			}
			
			DataObject oldData = arrPtr == null?arrPointerObject:interpreter.data.get(SCOPE_ID).var.get(arrPtr);
			if((oldData != null && (oldData.isFinalData() || oldData.isLangVar())) || (arrPtr != null && arrPtr.startsWith("&LANG_")))
				return interpreter.setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, SCOPE_ID);
			
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
				interpreter.data.get(SCOPE_ID).var.put(arrPtr, arrPointerObject);
				return arrPointerObject;
			}
		});
	}
	private void addPredefinedConversionFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("text", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			String value = dataObject.toText();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject(value), SCOPE_ID);
		});
		funcs.put("char", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Character value = dataObject.toChar();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setChar(value), SCOPE_ID);
		});
		funcs.put("int", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Integer value = dataObject.toInt();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setInt(value), SCOPE_ID);
		});
		funcs.put("long", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Long value = dataObject.toLong();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setLong(value), SCOPE_ID);
		});
		funcs.put("float", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Float value = dataObject.toFloat();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setFloat(value), SCOPE_ID);
		});
		funcs.put("double", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			Double value = dataObject.toDouble();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setDouble(value), SCOPE_ID);
		});
		funcs.put("array", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			DataObject[] value = dataObject.toArray();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setArray(value), SCOPE_ID);
		});
		funcs.put("bool", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			return new DataObject().setBoolean(dataObject.toBoolean());
		});
		funcs.put("number", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject dataObject = combinedArgumentList.get(0);
			
			dataObject = dataObject.convertToNumberAndCreateNewDataObject();
			return throwErrorOnNullOrErrorTypeHelper(dataObject.getType() == DataType.NULL?null:dataObject, SCOPE_ID);
		});
	}
	private void addPredefinedOperationFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		//General operator functions
		funcs.put("len", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opLen, SCOPE_ID), SCOPE_ID));
		funcs.put("deepCopy", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opDeepCopy, SCOPE_ID), SCOPE_ID));
		funcs.put("concat", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opConcat, SCOPE_ID), SCOPE_ID));
		funcs.put("spaceship", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opSpaceship, SCOPE_ID), SCOPE_ID));
		funcs.put("elvis", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (dataObject1, dataObject2) -> {
			return dataObject1.getBoolean()?dataObject1:dataObject2;
		}, SCOPE_ID), SCOPE_ID));
		funcs.put("nullCoalescing", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, (dataObject1, dataObject2) -> {
			return (dataObject1.getType() != DataType.NULL && dataObject1.getType() != DataType.VOID)?dataObject1:dataObject2;
		}, SCOPE_ID), SCOPE_ID));
		
		//Math operator functions
		funcs.put("inc", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opInc, SCOPE_ID), SCOPE_ID));
		funcs.put("dec", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opDec, SCOPE_ID), SCOPE_ID));
		funcs.put("pos", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opPos, SCOPE_ID), SCOPE_ID));
		funcs.put("inv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opInv, SCOPE_ID), SCOPE_ID));
		funcs.put("add", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opAdd, SCOPE_ID), SCOPE_ID));
		funcs.put("sub", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opSub, SCOPE_ID), SCOPE_ID));
		funcs.put("mul", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opMul, SCOPE_ID), SCOPE_ID));
		funcs.put("pow", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opPow, SCOPE_ID), SCOPE_ID));
		funcs.put("div", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opDiv, SCOPE_ID), SCOPE_ID));
		funcs.put("truncDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opTruncDiv, SCOPE_ID), SCOPE_ID));
		funcs.put("floorDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opFloorDiv, SCOPE_ID), SCOPE_ID));
		funcs.put("ceilDiv", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opCeilDiv, SCOPE_ID), SCOPE_ID));
		funcs.put("mod", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opMod, SCOPE_ID), SCOPE_ID));
		funcs.put("and", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opAnd, SCOPE_ID), SCOPE_ID));
		funcs.put("or", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opOr, SCOPE_ID), SCOPE_ID));
		funcs.put("xor", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opXor, SCOPE_ID), SCOPE_ID));
		funcs.put("not", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(unaryOperationHelper(argumentList, DataObject::opNot, SCOPE_ID), SCOPE_ID));
		funcs.put("lshift", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opLshift, SCOPE_ID), SCOPE_ID));
		funcs.put("rshift", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opRshift, SCOPE_ID), SCOPE_ID));
		funcs.put("rzshift", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opRzshift, SCOPE_ID), SCOPE_ID));
		funcs.put("getItem", (argumentList, SCOPE_ID) -> throwErrorOnNullOrErrorTypeHelper(binaryOperationHelper(argumentList, DataObject::opGetItem, SCOPE_ID), SCOPE_ID));
		
		//Condition operator functions
		funcs.put("conNot", (argumentList, SCOPE_ID) -> unaryFromBooleanValueInvertedOperationHelper(argumentList, DataObject::toBoolean, SCOPE_ID));
		funcs.put("conAnd", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, (dataObject1, dataObject2) ->
				dataObject1.toBoolean() && dataObject2.toBoolean(), SCOPE_ID));
		funcs.put("conOr", (argumentList, SCOPE_ID) -> binaryFromBooleanValueOperationHelper(argumentList, (dataObject1, dataObject2) ->
		dataObject1.toBoolean() || dataObject2.toBoolean(), SCOPE_ID));
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
		funcs.put("rand", (argumentList, SCOPE_ID) -> new DataObject().setInt(LangInterpreter.RAN.nextInt(interpreter.data.get(SCOPE_ID).var.get("$LANG_RAND_MAX").getInt())));
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
			
			List<DataObject> argsA = new LinkedList<>();
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combA", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combA2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combA3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA.add(d);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combA4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA.add(d);
			argsA.add(e);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
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
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(d);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
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
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combB", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combB2", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combB3", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			List<DataObject> argsB3 = new LinkedList<>();
			argsB3.add(e);
			DataObject retB3 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB3, SCOPE_ID);
			argsA.add(retB3 == null?new DataObject().setVoid():retB3);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combBN", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0, 1}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 2;i < args.size();i++) {
				List<DataObject> argsB = new LinkedList<>();
				argsB.add(args.get(i));
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
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
				List<DataObject> argsB = new LinkedList<>();
				argsB.add(ele);
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combBX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(d);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combBZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0, 1}, false, false, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i > 1;i--) {
				List<DataObject> argsB = new LinkedList<>();
				argsB.add(args.get(i));
				DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
				argsA.add(retB == null?new DataObject().setVoid():retB);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combC0", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combC1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combC", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combC3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(d);
			argsA.add(c);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combC4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(e);
			argsA.add(d);
			argsA.add(c);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combCX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(d);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combD", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 2}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			argsC.add(e);
			argsC = LangUtils.separateArgumentsWithArgumentSeparators(argsC);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combEX", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(e);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combF1", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(b);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
		}));
		funcs.put("combF", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(b);
			argsC.add(a);
			argsC = LangUtils.separateArgumentsWithArgumentSeparators(argsC);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
		}));
		funcs.put("combF3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {3}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(c);
			argsD.add(b);
			argsD.add(a);
			argsD = LangUtils.separateArgumentsWithArgumentSeparators(argsD);
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, SCOPE_ID);
		}));
		funcs.put("combG", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(d);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combH", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combHB", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, SCOPE_ID);
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			List<DataObject> argsB3 = new LinkedList<>();
			argsB3.add(c);
			DataObject retB3 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB3, SCOPE_ID);
			argsA.add(retB3 == null?new DataObject().setVoid():retB3);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combHX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(b);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combI", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combJ", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA1 = new LinkedList<>();
			argsA1.add(b);
			List<DataObject> argsA2 = new LinkedList<>();
			argsA2.add(d);
			argsA2.add(c);
			argsA2 = LangUtils.separateArgumentsWithArgumentSeparators(argsA2);
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA2, SCOPE_ID);
			argsA1.add(retA2 == null?new DataObject().setVoid():retA2);
			argsA1 = LangUtils.separateArgumentsWithArgumentSeparators(argsA1);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA1, SCOPE_ID);
		}));
		funcs.put("combJX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA1 = new LinkedList<>();
			argsA1.add(b);
			List<DataObject> argsA2 = new LinkedList<>();
			argsA2.add(c);
			argsA2.add(d);
			argsA2 = LangUtils.separateArgumentsWithArgumentSeparators(argsA2);
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA2, SCOPE_ID);
			argsA1.add(retA2 == null?new DataObject().setVoid():retA2);
			argsA1 = LangUtils.separateArgumentsWithArgumentSeparators(argsA1);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA1, SCOPE_ID);
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
		funcs.put("combKI", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			return b;
		}));
		funcs.put("combKX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			return c;
		}));
		funcs.put("combL", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combL2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			argsB.add(c);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combL3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			argsB.add(c);
			argsB.add(d);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combL4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			argsB.add(c);
			argsB.add(d);
			argsB.add(e);
			argsB = LangUtils.separateArgumentsWithArgumentSeparators(argsB);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combM", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(a, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combM2", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			argsA.add(b);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combM3", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			argsA.add(b);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combM4", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			argsA.add(b);
			argsA.add(c);
			argsA.add(d);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combM5", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			argsA.add(b);
			argsA.add(c);
			argsA.add(d);
			argsA.add(e);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combO", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combO2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combO3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA.add(d);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combO4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(c);
			argsA.add(d);
			argsA.add(e);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combP", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combP3", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1, 2, 3}, (Combinator5ArgFunction)(a, b, c, d, e, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(e);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(e);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(e);
			DataObject retD = interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, SCOPE_ID);
			argsA.add(retD == null?new DataObject().setVoid():retD);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combPN", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = 2;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(b);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
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
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(b);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combPX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combPZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(2, new int[] {0}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			DataObject b = args.get(1);
			
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			for(int i = args.size() - 1;i > 1;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(b);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
				argsA.add(retN == null?new DataObject().setVoid():retN);
			}
			argsA = LangUtils.separateArgumentsWithArgumentSeparators(argsA);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combQ", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
		}));
		funcs.put("combQN", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			DataObject ret = a;
			for(int i = 1;i < args.size();i++) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(ret);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
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
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(ret);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
				ret = retN == null?new DataObject().setVoid():retN;
			}
			
			return ret;
		}));
		funcs.put("combQX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1, 2}, (Combinator3ArgFunction)(a, b, c, SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, SCOPE_ID);
			argsC.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, SCOPE_ID);
		}));
		funcs.put("combQZ", combinatorFunctionInfiniteExternalFunctionObjectHelper(1, new int[] {}, false, true, (args, SCOPE_ID) -> {
			DataObject a = args.get(0);
			
			DataObject ret = a;
			for(int i = args.size() - 1;i > 0;i--) {
				DataObject n = args.get(i);
				
				FunctionPointerObject nFunc = n.getFunctionPointer();
				
				List<DataObject> argsN = new LinkedList<>();
				argsN.add(ret);
				DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, SCOPE_ID);
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
		funcs.put("copyAfterFP", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
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
			if(oldData != null)
				oldData.setArray(arr);
			else if(arrPointerObject == null)
				return new DataObject().setArray(arr);
			else
				interpreter.data.get(SCOPE_ID).var.put(arrPtr, new DataObject().setArray(arr).setVariableName(arrPtr));
			
			return null;
		});
		funcs.put("arrayOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> elements = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			elements = elements.stream().map(DataObject::new).collect(Collectors.toList());
			
			return new DataObject().setArray(elements.toArray(new DataObject[0]));
		});
		funcs.put("arraySet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "2 or (len + 1) arguments needed", SCOPE_ID);
			if(combinedArgumentList.size() > arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or (len + 1)"), SCOPE_ID);
			
			Iterator<DataObject> combinedArgumentIterator = combinedArgumentList.iterator();
			for(int i = 0;i < arr.length;i++)
				arr[i] = new DataObject(combinedArgumentIterator.next());
			
			return null;
		});
		funcs.put("arrayGet", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			return new DataObject(Arrays.stream(arrPointerObject.getArray()).map(DataObject::getText).collect(Collectors.joining(", ")));
		});
		funcs.put("arrayCountOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			return new DataObject().setInt(arrPointerObject.getArray().length);
		});
		funcs.put("arrayDistinctValuesOf", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
		funcs.put("arrayMap", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			for(int i = 0;i < arr.length;i++) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(arr[i]);
				arr[i] = new DataObject(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID));
			}
			
			return null;
		});
		funcs.put("arrayMapToOne", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject currentValueObject = combinedArgumentList.get(1);
			DataObject funcPointerObject = combinedArgumentList.get(2);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(currentValueObject);
				argumentListFuncCall.add(ele);
				argumentListFuncCall = LangUtils.separateArgumentsWithArgumentSeparators(argumentListFuncCall);
				currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID);
			}
			
			return currentValueObject;
		});
		funcs.put("arrayForEach", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(ele);
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("arrayEnumerate", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(new DataObject().setInt(i));
				argumentListFuncCall.add(arr[i]);
				argumentListFuncCall = LangUtils.separateArgumentsWithArgumentSeparators(argumentListFuncCall);
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("arrayMatchEvery", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			return new DataObject().setBoolean(Arrays.stream(arr).allMatch(ele -> {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(ele);
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("arrayMatchAny", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			return new DataObject().setBoolean(Arrays.stream(arr).anyMatch(ele -> {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(ele);
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("arrayMatchNon", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			DataObject funcPointerObject = combinedArgumentList.get(1);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			return new DataObject().setBoolean(Arrays.stream(arr).noneMatch(ele -> {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(ele);
				return interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID).getBoolean();
			}));
		});
		funcs.put("randChoice", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() == 0)
				return new DataObject().setVoid();
			
			DataObject arrPointerObject = combinedArgumentList.get(0);
			if(arrPointerObject.getType() == DataType.ARRAY) {
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of an array"), SCOPE_ID);
				
				DataObject[] arr = arrPointerObject.getArray();
				return arr.length == 0?null:arr[LangInterpreter.RAN.nextInt(arr.length)];
			}
			
			return combinedArgumentList.size() == 0?null:combinedArgumentList.get(LangInterpreter.RAN.nextInt(combinedArgumentList.size()));
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
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
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
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
				List<DataObject> funcArgs = new LinkedList<>();
				funcArgs.add(new DataObject().setArray(permutationArr));
				funcArgs.add(new DataObject().setInt(permutationNumber++));
				funcArgs = LangUtils.separateArgumentsWithArgumentSeparators(funcArgs);
				
				if(interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), funcArgs, SCOPE_ID).getBoolean())
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
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
				if(combinedArgumentList.size() < 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				if(combinedArgumentList.size() > 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
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
				return "func.clearVar";
			}
		});
	}
	private void addPredefinedLangTestFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("langTestUnit", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addUnit(textObject.getText());
			
			return null;
		});
		funcs.put("langTestSubUnit", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			if(textObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			try {
				interpreter.langTestStore.addSubUnit(textObject.getText());
			}catch(IllegalStateException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, e.getMessage(), SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("langTestAssertError", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject errorObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			InterpretingError langErrno = interpreter.getAndClearErrnoErrorObject(SCOPE_ID);
			InterpretingError expectedError = errorObject.getError().getInterprettingError();
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultError(langErrno == expectedError, messageObject == null?null:messageObject.getText(), langErrno, expectedError));
			
			return null;
		});
		funcs.put("langTestAssertEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultEquals(actualValueObject.isEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotEquals(!actualValueObject.isEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertLessThan", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThan(actualValueObject.isLessThan(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertLessThanOrEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThanOrEquals(actualValueObject.isLessThanOrEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertGreaterThan", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThan(actualValueObject.isGreaterThan(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertGreaterThanOrEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThanOrEquals(actualValueObject.isGreaterThanOrEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject,
					expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStrictEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictEquals(actualValueObject.isStrictEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStrictNotEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictNotEquals(!actualValueObject.isStrictEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertTranslationValueEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueEquals(translationValue != null && translationValue.equals(expectedValueObject.getText()),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("langTestAssertTranslationValueNotEquals", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			if(combinedArgumentList.size() > 3)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject expectedValueObject = combinedArgumentList.get(1);
			DataObject messageObject = combinedArgumentList.size() < 3?null:combinedArgumentList.get(2);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueNotEquals(translationValue != null && !translationValue.equals(expectedValueObject.getText()),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("langTestAssertTranslationKeyFound", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyFound(translationValue != null, messageObject == null?null:messageObject.getText(),
					translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("langTestAssertTranslationKeyNotFound", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject translationKey = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyNotFound(translationValue == null, messageObject == null?null:messageObject.getText(),
					translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("langTestAssertNull", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNull(actualValueObject.getType() == DataType.NULL, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotNull", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotNull(actualValueObject.getType() != DataType.NULL, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertVoid", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultVoid(actualValueObject.getType() == DataType.VOID, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotVoid", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotVoid(actualValueObject.getType() != DataType.VOID, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultFinal(actualValueObject.isFinalData(), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotFinal", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotFinal(!actualValueObject.isFinalData(), messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
						
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStatic(actualValueObject.isStaticData(), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotStatic", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject actualValueObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotStatic(!actualValueObject.isStaticData(), messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertThrow", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject expectedThrowObject= combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			if(expectedThrowObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			InterpretingError expectedError = expectedThrowObject.getError().getInterprettingError();
			
			interpreter.langTestExpectedReturnValueScopeID = SCOPE_ID;
			interpreter.langTestExpectedThrowValue = expectedError;
			interpreter.langTestMessageForLastTestResult = messageObject == null?null:messageObject.getText();
			
			return null;
		});
		funcs.put("langTestAssertReturn", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			if(combinedArgumentList.size() > 2)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			DataObject expectedReturnObject = combinedArgumentList.get(0);
			DataObject messageObject = combinedArgumentList.size() < 2?null:combinedArgumentList.get(1);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestExpectedReturnValueScopeID = SCOPE_ID;
			interpreter.langTestExpectedReturnValue = expectedReturnObject;
			interpreter.langTestMessageForLastTestResult = messageObject == null?null:messageObject.getText();
			
			return null;
		});
		funcs.put("langTestAssertNoReturn", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			if(combinedArgumentList.size() < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, "0 or 1"), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "0 or 1"), SCOPE_ID);
			
			DataObject messageObject = combinedArgumentList.size() < 1?null:combinedArgumentList.get(0);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
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
		DataObject langFileNameObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
		if(langFileNameObject.getType() != DataType.TEXT)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
		
		List<DataObject> langArgsList = getAllArguments(argumentList);
		String[] langArgs = new String[langArgsList.size()];
		for(int i = 0;i < langArgsList.size();i++)
			langArgs[i] = langArgsList.get(i).getText();
		
		String langFileName = langFileNameObject.getText();
		if(!langFileName.endsWith(".lang"))
			return interpreter.setErrnoErrorObject(InterpretingError.NO_LANG_FILE, SCOPE_ID);
		
		String absolutePath;
		if(new File(langFileName).isAbsolute())
			absolutePath = langFileName;
		else
			absolutePath = interpreter.getCurrentCallStackElement().getLangPath() + File.separator + langFileName;
		
		final int NEW_SCOPE_ID = SCOPE_ID + 1;
		
		String langPathTmp = absolutePath;
		langPathTmp = interpreter.langPlatformAPI.getLangPath(langPathTmp);
		
		//Update call stack
		interpreter.pushStackElement(new StackElement(langPathTmp, interpreter.langPlatformAPI.getLangFileName(langFileName), null));
		
		//Create an empty data map
		interpreter.createDataMap(NEW_SCOPE_ID, langArgs);
		
		try(BufferedReader reader = interpreter.langPlatformAPI.getLangReader(absolutePath)) {
			interpreter.interpretLines(reader, NEW_SCOPE_ID);
		}catch(IOException e) {
			interpreter.data.remove(NEW_SCOPE_ID);
			return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
		}finally {
			//Update call stack
			interpreter.popStackElement();
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
	}
}