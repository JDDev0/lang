package me.jddev0.module.lang;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

/**
 * Lang-Module<br>
 * Lang module data
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangModule {
	private final String file;
	
	private final boolean load;
	
	private final Map<String, ZipEntry> zipEntries;
	private final Map<String, byte[]> zipData;
	
	private final LangModuleConfiguration lmc;
	
	private final List<String> exportedFunctions = new LinkedList<>();
	private final Map<String, DataObject> exportedVariables = new HashMap<>();
	
	private final Map<String, LangNativeModule> loadedNativeModules = new HashMap<>();
	
	public LangModule(String file, boolean load, Map<String, ZipEntry> zipEntries, Map<String, byte[]> zipData, LangModuleConfiguration lmc) {
		this.file = file;
		this.load = load;
		this.zipEntries = new HashMap<>(zipEntries);
		this.zipData = new HashMap<>(zipData);
		this.lmc = lmc;
	}
	
	List<String> getExportedFunctions() {
		return exportedFunctions;
	}
	
	Map<String, DataObject> getExportedVariables() {
		return exportedVariables;
	}
	
	Map<String, LangNativeModule> getLoadedNativeModules() {
		return loadedNativeModules;
	}
	
	public String getFile() {
		return file;
	}
	
	public boolean isLoad() {
		return load;
	}
	
	public Map<String, ZipEntry> getZipEntries() {
		return new HashMap<>(zipEntries);
	}
	
	public Map<String, byte[]> getZipData() {
		return new HashMap<>(zipData);
	}
	
	public LangModuleConfiguration getLangModuleConfiguration() {
		return lmc;
	}
}