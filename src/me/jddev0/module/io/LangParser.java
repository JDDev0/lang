package me.jddev0.module.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * IO-Module<br>
 * Parsing of lang files into an AST structure for LangInterpreter
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class LangParser {
	private String currentLine;
	
	public static String escapeString(String str) {
		if(str == null)
			return null;
		
		return str.replace("\\", "\\\\").replace("\0", "\\0").replace("\n", "\\n").replace("\r", "\\r").
		replace("\f", "\\f").replace(" ", "\s").replace("\t", "\\t").replace("$", "\\$").replace("&", "\\&").
		replace("#", "\\#").replace(",", "\\,").replace("(", "\\(").replace(")", "\\)").replace("{", "\\{").
		replace("}", "\\}").
		
		replace("fp.", "fp\\!.").replace("func.", "func\\!.").replace("return", "retur\\!n").replace("=", "=\\!");
	}
	
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
						line = line.substring(0, startIndex) + escapeString(line.substring(startIndex + 3, endIndex)) + line.substring(endIndex + 3);
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
								line = line + escapeString(lineTmp.toString()) + lineTmpString.substring(endIndex + 3);
								
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
	
	public AbstractSyntaxTree.ConditionNode parseCondition(String condition) throws IOException {
		if(condition == null)
			return null;
		
		AbstractSyntaxTree.ConditionNode.Operator operator = null;
		List<AbstractSyntaxTree.Node> leftNodes = new ArrayList<>();
		AbstractSyntaxTree.Node rightNode = null;
		
		StringBuilder builder = new StringBuilder();
		while(condition.length() > 0) {
			//Ignore whitspaces
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
				int endIndex = getIndexOfMatchingBracket(condition, 0, Integer.MAX_VALUE, '(', ')');
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
				int parameterEndIndex = getIndexOfMatchingBracket(condition, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
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
		if(isInnerAssignment?line.matches("(\\$|&|fp\\.)\\w+ = .*"):line.matches("((\\$|&|fp\\.)\\w+||(\\$\\[+\\w+\\]+)||(\\w|\\.||\\$||\\\\||%||-)+) = .*")) {
			String[] tokens = line.split(" = ", 2);
			
			AbstractSyntaxTree.AssignmentNode returnedNode = parseAssignment(tokens[1], lines, true);
			return new AbstractSyntaxTree.AssignmentNode(parseLRvalue(tokens[0], null, false).convertToNode(),
			returnedNode != null?returnedNode:parseLRvalue(tokens[1], lines, true).convertToNode());
		}
		if(isInnerAssignment)
			return null;
		
		//Only for non multi assignments
		if(line.endsWith(" =") || line.matches("(\\$|&|fp\\.)\\w+") || line.matches("\\$\\[+\\w+\\]+")) {
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
		if(token.startsWith("con.")) {
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
						int conditionEndIndex = getIndexOfMatchingBracket(ifStatement, conditionStartIndex, Integer.MAX_VALUE, '(', ')');
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
					int parameterListEndIndex = getIndexOfMatchingBracket(lrvalue, 0, Integer.MAX_VALUE, '(', ')');
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
				int parameterEndIndex = getIndexOfMatchingBracket(token, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
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
			//Function call of returned value
			if(token.matches("\\(.*\\).*")) {
				clearAndParseStringBuilder(builder, nodes);
				
				int parameterEndIndex = getIndexOfMatchingBracket(token, 0, Integer.MAX_VALUE, '(', ')');
				if(parameterEndIndex != -1) {
					String functionCall = token.substring(0, parameterEndIndex + 1);
					token = token.substring(parameterEndIndex + 1);
					
					String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
					
					nodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode(parseFunctionParameterList(functionParameterList, false).getChildren()));
					continue;
				}
			}
			
			//VarPtr
			if(token.matches("\\$\\[+\\w+\\]+.*")) {
				clearAndParseStringBuilder(builder, nodes);
				
				int endIndex = getIndexOfMatchingBracket(token, 1, Integer.MAX_VALUE, '[', ']');
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
			if(builder.toString().matches("(\\$|&|fp\\.|func\\.|linker\\.)\\w+") && token.matches("\\W.*")) {
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
		if(token.matches("(\\$|&|fp\\.|func\\.|linker\\.)\\w+")) {
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
				Float floatNumber = Float.parseFloat(token);
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
					int parameterEndIndex = getIndexOfMatchingBracket(parameterList, parameterStartIndex, Integer.MAX_VALUE, '(', ')');
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
					
					int parameterEndIndex = getIndexOfMatchingBracket(parameterList, 0, Integer.MAX_VALUE, '(', ')');
					if(parameterEndIndex != -1) {
						String functionCall = parameterList.substring(0, parameterEndIndex + 1);
						parameterList = parameterList.substring(parameterEndIndex + 1);
						
						String functionParameterList = functionCall.substring(1, functionCall.length() - 1);
						
						nodes.add(new AbstractSyntaxTree.FunctionCallPreviousNodeValueNode(parseFunctionParameterList(functionParameterList, false).getChildren()));
						
						hasNodesFlag = true;
						continue;
					}
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
				if(parameterList.matches("\\$\\[+\\w+\\]+.*")) {
					if(builder.length() > 0)
						clearAndParseStringBuilder(builder, nodes);
					
					int endIndex = getIndexOfMatchingBracket(parameterList, 1, Integer.MAX_VALUE, '[', ']');
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
				if(builder.toString().matches("(\\$|&)\\w+") && parameterList.matches("\\W.*"))
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
	
	private int getIndexOfMatchingBracket(String string, int startIndex, int endIndex, char openedBracket, char closedBracket) {
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
	
	public static final class AbstractSyntaxTree implements Iterable<AbstractSyntaxTree.Node> {
		private final List<Node> nodes;
		
		public AbstractSyntaxTree() {
			nodes = new ArrayList<>();
		}
		
		public void addChild(Node node) {
			nodes.add(node);
		}
		
		public List<Node> getChildren() {
			return nodes;
		}
		
		public Node convertToNode() {
			if(nodes.size() == 1)
				return nodes.get(0);
			return new AbstractSyntaxTree.ListNode(nodes);
		}
		
		@Override
		public Iterator<AbstractSyntaxTree.Node> iterator() {
			return nodes.iterator();
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("AST: Children: {\n");
			nodes.forEach(node -> {
				String[] tokens = node.toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
			});
			builder.append("}\n");
			
			return builder.toString();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof AbstractSyntaxTree))
				return false;
			
			AbstractSyntaxTree that = (AbstractSyntaxTree)obj;
			return this.nodes.equals(that.nodes);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(nodes);
		}
		
		public static interface Node extends Iterable<Node> {
			public List<Node> getChildren();
			public NodeType getNodeType();
			
			@Override
			public default Iterator<AbstractSyntaxTree.Node> iterator() {
				return getChildren().iterator();
			}
		}
		
		public static final class ListNode implements Node {
			private final List<Node> nodes;
			
			public ListNode(List<Node> nodes) {
				this.nodes = new ArrayList<>(nodes);
			}
			
			@Override
			public List<Node> getChildren() {
				return nodes;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.LIST;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("ListNode: Children: {\n");
				nodes.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ListNode))
					return false;
				
				ListNode that = (ListNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && that.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.nodes);
			}
		}
		
		//Is only super class for other nodes
		public static class ChildlessNode implements Node {
			private final List<Node> nodes;
			
			public ChildlessNode() {
				this.nodes = new ArrayList<>(0);
			}
			
			@Override
			public List<Node> getChildren() {
				return new ArrayList<>(nodes);
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.GENERAL;
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ChildlessNode))
					return false;
				
				ListNode that = (ListNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && that.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.nodes);
			}
		}
		
		public static final class ParsingErrorNode implements Node {
			private final List<Node> nodes;
			private final ParsingError error;
			
			public ParsingErrorNode(ParsingError error) {
				this.nodes = new ArrayList<>(0);
				
				this.error = error;
			}
			
			@Override
			public List<Node> getChildren() {
				return nodes;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.PARSING_ERROR;
			}
			
			public ParsingError getError() {
				return error;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("ParsingErrorNode: Error: \"");
				builder.append(error);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ParsingErrorNode))
					return false;
				
				ParsingErrorNode that = (ParsingErrorNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.error == that.error && that.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.error, this.nodes);
			}
		}
		
		public static final class AssignmentNode implements Node {
			private final List<Node> nodes;
			
			public AssignmentNode(Node lvalue, Node rvalue) {
				nodes = new ArrayList<>(2);
				nodes.add(lvalue);
				nodes.add(rvalue);
			}
			
			@Override
			public List<Node> getChildren() {
				return nodes;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.ASSIGNMENT;
			}
			
			public Node getLvalue() {
				return nodes.get(0);
			}
			
			public Node getRvalue() {
				return nodes.get(1);
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("AssignmentNode: lvalue: {\n");
				String[] tokens = getLvalue().toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
				builder.append("}, rvalue: {\n");
				tokens = getRvalue().toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof AssignmentNode))
					return false;
				
				AssignmentNode that = (AssignmentNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && that.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.nodes);
			}
		}
		
		public static final class EscapeSequenceNode extends ChildlessNode {
			private final char c;
			
			public EscapeSequenceNode(char c) {
				this.c = c;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.ESCAPE_SEQUENCE;
			}
			
			public char getEscapeSequenceChar() {
				return c;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("EscapeNode: Char: \"");
				builder.append(c);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof EscapeSequenceNode))
					return false;
				
				EscapeSequenceNode that = (EscapeSequenceNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.c == that.c;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.c);
			}
		}
		
		/**
		 * Unprocessed name of variable (Could be more than variable name)<br>
		 * e.g.: $var = ABC<br>
		 *       $test = $vars<br> #"$vars" would be in the variable name node although only "$var" is the variable name
		 */
		public static final class UnprocessedVariableNameNode extends ChildlessNode {
			private final String variableName;
			
			public UnprocessedVariableNameNode(String variableName) {
				this.variableName = variableName;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.UNPROCESSED_VARIABLE_NAME;
			}
			
			public String getVariableName() {
				return variableName;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("UnprocessedVariableNameNode: VariableName: \"");
				builder.append(variableName);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof UnprocessedVariableNameNode))
					return false;
				
				UnprocessedVariableNameNode that = (UnprocessedVariableNameNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.variableName.equals(that.variableName);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.variableName);
			}
		}
		
		public static final class VariableNameNode extends ChildlessNode {
			private final String variableName;
			
			public VariableNameNode(String variableName) {
				this.variableName = variableName;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.VARIABLE_NAME;
			}
			
			public String getVariableName() {
				return variableName;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("VariableNameNode: VariableName: \"");
				builder.append(variableName);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof VariableNameNode))
					return false;
				
				VariableNameNode that = (VariableNameNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.variableName.equals(that.variableName);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.variableName);
			}
		}
		
		/**
		 * Will store an "," with whitespaces if needed
		 */
		public static class ArgumentSeparatorNode extends ChildlessNode {
			private final String originalText;
			
			public ArgumentSeparatorNode(String originalText) {
				this.originalText = originalText;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.ARGUMENT_SEPARATOR;
			}
			
			public String getOriginalText() {
				return originalText;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("ArgumentSeparatorNode: OriginalText: \"");
				builder.append(originalText);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ArgumentSeparatorNode))
					return false;
				
				ArgumentSeparatorNode that = (ArgumentSeparatorNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.originalText.equals(originalText);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), originalText);
			}
		}
		
		public static final class FunctionCallNode implements Node {
			private final List<Node> argumentList;
			private final String functionName;
			
			public FunctionCallNode(List<Node> argumentList, String functionName) {
				this.argumentList = new ArrayList<>(argumentList);
				this.functionName = functionName;
			}
			
			@Override
			public List<Node> getChildren() {
				return argumentList;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.FUNCTION_CALL;
			}
			
			public String getFunctionName() {
				return functionName;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("FunctionCallNode: FunctionName: \"");
				builder.append(functionName);
				builder.append("\", ParameterList: {\n");
				argumentList.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof FunctionCallNode))
					return false;
				
				FunctionCallNode that = (FunctionCallNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.functionName.equals(that.functionName) && this.argumentList.equals(that.argumentList);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.functionName, this.argumentList);
			}
		}
		
		public static final class FunctionCallPreviousNodeValueNode implements Node {
			private final List<Node> argumentList;
			
			public FunctionCallPreviousNodeValueNode(List<Node> argumentList) {
				this.argumentList = new ArrayList<>(argumentList);
			}
			
			@Override
			public List<Node> getChildren() {
				return argumentList;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.FUNCTION_CALL_PREVIOUS_NODE_VALUE;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("FunctionCallPreviousNodeValueNode: ArgumentList: {\n");
				argumentList.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof FunctionCallPreviousNodeValueNode))
					return false;
				
				FunctionCallPreviousNodeValueNode that = (FunctionCallPreviousNodeValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.argumentList.equals(that.argumentList);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.argumentList);
			}
		}
		
		public static final class FunctionDefinitionNode implements Node {
			private final List<Node> parameterList;
			private final AbstractSyntaxTree functionBody;
			
			public FunctionDefinitionNode(List<Node> parameterList, AbstractSyntaxTree functionBody) {
				this.parameterList = new ArrayList<>(parameterList);
				this.functionBody = functionBody;
			}
			
			@Override
			public List<Node> getChildren() {
				return parameterList;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.FUNCTION_DEFINITION;
			}
			
			public AbstractSyntaxTree getFunctionBody() {
				return functionBody;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("FunctionDefinitionNode: ParameterList: {\n");
				parameterList.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}, FunctionBody: {\n");
				String[] tokens = functionBody.toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof FunctionDefinitionNode))
					return false;
				
				FunctionDefinitionNode that = (FunctionDefinitionNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.parameterList.equals(that.parameterList) && this.functionBody.equals(that.functionBody);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.parameterList, this.functionBody);
			}
		}
		
		public static final class ConditionNode implements Node {
			private final List<Node> nodes;
			private final Operator operator;
			
			public ConditionNode(Node leftSideOperand, Node rightSideOperand, Operator operator) {
				if(operator.isUnary())
					throw new IllegalStateException("NOT and NON operators have only node");
				
				nodes = new ArrayList<>(2);
				nodes.add(leftSideOperand);
				nodes.add(rightSideOperand);
				
				this.operator = operator;
			}
			
			/**
			 * For NOT and NON (true or false values) operator
			 */
			public ConditionNode(Node operand, Operator operator) {
				if(!operator.isUnary())
					throw new IllegalStateException("Only NOT and NON operators are unary");
				
				nodes = new ArrayList<>(1);
				nodes.add(operand);
				
				this.operator = operator;
			}
			
			@Override
			public List<Node> getChildren() {
				return nodes;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.CONDITION;
			}
			
			public Node getLeftSideOperand() {
				return nodes.get(0);
			}
			
			public Node getRightSideOperand() {
				if(nodes.size() < 2)
					throw new IllegalStateException("NOT and NON operators aren't binary");
				
				return nodes.get(1);
			}
			
			public Operator getOperator() {
				return operator;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("ConditionNode: Operator: \"");
				builder.append(operator);
				builder.append("\", Operands: {\n");
				nodes.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ConditionNode))
					return false;
				
				ConditionNode that = (ConditionNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.operator.equals(that.operator) && this.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.operator, this.nodes);
			}
			
			public static enum Operator {
				NON("", true), NOT("!", true), AND("&&"), OR("||"), EQUALS("=="), NOT_EQUALS("!="), STRICT_EQUALS("==="), STRICT_NOT_EQUALS("!=="), LESS_THAN("<"), GREATER_THAN(">"),
				LESS_THAN_OR_EQUALS("<="), GREATER_THAN_OR_EQUALS(">=");
				
				private final String symbol;
				private final boolean unary;
				
				private Operator(String symbol, boolean onlyOneNode) {
					this.symbol = symbol;
					this.unary = onlyOneNode;
				}
				private Operator(String symbol) {
					this(symbol, false);
				}
				
				public String getSymbol() {
					return symbol;
				}
				
				public boolean isUnary() {
					return unary;
				}
			}
		}
		
		//Is only super class for other nodes
		public static class IfStatementPartNode extends ChildlessNode {
			private final AbstractSyntaxTree ifBody;
			
			public IfStatementPartNode(AbstractSyntaxTree ifBody) {
				this.ifBody = ifBody;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.GENERAL;
			}
			
			public AbstractSyntaxTree getIfBody() {
				return ifBody;
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof IfStatementPartNode))
					return false;
				
				IfStatementPartNode that = (IfStatementPartNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.ifBody.equals(that.ifBody);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.ifBody);
			}
		}
		
		public static final class IfStatementPartIfNode extends IfStatementPartNode {
			private final ConditionNode condition;
			
			public IfStatementPartIfNode(AbstractSyntaxTree ifBody, ConditionNode condition) {
				super(ifBody);
				
				this.condition = condition;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.IF_STATEMENT_PART_IF;
			}
			
			public ConditionNode getCondition() {
				return condition;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("IfStatementPartIfNode: Condition: {\n");
				String[] tokens = condition.toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
				builder.append("}, IfBody: {\n");
				tokens = getIfBody().toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof IfStatementPartIfNode))
					return false;
				
				IfStatementPartIfNode that = (IfStatementPartIfNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.condition.equals(that.condition) && this.getIfBody().equals(that.getIfBody());
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.condition, this.getIfBody());
			}
		}
		
		public static final class IfStatementPartElseNode extends IfStatementPartNode {
			public IfStatementPartElseNode(AbstractSyntaxTree ifBody) {
				super(ifBody);
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.IF_STATEMENT_PART_ELSE;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("IfStatementPartElseNode: IfBody: {\n");
				String[] tokens = getIfBody().toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof IfStatementPartElseNode))
					return false;
				
				IfStatementPartElseNode that = (IfStatementPartElseNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.getIfBody().equals(that.getIfBody());
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.getIfBody());
			}
		}
		
		public static final class IfStatementNode implements Node {
			private final List<IfStatementPartNode> nodes;
			
			public IfStatementNode(List<IfStatementPartNode> nodes) {
				this.nodes = nodes;
			}
			
			@Override
			public List<Node> getChildren() {
				return new ArrayList<>(nodes);
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.IF_STATEMENT;
			}
			
			public List<IfStatementPartNode> getIfStatementPartNodes() {
				return nodes;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("IfStatementNode: Children: {\n");
				nodes.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof IfStatementNode))
					return false;
				
				IfStatementNode that = (IfStatementNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.nodes);
			}
		}
		
		public static final class ReturnNode implements Node {
			private final List<Node> nodes;
			
			public ReturnNode(Node returnValue) {
				nodes = new ArrayList<>(1);
				nodes.add(returnValue);
			}
			
			public ReturnNode() {
				nodes = new ArrayList<>(0);
			}
			
			@Override
			public List<Node> getChildren() {
				return nodes;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.RETURN;
			}
			
			/**
			 * @return Returns null for return without return value
			 */
			public Node getReturnValue() {
				if(nodes.size() == 1)
					return nodes.get(0);
				
				return null;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("ReturnNode: Children: {\n");
				nodes.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
				builder.append("}\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ReturnNode))
					return false;
				
				ReturnNode that = (ReturnNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.nodes.equals(that.nodes);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), this.nodes);
			}
		}
		
		//Is only super class for other nodes
		public static class ValueNode extends ChildlessNode {
			@Override
			public NodeType getNodeType() {
				return NodeType.GENERAL;
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof ValueNode))
					return false;
				
				ValueNode that = (ValueNode)obj;
				return this.getNodeType().equals(that.getNodeType());
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType());
			}
		}
		
		public static final class IntValueNode extends ValueNode {
			private final int i;
			
			public IntValueNode(int i) {
				this.i = i;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.INT_VALUE;
			}
			
			public int getInt() {
				return i;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("IntValueNode: Value: \"");
				builder.append(i);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof IntValueNode))
					return false;
				
				IntValueNode that = (IntValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.i == that.i;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), i);
			}
		}
		
		public static final class LongValueNode extends ValueNode {
			private final long l;
			
			public LongValueNode(long l) {
				this.l = l;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.LONG_VALUE;
			}
			
			public long getLong() {
				return l;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("LongValueNode: Value: \"");
				builder.append(l);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof LongValueNode))
					return false;
				
				LongValueNode that = (LongValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.l == that.l;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), l);
			}
		}
		
		public static final class FloatValueNode extends ValueNode {
			private final float f;
			
			public FloatValueNode(float f) {
				this.f = f;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.FLOAT_VALUE;
			}
			
			public float getFloat() {
				return f;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("FloatValueNode: Value: \"");
				builder.append(f);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof FloatValueNode))
					return false;
				
				FloatValueNode that = (FloatValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.f == that.f;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), f);
			}
		}
		
		public static final class DoubleValueNode extends ValueNode {
			private final double d;
			
			public DoubleValueNode(double d) {
				this.d = d;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.DOUBLE_VALUE;
			}
			
			public double getDouble() {
				return d;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("DoubleValueNode: Value: \"");
				builder.append(d);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof DoubleValueNode))
					return false;
				
				DoubleValueNode that = (DoubleValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.d == that.d;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), d);
			}
		}
		
		public static final class CharValueNode extends ValueNode {
			private final char c;
			
			public CharValueNode(char c) {
				this.c = c;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.CHAR_VALUE;
			}
			
			public char getChar() {
				return c;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("CharValueNode: Value: \"");
				builder.append(c);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof CharValueNode))
					return false;
				
				CharValueNode that = (CharValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.c == that.c;
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), c);
			}
		}
		
		public static class TextValueNode extends ValueNode {
			private final String text;
			
			public TextValueNode(String text) {
				this.text = text;
			}
			
			@Override
			public NodeType getNodeType() {
				return NodeType.TEXT_VALUE;
			}
			
			public String getText() {
				return text;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("TextValueNode: Value: \"");
				builder.append(text);
				builder.append("\"\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof TextValueNode))
					return false;
				
				TextValueNode that = (TextValueNode)obj;
				return this.getNodeType().equals(that.getNodeType()) && this.text.equals(that.text);
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType(), text);
			}
		}
		
		public static final class NullValueNode extends ValueNode {
			@Override
			public NodeType getNodeType() {
				return NodeType.NULL_VALUE;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("NullValueNode\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof NullValueNode))
					return false;
				
				NullValueNode that = (NullValueNode)obj;
				return this.getNodeType().equals(that.getNodeType());
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType());
			}
		}
		
		public static final class VoidValueNode extends ValueNode {
			@Override
			public NodeType getNodeType() {
				return NodeType.VOID_VALUE;
			}
			
			@Override
			public String toString() {
				StringBuilder builder = new StringBuilder();
				builder.append("VoidValueNode\n");
				
				return builder.toString();
			}
			
			@Override
			public boolean equals(Object obj) {
				if(this == obj)
					return true;
				
				if(obj == null)
					return false;
				
				if(!(obj instanceof VoidValueNode))
					return false;
				
				VoidValueNode that = (VoidValueNode)obj;
				return this.getNodeType().equals(that.getNodeType());
			}
			
			@Override
			public int hashCode() {
				return Objects.hash(this.getNodeType());
			}
		}
		
		public static enum NodeType {
			GENERAL, LIST, PARSING_ERROR, ASSIGNMENT, ESCAPE_SEQUENCE, UNPROCESSED_VARIABLE_NAME, VARIABLE_NAME, ARGUMENT_SEPARATOR,
			FUNCTION_CALL, FUNCTION_CALL_PREVIOUS_NODE_VALUE, FUNCTION_DEFINITION, CONDITION, IF_STATEMENT_PART_IF, IF_STATEMENT_PART_ELSE,
			IF_STATEMENT, RETURN, INT_VALUE, LONG_VALUE, FLOAT_VALUE, DOUBLE_VALUE, CHAR_VALUE, TEXT_VALUE, NULL_VALUE, VOID_VALUE
		}
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