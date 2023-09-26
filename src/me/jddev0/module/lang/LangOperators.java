package me.jddev0.module.lang;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraintException;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.StructObject;
import me.jddev0.module.lang.LangFunction.AllowedTypes;
import me.jddev0.module.lang.LangFunction.LangParameter;
import me.jddev0.module.lang.LangFunction.LangParameter.RawVarArgs;
import me.jddev0.module.lang.LangFunction.LangParameter.VarArgs;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;

/**
 * Lang-Module<br>
 * Lang operators definitions
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangOperators {
	private final LangInterpreter interpreter;
	
	public LangOperators(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	//General operation functions
	/**
	 * For "@"
	 */
	public DataObject opLen(DataObject operand, int lineNumber, final int SCOPE_ID) {
		switch(operand.getType()) {
			case BYTE_BUFFER:
				return new DataObject().setInt(operand.getByteBuffer().length);
			case ARRAY:
				return new DataObject().setInt(operand.getArray().length);
			case LIST:
				return new DataObject().setInt(operand.getList().size());
			case TEXT:
				return new DataObject().setInt(operand.getText().length());
			case CHAR:
				return new DataObject().setInt(1);
			case STRUCT:
				return new DataObject().setInt(operand.getStruct().getMemberNames().length);
			
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
	public DataObject opDeepCopy(DataObject operand, int lineNumber, final int SCOPE_ID) {
		switch(operand.getType()) {
			case BYTE_BUFFER:
				return new DataObject().setByteBuffer(Arrays.copyOf(operand.getByteBuffer(), operand.getByteBuffer().length));
			case ARRAY:
				DataObject[] arrCopy = new DataObject[operand.getArray().length];
				for(int i = 0;i < operand.getArray().length;i++) {
					arrCopy[i] = opDeepCopy(operand.getArray()[i], lineNumber, SCOPE_ID);
					if(arrCopy[i] == null)
						return null;
				}
				
				return new DataObject().setArray(arrCopy);
			case LIST:
				LinkedList<DataObject> listCopy = new LinkedList<>();
				for(int i = 0;i < operand.getList().size();i++) {
					listCopy.add(opDeepCopy(operand.getList().get(i), lineNumber, SCOPE_ID));
					if(listCopy.get(i) == null)
						return null;
				}
				
				return new DataObject().setList(listCopy);
			case STRUCT:
				StructObject struct = operand.getStruct();
				try {
					if(struct.isDefinition()) {
						return new DataObject().setStruct(new StructObject(struct.getMemberNames(), struct.getTypeConstraints()));
					}else {
						StructObject structCopy = new StructObject(struct.getStructBaseDefinition());
						for(String memberName:struct.getMemberNames())
							structCopy.setMember(memberName, opDeepCopy(struct.getMember(memberName), lineNumber, SCOPE_ID));
						
						return new DataObject().setStruct(structCopy);
					}
				}catch(DataTypeConstraintException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), lineNumber, SCOPE_ID);
				}
			
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
	public DataObject opConcat(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
			case BYTE_BUFFER:
				if(rightSideOperand.getType() != DataType.BYTE_BUFFER)
					return null;
				
				byte[] newByteBuf = new byte[leftSideOperand.getByteBuffer().length + rightSideOperand.getByteBuffer().length];
				
				System.arraycopy(leftSideOperand.getByteBuffer(), 0, newByteBuf, 0, leftSideOperand.getByteBuffer().length);
				System.arraycopy(rightSideOperand.getByteBuffer(), 0, newByteBuf, leftSideOperand.getByteBuffer().length,
						rightSideOperand.getByteBuffer().length);
				
				return new DataObject().setByteBuffer(newByteBuf);
			case ARRAY:
				if(rightSideOperand.getType() == DataType.ARRAY) {
					DataObject[] arrNew = new DataObject[leftSideOperand.getArray().length + rightSideOperand.getArray().length];
					for(int i = 0;i < leftSideOperand.getArray().length;i++)
						arrNew[i] = leftSideOperand.getArray()[i];
					for(int i = 0;i < rightSideOperand.getArray().length;i++)
						arrNew[leftSideOperand.getArray().length + i] = rightSideOperand.getArray()[i];
					
					return new DataObject().setArray(arrNew);
				}else if(rightSideOperand.getType() == DataType.LIST) {
					DataObject[] arrNew = new DataObject[leftSideOperand.getArray().length + rightSideOperand.getList().size()];
					for(int i = 0;i < leftSideOperand.getArray().length;i++)
						arrNew[i] = leftSideOperand.getArray()[i];
					for(int i = 0;i < rightSideOperand.getList().size();i++)
						arrNew[leftSideOperand.getArray().length + i] = rightSideOperand.getList().get(i);
					
					return new DataObject().setArray(arrNew);
				}
				
				return null;
			case LIST:
				if(rightSideOperand.getType() == DataType.ARRAY) {
					LinkedList<DataObject> listNew = new LinkedList<>(leftSideOperand.getList());
					for(int i = 0;i < rightSideOperand.getArray().length;i++)
						listNew.add(rightSideOperand.getArray()[i]);
					
					return new DataObject().setList(listNew);
				}else if(rightSideOperand.getType() == DataType.LIST) {
					LinkedList<DataObject> listNew = new LinkedList<>(leftSideOperand.getList());
					listNew.addAll(rightSideOperand.getList());
					
					return new DataObject().setList(listNew);
				}
				
				return null;
			
			case FUNCTION_POINTER:
				if(rightSideOperand.getType() != DataType.FUNCTION_POINTER)
					return null;
				
				final FunctionPointerObject aFunc = leftSideOperand.getFunctionPointer();
				final FunctionPointerObject bFunc = rightSideOperand.getFunctionPointer();
				return new DataObject().setFunctionPointer(new FunctionPointerObject("<concat-func(" + aFunc + ", " + bFunc + ")>",
						LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("concat-func")
					public DataObject concatFuncFunction(LangInterpreter interpreter, int SCOPE_ID,
							@LangParameter("&args") @RawVarArgs List<DataObject> args) {
						DataObject retA = interpreter.callFunctionPointer(aFunc, leftSideOperand.getVariableName(), args, SCOPE_ID);
						
						return interpreter.callFunctionPointer(bFunc, rightSideOperand.getVariableName(), Arrays.asList(
								retA == null?new DataObject().setVoid():new DataObject(retA)
						), SCOPE_ID);
					}
				})));
			
			case STRUCT:
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
	public DataObject opSpaceship(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
	 * For "+|"
	 */
	public DataObject opInc(DataObject operand, int lineNumber, final int SCOPE_ID) {
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
				return new DataObject().setFunctionPointer(new FunctionPointerObject("<auto-unpack-func(" + func + ")>",
						LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("auto-unpack-func")
					public DataObject autoUnpackFuncFunction(LangInterpreter interpreter, int SCOPE_ID,
							@LangParameter("&array") @AllowedTypes(DataObject.DataType.ARRAY) DataObject arrayObject) {
						return interpreter.callFunctionPointer(func, operand.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
								Arrays.stream(arrayObject.getArray()).map(DataObject::new).collect(Collectors.toList())
						), SCOPE_ID);
					}
				})));
			
			case STRUCT:
			case TEXT:
			case BYTE_BUFFER:
			case ARRAY:
			case LIST:
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
	 * For "-|"
	 */
	public DataObject opDec(DataObject operand, int lineNumber, final int SCOPE_ID) {
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
				return new DataObject().setFunctionPointer(new FunctionPointerObject("<auto-pack-func(" + func + ")>",
						LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("auto-pack-func")
					public DataObject autopackFuncFunction(LangInterpreter interpreter, int SCOPE_ID,
							@LangParameter("&args") @VarArgs List<DataObject> args) {
						return interpreter.callFunctionPointer(func, operand.getVariableName(), Arrays.asList(
								new DataObject().setArray(args.stream().map(DataObject::new).toArray(DataObject[]::new))
						), SCOPE_ID);
					}
				})));
			
			case TEXT:
			case ARRAY:
			case BYTE_BUFFER:
			case LIST:
			case STRUCT:
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
	public DataObject opPos(DataObject operand, int lineNumber, final int SCOPE_ID) {
		return new DataObject(operand);
	}
	/**
	 * For "-"
	 */
	public DataObject opInv(DataObject operand, int lineNumber, final int SCOPE_ID) {
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
			case BYTE_BUFFER:
				byte[] revByteBuf = new byte[operand.getByteBuffer().length];
				for(int i = 0;i < revByteBuf.length;i++)
					revByteBuf[i] = operand.getByteBuffer()[revByteBuf.length - 1 - i];
				
				return new DataObject().setByteBuffer(revByteBuf);
			case ARRAY:
				DataObject[] arrInv = new DataObject[operand.getArray().length];
				int index = arrInv.length - 1;
				for(DataObject dataObject:operand.getArray())
					arrInv[index--] = dataObject;
				
				return new DataObject().setArray(arrInv);
			case LIST:
				LinkedList<DataObject> listInv = new LinkedList<>(operand.getList());
				Collections.reverse(listInv);
				
				return new DataObject().setList(listInv);
			
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case STRUCT:
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
	public DataObject opAdd(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
			case LIST:
				LinkedList<DataObject> listNew = new LinkedList<>(leftSideOperand.getList());
				listNew.add(new DataObject(rightSideOperand));
				return new DataObject().setList(listNew);
			
			case BYTE_BUFFER:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case STRUCT:
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
	public DataObject opSub(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
						return null;
				}
				return null;
			
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "*"
	 */
	public DataObject opMul(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
						return null;
				}
				return null;
			
			case TEXT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() < 0)
							return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Integer value must be larger than or equals to 0", lineNumber, SCOPE_ID);
						
						StringBuilder builder = new StringBuilder();
						for(int i = 0;i < rightSideOperand.getInt();i++)
							builder.append(leftSideOperand.getText());
						
						return new DataObject(builder.toString());
					
					case LONG:
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			
			case CHAR:
			case ARRAY:
			case BYTE_BUFFER:
			case LIST:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case STRUCT:
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
	public DataObject opPow(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
						return null;
				}
				return null;
			
			case FUNCTION_POINTER:
				if(rightSideOperand.getType() != DataType.INT)
					return null;
				
				final int count = rightSideOperand.getInt();
				if(count < 0)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Number must not be less than 0!", lineNumber, SCOPE_ID);
				
				final FunctionPointerObject func = leftSideOperand.getFunctionPointer();
				
				if(count == 0)
					return new DataObject().setFunctionPointer(new FunctionPointerObject("<" + func + " ** " + count + ">",
							LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
						@LangFunction("pow-func")
						public DataObject powFuncFunction(LangInterpreter interpreter, int SCOPE_ID,
								@LangParameter("&args") @RawVarArgs List<DataObject> args) {
							return new DataObject().setVoid();
						}
					})));
				
				return new DataObject().setFunctionPointer(new FunctionPointerObject("<" + func + " ** " + count + ">",
						LangNativeFunction.getSingleLangFunctionFromObject(interpreter, new Object() {
					@LangFunction("pow-func")
					public DataObject powFuncFunction(LangInterpreter interpreter, int SCOPE_ID,
							@LangParameter("&args") @RawVarArgs List<DataObject> args) {
						DataObject retN = interpreter.callFunctionPointer(func, leftSideOperand.getVariableName(), args, SCOPE_ID);
						DataObject ret = retN == null?new DataObject().setVoid():new DataObject(retN);
						
						for(int i = 1;i < count;i++) {
							retN = interpreter.callFunctionPointer(func, leftSideOperand.getVariableName(), Arrays.asList(
									ret
							), SCOPE_ID);
							ret = retN == null?new DataObject().setVoid():new DataObject(retN);
						}
						
						return ret;
					}
				})));
			
			case TEXT:
			case CHAR:
			case BYTE_BUFFER:
			case ARRAY:
			case LIST:
			case ERROR:
			case VAR_POINTER:
			case STRUCT:
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
	public DataObject opDiv(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setDouble(leftSideOperand.getInt() / 0.);
						
						if(leftSideOperand.getInt() % rightSideOperand.getInt() != 0)
							return new DataObject().setDouble(leftSideOperand.getInt() / (double)rightSideOperand.getInt());
						
						return new DataObject().setInt(leftSideOperand.getInt() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setDouble(leftSideOperand.getInt() / 0.);
						
						if(leftSideOperand.getInt() % rightSideOperand.getLong() != 0)
							return new DataObject().setDouble(leftSideOperand.getInt() / (double)rightSideOperand.getLong());
						
						return new DataObject().setLong(leftSideOperand.getInt() / rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getInt() / rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getInt() / rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return new DataObject().setDouble(leftSideOperand.getLong() / 0.);
						
						if(leftSideOperand.getLong() % rightSideOperand.getInt() != 0)
							return new DataObject().setDouble(leftSideOperand.getLong() / (double)rightSideOperand.getInt());
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return new DataObject().setDouble(leftSideOperand.getLong() / 0.);
						
						if(leftSideOperand.getLong() % rightSideOperand.getLong() != 0)
							return new DataObject().setDouble(leftSideOperand.getLong() / (double)rightSideOperand.getLong());
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getLong());
					case FLOAT:
						return new DataObject().setFloat(leftSideOperand.getLong() / rightSideOperand.getFloat());
					case DOUBLE:
						return new DataObject().setDouble(leftSideOperand.getLong() / rightSideOperand.getDouble());
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "~/"
	 */
	public DataObject opTruncDiv(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setInt(leftSideOperand.getInt() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(leftSideOperand.getInt() / rightSideOperand.getLong());
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						float tmpF = leftSideOperand.getInt() / rightSideOperand.getFloat();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						double tmpD = leftSideOperand.getInt() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(leftSideOperand.getLong() / rightSideOperand.getLong());
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						float tmpF = leftSideOperand.getLong() / rightSideOperand.getFloat();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						double tmpD = leftSideOperand.getLong() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case FLOAT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						float tmpF = leftSideOperand.getFloat() / rightSideOperand.getInt();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						tmpF = leftSideOperand.getFloat() / rightSideOperand.getLong();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						tmpF = leftSideOperand.getFloat() / rightSideOperand.getFloat();
						if(tmpF > 0)
							tmpF = (float)Math.floor(tmpF);
						else
							tmpF = (float)Math.ceil(tmpF);
						return new DataObject().setFloat(tmpF);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						double tmpD = leftSideOperand.getFloat() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				
				return null;
			case DOUBLE:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						double tmpD = leftSideOperand.getDouble() / rightSideOperand.getInt();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						tmpD = leftSideOperand.getDouble() / rightSideOperand.getLong();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						tmpD = leftSideOperand.getDouble() / rightSideOperand.getFloat();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						tmpD = leftSideOperand.getDouble() / rightSideOperand.getDouble();
						if(tmpD > 0)
							tmpD = Math.floor(tmpD);
						else
							tmpD = Math.ceil(tmpD);
						return new DataObject().setDouble(tmpD);
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "//"
	 */
	public DataObject opFloorDiv(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setInt(Math.floorDiv(leftSideOperand.getInt(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(Math.floorDiv(leftSideOperand.getInt(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getInt() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getInt() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(Math.floorDiv(leftSideOperand.getLong(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(Math.floorDiv(leftSideOperand.getLong(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getLong() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getLong() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case FLOAT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getFloat() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getFloat() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.floor(leftSideOperand.getFloat() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getFloat() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				
				return null;
			case DOUBLE:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.floor(leftSideOperand.getDouble() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "^/"
	 */
	public DataObject opCeilDiv(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setInt(-Math.floorDiv(-leftSideOperand.getInt(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(-Math.floorDiv(-leftSideOperand.getInt(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getInt() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getInt() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(-Math.floorDiv(-leftSideOperand.getLong(), rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(-Math.floorDiv(-leftSideOperand.getLong(), rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getLong() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getLong() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case FLOAT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setFloat((float)Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getFloat() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				
				return null;
			case DOUBLE:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getInt()));
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getLong()));
					case FLOAT:
						if(rightSideOperand.getFloat() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getFloat()));
					case DOUBLE:
						if(rightSideOperand.getDouble() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setDouble(Math.ceil(leftSideOperand.getDouble() / rightSideOperand.getDouble()));
					
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "%"
	 */
	public DataObject opMod(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case INT:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setInt(leftSideOperand.getInt() % rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(leftSideOperand.getInt() % rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			case LONG:
				switch(rightSideOperand.getType()) {
					case INT:
						if(rightSideOperand.getInt() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(leftSideOperand.getLong() % rightSideOperand.getInt());
					case LONG:
						if(rightSideOperand.getLong() == 0)
							return interpreter.setErrnoErrorObject(InterpretingError.DIV_BY_ZERO, lineNumber, SCOPE_ID);
						
						return new DataObject().setLong(leftSideOperand.getLong() % rightSideOperand.getLong());
					
					case FLOAT:
					case DOUBLE:
					case TEXT:
					case CHAR:
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
						return null;
				}
				return null;
			
			case TEXT:
				if(rightSideOperand.getType() == DataType.ARRAY)
					return interpreter.formatText(leftSideOperand.getText(), new LinkedList<>(Arrays.asList(rightSideOperand.getArray())), SCOPE_ID);
				
				return null;
			
			case FLOAT:
			case DOUBLE:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&"
	 */
	public DataObject opAnd(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
						return null;
				}
				return null;
			
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "|"
	 */
	public DataObject opOr(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		if(rightSideOperand.getType() == DataType.FUNCTION_POINTER) {
			FunctionPointerObject func = rightSideOperand.getFunctionPointer();
			
			return interpreter.callFunctionPointer(func, rightSideOperand.getVariableName(), Arrays.asList(
					leftSideOperand
			), SCOPE_ID);
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
						return null;
				}
				return null;
			
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "^"
	 */
	public DataObject opXor(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
						return null;
				}
				return null;
			
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "~"
	 */
	public DataObject opNot(DataObject operand, int lineNumber, final int SCOPE_ID) {
		switch(operand.getType()) {
			case INT:
				return new DataObject().setInt(~operand.getInt());
			case LONG:
				return new DataObject().setLong(~operand.getLong());
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&lt;&lt;"
	 */
	public DataObject opLshift(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
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
						return null;
				}
				return null;
			
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&gt;&gt;"
	 */
	public DataObject opRshift(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		if(rightSideOperand.getType() == DataType.FUNCTION_POINTER) {
			FunctionPointerObject func = rightSideOperand.getFunctionPointer();
			
			return interpreter.callFunctionPointer(func, rightSideOperand.getVariableName(), Arrays.asList(
					leftSideOperand
			), SCOPE_ID);
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
						return null;
				}
				return null;
			
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&gt;&gt;&gt;"
	 */
	public DataObject opRzshift(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		if(rightSideOperand.getType() == DataType.FUNCTION_POINTER && leftSideOperand.getType() == DataType.ARRAY) {
			FunctionPointerObject func = rightSideOperand.getFunctionPointer();
			
			return interpreter.callFunctionPointer(func, rightSideOperand.getVariableName(), LangUtils.separateArgumentsWithArgumentSeparators(
					Arrays.asList(leftSideOperand.getArray())
			), SCOPE_ID);
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
						return null;
				}
				return null;
			
			case FLOAT:
			case DOUBLE:
			case TEXT:
			case CHAR:
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
				return null;
		}
		
		return null;
	}
	
	//All operation functions
	public DataObject opCast(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		if(leftSideOperand.getType() != DataType.TYPE)
			return null;
		
		switch(leftSideOperand.getTypeValue()) {
			case TEXT:
				String txt = rightSideOperand.toText();
				if(txt == null)
					return null;
				
				return new DataObject(txt);
			case CHAR:
				Character chr = rightSideOperand.toChar();
				if(chr == null)
					return null;
				
				return new DataObject().setChar(chr);
			case INT:
				Integer i = rightSideOperand.toInt();
				if(i == null)
					return null;
				
				return new DataObject().setInt(i);
			case LONG:
				Long l = rightSideOperand.toLong();
				if(l == null)
					return null;
				
				return new DataObject().setLong(l);
			case FLOAT:
				Float f = rightSideOperand.toFloat();
				if(f == null)
					return null;
				
				return new DataObject().setFloat(f);
			case DOUBLE:
				Double d = rightSideOperand.toDouble();
				if(d == null)
					return null;
				
				return new DataObject().setDouble(d);
			case BYTE_BUFFER:
				byte[] byteBuffer = rightSideOperand.toByteBuffer();
				if(byteBuffer == null)
					return null;
				
				return new DataObject().setByteBuffer(byteBuffer);
			case ARRAY:
				DataObject[] arr = rightSideOperand.toArray();
				if(arr == null)
					return null;
				
				return new DataObject().setArray(arr);
			case LIST:
				LinkedList<DataObject> list = rightSideOperand.toList();
				if(list == null)
					return null;
				
				return new DataObject().setList(list);
			
			case ARGUMENT_SEPARATOR:
			case ERROR:
			case FUNCTION_POINTER:
			case NULL:
			case STRUCT:
			case TYPE:
			case VAR_POINTER:
			case VOID:
				break;
		}
		
		return null;
	}
	/**
	 * For "[...]"
	 */
	public DataObject opGetItem(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case BYTE_BUFFER:
				if(rightSideOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getByteBuffer().length;
					int index = rightSideOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					return new DataObject().setInt(leftSideOperand.getByteBuffer()[index]);
				}
				
				return null;
			case ARRAY:
				if(rightSideOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getArray().length;
					int index = rightSideOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					return new DataObject(leftSideOperand.getArray()[index]);
				}
				
				return null;
			case LIST:
				if(rightSideOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getList().size();
					int index = rightSideOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					return new DataObject(leftSideOperand.getList().get(index));
				}
				
				return null;
			case TEXT:
				if(rightSideOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getText().length();
					int index = rightSideOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					return new DataObject().setChar(leftSideOperand.getText().charAt(index));
				}
				
				return null;
			case CHAR:
				if(rightSideOperand.getType() == DataType.INT) {
					int index = rightSideOperand.getInt();
					if(index < 0)
						index++;
					
					if(index != 0)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					return new DataObject().setChar(leftSideOperand.getChar());
				}
				
				return null;
			case STRUCT:
				if(rightSideOperand.getType() == DataType.TEXT) {
					try {
						return new DataObject(leftSideOperand.getStruct().getMember(rightSideOperand.getText()));
					}catch(DataTypeConstraintException e) {
						return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), lineNumber, SCOPE_ID);
					}
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
	/**
	 * For "?[...]"
	 */
	public DataObject opOptionalGetItem(DataObject leftSideOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		if(leftSideOperand.getType() == DataType.NULL || leftSideOperand.getType() == DataType.VOID)
			return new DataObject().setVoid();
		
		return opGetItem(leftSideOperand, rightSideOperand, lineNumber, SCOPE_ID);
	}
	public DataObject opSetItem(DataObject leftSideOperand, DataObject middleOperand, DataObject rightSideOperand, int lineNumber, final int SCOPE_ID) {
		switch(leftSideOperand.getType()) {
			case BYTE_BUFFER:
				if(middleOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getByteBuffer().length;
					int index = middleOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					Number valueNumber = rightSideOperand.toNumber();
					if(valueNumber == null)
						return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, lineNumber, SCOPE_ID);
					byte value = valueNumber.byteValue();
					
					leftSideOperand.getByteBuffer()[index] = value;
					
					return new DataObject().setVoid();
				}
				
				return null;
			case ARRAY:
				if(middleOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getArray().length;
					int index = middleOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					leftSideOperand.getArray()[index] = new DataObject(rightSideOperand);
					
					return new DataObject().setVoid();
				}
				
				return null;
			case LIST:
				if(middleOperand.getType() == DataType.INT) {
					int len = leftSideOperand.getList().size();
					int index = middleOperand.getInt();
					if(index < 0)
						index += len;
					
					if(index < 0 || index >= len)
						return interpreter.setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, lineNumber, SCOPE_ID);
					
					leftSideOperand.getList().set(index, new DataObject(rightSideOperand));
					
					return new DataObject().setVoid();
				}
			case STRUCT:
				if(middleOperand.getType() == DataType.TEXT) {
					try {
						leftSideOperand.getStruct().setMember(middleOperand.getText(), rightSideOperand);
						
						return new DataObject().setVoid();
					}catch(DataTypeConstraintException e) {
						return interpreter.setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), lineNumber, SCOPE_ID);
					}
				}
				
				return null;
			
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
				return null;
		}
		
		return null;
	}
}