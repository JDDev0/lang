package me.jddev0.module.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
	//Value
	private String txt;
	private DataObject[] arr;
	private VarPointerObject vp;
	private FunctionPointerObject fp;
	private int intValue;
	private long longValue;
	private float floatValue;
	private double doubleValue;
	private char charValue;
	private ErrorObject error;
	
	//Meta-Data
	/**
	 * Variable name of the DataObject (null for anonymous variable)
	 */
	private String variableName;
	private DataType type;
	private boolean finalData;
	
	public DataObject(DataObject dataObject) {
		setData(dataObject);
	}
	
	public DataObject() {
		this("");
	}
	public DataObject(String txt) {
		this(txt, false);
	}
	public DataObject(String txt, boolean finalData) {
		setText(txt);
		setFinalData(finalData);
	}
	
	/**
	 * This method <b>ignores</b> the final state of the data object<br>
	 * This method will not change variableName nor finalData
	 */
	void setData(DataObject dataObject) {
		this.type = dataObject.type;
		this.txt = dataObject.txt;
		this.arr = dataObject.arr; //Array: copy reference only
		this.vp = dataObject.vp; //Var pointer: copy reference only
		this.fp = dataObject.fp; //Func pointer: copy reference only
		this.intValue = dataObject.intValue;
		this.longValue = dataObject.longValue;
		this.floatValue = dataObject.floatValue;
		this.doubleValue = dataObject.doubleValue;
		this.charValue = dataObject.charValue;
		this.error = dataObject.error; //Error: copy reference only
	}
	
	/**
	 * This method <b>ignores</b> the final state of the data object<br>
	 * This method will not change variableName nor finalData
	 */
	private void resetValue() {
		this.type = null;
		this.txt = null;
		this.arr = null;
		this.vp = null;
		this.fp = null;
		this.intValue = 0;
		this.longValue = 0;
		this.floatValue = 0;
		this.doubleValue = 0;
		this.charValue = 0;
		this.error = null;
	}
	
	DataObject setArgumentSeparator(String txt) {
		if(finalData)
			return this;
		if(txt == null)
			return setNull();
		
		resetValue();
		this.type = DataType.ARGUMENT_SEPARATOR;
		this.txt = txt;
		
		return this;
	}
	
	public DataObject setText(String txt) {
		if(finalData)
			return this;
		if(txt == null)
			return setNull();
		
		resetValue();
		this.type = DataType.TEXT;
		this.txt = txt;
		
		return this;
	}
	
	public String getText() {
		return toText();
	}
	
	public DataObject setArray(DataObject[] arr) {
		if(finalData)
			return this;
		if(arr == null)
			return setNull();
		
		resetValue();
		this.type = DataType.ARRAY;
		this.arr = arr;
		
		return this;
	}
	
	public DataObject[] getArray() {
		return arr;
	}
	
	public DataObject setVarPointer(VarPointerObject vp) {
		if(finalData)
			return this;
		if(vp == null)
			return setNull();
		
		resetValue();
		this.type = DataType.VAR_POINTER;
		this.vp = vp;
		
		return this;
	}
	
	public VarPointerObject getVarPointer() {
		return vp;
	}
	
	public DataObject setFunctionPointer(FunctionPointerObject fp) {
		if(finalData)
			return this;
		if(fp == null)
			return setNull();
		
		resetValue();
		this.type = DataType.FUNCTION_POINTER;
		this.fp = fp;
		
		return this;
	}
	
	public FunctionPointerObject getFunctionPointer() {
		return fp;
	}
	
	public DataObject setNull() {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.NULL;
		
		return this;
	}
	
	public DataObject setVoid() {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.VOID;
		
		return this;
	}
	
	public DataObject setInt(int intValue) {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.INT;
		this.intValue = intValue;
		
		return this;
	}
	
	public int getInt() {
		return intValue;
	}
	
	/**
	 * Sets data to INT = 1 if boolean value is true else INT = 0
	 */
	public DataObject setBoolean(boolean booleanValue) {
		return setInt(booleanValue?1:0);
	}
	
	public boolean getBoolean() {
		return toBoolean();
	}
	
	public DataObject setLong(long longValue) {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.LONG;
		this.longValue = longValue;
		
		return this;
	}
	
	public long getLong() {
		return longValue;
	}
	
	public DataObject setFloat(float floatValue) {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.FLOAT;
		this.floatValue = floatValue;
		
		return this;
	}
	
	public float getFloat() {
		return floatValue;
	}
	
	public DataObject setDouble(double doubleValue) {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.DOUBLE;
		this.doubleValue = doubleValue;
		
		return this;
	}
	
	public double getDouble() {
		return doubleValue;
	}
	
	public DataObject setChar(char charValue) {
		if(finalData)
			return this;
		
		resetValue();
		this.type = DataType.CHAR;
		this.charValue = charValue;
		
		return this;
	}
	
	public char getChar() {
		return charValue;
	}
	
	public DataObject setError(ErrorObject error) {
		if(finalData)
			return this;
		if(error == null)
			return setNull();
		
		resetValue();
		this.type = DataType.ERROR;
		this.error = error;
		
		return this;
	}
	
	public ErrorObject getError() {
		return error;
	}
	
	public DataObject setVariableName(String variableName) {
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
	
	public DataType getType() {
		return type;
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
	
	private String convertArrayToText() {
		StringBuilder builder = new StringBuilder("[");
		if(arr.length > 0) {
			for(DataObject ele:arr) {
				if(ele.getType() == DataType.ARRAY) {
					builder.append("<Array: len: " + ele.getArray().length + ">");
				}else if(ele.getType() == DataType.VAR_POINTER) {
					builder.append("VP -> {");
					DataObject data = ele.getVarPointer().getVar();
					if(data != null && data.getType() == DataType.ARRAY) {
						builder.append("<Array: len: " + data.getArray().length + ">");
					}else if(data != null && data.getType() == DataType.VAR_POINTER) {
						builder.append("VP -> {...}");
					}else {
						builder.append(data);
					}
					builder.append("}");
				}else {
					builder.append(ele.getText());
				}
				builder.append(", ");
			}
			builder.delete(builder.length() - 2, builder.length());
		}
		builder.append(']');
		return builder.toString();
	}
	
	//Conversion functions
	public String toText() {
		try {
			switch(type) {
				case TEXT:
				case ARGUMENT_SEPARATOR:
					return txt;
				case ARRAY:
					return convertArrayToText();
				case VAR_POINTER:
					if(variableName != null)
						return variableName;
					
					return vp.toString();
				case FUNCTION_POINTER:
					if(variableName != null)
						return variableName;
					
					return fp.toString();
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
			}
		}catch(StackOverflowError e) {
			return "Error";
		}
		
		return null;
	}
	public Character toChar() {
		try {
			switch(type) {
				case TEXT:
				case ARGUMENT_SEPARATOR:
					return null;
				case ARRAY:
					return null;
				case VAR_POINTER:
					return null;
				case FUNCTION_POINTER:
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
			}
		}catch(StackOverflowError e) {
			return null;
		}
		
		return null;
	}
	public Integer toInt() {
		switch(type) {
			case TEXT:
				if(!LangPatterns.matches(txt, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE)) {
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
			case ARRAY:
				return arr.length;
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	public Long toLong() {
		switch(type) {
			case TEXT:
				if(!LangPatterns.matches(txt, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE)) {
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
			case ARRAY:
				return (long)arr.length;
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	public Float toFloat() {
		switch(type) {
			case TEXT:
				if(!LangPatterns.matches(txt, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE)) {
					try {
						return Float.parseFloat(txt);
					}catch(NumberFormatException ignore) {}
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
			case ARRAY:
				return (float)arr.length;
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	public Double toDouble() {
		switch(type) {
			case TEXT:
				if(!LangPatterns.matches(txt, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE)) {
					try {
						return Double.parseDouble(txt);
					}catch(NumberFormatException ignore) {}
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
			case ARRAY:
				return (double)arr.length;
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	public DataObject[] toArray() {
		switch(type) {
			case ARRAY:
				return arr;
			
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
			case ARRAY:
				return arr.length > 0;
			case ERROR:
				return error.getErrno() != 0;
				
			case VAR_POINTER:
			case FUNCTION_POINTER:
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
				if(!LangPatterns.matches(txt, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE)) {
					//INT
					try {
						return Integer.parseInt(txt);
					}catch(NumberFormatException ignore) {}
					
					//LONG
					try {
						return Long.parseLong(txt);
					}catch(NumberFormatException ignore) {}
					
					//FLOAT
					try {
						float floatNumber = Float.parseFloat(txt);
						if(floatNumber != Float.POSITIVE_INFINITY && floatNumber != Float.NEGATIVE_INFINITY) {
							return floatNumber;
						}
					}catch(NumberFormatException ignore) {}
					
					//DOUBLE
					try {
						return Double.parseDouble(txt);
					}catch(NumberFormatException ignore) {}
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
			case ERROR:
				return error.getErrno();
			case ARRAY:
				return arr.length;
			
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
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
			
			case ARRAY:
				if(other.type == DataType.ARRAY)
					return Objects.deepEquals(arr, other.arr);
				return number != null && arr.length == number.intValue();
			
			case VAR_POINTER:
				return vp.equals(other.vp);
			
			case FUNCTION_POINTER:
				return fp.equals(other.fp);
			
			case ERROR:
				switch(other.type) {
					case TEXT:
						if(number == null)
							return error.getErrmsg().equals(other.txt);
						return error.getErrno() == number.intValue();
					
					case CHAR:
					case INT:
					case LONG:
					case FLOAT:
					case DOUBLE:
					case ARRAY:
						return number != null && error.getErrno() == number.intValue();
					
					case ERROR:
						return error.equals(other.error);
					
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return false;
				}
				
				return false;
			
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
			return this.type.equals(other.type) && Objects.equals(this.txt, other.txt) && Objects.deepEquals(this.arr, other.arr) &&
			Objects.equals(this.vp, other.vp) && Objects.equals(this.fp, other.fp) && this.intValue == other.intValue &&
			this.longValue == other.longValue && this.floatValue == other.floatValue && this.doubleValue == other.doubleValue &&
			this.charValue == other.charValue && Objects.equals(this.error, other.error);
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return false;
				}
			
			case ERROR:
				if(other.type == DataType.TEXT && number.getType() == DataType.NULL)
					return error.getErrmsg().compareTo(other.txt) < 0;
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return false;
				}
				
				return false;
				
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return false;
				}
			
			case ERROR:
				if(other.type == DataType.TEXT && number.getType() == DataType.NULL)
					return error.getErrmsg().compareTo(other.txt) > 0;
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
					case ERROR:
					case ARRAY:
					case TEXT:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return false;
				}
				
				return false;
				
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
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
	
	//General operation functions
	/**
	 * For "@"
	 */
	public DataObject opLen() {
		switch(type) {
			case ARRAY:
				return new DataObject().setInt(arr.length);
			case TEXT:
				return new DataObject().setInt(txt.length());
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "^"
	 */
	public DataObject opDeepCopy() {
		switch(type) {
			case ARRAY:
				DataObject[] arrCopy = new DataObject[arr.length];
				for(int i = 0;i < arr.length;i++) {
					arrCopy[i] = arr[i].opDeepCopy();
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
				return new DataObject(this);
		}
		
		return null;
	}
	/**
	 * For "&lt;=&gt;"
	 */
	public DataObject opSpaceship(DataObject other) {
		if(isLessThan(other))
			return new DataObject().setInt(-1);
		if(isEquals(other))
			return new DataObject().setInt(0);
		if(isGreaterThan(other))
			return new DataObject().setInt(1);
		
		return new DataObject().setNull();
	}
	
	//Math operation functions
	/**
	 * For "▲"
	 */
	public DataObject opInc() {
		switch(type) {
			case INT:
				return new DataObject().setInt(intValue + 1);
			case LONG:
				return new DataObject().setLong(longValue + 1);
			case FLOAT:
				return new DataObject().setFloat(floatValue + 1.f);
			case DOUBLE:
				return new DataObject().setDouble(doubleValue + 1.d);
			case CHAR:
				return new DataObject().setChar((char)(charValue + 1));
			
			case TEXT:
			case ARRAY:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	/**
	 * For "▼"
	 */
	public DataObject opDec() {
		switch(type) {
			case INT:
				return new DataObject().setInt(intValue - 1);
			case LONG:
				return new DataObject().setLong(longValue - 1);
			case FLOAT:
				return new DataObject().setFloat(floatValue - 1.f);
			case DOUBLE:
				return new DataObject().setDouble(doubleValue - 1.d);
			case CHAR:
				return new DataObject().setChar((char)(charValue - 1));
			
			case TEXT:
			case ARRAY:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	/**
	 * For "+"
	 */
	public DataObject opPos() {
		return new DataObject(this);
	}
	/**
	 * For "-"
	 */
	public DataObject opInv() {
		switch(type) {
			case INT:
				return new DataObject().setInt(-intValue);
			case LONG:
				return new DataObject().setLong(-longValue);
			case FLOAT:
				return new DataObject().setFloat(-floatValue);
			case DOUBLE:
				return new DataObject().setDouble(-doubleValue);
			case CHAR:
				return new DataObject().setChar((char)(-charValue));
			case ARRAY:
				DataObject[] arrInv = new DataObject[arr.length];
				int index = arrInv.length - 1;
				for(DataObject dataObject:arr)
					arrInv[index--] = dataObject;
				
				return new DataObject().setArray(arrInv);
			
			case TEXT:
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	/**
	 * For "+"
	 */
	public DataObject opAdd(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue + dataObject.intValue);
					case LONG:
						return new DataObject().setLong(intValue + dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(intValue + dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(intValue + dataObject.doubleValue);
					case CHAR:
						return new DataObject().setInt(intValue + dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue + dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue + dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(longValue + dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(longValue + dataObject.doubleValue);
					case CHAR:
						return new DataObject().setLong(longValue + dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case FLOAT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setFloat(floatValue + dataObject.intValue);
					case LONG:
						return new DataObject().setFloat(floatValue + dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(floatValue + dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(floatValue + dataObject.doubleValue);
					case CHAR:
						return new DataObject().setFloat(floatValue + dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case DOUBLE:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(doubleValue + dataObject.intValue);
					case LONG:
						return new DataObject().setDouble(doubleValue + dataObject.longValue);
					case FLOAT:
						return new DataObject().setDouble(doubleValue + dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(doubleValue + dataObject.doubleValue);
					case CHAR:
						return new DataObject().setDouble(doubleValue + dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case CHAR:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(charValue + dataObject.intValue);
					case LONG:
						return new DataObject().setLong(charValue + dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(charValue + dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(charValue + dataObject.doubleValue);
					case CHAR:
						return new DataObject().setInt(charValue + dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case TEXT:
				return new DataObject(txt + dataObject.getText());
			case ARRAY:
				DataObject[] arrNew = new DataObject[arr.length + 1];
				for(int i = 0;i < arr.length;i++)
					arrNew[i] = arr[i];
				
				arrNew[arr.length] = new DataObject(dataObject);
				return new DataObject().setArray(arrNew);
			
			case ERROR:
			case VAR_POINTER:
			case FUNCTION_POINTER:
			case NULL:
			case VOID:
			case ARGUMENT_SEPARATOR:
				return null;
		}
		
		return null;
	}
	/**
	 * For "-"
	 */
	public DataObject opSub(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue - dataObject.intValue);
					case LONG:
						return new DataObject().setLong(intValue - dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(intValue - dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(intValue - dataObject.doubleValue);
					case CHAR:
						return new DataObject().setInt(intValue - dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue - dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue - dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(longValue - dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(longValue - dataObject.doubleValue);
					case CHAR:
						return new DataObject().setLong(longValue - dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case FLOAT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setFloat(floatValue - dataObject.intValue);
					case LONG:
						return new DataObject().setFloat(floatValue - dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(floatValue - dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(floatValue - dataObject.doubleValue);
					case CHAR:
						return new DataObject().setFloat(floatValue - dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case DOUBLE:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(doubleValue - dataObject.intValue);
					case LONG:
						return new DataObject().setDouble(doubleValue - dataObject.longValue);
					case FLOAT:
						return new DataObject().setDouble(doubleValue - dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(doubleValue - dataObject.doubleValue);
					case CHAR:
						return new DataObject().setDouble(doubleValue - dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case CHAR:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(charValue - dataObject.intValue);
					case LONG:
						return new DataObject().setLong(charValue - dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(charValue - dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(charValue - dataObject.doubleValue);
					case CHAR:
						return new DataObject().setInt(charValue - dataObject.charValue);
					
					case TEXT:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "*"
	 */
	public DataObject opMul(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue * dataObject.intValue);
					case LONG:
						return new DataObject().setLong(intValue * dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(intValue * dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(intValue * dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue * dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue * dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(longValue * dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(longValue * dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case FLOAT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setFloat(floatValue * dataObject.intValue);
					case LONG:
						return new DataObject().setFloat(floatValue * dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(floatValue * dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(floatValue * dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case DOUBLE:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(doubleValue * dataObject.intValue);
					case LONG:
						return new DataObject().setDouble(doubleValue * dataObject.longValue);
					case FLOAT:
						return new DataObject().setDouble(doubleValue * dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(doubleValue * dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			
			case TEXT:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue < 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.INVALID_ARGUMENTS));
						
						StringBuilder builder = new StringBuilder();
						for(int i = 0;i < dataObject.intValue;i++)
							builder.append(txt);
						
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "**"
	 */
	public DataObject opPow(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(Math.pow(intValue, dataObject.intValue));
					case LONG:
						return new DataObject().setDouble(Math.pow(intValue, dataObject.longValue));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(intValue, dataObject.floatValue));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(intValue, dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(Math.pow(longValue, dataObject.intValue));
					case LONG:
						return new DataObject().setDouble(Math.pow(longValue, dataObject.longValue));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(longValue, dataObject.floatValue));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(longValue, dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case FLOAT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(Math.pow(floatValue, dataObject.intValue));
					case LONG:
						return new DataObject().setDouble(Math.pow(floatValue, dataObject.longValue));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(floatValue, dataObject.floatValue));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(floatValue, dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case DOUBLE:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(Math.pow(doubleValue, dataObject.intValue));
					case LONG:
						return new DataObject().setDouble(Math.pow(doubleValue, dataObject.longValue));
					case FLOAT:
						return new DataObject().setDouble(Math.pow(doubleValue, dataObject.floatValue));
					case DOUBLE:
						return new DataObject().setDouble(Math.pow(doubleValue, dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "/"
	 */
	public DataObject opDiv(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setFloat(intValue / 0.f);
						
						if(intValue % dataObject.intValue != 0)
							return new DataObject().setFloat(intValue / (float)dataObject.intValue);
						
						return new DataObject().setInt(intValue / dataObject.intValue);
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setFloat(intValue / 0.f);
						
						if(intValue % dataObject.longValue != 0)
							return new DataObject().setFloat(intValue / (float)dataObject.longValue);
						
						return new DataObject().setLong(intValue / dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(intValue / dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(intValue / dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setFloat(longValue / 0.f);
						
						if(longValue % dataObject.intValue != 0)
							return new DataObject().setFloat(longValue / (float)dataObject.intValue);
						
						return new DataObject().setLong(longValue / dataObject.intValue);
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setFloat(longValue / 0.f);
						
						if(longValue % dataObject.longValue != 0)
							return new DataObject().setFloat(longValue / (float)dataObject.longValue);
						
						return new DataObject().setLong(longValue / dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(longValue / dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(longValue / dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case FLOAT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setFloat(floatValue / dataObject.intValue);
					case LONG:
						return new DataObject().setFloat(floatValue / dataObject.longValue);
					case FLOAT:
						return new DataObject().setFloat(floatValue / dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(floatValue / dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case DOUBLE:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setDouble(doubleValue / dataObject.intValue);
					case LONG:
						return new DataObject().setDouble(doubleValue / dataObject.longValue);
					case FLOAT:
						return new DataObject().setDouble(doubleValue / dataObject.floatValue);
					case DOUBLE:
						return new DataObject().setDouble(doubleValue / dataObject.doubleValue);
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "//"
	 */
	public DataObject opFloorDiv(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setInt(intValue / dataObject.intValue);
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(intValue / dataObject.longValue);
					case FLOAT:
						if(dataObject.floatValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(intValue / dataObject.floatValue));
					case DOUBLE:
						if(dataObject.doubleValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(intValue / dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(longValue / dataObject.intValue);
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(longValue / dataObject.longValue);
					case FLOAT:
						if(dataObject.floatValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(longValue / dataObject.floatValue));
					case DOUBLE:
						if(dataObject.doubleValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(longValue / dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				return null;
			case FLOAT:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(floatValue / dataObject.intValue));
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(floatValue / dataObject.longValue));
					case FLOAT:
						if(dataObject.floatValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setFloat((float)Math.floor(floatValue / dataObject.floatValue));
					case DOUBLE:
						if(dataObject.doubleValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(floatValue / dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
						return null;
				}
				
				return null;
			case DOUBLE:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(doubleValue / dataObject.intValue));
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(floatValue / dataObject.longValue));
					case FLOAT:
						if(dataObject.floatValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(floatValue / dataObject.floatValue));
					case DOUBLE:
						if(dataObject.doubleValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setDouble(Math.floor(floatValue / dataObject.doubleValue));
					
					case TEXT:
					case CHAR:
					case ARRAY:
					case ERROR:
					case VAR_POINTER:
					case FUNCTION_POINTER:
					case NULL:
					case VOID:
					case ARGUMENT_SEPARATOR:
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "%"
	 */
	public DataObject opMod(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setInt(intValue % dataObject.intValue);
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(intValue % dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						if(dataObject.intValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(longValue % dataObject.intValue);
					case LONG:
						if(dataObject.longValue == 0)
							return new DataObject().setError(new ErrorObject(InterpretingError.DIV_BY_ZERO));
						
						return new DataObject().setLong(longValue % dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&"
	 */
	public DataObject opAnd(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue & dataObject.intValue);
					case LONG:
						return new DataObject().setLong(intValue & dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue & dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue & dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "|"
	 */
	public DataObject opOr(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue | dataObject.intValue);
					case LONG:
						return new DataObject().setLong(intValue | dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue | dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue | dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "^"
	 */
	public DataObject opXor(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue ^ dataObject.intValue);
					case LONG:
						return new DataObject().setLong(intValue ^ dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue ^ dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue ^ dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "~"
	 */
	public DataObject opNot() {
		switch(type) {
			case INT:
				return new DataObject().setInt(~intValue);
			case LONG:
				return new DataObject().setLong(~longValue);
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&lt;&lt;"
	 */
	public DataObject opLshift(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue << dataObject.intValue);
					case LONG:
						return new DataObject().setLong((long)intValue << dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue << dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue << dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&gt;&gt;"
	 */
	public DataObject opRshift(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue >> dataObject.intValue);
					case LONG:
						return new DataObject().setLong((long)intValue >> dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue >> dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue >> dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "&gt;&gt;&gt;"
	 */
	public DataObject opRzshift(DataObject dataObject) {
		switch(type) {
			case INT:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setInt(intValue >>> dataObject.intValue);
					case LONG:
						return new DataObject().setLong((long)intValue >>> dataObject.longValue);
					
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
						return null;
				}
				return null;
			case LONG:
				switch(dataObject.type) {
					case INT:
						return new DataObject().setLong(longValue >>> dataObject.intValue);
					case LONG:
						return new DataObject().setLong(longValue >>> dataObject.longValue);
					
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
				return null;
		}
		
		return null;
	}
	/**
	 * For "[...]"
	 */
	public DataObject opGetItem(DataObject dataObject) {
		switch(type) {
			case ARRAY:
				if(dataObject.type == DataType.INT) {
					int len = arr.length;
					int index = dataObject.intValue;
					if(index < 0)
						index = len + index;
					
					if(index < 0 || index >= len)
						return new DataObject().setError(new ErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS));
					
					return arr[index];
				}
				
				return null;
			case TEXT:
				if(dataObject.type == DataType.INT) {
					int len = txt.length();
					int index = dataObject.intValue;
					if(index < 0)
						index = len + index;
					
					if(index < 0 || index >= len)
						return new DataObject().setError(new ErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS));
					
					return new DataObject().setChar(txt.charAt(index));
				}
				
				return null;
			case CHAR:
				if(dataObject.type == DataType.INT) {
					int index = dataObject.intValue;
					if(index < 0)
						index = 1 + index;
					
					if(index != 0)
						return new DataObject().setError(new ErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS));
					
					return new DataObject().setChar(charValue);
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
				return null;
		}
		
		return null;
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
			return this.type.equals(that.type) && Objects.equals(this.txt, that.txt) && Objects.deepEquals(this.arr, that.arr) &&
			Objects.equals(this.vp, that.vp) && Objects.equals(this.fp, that.fp) && this.intValue == that.intValue &&
			this.longValue == that.longValue && this.floatValue == that.floatValue && this.doubleValue == that.doubleValue &&
			this.charValue == that.charValue && Objects.equals(this.error, that.error);
		}catch(StackOverflowError e) {
			return false;
		}
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(type, txt, arr, vp, fp, intValue, longValue, floatValue, doubleValue, charValue, error);
	}
	
	public static enum DataType {
		TEXT, CHAR, INT, LONG, FLOAT, DOUBLE, ARRAY, VAR_POINTER, FUNCTION_POINTER, ERROR, NULL, VOID, ARGUMENT_SEPARATOR;
	}
	public static final class FunctionPointerObject {
		/**
		 * Normal function pointer
		 */
		public static final int NORMAL = 0;
		/**
		 * Pointer to a predefined function
		 */
		public static final int PREDEFINED = 1;
		/**
		 * Function which is defined in the language
		 */
		public static final int EXTERNAL = 2;
		
		private final List<VariableNameNode> parameterList;
		private final AbstractSyntaxTree functionBody;
		private final LangPredefinedFunctionObject predefinedFunction;
		private final LangExternalFunctionObject externalFunction;
		private final int functionPointerType;
		
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(List<VariableNameNode> parameterList, AbstractSyntaxTree functionBody) {
			this.parameterList = parameterList == null?null:new ArrayList<>(parameterList);
			this.functionBody = functionBody;
			this.predefinedFunction = null;
			this.externalFunction = null;
			this.functionPointerType = NORMAL;
		}
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(LangPredefinedFunctionObject predefinedFunction) {
			this.parameterList = null;
			this.functionBody = null;
			this.predefinedFunction = predefinedFunction;
			this.externalFunction = null;
			this.functionPointerType = PREDEFINED;
		}
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(LangExternalFunctionObject externalFunction) {
			this.parameterList = null;
			this.functionBody = null;
			this.predefinedFunction = null;
			this.externalFunction = externalFunction;
			this.functionPointerType = EXTERNAL;
		}
		
		public List<VariableNameNode> getParameterList() {
			return parameterList;
		}
		
		public AbstractSyntaxTree getFunctionBody() {
			return functionBody;
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
			switch(functionPointerType) {
				case NORMAL:
					return "<Normal FP>";
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
			Objects.equals(this.functionBody, that.functionBody) && Objects.equals(this.predefinedFunction, that.predefinedFunction) &&
			Objects.equals(this.externalFunction, externalFunction);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(functionPointerType, parameterList, functionBody, predefinedFunction, externalFunction);
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
			return "VP -> {" + var + "}";
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
	public static final class ErrorObject {
		private final InterpretingError err;
		
		public ErrorObject(InterpretingError err) {
			if(err == null)
				this.err = InterpretingError.NO_ERROR;
			else
				this.err = err;
		}
		
		public InterpretingError getInterprettingError() {
			return err;
		}
		
		public int getErrno() {
			return err.getErrorCode();
		}
		
		public String getErrmsg() {
			return err.getErrorText();
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
			return this.err.equals(that.err);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(err);
		}
	}
}