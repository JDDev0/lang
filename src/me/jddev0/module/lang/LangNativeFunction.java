package me.jddev0.module.lang;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraint;
import me.jddev0.module.lang.DataObject.DataTypeConstraintException;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.VarPointerObject;
import me.jddev0.module.lang.LangFunction.*;
import me.jddev0.module.lang.LangFunction.LangParameter.*;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;

public class LangNativeFunction implements LangPredefinedFunctionObject {
	//TODO remove and add as parameter to call Function instance to call
	private final LangInterpreter interpreter;
	
	private final String functionName;
	private final String functionInfo;
	
	private final boolean linkerFunction;
	private final boolean deprecated;
	private final String deprecatedRemoveVersion;
	private final String deprecatedReplacementFunction;
	
	private final List<InternalFunction> internalFunctions;
	
	public static LangNativeFunction getSingleLangFunctionFromObject(LangInterpreter interpreter, Object obj)
			throws IllegalArgumentException, DataTypeConstraintException {
		Map<String, LangNativeFunction> functions = getLangFunctionsFromObject(interpreter, obj);
		if(functions.size() == 0)
			throw new IllegalArgumentException("No methods which are annotated with @LangFunctions are defined in " + obj);
		
		if(functions.size() > 1)
			throw new IllegalArgumentException("Multiple methods which are annotated with @LangFunctions are defined in " + obj);
		
		return functions.values().iterator().next();
	}
	
	public static Map<String, LangNativeFunction> getLangFunctionsFromObject(LangInterpreter interpreter, Object obj)
			throws IllegalArgumentException, DataTypeConstraintException {
		return getLangFunctionsOfClass(interpreter, obj, obj.getClass());
	}
	
	public static Map<String, LangNativeFunction> getLangFunctionsOfClass(LangInterpreter interpreter, Class<?> clazz)
			throws IllegalArgumentException, DataTypeConstraintException {
		return getLangFunctionsOfClass(interpreter, null, clazz);
	}
	
	public static Map<String, LangNativeFunction> getLangFunctionsOfClass(LangInterpreter interpreter, Object instance, Class<?> clazz)
			throws IllegalArgumentException, DataTypeConstraintException {
		Map<String, List<Method>> methodsByLangFunctionName = new HashMap<>();
		
		for(Method method:clazz.getDeclaredMethods()) {
			//Add static methods if instance is null else add non-static methods
			if(instance == null ^ Modifier.isStatic(method.getModifiers()))
				continue;
			
			if(method.isAnnotationPresent(LangFunction.class)) {
				LangFunction langFunction = method.getAnnotation(LangFunction.class);
				String functionName = langFunction.value();
				
				if(!methodsByLangFunctionName.containsKey(functionName))
					methodsByLangFunctionName.put(functionName, new LinkedList<>());
				
				if(langFunction.hasInfo())
					methodsByLangFunctionName.get(functionName).add(0, method);
				else
					methodsByLangFunctionName.get(functionName).add(method);
			}
		}
		
		Map<String, LangNativeFunction> langNativeFunctions = new HashMap<>();
		for(List<Method> methods:methodsByLangFunctionName.values()) {
			if(methods.size() >= 2 && (!methods.get(0).getAnnotation(LangFunction.class).hasInfo() || methods.get(1).getAnnotation(LangFunction.class).hasInfo()))
				throw new IllegalArgumentException("The value of hasInfo() must be true for exactly one overloaded @LangFunction");
			
			LangNativeFunction langNativeFunction = create(interpreter, instance, methods.get(0));
			for(int i = 1;i < methods.size();i++)
				langNativeFunction.addInternalFunction(langNativeFunction.createInternalFunction(instance, methods.get(i)));
			
			String functionName = langNativeFunction.getFunctionName();
			
			langNativeFunctions.put(functionName, langNativeFunction);
		}
		
		return langNativeFunctions;
	}
	
