package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lang-Module<br>
 * Parsing of Lang files into an AST structure for LangInterpreter
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangParser {
	private String currentLine;
	private int lineNumber;
	
	public LangParser() {
		resetPositionVars();
	}
	
	public void resetPositionVars() {
		currentLine = null;
		lineNumber = 0;
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
	
	public void setLineNumber(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	
	public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
		return parseLines(null, false, lines);
	}
	
	public AbstractSyntaxTree parseLines(String firstLine, boolean endBlockBeforeSecondLineOfCurrentBlock, BufferedReader lines) throws IOException {
		if(firstLine == null && (lines == null || !lines.ready()))
			return null;
		
		boolean hasFirstLine = firstLine != null;
		
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		int blockPos = 0;
		
		do {
			if(firstLine == null && hasFirstLine && endBlockBeforeSecondLineOfCurrentBlock) {
				blockPos--;
				
				if(blockPos < 0)
					break;
			}
			
			String line = firstLine == null?nextLine(lines):firstLine;
			firstLine = null;
			if(line == null) {
				currentLine = null;
				
				break;
			}
			
			List<AbstractSyntaxTree.Node> errorNodes = new LinkedList<>();
			line = currentLine = prepareNextLine(line, lines, errorNodes);
			if(errorNodes.size() > 0) {
				errorNodes.forEach(ast::addChild);
				return ast;
			}
			
			if(line != null) {
				//Blocks
				if(line.equals("{")) {
					blockPos++;
					
					continue;
				}else if(line.startsWith("}")) { //"startsWith" is needed for parsing of control flow statements
					blockPos--;
					
					if(blockPos < 0)
						break;
					continue;
				}
				
				//Assignments
				if(!line.startsWith("return ") && !line.startsWith("throw ")) {
					AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(line, lines, false);
					if(returnedNode != null) {
						ast.addChild(returnedNode);
						continue;
					}
				}
				
				//Non assignments
				AbstractSyntaxTree returnedAst = parseLine(line, lines);
				if(returnedAst == null) //End of if
					return ast;
				
				ast.addChild(returnedAst.convertToNode());
			}
		}while(lines != null && lines.ready());
		
		return ast;
	}
	
	private AbstractSyntaxTree.OperationNode parseCondition(String condition) throws IOException {
		return parseOperationExpr(condition, AbstractSyntaxTree.OperationNode.OperatorType.CONDITION);
	}
	
	private AbstractSyntaxTree.OperationNode parseMathExpr(String mathExpr) throws IOException {
		return parseOperationExpr(mathExpr, AbstractSyntaxTree.OperationNode.OperatorType.MATH);
	}
	
	private AbstractSyntaxTree.OperationNode parseOperationExpr(String token) throws IOException {
		return parseOperationExpr(token, AbstractSyntaxTree.OperationNode.OperatorType.GENERAL);
	}
	private AbstractSyntaxTree.OperationNode parseOperationExpr(String token, AbstractSyntaxTree.OperationNode.OperatorType type) throws IOException {
		return parseOperationExpr(token, null, null, 0, type);
	}
	private AbstractSyntaxTree.OperationNode parseOperationExpr(String token, StringBuilder tokensLeft, StringBuilder tokensLeftBehindMiddlePartEnd, int currentOperatorPrecedence,
	AbstractSyntaxTree.OperationNode.OperatorType type) throws IOException {
		if(token == null)
			return null;
		
		final AbstractSyntaxTree.OperationNode.Operator nonOperator;
		switch(type) {
			case MATH:
				nonOperator = AbstractSyntaxTree.OperationNode.Operator.MATH_NON;
				break;
			case CONDITION:
				nonOperator = AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON;
				break;
			case GENERAL:
				nonOperator = AbstractSyntaxTree.OperationNode.Operator.NON;
				break;
			
			default:
				return null;
		}
		
		token = token.trim();
		
		AbstractSyntaxTree.OperationNode.Operator operator = null;
		List<AbstractSyntaxTree.Node> leftNodes = new ArrayList<>();
		AbstractSyntaxTree.Node middleNode = null;
		AbstractSyntaxTree.Node rightNode = null;
		
		StringBuilder whitespaces = new StringBuilder();
		
		StringBuilder builder = new StringBuilder();
		while(token.length() > 0) {
			//Ignore whitespaces between operators
			if(LangPatterns.matches(token, LangPatterns.PARSING_LEADING_WHITSPACE)) {
				if(token.length() == 1)
					break;
				
				whitespaces.append(token.charAt(0));
				token = token.substring(1);
				
				continue;
			}
			
			//Unescaping
			if(token.startsWith("\\")) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				if(token.length() == 1)
					break;
				
				leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(lineNumber, token.charAt(1)));
				
				if(token.length() == 2)
					break;
				
				token = token.substring(2);
				
				continue;
			}
			
			//Parse module variable prefix
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_MODULE_VAR_IDENTIFIER)) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				int startIndexVariable = token.indexOf(':') + 2;
				builder.append(token.subSequence(0, startIndexVariable));
				token = token.substring(startIndexVariable);
				
				continue;
			}
			
			//Var pointer referencing and dereferencing
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_VAR_NAME_PTR_OR_DEREFERENCE)) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				builder.append('$');
				token = token.substring(1);
				
				while(token.charAt(0) == '*') {
					builder.append('*');
					token = token.substring(1);
				}
				
				int openingBracketCount = 0;
				while(token.charAt(0) == '[') {
					builder.append('[');
					token = token.substring(1);
					
					openingBracketCount++;
				}
				if(openingBracketCount > 0) {
					builder.append(token.substring(0, openingBracketCount));
					token = token.substring(openingBracketCount);
					
					int closingBracketCount = 0;
					while(token.length() > 0 && token.charAt(0) == ']' && closingBracketCount < openingBracketCount) {
						builder.append(']');
						token = token.substring(1);
						
						closingBracketCount++;
					}
				}
				
				continue;
			}
			
			//Grouping
			if(token.startsWith("(")) {
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '(', ')');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket in operator expression is missing"));
					
					break;
				}
				
				//Parse "()" as function call previous value if something was before
				if(builder.length() == 0 && leftNodes.isEmpty()) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					leftNodes.add(parseOperationExpr(token.substring(1, endIndex), type));
					token = token.substring(endIndex + 1);
					
					continue;
				}else {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					int lineNumberFrom = lineNumber;
					
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex == -1) {
						leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
						
						break;
					}
					
					String functionCall = token.substring(0, parameterEndIndex + 1);
					token = token.substring(parameterEndIndex + 1);
					
					String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
					
					leftNodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode("", "", convertCommaOperatorsToArgumentSeparators(
							parseOperationExpr(functionParameterList, type)), lineNumberFrom, lineNumber));
					
					continue;
				}
			}else if(token.startsWith("[") || token.startsWith("?.[")) {
				boolean startsWithOptionalMarker = token.startsWith("?.");
				
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, startsWithOptionalMarker?2:0, Integer.MAX_VALUE, '[', ']');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket in operator expression is missing"));
					
					break;
				}
				//Binary operator if something was before else unary operator
				if(AbstractSyntaxTree.OperationNode.OperatorType.ALL.isCompatibleWith(type) && (builder.length() > 0 || leftNodes.size() > 0)) {
					AbstractSyntaxTree.OperationNode.Operator oldOperator = operator;
					
					if(startsWithOptionalMarker)
						operator = AbstractSyntaxTree.OperationNode.Operator.OPTIONAL_GET_ITEM;
					else
						operator = AbstractSyntaxTree.OperationNode.Operator.GET_ITEM;
					
					if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
						tokensLeft.append(token.trim());
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						operator = oldOperator;
						
						break;
					}
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					AbstractSyntaxTree.OperationNode node = parseOperationExpr(
							token.substring(startsWithOptionalMarker?3:1, endIndex), type);
					token = token.substring(endIndex + 1);
					if(token.isEmpty()) {
						//Add node directly if node has NON operator
						if(node.getOperator() == nonOperator)
							rightNode = node.getLeftSideOperand();
						else
							rightNode = node;
						
						break;
					}else {
						AbstractSyntaxTree.Node innerRightNode;
						
						//Add node directly if node has NON operator
						if(node.getOperator() == nonOperator)
							innerRightNode = node.getLeftSideOperand();
						else
							innerRightNode = node;
						
						AbstractSyntaxTree.Node leftNode;
						if(leftNodes.size() == 1)
							leftNode = leftNodes.get(0);
						else
							leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
						
						leftNodes.clear();
						leftNodes.add(new AbstractSyntaxTree.OperationNode(leftNode, innerRightNode, operator, type));
						operator = null;
						continue;
					}
				}else if(AbstractSyntaxTree.OperationNode.OperatorType.ALL.isCompatibleWith(type) && !startsWithOptionalMarker) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					//Array creation
					leftNodes.add(new AbstractSyntaxTree.ArrayNode(convertCommaOperatorsToArgumentSeparators(
							parseOperationExpr(token.substring(1, endIndex), type))));
					token = token.substring(endIndex + 1);
					
					if(token.isEmpty()) {
						operator = null;
						
						break;
					}
					
					continue;
				}else {
					//Incompatible type
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}else if(token.startsWith("**")) {
				AbstractSyntaxTree.OperationNode.Operator oldOperator = operator;
				
				operator = AbstractSyntaxTree.OperationNode.Operator.POW;
				
				//If something is before operator and operator type is compatible with type
				if(operator.getOperatorType().isCompatibleWith(type) && (builder.length() > 0 || leftNodes.size() >= 0)) {
					if(tokensLeft != null && currentOperatorPrecedence < operator.getPrecedence()) { //No "<=" because it should be parsed right-to-left
						tokensLeft.append(token.trim());
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						operator = oldOperator;
						
						break;
					}
					
					//Add as value if nothing is behind operator
					if(token.length() == 2) {
						if(whitespaces.length() > 0) {
							builder.append(whitespaces.toString());
							whitespaces.delete(0, whitespaces.length());
						}
						
						operator = null;
						builder.append(token);
						
						break;
					}
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.OperationNode node = parseOperationExpr(token.substring(2), innerTokensLeft, tokensLeftBehindMiddlePartEnd, operator.getPrecedence(), type);
					if(node == null) //End was reached inside middle part of a ternary operator
						return null;
					
					token = innerTokensLeft.toString();
					
					if(token.isEmpty()) {
						//Add node directly if node has NON operator
						if(node.getOperator() == nonOperator)
							rightNode = node.getLeftSideOperand();
						else
							rightNode = node;
						
						break;
					}else {
						AbstractSyntaxTree.Node innerRightNode;
						
						//Add node directly if node has NON operator
						if(node.getOperator() == nonOperator)
							innerRightNode = node.getLeftSideOperand();
						else
							innerRightNode = node;
						
						AbstractSyntaxTree.Node leftNode;
						if(leftNodes.size() == 1)
							leftNode = leftNodes.get(0);
						else
							leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
						
						leftNodes.clear();
						leftNodes.add(new AbstractSyntaxTree.OperationNode(leftNode, innerRightNode, operator, type));
						operator = null;
						continue;
					}
				}else {
					operator = oldOperator;
					
					//Ignore operator: nothing was before for binary operator or operator type is not compatible with type
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}else if(token.startsWith("!==") || token.startsWith("!=~") || token.startsWith("!=") || token.startsWith("===") || token.startsWith("=~") || token.startsWith("==") ||
			token.startsWith("<=") || token.startsWith(">=") || token.startsWith("<") || token.startsWith(">") || token.startsWith("|||") || token.startsWith("&&") || token.startsWith("||") ||
			token.startsWith("!") || token.startsWith("&") || token.startsWith("~~") || token.startsWith("~/") || token.startsWith("~") || token.startsWith("▲") || token.startsWith("▼") ||
			token.startsWith("*") || token.startsWith("//") || token.startsWith("^/") || token.startsWith("/") || token.startsWith("%") || token.startsWith("^") || token.startsWith("|") ||
			token.startsWith("<<") || token.startsWith(">>>") || token.startsWith(">>") || token.startsWith("+|") || token.startsWith("-|") || token.startsWith("+") || token.startsWith("-") ||
			token.startsWith("@") || token.startsWith("?:") || token.startsWith("??") || token.startsWith(",") || token.startsWith("?::") || token.startsWith("::")) {
				boolean somethingBeforeOperator = builder.length() > 0 || leftNodes.size() > 0;
				
				AbstractSyntaxTree.OperationNode.Operator oldOperator = operator;
				int operatorLength = 1;
				if(token.startsWith("!==") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.STRICT_NOT_EQUALS;
				}else if(token.startsWith("!=~") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.NOT_MATCHES;
				}else if(token.startsWith("!=") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.NOT_EQUALS;
				}else if(token.startsWith("===") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.STRICT_EQUALS;
				}else if(token.startsWith("=~") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.MATCHES;
				}else if(token.startsWith("==") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.EQUALS;
				}else if(token.startsWith("?::") && AbstractSyntaxTree.OperationNode.OperatorType.ALL.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.OPTIONAL_MEMBER_ACCESS;
				}else if(token.startsWith("::") && AbstractSyntaxTree.OperationNode.OperatorType.ALL.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.MEMBER_ACCESS;
				}else if(token.startsWith("->") && AbstractSyntaxTree.OperationNode.OperatorType.ALL.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.MEMBER_ACCESS_POINTER;
				}else if(token.startsWith("<<") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.LSHIFT;
				}else if(token.startsWith(">>>") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.RZSHIFT;
				}else if(token.startsWith(">>") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.RSHIFT;
				}else if(token.startsWith("<=>") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.SPACESHIP;
				}else if(token.startsWith("<=") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.LESS_THAN_OR_EQUALS;
				}else if(token.startsWith(">=") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.GREATER_THAN_OR_EQUALS;
				}else if(token.startsWith("<") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.LESS_THAN;
				}else if(token.startsWith(">") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.GREATER_THAN;
				}else if(token.startsWith("|||") && AbstractSyntaxTree.OperationNode.OperatorType.GENERAL.isCompatibleWith(type)) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.OperationNode.Operator.CONCAT;
				}else if(token.startsWith("&&") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.AND;
				}else if(token.startsWith("||") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.OR;
				}else if(token.startsWith("!") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.NOT;
				}else if(token.startsWith("&") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type) &&
						(!LangPatterns.matches(builder.toString(), LangPatterns.PARSING_ENDS_WITH_MODULE_VAR_IDENTIFIER) || whitespaces.length() > 0)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_AND;
				}else if(token.startsWith("~~") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.INSTANCE_OF;
				}else if(token.startsWith("~/") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.TRUNC_DIV;
				}else if(token.startsWith("~") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_NOT;
				}else if(token.startsWith("▲") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.INC;
				}else if(token.startsWith("▼") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.DEC;
				}else if(token.startsWith("+|") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.INC;
				}else if(token.startsWith("-|") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.DEC;
				}else if(token.startsWith("*") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.MUL;
				}else if(token.startsWith("^/") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.CEIL_DIV;
				}else if(token.startsWith("//") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.FLOOR_DIV;
				}else if(token.startsWith("/") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.DIV;
				}else if(token.startsWith("%") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.MOD;
				}else if(token.startsWith("^")) {
					if(somethingBeforeOperator && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type))
						operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_XOR;
					else if(!somethingBeforeOperator && AbstractSyntaxTree.OperationNode.OperatorType.GENERAL.isCompatibleWith(type))
						operator = AbstractSyntaxTree.OperationNode.Operator.DEEP_COPY;
				}else if(token.startsWith("|") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_OR;
				}else if(token.startsWith("+") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = somethingBeforeOperator?AbstractSyntaxTree.OperationNode.Operator.ADD:AbstractSyntaxTree.OperationNode.Operator.POS;
				}else if(token.startsWith("-") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = somethingBeforeOperator?AbstractSyntaxTree.OperationNode.Operator.SUB:AbstractSyntaxTree.OperationNode.Operator.INV;
				}else if(token.startsWith("@") && AbstractSyntaxTree.OperationNode.OperatorType.GENERAL.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.LEN;
				}else if(token.startsWith("?:") && AbstractSyntaxTree.OperationNode.OperatorType.GENERAL.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.ELVIS;
				}else if(token.startsWith("??") && AbstractSyntaxTree.OperationNode.OperatorType.GENERAL.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.NULL_COALESCING;
				}else if(token.startsWith(",") && AbstractSyntaxTree.OperationNode.OperatorType.ALL.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.COMMA;
				}else {
					operator = null;
				}
				
				if(operator != null && operator.isBinary() && somethingBeforeOperator) {
					//Binary
					
					if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
						tokensLeft.append(token.trim());
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						operator = oldOperator;
						
						break;
					}
					
					//Add as value if nothing is behind "operator"
					if(token.length() == operatorLength) {
						if(whitespaces.length() > 0) {
							builder.append(whitespaces.toString());
							whitespaces.delete(0, whitespaces.length());
						}
						
						operator = null;
						builder.append(token);
						
						break;
					}
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.OperationNode node = parseOperationExpr(token.substring(operatorLength), innerTokensLeft, tokensLeftBehindMiddlePartEnd, operator.getPrecedence(), type);
					if(node == null) //End was reached inside middle part of a ternary operator
						return null;
					
					token = innerTokensLeft.toString();
					
					if(token.isEmpty()) {
						//Add node directly if node has NON operator
						if(node.getOperator() == nonOperator)
							rightNode = node.getLeftSideOperand();
						else
							rightNode = node;
						
						break;
					}else {
						AbstractSyntaxTree.Node innerRightNode;
						
						//Add node directly if node has NON operator
						if(node.getOperator() == nonOperator)
							innerRightNode = node.getLeftSideOperand();
						else
							innerRightNode = node;
						
						AbstractSyntaxTree.Node leftNode;
						if(leftNodes.size() == 1)
							leftNode = leftNodes.get(0);
						else
							leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
						
						leftNodes.clear();
						leftNodes.add(new AbstractSyntaxTree.OperationNode(leftNode, innerRightNode, operator, type));
						operator = null;
						continue;
					}
				}else if(operator != null && operator.isUnary() && !somethingBeforeOperator) {
					//Unary
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.OperationNode node = parseOperationExpr(token.substring(operatorLength), innerTokensLeft, tokensLeftBehindMiddlePartEnd, operator.getPrecedence(), type);
					if(node == null) //End was reached inside middle part of a ternary operator
						return null;
					
					token = innerTokensLeft.toString();
					
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == nonOperator)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					leftNodes.add(new AbstractSyntaxTree.OperationNode(innerRightNode, operator, type));
					operator = null;
					
					if(token.isEmpty())
						break;
					else
						continue;
				}else {
					operator = oldOperator;
					
					//Ignore operator: something was before for unary operator or nothing was before for binary operator or operator type is not compatible with type
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}else if(token.startsWith("?")) {
				AbstractSyntaxTree.OperationNode.Operator oldOperator = operator;
				
				operator = AbstractSyntaxTree.OperationNode.Operator.INLINE_IF;
				
				//Inline if -> Only parse if something is before and ":" was found -> else "?" will be parsed as text
				
				if(operator.getOperatorType().isCompatibleWith(type) && (builder.length() > 0 || leftNodes.size() > 0)) {
					if(tokensLeft != null && currentOperatorPrecedence < operator.getPrecedence()) { //No "<=" because it should be parsed right-to-left
						tokensLeft.append(token.trim());
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						operator = oldOperator;
						
						break;
					}
					
					//Parse middle part
					StringBuilder innerTokensLeftBehindMiddlePartEnd = new StringBuilder();
					AbstractSyntaxTree.OperationNode innerMiddleNodeRet = parseOperationExpr(token.substring(1), null, innerTokensLeftBehindMiddlePartEnd, 0, type);
					if(innerMiddleNodeRet != null) {
						//Only parse as operator if matching ":" was found
						
						String tokensAfterMiddlePartEnd = innerTokensLeftBehindMiddlePartEnd.toString();
						
						//Add as value if nothing is behind "operator"
						if(tokensAfterMiddlePartEnd.isEmpty()) {
							if(whitespaces.length() > 0) {
								builder.append(whitespaces.toString());
								whitespaces.delete(0, whitespaces.length());
							}
							
							operator = null;
							builder.append(token);
							
							break;
						}
						
						token = tokensAfterMiddlePartEnd;
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						if(builder.length() > 0) {
							leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
							builder.delete(0, builder.length());
						}
						
						StringBuilder innerTokensLeft = new StringBuilder();
						AbstractSyntaxTree.OperationNode innerRightNodeRet = parseOperationExpr(token, innerTokensLeft, tokensLeftBehindMiddlePartEnd, operator.getPrecedence(), type);
						token = innerTokensLeft.toString();
						
						if(token.isEmpty()) {
							//Add middle node directly if node has NON operator
							if(innerMiddleNodeRet.getOperator() == nonOperator)
								middleNode = innerMiddleNodeRet.getLeftSideOperand();
							else
								middleNode = innerMiddleNodeRet;
							
							//Add right node directly if node has NON operator
							if(innerRightNodeRet.getOperator() == nonOperator)
								rightNode = innerRightNodeRet.getLeftSideOperand();
							else
								rightNode = innerRightNodeRet;
							
							break;
						}else {
							AbstractSyntaxTree.Node innerMiddleNode;
							AbstractSyntaxTree.Node innerRightNode;
							
							//Add middle node directly if node has NON operator
							if(innerMiddleNodeRet.getOperator() == nonOperator)
								innerMiddleNode = innerMiddleNodeRet.getLeftSideOperand();
							else
								innerMiddleNode = innerMiddleNodeRet;
							
							//Add node directly if node has NON operator
							if(innerRightNodeRet.getOperator() == nonOperator)
								innerRightNode = innerRightNodeRet.getLeftSideOperand();
							else
								innerRightNode = innerRightNodeRet;
							
							AbstractSyntaxTree.Node leftNode;
							if(leftNodes.size() == 1)
								leftNode = leftNodes.get(0);
							else
								leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
							
							leftNodes.clear();
							leftNodes.add(new AbstractSyntaxTree.OperationNode(leftNode, innerMiddleNode, innerRightNode, operator, type));
							operator = null;
							continue;
						}
					}else {
						operator = oldOperator;
						
						//Ignore operator: nothing was before for ternary operator or operator type is not compatible with type
						if(whitespaces.length() > 0) {
							builder.append(whitespaces.toString());
							whitespaces.delete(0, whitespaces.length());
						}
					}
				}else {
					operator = oldOperator;
					
					//Ignore operator: nothing was before for ternary operator or operator type is not compatible with type
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}else if(tokensLeftBehindMiddlePartEnd != null && token.startsWith(":")) {
				//End of inline if
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				tokensLeftBehindMiddlePartEnd.append(token.substring(1).trim());
				
				//Reset (Simulated end)
				if(tokensLeft != null && tokensLeft.length() > 0)
					tokensLeft.delete(0, tokensLeft.length());
				
				break;
			}
			
			//Function calls
			if(!LangPatterns.matches(builder.toString(), LangPatterns.VAR_NAME_PREFIX_ARRAY_AND_NORMAL_WITH_PTR_AND_DEREFERENCE_WITH_OPTIONAL_NAME) &&
					(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL) ||
							LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL_WITHOUT_PREFIX))) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				int lineNumberFrom = lineNumber;
				
				String modulePrefix = "";
				if(LangPatterns.matches(builder.toString(), LangPatterns.PARSING_ENDS_WITH_MODULE_VAR_IDENTIFIER)) {
					String builderStr = builder.toString();
					int lastIndex = builderStr.lastIndexOf("[[");
					modulePrefix = builderStr.substring(lastIndex);
					
					builder.delete(builder.length() - modulePrefix.length(), builder.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
					
					break;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				token = token.substring(parameterEndIndex + 1);
				
				String functionName = modulePrefix + functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				leftNodes.add(new AbstractSyntaxTree.FunctionCallNode(convertCommaOperatorsToArgumentSeparators(
						parseOperationExpr(functionParameterList, type)), lineNumberFrom, lineNumber, functionName));
				continue;
			}
			
			//Parser function calls
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_PARSER_FUNCTION_CALL)) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
					break;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				
				String functionName = functionCall.substring(token.indexOf('.') + 1, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				AbstractSyntaxTree.Node ret = parseParserFunction(functionName, functionParameterList);
				if(ret != null) {
					token = token.substring(parameterEndIndex + 1);
					
					leftNodes.add(ret);
					
					continue;
				}
				
				//Invalid parser function
			}
			
			if(whitespaces.length() > 0) {
				builder.append(whitespaces.toString());
				whitespaces.delete(0, whitespaces.length());
			}
			
			char c = token.charAt(0);
			builder.append(c);
			if(token.length() == 1)
				break;
			
			token = token.substring(1);
		}
		
		if(tokensLeftBehindMiddlePartEnd != null && tokensLeftBehindMiddlePartEnd.length() == 0) //end of middle part was not found for ternary operator -> ignore ternary operator
			return null;
		
		if(whitespaces.length() > 0) {
			builder.append(whitespaces.toString());
			whitespaces.delete(0, whitespaces.length());
		}
		
		//Parse value
		if(builder.length() > 0) {
			leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
		}
		
		if(operator == null)
			operator = nonOperator;
		
		AbstractSyntaxTree.Node leftNode;
		if(leftNodes.size() == 1)
			leftNode = leftNodes.get(0);
		else
			leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
		
		if(operator.isUnary())
			return new AbstractSyntaxTree.OperationNode(leftNode, operator, type);
		if(operator.isBinary())
			return new AbstractSyntaxTree.OperationNode(leftNode, rightNode, operator, type);
		if(operator.isTernary())
			return new AbstractSyntaxTree.OperationNode(leftNode, middleNode, rightNode, operator, type);
		
		return null;
	}

	private List<AbstractSyntaxTree.Node> convertCommaOperatorsToArgumentSeparators(AbstractSyntaxTree.OperationNode operatorNode) {
		List<AbstractSyntaxTree.Node> nodes = new LinkedList<>();
		
		//Only parse COMMA operators and COMMA operators inside COMMA operators but only if they are the left node
		if(operatorNode.getOperator() == AbstractSyntaxTree.OperationNode.Operator.COMMA) {
			AbstractSyntaxTree.Node leftSideOperand = operatorNode.getLeftSideOperand();
			
			//Add left side operand
			if(leftSideOperand instanceof AbstractSyntaxTree.OperationNode)
				nodes.addAll(convertCommaOperatorsToArgumentSeparators((AbstractSyntaxTree.OperationNode)leftSideOperand));
			else
				nodes.add(leftSideOperand);
			
			//Add argument separator
			nodes.add(new AbstractSyntaxTree.ArgumentSeparatorNode(lineNumber, ","));
			
			//Add right side operand
			nodes.add(operatorNode.getRightSideOperand());
		}else {
			nodes.add(operatorNode);
		}
		
		return nodes;
	}
	
	private AbstractSyntaxTree.AssignmentNode parseAssignment(String line, BufferedReader lines, boolean isInnerAssignment) throws IOException {
		if(LangPatterns.matches(line, LangPatterns.PARSING_PARSER_FLAG)) {
			String[] tokens = line.split("=", 2);
			
			parseParserFlags(tokens[0].trim(), tokens[1].trim());
		}
		
		if(LangPatterns.matches(line, LangPatterns.PARSING_SIMPLE_TRANSLATION)) {
			String[] tokens = line.split("=", 2);
			
			//The translation value for empty simple translation will be set to empty text ""
			return new AbstractSyntaxTree.AssignmentNode(new AbstractSyntaxTree.TextValueNode(lineNumber, tokens[0]), parseSimpleAssignmentValue(tokens[1]).convertToNode());
		}else if(LangPatterns.matches(line, LangPatterns.PARSING_SIMPLE_ASSIGNMENT)) {
			String[] tokens = line.split("=", 2);
			
			//The assignment value for empty simple assignments will be set to empty text ""
			return new AbstractSyntaxTree.AssignmentNode(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, tokens[0]),
					parseSimpleAssignmentValue(tokens[1]).convertToNode());
		}
		
		boolean isVariableAssignment = LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT_VAR_NAME);
		boolean isNonLvalueOperationAssignment = LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT_VAR_NAME_OR_TRANSLATION);
		if(isInnerAssignment?isVariableAssignment:(isNonLvalueOperationAssignment ||
				LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT_OPERATION_WITH_OPERATOR))) {
			String[] tokens = LangPatterns.PARSING_ASSIGNMENT_OPERATOR.split(line, 2);
			
			//If lvalue contains "(", "[", or "{" which are not closed -> do not parse as assignment
			String[] bracketPairs = new String[] {
					"()", "[]", "{}"
			};
			for(String bracketPair:bracketPairs) {
				if(tokens[0].contains(bracketPair.charAt(0) + "") &&
						LangUtils.getIndexOfMatchingBracket(tokens[0], 0, tokens[0].length(), bracketPair.charAt(0), bracketPair.charAt(1)) == -1) {
					return null;
				}
			}
			
			String assignmentOperator = line.substring(tokens[0].length() + 1, line.indexOf('=', tokens[0].length()));
			
			AbstractSyntaxTree.OperationNode.Operator operator = null;
			if(!assignmentOperator.isEmpty()) {
				switch(assignmentOperator) {
					case "**":
						operator = AbstractSyntaxTree.OperationNode.Operator.POW;
						break;
					case "*":
						operator = AbstractSyntaxTree.OperationNode.Operator.MUL;
						break;
					case "/":
						operator = AbstractSyntaxTree.OperationNode.Operator.DIV;
						break;
					case "~/":
						operator = AbstractSyntaxTree.OperationNode.Operator.TRUNC_DIV;
						break;
					case "//":
						operator = AbstractSyntaxTree.OperationNode.Operator.FLOOR_DIV;
						break;
					case "^/":
						operator = AbstractSyntaxTree.OperationNode.Operator.CEIL_DIV;
						break;
					case "%":
						operator = AbstractSyntaxTree.OperationNode.Operator.MOD;
						break;
					case "+":
						operator = AbstractSyntaxTree.OperationNode.Operator.ADD;
						break;
					case "-":
						operator = AbstractSyntaxTree.OperationNode.Operator.SUB;
						break;
					case "<<":
						operator = AbstractSyntaxTree.OperationNode.Operator.LSHIFT;
						break;
					case ">>":
						operator = AbstractSyntaxTree.OperationNode.Operator.RSHIFT;
						break;
					case ">>>":
						operator = AbstractSyntaxTree.OperationNode.Operator.RZSHIFT;
						break;
					case "&":
						operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_AND;
						break;
					case "^":
						operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_XOR;
						break;
					case "|":
						operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_OR;
						break;
					case "|||":
						operator = AbstractSyntaxTree.OperationNode.Operator.CONCAT;
						break;
					case "?:":
						operator = AbstractSyntaxTree.OperationNode.Operator.ELVIS;
						break;
					case "??":
						operator = AbstractSyntaxTree.OperationNode.Operator.NULL_COALESCING;
						break;
					case "?":
						operator = AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON;
						break;
					case ":":
						operator = AbstractSyntaxTree.OperationNode.Operator.MATH_NON;
						break;
					case "$":
						operator = AbstractSyntaxTree.OperationNode.Operator.NON;
						break;
				}
			}
			
			AbstractSyntaxTree.Node lvalueNode;
			if(isNonLvalueOperationAssignment) {
				lvalueNode = ((isVariableAssignment || !assignmentOperator.isEmpty())?
						parseLRvalue(tokens[0], null, false):parseTranslationKey(tokens[0])).convertToNode();
			}else {
				if(operator == AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON)
					lvalueNode = parseCondition(tokens[0]);
				else if(operator == AbstractSyntaxTree.OperationNode.Operator.MATH_NON)
					lvalueNode = parseMathExpr(tokens[0]);
				else
					lvalueNode = parseOperationExpr(tokens[0]);
			}
			
			AbstractSyntaxTree.Node rvalueNode;
			
			if(assignmentOperator.isEmpty() || assignmentOperator.equals("::")) {
				AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(tokens[1], lines, true);
				rvalueNode = returnedNode != null?returnedNode:parseLRvalue(tokens[1], lines, true).convertToNode();
			}else {
				if(operator == null)
					rvalueNode = new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_ASSIGNMENT);
				else if(operator == AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON)
					rvalueNode = parseCondition(tokens[1]);
				else if(operator == AbstractSyntaxTree.OperationNode.Operator.MATH_NON)
					rvalueNode = parseMathExpr(tokens[1]);
				else if(operator == AbstractSyntaxTree.OperationNode.Operator.NON)
					rvalueNode = parseOperationExpr(tokens[1]);
				else
					rvalueNode = new AbstractSyntaxTree.OperationNode(lvalueNode, parseOperationExpr(tokens[1]), operator, operator.getOperatorType());
			}
			
			return new AbstractSyntaxTree.AssignmentNode(lvalueNode, rvalueNode);
		}
		if(isInnerAssignment)
			return null;
		
		//Only for non multi assignments
		isVariableAssignment = LangPatterns.matches(line, LangPatterns.VAR_NAME_FULL) || LangPatterns.matches(line, LangPatterns.VAR_NAME_PTR_AND_DEREFERENCE);
		if(line.endsWith(" =") || isVariableAssignment) {
			//Empty translation/assignment ("<var/translation key> =" or "$varName")
			if(line.endsWith(" ="))
				line = line.substring(0, line.length() - 2);
			return new AbstractSyntaxTree.AssignmentNode((isVariableAssignment?parseLRvalue(line, null, false):parseTranslationKey(line)).convertToNode(),
					new AbstractSyntaxTree.NullValueNode(lineNumber));
		}
		
		return null;
	}
	
	private AbstractSyntaxTree parseLine(String token, BufferedReader lines) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		//Control flow statements
		final String originalToken = token;
		if(token.startsWith("con.") || token.endsWith("{")) {
			if(token.endsWith("{") && !token.startsWith("con.")) //"con." is optional if the curly brackets syntax is used
				token = "con." + token;
			
			if(!token.endsWith("{") && (token.startsWith("con.continue") || token.startsWith("con.break"))) {
				List<AbstractSyntaxTree.Node> argumentNodes;
				if(!token.contains("(") && !token.contains(")")) {
					argumentNodes = null;
				}else {
					int argumentsStartIndex = token.indexOf('(');
					int argumentsEndIndex = LangUtils.getIndexOfMatchingBracket(token, argumentsStartIndex, Integer.MAX_VALUE, '(', ')');
					if(argumentsEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket for con.break or con.continue is missing"));
						return ast;
					}
					argumentNodes = parseFunctionParameterList(token.substring(argumentsStartIndex + 1, argumentsEndIndex), false).getChildren();
				}
				
				AbstractSyntaxTree.Node numberNode = argumentNodes == null?null:(argumentNodes.size() == 1?argumentNodes.get(0):new AbstractSyntaxTree.ListNode(argumentNodes));
				ast.addChild(new AbstractSyntaxTree.LoopStatementContinueBreakStatement(numberNode, lineNumber, token.startsWith("con.continue")));
				return ast;
			}else if(token.startsWith("con.try") || token.startsWith("con.softtry") || token.startsWith("con.nontry")) {
				int tryStatementLineNumberFrom = lineNumber;
				List<AbstractSyntaxTree.TryStatementPartNode> tryStatmentParts = new ArrayList<>();
				
				boolean blockBracketFlag = token.endsWith("{");
				boolean firstStatement = true;
				
				String tryStatement = token;
				do {
					if(blockBracketFlag) {
						//Remove "{" and "}" for the curly brackets if statement syntax
						if(firstStatement) {
							if(!tryStatement.endsWith("{"))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							
							tryStatement = tryStatement.substring(0, tryStatement.length() - 1).trim();
						}else if(!tryStatement.equals("}")) {
							if(!tryStatement.startsWith("}") || !tryStatement.endsWith("{"))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							
							tryStatement = tryStatement.substring(1, tryStatement.length() - 1).trim();
							
							if(!tryStatement.startsWith("con.")) //"con." is optional if "{" syntax is used
								tryStatement = "con." + tryStatement;
						}
					}
					
					int tryArgumentsLineNumberFrom = lineNumber;
					String tryArguments;
					if(tryStatement.startsWith("con.try") || tryStatement.startsWith("con.softtry") || tryStatement.startsWith("con.nontry") || tryStatement.startsWith("con.else") ||
					tryStatement.startsWith("con.finally")) {
						if(!tryStatement.equals("con.try") && !tryStatement.startsWith("con.softtry") && !tryStatement.startsWith("con.nontry") && !tryStatement.equals("con.else") &&
						!tryStatement.equals("con.finally")) {
							if(tryStatement.startsWith("con.try(") || tryStatement.startsWith("con.softtry(") || tryStatement.startsWith("con.nontry(") || tryStatement.startsWith("con.else(") ||
							tryStatement.startsWith("con.finally("))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Try/Softtry/Nontry/Finally/Else part with arguments"));
							else
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							return ast;
						}
						
						tryArguments = null;
					}else if(tryStatement.startsWith("con.catch")) {
						if(tryStatement.equals("con.catch")) {
							tryArguments = null;
						}else {
							int argumentsStartIndex = tryStatement.indexOf('(');
							int argumentsEndIndex = LangUtils.getIndexOfMatchingBracket(tryStatement, argumentsStartIndex, Integer.MAX_VALUE, '(', ')');
							if(argumentsEndIndex == -1) {
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Missing catch statement arguments"));
								return ast;
							}
							if(argumentsEndIndex != tryStatement.length() - 1) {
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Trailing stuff behind arguments"));
								return ast;
							}
							tryArguments = tryStatement.substring(argumentsStartIndex + 1, argumentsEndIndex);
						}
					}else {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Try statement part is invalid"));
						return ast;
					}
					
					AbstractSyntaxTree tryBody = parseLines(lines);
					if(tryBody == null) {
						nodes.add(new AbstractSyntaxTree.TryStatementNode(tryStatmentParts, tryStatementLineNumberFrom, lineNumber));
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "In try body"));
						
						return ast;
					}
					
					if(tryStatement.startsWith("con.try")) {
						tryStatmentParts.add(new AbstractSyntaxTree.TryStatementPartTryNode(tryBody, tryArgumentsLineNumberFrom, lineNumber));
					}else if(tryStatement.startsWith("con.softtry")) {
						tryStatmentParts.add(new AbstractSyntaxTree.TryStatementPartSoftTryNode(tryBody, tryArgumentsLineNumberFrom, lineNumber));
					}else if(tryStatement.startsWith("con.nontry")) {
						tryStatmentParts.add(new AbstractSyntaxTree.TryStatementPartNonTryNode(tryBody, tryArgumentsLineNumberFrom, lineNumber));
					}else if(tryStatement.startsWith("con.catch")) {
						int originalLineNumber = lineNumber;
						lineNumber = tryArgumentsLineNumberFrom;
						tryStatmentParts.add(new AbstractSyntaxTree.TryStatementPartCatchNode(tryBody, tryArgumentsLineNumberFrom, originalLineNumber,
								tryArguments == null?null:parseFunctionParameterList(tryArguments, false).getChildren()));
						lineNumber = originalLineNumber;
					}else if(tryStatement.startsWith("con.else")) {
						tryStatmentParts.add(new AbstractSyntaxTree.TryStatementPartElseNode(tryBody, tryArgumentsLineNumberFrom, lineNumber));
					}else if(tryStatement.startsWith("con.finally")) {
						tryStatmentParts.add(new AbstractSyntaxTree.TryStatementPartFinallyNode(tryBody, tryArgumentsLineNumberFrom, lineNumber));
					}
					
					firstStatement = false;
					tryStatement = currentLine;
				}while(tryStatement != null && !(blockBracketFlag?tryStatement.equals("}"):tryStatement.equals("con.endtry")));
				
				nodes.add(new AbstractSyntaxTree.TryStatementNode(tryStatmentParts, tryStatementLineNumberFrom, lineNumber));
				return ast;
			}else if(token.startsWith("con.loop") || token.startsWith("con.while") || token.startsWith("con.until") ||
					token.startsWith("con.repeat") || token.startsWith("con.foreach")) {
				int loopStatementLineNumberFrom = lineNumber;
				List<AbstractSyntaxTree.LoopStatementPartNode> loopStatmentParts = new ArrayList<>();
				
				boolean blockBracketFlag = token.endsWith("{");
				boolean firstStatement = true;
				
				String loopStatement = token;
				do {
					if(blockBracketFlag) {
						//Remove "{" and "}" for the curly brackets loop statement syntax
						if(firstStatement) {
							if(!loopStatement.endsWith("{"))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							
							loopStatement = loopStatement.substring(0, loopStatement.length() - 1).trim();
						}else if(!loopStatement.equals("}")) {
							if(!loopStatement.startsWith("}") || !loopStatement.endsWith("{"))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							
							loopStatement = loopStatement.substring(1, loopStatement.length() - 1).trim();
							
							if(!loopStatement.startsWith("con.")) //"con." is optional if "{" syntax is used
								loopStatement = "con." + loopStatement;
						}
					}
					
					int loopConditionLineNumberFrom = lineNumber;
					String loopCondition = null;
					if(loopStatement.startsWith("con.else") || loopStatement.startsWith("con.loop")) {
						if(!loopStatement.equals("con.else") && !loopStatement.equals("con.loop")) {
							if(loopStatement.startsWith("con.else(") || loopStatement.startsWith("con.loop("))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Else/Loop part with condition"));
							else
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							return ast;
						}
						
						loopCondition = null;
					}else if(!loopStatement.contains("(") || !loopStatement.contains(")")) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.CONT_FLOW_ARG_MISSING, "Missing loop statement arguments"));
						return ast;
					}else if(loopStatement.startsWith("con.while(") || loopStatement.startsWith("con.until(") ||
							loopStatement.startsWith("con.repeat(") || loopStatement.startsWith("con.foreach(")) {
						int conditionStartIndex = loopStatement.indexOf('(');
						int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(loopStatement, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
						if(conditionEndIndex == -1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket for loop statement is missing"));
							return ast;
						}
						if(conditionEndIndex != loopStatement.length() - 1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Trailing stuff behind condition"));
							return ast;
						}
						loopCondition = loopStatement.substring(conditionStartIndex + 1, conditionEndIndex);
					}else {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Loop statement part is invalid"));
						return ast;
					}
					
					AbstractSyntaxTree loopBody = parseLines(lines);
					if(loopBody == null) {
						nodes.add(new AbstractSyntaxTree.LoopStatementNode(loopStatmentParts, loopStatementLineNumberFrom, lineNumber));
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "In loop body"));
						
						return ast;
					}
					
					if(loopStatement.startsWith("con.else")) {
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartElseNode(loopBody, loopConditionLineNumberFrom, lineNumber));
					}else if(loopStatement.startsWith("con.loop")) {
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartLoopNode(loopBody, loopConditionLineNumberFrom, lineNumber));
					}else if(loopStatement.startsWith("con.while")) {
						int originalLineNumber = lineNumber;
						lineNumber = loopConditionLineNumberFrom;
						AbstractSyntaxTree.OperationNode conNonNode = new AbstractSyntaxTree.OperationNode(parseOperationExpr(loopCondition),
						AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON, AbstractSyntaxTree.OperationNode.OperatorType.CONDITION);
						lineNumber = originalLineNumber;
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartWhileNode(loopBody, loopConditionLineNumberFrom, lineNumber, conNonNode));
					}else if(loopStatement.startsWith("con.until")) {
						int originalLineNumber = lineNumber;
						lineNumber = loopConditionLineNumberFrom;
						AbstractSyntaxTree.OperationNode conNonNode = new AbstractSyntaxTree.OperationNode(parseOperationExpr(loopCondition),
						AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON, AbstractSyntaxTree.OperationNode.OperatorType.CONDITION);
						lineNumber = originalLineNumber;
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartUntilNode(loopBody, loopConditionLineNumberFrom, lineNumber, conNonNode));
					}else if(loopStatement.startsWith("con.repeat") || loopStatement.startsWith("con.foreach")) {
						int originalLineNumber = lineNumber;
						lineNumber = loopConditionLineNumberFrom;
						List<AbstractSyntaxTree.Node> arguments = convertCommaOperatorsToArgumentSeparators(parseOperationExpr(loopCondition));
						Iterator<AbstractSyntaxTree.Node> argumentIter = arguments.iterator();
						lineNumber = originalLineNumber;
						
						AbstractSyntaxTree.Node varPointerNode = null;
						boolean flag = false;
						while(argumentIter.hasNext()) {
							AbstractSyntaxTree.Node node = argumentIter.next();
							
							if(node.getNodeType() == AbstractSyntaxTree.NodeType.ARGUMENT_SEPARATOR || varPointerNode != null) {
								flag = true;
								break;
							}
							
							varPointerNode = node;
						}
						if(!flag) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(loopConditionLineNumberFrom, ParsingError.INVALID_CON_PART, "con.repeat or con.foreach arguments are invalid"));
							return ast;
						}
						
						List<AbstractSyntaxTree.Node> repeatCountArgument = new LinkedList<>();
						while(argumentIter.hasNext()) {
							AbstractSyntaxTree.Node node = argumentIter.next();
							
							if(node.getNodeType() == AbstractSyntaxTree.NodeType.ARGUMENT_SEPARATOR) {
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(loopConditionLineNumberFrom, ParsingError.INVALID_CON_PART, "con.repeat or con.foreach arguments are invalid"));
								return ast;
							}
							
							repeatCountArgument.add(node);
						}
						
						AbstractSyntaxTree.Node repeatCountOrArrayOrTextNode = repeatCountArgument.size() == 1?repeatCountArgument.get(0):new AbstractSyntaxTree.ListNode(repeatCountArgument);
						
						if(loopStatement.startsWith("con.repeat"))
							loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartRepeatNode(loopBody, loopConditionLineNumberFrom, lineNumber,
									varPointerNode, repeatCountOrArrayOrTextNode));
						else
							loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartForEachNode(loopBody, loopConditionLineNumberFrom, lineNumber,
									varPointerNode, repeatCountOrArrayOrTextNode));
					}
					
					firstStatement = false;
					loopStatement = currentLine;
				}while(loopStatement != null && !(blockBracketFlag?loopStatement.equals("}"):loopStatement.equals("con.endloop")));
				
				nodes.add(new AbstractSyntaxTree.LoopStatementNode(loopStatmentParts, loopStatementLineNumberFrom, lineNumber));
				
				return ast;
			}else if(token.startsWith("con.if")) {
				int ifStatementLineNumberFrom = lineNumber;
				List<AbstractSyntaxTree.IfStatementPartNode> ifStatmentParts = new ArrayList<>();
				
				boolean blockBracketFlag = token.endsWith("{");
				boolean firstStatement = true;
				
				String ifStatement = token;
				do {
					if(blockBracketFlag) {
						//Remove "{" and "}" for the curly brackets if statement syntax
						if(firstStatement) {
							if(!ifStatement.endsWith("{"))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							
							ifStatement = ifStatement.substring(0, ifStatement.length() - 1).trim();
						}else if(!ifStatement.equals("}")) {
							if(!ifStatement.startsWith("}") || !ifStatement.endsWith("{"))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							
							ifStatement = ifStatement.substring(1, ifStatement.length() - 1).trim();
							
							if(!ifStatement.startsWith("con.")) //"con." is optional if "{" syntax is used
								ifStatement = "con." + ifStatement;
						}
					}
					
					int ifConditionLineNumberFrom = lineNumber;
					String ifCondition;
					if(ifStatement.startsWith("con.else")) {
						if(!ifStatement.equals("con.else")) {
							if(ifStatement.startsWith("con.else("))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Else part with condition"));
							else
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART));
							return ast;
						}
						
						ifCondition = null;
					}else if(!ifStatement.contains("(") || !ifStatement.contains(")")) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.CONT_FLOW_ARG_MISSING, "Missing if statement condition"));
						
						ifCondition = null;
					}else if(ifStatement.startsWith("con.if(") || ifStatement.startsWith("con.elif(")) {
						int conditionStartIndex = ifStatement.indexOf('(');
						int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(ifStatement, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
						if(conditionEndIndex == -1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Missing if statement condition"));
							return ast;
						}
						if(conditionEndIndex != ifStatement.length() - 1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "Trailing stuff behind condition"));
							return ast;
						}
						ifCondition = ifStatement.substring(conditionStartIndex + 1, conditionEndIndex);
					}else {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_CON_PART, "If statement part is invalid"));
						return ast;
					}
					
					AbstractSyntaxTree ifBody = parseLines(lines);
					if(ifBody == null) {
						nodes.add(new AbstractSyntaxTree.IfStatementNode(ifStatmentParts, ifStatementLineNumberFrom, lineNumber));
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "In if body"));
						
						return ast;
					}
					if(ifCondition == null) {
						ifStatmentParts.add(new AbstractSyntaxTree.IfStatementPartElseNode(ifBody, ifConditionLineNumberFrom, lineNumber));
					}else {
						int originalLineNumber = lineNumber;
						lineNumber = ifConditionLineNumberFrom;
						AbstractSyntaxTree.OperationNode conNonNode = new AbstractSyntaxTree.OperationNode(parseOperationExpr(ifCondition),
						AbstractSyntaxTree.OperationNode.Operator.CONDITIONAL_NON, AbstractSyntaxTree.OperationNode.OperatorType.CONDITION);
						lineNumber = originalLineNumber;
						
						ifStatmentParts.add(new AbstractSyntaxTree.IfStatementPartIfNode(ifBody, ifConditionLineNumberFrom, lineNumber, conNonNode));
					}
					
					firstStatement = false;
					ifStatement = currentLine;
				}while(ifStatement != null && !(blockBracketFlag?ifStatement.equals("}"):ifStatement.equals("con.endif")));
				
				nodes.add(new AbstractSyntaxTree.IfStatementNode(ifStatmentParts, ifStatementLineNumberFrom, lineNumber));
				
				return ast;
			}else if(!originalToken.startsWith("con.")) {
				token = originalToken; //Ignore, because ".*{" was no control flow statement
			}else{
				return null;
			}
		}
		
		//Return values
		if(token.startsWith("return")) {
			//Return without value
			if(token.equals("return")) {
				nodes.add(new AbstractSyntaxTree.ReturnNode(lineNumber));
				
				return ast;
			}
			
			//Return with value
			String returnStatement = token.substring(7).trim();
			AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(returnStatement, lines, true);
			nodes.add(new AbstractSyntaxTree.ReturnNode(returnedNode == null?parseLRvalue(returnStatement, lines, true).convertToNode():returnedNode));
			
			return ast;
		}
		
		//Throw values
		if(token.startsWith("throw ")) {
			String throwStatement = token.substring(6).trim();
			AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(throwStatement, lines, true);
			nodes.add(new AbstractSyntaxTree.ThrowNode(returnedNode == null?parseLRvalue(throwStatement, lines, true).convertToNode():returnedNode));
			
			return ast;
		}
		
		nodes.addAll(parseToken(token, lines).getChildren());
		
		return ast;
	}
	
	/**
	 * @return true if the parser flag was valid else false
	 */
	private boolean parseParserFlags(String parserFlag, String value) {
		//String[] tokens = LangPatterns.GENERAL_DOT.split(parserFlag);
		
		return false;
	}
	private AbstractSyntaxTree parseTranslationKey(String translationKey) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		if(translationKey.startsWith("%$")) {
			//Prepare "%$" for translation key
			translationKey = translationKey.substring(1) + "\\e";
		}
		
		StringBuilder builder = new StringBuilder();
		while(translationKey.length() > 0) {
			//Unescaping
			if(translationKey.startsWith("\\")) {
				clearAndParseStringBuilderTranslationKey(builder, nodes);
				
				if(translationKey.length() == 1)
					break;
				
				nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(lineNumber, translationKey.charAt(1)));
				
				if(translationKey.length() < 3)
					break;
				
				translationKey = translationKey.substring(2);
				continue;
			}
			
			//Parser function calls
			if(LangPatterns.matches(translationKey, LangPatterns.PARSING_STARTS_WITH_PARSER_FUNCTION_CALL)) {
				clearAndParseStringBuilderTranslationKey(builder, nodes);
				
				int parameterStartIndex = translationKey.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(translationKey, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
					return ast;
				}
				
				String functionCall = translationKey.substring(0, parameterEndIndex + 1);
				
				String functionName = functionCall.substring(translationKey.indexOf('.') + 1, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				AbstractSyntaxTree.Node ret = parseParserFunction(functionName, functionParameterList);
				if(ret != null) {
					translationKey = translationKey.substring(parameterEndIndex + 1);
					
					nodes.add(ret);
					
					continue;
				}
				
				//Invalid parser function
			}
			
			//Vars
			Pattern varNamePattern = LangPatterns.VAR_NAME_NORMAL;
			Matcher matcher = varNamePattern.matcher(translationKey);
			if(matcher.find() && matcher.start() == 0) {
				clearAndParseStringBuilderTranslationKey(builder, nodes);
				
				int len = matcher.end();
				
				builder.append(translationKey.substring(0, len));
				translationKey = translationKey.substring(len);
				
				clearAndParseStringBuilderTranslationKey(builder, nodes);
				
				continue;
			}
			
			builder.append(translationKey.charAt(0));
			if(translationKey.length() > 1) {
				translationKey = translationKey.substring(1);
			}else {
				translationKey = "";
				clearAndParseStringBuilderTranslationKey(builder, nodes);
			}
		}
		
		return ast;
	}
	private void clearAndParseStringBuilderTranslationKey(StringBuilder builder, List<AbstractSyntaxTree.Node> nodes) {
		if(builder.length() == 0)
			return;
		
		String token = builder.toString();
		builder.delete(0, builder.length());
		
		//Vars
		if(LangPatterns.matches(token, LangPatterns.VAR_NAME_NORMAL)) {
			nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, token));
			
			return;
		}
		
		//TEXT
		nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, token));
	}
	private AbstractSyntaxTree parseLRvalue(String lrvalue, BufferedReader lines, boolean isRvalue) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		if(isRvalue) {
			if(lines != null) {
				if(lrvalue.startsWith("(") && LangPatterns.matches(lrvalue, LangPatterns.PARSING_CONTAINS_WITH_FUNC_DEFINITION_END_WITH_OR_WITHOUT_TYPE_CONSTRAINT)) {
					//Function definition
					
					int parameterListEndIndex = LangUtils.getIndexOfMatchingBracket(lrvalue, 0, Integer.MAX_VALUE, '(', ')');
					if(parameterListEndIndex < 1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in function definition"));
						
						return ast;
					}
					
					int lineNumberFrom = lineNumber;
					
					String functionHead = lrvalue.substring(1, parameterListEndIndex);
					List<AbstractSyntaxTree.Node> parameterList = parseFunctionParameterList(functionHead, true).getChildren();
					
					String functionReturnValueTypeConstraint;
					String functionBody;
					if(lrvalue.charAt(parameterListEndIndex + 1) == ':') {
						int endIndex = lrvalue.indexOf('}');
						String rawTypeConstraint = lrvalue.substring(parameterListEndIndex + 2, endIndex + 1);
						
						if(!LangPatterns.matches(rawTypeConstraint, LangPatterns.TYPE_CONSTRAINT)) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_ASSIGNMENT, "Invalid type constraint: \"" + rawTypeConstraint + "\""));
							
							return ast;
						}
						
						functionReturnValueTypeConstraint = rawTypeConstraint.substring(1, rawTypeConstraint.length() - 1);
						functionBody = lrvalue.substring(endIndex + 5);
					}else {
						functionReturnValueTypeConstraint = null;
						functionBody = lrvalue.substring(parameterListEndIndex + 5);
					}
					
					if(functionBody.trim().equals("{")) {
						nodes.add(new AbstractSyntaxTree.FunctionDefinitionNode(parameterList,
								functionReturnValueTypeConstraint, parseLines(lines), lineNumberFrom, lineNumber));
					}else if(lrvalue.endsWith("{") && (lrvalue.charAt(lrvalue.length() - 2) != '\\' ||
							LangUtils.isBackslashAtIndexEscaped(lrvalue, lrvalue.length() - 2))) {
						nodes.add(new AbstractSyntaxTree.FunctionDefinitionNode(parameterList,
								functionReturnValueTypeConstraint, parseLines(functionBody, true, lines),
								lineNumberFrom, lineNumber));
					}else {
						nodes.add(new AbstractSyntaxTree.FunctionDefinitionNode(parameterList,
								functionReturnValueTypeConstraint, parseLines(functionBody, true, null),
								lineNumberFrom, lineNumber));
					}
					
					return ast;
				}else if(LangPatterns.matches(lrvalue, LangPatterns.VAR_NAME_FUNC_PTR_WITH_FUNCS)) {
					//Function pointer copying
					
					nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, lrvalue));
					return ast;
				}else if(lrvalue.trim().equals("{")) { 
					//Struct definition
					
					int lineNumberFrom = lineNumber;
					List<String> memberNames = new LinkedList<>();
					List<String> typeConstraints = new LinkedList<>();
					boolean hasEndBrace = false;
					
					while(lines != null && lines.ready()) {
						String line = nextLine(lines).trim();
						
						List<AbstractSyntaxTree.Node> errorNodes = new LinkedList<>();
						line = prepareNextLine(line, lines, errorNodes);
						if(errorNodes.size() > 0) {
							errorNodes.forEach(ast::addChild);
							return ast;
						}
						
						if(line == null || line.isEmpty())
							continue;
						
						if(line.equals("}")) {
							hasEndBrace = true;
							
							break;
						}
						
						String typeConstraint = null;
						int braceIndex = line.indexOf('{');
						if(braceIndex != -1) {
							String rawTypeConstraint = line.substring(braceIndex);
							line = line.substring(0, braceIndex);
							
							if(!LangPatterns.matches(rawTypeConstraint, LangPatterns.TYPE_CONSTRAINT)) {
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_ASSIGNMENT, "Invalid type constraint: \"" + rawTypeConstraint + "\""));
								
								return ast;
							}
							
							typeConstraint = rawTypeConstraint.substring(1, rawTypeConstraint.length() - 1);
						}
						
						if(!LangPatterns.matches(line, LangPatterns.VAR_NAME_WITHOUT_PREFIX)) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_ASSIGNMENT, "Invalid struct member name: \"" + line + "\""));
							
							return ast;
						}
						
						if(memberNames.contains(line)) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_ASSIGNMENT, "Duplicated struct member name: \"" + line + "\""));
							
							return ast;
						}
						
						memberNames.add(line);
						typeConstraints.add(typeConstraint);
					}
					
					if(!hasEndBrace) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "\"}\" is missing in struct definition"));
						
						return ast;
					}
					
					nodes.add(new AbstractSyntaxTree.StructDefinitionNode(lineNumberFrom, lineNumber, memberNames, typeConstraints));
					
					return ast;
				}
			}
		}
		
		nodes.addAll(parseToken(lrvalue, lines).getChildren());
		
		return ast;
	}
	
	private AbstractSyntaxTree parseToken(String token, BufferedReader lines) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		StringBuilder builder = new StringBuilder();
		while(token.length() > 0) {
			//Unescaping
			if(token.startsWith("\\")) {
				clearAndParseStringBuilder(builder, nodes);
				
				if(token.length() == 1)
					break;
				
				nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(lineNumber, token.charAt(1)));
				
				if(token.length() < 3)
					break;
				
				token = token.substring(2);
				continue;
			}
			
			//Function calls
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int lineNumberFrom = lineNumber;
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in function call"));
					return ast;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				token = token.substring(parameterEndIndex + 1);
				
				String functionName = functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				nodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), lineNumberFrom, lineNumber, functionName));
				continue;
			}
			
			//Function call of previous value
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL_PREVIOUS_VALUE)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int lineNumberFrom = lineNumber;
				
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex != -1) {
					String functionCall = token.substring(0, parameterEndIndex + 1);
					token = token.substring(parameterEndIndex + 1);
					
					String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
					String tmp = LangPatterns.replaceAll(functionParameterList, "", LangPatterns.PARSING_FRONT_WHITESPACE);
					int leadingWhitespaceCount = functionParameterList.length() - tmp.length();
					String leadingWhitespace = functionParameterList.substring(0, leadingWhitespaceCount);
					tmp = tmp.trim();
					String trailingWhitespace = functionParameterList.substring(tmp.length() + leadingWhitespaceCount, functionParameterList.length());
					
					nodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode(leadingWhitespace, trailingWhitespace, parseFunctionParameterList(functionParameterList, false).
							getChildren(), lineNumberFrom, lineNumber));
					continue;
				}
			}
			
			//Parser function calls
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_PARSER_FUNCTION_CALL)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
					return ast;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				
				String functionName = functionCall.substring(token.indexOf('.') + 1, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				AbstractSyntaxTree.Node ret = parseParserFunction(functionName, functionParameterList);
				if(ret != null) {
					token = token.substring(parameterEndIndex + 1);
					
					nodes.add(ret);
					
					continue;
				}
				
				//Invalid parser function
			}
			
			//VarPtr
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_VAR_NAME_PTR_AND_DEREFERENCE)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, token.indexOf('$') + 1, Integer.MAX_VALUE, '[', ']');
				if(endIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in variable pointer"));
					return ast;
				}
				String varPtr = token.substring(0, endIndex + 1);
				token = token.substring(endIndex + 1);
				
				nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, varPtr));
				continue;
			}
			
			//Vars
			Pattern varNamePattern = LangPatterns.VAR_NAME_FULL_WITH_FUNCS;
			Matcher matcher = varNamePattern.matcher(token);
			if(matcher.find() && matcher.start() == 0) {
				clearAndParseStringBuilder(builder, nodes);
				
				int len = matcher.end();
				
				builder.append(token.substring(0, len));
				token = token.substring(len);
				
				clearAndParseStringBuilder(builder, nodes);
				
				continue;
			}
			
			builder.append(token.charAt(0));
			if(token.length() > 1) {
				token = token.substring(1);
			}else {
				token = "";
				clearAndParseStringBuilder(builder, nodes);
			}
		}
		
		return ast;
	}
	
	private AbstractSyntaxTree parseSimpleAssignmentValue(String translationValue) {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		StringBuilder builder = new StringBuilder();
		while(translationValue.length() > 0) {
			//Unescaping
			if(translationValue.startsWith("\\")) {
				if(builder.length() > 0) {
					nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, builder.toString()));
					builder.delete(0, builder.length());
				}
				
				if(translationValue.length() == 1)
					break;
				
				nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(lineNumber, translationValue.charAt(1)));
				
				if(translationValue.length() < 3)
					break;
				
				translationValue = translationValue.substring(2);
				continue;
			}
			
			builder.append(translationValue.charAt(0));
			if(translationValue.length() > 1) {
				translationValue = translationValue.substring(1);
			}else {
				translationValue = "";
			}
		}
		
		nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, builder.toString()));
		
		return ast;
	}
	
	private void clearAndParseStringBuilder(StringBuilder builder, List<AbstractSyntaxTree.Node> nodes) {
		if(builder.length() == 0)
			return;
		
		String token = builder.toString();
		builder.delete(0, builder.length());
		
		//Vars & FuncPtrs
		if(LangPatterns.matches(token, LangPatterns.VAR_NAME_FULL_WITH_FUNCS)) {
			nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, token));
			
			return;
		}
		
		if(!LangPatterns.matches(token, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE) && !LangPatterns.matches(token, LangPatterns.PARSING_INVALID_FLOATING_POINT_NUMBER)) {
			//INT
			try {
				nodes.add(new AbstractSyntaxTree.IntValueNode(lineNumber, Integer.parseInt(token)));
				
				return;
			}catch(NumberFormatException ignore) {}
			
			//LONG
			try {
				if(token.endsWith("l") || token.endsWith("L"))
					nodes.add(new AbstractSyntaxTree.LongValueNode(lineNumber, Long.parseLong(token.substring(0, token.length() - 1))));
				else
					nodes.add(new AbstractSyntaxTree.LongValueNode(lineNumber, Long.parseLong(token)));
				
				
				return;
			}catch(NumberFormatException ignore) {}
			
			//FLOAT
			if(token.endsWith("f") || token.endsWith("F")) {
				try {
					nodes.add(new AbstractSyntaxTree.FloatValueNode(lineNumber, Float.parseFloat(token.substring(0, token.length() - 1))));
					
					return;
				}catch(NumberFormatException ignore) {}
			}
			
			//DOUBLE
			try {
				nodes.add(new AbstractSyntaxTree.DoubleValueNode(lineNumber, Double.parseDouble(token)));
				
				return;
			}catch(NumberFormatException ignore) {}
		}
		
		//CHAR
		if(token.length() == 1) {
			nodes.add(new AbstractSyntaxTree.CharValueNode(lineNumber, token.charAt(0)));
			
			return;
		}
		
		//NULL
		if(token.equals("null")) {
			nodes.add(new AbstractSyntaxTree.NullValueNode(lineNumber));
			
			return;
		}
		
		//TEXT
		nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, token));
	}
	
	private AbstractSyntaxTree parseFunctionParameterList(String parameterList, boolean functionDefinition) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		parameterList = parameterList.trim();
		
		if(functionDefinition) {
			if(!parameterList.isEmpty()) {
				String[] tokens = parameterList.split(",");
				
				if(tokens.length == 0 || parameterList.startsWith(",") || parameterList.endsWith(",")) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_PARAMETER,
							"Empty function parameter"));
				}
				
				for(String token:tokens) {
					token = token.trim();
					
					if(token.isEmpty()) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_PARAMETER,
								"Empty function parameter"));
						
						continue;
					}
					
					String typeConstraint = null;
					int braceIndex = token.indexOf('{');
					if(braceIndex != -1) {
						String rawTypeConstraint = token.substring(braceIndex);
						token = token.substring(0, braceIndex);
						
						if(!LangPatterns.matches(rawTypeConstraint, LangPatterns.TYPE_CONSTRAINT)) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.INVALID_ASSIGNMENT, "Invalid type constraint: \"" + rawTypeConstraint + "\""));
							
							continue;
						}
						
						typeConstraint = rawTypeConstraint.substring(1, rawTypeConstraint.length() - 1);
					}
					
					nodes.add(new AbstractSyntaxTree.VariableNameNode(lineNumber, token, typeConstraint));
				}
			}
		}else {
			StringBuilder builder = new StringBuilder();
			boolean hasNodesFlag = false;
			
			loop:
			while(parameterList.length() > 0) {
				//Unescaping
				if(parameterList.startsWith("\\")) {
					clearAndParseStringBuilder(builder, nodes);
					
					if(parameterList.length() == 1)
						break;
					
					nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(lineNumber, parameterList.charAt(1)));
					hasNodesFlag = true;
					
					if(parameterList.length() < 3)
						break;
					
					parameterList = parameterList.substring(2);
					continue;
				}
				
				//Function calls
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int lineNumberFrom = lineNumber;
					
					int parameterStartIndex = parameterList.indexOf('(');
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in function call"));
						return ast;
					}
					
					String functionCall = parameterList.substring(0, parameterEndIndex + 1);
					parameterList = parameterList.substring(parameterEndIndex + 1);
					
					String functionName = functionCall.substring(0, parameterStartIndex);
					String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
					
					nodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), lineNumberFrom, lineNumber, functionName));
					
					hasNodesFlag = true;
					continue;
				}
				//Function call of previous value
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL_PREVIOUS_VALUE)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int lineNumberFrom = lineNumber;
					
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, 0, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex != -1) {
						String functionCall = parameterList.substring(0, parameterEndIndex + 1);
						parameterList = parameterList.substring(parameterEndIndex + 1);
						
						String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
						String tmp = LangPatterns.replaceAll(functionParameterList, "", LangPatterns.PARSING_FRONT_WHITESPACE);
						int leadingWhitespaceCount = functionParameterList.length() - tmp.length();
						String leadingWhitespace = functionParameterList.substring(0, leadingWhitespaceCount);
						tmp = tmp.trim();
						String trailingWhitespace = functionParameterList.substring(tmp.length() + leadingWhitespaceCount, functionParameterList.length());
						
						nodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode(leadingWhitespace, trailingWhitespace, parseFunctionParameterList(functionParameterList, false).
								getChildren(), lineNumberFrom, lineNumber));
						
						hasNodesFlag = true;
						continue;
					}
				}
				
				//Parser function calls
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_PARSER_FUNCTION_CALL)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int parameterStartIndex = parameterList.indexOf('(');
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
						return ast;
					}
					
					String functionCall = parameterList.substring(0, parameterEndIndex + 1);
					
					String functionName = functionCall.substring(parameterList.indexOf('.') + 1, parameterStartIndex);
					String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
					
					AbstractSyntaxTree.Node ret = parseParserFunction(functionName, functionParameterList);
					if(ret != null) {
						parameterList = parameterList.substring(parameterEndIndex + 1);
						
						nodes.add(ret);
						
						hasNodesFlag = true;
						continue;
					}
					
					//Invalid parser function
				}
				
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_ARGUMENT_SEPARATOR_LEADING_WHITESPACE)) {
					//Create new ArgumentSeparatorNode with "," surrounded by whitespaces
					
					if(builder.length() == 0) {
						if(nodes.size() == 0 || nodes.get(nodes.size() - 1) instanceof AbstractSyntaxTree.ArgumentSeparatorNode) {
							//Add empty TextObject in between two ","
							if(!hasNodesFlag)
								nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, ""));
						}
					}else {
						nodes.add(parseToken(builder.toString(), null).convertToNode());
						builder.delete(0, builder.length());
					}
					
					int commaIndex = parameterList.indexOf(',');
					builder.append(parameterList.substring(0, commaIndex + 1));
					parameterList = parameterList.substring(commaIndex + 1);
					while(parameterList.length() > 0 && LangPatterns.matches(parameterList, LangPatterns.PARSING_LEADING_WHITSPACE)) {
						builder.append(parameterList.charAt(0));
						if(parameterList.length() > 1) {
							parameterList = parameterList.substring(1);
						}else {
							nodes.add(new AbstractSyntaxTree.ArgumentSeparatorNode(lineNumber, builder.toString()));
							
							//Add empty TextObject after last ","
							nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, ""));
							
							break loop;
						}
					}
					
					nodes.add(new AbstractSyntaxTree.ArgumentSeparatorNode(lineNumber, builder.toString()));
					builder.delete(0, builder.length());
					if(parameterList.length() == 0) {
						//Add empty TextObject after last ","
						nodes.add(new AbstractSyntaxTree.TextValueNode(lineNumber, ""));
						
						break;
					}
					
					hasNodesFlag = false;
					continue;
				}
				
				//VarPtr
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_VAR_NAME_PTR_AND_DEREFERENCE)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int endIndex = LangUtils.getIndexOfMatchingBracket(parameterList, parameterList.indexOf('$') + 1, Integer.MAX_VALUE, '[', ']');
					if(endIndex != -1) {
						String varPtr = parameterList.substring(0, endIndex + 1);
						parameterList = parameterList.substring(endIndex + 1);
						
						nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, varPtr));
						hasNodesFlag = true;
						continue;
					}
				}
				
				//Array unpacking
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_ARRAY_UNPACKING)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int index = parameterList.indexOf('.') + 3;
					String varName = parameterList.substring(0, index);
					parameterList = parameterList.substring(index);
					
					nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lineNumber, varName));
					hasNodesFlag = true;
					continue;
				}
				
				//Vars
				Pattern varNamePattern = LangPatterns.VAR_NAME_FULL_WITH_FUNCS;
				Matcher matcher = varNamePattern.matcher(parameterList);
				if(matcher.find() && matcher.start() == 0) {
					clearAndParseStringBuilder(builder, nodes);
					
					int len = matcher.end();
					
					builder.append(parameterList.substring(0, len));
					parameterList = parameterList.substring(len);
					
					clearAndParseStringBuilder(builder, nodes);
					
					continue;
				}
				
				builder.append(parameterList.charAt(0));
				if(parameterList.length() > 1) {
					parameterList = parameterList.substring(1);
				}else {
					parameterList = "";
					nodes.add(parseToken(builder.toString(), null).convertToNode());
				}
			}
		}
		
		return ast;
	}
	private AbstractSyntaxTree.Node parseParserFunction(String name, String parameterList) throws IOException {
		switch(name) {
			case "con":
				return parseCondition(parameterList);
			case "math":
				return parseMathExpr(parameterList);
			case "norm":
				return parseToken(parameterList, null).convertToNode();
			case "op":
				return parseOperationExpr(parameterList);
		}
		
		return null;
	}
	
	/**
	 * Fix for "\{{{" would be parsed as mutliline text start sequence
	 */
	private String maskEscapedMultilineStringStartSequences(String line) {
		int i = 0;
		while(i < line.length()) {
			i = line.indexOf("\\{", i);
			if(i == -1)
				break;
			
			if(!LangUtils.isBackslashAtIndexEscaped(line, i)) {
				i += 2;
				line = line.substring(0, i) + "\\e" + line.substring(i); //Add "\e" after "\{"
				continue;
			}
			
			i++;
		}
		
		return line;
	}
	/**
	 * Fix for "\{{{" would be parsed as mutliline text start sequence ({"\{" -&gt; "{" [CHAR]} should be parsed as CHAR and not as TEXT {"\{\e" -&gt; "{" [TEXT]})
	 */
	private String unmaskEscapedMultilineStringStartSequences(String line) {
		int i = 0;
		while(i < line.length()) {
			i = line.indexOf("\\{\\e", i);
			if(i == -1)
				break;
			
			if(!LangUtils.isBackslashAtIndexEscaped(line, i)) {
				i += 2;
				line = line.substring(0, i) + line.substring(i + 2); //Remove "\e" after "\{"
				continue;
			}
			
			i++;
		}
		
		return line;
	}
	
	private String removeCommentsAndTrim(String line) {
		if(line == null)
			return null;
		
		line = line.trim();
		
		//Remove comments
		if(line.startsWith("#"))
			return null;
		
		//Find start index of comment if any
		int index = -1;
		int i = 0;
		while(i < line.length()) {
			char c = line.charAt(i);
			if(c == '\\') { //Ignore next char
				i += 2;
				
				continue;
			}
			
			if(c == '#') {
				index = i;
				
				break;
			}
			
			i++;
		}
		if(index > -1)
			line = line.substring(0, index);
		
		line = line.trim();
		
		if(line.isEmpty())
			return null;
		
		return line;
	}
	
	/**
	 * @param line (The line read by lines)
	 * @param errorNodes Will be used to add error nodes
	 * @return The next line to be executed or null if error
	 * @throws IOException
	 */
	private String parseMultilineTextAndLineContinuation(String line, BufferedReader lines, List<AbstractSyntaxTree.Node> errorNodes) throws IOException {
		StringBuilder lineTmp = new StringBuilder();
		
		line = maskEscapedMultilineStringStartSequences(line);
		
		while(line.contains("{{{") || line.endsWith("\\")) {
			if(line.contains("{{{")) { //Multiline text
				boolean multipleLinesFlag = true;
				int startIndex = line.indexOf("{{{");
				if(line.contains("}}}")) {
					//Start search for "}}}" after "{{{"
					int endIndex = line.indexOf("}}}", startIndex);
					
					//Multiple lines if "}}}" was found before "{{{"
					if(endIndex != -1) {
						multipleLinesFlag = false;
						line = line.substring(0, startIndex) + LangUtils.escapeString(line.substring(startIndex + 3, endIndex).replace("\\{\\e", "\\{")) + line.substring(endIndex + 3);
					}
				}
				
				if(multipleLinesFlag) {
					lineTmp.delete(0, lineTmp.length());
					lineTmp.append(line.substring(startIndex + 3));
					line = line.substring(0, startIndex);
					String lineTmpString;
					while(true) {
						if(lines == null || !lines.ready()) {
							errorNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "Multiline Text end is missing"));
							return null;
						}
						
						lineTmpString = nextLine(lines);
						if(lineTmpString == null) {
							errorNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "Multiline Text end is missing"));
							return null;
						}
						lineTmpString = maskEscapedMultilineStringStartSequences(lineTmpString);
						lineTmp.append('\n');
						if(lineTmpString.contains("}}}")) {
							int endIndex = lineTmpString.indexOf("}}}");
							lineTmp.append(lineTmpString.substring(0, endIndex));
							line = line + LangUtils.escapeString(lineTmp.toString().replace("\\{\\e", "\\{")) + lineTmpString.substring(endIndex + 3);
							
							break;
						}
						
						lineTmp.append(lineTmpString);
					}
				}
			}else { //Line continuation
				line = line.substring(0, line.length() - 1);
				
				if(lines == null || !lines.ready()) {
					errorNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "Line continuation has no second line"));
					return null;
				}
				String lineTmpString = nextLine(lines);
				if(lineTmpString == null) {
					errorNodes.add(new AbstractSyntaxTree.ParsingErrorNode(lineNumber, ParsingError.EOF, "Line continuation has no second line"));
					return null;
				}
				lineTmpString = maskEscapedMultilineStringStartSequences(lineTmpString);
				line += lineTmpString;
			}
		}
		
		return unmaskEscapedMultilineStringStartSequences(line);
	}
	
	private String prepareNextLine(String line, BufferedReader lines, List<AbstractSyntaxTree.Node> errorNodes) throws IOException {
		line = parseMultilineTextAndLineContinuation(line, lines, errorNodes);
		
		line = removeCommentsAndTrim(line);
		
		return line;
	}
	
	private String nextLine(BufferedReader lines) throws IOException {
		if(lines == null)
			return null;
		
		String line = lines.readLine();
		
		if(line != null)
			lineNumber++;
		
		return line;
	}
	
	public static enum ParsingError {
		BRACKET_MISMATCH     (-1, "Bracket mismatch"),
		CONT_FLOW_ARG_MISSING(-2, "Control flow statement condition(s) or argument(s) is/are missing"),
		EOF                  (-3, "End of file was reached early"),
		INVALID_CON_PART     (-4, "Invalid statement part in control flow statement"),
		INVALID_ASSIGNMENT   (-5, "Invalid assignment operation"),
		INVALID_PARAMETER    (-6, "Invalid function parameter");
		
		private final int errorCode;
		private final String errorText;
		
		private ParsingError(int errorCode, String errorText) {
			this.errorCode = errorCode;
			this.errorText = errorText;
		}
		
		public int getErrorCode() {
			return errorCode;
		}
		
		public String getErrorText() {
			return errorText;
		}
	}
}