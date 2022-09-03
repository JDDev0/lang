package me.jddev0.module.lang;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;

/**
 * Lang-Module<br>
 * Lang operators definitions
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangOperators {
	@SuppressWarnings("unused")
	private final LangInterpreter interpreter;
	
	public LangOperators(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	//General operation functions
	/**
	 * For "@"
	 */
	public DataObject opLen(DataObject operand, final int SCOPE_ID) {
		switch(operand.getType()) {
			case ARRAY:
				return new DataObject().setInt(operand.getArray().length);
			case TEXT:
				return new DataObject().setInt(operand.getText().length());
			case CHAR:
				return new DataObject().setInt(1);
			
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
	/**
	 * For "^"
	 */
	public DataObject opDeepCopy(DataObject operand, final int SCOPE_ID) {
		switch(operand.getType()) {
			case ARRAY:
				DataObject[] arrCopy = new DataObject[operand.getArray().length];
				for(int i = 0;i < operand.getArray().length;i++) {
					arrCopy[i] = opDeepCopy(operand.getArray()[i], SCOPE_ID);
					if(arrCopy[i] == null)
						return null;
				}
				
				return new DataObject().setArray(arrCopy);
			
			case TEXT:
			case CHAR:
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return new DataObject(operand);
		}
		
		return null;
	}
	/**
	 * For "|||"
	 */
	public DataObject opConcat(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				return new DataObject(leftSideOperand.getInt() + rightSideOperand.getText());
			case LONG:
				return new DataObject(leftSideOperand.getLong() + rightSideOperand.getText());
			case FLOAT:
				return new DataObject(leftSideOperand.getFloat() + rightSideOperand.getText());
			case DOUBLE:
				return new DataObject(leftSideOperand.getDouble() + rightSideOperand.getText());
			case CHAR:
				return new DataObject(leftSideOperand.getChar() + rightSideOperand.getText());
			case TEXT:
				return new DataObject(leftSideOperand.getText() + rightSideOperand.getText());
			case ARRAY:
				if(rightSideOperand.getType() != DataType.ARRAY)
					return null;
				
				DataObject[] arrNew = new DataObject[leftSideOperand.getArray().length + rightSideOperand.getArray().length];
				for(int i = 0;i < leftSideOperand.getArray().length;i++)
					arrNew[i] = leftSideOperand.getArray()[i];
				for(int i = 0;i < rightSideOperand.getArray().length;i++)
					arrNew[leftSideOperand.getArray().length + i] = rightSideOperand.getArray()[i];
				
				return new DataObject().setArray(arrNew);
				
			case FUNCTION_POINTER:
				if(rightSideOperand.getType() != DataType.FUNCTION_POINTER)
					return null;
				
				final FunctionPointerObject aFunc = leftSideOperand.getFunctionPointer();
				final FunctionPointerObject bFunc = rightSideOperand.getFunctionPointer();
				return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> argsB = new LinkedList<>();
					DataObject retA = interpreter.callFunctionPointer(aFunc, leftSideOperand.getVariableName(), args, INNER_SCOPE_ID);
					argsB.add(retA == null?new DataObject().setVoid():retA);
					
					return interpreter.callFunctionPointer(bFunc, rightSideOperand.getVariableName(), argsB, INNER_SCOPE_ID);
				}));
			
			case ERROR:
			case VAR_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
	/**
	 * For "&lt;=&gt;"
	 */
	public DataObject opSpaceship(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		if(leftSideOperand.isLessThan(rightSideOperand))
			return new DataObject().setInt(-1);
		if(leftSideOperand.isEquals(rightSideOperand))
			return new DataObject().setInt(0);
		if(leftSideOperand.isGreaterThan(rightSideOperand))
			return new DataObject().setInt(1);
		
		return new DataObject().setNull();
	}
	
	//Math operation functions
	/**
	 * For "▲"
	 */
	public DataObject opInc(DataObject operand, final int SCOPE_ID) {
		switch(operand.getType()) {
			case INT:
				return new DataObject().setInt(operand.getInt() + 1);
			case LONG:
				return new DataObject().setLong(operand.getLong() + 1);
			case FLOAT:
				return new DataObject().setFloat(operand.getFloat() + 1.f);
			case DOUBLE:
				return new DataObject().setDouble(operand.getDouble() + 1.d);
			case CHAR:
				return new DataObject().setChar((char)(operand.getChar() + 1));
			
			case FUNCTION_POINTER:
				final FunctionPointerObject func = operand.getFunctionPointer();
				return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(args);
					if(combinedArgumentList.size() < 1)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Not enough arguments (%s needed)", 1), INNER_SCOPE_ID);
					if(combinedArgumentList.size() > 1)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Too many arguments (%s needed)", 1), INNER_SCOPE_ID);
					
					DataObject arrPointerObject = combinedArgumentList.get(0);
					
					if(arrPointerObject.getType() != DataType.ARRAY)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, INNER_SCOPE_ID);
					
					List<DataObject> argsFunc = new LinkedList<>(Arrays.asList(arrPointerObject.getArray()));
					argsFunc = LangUtils.separateArgumentsWithArgumentSeparators(argsFunc);
					return interpreter.callFunctionPointer(func, operand.getVariableName(), argsFunc, INNER_SCOPE_ID);
				}));
			
			case TEXT:
			case ARRAY:
			case ERROR:
			case VAR_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
	/**
	 * For "▼"
	 */
	public DataObject opDec(DataObject operand, final int SCOPE_ID) {
		switch(operand.getType()) {
			case INT:
				return new DataObject().setInt(operand.getInt() - 1);
			case LONG:
				return new DataObject().setLong(operand.getLong() - 1);
			case FLOAT:
				return new DataObject().setFloat(operand.getFloat() - 1.f);
			case DOUBLE:
				return new DataObject().setDouble(operand.getDouble() - 1.d);
			case CHAR:
				return new DataObject().setChar((char)(operand.getChar() - 1));
			
			case FUNCTION_POINTER:
				final FunctionPointerObject func = operand.getFunctionPointer();
				return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(args);
					
					List<DataObject> argsFunc = new LinkedList<>();
					argsFunc.add(new DataObject().setArray(combinedArgumentList.toArray(new DataObject[0])));
					return interpreter.callFunctionPointer(func, operand.getVariableName(), argsFunc, INNER_SCOPE_ID);
				}));
			
			case TEXT:
			case ARRAY:
			case ERROR:
			case VAR_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
	/**
	 * For "+"
	 */
	public DataObject opPos(DataObject operand, final int SCOPE_ID) {
		return new DataObject(operand);
	}
	/**
	 * For "-"
	 */
	public DataObject opInv(DataObject operand, final int SCOPE_ID) {
		switch(operand.getType()) {
			case INT:
				return new DataObject().setInt(-operand.getInt());
			case LONG:
				return new DataObject().setLong(-operand.getLong());
			case FLOAT:
				return new DataObject().setFloat(-operand.getFloat());
			case DOUBLE:
				return new DataObject().setDouble(-operand.getDouble());
			case CHAR:
				return new DataObject().setChar((char)(-operand.getChar()));
			case TEXT:
				return new DataObject(new StringBuilder(operand.getText()).reverse().toString());
			case ARRAY:
				DataObject[] arrInv = new DataObject[operand.getArray().length];
				int index = arrInv.length - 1;
				for(DataObject dataObject:operand.getArray())
					arrInv[index--] = dataObject;
				
				return new DataObject().setArray(arrInv);
			
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
	/**
	 * For "+"
	 */
	public DataObject opAdd(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() + rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getInt() + rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getInt() + rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getInt() + rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setInt(leftSideOperand.getInt() + rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setLong(leftSideOperand.getLong() + rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() + rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getLong() + rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getLong() + rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setLong(leftSideOperand.getLong() + rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case FLOAT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setFloat(leftSideOperand.getFloat() + rightSideOperand.getInt());
					case LONG:
						return new DataObject().setFloat(leftSideOperand.getFloat() + rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getFloat() + rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getFloat() + rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setFloat(leftSideOperand.getFloat() + rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case DOUBLE:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setDouble(leftSideOperand.getDouble() + rightSideOperand.getInt());
					case LONG:
						return new DataObject().setDouble(leftSideOperand.getDouble() + rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setDouble(leftSideOperand.getDouble() + rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getDouble() + rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setDouble(leftSideOperand.getDouble() + rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case CHAR:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getChar() + rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getChar() + rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getChar() + rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getChar() + rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setInt(leftSideOperand.getChar() + rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case TEXT:
				return new DataObject(leftSideOperand.getText() + rightSideOperand.getText());
			case ARRAY:
				DataObject[] arrNew = new DataObject[leftSideOperand.getArray().length + 1];
				for(int i = 0;i < leftSideOperand.getArray().length;i++)
					arrNew[i] = leftSideOperand.getArray()[i];
				
				arrNew[leftSideOperand.getArray().length] = new DataObject(rightSideOperand);
				return new DataObject().setArray(arrNew);
			
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
	/**
	 * For "-"
	 */
	public DataObject opSub(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() - rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getInt() - rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getInt() - rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getInt() - rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setInt(leftSideOperand.getInt() - rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setLong(leftSideOperand.getLong() - rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() - rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getLong() - rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getLong() - rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setLong(leftSideOperand.getLong() - rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case FLOAT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setFloat(leftSideOperand.getFloat() - rightSideOperand.getInt());
					case LONG:
						return new DataObject().setFloat(leftSideOperand.getFloat() - rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getFloat() - rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getFloat() - rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setFloat(leftSideOperand.getFloat() - rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case DOUBLE:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setDouble(leftSideOperand.getDouble() - rightSideOperand.getInt());
					case LONG:
						return new DataObject().setDouble(leftSideOperand.getDouble() - rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setDouble(leftSideOperand.getDouble() - rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getDouble() - rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setDouble(leftSideOperand.getDouble() - rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			case CHAR:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getChar() - rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getChar() - rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getChar() - rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getChar() - rightSideOperand.getDouble());
					case CHAR:
						return new DataObject().setInt(leftSideOperand.getChar() - rightSideOperand.getChar());
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return null;
				}
				return null;
			
			case TEXT:
			case ARRAY:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return null;
		}
		
		return null;
	}
}