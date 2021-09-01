package me.jddev0.module.io;

import java.util.List;

import me.jddev0.module.io.LangInterpreter.DataObject;

/**
 * IO-Module<br>
 * Helper class for external lang functions
 * 
 * @author JDDev0
 * @version v1.0.0
 */
@FunctionalInterface
public interface LangExternalFunctionObject {
	DataObject callFunc(List<DataObject> argumentList, final int DATA_ID);
}