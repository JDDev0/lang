package me.jddev0.module.graphics;

import java.awt.Color;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

/**
 * Graphics-Module<br>
 * Easier Graphics Library
 * 
 * @author JDDev0
 * @version v0.1.1 beta 1 fix 1
 */
public class GraphicsHelper {
	private GraphicsHelper() {}
	
	/**
	 * Adds text with color to a JTextPane
	 * 
	 * @param pane The JTextPane to add the text with color
	 * @param str The String to add to the JTextPane
	 * @param c The color of the String
	 */
	public static void addText(JTextPane pane, String str, Color c) {
		StyleContext sc = StyleContext.getDefaultStyleContext();
		AttributeSet as = sc.addAttribute(sc.getEmptySet(), StyleConstants.Foreground, c);
		try {
			Document doc = pane.getDocument();
			doc.insertString(doc.getLength(), str, as);
		}catch(BadLocationException ignore) {}
	}
	public static void setColor(JTextPane pane, int start, int length, Color c) {
		StyleContext sc = StyleContext.getDefaultStyleContext();
		AttributeSet as = sc.addAttribute(sc.getEmptySet(), StyleConstants.Foreground, c);
		try {
			Document doc = pane.getDocument();
			doc.insertString(start, doc.getText(start, length), as);
			doc.remove(start + length, length);
		}catch(BadLocationException ignore) {}
	}
	public static void setBackgroundColor(JTextPane pane, int start, int length, Color c) {
		StyleContext sc = StyleContext.getDefaultStyleContext();
		AttributeSet as = sc.addAttribute(sc.getEmptySet(), StyleConstants.Background, c);
		try {
			Document doc = pane.getDocument();
			doc.insertString(start, doc.getText(start, length), as);
			doc.remove(start + length, length);
		}catch(BadLocationException ignore) {}
	}
}