package me.jddev0.module.lang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import me.jddev0.module.lang.DataObject.DataType;

/**
 * Lang-Module<br>
 * Utility methods for the LangInterpreter, LangParser, and classes using the LII
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangUtils {
	private LangUtils() {}
	
	/**
	 * @return Will return null if the dataObjects is empty or if dataObjects only contains Java null values
	 */
	public static DataObject combineDataObjects(List<DataObject> dataObjects) {
		dataObjects = new LinkedList<>(dataObjects);
		dataObjects.removeIf(Objects::isNull);
		
		if(dataObjects.isEmpty())
			return null;
		
		if(dataObjects.size() == 1)
			return dataObjects.get(0);
		
		//Remove all void objects
		dataObjects.removeIf(dataObject -> dataObject.getType() == DataType.VOID);
		
		//Return a single void object if every data object is a void object
		if(dataObjects.size() == 0)
			return new DataObject().setVoid();
		
		if(dataObjects.size() == 1)
			return dataObjects.get(0);
		
		//Combine everything to a single text object
		final StringBuilder builder = new StringBuilder();
		dataObjects.forEach(builder::append);
		return new DataObject(builder.toString());
	}
	
	/**
	 * @return Returns the next DataObject (One DataObject or a combined text value DataObject) before the next ARGUMENT_SEPARATOR or the end and removes all used data objects
	 */
	public static DataObject getNextArgumentAndRemoveUsedDataObjects(List<DataObject> argumentList, boolean removeArgumentSpearator) {
		List<DataObject> argumentTmpList = new LinkedList<>();
		while(!argumentList.isEmpty() && argumentList.get(0).getType() != DataType.ARGUMENT_SEPARATOR)
			argumentTmpList.add(argumentList.remove(0));
		
		if(argumentTmpList.isEmpty())
			argumentTmpList.add(new DataObject().setVoid());
		
		if(removeArgumentSpearator && argumentList.size() > 0)
			argumentList.remove(0); //Remove ARGUMENT_SEPARATOR
		
		return combineDataObjects(argumentTmpList);
	}
	
	/**
	 * @return Returns the the count of DataObjects in the with argument separated argumentList (Will return 1 for an empty list)
	 */
	public static int countDataObjects(List<DataObject> argumentList) {
		return (int)argumentList.stream().filter(dataObject -> dataObject.getType() == DataType.ARGUMENT_SEPARATOR).count() + 1;
	}
	
	/**
	 * @return Returns a list of DataObjects where every ARGUMENT_SEPARATOR is removed and arguments are combined into single values (A VOID value is used for empty arguments)
	 */
	public static List<DataObject> combineArgumentsWithoutArgumentSeparators(List<DataObject> argumentList) {
		if(argumentList.isEmpty())
			return new ArrayList<>();
		
		argumentList = new LinkedList<>(argumentList);
		
		List<DataObject> combinedArgumentList = new ArrayList<>();
		List<DataObject> argumentTmpList = new LinkedList<>();
		while(!argumentList.isEmpty()) {
			DataObject currentDataObject = argumentList.remove(0);
			if(currentDataObject.getType() == DataType.ARGUMENT_SEPARATOR) {
				if(argumentTmpList.isEmpty())
					argumentTmpList.add(new DataObject().setVoid());
				
				combinedArgumentList.add(combineDataObjects(argumentTmpList));
				argumentTmpList.clear();
				
				continue;
			}
			
			argumentTmpList.add(currentDataObject);
		}
		
		if(argumentTmpList.isEmpty())
			argumentTmpList.add(new DataObject().setVoid());
		
		combinedArgumentList.add(combineDataObjects(argumentTmpList));
		
		return combinedArgumentList;
	}
	
	/**
	 * @return Returns a list of DataObjects where all arguments are separated by an ARGUMENT_SEPARATOR
	 */
	public static List<DataObject> separateArgumentsWithArgumentSeparators(List<DataObject> argumentList) {
		argumentList = new LinkedList<>(argumentList);
		
		for(int i = argumentList.size() - 1;i > 0;i--)
			argumentList.add(i, new DataObject().setArgumentSeparator(", "));
		
		return argumentList;
	}
	
	/**
	 * @param varArgsParameterIndices Index of the var args argument of the function signature, if there is no var args argument the value must be set to -1
	 * @param argumentList The combined argument list
	 * 
	 * @return Returns the index of the most restrictive function signature for the provided arguments
	 */
	public static int getMostRestrictiveFunctionSignatureIndex(List<List<DataObject.DataTypeConstraint>> functionSignatures,
			List<Integer> varArgsParameterIndices, List<DataObject> argumentList) {
		List<DataObject.DataTypeConstraint> bestFunctionSignature = null;
		int bestFunctionIndex = -1;
		int bestAllowedTypesCount = -1;
		int bestVarArgsParameterIndex = -1;
		int bestVarArgsPenalty = -1;
		
		outer:
		for(int i = 0;i < functionSignatures.size();i++) {
			List<DataObject.DataTypeConstraint> functionSignature = functionSignatures.get(i);
			int varArgsParameterIndex = varArgsParameterIndices.get(i);
			if(varArgsParameterIndex == -1?functionSignature.size() != argumentList.size():functionSignature.size() - 1 > argumentList.size())
				continue; //Argument count does not match
			
			int varArgsPenalty = varArgsParameterIndex == -1?-1:functionSignature.get(varArgsParameterIndex).getAllowedTypes().size();
			
			int argumentIndex = 0;
			for(int j = 0;j < functionSignature.size();j++) {
				if(varArgsParameterIndex == j) {
					int oldArgumentIndex = argumentIndex;
					
					argumentIndex = argumentList.size() - functionSignature.size() + j + 1;
					
					//Check if types are allowed for var args parameter
					for(int k = oldArgumentIndex;k < argumentIndex;k++)
						if(!functionSignature.get(j).isTypeAllowed(argumentList.get(k).getType()))
							continue outer;
					
					continue;
				}
				
				if(!functionSignature.get(j).isTypeAllowed(argumentList.get(argumentIndex).getType()))
					continue outer;
				
				argumentIndex++;
			}
			
			int allowedTypesCount = functionSignature.stream().reduce(0, (cnt, arg) -> cnt + arg.getAllowedTypes().size(), Integer::sum);
			int sizeDiff = bestFunctionSignature == null?0:(bestFunctionSignature.size() - functionSignature.size());
			if(bestFunctionIndex == -1 || (varArgsParameterIndex == -1 && bestVarArgsParameterIndex != -1) ||
					(varArgsParameterIndex == -1 && bestVarArgsParameterIndex == -1 && allowedTypesCount < bestAllowedTypesCount) ||
					(varArgsParameterIndex != -1 && bestVarArgsParameterIndex != -1 && (sizeDiff == 0?varArgsPenalty < bestVarArgsPenalty:
						(sizeDiff < 0?(allowedTypesCount < bestAllowedTypesCount + bestVarArgsPenalty * -sizeDiff):
							(allowedTypesCount + varArgsPenalty * sizeDiff < bestAllowedTypesCount))))) {
				bestFunctionSignature = functionSignature;
				bestFunctionIndex = i;
				bestAllowedTypesCount = allowedTypesCount;
				bestVarArgsParameterIndex = varArgsParameterIndex;
				bestVarArgsPenalty = varArgsPenalty;
			}
		}
		
		return bestFunctionIndex;
	}
	
	/**
	 * @return Returns the version as an integer array of length 3 or null if the version is invalid
	 */
	public static int[] getVersionComponents(String version) {
		if(version.length() < 1)
			return null;
		
		if(version.charAt(0) != 'v')
			return null;
		
		if(version.contains("-0"))
			return null;
		
		int majorMinorSeparatorIndex = version.indexOf('.');
		if(majorMinorSeparatorIndex == -1)
			return null;
		
		int minorBugfixSeparatorIndex = version.indexOf('.', majorMinorSeparatorIndex + 1);
		if(minorBugfixSeparatorIndex == -1)
			return null;
		
		String majorStr = version.substring(1, majorMinorSeparatorIndex);
		String minorStr = version.substring(majorMinorSeparatorIndex + 1, minorBugfixSeparatorIndex);
		String bugfixStr = version.substring(minorBugfixSeparatorIndex + 1);
		
		try {
			int major = Integer.parseInt(majorStr);
			int minor = Integer.parseInt(minorStr);
			int bugfix = Integer.parseInt(bugfixStr);
			
			if(major < 0 || minor < 0 || bugfix < 0)
				return null;
			
			return new int[] {major, minor, bugfix};
		}catch(NumberFormatException e) {
			return null;
		}
	}
	
	/**
	 * @return Returns &lt;0 if versionA is older than versionB<br>
	 * returns &gt;0 if versionA is newer than versionB<br>
	 * returns 0 if versionA is equalsTo versionB<br>
	 * returns null if at least one argument is invalid
	 */
	public static Integer compareVersions(int[] versionA, int[] versionB) {
		if(versionA == null || versionA.length != 3 || versionB == null || versionB.length != 3)
			return null;
		
		if(versionA[0] < 0 || versionA[1] < 0 || versionA[2] < 0 ||
				versionB[0] < 0 || versionB[1] < 0 || versionB[2] < 0)
			return null;
		
		if(versionA[0] != versionB[0])
			return Integer.compare(versionA[0], versionB[0]);
		
		if(versionA[1] != versionB[1])
			return Integer.compare(versionA[1], versionB[1]);
		
		return Integer.compare(versionA[2], versionB[2]);
	}
	
	/**
	 * @return Returns &lt;0 if versionA is older than versionB<br>
	 * returns &gt;0 if versionA is newer than versionB<br>
	 * returns 0 if versionA is equalsTo versionB<br>
	 * returns null if at least one argument is invalid
	 */
	public static Integer compareVersions(String versionA, String versionB) {
		int[] versionAComponents = getVersionComponents(versionA);
		int[] versionBComponents = getVersionComponents(versionB);
		
		if(versionAComponents == null || versionBComponents == null)
			return null;
		
		if(versionAComponents[0] != versionBComponents[0])
			return Integer.compare(versionAComponents[0], versionBComponents[0]);
		
		if(versionAComponents[1] != versionBComponents[1])
			return Integer.compare(versionAComponents[1], versionBComponents[1]);
		
		return Integer.compare(versionAComponents[2], versionBComponents[2]);
	}
	
	/**
	 * @return Returns the index of the matching bracket (Escaped chars will be ignored (escape char: '\')) or -1 if no matching bracket was found
	 */
	public static int getIndexOfMatchingBracket(String string, int startIndex, int endIndex, char openedBracket, char closedBracket) {
		if(startIndex < 0)
			return -1;
		
		int bracketCount = 0;
		for(int i = startIndex;i < endIndex && i < string.length();i++) {
			char c = string.charAt(i);
			
			//Ignore escaped chars
			if(c == '\\') {
				i++;
				
				continue;
			}
			
			if(c == openedBracket) {
				bracketCount++;
			}else if(c == closedBracket) {
				bracketCount--;
				
				if(bracketCount == 0)
					return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * @return Returns an escaped string which would be parsed as value
	 */
	public static String escapeString(String str) {
		if(str == null)
			return null;
		
		//Escape slashes
		str = str.replace("\\", "\\\\");
		
		//Escape numbers
		for(int i = 0;i < 10;i++)
			str = str.replace(i + "", i + "\\e");
		
		//"\e": Used for escaping empty text
		return "\\e" + str.replace("\0", "\\0").replace("\n", "\\n").replace("\r", "\\r").replace("\f", "\\f").
		replace(" ", "\\s").replace("\b", "\\b").replace("\033", "\\E").replace("\t", "\\t").replace("$", "\\$").
		replace("&", "\\&").replace("#", "\\#").replace(",", "\\,").replace(".", "\\.").replace("(", "\\(").
		replace(")", "\\)").replace("[", "\\[").replace("]", "\\]").replace("{", "\\{").replace("}", "\\}").
		replace("=", "\\=").replace("<", "\\<").replace(">", "\\>").replace("+", "\\+").replace("-", "\\-").
		replace("/", "\\/").replace("*", "\\*").replace("%", "\\%").replace("|", "\\|").replace("~", "\\~").
		replace("^", "\\^").replace("?", "\\?").replace(":", "\\:").replace("@", "\\@").replace("▲", "\\▲").
		replace("▼", "\\▼").
		
		replace("!", "\\!!").replace("null", "nul\\!l").replace("return", "retur\\!n").
		replace("throw", "thro\\!w") + "\\e";
	}
	
	/**
	 * @return Returns true if the backslash at the index index is escaped else false
	 */
	public static boolean isBackslashAtIndexEscaped(String str, int index) {
		if(str == null || str.length() <= index || index < 0 || str.charAt(index) != '\\')
			return false;
		
		for(int i = index - 1;i >= 0;i--)
			if(str.charAt(i) != '\\')
				return (index - i) % 2 == 0;
		
		return index % 2 == 1;
	}
	
	/**
	 * @return Will return a formatted template translation ("{" can be escaped with "{{")
	 */
	public static String formatTranslationTemplate(String translationValue, Map<String, String> templateMap) throws InvalidTranslationTemplateSyntaxException {
		StringBuilder builder = new StringBuilder();
		
		int i;
		int startIndex = 0;
		do {
			i = translationValue.indexOf('{', startIndex);
			
			if(i == -1) {
				builder.append(translationValue.substring(startIndex));
				
				break;
			}else {
				builder.append(translationValue.substring(startIndex, i));
				startIndex = i;
			}
			
			if(translationValue.charAt(i) == '{' && i < translationValue.length() - 1 && translationValue.charAt(i + 1) == '{') {
				builder.append(translationValue.substring(startIndex, i + 1)); //Ignore second '{'
				startIndex = i + 2;
				
				continue;
			}
			
			int matchingBracketIndex = translationValue.indexOf('}', i);
			if(matchingBracketIndex == -1)
				throw new InvalidTranslationTemplateSyntaxException("Template closing bracket is missing");
			
			startIndex = matchingBracketIndex + 1;
			
			String templateName = translationValue.substring(i + 1, matchingBracketIndex);
			if(!templateMap.containsKey(templateName))
				throw new InvalidTranslationTemplateSyntaxException("Template with the name \"" + templateName + "\" was not defined");
			
			builder.append(templateMap.get(templateName));
		}while(i < translationValue.length());
		
		return builder.toString();
	}

	/**
	 * @return Will return a formatted translation with the correct pluralization (";" can be escaped with ";;" and "{" can be escaped with "{{")
	 */
	public static String formatTranslationTemplatePluralization(String translationValue, int count) throws InvalidTranslationTemplateSyntaxException, NumberFormatException {
		return formatTranslationTemplatePluralization(translationValue, count, new HashMap<>());
	}
	
	/**
	 * @return Will return a formatted translation with the correct pluralization and additional template values [the count template value will be overridden]
	 * (";" can be escaped with ";;" and "{" can be escaped with "{{")
	 */
	public static String formatTranslationTemplatePluralization(String translationValue, int count, Map<String, String> templateMap) throws InvalidTranslationTemplateSyntaxException,
	NumberFormatException {
		List<String> templateTokens = new LinkedList<>();
		
		int startIndex = 0;
		int i = 0;
		while(i < translationValue.length()) {
			if(i == translationValue.length() - 1) {
				templateTokens.add(translationValue.substring(startIndex, i + 1).replace(";;", ";")); //Ignore second ";"s
				
				break;
			}
			
			if(translationValue.charAt(i) == ';') {
				if(translationValue.charAt(i + 1) == ';') {
					i += 2; //Skip two ';'
					
					continue;
				}
				
				templateTokens.add(translationValue.substring(startIndex, i + 1).replace(";;", ";")); //Ignore second ";"s
				startIndex = i + 1;
			}
			
			i++;
		}
		
		List<TranslationPluralizationTemplate> templates = new LinkedList<>();
		for(i = 0;i < templateTokens.size();i++) {
			String templateToken = templateTokens.get(i);
			if(templateToken.charAt(templateToken.length() - 1) == ';')
				templateToken = templateToken.substring(0, templateToken.length() - 1);
			else if(i != templateTokens.size() - 1)
				throw new InvalidTranslationTemplateSyntaxException("Pluralization template token must end with \";\"");
			
			if(templateToken.charAt(0) != '[')
				throw new InvalidTranslationTemplateSyntaxException("Pluralization template token must start with \"[\"");
			
			int matchingBracketIndex = templateToken.indexOf(']');
			if(matchingBracketIndex == -1)
				throw new InvalidTranslationTemplateSyntaxException("Count range closing bracket is missing");
			
			String rawCountValues = templateToken.substring(1, matchingBracketIndex); //Ignore '[' and ']'
			String rawTranslationValue = templateToken.substring(matchingBracketIndex + 1);
			
			List<TranslationPluralizationTemplate.CountRange> countValues = new LinkedList<>();
			startIndex = 0;
			for(int j = 0;j < rawCountValues.length();j++) {
				char c = rawCountValues.charAt(j);
				if((c >= '0' && c <= '9') || c == '-' || c == '+') {
					if(j < rawCountValues.length() - 1)
						continue;
				}else if(c != ',') {
					throw new InvalidTranslationTemplateSyntaxException("Invalid token in count range");
				}
				
				String rawCountValue = rawCountValues.substring(startIndex, j < rawCountValues.length() - 1?j:(j + 1));
				startIndex = j + 1;
				
				int startCount = -2;
				int endCount = -2;
				int numberStartIndex = 0;
				for(int k = 0;k < rawCountValue.length();k++) {
					c = rawCountValue.charAt(k);
					if(c >= '0' && c <= '9') {
						if(k == rawCountValue.length() - 1) {
							String numberCount = rawCountValue.substring(numberStartIndex, k + 1);
							
							if(startCount == -2) {
								startCount = Integer.parseInt(numberCount);
								endCount = startCount;
							}else if(endCount == -2) {
								endCount = Integer.parseInt(numberCount);
							}else {
								throw new InvalidTranslationTemplateSyntaxException("Too many value in range inside a count range");
							}
						}
						
						continue;
					}
					
					if(c == '-') {
						if(numberStartIndex != 0)
							throw new InvalidTranslationTemplateSyntaxException("Invalid character \"-\" can not be used twice in a range inside a count range");
						
						String numberStartCount = rawCountValue.substring(numberStartIndex, k);
						numberStartIndex = k + 1;
						
						startCount = Integer.parseInt(numberStartCount);
					}else if(c == '+') {
						if(startCount != -2 || endCount != -2 || k < rawCountValue.length() - 1)
							throw new InvalidTranslationTemplateSyntaxException("Invalid character \"+\" can not be used twice or with multiple values in count range");
						
						String numberStartCount = rawCountValue.substring(numberStartIndex, k);
						startCount = Integer.parseInt(numberStartCount);
						
						endCount = -1;
					}
				}
				
				if(startCount == -2 || endCount == -2)
					throw new InvalidTranslationTemplateSyntaxException("Empty count range sequence");
				
				countValues.add(new TranslationPluralizationTemplate.CountRange(startCount, endCount));
			}
			
			templates.add(new TranslationPluralizationTemplate(countValues, rawTranslationValue));
		}
		
		for(TranslationPluralizationTemplate template:templates) {
			for(TranslationPluralizationTemplate.CountRange countRange:template.getCountValues()) {
				if(countRange.isCountInRange(count)) {
					templateMap = new HashMap<>(templateMap);
					templateMap.put("count", count + "");
					
					return formatTranslationTemplate(template.getRawTranslationValue(), templateMap);
				}
			}
		}
		
		throw new InvalidTranslationTemplateSyntaxException("No pluralization for count \"" + count + "\" was defined");
	}
	private static class TranslationPluralizationTemplate {
		private final List<CountRange> countValues;
		private final String rawTranslationValue;
		
		private TranslationPluralizationTemplate(List<CountRange> countValues, String rawTranslationValue) {
			this.countValues = countValues;
			this.rawTranslationValue = rawTranslationValue;
		}
		
		private List<CountRange> getCountValues() {
			return countValues;
		}
		
		private String getRawTranslationValue() {
			return rawTranslationValue;
		}
		
		private static class CountRange {
			private final int startCount;
			/**
			 * If -1: All values >= startCount
			 */
			private final int endCount;
			
			private CountRange(int startCount, int endCount) {
				this.startCount = startCount;
				this.endCount = endCount;
			}
			
			private boolean isCountInRange(int count) {
				return count == startCount || (count > startCount && (endCount == -1 || count <= endCount));
			}
		}
	}
	public static class InvalidTranslationTemplateSyntaxException extends RuntimeException {
		private static final long serialVersionUID = -608364324824905715L;
		
		public InvalidTranslationTemplateSyntaxException(String message) {
			super(message);
		}
	}
}