package me.jddev0.module.lang;

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
}