package me.jddev0.module.lang;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.jddev0.module.lang.DataObject.DataTypeConstraint;
import me.jddev0.module.lang.DataObject.DataTypeConstraintException;
import me.jddev0.module.lang.DataObject.VarPointerObject;
import me.jddev0.module.lang.LangFunction.*;
import me.jddev0.module.lang.LangFunction.LangParameter.*;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;

public class LangNativeFunction implements LangPredefinedFunctionObject {
	//TODO add support for native function overloading [Change class -> store list of public static InternalFunction and move all parameters into internal class, define callFunc for outer class and add getFunctions]
	
	//TODO remove and add as parameter to call Function instance to call
	public final LangInterpreter interpreter;
	
	public final String functionName;
	public final String functionInfo;
	public final List<Class<?>> methodParameterTypeList;
	public final List<DataObject> parameterList;
	public final List<DataTypeConstraint> paramaterDataTypeConstraintList;
	public final List<ParameterAnnotation> parameterAnnotationList;
	public final List<String> paramaterInfoList;
	public final DataTypeConstraint returnValueTypeConstraint;
	public final Object instance;
	public final Method functionBody;
	public final boolean hasInterpreterParameter;
	
	public final boolean linkerFunction;
	public final boolean deprecated;
	public final String deprecatedRemoveVersion;
	public final String deprecatedReplacementFunction;
	
	public static Map<String, LangNativeFunction> getLangFunctionsOfClass(LangInterpreter interpreter, Object instance, Class<?> clazz)
			throws IllegalArgumentException, DataTypeConstraintException {
		Map<String, LangNativeFunction> langNativeFunctions = new HashMap<>();
		
		for(Method method:clazz.getDeclaredMethods()) {
			//Add static methods if instance is null else add non-static methods
			if(instance == null ^ Modifier.isStatic(method.getModifiers()))
				continue;
			
			if(method.isAnnotationPresent(LangFunction.class)) {
				LangNativeFunction langNativeFunction = create(interpreter, instance, method);
				String functionName = langNativeFunction.getFunctionName();
				
				/*if(langNativeFunctions.containsKey(functionName)) {
					//TODO add support for native function overloading [Change class -> store list of public static InternalFunction and move all parameters into internal class, define
					//     callFunc for outer class and add getFunctions]
				}*/
				
				langNativeFunctions.put(functionName, langNativeFunction);
			}
		}
		
		return langNativeFunctions;
	}
	
