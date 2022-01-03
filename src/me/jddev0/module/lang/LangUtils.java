package me.jddev0.module.lang;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import me.jddev0.module.lang.LangInterpreter.DataObject;
import me.jddev0.module.lang.LangInterpreter.DataType;

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
	 * @return Might return null
	 */
	public static DataObject combineDataObjects(List<DataObject> dataObjects) {
		dataObjects = new LinkedList<>(dataObjects);
		dataObjects.removeIf(Objects::isNull);
		
		if(dataObjects.size() == 0)
			return null;
		
		if(dataObjects.size() == 1)
			return dataObjects.get(0);
		
		//Remove all void objects
		dataObjects.removeIf(dataObject -> dataObject.getType() == DataType.VOID);
		
		//Return a single void object if every data object was a void object
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
	 * @return Returns the next DataObject (One DataObject or a combined text value DataObject) before the next ARGUMENT_SEPARATOR or the end and removes all used data objects<br>
	 * Might return null
	 */
	public static DataObject getNextArgumentAndRemoveUsedDataObjects(List<DataObject> argumentList, boolean removeArgumentSpearator) {
		List<DataObject> argumentTmpList = new LinkedList<>();
		while(argumentList.size() > 0 && argumentList.get(0).getType() != DataType.ARGUMENT_SEPARATOR)
			argumentTmpList.add(argumentList.remove(0));
		
		if(argumentTmpList.isEmpty())
			argumentTmpList.add(new DataObject().setVoid());
		
		if(removeArgumentSpearator && argumentList.size() > 0)
			argumentList.remove(0); //Remove ARGUMENT_SEPARATOR
		
		return combineDataObjects(argumentTmpList);
	}
	
	/**
	 * @return Returns the index of the matching bracket (Escaped chars will be ignored (escape char: '\')) or -1 if no matching bracket was found
	 */
	public static int getIndexOfMatchingBracket(String string, int startIndex, int endIndex, char openedBracket, char closedBracket) {
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
		return "\\e" + str.replace("\0", "\\0").replace("\n", "\\n").replace("\r", "\\r").
		replace("\f", "\\f").replace(" ", "\\s").replace("\t", "\\t").replace("$", "\\$").replace("&", "\\&").
		replace("#", "\\#").replace(",", "\\,").replace("(", "\\(").replace(")", "\\)").replace("{", "\\{").
		replace("}", "\\}").replace("=", "\\=").replace("<", "\\<").replace(">", "\\>").replace("+", "\\+").
		replace("-", "\\-").replace("/", "\\/").replace("*", "\\*").replace("%", "\\%").replace("|", "\\|").
		replace("~", "\\~").
		
		replace("!", "\\!!").replace(".", "\\!.\\!").replace("null", "nul\\!l").replace("return", "retur\\!n").
		replace("throw", "thro\\!w") + "\\e";
	}
	
	/**
	 * @return Returns true if the backslash at the index index is escaped else false
	 */
	public static boolean isBackshlashAtIndexEscaped(String str, int index) {
		if(str == null || str.length() <= index || index < 0 || str.charAt(index) != '\\')
			return false;
		
		for(int i = index - 1;i >= 0;i--)
			if(str.charAt(i) != '\\')
				return (index - i) % 2 == 0;
		
		return index % 2 == 1;
	}
}