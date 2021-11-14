package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.AbstractSyntaxTree.*;
import me.jddev0.module.lang.AbstractSyntaxTree.ConditionNode.Operator;

/**
 * Lang-Module<br>
 * Lang interpreter for interpreting AST created by LangParser
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangInterpreter {
	static final String VERSION = "v1.0.0";
	static final Random RAN = new Random();
	
	final LangParser parser = new LangParser();
	final LangPatterns patterns = new LangPatterns();
	
	String langPath;
	TerminalIO term;
	LangPlatformAPI langPlatformAPI;
	
	//Fields for return node
	/**
	 * Will be set to true for returning a value
	 */
	private boolean stopParsingFlag;
	private DataObject returnedOrThrowedValue;
	private boolean isThrowedValue;
	/**
	 * <DATA_ID (of function), <to, from>><br>
	 * Data tmp for "func.copyAfterFP"
	 */
	Map<Integer, Map<String, String>> copyAfterFP = new HashMap<>();
	
	//Execution flags
	/**
	 * Allow terminal function to redirect to standard input, output, or error if no terminal is available
	 */
	boolean allowTermRedirect = true;
	
	//DATA
	Map<Integer, Data> data = new HashMap<>();
	
	//Predefined functions & linker functions (= Predefined functions)
	private Map<String, LangPredefinedFunctionObject> funcs = new HashMap<>();
	{
		LangPredefinedFunctions predefinedFunctions = new LangPredefinedFunctions(this);
		predefinedFunctions.addPredefinedFunctions(funcs);
		predefinedFunctions.addLinkerFunctions(funcs);
	}
	
	public LangInterpreter(String langPath, TerminalIO term, LangPlatformAPI langPlatformAPI) {
		this(langPath, term, langPlatformAPI, null);
	}
	public LangInterpreter(String langPath, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) {
		this.langPath = langPath;
		this.term = term;
		this.langPlatformAPI = langPlatformAPI;
		
		createDataMap(0, langArgs);
	}
	
	public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
		return parser.parseLines(lines);
	}
	
	public void interpretAST(AbstractSyntaxTree ast) {
		interpretAST(ast, 0);
	}
	
	public void interpretLines(BufferedReader lines) throws IOException {
		interpretLines(lines, 0);
	}
	
	public Map<Integer, Data> getData() {
		return new HashMap<>(data);
	}
	
	boolean interpretCondition(ConditionNode node, final int DATA_ID) {
		return interpretConditionNode(node, DATA_ID).getBoolean();
	}
	
	void interpretLines(BufferedReader lines, final int DATA_ID) throws IOException {
		interpretAST(parseLines(lines), DATA_ID);
	}
	
	void interpretAST(AbstractSyntaxTree ast, final int DATA_ID) {
		if(ast == null)
			return;
		
		for(Node node:ast) {
			if(stopParsingFlag)
				return;
			
			interpretNode(node, DATA_ID);
		}
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretNode(Node node, final int DATA_ID) {
		if(node == null) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			return null;
		}
		
		try {
			switch(node.getNodeType()) {
				case UNPROCESSED_VARIABLE_NAME:
					return interpretNode(processUnprocessedVariableNameNode((UnprocessedVariableNameNode)node, DATA_ID), DATA_ID);
					
				case FUNCTION_CALL_PREVIOUS_NODE_VALUE:
					return interpretNode(processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)node, null, DATA_ID), DATA_ID);
				
				case LIST:
					//Interpret a group of nodes
					return interpretListNode((ListNode)node, DATA_ID);
				
				case CHAR_VALUE:
				case TEXT_VALUE:
				case INT_VALUE:
				case LONG_VALUE:
				case FLOAT_VALUE:
				case DOUBLE_VALUE:
				case NULL_VALUE:
				case VOID_VALUE:
					return interpretValueNode((ValueNode)node, DATA_ID);
				
				case PARSING_ERROR:
					return interpretParsingErrorNode((ParsingErrorNode)node, DATA_ID);
				
				case IF_STATEMENT:
					return new DataObject().setBoolean(interpretIfStatementNode((IfStatementNode)node, DATA_ID));
				
				case IF_STATEMENT_PART_ELSE:
				case IF_STATEMENT_PART_IF:
					return new DataObject().setBoolean(interpretIfStatementPartNode((IfStatementPartNode)node, DATA_ID));
				
				case CONDITION:
					return interpretConditionNode((ConditionNode)node, DATA_ID);
				
				case RETURN:
					interpretReturnNode((ReturnNode)node, DATA_ID);
					return null;
				
				case THROW:
					interpretThrowNode((ThrowNode)node, DATA_ID);
					return null;
				
				case ASSIGNMENT:
					return interpretAssignmentNode((AssignmentNode)node, DATA_ID);
				
				case VARIABLE_NAME:
					return interpretVariableNameNode((VariableNameNode)node, DATA_ID);
				
				case ESCAPE_SEQUENCE:
					return interpretEscapeSequenceNode((EscapeSequenceNode)node, DATA_ID);
				
				case ARGUMENT_SEPARATOR:
					return interpretArgumentSeparatotNode((ArgumentSeparatorNode)node, DATA_ID);
				
				case FUNCTION_CALL:
					return interpretFunctionCallNode((FunctionCallNode)node, DATA_ID);
				
				case FUNCTION_DEFINITION:
					return interpretFunctionDefinitionNode((FunctionDefinitionNode)node, DATA_ID);
				
				case GENERAL:
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return null;
	}
	
	/**
	 * @param variablePrefixAppendAfterSearch If no part of the variable name matched an existing variable, the variable prefix will be added to the returned TextValueNode<br>
	 *                                             (e.g. "func.abc" ("func." is not part of the variableNames in the set))
	 * @param supportsPointerDereferencingAndReferencing If true, this node will return pointer reference or a dereferenced pointers as VariableNameNode<br>
	 *                                   (e.g. $[abc] is not in variableNames, but $abc is -> $[abc] will return a VariableNameNode)
	 */
	private Node convertVariableNameToVariableNameNodeOrComposition(String variableName, Set<String> variableNames,
	String variablePrefixAppendAfterSearch, final boolean supportsPointerDereferencingAndReferencing) {
		Optional<String> optionalReturnedVariableName = variableNames.stream().filter(varName -> {
			return variableName.startsWith(varName);
		}).sorted((s0, s1) -> { //Sort keySet from large to small length (e.g.: $abcd and $abc and $ab)
			if(s0.length() == s1.length())
				return 0;
			
			return (s0.length() < s1.length())?1:-1;
		}).findFirst();
		
		if(!optionalReturnedVariableName.isPresent()) {
			if(supportsPointerDereferencingAndReferencing) {
				String dereferences = null;
				int startIndex = -1;
				String modifiedVariableName = variableName;
				Node returnedNode = null;
				String text = null;
				if(variableName.contains("*")) { //Check referenced variable name
					startIndex = variableName.indexOf('*');
					int endIndex = variableName.lastIndexOf('*') + 1;
					if(endIndex >= variableName.length())
						return new TextValueNode(variablePrefixAppendAfterSearch + variableName);
					
					dereferences = variableName.substring(startIndex, endIndex);
					modifiedVariableName = variableName.substring(0, startIndex) + variableName.substring(endIndex);
					
					if(!modifiedVariableName.contains("[") && !modifiedVariableName.contains("]"))
						returnedNode = convertVariableNameToVariableNameNodeOrComposition(modifiedVariableName, variableNames, "", supportsPointerDereferencingAndReferencing);
				}
				
				if(modifiedVariableName.contains("[") && modifiedVariableName.contains("]")) { //Check dereferenced variable name
					int indexOpeningBracket = modifiedVariableName.indexOf("[");
					int indexMatchingBracket = LangUtils.getIndexOfMatchingBracket(modifiedVariableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
					if(indexMatchingBracket != -1) {
						if(indexMatchingBracket != modifiedVariableName.length() - 1) {
							text = modifiedVariableName.substring(indexMatchingBracket + 1);
							modifiedVariableName = modifiedVariableName.substring(0, indexMatchingBracket + 1);
						}
						
						returnedNode = convertVariableNameToVariableNameNodeOrComposition(modifiedVariableName.substring(0, indexOpeningBracket) +
						modifiedVariableName.substring(indexOpeningBracket + 1, indexMatchingBracket), variableNames, "", supportsPointerDereferencingAndReferencing);
					}
				}
				
				if(returnedNode != null) {
					if(dereferences != null)
						modifiedVariableName = modifiedVariableName.substring(0, startIndex) + dereferences + modifiedVariableName.substring(startIndex);
					switch(returnedNode.getNodeType()) {
						case VARIABLE_NAME: //Variable was found without additional text -> valid pointer reference
							if(text == null)
								return new VariableNameNode(variablePrefixAppendAfterSearch + variableName);
							
							//Variable composition
							List<Node> nodes = new ArrayList<>();
							nodes.add(new VariableNameNode(variablePrefixAppendAfterSearch + modifiedVariableName));
							nodes.add(new TextValueNode(text));
							return new ListNode(nodes);
						
						case LIST: //Variable was found with additional text -> no valid pointer reference
						case TEXT_VALUE: //Variable was not found
						default: //Default should never be reached
							return new TextValueNode(variablePrefixAppendAfterSearch + variableName);
					}
				}
			}
			
			return new TextValueNode(variablePrefixAppendAfterSearch + variableName);
		}
		
		String returendVariableName = optionalReturnedVariableName.get();
		if(returendVariableName.length() == variableName.length())
			return new VariableNameNode(variablePrefixAppendAfterSearch + variableName);
		
		//Variable composition
		List<Node> nodes = new ArrayList<>();
		nodes.add(new VariableNameNode(variablePrefixAppendAfterSearch + returendVariableName)); //Add matching part of variable as VariableNameNode
		nodes.add(new TextValueNode(variableName.substring(returendVariableName.length()))); //Add composition part as TextValueNode
		return new ListNode(nodes);
	}
	private Node processUnprocessedVariableNameNode(UnprocessedVariableNameNode node, final int DATA_ID) {
		String variableName = node.getVariableName();
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp."))
			return convertVariableNameToVariableNameNodeOrComposition(variableName, data.get(DATA_ID).var.keySet(), "", variableName.startsWith("$"));
		
		final boolean isLinkerFunction;
		if(variableName.startsWith("func.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(5);
		}else if(variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(7);
		}else {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			return new TextValueNode(variableName);
		}
		
		return convertVariableNameToVariableNameNodeOrComposition(variableName, funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).map(Entry<String, LangPredefinedFunctionObject>::getKey).collect(Collectors.toSet()), isLinkerFunction?"linker.":"func.", false);
	}
	
	private Node processFunctionCallPreviousNodeValueNode(FunctionCallPreviousNodeValueNode node, DataObject previousValue, final int DATA_ID) {
		if(previousValue != null && previousValue.getType() == DataType.FUNCTION_POINTER)
			return node;
		
		//Previous node value wasn't a function -> return children of node in between "(" and ")" as ListNode
		List<Node> nodes = new ArrayList<>();
		nodes.add(new TextValueNode("("));
		nodes.addAll(node.getChildren());
		nodes.add(new TextValueNode(")"));
		return new ListNode(nodes);
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretListNode(ListNode node, final int DATA_ID) {
		List<DataObject> dataObjects = new LinkedList<>();
		DataObject previousDataObject = null;
		for(Node childNode:node.getChildren()) {
			if(childNode.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
				try {
					Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)childNode, previousDataObject, DATA_ID);
					if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
						dataObjects.remove(dataObjects.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
						dataObjects.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, DATA_ID));
					}else {
						dataObjects.add(interpretNode(ret, DATA_ID));
					}
				}catch(ClassCastException e) {
					dataObjects.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID));
				}
				
				previousDataObject = dataObjects.get(dataObjects.size() - 1);
				
				continue;
			}
			
			DataObject ret = interpretNode(childNode, DATA_ID);
			if(ret != null)
				dataObjects.add(ret);
			
			previousDataObject = ret;
		}
		
		return LangUtils.combineDataObjects(dataObjects);
	}
	
	private DataObject interpretValueNode(ValueNode node, final int DATA_ID) {
		try {
			switch(node.getNodeType()) {
				case CHAR_VALUE:
					return new DataObject().setChar(((CharValueNode)node).getChar());
				case TEXT_VALUE:
					return new DataObject().setText(((TextValueNode)node).getText());
				case INT_VALUE:
					return new DataObject().setInt(((IntValueNode)node).getInt());
				case LONG_VALUE:
					return new DataObject().setLong(((LongValueNode)node).getLong());
				case FLOAT_VALUE:
					return new DataObject().setFloat(((FloatValueNode)node).getFloat());
				case DOUBLE_VALUE:
					return new DataObject().setDouble(((DoubleValueNode)node).getDouble());
				case NULL_VALUE:
					return new DataObject().setNull();
				case VOID_VALUE:
					return new DataObject().setVoid();
				
				default:
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return new DataObject().setError(new ErrorObject(InterpretingError.INVALID_AST_NODE));
	}
	
	private DataObject interpretParsingErrorNode(ParsingErrorNode node, final int DATA_ID) {
		InterpretingError error = null;
		
		switch(node.getError()) {
			case BRACKET_MISMATCH:
				error = InterpretingError.BRACKET_MISMATCH;
				break;
			
			case CONDITION_MISSING:
				error = InterpretingError.IF_CONDITION_MISSING;
				break;
			
			case EOF:
				error = InterpretingError.EOF;
				break;
		}
		
		if(error == null)
			error = InterpretingError.INVALID_AST_NODE;
		return new DataObject().setError(new ErrorObject(error));
	}
	
	/**
	 * @return Returns true if any condition was true and if any block was executed
	 */
	private boolean interpretIfStatementNode(IfStatementNode node, final int DATA_ID) {
		List<IfStatementPartNode> ifPartNodes = node.getIfStatementPartNodes();
		if(ifPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			return false;
		}
		
		for(IfStatementPartNode ifPartNode:ifPartNodes)
			if(interpretIfStatementPartNode(ifPartNode, DATA_ID))
				return true;
		
		return false;
	}
	
	/**
	 * @return Returns true if condition was true and if block was executed
	 */
	private boolean interpretIfStatementPartNode(IfStatementPartNode node, final int DATA_ID) {
		try {
			switch(node.getNodeType()) {
				case IF_STATEMENT_PART_IF:
					if(!interpretConditionNode(((IfStatementPartIfNode)node).getCondition(), DATA_ID).getBoolean())
						return false;
				case IF_STATEMENT_PART_ELSE:
					interpretAST(node.getIfBody(), DATA_ID);
					return true;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return false;
	}
	
	private DataObject interpretConditionNode(ConditionNode node, final int DATA_ID) {
		boolean conditionOuput = false;
		DataObject leftSideOperand = interpretNode(node.getLeftSideOperand(), DATA_ID);
		DataObject rightSideOperand = node.getOperator().isUnary() || node.getOperator() == Operator.AND ||
				node.getOperator() == Operator.OR?null:interpretNode(node.getRightSideOperand(), DATA_ID);
		if(leftSideOperand == null || (!node.getOperator().isUnary() && node.getOperator() != Operator.AND &&
				node.getOperator() != Operator.OR && rightSideOperand == null))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		switch(node.getOperator()) {
			//Unary (Logical operators)
			case NON:
			case NOT:
				conditionOuput = leftSideOperand.getBoolean();
				
				if(node.getOperator() == ConditionNode.Operator.NOT)
					conditionOuput = !conditionOuput;
				break;
			
			//Binary (Logical operators)
			case AND:
				boolean leftSideOperandBoolean = leftSideOperand.getBoolean();
				if(leftSideOperandBoolean) {
					rightSideOperand = interpretNode(node.getRightSideOperand(), DATA_ID);
					if(rightSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
					conditionOuput = rightSideOperand.getBoolean();
				}else {
					conditionOuput = false;
				}
				break;
			case OR:
				leftSideOperandBoolean = leftSideOperand.getBoolean();
				if(leftSideOperandBoolean) {
					conditionOuput = true;
				}else {
					rightSideOperand = interpretNode(node.getRightSideOperand(), DATA_ID);
					if(rightSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
					conditionOuput = rightSideOperand.getBoolean();
				}
				break;
			
			//Binary (Comparison operators)
			case EQUALS:
			case NOT_EQUALS:
				conditionOuput = leftSideOperand.isEquals(rightSideOperand);
				
				if(node.getOperator() == ConditionNode.Operator.NOT_EQUALS)
					conditionOuput = !conditionOuput;
				break;
			case STRICT_EQUALS:
			case STRICT_NOT_EQUALS:
				conditionOuput = leftSideOperand.isStrictEquals(rightSideOperand);
				
				if(node.getOperator() == ConditionNode.Operator.STRICT_NOT_EQUALS)
					conditionOuput = !conditionOuput;
				break;
			case LESS_THAN:
				conditionOuput = leftSideOperand.isLessThan(rightSideOperand);
				break;
			case GREATER_THAN:
				conditionOuput = leftSideOperand.isGreaterThan(rightSideOperand);
				break;
			case LESS_THAN_OR_EQUALS:
				conditionOuput = leftSideOperand.isLessThanOrEquals(rightSideOperand);
				break;
			case GREATER_THAN_OR_EQUALS:
				conditionOuput = leftSideOperand.isGreaterThanOrEquals(rightSideOperand);
				break;
		}
		
		return new DataObject().setBoolean(conditionOuput);
	}
	
	private void interpretReturnNode(ReturnNode node, final int DATA_ID) {
		Node returnValueNode = node.getReturnValue();
		
		returnedOrThrowedValue = returnValueNode == null?new DataObject().setVoid():interpretNode(returnValueNode, DATA_ID);
		isThrowedValue = false;
		stopParsingFlag = true;
	}
	
	private void interpretThrowNode(ThrowNode node, final int DATA_ID) {
		Node throwValueNode = node.getThrowValue();
		
		DataObject errorObject = interpretNode(throwValueNode, DATA_ID);
		if(errorObject == null || errorObject.getType() != DataType.ERROR)
			returnedOrThrowedValue = new DataObject().setError(new ErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE));
		else
			returnedOrThrowedValue = errorObject;
		isThrowedValue = true;
		stopParsingFlag = true;
	}
	
	private void interpretLangDataAndCompilerFlags(String langDataCompilerFlag, DataObject value) {
		if(value == null)
			value = new DataObject().setNull();
		
		switch(langDataCompilerFlag) {
			//Data
			case "lang.version":
				String langVer = value.getText();
				if(!langVer.equals(VERSION)) {
					if(term == null) {
						if(VERSION.compareTo(langVer) > 0)
							System.err.println("Lang file's version is older than this version! Maybe the lang file won't be compiled right!");
						else
							System.err.println("Lang file's version is newer than this version! Maybe the lang file won't be compiled right!");
					}else {
						if(VERSION.compareTo(langVer) > 0)
							term.logln(Level.WARNING, "Lang file's version is older than this version! Maybe the lang file won't be compiled right!", LangInterpreter.class);
						else
							term.logln(Level.ERROR, "Lang file's version is newer than this version! Maybe the lang file won't be compiled right!", LangInterpreter.class);
					}
				}
				break;
			
			case "lang.name":
				//Nothing do to
				break;
			
			//Flags
			case "lang.allowTermRedirect":
				Number number = value.getNumber();
				if(number == null) {
					if(term == null)
						System.err.println("Invalid Data Type for lang.allowTermRedirect flag!");
					else
						term.logln(Level.ERROR, "Invalid Data Type for lang.allowTermRedirect flag!", LangInterpreter.class);
					
					return;
				}
				allowTermRedirect = number.intValue() != 0;
				break;
		}
	}
	private DataObject interpretAssignmentNode(AssignmentNode node, final int DATA_ID) {
		DataObject rvalue = interpretNode(node.getRvalue(), DATA_ID);
		if(rvalue == null)
			rvalue = new DataObject().setNull();
		
		Node lvalueNode = node.getLvalue();
		if(lvalueNode == null)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		try {
			switch(lvalueNode.getNodeType()) {
				//Variable assignment
				case UNPROCESSED_VARIABLE_NAME:
					UnprocessedVariableNameNode variableNameNode = (UnprocessedVariableNameNode)lvalueNode;
					String variableName = variableNameNode.getVariableName();
					if(patterns.matches(variableName, LangPatterns.VAR_NAME_FULL) || patterns.matches(variableName, LangPatterns.VAR_NAME_PTR_AND_DEREFERENCE)) {
						int indexOpeningBracket = variableName.indexOf("[");
						int indexMatchingBracket = indexOpeningBracket == -1?-1:LangUtils.getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
						if(indexOpeningBracket == -1 || indexMatchingBracket == variableName.length() - 1) {
							if(rvalue.getType() != DataType.NULL &&
							((variableName.startsWith("&") && rvalue.getType() != DataType.ARRAY) ||
							(variableName.startsWith("fp.") && rvalue.getType() != DataType.FUNCTION_POINTER))) {
								//Only set errno to "INCOMPATIBLE_DATA_TYPE" if rvalue has not already set errno
								InterpretingError error = getAndClearErrnoErrorObject(DATA_ID);
								if(rvalue.getType() == DataType.ERROR && rvalue.getError().getErrno() == error.getErrorCode())
									return setErrnoErrorObject(error, DATA_ID);
								
								return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, DATA_ID);
							}
							
							if(rvalue.getVariableName() != null && rvalue.getVariableName().startsWith("&LANG_"))
								return setErrnoErrorObject(InterpretingError.LANG_ARRAYS_COPY, DATA_ID);
							
							DataObject lvalue = getOrCreateDataObjectFromVariableName(variableName, false, true, true, DATA_ID);
							if(lvalue != null) {
								if(lvalue.getVariableName() == null || (!variableName.contains("*") && !lvalue.getVariableName().equals(variableName)))
									return lvalue; //Forward error from getOrCreateDataObjectFromVariableName()
								
								if(lvalue.isFinalData() || lvalue.getVariableName().startsWith("$LANG_") || lvalue.getVariableName().startsWith("&LANG_"))
									return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
								
								lvalue.setData(rvalue);
								break;
							}
						}
					}
					//Fall through to "Lang translation" if variableName is not valid
				
				//Lang translation
				case ASSIGNMENT:
				case CHAR_VALUE:
				case CONDITION:
				case DOUBLE_VALUE:
				case ESCAPE_SEQUENCE:
				case FLOAT_VALUE:
				case FUNCTION_CALL:
				case FUNCTION_CALL_PREVIOUS_NODE_VALUE:
				case FUNCTION_DEFINITION:
				case IF_STATEMENT:
				case IF_STATEMENT_PART_ELSE:
				case IF_STATEMENT_PART_IF:
				case INT_VALUE:
				case LIST:
				case LONG_VALUE:
				case NULL_VALUE:
				case PARSING_ERROR:
				case RETURN:
				case THROW:
				case TEXT_VALUE:
				case VARIABLE_NAME:
				case VOID_VALUE:
					DataObject lvalue = interpretNode(lvalueNode, DATA_ID);
					if(lvalue == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
					
					String langRequest = lvalue.getText();
					if(langRequest.startsWith("lang."))
						interpretLangDataAndCompilerFlags(langRequest, rvalue);
					
					data.get(DATA_ID).lang.put(langRequest, rvalue.getText());
					break;
					
				case GENERAL:
				case ARGUMENT_SEPARATOR:
					return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}catch(ClassCastException e) {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return rvalue;
	}
	
	/**
	 * Will create a variable if doesn't exist or returns an error object, or returns null if shouldCreateDataObject is set to false and variable doesn't exist
	 * @param supportsPointerReferencing If true, this node will return pointer reference as DataObject<br>
	 *                                   (e.g. $[abc] is not in variableNames, but $abc is -> $[abc] will return a DataObject)
	 */
	private DataObject getOrCreateDataObjectFromVariableName(String variableName, boolean supportsPointerReferencing,
	boolean supportsPointerDereferencing, boolean shouldCreateDataObject, final int DATA_ID) {
		DataObject ret = data.get(DATA_ID).var.get(variableName);
		if(ret != null)
			return ret;
		
		if(supportsPointerDereferencing && variableName.contains("*")) {
			int index = variableName.indexOf('*');
			String referencedVariableName = variableName.substring(0, index) + variableName.substring(index + 1);
			DataObject referencedVariable = getOrCreateDataObjectFromVariableName(referencedVariableName, supportsPointerReferencing, true, false, DATA_ID);
			if(referencedVariable == null)
				return setErrnoErrorObject(InterpretingError.INVALID_PTR, DATA_ID);
			
			if(referencedVariable.getType() == DataType.VAR_POINTER)
				return referencedVariable.getVarPointer().getVar();
			
			return new DataObject().setNull(); //If no var pointer was dereferenced, return null
		}
		
		if(supportsPointerReferencing && variableName.contains("[") && variableName.contains("]")) { //Check dereferenced variable name
			int indexOpeningBracket = variableName.indexOf("[");
			int indexMatchingBracket = LangUtils.getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
			if(indexMatchingBracket != variableName.length() - 1)
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
			
			String dereferencedVariableName = variableName.substring(0, indexOpeningBracket) + variableName.substring(indexOpeningBracket + 1, indexMatchingBracket);
			DataObject dereferencedVariable = getOrCreateDataObjectFromVariableName(dereferencedVariableName, true, false, false, DATA_ID);
			if(dereferencedVariable != null)
				return new DataObject().setVarPointer(new VarPointerObject(dereferencedVariable));
			
			//VarPointer redirection (e.g.: create "$[...]" as variable) -> at method end
		}
		
		if(!shouldCreateDataObject)
			return null;
		
		//Variable creation if possible
		if(patterns.matches(variableName, LangPatterns.LANG_VAR))
			return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
		
		DataObject dataObject = new DataObject().setVariableName(variableName);
		data.get(DATA_ID).var.put(variableName, dataObject);
		return dataObject;
	}
	/**
	 * Will create a variable if doesn't exist or returns an error object
	 */
	private DataObject interpretVariableNameNode(VariableNameNode node, final int DATA_ID) {
		String variableName = node.getVariableName();
		
		if(!patterns.matches(variableName, LangPatterns.VAR_NAME_FULL_WITH_FUNCS) &&
		!patterns.matches(variableName, LangPatterns.VAR_NAME_PTR_AND_DEREFERENCE))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp."))
			return getOrCreateDataObjectFromVariableName(variableName, variableName.startsWith("$"), variableName.startsWith("$"),
			true, DATA_ID);
		
		final boolean isLinkerFunction;
		if(variableName.startsWith("func.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(5);
		}else if(variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(7);
		}else {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		final String variableNameCopy = variableName;
		Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).filter(entry -> {
			return variableNameCopy.equals(entry.getKey());
		}).findFirst();
		
		if(!ret.isPresent())
			return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, DATA_ID);
		
		return new DataObject().setFunctionPointer(new FunctionPointerObject(ret.get().getValue())).setVariableName(node.getVariableName());
	}
	
	/**
	 * @return Will return null for ("\!" escape sequence)
	 */
	private DataObject interpretEscapeSequenceNode(EscapeSequenceNode node, final int DATA_ID) {
		switch(node.getEscapeSequenceChar()) {
			case '0':
				return new DataObject().setChar('\0');
			case 'n':
				return new DataObject().setChar('\n');
			case 'r':
				return new DataObject().setChar('\r');
			case 'f':
				return new DataObject().setChar('\f');
			case 's':
				return new DataObject().setChar(' ');
			case 'e':
				return new DataObject("");
			case 'b':
				return new DataObject().setChar('\b');
			case 't':
				return new DataObject().setChar('\t');
			case '$':
				return new DataObject().setChar('$');
			case '&':
				return new DataObject().setChar('&');
			case '#':
				return new DataObject().setChar('#');
			case ',':
				return new DataObject().setChar(',');
			case '(':
				return new DataObject().setChar('(');
			case ')':
				return new DataObject().setChar(')');
			case '{':
				return new DataObject().setChar('{');
			case '}':
				return new DataObject().setChar('}');
			case '=':
				return new DataObject().setChar('=');
			case '<':
				return new DataObject().setChar('<');
			case '>':
				return new DataObject().setChar('>');
			case '!':
				return null;
			case '\\':
				return new DataObject().setChar('\\');
			
			//If no escape sequence: Remove "\" anyway
			default:
				return new DataObject().setChar(node.getEscapeSequenceChar());
		}
	}
	
	private DataObject interpretArgumentSeparatotNode(ArgumentSeparatorNode node, final int DATA_ID) {
		return new DataObject().setArgumentSeparator(node.getOriginalText());
	}
	
	DataObject getAndResetReturnValue(final int DATA_ID) {
		DataObject retTmp = returnedOrThrowedValue;
		returnedOrThrowedValue = null;
		if(isThrowedValue && DATA_ID > -1)
			setErrno(retTmp.getError().getInterprettingError(), DATA_ID);
		isThrowedValue = false;
		stopParsingFlag = false;
		return retTmp;
	}
	void executeAndClearCopyAfterFP(final int DATA_ID_TO, final int DATA_ID_FROM) {
		//Add copyValue after call
		copyAfterFP.get(DATA_ID_FROM).forEach((to, from) -> {
			if(from != null && to != null) {
				DataObject valFrom = data.get(DATA_ID_FROM).var.get(from);
				if(valFrom != null && valFrom.getType() != DataType.NULL) {
					if(to.startsWith("fp.") || to.startsWith("$") || to.startsWith("&")) {
						DataObject dataTo = data.get(DATA_ID_TO).var.get(to);
						 //$LANG and final vars can't be change
						if(to.startsWith("$LANG_") || to.startsWith("&LANG_") || (dataTo != null && dataTo.isFinalData())) {
							setErrno(InterpretingError.FINAL_VAR_CHANGE, DATA_ID_TO);
							return;
						}
						
						if(patterns.matches(from, LangPatterns.LANG_VAR_ARRAY)) {
							setErrno(InterpretingError.LANG_ARRAYS_COPY, DATA_ID_TO);
							return;
						}
						
						if(!patterns.matches(to, LangPatterns.VAR_NAME) && !patterns.matches(to, LangPatterns.VAR_NAME_PTR)) {
							setErrno(InterpretingError.INVALID_PTR, DATA_ID_TO);
							return;
						}
						int indexOpeningBracket = to.indexOf("[");
						int indexMatchingBracket = indexOpeningBracket == -1?-1:LangUtils.getIndexOfMatchingBracket(to, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
						if(indexOpeningBracket != -1 && indexMatchingBracket != to.length() - 1) {
							setErrno(InterpretingError.INVALID_PTR, DATA_ID_TO);
							return;
						}
						
						if(valFrom.getType() != DataType.NULL &&
						((to.startsWith("&") && valFrom.getType() != DataType.ARRAY) ||
						(to.startsWith("fp.") && valFrom.getType() != DataType.FUNCTION_POINTER))) {
							setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, DATA_ID_TO);
							return;
						}
						
						getOrCreateDataObjectFromVariableName(to, false, false, true, DATA_ID_TO).setData(valFrom);
						return;
					}
				}
			}
			
			setErrno(InterpretingError.INVALID_ARGUMENTS, DATA_ID_TO);
		});
		
		//Clear copyAfterFP
		copyAfterFP.remove(DATA_ID_FROM);
	}
	DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int DATA_ID) {
		switch(fp.getFunctionPointerType()) {
			case FunctionPointerObject.NORMAL:
				List<VariableNameNode> parameterList = fp.getParameterList();
				AbstractSyntaxTree functionBody = fp.getFunctionBody();
				if(parameterList == null || functionBody == null)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
				
				final int NEW_DATA_ID = DATA_ID + 1;
				
				//Add variables and local variables
				createDataMap(NEW_DATA_ID);
				//Copies must not be final
				data.get(DATA_ID).var.forEach((key, val) -> {
					if(!key.startsWith("$LANG_") && !key.startsWith("&LANG_"))
						data.get(NEW_DATA_ID).var.put(key, new DataObject(val).setVariableName(val.getVariableName()));
				});
				//Initialize copyAfterFP
				copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
				
				//Set arguments
				DataObject lastDataObject = null;
				Iterator<VariableNameNode> parameterListIterator = parameterList.iterator();
				while(parameterListIterator.hasNext()) {
					VariableNameNode parameter = parameterListIterator.next();
					String variableName = parameter.getVariableName();
					if(!parameterListIterator.hasNext() && !patterns.matches(variableName, LangPatterns.LANG_VAR) &&
					patterns.matches(variableName, LangPatterns.FUNC_CALL_VAR_ARGS)) {
						//Varargs (only the last parameter can be a varargs parameter)
						variableName = variableName.substring(0, variableName.length() - 3); //Remove "..."
						if(variableName.startsWith("$")) {
							//Text varargs
							DataObject dataObject = LangUtils.combineDataObjects(argumentValueList);
							data.get(NEW_DATA_ID).var.put(variableName, new DataObject(dataObject != null?dataObject.getText():
							new DataObject().setVoid().getText()).setVariableName(variableName));
						}else {
							//Array varargs
							List<DataObject> varArgsTmpList = new LinkedList<>();
							while(argumentValueList.size() > 0)
								varArgsTmpList.add(LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true));
							
							data.get(NEW_DATA_ID).var.put(variableName, new DataObject().setArray(varArgsTmpList.
							toArray(new DataObject[0])).setVariableName(variableName));
						}
						
						break;
					}
					
					if(patterns.matches(variableName, LangPatterns.FUNC_CALL_CALL_BY_PTR) && !patterns.matches(variableName,
					LangPatterns.FUNC_CALL_CALL_BY_PTR_LANG_VAR)) {
						//Call by pointer
						variableName = "$" + variableName.substring(2, variableName.length() - 1); //Remove '[' and ']' from variable name
						if(argumentValueList.size() > 0)
							lastDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true);
						else if(lastDataObject == null)
							lastDataObject = new DataObject().setVoid();
						data.get(NEW_DATA_ID).var.put(variableName, new DataObject().setVarPointer(new VarPointerObject(lastDataObject)).setVariableName(variableName));
						
						continue;
					}
					
					if(!patterns.matches(variableName, LangPatterns.VAR_NAME) || patterns.matches(variableName, LangPatterns.LANG_VAR)) {
						setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
						
						continue;
					}
					
					if(argumentValueList.size() > 0)
						lastDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true);
					else if(lastDataObject == null)
						lastDataObject = new DataObject().setVoid();
					
					if(lastDataObject.getVariableName() != null && patterns.matches(lastDataObject.getVariableName(), LangPatterns.LANG_VAR_ARRAY)) {
						setErrno(InterpretingError.LANG_ARRAYS_COPY, DATA_ID);
						
						continue;
					}
					
					if(lastDataObject.getType() != DataType.NULL &&
					((variableName.startsWith("&") && lastDataObject.getType() != DataType.ARRAY) ||
					(variableName.startsWith("fp.") && lastDataObject.getType() != DataType.FUNCTION_POINTER))) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, DATA_ID);
						
						continue;
					}
					
					data.get(NEW_DATA_ID).var.put(variableName, new DataObject(lastDataObject).setVariableName(variableName));
				}
				
				//Call function
				interpretAST(functionBody, NEW_DATA_ID);
				
				//Add lang after call
				data.get(DATA_ID).lang.putAll(data.get(NEW_DATA_ID).lang);
				
				executeAndClearCopyAfterFP(DATA_ID, NEW_DATA_ID);
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				DataObject retTmp = getAndResetReturnValue(DATA_ID);
				return retTmp == null?new DataObject().setVoid():retTmp;
			
			case FunctionPointerObject.PREDEFINED:
				LangPredefinedFunctionObject function = fp.getPredefinedFunction();
				if(function == null)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
				if(function.isDeprecated()) {
					if(term == null)
						System.err.printf("Use of deprecated function \"%s\", this function won't be supported in \"%s\"!\n%s", functionName, function.
						getDeprecatedRemoveVersion() == null?"the future":function.getDeprecatedRemoveVersion(), (function.
						getDeprecatedReplacementFunction() == null?"":("Use \"" + function.getDeprecatedReplacementFunction() + "\" instead!\n")));
					else
						term.logf(Level.WARNING, "Use of deprecated function \"%s\", this function won't be supported in \"%s\"!\n%s", LangInterpreter.class,
						functionName, function.getDeprecatedRemoveVersion() == null?"the future":function.getDeprecatedRemoveVersion(),
						(function.getDeprecatedReplacementFunction() == null?"":("Use \"" + function.getDeprecatedReplacementFunction() + "\" instead!\n")));
				}
				DataObject ret = function.callFunc(argumentValueList, DATA_ID);
				if(function.isDeprecated())
					setErrno(InterpretingError.DEPRECATED_FUNC_CALL, DATA_ID);
				return ret == null?new DataObject().setVoid():ret;
			
			case FunctionPointerObject.EXTERNAL:
				LangExternalFunctionObject externalFunction = fp.getExternalFunction();
				if(externalFunction == null)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
				ret = externalFunction.callFunc(argumentValueList, DATA_ID);
				return ret == null?new DataObject().setVoid():ret;
			
			default:
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
		}
	}
	private DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList, final int DATA_ID) {
		List<DataObject> argumentValueList = new LinkedList<>();
		DataObject previousDataObject = null;
		for(Node argument:argumentList) {
			if(argument.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
				try {
					Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)argument, previousDataObject, DATA_ID);
					if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
						argumentValueList.remove(argumentValueList.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
						argumentValueList.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, DATA_ID));
					}else {
						argumentValueList.add(interpretNode(ret, DATA_ID));
					}
				}catch(ClassCastException e) {
					argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID));
				}
				
				previousDataObject = argumentValueList.get(argumentValueList.size() - 1);
				
				continue;
			}
			
			DataObject argumentValue = interpretNode(argument, DATA_ID);
			if(argumentValue == null) {
				previousDataObject = null;
				
				continue;
			}
			
			argumentValueList.add(argumentValue);
			previousDataObject = argumentValue;
		}
		
		return callFunctionPointer(fp, functionName, argumentValueList, DATA_ID);
	}
	/**
	 * @return Will return void data for non return value functions
	 */
	private DataObject interpretFunctionCallNode(FunctionCallNode node, final int DATA_ID) {
		String functionName = node.getFunctionName();
		FunctionPointerObject fp;
		if(patterns.matches(functionName, LangPatterns.FUNC_NAME)) {
			final boolean isLinkerFunction;
			if(functionName.startsWith("func.")) {
				isLinkerFunction = false;
				
				functionName = functionName.substring(5);
			}else if(functionName.startsWith("linker.")) {
				isLinkerFunction = true;
				
				functionName = functionName.substring(7);
			}else {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
			
			final String functionNameCopy = functionName;
			Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
				return entry.getValue().isLinkerFunction() == isLinkerFunction && functionNameCopy.equals(entry.getKey());
			}).findFirst();
			
			if(!ret.isPresent())
				return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, DATA_ID);
			
			fp = new FunctionPointerObject(ret.get().getValue());
		}else if(patterns.matches(functionName, LangPatterns.VAR_NAME_FUNC_PTR)) {
			DataObject ret = data.get(DATA_ID).var.get(functionName);
			if(ret == null || ret.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, DATA_ID);
			
			fp = ret.getFunctionPointer();
		}else {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return interpretFunctionPointer(fp, functionName, node.getChildren(), DATA_ID);
	}
	
	private DataObject interpretFunctionCallPreviousNodeValueNode(FunctionCallPreviousNodeValueNode node, DataObject previousValue, final int DATA_ID) {
		if(previousValue == null || previousValue.getType() != DataType.FUNCTION_POINTER)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID);
		
		return interpretFunctionPointer(previousValue.getFunctionPointer(), previousValue.getVariableName(), node.getChildren(), DATA_ID);
	}
	
	private DataObject interpretFunctionDefinitionNode(FunctionDefinitionNode node, final int DATA_ID) {
		List<VariableNameNode> parameterList = new ArrayList<>();
		List<Node> children = node.getChildren();
		Iterator<Node> childrenIterator = children.listIterator();
		while(childrenIterator.hasNext()) {
			Node child = childrenIterator.next();
			try {
				if(child.getNodeType() != NodeType.VARIABLE_NAME) {
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
					
					continue;
				}
				
				VariableNameNode parameter = (VariableNameNode)child;
				String variableName = parameter.getVariableName();
				if(!childrenIterator.hasNext() && !patterns.matches(variableName, LangPatterns.LANG_VAR) &&
				patterns.matches(variableName, LangPatterns.FUNC_CALL_VAR_ARGS)) {
					//Varargs (only the last parameter can be a varargs parameter)
					parameterList.add(parameter);
					break;
				}
				
				if((!patterns.matches(variableName, LangPatterns.VAR_NAME) && !patterns.matches(variableName, LangPatterns.FUNC_CALL_CALL_BY_PTR)) ||
				patterns.matches(variableName, LangPatterns.LANG_VAR)) {
					setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
					
					continue;
				}
				parameterList.add(parameter);
			}catch(ClassCastException e) {
				setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}
		
		return new DataObject().setFunctionPointer(new FunctionPointerObject(parameterList, node.getFunctionBody()));
	}
	
	void createDataMap(final int DATA_ID) {
		createDataMap(DATA_ID, null);
	}
	private void createDataMap(final int DATA_ID, String[] langArgs) {
		data.put(DATA_ID, new Data());
		
		if(langArgs != null) {
			DataObject[] langArgsArray = new DataObject[langArgs.length];
			for(int i = 0;i < langArgs.length;i++)
				langArgsArray[i] = new DataObject(langArgs[i]);
			data.get(DATA_ID).var.put("&LANG_ARGS", new DataObject().setArray(langArgsArray).setFinalData(true).setVariableName("&LANG_ARGS"));
		}
		
		resetVarsAndFuncPtrs(DATA_ID);
	}
	private void resetVarsAndFuncPtrs(final int DATA_ID) {
		DataObject langArgs = data.get(DATA_ID).var.get("&LANG_ARGS");
		data.get(DATA_ID).var.clear();
		
		//Final vars
		data.get(DATA_ID).var.put("$LANG_COMPILER_VERSION", new DataObject(VERSION, true).setVariableName("$LANG_COMPILER_VERSION"));
		data.get(DATA_ID).var.put("$LANG_PATH", new DataObject(langPath, true).setVariableName("$LANG_PATH"));
		data.get(DATA_ID).var.put("$LANG_RAND_MAX", new DataObject().setInt(Integer.MAX_VALUE).setFinalData(true).setVariableName("$LANG_RAND_MAX"));
		data.get(DATA_ID).var.put("$LANG_OS_NAME", new DataObject(System.getProperty("os.name")).setFinalData(true).setVariableName("$LANG_OS_NAME"));
		data.get(DATA_ID).var.put("$LANG_OS_VER", new DataObject(System.getProperty("os.version")).setFinalData(true).setVariableName("$LANG_OS_VER"));
		data.get(DATA_ID).var.put("$LANG_OS_ARCH", new DataObject(System.getProperty("os.arch")).setFinalData(true).setVariableName("$LANG_OS_ARCH"));
		data.get(DATA_ID).var.put("$LANG_OS_FILE_SEPARATOR", new DataObject(System.getProperty("file.separator")).setFinalData(true).setVariableName("$LANG_OS_FILE_SEPARATOR"));
		data.get(DATA_ID).var.put("$LANG_OS_LINE_SEPARATOR", new DataObject(System.getProperty("line.separator")).setFinalData(true).setVariableName("$LANG_OS_LINE_SEPARATOR"));
		data.get(DATA_ID).var.put("$LANG_FLOAT_NAN", new DataObject().setFloat(Float.NaN).setFinalData(true).setVariableName("$LANG_FLOAT_NAN"));
		data.get(DATA_ID).var.put("$LANG_FLOAT_POS_INF", new DataObject().setFloat(Float.POSITIVE_INFINITY).setFinalData(true).setVariableName("$LANG_FLOAT_POS_INF"));
		data.get(DATA_ID).var.put("$LANG_FLOAT_NEG_INF", new DataObject().setFloat(Float.NEGATIVE_INFINITY).setFinalData(true).setVariableName("$LANG_FLOAT_NEG_INF"));
		data.get(DATA_ID).var.put("$LANG_DOUBLE_NAN", new DataObject().setDouble(Double.NaN).setFinalData(true).setVariableName("$LANG_DOUBLE_NAN"));
		data.get(DATA_ID).var.put("$LANG_DOUBLE_POS_INF", new DataObject().setDouble(Double.POSITIVE_INFINITY).setFinalData(true).setVariableName("$LANG_DOUBLE_POS_INF"));
		data.get(DATA_ID).var.put("$LANG_DOUBLE_NEG_INF", new DataObject().setDouble(Double.NEGATIVE_INFINITY).setFinalData(true).setVariableName("$LANG_DOUBLE_NEG_INF"));
		data.get(DATA_ID).var.put("$LANG_MATH_PI", new DataObject().setDouble(Math.PI).setFinalData(true).setVariableName("$LANG_MATH_PI"));
		data.get(DATA_ID).var.put("$LANG_MATH_E", new DataObject().setDouble(Math.E).setFinalData(true).setVariableName("$LANG_MATH_E"));
		data.get(DATA_ID).var.put("&LANG_ARGS", langArgs == null?new DataObject().setArray(new DataObject[0]).setFinalData(true).setVariableName("&LANG_ARGS"):langArgs);
		
		for(InterpretingError error:InterpretingError.values()) {
			String upperCaseErrorName = error.name().toUpperCase();
			String variableName = "$LANG_ERROR_" + upperCaseErrorName;
			data.get(DATA_ID).var.put(variableName, new DataObject().setError(new ErrorObject(error)).setFinalData(true).setVariableName(variableName));
			variableName = "$LANG_ERRNO_" + upperCaseErrorName;
			data.get(DATA_ID).var.put(variableName, new DataObject().setInt(error.getErrorCode()).setFinalData(true).setVariableName(variableName));
		}
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Set $LANG_ERRNO
	}
	void resetVars(final int DATA_ID) {
		String[] keys = data.get(DATA_ID).var.keySet().toArray(new String[0]);
		for(int i = data.get(DATA_ID).var.size() - 1;i > -1;i--) {
			if((keys[i].startsWith("$") && !keys[i].startsWith("$LANG_")) ||
			(keys[i].startsWith("&") && !keys[i].startsWith("&LANG_"))) {
				data.get(DATA_ID).var.remove(keys[i]);
			}
		}
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Set $LANG_ERRNO
	}
	
	void setErrno(InterpretingError error, final int DATA_ID) {
		data.get(DATA_ID).var.computeIfAbsent("$LANG_ERRNO", key -> new DataObject().setVariableName("$LANG_ERRNO"));
		
		int currentErrno = data.get(DATA_ID).var.get("$LANG_ERRNO").getInt();
		int newErrno = error.getErrorCode();
		
		if(newErrno >= 0 || currentErrno < 1)
			data.get(DATA_ID).var.get("$LANG_ERRNO").setInt(newErrno);
	}
	
	DataObject setErrnoErrorObject(InterpretingError error, final int DATA_ID) {
		setErrno(error, DATA_ID);
		
		return new DataObject().setError(new ErrorObject(error));
	}
	InterpretingError getAndClearErrnoErrorObject(final int DATA_ID) {
		int errno = data.get(DATA_ID).var.get("$LANG_ERRNO").getInt();
		
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Reset errno
		
		return InterpretingError.getErrorFromErrorCode(errno);
	}
	
	//Classes for variable data
	public static final class FunctionPointerObject {
		/**
		 * Normal function pointer
		 */
		public static final int NORMAL = 0;
		/**
		 * Pointer to a predefined function
		 */
		public static final int PREDEFINED = 1;
		/**
		 * Function which is defined in the language
		 */
		public static final int EXTERNAL = 2;
		
		private final List<VariableNameNode> parameterList;
		private final AbstractSyntaxTree functionBody;
		private final LangPredefinedFunctionObject predefinedFunction;
		private final LangExternalFunctionObject externalFunction;
		private final int functionPointerType;
		
		/**
		 * For normal function pointer definition
		 */
		public FunctionPointerObject(List<VariableNameNode> parameterList, AbstractSyntaxTree functionBody) {
			this.parameterList = parameterList == null?null:new ArrayList<>(parameterList);
			this.functionBody = functionBody;
			this.predefinedFunction = null;
			this.externalFunction = null;
			this.functionPointerType = NORMAL;
		}
		/**
		 * For pointer to predefined function/linker function
		 */
		public FunctionPointerObject(LangPredefinedFunctionObject predefinedFunction) {
			this.parameterList = null;
			this.functionBody = null;
			this.predefinedFunction = predefinedFunction;
			this.externalFunction = null;
			this.functionPointerType = PREDEFINED;
		}
		/**
		 * For pointer to external function
		 */
		public FunctionPointerObject(LangExternalFunctionObject externalFunction) {
			this.parameterList = null;
			this.functionBody = null;
			this.predefinedFunction = null;
			this.externalFunction = externalFunction;
			this.functionPointerType = EXTERNAL;
		}
		
		public List<VariableNameNode> getParameterList() {
			return parameterList;
		}
		
		public AbstractSyntaxTree getFunctionBody() {
			return functionBody;
		}
		
		public LangPredefinedFunctionObject getPredefinedFunction() {
			return predefinedFunction;
		}
		
		public LangExternalFunctionObject getExternalFunction() {
			return externalFunction;
		}
		
		public int getFunctionPointerType() {
			return functionPointerType;
		}
		
		@Override
		public String toString() {
			switch(functionPointerType) {
				case NORMAL:
					return "<Normal FP>";
				case PREDEFINED:
					return "<Predefined Function>";
				case EXTERNAL:
					return "<External Function>";
				default:
					return "Error";
			}
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof FunctionPointerObject))
				return false;
			
			FunctionPointerObject that = (FunctionPointerObject)obj;
			return this.functionPointerType == that.functionPointerType && Objects.equals(this.parameterList, that.parameterList) &&
			Objects.equals(this.functionBody, that.functionBody) && Objects.equals(this.predefinedFunction, that.predefinedFunction) &&
			Objects.equals(this.externalFunction, externalFunction);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(functionPointerType, parameterList, functionBody, predefinedFunction, externalFunction);
		}
	}
	public static final class VarPointerObject {
		private final DataObject var;
		
		public VarPointerObject(DataObject var) {
			this.var = var;
		}
		
		public DataObject getVar() {
			return var;
		}
		
		@Override
		public String toString() {
			return "VP -> {" + var + "}";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof VarPointerObject))
				return false;
			
			VarPointerObject that = (VarPointerObject)obj;
			return this.var.equals(that.var);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(var);
		}
	}
	public static final class ErrorObject {
		private final InterpretingError err;
		
		public ErrorObject(InterpretingError err) {
			if(err == null)
				this.err = InterpretingError.NO_ERROR;
			else
				this.err = err;
		}
		
		public InterpretingError getInterprettingError() {
			return err;
		}
		
		public int getErrno() {
			return err.getErrorCode();
		}
		
		public String getErrmsg() {
			return err.getErrorText();
		}
		
		@Override
		public String toString() {
			return "Error";
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof ErrorObject))
				return false;
			
			ErrorObject that = (ErrorObject)obj;
			return this.err.equals(that.err);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(err);
		}
	}
	public static enum DataType {
		TEXT, CHAR, INT, LONG, FLOAT, DOUBLE, ARRAY, VAR_POINTER, FUNCTION_POINTER, ERROR, NULL, VOID, ARGUMENT_SEPARATOR;
	}
	public static final class DataObject {
		//Value
		private String txt;
		private DataObject[] arr;
		private VarPointerObject vp;
		private FunctionPointerObject fp;
		private int intValue;
		private long longValue;
		private float floatValue;
		private double doubleValue;
		private char charValue;
		private ErrorObject error;
		
		//Meta-Data
		/**
		 * Variable name of the DataObject (null for anonymous variable)
		 */
		private String variableName;
		private DataType type;
		private boolean finalData;
		
		public DataObject(DataObject dataObject) {
			setData(dataObject);
		}
		
		public DataObject() {
			this("");
		}
		public DataObject(String txt) {
			this(txt, false);
		}
		public DataObject(String txt, boolean finalData) {
			setText(txt);
			setFinalData(finalData);
		}
		
		/**
		 * This method <b>ignores</b> the final state of the data object<br>
		 * This method will not change variableName nor finalData
		 */
		void setData(DataObject dataObject) {
			this.type = dataObject.type;
			this.txt = dataObject.txt;
			this.arr = dataObject.arr; //Array: copy reference only
			this.vp = dataObject.vp; //Var pointer: copy reference only
			this.fp = dataObject.fp; //Func pointer: copy reference only
			this.intValue = dataObject.intValue;
			this.longValue = dataObject.longValue;
			this.floatValue = dataObject.floatValue;
			this.doubleValue = dataObject.doubleValue;
			this.charValue = dataObject.charValue;
			this.error = dataObject.error; //Error: copy reference only
		}
		
		/**
		 * This method <b>ignores</b> the final state of the data object<br>
		 * This method will not change variableName nor finalData
		 */
		private void resetValue() {
			this.type = null;
			this.txt = null;
			this.arr = null;
			this.vp = null;
			this.fp = null;
			this.intValue = 0;
			this.longValue = 0;
			this.floatValue = 0;
			this.doubleValue = 0;
			this.charValue = 0;
			this.error = null;
		}
		
		DataObject setArgumentSeparator(String txt) {
			if(finalData)
				return this;
			if(txt == null)
				return setNull();
			
			resetValue();
			this.type = DataType.ARGUMENT_SEPARATOR;
			this.txt = txt;
			
			return this;
		}
		
		public DataObject setText(String txt) {
			if(finalData)
				return this;
			if(txt == null)
				return setNull();
			
			resetValue();
			this.type = DataType.TEXT;
			this.txt = txt;
			
			return this;
		}
		
		private String getArrayText() {
			StringBuilder builder = new StringBuilder("[");
			if(arr.length > 0) {
				for(DataObject ele:arr) {
					if(ele.getType() == DataType.ARRAY) {
						builder.append("<Array: len: " + ele.getArray().length + ">");
					}else if(ele.getType() == DataType.VAR_POINTER) {
						builder.append("VP -> {");
						DataObject data = ele.getVarPointer().getVar();
						if(data != null && data.getType() == DataType.ARRAY) {
							builder.append("<Array: len: " + data.getArray().length + ">");
						}else if(data != null && data.getType() == DataType.VAR_POINTER) {
							builder.append("VP -> {...}");
						}else {
							builder.append(data);
						}
						builder.append("}");
					}else {
						builder.append(ele.getText());
					}
					builder.append(", ");
				}
				builder.delete(builder.length() - 2, builder.length());
			}
			builder.append(']');
			return builder.toString();
		}
		public String getText() {
			try {
				switch(type) {
					case TEXT:
					case ARGUMENT_SEPARATOR:
						return txt;
					case ARRAY:
						return getArrayText();
					case VAR_POINTER:
						if(variableName != null)
							return variableName;
						
						return vp.toString();
					case FUNCTION_POINTER:
						if(variableName != null)
							return variableName;
						
						return fp.toString();
					case VOID:
						return "";
					case NULL:
						return "null";
					case INT:
						return intValue + "";
					case LONG:
						return longValue + "";
					case FLOAT:
						return floatValue + "";
					case DOUBLE:
						return doubleValue + "";
					case CHAR:
						return charValue + "";
					case ERROR:
						return error.toString();
				}
			}catch(StackOverflowError e) {
				return "Error";
			}
			
			return null;
		}
		
		public DataObject setArray(DataObject[] arr) {
			if(finalData)
				return this;
			if(arr == null)
				return setNull();
			
			resetValue();
			this.type = DataType.ARRAY;
			this.arr = arr;
			
			return this;
		}
		
		public DataObject[] getArray() {
			return arr;
		}
		
		public DataObject setVarPointer(VarPointerObject vp) {
			if(finalData)
				return this;
			if(vp == null)
				return setNull();
			
			resetValue();
			this.type = DataType.VAR_POINTER;
			this.vp = vp;
			
			return this;
		}
		
		public VarPointerObject getVarPointer() {
			return vp;
		}
		
		public DataObject setFunctionPointer(FunctionPointerObject fp) {
			if(finalData)
				return this;
			if(fp == null)
				return setNull();
			
			resetValue();
			this.type = DataType.FUNCTION_POINTER;
			this.fp = fp;
			
			return this;
		}
		
		public FunctionPointerObject getFunctionPointer() {
			return fp;
		}
		
		public DataObject setNull() {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.NULL;
			
			return this;
		}
		
		public DataObject setVoid() {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.VOID;
			
			return this;
		}
		
		public DataObject setInt(int intValue) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.INT;
			this.intValue = intValue;
			
			return this;
		}
		
		public int getInt() {
			return intValue;
		}
		
		/**
		 * Sets data to INT = 1 if boolean value is true else INT = 0
		 */
		public DataObject setBoolean(boolean booleanValue) {
			return setInt(booleanValue?1:0);
		}
		
		public boolean getBoolean() {
			switch(type) {
				case TEXT:
					return !txt.isEmpty();
				case CHAR:
					return charValue != 0;
				case INT:
					return intValue != 0;
				case LONG:
					return longValue != 0;
				case FLOAT:
					return floatValue != 0;
				case DOUBLE:
					return doubleValue != 0;
				case ARRAY:
					return arr.length > 0;
				case ERROR:
					return error.getErrno() != 0;
					
				case VAR_POINTER:
				case FUNCTION_POINTER:
					return true;
				
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return false;
			}
			
			return false;
		}
		
		public DataObject setLong(long longValue) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.LONG;
			this.longValue = longValue;
			
			return this;
		}
		
		public long getLong() {
			return longValue;
		}
		
		public DataObject setFloat(float floatValue) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.FLOAT;
			this.floatValue = floatValue;
			
			return this;
		}
		
		public float getFloat() {
			return floatValue;
		}
		
		public DataObject setDouble(double doubleValue) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.DOUBLE;
			this.doubleValue = doubleValue;
			
			return this;
		}
		
		public double getDouble() {
			return doubleValue;
		}
		
		public DataObject setChar(char charValue) {
			if(finalData)
				return this;
			
			resetValue();
			this.type = DataType.CHAR;
			this.charValue = charValue;
			
			return this;
		}
		
		public char getChar() {
			return charValue;
		}
		
		public DataObject setError(ErrorObject error) {
			if(finalData)
				return this;
			if(error == null)
				return setNull();
			
			resetValue();
			this.type = DataType.ERROR;
			this.error = error;
			
			return this;
		}
		
		public ErrorObject getError() {
			return error;
		}
		
		public DataObject setVariableName(String variableName) {
			this.variableName = variableName;
			
			return this;
		}
		
		public String getVariableName() {
			return variableName;
		}
		
		public DataObject setFinalData(boolean finalData) {
			this.finalData = finalData;
			
			return this;
		}
		
		public boolean isFinalData() {
			return finalData;
		}
		
		public DataType getType() {
			return type;
		}
		
		public DataObject convertToNumberAndCreateNewDataObject() {
			switch(type) {
				case TEXT:
					//INT
					try {
						return new DataObject().setInt(Integer.parseInt(txt));
					}catch(NumberFormatException ignore) {}
					
					//LONG
					try {
						return new DataObject().setLong(Long.parseLong(txt));
					}catch(NumberFormatException ignore) {}
					
					//FLOAT
					try {
						float floatNumber = Float.parseFloat(txt);
						if(floatNumber != Float.POSITIVE_INFINITY && floatNumber != Float.NEGATIVE_INFINITY) {
							return new DataObject().setFloat(floatNumber);
						}
					}catch(NumberFormatException ignore) {}
					
					//DOUBLE
					try {
						return new DataObject().setDouble(Double.parseDouble(txt));
					}catch(NumberFormatException ignore) {}
					
					//CHAR
					if(txt.length() == 1)
						return new DataObject().setInt(txt.charAt(0));
					
					return new DataObject().setNull();
				case CHAR:
					return new DataObject().setInt(charValue);
				case INT:
				case LONG:
				case FLOAT:
				case DOUBLE:
					return new DataObject(this);
				case ERROR:
					return new DataObject().setInt(error.getErrno());
				case ARRAY:
					return new DataObject().setInt(arr.length);
				
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return new DataObject().setNull();
			}
			
			return new DataObject().setNull();
		}
		
		public Number getNumber() {
			switch(type) {
				case TEXT:
					//INT
					try {
						return Integer.parseInt(txt);
					}catch(NumberFormatException ignore) {}
					
					//LONG
					try {
						return Long.parseLong(txt);
					}catch(NumberFormatException ignore) {}
					
					//FLOAT
					try {
						float floatNumber = Float.parseFloat(txt);
						if(floatNumber != Float.POSITIVE_INFINITY && floatNumber != Float.NEGATIVE_INFINITY) {
							return floatNumber;
						}
					}catch(NumberFormatException ignore) {}
					
					//DOUBLE
					try {
						return Double.parseDouble(txt);
					}catch(NumberFormatException ignore) {}
					
					//CHAR
					if(txt.length() == 1)
						return (int)txt.charAt(0);
					
					return null;
				case CHAR:
					return (int)charValue;
				case INT:
					return intValue;
				case LONG:
					return longValue;
				case FLOAT:
					return floatValue;
				case DOUBLE:
					return doubleValue;
				case ERROR:
					return error.getErrno();
				case ARRAY:
					return arr.length;
				
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return null;
			}
			
			return null;
		}
		
		//Comparison functions for conditions
		public boolean isEquals(DataObject other) {
			if(this == other)
				return true;
			
			if(other == null)
				return false;
			
			Number number = other.getNumber();
			switch(type) {
				case TEXT:
					if(other.type == DataType.TEXT)
						return txt.equals(other.txt);
					
					return number != null && other.isEquals(this);
				
				case CHAR:
					return number != null && charValue == number.intValue();
				
				case INT:
					return number != null && intValue == number.intValue();
				
				case LONG:
					return number != null && longValue == number.longValue();
				
				case FLOAT:
					return number != null && floatValue == number.floatValue();
				
				case DOUBLE:
					return number != null && doubleValue == number.doubleValue();
				
				case ARRAY:
					if(other.type == DataType.ARRAY)
						return Objects.deepEquals(arr, other.arr);
					return number != null && arr.length == number.intValue();
				
				case VAR_POINTER:
					return vp.equals(other.vp);
				
				case FUNCTION_POINTER:
					return fp.equals(other.fp);
				
				case ERROR:
					switch(other.type) {
						case TEXT:
							if(number == null)
								return error.getErrmsg().equals(other.txt);
							return error.getErrno() == number.intValue();
						
						case CHAR:
						case INT:
						case LONG:
						case FLOAT:
						case DOUBLE:
						case ARRAY:
							return number != null && error.getErrno() == number.intValue();
						
						case ERROR:
							return error.equals(other.error);
						
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
				
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return type == other.type;
			}
			
			return false;
		}
		public boolean isStrictEquals(DataObject other) {
			if(this == other)
				return true;
			
			if(other == null)
				return false;
			
			try {
				return this.type.equals(other.type) && Objects.equals(this.txt, other.txt) && Objects.deepEquals(this.arr, other.arr) &&
				Objects.equals(this.vp, other.vp) && Objects.equals(this.fp, other.fp) && this.intValue == other.intValue &&
				this.longValue == other.longValue && this.floatValue == other.floatValue && this.doubleValue == other.doubleValue &&
				this.charValue == other.charValue && Objects.equals(this.error, other.error);
			}catch(StackOverflowError e) {
				return false;
			}
		}
		public boolean isLessThan(DataObject other) {
			if(this == other || other == null)
				return false;
			
			Number number = other.getNumber();
			switch(type) {
				case TEXT:
					if(other.type == DataType.TEXT)
						return txt.compareTo(other.txt) < 0;
					
					Number thisNumber = getNumber();
					if(thisNumber == null)
						return false;
					
					switch(other.type) {
						case CHAR:
						case INT:
						case ERROR:
						case ARRAY:
							return number != null && thisNumber.intValue() < number.intValue();
						case LONG:
							return number != null && thisNumber.longValue() < number.longValue();
						case FLOAT:
							return number != null && thisNumber.floatValue() < number.floatValue();
						case DOUBLE:
							return number != null && thisNumber.doubleValue() < number.doubleValue();
							
						case TEXT:
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
				
				case CHAR:
					return number != null && charValue < number.intValue();
				
				case INT:
					return number != null && intValue < number.intValue();
				
				case LONG:
					return number != null && longValue < number.longValue();
				
				case FLOAT:
					return number != null && floatValue < number.floatValue();
				
				case DOUBLE:
					return number != null && doubleValue < number.doubleValue();
				
				case ARRAY:
					return number != null && arr.length < number.intValue();
				
				case ERROR:
					switch(other.type) {
						case TEXT:
							if(number == null)
								return error.getErrmsg().compareTo(other.txt) < 0;
							return error.getErrno() < number.intValue();
						
						case CHAR:
						case INT:
						case LONG:
						case FLOAT:
						case DOUBLE:
						case ARRAY:
						case ERROR:
							return number != null && error.getErrno() < number.intValue();
						
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
					
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return false;
			}
			
			return false;
		}
		public boolean isGreaterThan(DataObject other) {
			if(this == other || other == null)
				return false;
			
			Number number = other.getNumber();
			switch(type) {
				case TEXT:
					if(other.type == DataType.TEXT)
						return txt.compareTo(other.txt) > 0;
					
					Number thisNumber = getNumber();
					if(thisNumber == null)
						return false;
					
					switch(other.type) {
						case CHAR:
						case INT:
						case ERROR:
						case ARRAY:
							return number != null && thisNumber.intValue() > number.intValue();
						case LONG:
							return number != null && thisNumber.longValue() > number.longValue();
						case FLOAT:
							return number != null && thisNumber.floatValue() > number.floatValue();
						case DOUBLE:
							return number != null && thisNumber.doubleValue() > number.doubleValue();
							
						case TEXT:
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
				
				case CHAR:
					return number != null && charValue > number.intValue();
				
				case INT:
					return number != null && intValue > number.intValue();
				
				case LONG:
					return number != null && longValue > number.longValue();
				
				case FLOAT:
					return number != null && floatValue > number.floatValue();
				
				case DOUBLE:
					return number != null && doubleValue > number.doubleValue();
				
				case ARRAY:
					return number != null && arr.length > number.intValue();
				
				case ERROR:
					switch(other.type) {
						case TEXT:
							if(number == null)
								return error.getErrmsg().compareTo(other.txt) > 0;
							return error.getErrno() > number.intValue();
						
						case CHAR:
						case INT:
						case LONG:
						case FLOAT:
						case DOUBLE:
						case ARRAY:
						case ERROR:
							return number != null && error.getErrno() > number.intValue();
						
						case VAR_POINTER:
						case FUNCTION_POINTER:
						case NULL:
						case VOID:
						case ARGUMENT_SEPARATOR:
							return false;
					}
					
					return false;
					
				case VAR_POINTER:
				case FUNCTION_POINTER:
				case NULL:
				case VOID:
				case ARGUMENT_SEPARATOR:
					return false;
			}
			
			return false;
		}
		public boolean isLessThanOrEquals(DataObject other) {
			return isLessThan(other) || isEquals(other);
		}
		public boolean isGreaterThanOrEquals(DataObject other) {
			return isGreaterThan(other) || isEquals(other);
		}
		
		@Override
		public String toString() {
			return getText();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof DataObject))
				return false;
			
			try {
				DataObject that = (DataObject)obj;
				return this.type.equals(that.type) && Objects.equals(this.txt, that.txt) && Objects.deepEquals(this.arr, that.arr) &&
				Objects.equals(this.vp, that.vp) && Objects.equals(this.fp, that.fp) && this.intValue == that.intValue &&
				this.longValue == that.longValue && this.floatValue == that.floatValue && this.doubleValue == that.doubleValue &&
				this.charValue == that.charValue && Objects.equals(this.error, that.error) && Objects.equals(this.variableName, that.variableName) &&
				this.finalData == that.finalData;
			}catch(StackOverflowError e) {
				return false;
			}
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(type, txt, arr, vp, fp, intValue, longValue, floatValue, doubleValue, charValue, error, variableName, finalData);
		}
	}
	public static final class Data {
		public final Map<String, String> lang = new HashMap<>();
		public final Map<String, DataObject> var = new HashMap<>();
	}
	
	public static enum InterpretingError {
		NO_ERROR              ( 0, "No Error"),
		
		//ERRORS
		FINAL_VAR_CHANGE      ( 1, "LANG or final vars mustn't be changed"),
		TO_MANY_INNER_LINKS   ( 2, "To many inner links"),
		NO_LANG_FILE          ( 3, "No .lang-File"),
		FILE_NOT_FOUND        ( 4, "File not found"),
		INVALID_FUNC_PTR      ( 5, "FuncPtr is invalid"),
		STACK_OVERFLOW        ( 6, "Stack overflow"),
		NO_TERMINAL           ( 7, "No terminal available"),
		INVALID_ARG_COUNT     ( 8, "Invalid argument count"),
		INVALID_LOG_LEVEL     ( 9, "Invalid log level"),
		INVALID_ARR_PTR       (10, "Invalid array pointer"),
		NO_HEX_NUM            (11, "No hex num"),
		NO_CHAR               (12, "No char"),
		NO_NUM                (13, "No number"),
		DIV_BY_ZERO           (14, "Dividing by 0"),
		NEGATIVE_ARRAY_LEN    (15, "Negative array length"),
		EMPTY_ARRAY           (16, "Empty array"),
		LENGTH_NAN            (17, "Length NAN"),
		INDEX_OUT_OF_BOUNDS   (18, "Index out of bounds"),
		ARG_COUNT_NOT_ARR_LEN (19, "Argument count is not array length"),
		INVALID_FUNC_PTR_LOOP (20, "Invalid function pointer"),
		INVALID_ARGUMENTS     (21, "Invalid arguments"),
		FUNCTION_NOT_FOUND    (22, "Function not found"),
		EOF                   (23, "End of file was reached early"),
		SYSTEM_ERROR          (24, "System Error"),
		NEGATIVE_REPEAT_COUNT (25, "Negative repeat count"),
		TRANS_KEY_NOT_FOUND   (26, "Translation key dosen't exist"),
		FUNCTION_NOT_SUPPORTED(27, "Function not supported"),
		BRACKET_MISMATCH      (28, "Bracket mismatch"),
		IF_CONDITION_MISSING  (29, "If statement condition missing"),
		INVALID_AST_NODE      (30, "Invalid AST node or AST node order"),
		INVALID_PTR           (31, "Invalid Pointer"),
		INCOMPATIBLE_DATA_TYPE(32, "Incompatible data type"),
		LANG_ARRAYS_COPY      (33, "&LANG arrays can't be copied"),
		
		//WARNINGS
		DEPRECATED_FUNC_CALL  (-1, "A deprecated predefined function was called"),
		NO_TERMINAL_WARNING   (-2, "No terminal available");
		
		private final int errorCode;
		private final String errorText;
		
		private InterpretingError(int errorCode, String errorText) {
			this.errorCode = errorCode;
			this.errorText = errorText;
		}
		
		public int getErrorCode() {
			return errorCode;
		}
		
		public String getErrorText() {
			return errorText;
		}
		
		public static InterpretingError getErrorFromErrorCode(int errorCode) {
			for(InterpretingError error:values())
				if(error.getErrorCode() == errorCode)
					return error;
			
			return InterpretingError.NO_ERROR;
		}
	}
	
	/**
	 * Class for communication between the LangInterpreter and Java
	 */
	public static final class LangInterpreterInterface {
		private final LangInterpreter interpreter;
		
		public LangInterpreterInterface(LangInterpreter interpreter) {
			this.interpreter = interpreter;
		}
		
		public Map<Integer, Data> getData() {
			return interpreter.getData();
		}
		public Data getData(final int DATA_ID) {
			return interpreter.getData().get(DATA_ID);
		}
		
		public Map<String, String> getTranslationMap(final int DATA_ID) {
			Data data = getData(DATA_ID);
			if(data == null)
				return null;
			
			return data.lang;
		}
		public String getTranslation(final int DATA_ID, String key) {
			Map<String, String> translations = getTranslationMap(DATA_ID);
			if(translations == null)
				return null;
			
			return translations.get(key);
		}
		public void setTranslation(final int DATA_ID, String key, String value) {
			Map<String, String> translations = getTranslationMap(DATA_ID);
			if(translations != null)
				translations.put(key, value);
		}
		
		public Map<String, DataObject> getVarMap(final int DATA_ID) {
			Data data = getData(DATA_ID);
			if(data == null)
				return null;
			
			return data.var;
		}
		public DataObject getVar(final int DATA_ID, String varName) {
			Map<String, DataObject> vars = getVarMap(DATA_ID);
			if(vars == null)
				return null;
			
			return vars.get(varName);
		}
		public void setVar(final int DATA_ID, String varName, DataObject data) {
			setVar(DATA_ID, varName, data, false);
		}
		public void setVar(final int DATA_ID, String varName, DataObject data, boolean ignoreFinal) {
			Map<String, DataObject> vars = getVarMap(DATA_ID);
			if(vars != null) {
				DataObject oldData = vars.get(varName);
				if(oldData == null)
					vars.put(varName, data.setVariableName(varName));
				else if(ignoreFinal || !oldData.isFinalData())
					oldData.setData(data);
			}
		}
		public void setVar(final int DATA_ID, String varName, String text) {
			setVar(DATA_ID, varName, text, false);
		}
		public void setVar(final int DATA_ID, String varName, String text, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject(text), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, DataObject[] arr) {
			setVar(DATA_ID, varName, arr, false);
		}
		public void setVar(final int DATA_ID, String varName, DataObject[] arr, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject().setArray(arr), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, LangExternalFunctionObject function) {
			setVar(DATA_ID, varName, function, false);
		}
		public void setVar(final int DATA_ID, String varName, LangExternalFunctionObject function, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject().setFunctionPointer(new FunctionPointerObject(function)), ignoreFinal);
		}
		public void setVar(final int DATA_ID, String varName, InterpretingError error) {
			setVar(DATA_ID, varName, error, false);
		}
		public void setVar(final int DATA_ID, String varName, InterpretingError error, boolean ignoreFinal) {
			setVar(DATA_ID, varName, new DataObject().setError(new ErrorObject(error)), false);
		}
		
		/**
		 * Creates an function which is accessible globally in the Compiler (= in all DATA_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]" or with "liner.[funcName]" and can't be removed nor changed by the lang file
		 */
		public void addPredefinedFunction(String funcName, LangPredefinedFunctionObject function) {
			interpreter.funcs.put(funcName, function);
		}
		public Map<String, LangPredefinedFunctionObject> getPredefinedFunctions() {
			return interpreter.funcs;
		}
		
		public void exec(final int DATA_ID, BufferedReader lines) throws IOException {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretLines(lines, DATA_ID);
		}
		public void exec(final int DATA_ID, String lines) throws IOException {
			exec(DATA_ID, new BufferedReader(new StringReader(lines)));
		}
		
		/**
		 * Must be called before {@link #LangInterpreter.LangInterpreterInterface.getAndResetReturnValue() getAndResetReturnValue()} method
		 */
		public boolean isReturnedValueThrowValue() {
			return interpreter.isThrowedValue;
		}
		public DataObject getAndResetReturnValue() {
			return interpreter.getAndResetReturnValue(-1);
		}
		
		public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
			return interpreter.parseLines(lines);
		}
		
		public void interpretAST(final int DATA_ID, AbstractSyntaxTree ast) {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretAST(ast, DATA_ID);
		}
		public DataObject inerpretNode(final int DATA_ID, Node node) {
			return interpreter.interpretNode(node, DATA_ID);
		}
		public DataObject interpretFunctionCallNode(final int DATA_ID, FunctionCallNode node, String funcArgs) {
			return interpreter.interpretFunctionCallNode(node, DATA_ID);
		}
		public DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList, final int DATA_ID) {
			return interpreter.interpretFunctionPointer(fp, functionName, argumentList, DATA_ID);
		}
		
		public DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int DATA_ID) {
			return interpreter.callFunctionPointer(fp, functionName, argumentValueList, DATA_ID);
		}
		
		public void setErrno(InterpretingError error, final int DATA_ID) {
			interpreter.setErrno(error, DATA_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, final int DATA_ID) {
			return interpreter.setErrnoErrorObject(error, DATA_ID);
		}
		public InterpretingError getAndClearErrnoErrorObject(final int DATA_ID) {
			return interpreter.getAndClearErrnoErrorObject(DATA_ID);
		}
	}
}