	public static LangNativeFunction create(LangInterpreter interpreter, Object instance, Method functionBody)
			throws IllegalArgumentException, DataTypeConstraintException {
		LangFunction langFunction = functionBody.getAnnotation(LangFunction.class);
		if(langFunction == null)
			throw new IllegalArgumentException("Method must be annotated with @LangFunction");
		
		String functionName = langFunction.value();
		
		boolean linkerFunction = langFunction.isLinkerFunction();
		
		boolean deprecated = langFunction.isDeprecated();
		String deprecatedRemoveVersion = langFunction.getDeprecatedRemoveVersion();
		if(deprecatedRemoveVersion.equals(""))
			deprecatedRemoveVersion = null;
		String deprecatedReplacementFunction = langFunction.getDeprecatedReplacementFunction();
		if(deprecatedReplacementFunction.equals(""))
			deprecatedReplacementFunction = null;
		
		LangInfo langInfo = functionBody.getAnnotation(LangInfo.class);
		String functionInfo = langInfo == null?null:langInfo.value();
		
		DataTypeConstraint returnValueTypeConstraint = DataObject.CONSTRAINT_NORMAL;
		
		AllowedTypes allowedTypes = functionBody.getAnnotation(AllowedTypes.class);
		if(allowedTypes != null)
			returnValueTypeConstraint = DataTypeConstraint.fromAllowedTypes(Arrays.asList(allowedTypes.value()));
		
		NotAllowedTypes notAllowedTypes = functionBody.getAnnotation(NotAllowedTypes.class);
		if(notAllowedTypes != null)
			returnValueTypeConstraint = DataTypeConstraint.fromNotAllowedTypes(Arrays.asList(notAllowedTypes.value()));
		
		if(allowedTypes != null && notAllowedTypes != null)
			throw new IllegalArgumentException("Method must not be annotated with both @AllowedTypes and @NotAllowedTypes");
		
		Parameter[] parameters = functionBody.getParameters();
		if(parameters.length == 0)
			throw new IllegalArgumentException("Method must have at least one parameter (int SCOPE_ID)");
		
		Parameter firstParam = parameters[0];
		boolean hasInterpreterParameter = firstParam.getType().isAssignableFrom(LangInterpreter.class);
		
		Parameter secondParam = hasInterpreterParameter && parameters.length >= 2?parameters[1]:null;
		
		if(hasInterpreterParameter?
				secondParam == null || !secondParam.getType().isAssignableFrom(int.class):
					!firstParam.getType().isAssignableFrom(int.class))
			throw new IllegalArgumentException("The method must start with (LangInterpreter interpreter, int SCOPE_ID) or (int SCOPE_ID)");
		
		int diff = hasInterpreterParameter?2:1;
		List<Class<?>> methodParameterTypeList = new ArrayList<>(parameters.length - diff);
		List<DataObject> parameterList = new ArrayList<>(parameters.length - diff);
		List<DataTypeConstraint> paramaterDataTypeConstraintList = new ArrayList<>(parameters.length - diff);
		List<ParameterAnnotation> parameterAnnotationList = new ArrayList<>();
		List<String> paramaterInfoList = new ArrayList<>(parameters.length - diff);
		
		for(int i = diff;i < parameters.length;i++) {
			Parameter parameter = parameters[i];
			
			LangParameter langParameter = parameter.getAnnotation(LangParameter.class);
			if(langParameter == null)
				throw new IllegalArgumentException("Method parameters after the SCOPE_ID parameter must be annotated with @LangParameter");
			
			ParameterAnnotation parameterAnnotation;
			
			//If parameterCount > 1 -> Multiple type constraining annotations where used
			int typeConstraintingParameterCount = 0;
			
			boolean isNumberValue = parameter.isAnnotationPresent(NumberValue.class) && typeConstraintingParameterCount++ >= 0;
			
			//Call by pointer can be used with additional @AllowedTypes or @NotAllowedTypes type constraint
			boolean isCallByPointer = parameter.isAnnotationPresent(CallByPointer.class);
			if(isNumberValue && isCallByPointer)
				throw new IllegalArgumentException("@LangParameter must not be annotated with both @NumberValue and @CallByPointer");
			
			if(isNumberValue) {
				parameterAnnotation = ParameterAnnotation.NUMBER;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class) && !parameter.getType().isAssignableFrom(Number.class))
					throw new IllegalArgumentException("@LangParameter which are annotated with @NumberValue must be of type DataObject or Number");
			}else if(isCallByPointer) {
				parameterAnnotation = ParameterAnnotation.CALL_BY_POINTER;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class))
					throw new IllegalArgumentException("@LangParameter which are annotated with @CallByPointer must be of type DataObject");
			}else {
				parameterAnnotation = ParameterAnnotation.NORMAL;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class))
					throw new IllegalArgumentException("@LangParameter must be of type DataObject (Other types besides DataObject are allowed for certain annotations)");
			}
			
			String variableName = langParameter.value();
			
			DataTypeConstraint typeConstraint = null;
			
			allowedTypes = parameter.getAnnotation(AllowedTypes.class);
			if(allowedTypes != null && typeConstraintingParameterCount++ >= 0)
				typeConstraint = DataTypeConstraint.fromAllowedTypes(Arrays.asList(allowedTypes.value()));
			
			notAllowedTypes = parameter.getAnnotation(NotAllowedTypes.class);
			if(notAllowedTypes != null && typeConstraintingParameterCount++ >= 0)
				typeConstraint = DataTypeConstraint.fromNotAllowedTypes(Arrays.asList(notAllowedTypes.value()));
			
			if(typeConstraintingParameterCount > 1)
				throw new IllegalArgumentException("DataObject parameter must be annotated with at most one of @AllowedTypes, @NotAllowedTypes, or @NumberValue");
			
			langInfo = parameter.getAnnotation(LangInfo.class);
			String paramaterInfo = langInfo == null?null:langInfo.value();
			
			if(typeConstraint == null)
				typeConstraint = DataObject.getTypeConstraintFor(variableName);
			
			parameterAnnotationList.add(parameterAnnotation);
			methodParameterTypeList.add(parameter.getType());
			parameterList.add(new DataObject().setVariableName(variableName));
			paramaterDataTypeConstraintList.add(typeConstraint);
			paramaterInfoList.add(paramaterInfo);
			
			//TODO error if parameter name already exists or if name is invalid
		}
		
		return new LangNativeFunction(interpreter, functionName, functionInfo, methodParameterTypeList, parameterList,
				paramaterDataTypeConstraintList, paramaterInfoList, parameterAnnotationList, returnValueTypeConstraint, instance,
				functionBody, hasInterpreterParameter, linkerFunction, deprecated, deprecatedRemoveVersion, deprecatedReplacementFunction);
	}
	
	/**
	 * @param instance Null for static method
	 */
	private LangNativeFunction(LangInterpreter interpreter, String functionName, String functionInfo, List<Class<?>> methodParameterTypeList,
			List<DataObject> parameterList, List<DataTypeConstraint> paramaterDataTypeConstraintList, List<String> paramaterInfoList,
			List<ParameterAnnotation> parameterAnnotationList, DataTypeConstraint returnValueTypeConstraint, Object instance, Method functionBody,
			boolean hasInterpreterParameter, boolean linkerFunction, boolean deprecated, String deprecatedRemoveVersion,
			String deprecatedReplacementFunction) {
		this.interpreter = interpreter;
		this.functionName = functionName;
		this.functionInfo = functionInfo;
		this.methodParameterTypeList = methodParameterTypeList;
		this.parameterList = parameterList;
		this.paramaterDataTypeConstraintList = paramaterDataTypeConstraintList;
		this.paramaterInfoList = paramaterInfoList;
		this.parameterAnnotationList = parameterAnnotationList;
		this.returnValueTypeConstraint = returnValueTypeConstraint;
		this.instance = instance;
		this.functionBody = functionBody;
		this.hasInterpreterParameter = hasInterpreterParameter;
		this.linkerFunction = linkerFunction;
		this.deprecated = deprecated;
		this.deprecatedRemoveVersion = deprecatedRemoveVersion;
		this.deprecatedReplacementFunction = deprecatedReplacementFunction;
	}
	
	@Override
	public DataObject callFunc(List<DataObject> argumentList, int SCOPE_ID) {
		//TODO remove checks from this method and move to directly to interpreter
		//TODO add varargs support @VarArgs(includeSeparetors) [Type will be array or text]
		
		int argCount = parameterList.size();
		
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(combinedArgumentList.size() < argCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Not enough arguments (%s needed)", argCount), SCOPE_ID);
		if(combinedArgumentList.size() > argCount)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Too many arguments (%s needed)", argCount), SCOPE_ID);
		
		int diff = hasInterpreterParameter?2:1;
		Object[] methodArgumentList = new Object[diff + argCount];
		if(hasInterpreterParameter) {
			methodArgumentList[0] = interpreter;
			methodArgumentList[1] = SCOPE_ID;
		}else {
			methodArgumentList[0] = SCOPE_ID;
		}
		for(int i = 0;i < argCount;i++) {
			String variableName = parameterList.get(i).getVariableName();
			
			boolean ignoreTypeCheck = parameterAnnotationList.get(i) == ParameterAnnotation.CALL_BY_POINTER;
			
			if(!ignoreTypeCheck && !paramaterDataTypeConstraintList.get(i).isTypeAllowed(combinedArgumentList.get(i).getType()))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format("The type of argument %d (\"%s\") must be one of %s", i + 1,
						variableName, paramaterDataTypeConstraintList.get(i).getAllowedTypes()), SCOPE_ID);
			
			Number argumentNumberValue = parameterAnnotationList.get(i) == ParameterAnnotation.NUMBER?combinedArgumentList.get(i).toNumber():null;
			if(parameterAnnotationList.get(i) == ParameterAnnotation.NUMBER && argumentNumberValue == null)
				return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, String.format("Argument %d (\"%s\") must be a number", i + 1, variableName), SCOPE_ID);
			
			Class<?> methodParameterType = methodParameterTypeList.get(i);
			
			try {
				Object argument;
				if(methodParameterType.isAssignableFrom(DataObject.class)) {
					if(parameterAnnotationList.get(i) == ParameterAnnotation.CALL_BY_POINTER) {
						argument = new DataObject().setVariableName(variableName).
								setVarPointer(new VarPointerObject(combinedArgumentList.get(i))).
								setTypeConstraint(paramaterDataTypeConstraintList.get(i));
					}else {
						argument = new DataObject(combinedArgumentList.get(i)).setVariableName(variableName).
								setTypeConstraint(paramaterDataTypeConstraintList.get(i));
					}
				}else if(methodParameterType.isAssignableFrom(Number.class)) {
					argument = argumentNumberValue;
				}else {
					return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, "Invalid native method parameter argument type", SCOPE_ID);
				}
				
				methodArgumentList[i + diff] = argument;
			}catch(DataTypeConstraintException e) {
				return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR,
						String.format("Native method contains invalid type constraint combinations for Argument %d (\"%s\"): %s", i + 1, variableName, e.getMessage()), SCOPE_ID);
			}
		}
		
		try {
			//TODO check return type in LangInterpreter
			return (DataObject)functionBody.invoke(instance, methodArgumentList);
		}catch(IllegalAccessException|IllegalArgumentException e) {
			return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR,
					"Native Error (\"" + e.getClass().getSimpleName() + "\"): " + e.getMessage(), SCOPE_ID);
		}catch(InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if(t == null)
				t = e;
			
			return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR,
					"Native Error (\"" + t.getClass().getSimpleName() + "\"): " + t.getMessage(), SCOPE_ID);
		}
	}
	
	public String getFunctionName() {
		return functionName;
	}
	
	public String getFunctionInfo() {
		return functionInfo;
	}
	
	public List<DataObject> getParameterList() {
		return parameterList;
	}
	
	public List<DataTypeConstraint> getParamaterDataTypeConstraintList() {
		return paramaterDataTypeConstraintList;
	}
	
	public List<String> getParamaterInfoList() {
		return paramaterInfoList;
	}
	
	public List<ParameterAnnotation> getParameterAnnotationList() {
		return parameterAnnotationList;
	}
	
	public DataTypeConstraint getReturnValueTypeConstraint() {
		return returnValueTypeConstraint;
	}
	
	@Override
	public boolean isLinkerFunction() {
		return linkerFunction;
	}
	
	@Override
	public boolean isDeprecated() {
		return deprecated;
	}
	
	@Override
	public String getDeprecatedRemoveVersion() {
		return deprecatedRemoveVersion;
	}
	
	@Override
	public String getDeprecatedReplacementFunction() {
		return deprecatedReplacementFunction;
	}
	
	public static enum ParameterAnnotation {
		NORMAL, NUMBER, CALL_BY_POINTER;
	}
}