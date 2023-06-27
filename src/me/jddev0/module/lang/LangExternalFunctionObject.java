package me.jddev0.module.lang;

import java.util.List;

/**
 * Lang-Module<br>
 * Helper class for external Lang functions
 * 
 * @author JDDev0
 * @version v1.0.0
 */
@FunctionalInterface
public interface LangExternalFunctionObject {
	DataObject callFunc(LangInterpreter interpreter, List<DataObject> argumentList, final int SCOPE_ID);
}