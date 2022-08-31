package me.jddev0.module.lang;

import java.util.LinkedList;
import java.util.List;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;

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
	public DataObject opConcat(DataObject lettSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		switch(lettSideOperand.getType()) {
			case INT:
				return new DataObject(lettSideOperand.getInt() + rightSideOperand.getText());
			case LONG:
				return new DataObject(lettSideOperand.getLong() + rightSideOperand.getText());
			case FLOAT:
				return new DataObject(lettSideOperand.getFloat() + rightSideOperand.getText());
			case DOUBLE:
				return new DataObject(lettSideOperand.getDouble() + rightSideOperand.getText());
			case CHAR:
				return new DataObject(lettSideOperand.getChar() + rightSideOperand.getText());
			case TEXT:
				return new DataObject(lettSideOperand.getText() + rightSideOperand.getText());
			case ARRAY:
				if(rightSideOperand.getType() != DataType.ARRAY)
					return null;
				
				DataObject[] arrNew = new DataObject[lettSideOperand.getArray().length + rightSideOperand.getArray().length];
				for(int i = 0;i < lettSideOperand.getArray().length;i++)
					arrNew[i] = lettSideOperand.getArray()[i];
				for(int i = 0;i < rightSideOperand.getArray().length;i++)
					arrNew[lettSideOperand.getArray().length + i] = rightSideOperand.getArray()[i];
				
				return new DataObject().setArray(arrNew);
				
			case FUNCTION_POINTER:
				if(rightSideOperand.getType() != DataType.FUNCTION_POINTER)
					return null;
				
				final FunctionPointerObject aFunc = lettSideOperand.getFunctionPointer();
				final FunctionPointerObject bFunc = rightSideOperand.getFunctionPointer();
				return new DataObject().setFunctionPointer(new FunctionPointerObject((interpreter, args, INNER_SCOPE_ID) -> {
					List<DataObject> argsB = new LinkedList<>();
					DataObject retA = interpreter.callFunctionPointer(aFunc, lettSideOperand.getVariableName(), args, INNER_SCOPE_ID);
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
	public DataObject opSpaceship(DataObject lettSideOperand, DataObject rightSideOperand, final int SCOPE_ID) {
		if(lettSideOperand.isLessThan(rightSideOperand))
			return new DataObject().setInt(-1);
		if(lettSideOperand.isEquals(rightSideOperand))
			return new DataObject().setInt(0);
		if(lettSideOperand.isGreaterThan(rightSideOperand))
			return new DataObject().setInt(1);
		
		return new DataObject().setNull();
	}
}