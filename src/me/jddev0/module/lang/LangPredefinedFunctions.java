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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.VarPointerObject;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangInterpreter.StackElement;
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
	private static final int FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND = -3;
	private static final int FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS = -4;
	
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
	
	private DataObject unaryFromBooleanValueOperationHelper(List<DataObject> argumentList, Function<DataObject, Boolean> operation, final int SCOPE_ID) {
		DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
		if(argumentList.size() > 0) //Not 1 argument
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
		
		return new DataObject().setBoolean(operation.apply(dataObject));
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), SCOPE_ID);
		}
		
		LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
			List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
			
			List<DataObject> args = new LinkedList<>();
			args.addAll(outerArgs);
			args.addAll(innerArgs);
			
			if(args.size() > argumentCount)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, argumentCount), INNER_SCOPE_ID);
			
			if(args.size() < argumentCount)
				return combinatorFunctionRecursionHelper(args, argumentCount, functionPointerIndices, combinatorFunc, INNER_SCOPE_ID);
			
			for(int i:functionPointerIndices) {
				if(args.size() > i && args.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), SCOPE_ID);
			}
			
			return combinatorFunc.apply(args, INNER_SCOPE_ID);
		};
		if(argumentCount > outerArgs.size())
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		else
			return func.callFunc(new ArrayList<>(), SCOPE_ID);
	}
	
	/**
	 * @param argumentList The argument list of the function call without the format argument (= argument at index 0). Used data objects will be removed from the list
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
			DataObject dataObject = sizeArgumentIndex == null?LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true):fullArgumentList.get(sizeArgumentIndex);
			Number number = dataObject.toNumber();
			if(number == null)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
			
			size = number.intValue();
			if(size < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
		}
		if(decimalPlacesInArgument) {
			DataObject dataObject = decimalPlacesCountIndex == null?LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true):fullArgumentList.get(decimalPlacesCountIndex);
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
			return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
		DataObject dataObject = formatType == 'n'?null:(valueSpecifiedIndex == null?LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true):fullArgumentList.get(valueSpecifiedIndex));
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
		List<DataObject> fullArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
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
				DataObject pointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
				if(argumentList.size() > 0) //Not 1 argument
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
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
			DataObject pointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject errorObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.ERROR.name()), SCOPE_ID);
			
			return new DataObject(errorObject.getError().getErrtxt());
		});
		funcs.put("errorCode", (argumentList, SCOPE_ID) -> {
			DataObject errorObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			if(errorObject.getType() != DataType.ERROR)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format(ARGUMENT_TYPE_FORMAT, "", DataType.ERROR.name()), SCOPE_ID);
			
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject loopFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject repeatCountObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Loop function pointer is invalid", SCOPE_ID);
			
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
			DataObject loopFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject checkFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Loop function pointer is invalid", SCOPE_ID);
			if(checkFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Check function pointer is invalid", SCOPE_ID);
			
			FunctionPointerObject loopFunc = loopFunctionObject.getFunctionPointer();
			FunctionPointerObject checkFunc = checkFunctionObject.getFunctionPointer();
			
			while(interpreter.callFunctionPointer(checkFunc, checkFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID).getBoolean())
				interpreter.callFunctionPointer(loopFunc, loopFunctionObject.getVariableName(), new ArrayList<>(), SCOPE_ID);
			
			return null;
		});
		funcs.put("repeatUntil", (argumentList, SCOPE_ID) -> {
			DataObject loopFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject checkFunctionObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(loopFunctionObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Loop function pointer is invalid", SCOPE_ID);
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			return new DataObject(dataObject).setCopyStaticAndFinalModifiers(true).setFinalData(true);
		});
		funcs.put("isFinal", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			return new DataObject().setBoolean(dataObject.isFinalData());
		});
		funcs.put("makeStatic", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			return new DataObject(dataObject).setCopyStaticAndFinalModifiers(true).setStaticData(true);
		});
		funcs.put("isStatic", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			return new DataObject().setBoolean(dataObject.isStaticData());
		});
		funcs.put("pointerTo", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject dataTypeObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(dataTypeObject.getType() != DataType.TYPE)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "Second argument must be from type TYPE", SCOPE_ID);
			
			return new DataObject().setBoolean(dataObject.getType() == dataTypeObject.getTypeValue());
		});
		funcs.put("typeOf", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			
			DataObject logLevelObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = LangUtils.combineDataObjects(argumentList);
			if(messageObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
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
			Number maxCount = null;
			
			if(!argumentList.isEmpty()) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(argumentList.size() > 0) //Not 0 or 1 arguments
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "0 or 1"), SCOPE_ID);
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
			if(argumentList.size() == 0) //Not at least 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			DataObject formatObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject out = formatText(formatObject.getText(), argumentList, SCOPE_ID);
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
			if(argumentList.size() == 0) //Not at least 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			DataObject formatObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject out = formatText(formatObject.getText(), argumentList, SCOPE_ID);
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
			DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject baseObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(numberObject == null || baseObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
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
			DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject baseObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(numberObject == null || baseObject == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			dataObject = dataObject.convertToNumberAndCreateNewDataObject();
			if(dataObject.getType() == DataType.NULL)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			
			return dataObject;
		});
		funcs.put("ttoi", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			String str = textObject.getText();
			try {
				return new DataObject().setInt(Integer.parseInt(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("ttol", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			String str = textObject.getText();
			try {
				return new DataObject().setLong(Long.parseLong(str));
			}catch(NumberFormatException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
			}
		});
		funcs.put("ttof", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject charObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			if(charObject.getType() != DataType.CHAR)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_CHAR, SCOPE_ID);
			
			return new DataObject().setInt(charObject.getChar());
		});
		funcs.put("toChar", (argumentList, SCOPE_ID) -> {
			DataObject asciiValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Number asciiValue = asciiValueObject.toNumber();
			if(asciiValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			return new DataObject().setChar((char)asciiValue.intValue());
		});
		funcs.put("ttoc", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject regexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			String replacement = getArgumentListAsString(argumentList, false);
			if(replacement == null) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "Too few arguments (3 needed)", SCOPE_ID);
			
			return new DataObject(LangRegEx.replace(textObject.getText(), regexObject.getText(), replacement));
		});
		funcs.put("substring", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject startIndexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject endIndexObject;
			//3rd argument is optional
			if(argumentList.isEmpty())
				endIndexObject = null;
			else
				endIndexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
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
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			
			String txt = textObject.getText();
			int len = txt.length();
			int index = indexNumber.intValue();
			if(index < 0)
				index = len + index;
			
			if(index < 0 || index >= len)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return new DataObject().setChar(txt.charAt(index));
		});
		funcs.put("lpad", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject paddingTextObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject lenObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
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
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject paddingTextObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject lenObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
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
			if(argumentList.size() == 0) //Not at least 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			DataObject formatObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			return formatText(formatObject.getText(), argumentList, SCOPE_ID);
		});
		funcs.put("contains", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject containTextObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			return new DataObject().setBoolean(textObject.getText().contains(containTextObject.getText()));
		});
		funcs.put("startsWith", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject startsWithTextObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			return new DataObject().setBoolean(textObject.getText().startsWith(startsWithTextObject.getText()));
		});
		funcs.put("endsWith", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject endsWithTextObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			return new DataObject().setBoolean(textObject.getText().endsWith(endsWithTextObject.getText()));
		});
		funcs.put("matches", (argumentList, SCOPE_ID) -> {
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject matchTextObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			try {
				return new DataObject().setBoolean(LangRegEx.matches(textObject.getText(), matchTextObject.getText()));
			}catch(PatternSyntaxException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Invalid RegEx expression: " + e.getMessage(), SCOPE_ID);
			}
		});
		funcs.put("repeatText", (argumentList, SCOPE_ID) -> {
			DataObject countObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			
			if(argumentList.size() == 0) //Not at least 2 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			DataObject textObject = LangUtils.combineDataObjects(argumentList);
			
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
			DataObject textObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			String text = textObject.getText();
			DataObject[] arr = arrPointerObject.getArray();
			
			return new DataObject(Arrays.stream(arr).map(DataObject::getText).collect(Collectors.joining(text)));
		});
		funcs.put("split", (argumentList, SCOPE_ID) -> {
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
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "3 or 4"), SCOPE_ID);
			
			String[] arrTmp;
			
			if(maxSplitCountObject == null) {
				arrTmp = LangRegEx.split(textObject.getText(), regexObject.getText());
			}else {
				Number maxSplitCount = maxSplitCountObject.toNumber();
				if(maxSplitCount == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
				
				arrTmp = LangRegEx.split(textObject.getText(), regexObject.getText(), maxSplitCount.intValue());
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
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			String value = dataObject.toText();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject(value), SCOPE_ID);
		});
		funcs.put("char", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Character value = dataObject.toChar();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setChar(value), SCOPE_ID);
		});
		funcs.put("int", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Integer value = dataObject.toInt();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setInt(value), SCOPE_ID);
		});
		funcs.put("long", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Long value = dataObject.toLong();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setLong(value), SCOPE_ID);
		});
		funcs.put("float", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Float value = dataObject.toFloat();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setFloat(value), SCOPE_ID);
		});
		funcs.put("double", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			Double value = dataObject.toDouble();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setDouble(value), SCOPE_ID);
		});
		funcs.put("array", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			DataObject[] value = dataObject.toArray();
			return throwErrorOnNullOrErrorTypeHelper(value == null?null:new DataObject().setArray(value), SCOPE_ID);
		});
		funcs.put("bool", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			return new DataObject().setBoolean(dataObject.toBoolean());
		});
		funcs.put("number", (argumentList, SCOPE_ID) -> {
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			int sum = 0;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			int prod = 1;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			long sum = 0L;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			long prod = 1L;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			float sum = 0.f;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			float prod = 1.f;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			double sum = 0.d;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			double prod = 1.d;
			
			while(argumentList.size() > 0) {
				DataObject numberObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
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
			if(argumentList.size() == 0) //Not at least 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			DataObject min = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			while(argumentList.size() > 0) {
				DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(dataObject.isLessThan(min))
					min = dataObject;
			}
			
			return min;
		});
		funcs.put("max", (argumentList, SCOPE_ID) -> {
			if(argumentList.size() == 0) //Not at least 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			DataObject min = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			while(argumentList.size() > 0) {
				DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(dataObject.isGreaterThan(min))
					min = dataObject;
			}
			
			return min;
		});
	}
	private void addPredefinedCombinatorFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
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
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, SCOPE_ID);
		}));
		funcs.put("combA3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combA4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(e);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combAN", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(outerArgs.size() > 0 && outerArgs.get(0).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 1 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				
				if(a.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				
				FunctionPointerObject aFunc = a.getFunctionPointer();
				
				List<DataObject> argsA = new LinkedList<>();
				argsA.addAll(args);
				argsA.remove(0);
				for(int i = argsA.size() - 1;i > 0;i--)
					argsA.add(i, new DataObject().setArgumentSeparator(", "));
				
				return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combAX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combAZ", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(outerArgs.size() > 0 && outerArgs.get(0).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 1 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				
				if(a.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				
				FunctionPointerObject aFunc = a.getFunctionPointer();
				
				List<DataObject> argsA = new LinkedList<>();
				for(int i = args.size() - 1;i > 0;i--)
					argsA.add(args.get(i));
				for(int i = argsA.size() - 1;i > 0;i--)
					argsA.add(i, new DataObject().setArgumentSeparator(", "));
				
				return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combB", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combB2", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combB3", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			List<DataObject> argsB3 = new LinkedList<>();
			argsB3.add(e);
			DataObject retB3 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB3, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB3 == null?new DataObject().setVoid():retB3);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combBN", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(outerArgs.size() > 0 && outerArgs.get(0).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), SCOPE_ID);
			if(outerArgs.size() > 1 && outerArgs.get(1).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 2 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				DataObject b = args.get(1);
				
				if(a.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				if(b.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				
				FunctionPointerObject aFunc = a.getFunctionPointer();
				FunctionPointerObject bFunc = b.getFunctionPointer();
				
				List<DataObject> argsA = new LinkedList<>();
				for(int i = 2;i < args.size();i++) {
					List<DataObject> argsB = new LinkedList<>();
					argsB.add(args.get(i));
					DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
					argsA.add(retB == null?new DataObject().setVoid():retB);
				}
				for(int i = argsA.size() - 1;i > 0;i--)
					argsA.add(i, new DataObject().setArgumentSeparator(", "));
				
				return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combBX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combBZ", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(outerArgs.size() > 0 && outerArgs.get(0).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), SCOPE_ID);
			if(outerArgs.size() > 1 && outerArgs.get(1).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 2 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				DataObject b = args.get(1);
				
				if(a.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				if(b.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "2 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				
				FunctionPointerObject aFunc = a.getFunctionPointer();
				FunctionPointerObject bFunc = b.getFunctionPointer();
				
				List<DataObject> argsA = new LinkedList<>();
				for(int i = args.size() - 1;i > 1;i--) {
					List<DataObject> argsB = new LinkedList<>();
					argsB.add(args.get(i));
					DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
					argsA.add(retB == null?new DataObject().setVoid():retB);
				}
				for(int i = argsA.size() - 1;i > 0;i--)
					argsA.add(i, new DataObject().setArgumentSeparator(", "));
				
				return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combC", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combC3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(d);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combC4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(e);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combCX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combD", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 2}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retC == null?new DataObject().setVoid():retC);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combE", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 2}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			argsC.add(new DataObject().setArgumentSeparator(", "));
			argsC.add(e);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retC == null?new DataObject().setVoid():retC);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combEX", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(e);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combF", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(b);
			argsC.add(new DataObject().setArgumentSeparator(", "));
			argsC.add(a);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
		}));
		funcs.put("combF3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {3}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(c);
			argsD.add(new DataObject().setArgumentSeparator(", "));
			argsD.add(b);
			argsD.add(new DataObject().setArgumentSeparator(", "));
			argsD.add(a);
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, INNER_SCOPE_ID);
		}));
		funcs.put("combG", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(d);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combH", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combHB", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			List<DataObject> argsB3 = new LinkedList<>();
			argsB3.add(c);
			DataObject retB3 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB3, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB3 == null?new DataObject().setVoid():retB3);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combHX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combI", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {}, (Combinator1ArgFunction)(a, INNER_SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combJ", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA1 = new LinkedList<>();
			argsA1.add(b);
			List<DataObject> argsA2 = new LinkedList<>();
			argsA2.add(d);
			argsA2.add(new DataObject().setArgumentSeparator(", "));
			argsA2.add(c);
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA2, INNER_SCOPE_ID);
			argsA1.add(new DataObject().setArgumentSeparator(", "));
			argsA1.add(retA2 == null?new DataObject().setVoid():retA2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA1, INNER_SCOPE_ID);
		}));
		funcs.put("combJX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA1 = new LinkedList<>();
			argsA1.add(b);
			List<DataObject> argsA2 = new LinkedList<>();
			argsA2.add(c);
			argsA2.add(new DataObject().setArgumentSeparator(", "));
			argsA2.add(d);
			DataObject retA2 = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA2, INNER_SCOPE_ID);
			argsA1.add(new DataObject().setArgumentSeparator(", "));
			argsA1.add(retA2 == null?new DataObject().setVoid():retA2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA1, INNER_SCOPE_ID);
		}));
		funcs.put("combK", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combK3", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			return a;
		}));
		funcs.put("combKI", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			return b;
		}));
		funcs.put("combL", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combL2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combL3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combL4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(b);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(e);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combM", combinatorFunctionExternalFunctionObjectHelper(1, new int[] {0}, (Combinator1ArgFunction)(a, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combM2", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combM3", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(a);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combO", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0, 1}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combO2", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combO3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combP", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retC == null?new DataObject().setVoid():retC);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combP3", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1, 2, 3}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(e);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(e);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retC == null?new DataObject().setVoid():retC);
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(e);
			DataObject retD = interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retD == null?new DataObject().setVoid():retD);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combPN", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(outerArgs.size() > 0 && outerArgs.get(0).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 2 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				DataObject b = args.get(1);
				
				if(a.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				
				FunctionPointerObject aFunc = a.getFunctionPointer();
				
				List<DataObject> argsA = new LinkedList<>();
				for(int i = 2;i < args.size();i++) {
					DataObject n = args.get(i);
					
					if(n.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
					
					FunctionPointerObject nFunc = n.getFunctionPointer();
					
					List<DataObject> argsN = new LinkedList<>();
					argsN.add(b);
					DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, INNER_SCOPE_ID);
					argsA.add(retN == null?new DataObject().setVoid():retN);
				}
				for(int i = argsA.size() - 1;i > 0;i--)
					argsA.add(i, new DataObject().setArgumentSeparator(", "));
				
				return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combPX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1, 2}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(d);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
			argsA.add(retC == null?new DataObject().setVoid():retC);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combPZ", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(outerArgs.size() > 0 && outerArgs.get(0).getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 2 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				DataObject b = args.get(1);
				
				if(a.getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, "1 ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
				
				FunctionPointerObject aFunc = a.getFunctionPointer();
				
				List<DataObject> argsA = new LinkedList<>();
				for(int i = args.size() - 1;i > 1;i--) {
					DataObject n = args.get(i);
					
					if(n.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
					
					FunctionPointerObject nFunc = n.getFunctionPointer();
					
					List<DataObject> argsN = new LinkedList<>();
					argsN.add(b);
					DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, INNER_SCOPE_ID);
					argsA.add(retN == null?new DataObject().setVoid():retN);
				}
				for(int i = argsA.size() - 1;i > 0;i--)
					argsA.add(i, new DataObject().setArgumentSeparator(", "));
				
				return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combQ", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			DataObject retA = interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
			argsB.add(retA == null?new DataObject().setVoid():retA);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combQN", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			for(int i = 1;i < outerArgs.size();i++)
				if(outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 1 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				
				DataObject ret = a;
				for(int i = 1;i < args.size();i++) {
					DataObject n = args.get(i);
					
					if(n.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
					
					FunctionPointerObject nFunc = n.getFunctionPointer();
					
					List<DataObject> argsN = new LinkedList<>();
					argsN.add(ret);
					DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, INNER_SCOPE_ID);
					ret = retN == null?new DataObject().setVoid():retN;
				}
				
				return ret;
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combQX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1, 2}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsC.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
		}));
		funcs.put("combQZ", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			for(int i = 1;i < outerArgs.size();i++)
				if(outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 1 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject a = args.get(0);
				
				DataObject ret = a;
				for(int i = args.size() - 1;i > 0;i--) {
					DataObject n = args.get(i);
					
					if(n.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
					
					FunctionPointerObject nFunc = n.getFunctionPointer();
					
					List<DataObject> argsN = new LinkedList<>();
					argsN.add(ret);
					DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, INNER_SCOPE_ID);
					ret = retN == null?new DataObject().setVoid():retN;
				}
				
				return ret;
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combR", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combR3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combRX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combS", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combSX", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0, 1}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combT", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {1}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combT3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combT4", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {1}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(e);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combTN", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			for(int i = 0;i < outerArgs.size() - 1;i++)
				if(outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 1 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject z = args.get(args.size() - 1);
				
				DataObject ret = z;
				for(int i = 0;i < args.size() - 1;i++) {
					DataObject n = args.get(i);
					
					if(n.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
					
					FunctionPointerObject nFunc = n.getFunctionPointer();
					
					List<DataObject> argsN = new LinkedList<>();
					argsN.add(ret);
					DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, INNER_SCOPE_ID);
					ret = retN == null?new DataObject().setVoid():retN;
				}
				
				return ret;
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combTX", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combTZ", (argumentList, SCOPE_ID) -> {
			List<DataObject> outerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			for(int i = 0;i < outerArgs.size() - 1;i++)
				if(outerArgs.get(i).getType() != DataType.FUNCTION_POINTER)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), SCOPE_ID);
			
			final int minArgsLeft = 1 - outerArgs.size();
			LangExternalFunctionObject func = (LangExternalFunctionObject)(innerArgumentList, INNER_SCOPE_ID) -> {
				List<DataObject> innerArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(innerArgumentList);
				if(innerArgs.size() < minArgsLeft)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(NOT_ENOUGH_ARGUMENTS_FORMAT, minArgsLeft), INNER_SCOPE_ID);
				
				List<DataObject> args = new LinkedList<>();
				args.addAll(outerArgs);
				args.addAll(innerArgs);
				
				DataObject z = args.get(args.size() - 1);
				
				DataObject ret = z;
				for(int i = args.size() - 2;i > -1;i--) {
					DataObject n = args.get(i);
					
					if(n.getType() != DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, String.format(ARGUMENT_TYPE_FORMAT, (i + 1) + " ", "FUNCTION_POINTER"), INNER_SCOPE_ID);
					
					FunctionPointerObject nFunc = n.getFunctionPointer();
					
					List<DataObject> argsN = new LinkedList<>();
					argsN.add(ret);
					DataObject retN = interpreter.callFunctionPointer(nFunc, n.getVariableName(), argsN, INNER_SCOPE_ID);
					ret = retN == null?new DataObject().setVoid():retN;
				}
				
				return ret;
			};
			return new DataObject().setFunctionPointer(new FunctionPointerObject(func));
		});
		funcs.put("combU", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 1, 2}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(e);
			DataObject retC = interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retC == null?new DataObject().setVoid():retC);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combUX", combinatorFunctionExternalFunctionObjectHelper(5, new int[] {0, 3, 4}, (Combinator5ArgFunction)(a, b, c, d, e, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject dFunc = d.getFunctionPointer();
			FunctionPointerObject eFunc = e.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(b);
			DataObject retD = interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, INNER_SCOPE_ID);
			argsA.add(retD == null?new DataObject().setVoid():retD);
			List<DataObject> argsE = new LinkedList<>();
			argsE.add(c);
			DataObject retE = interpreter.callFunctionPointer(eFunc, e.getVariableName(), argsE, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retE == null?new DataObject().setVoid():retE);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combV", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {2}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject cFunc = c.getFunctionPointer();
			
			List<DataObject> argsC = new LinkedList<>();
			argsC.add(a);
			argsC.add(new DataObject().setArgumentSeparator(", "));
			argsC.add(b);
			
			return interpreter.callFunctionPointer(cFunc, c.getVariableName(), argsC, INNER_SCOPE_ID);
		}));
		funcs.put("combV3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {3}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject dFunc = d.getFunctionPointer();
			
			List<DataObject> argsD = new LinkedList<>();
			argsD.add(a);
			argsD.add(new DataObject().setArgumentSeparator(", "));
			argsD.add(b);
			argsD.add(new DataObject().setArgumentSeparator(", "));
			argsD.add(c);
			
			return interpreter.callFunctionPointer(dFunc, d.getVariableName(), argsD, INNER_SCOPE_ID);
		}));
		funcs.put("combW", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combW3", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combW4", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {0}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(b);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combWB", combinatorFunctionExternalFunctionObjectHelper(3, new int[] {0}, (Combinator3ArgFunction)(a, b, c, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(c);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combWX", combinatorFunctionExternalFunctionObjectHelper(2, new int[] {1}, (Combinator2ArgFunction)(a, b, INNER_SCOPE_ID) -> {
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(a);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(a);
			
			return interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
		}));
		funcs.put("combX1", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX2", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX3", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(c);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX4", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(d);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX5", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(c);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(d);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(c);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX6", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			argsA.add(d);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			List<DataObject> argsB = new LinkedList<>();
			argsB.add(c);
			argsB.add(new DataObject().setArgumentSeparator(", "));
			argsB.add(d);
			DataObject retB = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB, INNER_SCOPE_ID);
			argsA.add(retB == null?new DataObject().setVoid():retB);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX7", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(d);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(c);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX8", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combX9", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(d);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(d);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(c);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(c);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combXA", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(c);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(d);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(c);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(d);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(c);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(d);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
		funcs.put("combXB", combinatorFunctionExternalFunctionObjectHelper(4, new int[] {0, 1}, (Combinator4ArgFunction)(a, b, c, d, INNER_SCOPE_ID) -> {
			FunctionPointerObject aFunc = a.getFunctionPointer();
			FunctionPointerObject bFunc = b.getFunctionPointer();
			
			List<DataObject> argsA = new LinkedList<>();
			List<DataObject> argsB1 = new LinkedList<>();
			argsB1.add(d);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(c);
			argsB1.add(new DataObject().setArgumentSeparator(", "));
			argsB1.add(d);
			DataObject retB1 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB1, INNER_SCOPE_ID);
			argsA.add(retB1 == null?new DataObject().setVoid():retB1);
			List<DataObject> argsB2 = new LinkedList<>();
			argsB2.add(c);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(d);
			argsB2.add(new DataObject().setArgumentSeparator(", "));
			argsB2.add(c);
			DataObject retB2 = interpreter.callFunctionPointer(bFunc, b.getVariableName(), argsB2, INNER_SCOPE_ID);
			argsA.add(new DataObject().setArgumentSeparator(", "));
			argsA.add(retB2 == null?new DataObject().setVoid():retB2);
			
			return interpreter.callFunctionPointer(aFunc, a.getVariableName(), argsA, INNER_SCOPE_ID);
		}));
	}
	private void addPredefinedFuncPtrFunctions(Map<String, LangPredefinedFunctionObject> funcs) {
		funcs.put("copyAfterFP", (argumentList, SCOPE_ID) -> {
			DataObject toPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject fromPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			DataObject arrPointerObject = null;
			DataObject lengthObject = null;
			
			DataObject dataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() > 0) {
				arrPointerObject = dataObject;
				
				lengthObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
				
				if(argumentList.size() > 0) //Not 1 or 2 arguments
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			}else {
				lengthObject = dataObject;
			}
			
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
			
			Number lenghtNumber = lengthObject.toNumber();
			if(lenghtNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.LENGTH_NAN, SCOPE_ID);
			int length = lenghtNumber.intValue();
			
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
			List<DataObject> elements = new LinkedList<>();
			
			while(argumentList.size() > 0)
				elements.add(new DataObject(LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true)));
			
			return new DataObject().setArray(elements.toArray(new DataObject[0]));
		});
		funcs.put("arraySet", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject valueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			arr[index] = new DataObject(valueObject);
			
			return null;
		});
		funcs.put("arraySetAll", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() == 0) //Not enough arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			DataObject valueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			if(argumentList.size() == 0) { //arraySetAll with one value
				for(int i = 0;i < arr.length;i++)
					arr[i] = new DataObject(valueObject);
				
				return null;
			}
			
			if(arr.length == 0) //Too many arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			arr[0] = valueObject;
			for(int i = 1;i < arr.length;i++) {
				arr[i] = new DataObject(LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true));
				
				if(argumentList.size() == 0 && i != arr.length - 1) //Not enough arguments
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			}
			
			if(argumentList.size() > 0) //Too many arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
			
			return null;
		});
		funcs.put("arrayGet", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject indexObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			Number indexNumber = indexObject.toNumber();
			if(indexNumber == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, SCOPE_ID);
			int index = indexNumber.intValue();
			
			DataObject[] arr = arrPointerObject.getArray();
			if(index < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			else if(index >= arr.length)
				return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
			
			return arr[index];
		});
		funcs.put("arrayGetAll", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			StringBuilder builder = new StringBuilder();
			
			for(DataObject ele:arrPointerObject.getArray()) {
				builder.append(ele.getText());
				builder.append(", ");
			}
			if(builder.length() > 0) //Remove last ", " only if at least 1 element is in array
				builder.delete(builder.length() - 2, builder.length()); //Remove leading ", "
			return new DataObject(builder.toString());
		});
		funcs.put("arrayCountOf", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject elementObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			long count = Arrays.stream(arr).filter(ele -> ele.isStrictEquals(elementObject)).count();
			return new DataObject().setLong(count);
		});
		funcs.put("arrayIndexOf", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject elementObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++)
				if(arr[i].isStrictEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayLastIndexOf", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject elementObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = arr.length - 1;i >= 0;i--)
				if(arr[i].isStrictEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayCountLike", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject elementObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			long count = Arrays.stream(arr).filter(ele -> ele.isEquals(elementObject)).count();
			return new DataObject().setLong(count);
		});
		funcs.put("arrayIndexLike", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject elementObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++)
				if(arr[i].isEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayLastIndexLike", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject elementObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = arr.length - 1;i >= 0;i--)
				if(arr[i].isEquals(elementObject))
					return new DataObject().setInt(i);
			
			return new DataObject().setInt(-1);
		});
		funcs.put("arrayLength", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			return new DataObject().setInt(arrPointerObject.getArray().length);
		});
		funcs.put("arrayDistinctValuesOf", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject currentValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 3), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(DataObject ele:arr) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(currentValueObject);
				argumentListFuncCall.add(new DataObject().setArgumentSeparator(", "));
				argumentListFuncCall.add(ele);
				currentValueObject = interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID);
			}
			
			return currentValueObject;
		});
		funcs.put("arrayForEach", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
			if(arrPointerObject.getType() != DataType.ARRAY)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
			
			if(funcPointerObject.getType() != DataType.FUNCTION_POINTER)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, SCOPE_ID);
			
			DataObject[] arr = arrPointerObject.getArray();
			for(int i = 0;i < arr.length;i++) {
				List<DataObject> argumentListFuncCall = new ArrayList<>();
				argumentListFuncCall.add(new DataObject().setInt(i));
				argumentListFuncCall.add(new DataObject().setArgumentSeparator(", "));
				argumentListFuncCall.add(arr[i]);
				interpreter.callFunctionPointer(funcPointerObject.getFunctionPointer(), funcPointerObject.getVariableName(), argumentListFuncCall, SCOPE_ID);
			}
			
			return null;
		});
		funcs.put("arrayEveryMatch", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
		funcs.put("arrayAnyMatch", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
		funcs.put("arrayNonMatch", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject funcPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 2), SCOPE_ID);
			
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
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(arrPointerObject.getType() == DataType.ARRAY) {
				if(argumentList.size() > 0) //Not 1 argument
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 for randChoice of an array"), SCOPE_ID);
				
				DataObject[] arr = arrPointerObject.getArray();
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
		funcs.put("arrayCombine", (argumentList, SCOPE_ID) -> {
			List<DataObject> combinedArrays = new LinkedList<>();
			
			while(argumentList.size() > 0) {
				DataObject arrayPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
				if(arrayPointerObject.getType() != DataType.ARRAY)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, SCOPE_ID);
				
				for(DataObject ele:arrayPointerObject.getArray())
					combinedArrays.add(ele);
			}
			
			return new DataObject().setArray(combinedArrays.toArray(new DataObject[0]));
		});
		funcs.put("arrayDelete", (argumentList, SCOPE_ID) -> {
			DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
			
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
				DataObject arrPointerObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
				if(argumentList.size() > 0) //Not 1 argument
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, 1), SCOPE_ID);
				
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
			DataObject errorObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
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
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultEquals(actualValueObject.isEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotEquals", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotEquals(!actualValueObject.isEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertLessThan", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThan(actualValueObject.isLessThan(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertLessThanOrEquals", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultLessThanOrEquals(actualValueObject.isLessThanOrEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertGreaterThan", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThan(actualValueObject.isGreaterThan(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertGreaterThanOrEquals", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultGreaterThanOrEquals(actualValueObject.isGreaterThanOrEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject,
					expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStrictEquals", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictEquals(actualValueObject.isStrictEquals(expectedValueObject), messageObject == null?null:messageObject.getText(),
					actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStrictNotEquals", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStrictNotEquals(!actualValueObject.isStrictEquals(expectedValueObject),
					messageObject == null?null:messageObject.getText(), actualValueObject, expectedValueObject));
			
			return null;
		});
		funcs.put("langTestAssertTranslationValueEquals", (argumentList, SCOPE_ID) -> {
			DataObject translationKey = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueEquals(translationValue != null && translationValue.equals(expectedValueObject.getText()),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("langTestAssertTranslationValueNotEquals", (argumentList, SCOPE_ID) -> {
			DataObject translationKey = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject expectedValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 2 or 3 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "2 or 3"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationValueNotEquals(translationValue != null && !translationValue.equals(expectedValueObject.getText()),
					messageObject == null?null:messageObject.getText(), translationKey.getText(), translationValue, expectedValueObject.getText()));
			
			return null;
		});
		funcs.put("langTestAssertTranslationKeyFound", (argumentList, SCOPE_ID) -> {
			DataObject translationKey = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyFound(translationValue != null, messageObject == null?null:messageObject.getText(),
					translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("langTestAssertTranslationKeyNotFound", (argumentList, SCOPE_ID) -> {
			DataObject translationKey = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			String translationValue = interpreter.getData().get(SCOPE_ID).lang.get(translationKey.getText());
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultTranslationKeyNotFound(translationValue == null, messageObject == null?null:messageObject.getText(),
					translationKey.getText(), translationValue));
			
			return null;
		});
		funcs.put("langTestAssertNull", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNull(actualValueObject.getType() == DataType.NULL, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotNull", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotNull(actualValueObject.getType() != DataType.NULL, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertVoid", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultVoid(actualValueObject.getType() == DataType.VOID, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotVoid", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotVoid(actualValueObject.getType() != DataType.VOID, messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertFinal", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultFinal(actualValueObject.isFinalData(), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotFinal", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotFinal(!actualValueObject.isFinalData(), messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertStatic", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultStatic(actualValueObject.isStaticData(), messageObject == null?null:messageObject.getText(),
					actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertNotStatic", (argumentList, SCOPE_ID) -> {
			DataObject actualValueObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestStore.addAssertResult(new LangTest.AssertResultNotStatic(!actualValueObject.isStaticData(), messageObject == null?null:messageObject.getText(), actualValueObject));
			
			return null;
		});
		funcs.put("langTestAssertThrow", (argumentList, SCOPE_ID) -> {
			DataObject expectedThrowObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
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
			DataObject expectedReturnObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, true);
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 or 2 arguments
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format(TOO_MANY_ARGUMENTS_FORMAT, "1 or 2"), SCOPE_ID);
			
			if(!interpreter.executionFlags.langTest)
				return interpreter.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "langTest functions can only be used if the langTest flag is true", SCOPE_ID);
			
			interpreter.langTestExpectedReturnValueScopeID = SCOPE_ID;
			interpreter.langTestExpectedReturnValue = expectedReturnObject;
			interpreter.langTestMessageForLastTestResult = messageObject == null?null:messageObject.getText();
			
			return null;
		});
		funcs.put("langTestAssertNoReturn", (argumentList, SCOPE_ID) -> {
			DataObject messageObject = null;
			if(argumentList.size() > 0)
				messageObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			
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