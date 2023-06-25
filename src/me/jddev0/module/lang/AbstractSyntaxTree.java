package me.jddev0.module.lang;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import me.jddev0.module.lang.LangParser.ParsingError;

/**
 * Lang-Module<br>
 * AbstractSyntaxTree for Lang
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public final class AbstractSyntaxTree implements Iterable<AbstractSyntaxTree.Node> {
	private final List<Node> nodes;
	
	public AbstractSyntaxTree() {
		nodes = new ArrayList<>();
	}
	
	public int getLineNumberFrom() {
		return nodes.isEmpty()?-1:nodes.get(0).getLineNumberFrom();
	}
	
	public int getLineNumberTo() {
		return nodes.isEmpty()?-1:nodes.get(nodes.size() - 1).getLineNumberTo();
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
		public int getLineNumberFrom();
		public int getLineNumberTo();
		
		@Override
		public default Iterator<AbstractSyntaxTree.Node> iterator() {
			return getChildren().iterator();
		}
	}
	
	public static final class ListNode implements Node {
		private final List<Node> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public ListNode(List<Node> nodes, int lineNumberFrom, int lineNumberTo) {
			this.nodes = new ArrayList<>(nodes);
			
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		public ListNode(List<Node> nodes) {
			this(nodes, nodes.isEmpty()?-1:nodes.get(0).getLineNumberFrom(),
					nodes.isEmpty()?-1:nodes.get(nodes.size() - 1).getLineNumberTo());
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
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ListNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Children: {\n");
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
	public static abstract class ChildlessNode implements Node {
		private final List<Node> nodes;
		protected final int lineNumberFrom;
		protected final int lineNumberTo;
		
		public ChildlessNode(int lineNumberFrom, int lineNumberTo) {
			this.nodes = new ArrayList<>(0);
			
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
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
	
	public static final class ParsingErrorNode extends ChildlessNode {
		private final ParsingError error;
		private final String message;
		
		public ParsingErrorNode(int lineNumberFrom, int lineNumberTo, ParsingError error, String message) {
			super(lineNumberFrom, lineNumberTo);
			
			this.error = error;
			this.message = message;
		}
		public ParsingErrorNode(int lineNumber, ParsingError error, String message) {
			this(lineNumber, lineNumber, error, message);
		}
		public ParsingErrorNode(int lineNumberFrom, int lineNumberTo, ParsingError error) {
			this(lineNumberFrom, lineNumberTo, error, null);
		}
		public ParsingErrorNode(int lineNumber, ParsingError error) {
			this(lineNumber, lineNumber, error, null);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.PARSING_ERROR;
		}
		
		public ParsingError getError() {
			return error;
		}
		
		public String getMessage() {
			return message;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ParsingErrorNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Error: \"");
			builder.append(error);
			builder.append("\", Message: \"");
			builder.append(message);
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
			return this.getNodeType().equals(that.getNodeType()) && this.error == that.error && this.message.equals(that.message);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.error, this.message);
		}
	}
	
	public static final class AssignmentNode implements Node {
		private final List<Node> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public AssignmentNode(Node lvalue, Node rvalue, int lineNumberFrom, int lineNumberTo) {
			nodes = new ArrayList<>(2);
			nodes.add(lvalue);
			nodes.add(rvalue);
			
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		public AssignmentNode(Node lvalue, Node rvalue) {
			this(lvalue, rvalue, lvalue.getLineNumberFrom(), rvalue.getLineNumberTo());
		}
		
		@Override
		public List<Node> getChildren() {
			return nodes;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.ASSIGNMENT;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
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
			builder.append("AssignmentNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, lvalue: {\n");
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
		
		public EscapeSequenceNode(int lineNumberFrom, int lineNumberTo, char c) {
			super(lineNumberFrom, lineNumberTo);
			
			this.c = c;
		}
		public EscapeSequenceNode(int lineNumber, char c) {
			this(lineNumber, lineNumber, c);
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
			builder.append("EscapeSequenceNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Char: \"");
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
		
		public UnprocessedVariableNameNode(int lineNumberFrom, int lineNumberTo, String variableName) {
			super(lineNumberFrom, lineNumberTo);
			
			this.variableName = variableName;
		}
		public UnprocessedVariableNameNode(int lineNumber, String variableName) {
			this(lineNumber, lineNumber, variableName);
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
			builder.append("UnprocessedVariableNameNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, VariableName: \"");
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
		private final String typeConstraint;
		
		public VariableNameNode(int lineNumberFrom, int lineNumberTo, String variableName, String typeConstraint) {
			super(lineNumberFrom, lineNumberTo);
			
			this.variableName = variableName;
			this.typeConstraint = typeConstraint;
		}
		public VariableNameNode(int lineNumber, String variableName, String typeConstraint) {
			this(lineNumber, lineNumber, variableName, typeConstraint);
		}
		public VariableNameNode(int lineNumberFrom, int lineNumberTo, String variableName) {
			this(lineNumberFrom, lineNumberTo, variableName, null);
		}
		public VariableNameNode(int lineNumber, String variableName) {
			this(lineNumber, lineNumber, variableName);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.VARIABLE_NAME;
		}
		
		public String getVariableName() {
			return variableName;
		}
		
		public String getTypeConstraint() {
			return typeConstraint;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("VariableNameNode: LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append(", VariableName: \"");
			builder.append(variableName);
			builder.append(", TypeConstraint: \"");
			builder.append(typeConstraint);
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
			return this.getNodeType().equals(that.getNodeType()) && this.variableName.equals(that.variableName) &&
					Objects.equals(this.typeConstraint, that.typeConstraint);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.variableName, this.typeConstraint);
		}
	}
	
	/**
	 * Will store an "," with whitespaces if needed
	 */
	public static final class ArgumentSeparatorNode extends ChildlessNode {
		private final String originalText;
		
		public ArgumentSeparatorNode(int lineNumberFrom, int lineNumberTo, String originalText) {
			super(lineNumberFrom, lineNumberTo);
			
			this.originalText = originalText;
		}
		public ArgumentSeparatorNode(int lineNumber, String originalText) {
			this(lineNumber, lineNumber, originalText);
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
			builder.append("ArgumentSeparatorNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, OriginalText: \"");
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
		private final int lineNumberFrom;
		private final int lineNumberTo;
		private final String functionName;
		
		public FunctionCallNode(List<Node> argumentList, int lineNumberFrom, int lineNumberTo, String functionName) {
			this.argumentList = new ArrayList<>(argumentList);
			
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
			
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
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public String getFunctionName() {
			return functionName;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("FunctionCallNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, FunctionName: \"");
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
		private final String leadingWhitespace;
		private final String trailingWhitespace;
		private final List<Node> argumentList;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public FunctionCallPreviousNodeValueNode(String leadingWhitespace, String trailingWhitespace, List<Node> argumentList, int lineNumberFrom, int lineNumberTo) {
			this.leadingWhitespace = leadingWhitespace;
			this.trailingWhitespace = trailingWhitespace;
			this.argumentList = new ArrayList<>(argumentList);
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		
		public String getLeadingWhitespace() {
			return leadingWhitespace;
		}
		
		public String getTrailingWhitespace() {
			return trailingWhitespace;
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
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("FunctionCallPreviousNodeValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, ArgumentList: {\n");
			argumentList.forEach(node -> {
				String[] tokens = node.toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
			});
			builder.append("}, LeadingWhitespace: \"");
			builder.append(leadingWhitespace);
			builder.append("\", TrailingWhitespace: \"");
			builder.append(trailingWhitespace);
			builder.append("\"\n");
			
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
			return this.getNodeType().equals(that.getNodeType()) && this.leadingWhitespace.equals(that.leadingWhitespace) && this.trailingWhitespace.equals(that.trailingWhitespace) &&
				this.argumentList.equals(that.argumentList);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.leadingWhitespace, this.trailingWhitespace, this.argumentList);
		}
	}
	
	public static final class FunctionDefinitionNode implements Node {
		private final List<Node> parameterList;
		private final String returnValueTypeConstraint;
		private final AbstractSyntaxTree functionBody;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public FunctionDefinitionNode(List<Node> parameterList, String returnValueTypeConstraint,
				AbstractSyntaxTree functionBody, int lineNumberFrom, int lineNumberTo) {
			this.parameterList = new ArrayList<>(parameterList);
			this.returnValueTypeConstraint = returnValueTypeConstraint;
			this.functionBody = functionBody;
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		
		@Override
		public List<Node> getChildren() {
			return parameterList;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.FUNCTION_DEFINITION;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public String getReturnValueTypeConstraint() {
			return returnValueTypeConstraint;
		}
		
		public AbstractSyntaxTree getFunctionBody() {
			return functionBody;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("FunctionDefinitionNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, ParameterList: {\n");
			parameterList.forEach(node -> {
				String[] tokens = node.toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
			});
			builder.append("}, ReturnValueTypeConstraint: ");
			builder.append(returnValueTypeConstraint);
			builder.append(", FunctionBody: {\n");
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
	
	//Is only super class for other nodes
	public static abstract class IfStatementPartNode extends ChildlessNode {
		private final AbstractSyntaxTree ifBody;
		
		public IfStatementPartNode(AbstractSyntaxTree ifBody, int lineNumberFrom, int lineNumberTo) {
			super(lineNumberFrom, lineNumberTo);
			
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
		private final OperationNode condition;
		
		public IfStatementPartIfNode(AbstractSyntaxTree ifBody, int lineNumberFrom, int lineNumberTo, OperationNode condition) {
			super(ifBody, lineNumberFrom, lineNumberTo);
			
			if(condition.getNodeType() != NodeType.CONDITION)
				throw new IllegalStateException("Node type is not compatible with this node");
			
			this.condition = condition;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.IF_STATEMENT_PART_IF;
		}
		
		public OperationNode getCondition() {
			return condition;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("IfStatementPartIfNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Condition: {\n");
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
		public IfStatementPartElseNode(AbstractSyntaxTree ifBody, int lineNumberFrom, int lineNumberTo) {
			super(ifBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.IF_STATEMENT_PART_ELSE;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("IfStatementPartElseNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, IfBody: {\n");
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
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public IfStatementNode(List<IfStatementPartNode> nodes, int lineNumberFrom, int lineNumberTo) {
			this.nodes = nodes;
			
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		
		@Override
		public List<Node> getChildren() {
			return new ArrayList<>(nodes);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.IF_STATEMENT;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public List<IfStatementPartNode> getIfStatementPartNodes() {
			return nodes;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("IfStatementNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Children: {\n");
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
	
	//Is only super class for other nodes
	public static abstract class LoopStatementPartNode extends ChildlessNode {
		private final AbstractSyntaxTree loopBody;
		
		public LoopStatementPartNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo) {
			super(lineNumberFrom, lineNumberTo);
			
			this.loopBody = loopBody;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.GENERAL;
		}
		
		public AbstractSyntaxTree getLoopBody() {
			return loopBody;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof LoopStatementPartNode))
				return false;
			
			LoopStatementPartNode that = (LoopStatementPartNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.loopBody.equals(that.loopBody);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.loopBody);
		}
	}
	
	public static final class LoopStatementPartLoopNode extends LoopStatementPartNode {
		public LoopStatementPartLoopNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo) {
			super(loopBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_PART_LOOP;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementPartLoopNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, LoopBody: {\n");
			String[] tokens = getLoopBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartLoopNode))
				return false;
			
			LoopStatementPartLoopNode that = (LoopStatementPartLoopNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getLoopBody().equals(that.getLoopBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getLoopBody());
		}
	}
	
	public static final class LoopStatementPartWhileNode extends LoopStatementPartNode {
		private final OperationNode condition;
		
		public LoopStatementPartWhileNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo, OperationNode condition) {
			super(loopBody, lineNumberFrom, lineNumberTo);
			
			if(condition.getNodeType() != NodeType.CONDITION)
				throw new IllegalStateException("Node type is not compatible with this node");
			
			this.condition = condition;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_PART_WHILE;
		}
		
		public OperationNode getCondition() {
			return condition;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementPartWhileNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Condition: {\n");
			String[] tokens = condition.toString().split("\\n");
			for(String token:tokens) {
				builder.append("\t");
				builder.append(token);
				builder.append("\n");
			}
			builder.append("}, LoopBody: {\n");
			tokens = getLoopBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartWhileNode))
				return false;
			
			LoopStatementPartWhileNode that = (LoopStatementPartWhileNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.condition.equals(that.condition) && this.getLoopBody().equals(that.getLoopBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.condition, this.getLoopBody());
		}
	}
	
	public static final class LoopStatementPartUntilNode extends LoopStatementPartNode {
		private final OperationNode condition;
		
		public LoopStatementPartUntilNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo, OperationNode condition) {
			super(loopBody, lineNumberFrom, lineNumberTo);
			
			if(condition.getNodeType() != NodeType.CONDITION)
				throw new IllegalStateException("Node type is not compatible with this node");
			
			this.condition = condition;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_PART_UNTIL;
		}
		
		public OperationNode getCondition() {
			return condition;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementPartUntilNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Condition: {\n");
			String[] tokens = condition.toString().split("\\n");
			for(String token:tokens) {
				builder.append("\t");
				builder.append(token);
				builder.append("\n");
			}
			builder.append("}, LoopBody: {\n");
			tokens = getLoopBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartUntilNode))
				return false;
			
			LoopStatementPartUntilNode that = (LoopStatementPartUntilNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.condition.equals(that.condition) && this.getLoopBody().equals(that.getLoopBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.condition, this.getLoopBody());
		}
	}
	
	public static final class LoopStatementPartRepeatNode extends LoopStatementPartNode {
		private final Node varPointerNode;
		private final Node repeatCountNode;
		
		public LoopStatementPartRepeatNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo, Node varPointerNode, Node repeatCountNode) {
			super(loopBody, lineNumberFrom, lineNumberTo);
			
			this.varPointerNode = varPointerNode;
			this.repeatCountNode = repeatCountNode;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_PART_REPEAT;
		}
		
		public Node getVarPointerNode() {
			return varPointerNode;
		}
		
		public Node getRepeatCountNode() {
			return repeatCountNode;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementPartRepeatNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, varPointer: {\n");
			String[] tokens = varPointerNode.toString().split("\\n");
			for(String token:tokens) {
				builder.append("\t");
				builder.append(token);
				builder.append("\n");
			}
			builder.append("}, repeatCount: {\n");
			tokens = repeatCountNode.toString().split("\\n");
			for(String token:tokens) {
				builder.append("\t");
				builder.append(token);
				builder.append("\n");
			}
			builder.append("}, LoopBody: {\n");
			tokens = getLoopBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartRepeatNode))
				return false;
			
			LoopStatementPartRepeatNode that = (LoopStatementPartRepeatNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.varPointerNode.equals(that.varPointerNode) && this.repeatCountNode.equals(that.repeatCountNode) &&
					this.getLoopBody().equals(that.getLoopBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.varPointerNode, this.repeatCountNode, this.getLoopBody());
		}
	}
	
	public static final class LoopStatementPartForEachNode extends LoopStatementPartNode {
		private final Node varPointerNode;
		private final Node compositeOrTextNode;
		
		public LoopStatementPartForEachNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo, Node varPointerNode, Node collectionOrTextNode) {
			super(loopBody, lineNumberFrom, lineNumberTo);
			
			this.varPointerNode = varPointerNode;
			this.compositeOrTextNode = collectionOrTextNode;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_PART_FOR_EACH;
		}
		
		public Node getVarPointerNode() {
			return varPointerNode;
		}
		
		public Node getCompositeOrTextNode() {
			return compositeOrTextNode;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementPartForEachNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, varPointer: {\n");
			String[] tokens = varPointerNode.toString().split("\\n");
			for(String token:tokens) {
				builder.append("\t");
				builder.append(token);
				builder.append("\n");
			}
			builder.append("}, compositeOrTextNode: {\n");
			tokens = compositeOrTextNode.toString().split("\\n");
			for(String token:tokens) {
				builder.append("\t");
				builder.append(token);
				builder.append("\n");
			}
			builder.append("}, LoopBody: {\n");
			tokens = getLoopBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartForEachNode))
				return false;
			
			LoopStatementPartForEachNode that = (LoopStatementPartForEachNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.varPointerNode.equals(that.varPointerNode) && this.compositeOrTextNode.equals(that.compositeOrTextNode) &&
					this.getLoopBody().equals(that.getLoopBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.varPointerNode, this.compositeOrTextNode, this.getLoopBody());
		}
	}
	
	public static final class LoopStatementPartElseNode extends LoopStatementPartNode {
		public LoopStatementPartElseNode(AbstractSyntaxTree loopBody, int lineNumberFrom, int lineNumberTo) {
			super(loopBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_PART_ELSE;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementPartElseNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, LoopBody: {\n");
			String[] tokens = getLoopBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartElseNode))
				return false;
			
			LoopStatementPartElseNode that = (LoopStatementPartElseNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getLoopBody().equals(that.getLoopBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getLoopBody());
		}
	}
	
	public static final class LoopStatementNode implements Node {
		private final List<LoopStatementPartNode> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public LoopStatementNode(List<LoopStatementPartNode> nodes, int lineNumberFrom, int lineNumberTo) {
			this.nodes = nodes;
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		
		@Override
		public List<Node> getChildren() {
			return new ArrayList<>(nodes);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public List<LoopStatementPartNode> getLoopStatementPartNodes() {
			return nodes;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Children: {\n");
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
			
			if(!(obj instanceof LoopStatementNode))
				return false;
			
			LoopStatementNode that = (LoopStatementNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.nodes.equals(that.nodes);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.nodes);
		}
	}
	
	public static final class LoopStatementContinueBreakStatement extends ChildlessNode {
		private final Node numberNode;
		private final boolean continueNode;
		
		public LoopStatementContinueBreakStatement(Node numberNode, int lineNumberFrom, int lineNumberTo, boolean continueNode) {
			super(lineNumberFrom, lineNumberTo);
			
			this.numberNode = numberNode;
			this.continueNode = continueNode;
		}
		public LoopStatementContinueBreakStatement(Node numberNode, int lineNumber, boolean continueNode) {
			this(numberNode, lineNumber, lineNumber, continueNode);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.LOOP_STATEMENT_CONTINUE_BREAK;
		}
		
		public Node getNumberNode() {
			return numberNode;
		}
		
		public boolean isContinueNode() {
			return continueNode;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("LoopStatementContinueBreakStatementNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, numberNode: {\n");
			if(numberNode == null) {
				builder.append("null");
			}else {
				String[] tokens = numberNode.toString().split("\\n");
				for(String token:tokens) {
					builder.append("\t");
					builder.append(token);
					builder.append("\n");
				}
			}
			builder.append("}, continueNode: ");
			builder.append(continueNode);
			builder.append("\n");
			
			return builder.toString();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof LoopStatementContinueBreakStatement))
				return false;
			
			LoopStatementContinueBreakStatement that = (LoopStatementContinueBreakStatement)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.numberNode.equals(that.numberNode) && this.continueNode == that.continueNode;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.numberNode, this.continueNode);
		}
	}
	
	//Is only super class for other nodes
	public static abstract class TryStatementPartNode extends ChildlessNode {
		private final AbstractSyntaxTree tryBody;
		
		public TryStatementPartNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			super(lineNumberFrom, lineNumberTo);
			
			this.tryBody = tryBody;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.GENERAL;
		}
		
		public AbstractSyntaxTree getTryBody() {
			return tryBody;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof TryStatementPartNode))
				return false;
			
			TryStatementPartNode that = (TryStatementPartNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.tryBody.equals(that.tryBody);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.tryBody);
		}
	}
	
	public static final class TryStatementPartTryNode extends TryStatementPartNode {
		public TryStatementPartTryNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			super(tryBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT_PART_TRY;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementPartTryNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, TryBody: {\n");
			String[] tokens = getTryBody().toString().split("\\n");
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
			
			if(!(obj instanceof TryStatementPartTryNode))
				return false;
			
			TryStatementPartTryNode that = (TryStatementPartTryNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getTryBody().equals(that.getTryBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getTryBody());
		}
	}
	
	public static final class TryStatementPartSoftTryNode extends TryStatementPartNode {
		public TryStatementPartSoftTryNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			super(tryBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT_PART_SOFT_TRY;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementPartSoftTryNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, TryBody: {\n");
			String[] tokens = getTryBody().toString().split("\\n");
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
			
			if(!(obj instanceof TryStatementPartSoftTryNode))
				return false;
			
			TryStatementPartSoftTryNode that = (TryStatementPartSoftTryNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getTryBody().equals(that.getTryBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getTryBody());
		}
	}
	
	public static final class TryStatementPartNonTryNode extends TryStatementPartNode {
		public TryStatementPartNonTryNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			super(tryBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT_PART_NON_TRY;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementPartNonTryNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, TryBody: {\n");
			String[] tokens = getTryBody().toString().split("\\n");
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
			
			if(!(obj instanceof TryStatementPartNonTryNode))
				return false;
			
			TryStatementPartNonTryNode that = (TryStatementPartNonTryNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getTryBody().equals(that.getTryBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getTryBody());
		}
	}
	
	public static final class TryStatementPartCatchNode extends TryStatementPartNode {
		private final List<Node> errors;
		
		/**
		 * Every error will be accepted
		 */
		public TryStatementPartCatchNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			this(tryBody, lineNumberFrom, lineNumberTo, null);
		}
		
		/**
		 * @param errors Every error will be accepted if errors is null
		 */
		public TryStatementPartCatchNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo, List<Node> errors) {
			super(tryBody, lineNumberFrom, lineNumberTo);
			
			this.errors = errors == null?null:new ArrayList<>(errors);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT_PART_CATCH;
		}
		
		public List<Node> getExpections() {
			return errors == null?null:new ArrayList<>(errors);
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementPartCatchNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Errors: {\n");
			if(errors == null)
				builder.append("\tnull\n");
			else
				errors.forEach(node -> {
					String[] tokens = node.toString().split("\\n");
					for(String token:tokens) {
						builder.append("\t");
						builder.append(token);
						builder.append("\n");
					}
				});
			builder.append("}, TryBody: {\n");
			String[] tokens = getTryBody().toString().split("\\n");
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
			
			if(!(obj instanceof TryStatementPartCatchNode))
				return false;
			
			TryStatementPartCatchNode that = (TryStatementPartCatchNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.errors.equals(that.errors) && this.getTryBody().equals(that.getTryBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.errors, this.getTryBody());
		}
	}
	
	public static final class TryStatementPartElseNode extends TryStatementPartNode {
		public TryStatementPartElseNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			super(tryBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT_PART_ELSE;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementPartElseNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, TryBody: {\n");
			String[] tokens = getTryBody().toString().split("\\n");
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
			
			if(!(obj instanceof TryStatementPartElseNode))
				return false;
			
			TryStatementPartElseNode that = (TryStatementPartElseNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getTryBody().equals(that.getTryBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getTryBody());
		}
	}
	
	public static final class TryStatementPartFinallyNode extends TryStatementPartNode {
		public TryStatementPartFinallyNode(AbstractSyntaxTree tryBody, int lineNumberFrom, int lineNumberTo) {
			super(tryBody, lineNumberFrom, lineNumberTo);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT_PART_FINALLY;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementPartFinallyNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, TryBody: {\n");
			String[] tokens = getTryBody().toString().split("\\n");
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
			
			if(!(obj instanceof LoopStatementPartLoopNode))
				return false;
			
			TryStatementPartTryNode that = (TryStatementPartTryNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.getTryBody().equals(that.getTryBody());
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.getTryBody());
		}
	}
	
	public static final class TryStatementNode implements Node {
		private final List<TryStatementPartNode> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public  TryStatementNode(List<TryStatementPartNode> nodes, int lineNumberFrom, int lineNumberTo) {
			this.nodes = nodes;
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		
		@Override
		public List<Node> getChildren() {
			return new ArrayList<>(nodes);
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.TRY_STATEMENT;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public List<TryStatementPartNode> getTryStatementPartNodes() {
			return nodes;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("TryStatementNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Children: {\n");
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
			
			if(!(obj instanceof TryStatementNode))
				return false;
			
			TryStatementNode that = (TryStatementNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.nodes.equals(that.nodes);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.nodes);
		}
	}
	
	public static final class OperationNode implements Node {
		private final List<Node> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		private final Operator operator;
		private final OperatorType nodeType;
		
		/**
		 * For ternary operator
		 */
		public OperationNode(int lineNumberFrom, int lineNumberTo, Node leftSideOperand, Node middleOperand, Node rightSideOperand, Operator operator, OperatorType nodeType) {
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
			
			if(!operator.isTernary())
				throw new IllegalStateException("Non ternary operator \"" + operator.getSymbol() + "\" must not have 3 operands");
			
			if(nodeType == null)
				nodeType = OperatorType.GENERAL;
			else if(!operator.getOperatorType().isCompatibleWith(nodeType))
				throw new IllegalStateException("Node type is not compatible with the operator");
			
			nodes = new ArrayList<>(3);
			nodes.add(leftSideOperand);
			nodes.add(middleOperand);
			nodes.add(rightSideOperand);
			
			this.operator = operator;
			this.nodeType = nodeType;
		}
		/**
		 * For ternary operator
		 */
		public OperationNode(Node leftSideOperand, Node middleOperand, Node rightSideOperand, Operator operator, OperatorType nodeType) {
			this(leftSideOperand.getLineNumberFrom(), rightSideOperand.getLineNumberTo(), leftSideOperand, middleOperand, rightSideOperand, operator, nodeType);
		}
		
		/**
		 * For binary operator
		 */
		public OperationNode(int lineNumberFrom, int lineNumberTo, Node leftSideOperand, Node rightSideOperand, Operator operator, OperatorType nodeType) {
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
			
			if(!operator.isBinary())
				throw new IllegalStateException("Non binary operator \"" + operator.getSymbol() + "\" must not have 2 operand");
			
			if(nodeType == null)
				nodeType = OperatorType.GENERAL;
			else if(!operator.getOperatorType().isCompatibleWith(nodeType))
				throw new IllegalStateException("Node type is not compatible with the operator");
			
			nodes = new ArrayList<>(2);
			nodes.add(leftSideOperand);
			nodes.add(rightSideOperand);
			
			this.operator = operator;
			this.nodeType = nodeType;
		}
		/**
		 * For binary operator
		 */
		public OperationNode(Node leftSideOperand, Node rightSideOperand, Operator operator, OperatorType nodeType) {
			this(leftSideOperand.getLineNumberFrom(), rightSideOperand.getLineNumberTo(), leftSideOperand, rightSideOperand, operator, nodeType);
		}
		
		/**
		 * For unary operator
		 */
		public OperationNode(int lineNumberFrom, int lineNumberTo, Node operand, Operator operator, OperatorType nodeType) {
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
			
			if(!operator.isUnary())
				throw new IllegalStateException("Non unary operator \"" + operator.getSymbol() + "\" must not have 1 operands");
			
			if(nodeType == null)
				nodeType = OperatorType.GENERAL;
			else if(!operator.getOperatorType().isCompatibleWith(nodeType))
				throw new IllegalStateException("Node type is not compatible with the operator");
			
			nodes = new ArrayList<>(1);
			nodes.add(operand);
			
			this.operator = operator;
			this.nodeType = nodeType;
		}
		/**
		 * For unary operator
		 */
		public OperationNode(Node operand, Operator operator, OperatorType nodeType) {
			this(operand.getLineNumberFrom(), operand.getLineNumberTo(), operand, operator, nodeType);
		}
		
		@Override
		public List<Node> getChildren() {
			return nodes;
		}
		
		@Override
		public NodeType getNodeType() {
			switch(nodeType) {
				case ALL:
				case GENERAL:
					return NodeType.OPERATION;
				case MATH:
					return NodeType.MATH;
				case CONDITION:
					return NodeType.CONDITION;
			}
			
			return NodeType.GENERAL;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public Node getLeftSideOperand() {
			return nodes.get(0);
		}
		
		public Node getMiddleOperand() {
			if(nodes.size() < 3)
				throw new IllegalStateException("Non ternary operator \"" + operator.getSymbol() + "\" has not 3 operand");
			
			return nodes.get(1);
		}
		
		public Node getRightSideOperand() {
			if(nodes.size() < 2)
				throw new IllegalStateException("Unary operator \"" + operator.getSymbol() + "\" has only 1 operand");
			
			return nodes.get(nodes.size() - 1);
		}
		
		public Operator getOperator() {
			return operator;
		}
		
		public OperatorType getOperatorType() {
			return operator.getOperatorType();
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("OperationNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, NodeType: \"");
			builder.append(nodeType);
			builder.append("\", Operator: \"");
			builder.append(operator);
			builder.append("\", OperatorType: \"");
			builder.append(operator.getOperatorType());
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
			
			if(!(obj instanceof OperationNode))
				return false;
			
			OperationNode that = (OperationNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.operator.equals(that.operator) && this.nodes.equals(that.nodes);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.operator, this.nodes);
		}
		
		public static enum Operator {
			//General
			NON                   ("",       1, -1,       OperatorType.GENERAL),
			LEN                   ("@",      1,  1,       OperatorType.GENERAL),
			DEEP_COPY             ("^",      1,  1,       OperatorType.GENERAL),
			CONCAT                ("|||",        5,       OperatorType.GENERAL),
			SPACESHIP             ("<=>",       10,       OperatorType.GENERAL),
			ELVIS                 ("?:",        13, true, OperatorType.GENERAL),
			NULL_COALESCING       ("??",        13, true, OperatorType.GENERAL),
			INLINE_IF             ("?...:",  3, 14, true, OperatorType.GENERAL),
			
			//Math
			MATH_NON              ("",       1, -1,       OperatorType.MATH),
			POW                   ("**",         2,       OperatorType.MATH),
			POS                   ("+",      1,  3,       OperatorType.MATH),
			INV                   ("-",      1,  3,       OperatorType.MATH),
			BITWISE_NOT           ("~",      1,  3,       OperatorType.MATH),
			INC                   ("+|",     1,  3,       OperatorType.MATH),
			DEC                   ("-|",     1,  3,       OperatorType.MATH),
			MUL                   ("*",          4,       OperatorType.MATH),
			DIV                   ("/",          4,       OperatorType.MATH),
			TRUNC_DIV             ("~/",         4,       OperatorType.MATH),
			FLOOR_DIV             ("//",         4,       OperatorType.MATH),
			CEIL_DIV              ("^/",         4,       OperatorType.MATH),
			MOD                   ("%",          4,       OperatorType.MATH),
			ADD                   ("+",          5,       OperatorType.MATH),
			SUB                   ("-",          5,       OperatorType.MATH),
			LSHIFT                ("<<",         6,       OperatorType.MATH),
			RSHIFT                (">>",         6,       OperatorType.MATH),
			RZSHIFT               (">>>",        6,       OperatorType.MATH),
			BITWISE_AND           ("&",          7,       OperatorType.MATH),
			BITWISE_XOR           ("^",          8,       OperatorType.MATH),
			BITWISE_OR            ("|",          9,       OperatorType.MATH),
			
			//Condition
			CONDITIONAL_NON       ("",       1, -1,       OperatorType.CONDITION),
			NOT                   ("!",      1,  3,       OperatorType.CONDITION),
			INSTANCE_OF           ("~~",        10,       OperatorType.CONDITION),
			EQUALS                ("==",        10,       OperatorType.CONDITION),
			NOT_EQUALS            ("!=",        10,       OperatorType.CONDITION),
			MATCHES               ("=~",        10,       OperatorType.CONDITION),
			NOT_MATCHES           ("!=~",       10,       OperatorType.CONDITION),
			STRICT_EQUALS         ("===",       10,       OperatorType.CONDITION),
			STRICT_NOT_EQUALS     ("!==",       10,       OperatorType.CONDITION),
			LESS_THAN             ("<",         10,       OperatorType.CONDITION),
			GREATER_THAN          (">",         10,       OperatorType.CONDITION),
			LESS_THAN_OR_EQUALS   ("<=",        10,       OperatorType.CONDITION),
			GREATER_THAN_OR_EQUALS(">=",        10,       OperatorType.CONDITION),
			AND                   ("&&",        11, true, OperatorType.CONDITION),
			OR                    ("||",        12, true, OperatorType.CONDITION),
			
			//ALL
			/**
			 * COMMA is a parser-only operator (This operator can only be used during parsing)
			 */
			COMMA                 (",",         15,       OperatorType.ALL),
			GET_ITEM              ("[...]",      0,       OperatorType.ALL),
			OPTIONAL_GET_ITEM     ("?.[...]",    0,       OperatorType.ALL),
			MEMBER_ACCESS         ("::",         0, true, OperatorType.ALL),
			OPTIONAL_MEMBER_ACCESS("?::",        0, true, OperatorType.ALL),
			MEMBER_ACCESS_POINTER ("->",         0, true, OperatorType.ALL);
			
			private final String symbol;
			private final int arity;
			private final int precedence;
			private final boolean lazyEvaluation;
			private final OperatorType operatorType;
			
			private Operator(String symbol, int arity, int precedence, boolean lazyEvaluation, OperatorType operatorType) {
				this.symbol = symbol;
				this.arity = arity;
				this.precedence = precedence;
				this.lazyEvaluation = lazyEvaluation;
				this.operatorType = operatorType;
			}
			private Operator(String symbol, int arity, int precedence, OperatorType operatorType) {
				this(symbol, arity, precedence, false, operatorType);
			}
			private Operator(String symbol, int precedence, boolean lazyEvaluation, OperatorType operatorType) {
				this(symbol, 2, precedence, lazyEvaluation, operatorType);
			}
			private Operator(String symbol, int precedence, OperatorType operatorType) {
				this(symbol, 2, precedence, false, operatorType);
			}
			
			public String getSymbol() {
				return symbol;
			}
			
			public boolean isUnary() {
				return arity == 1;
			}
			
			public boolean isBinary() {
				return arity == 2;
			}
			
			public boolean isTernary() {
				return arity == 3;
			}
			
			public int getPrecedence() {
				return precedence;
			}
			
			public boolean isLazyEvaluation() {
				return lazyEvaluation;
			}
			
			public OperatorType getOperatorType() {
				return operatorType;
			}
		}
		
		public static enum OperatorType {
			ALL, GENERAL, MATH, CONDITION;
			
			public boolean isCompatibleWith(OperatorType type) {
				return this == ALL || type == GENERAL || type == this;
			}
		}
	}
	
	public static final class ReturnNode implements Node {
		private final List<Node> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public ReturnNode(Node returnValue, int lineNumberFrom, int lineNumberTo) {
			nodes = new ArrayList<>(1);
			nodes.add(returnValue);
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		public ReturnNode(Node returnValue, int lineNumber) {
			this(returnValue, lineNumber, lineNumber);
		}
		public ReturnNode(Node returnValue) {
			this(returnValue, returnValue.getLineNumberFrom(), returnValue.getLineNumberTo());
		}
		
		public ReturnNode(int lineNumberFrom, int lineNumberTo) {
			nodes = new ArrayList<>(0);
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		public ReturnNode(int lineNumber) {
			this(lineNumber, lineNumber);
		}
		
		@Override
		public List<Node> getChildren() {
			return nodes;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.RETURN;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
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
			builder.append("ReturnNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Children: {\n");
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
	
	public static final class ThrowNode implements Node {
		private final List<Node> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public ThrowNode(Node throwValue, int lineNumberFrom, int lineNumberTo) {
			nodes = new ArrayList<>(1);
			nodes.add(throwValue);
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		public ThrowNode(Node throwValue, int lineNumber) {
			this(throwValue, lineNumber, lineNumber);
		}
		public ThrowNode(Node throwValue) {
			this(throwValue, throwValue.getLineNumberFrom(), throwValue.getLineNumberTo());
		}
		
		@Override
		public List<Node> getChildren() {
			return nodes;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.THROW;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		public Node getThrowValue() {
			return nodes.get(0);
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ThrowNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, ThrowValue: {\n");
			String[] tokens = nodes.get(0).toString().split("\\n");
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
	public static abstract class ValueNode extends ChildlessNode {
		public ValueNode(int lineNumberFrom, int lineNumberTo) {
			super(lineNumberFrom, lineNumberTo);
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
		
		public IntValueNode(int lineNumberFrom, int lineNumberTo, int i) {
			super(lineNumberFrom, lineNumberTo);
			
			this.i = i;
		}
		public IntValueNode(int lineNumber, int i) {
			this(lineNumber, lineNumber, i);
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
			builder.append("IntValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Value: \"");
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
		
		public LongValueNode(int lineNumberFrom, int lineNumberTo, long l) {
			super(lineNumberFrom, lineNumberTo);
			
			this.l = l;
		}
		public LongValueNode(int lineNumber, long l) {
			this(lineNumber, lineNumber, l);
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
			builder.append("LongValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Value: \"");
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
		
		public FloatValueNode(int lineNumberFrom, int lineNumberTo, float f) {
			super(lineNumberFrom, lineNumberTo);
			
			this.f = f;
		}
		public FloatValueNode(int lineNumber, float f) {
			this(lineNumber, lineNumber, f);
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
			builder.append("FloatValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Value: \"");
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
		
		public DoubleValueNode(int lineNumberFrom, int lineNumberTo, double d) {
			super(lineNumberFrom, lineNumberTo);
			
			this.d = d;
		}
		public DoubleValueNode(int lineNumber, double d) {
			this(lineNumber, lineNumber, d);
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
			builder.append("DoubleValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Value: \"");
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
		
		public CharValueNode(int lineNumberFrom, int lineNumberTo, char c) {
			super(lineNumberFrom, lineNumberTo);
			
			this.c = c;
		}
		public CharValueNode(int lineNumber, char c) {
			this(lineNumber, lineNumber, c);
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
			builder.append("CharValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Value: \"");
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
	
	public static final class TextValueNode extends ValueNode {
		private final String text;
		
		public TextValueNode(int lineNumberFrom, int lineNumberTo, String text) {
			super(lineNumberFrom, lineNumberTo);
			
			this.text = text;
		}
		public TextValueNode(int lineNumber, String text) {
			this(lineNumber, lineNumber, text);
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
			builder.append("TextValueNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Value: \"");
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
		public NullValueNode(int lineNumberFrom, int lineNumberTo) {
			super(lineNumberFrom, lineNumberTo);
		}
		public NullValueNode(int lineNumber) {
			this(lineNumber, lineNumber);
		}
		
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
		public VoidValueNode(int lineNumberFrom, int lineNumberTo) {
			super(lineNumberFrom, lineNumberTo);
		}
		public VoidValueNode(int lineNumber) {
			this(lineNumber, lineNumber);
		}
		
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
	
	public static final class ArrayNode implements Node {
		private final List<Node> nodes;
		private final int lineNumberFrom;
		private final int lineNumberTo;
		
		public ArrayNode(List<Node> nodes, int lineNumberFrom, int lineNumberTo) {
			this.nodes = new ArrayList<>(nodes);
			
			this.lineNumberFrom = lineNumberFrom;
			this.lineNumberTo = lineNumberTo;
		}
		public ArrayNode(List<Node> nodes) {
			this(nodes, nodes.isEmpty()?-1:nodes.get(0).getLineNumberFrom(),
					nodes.isEmpty()?-1:nodes.get(nodes.size() - 1).getLineNumberTo());
		}
		
		@Override
		public List<Node> getChildren() {
			return nodes;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.ARRAY;
		}
		
		@Override
		public int getLineNumberFrom() {
			return lineNumberFrom;
		}
		
		@Override
		public int getLineNumberTo() {
			return lineNumberTo;
		}
		
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ArrayNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Elements: {\n");
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
			
			if(!(obj instanceof ArrayNode))
				return false;
			
			ArrayNode that = (ArrayNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.nodes.equals(that.nodes);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.nodes);
		}
	}
	
	public static final class StructDefinitionNode extends ChildlessNode {
		private final List<String> memberNames;
		private final List<String> typeConstraints;
		
		public StructDefinitionNode(int lineNumberFrom, int lineNumberTo, List<String> memberNames, List<String> typeConstraints) {
			super(lineNumberFrom, lineNumberTo);
			
			this.memberNames = memberNames;
			this.typeConstraints = typeConstraints;
		}
		
		public List<String> getMemberNames() {
			return memberNames;
		}
		
		public List<String> getTypeConstraints() {
			return typeConstraints;
		}
		
		@Override
		public NodeType getNodeType() {
			return NodeType.STRUCT_DEFINITION;
		}
		
		@Override
		public String toString() {
			if(memberNames.size() != typeConstraints.size())
				return "StructDefinitionNode: <INVALID>";
			
			StringBuilder builder = new StringBuilder();
			builder.append("StructDefinitionNode: Position: {LineFrom: ");
			builder.append(lineNumberFrom);
			builder.append(", LineTo: ");
			builder.append(lineNumberTo);
			builder.append("}, Members{TypeConstraints}: {\n");
			for(int i = 0;i < memberNames.size();i++) {
				builder.append("\t");
				builder.append(memberNames.get(i));
				if(typeConstraints.get(i) != null) {
					builder.append("{");
					builder.append(typeConstraints.get(i));
					builder.append("}");
				}
				builder.append("\n");
			}
			builder.append("}");
			
			return builder.toString();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof StructDefinitionNode))
				return false;
			
			StructDefinitionNode that = (StructDefinitionNode)obj;
			return this.getNodeType().equals(that.getNodeType()) && this.memberNames.equals(that.memberNames) &&
					this.typeConstraints.equals(that.typeConstraints);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(this.getNodeType(), this.memberNames, this.typeConstraints);
		}
	}
	
	public static enum NodeType {
		GENERAL, LIST, PARSING_ERROR, ASSIGNMENT, ESCAPE_SEQUENCE, UNPROCESSED_VARIABLE_NAME, VARIABLE_NAME, ARGUMENT_SEPARATOR,
		FUNCTION_CALL, FUNCTION_CALL_PREVIOUS_NODE_VALUE, FUNCTION_DEFINITION, CONDITION, IF_STATEMENT_PART_IF, IF_STATEMENT_PART_ELSE,
		IF_STATEMENT, LOOP_STATEMENT_PART_LOOP, LOOP_STATEMENT_PART_WHILE, LOOP_STATEMENT_PART_UNTIL, LOOP_STATEMENT_PART_REPEAT, LOOP_STATEMENT_PART_FOR_EACH,
		LOOP_STATEMENT_PART_ELSE, LOOP_STATEMENT, LOOP_STATEMENT_CONTINUE_BREAK, TRY_STATEMENT_PART_TRY, TRY_STATEMENT_PART_SOFT_TRY, TRY_STATEMENT_PART_NON_TRY,
		TRY_STATEMENT_PART_CATCH, TRY_STATEMENT_PART_ELSE, TRY_STATEMENT_PART_FINALLY, TRY_STATEMENT, MATH, OPERATION, RETURN, THROW, INT_VALUE, LONG_VALUE,
		FLOAT_VALUE, DOUBLE_VALUE, CHAR_VALUE, TEXT_VALUE, NULL_VALUE, VOID_VALUE, ARRAY, STRUCT_DEFINITION;
	}
}