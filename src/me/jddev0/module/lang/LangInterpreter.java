package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.AbstractSyntaxTree.*;
import me.jddev0.module.lang.AbstractSyntaxTree.OperationNode.Operator;
import me.jddev0.module.lang.AbstractSyntaxTree.OperationNode.OperatorType;
import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraint;
import me.jddev0.module.lang.DataObject.DataTypeConstraintException;
import me.jddev0.module.lang.DataObject.DataTypeConstraintViolatedException;
import me.jddev0.module.lang.DataObject.ErrorObject;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.StructObject;
import me.jddev0.module.lang.DataObject.VarPointerObject;
import me.jddev0.module.lang.LangUtils.InvalidTranslationTemplateSyntaxException;
import me.jddev0.module.lang.regex.InvalidPaternSyntaxException;
import me.jddev0.module.lang.regex.LangRegEx;

/**
 * Lang-Module<br>
 * Lang interpreter for interpreting AST created by LangParser
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangInterpreter {
	static final String VERSION = "v1.0.0";
	
	final LangParser parser = new LangParser();
	
	final LangModuleManager moduleManager = new LangModuleManager(this);
	final Map<String, LangModule> modules = new HashMap<>();
	
	
	private StackElement currentCallStackElement;
	private final LinkedList<StackElement> callStack;
	
	final TerminalIO term;
	final ILangPlatformAPI langPlatformAPI;
	final Random RAN = new Random();
	
	//Lang tests
	final LangTest langTestStore = new LangTest();
	InterpretingError langTestExpectedThrowValue;
	DataObject langTestExpectedReturnValue;
	boolean langTestExpectedNoReturnValue;
	String langTestMessageForLastTestResult;
	int langTestExpectedReturnValueScopeID;
	
	//Fields for return/throw node, continue/break node, and force stopping execution
	private final ExecutionState executionState = new ExecutionState();
	final ExecutionFlags executionFlags = new ExecutionFlags();
	
	//DATA
	final Map<Integer, Data> data = new HashMap<>();
	
	//Predefined functions & linker functions (= Predefined functions)
	Map<String, LangPredefinedFunctionObject> funcs = new HashMap<>();
	{
		LangPredefinedFunctions.addPredefinedFunctions(this, funcs);
		LangPredefinedFunctions.addLinkerFunctions(this, funcs);
	}
	final LangOperators operators = new LangOperators(this);
	final LangVars langVars = new LangVars(this);
	
	/**
	 * @param term can be null
	 */
	public LangInterpreter(String langPath, TerminalIO term, ILangPlatformAPI langPlatformAPI) {
		this(langPath, null, term, langPlatformAPI, null);
	}
	/**
	 * @param langFile can be null
	 * @param term can be null
	 */
	public LangInterpreter(String langPath, String langFile, TerminalIO term, ILangPlatformAPI langPlatformAPI) {
		this(langPath, langFile, term, langPlatformAPI, null);
	}
	/**
	 * @param term can be null
	 * @param langArgs can be null
	 */
	public LangInterpreter(String langPath, TerminalIO term, ILangPlatformAPI langPlatformAPI, String[] langArgs) {
		this(langPath, null, term, langPlatformAPI, langArgs);
	}
	/**
	 * @param langFile can be null
	 * @param term can be null
	 * @param langArgs can be null
	 */
	public LangInterpreter(String langPath, String langFile, TerminalIO term, ILangPlatformAPI langPlatformAPI, String[] langArgs) {
		callStack = new LinkedList<>();
		currentCallStackElement = new StackElement(langPath, langFile, null, null);
		this.term = term;
		this.langPlatformAPI = langPlatformAPI;
		
		createDataMap(0, langArgs);
	}
	
	public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
		return parser.parseLines(lines);
	}
	
	public void interpretAST(AbstractSyntaxTree ast) throws StoppedException {
		interpretAST(ast, 0);
	}
	
	public void interpretLines(BufferedReader lines) throws IOException, StoppedException {
		interpretLines(lines, 0);
	}
	
	public Map<Integer, Data> getData() {
		return new HashMap<>(data);
	}
	
	public void forceStop() {
		executionState.forceStopExecutionFlag = true;
	}
	
	public boolean isForceStopExecutionFlag() {
		return executionState.forceStopExecutionFlag;
	}
	
	public StackElement getCurrentCallStackElement() {
		return currentCallStackElement;
	}
	
	List<StackElement> getCallStackElements() {
		return new ArrayList<>(callStack);
	}
	
	void pushStackElement(StackElement stackElement, int parentLineNumber) {
		callStack.addLast(currentCallStackElement.withLineNumber(parentLineNumber));
		currentCallStackElement = stackElement;
	}
	
	StackElement popStackElement() {
		currentCallStackElement = callStack.pollLast().withLineNumber(-1);
		return currentCallStackElement;
	}
	
	String printStackTrace(int currentLineNumber) {
		StringBuilder builder = new StringBuilder();
		
		ListIterator<StackElement> iter = callStack.listIterator(callStack.size());
		builder.append(currentCallStackElement.withLineNumber(currentLineNumber));
		if(!iter.hasPrevious())
			return builder.toString();
		
		builder.append("\n");
		while(true) {
			builder.append(iter.previous());
			if(iter.hasPrevious())
				builder.append("\n");
			else
				break;
		}
		
		return builder.toString();
	}
	
	boolean interpretCondition(OperationNode node, final int SCOPE_ID) throws StoppedException {
		return interpretOperationNode(node, SCOPE_ID).getBoolean();
	}
	
	DataObject interpretLines(BufferedReader lines, final int SCOPE_ID) throws IOException, StoppedException {
		return interpretAST(parseLines(lines), SCOPE_ID);
	}
	
	DataObject interpretAST(AbstractSyntaxTree ast, final int SCOPE_ID) {
		if(ast == null)
			return null;
		
		if(executionState.forceStopExecutionFlag)
			throw new StoppedException();
		
		DataObject ret = null;
		for(Node node:ast) {
			if(executionState.stopExecutionFlag)
				return null;
			
			ret = interpretNode(null, node, SCOPE_ID);
		}
		
		return ret;
	}
	
	int getParserLineNumber() {
		return parser.getLineNumber();
	}
	
	void setParserLineNumber(int lineNumber) {
		parser.setLineNumber(lineNumber);
	}
	
	void resetParserPositionVars() {
		parser.resetPositionVars();
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretNode(DataObject compositeType, Node node, final int SCOPE_ID) {
		if(executionState.forceStopExecutionFlag)
			throw new StoppedException();
		
		try {
			loop:
			while(true) {
				if(node == null) {
					setErrno(InterpretingError.INVALID_AST_NODE, SCOPE_ID);
					
					return null;
				}
				
				switch(node.getNodeType()) {
					case UNPROCESSED_VARIABLE_NAME:
						node = processUnprocessedVariableNameNode(compositeType, (UnprocessedVariableNameNode)node, SCOPE_ID);
						continue loop;
						
					case FUNCTION_CALL_PREVIOUS_NODE_VALUE:
						node = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)node, null, SCOPE_ID);
						continue loop;
					
					case LIST:
						//Interpret a group of nodes
						return interpretListNode(compositeType, (ListNode)node, SCOPE_ID);
					
					case CHAR_VALUE:
					case TEXT_VALUE:
					case INT_VALUE:
					case LONG_VALUE:
					case FLOAT_VALUE:
					case DOUBLE_VALUE:
					case NULL_VALUE:
					case VOID_VALUE:
						return interpretValueNode((ValueNode)node, SCOPE_ID);
					
					case PARSING_ERROR:
						return interpretParsingErrorNode((ParsingErrorNode)node, SCOPE_ID);
					
					case IF_STATEMENT:
						return new DataObject().setBoolean(interpretIfStatementNode((IfStatementNode)node, SCOPE_ID));
					
					case IF_STATEMENT_PART_ELSE:
					case IF_STATEMENT_PART_IF:
						return new DataObject().setBoolean(interpretIfStatementPartNode((IfStatementPartNode)node, SCOPE_ID));
					
					case LOOP_STATEMENT:
						return new DataObject().setBoolean(interpretLoopStatementNode((LoopStatementNode)node, SCOPE_ID));
					
					case LOOP_STATEMENT_PART_WHILE:
					case LOOP_STATEMENT_PART_UNTIL:
					case LOOP_STATEMENT_PART_REPEAT:
					case LOOP_STATEMENT_PART_FOR_EACH:
					case LOOP_STATEMENT_PART_LOOP:
					case LOOP_STATEMENT_PART_ELSE:
						return new DataObject().setBoolean(interpretLoopStatementPartNode((LoopStatementPartNode)node, SCOPE_ID));
					
					case LOOP_STATEMENT_CONTINUE_BREAK:
						interpretLoopStatementContinueBreak((LoopStatementContinueBreakStatement)node, SCOPE_ID);
						return null;
					
					case TRY_STATEMENT:
						return new DataObject().setBoolean(interpretTryStatementNode((TryStatementNode)node, SCOPE_ID));
					
					case TRY_STATEMENT_PART_TRY:
					case TRY_STATEMENT_PART_SOFT_TRY:
					case TRY_STATEMENT_PART_NON_TRY:
					case TRY_STATEMENT_PART_CATCH:
					case TRY_STATEMENT_PART_ELSE:
					case TRY_STATEMENT_PART_FINALLY:
						return new DataObject().setBoolean(interpretTryStatementPartNode((TryStatementPartNode)node, SCOPE_ID));
					
					case OPERATION:
					case MATH:
					case CONDITION:
						return interpretOperationNode((OperationNode)node, SCOPE_ID);
					
					case RETURN:
						interpretReturnNode((ReturnNode)node, SCOPE_ID);
						return null;
					
					case THROW:
						interpretThrowNode((ThrowNode)node, SCOPE_ID);
						return null;
					
					case ASSIGNMENT:
						return interpretAssignmentNode((AssignmentNode)node, SCOPE_ID);
					
					case VARIABLE_NAME:
						return interpretVariableNameNode(compositeType, (VariableNameNode)node, SCOPE_ID);
					
					case ESCAPE_SEQUENCE:
						return interpretEscapeSequenceNode((EscapeSequenceNode)node, SCOPE_ID);
					
					case ARGUMENT_SEPARATOR:
						return interpretArgumentSeparatotNode((ArgumentSeparatorNode)node, SCOPE_ID);
					
					case FUNCTION_CALL:
						return interpretFunctionCallNode(compositeType, (FunctionCallNode)node, SCOPE_ID);
					
					case FUNCTION_DEFINITION:
						return interpretFunctionDefinitionNode((FunctionDefinitionNode)node, SCOPE_ID);
					
					case ARRAY:
						return interpretArrayNode((ArrayNode)node, SCOPE_ID);
					
					case STRUCT_DEFINITION:
						return interpretStructDefinitionNode((StructDefinitionNode)node, SCOPE_ID);
					
					case GENERAL:
						setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
						return null;
				}
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		}
		
		return null;
	}
	
	/**
	 * @param variablePrefixAppendAfterSearch If no part of the variable name matched an existing variable, the variable prefix will be added to the returned TextValueNode<br>
	 *                                             (e.g. "func.abc" ("func." is not part of the variableNames in the set))
	 * @param supportsPointerDereferencingAndReferencing If true, this node will return pointer reference or a dereferenced pointers as VariableNameNode<br>
	 *                                   (e.g. $[abc] is not in variableNames, but $abc is -> $[abc] will return a VariableNameNode)
	 */
	private Node convertVariableNameToVariableNameNodeOrComposition(int lineNumberFrom, int lineNumberTo, String moduleName, String variableName,
	Set<String> variableNames, String variablePrefixAppendAfterSearch, final boolean supportsPointerDereferencingAndReferencing, int lineNumber, final int SCOPE_ID) {
		Stream<String> variableNameStream;
		if(moduleName == null) {
			variableNameStream = variableNames.stream();
		}else {
			LangModule module = modules.get(moduleName);
			if(module == null) {
				setErrno(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" is not loaded!", lineNumber, SCOPE_ID);
				
				return new TextValueNode(lineNumberFrom, lineNumberTo,
						(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + variableName);
			}
			
			variableNameStream = module.getExportedVariables().keySet().stream();
		}
		
		Optional<String> optionalReturnedVariableName = variableNameStream.filter(varName -> {
			return variableName.startsWith(varName);
		}).sorted((s0, s1) -> { //Sort keySet from large to small length (e.g.: $abcd and $abc and $ab)
			return s0.length() < s1.length()?1:(s0.length() == s1.length()?0:-1);
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
						return new TextValueNode(lineNumberFrom, lineNumberTo,
								(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + variableName);
					
					dereferences = variableName.substring(startIndex, endIndex);
					modifiedVariableName = variableName.substring(0, startIndex) + variableName.substring(endIndex);
					
					if(!modifiedVariableName.contains("[") && !modifiedVariableName.contains("]"))
						returnedNode = convertVariableNameToVariableNameNodeOrComposition(lineNumberFrom, lineNumberTo,
								moduleName, modifiedVariableName, variableNames, "", supportsPointerDereferencingAndReferencing, lineNumber, SCOPE_ID);
				}
				
				if(modifiedVariableName.contains("[") && modifiedVariableName.contains("]")) { //Check dereferenced variable name
					int indexOpeningBracket = modifiedVariableName.indexOf("[");
					int indexMatchingBracket = LangUtils.getIndexOfMatchingBracket(modifiedVariableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
					if(indexMatchingBracket != -1) {
						//Remove all "[" "]" pairs
						int currentIndex = indexOpeningBracket;
						int currentIndexMatchingBracket = indexMatchingBracket;
						//"&" both "++" and "--" must be executed
						while(modifiedVariableName.charAt(++currentIndex) == '[' & modifiedVariableName.charAt(--currentIndexMatchingBracket) == ']');
						
						if(indexMatchingBracket != modifiedVariableName.length() - 1) {
							text = modifiedVariableName.substring(indexMatchingBracket + 1);
							modifiedVariableName = modifiedVariableName.substring(0, indexMatchingBracket + 1);
						}
						
						if(modifiedVariableName.indexOf('[', currentIndex) == -1) {
							returnedNode = convertVariableNameToVariableNameNodeOrComposition(lineNumberFrom, lineNumberTo,
									moduleName, modifiedVariableName.substring(0, indexOpeningBracket) +
									modifiedVariableName.substring(currentIndex, currentIndexMatchingBracket + 1), variableNames, "", supportsPointerDereferencingAndReferencing,
									lineNumber, SCOPE_ID);
						}
					}
				}
				
				if(returnedNode != null) {
					if(dereferences != null)
						modifiedVariableName = modifiedVariableName.substring(0, startIndex) + dereferences + modifiedVariableName.substring(startIndex);
					switch(returnedNode.getNodeType()) {
						case VARIABLE_NAME: //Variable was found without additional text -> valid pointer reference
							if(text == null)
								return new VariableNameNode(lineNumberFrom, lineNumberTo,
										(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + variableName);
							
							//Variable composition
							List<Node> nodes = new ArrayList<>();
							nodes.add(new VariableNameNode(lineNumberFrom, lineNumberTo,
									(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + modifiedVariableName));
							nodes.add(new TextValueNode(lineNumberFrom, lineNumberTo, text));
							return new ListNode(nodes);
						
						case LIST: //Variable was found with additional text -> no valid pointer reference
						case TEXT_VALUE: //Variable was not found
						default: //Default should never be reached
							return new TextValueNode(lineNumberFrom, lineNumberTo,
									(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + variableName);
					}
				}
			}
			
			return new TextValueNode(lineNumberFrom, lineNumberTo,
					(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + variableName);
		}
		
		String returendVariableName = optionalReturnedVariableName.get();
		if(returendVariableName.length() == variableName.length())
			return new VariableNameNode(lineNumberFrom, lineNumberTo,
					(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + variableName);
		
		//Variable composition
		List<Node> nodes = new ArrayList<>();
		//Add matching part of variable as VariableNameNode
		nodes.add(new VariableNameNode(lineNumberFrom, lineNumberTo,
				(moduleName == null?"":("[[" + moduleName + "]]::")) + variablePrefixAppendAfterSearch + returendVariableName));
		nodes.add(new TextValueNode(lineNumberFrom, lineNumberTo,
				variableName.substring(returendVariableName.length()))); //Add composition part as TextValueNode
		return new ListNode(nodes);
	}
	private Node processUnprocessedVariableNameNode(DataObject compositeType, UnprocessedVariableNameNode node, final int SCOPE_ID) {
		String variableName = node.getVariableName();
		
		if(executionFlags.rawVariableNames)
			return new VariableNameNode(node.getLineNumberFrom(), node.getLineNumberTo(), variableName);
		
		boolean isModuleVariable = variableName.startsWith("[[");
		String moduleName = null;
		if(isModuleVariable) {
			int indexModuleIdientifierEnd = variableName.indexOf("]]::");
			if(indexModuleIdientifierEnd == -1) {
				setErrno(InterpretingError.INVALID_AST_NODE, "Invalid variable name", node.getLineNumberFrom(), SCOPE_ID);
				
				return new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), variableName);
			}
			
			moduleName = variableName.substring(2, indexModuleIdientifierEnd);
			if(!isAlphaNummericWithUnderline(moduleName)) {
				setErrno(InterpretingError.INVALID_AST_NODE, "Invalid module name", node.getLineNumberFrom(), SCOPE_ID);
				
				return new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), variableName);
			}
			
			variableName = variableName.substring(indexModuleIdientifierEnd + 4);
		}
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp.")) {
			Set<String> variableNames;
			if(compositeType != null) {
				if(compositeType.getType() != DataType.STRUCT) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid composite type", node.getLineNumberFrom(), SCOPE_ID);
					
					return new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), variableName);
				}
				
				variableNames = new HashSet<>(Arrays.asList(compositeType.getStruct().getMemberNames()));
			}else {
				variableNames = data.get(SCOPE_ID).var.keySet();
			}
			
			return convertVariableNameToVariableNameNodeOrComposition(node.getLineNumberFrom(), node.getLineNumberTo(),
					moduleName, variableName, variableNames, "", variableName.startsWith("$"), node.getLineNumberFrom(), SCOPE_ID);
		}
		
		if(compositeType != null) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Invalid composite type member name: \"" + variableName + "\"", node.getLineNumberFrom(), SCOPE_ID);
			
			return new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), variableName);
		}
		
		final boolean isLinkerFunction;
		final String prefix;
		if(!isModuleVariable && variableName.startsWith("func.")) {
			isLinkerFunction = false;
			prefix = "func.";
			
			variableName = variableName.substring(5);
		}else if(!isModuleVariable && variableName.startsWith("fn.")) {
			isLinkerFunction = false;
			prefix = "fn.";
			
			variableName = variableName.substring(3);
		}else if(!isModuleVariable && variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			prefix = "linker.";
			
			variableName = variableName.substring(7);
		}else if(!isModuleVariable && variableName.startsWith("ln.")) {
			isLinkerFunction = true;
			prefix = "ln.";
			
			variableName = variableName.substring(3);
		}else {
			setErrno(InterpretingError.INVALID_AST_NODE, "Invalid variable name", node.getLineNumberFrom(), SCOPE_ID);
			
			return new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), variableName);
		}
		
		return convertVariableNameToVariableNameNodeOrComposition(node.getLineNumberFrom(), node.getLineNumberTo(),
		null, variableName, funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).map(Entry<String, LangPredefinedFunctionObject>::getKey).collect(Collectors.toSet()), prefix, false, node.getLineNumberFrom(), SCOPE_ID);
	}
	
	private Node processFunctionCallPreviousNodeValueNode(FunctionCallPreviousNodeValueNode node, DataObject previousValue, final int SCOPE_ID) {
		if(previousValue != null) {
			if(previousValue.getType() == DataType.FUNCTION_POINTER || previousValue.getType() == DataType.TYPE)
				return node;
			
			if(previousValue.getType() == DataType.STRUCT && previousValue.getStruct().isDefinition())
				return node;
		}
		
		//Previous node value wasn't a function -> return children of node in between "(" and ")" as ListNode
		List<Node> nodes = new ArrayList<>();
		nodes.add(new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), "(" + node.getLeadingWhitespace()));
		nodes.addAll(node.getChildren());
		nodes.add(new TextValueNode(node.getLineNumberFrom(), node.getLineNumberTo(), node.getTrailingWhitespace() + ")"));
		return new ListNode(nodes);
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretListNode(DataObject compositeType, ListNode node, final int SCOPE_ID) {
		List<DataObject> dataObjects = new LinkedList<>();
		DataObject previousDataObject = null;
		
		for(Node childNode:node.getChildren()) {
			if(childNode.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
				try {
					Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)childNode, previousDataObject, SCOPE_ID);
					if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
						dataObjects.remove(dataObjects.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
						dataObjects.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, SCOPE_ID));
					}else {
						dataObjects.add(interpretNode(null, ret, SCOPE_ID));
					}
				}catch(ClassCastException e) {
					dataObjects.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID));
				}
				
				previousDataObject = dataObjects.get(dataObjects.size() - 1);
				
				compositeType = null;
				
				continue;
			}
			
			DataObject ret = interpretNode(compositeType, childNode, SCOPE_ID);
			if(ret != null)
				dataObjects.add(ret);
			
			compositeType = null;
			
			previousDataObject = ret;
		}
		
		return LangUtils.combineDataObjects(dataObjects);
	}
	
	private DataObject interpretValueNode(ValueNode node, final int SCOPE_ID) {
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
					return new DataObject();
				case VOID_VALUE:
					return new DataObject().setVoid();
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		}
		
		return new DataObject().setError(new ErrorObject(InterpretingError.INVALID_AST_NODE));
	}
	
	private DataObject interpretParsingErrorNode(ParsingErrorNode node, final int SCOPE_ID) {
		InterpretingError error = null;
		
		switch(node.getError()) {
			case BRACKET_MISMATCH:
				error = InterpretingError.BRACKET_MISMATCH;
				break;
			
			case CONT_FLOW_ARG_MISSING:
				error = InterpretingError.CONT_FLOW_ARG_MISSING;
				break;
			
			case EOF:
				error = InterpretingError.EOF;
				break;
			
			case INVALID_CON_PART:
				error = InterpretingError.INVALID_CON_PART;
				break;
			
			case INVALID_ASSIGNMENT:
				error = InterpretingError.INVALID_ASSIGNMENT;
				break;
			
			case INVALID_PARAMETER:
				error = InterpretingError.INVALID_AST_NODE;
				break;
		}
		
		if(error == null)
			error = InterpretingError.INVALID_AST_NODE;
		return setErrnoErrorObject(error, node.getMessage() == null?node.getError().getErrorText():node.getMessage(),
				node.getLineNumberFrom(), SCOPE_ID);
	}
	
	/**
	 * @return Returns true if any condition was true and if any block was executed
	 */
	private boolean interpretIfStatementNode(IfStatementNode node, final int SCOPE_ID) {
		List<IfStatementPartNode> ifPartNodes = node.getIfStatementPartNodes();
		if(ifPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Empty if statement", node.getLineNumberFrom(), SCOPE_ID);
			
			return false;
		}
		
		for(IfStatementPartNode ifPartNode:ifPartNodes)
			if(interpretIfStatementPartNode(ifPartNode, SCOPE_ID))
				return true;
		
		return false;
	}
	
	/**
	 * @return Returns true if condition was true and if block was executed
	 */
	private boolean interpretIfStatementPartNode(IfStatementPartNode node, final int SCOPE_ID) {
		try {
			switch(node.getNodeType()) {
				case IF_STATEMENT_PART_IF:
					if(!interpretOperationNode(((IfStatementPartIfNode)node).getCondition(), SCOPE_ID).getBoolean())
						return false;
				case IF_STATEMENT_PART_ELSE:
					interpretAST(node.getIfBody(), SCOPE_ID);
					return true;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		}
		
		return false;
	}
	
	/**
	 * @return Returns true if at least one loop iteration was executed
	 */
	private boolean interpretLoopStatementNode(LoopStatementNode node, final int SCOPE_ID) {
		List<LoopStatementPartNode> loopPartNodes = node.getLoopStatementPartNodes();
		if(loopPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Empty loop statement", node.getLineNumberFrom(), SCOPE_ID);
			
			return false;
		}
		
		for(LoopStatementPartNode loopPartNode:loopPartNodes)
			if(interpretLoopStatementPartNode(loopPartNode, SCOPE_ID))
				return true;
		
		return false;
	}
	
	/**
	 * @return null if neither continue nor break<br>
	 * true if break or continue with level > 1<br>
	 * false if continue for the current level
	 */
	private Boolean interpretLoopContinueAndBreak() {
		if(executionState.stopExecutionFlag) {
			if(executionState.breakContinueCount == 0)
				return true;
			
			//Handle continue and break
			executionState.breakContinueCount -= 1;
			if(executionState.breakContinueCount > 0)
				return true;
			
			executionState.stopExecutionFlag = false;
			
			if(executionState.isContinueStatement)
				return false;
			else
				return true;
		}
		
		return null;
	}
	
	/**
	 * @return Returns true if at least one loop iteration was executed
	 */
	private boolean interpretLoopStatementPartNode(LoopStatementPartNode node, final int SCOPE_ID) {
		boolean flag = false;
		
		try {
			switch(node.getNodeType()) {
				case LOOP_STATEMENT_PART_LOOP:
					while(true) {
						interpretAST(node.getLoopBody(), SCOPE_ID);
						Boolean ret = interpretLoopContinueAndBreak();
						if(ret != null) {
							if(ret)
								return true;
							else
								continue;
						}
					}
				case LOOP_STATEMENT_PART_WHILE:
					while(interpretOperationNode(((LoopStatementPartWhileNode)node).getCondition(), SCOPE_ID).getBoolean()) {
						flag = true;
						
						interpretAST(node.getLoopBody(), SCOPE_ID);
						Boolean ret = interpretLoopContinueAndBreak();
						if(ret != null) {
							if(ret)
								return true;
							else
								continue;
						}
					}
					
					break;
				case LOOP_STATEMENT_PART_UNTIL:
					while(!interpretOperationNode(((LoopStatementPartUntilNode)node).getCondition(), SCOPE_ID).getBoolean()) {
						flag = true;
						
						interpretAST(node.getLoopBody(), SCOPE_ID);
						Boolean ret = interpretLoopContinueAndBreak();
						if(ret != null) {
							if(ret)
								return true;
							else
								continue;
						}
					}
					
					break;
				case LOOP_STATEMENT_PART_REPEAT:
					LoopStatementPartRepeatNode repeatNode = (LoopStatementPartRepeatNode)node;
					DataObject varPointer = interpretNode(null, repeatNode.getVarPointerNode(), SCOPE_ID);
					if(varPointer.getType() != DataType.VAR_POINTER && varPointer.getType() != DataType.NULL) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.repeat needs a variablePointer or a null value for the current iteration variable",
								node.getLineNumberFrom(), SCOPE_ID);
						return false;
					}
					DataObject var = varPointer.getType() == DataType.NULL?null:varPointer.getVarPointer().getVar();
					
					DataObject numberObject = interpretNode(null, repeatNode.getRepeatCountNode(), SCOPE_ID);
					Number number = numberObject == null?null:numberObject.toNumber();
					if(number == null) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.repeat needs a repeat count value",
								node.getLineNumberFrom(), SCOPE_ID);
						return false;
					}
					
					int iterations = number.intValue();
					if(iterations < 0) {
						setErrno(InterpretingError.INVALID_ARGUMENTS, "con.repeat repeat count can not be less than 0",
								node.getLineNumberFrom(), SCOPE_ID);
						return false;
					}
					
					for(int i = 0;i < iterations;i++) {
						flag = true;
						
						if(var != null) {
							if(var.isFinalData() || var.isLangVar())
								setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.repeat current iteration value can not be set",
										node.getLineNumberFrom(), SCOPE_ID);
							else if(var.getTypeConstraint().isTypeAllowed(DataType.INT))
								var.setInt(i);
							else
								setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.repeat current iteration value can not be set",
										node.getLineNumberFrom(), SCOPE_ID);
						}
						
						interpretAST(node.getLoopBody(), SCOPE_ID);
						Boolean ret = interpretLoopContinueAndBreak();
						if(ret != null) {
							if(ret)
								return true;
							else
								continue;
						}
					}
					
					break;
				case LOOP_STATEMENT_PART_FOR_EACH:
					LoopStatementPartForEachNode forEachNode = (LoopStatementPartForEachNode)node;
					varPointer = interpretNode(null, forEachNode.getVarPointerNode(), SCOPE_ID);
					if(varPointer.getType() != DataType.VAR_POINTER) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.foreach needs a variablePointer for the current element variable", node.getLineNumberFrom(), SCOPE_ID);
						return false;
					}
					
					var = varPointer.getVarPointer().getVar();
					
					DataObject compositeOrText = interpretNode(null, forEachNode.getCompositeOrTextNode(), SCOPE_ID);
					if(compositeOrText.getType() == DataType.ARRAY) {
						DataObject[] arr = compositeOrText.getArray();
						for(int i = 0;i < arr.length;i++) {
							flag = true;
							
							if(var != null) {
								if(var.isFinalData() || var.isLangVar()) {
									setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", node.getLineNumberFrom(), SCOPE_ID);
									return false;
								}else {
									var.setData(arr[i]);
								}
							}
							
							interpretAST(node.getLoopBody(), SCOPE_ID);
							Boolean ret = interpretLoopContinueAndBreak();
							if(ret != null) {
								if(ret)
									return true;
								else
									continue;
							}
						}
					}else if(compositeOrText.getType() == DataType.LIST) {
						List<DataObject> list = compositeOrText.getList();
						for(int i = 0;i < list.size();i++) {
							flag = true;
							
							if(var != null) {
								if(var.isFinalData() || var.isLangVar()) {
									setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", node.getLineNumberFrom(), SCOPE_ID);
									return false;
								}else {
									var.setData(list.get(i));
								}
							}
							
							interpretAST(node.getLoopBody(), SCOPE_ID);
							Boolean ret = interpretLoopContinueAndBreak();
							if(ret != null) {
								if(ret)
									return true;
								else
									continue;
							}
						}
					}else if(compositeOrText.getType() == DataType.STRUCT) {
						StructObject struct = compositeOrText.getStruct();
						if(struct.isDefinition()) {
							for(int i = 0;i < struct.getMemberNames().length;i++) {
								flag = true;
								
								if(var != null) {
									if(var.isFinalData() || var.isLangVar())
										setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", node.getLineNumberFrom(), SCOPE_ID);
									else
										var.setText(struct.getMemberNames()[i]);
								}
								
								interpretAST(node.getLoopBody(), SCOPE_ID);
								Boolean ret = interpretLoopContinueAndBreak();
								if(ret != null) {
									if(ret)
										return true;
									else
										continue;
								}
							}
						}else {
							for(int i = 0;i < struct.getMemberNames().length;i++) {
								flag = true;
								
								String memberName = struct.getMemberNames()[i];
								
								if(var != null) {
									if(var.isFinalData() || var.isLangVar())
										setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", node.getLineNumberFrom(), SCOPE_ID);
									else
										var.setStruct(LangCompositeTypes.createPair(new DataObject(memberName), struct.getMember(memberName)));
								}
								
								interpretAST(node.getLoopBody(), SCOPE_ID);
								Boolean ret = interpretLoopContinueAndBreak();
								if(ret != null) {
									if(ret)
										return true;
									else
										continue;
								}
							}
						}
					}else if(compositeOrText.getType() == DataType.TEXT) {
						String text = compositeOrText.getText();
						for(int i = 0;i < text.length();i++) {
							flag = true;
							
							if(var != null) {
								if(var.isFinalData() || var.isLangVar()) {
									setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", node.getLineNumberFrom(), SCOPE_ID);
									return false;
								}else {
									var.setChar(text.charAt(i));
								}
							}
							
							interpretAST(node.getLoopBody(), SCOPE_ID);
							Boolean ret = interpretLoopContinueAndBreak();
							if(ret != null) {
								if(ret)
									return true;
								else
									continue;
							}
						}
					}else {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.foreach needs a composite or a text value to iterate over", node.getLineNumberFrom(), SCOPE_ID);
						return false;
					}
					
					break;
				case LOOP_STATEMENT_PART_ELSE:
					flag = true;
					interpretAST(node.getLoopBody(), SCOPE_ID);
					break;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		}catch(DataTypeConstraintException e) {
			setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), node.getLineNumberFrom(), SCOPE_ID);
			return false;
		}
		
		return flag;
	}
	
	private void interpretLoopStatementContinueBreak(LoopStatementContinueBreakStatement node, final int SCOPE_ID) {
		Node numberNode = node.getNumberNode();
		if(numberNode == null) {
			executionState.breakContinueCount = 1;
		}else {
			DataObject numberObject = interpretNode(null, numberNode, SCOPE_ID);
			Number number = numberObject == null?null:numberObject.toNumber();
			if(number == null) {
				setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con." + (node.isContinueNode()?"continue":"break") + " needs either non value or a level number",
						node.getLineNumberFrom(), SCOPE_ID);
				return;
			}
			
			executionState.breakContinueCount = number.intValue();
			if(executionState.breakContinueCount < 1) {
				executionState.breakContinueCount = 0;
				
				setErrno(InterpretingError.INVALID_ARGUMENTS, "con." + (node.isContinueNode()?"continue":"break") + " the level must be > 0", node.getLineNumberFrom(), SCOPE_ID);
				return;
			}
		}
		
		executionState.isContinueStatement = node.isContinueNode();
		executionState.stopExecutionFlag = true;
	}
	
	private void saveExecutionStopStateToVarAndReset(ExecutionState savedExecutionState) {
		savedExecutionState.stopExecutionFlag = executionState.stopExecutionFlag;
		savedExecutionState.returnedOrThrownValue = executionState.returnedOrThrownValue;
		savedExecutionState.isThrownValue = executionState.isThrownValue;
		savedExecutionState.returnOrThrowStatementLineNumber = executionState.returnOrThrowStatementLineNumber;
		savedExecutionState.breakContinueCount = executionState.breakContinueCount;
		savedExecutionState.isContinueStatement = executionState.isContinueStatement;
		executionState.stopExecutionFlag = false;
		executionState.returnedOrThrownValue = null;
		executionState.isThrownValue = false;
		executionState.returnOrThrowStatementLineNumber = -1;
		executionState.breakContinueCount = 0;
		executionState.isContinueStatement = false;
	}
	/**
	 * @return Returns true if a catch or an else block was executed
	 */
	private boolean interpretTryStatementNode(TryStatementNode node, final int SCOPE_ID) {
		List<TryStatementPartNode> tryPartNodes = node.getTryStatementPartNodes();
		if(tryPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Empty try statement", node.getLineNumberFrom(), SCOPE_ID);
			
			return false;
		}
		
		ExecutionState savedExecutionState = new ExecutionState();
		
		TryStatementPartNode tryPart = tryPartNodes.get(0);
		if(tryPart.getNodeType() != NodeType.TRY_STATEMENT_PART_TRY && tryPart.getNodeType() != NodeType.TRY_STATEMENT_PART_SOFT_TRY &&
		tryPart.getNodeType() != NodeType.TRY_STATEMENT_PART_NON_TRY) {
			setErrno(InterpretingError.INVALID_AST_NODE, "First part of try statement was no try nor soft try nor non try part", node.getLineNumberFrom(), SCOPE_ID);
			
			return false;
		}
		interpretTryStatementPartNode(tryPart, SCOPE_ID);
		
		if(executionState.stopExecutionFlag)
			saveExecutionStopStateToVarAndReset(savedExecutionState);
		
		boolean flag = false;
		if(savedExecutionState.stopExecutionFlag && executionState.tryThrownError != null) {
			List<TryStatementPartNode> catchParts = new LinkedList<>();
			for(int i = 1;i < tryPartNodes.size();i++) {
				TryStatementPartNode tryPartNode = tryPartNodes.get(i);
				if(tryPartNode.getNodeType() != NodeType.TRY_STATEMENT_PART_CATCH)
					break;
				
				catchParts.add(tryPartNode);
			}
			
			for(TryStatementPartNode catchPart:catchParts) {
				if(flag = interpretTryStatementPartNode(catchPart, SCOPE_ID)) {
					if(executionState.stopExecutionFlag) {
						saveExecutionStopStateToVarAndReset(savedExecutionState);
					}else {
						//Reset saved execution state because the reason of the execution stop was handled by the catch block
						savedExecutionState = new ExecutionState();
						executionState.tryThrownError = null;
						
						//Error was handled and (the try statement is the most outer try statement or no other error was thrown): reset $LANG_ERRNO
						getAndClearErrnoErrorObject(SCOPE_ID);
					}
					
					break;
				}
			}
		}
		
		boolean savedStopExecutionFlagForElseBlock = savedExecutionState.stopExecutionFlag;
		
		//Cancel execution stop because of error if most outer try block is reached or if inside a nontry statement
		if(savedExecutionState.stopExecutionFlag && (savedExecutionState.tryThrownError == null || savedExecutionState.tryBlockLevel == 0 ||
				(savedExecutionState.isSoftTry && savedExecutionState.tryBodyScopeID != SCOPE_ID)))
			savedExecutionState.stopExecutionFlag = false;
		
		if(!flag && !savedStopExecutionFlagForElseBlock) {
			TryStatementPartNode elsePart = null;
			if(!flag && tryPartNodes.size() > 1) {
				if(tryPartNodes.get(tryPartNodes.size() - 2).getNodeType() == NodeType.TRY_STATEMENT_PART_ELSE)
					elsePart = tryPartNodes.get(tryPartNodes.size() - 2);
				if(tryPartNodes.get(tryPartNodes.size() - 1).getNodeType() == NodeType.TRY_STATEMENT_PART_ELSE)
					elsePart = tryPartNodes.get(tryPartNodes.size() - 1);
			}
			if(elsePart != null) {
				flag = interpretTryStatementPartNode(elsePart, SCOPE_ID);
				
				if(executionState.stopExecutionFlag)
					saveExecutionStopStateToVarAndReset(savedExecutionState);
			}
		}
		
		TryStatementPartNode finallyPart = null;
		if(tryPartNodes.size() > 1 && tryPartNodes.get(tryPartNodes.size() - 1).getNodeType() == NodeType.TRY_STATEMENT_PART_FINALLY)
			finallyPart = tryPartNodes.get(tryPartNodes.size() - 1);
		
		if(finallyPart != null)
			interpretTryStatementPartNode(finallyPart, SCOPE_ID);
		
		//Reset saved execution flag to stop execution if finally has not set the stop execution flag
		if(!executionState.stopExecutionFlag) {
			executionState.stopExecutionFlag = savedExecutionState.stopExecutionFlag;
			executionState.returnedOrThrownValue = savedExecutionState.returnedOrThrownValue;
			executionState.isThrownValue = savedExecutionState.isThrownValue;
			executionState.returnOrThrowStatementLineNumber = savedExecutionState.returnOrThrowStatementLineNumber;
			executionState.breakContinueCount = savedExecutionState.breakContinueCount;
			executionState.isContinueStatement = savedExecutionState.isContinueStatement;
		}
		
		return flag;
	}
	
	/**
	 * @return Returns true if a catch or an else block was executed
	 */
	private boolean interpretTryStatementPartNode(TryStatementPartNode node, final int SCOPE_ID) {
		boolean flag = false;
		
		try {
			switch(node.getNodeType()) {
				case TRY_STATEMENT_PART_TRY:
				case TRY_STATEMENT_PART_SOFT_TRY:
					executionState.tryThrownError = null;
					executionState.tryBlockLevel++;
					boolean isSoftTryOld = executionState.isSoftTry;
					executionState.isSoftTry = node.getNodeType() == NodeType.TRY_STATEMENT_PART_SOFT_TRY;
					int oldTryBlockScopeID = executionState.tryBodyScopeID;
					executionState.tryBodyScopeID = SCOPE_ID;
					
					try {
						interpretAST(node.getTryBody(), SCOPE_ID);
					}finally {
						executionState.tryBlockLevel--;
						executionState.isSoftTry = isSoftTryOld;
						executionState.tryBodyScopeID = oldTryBlockScopeID;
					}
					break;
				case TRY_STATEMENT_PART_NON_TRY:
					executionState.tryThrownError = null;
					int oldTryBlockLevel = executionState.tryBlockLevel;
					executionState.tryBlockLevel = 0;
					isSoftTryOld = executionState.isSoftTry;
					executionState.isSoftTry = false;
					oldTryBlockScopeID = executionState.tryBodyScopeID;
					executionState.tryBodyScopeID = 0;
					
					try {
						interpretAST(node.getTryBody(), SCOPE_ID);
					}finally {
						executionState.tryBlockLevel = oldTryBlockLevel;
						executionState.isSoftTry = isSoftTryOld;
						executionState.tryBodyScopeID = oldTryBlockScopeID;
					}
					break;
				case TRY_STATEMENT_PART_CATCH:
					if(executionState.tryThrownError == null)
						return false;
					
					TryStatementPartCatchNode catchNode = (TryStatementPartCatchNode)node;
					if(catchNode.getExpections() != null) {
						if(catchNode.getExpections().size() == 0) {
							setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Empty catch part \"catch()\" is not allowed!\n"
									+ "For checking all warnings \"catch\" without \"()\" should be used", node.getLineNumberFrom(), SCOPE_ID);
							
							return false;
						}
						
						List<DataObject> catchErrors = new LinkedList<>();
						List<DataObject> interpretedNodes = new LinkedList<>();
						int foundErrorIndex = -1;
						DataObject previousDataObject = null;
						for(Node argument:catchNode.getExpections()) {
							if(argument.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
								try {
									Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)argument, previousDataObject, SCOPE_ID);
									if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
										interpretedNodes.remove(interpretedNodes.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
										interpretedNodes.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, SCOPE_ID));
									}else {
										interpretedNodes.add(interpretNode(null, ret, SCOPE_ID));
									}
								}catch(ClassCastException e) {
									interpretedNodes.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID));
								}
								
								previousDataObject = interpretedNodes.get(interpretedNodes.size() - 1);
								
								continue;
							}
							
							DataObject argumentValue = interpretNode(null, argument, SCOPE_ID);
							if(argumentValue == null) {
								previousDataObject = null;
								
								continue;
							}
							
							interpretedNodes.add(argumentValue);
							previousDataObject = argumentValue;
						}
						List<DataObject> errorList = LangUtils.combineArgumentsWithoutArgumentSeparators(interpretedNodes);
						for(DataObject dataObject:errorList) {
							if(dataObject.getType() != DataType.ERROR) {
								setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Variable with type other than " + DataType.ERROR + " in catch statement",
										node.getLineNumberFrom(), SCOPE_ID);
								
								continue;
							}
							
							if(dataObject.getError().getInterprettingError() == executionState.tryThrownError)
								foundErrorIndex = catchErrors.size();
							
							catchErrors.add(dataObject);
						}
						
						if(foundErrorIndex == -1)
							return false;
					}
					
					flag = true;
					
					interpretAST(node.getTryBody(), SCOPE_ID);
					break;
				case TRY_STATEMENT_PART_ELSE:
					if(executionState.tryThrownError != null)
						return false;
					
					flag = true;
					
					interpretAST(node.getTryBody(), SCOPE_ID);
					break;
				case TRY_STATEMENT_PART_FINALLY:
					interpretAST(node.getTryBody(), SCOPE_ID);
					break;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		}
		
		return flag;
	}
	
	private DataObject interpretOperationNode(OperationNode node, final int SCOPE_ID) {
		DataObject leftSideOperand = interpretNode(null, node.getLeftSideOperand(), SCOPE_ID);
		DataObject middleOperand = (!node.getOperator().isTernary() || node.getOperator().isLazyEvaluation())?null:interpretNode(null, node.getMiddleOperand(), SCOPE_ID);
		DataObject rightSideOperand = (node.getOperator().isUnary() || node.getOperator().isLazyEvaluation())?null:interpretNode(null, node.getRightSideOperand(), SCOPE_ID);
		
		//Forward Java null values for NON operators
		if(leftSideOperand == null && (node.getOperator() == Operator.NON || node.getOperator() == Operator.CONDITIONAL_NON || node.getOperator() == Operator.MATH_NON)) {
			return null;
		}
		
		if(leftSideOperand == null || (!node.getOperator().isLazyEvaluation() && ((!node.getOperator().isUnary() && rightSideOperand == null) ||
		(node.getOperator().isTernary() && middleOperand == null))))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", node.getLineNumberFrom(), SCOPE_ID);
		
		if(node.getOperatorType() == OperatorType.ALL) {
			DataObject output;
			switch(node.getOperator()) {
				//Binary
				case COMMA:
					return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
							"The COMMA operator is parser-only (If you meant the text value of \",\", you must escape the COMMA operator: \"\\,\")",
							node.getLineNumberFrom(), SCOPE_ID);
				case OPTIONAL_GET_ITEM:
					output = operators.opOptionalGetItem(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case GET_ITEM:
					output = operators.opGetItem(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case MEMBER_ACCESS_POINTER:
					if(leftSideOperand.getType() != DataType.VAR_POINTER)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
								"The left side operand of the member access pointer operator (\"" + node.getOperator().getSymbol() + "\") must be a pointer",
								node.getLineNumberFrom(), SCOPE_ID);
					
					leftSideOperand = leftSideOperand.getVarPointer().getVar();
					if(leftSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_PTR, node.getLineNumberFrom(), SCOPE_ID);
					
					if(leftSideOperand.getType() != DataType.STRUCT)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
								"The left side operand of the member access pointer operator (\"" + node.getOperator().getSymbol() + "\") must be a pointer pointing to a composite type",
								node.getLineNumberFrom(), SCOPE_ID);
					
					return interpretNode(leftSideOperand, node.getRightSideOperand(), SCOPE_ID);
				case MEMBER_ACCESS:
					if(leftSideOperand.getType() != DataType.STRUCT)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
								"The left side operand of the member access operator (\"" + node.getOperator().getSymbol() + "\") must be a composite type",
								node.getLineNumberFrom(), SCOPE_ID);
					
					return interpretNode(leftSideOperand, node.getRightSideOperand(), SCOPE_ID);
				case OPTIONAL_MEMBER_ACCESS:
					if(leftSideOperand.getType() == DataType.NULL || leftSideOperand.getType() == DataType.VOID)
						return new DataObject().setVoid();
					
					if(leftSideOperand.getType() != DataType.STRUCT)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS,
								"The left side operand of the member access operator (\"" + node.getOperator().getSymbol() + "\") must be a composite type",
								node.getLineNumberFrom(), SCOPE_ID);
					
					return interpretNode(leftSideOperand, node.getRightSideOperand(), SCOPE_ID);
				
				default:
					return null;
			}
			
			if(output == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The \"" + node.getOperator().getSymbol() + "\" operator is not defined for " + leftSideOperand.getType().name() + (
					node.getOperator().isTernary()?", " + middleOperand.getType().name() + ",":"") + (!node.getOperator().isUnary()?" and " + rightSideOperand.getType().name():""),
						node.getLineNumberFrom(), SCOPE_ID);
			
			return output;
		}else if(node.getOperatorType() == OperatorType.GENERAL) {
			DataObject output;
			switch(node.getOperator()) {
				//Unary
				case NON:
					return leftSideOperand;
				case LEN:
					output = operators.opLen(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case DEEP_COPY:
					output = operators.opDeepCopy(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				
				//Binary
				case CONCAT:
					output = operators.opConcat(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case SPACESHIP:
					output = operators.opSpaceship(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case ELVIS:
					if(leftSideOperand.getBoolean())
						return leftSideOperand;
					
					rightSideOperand = interpretNode(null, node.getRightSideOperand(), SCOPE_ID);
					if(rightSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", node.getLineNumberFrom(), SCOPE_ID);
					return rightSideOperand;
				case NULL_COALESCING:
					if(leftSideOperand.getType() != DataType.NULL && leftSideOperand.getType() != DataType.VOID)
						return leftSideOperand;
					
					rightSideOperand = interpretNode(null, node.getRightSideOperand(), SCOPE_ID);
					if(rightSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", node.getLineNumberFrom(), SCOPE_ID);
					return rightSideOperand;
				
				//Ternary
				case INLINE_IF:
					DataObject operand = leftSideOperand.getBoolean()?interpretNode(null, node.getMiddleOperand(), SCOPE_ID):interpretNode(null, node.getRightSideOperand(), SCOPE_ID);
					
					if(operand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", node.getLineNumberFrom(), SCOPE_ID);
					return operand;
				
				default:
					return null;
			}
			
			if(output == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The \"" + node.getOperator().getSymbol() + "\" operator is not defined for " + leftSideOperand.getType().name() + (
					node.getOperator().isTernary()?", " + middleOperand.getType().name() + ",":"") + (!node.getOperator().isUnary()?" and " + rightSideOperand.getType().name():""),
						node.getLineNumberFrom(), SCOPE_ID);
			
			return output;
		}else if(node.getOperatorType() == OperatorType.MATH) {
			DataObject output = null;
			
			switch(node.getOperator()) {
				//Unary
				case MATH_NON:
					output = leftSideOperand;
					break;
				case POS:
					output = operators.opPos(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case INV:
					output = operators.opInv(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case BITWISE_NOT:
					output = operators.opNot(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case INC:
					output = operators.opInc(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case DEC:
					output = operators.opDec(leftSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				
				//Binary
				case POW:
					output = operators.opPow(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case MUL:
					output = operators.opMul(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case DIV:
					output = operators.opDiv(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case TRUNC_DIV:
					output = operators.opTruncDiv(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case FLOOR_DIV:
					output = operators.opFloorDiv(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case CEIL_DIV:
					output = operators.opCeilDiv(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case MOD:
					output = operators.opMod(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case ADD:
					output = operators.opAdd(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case SUB:
					output = operators.opSub(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case LSHIFT:
					output = operators.opLshift(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case RSHIFT:
					output = operators.opRshift(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case RZSHIFT:
					output = operators.opRzshift(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case BITWISE_AND:
					output = operators.opAnd(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case BITWISE_XOR:
					output = operators.opXor(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				case BITWISE_OR:
					output = operators.opOr(leftSideOperand, rightSideOperand, node.getLineNumberFrom(), SCOPE_ID);
					break;
				
				default:
					break;
			}
			
			if(output == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The \"" + node.getOperator().getSymbol() + "\" operator is not defined for " + leftSideOperand.getType().name() + (
					node.getOperator().isTernary()?", " + middleOperand.getType().name() + ",":"") + (!node.getOperator().isUnary()?" and " + rightSideOperand.getType().name():""),
						node.getLineNumberFrom(), SCOPE_ID);
			
			return output;
		}else if(node.getOperatorType() == OperatorType.CONDITION) {
			boolean conditionOutput = false;
			
			switch(node.getOperator()) {
				//Unary (Logical operators)
				case CONDITIONAL_NON:
				case NOT:
					conditionOutput = leftSideOperand.getBoolean();
					
					if(node.getOperator() == Operator.NOT)
						conditionOutput = !conditionOutput;
					break;
				
				//Binary (Logical operators)
				case AND:
					boolean leftSideOperandBoolean = leftSideOperand.getBoolean();
					if(leftSideOperandBoolean) {
						rightSideOperand = interpretNode(null, node.getRightSideOperand(), SCOPE_ID);
						if(rightSideOperand == null)
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", node.getLineNumberFrom(), SCOPE_ID);
						conditionOutput = rightSideOperand.getBoolean();
					}else {
						conditionOutput = false;
					}
					break;
				case OR:
					leftSideOperandBoolean = leftSideOperand.getBoolean();
					if(leftSideOperandBoolean) {
						conditionOutput = true;
					}else {
						rightSideOperand = interpretNode(null, node.getRightSideOperand(), SCOPE_ID);
						if(rightSideOperand == null)
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", node.getLineNumberFrom(), SCOPE_ID);
						conditionOutput = rightSideOperand.getBoolean();
					}
					break;
				
				//Binary (Comparison operators)
				case INSTANCE_OF:
					DataObject dataObject = leftSideOperand;
					DataObject typeObject = rightSideOperand;
					
					if(typeObject.getType() == DataType.TYPE) {
						conditionOutput = leftSideOperand.getType() == rightSideOperand.getTypeValue();
						
						break;
					}
					
					if(typeObject.getType() == DataType.STRUCT) {
						StructObject dataStruct = dataObject.getStruct();
						StructObject typeStruct = typeObject.getStruct();
						
						if(dataStruct.isDefinition())
							return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The first operand of the \"" +
									node.getOperator().getSymbol() + "\" operator may not be a definition struct", node.getLineNumberFrom(), SCOPE_ID);
						
						if(!typeStruct.isDefinition())
							return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The second operand of the \"" +
									node.getOperator().getSymbol() + "\" operator must be a definition struct", node.getLineNumberFrom(), SCOPE_ID);
						
						conditionOutput = dataStruct.getStructBaseDefinition().equals(typeStruct);
						
						break;
					}
					
					return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The second operand of the \"" +
							node.getOperator().getSymbol() + "\" operator must be of type " + DataType.TYPE + " or " +
							DataType.STRUCT, node.getLineNumberFrom(), SCOPE_ID);
				case EQUALS:
				case NOT_EQUALS:
					conditionOutput = leftSideOperand.isEquals(rightSideOperand);
					
					if(node.getOperator() == Operator.NOT_EQUALS)
						conditionOutput = !conditionOutput;
					break;
				case MATCHES:
				case NOT_MATCHES:
					try {
						conditionOutput = LangRegEx.matches(leftSideOperand.getText(), rightSideOperand.getText());
					}catch(InvalidPaternSyntaxException e) {
						return setErrnoErrorObject(InterpretingError.INVALID_REGEX_SYNTAX, e.getMessage(), node.getLineNumberFrom(), SCOPE_ID);
					}
					
					if(node.getOperator() == Operator.NOT_MATCHES)
						conditionOutput = !conditionOutput;
					break;
				case STRICT_EQUALS:
				case STRICT_NOT_EQUALS:
					conditionOutput = leftSideOperand.isStrictEquals(rightSideOperand);
					
					if(node.getOperator() == Operator.STRICT_NOT_EQUALS)
						conditionOutput = !conditionOutput;
					break;
				case LESS_THAN:
					conditionOutput = leftSideOperand.isLessThan(rightSideOperand);
					break;
				case GREATER_THAN:
					conditionOutput = leftSideOperand.isGreaterThan(rightSideOperand);
					break;
				case LESS_THAN_OR_EQUALS:
					conditionOutput = leftSideOperand.isLessThanOrEquals(rightSideOperand);
					break;
				case GREATER_THAN_OR_EQUALS:
					conditionOutput = leftSideOperand.isGreaterThanOrEquals(rightSideOperand);
					break;
				
				default:
					break;
			}
			
			return new DataObject().setBoolean(conditionOutput);
		}
		
		return null;
	}
	
	private void interpretReturnNode(ReturnNode node, final int SCOPE_ID) {
		Node returnValueNode = node.getReturnValue();
		
		executionState.returnedOrThrownValue = returnValueNode == null?null:interpretNode(null, returnValueNode, SCOPE_ID);
		executionState.isThrownValue = false;
		executionState.returnOrThrowStatementLineNumber = node.getLineNumberFrom();
		executionState.stopExecutionFlag = true;
	}
	
	private void interpretThrowNode(ThrowNode node, final int SCOPE_ID) {
		Node throwValueNode = node.getThrowValue();
		
		DataObject errorObject = interpretNode(null, throwValueNode, SCOPE_ID);
		if(errorObject == null || errorObject.getType() != DataType.ERROR)
			executionState.returnedOrThrownValue = new DataObject().setError(new ErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE));
		else
			executionState.returnedOrThrownValue = errorObject;
		executionState.isThrownValue = true;
		executionState.returnOrThrowStatementLineNumber = node.getLineNumberFrom();
		executionState.stopExecutionFlag = true;
		
		if(executionState.returnedOrThrownValue.getError().getErrno() > 0 && executionState.tryBlockLevel > 0 && (!executionState.isSoftTry || executionState.tryBodyScopeID == SCOPE_ID)) {
			executionState.tryThrownError = executionState.returnedOrThrownValue.getError().getInterprettingError();
			executionState.stopExecutionFlag = true;
		}
	}
	
	private void interpretLangDataAndExecutionFlags(String langDataExecutionFlag, DataObject value, int lineNumber, final int SCOPE_ID) {
		if(value == null)
			value = new DataObject(); //Set value to null data object
		
		switch(langDataExecutionFlag) {
			//Data
			case "lang.version":
				String langVer = value.getText();
				Integer compVer = LangUtils.compareVersions(LangInterpreter.VERSION, langVer);
				if(compVer == null) {
					setErrno(InterpretingError.LANG_VER_ERROR, "lang.version has an invalid format", lineNumber, SCOPE_ID);
					
					return;
				}
				
				if(compVer > 0)
					setErrno(InterpretingError.LANG_VER_WARNING, "Lang file's version is older than this version! The Lang file could not be executed correctly",
							lineNumber, SCOPE_ID);
				else if(compVer < 0)
					setErrno(InterpretingError.LANG_VER_ERROR, "Lang file's version is newer than this version! The Lang file will not be executed correctly!",
							lineNumber, SCOPE_ID);
				
				break;
			
			case "lang.name":
				//Nothing to do
				break;
			
			//Flags
			case "lang.allowTermRedirect":
				Number number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.allowTermRedirect flag!", lineNumber, SCOPE_ID);
					
					return;
				}
				executionFlags.allowTermRedirect = number.intValue() != 0;
				break;
			case "lang.errorOutput":
				number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.errorOutput flag!", lineNumber, SCOPE_ID);
					
					return;
				}
				executionFlags.errorOutput = ExecutionFlags.ErrorOutputFlag.getErrorFlagFor(number.intValue());
				break;
			case "lang.test":
				number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.test flag!", lineNumber, SCOPE_ID);
					
					return;
				}
				
				boolean langTestNewValue = number.intValue() != 0;
				if(executionFlags.langTest && !langTestNewValue) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "The lang.test flag can not be changed if it was once set to true!", lineNumber, SCOPE_ID);
					
					return;
				}
				
				executionFlags.langTest = langTestNewValue;
				break;
			case "lang.rawVariableNames":
				number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.rawVariableNames flag!", lineNumber, SCOPE_ID);
					
					return;
				}
				executionFlags.rawVariableNames = number.intValue() != 0;
				break;
			default:
				setErrno(InterpretingError.INVALID_EXEC_FLAG_DATA, "\"" + langDataExecutionFlag + "\" is neither Lang data nor an execution flag", lineNumber, SCOPE_ID);
		}
	}
	private DataObject interpretAssignmentNode(AssignmentNode node, final int SCOPE_ID) {
		DataObject rvalue = interpretNode(null, node.getRvalue(), SCOPE_ID);
		if(rvalue == null)
			rvalue = new DataObject(); //Set rvalue to null data object
		
		Node lvalueNode = node.getLvalue();
		if(lvalueNode == null)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Assignment without lvalue", node.getLineNumberFrom(), SCOPE_ID);
		
		try {
			if(lvalueNode.getNodeType() == NodeType.OPERATION || lvalueNode.getNodeType() == NodeType.CONDITION ||
			lvalueNode.getNodeType() == NodeType.MATH) {
				//Composite type lvalue assignment (MEMBER_ACCESS and GET_ITEM)
				OperationNode operationNode = (OperationNode)lvalueNode;
				while((operationNode.getOperator() == Operator.NON ||
				operationNode.getOperator() == Operator.CONDITIONAL_NON ||
				operationNode.getOperator() == Operator.MATH_NON) &&
				operationNode.getLeftSideOperand() instanceof OperationNode)
					operationNode = (OperationNode)operationNode.getLeftSideOperand();
				
				boolean isMemberAccessPointerOperator = operationNode.getOperator() == Operator.MEMBER_ACCESS_POINTER;
				if(isMemberAccessPointerOperator || operationNode.getOperator() == Operator.MEMBER_ACCESS) {
					DataObject lvalue = interpretOperationNode(operationNode, SCOPE_ID);
					if(lvalue == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
								"Invalid arguments for member access" + (isMemberAccessPointerOperator?" pointer":""),
								node.getLineNumberFrom(), SCOPE_ID);
					
					String variableName = lvalue.getVariableName();
					if(variableName == null)
						return setErrnoErrorObject(InterpretingError.INVALID_ASSIGNMENT,
								"Anonymous values can not be changed", node.getLineNumberFrom(), SCOPE_ID);
					
					if(lvalue.isFinalData() || lvalue.isLangVar())
						return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE,
								node.getLineNumberFrom(), SCOPE_ID);
					
					try {
						lvalue.setData(rvalue);
					}catch(DataTypeConstraintViolatedException e) {
						return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE,
								"Incompatible type for rvalue in assignment", node.getLineNumberFrom(), SCOPE_ID);
					}
					
					return rvalue;
				}else if(operationNode.getOperator() == Operator.GET_ITEM) {
					DataObject compositeTypeObject = interpretNode(null, operationNode.getLeftSideOperand(), SCOPE_ID);
					if(compositeTypeObject == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing composite type operand for set item",
								node.getLineNumberFrom(), SCOPE_ID);
					
					DataObject indexObject = interpretNode(null, operationNode.getRightSideOperand(), SCOPE_ID);
					if(indexObject == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing index operand for set item",
								node.getLineNumberFrom(), SCOPE_ID);
					
					DataObject ret = operators.opSetItem(compositeTypeObject, indexObject, rvalue, operationNode.getLineNumberFrom(), SCOPE_ID);
					if(ret == null)
						return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE,
								"Incompatible type for lvalue (composite type + index) or rvalue in assignment",
								node.getLineNumberFrom(), SCOPE_ID);
					
					return rvalue;
				}
				
				//Continue in "Lang translation" in the switch statement below
			}
			
			switch(lvalueNode.getNodeType()) {
				//Variable assignment
				case UNPROCESSED_VARIABLE_NAME:
					UnprocessedVariableNameNode variableNameNode = (UnprocessedVariableNameNode)lvalueNode;
					String variableName = variableNameNode.getVariableName();
					
					boolean isModuleVariable = variableName.startsWith("[[");
					String moduleName = null;
					if(isModuleVariable) {
						int indexModuleIdientifierEnd = variableName.indexOf("]]::");
						if(indexModuleIdientifierEnd == -1) {
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", node.getLineNumberFrom(), SCOPE_ID);
						}
						
						moduleName = variableName.substring(2, indexModuleIdientifierEnd);
						if(!isAlphaNummericWithUnderline(moduleName)) {
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid module name", node.getLineNumberFrom(), SCOPE_ID);
						}
						
						variableName = variableName.substring(indexModuleIdientifierEnd + 4);
					}
					
					if(isVarNameFullWithoutPrefix(variableName) || isVarNamePtrAndDereferenceWithoutPrefix(variableName)) {
						if(variableName.indexOf("[") == -1) { //Pointer redirection is no longer supported
							boolean[] flags = new boolean[] {false, false};
							DataObject lvalue = getOrCreateDataObjectFromVariableName(null, moduleName, variableName, false, true, true, flags,
									node.getLineNumberFrom(), SCOPE_ID);
							if(flags[0])
								return lvalue; //Forward error from getOrCreateDataObjectFromVariableName()
							
							variableName = lvalue.getVariableName();
							if(variableName == null) {
								return setErrnoErrorObject(InterpretingError.INVALID_ASSIGNMENT, "Anonymous values can not be changed", node.getLineNumberFrom(), SCOPE_ID);
							}
							
							if(lvalue.isFinalData() || lvalue.isLangVar()) {
								if(flags[1])
									data.get(SCOPE_ID).var.remove(variableName);
								
								return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, node.getLineNumberFrom(), SCOPE_ID);
							}
							
							try {
								lvalue.setData(rvalue);
							}catch(DataTypeConstraintViolatedException e) {
								if(flags[1])
									data.get(SCOPE_ID).var.remove(variableName);
								
								return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, "Incompatible type for rvalue in assignment", node.getLineNumberFrom(), SCOPE_ID);
							}
							
							if(variableName.startsWith("fp.")) {
								final String functionNameCopy = variableName.substring(3);
								Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
									return functionNameCopy.equals(entry.getKey());
								}).findFirst();
								
								if(ret.isPresent())
									setErrno(InterpretingError.VAR_SHADOWING_WARNING, "\"" + variableName + "\" shadows a predfined, linker, or external function",
											node.getLineNumberFrom(), SCOPE_ID);
							}
							break;
						}
					}
					//Fall-through to "Lang translation" if variableName is not valid
				
				//Lang translation
				case ASSIGNMENT:
				case CHAR_VALUE:
				case CONDITION:
				case MATH:
				case OPERATION:
				case DOUBLE_VALUE:
				case ESCAPE_SEQUENCE:
				case FLOAT_VALUE:
				case FUNCTION_CALL:
				case FUNCTION_CALL_PREVIOUS_NODE_VALUE:
				case FUNCTION_DEFINITION:
				case IF_STATEMENT:
				case IF_STATEMENT_PART_ELSE:
				case IF_STATEMENT_PART_IF:
				case LOOP_STATEMENT:
				case LOOP_STATEMENT_PART_WHILE:
				case LOOP_STATEMENT_PART_UNTIL:
				case LOOP_STATEMENT_PART_REPEAT:
				case LOOP_STATEMENT_PART_FOR_EACH:
				case LOOP_STATEMENT_PART_LOOP:
				case LOOP_STATEMENT_PART_ELSE:
				case LOOP_STATEMENT_CONTINUE_BREAK:
				case TRY_STATEMENT:
				case TRY_STATEMENT_PART_TRY:
				case TRY_STATEMENT_PART_SOFT_TRY:
				case TRY_STATEMENT_PART_NON_TRY:
				case TRY_STATEMENT_PART_CATCH:
				case TRY_STATEMENT_PART_ELSE:
				case TRY_STATEMENT_PART_FINALLY:
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
				case ARRAY:
				case STRUCT_DEFINITION:
					DataObject translationKeyDataObject = interpretNode(null, lvalueNode, SCOPE_ID);
					if(translationKeyDataObject == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid translationKey", node.getLineNumberFrom(), SCOPE_ID);
					
					String translationKey = translationKeyDataObject.getText();
					if(translationKey.startsWith("lang."))
						interpretLangDataAndExecutionFlags(translationKey, rvalue, node.getLineNumberFrom(), SCOPE_ID);
					
					data.get(SCOPE_ID).lang.put(translationKey, rvalue.getText());
					break;
					
				case GENERAL:
				case ARGUMENT_SEPARATOR:
					return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Neither lvalue nor translationKey", node.getLineNumberFrom(), SCOPE_ID);
			}
		}catch(ClassCastException e) {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		}
		
		return rvalue;
	}
	
	/**
	 * Will create a variable if doesn't exist or returns an error object, or returns null if shouldCreateDataObject is set to false and variable doesn't exist
	 * @param supportsPointerReferencing If true, this node will return pointer reference as DataObject<br>
	 *                                   (e.g. $[abc] is not in variableNames, but $abc is -> $[abc] will return a DataObject)
	 * @param flags Will set by this method in format: [error, created]
	 */
	private DataObject getOrCreateDataObjectFromVariableName(DataObject compositeType, String moduleName, String variableName, boolean supportsPointerReferencing,
	boolean supportsPointerDereferencing, boolean shouldCreateDataObject, final boolean[] flags, int lineNumber, final int SCOPE_ID) {
		Map<String, DataObject> variables;
		if(compositeType != null) {
			if(compositeType.getType() != DataType.STRUCT)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Invalid composite type", lineNumber, SCOPE_ID);
			
			variables = new HashMap<>();
			try {
				for(String memberName:compositeType.getStruct().getMemberNames())
					variables.put(memberName, compositeType.getStruct().getMember(memberName));
			}catch(DataTypeConstraintException e) {
				return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), lineNumber, SCOPE_ID);
			}
		}else if(moduleName == null) {
			variables = data.get(SCOPE_ID).var;
		}else {
			LangModule module = modules.get(moduleName);
			if(module == null) {
				if(flags != null && flags.length == 2)
					flags[0] = true;
				
				return setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" is not loaded!", lineNumber, SCOPE_ID);
			}
			
			variables = module.getExportedVariables();
		}
		
		DataObject ret = variables.get(variableName);
		if(ret != null)
			return ret;
		
		if(supportsPointerDereferencing && variableName.contains("*")) {
			int index = variableName.indexOf('*');
			String referencedVariableName = variableName.substring(0, index) + variableName.substring(index + 1);
			DataObject referencedVariable = getOrCreateDataObjectFromVariableName(compositeType, moduleName, referencedVariableName,
					supportsPointerReferencing, true, false, flags, lineNumber, SCOPE_ID);
			if(referencedVariable == null) {
				if(flags != null && flags.length == 2)
					flags[0] = true;
				return setErrnoErrorObject(InterpretingError.INVALID_PTR, lineNumber, SCOPE_ID);
			}
			
			if(referencedVariable.getType() == DataType.VAR_POINTER)
				return referencedVariable.getVarPointer().getVar();
			
			return new DataObject(); //If no var pointer was dereferenced, return null data object
		}
		
		if(supportsPointerReferencing && variableName.contains("[") && variableName.contains("]")) { //Check referenced variable name
			int indexOpeningBracket = variableName.indexOf("[");
			int indexMatchingBracket = LangUtils.getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
			if(indexMatchingBracket != variableName.length() - 1) {
				if(flags != null && flags.length == 2)
					flags[0] = true;
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Non matching referencing brackets", lineNumber, SCOPE_ID);
			}
			
			String dereferencedVariableName = variableName.substring(0, indexOpeningBracket) + variableName.substring(indexOpeningBracket + 1, indexMatchingBracket);
			DataObject dereferencedVariable = getOrCreateDataObjectFromVariableName(compositeType, moduleName, dereferencedVariableName,
					true, false, false, flags, lineNumber, SCOPE_ID);
			if(dereferencedVariable != null)
				return new DataObject().setVarPointer(new VarPointerObject(dereferencedVariable));
			
			if(shouldCreateDataObject) {
				if(flags != null && flags.length == 2)
					flags[0] = true;
				
				return setErrnoErrorObject(InterpretingError.INVALID_PTR, "Pointer redirection is not supported", lineNumber, SCOPE_ID);
			}
		}
		
		if(!shouldCreateDataObject)
			return null;
		
		//Variable creation if possible
		if(compositeType != null || moduleName != null || isLangVarOrLangVarPointerRedirectionWithoutPrefix(variableName)) {
			if(flags != null && flags.length == 2)
				flags[0] = true;
			
			if(compositeType != null)
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, "Composite type members can not be created", lineNumber, SCOPE_ID);
			else if(moduleName == null)
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, lineNumber, SCOPE_ID);
			else
				return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, "Module variables can not be created", lineNumber, SCOPE_ID);
		}
		
		if(flags != null && flags.length == 2)
			flags[1] = true;
		
		DataObject dataObject = new DataObject().setVariableName(variableName);
		data.get(SCOPE_ID).var.put(variableName, dataObject);
		return dataObject;
	}
	/**
	 * Will create a variable if doesn't exist or returns an error object
	 */
	private DataObject interpretVariableNameNode(DataObject compositeType, VariableNameNode node, final int SCOPE_ID) {
		String variableName = node.getVariableName();
		
		boolean isModuleVariable = compositeType == null && variableName.startsWith("[[");
		String moduleName = null;
		if(isModuleVariable) {
			int indexModuleIdientifierEnd = variableName.indexOf("]]::");
			if(indexModuleIdientifierEnd == -1) {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", node.getLineNumberFrom(), SCOPE_ID);
			}
			
			moduleName = variableName.substring(2, indexModuleIdientifierEnd);
			if(!isAlphaNummericWithUnderline(moduleName)) {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid module name", node.getLineNumberFrom(), SCOPE_ID);
			}
			
			variableName = variableName.substring(indexModuleIdientifierEnd + 4);
		}
		
		if(!isVarNameFullWithFuncsWithoutPrefix(variableName) && !isVarNamePtrAndDereferenceWithoutPrefix(variableName))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", node.getLineNumberFrom(), SCOPE_ID);
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp."))
			return getOrCreateDataObjectFromVariableName(compositeType, moduleName, variableName, variableName.startsWith("$"),
					variableName.startsWith("$"), true, null, node.getLineNumberFrom(), SCOPE_ID);
		
		if(compositeType != null)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid composite type member name: \"" + variableName + "\"", node.getLineNumberFrom(), SCOPE_ID);
		
		final boolean isLinkerFunction;
		if(!isModuleVariable && variableName.startsWith("func.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(5);
		}else if(!isModuleVariable && variableName.startsWith("fn.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(3);
		}else if(!isModuleVariable && variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(7);
		}else if(!isModuleVariable && variableName.startsWith("ln.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(3);
		}else {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", node.getLineNumberFrom(), SCOPE_ID);
		}
		
		final String variableNameCopy = variableName;
		Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).filter(entry -> {
			return variableNameCopy.equals(entry.getKey());
		}).findFirst();
		
		if(!ret.isPresent())
			return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + variableName + "\" was not found", node.getLineNumberFrom(), SCOPE_ID);
		
		LangPredefinedFunctionObject funcObj = ret.get().getValue();
		
		if(funcObj instanceof LangNativeFunction)
			return new DataObject().setFunctionPointer(new FunctionPointerObject(node.getVariableName(), (LangNativeFunction)funcObj)).setVariableName(node.getVariableName());
		
		return new DataObject().setFunctionPointer(new FunctionPointerObject(node.getVariableName(), funcObj)).setVariableName(node.getVariableName());
	}
	
	/**
	 * @return Will return null for ("\!" escape sequence)
	 */
	private DataObject interpretEscapeSequenceNode(EscapeSequenceNode node, final int SCOPE_ID) {
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
			case 'E':
				return new DataObject().setChar('\033');
			case 'b':
				return new DataObject().setChar('\b');
			case 't':
				return new DataObject().setChar('\t');
			case '$':
			case '&':
			case '#':
			case ',':
			case '.':
			case '(':
			case ')':
			case '[':
			case ']':
			case '{':
			case '}':
			case '=':
			case '<':
			case '>':
			case '+':
			case '-':
			case '/':
			case '*':
			case '%':
			case '|':
			case '~':
			case '^':
			case '?':
			case ':':
			case '@':
			case '▲':
			case '▼':
				return new DataObject().setChar(node.getEscapeSequenceChar());
			case '!':
				return null;
			case '\\':
				return new DataObject().setChar('\\');
			
			//If no escape sequence: Remove "\" anyway
			default:
				setErrno(InterpretingError.UNDEF_ESCAPE_SEQUENCE, "\"\\" + node.getEscapeSequenceChar() + "\" was used", node.getLineNumberFrom(), SCOPE_ID);
				
				return new DataObject().setChar(node.getEscapeSequenceChar());
		}
	}
	
	private DataObject interpretArgumentSeparatotNode(ArgumentSeparatorNode node, final int SCOPE_ID) {
		return new DataObject().setArgumentSeparator(node.getOriginalText());
	}
	
	DataObject getAndResetReturnValue(final int SCOPE_ID) {
		DataObject retTmp = executionState.returnedOrThrownValue;
		executionState.returnedOrThrownValue = null;
		
		if(executionState.isThrownValue && SCOPE_ID > -1)
			setErrno(retTmp.getError().getInterprettingError(), retTmp.getError().getMessage(),
					executionState.returnOrThrowStatementLineNumber, SCOPE_ID);
		
		if(executionFlags.langTest && SCOPE_ID == langTestExpectedReturnValueScopeID) {
			if(langTestExpectedThrowValue != null) {
				InterpretingError gotError = executionState.isThrownValue?retTmp.getError().getInterprettingError():null;
				langTestStore.addAssertResult(new LangTest.AssertResultThrow(gotError == langTestExpectedThrowValue,
						printStackTrace(-1), langTestMessageForLastTestResult, gotError, langTestExpectedThrowValue));
				
				langTestExpectedThrowValue = null;
			}
			
			if(langTestExpectedReturnValue != null) {
				langTestStore.addAssertResult(new LangTest.AssertResultReturn(!executionState.isThrownValue &&
						langTestExpectedReturnValue.isStrictEquals(retTmp), printStackTrace(-1),
						langTestMessageForLastTestResult, retTmp, langTestExpectedReturnValue));
				
				langTestExpectedReturnValue = null;
			}
			
			if(langTestExpectedNoReturnValue) {
				langTestStore.addAssertResult(new LangTest.AssertResultNoReturn(retTmp == null, printStackTrace(-1),
						langTestMessageForLastTestResult, retTmp));
				
				langTestExpectedNoReturnValue = false;
			}
			langTestMessageForLastTestResult = null;
			langTestExpectedReturnValueScopeID = 0;
		}
		
		executionState.isThrownValue = false;
		
		if(executionState.tryThrownError == null || executionState.tryBlockLevel == 0 || (executionState.isSoftTry && executionState.tryBodyScopeID != SCOPE_ID))
			executionState.stopExecutionFlag = false;
		
		return retTmp == null?retTmp:new DataObject(retTmp);
	}
	DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, int parentLineNumber, final int SCOPE_ID) {
		argumentValueList = new ArrayList<>(argumentValueList);
		
		try {
			String functionLangPath = fp.getLangPath();
			String functionLangFile = fp.getLangFile();
			
			functionName = (functionName == null || fp.getFunctionName() != null)?fp.toString():functionName;
			
			//Update call stack
			StackElement currentStackElement = getCurrentCallStackElement();
			pushStackElement(new StackElement(functionLangPath == null?currentStackElement.getLangPath():functionLangPath,
					(functionLangPath == null && functionLangFile == null)?currentStackElement.getLangFile():functionLangFile,
					functionName, currentStackElement.getModule()), parentLineNumber);
			
			switch(fp.getFunctionPointerType()) {
				case FunctionPointerObject.NORMAL:
					List<VariableNameNode> parameterList = fp.getParameterList();
					AbstractSyntaxTree functionBody = fp.getFunctionBody();
					if(parameterList == null || functionBody == null)
						return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", parentLineNumber, SCOPE_ID);
					
					final int NEW_SCOPE_ID = SCOPE_ID + 1;
					
					//Add variables and local variables
					createDataMap(NEW_SCOPE_ID);
					//Copies must not be final
					data.get(SCOPE_ID).var.forEach((key, val) -> {
						if(!val.isLangVar())
							data.get(NEW_SCOPE_ID).var.put(key, new DataObject(val).setVariableName(val.getVariableName()));
						
						if(val.isStaticData()) //Static Lang vars should also be copied
							data.get(NEW_SCOPE_ID).var.put(key, val);
					});
					
					//Set arguments
					DataObject lastDataObject = new DataObject().setVoid();
					Iterator<VariableNameNode> parameterListIterator = parameterList.iterator();
					boolean isLastDataObjectArgumentSeparator = argumentValueList.size() > 0 && argumentValueList.get(argumentValueList.size() - 1).getType() == DataType.ARGUMENT_SEPARATOR;
					while(parameterListIterator.hasNext()) {
						VariableNameNode parameter = parameterListIterator.next();
						String variableName = parameter.getVariableName();
						String rawTypeConstraint = parameter.getTypeConstraint();
						DataTypeConstraint typeConstraint;
						if(rawTypeConstraint == null) {
							typeConstraint = null;
						}else {
							DataObject errorOut = new DataObject().setVoid();
							typeConstraint = interpretTypeConstraint(rawTypeConstraint, errorOut, parameter.getLineNumberFrom(), SCOPE_ID);
							
							if(errorOut.getType() == DataType.ERROR)
								return errorOut;
						}
						
						if(!parameterListIterator.hasNext() && !isLangVarWithoutPrefix(variableName) && isFuncCallVarArgs(variableName)) {
							//Varargs (only the last parameter can be a varargs parameter)
							variableName = variableName.substring(0, variableName.length() - 3); //Remove "..."
							if(variableName.startsWith("$")) {
								//Text varargs
								if(typeConstraint != null) {
									return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
											"function parameter \"" + variableName + "\": Text var args argument must not have a type constraint definition",
											parameter.getLineNumberFrom(), SCOPE_ID);
								}
								
								DataObject dataObject = LangUtils.combineDataObjects(argumentValueList);
								try {
									DataObject newDataObject = new DataObject(dataObject != null?dataObject.getText():
										new DataObject().setVoid().getText()).setVariableName(variableName);
									
									
									DataObject old = data.get(NEW_SCOPE_ID).var.put(variableName, newDataObject);
									if(old != null && old.isStaticData())
										setErrno(InterpretingError.VAR_SHADOWING_WARNING, "Parameter \"" + variableName + "\" shadows a static variable",
												parameter.getLineNumberFrom(), NEW_SCOPE_ID);
								}catch(DataTypeConstraintException e) {
									return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
											"Invalid argument value for function parameter \"" + variableName + "\" (" + e.getMessage() + ")",
											parameter.getLineNumberFrom(), SCOPE_ID);
								}
							}else {
								//Array varargs
								List<DataObject> varArgsTmpList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentValueList).stream().
										map(DataObject::new).collect(Collectors.toList());
								if(varArgsTmpList.isEmpty() && isLastDataObjectArgumentSeparator)
									varArgsTmpList.add(new DataObject().setVoid());
								
								if(typeConstraint != null) {
									for(int i = 0;i < varArgsTmpList.size();i++) {
										DataObject varArgsArgument = varArgsTmpList.get(i);
										if(!typeConstraint.isTypeAllowed(varArgsArgument.getType()))
											return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE,
													"Invalid argument (Var args argument " + (i + 1) + ") value for var args function parameter \"" +
															variableName + "\": Value must be one of " + typeConstraint.getAllowedTypes(),
													parameter.getLineNumberFrom(), SCOPE_ID);
									}
								}
								
								try {
									DataObject newDataObject = new DataObject().
											setArray(varArgsTmpList.toArray(new DataObject[0])).
											setVariableName(variableName);
									
									DataObject old = data.get(NEW_SCOPE_ID).var.put(variableName, newDataObject);
									if(old != null && old.isStaticData())
										setErrno(InterpretingError.VAR_SHADOWING_WARNING, "Parameter \"" + variableName + "\" shadows a static variable",
												parameter.getLineNumberFrom(), NEW_SCOPE_ID);
								}catch(DataTypeConstraintException e) {
									return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
											"Invalid argument value for function parameter \"" + variableName + "\" (" + e.getMessage() + ")",
											parameter.getLineNumberFrom(), SCOPE_ID);
								}
							}
							
							break;
						}
						
						if(isFuncCallCallByPtr(variableName) && !isFuncCallCallByPtrLangVar(variableName)) {
							//Call by pointer
							variableName = "$" + variableName.substring(2, variableName.length() - 1); //Remove '[' and ']' from variable name
							if(argumentValueList.size() > 0)
								lastDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true);
							else if(isLastDataObjectArgumentSeparator && lastDataObject.getType() != DataType.VOID)
								lastDataObject = new DataObject().setVoid();
							
							try {
								DataObject newDataObject = new DataObject().
										setVarPointer(new VarPointerObject(lastDataObject)).
										setVariableName(variableName);
								if(typeConstraint != null)
									newDataObject.setTypeConstraint(typeConstraint);
								
								DataObject old = data.get(NEW_SCOPE_ID).var.put(variableName, newDataObject);
								if(old != null && old.isStaticData())
									setErrno(InterpretingError.VAR_SHADOWING_WARNING, "Parameter \"" + variableName + "\" shadows a static variable",
											parameter.getLineNumberFrom(), NEW_SCOPE_ID);
							}catch(DataTypeConstraintException e) {
								return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
										"Invalid argument value for function parameter \"" + variableName + "\" (" + e.getMessage() + ")",
										parameter.getLineNumberFrom(), SCOPE_ID);
							}
							
							continue;
						}
						
						if(!isVarNameWithoutPrefix(variableName) || isLangVarWithoutPrefix(variableName))
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
									"Invalid parameter variable name: \"" + variableName + "\"",
									parameter.getLineNumberFrom(), NEW_SCOPE_ID);
						
						if(argumentValueList.size() > 0)
							lastDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true);
						else if(isLastDataObjectArgumentSeparator && lastDataObject.getType() != DataType.VOID)
							lastDataObject = new DataObject().setVoid();
						
						try {
							DataObject newDataObject = new DataObject(lastDataObject).
									setVariableName(variableName);
							if(typeConstraint != null)
								newDataObject.setTypeConstraint(typeConstraint);
							
							DataObject old = data.get(NEW_SCOPE_ID).var.put(variableName, newDataObject);
							if(old != null && old.isStaticData())
								setErrno(InterpretingError.VAR_SHADOWING_WARNING, "Parameter \"" + variableName + "\" shadows a static variable",
										parameter.getLineNumberFrom(), NEW_SCOPE_ID);
						}catch(DataTypeConstraintViolatedException e) {
							return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE,
									"Invalid argument value for function parameter \"" + variableName + "\" (" + e.getMessage() + ")",
									parameter.getLineNumberFrom(), SCOPE_ID);
						}
					}
					
					//Call function
					interpretAST(functionBody, NEW_SCOPE_ID);
					
					//Add translations after call
					data.get(SCOPE_ID).lang.putAll(data.get(NEW_SCOPE_ID).lang);
					
					//Remove data map
					data.remove(NEW_SCOPE_ID);
					
					DataTypeConstraint returnValueTypeConstraint = fp.getReturnValueTypeConstraint();
					
					boolean isReturnValueThrownError = executionState.isThrownValue ||
							(executionState.tryThrownError != null && executionState.tryBlockLevel > 0 &&
									(!executionState.isSoftTry || executionState.tryBodyScopeID == SCOPE_ID));
					int returnOrThrowStatementLineNumber = executionState.returnOrThrowStatementLineNumber;
					
					DataObject retTmp = getAndResetReturnValue(SCOPE_ID);
					retTmp = retTmp == null?new DataObject().setVoid():retTmp;
					
					if(returnValueTypeConstraint != null && !isReturnValueThrownError) {
						//Thrown values are always allowed
						
						if(!returnValueTypeConstraint.isTypeAllowed(retTmp.getType()))
							return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE,
									"Invalid return value type \"" + retTmp.getType() + "\"",
									returnOrThrowStatementLineNumber, SCOPE_ID);
					}
					
					return retTmp;
				
				case FunctionPointerObject.NATIVE:
					LangNativeFunction nativeFunction = fp.getNativeFunction();
					if(nativeFunction == null)
						return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", parentLineNumber, SCOPE_ID);
					
					DataObject ret = nativeFunction.callFunc(argumentValueList, SCOPE_ID);
					if(nativeFunction.isDeprecated()) {
						String message = String.format("Use of deprecated function \"%s\". This function will no longer be supported in \"%s\"!%s", functionName,
						nativeFunction.getDeprecatedRemoveVersion() == null?"the future":nativeFunction.getDeprecatedRemoveVersion(),
						nativeFunction.getDeprecatedReplacementFunction() == null?"":("\nUse \"" + nativeFunction.getDeprecatedReplacementFunction() + "\" instead!"));
						setErrno(InterpretingError.DEPRECATED_FUNC_CALL, message, parentLineNumber, SCOPE_ID);
					}
					
					//Return non copy if copyStaticAndFinalModifiers flag is set for "func.asStatic()" and "func.asFinal()"
					return ret == null?new DataObject().setVoid():(ret.isCopyStaticAndFinalModifiers()?ret:new DataObject(ret));
				
				case FunctionPointerObject.PREDEFINED:
					LangPredefinedFunctionObject function = fp.getPredefinedFunction();
					if(function == null)
						return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", parentLineNumber, SCOPE_ID);
					
					ret = function.callFunc(argumentValueList, SCOPE_ID);
					if(function.isDeprecated()) {
						String message = String.format("Use of deprecated function \"%s\". This function will no longer be supported in \"%s\"!%s", functionName,
						function.getDeprecatedRemoveVersion() == null?"the future":function.getDeprecatedRemoveVersion(),
						function.getDeprecatedReplacementFunction() == null?"":("\nUse \"" + function.getDeprecatedReplacementFunction() + "\" instead!"));
						setErrno(InterpretingError.DEPRECATED_FUNC_CALL, message, parentLineNumber, SCOPE_ID);
					}
					
					//Return non copy if copyStaticAndFinalModifiers flag is set for "func.asStatic()" and "func.asFinal()"
					return ret == null?new DataObject().setVoid():(ret.isCopyStaticAndFinalModifiers()?ret:new DataObject(ret));
				
				case FunctionPointerObject.EXTERNAL:
					LangExternalFunctionObject externalFunction = fp.getExternalFunction();
					if(externalFunction == null)
						return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", parentLineNumber, SCOPE_ID);
					ret = externalFunction.callFunc(this, argumentValueList, SCOPE_ID);
					return ret == null?new DataObject().setVoid():new DataObject(ret);
				
				default:
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP type", parentLineNumber, SCOPE_ID);
			}
		}finally {
			//Update call stack
			popStackElement();
		}
	}
	DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int SCOPE_ID) {
		return callFunctionPointer(fp, functionName, argumentValueList, -1, SCOPE_ID);
	}
	private List<DataObject> interpretFunctionPointerArguments(List<Node> argumentList, final int SCOPE_ID) {
		List<DataObject> argumentValueList = new LinkedList<>();
		DataObject previousDataObject = null;
		for(Node argument:argumentList) {
			if(argument.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
				try {
					Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)argument, previousDataObject, SCOPE_ID);
					if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
						argumentValueList.remove(argumentValueList.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
						argumentValueList.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, SCOPE_ID));
					}else {
						argumentValueList.add(interpretNode(null, ret, SCOPE_ID));
					}
				}catch(ClassCastException e) {
					argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, argument.getLineNumberFrom(), SCOPE_ID));
				}
				
				previousDataObject = argumentValueList.get(argumentValueList.size() - 1);
				
				continue;
			}
			
			//Composite type unpacking
			if(argument.getNodeType() == NodeType.UNPROCESSED_VARIABLE_NAME) {
				try {
					String variableName = ((UnprocessedVariableNameNode)argument).getVariableName();
					if(variableName.contains("&") && variableName.endsWith("...")) {
						boolean isModuleVariable = variableName.startsWith("[[");
						String moduleName = null;
						if(isModuleVariable) {
							int indexModuleIdientifierEnd = variableName.indexOf("]]::");
							if(indexModuleIdientifierEnd == -1) {
								argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", argument.getLineNumberFrom(), SCOPE_ID));
								
								continue;
							}
							
							moduleName = variableName.substring(2, indexModuleIdientifierEnd);
							if(!isAlphaNummericWithUnderline(moduleName)) {
								argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid module name", argument.getLineNumberFrom(), SCOPE_ID));
								
								continue;
							}
							
							variableName = variableName.substring(indexModuleIdientifierEnd + 4);
						}
						
						if(variableName.startsWith("&")) {
							DataObject dataObject = getOrCreateDataObjectFromVariableName(null, moduleName, variableName.
									substring(0, variableName.length() - 3), false, false, false, null, argument.getLineNumberFrom(), SCOPE_ID);
							if(dataObject == null) {
								argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, "Unpacking of undefined variable",
										argument.getLineNumberFrom(), SCOPE_ID));
								
								continue;
							}
							
							if(dataObject.getType() == DataType.ARRAY) {
								argumentValueList.addAll(LangUtils.separateArgumentsWithArgumentSeparators(Arrays.asList(dataObject.getArray())));
								
								continue;
							}
							
							if(dataObject.getType() == DataType.LIST) {
								argumentValueList.addAll(LangUtils.separateArgumentsWithArgumentSeparators(dataObject.getList()));
								
								continue;
							}
							
							if(dataObject.getType() == DataType.STRUCT) {
								StructObject struct = dataObject.getStruct();
								
								if(struct.isDefinition())
									argumentValueList.addAll(LangUtils.separateArgumentsWithArgumentSeparators(Arrays.stream(struct.getMemberNames()).
											map(DataObject::new).collect(Collectors.toList())));
								else
									argumentValueList.addAll(LangUtils.separateArgumentsWithArgumentSeparators(Arrays.stream(struct.getMemberNames()).
											map(struct::getMember).collect(Collectors.toList())));
								
								continue;
							}
							
							argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, "Unpacking of unsupported composite type variable",
									argument.getLineNumberFrom(), SCOPE_ID));
							
							continue;
						}
					}
				}catch(ClassCastException e) {
					argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, argument.getLineNumberFrom(), SCOPE_ID));
				}
			}
			
			DataObject argumentValue = interpretNode(null, argument, SCOPE_ID);
			if(argumentValue == null) {
				previousDataObject = null;
				
				continue;
			}
			
			argumentValueList.add(argumentValue);
			previousDataObject = argumentValue;
		}
		
		return argumentValueList;
	}
	/**
	 * @return Will return void data for non return value functions
	 */
	private DataObject interpretFunctionCallNode(DataObject compositeType, FunctionCallNode node, final int SCOPE_ID) {
		String functionName = node.getFunctionName();
		final String originalFunctionName = functionName;
		
		boolean isModuleVariable = compositeType == null && functionName.startsWith("[[");
		Map<String, DataObject> variables;
		if(isModuleVariable) {
			int indexModuleIdientifierEnd = functionName.indexOf("]]::");
			if(indexModuleIdientifierEnd == -1) {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid function name", node.getLineNumberFrom(), SCOPE_ID);
			}
			
			String moduleName = functionName.substring(2, indexModuleIdientifierEnd);
			if(!isAlphaNummericWithUnderline(moduleName)) {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid module name", node.getLineNumberFrom(), SCOPE_ID);
			}
			
			functionName = functionName.substring(indexModuleIdientifierEnd + 4);
			
			LangModule module = modules.get(moduleName);
			if(module == null) {
				return setErrnoErrorObject(InterpretingError.MODULE_LOAD_UNLOAD_ERR, "The module \"" + moduleName + "\" is not loaded!", node.getLineNumberFrom(), SCOPE_ID);
			}
			
			variables = module.getExportedVariables();
		}else {
			variables = data.get(SCOPE_ID).var;
		}
		
		FunctionPointerObject fp;
		if(compositeType != null) {
			if(compositeType.getType() != DataType.STRUCT)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Invalid composite type", node.getLineNumberFrom(), SCOPE_ID);
			
			if(!functionName.startsWith("fp."))
				functionName = "fp." + functionName;
			
			try {
				DataObject member = compositeType.getStruct().getMember(functionName);
				
				if(member.getType() != DataType.FUNCTION_POINTER)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"" + node.getFunctionName() +
							"\": Function pointer is invalid", node.getLineNumberFrom(), SCOPE_ID);
				
				fp = member.getFunctionPointer();
			}catch(DataTypeConstraintException e) {
				return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), node.getLineNumberFrom(), SCOPE_ID);
			}
		}else if(!isModuleVariable && isFuncName(functionName)) {
			final boolean isLinkerFunction;
			if(functionName.startsWith("func.")) {
				isLinkerFunction = false;
				
				functionName = functionName.substring(5);
			}else if(functionName.startsWith("fn.")) {
				isLinkerFunction = false;
				
				functionName = functionName.substring(3);
			}else if(functionName.startsWith("linker.")) {
				isLinkerFunction = true;
				
				functionName = functionName.substring(7);
			}else if(functionName.startsWith("ln.")) {
				isLinkerFunction = true;
				
				functionName = functionName.substring(3);
			}else {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid native, predfined, or linker function name", node.getLineNumberFrom(), SCOPE_ID);
			}
			
			final String functionNameCopy = functionName;
			Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
				return entry.getValue().isLinkerFunction() == isLinkerFunction && functionNameCopy.equals(entry.getKey());
			}).findFirst();
			
			if(!ret.isPresent())
				return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + node.getFunctionName() +
						"\": Native, predfined, or linker function was not found", node.getLineNumberFrom(), SCOPE_ID);
			
			fp = new FunctionPointerObject(originalFunctionName, ret.get().getValue());
		}else if(isVarNameFuncPtrWithoutPrefix(functionName)) {
			DataObject ret = variables.get(functionName);
			if(ret == null || ret.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"" + node.getFunctionName() +
						"\": Function pointer was not found or is invalid", node.getLineNumberFrom(), SCOPE_ID);
			
			fp = ret.getFunctionPointer();
		}else {
			//Function call without prefix
			
			//Function pointer
			DataObject ret = variables.get("fp." + functionName);
			if(ret != null) {
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"" + node.getFunctionName() +
							"\": Function pointer is invalid", node.getLineNumberFrom(), SCOPE_ID);
					
				fp = ret.getFunctionPointer();
			}else if(!isModuleVariable) {
				//Predefined/External function
				
				final String functionNameCopy = functionName;
				Optional<Map.Entry<String, LangPredefinedFunctionObject>> retPredefinedFunction = funcs.entrySet().stream().filter(entry -> {
					return !entry.getValue().isLinkerFunction() && functionNameCopy.equals(entry.getKey());
				}).findFirst();
				
				if(retPredefinedFunction.isPresent()) {
					fp = new FunctionPointerObject("func." + functionName, retPredefinedFunction.get().getValue());;
				}else {
					//Predefined linker function
					retPredefinedFunction = funcs.entrySet().stream().filter(entry -> {
						return entry.getValue().isLinkerFunction() && functionNameCopy.equals(entry.getKey());
					}).findFirst();
					
					if(!retPredefinedFunction.isPresent())
						return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + node.getFunctionName() +
								"\": Normal, native, predfined, linker, or external function was not found", node.getLineNumberFrom(), SCOPE_ID);
					
					fp = new FunctionPointerObject("linker." + functionName, retPredefinedFunction.get().getValue());
				}
			}else {
				return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + node.getFunctionName() +
						"\": Normal, native, predfined, linker, or external function was not found", node.getLineNumberFrom(), SCOPE_ID);
			}
		}
		
		return callFunctionPointer(fp, functionName, interpretFunctionPointerArguments(node.getChildren(), SCOPE_ID), node.getLineNumberFrom(), SCOPE_ID);
	}
	
	private DataObject interpretFunctionCallPreviousNodeValueNode(FunctionCallPreviousNodeValueNode node, DataObject previousValue, final int SCOPE_ID) {
		if(previousValue == null)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing previous value for FunctionCallPreviousNodeValueNode",
					node.getLineNumberFrom(), SCOPE_ID);
		
		if(previousValue.getType() == DataType.FUNCTION_POINTER)
			return callFunctionPointer(previousValue.getFunctionPointer(), previousValue.getVariableName(),
					interpretFunctionPointerArguments(node.getChildren(), SCOPE_ID), node.getLineNumberFrom(), SCOPE_ID);
		
		if(previousValue.getType() == DataType.TYPE) {
			List<DataObject> argumentList = interpretFunctionPointerArguments(node.getChildren(), SCOPE_ID);
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			if(combinedArgumentList.size() < 1)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "Not enough arguments (1 needed)",
						node.getLineNumberFrom(), SCOPE_ID);
			if(combinedArgumentList.size() > 1)
				return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, "Too many arguments (1 needed)",
						node.getLineNumberFrom(), SCOPE_ID);
			
			DataObject arg = combinedArgumentList.get(0);
			
			DataObject output = operators.opCast(previousValue, arg, node.getLineNumberFrom(), SCOPE_ID);
			if(output == null)
				return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, "Data type \"" + arg.getType() +
						"\" can not be casted to \"" + previousValue.getTypeValue() + "\"!", node.getLineNumberFrom(),
						SCOPE_ID);
			
			return output;
		}
		
		if(previousValue.getType() == DataType.STRUCT && previousValue.getStruct().isDefinition()) {
			List<DataObject> argumentList = interpretFunctionPointerArguments(node.getChildren(), SCOPE_ID);
			List<DataObject> combinedArgumentList = LangUtils.combineArgumentsWithoutArgumentSeparators(argumentList);
			
			StructObject struct = previousValue.getStruct();
			
			String[] memberNames = struct.getMemberNames();
			if(combinedArgumentList.size() != memberNames.length) {
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The array length is not equals to the count of member names (" + memberNames.length + ")",
						node.getLineNumberFrom(), SCOPE_ID);
			}
			
			try {
				return new DataObject().setStruct(new StructObject(struct, combinedArgumentList.toArray(new DataObject[0])));
			}catch(DataTypeConstraintException e) {
				return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, e.getMessage(), node.getLineNumberFrom(), SCOPE_ID);
			}
		}
		
		return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid data type", node.getLineNumberFrom(), SCOPE_ID);
	}
	
	private DataObject interpretFunctionDefinitionNode(FunctionDefinitionNode node, final int SCOPE_ID) {
		List<VariableNameNode> parameterList = new ArrayList<>();
		List<Node> children = node.getChildren();
		Iterator<Node> childrenIterator = children.listIterator();
		while(childrenIterator.hasNext()) {
			Node child = childrenIterator.next();
			try {
				if(child.getNodeType() != NodeType.VARIABLE_NAME) {
					if(child.getNodeType() == NodeType.PARSING_ERROR)
						return interpretNode(null, child, SCOPE_ID);
					else
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
								"Invalid AST node type for parameter", node.getLineNumberFrom(), SCOPE_ID);
				}
				
				VariableNameNode parameter = (VariableNameNode)child;
				String variableName = parameter.getVariableName();
				if(!childrenIterator.hasNext() && !isLangVarWithoutPrefix(variableName) && isFuncCallVarArgs(variableName)) {
					//Varargs (only the last parameter can be a varargs parameter)
					parameterList.add(parameter);
					break;
				}
				
				if((!isVarNameWithoutPrefix(variableName) && !isFuncCallCallByPtr(variableName)) ||
						isLangVarWithoutPrefix(variableName))
					return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE,
							"Invalid parameter: \"" + variableName + "\"", node.getLineNumberFrom(), SCOPE_ID);
				
				parameterList.add(parameter);
			}catch(ClassCastException e) {
				setErrno(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
			}
		}
		
		String rawTypeConstraint = node.getReturnValueTypeConstraint();
		DataTypeConstraint typeConstraint;
		if(rawTypeConstraint == null) {
			typeConstraint = null;
		}else {
			DataObject errorOut = new DataObject().setVoid();
			typeConstraint = interpretTypeConstraint(rawTypeConstraint, errorOut, node.getLineNumberFrom(), SCOPE_ID);
			
			if(errorOut.getType() == DataType.ERROR)
				return errorOut;
		}
		
		StackElement currentStackElement = getCurrentCallStackElement();
		return new DataObject().setFunctionPointer(new FunctionPointerObject(currentStackElement.getLangPath(),
				currentStackElement.getLangFile(), parameterList, typeConstraint, node.getFunctionBody()));
	}
	
	private DataObject interpretArrayNode(ArrayNode node, final int SCOPE_ID) {
		List<DataObject> interpretedNodes = new LinkedList<>();
		
		for(Node element:node.getChildren()) {
			DataObject argumentValue = interpretNode(null, element, SCOPE_ID);
			if(argumentValue == null)
				continue;
			interpretedNodes.add(new DataObject(argumentValue));
		}
		
		List<DataObject> elements = LangUtils.combineArgumentsWithoutArgumentSeparators(interpretedNodes);
		return new DataObject().setArray(elements.toArray(new DataObject[0]));
	}
	
	private DataObject interpretStructDefinitionNode(StructDefinitionNode node, final int SCOPE_ID) {
		List<String> memberNames = node.getMemberNames();
		List<String> typeConstraints = node.getTypeConstraints();
		
		if(memberNames.size() != typeConstraints.size())
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, node.getLineNumberFrom(), SCOPE_ID);
		
		for(String memberName:memberNames)
			if(!isVarNameWithoutPrefix(memberName))
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "\"" + memberName + "\" is no valid struct member name", node.getLineNumberFrom(), SCOPE_ID);
		
		if(new HashSet<>(memberNames).size() < memberNames.size())
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Struct member name may not be duplicated", node.getLineNumberFrom(), SCOPE_ID);
		
		DataTypeConstraint[] typeConstraintsArray = new DataTypeConstraint[typeConstraints.size()];
		for(int i = 0;i < typeConstraintsArray.length;i++) {
			String typeConstraint = typeConstraints.get(i);
			if(typeConstraint == null)
				continue;
			
			DataObject errorOut = new DataObject().setVoid();
			typeConstraintsArray[i] = interpretTypeConstraint(typeConstraint, errorOut, node.getLineNumberFrom(), SCOPE_ID);
			
			if(errorOut.getType() == DataType.ERROR)
				return errorOut;
		}
		
		try {
			return new DataObject().setStruct(new StructObject(memberNames.toArray(new String[0]), typeConstraintsArray));
		}catch(DataTypeConstraintException e) {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, e.getMessage(), node.getLineNumberFrom(), SCOPE_ID);
		}
	}
	
	private DataTypeConstraint interpretTypeConstraint(String typeConstraint, DataObject errorOut, int lineNumber, final int SCOPE_ID) {
		if(typeConstraint.isEmpty())
			errorOut.setData(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Empty type constraint is not allowed", lineNumber, SCOPE_ID));
		
		boolean nullable = typeConstraint.charAt(0) == '?';
		boolean inverted = typeConstraint.charAt(0) == '!';
		List<DataType> typeValues = new LinkedList<>(); 
		
		if(nullable || inverted)
			typeConstraint = typeConstraint.substring(1);
		
		int pipeIndex;
		do {
			pipeIndex = typeConstraint.indexOf('|');
			
			String type = pipeIndex > -1?typeConstraint.substring(0, pipeIndex):typeConstraint;
			
			if(type.isEmpty() || pipeIndex == typeConstraint.length() - 1)
				errorOut.setData(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Empty type constraint is not allowed", lineNumber, SCOPE_ID));
			
			typeConstraint = pipeIndex > -1?typeConstraint.substring(pipeIndex + 1):"";
			
			try {
				DataType typeValue = DataType.valueOf(type);
				typeValues.add(typeValue);
			}catch(IllegalArgumentException e) {
				errorOut.setData(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid type: \"" + type + "\"", lineNumber, SCOPE_ID));
			}
		}while(pipeIndex > -1);
		
		if(nullable)
			typeValues.add(DataType.NULL);
		
		if(inverted)
			return DataTypeConstraint.fromNotAllowedTypes(typeValues);
		else
			return DataTypeConstraint.fromAllowedTypes(typeValues);
	}
	
	//Return values for format sequence errors
	private static final int FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE = -1;
	private static final int FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS = -2;
	private static final int FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT = -3;
	private static final int FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND = -4;
	private static final int FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS = -5;
	private static final int FORMAT_SEQUENCE_ERROR_TRANSLATION_INVALID_PLURALIZATION_TEMPLATE = -6;
	
	/**
	 * @param argumentList The argument list without argument separators of the function call without the format argument (= argument at index 0). Used data objects will be removed from the list
	 * @param fullArgumentList The argument list of the function call where every argument are already combined to single values without argument separators with the format argument
	 * (= argument at index 0). This list will not be modified and is used for value referencing by index
	 * 
	 * @return The count of chars used for the format sequence
	 * Will return any of
	 * <ul>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE}</li>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS}</li>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND}</li>
	 * <li>{@code FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS}</li>
	 * </ul>
	 * for errors
	 */
	private int interpretNextFormatSequence(String format, StringBuilder builder, List<DataObject> argumentList, List<DataObject> fullArgumentList, final int SCOPE_ID) {
		char[] posibleFormats = {'b', 'c', 'd', 'f', 'n', 'o', 's', 't', 'x', '?'};
		int[] indices = new int[posibleFormats.length];
		for(int i = 0;i < posibleFormats.length;i++)
			indices[i] = format.indexOf(posibleFormats[i]);
		
		int minEndIndex = Integer.MAX_VALUE;
		for(int index:indices) {
			if(index == -1)
				continue;
			
			if(index < minEndIndex)
				minEndIndex = index;
		}
		
		if(minEndIndex == Integer.MAX_VALUE)
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
		
		String fullFormat = format.substring(0, minEndIndex + 1);
		char formatType = fullFormat.charAt(fullFormat.length() - 1);
		
		//Parsing format arguments
		Integer valueSpecifiedIndex = null;
		if(fullFormat.charAt(0) == '[') {
			int valueSpecifiedIndexEndIndex = fullFormat.indexOf(']');
			if(valueSpecifiedIndexEndIndex < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
			
			String valueSpecifiedIndexString = fullFormat.substring(1, valueSpecifiedIndexEndIndex);
			fullFormat = fullFormat.substring(valueSpecifiedIndexEndIndex + 1);
			
			String number = "";
			while(!valueSpecifiedIndexString.isEmpty()) {
				if(valueSpecifiedIndexString.charAt(0) < '0' || valueSpecifiedIndexString.charAt(0) > '9')
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				number += valueSpecifiedIndexString.charAt(0);
				valueSpecifiedIndexString = valueSpecifiedIndexString.substring(1);
			}
			valueSpecifiedIndex = Integer.parseInt(number);
			if(valueSpecifiedIndex >= fullArgumentList.size())
				return FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS;
		}
		boolean leftJustify = fullFormat.charAt(0) == '-';
		if(leftJustify)
			fullFormat = fullFormat.substring(1);
		boolean forceSign = fullFormat.charAt(0) == '+';
		if(forceSign)
			fullFormat = fullFormat.substring(1);
		boolean signSpace = !forceSign && fullFormat.charAt(0) == ' ';
		if(signSpace)
			fullFormat = fullFormat.substring(1);
		boolean leadingZeros = fullFormat.charAt(0) == '0';
		if(leadingZeros)
			fullFormat = fullFormat.substring(1);
		boolean sizeInArgument = fullFormat.charAt(0) == '*';
		if(sizeInArgument)
			fullFormat = fullFormat.substring(1);
		Integer sizeArgumentIndex = null;
		if(sizeInArgument && fullFormat.charAt(0) == '[') {
			int sizeArgumentIndexEndIndex = fullFormat.indexOf(']');
			if(sizeArgumentIndexEndIndex < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
			
			String sizeArgumentIndexString = fullFormat.substring(1, sizeArgumentIndexEndIndex);
			fullFormat = fullFormat.substring(sizeArgumentIndexEndIndex + 1);
			
			String number = "";
			while(!sizeArgumentIndexString.isEmpty()) {
				if(sizeArgumentIndexString.charAt(0) < '0' || sizeArgumentIndexString.charAt(0) > '9')
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				number += sizeArgumentIndexString.charAt(0);
				sizeArgumentIndexString = sizeArgumentIndexString.substring(1);
			}
			sizeArgumentIndex = Integer.parseInt(number);
			if(sizeArgumentIndex >= fullArgumentList.size())
				return FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS;
		}
		Integer size = null;
		if(fullFormat.charAt(0) > '0' && fullFormat.charAt(0) <= '9') {
			String number = "";
			while(fullFormat.charAt(0) >= '0' && fullFormat.charAt(0) <= '9') {
				number += fullFormat.charAt(0);
				fullFormat = fullFormat.substring(1);
			}
			size = Integer.parseInt(number);
		}
		boolean decimalPlaces = fullFormat.charAt(0) == '.';
		boolean decimalPlacesInArgument = false;
		Integer decimalPlacesCountIndex = null;
		Integer decimalPlacesCount = null;
		if(decimalPlaces) {
			fullFormat = fullFormat.substring(1);
			decimalPlacesInArgument = fullFormat.charAt(0) == '*';
			if(decimalPlacesInArgument)
				fullFormat = fullFormat.substring(1);
			if(decimalPlacesInArgument && fullFormat.charAt(0) == '[') {
				int decimalPlacesCountIndexEndIndex = fullFormat.indexOf(']');
				if(decimalPlacesCountIndexEndIndex < 0)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				String decimalPlacesCountIndexString = fullFormat.substring(1, decimalPlacesCountIndexEndIndex);
				fullFormat = fullFormat.substring(decimalPlacesCountIndexEndIndex + 1);
				
				String number = "";
				while(!decimalPlacesCountIndexString.isEmpty()) {
					if(decimalPlacesCountIndexString.charAt(0) < '0' || decimalPlacesCountIndexString.charAt(0) > '9')
						return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
					
					number += decimalPlacesCountIndexString.charAt(0);
					decimalPlacesCountIndexString = decimalPlacesCountIndexString.substring(1);
				}
				decimalPlacesCountIndex = Integer.parseInt(number);
				if(decimalPlacesCountIndex >= fullArgumentList.size())
					return FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS;
			}
			if(fullFormat.charAt(0) >= '0' && fullFormat.charAt(0) <= '9') {
				String number = "";
				while(fullFormat.charAt(0) >= '0' && fullFormat.charAt(0) <= '9') {
					number += fullFormat.charAt(0);
					fullFormat = fullFormat.substring(1);
				}
				boolean leadingZero = number.charAt(0) == '0';
				if(leadingZero && number.length() > 1)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				decimalPlacesCount = Integer.parseInt(number);
			}
		}
		
		if(fullFormat.charAt(0) != formatType)
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE; //Invalid characters
		if((sizeInArgument && size != null) || (decimalPlacesInArgument && decimalPlacesCount != null) || (leftJustify && leadingZeros))
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE; //Invalid format argument combinations
		if(leftJustify && (!sizeInArgument && size == null))
			return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE; //Missing size format argument for leftJustify
		switch(formatType) { //Invalid arguments for formatType
			case 'f':
				break;
			
			case 'n':
				if(valueSpecifiedIndex != null || sizeInArgument || size != null)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				//Fall-through
			case 'c':
			case 's':
			case '?':
				if(forceSign || signSpace || leadingZeros)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				
				//Fall-through
			case 'b':
			case 'd':
			case 'o':
			case 'x':
				if(decimalPlaces)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
				break;
				
			case 't':
				if(forceSign || signSpace || leadingZeros)
					return FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE;
		}
		
		//Get size from arguments
		if(sizeInArgument) {
			if(sizeArgumentIndex == null && argumentList.isEmpty())
				return FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT;
			DataObject dataObject = sizeArgumentIndex == null?argumentList.remove(0):fullArgumentList.get(sizeArgumentIndex);
			Number number = dataObject.toNumber();
			if(number == null)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
			
			size = number.intValue();
			if(size < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
		}
		if(decimalPlacesInArgument) {
			if(decimalPlacesCountIndex == null && argumentList.isEmpty())
				return FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT;
			DataObject dataObject = decimalPlacesCountIndex == null?argumentList.remove(0):fullArgumentList.get(decimalPlacesCountIndex);
			Number number = dataObject.toNumber();
			if(number == null)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
			
			decimalPlacesCount = number.intValue();
			if(decimalPlacesCount < 0)
				return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
		}
		
		//Format argument
		String output = null;
		if(formatType != 'n' && valueSpecifiedIndex == null && argumentList.isEmpty())
			return FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT;
		DataObject dataObject = formatType == 'n'?null:(valueSpecifiedIndex == null?argumentList.remove(0):fullArgumentList.get(valueSpecifiedIndex));
		switch(formatType) {
			case 'd':
				Number number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = number.longValue() + "";
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'b':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = Long.toString(number.longValue(), 2).toUpperCase();
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'o':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = Long.toString(number.longValue(), 8).toUpperCase();
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'x':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = Long.toString(number.longValue(), 16).toUpperCase();
				if(forceSign && output.charAt(0) != '-')
					output = "+" + output;
				
				if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
					output = " " + output;
				
				break;
			
			case 'f':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				double value = number.doubleValue();
				if(Double.isNaN(value)) {
					output = "NaN";
					forceSign = false;
					leadingZeros = false;
				}else if(Double.isInfinite(value)) {
					output = (value == Double.NEGATIVE_INFINITY?"-":"") + "Infinity";
					leadingZeros = false;
					if(forceSign && output.charAt(0) != '-')
						output = "+" + output;
					
					if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
						output = " " + output;
				}else {
					output = String.format(Locale.ENGLISH, "%" + (decimalPlacesCount == null?"":("." + decimalPlacesCount)) + "f", value);
					if(forceSign && output.charAt(0) != '-')
						output = "+" + output;
					
					if(signSpace && output.charAt(0) != '+' && output.charAt(0) != '-')
						output = " " + output;
				}
				
				break;
				
			case 'c':
				number = dataObject.toNumber();
				if(number == null)
					return FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS;
				
				output = "" + (char)number.intValue();
				
				break;
				
			case 's':
				output = dataObject.getText();
				
				break;
				
			case 't':
				String translationKey = dataObject.getText();
				
				output = getData().get(SCOPE_ID).lang.get(translationKey);
				if(output == null)
					return FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND;
				
				if(decimalPlacesCount != null) {
					try {
						output = LangUtils.formatTranslationTemplatePluralization(output, decimalPlacesCount.intValue());
					}catch(NumberFormatException|InvalidTranslationTemplateSyntaxException e) {
						return FORMAT_SEQUENCE_ERROR_TRANSLATION_INVALID_PLURALIZATION_TEMPLATE;
					}
				}
				
				break;
				
			case '?':
				output = dataObject.getBoolean()?"true":"false";
				
				break;
				
			case 'n':
				output = System.getProperty("line.separator");
				
				break;
		}
		
		if(output != null) {
			if(size == null) {
				builder.append(output);
			}else {
				if(leftJustify) {
					while(output.length() < size)
						output = output + " ";
				}else if(leadingZeros) {
					char signOutput = 0;
					if(output.charAt(0) == '+' || output.charAt(0) == '-' || output.charAt(0) == ' ') {
						signOutput = output.charAt(0);
						output = output.substring(1);
					}
					
					int paddingSize = size - (signOutput == 0?0:1);
					while(output.length() < paddingSize)
						output = "0" + output;
					
					if(signOutput != 0)
						output = signOutput + output;
				}else {
					while(output.length() < size)
						output = " " + output;
				}
				
				builder.append(output);
			}
		}
		
		return minEndIndex + 1;
	}
	/**
	 * @param argumentList The argument list without argument separators of the function call. Used data objects will be removed from the list
	 * 
	 * @return The formated text as TextObject or an ErrorObject if an error occurred
	 */
	DataObject formatText(String format, List<DataObject> argumentList, final int SCOPE_ID) {
		StringBuilder builder = new StringBuilder();
		List<DataObject> fullArgumentList = new LinkedList<>(argumentList);
		fullArgumentList.add(0, new DataObject(format));
		
		int i = 0;
		while(i < format.length()) {
			char c = format.charAt(i);
			if(c == '%') {
				if(++i == format.length())
					return setErrnoErrorObject(InterpretingError.INVALID_FORMAT, SCOPE_ID);
				
				c = format.charAt(i);
				if(c == '%') {
					builder.append(c);
					
					i++;
					continue;
				}
				
				int charCountUsed = interpretNextFormatSequence(format.substring(i), builder, argumentList, fullArgumentList, SCOPE_ID);
				if(charCountUsed < 0) {
					switch(charCountUsed) {
						case FORMAT_SEQUENCE_ERROR_INVALID_FORMAT_SEQUENCE:
							return setErrnoErrorObject(InterpretingError.INVALID_FORMAT, SCOPE_ID);
						case FORMAT_SEQUENCE_ERROR_INVALID_ARGUMENTS:
							return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, SCOPE_ID);
						case FORMAT_SEQUENCE_ERROR_INVALID_ARG_COUNT:
							return setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, SCOPE_ID);
						case FORMAT_SEQUENCE_ERROR_TRANSLATION_KEY_NOT_FOUND:
							return setErrnoErrorObject(InterpretingError.TRANS_KEY_NOT_FOUND, SCOPE_ID);
						case FORMAT_SEQUENCE_ERROR_SPECIFIED_INDEX_OUT_OF_BOUNDS:
							return setErrnoErrorObject(InterpretingError.INDEX_OUT_OF_BOUNDS, SCOPE_ID);
						case FORMAT_SEQUENCE_ERROR_TRANSLATION_INVALID_PLURALIZATION_TEMPLATE:
							return setErrnoErrorObject(InterpretingError.INVALID_TEMPLATE_SYNTAX, SCOPE_ID);
					}
				}
				
				i += charCountUsed;
				
				continue;
			}
			
			builder.append(c);
			
			i++;
		}
		
		return new DataObject(builder.toString());
	}
	
	/**
	 * LangPatterns: Regex: \w+
	 */
	private boolean isAlphaNummericWithUnderline(String token) {
		if(token.length() == 0)
			return false;
		
		for(int i = 0;i < token.length();i++) {
			char c = token.charAt(i);
			if(!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'))
				return false;
		}
		
		return true;
	}
	
	/**
	 * LangPatterns: LANG_VAR ((\$|&)LANG_.*)
	 */
	private boolean isLangVarWithoutPrefix(String token) {
		char firstChar = token.charAt(0);
		return (firstChar == '$' || firstChar == '&') && token.startsWith("LANG_",  1);
	}
	
	/**
	 * LangPatterns: LANG_VAR ((\$|&)LANG_.*) || LANG_VAR_POINTER_REDIRECTION (\$\[+LANG_.*\]+)
	 */
	private boolean isLangVarOrLangVarPointerRedirectionWithoutPrefix(String token) {
		char firstChar = token.charAt(0);
		return (firstChar == '$' || firstChar == '&') && (token.startsWith("LANG_",  1) || token.contains("[LANG_"));
	}
	
	/**
	 * LangPatterns: FUNC_CALL_VAR_ARGS ((\$|&)\w+\.\.\.)
	 */
	private boolean isFuncCallVarArgs(String token) {
		char firstChar = token.charAt(0);
		if(!((firstChar == '$' || firstChar == '&') && token.endsWith("...")))
			return false;
		
		boolean hasVarName = false;
		for(int i = 1;i < token.length() - 3;i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	/**
	 * LangPatterns: FUNC_CALL_CALL_BY_PTR (\$\[\w+\])
	 */
	private boolean isFuncCallCallByPtr(String token) {
		if(!(token.startsWith("$[") && token.endsWith("]")))
			return false;
		
		boolean hasVarName = false;
		for(int i = 2;i < token.length() - 1;i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	/**
	 * LangPatterns: FUNC_CALL_CALL_BY_PTR_LANG_VAR (\$\[LANG_.*\])
	 */
	private boolean isFuncCallCallByPtrLangVar(String token) {
		return token.startsWith("$[LANG_") && token.endsWith("]");
	}
	
	/**
	 * LangPatterns: VAR_NAME_WITHOUT_PREFIX ((\$|&|fp\.)\w+)
	 */
	private boolean isVarNameWithoutPrefix(String token) {
		boolean funcPtr = token.startsWith("fp.");
		
		char firstChar = token.charAt(0);
		if(!(funcPtr || firstChar == '$' || firstChar == '&'))
			return false;
		
		boolean hasVarName = false;
		for(int i = funcPtr?3:1;i < token.length();i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	/**
	 * LangPatterns: VAR_NAME_FULL ((\$\**|&|fp\.)\w+)
	 */
	private boolean isVarNameFullWithoutPrefix(String token) {
		boolean funcPtr = token.startsWith("fp.");
		char firstChar = token.charAt(0);
		boolean normalVar = firstChar == '$';
		
		if(!(funcPtr || normalVar || firstChar == '&'))
			return false;
		
		int i = funcPtr?3:1;
		
		if(normalVar)
			for(;i < token.length();i++)
				if(token.charAt(i) != '*')
					break;
		
		boolean hasVarName = false;
		for(;i < token.length();i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	/**
	 * LangPatterns: VAR_NAME_FULL_WITH_FUNCS ((\$\**|&|fp\.|func\.|fn\.|linker\.|ln\.)\w+)
	 */
	private boolean isVarNameFullWithFuncsWithoutPrefix(String token) {
		boolean funcPtr = token.startsWith("fp.");
		boolean func = token.startsWith("func.");
		boolean fn = token.startsWith("fn.");
		boolean linker = token.startsWith("linker.");
		boolean ln = token.startsWith("ln.");
		char firstChar = token.charAt(0);
		boolean normalVar = firstChar == '$';
		
		if(!(funcPtr || func || fn || linker || ln || normalVar || firstChar == '&'))
			return false;
		
		int i = (funcPtr || fn || ln)?3:(func?5:(linker?7:1));
		
		if(normalVar)
			for(;i < token.length();i++)
				if(token.charAt(i) != '*')
					break;
		
		boolean hasVarName = false;
		for(;i < token.length();i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	/**
	 * LangPatterns: VAR_NAME_PTR_AND_DEREFERENCE (\$\**\[+\w+\]+)
	 */
	private boolean isVarNamePtrAndDereferenceWithoutPrefix(String token) {
		if(token.charAt(0) != '$')
			return false;
		
		int i = 1;
		for(;i < token.length();i++)
			if(token.charAt(i) != '*')
				break;
		
		boolean hasNoBracketOpening = true;
		for(;i < token.length();i++) {
			if(token.charAt(i) == '[')
				hasNoBracketOpening = false;
			else
				break;
		}
		
		if(hasNoBracketOpening)
			return false;
		
		boolean hasNoVarName = true;
		for(;i < token.length();i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasNoVarName = false;
			else
				break;
		}
		
		if(hasNoVarName)
			return false;
		
		boolean hasBracketClosing = false;
		for(;i < token.length();i++)
			if(token.charAt(i) == ']')
				hasBracketClosing = true;
			else
				return false;
		
		return hasBracketClosing;
	}
	
	/**
	 * LangPatterns: FUNC_NAME ((func\.|fn\.|linker\.|ln\.)\w+)
	 */
	private boolean isFuncName(String token) {
		boolean func = token.startsWith("func.");
		boolean linker = token.startsWith("linker.");
		
		if(!(func || linker || token.startsWith("fn.") || token.startsWith("ln.")))
			return false;
		
		boolean hasVarName = false;
		for(int i = func?5:(linker?7:3);i < token.length();i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	/**
	 * LangPatterns: VAR_NAME_FUNC_PTR (fp\.\w+)
	 */
	private boolean isVarNameFuncPtrWithoutPrefix(String token) {
		if(!token.startsWith("fp."))
			return false;
		
		boolean hasVarName = false;
		for(int i = 3;i < token.length();i++) {
			char c = token.charAt(i);
			if((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')
				hasVarName = true;
			else
				return false;
		}
		
		return hasVarName;
	}
	
	void createDataMap(final int SCOPE_ID) {
		createDataMap(SCOPE_ID, null);
	}
	void createDataMap(final int SCOPE_ID, String[] langArgs) {
		data.put(SCOPE_ID, new Data());
		
		if(langArgs != null) {
			DataObject[] langArgsArray = new DataObject[langArgs.length];
			for(int i = 0;i < langArgs.length;i++)
				langArgsArray[i] = new DataObject(langArgs[i]);
			data.get(SCOPE_ID).var.put("&LANG_ARGS", new DataObject().setArray(langArgsArray).setFinalData(true).setVariableName("&LANG_ARGS"));
		}
		
		resetVarsAndFuncPtrs(SCOPE_ID);
	}
	private void resetVarsAndFuncPtrs(final int SCOPE_ID) {
		DataObject langArgs = data.get(SCOPE_ID).var.get("&LANG_ARGS");
		data.get(SCOPE_ID).var.clear();
		
		langVars.addLangVars(langArgs, SCOPE_ID);
	}
	void resetVars(final int SCOPE_ID) {
		Set<Map.Entry<String, DataObject>> entrySet = new HashSet<>(data.get(SCOPE_ID).var.entrySet());
		entrySet.forEach(entry -> {
			String key = entry.getKey();
			if(!entry.getValue().isLangVar() && (key.startsWith("$") || key.startsWith("&")))
				data.get(SCOPE_ID).var.remove(key);
		});
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, SCOPE_ID); //Set $LANG_ERRNO
	}
	
	void setErrno(InterpretingError error, final int SCOPE_ID) {
		setErrno(error, null, SCOPE_ID);
	}
	void setErrno(InterpretingError error, int lineNumber, final int SCOPE_ID) {
		setErrno(error, null, lineNumber, SCOPE_ID);
	}
	void setErrno(InterpretingError error, String message, final int SCOPE_ID) {
		setErrno(error, message, -1, false, SCOPE_ID);
	}
	void setErrno(InterpretingError error, String message, int lineNumber, final int SCOPE_ID) {
		setErrno(error, message, lineNumber, false, SCOPE_ID);
	}
	private void setErrno(InterpretingError error, String message, int lineNumber, boolean forceNoErrorOutput, final int SCOPE_ID) {
		int currentErrno = data.get(SCOPE_ID).var.get("$LANG_ERRNO").getInt();
		int newErrno = error.getErrorCode();
		
		if(newErrno >= 0 || currentErrno < 1)
			data.get(SCOPE_ID).var.get("$LANG_ERRNO").setInt(newErrno);
		
		if(!forceNoErrorOutput && executionFlags.errorOutput.shouldPrint(newErrno)) {
			if(message == null)
				message = "";
			
			StackElement currentStackElement = getCurrentCallStackElement();
			String langPath = currentStackElement.getLangPath();
			String langFile = currentStackElement.getLangFile();
			langFile = langFile == null?"<shell>":langFile;
			
			String langPathWithFile = langPath + (langPath.endsWith("/")?"":"/") + langFile;
			String langFunctionName = currentStackElement.getLangFunctionName();
			
			String output = String.format("A%s %s occured in \"%s:%s\" (FUNCTION: \"%s\", SCOPE_ID: \"%d\")!\n%s: %s (%d)%s\nStack trace:\n%s",
					newErrno < 0?"":"n", newErrno < 0?"warning":"error", langPathWithFile, lineNumber > 0?lineNumber:"x",
							langFunctionName == null?"<main>":langFunctionName, SCOPE_ID, newErrno < 0?"Warning":"Error",
									error.getErrorText(), error.getErrorCode(), message.isEmpty()?"":"\nMessage: " + message,
											printStackTrace(lineNumber));
			if(term == null)
				System.err.println(output);
			else
				term.logln(newErrno < 0?Level.WARNING:Level.ERROR, output, LangInterpreter.class);
		}
		
		if(newErrno > 0 && executionState.tryBlockLevel > 0 && (!executionState.isSoftTry || executionState.tryBodyScopeID == SCOPE_ID)) {
			executionState.tryThrownError = error;
			executionState.stopExecutionFlag = true;
		}
	}
	
	DataObject setErrnoErrorObject(InterpretingError error, final int SCOPE_ID) {
		return setErrnoErrorObject(error, null, SCOPE_ID);
	}
	DataObject setErrnoErrorObject(InterpretingError error, int lineNumber, final int SCOPE_ID) {
		return setErrnoErrorObject(error, null, lineNumber, SCOPE_ID);
	}
	DataObject setErrnoErrorObject(InterpretingError error, String message, final int SCOPE_ID) {
		return setErrnoErrorObject(error, message, -1, false, SCOPE_ID);
	}
	DataObject setErrnoErrorObject(InterpretingError error, String message, int lineNumber, final int SCOPE_ID) {
		return setErrnoErrorObject(error, message, lineNumber, false, SCOPE_ID);
	}
	private DataObject setErrnoErrorObject(InterpretingError error, String message, int lineNumber, boolean forceNoErrorOutput, final int SCOPE_ID) {
		setErrno(error, message, lineNumber, forceNoErrorOutput, SCOPE_ID);
		
		return new DataObject().setError(new ErrorObject(error, message));
	}
	InterpretingError getAndClearErrnoErrorObject(final int SCOPE_ID) {
		int errno = data.get(SCOPE_ID).var.get("$LANG_ERRNO").getInt();
		
		setErrno(InterpretingError.NO_ERROR, SCOPE_ID); //Reset errno
		
		return InterpretingError.getErrorFromErrorCode(errno);
	}
	
	public static final class Data {
		public final Map<String, String> lang = new HashMap<>();
		public final Map<String, DataObject> var = new HashMap<>();
	}
	
	//Classes for call stack
	public static final class StackElement {
		private final String langPath;
		private final String langFile;
		private final int lineNumber;
		private final String langFunctionName;
		final LangModule module;
		
		public StackElement(String langPath, String langFile, int lineNumber, String langFunctionName, LangModule module) {
			this.langPath = langPath;
			this.langFile = langFile;
			this.langFunctionName = langFunctionName;
			this.lineNumber = lineNumber;
			this.module = module;
		}
		public StackElement(String langPath, String langFile, String langFunctionName, LangModule module) {
			this(langPath, langFile, -1, langFunctionName, module);
		}
		
		public StackElement withLineNumber(int lineNumber) {
			return new StackElement(langPath, langFile, lineNumber, langFunctionName, module);
		}
		
		public String getLangPath() {
			return langPath;
		}
		
		public String getLangFile() {
			return langFile;
		}
		
		public int getLineNumber() {
			return lineNumber;
		}
		
		public String getLangFunctionName() {
			return langFunctionName;
		}
		
		public LangModule getModule() {
			return module;
		}
		
		@Override
		public String toString() {
			String langPathWithFile = langPath + (langPath.endsWith("/")?"":"/") + (langFile == null?"<shell>":langFile);
			return String.format("    at \"%s:%s\" in function \"%s\"", langPathWithFile, lineNumber > 0?lineNumber:"x", langFunctionName == null?"<main>":langFunctionName);
		}
	}
	
	//Classes for execution flags and execution of execution state
	public static class ExecutionFlags {
		/**
		 * Allow terminal function to redirect to standard input, output, or error if no terminal is available
		 */
		boolean allowTermRedirect = true;
		/**
		 * Will print all errors and warnings in the terminal or to standard error if no terminal is available
		 */
		ErrorOutputFlag errorOutput = ErrorOutputFlag.ERROR_ONLY;
		/**
		 * Will enable langTest unit tests (Can not be disabled if enabled once)
		 */
		boolean langTest = false;
		/**
		 * Will disable variable name processing which makes the interpreter faster
		 */
		boolean rawVariableNames = false;
		
		public static enum ErrorOutputFlag {
			NOTHING, ALL, ERROR_ONLY;
			
			public static ErrorOutputFlag getErrorFlagFor(int number) {
				if(number > 0)
					return ALL;
				
				if(number < 0)
					return ERROR_ONLY;
				
				return NOTHING;
			}
			
			public boolean shouldPrint(int errorCode) {
				return (errorCode < 0 && this == ALL) || (errorCode > 0 && this != NOTHING);
			}
		}
	}
	public static class ExecutionState {
		/**
		 * Will be set to true for returning/throwing a value or breaking/continuing a loop or for try statements
		 */
		private boolean stopExecutionFlag;
		private boolean forceStopExecutionFlag;
		
		//Fields for return statements
		private DataObject returnedOrThrownValue;
		private boolean isThrownValue;
		private int returnOrThrowStatementLineNumber = -1;
		
		//Fields for continue & break statements
		/**
		 * If > 0: break or continue statement is being processed
		 */
		private int breakContinueCount;
		private boolean isContinueStatement;
		
		//Fields for try statements
		/**
		 * Current try block level
		 */
		private int tryBlockLevel;
		private InterpretingError tryThrownError;
		private boolean isSoftTry;
		private int tryBodyScopeID;
	}
	
	public static enum InterpretingError {
		NO_ERROR               ( 0, "No Error"),
		
		//ERRORS
		FINAL_VAR_CHANGE       ( 1, "LANG or final vars must not be changed"),
		TO_MANY_INNER_LINKS    ( 2, "To many inner links"),
		NO_LANG_FILE           ( 3, "No .lang-File"),
		FILE_NOT_FOUND         ( 4, "File not found"),
		INVALID_FUNC_PTR       ( 5, "Function pointer is invalid"),
		STACK_OVERFLOW         ( 6, "Stack overflow"),
		NO_TERMINAL            ( 7, "No terminal available"),
		INVALID_ARG_COUNT      ( 8, "Invalid argument count"),
		INVALID_LOG_LEVEL      ( 9, "Invalid log level"),
		INVALID_ARR_PTR        (10, "Invalid array pointer"),
		NO_HEX_NUM             (11, "No hexadecimal number"),
		NO_CHAR                (12, "No char"),
		NO_NUM                 (13, "No number"),
		DIV_BY_ZERO            (14, "Dividing by 0"),
		NEGATIVE_ARRAY_LEN     (15, "Negative array length"),
		EMPTY_ARRAY            (16, "Empty array"),
		LENGTH_NAN             (17, "Length NAN"),
		INDEX_OUT_OF_BOUNDS    (18, "Index out of bounds"),
		ARG_COUNT_NOT_ARR_LEN  (19, "Argument count is not array length"),
		INVALID_FUNC_PTR_LOOP  (20, "Invalid function pointer"),
		INVALID_ARGUMENTS      (21, "Invalid arguments"),
		FUNCTION_NOT_FOUND     (22, "Function not found"),
		EOF                    (23, "End of file was reached early"),
		SYSTEM_ERROR           (24, "System Error"),
		NEGATIVE_REPEAT_COUNT  (25, "Negative repeat count"),
		TRANS_KEY_NOT_FOUND    (26, "Translation key does not exist"),
		FUNCTION_NOT_SUPPORTED (27, "Function not supported"),
		BRACKET_MISMATCH       (28, "Bracket mismatch"),
		CONT_FLOW_ARG_MISSING  (29, "Control flow statement condition(s) or argument(s) is/are missing"),
		INVALID_AST_NODE       (30, "Invalid AST node or AST node order"),
		INVALID_PTR            (31, "Invalid pointer"),
		INCOMPATIBLE_DATA_TYPE (32, "Incompatible data type"),
		LANG_ARRAYS_COPY       (33, "&LANG arrays can not be copied"),
		LANG_VER_ERROR         (34, "Lang file's version is not compatible with this version"),
		INVALID_CON_PART       (35, "Invalid statement in control flow statement"),
		INVALID_FORMAT         (36, "Invalid format sequence"),
		INVALID_ASSIGNMENT     (37, "Invalid assignment"),
		NO_BIN_NUM             (38, "No binary number"),
		NO_OCT_NUM             (39, "No octal number"),
		NO_BASE_N_NUM          (40, "Number is not in base N"),
		INVALID_NUMBER_BASE    (41, "Invalid number base"),
		INVALID_REGEX_SYNTAX   (42, "Invalid RegEx syntax"),
		INVALID_TEMPLATE_SYNTAX(43, "Invalid translation template syntax"),
		INVALID_MODULE         (44, "The Lang module is invalid"),
		MODULE_LOAD_UNLOAD_ERR (45, "Error during load or unload of Lang module"),
		
		//WARNINGS
		DEPRECATED_FUNC_CALL   (-1, "A deprecated predefined function was called"),
		NO_TERMINAL_WARNING    (-2, "No terminal available"),
		LANG_VER_WARNING       (-3, "Lang file's version is not compatible with this version"),
		INVALID_EXEC_FLAG_DATA (-4, "Execution flag or Lang data is invalid"),
		VAR_SHADOWING_WARNING  (-5, "Variable name shadows an other variable"),
		UNDEF_ESCAPE_SEQUENCE  (-6, "An undefined escape sequence was used");
		
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
		
		public LangInterpreter getInterpreter() {
			return interpreter;
		}
		
		public Map<Integer, Data> getData() {
			return interpreter.getData();
		}
		public Data getData(final int SCOPE_ID) {
			return interpreter.getData().get(SCOPE_ID);
		}
		
		public Map<String, String> getTranslationMap(final int SCOPE_ID) {
			Data data = getData(SCOPE_ID);
			if(data == null)
				return null;
			
			return data.lang;
		}
		public String getTranslation(final int SCOPE_ID, String key) {
			Map<String, String> translations = getTranslationMap(SCOPE_ID);
			if(translations == null)
				return null;
			
			return translations.get(key);
		}
		public void setTranslation(final int SCOPE_ID, String key, String value) {
			Map<String, String> translations = getTranslationMap(SCOPE_ID);
			if(translations != null)
				translations.put(key, value);
		}
		
		public Map<String, DataObject> getVarMap(final int SCOPE_ID) {
			Data data = getData(SCOPE_ID);
			if(data == null)
				return null;
			
			return data.var;
		}
		public DataObject getVar(final int SCOPE_ID, String varName) {
			Map<String, DataObject> vars = getVarMap(SCOPE_ID);
			if(vars == null)
				return null;
			
			return vars.get(varName);
		}
		public void setVar(final int SCOPE_ID, String varName, DataObject data) {
			setVar(SCOPE_ID, varName, data, false);
		}
		public void setVar(final int SCOPE_ID, String varName, DataObject data, boolean ignoreFinal) {
			Map<String, DataObject> vars = getVarMap(SCOPE_ID);
			if(vars != null) {
				DataObject oldData = vars.get(varName);
				if(oldData == null)
					vars.put(varName, data.setVariableName(varName));
				else if(ignoreFinal || !oldData.isFinalData())
					oldData.setData(data);
			}
		}
		public void setVar(final int SCOPE_ID, String varName, String text) {
			setVar(SCOPE_ID, varName, text, false);
		}
		public void setVar(final int SCOPE_ID, String varName, String text, boolean ignoreFinal) {
			setVar(SCOPE_ID, varName, new DataObject(text), ignoreFinal);
		}
		public void setVar(final int SCOPE_ID, String varName, DataObject[] arr) {
			setVar(SCOPE_ID, varName, arr, false);
		}
		public void setVar(final int SCOPE_ID, String varName, DataObject[] arr, boolean ignoreFinal) {
			setVar(SCOPE_ID, varName, new DataObject().setArray(arr), ignoreFinal);
		}
		public void setVar(final int SCOPE_ID, String varName, LangExternalFunctionObject function) {
			setVar(SCOPE_ID, varName, function, false);
		}
		public void setVar(final int SCOPE_ID, String varName, LangExternalFunctionObject function, boolean ignoreFinal) {
			setVar(SCOPE_ID, varName, new DataObject().setFunctionPointer(new FunctionPointerObject(varName, function)), ignoreFinal);
		}
		public void setVar(final int SCOPE_ID, String varName, InterpretingError error) {
			setVar(SCOPE_ID, varName, error, false);
		}
		public void setVar(final int SCOPE_ID, String varName, InterpretingError error, boolean ignoreFinal) {
			setVar(SCOPE_ID, varName, new DataObject().setError(new ErrorObject(error)), false);
		}
		
		public void setErrno(InterpretingError error, final int SCOPE_ID) {
			setErrno(error, "", SCOPE_ID);
		}
		public void setErrno(InterpretingError error, int lineNumber, final int SCOPE_ID) {
			setErrno(error, "", lineNumber, SCOPE_ID);
		}
		public void setErrno(InterpretingError error, String message, final int SCOPE_ID) {
			interpreter.setErrno(error, message, SCOPE_ID);
		}
		public void setErrno(InterpretingError error, String message, int lineNumber, final int SCOPE_ID) {
			interpreter.setErrno(error, message, lineNumber, SCOPE_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, final int SCOPE_ID) {
			return setErrnoErrorObject(error, "", SCOPE_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, int lineNumber, final int SCOPE_ID) {
			return setErrnoErrorObject(error, "", lineNumber, SCOPE_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, String message, final int SCOPE_ID) {
			return interpreter.setErrnoErrorObject(error, message, SCOPE_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, String message, int lineNumber, final int SCOPE_ID) {
			return interpreter.setErrnoErrorObject(error, message, lineNumber, SCOPE_ID);
		}
		public InterpretingError getAndClearErrnoErrorObject(final int SCOPE_ID) {
			return interpreter.getAndClearErrnoErrorObject(SCOPE_ID);
		}
		
		/**
		 * Creates an function which is accessible globally in the Interpreter (= in all SCOPE_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]"/"fn.[funcName]" or with "linker.[funcName]"/"ln.[funcName]" and can't be removed nor changed by the Lang file
		 */
		public void addNativeFunction(String funcName, LangNativeFunction function) {
			interpreter.funcs.put(funcName, function);
		}
		/**
		 * Creates an function which is accessible globally in the Interpreter (= in all SCOPE_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]"/"fn.[funcName]" or with "linker.[funcName]"/"ln.[funcName]" and can't be removed nor changed by the Lang file
		 */
		public void addPredefinedFunction(String funcName, LangPredefinedFunctionObject function) {
			interpreter.funcs.put(funcName, function);
		}
		public void addPredefinedFunctions(Map<String, ? extends LangPredefinedFunctionObject> funcs) {
			interpreter.funcs.putAll(funcs);
		}
		public Map<String, LangPredefinedFunctionObject> getPredefinedFunctions() {
			return interpreter.funcs;
		}
		
		public DataObject exec(final int SCOPE_ID, BufferedReader lines) throws IOException, StoppedException {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			return interpreter.interpretLines(lines, SCOPE_ID);
		}
		public DataObject exec(final int SCOPE_ID, String lines) throws IOException, StoppedException {
			try(BufferedReader reader = new BufferedReader(new StringReader(lines))) {
				return exec(SCOPE_ID, reader);
			}
		}
		/**
		 * Can be called in a different thread<br>
		 * Any execution method which was previously called and are still running and any future call of execution methods will throw a
		 * {@link me.jddev0.module.lang.LangInterpreter.StoppedException StoppedException} exception if the stop flag is set
		 */
		public void stop() {
			interpreter.executionState.forceStopExecutionFlag = true;
		}
		/**
		 * Must be called before execution if the {@link LangInterpreter.LangInterpreterInterface#stop() stop()} method was previously called
		 */
		public void resetStopFlag() {
			interpreter.executionState.forceStopExecutionFlag = false;
		}
		
		public void setErrorOutputFlag(ExecutionFlags.ErrorOutputFlag errorOutput) {
			if(errorOutput == null)
				throw new NullPointerException();
			
			interpreter.executionFlags.errorOutput = errorOutput;
		}
		
		public StackElement getCurrentCallStackElement() {
			return interpreter.getCurrentCallStackElement();
		}
		
		/**
		 * Must be called before {@link LangInterpreter.LangInterpreterInterface#getAndResetReturnValue() getAndResetReturnValue()} method
		 */
		public boolean isReturnedValueThrowValue() {
			return interpreter.executionState.isThrownValue;
		}
		public int getThrowStatementLineNumber() {
			return interpreter.executionState.returnOrThrowStatementLineNumber;
		}
		public DataObject getAndResetReturnValue() {
			return interpreter.getAndResetReturnValue(-1);
		}
		
		public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
			return interpreter.parseLines(lines);
		}
		
		public void interpretAST(final int SCOPE_ID, AbstractSyntaxTree ast) throws StoppedException {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretAST(ast, SCOPE_ID);
		}
		public DataObject inerpretNode(final int SCOPE_ID, Node node) throws StoppedException {
			return interpreter.interpretNode(null, node, SCOPE_ID);
		}
		public DataObject interpretFunctionCallNode(final int SCOPE_ID, FunctionCallNode node) throws StoppedException {
			return interpreter.interpretFunctionCallNode(null, node, SCOPE_ID);
		}
		public DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList,
				int parentLineNumber, final int SCOPE_ID) throws StoppedException {
			return interpreter.callFunctionPointer(fp, functionName, interpreter.interpretFunctionPointerArguments(argumentList, SCOPE_ID), parentLineNumber, SCOPE_ID);
		}
		public DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList, final int SCOPE_ID) throws StoppedException {
			return interpreter.callFunctionPointer(fp, functionName, interpreter.interpretFunctionPointerArguments(argumentList, SCOPE_ID), SCOPE_ID);
		}
		
		public int getParserLineNumber() {
			return interpreter.getParserLineNumber();
		}
		
		public void setParserLineNumber(int lineNumber) {
			interpreter.setParserLineNumber(lineNumber);
		}
		
		public void resetParserPositionVars() {
			interpreter.resetParserPositionVars();
		}
		
		public DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList,
				int parentLineNumber, final int SCOPE_ID) throws StoppedException {
			return interpreter.callFunctionPointer(fp, functionName, argumentValueList, parentLineNumber, SCOPE_ID);
		}
		public DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int SCOPE_ID) throws StoppedException {
			return interpreter.callFunctionPointer(fp, functionName, argumentValueList, SCOPE_ID);
		}
		
		public Map<String, LangModule> getModules() {
			return interpreter.modules;
		}
		
		public List<String> getModuleExportedFunctions(String moduleName) {
			LangModule module = interpreter.modules.get(moduleName);
			return module == null?null:module.getExportedFunctions();
		}
		
		public Map<String, DataObject> getModuleExportedVariables(String moduleName) {
			LangModule module = interpreter.modules.get(moduleName);
			return module == null?null:module.getExportedVariables();
		}
	}
	
	/**
	 * Exception which will be thrown if {@link LangInterpreter#forceStop() forceStop()} or {@link LangInterpreter.LangInterpreterInterface#stop() stop()} is called
	 */
	public static class StoppedException extends RuntimeException {
		private static final long serialVersionUID = 3184689513001702458L;
		
		public StoppedException() {
			super("The execution was stopped!");
		}
	}
}