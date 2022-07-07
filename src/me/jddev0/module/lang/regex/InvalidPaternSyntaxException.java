package me.jddev0.module.lang.regex;

/**
 * Lang-Module<br>
 * Lang RegEx invalid pattern syntax exception
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class InvalidPaternSyntaxException extends RuntimeException {
	private static final long serialVersionUID = -2631264953536046294L;
	
	public InvalidPaternSyntaxException(String message) {
		super(message);
	}
}