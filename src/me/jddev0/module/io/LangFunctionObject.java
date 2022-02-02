package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import me.jddev0.module.lang.DataObject;
import me.jddev0.module.lang.LangPredefinedFunctionObject;

/**
 * IO-Module<br>
 * Helper class for predefined lang functions
 * 
 * @deprecated Will be removed in v1.2.0
 * @author JDDev0
 * @version v1.0.0
 */
@Deprecated
@FunctionalInterface
public interface LangFunctionObject extends LangPredefinedFunctionObject {
	/**
	 * @deprecated Will be removed in v1.2.0
	 */
	@Deprecated
	String callFunc(BufferedReader lines, String arg, final int SCOPE_ID);
	
	/**
	 * @deprecated Will be removed in v1.2.0
	 */
	@Deprecated
	@Override
	default DataObject callFunc(List<DataObject> parameterList, final int SCOPE_ID) {
		StringBuilder builder = new StringBuilder();
		parameterList.forEach(builder::append);
		return new DataObject(callFunc(new BufferedReader(new StringReader("")), builder.toString(), SCOPE_ID));
	}
}