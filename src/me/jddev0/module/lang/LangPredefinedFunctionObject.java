package me.jddev0.module.lang;

import java.util.List;

/**
 * Lang-Module<br>
 * Helper class for predefined Lang functions
 * 
 * @author JDDev0
 * @version v1.0.0
 */
@FunctionalInterface
public interface LangPredefinedFunctionObject {
	DataObject callFunc(List<DataObject> argumentList, final int SCOPE_ID);
	
	/**
	 * @return Returns true if this function is a linker function
	 */
	default boolean isLinkerFunction() {
		return false;
	}
	
	/**
	 * @return Returns true if this function is deprecated
	 */
	default boolean isDeprecated() {
		return false;
	}
	
	/**
	 * @return Returns in which version this function will be removed
	 */
	default String getDeprecatedRemoveVersion() {
		return null;
	}
	
	/**
	 * @return Returns which function could be used in future
	 */
	default String getDeprecatedReplacementFunction() {
		return null;
	}
}