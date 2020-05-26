package me.jddev0.module.io;

import java.io.BufferedReader;

/**
 * IO-Module<br>
 * Helper class for Lang
 * 
 * @author JDDev0
 * @version v0.1.5
 */
@FunctionalInterface
public interface LangFunctionObject {
	String callFunc(BufferedReader lines, String arg, final int DATA_ID);
}