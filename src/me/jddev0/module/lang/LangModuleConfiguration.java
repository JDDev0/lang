package me.jddev0.module.lang;

import java.util.HashMap;
import java.util.Map;

/**
 * Lang-Module<br>
 * Lang module configuration data
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangModuleConfiguration {
	private final String name;
	private final String description;
	private final String version;
	
	private final String minSupportedVersion;
	private final String maxSupportedVersion;
	
	private final String[] supportedImplementations;
	
	private final ModuleType moduleType;
	
	private final String nativeEntryPoint;
	
	public static LangModuleConfiguration parseLmc(String lmc) throws InvalidModuleConfigurationException {
		Map<String, String> data = new HashMap<>();
		
		for(String line:lmc.split("\n")) {
			if(line.contains("#"))
				line = line.substring(0, line.indexOf("#"));
			
			line = line.trim();
			
			if(line.isEmpty())
				continue;
			
			String[] tokens = line.split(" = ", 2);
			if(tokens.length != 2)
				throw new InvalidModuleConfigurationException("Invalid configuration line (\" = \" is missing): " + line);
			
			data.put(tokens[0], tokens[1]);
		}
		
		String name = data.get("name");
		if(name == null)
			throw new InvalidModuleConfigurationException("Mandatory configuration of \"name\" is missing");
		for(int i = 0; i < name.length(); ++i) {
			char c = name.charAt(i);
			if((c < 'a' || c > 'z') && (c < 'A' || c > 'Z') && (c < '0' || c > '9') && c != '_') {
				throw new InvalidModuleConfigurationException("The module name may only contain alphanumeric characters and underscores (_)");
			}
		}
		
		String supportedImplementationsString = data.get("supportedImplementations");
		String[] supportedImplementations = null;
		if(supportedImplementationsString != null) {
			supportedImplementations = supportedImplementationsString.split(",");
			for(int i = 0;i < supportedImplementations.length;i++)
				supportedImplementations[i] = supportedImplementations[i].trim();
		}
		
		String moduleTypeString = data.get("moduleType");
		if(moduleTypeString == null)
			throw new InvalidModuleConfigurationException("Mandatory configuration of \"moduleType\" is missing");
		
		ModuleType moduleType;
		if(moduleTypeString.equals("lang"))
			moduleType = ModuleType.LANG;
		else if(moduleTypeString.equals("native"))
			moduleType = ModuleType.NATIVE;
		else
			throw new InvalidModuleConfigurationException("Invalid configuration for \"moduleType\" (Must be one of \"lang\", \"native\"): " + moduleTypeString);
		
		return new LangModuleConfiguration(name, data.get("description"), data.get("version"), data.get("minSupportedVersion"),
				data.get("maxSupportedVersion"), supportedImplementations, moduleType, data.get("nativeEntryPoint"));
	}
	
	public LangModuleConfiguration(String name, String description, String version, String minSupportedVersion,
			String maxSupportedVersion, String[] supportedImplementations, ModuleType moduleType, String nativeEntryPoint) {
		this.name = name;
		this.description = description;
		this.version = version;
		this.minSupportedVersion = minSupportedVersion;
		this.maxSupportedVersion = maxSupportedVersion;
		this.supportedImplementations = supportedImplementations;
		this.moduleType = moduleType;
		this.nativeEntryPoint = nativeEntryPoint;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getVersion() {
		return version;
	}
	
	public String getMinSupportedVersion() {
		return minSupportedVersion;
	}
	
	public String getMaxSupportedVersion() {
		return maxSupportedVersion;
	}
	
	public String[] getSupportedImplementations() {
		return supportedImplementations;
	}
	
	public ModuleType getModuleType() {
		return moduleType;
	}
	
	public String getNativeEntryPoint() {
		return nativeEntryPoint;
	}
	
	public enum ModuleType {
		LANG, NATIVE
	}
	
	public static class InvalidModuleConfigurationException extends RuntimeException {
		private static final long serialVersionUID = -5267162637317286784L;

		public InvalidModuleConfigurationException(String message) {
			super(message);
		}
	}
}