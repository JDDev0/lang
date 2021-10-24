package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * IO-Module<br>
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
			while(line.contains("{{{") || line.endsWith("\\")) {
				if(line.contains("{{{")) { //Multiline text
					int startIndex = line.indexOf("{{{");
					if(line.contains("}}}")) {
						int endIndex = line.indexOf("}}}");
						line = line.substring(0, startIndex) + LangUtils.escapeString(line.substring(startIndex + 3, endIndex)) + line.substring(endIndex + 3);
					}else {
						//Multiple lines
						lineTmp.delete(0, lineTmp.length());
						lineTmp.append(line.substring(startIndex + 3));
						line = line.substring(0, startIndex);
						String lineTmpString;
						while(true) {
							if(!lines.ready()) {
								ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF));
								return ast;
							}
							
							lineTmpString = lines.readLine();
							if(lineTmpString == null) {
								ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF));
								return ast;
							}
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
						ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF));
						return ast;
					}
					String lineTmpString = lines.readLine();
					if(lineTmpString == null) {
						ast.addChild(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF));
						return ast;
					}
					line += lineTmpString;
				}
			}
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
		if(condition == null)
			return null;
		
		AbstractSyntaxTree.ConditionNode.Operator operator = null;
		List<AbstractSyntaxTree.Node> leftNodes = new ArrayList<>();
		AbstractSyntaxTree.Node rightNode = null;
		
		StringBuilder builder = new StringBuilder();
		while(condition.length() > 0) {
			//Ignore whitespaces
			if(condition.matches("\\s.*")) {
				if(condition.length() == 1)
					break;
				
				condition = condition.substring(1);
				
				continue;
			}
			
			//Unescaping
			if(condition.startsWith("\\")) {
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
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
					
					break;
				}
				
				//Ignore "()" if something was before (=> "Escaped" "()") -> Add "(" to builder (below outer if)
				if(builder.length() == 0) {
					leftNodes.add(parseCondition(condition.substring(1, endIndex)));
					condition = condition.substring(endIndex + 1);
					
					continue;
				}
			}else if(condition.startsWith("!==") || condition.startsWith("!=") || condition.startsWith("&&") || condition.startsWith("||") || condition.startsWith("===") ||
			condition.startsWith("==") || condition.startsWith("<=") || condition.startsWith(">=") || condition.startsWith("<") || condition.startsWith(">")) {
				int operatorLength = 2;
				if(condition.startsWith("!==")) {
					operatorLength = 3;
					operator = AbstractSyntaxTree.ConditionNode.Operator.STRICT_NOT_EQUALS;
				}else if(condition.startsWith("!=")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.NOT_EQUALS;
				}else if(condition.startsWith("&&")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.AND;
				}else if(condition.startsWith("||")) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.OR;
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
				
				//Add as value if nothing is behind "operator"
				if(condition.length() == operatorLength) {
					operator = null;
					builder.append(condition);
					
					break;
				}
				
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				AbstractSyntaxTree.ConditionNode node = parseCondition(condition.substring(operatorLength));
				
				//Add node directly if node has NON operator
				if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
					rightNode = node.getLeftSideOperand();
				else
					rightNode = node;
				
				break;
			}else if(condition.startsWith("!")) {
				//Ignore NOT operator if something was before (=> "Escaped" NOT) -> Add "!" to builder (below outer if)
				if(builder.length() == 0 && leftNodes.size() == 0) {
					operator = AbstractSyntaxTree.ConditionNode.Operator.NOT;
					
					AbstractSyntaxTree.ConditionNode node = parseCondition(condition.substring(1));
					//Add node directly if node has NON operator
					if(node.getOperator() == AbstractSyntaxTree.ConditionNode.Operator.NON)
						leftNodes.add(node.getLeftSideOperand());
					else
						leftNodes.add(node);
					
					break;
				}
			}
			
			//Function calls
			if(condition.matches("(func|fp|linker)\\.\\w+\\(.*\\).*")) {
				if(builder.length() > 0) {
					leftNodes.add(parseLRvalue(builder.toString(), null, true).convertToNode());
					builder.delete(0, builder.length());
				}
				
				int parameterStartIndex = condition.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(condition, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					leftNodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
					
					break;
				}
				
				String functionCall = condition.substring(0, parameterEndIndex + 1);
				condition = condition.substring(parameterEndIndex + 1);
				
				String functionName = functionCall.substring(0, parameterStartIndex);
				String functionParameterList = functionCall.substring(parameterStartIndex + 1, functionCall.length() - 1);
				
				leftNodes.add(new AbstractSyntaxTree.FunctionCallNode(parseFunctionParameterList(functionParameterList, false).getChildren(), functionName));
				continue;
			}
			
			char c = condition.charAt(0);
			builder.append(c);
			if(condition.length() == 1)
				break;
			
			condition = condition.substring(1);
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
	
	private AbstractSyntaxTree.AssignmentNode parseAssignment(String line, BufferedReader lines, boolean isInnerAssignment) throws IOException {
		if(isInnerAssignment?line.matches("(\\$\\**|&|fp\\.)\\w+ = .*"):line.matches("((\\$\\**|&|fp\\.)\\w+||(\\$\\**\\[+\\w+\\]+)||(\\w|\\.||\\$||\\\\||%||-)+) = .*")) {
			String[] tokens = line.split(" = ", 2);
			
			AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(tokens[1], lines, true);
			return new AbstractSyntaxTree.AssignmentNode(parseLRvalue(tokens[0], null, false).convertToNode(),
			returnedNode != null?returnedNode:parseLRvalue(tokens[1], lines, true).convertToNode());
		}
		if(isInnerAssignment)
			return null;
		
		//Only for non multi assignments
		if(line.endsWith(" =") || line.matches("(\\$\\**|&|fp\\.)\\w+") || line.matches("\\$\\**\\[+\\w+\\]+")) {
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
			if(token.startsWith("con.if")) {
				List<AbstractSyntaxTree.IfStatementPartNode> ifStatmentParts = new ArrayList<>();
				
				String ifStatement = token;
				do {
					String ifCondition;
					if(ifStatement.startsWith("con.else")) {
						ifCondition = null;
					}else if(!ifStatement.contains("(") || !ifStatement.contains(")")) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.CONDITION_MISSING));
						
						ifCondition = null;
					}else {
						int conditionStartIndex = ifStatement.indexOf('(');
						int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(ifStatement, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
						if(conditionEndIndex == -1) {
							nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
							return ast;
						}
						ifCondition = ifStatement.substring(conditionStartIndex + 1, conditionEndIndex);
					}
					
					AbstractSyntaxTree ifBody = parseLines(lines);
					if(ifBody == null) {
						nodes.add(new AbstractSyntaxTree.IfStatementNode(ifStatmentParts));
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.EOF));
						
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
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
						
						return ast;
					}
					
					String functionHead = lrvalue.substring(1, parameterListEndIndex);
					List<AbstractSyntaxTree.Node> parameterList = parseFunctionParameterList(functionHead, true).getChildren();
					
					String functionBody = lrvalue.substring(parameterListEndIndex + 5);
					
					BufferedReader functionBodyReader;
					if(lrvalue.endsWith("{"))
						functionBodyReader = lines;
					else
						functionBodyReader = new BufferedReader(new StringReader(functionBody));
					nodes.add(new AbstractSyntaxTree.FunctionDefinitionNode(parameterList, parseLines(functionBodyReader)));
					
					
					return ast;
				}else if(lrvalue.matches("(func|fp|linker)\\.\\w+")) {
					//Function pointer copying
					
					nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(lrvalue));
					return ast;
				}
			}
		}else if(lrvalue.startsWith("%$")) {
			//Prepare unescaping of "%$" for lang request (lvalue only)
			lrvalue = "\\" + lrvalue.substring(1);
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
			if(token.matches("(func|fp|linker)\\.\\w+\\(.*\\).*")) {
				clearAndParseStringBuilder(builder, nodes);
				
				int parameterStartIndex = token.indexOf('(');
				int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
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
			if(token.matches("\\(.*\\).*")) {
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
			if(token.matches("con\\.condition\\(.*\\).*")) {
				clearAndParseStringBuilder(builder, nodes);
				
				int conditionStartIndex = token.indexOf('(');
				int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(token, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
				if(conditionEndIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
					return ast;
				}
				String condition = token.substring(conditionStartIndex + 1, conditionEndIndex);
				token = token.substring(conditionEndIndex + 1);
				
				nodes.add(parseCondition(condition));
				continue;
			}
			
			//VarPtr
			if(token.matches("\\$\\**\\[+\\w+\\]+.*")) {
				clearAndParseStringBuilder(builder, nodes);
				
				int endIndex = LangUtils.getIndexOfMatchingBracket(token, 1, Integer.MAX_VALUE, '[', ']');
				if(endIndex == -1) {
					nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
					return ast;
				}
				String varPtr = token.substring(0, endIndex + 1);
				token = token.substring(endIndex + 1);
				
				nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(varPtr));
				continue;
			}
			
			//Force node split
			if(token.startsWith("$")) {
				//Variable split for variable concatenation
				clearAndParseStringBuilder(builder, nodes);
			}
			if(builder.toString().matches("(\\$\\**|&|fp\\.|func\\.|linker\\.)\\w+") && token.matches("\\W.*")) {
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
		if(token.matches("(\\$\\**|&|fp\\.|func\\.|linker\\.)\\w+")) {
			nodes.add(new AbstractSyntaxTree.UnprocessedVariableNameNode(token));
			
			return;
		}
		
		if(!token.matches("(\\s.*|.*\\s)")) {
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
				if(parameterList.matches("(func|fp|linker)\\.\\w+\\(.*\\).*")) {
					clearAndParseStringBuilder(builder, nodes);
					
					int parameterStartIndex = parameterList.indexOf('(');
					int parameterEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
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
				//Function call of returned value
				if(parameterList.matches("\\(.*\\).*")) {
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
				if(parameterList.matches("con\\.condition\\(.*\\).*")) {
					clearAndParseStringBuilder(builder, nodes);
					
					int conditionStartIndex = parameterList.indexOf('(');
					int conditionEndIndex = LangUtils.getIndexOfMatchingBracket(parameterList, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
					if(conditionEndIndex == -1) {
						nodes.add(new AbstractSyntaxTree.ParsingErrorNode(ParsingError.BRACKET_MISMATCH));
						return ast;
					}
					String condition = parameterList.substring(conditionStartIndex + 1, conditionEndIndex);
					parameterList = parameterList.substring(conditionEndIndex + 1);
					
					nodes.add(parseCondition(condition));
					continue;
				}
				
				if(parameterList.matches("\\s*,.*")) {
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
					while(parameterList.length() > 0 && parameterList.matches("\\s.*")) {
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
				if(parameterList.matches("\\$\\**\\[+\\w+\\]+.*")) {
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
				
				//Force node split
				if(parameterList.startsWith("$") && builder.length() > 0)
					clearAndParseStringBuilder(builder, nodes);
				
				//Variable split after invalid character (Not [A-Za-z0-9_]
				if(builder.toString().matches("(\\$\\**|&)\\w+") && parameterList.matches("\\W.*"))
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
	
	public static enum ParsingError {
		BRACKET_MISMATCH (-1, "Bracket mismatch"),
		CONDITION_MISSING(-2, "If statement condition missing"),
		EOF              (-3, "End of file was reached early");
		
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