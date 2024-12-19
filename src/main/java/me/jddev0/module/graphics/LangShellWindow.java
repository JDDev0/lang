package me.jddev0.module.graphics;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import at.jddev0.io.TerminalIO;
import at.jddev0.io.TerminalIO.Level;
import at.jddev0.lang.*;
import at.jddev0.lang.LangInterpreter.InterpretingError;
import at.jddev0.lang.platform.swing.LangPlatformAPI;
import at.jddev0.lang.LangFunction.AllowedTypes;
import at.jddev0.lang.LangFunction.LangParameter;
import at.jddev0.lang.LangFunction.LangParameter.CallByPointer;
import at.jddev0.lang.LangFunction.LangParameter.NumberValue;
import at.jddev0.lang.LangFunction.LangParameter.VarArgs;

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

    private static final Color VARIABLE_IDENTIFIER_COLOR = new Color(152, 118, 170);
    private static final Color MODULE_PREFIX_COLOR = new Color(178, 82, 0);
    private static final Color OPERATOR_BRACKET_COLOR = new Color(192, 192, 192);
    private static final Color COMMENT_COLOR = new Color(98, 151, 85);
    private static final Color DOC_COMMENT_COLOR = new Color(0, 127, 0);
    private static final Color NUMBER_COLOR = new Color(104, 151, 187);
    private static final Color KEYWORD_COLOR = new Color(204, 120, 50);
    private static final Color FUNCTION_COLOR = new Color(255, 198, 109);
    private static final Color TEXT_COLOR = new Color(110, 184, 63);
    private static final Color MIGHT_BE_TEXT_COLOR = new Color(136, 162, 122);
    private static final Color NORMAL_COLOR = new Color(255, 255, 255);

    private final JTextPane shell;
    private final KeyListener shellKeyListener;
    private final TerminalIO term;

    private int fontSize;

    private SpecialCharInputWindow specialCharInputWindow = null;

    private File lastLangFileSavedTo = null;
    private final StringBuilder langFileOutputBuilder = new StringBuilder();

    private final List<String> history = new LinkedList<>();
    private int historyPos = 0;
    private String currentCommand = "";

    private String autoCompleteText = "";
    private int autoCompletePos = 0;
    private Color lastColor = Color.BLACK;

    private final Queue<String> executionQueue = new LinkedList<>();
    private final StringBuilder multiLineTmp = new StringBuilder();
    private int indent = 0;
    private boolean flagMultilineText = false;
    private boolean flagLineContinuation = false;
    private boolean flagEnd = false;
    private boolean flagRunning = false;
    private boolean flagExecutingQueue = false;

    private AutoPrintMode autoPrintMode = AutoPrintMode.AUTO;

    private final ILangPlatformAPI langPlatformAPI = new LangPlatformAPI();
    private LangInterpreter.LangInterpreterInterface lii;
    private final LangLexer lexer = new LangLexer();
    private PrintStream oldOut;

    //Lists for auto complete
    private final List<String> langDataAndExecutionFlags = Arrays.asList("allowTermRedirect = ", "errorOutput = ", "name = ", "nativeStackTraces = ", "test = ", "rawVariableNames = ", "version = ");
    private final List<String> controlFlowStatements = Arrays.asList("break", "catch", "continue", "elif(", "else", "endif", "endloop", "endtry", "finally", "foreach(", "if(", "loop", "nontry",
            "repeat(", "softtry", "try", "until(", "while(");
    private final List<String> parserFunctions = Arrays.asList("con(", "math(", "norm(", "op(");

    public LangShellWindow(Frame owner, TerminalIO term, int fontSize, String[] langArgs) {
        super(owner, true);

        this.term = term;
        this.fontSize = fontSize;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setTitle("LangShell");
        setSize(owner.getSize());
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if(specialCharInputWindow != null)
                    specialCharInputWindow.dispatchEvent(new WindowEvent(specialCharInputWindow, WindowEvent.WINDOW_CLOSING));

                lii.stop(); //Stop interpreter if window is closed
            }
        });

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
        shell.setFocusTraversalKeysEnabled(false);
        shellKeyListener = new KeyAdapter() {
            private final StringBuilder lineTmp = new StringBuilder();
            private String lastHistoryEntryUsed = "";

            @Override
            public void keyTyped(KeyEvent e) {
                if(flagEnd)
                    return;

                char c = e.getKeyChar();
                if(c == KeyEvent.CHAR_UNDEFINED)
                    return;
                if((c > -1 && c < 8) || c == 12 || (c > 13 && c < 32) || c == 127) //Ignores certain control chars
                    return;

                if(c == '\b') {
                    //Remove the last char (if line is not empty)
                    if(lineTmp.length() > 0) {
                        removeAutoCompleteText();
                        try {
                            Document doc = shell.getDocument();
                            doc.remove(doc.getLength() - 1, 1);
                        }catch(BadLocationException ignored) {}
                        lineTmp.deleteCharAt(lineTmp.length() - 1);
                        highlightSyntaxLastLine();
                        updateAutoCompleteText(lineTmp.toString());
                    }
                }else if(c == '\n') {
                    if(autoCompleteText.isEmpty()) {
                        removeAutoCompleteText();
                        addLine(lineTmp.toString(), false, false);
                        lineTmp.delete(0, lineTmp.length());
                        lastHistoryEntryUsed = "";
                    }else {
                        lineTmp.append(autoCompleteText);
                        GraphicsHelper.addText(shell, autoCompleteText, Color.WHITE);
                        removeAutoCompleteText();
                        highlightSyntaxLastLine();
                        updateAutoCompleteText(lineTmp.toString());
                    }
                }else if(c == '\t') { //Cycle through auto completes
                    int oldAutoCompletePos = autoCompletePos;
                    removeAutoCompleteText();
                    autoCompletePos = oldAutoCompletePos + (e.isShiftDown()?-1:1);
                    updateAutoCompleteText(lineTmp.toString());
                }else {
                    removeAutoCompleteText();
                    lineTmp.append(c);
                    GraphicsHelper.addText(shell, c + "", Color.WHITE);
                    highlightSyntaxLastLine();
                    updateAutoCompleteText(lineTmp.toString());
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if(flagEnd) {
                    if(e.getKeyCode() == KeyEvent.VK_C && e.isControlDown() && !e.isShiftDown())
                        dispatchEvent(new WindowEvent(LangShellWindow.this, WindowEvent.WINDOW_CLOSING));

                    return;
                }

                if(e.getKeyCode() == KeyEvent.VK_C && e.isControlDown() && e.isShiftDown()) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(shell.getSelectedText()), null);
                }else if(e.getKeyCode() == KeyEvent.VK_V && e.isControlDown() && e.isShiftDown()) {
                    try {
                        removeAutoCompleteText();
                        Object copiedRaw = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null).getTransferData(DataFlavor.stringFlavor);
                        String copied = copiedRaw.toString();
                        String[] lines = copied.split("\n");
                        for(int i = 0;i < lines.length;i++) {
                            String line = lines[i].trim();
                            GraphicsHelper.addText(shell, line, Color.WHITE);
                            highlightSyntaxLastLine();
                            lineTmp.append(line);
                            if(i != lines.length - 1) { //Line has an '\n' at end -> finished line
                                addLine(lineTmp.toString(), true, true);
                                lineTmp.delete(0, lineTmp.length());
                            }
                        }

                        if(lines.length > 1) {
                            addLine(lines[lines.length - 1], true, false);
                            lineTmp.delete(0, lineTmp.length());
                        }

                        if(flagRunning) {
                            if(!flagExecutingQueue) {
                                executionQueue.clear();
                                term.logln(Level.ERROR, "The interpreter is already executing stuff!\nPress CTRL + C for stopping the execution.", LangShellWindow.class);
                            }
                        }else if(lines.length > 1 && !executionQueue.isEmpty()) {
                            executeCodeFromExecutionQueue();
                        }

                        updateAutoCompleteText(lineTmp.toString());
                    }catch(UnsupportedFlavorException e1) {
                        term.logln(Level.WARNING, "The clipboard contains no string data!", LangShellWindow.class);
                    }catch(HeadlessException|IOException e1) {
                        term.logStackTrace(e1, LangShellWindow.class);
                    }
                }else if(e.getKeyCode() == KeyEvent.VK_F1 && e.isControlDown()) {
                    String line = lineTmp.toString();

                    String[] tokens = line.split(".(?=\\[\\[|(\\[\\[\\w+\\]\\]::)(\\$|&|fp\\.)|(?<!\\w]]::)(\\$|&|fp\\.)|func\\.|fn\\.|linker\\.|ln\\.|con\\.|parser\\.)");
                    if(tokens.length == 0)
                        return;

                    String input = tokens[tokens.length - 1] + autoCompleteText;

                    int bracketIndex = input.indexOf('(');
                    if(bracketIndex != -1)
                        input = input.substring(0, bracketIndex);

                    String moduleName = null;
                    if(input.matches("\\[\\[\\w+\\]\\]::.+")) {
                        moduleName = input.substring(2, input.indexOf(']'));
                    }

                    String functionName = input;
                    DataObject.FunctionPointerObject function = null;
                    if(input.matches("(func\\.|fn\\.|linker\\.|ln\\.)\\w+")) {
                        function = lii.getPredefinedFunctions().
                                get(functionName.substring(functionName.indexOf('.') + 1));
                    }else if(input.matches("(\\[\\[\\w+\\]\\]::)?(\\$|fp\\.)\\w+")) {
                        String variableName = input.contains("::")?input.substring(input.indexOf(':') + 2):input;

                        Map<String, DataObject> moduleVars = lii.getModuleExportedVariables(moduleName);
                        DataObject value = moduleVars == null?lii.getVar(variableName):moduleVars.get(variableName);

                        if(value != null && value.getType() == DataObject.DataType.FUNCTION_POINTER) {
                            function = value.getFunctionPointer();
                        }
                    }

                    new FunctionHelpWindow(LangShellWindow.this, functionName, function).setVisible(true);
                }else if(e.getKeyCode() == KeyEvent.VK_I && e.isControlDown() && !e.isShiftDown()) {
                    if(specialCharInputWindow == null) {
                        specialCharInputWindow = new SpecialCharInputWindow(LangShellWindow.this, new String[] {"^", "\u25b2", "\u25bc"});
                        specialCharInputWindow.setVisible(true);
                    }
                }else if(e.getKeyCode() == KeyEvent.VK_S && e.isControlDown()) {
                    saveLangFile(e.isShiftDown());
                }else if(e.getKeyCode() == KeyEvent.VK_F && e.isControlDown() && e.isShiftDown()) {
                    if(specialCharInputWindow == null) {
                        JFileChooser fileChooser = new JFileChooser(".");
                        fileChooser.setDialogTitle("Select file for inserting the path");
                        if(fileChooser.showOpenDialog(LangShellWindow.this) == JFileChooser.APPROVE_OPTION) {
                            File file = fileChooser.getSelectedFile();

                            removeAutoCompleteText();

                            String textInsert = file.getAbsolutePath();
                            GraphicsHelper.addText(shell, textInsert, Color.WHITE);
                            highlightSyntaxLastLine();
                            lineTmp.append(textInsert);

                            updateAutoCompleteText(lineTmp.toString());
                        }
                    }
                }else if(e.getKeyCode() == KeyEvent.VK_C && e.isControlDown() && !e.isShiftDown()) {
                    if(flagRunning) {
                        lii.stop();
                        GraphicsHelper.addText(shell, "^C\n", Color.WHITE);
                    }else {
                        end();
                    }
                }else if(e.getKeyCode() == KeyEvent.VK_L && e.isControlDown() && !e.isShiftDown()) {
                    clear();
                }else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
                    removeAutoCompleteText();
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
                        updateAutoCompleteText(lineTmp.toString());
                    }else {
                        if(historyPos == history.size() - 1)
                            historyPos++;
                        else
                            return;

                        removeLines(lastHistoryEntryUsed);
                        multiLineTmp.delete(0, multiLineTmp.length());

                        String[] lines = currentCommand.split("\n");
                        for(int i = 0;i < lines.length - 1;i++) {
                            String line = lines[i];
                            GraphicsHelper.addText(shell, line, Color.WHITE);
                            highlightSyntaxLastLine();
                            addLine(line, false, false);
                        }
                        lineTmp.delete(0, lineTmp.length());
                        lineTmp.append(lines[lines.length - 1]);
                        if(lines.length > 1)
                            lineTmp.delete(0, 1); //Remove tmp space
                        GraphicsHelper.addText(shell, lineTmp.toString(), Color.WHITE);
                        highlightSyntaxLastLine();
                        updateAutoCompleteText(lineTmp.toString());
                    }
                }else if(e.getKeyCode() == KeyEvent.VK_UP) {
                    if(historyPos > 0) {
                        removeAutoCompleteText();
                        if(historyPos == history.size()) {
                            currentCommand = lineTmp.toString();
                            if(multiLineTmp.length() > 0)
                                currentCommand = multiLineTmp + " " + currentCommand; //Add tmp space for split at "\n" in removeLines()
                            lastHistoryEntryUsed = currentCommand;
                        }

                        historyPos--;

                        String historyRet = history.get(historyPos);
                        String[] lines = historyRet.split("\n");
                        String lastLine = lines[lines.length - 1];

                        lineTmp.delete(0, lineTmp.length());
                        lineTmp.append(lastLine);

                        removeLines(lastHistoryEntryUsed);
                        lastHistoryEntryUsed = historyRet;
                        addLinesWithoutExec(historyRet);
                        updateAutoCompleteText(lineTmp.toString());
                    }
                }
            }
        };
        shell.addKeyListener(shellKeyListener);
        scrollPane.setViewportView(shell);

        initShell(langArgs);
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;

        shell.setFont(new Font(Font.MONOSPACED, Font.PLAIN, fontSize));

        revalidate();

        //Auto scroll
        shell.setCaretPosition(shell.getDocument().getLength());
    }

    private void initShell(String[] langArgs) {
        //Sets System.out
        oldOut = System.out;
        System.setOut(new PrintStream(new OutputStream() {
            //Tmp for multibyte char
            private final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

            private int charsLeftInLogOutput;
            private int type = 0;
            //Colors for the levels
            private final Color[] colors = {Color.WHITE, new Color(63, 63, 255), Color.MAGENTA, Color.GREEN, Color.YELLOW, new Color(255, 127, 0), Color.RED, new Color(127, 0, 0)};

            @Override
            public void write(int b) throws IOException {
                oldOut.write(b);
                byteOut.write(b);
            }

            @Override
            public void flush() throws IOException {
                String output = byteOut.toString();
                byteOut.reset();

                updateOutput(output);

                //Auto scroll
                shell.setCaretPosition(shell.getDocument().getLength());
            }

            private void updateOutput(String output) {
                if(output.isEmpty())
                    return;

                if(charsLeftInLogOutput > 0) {
                    if(output.length() > charsLeftInLogOutput) {
                        GraphicsHelper.addText(shell, output.substring(0, charsLeftInLogOutput), colors[type]);

                        String outputLeft = output.substring(charsLeftInLogOutput);
                        charsLeftInLogOutput = 0;
                        updateOutput(outputLeft);
                    }else {
                        charsLeftInLogOutput -= output.length();

                        GraphicsHelper.addText(shell, output, colors[type]);
                    }

                    return;
                }

                int outputLength = getOutputLength(output);
                if(outputLength == -1) {
                    type = 0;

                    int bracketIndex = output.indexOf('[', 1); //Ignore "[" at start, because it was already tested

                    if(bracketIndex == -1) {
                        GraphicsHelper.addText(shell, output, colors[type]);
                    }else {
                        GraphicsHelper.addText(shell, output.substring(0, bracketIndex), colors[type]);

                        String outputLeft = output.substring(bracketIndex);
                        updateOutput(outputLeft);
                    }

                    return;
                }

                charsLeftInLogOutput = outputLength;

                //Sets color of message after new line
                if(output.startsWith("[" + Level.NOTSET + "]")) {
                    type = 0;
                }else if(output.startsWith("[" + Level.USER + "]")) {
                    type = 1;
                }else if(output.startsWith("[" + Level.DEBUG + "]")) {
                    type = 2;
                }else if(output.startsWith("[" + Level.CONFIG + "]")) {
                    type = 3;
                }else if(output.startsWith("[" + Level.INFO + "]")) {
                    type = 4;
                }else if(output.startsWith("[" + Level.WARNING + "]")) {
                    type = 5;
                }else if(output.startsWith("[" + Level.ERROR + "]")) {
                    type = 6;
                }else if(output.startsWith("[" + Level.CRITICAL + "]")) {
                    type = 7;
                }

                //Extract message from debug output
                output = output.split("]: ", 2)[1];

                if(output.startsWith("[From Lang file]: ")) { //Drop "[From Lang file]: " prefix
                    output = output.substring(18);

                    charsLeftInLogOutput -= 18;
                }

                updateOutput(output);
            }

            private int getOutputLength(String output) {
                if(!output.startsWith("[") || !output.contains("]: "))
                    return -1;

                int msgLenIndex = output.indexOf("][Msg len: ");
                if(msgLenIndex == -1)
                    return -1;

                msgLenIndex += 11; //Index at end of "][Msg len: "
                int endMsgLenIndex = output.indexOf(']', msgLenIndex);
                if(endMsgLenIndex == -1)
                    return -1;

                String msgLen = output.substring(msgLenIndex, endMsgLenIndex);
                try {
                    return Integer.parseInt(msgLen);
                }catch(NumberFormatException e) {
                    return -1;
                }
            }
        }, true));

        lii = Lang.createInterpreterInterface(term, langPlatformAPI, langArgs);
        //Change the "errorOutput" flag to ALL
        lii.setErrorOutputFlag(LangInterpreter.ExecutionFlags.ErrorOutputFlag.ALL);

        lii.addPredefinedFunctions(this);

        printWelcomeText();
    }
    private void printWelcomeText() {
        GraphicsHelper.addText(shell, "Lang-Shell", Color.RED);
        GraphicsHelper.addText(shell, " - Press CTRL + C for cancelling execution or for exiting!\n" +
                "• Copy with (CTRL + SHIFT + C) and paste with (CTRL + SHIT + V)\n" +
                "• Press CTRL + F1 for getting a help popup for the current function\n" +
                "• Press CTRL + S for saving all inputs to a .lang file (Save)\n" +
                "• Press CTRL + SHIFT + S for saving all inputs to a .lang file (Save As...)\n" +
                "• Press CTRL + I for opening the special char input window\n" +
                "• Press CTRL + SHIFT + F for opening a file chooser to insert file paths\n" +
                "• Press UP and DOWN for scrolling through the history\n" +
                "• Press TAB and SHIFT + TAB for scrolling trough auto complete texts\n" +
                "    ◦ Press ENTER for accepting the auto complete text\n" +
                "• Press CTRL + L to clear the screen\n" +
                "• Use func.printHelp() to get information about LangShell functions\n> ", Color.WHITE);
    }

    //Debug functions
    @LangFunction("printHelp")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject printHelpFunction() {
        term.logln(Level.DEBUG, "func.printHelp() # Prints this help text\n" +
                "func.printDebug(value) # Prints debug information about the provided DataObject\n" +
                "func.printTokens(text) # Prints the tokens returned by the LangLexer for the input text\n" +
                "func.printAST(text) # Prints the AST tree returned by the LangParser for the input text\n" +
                "func.setAutoPrintMode(value) # Sets the auto print mode [Value can be one of 'NONE', 'AUTO', and 'DEBUG']", LangShellWindow.class);

        return null;
    }
    @LangFunction("printDebug")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject printDebugFunction(
            @LangParameter("$value") @CallByPointer DataObject pointerObject
    ) {
        DataObject dereferencedVarPointer = pointerObject.getVarPointer().getVar();

        String builder = "Debug[" +
                (dereferencedVarPointer.getVariableName() == null?"<ANONYMOUS>":dereferencedVarPointer.getVariableName()) +
                "]:\n" + getDebugString(dereferencedVarPointer, 4);

        term.logln(Level.DEBUG, builder, LangShellWindow.class);

        return null;
    }
    @LangFunction("printTokens")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject printTokensFunction(
            @LangParameter("$code") @AllowedTypes(DataObject.DataType.TEXT) DataObject codeObject
    ) {
        try(BufferedReader reader = new BufferedReader(new StringReader(codeObject.getText().toString()))) {
            lexer.resetPositionVars();
            List<Token> tokens = lexer.readTokens(reader);

            term.logln(Level.DEBUG, tokens.stream().map(Token::toString).collect(Collectors.joining("\n")), LangShellWindow.class);
        }catch(IOException e) {
            term.logStackTrace(e, LangShellWindow.class);
        }

        return null;
    }
    @LangFunction("printAST")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject printASTFunction(
            @LangParameter("$code") @AllowedTypes(DataObject.DataType.TEXT) DataObject codeObject
    ) {
        try(BufferedReader reader = new BufferedReader(new StringReader(codeObject.getText().toString()))) {
            AbstractSyntaxTree ast = new LangParser().parseLines(reader);

            term.logln(Level.DEBUG, ast.toString(), LangShellWindow.class);
        }catch(IOException e) {
            term.logStackTrace(e, LangShellWindow.class);
        }

        return null;
    }
    @LangFunction("setAutoPrintMode")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject setAutoPrintModeFunction(
            @LangParameter("$value") DataObject valueObject
    ) {
        try {
            LangShellWindow.this.autoPrintMode = AutoPrintMode.valueOf(lii.getInterpreter().conversions.toText(valueObject, CodePosition.EMPTY).toString());
        }catch(IllegalArgumentException e) {
            return lii.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$value\") mode must be one of 'NONE', 'AUTO', 'DEBUG'");
        }

        return null;
    }
    @LangFunction("getParserLineNumber")
    @AllowedTypes(DataObject.DataType.INT)
    @SuppressWarnings("unused")
    public DataObject getParserLineNumberFunction() {
        return new DataObject().setInt(lii.getParserLineNumber());
    }
    @LangFunction("setParserLineNumber")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject setParserLineNumberFunction(
            @LangParameter("$lineNumber") @NumberValue Number number
    ) {
        int lineNumber = number.intValue();
        if(lineNumber < 0)
            return lii.setErrnoErrorObject(InterpretingError.INVALID_ARGUMENTS, "Argument 1 (\"$lineNumber\") must be >= 0");

        lii.setParserLineNumber(lineNumber);

        return null;
    }
    @LangFunction("resetParserLineNumber")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject resetParserLineNumberFunction() {
        lii.resetParserPositionVars();

        return null;
    }
    /**
     * Disable the input() function: It would not work in the LangShell, because the "TermIO-Control" window is not accessible
     */
    @LangFunction("input")
    @AllowedTypes(DataObject.DataType.VOID)
    @SuppressWarnings("unused")
    public DataObject inputFunctionRemoval(
            @LangParameter("$dummy") @VarArgs DataObject dummy
    ) {
        return lii.setErrnoErrorObject(InterpretingError.FUNCTION_NOT_SUPPORTED, "Function not supported in the LangShell");
    }

    private String getDebugString(DataObject dataObject, int maxRecursionDepth) {
        if(dataObject == null)
            return "<NULL>";

        if(maxRecursionDepth < 1)
            return "<Max recursion depth reached>";

        StringBuilder builder = new StringBuilder();
        builder.append("Raw Text: ");
        builder.append(lii.getInterpreter().conversions.toText(dataObject, CodePosition.EMPTY));
        builder.append("\nType: ");
        builder.append(dataObject.getType());
        builder.append("\nFinal: ");
        builder.append(dataObject.isFinalData());
        builder.append("\nStatic: ");
        builder.append(dataObject.isStaticData());
        builder.append("\nCopy static & final modifiers: ");
        builder.append(dataObject.isCopyStaticAndFinalModifiers());
        builder.append("\nLang var: ");
        builder.append(dataObject.isLangVar());
        builder.append("\nVariable Name: ");
        builder.append(dataObject.getVariableName());
        builder.append("\nMember of: ");
        builder.append(dataObject.getMemberOfClass() == null?null:(
                dataObject.getMemberOfClass().getClassName() == null?"<class>":dataObject.getMemberOfClass().getClassName()));
        builder.append("\nVisibility: ");
        builder.append(dataObject.getMemberVisibility());
        builder.append("\nType constraint: ");
        builder.append(dataObject.getTypeConstraint().toTypeConstraintSyntax());
        builder.append("\nAllowed types: ");
        builder.append(dataObject.getTypeConstraint().printAllowedTypes());
        builder.append("\nNot allowed types: ");
        builder.append(dataObject.getTypeConstraint().printNotAllowedTypes());
        switch(dataObject.getType()) {
            case VAR_POINTER:
                builder.append("\nPointing to: {\n");
                String[] debugStringLines = getDebugString(dataObject.getVarPointer().getVar(), maxRecursionDepth - 1).split("\\n");
                for(String debugStringLine:debugStringLines) {
                    builder.append("    ");
                    builder.append(debugStringLine);
                    builder.append("\n");
                }
                builder.append("}");
                break;

            case STRUCT:
                boolean isStructDefinition = dataObject.getStruct().isDefinition();
                builder.append("\nIs struct definition: ");
                builder.append(isStructDefinition);
                builder.append("\nMembers:");
                for(String memberName:dataObject.getStruct().getMemberNames()) {
                    builder.append("\n    ");
                    builder.append(memberName);

                    if(isStructDefinition) {
                        if(dataObject.getStruct().getTypeConstraint(memberName) != null)
                            builder.append(dataObject.getStruct().getTypeConstraint(memberName).toTypeConstraintSyntax());
                    }else {
                        DataObject member = dataObject.getStruct().getMember(memberName);

                        builder.append(": {\n");
                        debugStringLines = getDebugString(member, maxRecursionDepth > 1?1:0).split("\\n");
                        for(String debugStringLine:debugStringLines) {
                            builder.append("        ");
                            builder.append(debugStringLine);
                            builder.append("\n");
                        }
                        builder.append("    }");
                    }
                }
                break;

            case OBJECT:
                boolean isClass = dataObject.getObject().isClass();
                builder.append("\nIs class: ");
                builder.append(isClass);

                builder.append("\nStatic members:");
                for(DataObject staticMember:dataObject.getObject().getStaticMembers()) {
                    builder.append("\n    ");

                    builder.append(staticMember.getMemberVisibility());
                    if(staticMember.getMemberOfClass() != null) {
                        builder.append("(");
                        builder.append(staticMember.getMemberOfClass().getClassName() == null?"<class>":
                                staticMember.getMemberOfClass().getClassName());
                        builder.append(")");
                    }
                    builder.append(" ");

                    builder.append(staticMember.getVariableName());

                    if(!staticMember.getTypeConstraint().equals(DataObject.getTypeConstraintFor(staticMember.getVariableName())))
                        builder.append(staticMember.getTypeConstraint().toTypeConstraintSyntax());

                    builder.append(": {\n");
                    debugStringLines = getDebugString(staticMember, maxRecursionDepth > 1?1:0).split("\\n");
                    for(String debugStringLine:debugStringLines) {
                        builder.append("        ");
                        builder.append(debugStringLine);
                        builder.append("\n");
                    }
                    builder.append("    }");
                }

                builder.append("\nMembers:");
                for(int i = 0;i < dataObject.getObject().getMemberNames().length;i++) {
                    String memberName = dataObject.getObject().getMemberNames()[i];
                    builder.append("\n    ");

                    builder.append(dataObject.getObject().getMemberVisibility()[i]);
                    if(dataObject.getObject().getMemberOfClass()[i] != null) {
                        builder.append("(");
                        builder.append(dataObject.getObject().getMemberOfClass()[i].getClassName() == null?"<class>":
                                dataObject.getObject().getMemberOfClass()[i].getClassName());
                        builder.append(")");
                    }
                    builder.append(" ");

                    if(dataObject.getObject().getMemberFinalFlags()[i])
                        builder.append("final:");

                    builder.append(memberName);

                    if(isClass) {
                        if(dataObject.getObject().getMemberTypeConstraints()[i] != null)
                            builder.append(dataObject.getObject().getMemberTypeConstraints()[i].toTypeConstraintSyntax());
                    }else {
                        DataObject member = dataObject.getObject().getMember(memberName);

                        builder.append(": {\n");
                        debugStringLines = getDebugString(member, maxRecursionDepth > 1?1:0).toString().split("\\n");
                        for(String debugStringLine:debugStringLines) {
                            builder.append("        ");
                            builder.append(debugStringLine);
                            builder.append("\n");
                        }
                        builder.append("    }");
                    }
                }

                builder.append("\nConstructors:");
                for(int i = 0;i < dataObject.getObject().getConstructors().getOverloadedFunctionCount();i++) {
                    DataObject.FunctionPointerObject.InternalFunction constructor = dataObject.getObject().getConstructors().getFunction(i);

                    builder.append("\n    ");

                    switch(Optional.ofNullable(constructor.getMemberVisibility()).orElse(DataObject.Visibility.PUBLIC)) {
                        case PUBLIC:
                            builder.append("+");
                            break;

                        case PROTECTED:
                            builder.append("~");
                            break;

                        case PRIVATE:
                            builder.append("-");
                            break;
                    }

                    builder.append("construct");
                    builder.append(constructor.toFunctionSignatureSyntax());


                    builder.append(": {\n");
                    debugStringLines = getDebugString(new DataObject().setFunctionPointer(dataObject.getObject().
                                    getConstructors().withFunctions(Arrays.asList(constructor))),
                            maxRecursionDepth > 1?1:0).split("\\n");
                    for(String debugStringLine:debugStringLines) {
                        builder.append("        ");
                        builder.append(debugStringLine);
                        builder.append("\n");
                    }
                    builder.append("    }");
                }

                builder.append("\nMethods:");
                dataObject.getObject().getMethods().forEach((methodName, overloadedMethodDefinitions) -> {
                    for(int i = 0;i < overloadedMethodDefinitions.getOverloadedFunctionCount();i++) {
                        DataObject.FunctionPointerObject.InternalFunction methodDefinition = overloadedMethodDefinitions.getFunction(i);

                        builder.append("\n    ");

                        switch(Optional.ofNullable(methodDefinition.getMemberVisibility()).orElse(DataObject.Visibility.PUBLIC)) {
                            case PUBLIC:
                                builder.append("+");
                                break;

                            case PROTECTED:
                                builder.append("~");
                                break;

                            case PRIVATE:
                                builder.append("-");
                                break;
                        }

                        builder.append(methodName);
                        builder.append(methodDefinition.toFunctionSignatureSyntax());


                        builder.append(": {\n");
                        String[] debugStringLinesMethod = getDebugString(new DataObject().setFunctionPointer(overloadedMethodDefinitions.
                                        withFunctions(Arrays.asList(methodDefinition))),
                                maxRecursionDepth > 1?1:0).split("\\n");
                        for(String debugStringLine:debugStringLinesMethod) {
                            builder.append("        ");
                            builder.append(debugStringLine);
                            builder.append("\n");
                        }
                        builder.append("    }");
                    }
                });

                builder.append("\nParrent classes:");
                for(int i = 0;i < dataObject.getObject().getParentClasses().length;i++) {
                    DataObject.LangObject parentClass = dataObject.getObject().getParentClasses()[i];

                    builder.append("\n    ");
                    builder.append(i);
                    builder.append(": {\n");
                    String[] debugStringLinesMethod = getDebugString(new DataObject().setObject(parentClass),
                            maxRecursionDepth > 1?1:0).split("\\n");
                    for(String debugStringLine:debugStringLinesMethod) {
                        builder.append("        ");
                        builder.append(debugStringLine);
                        builder.append("\n");
                    }
                    builder.append("    }");
                }
                break;

            case BYTE_BUFFER:
                builder.append("\nSize: ");
                builder.append(dataObject.getByteBuffer().length);
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
                    debugStringLines = getDebugString(ele, maxRecursionDepth > 1?1:0).split("\\n");
                    for(String debugStringLine:debugStringLines) {
                        builder.append("        ");
                        builder.append(debugStringLine);
                        builder.append("\n");
                    }
                    builder.append("    }");
                }
                break;

            case LIST:
                builder.append("\nSize: ");
                builder.append(dataObject.getList().size());
                builder.append("\nElements:");
                for(int i = 0;i < dataObject.getList().size();i++) {
                    DataObject ele = dataObject.getList().get(i);
                    builder.append("\n    list(");
                    builder.append(i);
                    builder.append("): {\n");
                    debugStringLines = getDebugString(ele, maxRecursionDepth > 1?1:0).split("\\n");
                    for(String debugStringLine:debugStringLines) {
                        builder.append("        ");
                        builder.append(debugStringLine);
                        builder.append("\n");
                    }
                    builder.append("    }");
                }
                break;

            case FUNCTION_POINTER:
                builder.append("\nFunction-Name: ");
                builder.append(dataObject.getFunctionPointer().getFunctionName());
                builder.append("\nFunction info: ");
                builder.append(dataObject.getFunctionPointer().getFunctionInfo());
                builder.append("\nIs bound: ");
                builder.append(dataObject.getFunctionPointer().getThisObject() != null);
                builder.append("\nLinker Function: ");
                builder.append(dataObject.getFunctionPointer().isLinkerFunction());
                builder.append("\nDeprecated: ");
                boolean deprecated = dataObject.getFunctionPointer().isDeprecated();
                builder.append(deprecated);
                if(deprecated) {
                    builder.append("\n    Will be removed in: ");
                    builder.append(dataObject.getFunctionPointer().getDeprecatedRemoveVersion());
                    builder.append("\n    Replacement function: ");
                    builder.append(dataObject.getFunctionPointer().getDeprecatedReplacementFunction());
                }
                builder.append("\nInternal Functions: {");
                for(int i = 0;i < dataObject.getFunctionPointer().getOverloadedFunctionCount();i++) {
                    DataObject.FunctionPointerObject.InternalFunction internalFunction = dataObject.getFunctionPointer().getFunction(i);
                    builder.append("\n    ").append(i).append(": ");
                    builder.append("\n    Super level: ");
                    builder.append(internalFunction.getSuperLevel());
                    builder.append("\n    Function-Type: ");
                    builder.append(internalFunction.getFunctionPointerType());
                    builder.append("\n    Lang-Path: ");
                    builder.append(internalFunction.getLangPath());
                    builder.append("\n    Lang-File: ");
                    builder.append(internalFunction.getLangFile());
                    builder.append("\n    Member of: ");
                    builder.append(internalFunction.getMemberOfClass() == null?null:(
                            internalFunction.getMemberOfClass().getClassName() == null?"<class>":internalFunction.getMemberOfClass().getClassName()));
                    builder.append("\n    Visibility: ");
                    builder.append(internalFunction.getMemberVisibility());
                    builder.append("\n    Normal Function: ");
                    LangNormalFunction normalFunction = internalFunction.getNormalFunction();
                    if(normalFunction == null) {
                        builder.append(normalFunction);
                    }else {
                        builder.append("{");
                        builder.append("\n        Raw String: ");
                        builder.append(normalFunction);
                        builder.append("\n        Function signature:");
                        {
                            List<DataObject> parameterList = normalFunction.getParameterList();
                            List<DataObject.DataTypeConstraint> paramaterDataTypeConstraintList = normalFunction.getParameterDataTypeConstraintList();
                            List<String> parameterInfoList = normalFunction.getParameterInfoList();
                            builder.append("\n            Function Signature: ");
                            builder.append(normalFunction.toFunctionSignatureSyntax());
                            builder.append("\n        Combinator Function: ");
                            builder.append(normalFunction.isCombinatorFunction());
                            builder.append("\n        Combinator Function Call Count: ");
                            builder.append(normalFunction.getCombinatorFunctionCallCount());
                            builder.append("\n        Combinator Function Arguments: ");
                            builder.append(normalFunction.getCombinatorProvidedArgumentList());
                            builder.append("\n            Return Value Type Constraint: ");
                            builder.append(normalFunction.getReturnValueTypeConstraint().toTypeConstraintSyntax());
                            builder.append("\n            Parameters: ");
                            for(int j = 0;j < parameterList.size();j++) {
                                builder.append("\n                Parameter ");
                                builder.append(j + 1);
                                builder.append(" (\"");
                                builder.append(parameterList.get(j).getVariableName());
                                builder.append("\"): ");
                                builder.append("\n                    Data type constraint: ");
                                builder.append(paramaterDataTypeConstraintList.get(j).toTypeConstraintSyntax());
                                builder.append("\n                    Parameter info: ");
                                builder.append(parameterInfoList.get(j));
                            }
                        }
                        builder.append("\n        Function Body: {");
                        String[] tokens = normalFunction.getFunctionBody().toString().split("\\n");
                        for(String token:tokens) {
                            builder.append("\n            ");
                            builder.append(token);
                        }
                        builder.append("\n    }");
                    }
                    builder.append("\n    Native Function: ");
                    LangNativeFunction nativeFunction = internalFunction.getNativeFunction();
                    if(nativeFunction == null) {
                        builder.append(nativeFunction);
                    }else {
                        builder.append("{");
                        builder.append("\n        Raw String: ");
                        builder.append(nativeFunction);
                        builder.append("\n        Function name: ");
                        builder.append(nativeFunction.getFunctionName());
                        builder.append("\n        Function signatures:");
                        List<DataObject> parameterList = nativeFunction.getParameterList();
                        List<DataObject.DataTypeConstraint> paramaterDataTypeConstraintList = nativeFunction.getParameterDataTypeConstraintList();
                        List<String> parameterInfoList = nativeFunction.getParameterInfoList();
                        builder.append("\n        Is method: ");
                        builder.append(nativeFunction.isMethod());
                        builder.append("\n        Combinator Function: ");
                        builder.append(nativeFunction.isCombinatorFunction());
                        builder.append("\n        Combinator Function Call Count: ");
                        builder.append(nativeFunction.getCombinatorFunctionCallCount());
                        builder.append("\n        Combinator Function Arguments: ");
                        builder.append(nativeFunction.getCombinatorProvidedArgumentList());
                        builder.append("\n        Function Signature: ");
                        builder.append(nativeFunction.toFunctionSignatureSyntax());
                        builder.append("\n        Return Value Type Constraint: ");
                        builder.append(nativeFunction.getReturnValueTypeConstraint().toTypeConstraintSyntax());
                        builder.append("\n        Parameters: ");
                        for(int j = 0;j < parameterList.size();j++) {
                            builder.append("\n            Parameter ");
                            builder.append(j + 1);
                            builder.append(" (\"");
                            builder.append(parameterList.get(j).getVariableName());
                            builder.append("\"): ");
                            builder.append("\n                Data type constraint: ");
                            builder.append(paramaterDataTypeConstraintList.get(j).toTypeConstraintSyntax());
                            builder.append("\n                Parameter info: ");
                            builder.append(parameterInfoList.get(j));
                        }
                        builder.append("\n    }");
                    }
                    builder.append("\n");
                }
                builder.append("\n}");
                break;

            case ERROR:
                builder.append("\nError-Code: ");
                builder.append(dataObject.getError().getErrno());
                builder.append("\nError-Text: ");
                builder.append(dataObject.getError().getErrtxt());
                builder.append("\nError-Message: ");
                builder.append(dataObject.getError().getMessage());
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

            //Skip "> "
            startOfLine += line.indexOf('>') + 2;
            line = line.substring(line.indexOf('>') + 2);

            String code = (multiLineTmp.length() == 0?"":(multiLineTmp.toString())) + line;
            List<Token> tokens;
            try(BufferedReader reader = new BufferedReader(new StringReader(code))) {
                lexer.resetPositionVars();

                tokens = lexer.readTokens(reader);
            }catch(IOException e) {
                term.logStackTrace(e, LangShellWindow.class);

                return;
            }

            //Extract tokens for last line
            int lineStartIndex = -2;
            for(int i = tokens.size() - 1;i >= 0;i--) {
                if(tokens.get(i).getTokenType() == Token.TokenType.EOL) {
                    if(lineStartIndex == -2) {
                        //Skip EOL after last line
                        lineStartIndex = -1;
                    }else {
                        lineStartIndex = i + 1;
                        break;
                    }
                }
            }

            if(lineStartIndex < 0)
                lineStartIndex = 0;

            if(lineStartIndex >= tokens.size())
                return;

            List<Token> tokensBeforeCurrentLine = new ArrayList<>(tokens.subList(0, lineStartIndex));
            tokens = new ArrayList<>(tokens.subList(lineStartIndex, tokens.size()));

            //Add not yet closed START_COMMENT and START_DOC_COMMENT tokens to current line
            for(int i = tokensBeforeCurrentLine.size() - 1;i >= 0;i--) {
                Token t = tokensBeforeCurrentLine.get(i);
                Token.TokenType tokenType = t.getTokenType();
                if(tokenType == Token.TokenType.END_COMMENT)
                    break;

                if(tokenType == Token.TokenType.START_COMMENT || tokenType == Token.TokenType.START_DOC_COMMENT) {
                    tokens.add(0, new Token(CodePosition.EMPTY, t.getValue(), t.getTokenType()));

                    break;
                }
            }

            doc.remove(startOfLine, doc.getLength() - startOfLine);

            //TODO:
            //- dereferencingAndReferencingOperatorFlag: OPERATOR_BRACKET_COLOR;

            boolean lineEndsWithBracket = false;
            for(int i = tokens.size() - 1;i >= 0;i--) {
                if(tokens.get(i).getTokenType() == Token.TokenType.WHITESPACE ||
                        tokens.get(i).getTokenType() == Token.TokenType.EOL ||
                        tokens.get(i).getTokenType() == Token.TokenType.EOF)
                    continue;

                if(tokens.get(i).getTokenType() == Token.TokenType.OPENING_BLOCK_BRACKET &&
                        tokens.get(i).getValue().equals("{")) {
                    lineEndsWithBracket = true;
                    break;
                }

                break;
            }

            //Split Identifier tokens to add different color for module prefix
            for(int i = tokens.size() - 1;i >= 0;i--) {
                Token t = tokens.get(i);
                if(t.getTokenType() == Token.TokenType.IDENTIFIER && t.getValue().startsWith("[[") &&
                        t.getValue().contains("]]::")) {
                    int modulePrefixEndIndex = t.getValue().indexOf("]]::") + 4;
                    tokens.set(i, new Token(t.pos, t.getValue().substring(0, modulePrefixEndIndex),
                            Token.TokenType.IDENTIFIER));
                    tokens.add(i + 1, new Token(t.pos, t.getValue().substring(modulePrefixEndIndex),
                            Token.TokenType.IDENTIFIER));
                }
            }

            boolean docCommentFlag = false;
            boolean commentFlag = false;
            int columnFromIndex = 0;
            for(Token t:tokens) {
                int tokenSize = 0;
                Color col = NORMAL_COLOR;

                switch(t.getTokenType()) {
                    case START_COMMENT:
                        commentFlag = true;

                        if(!t.getPos().equals(CodePosition.EMPTY))
                            tokenSize = t.getValue().length();

                        break;

                    case START_DOC_COMMENT:
                        docCommentFlag = true;

                        if(!t.getPos().equals(CodePosition.EMPTY))
                            tokenSize = t.getValue().length();

                        break;

                    case END_COMMENT:
                        commentFlag = false;
                        docCommentFlag = false;

                        tokenSize = t.getValue().length();

                        break;

                    case WHITESPACE:
                        tokenSize = t.getValue().length();

                        break;

                    case OPERATOR:
                    case OPENING_BRACKET:
                    case CLOSING_BRACKET:
                    case OPENING_BLOCK_BRACKET:
                    case CLOSING_BLOCK_BRACKET:
                    case ASSIGNMENT:
                    case ARGUMENT_SEPARATOR:
                        tokenSize = t.getValue().length();

                        col = OPERATOR_BRACKET_COLOR;

                        break;

                    case IDENTIFIER:
                        tokenSize = t.getValue().length();

                        if(t.getValue().startsWith("[[") && t.getValue().contains("]]::"))
                            col = MODULE_PREFIX_COLOR;
                        else if(t.getValue().contains("$") || t.getValue().contains("&"))
                            col = VARIABLE_IDENTIFIER_COLOR;
                        else
                            col = FUNCTION_COLOR;

                        break;

                    case LITERAL_NUMBER:
                        tokenSize = t.getValue().length();

                        col = NUMBER_COLOR;

                        break;

                    case START_MULTILINE_TEXT:
                    case END_MULTILINE_TEXT:
                    case SINGLE_LINE_TEXT_QUOTES:
                    case LITERAL_TEXT:
                        tokenSize = t.getValue().length();

                        col = TEXT_COLOR;

                        break;

                    case LITERAL_NULL:
                    case ESCAPE_SEQUENCE:
                    case LINE_CONTINUATION:
                        tokenSize = t.getValue().length();

                        col = KEYWORD_COLOR;

                        break;

                    case PARSER_FUNCTION_IDENTIFIER:
                        tokenSize = t.getValue().length();

                        col = FUNCTION_COLOR;

                        break;

                    case OTHER:
                        tokenSize = t.getValue().length();

                        if(t.getValue().equals("return") || t.getValue().equals("throw") ||
                                t.getValue().equals("class") || t.getValue().equals("struct") ||
                                t.getValue().equals("function") || t.getValue().equals("overload") ||
                                t.getValue().equals("combinator") ||
                                t.getValue().equals("super") || t.getValue().equals("override") ||
                                t.getValue().equals("final") || t.getValue().equals("static") ||
                                t.getValue().equals("construct") || t.getValue().equals("private") ||
                                t.getValue().equals("protected") || t.getValue().equals("public") ||
                                t.getValue().startsWith("con.") ||
                                (lineEndsWithBracket && (t.getValue().equals("break") ||
                                        t.getValue().equals("catch") || t.getValue().equals("continue") ||
                                        t.getValue().equals("elif") || t.getValue().equals("else") ||
                                        t.getValue().equals("finally") || t.getValue().equals("foreach") ||
                                        t.getValue().equals("if") || t.getValue().equals("loop") ||
                                        t.getValue().equals("nontry") || t.getValue().equals("repeat") ||
                                        t.getValue().equals("softtry") || t.getValue().equals("try") ||
                                        t.getValue().equals("until") || t.getValue().equals("while"))))
                            col = KEYWORD_COLOR;
                        else if(t.getValue().startsWith("fp.") || t.getValue().startsWith("mp.") ||
                                t.getValue().startsWith("fn.") || t.getValue().startsWith("ln.") ||
                                t.getValue().startsWith("func.") || t.getValue().startsWith("linker.") ||
                                t.getValue().startsWith("parser."))
                            col = FUNCTION_COLOR;
                        else
                            col = MIGHT_BE_TEXT_COLOR;

                        break;

                    case EOF:
                    case EOL:
                    case LEXER_ERROR:
                        break;
                }

                if(tokenSize == 0)
                    continue;

                //Override color with comment color if inside comment
                if(docCommentFlag)
                    col = DOC_COMMENT_COLOR;
                else if(commentFlag)
                    col = COMMENT_COLOR;

                if(columnFromIndex >= line.length())
                    break;

                String token = line.substring(columnFromIndex, Math.min(columnFromIndex + tokenSize, line.length()));
                columnFromIndex += tokenSize;
                GraphicsHelper.addText(shell, token, col);
                lastColor = col;

                if(columnFromIndex >= line.length())
                    break;
            }

            if(columnFromIndex < line.length() - 1) {
                Color col = NORMAL_COLOR;

                String token = line.substring(columnFromIndex);
                GraphicsHelper.addText(shell, token, col);
                lastColor = col;
            }
        }catch(BadLocationException ignore) {}

        //Auto scroll
        shell.setCaretPosition(shell.getDocument().getLength());
    }

    private void updateAutoCompleteText(String line) {
        Color col = lastColor.darker().darker();
        if(col.equals(lastColor)) //Color is already the darkest
            col = lastColor.brighter().brighter();

        if(line.startsWith("lang.") && !line.contains(" ")) {
            int indexConNameStart = line.indexOf('.') + 1;
            String conNameStart = indexConNameStart == line.length()?"":line.substring(indexConNameStart);
            List<String> autoCompletes = langDataAndExecutionFlags.stream().
                    filter(conName -> conName.startsWith(conNameStart) && !conName.equals(conNameStart)).
                    collect(Collectors.toList());
            if(autoCompletes.isEmpty())
                return;
            autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
            if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size())
                autoCompleteText = "";
            else
                autoCompleteText = autoCompletes.get(autoCompletePos).substring(conNameStart.length());
        }else {
            String[] tokens = line.split(".(?=\\[\\[|(\\[\\[\\w+\\]\\]::)(\\$|&|fp\\.)|(?<!\\w]]::)(\\$|&|fp\\.)|func\\.|fn\\.|linker\\.|ln\\.|con\\.|parser\\.)");
            if(tokens.length == 0)
                return;

            String lastToken = tokens[tokens.length - 1];
            if(lastToken.matches("(\\[\\[\\w+\\]\\]::)?(\\$|&|fp\\.).*")) {
                Map<String, DataObject> moduleVariables = null;
                if(lastToken.matches("(\\[\\[\\w+\\]\\]::).*")) {
                    int moduleIdentifierEndIndex = lastToken.indexOf(']');
                    String moduleName = lastToken.substring(2, moduleIdentifierEndIndex);
                    lastToken = lastToken.substring(moduleIdentifierEndIndex + 4);

                    moduleVariables = lii.getModuleExportedVariables(moduleName);
                }

                final int appendClosingBracketCount;
                if(lastToken.matches("\\$\\**\\[*\\w*")) {
                    //Handle var pointer referencing and dereferencing "$*" and "$["

                    lastToken = lastToken.replace("*", ""); //Ignore "*"

                    int oldLen = lastToken.length();
                    lastToken = lastToken.replace("[", ""); //Ignore "["
                    int diff = oldLen - lastToken.length();
                    if(diff == 0 && !lastToken.isEmpty())
                        appendClosingBracketCount = -1;
                    else
                        appendClosingBracketCount = diff;
                }else {
                    appendClosingBracketCount = -1;
                }

                final String lastTokenCopy = lastToken;
                List<String> autoCompletes = (moduleVariables == null?lii.getVarMap():moduleVariables).keySet().stream().filter(varName -> {
                    int oldLen = varName.length();
                    varName = varName.replace("[", "");

                    return (oldLen == varName.length() || appendClosingBracketCount > -1) && varName.startsWith(lastTokenCopy) && !varName.equals(lastTokenCopy);
                }).sorted().collect(Collectors.toList());
                if(autoCompletes.isEmpty())
                    return;
                autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
                if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size()) {
                    autoCompleteText = "";
                }else {
                    autoCompleteText = autoCompletes.get(autoCompletePos).replace("]", "");

                    int openingBracketCountVarName = (int)autoCompleteText.chars().filter(c -> c == '[').count();
                    int diff = Math.max(0, openingBracketCountVarName - Math.max(0, appendClosingBracketCount));
                    for(int i = 0;i < diff;i++)
                        autoCompleteText = "$[" + autoCompleteText.substring(1);

                    autoCompleteText = autoCompleteText.substring(openingBracketCountVarName - diff + lastTokenCopy.length()) + (lastTokenCopy.startsWith("fp.")?"(":"");

                    for(int i = 0;i < Math.max(appendClosingBracketCount, openingBracketCountVarName);i++)
                        autoCompleteText += "]";
                }
            }else if(lastToken.matches("\\[\\[.*")) {
                final String lastTokenCopy = lastToken.substring(2); //Remove "[["

                List<String> autoCompletes = lii.getModules().keySet().stream().filter(moduleName -> {
                    return moduleName.startsWith(lastTokenCopy);
                }).sorted().collect(Collectors.toList());
                if(autoCompletes.isEmpty())
                    return;
                autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
                if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size()) {
                    autoCompleteText = "";
                }else {
                    autoCompleteText = autoCompletes.get(autoCompletePos).substring(lastTokenCopy.length()) + "]]::";
                }
            }else if(lastToken.matches("(linker|ln)\\.unloadModule.*")) {
                int indexArgumentNameStart = lastToken.indexOf('.') + 13;
                String argumentStart = indexArgumentNameStart == lastToken.length()?"":lastToken.substring(indexArgumentNameStart);
                boolean hasParentheses = argumentStart.startsWith("(");
                if(hasParentheses)
                    argumentStart = argumentStart.substring(1);

                final String argumentStartCopy = argumentStart;
                List<String> autoCompletes = lii.getModules().keySet().stream().filter(moduleName -> {
                    return moduleName.startsWith(argumentStartCopy);
                }).sorted().collect(Collectors.toList());
                if(autoCompletes.isEmpty())
                    return;
                autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
                if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size()) {
                    autoCompleteText = "";
                }else {
                    autoCompleteText = (hasParentheses?"":"(") + autoCompletes.get(autoCompletePos).substring(argumentStartCopy.length()) + ")";
                }
            }else if(lastToken.matches("(func|fn|linker|ln)\\..*")) {
                boolean isLinkerFunction = lastToken.startsWith("linker.") || lastToken.startsWith("ln.");
                int indexFunctionNameStart = lastToken.indexOf('.') + 1;
                String functionNameStart = indexFunctionNameStart == lastToken.length()?"":lastToken.substring(indexFunctionNameStart);
                List<String> autoCompletes = lii.getPredefinedFunctions().entrySet().stream().filter(entry -> {
                            return entry.getValue().isLinkerFunction() == isLinkerFunction;
                        }).map(Entry::getKey).filter(functionName ->
                                functionName.startsWith(functionNameStart) && !functionName.equals(functionNameStart)).
                        sorted().collect(Collectors.toList());

                if(autoCompletes.contains("setAutoPrintMode")) {
                    autoCompletes = new LinkedList<>(autoCompletes);

                    int index = autoCompletes.indexOf("setAutoPrintMode");

                    //Replace original
                    autoCompletes.set(index, "setAutoPrintMode(NONE)");

                    //Add other modes
                    autoCompletes.add(index + 1, "setAutoPrintMode(AUTO)");
                    autoCompletes.add(index + 2, "setAutoPrintMode(DEBUG)");
                }

                if(autoCompletes.isEmpty())
                    return;
                autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
                if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size()) {
                    autoCompleteText = "";
                }else {
                    String autoComplete = autoCompletes.get(autoCompletePos);
                    autoCompleteText = autoComplete.substring(functionNameStart.length());

                    if(!autoComplete.startsWith("setAutoPrintMode")) {
                        //Mark deprecated function
                        if(lii.getPredefinedFunctions().get(functionNameStart + autoCompleteText).isDeprecated())
                            col = Color.RED.darker().darker();

                        autoCompleteText += "(";
                    }
                }
            }else if(lastToken.matches("con\\..*")) {
                int indexConNameStart = lastToken.indexOf('.') + 1;
                String conNameStart = indexConNameStart == lastToken.length()?"":lastToken.substring(indexConNameStart);
                List<String> autoCompletes = controlFlowStatements.stream().
                        filter(conName -> conName.startsWith(conNameStart) && !conName.equals(conNameStart)).
                        collect(Collectors.toList());
                if(autoCompletes.isEmpty())
                    return;
                autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
                if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size())
                    autoCompleteText = "";
                else
                    autoCompleteText = autoCompletes.get(autoCompletePos).substring(conNameStart.length());
            }else if(lastToken.matches("parser\\..*")) {
                int indexConNameStart = lastToken.indexOf('.') + 1;
                String functionNameStart = indexConNameStart == lastToken.length()?"":lastToken.substring(indexConNameStart);
                List<String> autoCompletes = parserFunctions.stream().
                        filter(functionName -> functionName.startsWith(functionNameStart) && !functionName.equals(functionNameStart)).
                        collect(Collectors.toList());
                if(autoCompletes.isEmpty())
                    return;
                autoCompletePos = Math.max(-1, Math.min(autoCompletePos, autoCompletes.size()));
                if(autoCompletePos < 0 || autoCompletePos >= autoCompletes.size())
                    autoCompleteText = "";
                else
                    autoCompleteText = autoCompletes.get(autoCompletePos).substring(functionNameStart.length());
            }else {
                return;
            }
        }

        GraphicsHelper.addText(shell, autoCompleteText, col);
    }
    private void removeAutoCompleteText() {
        try {
            Document doc = shell.getDocument();
            int startOfAutoComplete = doc.getLength() - autoCompleteText.length();
            doc.remove(startOfAutoComplete, autoCompleteText.length());
        }catch(BadLocationException ignored) {}
        autoCompleteText = "";
        autoCompletePos = 0;
    }

    private void addToHistory(String str) {
        if(!str.trim().isEmpty() && (history.isEmpty() || !history.get(history.size() - 1).equals(str)))
            history.add(str);

        historyPos = history.size();
        currentCommand = "";
    }

    private void resetAddLineFlags() {
        flagMultilineText = flagLineContinuation = false;
    }
    private void removeLines(String str) {
        resetAddLineFlags();
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
            }catch(BadLocationException ignore) {}
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

                addLine(line, false, false);
            }
            lastLine = lines[lines.length - 1];
        }

        GraphicsHelper.addText(shell, lastLine, Color.WHITE);
        highlightSyntaxLastLine();
    }
    private boolean containsMultilineText(String line) {
        while(line.contains("{{{")) {
            //Ignore escaped multiline text start sequences
            int startIndex = line.indexOf("{{{");
            if(startIndex > 0 && !LangUtils.isBackslashAtIndexEscaped(line, startIndex - 1)) {
                line = line.substring(startIndex + 3);
                continue;
            }

            int index = line.indexOf("}}}");
            if(index == -1)
                return true;

            line = line.substring(index + 3);
        }

        return false;
    }
    private boolean hasMultilineTextEnd(String line) {
        while(line.contains("}}}")) {
            int index = line.indexOf("{{{");
            if(index == -1)
                return true;

            line = line.substring(index + 3);
        }

        return false;
    }
    private void addLine(String line, boolean addToExecutionQueueOrExecute, boolean addNewLinePromptForLinesPutInExecutionQueue) {
        if(!flagMultilineText && !flagLineContinuation && indent == 0) {
            GraphicsHelper.addText(shell, "\n", Color.WHITE);

            flagMultilineText = containsMultilineText(line);
            if(!flagMultilineText)
                flagLineContinuation = line.endsWith("\\");
            if(line.trim().endsWith("{") || (line.trim().startsWith("con.") && !line.trim().startsWith("con.end") && !line.trim().startsWith("con.break") &&
                    !line.trim().startsWith("con.continue")) ||
                    flagMultilineText || flagLineContinuation) {
                indent++;
                multiLineTmp.append(line);
                multiLineTmp.append("\n");

                GraphicsHelper.addText(shell, "    > ", Color.WHITE);
            }else {
                addToHistory(line);
                if(addToExecutionQueueOrExecute) {
                    executionQueue.add(line);
                    if(addNewLinePromptForLinesPutInExecutionQueue)
                        GraphicsHelper.addText(shell, "> ", Color.WHITE);
                }else {
                    executeCode(line);
                }
            }
        }else {
            if(!flagMultilineText) {
                flagMultilineText = containsMultilineText(line);
                if(flagMultilineText) {
                    if(flagLineContinuation)
                        flagLineContinuation = false;
                    else
                        indent++;
                }
            }

            if(!flagMultilineText && (line.trim().endsWith("{") || line.trim().startsWith("con.if") || line.trim().startsWith("con.loop") || line.trim().startsWith("con.while") ||
                    line.trim().startsWith("con.until") || line.trim().startsWith("con.repeat") || line.trim().startsWith("con.foreach") || line.trim().startsWith("con.try") ||
                    line.trim().startsWith("con.softtry") || line.trim().startsWith("con.nontry")))
                indent++;

            multiLineTmp.append(line);
            multiLineTmp.append("\n");

            if(!flagMultilineText && (line.trim().startsWith("}") || (line.trim().startsWith("con.") && !line.trim().startsWith("con.loop") && !line.trim().startsWith("con.while") &&
                    !line.trim().startsWith("con.until") && !line.trim().startsWith("con.repeat") && !line.trim().startsWith("con.foreach") && !line.trim().startsWith("con.if") &&
                    !line.trim().startsWith("con.try") && !line.trim().startsWith("con.starttry") && !line.trim().startsWith("con.nontry") && !line.trim().startsWith("con.break") &&
                    !line.trim().startsWith("con.continue")))) {
                indent--;

                if(line.trim().startsWith("con.") && !line.trim().startsWith("con.end"))
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
                }catch(BadLocationException ignored) {}
            }

            if(flagMultilineText && hasMultilineTextEnd(line)) {
                flagMultilineText = false;
                indent--;

                //Remove the first indent from actual line
                try {
                    Document doc = shell.getDocument();
                    int startOfLine;
                    for(startOfLine = doc.getLength() - 1;startOfLine > 0;startOfLine--)
                        if(doc.getText(startOfLine, 1).charAt(0) == '\n')
                            break;
                    startOfLine++; //The line starts on char after '\n'
                    doc.remove(startOfLine, 4);
                }catch(BadLocationException ignored) {}
            }

            if(!flagMultilineText) {
                if(flagLineContinuation) {
                    flagLineContinuation = line.endsWith("\\");
                    if(!flagLineContinuation)
                        indent--;
                }else {
                    flagLineContinuation = line.endsWith("\\");
                    if(flagLineContinuation)
                        indent++;
                }
            }

            GraphicsHelper.addText(shell, "\n", Color.WHITE);
            if(indent < 1) {
                indent = 0;
                String multiLineTmpString = multiLineTmp.toString();
                addToHistory(multiLineTmpString.substring(0, multiLineTmpString.length() - 1)); //Remove "\n"

                String code = multiLineTmp.toString();
                if(addToExecutionQueueOrExecute) {
                    executionQueue.add(code);
                    if(addNewLinePromptForLinesPutInExecutionQueue)
                        GraphicsHelper.addText(shell, "> ", Color.WHITE);
                }else {
                    executeCode(code);
                }

                multiLineTmp.delete(0, multiLineTmp.length());
                currentCommand = "";
            }else {
                for(int i = 0;i < indent;i++)
                    GraphicsHelper.addText(shell, "    ", Color.WHITE);
                GraphicsHelper.addText(shell, "> ", Color.WHITE);
            }
        }
    }

    private void executeCode(String code) {
        if(flagRunning) {
            term.logln(Level.ERROR, "The interpreter is already executing stuff!\nPress CTRL + C for stopping the execution.", LangShellWindow.class);
        }else {
            flagRunning = true;
            langFileOutputBuilder.append(code).append('\n');
            Thread t = new Thread(() -> {
                try {
                    DataObject lastVal = lii.exec(code);
                    if(autoPrintMode == AutoPrintMode.AUTO)
                        GraphicsHelper.addText(shell, " ==> " + (lastVal == null?null:lii.getInterpreter().conversions.
                                toText(lastVal, CodePosition.EMPTY)) + "\n", Color.PINK);
                    else if(autoPrintMode == AutoPrintMode.DEBUG)
                        GraphicsHelper.addText(shell, " ==> " + getDebugString(lastVal, 4) + "\n", Color.PINK);
                }catch(IOException e) {
                    term.logStackTrace(e, LangShellWindow.class);
                }catch(LangInterpreter.StoppedException e) {
                    term.logStackTrace(e, LangShellWindow.class);
                    lii.resetStopFlag();
                }
                GraphicsHelper.addText(shell, "> ", Color.WHITE);

                flagRunning = false;
            });
            t.setDaemon(true);
            t.start();
        }
    }

    private void executeCodeFromExecutionQueue() {
        if(flagRunning) {
            term.logln(Level.ERROR, "The interpreter is already executing stuff!\nPress CTRL + C for stopping the execution.", LangShellWindow.class);
        }else {
            flagRunning = true;
            flagExecutingQueue = true;
            Thread t = new Thread(() -> {
                while(!executionQueue.isEmpty()) {
                    langFileOutputBuilder.append(executionQueue.peek()).append('\n');
                    try {
                        DataObject lastVal = lii.exec(executionQueue.poll());
                        if(executionQueue.isEmpty()) {
                            if(autoPrintMode == AutoPrintMode.AUTO)
                                GraphicsHelper.addText(shell, " ==> " + (lastVal == null?null:lii.getInterpreter().conversions.
                                        toText(lastVal, CodePosition.EMPTY)) + "\n", Color.PINK);
                            else if(autoPrintMode == AutoPrintMode.DEBUG)
                                GraphicsHelper.addText(shell, " ==> " + getDebugString(lastVal, 4) + "\n", Color.PINK);
                        }
                    }catch(IOException e) {
                        term.logStackTrace(e, LangShellWindow.class);
                    }catch(LangInterpreter.StoppedException e) {
                        term.logStackTrace(e, LangShellWindow.class);
                        lii.resetStopFlag();
                    }
                }
                GraphicsHelper.addText(shell, "> ", Color.WHITE);

                flagExecutingQueue = false;
                flagRunning = false;
            });
            t.setDaemon(true);
            t.start();
        }
    }

    private void saveLangFile(boolean chooseFile) {
        File file;
        if(chooseFile || lastLangFileSavedTo == null) {
            JFileChooser fileChooser = new JFileChooser(".");
            fileChooser.setDialogTitle("Select file to save input to");
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public String getDescription() {
                    return "Lang files";
                }

                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().endsWith(".lang");
                }
            });

            if(fileChooser.showSaveDialog(LangShellWindow.this) != JFileChooser.APPROVE_OPTION)
                return;

            file = fileChooser.getSelectedFile();
            if(!file.getName().contains("."))
                file = new File(file.getAbsolutePath() + ".lang");
        }else {
            file = lastLangFileSavedTo;
        }

        if(file.exists() && (chooseFile || lastLangFileSavedTo == null)) {
            if(JOptionPane.showOptionDialog(LangShellWindow.this, "The file already exists!\nDo you want to override it?",
                    "Select an option", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null) != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(langFileOutputBuilder.toString());

            JOptionPane.showOptionDialog(this, "The file was saved successfully!", "Successfully saved!", JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE, null, null, null);

            lastLangFileSavedTo = file;
        }catch(IOException e1) {
            term.logStackTrace(e1, LangShellWindow.class);
        }
    }

    private void clear() {
        shell.setText("");
        printWelcomeText();
    }
    private void end() {
        flagEnd = true;

        GraphicsHelper.addText(shell, "^C\nTranslation map:\n", Color.WHITE);
        Map<String, String> lang = lii.getTranslationMap();
        lang.forEach((key, value) -> {
            term.logln(Level.DEBUG, key + " = " + value, LangShellWindow.class);
        });

        boolean isThrowValue = lii.isReturnedValueThrowValue();
        DataObject retValue = lii.getAndResetReturnValue();
        if(isThrowValue) {
            GraphicsHelper.addText(shell, "Throw Value:\n", Color.WHITE);
            term.logf(Level.DEBUG, "Error code: \"%d\"\nError message: \"%s\"\n", LangShellWindow.class, retValue.getError().getErrno(), retValue.getError().getErrtxt());
        }else {
            GraphicsHelper.addText(shell, "Returned Value:\n", Color.WHITE);
            if(retValue == null)
                term.logln(Level.DEBUG, "No returned value", LangShellWindow.class);
            else
                term.logf(Level.DEBUG, "Returned Value: \"%s\"\n", LangShellWindow.class,
                        lii.getInterpreter().conversions.toText(retValue, CodePosition.EMPTY));
        }

        //Reset the printStream output
        System.setOut(oldOut);
    }

    private final class SpecialCharInputWindow extends JDialog {
        private static final long serialVersionUID = -5520154945750708443L;

        public SpecialCharInputWindow(Dialog owner, String[] specialCharInputs) {
            super(owner, false);

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setTitle("Special Char Input");
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    specialCharInputWindow = null;
                }
            });

            int buttonCount = specialCharInputs.length;
            int gridXCount = (int)Math.ceil(Math.sqrt(buttonCount));
            int gridYCount = buttonCount / gridXCount + (buttonCount % gridXCount > 0?1:0);

            JPanel contentPane = new JPanel();
            setContentPane(contentPane);
            contentPane.setLayout(new GridLayout(gridYCount, gridXCount, 10, 10));
            contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            for(String specialCharInput:specialCharInputs) {
                JButton button = new JButton("   " + specialCharInput + "   ");
                button.addActionListener(e -> {
                    for(char c:specialCharInput.toCharArray())
                        shellKeyListener.keyTyped(new KeyEvent(this, 0, 0, 0, 0, c, KeyEvent.KEY_LOCATION_UNKNOWN));

                    LangShellWindow.this.requestFocus();
                    shell.requestFocus();
                });
                button.setFont(new Font(Font.DIALOG_INPUT, Font.BOLD, 24));
                contentPane.add(button);
            }

            pack();
            setMinimumSize(getSize());
            setLocationRelativeTo(owner);
        }
    }

    private final class FunctionHelpWindow extends JDialog {
        public FunctionHelpWindow(Dialog owner, String rawFunctionName, DataObject.FunctionPointerObject function) {
            super(owner, false);

            String functionName = (function == null || function.getFunctionName() == null)?
                    (rawFunctionName == null?function + "":rawFunctionName):(function + "");

            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setTitle("Function Help for " + functionName);

            JPanel contentPane = new JPanel();
            setContentPane(contentPane);
            contentPane.setLayout(new BorderLayout());
            contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JScrollPane scrollPane = new JScrollPane();
            scrollPane.setDoubleBuffered(true);
            scrollPane.setRequestFocusEnabled(false);
            contentPane.add(scrollPane, BorderLayout.CENTER);

            JEditorPane outputPane = new JEditorPane();
            outputPane.setEditable(false);
            outputPane.setContentType("text/html");
            outputPane.setText(generateHTML(functionName, function));
            scrollPane.setViewportView(outputPane);

            Dimension thisSize = owner.getPreferredSize();
            Dimension ownerSize = owner.getSize();
            setSize(new Dimension(Math.min(thisSize.width, ownerSize.width), (int)Math.min(thisSize.height * 1.5, ownerSize.height)));
            setLocationRelativeTo(owner);
        }

        private String generateHTML(String functionName, DataObject.FunctionPointerObject function) {
            functionName = functionName.replace("<", "&lt;").replace(">", "&gt;");

            StringBuilder builder = new StringBuilder();
            builder.append("<html>");
            {
                builder.append("<head>");
                {
                    builder.append("<style>");
                    {
                        builder.append("h1 { font-size: ").append(fontSize * 1.5).append("px; }");
                        builder.append("h2 { font-size: ").append(fontSize * 1.4).append("px; }");
                        builder.append("p, code, li { font-size: ").append(fontSize).append("px; }");

                        builder.append("ul.lvl2 { list-style-type: circle; }");

                        builder.append(".error { color: red; }");
                    }
                    builder.append("</style>");
                }
                builder.append("</head>");

                builder.append("<body>");
                {
                    builder.append("<h1>").append(functionName).append("</h1>");
                    if(function == null) {
                        builder.append("<p class='error'>No function found at cursor position!</p>");
                    }else {
                        String description = function.getFunctionInfo();
                        builder.append("<p>Description: ").append(description == null?"No description available":description).append("</p>");

                        if(function.getOverloadedFunctionCount() > 0) {
                            DataObject.FunctionPointerObject.InternalFunction internalFunction = function.getFunction(0);

                            builder.append("<p>Combinator function info:").append("</p>");
                            builder.append("<ul>");
                            {
                                builder.append("<li>Call count: ").append(internalFunction.getFunction().getCombinatorFunctionCallCount()).append("</li>");
                            }
                            builder.append("</ul>");
                        }

                        builder.append("<h2>Function signatures</h2>");
                        builder.append("<ul>");
                        for(DataObject.FunctionPointerObject.InternalFunction internalFunction:function.getFunctions()) {
                            generateFunctionSignatureHTML(builder, functionName, internalFunction.getFunction());
                        }
                        builder.append("</ul>");
                    }
                }
                builder.append("</body>");
            }
            builder.append("</html>");
            return builder.toString();
        }

        private void generateFunctionSignatureHTML(StringBuilder builder, String functionName, LangBaseFunction function) {
            List<DataObject> combinatorArguments = function.isCombinatorFunction()?function.getCombinatorProvidedArgumentList():
                    new ArrayList<>();
            boolean hideCombinatorArgument = false;

            builder.append("<li><code>");
            {
                builder.append(functionName).append(function.toFunctionSignatureSyntax());
                DataObject.DataTypeConstraint returnTypeConstraint = function.getReturnValueTypeConstraint();
                if(!returnTypeConstraint.equals(DataObject.CONSTRAINT_NORMAL)) {
                    builder.append(":").append(returnTypeConstraint.toTypeConstraintSyntax());
                }
            }
            builder.append("</code></li>");
            builder.append("<ul class='lvl2'>");
            for(int i = 0;i < function.getParameterList().size();i++) {
                hideCombinatorArgument |= function.getParameterAnnotationList().get(i) == LangBaseFunction.ParameterAnnotation.VAR_ARGS;

                builder.append("<li>");
                {
                    builder.append(function.getParameterList().get(i).getVariableName()).append("</code>");

                    if(!hideCombinatorArgument && combinatorArguments.size() > i) {
                        builder.append(" (= <code>").append(lii.getInterpreter().conversions.
                                toText(combinatorArguments.get(i), CodePosition.EMPTY)).append("</code>)");
                    }

                    String description = function.getParameterInfoList().get(i);
                    builder.append(": ").append(description == null?"No description available":description).append("</p>");
                }
                builder.append("</li>");
            }
            builder.append("</ul>");
        }
    }

    private enum AutoPrintMode {
        NONE, AUTO, DEBUG
    }
}