package me.jddev0.module.lang;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.ErrorObject;
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
	/**
	 * For "*"
	 */
	public DataObject opMul(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() * rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getInt() * rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getInt() * rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getInt() * rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() * rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() * rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getLong() * rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getLong() * rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setFloat(leftSideOperand.getFloat() * rightSideOperand.getInt());
					case LONG:
						return new DataObject().setFloat(leftSideOperand.getFloat() * rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getFloat() * rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getFloat() * rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setDouble(leftSideOperand.getDouble() * rightSideOperand.getInt());
					case LONG:
						return new DataObject().setDouble(leftSideOperand.getDouble() * rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setDouble(leftSideOperand.getDouble() * rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getDouble() * rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() < 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.INVALID_ARGUMENTS, "Integer value must be larger than or equals to 0"));
						
						StringBuilder builder = new StringBuilder();
						for(int i = 0;i < rightSideOperand.getInt();i++)
							builder.append(leftSideOperand.getText());
						
						return new DataObject(builder.toString());
					
					case LONG:
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
	/**
	 * For "**"
	 */
	public DataObject opPow(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						double ret = Math.pow(leftSideOperand.getInt(), rightSideOperand.getInt());
						if(Math.abs(ret) > Integer.MAX_VALUE || rightSideOperand.getInt() < 0)
							return new DataObject().setDouble(ret);
						return new DataObject().setInt((int)ret);
					case LONG:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getInt(), rightSideOperand.getLong()));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getInt(), rightSideOperand.getFloat()));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getInt(), rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setDouble(Math.pow(leftSideOperand.getLong(), rightSideOperand.getInt()));
					case LONG:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getLong(), rightSideOperand.getLong()));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getLong(), rightSideOperand.getFloat()));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getLong(), rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setDouble(Math.pow(leftSideOperand.getFloat(), rightSideOperand.getInt()));
					case LONG:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getFloat(), rightSideOperand.getLong()));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getFloat(), rightSideOperand.getFloat()));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getFloat(), rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setDouble(Math.pow(leftSideOperand.getDouble(), rightSideOperand.getInt()));
					case LONG:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getDouble(), rightSideOperand.getLong()));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getDouble(), rightSideOperand.getFloat()));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(leftSideOperand.getDouble(), rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
			
			case FUNCTION_POINTER:
				if(rightSideOperand.getType() != DataType.INT)
					return null;
				
				final int count = rightSideOperand.getInt();
				if(count < 0)
					return new DataObject().setError(new ErrorObject(InterpretingError.INVALID_ARGUMENTS, "Number must not be less than 0!"));
				
				if(count == 0)
					return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
						return new DataObject().setVoid();
					}));
				
				final FunctionPointerObject func = leftSideOperand.getFunctionPointer();
				return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					DataObject retN = interpreter.callFunctionPointer(func, leftSideOperand.getVariableName(), args, INNER_SCOPE_ID);
					DataObject ret = retN == null?new DataObject().setVoid():retN;
					
					for(int i = 1;i < count;i++) {
						args = new LinkedList<>();
						args.add(ret);
						retN = interpreter.callFunctionPointer(func, leftSideOperand.getVariableName(), args, INNER_SCOPE_ID);
						ret = retN == null?new DataObject().setVoid():retN;
					}
					
					return ret;
				}));
			
			case TEXT:
			case CHAR:
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
	 * For "/"
	 */
	public DataObject opDiv(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setFloat(leftSideOperand.getInt() / 0.f);
						
						if(leftSideOperand.getInt() % rightSideOperand.getInt() != 0)
							return new DataObject().setFloat(leftSideOperand.getInt() / (float)rightSideOperand.getInt());
						
						return new DataObject().setInt(leftSideOperand.getInt() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setFloat(leftSideOperand.getInt() / 0.f);
						
						if(leftSideOperand.getInt() % rightSideOperand.getLong() != 0)
							return new DataObject().setFloat(leftSideOperand.getInt() / (float)rightSideOperand.getLong());
						
						return new DataObject().setLong(leftSideOperand.getInt() / rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getInt() / rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getInt() / rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setFloat(leftSideOperand.getLong() / 0.f);
						
						if(leftSideOperand.getLong() % rightSideOperand.getInt() != 0)
							return new DataObject().setFloat(leftSideOperand.getLong() / (float)rightSideOperand.getInt());
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setFloat(leftSideOperand.getLong() / 0.f);
						
						if(leftSideOperand.getLong() % rightSideOperand.getLong() != 0)
							return new DataObject().setFloat(leftSideOperand.getLong() / (float)rightSideOperand.getLong());
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getLong() / rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getLong() / rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setFloat(leftSideOperand.getFloat() / rightSideOperand.getInt());
					case LONG:
						return new DataObject().setFloat(leftSideOperand.getFloat() / rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getFloat() / rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getFloat() / rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return new DataObject().setDouble(leftSideOperand.getDouble() / rightSideOperand.getInt());
					case LONG:
						return new DataObject().setDouble(leftSideOperand.getDouble() / rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setDouble(leftSideOperand.getDouble() / rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getDouble() / rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
			case CHAR:
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
	/**
	 * For "~/"
	 */
	public DataObject opTruncDiv(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setInt(leftSideOperand.getInt() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(leftSideOperand.getInt() / rightSideOperand.getLong());
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						float tmpF = leftSideOperand.getInt() / rightSideOperand.getFloat();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						double tmpD = leftSideOperand.getInt() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getLong());
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						float tmpF = leftSideOperand.getLong() / rightSideOperand.getFloat();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						double tmpD = leftSideOperand.getLong() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						float tmpF = leftSideOperand.getFloat() / rightSideOperand.getInt();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						tmpF = leftSideOperand.getFloat() / rightSideOperand.getLong();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						tmpF = leftSideOperand.getFloat() / rightSideOperand.getFloat();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						double tmpD = leftSideOperand.getFloat() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						double tmpD = leftSideOperand.getDouble() / rightSideOperand.getInt();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						tmpD = leftSideOperand.getDouble() / rightSideOperand.getLong();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						tmpD = leftSideOperand.getDouble() / rightSideOperand.getFloat();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						tmpD = leftSideOperand.getDouble() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
			case CHAR:
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
	/**
	 * For "//"
	 */
	public DataObject opFloorDiv(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setInt(Math.floorDiv(leftSideOperand.getInt(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(Math.floorDiv(leftSideOperand.getInt(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getInt() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getInt() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(Math.floorDiv(leftSideOperand.getLong(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(Math.floorDiv(leftSideOperand.getLong(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getLong() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getLong() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getFloat() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getFloat() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getFloat() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getFloat() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
			case CHAR:
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
	/**
	 * For "^/"
	 */
	public DataObject opCeilDiv(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setInt(-Math.floorDiv(-leftSideOperand.getInt(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(-Math.floorDiv(-leftSideOperand.getInt(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getInt() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getInt() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(-Math.floorDiv(-leftSideOperand.getLong(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(-Math.floorDiv(-leftSideOperand.getLong(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getLong() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getLong() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
			case CHAR:
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
	/**
	 * For "%"
	 */
	public DataObject opMod(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setInt(leftSideOperand.getInt() % rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(leftSideOperand.getInt() % rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(leftSideOperand.getLong() % rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(leftSideOperand.getLong() % rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "&"
	 */
	public DataObject opAnd(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() & rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getInt() & rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() & rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() & rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "|"
	 */
	public DataObject opOr(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		if(rightSideOperand.getType() == DataType.FUNCTION_POINTER) {
			FunctionPointerObject func = rightSideOperand.getFunctionPointer();
			List<DataObject> args = new LinkedList<>();
			args.add(leftSideOperand);
			
			return interpreter.callFunctionPointer(func, rightSideOperand.getVariableName(), args, SCOPE_ID);
		}
		
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() | rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getInt() | rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() | rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() | rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "^"
	 */
	public DataObject opXor(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() ^ rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getInt() ^ rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() ^ rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() ^ rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "~"
	 */
	public DataObject opNot(DataObject operand, final int SCOPE_ID) {
		switch(operand.getType()) {
			case INT:
				return new DataObject().setInt(~operand.getInt());
			case LONG:
				return new DataObject().setLong(~operand.getLong());
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "&lt;&lt;"
	 */
	public DataObject opLshift(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() << rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong((long)leftSideOperand.getInt() << rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() << rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() << rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "&gt;&gt;"
	 */
	public DataObject opRshift(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		if(rightSideOperand.getType() == DataType.FUNCTION_POINTER) {
			FunctionPointerObject func = rightSideOperand.getFunctionPointer();
			List<DataObject> args = new LinkedList<>();
			args.add(leftSideOperand);
			
			return interpreter.callFunctionPointer(func, rightSideOperand.getVariableName(), args, SCOPE_ID);
		}
		
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() >> rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong((long)leftSideOperand.getInt() >> rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() >> rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() >> rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "&gt;&gt;&gt;"
	 */
	public DataObject opRzshift(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		if(rightSideOperand.getType() == DataType.FUNCTION_POINTER && leftSideOperand.getType() == DataType.ARRAY) {
			FunctionPointerObject func = rightSideOperand.getFunctionPointer();
			
			List<DataObject> args = new LinkedList<>(Arrays.asList(leftSideOperand.getArray()));
			args = LangUtils.separateArgumentsWithArgumentSeparators(args);
			
			return interpreter.callFunctionPointer(func, rightSideOperand.getVariableName(), args, SCOPE_ID);
		}
		
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						return new DataObject().setInt(leftSideOperand.getInt() >>> rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong((long)leftSideOperand.getInt() >>> rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return new DataObject().setLong(leftSideOperand.getLong() >>> rightSideOperand.getInt());
					case LONG:
						return new DataObject().setLong(leftSideOperand.getLong() >>> rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
			case DOUBLE:
			case TEXT:
			case CHAR:
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
	/**
	 * For "[...]"
	 */
	public DataObject opGetItem(DataObject leftSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case ARRAY:
				if(rightSideOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getArray().length;
					int index = rightSideOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return new DataObject().setError(new ErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS));
					
					return leftSideOperand.getArray()[index];
				}
				
				return null;
			case TEXT:
				if(rightSideOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getText().length();
					int index = rightSideOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return new DataObject().setError(new ErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS));
					
					return new DataObject().setChar(leftSideOperand.getText().charAt(index));
				}
				
				return null;
			case CHAR:
				if(rightSideOperand.getType() == DataType.INT) {
					int index = rightSideOperand.getInt();
					if(index < 0)
						index++;
					
					if(index != 0)
						return new DataObject().setError(new ErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS));
					
					return new DataObject().setChar(leftSideOperand.getChar());
				}
				
				return null;
			
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
}