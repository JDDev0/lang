package me.jddev0.module.lang.regex;

/**
 * Lang-Module<br>
 * Lang RegEx implementation
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangRegEx {
	private LangRegEx() {}
	
	public static boolean matches(String text, String regex) {
		return text.matches(regex);
	}
	
	public static String[] split(String text, String regex) {
		return text.split(regex);
	}
	
	public static String[] split(String text, String regex, int limit) {
		return text.split(regex, limit);
	}
	
	public static String replace(String text, String regex, String replacement) {
		return text.replaceAll(regex, replacement);
	}
}