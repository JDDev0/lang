package me.jddev0.module.lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import me.jddev0.module.lang.AbstractSyntaxTree.VariableNameNode;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;

/**
 * Lang-Module<br>
 * Class for variable data
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class DataObject {
	public final static DataTypeConstraint CONSTRAINT_NORMAL = DataTypeConstraint.fromNotAllowedTypes(new ArrayList<>());
	public final static DataTypeConstraint CONSTRAINT_COMPOSITE = DataTypeConstraint.fromAllowedTypes(Arrays.asList(DataType.ARRAY, DataType.LIST, DataType.STRUCT, DataType.NULL));
	public final static DataTypeConstraint CONSTRAINT_FUNCTION_POINTER = DataTypeConstraint.fromAllowedTypes(Arrays.asList(DataType.FUNCTION_POINTER, DataType.NULL));
	
	//Value
	private String txt;
	private byte[] byteBuf;
	private DataObject[] arr;
	private LinkedList<DataObject> list;
	private VarPointerObject vp;
	private FunctionPointerObject fp;
	private StructObject sp;
	private int intValue;
	private long longValue;
	private float floatValue;
	private double doubleValue;
	private char charValue;
	private ErrorObject error;
	private DataType typeValue;
	
	private DataTypeConstraint typeConstraint = CONSTRAINT_NORMAL;
	
	//Meta-Data
	/**
	 * Variable name of the DataObject (null for anonymous variable)
	 */
	private String variableName;
	private DataType type;
	private boolean finalData;
	private boolean staticData;
	private boolean copyStaticAndFinalModifiers;
	private boolean langVar;
	
	public static DataTypeConstraint getTypeConstraintFor(String variableName) {
		if(variableName == null)
			return CONSTRAINT_NORMAL;
		
		if(variableName.startsWith("&"))
			return CONSTRAINT_COMPOSITE;
		
		if(variableName.startsWith("fp.") || variableName.startsWith("func.") || variableName.startsWith("fn.") || variableName.startsWith("linker.") || variableName.startsWith("ln."))
			return CONSTRAINT_FUNCTION_POINTER;
		
		return CONSTRAINT_NORMAL;
	}
	
	public DataObject(DataObject dataObject) {
		setData(dataObject);
	}
	
	/**
	 * Creates a new data object of type NULL
	 */
	public DataObject() {
		setNull();
	}
	public DataObject(String txt) {
		this(txt, false);
	}
	public DataObject(String txt, boolean finalData) {
		setText(txt);
		setFinalData(finalData);
	}
	
	/**
	 * This method <b>ignores</b> the final and static state of the data object<br>
	 * This method will not modify variableName<br>
	 * This method will also not modify finalData nor staticData (<b>Except</b>: {@code dataObject.copyStaticAndFinalModifiers} flag is set)
	 */
	void setData(DataObject dataObject) throws DataTypeConstraintViolatedException {
		this.type = checkAndRetType(dataObject.type);
		
		this.txt = dataObject.txt;
		this.byteBuf = dataObject.byteBuf; //ByteBuffer: copy reference only
		this.arr = dataObject.arr; //Array: copy reference only
		this.list = dataObject.list; //List: copy reference only
		this.vp = dataObject.vp; //Var pointer: copy reference only
		
		//Func pointer: copy reference only
		//Set function name for better debugging experience
		this.fp = (dataObject.fp != null && getVariableName() != null && dataObject.fp.getFunctionName() == null)?
				dataObject.fp.withFunctionName(getVariableName()):dataObject.fp;
		
		this.sp = dataObject.sp; //Struct: copy reference only
		
		this.intValue = dataObject.intValue;
		this.longValue = dataObject.longValue;
		this.floatValue = dataObject.floatValue;
		this.doubleValue = dataObject.doubleValue;
		this.charValue = dataObject.charValue;
		this.error = dataObject.error; //Error: copy reference only
		this.typeValue = dataObject.typeValue;
		
		if(dataObject.copyStaticAndFinalModifiers) {
			if(dataObject.finalData)
				this.finalData = true;
			if(dataObject.staticData)
				this.staticData = true;
		}
	}
	
	//Data value methods
	/**
	 * This method <b>ignores</b> the final state of the data object<br>
	 * This method will not change variableName nor finalData
	 */
	private void resetValue() {
		this.txt = null;
		this.byteBuf = null;
		this.arr = null;
		this.list = null;
		this.vp = null;
		this.fp = null;
		this.intValue = 0;
		this.longValue = 0;
		this.floatValue = 0;
		this.doubleValue = 0;
		this.charValue = 0;
		this.error = null;
		this.typeValue = null;
	}
	
	DataObject setArgumentSeparator(String txt) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(txt == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.ARGUMENT_SEPARATOR);
		resetValue();
		this.txt = txt;
		
		return this;
	}
	
	public DataObject setText(String txt) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(txt == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.TEXT);
		resetValue();
		this.txt = txt;
		
		return this;
	}
	
	public String getText() {
		return toText();
	}
	
	public DataObject setByteBuffer(byte[] byteBuf) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(byteBuf == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.BYTE_BUFFER);
		resetValue();
		this.byteBuf = byteBuf;
		
		return this;
	}
	
	public byte[] getByteBuffer() {
		return byteBuf;
	}
	
	public DataObject setArray(DataObject[] arr) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(arr == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.ARRAY);
		resetValue();
		this.arr = arr;
		
		return this;
	}
	
	public DataObject[] getArray() {
		return arr;
	}
	
	public DataObject setList(LinkedList<DataObject> list) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(list == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.LIST);
		resetValue();
		this.list = list;
		
		return this;
	}
	
	public LinkedList<DataObject> getList() {
		return list;
	}
	
	public DataObject setVarPointer(VarPointerObject vp) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(vp == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.VAR_POINTER);
		resetValue();
		this.vp = vp;
		
		return this;
	}
	
	public VarPointerObject getVarPointer() {
		return vp;
	}
	
	public DataObject setFunctionPointer(FunctionPointerObject fp) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(fp == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.FUNCTION_POINTER);
		resetValue();
		
		this.fp = (getVariableName() != null && fp.getFunctionName() == null)?fp.withFunctionName(getVariableName()):fp;
		
		return this;
	}
	
	public FunctionPointerObject getFunctionPointer() {
		return fp;
	}
	
	public DataObject setStruct(StructObject sp) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(sp == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.STRUCT);
		resetValue();
		this.sp = sp;
		
		return this;
	}
	
	public StructObject getStruct() {
		return sp;
	}
	
	public DataObject setNull() throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.NULL);
		resetValue();
		
		return this;
	}
	
	public DataObject setVoid() throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.VOID);
		resetValue();
		
		return this;
	}
	
	public DataObject setInt(int intValue) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.INT);
		resetValue();
		this.intValue = intValue;
		
		return this;
	}
	
	public int getInt() {
		return intValue;
	}
	
	/**
	 * Sets data to INT = 1 if boolean value is true else INT = 0
	 */
	public DataObject setBoolean(boolean booleanValue) throws DataTypeConstraintViolatedException {
		return setInt(booleanValue?1:0);
	}
	
	public boolean getBoolean() {
		return toBoolean();
	}
	
	public DataObject setLong(long longValue) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.LONG);
		resetValue();
		this.longValue = longValue;
		
		return this;
	}
	
	public long getLong() {
		return longValue;
	}
	
	public DataObject setFloat(float floatValue) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.FLOAT);
		resetValue();
		this.floatValue = floatValue;
		
		return this;
	}
	
	public float getFloat() {
		return floatValue;
	}
	
	public DataObject setDouble(double doubleValue) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.DOUBLE);
		resetValue();
		this.doubleValue = doubleValue;
		
		return this;
	}
	
	public double getDouble() {
		return doubleValue;
	}
	
	public DataObject setChar(char charValue) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		
		this.type = checkAndRetType(DataType.CHAR);
		resetValue();
		this.charValue = charValue;
		
		return this;
	}
	
	public char getChar() {
		return charValue;
	}
	
	public DataObject setError(ErrorObject error) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(error == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.ERROR);
		resetValue();
		this.error = error;
		
		return this;
	}
	
	public ErrorObject getError() {
		return error;
	}
	
	public DataObject setTypeValue(DataType typeValue) throws DataTypeConstraintViolatedException {
		if(finalData)
			return this;
		if(typeValue == null)
			return setNull();
		
		this.type = checkAndRetType(DataType.TYPE);
		resetValue();
		this.typeValue = typeValue;
		
		return this;
	}
	
	public DataType getTypeValue() {
		return typeValue;
	}
	
	//Meta data methods
	public DataObject setVariableName(String variableName) throws DataTypeConstraintViolatedException {
		DataTypeConstraint newTypeRequirement = getTypeConstraintFor(variableName);
		if(!newTypeRequirement.isTypeAllowed(type))
			throw new DataTypeConstraintViolatedException();
		
		this.typeConstraint = newTypeRequirement;
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
	
	public DataObject setStaticData(boolean staticData) {
		this.staticData = staticData;
		
		return this;
	}
	
	public boolean isStaticData() {
		return staticData;
	}
	
	DataObject setCopyStaticAndFinalModifiers(boolean copyStaticAndFinalModifiers) {
		this.copyStaticAndFinalModifiers = copyStaticAndFinalModifiers;
		
		return this;
	}
	
	public boolean isCopyStaticAndFinalModifiers() {
		return copyStaticAndFinalModifiers;
	}
	
	DataObject setLangVar() {
		this.langVar = true;
		
		return this;
	}
	
	public boolean isLangVar() {
		return langVar;
	}
	
	public DataType getType() {
		return type;
	}
	
	DataObject setTypeConstraint(DataTypeConstraint typeConstraint) throws DataTypeConstraintException {
		for(DataType type:this.typeConstraint.getNotAllowedTypes()) {
			if(typeConstraint.isTypeAllowed(type))
				throw new DataTypeConstraintException("New type constraint must not allow types which were not allowed previously");
		}
		
		if(!typeConstraint.isTypeAllowed(type))
			throw new DataTypeConstraintViolatedException();
		
		this.typeConstraint = typeConstraint;
		
		return this;
	}
	
	public DataType checkAndRetType(DataType type) throws DataTypeConstraintViolatedException {
		if(!typeConstraint.isTypeAllowed(type))
			throw new DataTypeConstraintViolatedException();
		
		return type;
	}
	
	public DataTypeConstraint getTypeConstraint() {
		return typeConstraint;
	}
	
	public DataObject convertToNumberAndCreateNewDataObject() {
		Number number = toNumber();
		if(number == null)
			return new DataObject().setNull();
		
		if(number instanceof Integer)
			return new DataObject().setInt(number.intValue());
		
		if(number instanceof Long)
			return new DataObject().setLong(number.longValue());
		
		if(number instanceof Float)
			return new DataObject().setFloat(number.floatValue());
		
		if(number instanceof Double)
			return new DataObject().setDouble(number.doubleValue());
		
		return new DataObject().setNull();
	}
	
	private String convertByteBufferToText() {
		StringBuilder builder = new StringBuilder();
		if(byteBuf.length > 0) {
			final String HEX_DIGITS = "0123456789ABCDEF";
			
			builder.append("0x");
			for(byte b:byteBuf) {
				builder.append(HEX_DIGITS.charAt((b >> 4) & 0xF));
				builder.append(HEX_DIGITS.charAt(b & 0xF));
			}
		}else {
			builder.append("<Empty ByteBuffer>");
		}
		return builder.toString();
	}
	
	private void convertCompositeElementToText(DataObject ele, StringBuilder builder) {
		if(ele.getType() == DataType.ARRAY) {
			builder.append("<Array[" + ele.getArray().length + "]>");
		}else if(ele.getType() == DataType.LIST) {
			builder.append("<List[" + ele.getList().size() + "]>");
		}else if(ele.getType() == DataType.VAR_POINTER) {
			builder.append("-->{");
			DataObject data = ele.getVarPointer().getVar();
			if(data != null && data.getType() == DataType.ARRAY) {
				builder.append("<Array[" + data.getArray().length + "]>");
			}else if(data != null && data.getType() == DataType.LIST) {
				builder.append("<List[" + data.getList().size() + "]>");
			}else if(data != null && data.getType() == DataType.VAR_POINTER) {
				builder.append("-->{...}");
			}else if(data != null && data.getType() == DataType.STRUCT) {
				builder.append(data.getStruct().isDefinition()?"<Struct[Definition]>":"<Struct[Instance]>");
			}else {
				builder.append(data);
			}
			builder.append("}");
		}else if(ele.getType() == DataType.STRUCT) {
			builder.append(ele.getStruct().isDefinition()?"<Struct[Definition]>":"<Struct[Instance]>");
		}else {
			builder.append(ele.getText());
		}
		builder.append(", ");
	}
	
	private String convertArrayToText() {
		StringBuilder builder = new StringBuilder("[");
		if(arr.length > 0) {
			for(DataObject ele:arr)
				convertCompositeElementToText(ele, builder);
			builder.delete(builder.length() - 2, builder.length());
		}
		builder.append(']');
		return builder.toString();
	}
	
	private String convertListToText() {
		StringBuilder builder = new StringBuilder("[");
		if(list.size() > 0) {
			for(DataObject ele:list)
				convertCompositeElementToText(ele, builder);
			builder.delete(builder.length() - 2, builder.length());
		}
		builder.append(']');
		return builder.toString();
	}
	
	private String convertStructToText() {
		StringBuilder builder = new StringBuilder("{");
		String[] memberNames = sp.getMemberNames();
		if(memberNames.length > 0) {
			if(sp.isDefinition()) {
				for(String memberName:memberNames)
					builder.append(memberName).append(", ");
			}else {
				for(String memberName:memberNames) {
					builder.append(memberName).append(": ");
					convertCompositeElementToText(sp.getMember(memberName), builder);
				}
			}
			
			builder.delete(builder.length() - 2, builder.length());
		}
		builder.append('}');
		return builder.toString();
	}
	
	//Conversion functions
	public String toText() {
		switch(type) {
			case TEXT:
			case ARGUMENT_SEPARATOR:
				return txt;
			case BYTE_BUFFER:
				return convertByteBufferToText();
			case ARRAY:
				return convertArrayToText();
			case LIST:
				return convertListToText();
			case VAR_POINTER:
				return vp.toString();
			case FUNCTION_POINTER:
				if(variableName != null)
					return variableName;
				
				return fp.toString();
			case STRUCT:
				return convertStructToText();
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
			case TYPE:
				return typeValue.name();
		}
		
		return null;
	}
	public Character toChar() {
		switch(type) {
			case TEXT:
			case ARGUMENT_SEPARATOR:
				return null;
			case BYTE_BUFFER:
				return null;
			case ARRAY:
				return null;
			case LIST:
				return null;
			case VAR_POINTER:
				return null;
			case FUNCTION_POINTER:
				return null;
			case STRUCT:
				return null;
			case VOID:
				return null;
			case NULL:
				return null;
			case INT:
				return (char)intValue;
			case LONG:
				return (char)longValue;
			case FLOAT:
				return (char)floatValue;
			case DOUBLE:
				return (char)doubleValue;
			case CHAR:
				return charValue;
			case ERROR:
				return null;
			case TYPE:
				return null;
		}
		
		return null;
	}
	public Integer toInt() {
		switch(type) {
			case TEXT:
				if(txt.trim().length() == txt.length()) {
					try {
						return Integer.parseInt(txt);
					}catch(NumberFormatException ignore) {}
				}
				
				return null;
			case CHAR:
				return (int)charValue;
			case INT:
				return intValue;
			case LONG:
				return (int)longValue;
			case FLOAT:
				return (int)floatValue;
			case DOUBLE:
				return (int)doubleValue;
			case ERROR:
				return error.getErrno();
			case BYTE_BUFFER:
				return byteBuf.length;
			case ARRAY:
				return arr.length;
			case LIST:
				return list.size();
			case STRUCT:
				return sp.getMemberNames().length;
			
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
	public Long toLong() {
		switch(type) {
			case TEXT:
				if(txt.trim().length() == txt.length()) {
					try {
						return Long.parseLong(txt);
					}catch(NumberFormatException ignore) {}
				}
				
				return null;
			case CHAR:
				return (long)charValue;
			case INT:
				return (long)intValue;
			case LONG:
				return longValue;
			case FLOAT:
				return (long)floatValue;
			case DOUBLE:
				return (long)doubleValue;
			case ERROR:
				return (long)error.getErrno();
			case BYTE_BUFFER:
				return (long)byteBuf.length;
			case ARRAY:
				return (long)arr.length;
			case LIST:
				return (long)list.size();
			case STRUCT:
				return (long)sp.getMemberNames().length;
			
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
	public Float toFloat() {
		switch(type) {
			case TEXT:
				if(txt.length() > 0) {
					char lastChar = txt.charAt(txt.length() - 1);
					
					if(txt.trim().length() == txt.length() && lastChar != 'f' && lastChar != 'F' && lastChar != 'd' &&
							lastChar != 'D' && !txt.contains("x") && !txt.contains("X")) {
						try {
							return Float.parseFloat(txt);
						}catch(NumberFormatException ignore) {}
					}
				}
				
				return null;
			case CHAR:
				return (float)charValue;
			case INT:
				return (float)intValue;
			case LONG:
				return (float)longValue;
			case FLOAT:
				return floatValue;
			case DOUBLE:
				return (float)doubleValue;
			case ERROR:
				return (float)error.getErrno();
			case BYTE_BUFFER:
				return (float)byteBuf.length;
			case ARRAY:
				return (float)arr.length;
			case LIST:
				return (float)list.size();
			case STRUCT:
				return (float)sp.getMemberNames().length;
			
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
	public Double toDouble() {
		switch(type) {
			case TEXT:
				if(txt.length() > 0) {
					char lastChar = txt.charAt(txt.length() - 1);
					
					if(txt.trim().length() == txt.length() && lastChar != 'f' && lastChar != 'F' && lastChar != 'd' &&
							lastChar != 'D' && !txt.contains("x") && !txt.contains("X")) {
						try {
							return Double.parseDouble(txt);
						}catch(NumberFormatException ignore) {}
					}
				}
				
				return null;
			case CHAR:
				return (double)charValue;
			case INT:
				return (double)intValue;
			case LONG:
				return (double)longValue;
			case FLOAT:
				return (double)floatValue;
			case DOUBLE:
				return doubleValue;
			case ERROR:
				return (double)error.getErrno();
			case BYTE_BUFFER:
				return (double)byteBuf.length;
			case ARRAY:
				return (double)arr.length;
			case LIST:
				return (double)list.size();
			case STRUCT:
				return (double)sp.getMemberNames().length;
			
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
	public byte[] toByteBuffer() {
		switch(type) {
			case BYTE_BUFFER:
				return byteBuf;
			
			case TEXT:
			case CHAR:
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
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
	public DataObject[] toArray() {
		switch(type) {
			case ARRAY:
				return arr;
			case LIST:
				return list.stream().map(DataObject::new).toArray(len -> new DataObject[len]);
			case STRUCT:
				try {
					return Arrays.stream(sp.getMemberNames()).
							map(memberName -> new DataObject(sp.getMember(memberName))).toArray(DataObject[]::new);
				}catch(DataTypeConstraintException e) {
					return null;
				}
			
			case TEXT:
			case CHAR:
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
			case BYTE_BUFFER:
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
	public LinkedList<DataObject> toList() {
		switch(type) {
			case ARRAY:
				return new LinkedList<DataObject>(Arrays.stream(arr).map(DataObject::new).collect(Collectors.toList()));
			case LIST:
				return list;
			case STRUCT:
				try {
					return new LinkedList<DataObject>(Arrays.asList(Arrays.stream(sp.getMemberNames()).
							map(memberName -> new DataObject(sp.getMember(memberName))).toArray(DataObject[]::new)));
				}catch(DataTypeConstraintException e) {
					return null;
				}
			
			case TEXT:
			case CHAR:
			case INT:
			case LONG:
			case FLOAT:
			case DOUBLE:
			case BYTE_BUFFER:
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
	public boolean toBoolean() {
		switch(type) {
			case TEXT:
				return !txt.isEmpty();
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
			case BYTE_BUFFER:
				return byteBuf.length > 0;
			case ARRAY:
				return arr.length > 0;
			case LIST:
				return list.size() > 0;
			case STRUCT:
				return sp.getMemberNames().length > 0;
			case ERROR:
				return error.getErrno() != 0;
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case TYPE:
				return true;
			
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return false;
		}
		
		return false;
	}
	public Number toNumber() {
		switch(type) {
			case TEXT:
				if(txt.length() > 0) {
					char lastChar = txt.charAt(txt.length() - 1);
					
					if(txt.trim().length() == txt.length() && lastChar != 'f' && lastChar != 'F' && lastChar != 'd' &&
							lastChar != 'D' && !txt.contains("x") && !txt.contains("X")) {
						//INT
						try {
							return Integer.parseInt(txt);
						}catch(NumberFormatException ignore) {}
						
						//LONG
						try {
							if(txt.endsWith("l") || txt.endsWith("L"))
								return Long.parseLong(txt.substring(0, txt.length() - 1));
							else
								return Long.parseLong(txt);
						}catch(NumberFormatException ignore) {}
						
						//FLOAT
						if(txt.endsWith("f") || txt.endsWith("F")) {
							try {
								return Float.parseFloat(txt.substring(0, txt.length() - 1));
							}catch(NumberFormatException ignore) {}
						}
						
						//DOUBLE
						try {
							return Double.parseDouble(txt);
						}catch(NumberFormatException ignore) {}
					}
				}
				
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
			case BYTE_BUFFER:
				return byteBuf.length;
			case ERROR:
				return error.getErrno();
			case ARRAY:
				return arr.length;
			case LIST:
				return list.size();
			case STRUCT:
				return sp.getMemberNames().length;
			
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
	
	//Comparison functions
	/**
	 * For "=="
	 */
	public boolean isEquals(DataObject other) {
		if(this == other)
			return true;
		
		if(other == null)
			return false;
		
		Number number = other.toNumber();
		switch(type) {
			case TEXT:
				if(other.type == DataType.TEXT)
					return txt.equals(other.txt);
				
				if(txt.length() == 1 && other.type == DataType.CHAR)
					return txt.charAt(0) == other.charValue;
				
				return number != null && other.isEquals(this);
			
			case CHAR:
				if(other.type == DataType.TEXT && other.txt.length() == 1)
					return charValue == other.txt.charAt(0);
				
				return number != null && charValue == number.intValue();
			
			case INT:
				return number != null && intValue == number.intValue();
			
			case LONG:
				return number != null && longValue == number.longValue();
			
			case FLOAT:
				return number != null && floatValue == number.floatValue();
			
			case DOUBLE:
				return number != null && doubleValue == number.doubleValue();
				
			case BYTE_BUFFER:
				if(other.type == DataType.BYTE_BUFFER)
					return Objects.equals(byteBuf, other.byteBuf);
				
				return number != null && byteBuf.length == number.intValue();
			
			case ARRAY:
				if(other.type == DataType.ARRAY)
					return Objects.deepEquals(arr, other.arr);
				
				if(other.type == DataType.LIST)
					return Objects.deepEquals(arr, other.list.toArray(new DataObject[0]));
				
				return number != null && arr.length == number.intValue();
				
			case LIST:
				if(other.type == DataType.LIST)
					return Objects.deepEquals(list, other.list);
				
				if(other.type == DataType.ARRAY)
					return Objects.deepEquals(list.toArray(new DataObject[0]), other.arr);
				
				return number != null && list.size() == number.intValue();
				
			case STRUCT:
				if(other.type == DataType.STRUCT)
					return Objects.equals(sp, other.sp);
				
				return number != null && sp.getMemberNames().length == number.intValue();
			
			case VAR_POINTER:
				return vp.equals(other.vp);
			
			case FUNCTION_POINTER:
				return fp.equals(other.fp);
			
			case ERROR:
				switch(other.type) {
					case TEXT:
						if(number == null)
							return error.getErrtxt().equals(other.txt);
						return error.getErrno() == number.intValue();
					
					case CHAR:
					case INT:
					case LONG:
					case FLOAT:
					case DOUBLE:
					case BYTE_BUFFER:
					case ARRAY:
					case LIST:
					case STRUCT:
						return number != null && error.getErrno() == number.intValue();
					
					case ERROR:
						return error.equals(other.error);
					
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
				
				return false;
			
			case TYPE:
				return other.type == DataType.TYPE?typeValue == other.typeValue:typeValue == other.type;
			
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return type == other.type;
		}
		
		return false;
	}
	/**
	 * For "==="
	 */
	public boolean isStrictEquals(DataObject other) {
		if(this == other)
			return true;
		
		if(other == null)
			return false;
		
		try {
			return this.type.equals(other.type) && Objects.equals(this.txt, other.txt) && Objects.deepEquals(this.byteBuf, other.byteBuf) &&
			Objects.deepEquals(this.arr, other.arr) && Objects.deepEquals(this.list, other.list) && Objects.equals(this.vp, other.vp) &&
			Objects.equals(this.fp, other.fp) && Objects.equals(this.sp, other.sp) && this.intValue == other.intValue &&
			this.longValue == other.longValue && this.floatValue == other.floatValue && this.doubleValue == other.doubleValue &&
			this.charValue == other.charValue && Objects.equals(this.error, other.error) && this.typeValue == other.typeValue;
		}catch(StackOverflowError e) {
			return false;
		}
	}
	/**
	 * For "&lt;"
	 */
	public boolean isLessThan(DataObject other) {
		if(this == other || other == null)
			return false;
		
		DataObject number = other.convertToNumberAndCreateNewDataObject();
		switch(type) {
			case TEXT:
				if(other.type == DataType.TEXT)
					return txt.compareTo(other.txt) < 0;
				
				if(txt.length() == 1 && other.type == DataType.CHAR)
					return txt.charAt(0) < other.charValue;
				
				Number thisNumber = toNumber();
				if(thisNumber == null)
					return false;
				
				switch(number.type) {
					case INT:
						return thisNumber.intValue() < number.getInt();
					case LONG:
						return thisNumber.longValue() < number.getLong();
					case FLOAT:
						return thisNumber.floatValue() < number.getFloat();
					case DOUBLE:
						return thisNumber.doubleValue() < number.getDouble();
					
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
				
				return false;
			
			case CHAR:
				if(other.type == DataType.TEXT && other.txt.length() == 1)
					return charValue < other.txt.charAt(0);
				
				switch(number.type) {
					case INT:
						return charValue < number.getInt();
					case LONG:
						return charValue < number.getLong();
					case FLOAT:
						return charValue < number.getFloat();
					case DOUBLE:
						return charValue < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case INT:
				switch(number.type) {
					case INT:
						return intValue < number.getInt();
					case LONG:
						return intValue < number.getLong();
					case FLOAT:
						return intValue < number.getFloat();
					case DOUBLE:
						return intValue < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case LONG:
				switch(number.type) {
					case INT:
						return longValue < number.getInt();
					case LONG:
						return longValue < number.getLong();
					case FLOAT:
						return longValue < number.getFloat();
					case DOUBLE:
						return longValue < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case FLOAT:
				switch(number.type) {
					case INT:
						return floatValue < number.getInt();
					case LONG:
						return floatValue < number.getLong();
					case FLOAT:
						return floatValue < number.getFloat();
					case DOUBLE:
						return floatValue < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case DOUBLE:
				switch(number.type) {
					case INT:
						return doubleValue < number.getInt();
					case LONG:
						return doubleValue < number.getLong();
					case FLOAT:
						return doubleValue < number.getFloat();
					case DOUBLE:
						return doubleValue < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case BYTE_BUFFER:
				switch(number.type) {
					case INT:
						return byteBuf.length < number.getInt();
					case LONG:
						return byteBuf.length < number.getLong();
					case FLOAT:
						return byteBuf.length < number.getFloat();
					case DOUBLE:
						return byteBuf.length < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case ARRAY:
				switch(number.type) {
					case INT:
						return arr.length < number.getInt();
					case LONG:
						return arr.length < number.getLong();
					case FLOAT:
						return arr.length < number.getFloat();
					case DOUBLE:
						return arr.length < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case LIST:
				switch(number.type) {
					case INT:
						return list.size() < number.getInt();
					case LONG:
						return list.size() < number.getLong();
					case FLOAT:
						return list.size() < number.getFloat();
					case DOUBLE:
						return list.size() < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case ERROR:
				if(other.type == DataType.TEXT && number.getType() == DataType.NULL)
					return error.getErrtxt().compareTo(other.txt) < 0;
				switch(number.type) {
					case INT:
						return error.getErrno() < number.getInt();
					case LONG:
						return error.getErrno() < number.getLong();
					case FLOAT:
						return error.getErrno() < number.getFloat();
					case DOUBLE:
						return error.getErrno() < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
				
				return false;
			
			case STRUCT:
				switch(number.type) {
					case INT:
						return sp.getMemberNames().length < number.getInt();
					case LONG:
						return sp.getMemberNames().length < number.getLong();
					case FLOAT:
						return sp.getMemberNames().length < number.getFloat();
					case DOUBLE:
						return sp.getMemberNames().length < number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return false;
		}
		
		return false;
	}
	/**
	 * For "&gt;"
	 */
	public boolean isGreaterThan(DataObject other) {
		if(this == other || other == null)
			return false;
		
		DataObject number = other.convertToNumberAndCreateNewDataObject();
		switch(type) {
			case TEXT:
				if(other.type == DataType.TEXT)
					return txt.compareTo(other.txt) > 0;
				
				if(txt.length() == 1 && other.type == DataType.CHAR)
					return txt.charAt(0) > other.charValue;
				
				Number thisNumber = toNumber();
				if(thisNumber == null)
					return false;
				
				switch(number.type) {
					case INT:
						return thisNumber.intValue() > number.getInt();
					case LONG:
						return thisNumber.longValue() > number.getLong();
					case FLOAT:
						return thisNumber.floatValue() > number.getFloat();
					case DOUBLE:
						return thisNumber.doubleValue() > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
				
				return false;
			
			case CHAR:
				if(other.type == DataType.TEXT && other.txt.length() == 1)
					return charValue > other.txt.charAt(0);
				
				switch(number.type) {
					case INT:
						return charValue > number.getInt();
					case LONG:
						return charValue > number.getLong();
					case FLOAT:
						return charValue > number.getFloat();
					case DOUBLE:
						return charValue > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case INT:
				switch(number.type) {
					case INT:
						return intValue > number.getInt();
					case LONG:
						return intValue > number.getLong();
					case FLOAT:
						return intValue > number.getFloat();
					case DOUBLE:
						return intValue > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case LONG:
				switch(number.type) {
					case INT:
						return longValue > number.getInt();
					case LONG:
						return longValue > number.getLong();
					case FLOAT:
						return longValue > number.getFloat();
					case DOUBLE:
						return longValue > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case FLOAT:
				switch(number.type) {
					case INT:
						return floatValue > number.getInt();
					case LONG:
						return floatValue > number.getLong();
					case FLOAT:
						return floatValue > number.getFloat();
					case DOUBLE:
						return floatValue > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case DOUBLE:
				switch(number.type) {
					case INT:
						return doubleValue > number.getInt();
					case LONG:
						return doubleValue > number.getLong();
					case FLOAT:
						return doubleValue > number.getFloat();
					case DOUBLE:
						return doubleValue > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case BYTE_BUFFER:
				switch(number.type) {
					case INT:
						return byteBuf.length > number.getInt();
					case LONG:
						return byteBuf.length > number.getLong();
					case FLOAT:
						return byteBuf.length > number.getFloat();
					case DOUBLE:
						return byteBuf.length > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case ARRAY:
				switch(number.type) {
					case INT:
						return arr.length > number.getInt();
					case LONG:
						return arr.length > number.getLong();
					case FLOAT:
						return arr.length > number.getFloat();
					case DOUBLE:
						return arr.length > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case LIST:
				switch(number.type) {
					case INT:
						return list.size() > number.getInt();
					case LONG:
						return list.size() > number.getLong();
					case FLOAT:
						return list.size() > number.getFloat();
					case DOUBLE:
						return list.size() > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case ERROR:
				if(other.type == DataType.TEXT && number.getType() == DataType.NULL)
					return error.getErrtxt().compareTo(other.txt) > 0;
				switch(number.type) {
					case INT:
						return error.getErrno() > number.getInt();
					case LONG:
						return error.getErrno() > number.getLong();
					case FLOAT:
						return error.getErrno() > number.getFloat();
					case DOUBLE:
						return error.getErrno() > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
				
				return false;
			
			case STRUCT:
				switch(number.type) {
					case INT:
						return sp.getMemberNames().length > number.getInt();
					case LONG:
						return sp.getMemberNames().length > number.getLong();
					case FLOAT:
						return sp.getMemberNames().length > number.getFloat();
					case DOUBLE:
						return sp.getMemberNames().length > number.getDouble();
						
					case CHAR:
					case BYTE_BUFFER:
					case ERROR:
					case ARRAY:
					case LIST:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case STRUCT:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
					case TYPE:
						return false;
				}
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
			case TYPE:
				return false;
		}
		
		return false;
	}
	/**
	 * For "&lt;="
	 */
	public boolean isLessThanOrEquals(DataObject other) {
		return isLessThan(other) || isEquals(other);
	}
	/**
	 * For "&gt;="
	 */
	public boolean isGreaterThanOrEquals(DataObject other) {
		return isGreaterThan(other) || isEquals(other);
	}
	
	@Override
	public String toString() {
		return toText();
	}
	
	@Override
	public boolean equals(Object obj) {
		if(this == obj)
			return true;
		
		if(obj == null)
			return false;
		
		if(!(obj instanceof DataObject))
			return false;
		
		try {
			DataObject that = (DataObject)obj;
			return this.type.equals(that.type) && Objects.equals(this.txt, that.txt) && Objects.equals(this.byteBuf, that.byteBuf) &&
			Objects.deepEquals(this.arr, that.arr) && Objects.deepEquals(this.list, that.list) && Objects.equals(this.vp, that.vp) &&
			Objects.equals(this.fp, that.fp) && Objects.equals(this.sp, that.sp) && this.intValue == that.intValue && this.longValue == that.longValue &&
			this.floatValue == that.floatValue && this.doubleValue == that.doubleValue && this.charValue == that.charValue &&
			Objects.equals(this.error, that.error) && this.typeValue == that.typeValue;
		}catch(StackOverflowError e) {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(type, txt, byteBuf, arr, list == null?null:list.toArray(), vp, fp, sp, intValue, longValue, floatValue, doubleValue, charValue, error, typeValue);
	}
	
	public static enum DataType {
		TEXT, CHAR, INT, LONG, FLOAT, DOUBLE, BYTE_BUFFER, ARRAY, LIST, VAR_POINTER, FUNCTION_POINTER, STRUCT, ERROR, NULL, VOID, ARGUMENT_SEPARATOR, TYPE;
	}
	public static final class DataTypeConstraint {
		private final Set<DataType> types;
		private final boolean allowed;
		
		public static DataTypeConstraint fromAllowedTypes(Collection<DataType> allowedTypes) {
			return new DataTypeConstraint(allowedTypes, true);
		}
		
		public static DataTypeConstraint fromNotAllowedTypes(Collection<DataType> notAllowedTypes) {
			return new DataTypeConstraint(notAllowedTypes, false);
		}
		
		public static DataTypeConstraint fromSingleAllowedType(DataType allowedType) {
			return new DataTypeConstraint(Arrays.asList(allowedType), true);
		}
		
		private DataTypeConstraint(Collection<DataType> types, boolean allowed) {
			this.types = new HashSet<>(types);
			this.allowed = allowed;
		}
		
		public boolean isTypeAllowed(DataType type) {
			return type == null || types.contains(type) == allowed;
		}
		
		public List<DataType> getAllowedTypes() {
			if(allowed)
				return types.stream().collect(Collectors.toList());
			
			return Arrays.stream(DataType.values()).filter(((Predicate<DataType>)types::contains).negate()).collect(Collectors.toList());
		}
		
		public List<DataType> getNotAllowedTypes() {
			if(allowed)
				return Arrays.stream(DataType.values()).filter(((Predicate<DataType>)types::contains).negate()).collect(Collectors.toList());
			
			return types.stream().collect(Collectors.toList());
		}
		
		public String toTypeConstraintSyntax() {
			StringBuilder strBuilder = new StringBuilder();
			strBuilder.append("{");
			
			//Invert "!" if no types are set and print all types
			boolean inverted = !allowed ^ this.types.size() == 0;
			
			if(inverted)
				strBuilder.append("!");
			
			Set<DataType> types = new HashSet<>(this.types.size() == 0?Arrays.asList(DataType.values()):this.types);
			
			if(!inverted && types.contains(DataType.NULL) && types.size() > 1) {
				types.remove(DataType.NULL);
				
				strBuilder.append("?");
			}
			
			for(DataType type:types)
				strBuilder.append(type).append("|");
			
			strBuilder.delete(strBuilder.length() - 1, strBuilder.length());
			
			strBuilder.append("}");
			return strBuilder.toString();
		}
		
		@Override
		public String toString() {
			return (allowed?"= ":"! ") + "[" + types.stream().map(DataType::name).collect(Collectors.joining(", ")) + "]";
		}
		
		public String printAllowedTypes() {
			if(allowed)
				return "[" + types.stream().map(DataType::name).collect(Collectors.joining(", ")) + "]";
			
			return "[" + Arrays.stream(DataType.values()).filter(((Predicate<DataType>)types::contains).negate()).map(DataType::name).collect(Collectors.joining(", ")) + "]";
		}
		
		public String printNotAllowedTypes() {
			if(allowed)
				return "[" + Arrays.stream(DataType.values()).filter(((Predicate<DataType>)types::contains).negate()).map(DataType::name).collect(Collectors.joining(", ")) + "]";
			
			return "[" + types.stream().map(DataType::name).collect(Collectors.joining(", ")) + "]";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof DataTypeConstraint))
				return false;
			
			DataTypeConstraint that = (DataTypeConstraint)obj;
			return Objects.deepEquals(new HashSet<>(this.getAllowedTypes()), new HashSet<>(that.getAllowedTypes()));
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(new HashSet<>(getAllowedTypes()));
		}
	}
	public static final class FunctionPointerObject {
		/**
		 * Normal function pointer
		 */
		public static final int NORMAL = 0;
		/**
		 * Pointer to a native function
		 */
		public static final int NATIVE = 1;
		/**
		 * Pointer to a predefined function
		 */
		public static final int PREDEFINED = 2;
		/**
		 * Function which is defined in the language
		 */
		public static final int EXTERNAL = 3;
		
		/**
		 * If langPath is set, the Lang path from the stack frame element which is created for the function call will be overridden
		 */
		private final String langPath;
		/**
		 * If langFile or langPath is set, the Lang file from the stack frame element which is created for the function call will be overridden<br>
		 * This behavior allows for keeping the "&lt;shell&gt;" special case - when the Lang file is null - if a function within a stack frame element where the Lang file is null is
		 * called from within a stack frame element where Lang file is not null.
		 */
		private final String langFile;
		/**
		 * If functionName is set, the function name from the stack frame element which is created for the function call will be overridden
		 */
		private final String functionName;
		private final List<VariableNameNode> parameterList;
		private final DataTypeConstraint returnValueTypeConstraint;
		private final AbstractSyntaxTree functionBody;
		private final LangNativeFunction nativeFunction;
		private final LangPredefinedFunctionObject predefinedFunction;
		private final LangExternalFunctionObject externalFunction;
		private final int functionPointerType;
		
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(String langPath, String langFile, String functionName,
				List<VariableNameNode> parameterList, DataTypeConstraint returnValueTypeConstraint, AbstractSyntaxTree functionBody) {
			this.langPath = langPath;
			this.langFile = langFile;
			this.functionName = functionName;
			this.parameterList = parameterList == null?null:new ArrayList<>(parameterList);
			this.returnValueTypeConstraint = returnValueTypeConstraint;
			this.functionBody = functionBody;
			this.nativeFunction = null;
			this.predefinedFunction = null;
			this.externalFunction = null;
			this.functionPointerType = NORMAL;
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(String langPath, String langFile, String functionName,
				List<VariableNameNode> parameterList, AbstractSyntaxTree functionBody) {
			this(langPath, langFile, functionName, parameterList, null, functionBody);
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(String langPath, String langFile, List<VariableNameNode> parameterList,
				DataTypeConstraint returnValueTypeConstraint, AbstractSyntaxTree functionBody) {
			this(langPath, langFile, null, parameterList, returnValueTypeConstraint, functionBody);
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(String langPath, String langFile, List<VariableNameNode> parameterList,
				AbstractSyntaxTree functionBody) {
			this(langPath, langFile, null, parameterList, functionBody);
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(String functionName, List<VariableNameNode> parameterList,
				DataTypeConstraint returnValueTypeConstraint, AbstractSyntaxTree functionBody) {
			this(null, null, functionName, parameterList, returnValueTypeConstraint, functionBody);
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(String functionName, List<VariableNameNode> parameterList,
				AbstractSyntaxTree functionBody) {
			this(null, null, functionName, parameterList, functionBody);
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(List<VariableNameNode> parameterList, DataTypeConstraint returnValueTypeConstraint,
				AbstractSyntaxTree functionBody) {
			this(null, parameterList, returnValueTypeConstraint, functionBody);
		}
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(List<VariableNameNode> parameterList, AbstractSyntaxTree functionBody) {
			this(null, parameterList, functionBody);
		}
		
		/**
		 * For pointer to native function/linker function
		 */
		public FunctionPointerObject(String langPath, String langFile, String functionName, LangNativeFunction nativeFunction) {
			this.langPath = langPath;
			this.langFile = langFile;
			this.functionName = functionName == null?nativeFunction.getFunctionName():functionName;
			this.parameterList = null;
			this.returnValueTypeConstraint = null;
			this.functionBody = null;
			this.nativeFunction = nativeFunction;
			this.predefinedFunction = null;
			this.externalFunction = null;
			this.functionPointerType = NATIVE;
		}
		/**
		 * For pointer to native function/linker function
		 */
		public FunctionPointerObject(String langPath, String langFile, LangNativeFunction nativeFunction) {
			this(langPath, langFile, nativeFunction.getFunctionName(), nativeFunction);
		}
		/**
		 * For pointer to native function/linker function
		 */
		public FunctionPointerObject(String functionName, LangNativeFunction nativeFunction) {
			this(null, null, functionName, nativeFunction);
		}
		/**
		 * For pointer to native function/linker function
		 */
		public FunctionPointerObject(LangNativeFunction nativeFunction) {
			this(nativeFunction.getFunctionName(), nativeFunction);
		}
		
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(String langPath, String langFile, String functionName, LangPredefinedFunctionObject predefinedFunction) {
			this.langPath = langPath;
			this.langFile = langFile;
			this.functionName = functionName;
			this.parameterList = null;
			this.returnValueTypeConstraint = null;
			this.functionBody = null;
			this.nativeFunction = null;
			this.predefinedFunction = predefinedFunction;
			this.externalFunction = null;
			this.functionPointerType = PREDEFINED;
		}
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(String langPath, String langFile, LangPredefinedFunctionObject predefinedFunction) {
			this(langPath, langFile, null, predefinedFunction);
		}
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(String functionName, LangPredefinedFunctionObject predefinedFunction) {
			this(null, null, functionName, predefinedFunction);
		}
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(LangPredefinedFunctionObject predefinedFunction) {
			this(null, predefinedFunction);
		}
		
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(String langPath, String langFile, String functionName, LangExternalFunctionObject externalFunction) {
			this.langPath = langPath;
			this.langFile = langFile;
			this.functionName = functionName;
			this.parameterList = null;
			this.returnValueTypeConstraint = null;
			this.functionBody = null;
			this.nativeFunction = null;
			this.predefinedFunction = null;
			this.externalFunction = externalFunction;
			this.functionPointerType = EXTERNAL;
		}
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(String langPath, String langFile, LangExternalFunctionObject externalFunction) {
			this(langPath, langFile, null, externalFunction);
		}
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(String functionName, LangExternalFunctionObject externalFunction) {
			this(null, null, functionName, externalFunction);
		}
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(LangExternalFunctionObject externalFunction) {
			this(null, externalFunction);
		}
		
		public FunctionPointerObject withFunctionName(String functionName) {
			switch(functionPointerType) {
				case NORMAL:
					return new FunctionPointerObject(langPath, langFile, functionName, parameterList,
							returnValueTypeConstraint, functionBody);
				case NATIVE:
					return new FunctionPointerObject(langPath, langFile, functionName, nativeFunction);
				case PREDEFINED:
					return new FunctionPointerObject(langPath, langFile, functionName, predefinedFunction);
				case EXTERNAL:
					return new FunctionPointerObject(langPath, langFile, functionName, externalFunction);
			}
			
			return null;
		}
		
		public String getLangPath() {
			return langPath;
		}
		
		public String getLangFile() {
			return langFile;
		}
		
		public String getFunctionName() {
			return functionName;
		}
		
		public List<VariableNameNode> getParameterList() {
			return parameterList;
		}
		
		public DataTypeConstraint getReturnValueTypeConstraint() {
			return returnValueTypeConstraint;
		}
		
		public AbstractSyntaxTree getFunctionBody() {
			return functionBody;
		}
		
		public LangNativeFunction getNativeFunction() {
			return nativeFunction;
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
			if(functionName != null)
				return functionName;
			
			switch(functionPointerType) {
				case NORMAL:
					return "<Normal FP>";
				case NATIVE:
					return "<Native Function>";
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
			return this.functionPointerType == that.functionPointerType && Objects.equals(this.parameterList, that.parameterList) &&
			Objects.equals(this.functionBody, that.functionBody) && Objects.equals(this.nativeFunction, that.nativeFunction) &&
			Objects.equals(this.predefinedFunction, that.predefinedFunction) && Objects.equals(this.externalFunction, that.externalFunction);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(functionPointerType, parameterList, functionBody, nativeFunction, predefinedFunction, externalFunction);
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
			if(var.getType() == DataType.VAR_POINTER)
				return "-->{-->{...}}";
			
			return "-->{" + var + "}";
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
	public static final class StructObject {
		private final String[] memberNames;
		private final DataTypeConstraint[] typeConstraints;
		private final DataObject[] members;
		/**
		 * If null: This is the struct definition<br>
		 * If not null: This is an instance of structBaseDefinition<br>
		 * This is also been used for instance of checks
		 */
		private final StructObject structBaseDefinition;
		
		public StructObject(String[] memberNames) throws DataTypeConstraintException {
			this(memberNames, null);
		}
		
		public StructObject(String[] memberNames, DataTypeConstraint[] typeConstraints) throws DataTypeConstraintException {
			this.memberNames = Arrays.copyOf(memberNames, memberNames.length);
			this.typeConstraints = typeConstraints == null?new DataTypeConstraint[this.memberNames.length]:
				Arrays.copyOf(typeConstraints, typeConstraints.length);
			
			if(this.memberNames.length != this.typeConstraints.length)
				throw new DataTypeConstraintException("The count of members must be equals to the count of type constraints");
			
			this.members = null;
			this.structBaseDefinition = null;
		}
		
		public StructObject(StructObject structBaseDefinition) throws DataTypeConstraintException {
			this(structBaseDefinition, null);
		}
		
		public StructObject(StructObject structBaseDefinition, DataObject[] values) throws DataTypeConstraintException {
			if(!structBaseDefinition.isDefinition())
				throw new DataTypeConstraintException("No instance can be created of another struct instance");
			
			this.memberNames = Arrays.copyOf(structBaseDefinition.memberNames, structBaseDefinition.memberNames.length);
			this.typeConstraints = Arrays.copyOf(structBaseDefinition.typeConstraints, structBaseDefinition.typeConstraints.length);
			this.members = new DataObject[this.memberNames.length];
			
			if(values != null && this.memberNames.length != values.length)
				throw new DataTypeConstraintException("The count of members must be equals to the count of values");
			
			for(int i = 0;i < this.members.length;i++) {
				this.members[i] = new DataObject().setVariableName(this.memberNames[i]);
				
				if(values != null && values[i] != null)
					this.members[i].setData(values[i]);
				
				if(this.typeConstraints[i] != null)
					this.members[i].setTypeConstraint(this.typeConstraints[i]);
			}
			
			this.structBaseDefinition = structBaseDefinition;
		}
		
		public boolean isDefinition() {
			return structBaseDefinition == null;
		}
		
		public String[] getMemberNames() {
			return Arrays.copyOf(memberNames, memberNames.length);
		}
		
		public DataTypeConstraint[] getTypeConstraints() {
			return Arrays.copyOf(typeConstraints, typeConstraints.length);
		}
		
		public StructObject getStructBaseDefinition() {
			return structBaseDefinition;
		}
		
		/**
		 * @return Will return null, if the member was not found
		 */
		public int getIndexOfMember(String memeberName) {
			for(int i = 0;i < memberNames.length;i++)
				if(memberNames[i].equals(memeberName))
					return i;
			
			return -1;
		}
		
		public DataTypeConstraint getTypeConstraint(String memberName) throws DataTypeConstraintException {
			int index = getIndexOfMember(memberName);
			if(index == -1)
				throw new DataTypeConstraintException("The member \"" + memberName + "\" is not part of this struct");
			
			return typeConstraints[index];
		}
		
		public DataObject getMember(String memberName) throws DataTypeConstraintException {
			if(isDefinition())
				throw new DataTypeConstraintException("The struct definition is no struct instance and has no member values");
			
			int index = getIndexOfMember(memberName);
			if(index == -1)
				throw new DataTypeConstraintException("The member \"" + memberName + "\" is not part of this struct");
			
			return members[index];
		}
		
		public void setMember(String memberName, DataObject dataObject) throws DataTypeConstraintException {
			if(isDefinition())
				throw new DataTypeConstraintException("The struct definition is no struct instance and has no member values");
			
			int index = getIndexOfMember(memberName);
			if(index == -1)
				throw new DataTypeConstraintException("The member \"" + memberName + "\" is not part of this struct");
			
			members[index].setData(dataObject);
		}
		
		@Override
		public String toString() {
			return structBaseDefinition == null?"<Struct[Definition]>":"<Struct[Instance]>";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof StructObject))
				return false;
			
			StructObject that = (StructObject)obj;
			return Objects.deepEquals(this.memberNames, that.memberNames) && Objects.deepEquals(this.members, that.members) &&
					Objects.deepEquals(this.typeConstraints, that.typeConstraints) && Objects.equals(this.structBaseDefinition, that.structBaseDefinition);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(memberNames, members, typeConstraints, structBaseDefinition);
		}
	}
	public static final class ErrorObject {
		private final InterpretingError err;
		private final String message;
		
		public ErrorObject(InterpretingError err, String message) {
			if(err == null)
				this.err = InterpretingError.NO_ERROR;
			else
				this.err = err;
			
			this.message = message;
		}
		public ErrorObject(InterpretingError err) {
			this(err, null);
		}
		
		public InterpretingError getInterprettingError() {
			return err;
		}
		
		public int getErrno() {
			return err.getErrorCode();
		}
		
		public String getErrtxt() {
			return err.getErrorText();
		}
		
		public String getMessage() {
			return message;
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
			return this.err == that.err;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(err);
		}
	}
	
	public static class DataTypeConstraintException extends RuntimeException {
		private static final long serialVersionUID = 7335599147999542200L;
		
		public DataTypeConstraintException(String msg) {
			super(msg);
		}
	}
	public static class DataTypeConstraintViolatedException extends DataTypeConstraintException {
		private static final long serialVersionUID = 7449156115495467372L;
		
		public DataTypeConstraintViolatedException() {
			super("The data type would violate a type constraint");
		}
	}
}