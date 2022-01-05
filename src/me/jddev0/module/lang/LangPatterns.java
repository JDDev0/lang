package me.jddev0.module.lang;

import java.util.regex.Pattern;


/**
 * Lang-Module<br>
 * RegEx patterns for other lang classes
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangPatterns {
	//General patterns
	/**
	 * RegEx: (\$|&)LANG_.*
	 */
	public static final Pattern LANG_VAR = Pattern.compile("(\\$|&)LANG_.*");
	/**
	 * RegEx: &LANG_.*
	 */
	public static final Pattern LANG_VAR_ARRAY = Pattern.compile("&LANG_.*");
	/**
	 * RegEx: (\$|&|fp\.)\w+
	 */
	public static final Pattern VAR_NAME = Pattern.compile("(\\$|&|fp\\.)\\w+");
	/**
	 * RegEx: (\$\**|&|fp\.)\w+
	 */
	public static final Pattern VAR_NAME_FULL = Pattern.compile("(\\$\\**|&|fp\\.)\\w+");
	/**
	 * RegEx: (\$\**|&|fp\.|func\.|linker\.)\w+
	 */
	public static final Pattern VAR_NAME_FULL_WITH_FUNCS = Pattern.compile("(\\$\\**|&|fp\\.|func\\.|linker\\.)\\w+");
	/**
	 * RegEx: fp\.\w+
	 */
	public static final Pattern VAR_NAME_FUNC_PTR = Pattern.compile("fp\\.\\w+");
	/**
	 * RegEx: (fp|func|linker)\.\w+
	 */
	public static final Pattern VAR_NAME_FUNC_PTR_WITH_FUNCS = Pattern.compile("(fp|func|linker)\\.\\w+");
	/**
	 * RegEx: &\w+
	 */
	public static final Pattern VAR_NAME_ARRAY = Pattern.compile("&\\w+");
	/**
	 * RegEx: \$\[+\w+\]+
	 */
	public static final Pattern VAR_NAME_PTR = Pattern.compile("\\$\\[+\\w+\\]+");
	/**
	 * RegEx: (\$\**|&)\w+
	 */
	public static final Pattern VAR_NAME_DEREFERENCE_AND_ARRAY = Pattern.compile("(\\$\\**|&)\\w+");
	/**
	 * RegEx: \$\**\[+\w+\]+
	 */
	public static final Pattern VAR_NAME_PTR_AND_DEREFERENCE = Pattern.compile("\\$\\**\\[+\\w+\\]+");
	/**
	 * RegEx: (func\.|linker\.)\w+
	 */
	public static final Pattern FUNC_NAME = Pattern.compile("(func\\.|linker\\.)\\w+");
	
	//Function call specific
	/**
	 * RegEx: (\$|&)\w+\.\.\.
	 */
	public static final Pattern FUNC_CALL_VAR_ARGS = Pattern.compile("(\\$|&)\\w+\\.\\.\\.");
	/**
	 * RegEx: \$\[\w+\]
	 */
	public static final Pattern FUNC_CALL_CALL_BY_PTR = Pattern.compile("\\$\\[\\w+\\]");
	/**
	 * RegEx: \$\[LANG_.*\]
	 */
	public static final Pattern FUNC_CALL_CALL_BY_PTR_LANG_VAR = Pattern.compile("\\$\\[LANG_.*\\]");
	
	//LangParser specific
	/**
	 * RegEx: \s.{@literal *}
	 */
	public static final Pattern PARSING_LEADING_WHITSPACE = Pattern.compile("\\s.*");
	/**
	 * RegEx: (\s.*|.*\s)
	 */
	public static final Pattern PARSING_LEADING_OR_TRAILING_WHITSPACE = Pattern.compile("(\\s.*|.*\\s)");
	/**
	 * RegEx: (func|fp|linker)\.\w+\(.*\).{@literal *}
	 */
	public static final Pattern PARSING_FUNCTION_CALL = Pattern.compile("(func|fp|linker)\\.\\w+\\(.*\\).*");
	/**
	 * RegEx: \(.*\).{@literal *}
	 */
	public static final Pattern PARSING_FUNCTION_CALL_PREVIOUS_VALUE = Pattern.compile("\\(.*\\).*");
	/**
	 * RegEx: con\.condition\(.*\).{@literal *}
	 */
	public static final Pattern PARSING_CON_CONDITION = Pattern.compile("con\\.condition\\(.*\\).*");
	/**
	 * RegEx: math\.math\(.*\).{@literal *}
	 */
	public static final Pattern PARSING_MATH_MATH = Pattern.compile("math\\.math\\(.*\\).*");
	/**
	 * RegEx: \s*,.{@literal *}
	 */
	public static final Pattern PARSING_ARGUMENT_SEPARATOR_LEADING_WHITESPACE = Pattern.compile("\\s*,.*");
	/**
	 * RegEx: \$\**\[+\w+\]+.{@literal *}
	 */
	public static final Pattern PARSING_STARTS_WITH_VAR_NAME_PTR_AND_DEREFERENCE = Pattern.compile("\\$\\**\\[+\\w+\\]+.*");
	/**
	 * RegEx: \$(\*+\w+|\[+\w+\]+|\*+\[+\w+\]+).{@literal *}
	 */
	public static final Pattern PARSING_STARTS_WITH_VAR_NAME_PTR_OR_DEREFERENCE = Pattern.compile("\\$(\\*+\\w+|\\[+\\w+\\]+|\\*+\\[+\\w+\\]+).*");
	/**
	 * RegEx: \W.{@literal *}
	 */
	public static final Pattern PARSING_STARTS_WITH_NON_WORD_CHAR = Pattern.compile("\\W.*");
	/**
	 * RegEx: &\w+\.\.\..{@literal *}
	 */
	public static final Pattern PARSING_STARTS_WITH_ARRAY_UNPACKING = Pattern.compile("&\\w+\\.\\.\\..*");
	
	//LangParser assignment specific
	/**
	 * RegEx: "  [^= ]{0,3}= "
	 */
	public static final Pattern PARSING_ASSIGNMENT_OPERATOR = Pattern.compile(" [^= ]{0,3}= ");
	/**
	 * RegEx: (\$\**|&|fp\.)\w+ [^= ]{0,3}= .{@literal *}
	 */
	public static final Pattern PARSING_ASSIGNMENT_VAR_NAME = Pattern.compile("(\\$\\**|&|fp\\.)\\w+ [^= ]{0,3}= .*");
	/**
	 * RegEx: ((\$\**|&|fp\.)\w+ [^= ]{0,3}= .*|(\$\**\[+\w+\]+) [^= ]{0,3}= .*|(\w|\.|\$|\\|%|-)+ = .*)
	 */
	public static final Pattern PARSING_ASSIGNMENT_VAR_NAME_OR_TRANSLATION = Pattern.compile(
		"((\\$\\**|&|fp\\.)\\w+ [^= ]{0,3}= .*|(\\$\\**\\[+\\w+\\]+) [^= ]{0,3}= .*|(\\w|\\.|\\$|\\\\|%|-)+ = .*)");
	
	public static boolean matches(String str, Pattern pattern) {
		return pattern.matcher(str).matches();
	}
	
	private LangPatterns() {}
}