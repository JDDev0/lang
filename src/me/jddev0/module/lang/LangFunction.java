package me.jddev0.module.lang;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LangFunction {
	String value();
	
	/**
	 * @return true for the lang function which contains the function info if the function is overloaded
	 */
	boolean hasInfo() default false;
	
	boolean isLinkerFunction() default false;
	
	boolean isDeprecated() default false;
	String getDeprecatedRemoveVersion() default "";
	String getDeprecatedReplacementFunction() default "";
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	public static @interface CombinatorFunction {}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.PARAMETER)
	public static @interface LangParameter {
		String value();
		
		@Documented
		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.PARAMETER)
		public static @interface NumberValue {}
		
		@Documented
		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.PARAMETER)
		public static @interface BooleanValue {}
		
		@Documented
		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.PARAMETER)
		public static @interface CallByPointer {}
		
		@Documented
		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.PARAMETER)
		public static @interface VarArgs {}
		
		@Documented
		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.PARAMETER)
		public static @interface RawVarArgs {}
	}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.PARAMETER})
	/**
	 * If used in method -> return value type constraint
	 */
	public static @interface AllowedTypes {
		DataObject.DataType[] value();
	}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.PARAMETER})
	/**
	 * If used in method -> return value type constraint
	 */
	public static @interface NotAllowedTypes {
		DataObject.DataType[] value();
	}
	
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.PARAMETER})
	public static @interface LangInfo {
		String value();
	}
}