	public static LangNativeFunction create(LangInterpreter interpreter, Object instance, Method functionBody)
			throws IllegalArgumentException, DataTypeConstraintException {
		LangFunction langFunction = functionBody.getAnnotation(LangFunction.class);
		if(langFunction == null)
			throw new IllegalArgumentException("Method must be annotated with @LangFunction");
		
		if(!DataObject.class.isAssignableFrom(functionBody.getReturnType()))
			throw new IllegalArgumentException("Method must be annotated with @LangFunction must return a DataObject");
		
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
		
		LangNativeFunction langNativeFunction = new LangNativeFunction(interpreter, functionName, functionInfo,
				linkerFunction, deprecated, deprecatedRemoveVersion, deprecatedReplacementFunction);
		
		langNativeFunction.addInternalFunction(langNativeFunction.createInternalFunction(instance, functionBody));
		
		return langNativeFunction;
	}
	
	/**
	 * @param instance Null for static method
	 */
	private LangNativeFunction(LangInterpreter interpreter, String functionName, String functionInfo,
			boolean linkerFunction, boolean deprecated, String deprecatedRemoveVersion, String deprecatedReplacementFunction) {
		this.interpreter = interpreter;
		this.functionName = functionName;
		this.functionInfo = functionInfo;
		this.linkerFunction = linkerFunction;
		this.deprecated = deprecated;
		this.deprecatedRemoveVersion = deprecatedRemoveVersion;
		this.deprecatedReplacementFunction = deprecatedReplacementFunction;
		this.internalFunctions = new ArrayList<>();
	}
	
	public void addInternalFunction(InternalFunction internalFunction) {
		if(internalFunctions.size() > 0 &&
				(internalFunction.isCombinatorFunction() || internalFunctions.get(0).isCombinatorFunction()))
			throw new IllegalArgumentException("Combinator functions can not be overloaded");
		
		internalFunctions.add(internalFunction);
	}
	
	@Override
	public DataObject callFunc(List<DataObject> argumentList, int SCOPE_ID) {
		//TODO remove checks from this method and move to directly to interpreter
		
		List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
		if(internalFunctions.size() == 1)
			return internalFunctions.get(0).callFunc(interpreter, argumentList, combinedArgumentList, SCOPE_ID);
		
		int index = LangUtils.getMostRestrictiveFunctionSignatureIndex(internalFunctions.stream().map(InternalFunction::getParamaterDataTypeConstraintList).
				collect(Collectors.toList()), internalFunctions.stream().map(InternalFunction::getVarArgsParameterIndex).collect(Collectors.toList()), combinedArgumentList);
		
		if(index == -1) {
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "No matching function signature was found for the given arguments." +
					" Available function singauters:\n    " + functionName + internalFunctions.stream().map(InternalFunction::toFunctionSignatureSyntax).
					collect(Collectors.joining("\n    " + functionName)), SCOPE_ID);
		}
		
