package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import me.jddev0.module.lang.LangInterpreter.InterpretingError;
import me.jddev0.module.lang.LangInterpreter.StackElement;
import me.jddev0.module.lang.LangModuleConfiguration.ModuleType;

/**
 * Lang-Module<br>
 * Loads and unloads Lang modules
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangModuleManager {
	private final static int maxModuleFileSize = 1024 * 1024 * 1024; //1 GiB
	
	private LangInterpreter interpreter;
	
	public LangModuleManager(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	private static String removeDotsModuleFilePath(String file) {
		//Remove "/./"
		while(file.contains("/./"))
			file = file.replaceAll("\\/\\.\\/", "/");
		
		//Remove "/../" and go to parent
		while(file.matches(".*\\/([^/]|[^/.][^/]|[^/][^/.]|[^/]{3,})\\/\\.\\.\\/.*"))
			file = file.replaceAll("\\/([^/]|[^/.][^/]|[^/][^/.]|[^/]{3,})\\/\\.\\.\\/", "/");
		
		return file;
	}
	
	public static String getModuleFilePath(LangModule module, String currentPath, String file) {
		if(file.startsWith("/"))
			return removeDotsModuleFilePath(file);
		
		String prefix = "<module:" + module.getFile() + "[" + module.getLangModuleConfiguration().getName() + "]>";
		currentPath = currentPath.substring(prefix.length());
		
		String path = currentPath + "/" + file;
		
		if(!path.startsWith("/"))
			return "/" + path;
		
		return removeDotsModuleFilePath(path);
	}
	
	/**
	 * @param file Must be absolute (Starts with "/")
	 */
	public static BufferedReader readModuleLangFile(LangModule module, String file) throws IOException {
		byte[] moduleLangBytes = module.getZipData().get("lang" + file);
		if(moduleLangBytes == null)
			throw new IOException("File \"" + file + "\" was not found inside the module \"" + module.getLangModuleConfiguration().getName() + "\"");
		
		return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(moduleLangBytes), "UTF-8"));
	}
	
	public DataObject load(String moduleFile, List<DataObject> args, final int CALLER_SCOPE_ID) {
		return loadUnload(true, moduleFile, args, CALLER_SCOPE_ID);
	}
	
	public DataObject unload(String moduleName, List<DataObject> args, final int CALLER_SCOPE_ID) {
		return loadUnload(false, moduleName, args, CALLER_SCOPE_ID);
	}
	
	DataObject loadNative(String entryPoint, LangModule module, List<DataObject> args, final int SCOPE_ID) {
		return loadUnloadNative(true, false, entryPoint, module, args, SCOPE_ID);
	}
	
	DataObject unloadNative(String entryPoint, LangModule module, List<DataObject> args, final int SCOPE_ID) {
		return loadUnloadNative(false, false, entryPoint, module, args, SCOPE_ID);
	}
	
	private DataObject loadUnload(boolean load, String moduleFileOrName, List<DataObject> args, final int CALLER_SCOPE_ID) {
		Map<String, ZipEntry> zipEntries;
		Map<String, byte[]> zipData;
		
		LangModule module;
		LangModuleConfiguration lmc;
		if(load) {
			LangModuleConfiguration[] lmcArray = new LangModuleConfiguration[1];
			zipEntries = new HashMap<>();
			zipData = new HashMap<>();
			
			DataObject errorObject = readModuleData(moduleFileOrName, zipEntries, zipData, lmcArray, CALLER_SCOPE_ID);
			if(errorObject != null)
				return errorObject;
			
			lmc = lmcArray[0];
			module = new LangModule(moduleFileOrName, load, zipEntries, zipData, lmc);
			
			if(interpreter.modules.get(lmc.getName()) != null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The Lang module \"" + lmc.getName() + "\" was already loaded", CALLER_SCOPE_ID);
			
			interpreter.modules.put(lmc.getName(), module);
		}else {
			if(interpreter.modules.get(moduleFileOrName) == null)
				return interpreter.setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The Lang module \"" + moduleFileOrName + "\" was not loaded", CALLER_SCOPE_ID);
			
			module = interpreter.modules.remove(moduleFileOrName);
			zipEntries = module.getZipEntries();
			zipData = module.getZipData();
			lmc = module.getLangModuleConfiguration();
		}
		
		String loadUnloadStr = load?"load":"unload";
		
		final int SCOPE_ID = CALLER_SCOPE_ID + 1;
		try {
			//Update call stack (Path inside module archive)
			interpreter.pushStackElement(new StackElement("<module:" + module.getFile()  + "[" + lmc.getName() + "]>", "<entryPoint>", null, module), -1);
			
			String[] langArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(args).stream().map(DataObject::getText).collect(Collectors.toList()).toArray(new String[0]);
			
			//Create an empty data map
			interpreter.createDataMap(SCOPE_ID, langArgs);
			
			LangModuleConfiguration.ModuleType moduleType = lmc.getModuleType();
			if(moduleType == ModuleType.LANG) {
				ZipEntry moduleLang = zipEntries.get("lang/module.lang");
				if(moduleLang == null) {
					moduleLang = zipEntries.get("lang/module.lang/");
					if(moduleLang != null && moduleLang.isDirectory())
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/lang/module.lang\" must be a file", SCOPE_ID);
					
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/lang/module.lang\" was not found", SCOPE_ID);
				}
				
				byte[] moduleLangBytes = zipData.get("lang/module.lang");
				int originalLineNumber = interpreter.getParserLineNumber();
				try(BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(moduleLangBytes), "UTF-8"))) {
					interpreter.resetParserPositionVars();
					interpreter.interpretLines(br, SCOPE_ID);
				}catch(IOException e) {
					return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
				}finally {
					interpreter.setParserLineNumber(originalLineNumber);
				}
				
				DataObject ret = null;
				DataObject loadUnloadFP = interpreter.data.get(SCOPE_ID).var.get("fp." + loadUnloadStr);
				if(loadUnloadFP != null) {
					if(loadUnloadFP.getType() != DataObject.DataType.FUNCTION_POINTER)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"fp." + loadUnloadStr + "\" is invalid", SCOPE_ID);
					
					ret = interpreter.callFunctionPointer(loadUnloadFP.getFunctionPointer(), loadUnloadFP.getVariableName(), args, SCOPE_ID);
				}
				
				return ret;
			}else if(moduleType == ModuleType.NATIVE) {
				String nativeEntryPoint = lmc.getNativeEntryPoint();
				if(nativeEntryPoint == null) {
					ZipEntry moduleLang = zipEntries.get("lang/module.lang");
					if(moduleLang == null)
						return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/data.lmc\" is invalid: \"nativeEntryPoint\" is required if module type is \"native\""
								+ " and \"lang/module.lang\" does not exist", SCOPE_ID);
					
					byte[] moduleLangBytes = zipData.get("lang/module.lang");
					int originalLineNumber = interpreter.getParserLineNumber();
					try(BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(moduleLangBytes), "UTF-8"))) {
						interpreter.resetParserPositionVars();
						interpreter.interpretLines(br, SCOPE_ID);
					}catch(IOException e) {
						return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
					}finally {
						interpreter.setParserLineNumber(originalLineNumber);
					}
					
					DataObject ret = null;
					DataObject loadUnloadFP = interpreter.data.get(SCOPE_ID).var.get("fp." + loadUnloadStr);
					if(loadUnloadFP != null) {
						if(loadUnloadFP.getType() != DataObject.DataType.FUNCTION_POINTER)
							return interpreter.setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"fp." + loadUnloadStr + "\" is invalid", SCOPE_ID);
						
						ret = interpreter.callFunctionPointer(loadUnloadFP.getFunctionPointer(), loadUnloadFP.getVariableName(), args, SCOPE_ID);
					}
					
					return ret;
				}
				
				return loadUnloadNative(load, true, nativeEntryPoint, module, args, SCOPE_ID);
			}else {
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "Invalid module type: \"" + moduleType.name() + "\"", SCOPE_ID);
			}
		}finally {
			interpreter.popStackElement();
			
			//Remove data map
			interpreter.data.remove(SCOPE_ID);
			
			if(!load && module != null) {
				//Remove exported functions and variables
				module.getExportedFunctions().forEach(interpreter.funcs::remove);
			}
		}
	}
	
	private DataObject loadUnloadNative(boolean load, boolean moduleEntryPoint, String entryPoint, LangModule module, List<DataObject> args, final int SCOPE_ID) {
		LangNativeModule[] lnmArray = new LangNativeModule[1];
		DataObject errorObject = readNativeModule(entryPoint, module, lnmArray, SCOPE_ID);
		if(errorObject != null)
			return errorObject;
		
		LangNativeModule nativeModule = lnmArray[0];
		
		final int INNER_SCOPE_ID = SCOPE_ID + 1;
		
		interpreter.pushStackElement(new StackElement("<module:" + module.getFile() + "[" + module.getLangModuleConfiguration().getName() + "]>",
				(moduleEntryPoint?"<entryPoint>":entryPoint), "<native:" + (load?"load":"unload") + ">", module), -1);
		
		String[] langArgs = LangUtils.combineArgumentsWithoutArgumentSeparators(args).stream().map(DataObject::getText).collect(Collectors.toList()).toArray(new String[0]);
		
		//Create an empty data map
		interpreter.createDataMap(INNER_SCOPE_ID, langArgs);
		
		try {
			if(load)
				return nativeModule.load(args, INNER_SCOPE_ID);
			else
				return nativeModule.unload(args, INNER_SCOPE_ID);
		}finally {
			interpreter.popStackElement();
			
			//Remove data map
			interpreter.data.remove(INNER_SCOPE_ID);
		}
	}
	
	private DataObject readModuleData(String moduleFile, Map<String, ZipEntry> zipEntries, Map<String, byte[]> zipData, LangModuleConfiguration[] lmcArray, final int SCOPE_ID) {
		InputStream in;
		try {
			in = interpreter.langPlatformAPI.getInputStream(moduleFile);
		}catch(IOException e) {
			return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
		}
		
		try(ZipInputStream zipIn = new ZipInputStream(in)) {
			ZipEntry zipEntry;
			while((zipEntry = zipIn.getNextEntry()) != null) {
				zipEntries.put(zipEntry.getName(), zipEntry);
				
				if(zipEntry.getSize() > maxModuleFileSize)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/" + zipEntry.getName() + "\" is larger than " + maxModuleFileSize, SCOPE_ID);
				
				ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
				
				byte[] buf = new byte[1024];
				int byteCount;
				while((byteCount = zipIn.read(buf)) > -1)
					bufOut.write(buf, 0, byteCount);
				
				byte[] buffer = bufOut.toByteArray();
				zipData.put(zipEntry.getName(), buffer);
			}
			
			ZipEntry dataLmc = zipEntries.get("data.lmc");
			if(dataLmc == null) {
				dataLmc = zipEntries.get("data.lmc/");
				if(dataLmc != null && dataLmc.isDirectory())
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/data.lmc\" must be a file", SCOPE_ID);
				
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/data.lmc\" was not found", SCOPE_ID);
			}
			
			byte[] dataLmcBytes = zipData.get("data.lmc");
			LangModuleConfiguration lmc = LangModuleConfiguration.parseLmc(new String(dataLmcBytes, "UTF-8"));
			
			String[] supportedImplementations = lmc.getSupportedImplementations();
			if(supportedImplementations != null) {
				boolean standardLangSupported = false;
				for(String implementation:supportedImplementations) {
					if(implementation.equals("Standard Lang")) {
						standardLangSupported = true;
						break;
					}
				}
				
				if(!standardLangSupported)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "The module is not supported in Standard Lang!", SCOPE_ID);
			}
			
			String minSupportedVersion = lmc.getMinSupportedVersion();
			Integer minCompVer = LangUtils.compareVersions(minSupportedVersion, LangInterpreter.VERSION);
			if(minCompVer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "The min supported version has an invalid format!", SCOPE_ID);
			if(minSupportedVersion != null && minCompVer > 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "The minimal supported version of the module is higher than " + LangInterpreter.VERSION +
						": " + minSupportedVersion + "!", SCOPE_ID);
			
			String maxSupportedVersion = lmc.getMaxSupportedVersion();
			Integer maxCompVer = LangUtils.compareVersions(maxSupportedVersion, LangInterpreter.VERSION);
			if(maxCompVer == null)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "The max supported version has an invalid format!", SCOPE_ID);
			if(maxSupportedVersion != null && maxCompVer < 0)
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "The maximal supported version of the module is lower than " + LangInterpreter.VERSION +
						": " + maxSupportedVersion + "!", SCOPE_ID);
			
			lmcArray[0] = lmc;
		}catch(IOException e) {
			return interpreter.setErrnoErrorObject(InterpretingError.FILE_NOT_FOUND, e.getMessage(), SCOPE_ID);
		}catch(LangModuleConfiguration.InvalidModuleConfigurationException e) {
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/data.lmc\" is invalid: " + e.getMessage(), SCOPE_ID);
		}
		
		return null;
	}
	
	private DataObject readNativeModule(final String nativeEntryPoint, LangModule module, LangNativeModule[] lnmArray, final int SCOPE_ID) {
		//Fix the unload method was not executed on the same instances as the load method was called on (Unloading class's attributes is now possible)
		Map<String, LangNativeModule> loadedNativeModules = module.getLoadedNativeModules();
		if(loadedNativeModules.containsKey(nativeEntryPoint)) {
			lnmArray[0] = loadedNativeModules.get(nativeEntryPoint);
			
			return null;
		}
		
		int colonIndex = nativeEntryPoint.lastIndexOf(':');
		if(colonIndex == -1)
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/data.lmc\" is invalid: \"nativeEntryPoint\" must be of format"
					+ " \"path/file.jar:tld.pacakge.Class\"", SCOPE_ID);
		
		String file = "native/" + nativeEntryPoint.substring(0, colonIndex);
		String classPath = nativeEntryPoint.substring(colonIndex + 1);
		
		Map<String, ZipEntry> zipEntries = module.getZipEntries();
		Map<String, byte[]> zipData = module.getZipData();
		
		ZipEntry entryPoint = zipEntries.get(file);
		if(entryPoint == null) {
			entryPoint = zipEntries.get(file + "/");
			if(entryPoint != null && entryPoint.isDirectory())
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"The entry point /" + file + "\" must be a file", SCOPE_ID);
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "The entry point \"/" + file + "\" was not found", SCOPE_ID);
		}
		
		Map<String, JarEntry> jarEntries = new HashMap<>();
		Map<String, byte[]> jarData = new HashMap<>();
		
		byte[] entryPointBytes = zipData.get(file);
		try(JarInputStream entryPointJarIn = new JarInputStream(new ByteArrayInputStream(entryPointBytes))) {
			JarEntry jarEntry;
			while((jarEntry = entryPointJarIn.getNextJarEntry()) != null) {
				jarEntries.put(jarEntry.getName(), jarEntry);
				
				if(jarEntry.getSize() > maxModuleFileSize)
					return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "\"/" + jarEntry.getName() + "\" is larger than " + maxModuleFileSize, SCOPE_ID);
				
				ByteArrayOutputStream bufOut = new ByteArrayOutputStream();
				
				byte[] buf = new byte[1024];
				int byteCount;
				while((byteCount = entryPointJarIn.read(buf)) > -1)
					bufOut.write(buf, 0, byteCount);
				
				byte[] buffer = bufOut.toByteArray();
				jarData.put(jarEntry.getName(), buffer);
			}
		}catch(IOException e) {
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "Invalid entry point: " + e.getMessage(), SCOPE_ID);
		}
		
		ClassLoader jarClassLoader = new ClassLoader() {
			private final Map<String, JarEntry> classEntries = new HashMap<>(jarEntries);
			private final Map<String, byte[]> classData = new HashMap<>(jarData);
			
			@Override
			protected Class<?> findClass(String name) throws ClassNotFoundException {
				String classEntryName = name.replace('.', '/') + ".class";
				
				JarEntry classEntry = classEntries.get(classEntryName);
				if(classEntry != null) {
					byte[] classBytes = classData.get(classEntryName);
					
					//Remove class entry in order to not be loaded twice
					classEntries.remove(classEntryName);
					classData.remove(classEntryName);
					
					return defineClass(name, classBytes, 0, classBytes.length);
				}
				
				return super.findClass(name);
			}
		};
		
		try {
			Class<?> entryPointClass = jarClassLoader.loadClass(classPath);
			
			if(!LangNativeModule.class.isAssignableFrom(entryPointClass))
				return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "Entry point class must extend LangNativeModule", SCOPE_ID);
			
			Constructor<?> entryPointConstructor = entryPointClass.getConstructor();
			
			LangNativeModule langNativeModule = lnmArray[0] = (LangNativeModule)entryPointConstructor.newInstance();
			loadedNativeModules.put(nativeEntryPoint, langNativeModule);
			
			Field interpreterField = LangNativeModule.class.getDeclaredField("interpreter");
			interpreterField.setAccessible(true);
			interpreterField.set(langNativeModule, interpreter);
			
			Field moduleField = LangNativeModule.class.getDeclaredField("module");
			moduleField.setAccessible(true);
			moduleField.set(langNativeModule, module);
		}catch(ClassNotFoundException|NoSuchMethodException|SecurityException|InstantiationException|
				IllegalAccessException|IllegalArgumentException|NoSuchFieldException|UnsupportedClassVersionError e) {
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "Invalid entry point (\"" + e.getClass().getSimpleName() + "\"): " + e.getMessage(), SCOPE_ID);
		}catch(InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if(t == null)
				t = e;
			
			return interpreter.setErrnoErrorObject(InterpretingError.INVALID_MODULE, "Invalid entry point (\"" + t.getClass().getSimpleName() + "\"): " + t.getMessage(), SCOPE_ID);
		}
		
		return null;
	}
}