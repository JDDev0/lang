package me.jddev0.module.graphics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import me.jddev0.module.io.Lang;
import me.jddev0.module.io.LangInterpreter;
import me.jddev0.module.io.LangInterpreter.DataObject;
import me.jddev0.module.io.LangInterpreter.InterpretingError;
import me.jddev0.module.io.LangPlatformAPI;
import me.jddev0.module.io.LangPredefinedFunctionObject;
import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;

/**
 * Uses the io module<br>
 * <br>
 * Graphics-Module<br>
 * Lang Shell
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class LangShellWindow extends JDialog {
	private static final long serialVersionUID = 3517996790399999763L;

	private JTextPane shell;
	
	private List<String> history = new LinkedList<String>();
	private int historyPos = 0;
	private String currentCommand = "";
	private StringBuilder multiLineTmp = new StringBuilder();
	private TerminalIO term;
	private boolean flagEnd = false;
	private int indent = 0;
	private LangPlatformAPI langPlatformAPI = new LangPlatformAPI();
	
	public LangShellWindow(JFrame owner, TerminalIO term) {
		this(owner, term, 12);
	}
	public LangShellWindow(JFrame owner, TerminalIO term, int fontSize) {
		super(owner, true); //Make this window to an modal window (Focus won't be given back to owner window)
		
		this.term = term;
		
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setTitle("LangShell");
		setSize((int)(750*fontSize / 12.), (int)(500*fontSize / 12.));
		setLocationRelativeTo(null);
		
		JPanel contentPane = new JPanel();
		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));
		
		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setDoubleBuffered(true);
		scrollPane.setRequestFocusEnabled(false);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		contentPane.add(scrollPane, BorderLayout.CENTER);
		
		//Pane for displaying output
		shell = new JTextPane();
		shell.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
		shell.setBackground(Color.BLACK);
		shell.setEditable(false);
		shell.setAutoscrolls(true);
		shell.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
		shell.setMargin(new Insets(3, 5, 0, 5));
		shell.addKeyListener(new KeyAdapter() {
			private StringBuilder lineTmp = new StringBuilder();
			private String lastHistoryEntryUsed = "";
			
			@Override
			public void keyTyped(KeyEvent e) {
				if(flagEnd)
					return;
				
				char c = e.getKeyChar();
				if(c == KeyEvent.VK_BACK_SPACE) {
					//Remove the last char (if line is not empty)
					if(lineTmp.length() > 0) {
						try {
							Document doc = shell.getDocument();
							doc.remove(doc.getLength() - 1, 1);
						}catch(BadLocationException e1) {}
						lineTmp.deleteCharAt(lineTmp.length() - 1);
						highlightSyntaxLastLine();
					}
				}else if(c != KeyEvent.CHAR_UNDEFINED) {
					if(c == '\n') {
						addLine(lineTmp.toString());
						lineTmp.delete(0, lineTmp.length());
						lastHistoryEntryUsed = "";
					}else {
						lineTmp.append(c);
						GraphicsHelper.addText(shell, c + "", Color.WHITE);
						highlightSyntaxLastLine();
					}
				}
			}
			
			@Override
			public void keyPressed(KeyEvent e) {
				if(flagEnd)
					return;
				
				if(e.getKeyCode() == KeyEvent.VK_C && e.isControlDown() && e.isShiftDown()){
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(shell.getSelectedText()), null);
				}else if(e.getKeyCode() == KeyEvent.VK_V && e.isControlDown() && e.isShiftDown()){
					try {
						Object copiedRaw = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null).getTransferData(DataFlavor.stringFlavor);
						if(copiedRaw != null) {
							String copied = copiedRaw.toString();
							String[] lines = copied.split("\n");
							for(int i = 0;i < lines.length;i++) {
								String line = lines[i].trim();
								GraphicsHelper.addText(shell, line, Color.WHITE);
								highlightSyntaxLastLine();
								lineTmp.append(line);
								if(i != lines.length - 1 || copied.endsWith("\n")) { //Line has an '\n' at end -> finished line
									addLine(lineTmp.toString());
									lineTmp.delete(0, lineTmp.length());
								}
							}
						}
					}catch(HeadlessException|UnsupportedFlavorException|IOException e1) {
						term.logStackTrace(e1, LangShellWindow.class);
					}
				}else if(e.getKeyCode() == KeyEvent.VK_C && e.isControlDown()) {
					end();
					return;
				}else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
					if(historyPos < history.size() - 1) {
						historyPos++;
						
						String historyRet = history.get(historyPos);
						String[] lines = historyRet.split("\n");
						String lastLine = lines[lines.length - 1];
						
						lineTmp.delete(0, lineTmp.length());
						lineTmp.append(lastLine);
						
						removeLines(lastHistoryEntryUsed);
						lastHistoryEntryUsed = historyRet;
						addLinesWithoutExec(historyRet);
					}else {
						if(historyPos == history.size() - 1)
							historyPos++;
						
						lineTmp.delete(0, lineTmp.length());
						lineTmp.append(currentCommand);
						
						removeLines(lastHistoryEntryUsed);
						lastHistoryEntryUsed = currentCommand;
						addLinesWithoutExec(currentCommand);
					}
				}else if(e.getKeyCode() == KeyEvent.VK_UP) {
					if(historyPos > 0) {
						if(historyPos == history.size())
							currentCommand = lineTmp.toString();
						
						historyPos--;
						
						String historyRet = history.get(historyPos);
						String[] lines = historyRet.split("\n");
						String lastLine = lines[lines.length - 1];
						
						lineTmp.delete(0, lineTmp.length());
						lineTmp.append(lastLine);
						
						removeLines(lastHistoryEntryUsed);
						lastHistoryEntryUsed = historyRet;
						addLinesWithoutExec(historyRet);
					}
				}
			}
		});
		scrollPane.setViewportView(shell);
		
		initShell();
	}
	
	public void setFontSize(int fontSize) {
		shell.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));
		
		revalidate();
		
		//Auto scroll
		shell.setCaretPosition(shell.getDocument().getLength());
	}
	
	private LangInterpreter.LangInterpreterInterface lii;
	private PrintStream oldOut;
	
	private void initShell() {
		//Sets System.out
		oldOut = System.out;
		System.setOut(new PrintStream(new OutputStream() {
			//Tmp for the printing
			private StringBuilder printingTmp = new StringBuilder();
			private int type = 0;
			//Colors for the levels
			private Color[] colors = {Color.WHITE, Color.BLUE, Color.MAGENTA, Color.GREEN, Color.YELLOW, new Color(255, 127, 0), Color.RED, new Color(127, 0, 0)};
			
			@Override
			public void write(int b) throws IOException {
				oldOut.write(b);
				printingTmp.append((char)b);
				
				if((char)b == '\n') {//Sets color of message after new line
					String printStr = printingTmp.toString();
					if(printStr.startsWith("[" + Level.NOTSET + "]")) {
						type = 0;
					}else if(printStr.startsWith("[" + Level.USER + "]")) {
						type = 1;
					}else if(printStr.startsWith("[" + Level.DEBUG + "]")) {
						type = 2;
					}else if(printStr.startsWith("[" + Level.CONFIG + "]")) {
						type = 3;
					}else if(printStr.startsWith("[" + Level.INFO + "]")) {
						type = 4;
					}else if(printStr.startsWith("[" + Level.WARNING + "]")) {
						type = 5;
					}else if(printStr.startsWith("[" + Level.ERROR + "]")) {
						type = 6;
					}else if(printStr.startsWith("[" + Level.CRITICAL + "]")) {
						type = 7;
					}
					
					//Adds message to term
					if(printStr.contains("[From lang file]: ")) {
						GraphicsHelper.addText(shell, printStr.split("\\[From lang file\\]: ")[1], colors[type]);
					}else if(printStr.contains("]: ")) {
						GraphicsHelper.addText(shell, printStr.split("\\]: ")[1], colors[type]);
					}else {
						GraphicsHelper.addText(shell, printStr, colors[type]);
					}
					
					//Auto scroll
					shell.setCaretPosition(shell.getDocument().getLength());
					
					//Clears tmp for the printing
					printingTmp.delete(0, printingTmp.length());
				}
			}
		}));
		
		lii = Lang.createInterpreterInterface(term, langPlatformAPI);
		
		//Add debug functions
		lii.addPredefinedFunction("printHelp", (argumentList, DATA_ID) -> {
			term.logln(Level.DEBUG, "func.printHelp() # Prints this help text\n" +
			"func.printDebug(ptr) # Prints debug information about the provided DataObject", LangShellWindow.class);
			
			return null;
		});
		lii.addPredefinedFunction("printDebug", (argumentList, DATA_ID) -> {
			DataObject dataObject = lii.getNextArgumentAndRemoveUsedDataObjects(argumentList, false);
			if(argumentList.size() > 0) //Not 1 argument
				return lii.setErrnoErrorObject(InterpretingError.INVALID_ARG_COUNT, DATA_ID);
			
			StringBuilder builder = new StringBuilder();
			builder.append("Debug[");
			builder.append(dataObject.getVariableName() == null?"<ANONYMOUS>":dataObject.getVariableName());
			builder.append("]:\n");
			builder.append(getDebugString(dataObject));
			
			term.logln(Level.DEBUG, builder.toString(), LangShellWindow.class);
			
			return null;
		});
		
		//"Remove" input() function: Would not work ("TermIO-Control" window has to be accessible)
		lii.addPredefinedFunction("input", (argumentList, DATA_ID) -> {
			lii.setErrno(InterpretingError.FUNCTION_NOT_SUPPORTED, DATA_ID);
			return new DataObject().setError(new LangInterpreter.ErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED));
		});
		
		GraphicsHelper.addText(shell, "Lang-Shell", Color.RED);
		GraphicsHelper.addText(shell, " - Press CTRL + C to exit!\nCopy with (CTRL + SHIFT + C) and paste with (CTRL + SHIT + V)\n" +
		"Use func.printHelp() to get information about LangShell functions\n> ", Color.WHITE);
	}
	
	private String getDebugString(LangInterpreter.DataObject dataObject) {
		if(dataObject == null)
			return "<NULL>";
		
		StringBuilder builder = new StringBuilder();
		builder.append("Raw Text: ");
		builder.append(dataObject.getText());
		builder.append("\nType: ");
		builder.append(dataObject.getType());
		builder.append("\nFinal: ");
		builder.append(dataObject.isFinalData());
		builder.append("\nVariable Name: ");
		builder.append(dataObject.getVariableName());
		switch(dataObject.getType()) {
			case VAR_POINTER:
				builder.append("\nPointing to: {\n");
				String[] debugStringLines = getDebugString(dataObject.getVarPointer().getVar()).toString().split("\\n");
				for(String debugStringLine:debugStringLines) {
					builder.append("    ");
					builder.append(debugStringLine);
					builder.append("\n");
				}
				builder.append("}");
				break;
			
			case ARRAY:
				builder.append("\nSize: ");
				builder.append(dataObject.getArray().length);
				builder.append("\nElements:");
				for(int i = 0;i < dataObject.getArray().length;i++) {
					DataObject ele = dataObject.getArray()[i];
					builder.append("\n    arr(");
					builder.append(i);
					builder.append("): {\n");
					debugStringLines = getDebugString(ele).toString().split("\\n");
					for(String debugStringLine:debugStringLines) {
						builder.append("       ");
						builder.append(debugStringLine);
						builder.append("\n");
					}
					builder.append("   }");
				}
				break;
			
			case FUNCTION_POINTER:
				builder.append("\nFunction-Type: ");
				builder.append(dataObject.getFunctionPointer().getFunctionPointerType());
				builder.append("\nParameter List: ");
				builder.append(dataObject.getFunctionPointer().getParameterList());
				builder.append("\nFunction Body: ");
				builder.append(dataObject.getFunctionPointer().getFunctionBody());
				builder.append("\nPredefined Function: ");
				LangPredefinedFunctionObject predefinedFunction = dataObject.getFunctionPointer().getPredefinedFunction();
				if(predefinedFunction == null) {
					builder.append(predefinedFunction);
				}else {
					builder.append("{");
					builder.append("\n    Raw String: ");
					builder.append(predefinedFunction);
					builder.append("\n    Deprecated: ");
					boolean deprecated = predefinedFunction.isDeprecated();
					builder.append(deprecated);
					if(deprecated) {
						builder.append("\n        Will be removed in: ");
						builder.append(predefinedFunction.getDeprecatedRemoveVersion());
						builder.append("\n        Replacement function: ");
						builder.append(predefinedFunction.getDeprecatedReplacementFunction());
					}
					builder.append("\n}");
				}
				builder.append("\nExternal Function: ");
				builder.append(dataObject.getFunctionPointer().getExternalFunction());
				break;
			
			case ERROR:
				builder.append("\nError-Code: ");
				builder.append(dataObject.getError().getErrno());
				builder.append("\nError-Text: ");
				builder.append(dataObject.getError().getErrmsg());
				break;
			
			default:
				break;
		}
		
		return builder.toString();
	}
	
	private void highlightSyntaxLastLine() {
		try {
			Document doc = shell.getDocument();
			int startOfLine;
			for(startOfLine = doc.getLength() - 1;startOfLine > 0;startOfLine--)
				if(doc.getText(startOfLine, 1).charAt(0) == '\n')
					break;
			startOfLine++; //The line starts on char after '\n'
			
			String line = doc.getText(startOfLine, doc.getLength() - startOfLine);
			doc.remove(startOfLine, doc.getLength() - startOfLine);
			
			boolean commentFlag = false, varFlag = false, funcFlag = false, bracketsFlag = false, returnFlag = false;
			for(int i = 0;i < line.length();i++) {
				char c = line.charAt(i);
				
				if(!commentFlag && c == '#' && !(i > 0 && line.charAt(i - 1) == '\\'))
					commentFlag = true;
				
				if(!varFlag && (c == '$' || c == '&'))
					varFlag = true;
				
				if(!funcFlag) {
					String checkTmp = line.substring(i);
					if(checkTmp.startsWith("fp.") || checkTmp.startsWith("func.") || checkTmp.startsWith("linker.") || checkTmp.startsWith("con."))
						funcFlag = true;
				}
				
				if(!returnFlag) {
					String checkTmp = line.substring(i);
					if(checkTmp.startsWith("return "))
						returnFlag = true;
				}
				
				bracketsFlag = c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == '.' || c == ',';
				
				if(varFlag && !(Character.isAlphabetic(c) || Character.isDigit(c) || c == '_' || c == '[' || c == ']' || c == '.' || c == '$' || c == '&'))
					varFlag = false;
				if(funcFlag && !(Character.isAlphabetic(c) || Character.isDigit(c) || c == '_' || c == '[' || c == ']' || c == '.'))
					funcFlag = false;
				if(returnFlag && i > 6 && line.substring(i - 6).startsWith("return "))
					returnFlag = false;
				
				if(varFlag && i > 0 && line.charAt(i - 1) == '\\')
					varFlag = false;
				
				//Remove var highlighting if "&&"
				if(line.substring(i).startsWith("&&") || (i > 0 && line.substring(i - 1).startsWith("&&")))
					varFlag = false;
				
				Color col = Color.WHITE;
				if(commentFlag)
					col = Color.GREEN;
				else if(bracketsFlag)
					col = Color.LIGHT_GRAY;
				else if(funcFlag)
					col = Color.CYAN;
				else if(varFlag)
					col = Color.MAGENTA;
				else if(returnFlag)
					col = Color.LIGHT_GRAY;
				else if(Character.isDigit(c))
					col = Color.YELLOW;
				
				GraphicsHelper.addText(shell, c + "", col);
			}
		}catch(BadLocationException e) {}
		
		//Auto scroll
		shell.setCaretPosition(shell.getDocument().getLength());
	}
	
	private void addToHistory(String str) {
		if(!str.trim().isEmpty() && (history.isEmpty() || !history.get(history.size() - 1).equals(str)))
			history.add(str);
		
		historyPos = history.size();
		currentCommand = "";
	}
	
	private void removeLines(String str) {
		multiLineTmp.delete(0, multiLineTmp.length());
		indent = 0;
		
		String[] lines = str.split("\n");
		for(int i = 0;i < lines.length;i++) {
			try {
				Document doc = shell.getDocument();
				int startOfLine;
				for(startOfLine = doc.getLength() - 1;startOfLine > 0;startOfLine--)
					if(doc.getText(startOfLine, 1).charAt(0) == '\n')
						break;
				doc.remove(startOfLine, doc.getLength() - startOfLine);
			}catch(BadLocationException e) {}
		}
		
		GraphicsHelper.addText(shell, "\n> ", Color.WHITE);
	}
	private void addLinesWithoutExec(String str) {
		String lastLine = str;
		if(str.contains("\n")) {
			String[] lines = str.split("\n");
			for(int i = 0;i < lines.length - 1;i++) {
				String line = lines[i];
				
				GraphicsHelper.addText(shell, line, Color.WHITE);
				highlightSyntaxLastLine();
				
				addLine(line);
			}
			lastLine = lines[lines.length - 1];
		}
		
		GraphicsHelper.addText(shell, lastLine, Color.WHITE);
		highlightSyntaxLastLine();
	}
	private void addLine(String line) {
		if(indent == 0) {
			GraphicsHelper.addText(shell, "\n", Color.WHITE);
			
			if(line.trim().endsWith("{") || (line.trim().startsWith("con.") && !line.trim().startsWith("con.endif"))) {
				indent++;
				multiLineTmp.append(line);
				multiLineTmp.append("\n");
				
				GraphicsHelper.addText(shell, "    > ", Color.WHITE);
			}else {
				try {
					addToHistory(line);
					
					lii.exec(0, line);
				}catch(IOException e) {
					term.logStackTrace(e, LangShellWindow.class);
				}
				GraphicsHelper.addText(shell, "> ", Color.WHITE);
			}
		}else {
			if(line.trim().endsWith("{") || line.trim().startsWith("con.if"))
				indent++;
			
			multiLineTmp.append(line);
			multiLineTmp.append("\n");
			
			if(line.trim().startsWith("}") || (line.trim().startsWith("con.") && !line.trim().startsWith("con.if"))) {
				indent--;
				
				if(line.trim().startsWith("con.") && !line.trim().startsWith("con.endif"))
					indent++;
				
				//Remove the first indent from actual line
				try {
					Document doc = shell.getDocument();
					int startOfLine;
					for(startOfLine = doc.getLength() - 1;startOfLine > 0;startOfLine--)
						if(doc.getText(startOfLine, 1).charAt(0) == '\n')
							break;
					startOfLine++; //The line starts on char after '\n'
					doc.remove(startOfLine, 4);
				}catch(BadLocationException e) {}
			}
			
			GraphicsHelper.addText(shell, "\n", Color.WHITE);
			if(indent == 0) {
				String multiLineTmpString = multiLineTmp.toString();
				addToHistory(multiLineTmpString.substring(0, multiLineTmpString.length() - 1)); //Remove "\n"
				
				try {
					lii.exec(0, multiLineTmp.toString());
				}catch(IOException e) {
					term.logStackTrace(e, LangShellWindow.class);
				}
				
				multiLineTmp.delete(0, multiLineTmp.length());
				currentCommand = "";
			}
			
			for(int i = 0;i < indent;i++)
				GraphicsHelper.addText(shell, "    ", Color.WHITE);
			GraphicsHelper.addText(shell, "> ", Color.WHITE);
		}
	}
	
	private void end() {
		flagEnd = true;
		
		GraphicsHelper.addText(shell, "^C\nTranslation map:\n", Color.WHITE);
		
		Map<String, String> lang = lii.getTranslationMap(0);
		lang.forEach((key, value) -> {
			term.logln(Level.DEBUG, key + " = " + value, LangShellWindow.class);
		});
		
		//Reset the printStream output
		System.setOut(oldOut);
	}
}