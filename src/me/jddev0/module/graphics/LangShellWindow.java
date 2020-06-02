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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;

import me.jddev0.module.io.Lang;
import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;

/**
 * Uses the io module<br>
 * <br>
 * Graphics-Module<br>
 * Lang Shell
 * 
 * @author JDDev0
 * @version v0.1
 */
public class LangShellWindow extends JDialog {
	private static final long serialVersionUID = 3517996790399999763L;

	private JTextPane shell;
	private TerminalIO term;
	private boolean flagEnd = false;
	private int indent = 0;
	private StringBuilder multiLineTmp = new StringBuilder();
	
	public LangShellWindow(JFrame owner, TerminalIO term) {
		super(owner, true); //Make this window to an modal window (Focus won't be given back to owner window)
		
		this.term = term;
		
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setTitle("LangShell");
		setSize(750, 500);
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
		{
			//Auto Scroll
			DefaultCaret caret = (DefaultCaret)shell.getCaret();
			caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		}
		shell.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		shell.setMargin(new Insets(3, 5, 0, 5));
		shell.addKeyListener(new KeyAdapter() {
			private StringBuilder lineTmp = new StringBuilder();
			
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
					}
				}else if(c != KeyEvent.CHAR_UNDEFINED) {
					if(c == '\n') {
						addLine(lineTmp.toString());
						lineTmp = new StringBuilder();
					}else {
						lineTmp.append(c);
						GraphicsHelper.addText(shell, c + "", Color.WHITE);
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
								String line = lines[i];
								GraphicsHelper.addText(shell, line, Color.WHITE);
								lineTmp.append(line);
								if(i != lines.length - 1 || copied.endsWith("\n")) { //Line has an '\n' at end -> finished line
									addLine(lineTmp.toString());
									lineTmp = new StringBuilder();
								}
							}
						}
					}catch(HeadlessException|UnsupportedFlavorException|IOException e1) {
						term.logStackTrace(e1, LangShellWindow.class);
					}
				}else if(e.getKeyCode() == KeyEvent.VK_C && e.isControlDown()) {
					end();
					return;
				}
			}
		});
		scrollPane.setViewportView(shell);
		
		initShell();
	}
	
	private Class<?> compilerClass;
	private Method compileLangFileMethod;
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
					
					//Clears tmp for the printing
					printingTmp.delete(0, printingTmp.length());
				}
			}
		}));
		
		//Gets Compiler class
		compilerClass = Lang.class.getDeclaredClasses()[0];
		for(Method m:compilerClass.getMethods()) {
			if(m.getName().equals("compileLangFile")) {
				
			}
		}
		try {
			//Gets compileLangFile method
			compileLangFileMethod = compilerClass.getDeclaredMethod("compileLangFile", BufferedReader.class, int.class);
			compileLangFileMethod.setAccessible(true);
			
			//Creates new DataMap
			Method createDataMapMethod = compilerClass.getDeclaredMethod("createDataMap", int.class);
			createDataMapMethod.setAccessible(true);
			createDataMapMethod.invoke(null, 0);
		}catch(NoSuchMethodException|SecurityException|IllegalAccessException|IllegalArgumentException|InvocationTargetException e) {
			term.logStackTrace(e, LangShellWindow.class);
		}
		
		GraphicsHelper.addText(shell, "Lang-Shell", Color.RED);
		GraphicsHelper.addText(shell, " - Press CTRL + C to exit!\nCopy with (CTRL + SHIFT + C) and paste with (CTRL + SHIT + P)\n> ", Color.WHITE);
	}
	
	private void addLine(String line) {
		if(indent == 0) {
			GraphicsHelper.addText(shell, "\n", Color.WHITE);
			
			if(line.trim().endsWith("{")) {
				indent++;
				multiLineTmp.append(line);
				multiLineTmp.append("\n");
				
				GraphicsHelper.addText(shell, "    > ", Color.WHITE);
			}else {
				try {
					compileLangFileMethod.invoke(null, new BufferedReader(new StringReader(line)), 0);
				}catch(Exception e) {
					term.logStackTrace(e, LangShellWindow.class);
				}
				GraphicsHelper.addText(shell, "> ", Color.WHITE);
			}
		}else {
			if(line.trim().endsWith("{"))
				indent++;
			
			multiLineTmp.append(line);
			multiLineTmp.append("\n");
			
			if(line.trim().startsWith("}")) {
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
				}catch(BadLocationException e) {}
			}
			
			if(indent == 0) {
				try {
					compileLangFileMethod.invoke(null, new BufferedReader(new StringReader(multiLineTmp.toString())), 0);
				}catch(Exception e) {
					term.logStackTrace(e, LangShellWindow.class);
				}
				
				multiLineTmp = new StringBuilder();
			}
			
			GraphicsHelper.addText(shell, "\n", Color.WHITE);
			for(int i = 0;i < indent;i++)
				GraphicsHelper.addText(shell, "    ", Color.WHITE);
			GraphicsHelper.addText(shell, "> ", Color.WHITE);
		}
	}
	
	private void end() {
		flagEnd = true;
		
		GraphicsHelper.addText(shell, "^C\nTranslation map:\n", Color.WHITE);
		try {
			Field dataField = Lang.class.getDeclaredField("data");
			dataField.setAccessible(true);
			Map<?, ?> dataMaps = (Map<?, ?>)dataField.get(null);
			Class<?> dataClass = null;
			for(Class<?> c:compilerClass.getDeclaredClasses()) {
				if(c.getSimpleName().equals("Data")) {
					dataClass = c;
					break;
				}
			}
			Field langField = dataClass.getDeclaredField("lang");
			langField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<String, String> lang = (Map<String, String>)langField.get(dataMaps.get(0));
			lang.forEach((key, value) -> {
				term.logln(Level.DEBUG, key + " = " + value, LangShellWindow.class);
			});
		}catch(NullPointerException|NoSuchFieldException|SecurityException|IllegalArgumentException|IllegalAccessException e) {
			term.logStackTrace(e, LangShellWindow.class);
		}
		
		//Reset the printStream output
		System.setOut(oldOut);
	}
}