		return internalFunctions.get(index).callFunc(interpreter, argumentList, combinedArgumentList, SCOPE_ID);
	}
	
	public InternalFunction createInternalFunction(Object instance, Method functionBody)
			throws IllegalArgumentException, DataTypeConstraintException {
		LangFunction langFunction = functionBody.getAnnotation(LangFunction.class);
		if(langFunction == null)
			throw new IllegalArgumentException("Method must be annotated with @LangFunction");
		
		if(!DataObject.class.isAssignableFrom(functionBody.getReturnType()))
			throw new IllegalArgumentException("Method must be annotated with @LangFunction must return a DataObject");
		
		boolean combinatorFunction = functionBody.isAnnotationPresent(CombinatorFunction.class);
		
		LangInfo langInfo = functionBody.getAnnotation(LangInfo.class);
		
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
		int varArgsParameterIndex = -1;
		boolean textVarArgsParameter = false;
		boolean rawVarArgsParameter = false;
		
		for(int i = diff;i < parameters.length;i++) {
			Parameter parameter = parameters[i];
			
			LangParameter langParameter = parameter.getAnnotation(LangParameter.class);
			if(langParameter == null)
				throw new IllegalArgumentException("Method parameters after the SCOPE_ID parameter must be annotated with @LangParameter");
			
			ParameterAnnotation parameterAnnotation;
			
			//If parameterCount > 1 -> Multiple type constraining annotations where used
			int typeConstraintingParameterCount = 0;
			int specialTypeConstraintingParameterCount = 0;
			
			boolean isNumberValue = parameter.isAnnotationPresent(NumberValue.class) && typeConstraintingParameterCount++ >= 0 && specialTypeConstraintingParameterCount++ >= 0;
			boolean isBooleanValue = parameter.isAnnotationPresent(BooleanValue.class) && typeConstraintingParameterCount++ >= 0 && specialTypeConstraintingParameterCount++ >= 0;
			
			//Call by pointer can be used with additional @AllowedTypes or @NotAllowedTypes type constraint
			boolean isCallByPointer = parameter.isAnnotationPresent(CallByPointer.class) && specialTypeConstraintingParameterCount++ >= 0;
			
			boolean isContainsVarArgsParameter = parameter.isAnnotationPresent(VarArgs.class) && specialTypeConstraintingParameterCount++ >= 0;
			if(specialTypeConstraintingParameterCount > 1)
				throw new IllegalArgumentException("DataObject parameter must be annotated with at most one of @NumberValue, @BooleanValue, @CallByPointer, or @VarArgs");
			
			boolean isContainsRawVarArgsParameter = parameter.isAnnotationPresent(RawVarArgs.class) && typeConstraintingParameterCount++ >= 0 && specialTypeConstraintingParameterCount++ >= 0;
			if(isContainsRawVarArgsParameter && combinatorFunction)
				throw new IllegalArgumentException("@RawVarArgs can not be used for combinator functions");
			if(typeConstraintingParameterCount > 1 || specialTypeConstraintingParameterCount > 1)
				throw new IllegalArgumentException("@LangParameter which are annotated with @RawVarArgs can not be annotated with other lang parameter annotations apart from @LangInfo");
			
			String variableName = langParameter.value();
			
			//TODO error if parameter name already exists or if name is invalid [Check for all cases: {NUMBER, BOOLEAN, CALL_BY_POINTER -> $, VAR_ARGS -> $, &, NORMAL -> $, &, fp.}]
			
			if(isBooleanValue) {
				parameterAnnotation = ParameterAnnotation.BOOLEAN;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class) && !parameter.getType().isAssignableFrom(boolean.class))
					throw new IllegalArgumentException("@LangParameter which are annotated with @BooleanValue must be of type DataObject or boolean");
			}else if(isNumberValue) {
				parameterAnnotation = ParameterAnnotation.NUMBER;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class) && !parameter.getType().isAssignableFrom(Number.class))
					throw new IllegalArgumentException("@LangParameter which are annotated with @NumberValue must be of type DataObject or Number");
			}else if(isCallByPointer) {
				parameterAnnotation = ParameterAnnotation.CALL_BY_POINTER;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class))
					throw new IllegalArgumentException("@LangParameter which are annotated with @CallByPointer must be of type DataObject");
			}else if(isContainsRawVarArgsParameter) {
				parameterAnnotation = ParameterAnnotation.RAW_VAR_ARGS;
				
				varArgsParameterIndex = parameterList.size();
				
				rawVarArgsParameter = true;
				
				boolean isListParameter = parameter.getType().isAssignableFrom(List.class);
				if(isListParameter) {
					Type parameterType = parameter.getParameterizedType();
					if(parameterType instanceof ParameterizedType) {
						ParameterizedType parameterizedType = (ParameterizedType)parameterType;
						Type[] types = parameterizedType.getActualTypeArguments();
						
						isListParameter = types.length == 1 && types[0] instanceof Class<?> && ((Class<?>)types[0]).isAssignableFrom(DataObject.class);
					}else {
						isListParameter = false;
					}
				}
				
				if(!isListParameter)
					throw new IllegalArgumentException("@LangParameter which are annotated with @RawVarArgs must be of type List<DataObject>");
			}else if(isContainsVarArgsParameter) {
				parameterAnnotation = ParameterAnnotation.VAR_ARGS;
				
				if(varArgsParameterIndex != -1)
					throw new IllegalArgumentException("There can only be one @LangParameter which is annotated with @VarArgs");
				
				varArgsParameterIndex = parameterList.size();
				
				textVarArgsParameter = variableName.startsWith("$");
				if(textVarArgsParameter && combinatorFunction)
					throw new IllegalArgumentException("Combinator functions can not have @LangParameters with @VarArgs as text var args parameter");
				
				if(textVarArgsParameter && !parameter.getType().isAssignableFrom(DataObject.class))
					throw new IllegalArgumentException("@LangParameter which are annotated with @VarArgs as text var args parameter must be of type DataObject");
				
				boolean isListParameter = parameter.getType().isAssignableFrom(List.class);
				if(isListParameter) {
					Type parameterType = parameter.getParameterizedType();
					if(parameterType instanceof ParameterizedType) {
						ParameterizedType parameterizedType = (ParameterizedType)parameterType;
						Type[] types = parameterizedType.getActualTypeArguments();
						
						isListParameter = types.length == 1 && types[0] instanceof Class<?> && ((Class<?>)types[0]).isAssignableFrom(DataObject.class);
					}else {
						isListParameter = false;
					}
				}
				
				if(!parameter.getType().isAssignableFrom(DataObject.class) && !parameter.getType().isAssignableFrom(DataObject[].class) && !isListParameter)
					throw new IllegalArgumentException("@LangParameter which are annotated with @VarArgs as array var args parameter"
							+ " must be of type DataObject, DataObject[], or List<DataObject>");
			}else {
				parameterAnnotation = ParameterAnnotation.NORMAL;
				
				if(!parameter.getType().isAssignableFrom(DataObject.class))
					throw new IllegalArgumentException("@LangParameter must be of type DataObject (Other types besides DataObject are allowed for certain annotations)");
			}
			
			DataTypeConstraint typeConstraint = null;
			
			allowedTypes = parameter.getAnnotation(AllowedTypes.class);
			if(allowedTypes != null && !isContainsVarArgsParameter && !parameter.getType().isAssignableFrom(DataObject.class))
				throw new IllegalArgumentException("@AllowedTypes can only be used if @LangParameter is of type DataObject");
			if(allowedTypes != null && isContainsVarArgsParameter && textVarArgsParameter)
				throw new IllegalArgumentException("@AllowedTypes can not be used with @VarArgs as text var args");
			if(allowedTypes != null && typeConstraintingParameterCount++ >= 0)
				typeConstraint = DataTypeConstraint.fromAllowedTypes(Arrays.asList(allowedTypes.value()));
			
			notAllowedTypes = parameter.getAnnotation(NotAllowedTypes.class);
			if(notAllowedTypes != null && !isContainsVarArgsParameter && !parameter.getType().isAssignableFrom(DataObject.class))
				throw new IllegalArgumentException("@NotAllowedTypes can only be used if @LangParameter is of type DataObject");
			if(notAllowedTypes != null && isContainsVarArgsParameter && textVarArgsParameter)
				throw new IllegalArgumentException("@NotAllowedTypes can not be used with @VarArgs as text var args");
			if(notAllowedTypes != null && typeConstraintingParameterCount++ >= 0)
				typeConstraint = DataTypeConstraint.fromNotAllowedTypes(Arrays.asList(notAllowedTypes.value()));
			
			if(typeConstraintingParameterCount > 1)
				throw new IllegalArgumentException("DataObject parameter must be annotated with at most one of @RawVarArgs, @AllowedTypes, @NotAllowedTypes, @NumberValue, or @BooleanValue");
			
			langInfo = parameter.getAnnotation(LangInfo.class);
			String paramaterInfo = langInfo == null?null:langInfo.value();
			
			//Allow all types for var args parameters
			if(typeConstraint == null)
				typeConstraint = (isContainsVarArgsParameter || isContainsRawVarArgsParameter)?DataObject.CONSTRAINT_NORMAL:DataObject.getTypeConstraintFor(variableName);
			
			methodParameterTypeList.add(parameter.getType());
			parameterList.add(new DataObject().setVariableName(variableName));
			paramaterDataTypeConstraintList.add(typeConstraint);
			paramaterInfoList.add(paramaterInfo);
			parameterAnnotationList.add(parameterAnnotation);
		}
		
		if(rawVarArgsParameter && parameterList.size() != 1)
			throw new IllegalArgumentException("If @RawVarArgs is used there must be exactly one lang parameter");
		
		return new InternalFunction(methodParameterTypeList, parameterList, paramaterDataTypeConstraintList,
				parameterAnnotationList, paramaterInfoList, varArgsParameterIndex, textVarArgsParameter, rawVarArgsParameter,
				returnValueTypeConstraint, instance, functionBody, hasInterpreterParameter, combinatorFunction, 0, new ArrayList<>());
	}
	
	public class InternalFunction {
		private final List<Class<?>> methodParameterTypeList;
		private final List<DataObject> parameterList;
		private final List<DataTypeConstraint> paramaterDataTypeConstraintList;
		private final List<ParameterAnnotation> parameterAnnotationList;
		private final List<String> paramaterInfoList;
		private final int varArgsParameterIndex;
		private final boolean textVarArgsParameter;
		private final boolean rawVarArgsParameter;
		private final DataTypeConstraint returnValueTypeConstraint;
		private final Object instance;
		private final Method functionBody;
		private final boolean hasInterpreterParameter;
		
		private final boolean combinatorFunction;
		private final int combinatorFunctionCallCount;
		private final List<DataObject> combinatorProvidedArgumentList;
		
		private InternalFunction(List<Class<?>> methodParameterTypeList, List<DataObject> parameterList,
				List<DataTypeConstraint> paramaterDataTypeConstraintList,
				List<ParameterAnnotation> parameterAnnotationList, List<String> paramaterInfoList,
				int varArgsParameterIndex, boolean textVarArgsParameter, boolean rawVarArgsParameter,
				DataTypeConstraint returnValueTypeConstraint, Object instance, Method functionBody,
				boolean hasInterpreterParameter, boolean combinatorFunction, int combinatorFunctionCallCount,
				List<DataObject> combinatorProvidedArgumentList) {
			this.methodParameterTypeList = methodParameterTypeList;
			this.parameterList = parameterList;
			this.paramaterDataTypeConstraintList = paramaterDataTypeConstraintList;
			this.parameterAnnotationList = parameterAnnotationList;
			this.paramaterInfoList = paramaterInfoList;
			this.varArgsParameterIndex = varArgsParameterIndex;
			this.textVarArgsParameter = textVarArgsParameter;
			this.rawVarArgsParameter = rawVarArgsParameter;
			this.returnValueTypeConstraint = returnValueTypeConstraint;
			this.instance = instance;
			this.functionBody = functionBody;
			this.hasInterpreterParameter = hasInterpreterParameter;
			this.combinatorFunction = combinatorFunction;
			this.combinatorFunctionCallCount = combinatorFunctionCallCount;
			this.combinatorProvidedArgumentList = combinatorProvidedArgumentList;
		}
		
		public DataObject callFunc(LangInterpreter interpreter, List<DataObject> argumentList, List<DataObject> combinedArgumentList, int SCOPE_ID) {
			int argCount = parameterList.size();
			
			if(combinatorFunction) {
				combinedArgumentList = new ArrayList<>(combinedArgumentList);
				combinedArgumentList.addAll(0, combinatorProvidedArgumentList);
			}
			
			if(varArgsParameterIndex == -1) {
				if(!combinatorFunction && combinedArgumentList.size() < argCount)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Not enough arguments (%s needed)", argCount), SCOPE_ID);
				if(combinedArgumentList.size() > argCount)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Too many arguments (%s needed)", argCount), SCOPE_ID);
			}else {
				//Infinite combinator functions (= Combinator functions with var args argument) must be called exactly two times
				if((!combinatorFunction || combinatorFunctionCallCount > 0) && combinedArgumentList.size() < argCount - 1)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, String.format("Not enough arguments (at least %s needed)", argCount - 1), SCOPE_ID);
			}
			
			int diff = hasInterpreterParameter?2:1;
			Object[] methodArguments = new Object[diff + argCount];
			if(hasInterpreterParameter) {
				methodArguments[0] = interpreter;
				methodArguments[1] = SCOPE_ID;
			}else {
				methodArguments[0] = SCOPE_ID;
			}
			
			int argumentIndex = 0;
			for(int i = 0;i < argCount;i++) {
				if(combinatorFunction && argumentIndex >= combinedArgumentList.size() && (varArgsParameterIndex == -1 || combinatorFunctionCallCount == 0))
					return combinatorCall(interpreter, combinedArgumentList, SCOPE_ID);
				
				String variableName = parameterList.get(i).getVariableName();
				
				boolean ignoreTypeCheck = parameterAnnotationList.get(i) == ParameterAnnotation.CALL_BY_POINTER || parameterAnnotationList.get(i) == ParameterAnnotation.VAR_ARGS ||
						parameterAnnotationList.get(i) == ParameterAnnotation.RAW_VAR_ARGS;
				
				if(!ignoreTypeCheck && !paramaterDataTypeConstraintList.get(i).isTypeAllowed(combinedArgumentList.get(argumentIndex).getType()))
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, String.format("The type of argument %d (\"%s\") must be one of %s", argumentIndex + 1,
							variableName, paramaterDataTypeConstraintList.get(i).getAllowedTypes()), SCOPE_ID);
				
				Number argumentNumberValue = parameterAnnotationList.get(i) == ParameterAnnotation.NUMBER?combinedArgumentList.get(argumentIndex).toNumber():null;
				if(parameterAnnotationList.get(i) == ParameterAnnotation.NUMBER && argumentNumberValue == null)
					return interpreter.setErrnoErrorObject(InterpretingError.NO_NUM, String.format("Argument %d (\"%s\") must be a number", argumentIndex + 1, variableName), SCOPE_ID);
				
				Class<?> methodParameterType = methodParameterTypeList.get(i);
				
				try {
					Object argument;
					if(parameterAnnotationList.get(i) == ParameterAnnotation.RAW_VAR_ARGS) {
						argument = new LinkedList<>(argumentList);
					}else if(parameterAnnotationList.get(i) == ParameterAnnotation.VAR_ARGS) {
						//Infinite combinator functions (= Combinator functions with var args argument) must be called exactly two times
						if(combinatorFunction && combinatorFunctionCallCount == 0)
							return combinatorCall(interpreter, combinedArgumentList, SCOPE_ID);
						
						List<DataObject> varArgsArgumentList = combinedArgumentList.subList(i, combinedArgumentList.size() - argCount + i + 1).stream().
								map(DataObject::new).collect(Collectors.toList());
						if(!textVarArgsParameter) {
							DataTypeConstraint typeConstraint = paramaterDataTypeConstraintList.get(i);
							
							for(int j = 0;j < varArgsArgumentList.size();j++) {
								DataObject varArgsArgument = varArgsArgumentList.get(j);
								if(!typeConstraint.isTypeAllowed(varArgsArgument.getType()))
									return interpreter.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
											String.format("The type of argument %d (for var args parameter \"%s\") must be one of %s", i + j + 1,
													variableName, typeConstraint.getAllowedTypes()), SCOPE_ID);
							}
						}
						
						if(methodParameterType.isAssignableFrom(DataObject.class)) {
							if(textVarArgsParameter) {
								List<DataObject> argumentListCopy = new ArrayList<>(argumentList);
								
								//Remove leading arguments
								for(int j = 0;j < i;j++)
									for(int k = 0;k < argumentListCopy.size();k++)
										if(argumentListCopy.remove(0).getType() == DataType.ARGUMENT_SEPARATOR)
											break;
								
								//Remove trailing arguments
								for(int j = 0;j < argCount - i - 1;j++)
									for(int k = argumentListCopy.size() - 1;k >= 0;k--)
										if(argumentListCopy.remove(k).getType() == DataType.ARGUMENT_SEPARATOR)
											break;
								
								DataObject combinedArgument = LangUtils.combineDataObjects(argumentListCopy);
								argument = new DataObject(combinedArgument == null?"":combinedArgument.getText()).setVariableName(variableName);
							}else {
								argument = new DataObject().setVariableName(variableName).
										setArray(varArgsArgumentList.toArray(new DataObject[0]));
							}
						}else if(methodParameterType.isAssignableFrom(DataObject[].class)) {
							argument = varArgsArgumentList.toArray(new DataObject[0]);
						}else {
							argument = new ArrayList<>(varArgsArgumentList);
						}
						
						//Not "+1", because argumentIndex will be incremented at the end of the for loop
						argumentIndex = combinedArgumentList.size() - argCount + i;
					}else if(methodParameterType.isAssignableFrom(DataObject.class)) {
						if(parameterAnnotationList.get(i) == ParameterAnnotation.CALL_BY_POINTER) {
							argument = new DataObject().setVariableName(variableName).
									setVarPointer(new VarPointerObject(combinedArgumentList.get(argumentIndex))).
									setTypeConstraint(paramaterDataTypeConstraintList.get(i));
						}else {
							argument = new DataObject(combinedArgumentList.get(argumentIndex)).setVariableName(variableName).
									setTypeConstraint(paramaterDataTypeConstraintList.get(i));
						}
					}else if(methodParameterType.isAssignableFrom(Number.class)) {
						argument = argumentNumberValue;
					}else if(methodParameterType.isAssignableFrom(boolean.class)) {
						argument = combinedArgumentList.get(argumentIndex).toBoolean();
					}else {
						return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR, "Invalid native method parameter argument type", SCOPE_ID);
					}
					
					methodArguments[i + diff] = argument;
				}catch(DataTypeConstraintException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.SYSTEM_ERROR,
							String.format("Native method contains invalid type constraint combinations for Argument %d (\"%s\"): %s", i + 1, variableName, e.getMessage()), SCOPE_ID);
				}
				
				argumentIndex++;
			}
			
			try {
				//TODO check return type in LangInterpreter
				return (DataObject)functionBody.invoke(instance, methodArguments);
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
		
		private DataObject combinatorCall(LangInterpreter interpreter, List<DataObject> combinedArgumentList, int SCOPE_ID) {
			LangNativeFunction langNativeFunction = new LangNativeFunction(interpreter, LangNativeFunction.this.functionName, functionInfo,
					linkerFunction, deprecated, deprecatedRemoveVersion, deprecatedReplacementFunction);
			
			InternalFunction internalFunction = new InternalFunction(methodParameterTypeList, parameterList, paramaterDataTypeConstraintList,
							parameterAnnotationList, paramaterInfoList, varArgsParameterIndex, textVarArgsParameter, rawVarArgsParameter,
							returnValueTypeConstraint, instance, functionBody, hasInterpreterParameter, combinatorFunction,
							combinatorFunctionCallCount + 1, combinedArgumentList);
			
			langNativeFunction.addInternalFunction(internalFunction);
			
			String functionNames = combinedArgumentList.stream().map(dataObject -> {
				if(dataObject.getType() != DataType.FUNCTION_POINTER)
					return "<arg>";
				
				String functionName = dataObject.getFunctionPointer().getFunctionName();
				return functionName == null?dataObject.getVariableName():functionName;
			}).collect(Collectors.joining(", "));
			
			String functionName = "<" + (varArgsParameterIndex == -1?"":"inf-") + LangNativeFunction.this.functionName + "-func(" + functionNames + ")>";
			
			
			return new DataObject().setFunctionPointer(new FunctionPointerObject(functionName, langNativeFunction));
		}
		
		public String toFunctionSignatureSyntax() {
			StringBuilder builder = new StringBuilder();
			builder.append("(");
			
			for(int i = 0;i < parameterList.size();i++) {
				//TODO add $[x] for CALL_BY_POINTER
				
				String variableName = parameterList.get(i).getVariableName();
				
				builder.append(variableName);
				if(parameterAnnotationList.get(i) == ParameterAnnotation.VAR_ARGS)
					builder.append("...");
				else if(parameterAnnotationList.get(i) == ParameterAnnotation.RAW_VAR_ARGS)
					builder.append("...{raw}");
				else if(parameterAnnotationList.get(i) == ParameterAnnotation.NUMBER)
					builder.append("{number}");
				else if(parameterAnnotationList.get(i) == ParameterAnnotation.BOOLEAN)
					builder.append("{boolean}");
				else if(!paramaterDataTypeConstraintList.get(i).equals(DataObject.getTypeConstraintFor(variableName)))
					builder.append(paramaterDataTypeConstraintList.get(i).toTypeConstraintSyntax());
				
				builder.append(", ");
			}
			
			if(parameterList.size() > 0)
				builder.delete(builder.length() - 2, builder.length());
			
			builder.append(")");
			return builder.toString();
		}
		
		public List<DataObject> getParameterList() {
			return new ArrayList<>(parameterList);
		}
		
		public List<DataTypeConstraint> getParamaterDataTypeConstraintList() {
			return new ArrayList<>(paramaterDataTypeConstraintList);
		}
		
		public List<String> getParamaterInfoList() {
			return new ArrayList<>(paramaterInfoList);
		}
		
		public List<ParameterAnnotation> getParameterAnnotationList() {
			return new ArrayList<>(parameterAnnotationList);
		}
		
		public int getVarArgsParameterIndex() {
			return varArgsParameterIndex;
		}
		
		public boolean isTextVarArgsParameter() {
			return textVarArgsParameter;
		}
		
		public boolean isRawVarArgsParameter() {
			return rawVarArgsParameter;
		}
		
		public DataTypeConstraint getReturnValueTypeConstraint() {
			return returnValueTypeConstraint;
		}
		
		public boolean isCombinatorFunction() {
			return combinatorFunction;
		}
		
		public int getCombinatorFunctionCallCount() {
			return combinatorFunctionCallCount;
		}
		
		public List<DataObject> getCombinatorProvidedArgumentList() {
			return new ArrayList<>(combinatorProvidedArgumentList);
		}
	}
	
	public String getFunctionName() {
		return functionName;
	}
	
	public String getFunctionInfo() {
		return functionInfo;
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
	
	public List<InternalFunction> getInteralFunctions() {
		return new ArrayList<>(internalFunctions);
	}
	
	public static enum ParameterAnnotation {
		NORMAL, NUMBER, BOOLEAN, CALL_BY_POINTER, VAR_ARGS, RAW_VAR_ARGS;
	}
}