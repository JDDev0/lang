package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.AbstractSyntaxTree.*;
import me.jddev0.module.lang.AbstractSyntaxTree.OperationNode.Operator;
import me.jddev0.module.lang.AbstractSyntaxTree.OperationNode.OperatorType;
import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraintViolatedException;
import me.jddev0.module.lang.DataObject.ErrorObject;
import me.jddev0.module.lang.DataObject.FunctionPointerObject;
import me.jddev0.module.lang.DataObject.VarPointerObject;

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
	
	private final LinkedList<StackElement> callStack;
	
	final TerminalIO term;
	final LangPlatformAPI langPlatformAPI;
	
	//Lang tests
	final LangTest langTestStore = new LangTest();
	InterpretingError langTestExpectedThrowValue;
	DataObject langTestExpectedReturnValue;
	boolean langTestExpectedNoReturnValue;
	String langTestMessageForLastTestResult;
	int langTestExpectedReturnValueDataID;
	
	//Fields for return/throw node, continue/break node, and force stopping execution
	private final ExecutionState executionState = new ExecutionState();
	/**
	 * <DATA_ID (of function), <to, from>><br>
	 * Data tmp for "func.copyAfterFP"
	 */
	final Map<Integer, Map<String, String>> copyAfterFP = new HashMap<>();
	final ExecutionFlags executionFlags = new ExecutionFlags();
	
	//DATA
	final Map<Integer, Data> data = new HashMap<>();
	
	//Predefined functions & linker functions (= Predefined functions)
	private Map<String, LangPredefinedFunctionObject> funcs = new HashMap<>();
	{
		LangPredefinedFunctions predefinedFunctions = new LangPredefinedFunctions(this);
		predefinedFunctions.addPredefinedFunctions(funcs);
		predefinedFunctions.addLinkerFunctions(funcs);
	}
	
	/**
	 * @param term can be null
	 */
	public LangInterpreter(String langPath, TerminalIO term, LangPlatformAPI langPlatformAPI) {
		this(langPath, null, term, langPlatformAPI, null);
	}
	/**
	 * @param langFile can be null
	 * @param term can be null
	 */
	public LangInterpreter(String langPath, String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI) {
		this(langPath, langFile, term, langPlatformAPI, null);
	}
	/**
	 * @param term can be null
	 * @param langArgs can be null
	 */
	public LangInterpreter(String langPath, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) {
		this(langPath, null, term, langPlatformAPI, langArgs);
	}
	/**
	 * @param langFile can be null
	 * @param term can be null
	 * @param langArgs can be null
	 */
	public LangInterpreter(String langPath, String langFile, TerminalIO term, LangPlatformAPI langPlatformAPI, String[] langArgs) {
		callStack = new LinkedList<>();
		callStack.add(new StackElement(langPath, langFile, null));
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
		return callStack.peekLast();
	}
	
	void pushStackElement(StackElement stackElement) {
		callStack.addLast(stackElement);
	}
	
	StackElement popStackElement() {
		return callStack.pollLast();
	}
	
	private String printStackTrace() {
		StringBuilder builder = new StringBuilder();
		
		ListIterator<StackElement> iter = callStack.listIterator(callStack.size());
		while(true) {
			builder.append(iter.previous());
			if(iter.hasPrevious())
				builder.append("\n");
			else
				break;
		}
		
		return builder.toString();
	}
	
	boolean interpretCondition(OperationNode node, final int DATA_ID) throws StoppedException {
		return interpretOperationNode(node, DATA_ID).getBoolean();
	}
	
	void interpretLines(BufferedReader lines, final int DATA_ID) throws IOException, StoppedException {
		interpretAST(parseLines(lines), DATA_ID);
	}
	
	void interpretAST(AbstractSyntaxTree ast, final int DATA_ID) {
		if(ast == null)
			return;
		
		if(executionState.forceStopExecutionFlag)
			throw new StoppedException();
		
		for(Node node:ast) {
			if(executionState.stopExecutionFlag)
				return;
			
			interpretNode(node, DATA_ID);
		}
	}
	
	/**
	 * @return Might return null
	 */
	private DataObject interpretNode(Node node, final int DATA_ID) {
		if(executionState.forceStopExecutionFlag)
			throw new StoppedException();
		
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
				
				case LOOP_STATEMENT:
					return new DataObject().setBoolean(interpretLoopStatementNode((LoopStatementNode)node, DATA_ID));
				
				case LOOP_STATEMENT_PART_WHILE:
				case LOOP_STATEMENT_PART_UNTIL:
				case LOOP_STATEMENT_PART_REPEAT:
				case LOOP_STATEMENT_PART_FOR_EACH:
				case LOOP_STATEMENT_PART_LOOP:
				case LOOP_STATEMENT_PART_ELSE:
					return new DataObject().setBoolean(interpretLoopStatementPartNode((LoopStatementPartNode)node, DATA_ID));
				
				case LOOP_STATEMENT_CONTINUE_BREAK:
					interpretLoopStatementContinueBreak((LoopStatementContinueBreakStatement)node, DATA_ID);
					return null;
				
				case TRY_STATEMENT:
					return new DataObject().setBoolean(interpretTryStatementNode((TryStatementNode)node, DATA_ID));
				
				case TRY_STATEMENT_PART_TRY:
				case TRY_STATEMENT_PART_CATCH:
				case TRY_STATEMENT_PART_ELSE:
				case TRY_STATEMENT_PART_FINALLY:
					return new DataObject().setBoolean(interpretTryStatementPartNode((TryStatementPartNode)node, DATA_ID));
				
				case OPERATION:
				case MATH:
				case CONDITION:
					return interpretOperationNode((OperationNode)node, DATA_ID);
				
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
				
				case ARRAY:
					return interpretArrayNode((ArrayNode)node, DATA_ID);
				
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
			setErrno(InterpretingError.INVALID_AST_NODE, "Invalid variable name", DATA_ID);
			
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
					return new DataObject();
				case VOID_VALUE:
					return new DataObject().setVoid();
				
				default:
					break;
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
		}
		
		if(error == null)
			error = InterpretingError.INVALID_AST_NODE;
		return setErrnoErrorObject(error, node.getMessage() == null?"":node.getMessage(), DATA_ID);
	}
	
	/**
	 * @return Returns true if any condition was true and if any block was executed
	 */
	private boolean interpretIfStatementNode(IfStatementNode node, final int DATA_ID) {
		List<IfStatementPartNode> ifPartNodes = node.getIfStatementPartNodes();
		if(ifPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Empty if statement", DATA_ID);
			
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
					if(!interpretOperationNode(((IfStatementPartIfNode)node).getCondition(), DATA_ID).getBoolean())
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
	
	/**
	 * @return Returns true if at least one loop iteration was executed
	 */
	private boolean interpretLoopStatementNode(LoopStatementNode node, final int DATA_ID) {
		List<LoopStatementPartNode> loopPartNodes = node.getLoopStatementPartNodes();
		if(loopPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Empty loop statement", DATA_ID);
			
			return false;
		}
		
		for(LoopStatementPartNode loopPartNode:loopPartNodes)
			if(interpretLoopStatementPartNode(loopPartNode, DATA_ID))
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
	private boolean interpretLoopStatementPartNode(LoopStatementPartNode node, final int DATA_ID) {
		boolean flag = false;
		
		try {
			switch(node.getNodeType()) {
				case LOOP_STATEMENT_PART_LOOP:
					while(true) {
						interpretAST(node.getLoopBody(), DATA_ID);
						Boolean ret = interpretLoopContinueAndBreak();
						if(ret != null) {
							if(ret)
								return true;
							else
								continue;
						}
					}
				case LOOP_STATEMENT_PART_WHILE:
					while(interpretOperationNode(((LoopStatementPartWhileNode)node).getCondition(), DATA_ID).getBoolean()) {
						flag = true;
						
						interpretAST(node.getLoopBody(), DATA_ID);
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
					while(!interpretOperationNode(((LoopStatementPartUntilNode)node).getCondition(), DATA_ID).getBoolean()) {
						flag = true;
						
						interpretAST(node.getLoopBody(), DATA_ID);
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
					DataObject varPointer = interpretNode(repeatNode.getVarPointerNode(), DATA_ID);
					if(varPointer.getType() != DataType.VAR_POINTER && varPointer.getType() != DataType.NULL) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.repeat needs a variablePointer or a null value for the current iteration variable", DATA_ID);
						return false;
					}
					DataObject var = varPointer.getType() == DataType.NULL?null:varPointer.getVarPointer().getVar();
					
					DataObject numberObject = interpretNode(repeatNode.getRepeatCountNode(), DATA_ID);
					Number number = numberObject == null?null:numberObject.toNumber();
					if(number == null) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.repeat needs a repeat count value", DATA_ID);
						return false;
					}
					
					int iterations = number.intValue();
					if(iterations < 0) {
						setErrno(InterpretingError.INVALID_ARGUMENTS, "con.repeat repeat count can not be less than 0", DATA_ID);
						return false;
					}
					
					for(int i = 0;i < iterations;i++) {
						flag = true;
						
						if(var != null) {
							if(var.isFinalData() || var.isLangVar())
								setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.repeat current iteration value can not be set", DATA_ID);
							else
								var.setInt(i);
						}
						
						interpretAST(node.getLoopBody(), DATA_ID);
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
					varPointer = interpretNode(forEachNode.getVarPointerNode(), DATA_ID);
					if(varPointer.getType() != DataType.VAR_POINTER) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con.foreach needs a variablePointer for the current element variable", DATA_ID);
						return false;
					}
					
					var = varPointer.getVarPointer().getVar();
					
					DataObject arrayOrTextNode = interpretNode(forEachNode.getArrayOrTextNode(), DATA_ID);
					if(arrayOrTextNode.getType() == DataType.ARRAY) {
						DataObject[] arr = arrayOrTextNode.getArray();
						for(int i = 0;i < arr.length;i++) {
							flag = true;
							
							if(var != null) {
								if(var.isFinalData() || var.isLangVar())
									setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", DATA_ID);
								else
									var.setData(arr[i]);
							}
							
							interpretAST(node.getLoopBody(), DATA_ID);
							Boolean ret = interpretLoopContinueAndBreak();
							if(ret != null) {
								if(ret)
									return true;
								else
									continue;
							}
						}
					}else {
						String text = arrayOrTextNode.getText();
						for(int i = 0;i < text.length();i++) {
							flag = true;
							
							if(var != null) {
								if(var.isFinalData() || var.isLangVar())
									setErrno(InterpretingError.FINAL_VAR_CHANGE, "con.foreach current element value can not be set", DATA_ID);
								else
									var.setChar(text.charAt(i));
							}
							
							interpretAST(node.getLoopBody(), DATA_ID);
							Boolean ret = interpretLoopContinueAndBreak();
							if(ret != null) {
								if(ret)
									return true;
								else
									continue;
							}
						}
					}
					
					break;
				case LOOP_STATEMENT_PART_ELSE:
					flag = true;
					interpretAST(node.getLoopBody(), DATA_ID);
					break;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return flag;
	}
	
	private void interpretLoopStatementContinueBreak(LoopStatementContinueBreakStatement node, final int DATA_ID) {
		Node numberNode = node.getNumberNode();
		if(numberNode == null) {
			executionState.breakContinueCount = 1;
		}else {
			DataObject numberObject = interpretNode(numberNode, DATA_ID);
			Number number = numberObject == null?null:numberObject.toNumber();
			if(number == null) {
				setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "con." + (node.isContinueNode()?"continue":"break") + " needs either non value or a level number", DATA_ID);
				return;
			}
			
			executionState.breakContinueCount = number.intValue();
			if(executionState.breakContinueCount < 1) {
				executionState.breakContinueCount = 0;
				
				setErrno(InterpretingError.INVALID_ARGUMENTS, "con." + (node.isContinueNode()?"continue":"break") + " the level must be > 0", DATA_ID);
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
		savedExecutionState.breakContinueCount = executionState.breakContinueCount;
		savedExecutionState.isContinueStatement = executionState.isContinueStatement;
		executionState.stopExecutionFlag = false;
		executionState.returnedOrThrownValue = null;
		executionState.isThrownValue = false;
		executionState.breakContinueCount = 0;
		executionState.isContinueStatement = false;
	}
	/**
	 * @return Returns true if a catch or an else block was executed
	 */
	private boolean interpretTryStatementNode(TryStatementNode node, final int DATA_ID) {
		List<TryStatementPartNode> tryPartNodes = node.getTryStatementPartNodes();
		if(tryPartNodes.isEmpty()) {
			setErrno(InterpretingError.INVALID_AST_NODE, "Empty try statement", DATA_ID);
			
			return false;
		}
		
		ExecutionState savedExecutionState = new ExecutionState();
		
		TryStatementPartNode tryPart = tryPartNodes.get(0);
		if(tryPart.getNodeType() != NodeType.TRY_STATEMENT_PART_TRY) {
			setErrno(InterpretingError.INVALID_AST_NODE, "First part of try statement was no try part", DATA_ID);
			
			return false;
		}
		interpretTryStatementPartNode(tryPart, DATA_ID);
		
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
				if(flag = interpretTryStatementPartNode(catchPart, DATA_ID)) {
					if(executionState.stopExecutionFlag) {
						saveExecutionStopStateToVarAndReset(savedExecutionState);
					}else {
						//Reset saved execution state because the reason of the execution stop was handled by the catch block
						savedExecutionState = new ExecutionState();
						executionState.tryThrownError = null;
					}
					
					//Error was handled: reset $LANG_ERRNO
					getAndClearErrnoErrorObject(DATA_ID);
					
					break;
				}
			}
		}
		
		if(!flag && !savedExecutionState.stopExecutionFlag) {
			TryStatementPartNode elsePart = null;
			if(!flag && tryPartNodes.size() > 1) {
				if(tryPartNodes.get(tryPartNodes.size() - 2).getNodeType() == NodeType.TRY_STATEMENT_PART_ELSE)
					elsePart = tryPartNodes.get(tryPartNodes.size() - 2);
				if(tryPartNodes.get(tryPartNodes.size() - 1).getNodeType() == NodeType.TRY_STATEMENT_PART_ELSE)
					elsePart = tryPartNodes.get(tryPartNodes.size() - 1);
			}
			if(elsePart != null) {
				flag = interpretTryStatementPartNode(elsePart, DATA_ID);
				
				if(executionState.stopExecutionFlag)
					saveExecutionStopStateToVarAndReset(savedExecutionState);
			}
		}
		
		TryStatementPartNode finallyPart = null;
		if(tryPartNodes.size() > 1 && tryPartNodes.get(tryPartNodes.size() - 1).getNodeType() == NodeType.TRY_STATEMENT_PART_FINALLY)
			finallyPart = tryPartNodes.get(tryPartNodes.size() - 1);
		
		if(finallyPart != null)
			interpretTryStatementPartNode(finallyPart, DATA_ID);
		
		//Reset saved execution flag to stop execution if finally has not set the stop execution flag
		if(!executionState.stopExecutionFlag) {
			executionState.stopExecutionFlag = savedExecutionState.stopExecutionFlag;
			executionState.returnedOrThrownValue = savedExecutionState.returnedOrThrownValue;
			executionState.isThrownValue = savedExecutionState.isThrownValue;
			executionState.breakContinueCount = savedExecutionState.breakContinueCount;
			executionState.isContinueStatement = savedExecutionState.isContinueStatement;
		}
		
		return flag;
	}
	
	/**
	 * @return Returns true if a catch or an else block was executed
	 */
	private boolean interpretTryStatementPartNode(TryStatementPartNode node, final int DATA_ID) {
		boolean flag = false;
		
		try {
			switch(node.getNodeType()) {
				case TRY_STATEMENT_PART_TRY:
					executionState.tryThrownError = null;
					executionState.tryBlockLevel++;
					
					try {
						interpretAST(node.getTryBody(), DATA_ID);
					}finally {
						executionState.tryBlockLevel--;
					}
					break;
				case TRY_STATEMENT_PART_CATCH:
					if(executionState.tryThrownError == null)
						return false;
					
					TryStatementPartCatchNode catchNode = (TryStatementPartCatchNode)node;
					if(catchNode.getExpections() != null) {
						if(catchNode.getExpections().size() == 0) {
							setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Empty catch part \"catch()\" is not allowed!\n"
									+ "For checking all warnings \"catch\" without \"()\" should be used", DATA_ID);
							
							return false;
						}
						
						List<DataObject> catchErrors = new LinkedList<>();
						List<DataObject> interpretedNodes = new LinkedList<>();
						int foundErrorIndex = -1;
						DataObject previousDataObject = null;
						for(Node argument:catchNode.getExpections()) {
							if(argument.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE && previousDataObject != null) {
								try {
									Node ret = processFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)argument, previousDataObject, DATA_ID);
									if(ret.getNodeType() == NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE) {
										interpretedNodes.remove(interpretedNodes.size() - 1); //Remove last data Object, because it is used as function pointer for a function call
										interpretedNodes.add(interpretFunctionCallPreviousNodeValueNode((FunctionCallPreviousNodeValueNode)ret, previousDataObject, DATA_ID));
									}else {
										interpretedNodes.add(interpretNode(ret, DATA_ID));
									}
								}catch(ClassCastException e) {
									interpretedNodes.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID));
								}
								
								previousDataObject = interpretedNodes.get(interpretedNodes.size() - 1);
								
								continue;
							}
							
							DataObject argumentValue = interpretNode(argument, DATA_ID);
							if(argumentValue == null) {
								previousDataObject = null;
								
								continue;
							}
							
							interpretedNodes.add(argumentValue);
							previousDataObject = argumentValue;
						}
						while(!interpretedNodes.isEmpty()) {
							DataObject dataObject = new DataObject(LangUtils.getNextArgumentAndRemoveUsedDataObjects(interpretedNodes, true));
							if(dataObject.getType() != DataType.ERROR) {
								setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Variable with type other than ERROR in catch statement", DATA_ID);
								
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
					
					interpretAST(node.getTryBody(), DATA_ID);
					break;
				case TRY_STATEMENT_PART_ELSE:
					if(executionState.tryThrownError != null)
						return false;
					
					flag = true;
					
					interpretAST(node.getTryBody(), DATA_ID);
					break;
				case TRY_STATEMENT_PART_FINALLY:
					interpretAST(node.getTryBody(), DATA_ID);
					break;
				
				default:
					break;
			}
		}catch(ClassCastException e) {
			setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
		}
		
		return flag;
	}
	
	private DataObject interpretOperationNode(OperationNode node, final int DATA_ID) {
		DataObject leftSideOperand = interpretNode(node.getLeftSideOperand(), DATA_ID);
		DataObject middleOperand = (!node.getOperator().isTernary() || node.getOperator().isLazyEvaluation())?null:interpretNode(node.getMiddleOperand(), DATA_ID);
		DataObject rightSideOperand = (node.getOperator().isUnary() || node.getOperator().isLazyEvaluation())?null:interpretNode(node.getRightSideOperand(), DATA_ID);
		if(leftSideOperand == null || (!node.getOperator().isLazyEvaluation() && ((!node.getOperator().isUnary() && rightSideOperand == null) ||
		(node.getOperator().isTernary() && middleOperand == null))))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", DATA_ID);
		
		if(node.getOperatorType() == OperatorType.GENERAL) {
			DataObject output;
			switch(node.getOperator()) {
				//Unary
				case NON:
					return leftSideOperand;
				case LEN:
					output = leftSideOperand.opLen();
					break;
				case DEEP_COPY:
					output = leftSideOperand.opDeepCopy();
					break;
				
				//Binary
				case SPACESHIP:
					output = leftSideOperand.opSpaceship(rightSideOperand);
					break;
				case ELVIS:
					if(leftSideOperand.getBoolean())
						return leftSideOperand;
					
					rightSideOperand = interpretNode(node.getRightSideOperand(), DATA_ID);
					if(rightSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", DATA_ID);
					return rightSideOperand;
				case NULL_COALESCING:
					if(leftSideOperand.getType() != DataType.NULL && leftSideOperand.getType() != DataType.VOID)
						return leftSideOperand;
					
					rightSideOperand = interpretNode(node.getRightSideOperand(), DATA_ID);
					if(rightSideOperand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", DATA_ID);
					return rightSideOperand;
				
				//Ternary
				case INLINE_IF:
					DataObject operand = leftSideOperand.getBoolean()?interpretNode(node.getMiddleOperand(), DATA_ID):interpretNode(node.getRightSideOperand(), DATA_ID);
					
					if(operand == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", DATA_ID);
					return operand;
				
				default:
					return null;
			}
			
			if(output == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The \"" + node.getOperator().getSymbol() + "\" operator is not defined for " + leftSideOperand.getType().name() + (
					node.getOperator().isTernary()?", " + middleOperand.getType().name() + ",":"") + (!node.getOperator().isUnary()?" and " + rightSideOperand.getType().name():""), DATA_ID);
			
			if(output.getType() == DataType.ERROR)
				return setErrnoErrorObject(output.getError().getInterprettingError(), output.getError().getMessage(), DATA_ID);
			
			return output;
		}else if(node.getOperatorType() == OperatorType.MATH) {
			DataObject output = null;
			
			switch(node.getOperator()) {
				//Unary
				case MATH_NON:
					output = leftSideOperand;
					break;
				case POS:
					output = leftSideOperand.opPos();
					break;
				case INV:
					output = leftSideOperand.opInv();
					break;
				case BITWISE_NOT:
					output = leftSideOperand.opNot();
					break;
				case INC:
					output = leftSideOperand.opInc();
					break;
				case DEC:
					output = leftSideOperand.opDec();
					break;
				
				//Binary
				case POW:
					output = leftSideOperand.opPow(rightSideOperand);
					break;
				case MUL:
					output = leftSideOperand.opMul(rightSideOperand);
					break;
				case DIV:
					output = leftSideOperand.opDiv(rightSideOperand);
					break;
				case TRUNC_DIV:
					output = leftSideOperand.opTruncDiv(rightSideOperand);
					break;
				case FLOOR_DIV:
					output = leftSideOperand.opFloorDiv(rightSideOperand);
					break;
				case CEIL_DIV:
					output = leftSideOperand.opCeilDiv(rightSideOperand);
					break;
				case MOD:
					output = leftSideOperand.opMod(rightSideOperand);
					break;
				case ADD:
					output = leftSideOperand.opAdd(rightSideOperand);
					break;
				case SUB:
					output = leftSideOperand.opSub(rightSideOperand);
					break;
				case LSHIFT:
					output = leftSideOperand.opLshift(rightSideOperand);
					break;
				case RSHIFT:
					output = leftSideOperand.opRshift(rightSideOperand);
					break;
				case RZSHIFT:
					output = leftSideOperand.opRzshift(rightSideOperand);
					break;
				case BITWISE_AND:
					output = leftSideOperand.opAnd(rightSideOperand);
					break;
				case BITWISE_XOR:
					output = leftSideOperand.opXor(rightSideOperand);
					break;
				case BITWISE_OR:
					output = leftSideOperand.opOr(rightSideOperand);
					break;
				case GET_ITEM:
					output = leftSideOperand.opGetItem(rightSideOperand);
					break;
				
				default:
					break;
			}
			
			if(output == null)
				return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The \"" + node.getOperator().getSymbol() + "\" operator is not defined for " + leftSideOperand.getType().name() + (
					node.getOperator().isTernary()?", " + middleOperand.getType().name() + ",":"") + (!node.getOperator().isUnary()?" and " + rightSideOperand.getType().name():""), DATA_ID);
			
			if(output.getType() == DataType.ERROR)
				return setErrnoErrorObject(output.getError().getInterprettingError(), output.getError().getMessage(), DATA_ID);
			
			return output;
		}else if(node.getOperatorType() == OperatorType.CONDITION) {
			boolean conditionOuput = false;
			
			switch(node.getOperator()) {
				//Unary (Logical operators)
				case CONDITIONAL_NON:
				case NOT:
					conditionOuput = leftSideOperand.getBoolean();
					
					if(node.getOperator() == Operator.NOT)
						conditionOuput = !conditionOuput;
					break;
				
				//Binary (Logical operators)
				case AND:
					boolean leftSideOperandBoolean = leftSideOperand.getBoolean();
					if(leftSideOperandBoolean) {
						rightSideOperand = interpretNode(node.getRightSideOperand(), DATA_ID);
						if(rightSideOperand == null)
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", DATA_ID);
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
							return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Missing operand", DATA_ID);
						conditionOuput = rightSideOperand.getBoolean();
					}
					break;
				
				//Binary (Comparison operators)
				case INSTANCE_OF:
					if(rightSideOperand.getType() != DataType.TYPE)
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "The second operand of the \"" + node.getOperator().getSymbol() + "\" operator must be of type " +
							DataType.TYPE.name(), DATA_ID);
					
					conditionOuput = leftSideOperand.getType() == rightSideOperand.getTypeValue();
					break;
				case EQUALS:
				case NOT_EQUALS:
					conditionOuput = leftSideOperand.isEquals(rightSideOperand);
					
					if(node.getOperator() == Operator.NOT_EQUALS)
						conditionOuput = !conditionOuput;
					break;
				case MATCHES:
				case NOT_MATCHES:
					try {
						conditionOuput = leftSideOperand.getText().matches(rightSideOperand.getText());
					}catch(PatternSyntaxException e) {
						return setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Invalid RegEx expression: " + e.getMessage(), DATA_ID);
					}
					
					if(node.getOperator() == Operator.NOT_MATCHES)
						conditionOuput = !conditionOuput;
					break;
				case STRICT_EQUALS:
				case STRICT_NOT_EQUALS:
					conditionOuput = leftSideOperand.isStrictEquals(rightSideOperand);
					
					if(node.getOperator() == Operator.STRICT_NOT_EQUALS)
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
				
				default:
					break;
			}
			
			return new DataObject().setBoolean(conditionOuput);
		}
		
		return null;
	}
	
	private void interpretReturnNode(ReturnNode node, final int DATA_ID) {
		Node returnValueNode = node.getReturnValue();
		
		executionState.returnedOrThrownValue = returnValueNode == null?null:interpretNode(returnValueNode, DATA_ID);
		executionState.isThrownValue = false;
		executionState.stopExecutionFlag = true;
	}
	
	private void interpretThrowNode(ThrowNode node, final int DATA_ID) {
		Node throwValueNode = node.getThrowValue();
		
		DataObject errorObject = interpretNode(throwValueNode, DATA_ID);
		if(errorObject == null || errorObject.getType() != DataType.ERROR)
			executionState.returnedOrThrownValue = new DataObject().setError(new ErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE));
		else
			executionState.returnedOrThrownValue = errorObject;
		executionState.isThrownValue = true;
		executionState.stopExecutionFlag = true;
		
		if(executionState.returnedOrThrownValue.getError().getErrno() > 0 && executionState.tryBlockLevel > 0) {
			executionState.tryThrownError = executionState.returnedOrThrownValue.getError().getInterprettingError();
			executionState.stopExecutionFlag = true;
		}
	}
	
	private void interpretLangDataAndExecutionFlags(String langDataExecutionFlag, DataObject value, final int DATA_ID) {
		if(value == null)
			value = new DataObject(); //Set value to null data object
		
		switch(langDataExecutionFlag) {
			//Data
			case "lang.version":
				String langVer = value.getText();
				if(!langVer.equals(VERSION)) {
					if(VERSION.compareTo(langVer) > 0)
						setErrno(InterpretingError.LANG_VER_WARNING, "Lang file's version is older than this version! The lang file could not be compiled right", DATA_ID);
					else
						setErrno(InterpretingError.LANG_VER_ERROR, "Lang file's version is newer than this version! The lang file will not be compiled right!", DATA_ID);
				}
				break;
			
			case "lang.name":
				//Nothing to do
				break;
			
			//Flags
			case "lang.allowTermRedirect":
				Number number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.allowTermRedirect flag!", DATA_ID);
					
					return;
				}
				executionFlags.allowTermRedirect = number.intValue() != 0;
				break;
			case "lang.errorOutput":
				number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.errorOutput flag!", DATA_ID);
					
					return;
				}
				executionFlags.errorOutput = ExecutionFlags.ErrorOutputFlag.getErrorFlagFor(number.intValue());
				break;
			case "lang.langTest":
				number = value.toNumber();
				if(number == null) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "Invalid Data Type for the lang.langTest flag!", DATA_ID);
					
					return;
				}
				
				boolean langTestNewValue = number.intValue() != 0;
				if(executionFlags.langTest && !langTestNewValue) {
					setErrno(InterpretingError.INVALID_ARGUMENTS, "The lang.langTest flag can not be changed if it was once set to true!", DATA_ID);
					
					return;
				}
				
				executionFlags.langTest = langTestNewValue;
				break;
			default:
				setErrno(InterpretingError.INVALID_EXEC_FLAG_DATA, "\"" + langDataExecutionFlag + "\" is neither lang data nor an execution flag", DATA_ID);
		}
	}
	private DataObject interpretAssignmentNode(AssignmentNode node, final int DATA_ID) {
		DataObject rvalue = interpretNode(node.getRvalue(), DATA_ID);
		if(rvalue == null)
			rvalue = new DataObject(); //Set rvalue to null data object
		
		Node lvalueNode = node.getLvalue();
		if(lvalueNode == null)
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Assignment without lvalue", DATA_ID);
		
		try {
			switch(lvalueNode.getNodeType()) {
				//Variable assignment
				case UNPROCESSED_VARIABLE_NAME:
					UnprocessedVariableNameNode variableNameNode = (UnprocessedVariableNameNode)lvalueNode;
					String variableName = variableNameNode.getVariableName();
					if(LangPatterns.matches(variableName, LangPatterns.VAR_NAME_FULL) || LangPatterns.matches(variableName, LangPatterns.VAR_NAME_PTR_AND_DEREFERENCE)) {
						int indexOpeningBracket = variableName.indexOf("[");
						int indexMatchingBracket = indexOpeningBracket == -1?-1:LangUtils.getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
						if(indexOpeningBracket == -1 || indexMatchingBracket == variableName.length() - 1) {
							boolean[] flags = new boolean[] {false, false};
							DataObject lvalue = getOrCreateDataObjectFromVariableName(variableName, false, true, true, flags, DATA_ID);
							if(flags[0])
								return lvalue; //Forward error from getOrCreateDataObjectFromVariableName()
							
							variableName = lvalue.getVariableName();
							if(variableName == null) {
								return setErrnoErrorObject(InterpretingError.INVALID_ASSIGNMENT, "Anonymous values can not be changed", DATA_ID);
							}
							
							if(lvalue.isFinalData() || lvalue.isLangVar()) {
								if(flags[1])
									data.get(DATA_ID).var.remove(variableName);
								
								return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
							}
							
							try {
								lvalue.setData(rvalue);
							}catch(DataTypeConstraintViolatedException e) {
								if(flags[1])
									data.get(DATA_ID).var.remove(variableName);
								
								return setErrnoErrorObject(InterpretingError.INCOMPATIBLE_DATA_TYPE, "Incompatible type for rvalue in assignment", DATA_ID);
							}
							
							if(variableName.startsWith("fp.")) {
								final String functionNameCopy = variableName.substring(3);
								Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
									return functionNameCopy.equals(entry.getKey());
								}).findFirst();
								
								if(ret.isPresent())
									setErrno(InterpretingError.VAR_SHADOWING_WARNING, "\"" + variableName + "\" shadows a predfined, linker, or external function", DATA_ID);
							}
							break;
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
				case TRY_STATEMENT_PART_CATCH:
				case TRY_STATEMENT_PART_ELSE:
				case TRY_STATEMENT_PART_FINALLY:
				case MATH:
				case OPERATION:
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
					DataObject translationKeyDataObject = interpretNode(lvalueNode, DATA_ID);
					if(translationKeyDataObject == null)
						return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid translationKey", DATA_ID);
					
					String translationKey = translationKeyDataObject.getText();
					if(translationKey.startsWith("lang."))
						interpretLangDataAndExecutionFlags(translationKey, rvalue, DATA_ID);
					
					data.get(DATA_ID).lang.put(translationKey, rvalue.getText());
					break;
					
				case GENERAL:
				case ARGUMENT_SEPARATOR:
					return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Neither lvalue nor translationKey", DATA_ID);
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
	 * @param flags Will set by this method in format: [error, created]
	 */
	private DataObject getOrCreateDataObjectFromVariableName(String variableName, boolean supportsPointerReferencing,
	boolean supportsPointerDereferencing, boolean shouldCreateDataObject, final boolean[] flags, final int DATA_ID) {
		DataObject ret = data.get(DATA_ID).var.get(variableName);
		if(ret != null)
			return ret;
		
		if(supportsPointerDereferencing && variableName.contains("*")) {
			int index = variableName.indexOf('*');
			String referencedVariableName = variableName.substring(0, index) + variableName.substring(index + 1);
			DataObject referencedVariable = getOrCreateDataObjectFromVariableName(referencedVariableName, supportsPointerReferencing, true, false, flags, DATA_ID);
			if(referencedVariable == null) {
				if(flags != null && flags.length == 2)
					flags[0] = true;
				return setErrnoErrorObject(InterpretingError.INVALID_PTR, DATA_ID);
			}
			
			if(referencedVariable.getType() == DataType.VAR_POINTER)
				return referencedVariable.getVarPointer().getVar();
			
			return new DataObject(); //If no var pointer was dereferenced, return null data object
		}
		
		if(supportsPointerReferencing && variableName.contains("[") && variableName.contains("]")) { //Check dereferenced variable name
			int indexOpeningBracket = variableName.indexOf("[");
			int indexMatchingBracket = LangUtils.getIndexOfMatchingBracket(variableName, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
			if(indexMatchingBracket != variableName.length() - 1) {
				if(flags != null && flags.length == 2)
					flags[0] = true;
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Non matching dereferencing brackets", DATA_ID);
			}
			
			String dereferencedVariableName = variableName.substring(0, indexOpeningBracket) + variableName.substring(indexOpeningBracket + 1, indexMatchingBracket);
			DataObject dereferencedVariable = getOrCreateDataObjectFromVariableName(dereferencedVariableName, true, false, false, flags, DATA_ID);
			if(dereferencedVariable != null)
				return new DataObject().setVarPointer(new VarPointerObject(dereferencedVariable));
			
			//VarPointer redirection (e.g.: create "$[...]" as variable) -> at method end
		}
		
		if(!shouldCreateDataObject)
			return null;
		
		//Variable creation if possible
		if(LangPatterns.matches(variableName, LangPatterns.LANG_VAR)) {
			if(flags != null && flags.length == 2)
				flags[0] = true;
			return setErrnoErrorObject(InterpretingError.FINAL_VAR_CHANGE, DATA_ID);
		}
		
		if(flags != null && flags.length == 2)
			flags[1] = true;
		
		DataObject dataObject = new DataObject().setVariableName(variableName);
		data.get(DATA_ID).var.put(variableName, dataObject);
		return dataObject;
	}
	/**
	 * Will create a variable if doesn't exist or returns an error object
	 */
	private DataObject interpretVariableNameNode(VariableNameNode node, final int DATA_ID) {
		String variableName = node.getVariableName();
		
		if(!LangPatterns.matches(variableName, LangPatterns.VAR_NAME_FULL_WITH_FUNCS) && !LangPatterns.matches(variableName, LangPatterns.VAR_NAME_PTR_AND_DEREFERENCE))
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", DATA_ID);
		
		if(variableName.startsWith("$") || variableName.startsWith("&") || variableName.startsWith("fp."))
			return getOrCreateDataObjectFromVariableName(variableName, variableName.startsWith("$"), variableName.startsWith("$"),
			true, null, DATA_ID);
		
		final boolean isLinkerFunction;
		if(variableName.startsWith("func.")) {
			isLinkerFunction = false;
			
			variableName = variableName.substring(5);
		}else if(variableName.startsWith("linker.")) {
			isLinkerFunction = true;
			
			variableName = variableName.substring(7);
		}else {
			return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid variable name", DATA_ID);
		}
		
		final String variableNameCopy = variableName;
		Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
			return entry.getValue().isLinkerFunction() == isLinkerFunction;
		}).filter(entry -> {
			return variableNameCopy.equals(entry.getKey());
		}).findFirst();
		
		if(!ret.isPresent())
			return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + variableName + "\" was not found", DATA_ID);
		
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
			case '&':
			case '#':
			case ',':
			case '.':
			case '(':
			case ')':
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
				setErrno(InterpretingError.UNDEF_ESCAPE_SEQUENCE, "\"\\" + node.getEscapeSequenceChar() + "\" was used", DATA_ID);
				
				return new DataObject().setChar(node.getEscapeSequenceChar());
		}
	}
	
	private DataObject interpretArgumentSeparatotNode(ArgumentSeparatorNode node, final int DATA_ID) {
		return new DataObject().setArgumentSeparator(node.getOriginalText());
	}
	
	DataObject getAndResetReturnValue(final int DATA_ID) {
		DataObject retTmp = executionState.returnedOrThrownValue;
		executionState.returnedOrThrownValue = null;
		
		if(executionState.isThrownValue && DATA_ID > -1)
			setErrno(retTmp.getError().getInterprettingError(), retTmp.getError().getMessage(), DATA_ID);
		
		if(executionFlags.langTest && DATA_ID == langTestExpectedReturnValueDataID) {
			if(langTestExpectedThrowValue != null) {
				InterpretingError gotError = executionState.isThrownValue?retTmp.getError().getInterprettingError():null;
				langTestStore.addAssertResult(new LangTest.AssertResultThrow(gotError == langTestExpectedThrowValue, langTestMessageForLastTestResult, gotError, langTestExpectedThrowValue));
				
				langTestExpectedThrowValue = null;
			}
			
			if(langTestExpectedReturnValue != null) {
				langTestStore.addAssertResult(new LangTest.AssertResultReturn(!executionState.isThrownValue && langTestExpectedReturnValue.isStrictEquals(retTmp), langTestMessageForLastTestResult, retTmp,
						langTestExpectedReturnValue));
				
				langTestExpectedReturnValue = null;
			}
			
			if(langTestExpectedNoReturnValue) {
				langTestStore.addAssertResult(new LangTest.AssertResultNoReturn(retTmp == null, langTestMessageForLastTestResult, retTmp));
				
				langTestExpectedNoReturnValue = false;
			}
			langTestMessageForLastTestResult = null;
			langTestExpectedReturnValueDataID = 0;
		}
		
		executionState.isThrownValue = false;
		
		if(executionState.tryThrownError == null || executionState.tryBlockLevel == 0)
			executionState.stopExecutionFlag = false;
		
		return retTmp;
	}
	void executeAndClearCopyAfterFP(final int DATA_ID_TO, final int DATA_ID_FROM) {
		//Add copyValue after call
		copyAfterFP.get(DATA_ID_FROM).forEach((to, from) -> {
			if(from != null && to != null) {
				DataObject valFrom = data.get(DATA_ID_FROM).var.get(from);
				if(valFrom != null) {
					if(to.startsWith("fp.") || to.startsWith("$") || to.startsWith("&")) {
						DataObject dataTo = data.get(DATA_ID_TO).var.get(to);
						//LANG and final vars can't be change
						if(LangPatterns.matches(to, LangPatterns.LANG_VAR) || (dataTo != null && (dataTo.isFinalData() || dataTo.isLangVar()))) {
							setErrno(InterpretingError.FINAL_VAR_CHANGE, "during copy after FP execution", DATA_ID_TO);
							return;
						}
						
						if(!LangPatterns.matches(to, LangPatterns.VAR_NAME) && !LangPatterns.matches(to, LangPatterns.VAR_NAME_PTR)) {
							setErrno(InterpretingError.INVALID_PTR, "during copy after FP execution", DATA_ID_TO);
							return;
						}
						int indexOpeningBracket = to.indexOf("[");
						int indexMatchingBracket = indexOpeningBracket == -1?-1:LangUtils.getIndexOfMatchingBracket(to, indexOpeningBracket, Integer.MAX_VALUE, '[', ']');
						if(indexOpeningBracket != -1 && indexMatchingBracket != to.length() - 1) {
							setErrno(InterpretingError.INVALID_PTR, "Non matching dereferencing prackets", DATA_ID_TO);
							return;
						}
						
						boolean[] flags = new boolean[] {false, false};
						DataObject dataObject = getOrCreateDataObjectFromVariableName(to, false, false, true, flags, DATA_ID_TO);
						try {
							dataObject.setData(valFrom);
						}catch(DataTypeConstraintViolatedException e) {
							if(flags[1])
								data.get(DATA_ID_TO).var.remove(to);
							
							setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "during copy after FP execution", DATA_ID_TO);
							return;
						}
						
						return;
					}
				}
			}
			
			setErrno(InterpretingError.INVALID_ARGUMENTS, "for copy after FP", DATA_ID_TO);
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
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", DATA_ID);
				
				//Update call stack
				StackElement currentStackElement = getCurrentCallStackElement();
				pushStackElement(new StackElement(currentStackElement.getLangPath(), currentStackElement.getLangFile(), functionName == null?fp.toString():functionName));
				
				final int NEW_DATA_ID = DATA_ID + 1;
				
				//Add variables and local variables
				createDataMap(NEW_DATA_ID);
				//Copies must not be final
				data.get(DATA_ID).var.forEach((key, val) -> {
					if(!val.isLangVar())
						data.get(NEW_DATA_ID).var.put(key, new DataObject(val).setVariableName(val.getVariableName()));
					
					if(val.isStaticData()) //Static lang vars should also be copied
						data.get(NEW_DATA_ID).var.put(key, val);
				});
				//Initialize copyAfterFP
				copyAfterFP.put(NEW_DATA_ID, new HashMap<String, String>());
				
				//Set arguments
				DataObject lastDataObject = null;
				Iterator<VariableNameNode> parameterListIterator = parameterList.iterator();
				while(parameterListIterator.hasNext()) {
					VariableNameNode parameter = parameterListIterator.next();
					String variableName = parameter.getVariableName();
					if(!parameterListIterator.hasNext() && !LangPatterns.matches(variableName, LangPatterns.LANG_VAR) &&
					LangPatterns.matches(variableName, LangPatterns.FUNC_CALL_VAR_ARGS)) {
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
					
					if(LangPatterns.matches(variableName, LangPatterns.FUNC_CALL_CALL_BY_PTR) && !LangPatterns.matches(variableName,
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
					
					if(!LangPatterns.matches(variableName, LangPatterns.VAR_NAME) || LangPatterns.matches(variableName, LangPatterns.LANG_VAR)) {
						setErrno(InterpretingError.INVALID_AST_NODE, "Invalid parameter variable name", DATA_ID);
						
						continue;
					}
					
					if(argumentValueList.size() > 0)
						lastDataObject = LangUtils.getNextArgumentAndRemoveUsedDataObjects(argumentValueList, true);
					else if(lastDataObject == null)
						lastDataObject = new DataObject().setVoid();
					
					try {
						data.get(NEW_DATA_ID).var.put(variableName, new DataObject(lastDataObject).setVariableName(variableName));
					}catch(DataTypeConstraintViolatedException e) {
						setErrno(InterpretingError.INCOMPATIBLE_DATA_TYPE, "Invalid argument value for parameter variable", DATA_ID);
						
						continue;
					}
				}
				
				//Call function
				interpretAST(functionBody, NEW_DATA_ID);
				
				//Add lang after call
				data.get(DATA_ID).lang.putAll(data.get(NEW_DATA_ID).lang);
				
				executeAndClearCopyAfterFP(DATA_ID, NEW_DATA_ID);
				
				//Remove data map
				data.remove(NEW_DATA_ID);
				
				//Update call stack
				popStackElement();
				
				DataObject retTmp = getAndResetReturnValue(DATA_ID);
				return retTmp == null?new DataObject().setVoid():retTmp;
			
			case FunctionPointerObject.PREDEFINED:
				LangPredefinedFunctionObject function = fp.getPredefinedFunction();
				if(function == null)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", DATA_ID);
				
				DataObject ret = function.callFunc(argumentValueList, DATA_ID);
				if(function.isDeprecated()) {
					String message = String.format("Use of deprecated function \"%s\", this function woll no longer be supported in \"%s\"!%s", functionName,
					function.getDeprecatedRemoveVersion() == null?"the future":function.getDeprecatedRemoveVersion(),
					function.getDeprecatedReplacementFunction() == null?"":("\nUse \"" + function.getDeprecatedReplacementFunction() + "\" instead!"));
					setErrno(InterpretingError.DEPRECATED_FUNC_CALL, message, DATA_ID);
				}
				return ret == null?new DataObject().setVoid():ret;
			
			case FunctionPointerObject.EXTERNAL:
				LangExternalFunctionObject externalFunction = fp.getExternalFunction();
				if(externalFunction == null)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP", DATA_ID);
				ret = externalFunction.callFunc(argumentValueList, DATA_ID);
				return ret == null?new DataObject().setVoid():ret;
			
			default:
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "Function call of invalid FP type", DATA_ID);
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
			
			//Array unpacking
			if(argument.getNodeType() == NodeType.UNPROCESSED_VARIABLE_NAME) {
				try {
					String variableName = ((UnprocessedVariableNameNode)argument).getVariableName();
					if(variableName.startsWith("&") && variableName.endsWith("...")) {
						DataObject dataObject = getOrCreateDataObjectFromVariableName(variableName.substring(0, variableName.length() - 3), false, false, false, null, DATA_ID);
						if(dataObject == null) {
							argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, "Array unpacking of undefined variable", DATA_ID));
							
							continue;
						}
						
						if(dataObject.getType() != DataType.ARRAY) {
							argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_ARR_PTR, "Array unpacking of non ARRAY type variable", DATA_ID));
							
							continue;
						}
						
						DataObject[] arr = dataObject.getArray();
						for(int i = 0;i < arr.length;i++) {
							DataObject ele = arr[i];
							
							argumentValueList.add(ele);
							if(i != arr.length - 1)
								argumentValueList.add(new DataObject().setArgumentSeparator(", "));
						}
						
						continue;
					}
				}catch(ClassCastException e) {
					argumentValueList.add(setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, DATA_ID));
				}
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
		if(LangPatterns.matches(functionName, LangPatterns.FUNC_NAME)) {
			final boolean isLinkerFunction;
			if(functionName.startsWith("func.")) {
				isLinkerFunction = false;
				
				functionName = functionName.substring(5);
			}else if(functionName.startsWith("linker.")) {
				isLinkerFunction = true;
				
				functionName = functionName.substring(7);
			}else {
				return setErrnoErrorObject(InterpretingError.INVALID_AST_NODE, "Invalid predfined, linker, or external function name", DATA_ID);
			}
			
			final String functionNameCopy = functionName;
			Optional<Map.Entry<String, LangPredefinedFunctionObject>> ret = funcs.entrySet().stream().filter(entry -> {
				return entry.getValue().isLinkerFunction() == isLinkerFunction && functionNameCopy.equals(entry.getKey());
			}).findFirst();
			
			if(!ret.isPresent())
				return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + node.getFunctionName() + "\": Predfined, linker, or external function was not found", DATA_ID);
			
			fp = new FunctionPointerObject(ret.get().getValue());
		}else if(LangPatterns.matches(functionName, LangPatterns.VAR_NAME_FUNC_PTR)) {
			DataObject ret = data.get(DATA_ID).var.get(functionName);
			if(ret == null || ret.getType() != DataType.FUNCTION_POINTER)
				return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"" + node.getFunctionName() + "\": Function pointer was not found or is invalid", DATA_ID);
			
			fp = ret.getFunctionPointer();
		}else {
			//Function call without prefix
			
			//Function pointer
			DataObject ret = data.get(DATA_ID).var.get("fp." + functionName);
			if(ret != null) {
				if(ret.getType() != DataType.FUNCTION_POINTER)
					return setErrnoErrorObject(InterpretingError.INVALID_FUNC_PTR, "\"" + node.getFunctionName() + "\": Function pointer is invalid", DATA_ID);
					
				fp = ret.getFunctionPointer();
			}else {
				//Predefined/External function
				
				final String functionNameCopy = functionName;
				Optional<Map.Entry<String, LangPredefinedFunctionObject>> retPredefinedFunction = funcs.entrySet().stream().filter(entry -> {
					return !entry.getValue().isLinkerFunction() && functionNameCopy.equals(entry.getKey());
				}).findFirst();
				
				if(retPredefinedFunction.isPresent()) {
					fp = new FunctionPointerObject(retPredefinedFunction.get().getValue());;
				}else {
					//Predefined linker function
					retPredefinedFunction = funcs.entrySet().stream().filter(entry -> {
						return entry.getValue().isLinkerFunction() && functionNameCopy.equals(entry.getKey());
					}).findFirst();
					
					if(!retPredefinedFunction.isPresent())
						return setErrnoErrorObject(InterpretingError.FUNCTION_NOT_FOUND, "\"" + node.getFunctionName() + "\": Normal, predfined, linker, or external function was not found", DATA_ID);
					
					fp = new FunctionPointerObject(retPredefinedFunction.get().getValue());
				}
			}
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
					setErrno(InterpretingError.INVALID_AST_NODE, "Invalid AST node type for parameter", DATA_ID);
					
					continue;
				}
				
				VariableNameNode parameter = (VariableNameNode)child;
				String variableName = parameter.getVariableName();
				if(!childrenIterator.hasNext() && !LangPatterns.matches(variableName, LangPatterns.LANG_VAR) && LangPatterns.matches(variableName, LangPatterns.FUNC_CALL_VAR_ARGS)) {
					//Varargs (only the last parameter can be a varargs parameter)
					parameterList.add(parameter);
					break;
				}
				
				if((!LangPatterns.matches(variableName, LangPatterns.VAR_NAME) && !LangPatterns.matches(variableName, LangPatterns.FUNC_CALL_CALL_BY_PTR)) ||
				LangPatterns.matches(variableName, LangPatterns.LANG_VAR)) {
					setErrno(InterpretingError.INVALID_AST_NODE, "Invalid parameter: \"" + variableName + "\"", DATA_ID);
					
					continue;
				}
				parameterList.add(parameter);
			}catch(ClassCastException e) {
				setErrno(InterpretingError.INVALID_AST_NODE, DATA_ID);
			}
		}
		
		return new DataObject().setFunctionPointer(new FunctionPointerObject(parameterList, node.getFunctionBody()));
	}
	
	private DataObject interpretArrayNode(ArrayNode node, final int DATA_ID) {
		List<DataObject> interpretedNodes = new LinkedList<>();
		List<DataObject> elements = new LinkedList<>();
		
		for(Node element:node.getChildren()) {
			DataObject argumentValue = interpretNode(element, DATA_ID);
			if(argumentValue == null)
				continue;
			interpretedNodes.add(argumentValue);
		}
		while(!interpretedNodes.isEmpty())
			elements.add(new DataObject(LangUtils.getNextArgumentAndRemoveUsedDataObjects(interpretedNodes, true)));
		
		return new DataObject().setArray(elements.toArray(new DataObject[0]));
	}
	
	void createDataMap(final int DATA_ID) {
		createDataMap(DATA_ID, null);
	}
	void createDataMap(final int DATA_ID, String[] langArgs) {
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
		
		StackElement currentStackElement = getCurrentCallStackElement();
		
		//Final vars
		data.get(DATA_ID).var.put("$LANG_VERSION", new DataObject(VERSION, true).setLangVar().setVariableName("$LANG_VERSION"));
		data.get(DATA_ID).var.put("$LANG_NAME", new DataObject("Standard LANG", true).setLangVar().setVariableName("$LANG_NAME"));
		data.get(DATA_ID).var.put("$LANG_PATH", new DataObject(currentStackElement.getLangPath(), true).setLangVar().setVariableName("$LANG_PATH"));
		data.get(DATA_ID).var.put("$LANG_FILE", new DataObject(currentStackElement.getLangFile(), true).setLangVar().setVariableName("$LANG_FILE"));
		data.get(DATA_ID).var.put("$LANG_CURRENT_FUNCTION", new DataObject(currentStackElement.getLangFunctionName(), true).setLangVar().setVariableName("$LANG_CURRENT_FUNCTION"));
		data.get(DATA_ID).var.put("$LANG_RAND_MAX", new DataObject().setInt(Integer.MAX_VALUE).setFinalData(true).setLangVar().setVariableName("$LANG_RAND_MAX"));
		data.get(DATA_ID).var.put("$LANG_OS_NAME", new DataObject(System.getProperty("os.name")).setFinalData(true).setLangVar().setVariableName("$LANG_OS_NAME"));
		data.get(DATA_ID).var.put("$LANG_OS_VER", new DataObject(System.getProperty("os.version")).setFinalData(true).setLangVar().setVariableName("$LANG_OS_VER"));
		data.get(DATA_ID).var.put("$LANG_OS_ARCH", new DataObject(System.getProperty("os.arch")).setFinalData(true).setLangVar().setVariableName("$LANG_OS_ARCH"));
		data.get(DATA_ID).var.put("$LANG_OS_FILE_SEPARATOR", new DataObject(System.getProperty("file.separator")).setFinalData(true).setLangVar().setVariableName("$LANG_OS_FILE_SEPARATOR"));
		data.get(DATA_ID).var.put("$LANG_OS_LINE_SEPARATOR", new DataObject(System.getProperty("line.separator")).setFinalData(true).setLangVar().setVariableName("$LANG_OS_LINE_SEPARATOR"));
		data.get(DATA_ID).var.put("$LANG_FLOAT_NAN", new DataObject().setFloat(Float.NaN).setFinalData(true).setLangVar().setVariableName("$LANG_FLOAT_NAN"));
		data.get(DATA_ID).var.put("$LANG_FLOAT_POS_INF", new DataObject().setFloat(Float.POSITIVE_INFINITY).setFinalData(true).setLangVar().setVariableName("$LANG_FLOAT_POS_INF"));
		data.get(DATA_ID).var.put("$LANG_FLOAT_NEG_INF", new DataObject().setFloat(Float.NEGATIVE_INFINITY).setFinalData(true).setLangVar().setVariableName("$LANG_FLOAT_NEG_INF"));
		data.get(DATA_ID).var.put("$LANG_DOUBLE_NAN", new DataObject().setDouble(Double.NaN).setFinalData(true).setLangVar().setVariableName("$LANG_DOUBLE_NAN"));
		data.get(DATA_ID).var.put("$LANG_DOUBLE_POS_INF", new DataObject().setDouble(Double.POSITIVE_INFINITY).setFinalData(true).setLangVar().setVariableName("$LANG_DOUBLE_POS_INF"));
		data.get(DATA_ID).var.put("$LANG_DOUBLE_NEG_INF", new DataObject().setDouble(Double.NEGATIVE_INFINITY).setFinalData(true).setLangVar().setVariableName("$LANG_DOUBLE_NEG_INF"));
		data.get(DATA_ID).var.put("$LANG_MATH_PI", new DataObject().setDouble(Math.PI).setFinalData(true).setLangVar().setVariableName("$LANG_MATH_PI"));
		data.get(DATA_ID).var.put("$LANG_MATH_E", new DataObject().setDouble(Math.E).setFinalData(true).setLangVar().setVariableName("$LANG_MATH_E"));
		data.get(DATA_ID).var.put("&LANG_ARGS", langArgs == null?new DataObject().setArray(new DataObject[0]).setFinalData(true).setLangVar().setVariableName("&LANG_ARGS"):langArgs);
		
		for(InterpretingError error:InterpretingError.values()) {
			String upperCaseErrorName = error.name().toUpperCase();
			String variableName = "$LANG_ERROR_" + upperCaseErrorName;
			data.get(DATA_ID).var.put(variableName, new DataObject().setError(new ErrorObject(error)).setFinalData(true).setLangVar().setVariableName(variableName));
			variableName = "$LANG_ERRNO_" + upperCaseErrorName;
			data.get(DATA_ID).var.put(variableName, new DataObject().setInt(error.getErrorCode()).setFinalData(true).setLangVar().setVariableName(variableName));
		}
		
		for(DataType type:DataType.values()) {
			String upperCaseTypeName = type.name().toUpperCase();
			String variableName = "$LANG_TYPE_" + upperCaseTypeName;
			data.get(DATA_ID).var.put(variableName, new DataObject().setTypeValue(type).setFinalData(true).setLangVar().setVariableName(variableName));
		}
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Set $LANG_ERRNO
	}
	void resetVars(final int DATA_ID) {
		Set<Map.Entry<String, DataObject>> entrySet = new HashSet<>(data.get(DATA_ID).var.entrySet());
		entrySet.forEach(entry -> {
			String key = entry.getKey();
			if(!entry.getValue().isLangVar() && key.startsWith("$") && key.startsWith("&"))
				data.get(DATA_ID).var.remove(key);
		});
		
		//Not final vars
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Set $LANG_ERRNO
	}
	
	void setErrno(InterpretingError error, final int DATA_ID) {
		setErrno(error, null, DATA_ID);
	}
	void setErrno(InterpretingError error, String message, final int DATA_ID) {
		setErrno(error, message, false, DATA_ID);
	}
	private void setErrno(InterpretingError error, String message, boolean forceNoErrorOutput, final int DATA_ID) {
		data.get(DATA_ID).var.computeIfAbsent("$LANG_ERRNO", key -> new DataObject().setStaticData(true).setLangVar().setVariableName("$LANG_ERRNO"));
		
		int currentErrno = data.get(DATA_ID).var.get("$LANG_ERRNO").getInt();
		int newErrno = error.getErrorCode();
		
		if(newErrno >= 0 || currentErrno < 1)
			data.get(DATA_ID).var.get("$LANG_ERRNO").setInt(newErrno);
		
		if(!forceNoErrorOutput && executionFlags.errorOutput.shouldPrint(newErrno)) {
			if(message == null)
				message = "";
			
			StackElement currentStackElement = getCurrentCallStackElement();
			String langPath = currentStackElement.getLangPath();
			String langFile = currentStackElement.getLangFile();
			String langFunctionName = currentStackElement.getLangFunctionName();
			
			String output = String.format("A%s %s occured in \"%s/%s\" (FUNCTION: \"%s\", DATA_ID: \"%d\")!\n%s: %s (%d)%s\nStack trace:\n%s", newErrno < 0?"":"n",
					newErrno < 0?"warning":"error", langPath, langFile == null?"<shell>":langFile, langFunctionName == null?"main":langFunctionName, DATA_ID, newErrno < 0?"Warning":"Error",
					error.getErrorText(), error.getErrorCode(), message.isEmpty()?"":"\nMessage: " + message, printStackTrace());
			if(term == null)
				System.err.println(output);
			else
				term.logln(newErrno < 0?Level.WARNING:Level.ERROR, output, LangInterpreter.class);
		}
		
		if(newErrno > 0 && executionState.tryBlockLevel > 0) {
			executionState.tryThrownError = error;
			executionState.stopExecutionFlag = true;
		}
	}
	
	DataObject setErrnoErrorObject(InterpretingError error, final int DATA_ID) {
		return setErrnoErrorObject(error, null, DATA_ID);
	}
	DataObject setErrnoErrorObject(InterpretingError error, String message, final int DATA_ID) {
		return setErrnoErrorObject(error, message, false, DATA_ID);
	}
	private DataObject setErrnoErrorObject(InterpretingError error, String message, boolean forceNoErrorOutput, final int DATA_ID) {
		setErrno(error, message, forceNoErrorOutput, DATA_ID);
		
		return new DataObject().setError(new ErrorObject(error, message));
	}
	InterpretingError getAndClearErrnoErrorObject(final int DATA_ID) {
		int errno = data.get(DATA_ID).var.get("$LANG_ERRNO").getInt();
		
		setErrno(InterpretingError.NO_ERROR, DATA_ID); //Reset errno
		
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
		private final String langFunctionName;
		
		public StackElement(String langPath, String langFile, String langFunctionName) {
			this.langPath = langPath;
			this.langFile = langFile;
			this.langFunctionName = langFunctionName;
		}
		
		public String getLangPath() {
			return langPath;
		}
		
		public String getLangFile() {
			return langFile;
		}
		
		public String getLangFunctionName() {
			return langFunctionName;
		}
		
		@Override
		public String toString() {
			return String.format("    at \"%s/%s\" in function \"%s\"", langPath, langFile == null?"<shell>":langFile, langFunctionName == null?"main":langFunctionName);
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
		ErrorOutputFlag errorOutput = ErrorOutputFlag.ALL;
		/**
		 * Will enable langTest unit tests (Can not be disabled if enabled once)
		 */
		boolean langTest = false;
		
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
	}
	
	public static enum InterpretingError {
		NO_ERROR              ( 0, "No Error"),
		
		//ERRORS
		FINAL_VAR_CHANGE      ( 1, "LANG or final vars must not be changed"),
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
		TRANS_KEY_NOT_FOUND   (26, "Translation key does not exist"),
		FUNCTION_NOT_SUPPORTED(27, "Function not supported"),
		BRACKET_MISMATCH      (28, "Bracket mismatch"),
		CONT_FLOW_ARG_MISSING (29, "Control flow statement condition(s) or argument(s) is/are missing"),
		INVALID_AST_NODE      (30, "Invalid AST node or AST node order"),
		INVALID_PTR           (31, "Invalid pointer"),
		INCOMPATIBLE_DATA_TYPE(32, "Incompatible data type"),
		LANG_ARRAYS_COPY      (33, "&LANG arrays can not be copied"),
		LANG_VER_ERROR        (34, "Lang file's version is not compatible with this version"),
		INVALID_CON_PART      (35, "Invalid statement in control flow statement"),
		INVALID_FORMAT        (36, "Invalid format sequence"),
		INVALID_ASSIGNMENT    (37, "Invalid assignment"),
		
		//WARNINGS
		DEPRECATED_FUNC_CALL  (-1, "A deprecated predefined function was called"),
		NO_TERMINAL_WARNING   (-2, "No terminal available"),
		LANG_VER_WARNING      (-3, "Lang file's version is not compatible with this version"),
		INVALID_EXEC_FLAG_DATA(-4, "Execution flag or lang data is invalid"),
		VAR_SHADOWING_WARNING (-5, "Variable name shadows an other value"),
		UNDEF_ESCAPE_SEQUENCE (-6, "An undefined escape sequence was used");
		
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
		
		public void setErrno(InterpretingError error, final int DATA_ID) {
			setErrno(error, "", DATA_ID);
		}
		public void setErrno(InterpretingError error, String message, final int DATA_ID) {
			interpreter.setErrno(error, message, DATA_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, final int DATA_ID) {
			return setErrnoErrorObject(error, "", DATA_ID);
		}
		public DataObject setErrnoErrorObject(InterpretingError error, String message, final int DATA_ID) {
			return interpreter.setErrnoErrorObject(error, message, DATA_ID);
		}
		public InterpretingError getAndClearErrnoErrorObject(final int DATA_ID) {
			return interpreter.getAndClearErrnoErrorObject(DATA_ID);
		}
		
		/**
		 * Creates an function which is accessible globally in the Interpreter (= in all DATA_IDs)<br>
		 * If function already exists, it will be overridden<br>
		 * Function can be accessed with "func.[funcName]" or with "liner.[funcName]" and can't be removed nor changed by the lang file
		 */
		public void addPredefinedFunction(String funcName, LangPredefinedFunctionObject function) {
			interpreter.funcs.put(funcName, function);
		}
		public Map<String, LangPredefinedFunctionObject> getPredefinedFunctions() {
			return interpreter.funcs;
		}
		
		public void exec(final int DATA_ID, BufferedReader lines) throws IOException, StoppedException {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretLines(lines, DATA_ID);
		}
		public void exec(final int DATA_ID, String lines) throws IOException, StoppedException {
			try(BufferedReader reader = new BufferedReader(new StringReader(lines))) {
				exec(DATA_ID, reader);
			}
		}
		/**
		 * Can be called in another thread<br>
		 * Any execution method previously called which are still running or any future call of execution methods if the stop flag is set will throw a
		 * {@link me.jddev0.module.lang.LangInterpreter.StoppedException StoppedException} exception
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
		
		public StackElement getCurrentCallStackElement() {
			return interpreter.getCurrentCallStackElement();
		}
		
		/**
		 * Must be called before {@link LangInterpreter.LangInterpreterInterface#getAndResetReturnValue() getAndResetReturnValue()} method
		 */
		public boolean isReturnedValueThrowValue() {
			return interpreter.executionState.isThrownValue;
		}
		public DataObject getAndResetReturnValue() {
			return interpreter.getAndResetReturnValue(-1);
		}
		
		public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
			return interpreter.parseLines(lines);
		}
		
		public void interpretAST(final int DATA_ID, AbstractSyntaxTree ast) throws StoppedException {
			getAndResetReturnValue(); //Reset returned value else the interpreter would stop immediately
			interpreter.interpretAST(ast, DATA_ID);
		}
		public DataObject inerpretNode(final int DATA_ID, Node node) throws StoppedException {
			return interpreter.interpretNode(node, DATA_ID);
		}
		public DataObject interpretFunctionCallNode(final int DATA_ID, FunctionCallNode node) throws StoppedException {
			return interpreter.interpretFunctionCallNode(node, DATA_ID);
		}
		public DataObject interpretFunctionPointer(FunctionPointerObject fp, String functionName, List<Node> argumentList, final int DATA_ID) throws StoppedException {
			return interpreter.interpretFunctionPointer(fp, functionName, argumentList, DATA_ID);
		}
		
		public DataObject callFunctionPointer(FunctionPointerObject fp, String functionName, List<DataObject> argumentValueList, final int DATA_ID) throws StoppedException {
			return interpreter.callFunctionPointer(fp, functionName, argumentValueList, DATA_ID);
		}
	}
	
	public static class StoppedException extends RuntimeException {
		private static final long serialVersionUID = 3184689513001702458L;
		
		public StoppedException() {
			super("The execution was stopped!");
		}
	}
}