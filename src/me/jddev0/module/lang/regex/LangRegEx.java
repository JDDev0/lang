package me.jddev0.module.lang.regex;

import java.util.regex.PatternSyntaxException;

/**
 * Lang-Module<br>
 * Lang RegEx implementation
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangRegEx {
	private LangRegEx() {}
	
	public static boolean matches(String text, String regex) throws InvalidPaternSyntaxException {
		try {
			return text.matches(regex);
		}catch(PatternSyntaxException e) {
			throw new InvalidPaternSyntaxException(e.getMessage());
		}
	}
	
	public static String[] split(String text, String regex) throws InvalidPaternSyntaxException {
		try {
			return text.split(regex);
		}catch(PatternSyntaxException e) {
			throw new InvalidPaternSyntaxException(e.getMessage());
		}
	}
	
	public static String[] split(String text, String regex, int limit) throws InvalidPaternSyntaxException {
		try {
			return text.split(regex, limit);
		}catch(PatternSyntaxException e) {
			throw new InvalidPaternSyntaxException(e.getMessage());
		}
	}
	
	public static String replace(String text, String regex, String replacement) throws InvalidPaternSyntaxException {
		try {
			return text.replaceAll(regex, replacement);
		}catch(PatternSyntaxException e) {
			throw new InvalidPaternSyntaxException(e.getMessage());
		}
	}
}