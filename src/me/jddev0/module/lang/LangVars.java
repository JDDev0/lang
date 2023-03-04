package me.jddev0.module.lang;

import java.util.Arrays;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraint;
import me.jddev0.module.lang.DataObject.ErrorObject;
import me.jddev0.module.lang.DataObject.StructObject;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangInterpreter.StackElement;

final class LangVars {
	private static final DataTypeConstraint TYPE_CONSTRAINT_OPTIONAL_TEXT = DataTypeConstraint.fromAllowedTypes(Arrays.asList(
					DataType.NULL, DataType.TEXT
			));
	
	public static final StructObject STRUCT_STACK_FRAME = new StructObject(new String[] {
					"$path",
					"$file",
					"$functionName",
					"$modulePath",
					"$moduleFile"
			}, new DataTypeConstraint[] {
					TYPE_CONSTRAINT_OPTIONAL_TEXT,
					TYPE_CONSTRAINT_OPTIONAL_TEXT,
					TYPE_CONSTRAINT_OPTIONAL_TEXT,
					TYPE_CONSTRAINT_OPTIONAL_TEXT,
					TYPE_CONSTRAINT_OPTIONAL_TEXT
			});
	
	private final LangInterpreter interpreter;
	
	public LangVars(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	private void addLangVar(String variableName, DataObject langVar, final int SCOPE_ID) {
		interpreter.data.get(SCOPE_ID).var.put(variableName, langVar.setLangVar().setVariableName(variableName));
	}
	
	private void addStaticLangVar(String variableName, DataObject langVar, final int SCOPE_ID) {
		interpreter.data.get(SCOPE_ID).var.computeIfAbsent(variableName, key -> langVar.setStaticData(true).setLangVar().setVariableName(variableName));
	}
	
	public void addLangVars(DataObject langArgs, final int SCOPE_ID) {
		interpreter.data.get(SCOPE_ID).var.put("&LANG_ARGS", langArgs == null?new DataObject().setArray(new DataObject[0]).
				setFinalData(true).setLangVar().setVariableName("&LANG_ARGS"):langArgs);
		
		addSystemLangVars(SCOPE_ID);
		addExectionLangVars(interpreter.getCurrentCallStackElement(), SCOPE_ID);
		addNumberLangVars(SCOPE_ID);
		addErrorLangVars(SCOPE_ID);
		addTypeLangVars(SCOPE_ID);
		addCompositeLangVars(SCOPE_ID);
	}
	private void addSystemLangVars(final int SCOPE_ID) {
		addLangVar("$LANG_VERSION", new DataObject(LangInterpreter.VERSION, true).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_NAME", new DataObject("Standard Lang", true).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_RAND_MAX", new DataObject().setInt(Integer.MAX_VALUE).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_OS_NAME", new DataObject(System.getProperty("os.name")).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_OS_VER", new DataObject(System.getProperty("os.version")).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_OS_ARCH", new DataObject(System.getProperty("os.arch")).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_OS_FILE_SEPARATOR", new DataObject(System.getProperty("file.separator")).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_OS_LINE_SEPARATOR", new DataObject(System.getProperty("line.separator")).setFinalData(true), SCOPE_ID);
	}
	private void addExectionLangVars(StackElement currentStackElement, final int SCOPE_ID) {
		addLangVar("$LANG_PATH", new DataObject(currentStackElement.getLangPath(), true).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_FILE", new DataObject(currentStackElement.getLangFile(), true).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_CURRENT_FUNCTION", new DataObject(currentStackElement.getLangFunctionName(), true).setFinalData(true), SCOPE_ID);
		
		//Module vars
		if(currentStackElement.module != null) {
			addLangVar("$LANG_MODULE_STATE", new DataObject(currentStackElement.module.isLoad()?"load":"unload"), SCOPE_ID);
			
			String prefix = "<module:" + currentStackElement.module.getFile() + "[" + currentStackElement.module.getLangModuleConfiguration().getName() + "]>";
			
			String modulePath = currentStackElement.getLangPath().substring(prefix.length());
			if(!modulePath.startsWith("/"))
				modulePath = "/" + modulePath;
			
			addLangVar("$LANG_MODULE_PATH", new DataObject(modulePath, true), SCOPE_ID);
			addLangVar("$LANG_MODULE_FILE", new DataObject(currentStackElement.getLangFile(), true), SCOPE_ID);
		}
	}
	private void addNumberLangVars(final int SCOPE_ID) {
		addLangVar("$LANG_INT_MIN", new DataObject().setInt(Integer.MIN_VALUE).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_INT_MAX", new DataObject().setInt(Integer.MAX_VALUE).setFinalData(true), SCOPE_ID);
		
		addLangVar("$LANG_LONG_MIN", new DataObject().setLong(Long.MIN_VALUE).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_LONG_MAX", new DataObject().setLong(Long.MAX_VALUE).setFinalData(true), SCOPE_ID);
		
		addLangVar("$LANG_FLOAT_NAN", new DataObject().setFloat(Float.NaN).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_FLOAT_POS_INF", new DataObject().setFloat(Float.POSITIVE_INFINITY).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_FLOAT_NEG_INF", new DataObject().setFloat(Float.NEGATIVE_INFINITY).setFinalData(true), SCOPE_ID);
		
		addLangVar("$LANG_DOUBLE_NAN", new DataObject().setDouble(Double.NaN).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_DOUBLE_POS_INF", new DataObject().setDouble(Double.POSITIVE_INFINITY).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_DOUBLE_NEG_INF", new DataObject().setDouble(Double.NEGATIVE_INFINITY).setFinalData(true), SCOPE_ID);
		
		addLangVar("$LANG_MATH_PI", new DataObject().setDouble(Math.PI).setFinalData(true), SCOPE_ID);
		addLangVar("$LANG_MATH_E", new DataObject().setDouble(Math.E).setFinalData(true), SCOPE_ID);
	}
	private void addErrorLangVars(final int SCOPE_ID) {
		for(InterpretingError error:InterpretingError.values()) {
			String upperCaseErrorName = error.name().toUpperCase();
			String variableName = "$LANG_ERROR_" + upperCaseErrorName;
			addLangVar(variableName, new DataObject().setError(new ErrorObject(error)).setFinalData(true), SCOPE_ID);
			variableName = "$LANG_ERRNO_" + upperCaseErrorName;
			addLangVar(variableName, new DataObject().setInt(error.getErrorCode()).setFinalData(true), SCOPE_ID);
		}
		
		//Non-final
		addStaticLangVar("$LANG_ERRNO", new DataObject().setError(new ErrorObject(InterpretingError.NO_ERROR)), SCOPE_ID);
	}
	private void addTypeLangVars(final int SCOPE_ID) {
		for(DataType type:DataType.values()) {
			String upperCaseTypeName = type.name().toUpperCase();
			String variableName = "$LANG_TYPE_" + upperCaseTypeName;
			addLangVar(variableName, new DataObject().setTypeValue(type).setFinalData(true), SCOPE_ID);
		}
	}
	private void addCompositeLangVars(final int SCOPE_ID) {
		addStaticLangVar("&StackFrame", new DataObject().setStruct(STRUCT_STACK_FRAME).setFinalData(true), SCOPE_ID);
	}
}