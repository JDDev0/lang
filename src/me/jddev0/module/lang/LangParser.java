package me.jddev0.module.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Lang-Module<br>
 * Parsing of lang files into an AST structure for LangInterpreter
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangParser {
	private String currentLine;
	
	public void resetCurrentLine() {
		currentLine = null;
	}
	
	public AbstractSyntaxTree parseLines(BufferedReader lines) throws IOException {
		if(lines == null || !lines.ready())
			return null;
		
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		int blockPos = 0;
		
		StringBuilder lineTmp = new StringBuilder();
		do {
			String line = lines.readLine();
			if(line == null) {
				currentLine = null;
				
				break;
			}
			//Parse multiline text & line continuation
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
							line = line.substring(0, startIndex) + LangUtils.escapeString(line.substring(startIndex + 3, endIndex)) + line.substring(endIndex + 3);
						}
					}
					
					if(multipleLinesFlag) {
						lineTmp.delete(0, lineTmp.length());
						lineTmp.append(line.substring(startIndex + 3));
						line = line.substring(0, startIndex);
						String lineTmpString;
						while(true) {
							if(!lines.ready()) {
								ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF, "Multiline Text end is missing"));
								return ast;
							}
							
							lineTmpString = lines.readLine();
							if(lineTmpString == null) {
								ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF, "Multiline Text end is missing"));
								return ast;
							}
							lineTmpString = maskEscapedMultilineStringStartSequences(lineTmpString);
							lineTmp.append('\n');
							if(lineTmpString.contains("}}}")) {
								int endIndex = lineTmpString.indexOf("}}}");
								lineTmp.append(lineTmpString.substring(0, endIndex));
								line = line + LangUtils.escapeString(lineTmp.toString()) + lineTmpString.substring(endIndex + 3);
								
								break;
							}
							
							lineTmp.append(lineTmpString);
						}
					}
				}else { //Line continuation
					line = line.substring(0, line.length() - 1);
					
					if(!lines.ready()) {
						ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF, "Line continuation has no second line"));
						return ast;
					}
					String lineTmpString = lines.readLine();
					if(lineTmpString == null) {
						ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF, "Line continuation has no second line"));
						return ast;
					}
					lineTmpString = maskEscapedMultilineStringStartSequences(lineTmpString);
					line += lineTmpString;
				}
			}
			line = unmaskEscapedMultilineStringStartSequences(line);
			
			line = currentLine = prepareLine(line);
			if(line != null) {
				//Blocks and function bodies
				if(line.endsWith("{") && !line.endsWith("\\{") && !line.endsWith(") -> {") /* Function definition is no "block" */) {
					blockPos++;
					
					continue;
				}else if(line.startsWith("}")) {
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
		}while(lines.ready());
		
		return ast;
	}
	
	AbstractSyntaxTree.OperationNode parseCondition(String condition) throws IOException {
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
				
				leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(token.charAt(1)));
				
				if(token.length() == 2)
					break;
				
				token = token.substring(2);
				
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
			
			if(token.startsWith("(")) {
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '(', ')');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in math expression is missing"));
					
					break;
				}
				
				//Ignore "()" if something was before (=> "Escaped" "()") -> Add "(" to builder (below outer if)
				if(builder.length() == 0) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					leftNodes.add(parseOperationExpr(token.substring(1, endIndex), null, null, 0, type));
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
					
					leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(token.charAt(0)));
					token = token.substring(1);
					
					continue;
				}
			}else if(token.startsWith("[")) {
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '[', ']');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in math expression is missing"));
					
					break;
				}
				//Binary operator if something was before else unary operator
				if(AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type) && (builder.length() > 0 || leftNodes.size() > 0)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.GET_ITEM;
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					AbstractSyntaxTree.OperationNode node = parseOperationExpr(token.substring(1, endIndex), null, null, 0, type);
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
				}else if(AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					//Array creation
					leftNodes.add(new AbstractSyntaxTree.ArrayNode(parseFunctionParameterList(token.substring(1, endIndex), false).getChildren()));
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
					
					//Add as value if nothing is behind opeator
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
			token.startsWith("<=") || token.startsWith(">=") || token.startsWith("<") || token.startsWith(">") || token.startsWith("&&") || token.startsWith("||") || token.startsWith("!") ||
			token.startsWith("&") || token.startsWith("~") || token.startsWith("▲") || token.startsWith("▼") || token.startsWith("*") || token.startsWith("//") || token.startsWith("/") ||
			token.startsWith("%") || token.startsWith("^") || token.startsWith("|") || token.startsWith("<<") || token.startsWith(">>>") || token.startsWith(">>") || token.startsWith("+") ||
			token.startsWith("-") || token.startsWith("@") || token.startsWith("?:") || token.startsWith("??")) {
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
				}else if(token.startsWith("&&") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.AND;
				}else if(token.startsWith("||") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.OperationNode.Operator.OR;
				}else if(token.startsWith("!") && AbstractSyntaxTree.OperationNode.OperatorType.CONDITION.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.NOT;
				}else if(token.startsWith("&") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_AND;
				}else if(token.startsWith("~") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.BITWISE_NOT;
				}else if(token.startsWith("▲") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.INC;
				}else if(token.startsWith("▼") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.DEC;
				}else if(token.startsWith("*") && AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type)) {
					operator = AbstractSyntaxTree.OperationNode.Operator.MUL;
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
					if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
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
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL)) {
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
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
					
					break;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				token = token.substring(parameterEndIndex + 1);
				
				String functionName = functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				leftNodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
				continue;
			}
			
			//Normal function calls without prefix
			if(AbstractSyntaxTree.OperationNode.OperatorType.MATH.isCompatibleWith(type) && LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNC_FUNCTION_CALL_WITHOUT_PREFIX)) {
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
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
					
					break;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				token = token.substring(parameterEndIndex + 1);
				
				String functionName = "func." + functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				leftNodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
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
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
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
	
	private AbstractSyntaxTree.AssignmentNode parseAssignment(String line, BufferedReader lines, boolean isInnerAssignment) throws IOException {
		boolean isVariableAssignment = LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT);
		if(isInnerAssignment?isVariableAssignment:LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT_VAR_NAME_OR_TRANSLATION)) {
			String[] tokens = LangPatterns.PARSING_ASSIGNMENT_OPERATOR.split(line, 2);
			String assignmentOperator = line.substring(tokens[0].length() + 1, line.indexOf('=', tokens[0].length()));
			
			AbstractSyntaxTree.Node lvalueNode = ((isVariableAssignment || !assignmentOperator.isEmpty())?parseLRvalue(tokens[0], null, false):parseTranslationKey(tokens[0])).convertToNode();
			AbstractSyntaxTree.Node rvalueNode;
			
			if(assignmentOperator.isEmpty()) {
				AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(tokens[1], lines, true);
				rvalueNode = returnedNode != null?returnedNode:parseLRvalue(tokens[1], lines, true).convertToNode();
			}else {
				AbstractSyntaxTree.OperationNode.Operator operator;
				
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
					case "//":
						operator = AbstractSyntaxTree.OperationNode.Operator.FLOOR_DIV;
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
					case "$":
						operator = AbstractSyntaxTree.OperationNode.Operator.NON;
						break;
					
					default:
						operator = null;
				}
				
				if(operator == null)
					rvalueNode = new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_ASSIGNMENT);
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
			return new AbstractSyntaxTree.AssignmentNode((isVariableAssignment?parseLRvalue(line, null, false):parseTranslationKey(line)).convertToNode(), new AbstractSyntaxTree.NullValueNode());
		}
		
		return null;
	}
	
	private AbstractSyntaxTree parseLine(String token, BufferedReader lines) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		//If statement
		if(token.startsWith("con.")) {
			if(token.startsWith("con.continue") || token.startsWith("con.break")) {
				List<AbstractSyntaxTree.Node> argumentNodes;
				if(!token.contains("(") && !token.contains(")")) {
					argumentNodes = null;
				}else {
					int argumentsStartIndex = token.indexOf('(');
					int argumentsEndIndex = LangUtils.getIndexOfMatchingBracket(token, argumentsStartIndex, Integer.MAX_VALUE, '(', ')');
					if(argumentsEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket for con.break or con.continue is missing"));
						return ast;
					}
					argumentNodes = parseFunctionParameterList(token.substring(argumentsStartIndex + 1, argumentsEndIndex), false).getChildren();
				}
				
				AbstractSyntaxTree.Node numberNode = argumentNodes == null?null:(argumentNodes.size() == 1?argumentNodes.get(0):new AbstractSyntaxTree.ListNode(argumentNodes));
				ast.addChild(new AbstractSyntaxTree.LoopStatementContinueBreakStatement(numberNode, token.startsWith("con.continue")));
				return ast;
			}else if(token.startsWith("con.loop") || token.startsWith("con.while") || token.startsWith("con.until") || token.startsWith("con.repeat") || token.startsWith("con.foreach")) {
				List<AbstractSyntaxTree.LoopStatementPartNode> loopStatmentParts = new ArrayList<>();
				
				String loopStatement = token;
				do {
					String loopCondition = null;
					if(loopStatement.startsWith("con.else") || loopStatement.startsWith("con.loop")) {
						if(!loopStatement.equals("con.else") && !loopStatement.equals("con.loop")) {
							if(loopStatement.startsWith("con.else(") || loopStatement.startsWith("con.loop("))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "Else/Loop part with condition"));
							else
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART));
							return ast;
						}
						
						loopCondition = null;
					}else if(!loopStatement.contains("(") || !loopStatement.contains(")")) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.CONDITION_MISSING, "Missing loop statement arguments"));
						return ast;
					}else if(loopStatement.startsWith("con.while(") || loopStatement.startsWith("con.until(") || loopStatement.startsWith("con.repeat(") || loopStatement.startsWith("con.foreach(")) {
						int conditionStartIndex = loopStatement.indexOf('(');
						int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(loopStatement, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
						if(conditionEndIndex == -1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket for loop statement is missing"));
							return ast;
						}
						if(conditionEndIndex != loopStatement.length() - 1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "Trailing stuff behind condition"));
							return ast;
						}
						loopCondition = loopStatement.substring(conditionStartIndex + 1, conditionEndIndex);
					}else {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "Loop statement part is invalid"));
						return ast;
					}
					
					AbstractSyntaxTree loopBody = parseLines(lines);
					if(loopBody == null) {
						nodes.add(new AbstractSyntaxTree.LoopStatementNode(loopStatmentParts));
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF, "In loop body"));
						
						return ast;
					}
					
					if(loopStatement.startsWith("con.else")) {
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartElseNode(loopBody));
					}else if(loopStatement.startsWith("con.loop")) {
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartLoopNode(loopBody));
					}else if(loopStatement.startsWith("con.while")) {
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartWhileNode(loopBody, parseCondition(loopCondition)));
					}else if(loopStatement.startsWith("con.until")) {
						loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartUntilNode(loopBody, parseCondition(loopCondition)));
					}else if(loopStatement.startsWith("con.repeat") || loopStatement.startsWith("con.foreach")) {
						List<AbstractSyntaxTree.Node> arguments = parseFunctionParameterList(loopCondition, false).getChildren();
						Iterator<AbstractSyntaxTree.Node> argumentIter = arguments.iterator();
						
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
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "con.repeat or con.foreach arguments are invalid"));
							return ast;
						}
						
						List<AbstractSyntaxTree.Node> repeatCountArgument = new LinkedList<>();
						while(argumentIter.hasNext()) {
							AbstractSyntaxTree.Node node = argumentIter.next();
							
							if(node.getNodeType() == AbstractSyntaxTree.NodeType.ARGUMENT_SEPARATOR) {
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "con.repeat or con.foreach arguments are invalid"));
								return ast;
							}
							
							repeatCountArgument.add(node);
						}
						
						AbstractSyntaxTree.Node repeatCountOrArrayOrTextNode = repeatCountArgument.size() == 1?repeatCountArgument.get(0):new AbstractSyntaxTree.ListNode(repeatCountArgument);
						
						if(loopStatement.startsWith("con.repeat"))
							loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartRepeatNode(loopBody, varPointerNode, repeatCountOrArrayOrTextNode));
						else
							loopStatmentParts.add(new AbstractSyntaxTree.LoopStatementPartForEachNode(loopBody, varPointerNode, repeatCountOrArrayOrTextNode));
					}
					
					loopStatement = currentLine;
				}while(loopStatement != null && !loopStatement.equals("con.endloop"));
				
				nodes.add(new AbstractSyntaxTree.LoopStatementNode(loopStatmentParts));
				
				return ast;
			}else if(token.startsWith("con.if")) {
				List<AbstractSyntaxTree.IfStatementPartNode> ifStatmentParts = new ArrayList<>();
				
				String ifStatement = token;
				do {
					String ifCondition;
					if(ifStatement.startsWith("con.else")) {
						if(!ifStatement.equals("con.else")) {
							if(ifStatement.startsWith("con.else("))
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "Else part with condition"));
							else
								nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART));
							return ast;
						}
						
						ifCondition = null;
					}else if(!ifStatement.contains("(") || !ifStatement.contains(")")) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.CONDITION_MISSING, "Missing if statement condition"));
						
						ifCondition = null;
					}else if(ifStatement.startsWith("con.if(") || ifStatement.startsWith("con.elif(")) {
						int conditionStartIndex = ifStatement.indexOf('(');
						int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(ifStatement, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
						if(conditionEndIndex == -1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Missing if statement condition"));
							return ast;
						}
						if(conditionEndIndex != ifStatement.length() - 1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "Trailing stuff behind condition"));
							return ast;
						}
						ifCondition = ifStatement.substring(conditionStartIndex + 1, conditionEndIndex);
					}else {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.INVALID_CON_PART, "If statement part is invalid"));
						return ast;
					}
					
					AbstractSyntaxTree ifBody = parseLines(lines);
					if(ifBody == null) {
						nodes.add(new AbstractSyntaxTree.IfStatementNode(ifStatmentParts));
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF, "In if body"));
						
						return ast;
					}
					if(ifCondition == null)
						ifStatmentParts.add(new AbstractSyntaxTree.IfStatementPartElseNode(ifBody));
					else
						ifStatmentParts.add(new AbstractSyntaxTree.IfStatementPartIfNode(ifBody, parseCondition(ifCondition)));
					
					ifStatement = currentLine;
				}while(ifStatement != null && !ifStatement.equals("con.endif"));
				
				nodes.add(new AbstractSyntaxTree.IfStatementNode(ifStatmentParts));
				
				return ast;
			}else {
				return null;
			}
		}
		
		//Return values
		if(token.startsWith("return")) {
			//Return without value
			if(token.equals("return")) {
				nodes.add(new AbstractSyntaxTree.ReturnNode());
				
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
				
				nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(translationKey.charAt(1)));
				
				if(translationKey.length() < 3)
					break;
				
				translationKey = translationKey.substring(2);
				continue;
			}
			
			//Force node split
			if(translationKey.startsWith("$")) {
				//Variable split for variable concatenation
				clearAndParseStringBuilderTranslationKey(builder, nodes);
			}
			if(LangPatterns.matches(builder.toString(), LangPatterns.VAR_NAME_NORMAL) && LangPatterns.matches(translationKey, LangPatterns.PARSING_STARTS_WITH_NON_WORD_CHAR)) {
				//Variable split after invalid character (Not [A-Za-z0-9_]
				clearAndParseStringBuilderTranslationKey(builder, nodes);
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
			nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(token));
			
			return;
		}
		
		//TEXT
		nodes.add(new AbstractSyntaxTree.TextValueNode(token));
	}
	private AbstractSyntaxTree parseLRvalue(String lrvalue, BufferedReader lines, boolean isRvalue) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		if(isRvalue) {
			if(lines != null) {
				//Function definition (rvalue only)
				if(lrvalue.startsWith("(") && lrvalue.contains(") -> ")) {
					int parameterListEndIndex = LangUtils.getIndexOfMatchingBracket(lrvalue, 0, Integer.MAX_VALUE, '(', ')');
					if(parameterListEndIndex < 1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in function definition"));
						
						return ast;
					}
					
					String functionHead = lrvalue.substring(1, parameterListEndIndex);
					List<AbstractSyntaxTree.Node> parameterList = parseFunctionParameterList(functionHead, true).getChildren();
					
					String functionBody = lrvalue.substring(parameterListEndIndex + 5);
					
					if(lrvalue.endsWith("{")) {
						nodes.add(new AbstractSyntaxTree.FunctionDefinitionNode(parameterList, parseLines(lines)));
					}else {
						try(BufferedReader reader = new BufferedReader(new StringReader(functionBody))) {
							nodes.add(new AbstractSyntaxTree.FunctionDefinitionNode(parameterList, parseLines(reader)));
						}
					}
					
					return ast;
				}else if(LangPatterns.matches(lrvalue, LangPatterns.VAR_NAME_FUNC_PTR_WITH_FUNCS)) {
					//Function pointer copying
					
					nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lrvalue));
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
				
				nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(token.charAt(1)));
				
				if(token.length() < 3)
					break;
				
				token = token.substring(2);
				continue;
			}
			
			//Function calls
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in function call"));
					return ast;
				}
				
				String functionCall = token.substring(0, parameterEndIndex + 1);
				token = token.substring(parameterEndIndex + 1);
				
				String functionName = functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				nodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
				continue;
			}
			//Function call of previous value
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL_PREVIOUS_VALUE)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex != -1) {
					String functionCall = token.substring(0, parameterEndIndex + 1);
					token = token.substring(parameterEndIndex + 1);
					
					String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
					
					nodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode(parseFunctionParameterList(functionParameterList, false).getChildren()));
					continue;
				}
			}
			
			//Parser function calls
			if(LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_PARSER_FUNCTION_CALL)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
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
				
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, 1, Integer.MAX_VALUE, '[', ']');
				if(endIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in variable pointer"));
					return ast;
				}
				String varPtr = token.substring(0, endIndex + 1);
				token = token.substring(endIndex + 1);
				
				nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(varPtr));
				continue;
			}
			
			//Force node split
			if(token.startsWith("$") || token.startsWith("&")) {
				//Variable split for variable concatenation
				clearAndParseStringBuilder(builder, nodes);
			}
			if(LangPatterns.matches(builder.toString(), LangPatterns.VAR_NAME_FULL_WITH_FUNCS) && LangPatterns.matches(token, LangPatterns.PARSING_STARTS_WITH_NON_WORD_CHAR)) {
				//Variable split after invalid character (Not [A-Za-z0-9_]
				clearAndParseStringBuilder(builder, nodes);
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
	
	private void clearAndParseStringBuilder(StringBuilder builder, List<AbstractSyntaxTree.Node> nodes) {
		if(builder.length() == 0)
			return;
		
		String token = builder.toString();
		builder.delete(0, builder.length());
		
		//Vars & FuncPtrs
		if(LangPatterns.matches(token, LangPatterns.VAR_NAME_FULL_WITH_FUNCS)) {
			nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(token));
			
			return;
		}
		
		if(!LangPatterns.matches(token, LangPatterns.PARSING_LEADING_OR_TRAILING_WHITSPACE)) {
			//INT
			try {
				nodes.add(new AbstractSyntaxTree.IntValueNode(Integer.parseInt(token)));
				
				return;
			}catch(NumberFormatException ignore) {}
			
			//LONG
			try {
				nodes.add(new AbstractSyntaxTree.LongValueNode(Long.parseLong(token)));
				
				return;
			}catch(NumberFormatException ignore) {}
			
			//FLOAT
			try {
				float floatNumber = Float.parseFloat(token);
				if(floatNumber != Float.POSITIVE_INFINITY && floatNumber != Float.NEGATIVE_INFINITY) {
					nodes.add(new AbstractSyntaxTree.FloatValueNode(floatNumber));
					
					return;
				}
			}catch(NumberFormatException ignore) {}
			
			//DOUBLE
			try {
				nodes.add(new AbstractSyntaxTree.DoubleValueNode(Double.parseDouble(token)));
				
				return;
			}catch(NumberFormatException ignore) {}
		}
		
		//CHAR
		if(token.length() == 1) {
			nodes.add(new AbstractSyntaxTree.CharValueNode(token.charAt(0)));
			
			return;
		}
		
		//NULL
		if(token.equals("null")) {
			nodes.add(new AbstractSyntaxTree.NullValueNode());
			
			return;
		}
		
		//TEXT
		nodes.add(new AbstractSyntaxTree.TextValueNode(token));
	}
	
	private AbstractSyntaxTree parseFunctionParameterList(String parameterList, boolean functionDefinition) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		if(functionDefinition) {
			String[] tokens = parameterList.split(",");
			
			for(String token:tokens)
				if(!token.isEmpty())
					nodes.add(new AbstractSyntaxTree.VariableNameNode(token.trim()));
		}else {
			StringBuilder builder = new StringBuilder();
			boolean hasNodesFlag = false;
			
			loop:
			while(parameterList.length() > 0) {
				//Unescaping
				if(parameterList.startsWith("\\")) {
					if(builder.length() > 0)
						clearAndParseStringBuilder(builder, nodes);
					
					if(parameterList.length() == 1)
						break;
					
					nodes.add(new AbstractSyntaxTree.EscapeSequenceNode(parameterList.charAt(1)));
					hasNodesFlag = true;
					
					if(parameterList.length() < 3)
						break;
					
					parameterList = parameterList.substring(2);
					continue;
				}
				
				//Function calls
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int parameterStartIndex = parameterList.indexOf('(');
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in function call"));
						return ast;
					}
					
					String functionCall = parameterList.substring(0, parameterEndIndex + 1);
					parameterList = parameterList.substring(parameterEndIndex + 1);
					
					String functionName = functionCall.substring(0, parameterStartIndex);
					String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
					
					nodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
					
					hasNodesFlag = true;
					continue;
				}
				//Function call of previous value
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_FUNCTION_CALL_PREVIOUS_VALUE)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, 0, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex != -1) {
						String functionCall = parameterList.substring(0, parameterEndIndex + 1);
						parameterList = parameterList.substring(parameterEndIndex + 1);
						
						String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
						
						nodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode(parseFunctionParameterList(functionParameterList, false).getChildren()));
						
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
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in parser function call"));
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
						//Add empty TextObject in between two ","
						if(!hasNodesFlag)
							nodes.add(new AbstractSyntaxTree.TextValueNode(""));
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
							nodes.add(new AbstractSyntaxTree.ArgumentSeparatorNode(builder.toString()));
							
							//Add empty TextObject after last ","
							nodes.add(new AbstractSyntaxTree.TextValueNode(""));
							
							break loop;
						}
					}
					
					nodes.add(new AbstractSyntaxTree.ArgumentSeparatorNode(builder.toString()));
					builder.delete(0, builder.length());
					if(parameterList.length() == 0) {
						//Add empty TextObject after last ","
						nodes.add(new AbstractSyntaxTree.TextValueNode(""));
						
						break;
					}
					
					hasNodesFlag = false;
					continue;
				}
				
				//VarPtr
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_VAR_NAME_PTR_AND_DEREFERENCE)) {
					if(builder.length() > 0)
						clearAndParseStringBuilder(builder, nodes);
					
					int endIndex = LangUtils.getIndexOfMatchingBracket(parameterList, 1, Integer.MAX_VALUE, '[', ']');
					if(endIndex != -1) {
						String varPtr = parameterList.substring(0, endIndex + 1);
						parameterList = parameterList.substring(endIndex + 1);
						
						nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(varPtr));
						hasNodesFlag = true;
						continue;
					}
				}
				
				//Array unpacking
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_ARRAY_UNPACKING)) {
					if(builder.length() > 0)
						clearAndParseStringBuilder(builder, nodes);
					
					int index = parameterList.indexOf('.') + 3;
					String varName = parameterList.substring(0, index);
					parameterList = parameterList.substring(index);
					
					nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(varName));
					hasNodesFlag = true;
					continue;
				}
				
				//Force node split
				if(parameterList.startsWith("$") || parameterList.startsWith("&"))
					clearAndParseStringBuilder(builder, nodes);
				
				//Variable split after invalid character (Not [A-Za-z0-9_]
				if(LangPatterns.matches(builder.toString(), LangPatterns.VAR_NAME_DEREFERENCE_AND_ARRAY) &&
				LangPatterns.matches(parameterList, LangPatterns.PARSING_STARTS_WITH_NON_WORD_CHAR))
					clearAndParseStringBuilder(builder, nodes);
				
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
			case "op":
				return parseOperationExpr(parameterList);
		}
		
		return null;
	}
	
	
	private String prepareLine(String line) {
		if(line == null)
			return null;
		
		line = line.trim();
		
		//Remove comments
		if(line.startsWith("#"))
			return null;
		line = line.split("(?<!\\\\)#")[0]; //Splits at "#", but not at "\#" (RegEx negative look behind)
		line = line.replace("\\#", "#");
		
		line = line.trim();
		
		if(line.isEmpty())
			return null;
		
		return line;
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
			
			if(!LangUtils.isBackshlashAtIndexEscaped(line, i)) {
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
			
			if(!LangUtils.isBackshlashAtIndexEscaped(line, i)) {
				i += 2;
				line = line.substring(0, i) + line.substring(i + 2); //Remove "\e" after "\{"
				continue;
			}
			
			i++;
		}
		
		return line;
	}
	
	public static enum ParsingError {
		BRACKET_MISMATCH  (-1, "Bracket mismatch"),
		CONDITION_MISSING (-2, "If statement condition missing"),
		EOF               (-3, "End of file was reached early"),
		INVALID_CON_PART  (-4, "Invalid statement part for conditional statement"),
		INVALID_ASSIGNMENT(-5, "Invalid assignment operation");
		
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