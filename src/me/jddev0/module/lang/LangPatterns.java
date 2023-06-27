package me.jddev0.module.lang;

import java.util.regex.Pattern;

/**
 * Lang-Module<br>
 * RegEx patterns for other Lang classes
 * 
 * @author JDDev0
 * @version v1.0.0
 */
final class LangPatterns {
	//General patterns
	/**
	 * RegEx: "<code>\.</code>"
	 */
	public static final Pattern GENERAL_DOT = Pattern.compile("\\.");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?(\$|&)LANG_.*</code>"
	 */
	public static final Pattern LANG_VAR = Pattern.compile("(\\[\\[\\w+\\]\\]::)?(\\$|&)LANG_.*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?&LANG_.*</code>"
	 */
	public static final Pattern LANG_VAR_ARRAY = Pattern.compile("(\\[\\[\\w+\\]\\]::)?&LANG_.*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?\$\w+</code>"
	 */
	public static final Pattern VAR_NAME_NORMAL = Pattern.compile("(\\[\\[\\w+\\]\\]::)?\\$\\w+");
	/**
	 * RegEx: "<code>(\$|&|fp\.)\w+</code>"
	 */
	public static final Pattern VAR_NAME_WITHOUT_PREFIX = Pattern.compile("(\\$|&|fp\\.)\\w+");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?(\$|&|fp\.)\w+</code>"
	 */
	public static final Pattern VAR_NAME = Pattern.compile("(\\[\\[\\w+\\]\\]::)?(\\$|&|fp\\.)\\w+");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?(\$\**|&|fp\.)\w+</code>"
	 */
	public static final Pattern VAR_NAME_FULL = Pattern.compile("(\\[\\[\\w+\\]\\]::)?(\\$\\**|&|fp\\.)\\w+");
	/**
	 * RegEx: "<code>((\[\[\w+\]\]::)?(\$\**|&|fp\.)|func\.|fn\.|linker\.|ln\.)\w+</code>"
	 */
	public static final Pattern VAR_NAME_FULL_WITH_FUNCS = Pattern.compile("((\\[\\[\\w+\\]\\]::)?(\\$\\**|&|fp\\.)|func\\.|fn\\.|linker\\.|ln\\.)\\w+");
	/**
	 * RegEx: "<code>((\[\[\w+\]\]::)?(\$\**|&|fp\.)\w+|\$\**\[+\w+\]+)</code>"
	 */
	public static final Pattern VAR_NAME_FULL_WITH_PTR_AND_DEREFERENCE = Pattern.compile("((\\[\\[\\w+\\]\\]::)?(\\$\\**|&|fp\\.)\\w+|\\$\\**\\[+\\w+\\]+)");
	/**
	 * RegEx: "<code>(((\[\[\w+\]\]::)?(\$\**|&|fp\.)|func\.|fn\.|linker\.|ln\.)\w+|(\[\[\w+\]\]::)?\$\**\[+\w+\]+)</code>"
	 */
	public static final Pattern VAR_NAME_FULL_WITH_FUNCS_AND_PTR_AND_DEREFERENCE = Pattern.compile(
			"(((\\[\\[\\w+\\]\\]::)?(\\$\\**|&|fp\\.)|func\\.|fn\\.|linker\\.|ln\\.)\\w+|(\\[\\[\\w+\\]\\]::)?\\$\\**\\[+\\w+\\]+)");
	/**
	 * RegEx: "<code>((\[\[\w+\]\]::)?(\$\**|&)\w*|\$\**\[+\w+\]+)</code>"
	 */
	public static final Pattern VAR_NAME_PREFIX_ARRAY_AND_NORMAL_WITH_PTR_AND_DEREFERENCE_WITH_OPTIONAL_NAME = Pattern.compile("((\\[\\[\\w+\\]\\]::)?(\\$\\**|&)\\w*|\\$\\**\\[+\\w+\\]+)");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?fp\.\w+</code>"
	 */
	public static final Pattern VAR_NAME_FUNC_PTR = Pattern.compile("(\\[\\[\\w+\\]\\]::)?fp\\.\\w+");
	/**
	 * RegEx: "<code>((\[\[\w+\]\]::)?fp|func|fn|linker|ln)\.\w+</code>"
	 */
	public static final Pattern VAR_NAME_FUNC_PTR_WITH_FUNCS = Pattern.compile("((\\[\\[\\w+\\]\\]::)?fp|func|fn|linker|ln)\\.\\w+");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?&\w+</code>"
	 */
	public static final Pattern VAR_NAME_ARRAY = Pattern.compile("(\\[\\[\\w+\\]\\]::)?&\\w+");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?\$\[+\w+\]+</code>"
	 */
	public static final Pattern VAR_NAME_PTR = Pattern.compile("(\\[\\[\\w+\\]\\]::)?\\$\\[+\\w+\\]+");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?(\$\**|&)\w+</code>"
	 */
	public static final Pattern VAR_NAME_DEREFERENCE_AND_ARRAY = Pattern.compile("(\\[\\[\\w+\\]\\]::)?(\\$\\**|&)\\w+");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?\$\**\[+\w+\]+</code>"
	 */
	public static final Pattern VAR_NAME_PTR_AND_DEREFERENCE = Pattern.compile("(\\[\\[\\w+\\]\\]::)?\\$\\**\\[+\\w+\\]+");
	/**
	 * RegEx: "<code>(func\.|fn\.|linker\.|ln\.)\w+</code>"
	 */
	public static final Pattern FUNC_NAME = Pattern.compile("(func\\.|fn\\.|linker\\.|ln\\.)\\w+");
	/**
	 * RegEx: "<code>\{[?!]?([A-Z]+\|)*[A-Z]+\}</code>"
	 */
	public static final Pattern TYPE_CONSTRAINT = Pattern.compile("\\{[?!]?([A-Z_]+\\|)*[A-Z_]+\\}");
	
	//Function call specific
	/**
	 * RegEx: "<code>(\$|&)\w+\.\.\.</code>"
	 */
	public static final Pattern FUNC_CALL_VAR_ARGS = Pattern.compile("(\\$|&)\\w+\\.\\.\\.");
	/**
	 * RegEx: "<code>\$\[\w+\]</code>"
	 */
	public static final Pattern FUNC_CALL_CALL_BY_PTR = Pattern.compile("\\$\\[\\w+\\]");
	/**
	 * RegEx: "<code>\$\[LANG_.*\]</code>"
	 */
	public static final Pattern FUNC_CALL_CALL_BY_PTR_LANG_VAR = Pattern.compile("\\$\\[LANG_.*\\]");
	
	//LangParser specific
	/**
	 * RegEx: "<code>\s.*</code>"
	 */
	public static final Pattern PARSING_LEADING_WHITSPACE = Pattern.compile("\\s.*");
	/**
	 * RegEx: "<code>^\s+</code>"
	 */
	public static final Pattern PARSING_FRONT_WHITESPACE = Pattern.compile("^\\s+");
	/**
	 * RegEx: "<code>(\s.*|.*\s)</code>"
	 */
	public static final Pattern PARSING_LEADING_OR_TRAILING_WHITSPACE = Pattern.compile("(\\s.*|.*\\s)");
	/**
	 * RegEx: "<code>((\[\[\w+\]\]::)?fp|func|fn|linker|ln)\.\w+\(.*\).*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_FUNCTION_CALL = Pattern.compile("((\\[\\[\\w+\\]\\]::)?fp|func|fn|linker|ln)\\.\\w+\\(.*\\).*");
	/**
	 * RegEx: "<code>\w+\(.*\).*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_FUNCTION_CALL_WITHOUT_PREFIX = Pattern.compile("\\w+\\(.*\\).*");
	/**
	 * RegEx: "<code>parser\.\w+\(.*\).*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_PARSER_FUNCTION_CALL = Pattern.compile("parser\\.\\w+\\(.*\\).*");
	/**
	 * RegEx: "<code>\(.*\).*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_FUNCTION_CALL_PREVIOUS_VALUE = Pattern.compile("\\(.*\\).*");
	/**
	 * RegEx: "<code>\s*,.*</code>"
	 */
	public static final Pattern PARSING_ARGUMENT_SEPARATOR_LEADING_WHITESPACE = Pattern.compile("\\s*,.*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::).*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_MODULE_VAR_IDENTIFIER = Pattern.compile("(\\[\\[\\w+\\]\\]::).*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?\$\**\[+\w+\]+.*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_VAR_NAME_PTR_AND_DEREFERENCE = Pattern.compile("(\\[\\[\\w+\\]\\]::)?\\$\\**\\[+\\w+\\]+.*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?\$(\*+\w+|\[+\w+\]+|\*+\[+\w+\]+).*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_VAR_NAME_PTR_OR_DEREFERENCE = Pattern.compile("(\\[\\[\\w+\\]\\]::)?\\$(\\*+\\w+|\\[+\\w+\\]+|\\*+\\[+\\w+\\]+).*");
	/**
	 * RegEx: "<code>.*(\[\[\w+\]\]::)</code>"
	 */
	public static final Pattern PARSING_ENDS_WITH_MODULE_VAR_IDENTIFIER = Pattern.compile(".*(\\[\\[\\w+\\]\\]::)");
	/**
	 * RegEx: "<code>(-?(NaN|Infinity))|(.*[fFdD])|(0[xX].*)</code>"
	 */
	public static final Pattern PARSING_INVALID_FLOATING_POINT_NUMBER = Pattern.compile("(-?(NaN|Infinity))|(.*[dD])|(0[xX].*)");
	/**
	 * RegEx: "<code>(.*[fFdD])|(0[xX].*)</code>"
	 */
	public static final Pattern PARSING_INVALID_FLOATING_POINT_NUMBER_ALLOW_NaN_INFINITY = Pattern.compile("(.*[fFdD])|(0[xX].*)");
	/**
	 * RegEx: "<code>(.*[fFdD])|(0[xX].*)|(\s.*|.*\s)</code>"
	 */
	public static final Pattern PARSING_INVALID_FLOATING_POINT_NUMBER_ALLOW_NaN_INFINITY_OR_LEADING_OR_TRAILING_WHITESPACES = Pattern.compile("(.*[dD])|(0[xX].*)|(\\s.*|.*\\s)");
	/**
	 * RegEx: "<code>\W.*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_NON_WORD_CHAR = Pattern.compile("\\W.*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?&\w+\.\.\..*</code>"
	 */
	public static final Pattern PARSING_STARTS_WITH_ARRAY_UNPACKING = Pattern.compile("(\\[\\[\\w+\\]\\]::)?&\\w+\\.\\.\\..*");
	/**
	 * RegEx: "<code>\{[?!]?([A-Z]+\|)*[A-Z]+\}.*</code>"
	 */
	public static final Pattern PARSING_STARTS_TYPE_CONSTRAINT = Pattern.compile("\\{[?!]?([A-Z]+\\|)*[A-Z]+\\}.*");
	/**
	 * RegEx: "<code>.*\)(:\{[?!]?([A-Z]+\|)*[A-Z]+\})? -> .*</code>"
	 */
	public static final Pattern PARSING_CONTAINS_WITH_FUNC_DEFINITION_END_WITH_OR_WITHOUT_TYPE_CONSTRAINT = Pattern.compile(".*\\)(:\\{[?!]?([A-Z]+\\|)*[A-Z]+\\})? -> .*");
	
	//LangParser assignment specific
	/**
	 * RegEx: "<code>parser(\.\w+)+ ?= ?.*</code>"
	 */
	public static final Pattern PARSING_PARSER_FLAG = Pattern.compile("parser(\\.\\w+)+ ?= ?.*");
	/**
	 * RegEx: "<code>"  [^\\= ]{0,3}= "</code>"
	 */
	public static final Pattern PARSING_ASSIGNMENT_OPERATOR = Pattern.compile(" [^\\\\= ]{0,3}= ");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?(\$\**|&|fp\.)\w+ [^\\= ]{0,3}= .*</code>"
	 */
	public static final Pattern PARSING_ASSIGNMENT_VAR_NAME = Pattern.compile("(\\[\\[\\w+\\]\\]::)?(\\$\\**|&|fp\\.)\\w+ [^\\\\= ]{0,3}= .*");
	/**
	 * RegEx: "<code>[^=]+ [^\\= ]{0,3}= .*</code>"
	 */
	public static final Pattern PARSING_ASSIGNMENT_OPERATION_WITH_OPERATOR = Pattern.compile("[^=]+ [^\\\\= ]{1,3}= .*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?(\$\**|&|fp\.)\w+ [^\\= ]{0,3}= .*|[^=]+ = .*</code>"
	 */
	public static final Pattern PARSING_ASSIGNMENT_VAR_NAME_OR_TRANSLATION = Pattern.compile(
		"(\\[\\[\\w+\\]\\]::)?(\\$\\**|&|fp\\.)\\w+ [^\\\\= ]{0,3}= .*|[^=]+ = .*");
	/**
	 * RegEx: "<code>(\[\[\w+\]\]::)?\$\**\w+=.*</code>"
	 */
	public static final Pattern PARSING_SIMPLE_ASSIGNMENT = Pattern.compile("(\\[\\[\\w+\\]\\]::)?\\$\\**\\w+=.*");
	/**
	 * RegEx: "<code>[\w\-\.\:]+=.*</code>"
	 */
	public static final Pattern PARSING_SIMPLE_TRANSLATION = Pattern.compile("[\\w\\-\\.\\:]+=.*");
	
	public static boolean matches(String str, Pattern pattern) {
		return pattern.matcher(str).matches();
	}
	
	public static String replaceAll(String str, String replacement, Pattern pattern) {
		return pattern.matcher(str).replaceAll(replacement);
	}
	
	private LangPatterns() {}
}