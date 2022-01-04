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
				AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(line, lines, false);
				if(returnedNode != null) {
					ast.addChild(returnedNode);
					continue;
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
	
	AbstractSyntaxTree.ConditionNode parseCondition(String condition) throws IOException {
		return parseCondition(condition, null, 0);
	}
	private AbstractSyntaxTree.ConditionNode parseCondition(String condition, StringBuilder tokensLeft, int currentOperatorPrecedence) throws IOException {
		if(condition == null)
			return null;
		
		condition = condition.trim();
		
		AbstractSyntaxTree.ConditionNode.Operator operator = null;
		List<AbstractSyntaxTree.Node> leftNodes = new ArrayList<>();
		AbstractSyntaxTree.Node rightNode = null;
		
		StringBuilder whitespaces = new StringBuilder();
		
		StringBuilder builder = new StringBuilder();
		while(condition.length() > 0) {
			//Ignore whitespaces between operators
			if(LangPatterns.matches(condition, LangPatterns.PARSING_LEADING_WHITSPACE)) {
				if(condition.length() == 1)
					break;
				
				whitespaces.append(condition.charAt(0));
				condition = condition.substring(1);
				
				continue;
			}
			
			//Unescaping
			if(condition.startsWith("\\")) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				if(condition.length() == 1)
					break;
				
				leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(condition.charAt(1)));
				
				if(condition.length() == 2)
					break;
				
				condition = condition.substring(2);
				
				continue;
			}
			
			if(condition.startsWith("(")) {
				int endIndex = LangUtils.getIndexOfMatchingBracket(condition, 0, Integer.MAX_VALUE, '(', ')');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
					
					break;
				}
				
				//Ignore "()" if something was before (=> "Escaped" "()") -> Add "(" to builder (below outer if)
				if(builder.length() == 0) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					leftNodes.add(parseCondition(condition.substring(1, endIndex), null, 0));
					condition = condition.substring(endIndex + 1);
					
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
					
					leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(condition.charAt(0)));
					condition = condition.substring(1);
					
					continue;
				}
			}else if(condition.startsWith("!==") || condition.startsWith("!=") || condition.startsWith("===") || condition.startsWith("==") || condition.startsWith("<=") ||
			condition.startsWith(">=") || condition.startsWith("<") || condition.startsWith(">")) {
				AbstractSyntaxTree.ConditionNode.Operator oldOperator = operator;
				
				int operatorLength = 2;
				if(condition.startsWith("!==")) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.ConditionNode.Operator.STRICT_NOT_EQUALS;
				}else if(condition.startsWith("!=")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.NOT_EQUALS;
				}else if(condition.startsWith("===")) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.ConditionNode.Operator.STRICT_EQUALS;
				}else if(condition.startsWith("==")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.EQUALS;
				}else if(condition.startsWith("<=")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.LESS_THAN_OR_EQUALS;
				}else if(condition.startsWith(">=")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.GREATER_THAN_OR_EQUALS;
				}else if(condition.startsWith("<")) {
					operatorLength = 1;
					operator = AbstractSyntaxTree.ConditionNode.Operator.LESS_THAN;
				}else if(condition.startsWith(">")) {
					operatorLength = 1;
					operator = AbstractSyntaxTree.ConditionNode.Operator.GREATER_THAN;
				}
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(condition.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(condition.length() == operatorLength) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(condition);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.ConditionNode node = parseCondition(condition.substring(operatorLength), innerTokensLeft, operator.getPrecedence());
				condition = innerTokensLeft.toString();
				
				if(condition.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.ConditionNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(condition.startsWith("&&")) {
				AbstractSyntaxTree.ConditionNode.Operator oldOperator = operator;
				
				operator = AbstractSyntaxTree.ConditionNode.Operator.AND;
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(condition.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(condition.length() == 2) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(condition);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.ConditionNode node = parseCondition(condition.substring(2), innerTokensLeft, operator.getPrecedence());
				condition = innerTokensLeft.toString();
				
				if(condition.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.ConditionNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(condition.startsWith("||")) {
				AbstractSyntaxTree.ConditionNode.Operator oldOperator = operator;
				
				operator = AbstractSyntaxTree.ConditionNode.Operator.OR;
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(condition.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(condition.length() == 2) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(condition);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.ConditionNode node = parseCondition(condition.substring(2), innerTokensLeft, operator.getPrecedence());
				condition = innerTokensLeft.toString();
				
				if(condition.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.ConditionNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(condition.startsWith("!")) {
				//Ignore NOT operator if something was before (=> "Escaped" NOT) -> Add "!" to builder (below outer if)
				if(builder.length() == 0 && leftNodes.size() == 0) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = AbstractSyntaxTree.ConditionNode.Operator.NOT;
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.ConditionNode node = parseCondition(condition.substring(1), innerTokensLeft, operator.getPrecedence());
					condition = innerTokensLeft.toString();
					
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					leftNodes.add(new AbstractSyntaxTree.ConditionNode(innerRightNode, operator));
					operator = null;
					
					if(condition.isEmpty()) {
						break;
					}else {
						continue;
					}
				}else {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}
			
			//Function calls
			if(LangPatterns.matches(condition, LangPatterns.PARSING_FUNCTION_CALL)) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				int parameterStartIndex = condition.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(condition, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
					
					break;
				}
				
				String functionCall = condition.substring(0, parameterEndIndex + 1);
				condition = condition.substring(parameterEndIndex + 1);
				
				String functionName = functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				leftNodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
				continue;
			}
			
			if(whitespaces.length() > 0) {
				builder.append(whitespaces.toString());
				whitespaces.delete(0, whitespaces.length());
			}
			
			char c = condition.charAt(0);
			builder.append(c);
			if(condition.length() == 1)
				break;
			
			condition = condition.substring(1);
		}
		
		if(whitespaces.length() > 0) {
			builder.append(whitespaces.toString());
			whitespaces.delete(0, whitespaces.length());
		}
		
		//Parse value
		if(builder.length() > 0) {
			leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
		}
		
		if(operator == null)
			operator = AbstractSyntaxTree.ConditionNode.Operator.NON;
		
		AbstractSyntaxTree.Node leftNode;
		if(leftNodes.size() == 1)
			leftNode = leftNodes.get(0);
		else
			leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
		
		if(operator.isUnary())
			return new AbstractSyntaxTree.ConditionNode(leftNode, operator);
		
		return new AbstractSyntaxTree.ConditionNode(leftNode, rightNode, operator);
	}
	
	private AbstractSyntaxTree.MathNode parseMathExpr(String mathExpr) throws IOException {
		return parseMathExpr(mathExpr, null, 0);
	}
	private AbstractSyntaxTree.MathNode parseMathExpr(String mathExpr, StringBuilder tokensLeft, int currentOperatorPrecedence) throws IOException {
		if(mathExpr == null)
			return null;
		
		mathExpr = mathExpr.trim();
		
		AbstractSyntaxTree.MathNode.Operator operator = null;
		List<AbstractSyntaxTree.Node> leftNodes = new ArrayList<>();
		AbstractSyntaxTree.Node rightNode = null;
		
		StringBuilder whitespaces = new StringBuilder();
		
		StringBuilder builder = new StringBuilder();
		while(mathExpr.length() > 0) {
			//Ignore whitespaces between operators
			if(LangPatterns.matches(mathExpr, LangPatterns.PARSING_LEADING_WHITSPACE)) {
				if(mathExpr.length() == 1)
					break;
				
				whitespaces.append(mathExpr.charAt(0));
				mathExpr = mathExpr.substring(1);
				
				continue;
			}
			
			//Unescaping
			if(mathExpr.startsWith("\\")) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				if(mathExpr.length() == 1)
					break;
				
				leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(mathExpr.charAt(1)));
				
				if(mathExpr.length() == 2)
					break;
				
				mathExpr = mathExpr.substring(2);
				
				continue;
			}
			
			//Var pointer referencing and dereferencing
			if(LangPatterns.matches(mathExpr, LangPatterns.PARSING_STARTS_WITH_VAR_NAME_PTR_OR_DEREFERENCE)) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				builder.append('$');
				mathExpr = mathExpr.substring(1);
				
				while(mathExpr.charAt(0) == '*') {
					builder.append('*');
					mathExpr = mathExpr.substring(1);
				}
				
				int openingBracketCount = 0;
				while(mathExpr.charAt(0) == '[') {
					builder.append('[');
					mathExpr = mathExpr.substring(1);
					
					openingBracketCount++;
				}
				if(openingBracketCount > 0) {
					builder.append(mathExpr.substring(0, openingBracketCount));
					mathExpr = mathExpr.substring(openingBracketCount);
					
					int closingBracketCount = 0;
					while(mathExpr.length() > 0 && mathExpr.charAt(0) == ']' && closingBracketCount < openingBracketCount) {
						builder.append(']');
						mathExpr = mathExpr.substring(1);
						
						closingBracketCount++;
					}
				}
				
				continue;
			}
			
			if(mathExpr.startsWith("(")) {
				int endIndex = LangUtils.getIndexOfMatchingBracket(mathExpr, 0, Integer.MAX_VALUE, '(', ')');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in math expression is missing"));
					
					break;
				}
				
				//Ignore "()" if something was before (=> "Escaped" "()") -> Add "(" to builder (below outer if)
				if(builder.length() == 0) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					leftNodes.add(parseMathExpr(mathExpr.substring(1, endIndex), null, 0));
					mathExpr = mathExpr.substring(endIndex + 1);
					
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
					
					leftNodes.add(new AbstractSyntaxTree.EscapeSequenceNode(mathExpr.charAt(0)));
					mathExpr = mathExpr.substring(1);
					
					continue;
				}
			}else if(mathExpr.startsWith("[")) {
				int endIndex = LangUtils.getIndexOfMatchingBracket(mathExpr, 0, Integer.MAX_VALUE, '[', ']');
				if(endIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in math expression is missing"));
					
					break;
				}
				//Binary operator if something was before else unary operator
				if(builder.length() > 0 || leftNodes.size() > 0) {
					operator = AbstractSyntaxTree.MathNode.Operator.GET_ITEM;
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1, endIndex), null, 0);
					mathExpr = mathExpr.substring(endIndex + 1);
					if(mathExpr.isEmpty()) {
						//Add node directly if node has NON operator
						if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
							rightNode = node.getLeftSideOperand();
						else
							rightNode = node;
						
						break;
					}else {
						AbstractSyntaxTree.Node innerRightNode;
						
						//Add node directly if node has NON operator
						if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
							innerRightNode = node.getLeftSideOperand();
						else
							innerRightNode = node;
						
						AbstractSyntaxTree.Node leftNode;
						if(leftNodes.size() == 1)
							leftNode = leftNodes.get(0);
						else
							leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
						
						leftNodes.clear();
						leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
						operator = null;
						continue;
					}
				}
			}else if(mathExpr.startsWith("**")) {
				operator = AbstractSyntaxTree.MathNode.Operator.POW;
				
				//Add as value if nothing is behind "operator"
				if(mathExpr.length() == 2) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(mathExpr);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(2), innerTokensLeft, operator.getPrecedence());
				mathExpr = innerTokensLeft.toString();
				
				if(mathExpr.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(mathExpr.startsWith("*") || mathExpr.startsWith("//") || mathExpr.startsWith("/") || mathExpr.startsWith("%")) {
				AbstractSyntaxTree.MathNode.Operator oldOperator = operator;
				
				int operatorLength = 1;
				if(mathExpr.startsWith("*")) {
					operator = AbstractSyntaxTree.MathNode.Operator.MUL;
				}else if(mathExpr.startsWith("//")) {
					operatorLength = 2;
					operator = AbstractSyntaxTree.MathNode.Operator.FLOOR_DIV;
				}else if(mathExpr.startsWith("/")) {
					operator = AbstractSyntaxTree.MathNode.Operator.DIV;
				}else if(mathExpr.startsWith("%")) {
					operator = AbstractSyntaxTree.MathNode.Operator.MOD;
				}
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(mathExpr.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(mathExpr.length() == operatorLength) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(mathExpr);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(operatorLength), innerTokensLeft, operator.getPrecedence());
				mathExpr = innerTokensLeft.toString();
				
				if(mathExpr.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(mathExpr.startsWith("+") || mathExpr.startsWith("-")) {
				//Binary operator if something was before else unary operator
				if(builder.length() > 0 || leftNodes.size() > 0) {
					AbstractSyntaxTree.MathNode.Operator oldOperator = operator;
					
					if(mathExpr.startsWith("+")) {
						operator = AbstractSyntaxTree.MathNode.Operator.ADD;
					}else if(mathExpr.startsWith("-")) {
						operator = AbstractSyntaxTree.MathNode.Operator.SUB;
					}
					
					if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
						tokensLeft.append(mathExpr.trim());
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						operator = oldOperator;
						
						break;
					}
					
					//Add as value if nothing is behind "operator"
					if(mathExpr.length() == 1) {
						if(whitespaces.length() > 0) {
							builder.append(whitespaces.toString());
							whitespaces.delete(0, whitespaces.length());
						}
						
						operator = null;
						builder.append(mathExpr);
						
						break;
					}
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1), innerTokensLeft, operator.getPrecedence());
					mathExpr = innerTokensLeft.toString();
					
					if(mathExpr.isEmpty()) {
						//Add node directly if node has NON operator
						if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
							rightNode = node.getLeftSideOperand();
						else
							rightNode = node;
						
						break;
					}else {
						AbstractSyntaxTree.Node innerRightNode;
						
						//Add node directly if node has NON operator
						if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
							innerRightNode = node.getLeftSideOperand();
						else
							innerRightNode = node;
						
						AbstractSyntaxTree.Node leftNode;
						if(leftNodes.size() == 1)
							leftNode = leftNodes.get(0);
						else
							leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
						
						leftNodes.clear();
						leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
						operator = null;
						continue;
					}
				}else {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(mathExpr.startsWith("+")) {
						operator = AbstractSyntaxTree.MathNode.Operator.POS;
					}else if(mathExpr.startsWith("-")) {
						operator = AbstractSyntaxTree.MathNode.Operator.INV;
					}
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1), innerTokensLeft, operator.getPrecedence());
					mathExpr = innerTokensLeft.toString();
					
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					leftNodes.add(new AbstractSyntaxTree.MathNode(innerRightNode, operator));
					operator = null;
					
					if(mathExpr.isEmpty()) {
						break;
					}else {
						continue;
					}
				}
			}else if(mathExpr.startsWith("<<") || mathExpr.startsWith(">>>") || mathExpr.startsWith(">>")) {
				AbstractSyntaxTree.MathNode.Operator oldOperator = operator;
				
				int operatorLength = 2;
				if(mathExpr.startsWith("<<")) {
					operator = AbstractSyntaxTree.MathNode.Operator.LSHIFT;
				}else if(mathExpr.startsWith(">>>")) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.MathNode.Operator.RZSHIFT;
				}else if(mathExpr.startsWith(">>")) {
					operator = AbstractSyntaxTree.MathNode.Operator.RSHIFT;
				}
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(mathExpr.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(mathExpr.length() == operatorLength) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(mathExpr);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(operatorLength), innerTokensLeft, operator.getPrecedence());
				mathExpr = innerTokensLeft.toString();
				
				if(mathExpr.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(mathExpr.startsWith("&")) {
				//Binary operator if something was before else array
				if(builder.length() > 0 || leftNodes.size() > 0) {
					AbstractSyntaxTree.MathNode.Operator oldOperator = operator;
					
					operator = AbstractSyntaxTree.MathNode.Operator.BITWISE_AND;
					
					if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
						tokensLeft.append(mathExpr.trim());
						
						if(whitespaces.length() > 0)
							whitespaces.delete(0, whitespaces.length());
						
						operator = oldOperator;
						
						break;
					}
					
					//Add as value if nothing is behind "operator"
					if(mathExpr.length() == 1) {
						if(whitespaces.length() > 0) {
							builder.append(whitespaces.toString());
							whitespaces.delete(0, whitespaces.length());
						}
						
						operator = null;
						builder.append(mathExpr);
						
						break;
					}
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(builder.length() > 0) {
						leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
						builder.delete(0, builder.length());
					}
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1), innerTokensLeft, operator.getPrecedence());
					mathExpr = innerTokensLeft.toString();
					
					if(mathExpr.isEmpty()) {
						//Add node directly if node has NON operator
						if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
							rightNode = node.getLeftSideOperand();
						else
							rightNode = node;
						
						break;
					}else {
						AbstractSyntaxTree.Node innerRightNode;
						
						//Add node directly if node has NON operator
						if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
							innerRightNode = node.getLeftSideOperand();
						else
							innerRightNode = node;
						
						AbstractSyntaxTree.Node leftNode;
						if(leftNodes.size() == 1)
							leftNode = leftNodes.get(0);
						else
							leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
						
						leftNodes.clear();
						leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
						operator = null;
						continue;
					}
				}else {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}else if(mathExpr.startsWith("^")) {
				AbstractSyntaxTree.MathNode.Operator oldOperator = operator;
				
				operator = AbstractSyntaxTree.MathNode.Operator.BITWISE_XOR;
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(mathExpr.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(mathExpr.length() == 1) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(mathExpr);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1), innerTokensLeft, operator.getPrecedence());
				mathExpr = innerTokensLeft.toString();
				
				if(mathExpr.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(mathExpr.startsWith("|")) {
				AbstractSyntaxTree.MathNode.Operator oldOperator = operator;
				
				operator = AbstractSyntaxTree.MathNode.Operator.BITWISE_OR;
				
				if(tokensLeft != null && currentOperatorPrecedence <= operator.getPrecedence()) {
					tokensLeft.append(mathExpr.trim());
					
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					operator = oldOperator;
					
					break;
				}
				
				//Add as value if nothing is behind "operator"
				if(mathExpr.length() == 1) {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
					
					operator = null;
					builder.append(mathExpr);
					
					break;
				}
				
				if(whitespaces.length() > 0)
					whitespaces.delete(0, whitespaces.length());
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				StringBuilder innerTokensLeft = new StringBuilder();
				AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1), innerTokensLeft, operator.getPrecedence());
				mathExpr = innerTokensLeft.toString();
				
				if(mathExpr.isEmpty()) {
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						rightNode = node.getLeftSideOperand();
					else
						rightNode = node;
					
					break;
				}else {
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					AbstractSyntaxTree.Node leftNode;
					if(leftNodes.size() == 1)
						leftNode = leftNodes.get(0);
					else
						leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
					
					leftNodes.clear();
					leftNodes.add(new AbstractSyntaxTree.MathNode(leftNode, innerRightNode, operator));
					operator = null;
					continue;
				}
			}else if(mathExpr.startsWith("~") || mathExpr.startsWith("▲") || mathExpr.startsWith("▼")) {
				//Ignore unary operator if something was before (=> "Escaped" unary operator) -> Add unary operator to builder (below outer if)
				if(builder.length() == 0 && leftNodes.size() == 0) {
					if(whitespaces.length() > 0)
						whitespaces.delete(0, whitespaces.length());
					
					if(mathExpr.startsWith("~")) {
						operator = AbstractSyntaxTree.MathNode.Operator.BITWISE_NOT;
					}else if(mathExpr.startsWith("▲")) {
						operator = AbstractSyntaxTree.MathNode.Operator.INC;
					}else if(mathExpr.startsWith("▼")) {
						operator = AbstractSyntaxTree.MathNode.Operator.DEC;
					}
					
					StringBuilder innerTokensLeft = new StringBuilder();
					AbstractSyntaxTree.MathNode node = parseMathExpr(mathExpr.substring(1), innerTokensLeft, operator.getPrecedence());
					mathExpr = innerTokensLeft.toString();
					
					AbstractSyntaxTree.Node innerRightNode;
					
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.MathNode.Operator.NON)
						innerRightNode = node.getLeftSideOperand();
					else
						innerRightNode = node;
					
					leftNodes.add(new AbstractSyntaxTree.MathNode(innerRightNode, operator));
					operator = null;
					
					if(mathExpr.isEmpty()) {
						break;
					}else {
						continue;
					}
				}else {
					if(whitespaces.length() > 0) {
						builder.append(whitespaces.toString());
						whitespaces.delete(0, whitespaces.length());
					}
				}
			}
			
			//Function calls
			if(LangPatterns.matches(mathExpr, LangPatterns.PARSING_FUNCTION_CALL)) {
				if(whitespaces.length() > 0) {
					builder.append(whitespaces.toString());
					whitespaces.delete(0, whitespaces.length());
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				int parameterStartIndex = mathExpr.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(mathExpr, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket in condition is missing"));
					
					break;
				}
				
				String functionCall = mathExpr.substring(0, parameterEndIndex + 1);
				mathExpr = mathExpr.substring(parameterEndIndex + 1);
				
				String functionName = functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				leftNodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
				continue;
			}
			
			if(whitespaces.length() > 0) {
				builder.append(whitespaces.toString());
				whitespaces.delete(0, whitespaces.length());
			}
			
			char c = mathExpr.charAt(0);
			builder.append(c);
			if(mathExpr.length() == 1)
				break;
			
			mathExpr = mathExpr.substring(1);
		}
		
		if(whitespaces.length() > 0) {
			builder.append(whitespaces.toString());
			whitespaces.delete(0, whitespaces.length());
		}
		
		//Parse value
		if(builder.length() > 0) {
			leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
		}
		
		if(operator == null)
			operator = AbstractSyntaxTree.MathNode.Operator.NON;
		
		AbstractSyntaxTree.Node leftNode;
		if(leftNodes.size() == 1)
			leftNode = leftNodes.get(0);
		else
			leftNode = new AbstractSyntaxTree.ListNode(leftNodes);
		
		if(operator.isUnary())
			return new AbstractSyntaxTree.MathNode(leftNode, operator);
		
		return new AbstractSyntaxTree.MathNode(leftNode, rightNode, operator);
	}
	
	private AbstractSyntaxTree.AssignmentNode parseAssignment(String line, BufferedReader lines, boolean isInnerAssignment) throws IOException {
		if(isInnerAssignment?LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT_VAR_NAME):LangPatterns.matches(line, LangPatterns.PARSING_ASSIGNMENT_VAR_NAME_OR_TRANSLATION)) {
			String[] tokens = line.split(" = ", 2);
			
			AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(tokens[1], lines, true);
			return new AbstractSyntaxTree.AssignmentNode(parseLRvalue(tokens[0], null, false).convertToNode(),
			returnedNode != null?returnedNode:parseLRvalue(tokens[1], lines, true).convertToNode());
		}
		if(isInnerAssignment)
			return null;
		
		//Only for non multi assignments
		if(line.endsWith(" =") || LangPatterns.matches(line, LangPatterns.VAR_NAME_FULL) || LangPatterns.matches(line, LangPatterns.VAR_NAME_PTR_AND_DEREFERENCE)) {
			//Empty translation/assignment ("<var/lang> =" or "$varName")
			if(line.endsWith(" ="))
				line = line.substring(0, line.length() - 2);
			return new AbstractSyntaxTree.AssignmentNode(parseLRvalue(line, null, false).convertToNode(), new AbstractSyntaxTree.NullValueNode());
		}
		
		return null;
	}
	
	private AbstractSyntaxTree parseLine(String token, BufferedReader lines) throws IOException {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		List<AbstractSyntaxTree.Node> nodes = ast.getChildren();
		
		//If statement
		if(token.startsWith("con.") && !token.startsWith("con.condition")) {
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
		}else if(lrvalue.startsWith("%$")) {
			//Prepare "%$" for translation key (lvalue only)
			lrvalue = lrvalue.substring(1) + "\\e";
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
			if(LangPatterns.matches(token, LangPatterns.PARSING_FUNCTION_CALL)) {
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
			if(LangPatterns.matches(token, LangPatterns.PARSING_FUNCTION_CALL_PREVIOUS_VALUE)) {
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
			
			//con.condition
			if(LangPatterns.matches(token, LangPatterns.PARSING_CON_CONDITION)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int conditionStartIndex = token.indexOf('(');
				int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(token, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
				if(conditionEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in con.condition"));
					return ast;
				}
				String condition = token.substring(conditionStartIndex + 1, conditionEndIndex);
				token = token.substring(conditionEndIndex + 1);
				
				nodes.add(parseCondition(condition));
				continue;
			}
			
			//math.math
			if(LangPatterns.matches(token, LangPatterns.PARSING_MATH_MATH)) {
				clearAndParseStringBuilder(builder, nodes);
				
				int mathExprStartIndex = token.indexOf('(');
				int mathExprEndIndex = LangUtils.getIndexOfMatchingBracket(token, mathExprStartIndex, Integer.MAX_VALUE, '(', ')');
				if(mathExprEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in con.condition"));
					return ast;
				}
				String mathExpr = token.substring(mathExprStartIndex + 1, mathExprEndIndex);
				token = token.substring(mathExprEndIndex + 1);
				
				nodes.add(parseMathExpr(mathExpr));
				continue;
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
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_FUNCTION_CALL)) {
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
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_FUNCTION_CALL_PREVIOUS_VALUE)) {
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
				
				//con.condition
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_CON_CONDITION)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int conditionStartIndex = parameterList.indexOf('(');
					int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
					if(conditionEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in con.condition"));
						return ast;
					}
					String condition = parameterList.substring(conditionStartIndex + 1, conditionEndIndex);
					parameterList = parameterList.substring(conditionEndIndex + 1);
					
					nodes.add(parseCondition(condition));
					
					hasNodesFlag = true;
					continue;
				}
				
				//math.math
				if(LangPatterns.matches(parameterList, LangPatterns.PARSING_MATH_MATH)) {
					clearAndParseStringBuilder(builder, nodes);
					
					int mathExprStartIndex = parameterList.indexOf('(');
					int mathExprEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, mathExprStartIndex, Integer.MAX_VALUE, '(', ')');
					if(mathExprEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH, "Bracket is missing in con.condition"));
						return ast;
					}
					String mathExpr = parameterList.substring(mathExprStartIndex + 1, mathExprEndIndex);
					parameterList = parameterList.substring(mathExprEndIndex + 1);
					
					nodes.add(parseMathExpr(mathExpr));
					
					hasNodesFlag = true;
					continue;
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
		BRACKET_MISMATCH (-1, "Bracket mismatch"),
		CONDITION_MISSING(-2, "If statement condition missing"),
		EOF              (-3, "End of file was reached early"),
		INVALID_CON_PART (-4, "Invalid statement part for conditional statement");
		
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