package me.jddev0.module.lang;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.LangInterpreter.InterpretingError;

/**
 * Lang-Module<br>
 * LangTest data class for langTests
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class LangTest {
	private final List<Unit> units;
	
	private Long startTime = null;
	
	private static String printFailedTestResult(String linePrefix, AssertResult assertResult) {
		String message = assertResult.getMessage();
		String actualValue = assertResult.getActualValue();
		String expectedValue = assertResult.getExpectedValue();
		String stackTrace = assertResult.getStackTrace();
		
		StringBuilder stackTraceWithLinePrefixes = new StringBuilder();
		if(stackTrace != null) {
			String[] stackTraceLines = stackTrace.split("\\n");
			for(String stackTraceLine:stackTraceLines)
				stackTraceWithLinePrefixes.append("\n").append(linePrefix).append(stackTraceLine);
		}
		
		return assertResult.getAssertTestName() + ":" + (message == null?"":(
		       "\n" + linePrefix + "Message:  " + message)) +
		       
		       ((actualValue == null || expectedValue == null)?"":(
		       "\n" + linePrefix + "Actual:   " + actualValue +
		       "\n" + linePrefix + "Excepted: " + expectedValue)) +
		       
		       ((stackTrace == null)?"":(
		       "\n" + linePrefix + "Stack trace:" +
		       stackTraceWithLinePrefixes));
	}
	
	public LangTest() {
		units = new LinkedList<>();
		units.add(new Unit());
	}
	
	public void addUnit(String name) {
		if(name == null)
			throw new IllegalStateException("name must not be null");
		
		units.add(new Unit(name));
	}
	
	public void addSubUnit(String name) {
		if(name == null)
			throw new IllegalStateException("name must not be null");
		
		Unit currentUnit = units.get(units.size() - 1);
		if(currentUnit.getName() == null)
			throw new IllegalStateException("the no name unit must not have sub units");
		
		currentUnit.addSubUnit(new SubUnit(name));
	}
	
	public void addAssertResult(AssertResult assertResult) {
		if(assertResult == null)
			throw new IllegalStateException("assertResult must not be null");
		
		if(startTime == null)
			startTime = System.currentTimeMillis();
		
		Unit currentUnit = units.get(units.size() - 1);
		SubUnit currentSubUnit = currentUnit.getSubUnits().get(currentUnit.getSubUnits().size() - 1);
		
		currentSubUnit.addAssertResult(assertResult);
	}
	
	private int getTestCount() {
		return units.stream().mapToInt(Unit::getTestCount).sum();
	}
	
	private int getTestPassedCount() {
		return units.stream().mapToInt(Unit::getTestPassedCount).sum();
	}
	
	public String printResults() {
		return toString();
	}
	
	public void printResultsToTerminal(TerminalIO term) {
		long endTime = System.currentTimeMillis();
		Long startTime = this.startTime;
		if(startTime == null)
			startTime = endTime;
		
		units.forEach(unit -> unit.printResultsToTerminal(term));
		
		term.logln(Level.INFO, "------------------------------------------", LangTest.class);
		term.logln(Level.CONFIG, "Summary:\nTime taken: " + String.format(Locale.ENGLISH,
				"%.3f s", (endTime - startTime) / 1000.) +
				"\nTests passed: " + getTestPassedCount() + "/" + getTestCount(), LangTest.class);
		
		String out = "";
		if(getTestPassedCount() != getTestCount())
			out += "Failed tests:";
		
		for(Unit unit:units) {
			if(unit.getTestPassedCount() == unit.getTestCount() || unit.getTestCount() == 0)
				continue;
			
			out += "\n\tUnit: " + (unit.getName() == null?"noname":("\"" + unit.getName() + "\"")) + ":";
			
			for(SubUnit subUnit:unit.getSubUnits()) {
				List<AssertResult> failedTests = subUnit.getFailedTests();
				
				if(subUnit.getName() == null) {
					if(failedTests.size() > 0) {
						for(AssertResult failedTest:failedTests) {
							out += "\n\t\t" + printFailedTestResult("\t\t\t", failedTest);
						}
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\n\t\tSubUnit: \"" + subUnit.getName() + "\"";
					for(AssertResult failedTest:failedTests) {
						out += "\n\t\t\t" + printFailedTestResult("\t\t\t\t", failedTest);
					}
				}
			}
		}
		
		if(!out.isEmpty())
			term.logln(Level.ERROR, out, LangTest.class);
	}
	
	@Override
	public String toString() {
		long endTime = System.currentTimeMillis();
		Long startTime = this.startTime;
		if(startTime == null)
			startTime = endTime;
		
		String out = units.stream().map(Unit::printResults).collect(Collectors.joining());
		
		out += "------------------------------------------\n" +
		       "Summary:\n" +
		       "Time taken: " + String.format(Locale.ENGLISH, "%.3f s", (endTime - startTime) / 1000.) + "\n" +
		       "Tests passed: " + getTestPassedCount() + "/" + getTestCount();
		
		if(getTestPassedCount() != getTestCount())
			out += "\nFailed tests:";
		
		for(Unit unit:units) {
			if(unit.getTestPassedCount() == unit.getTestCount() || unit.getTestCount() == 0)
				continue;
			
			out += "\n\tUnit: " + (unit.getName() == null?"noname":("\"" + unit.getName() + "\"")) + ":";
			
			for(SubUnit subUnit:unit.getSubUnits()) {
				List<AssertResult> failedTests = subUnit.getFailedTests();
				
				if(subUnit.getName() == null) {
					if(failedTests.size() > 0) {
						for(AssertResult failedTest:failedTests) {
							out += "\n\t\t" + printFailedTestResult("\t\t\t", failedTest);
						}
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\n\t\tSubUnit: \"" + subUnit.getName() + "\"";
					for(AssertResult failedTest:failedTests) {
						out += "\n\t\t\t" + printFailedTestResult("\t\t\t\t", failedTest);
					}
				}
			}
		}
		
		return out;
	}
	
	private static final class Unit {
		private final String name;
		private final List<SubUnit> subUnits;
		
		public Unit(String name) {
			if(name == null)
				throw new IllegalStateException("name must not be null");
			
			this.name = name;
			
			subUnits = new LinkedList<>();
			subUnits.add(new SubUnit());
		}
		
		private Unit() {
			this.name = null;
			
			subUnits = new LinkedList<>();
			subUnits.add(new SubUnit());
		}
		
		public String getName() {
			return name;
		}
		
		private void addSubUnit(SubUnit subUnit) {
			subUnits.add(subUnit);
		}
		
		private List<SubUnit> getSubUnits() {
			return subUnits;
		}
		
		private int getTestCount() {
			return subUnits.stream().mapToInt(SubUnit::getTestCount).sum();
		}
		
		private int getTestPassedCount() {
			return subUnits.stream().mapToInt(SubUnit::getTestPassedCount).sum();
		}
		
		public String printResults() {
			return toString();
		}
		
		public void printResultsToTerminal(TerminalIO term) {
			if(getTestCount() == 0)
				return;
			
			term.logln(Level.CONFIG,
					"Unit: " + (name == null?"noname":("\"" + name + "\"")) + ":\n" +
					"Tests passed: " + getTestPassedCount() + "/" + getTestCount(), LangTest.class);
			
			String out = "";
			if(getTestPassedCount() != getTestCount())
				out += "Failed tests:";
			for(SubUnit subUnit:subUnits) {
				List<AssertResult> failedTests = subUnit.getFailedTests();
				
				if(subUnit.getName() == null) {
					if(failedTests.size() > 0) {
						for(AssertResult failedTest:failedTests) {
							out += "\n\t" + printFailedTestResult("\t\t", failedTest);
						}
						
						out += "\n";
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\n\tSubUnit: \"" + subUnit.getName() + "\"";
					for(AssertResult failedTest:failedTests) {
						out += "\n\t\t" + printFailedTestResult("\t\t\t", failedTest);
					}
					
					out += "\n";
				}
			}
			
			if(!out.isEmpty())
				term.logln(Level.ERROR, out, LangTest.class);
			
			if(subUnits.size() > 1) {
				term.logln(Level.CONFIG, "SubUnits:", LangTest.class);
				
				for(SubUnit subUnit:subUnits)
					subUnit.printResultsToTerminal(term);
			}else if(out.isEmpty()) {
				term.logln(Level.CONFIG, "", LangTest.class);
			}
		}
		
		@Override
		public String toString() {
			if(getTestCount() == 0)
				return "";
			
			String out = "Unit: " + (name == null?"noname":("\"" + name + "\"")) + ":\n" +
			             "Tests passed: " + getTestPassedCount() + "/" + getTestCount() + "\n";
			
			if(getTestPassedCount() != getTestCount())
				out += "Failed tests:";
			for(SubUnit subUnit:subUnits) {
				List<AssertResult> failedTests = subUnit.getFailedTests();
				
				if(subUnit.getName() == null) {
					if(failedTests.size() > 0) {
						for(AssertResult failedTest:failedTests) {
							out += "\n\t" + printFailedTestResult("\t\t", failedTest);
						}
						
						out += "\n";
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\n\tSubUnit: \"" + subUnit.getName() + "\"";
					for(AssertResult failedTest:failedTests) {
						out += "\n\t\t" + printFailedTestResult("\t\t\t", failedTest);
					}
					
					out += "\n";
				}
			}
			
			if(subUnits.size() > 1) {
				out += "SubUnits:\n";
				
				for(SubUnit subUnit:subUnits)
					out += subUnit.printResults();
			}else if(out.isEmpty()) {
				out += "\n";
			}
			
			return out;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof Unit))
				return false;
			
			Unit that = (Unit)obj;
			return Objects.equals(this.name, that.name);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(name);
		}
	}
	
	private static final class SubUnit {
		private final String name;
		private final List<AssertResult> assertResults;
		
		public SubUnit(String name) {
			if(name == null)
				throw new IllegalStateException("name must not be null");
			
			this.name = name;
			
			assertResults = new LinkedList<>();
		}
		
		private SubUnit() {
			this.name = null;
			
			assertResults = new LinkedList<>();
		}
		
		public String getName() {
			return name;
		}
		
		private void addAssertResult(AssertResult assertResult) {
			assertResults.add(assertResult);
		}
		
		private int getTestCount() {
			return assertResults.size();
		}
		
		private int getTestPassedCount() {
			return assertResults.size() - assertResults.stream().mapToInt(assertResult -> assertResult.hasTestPassed()?0:1).sum();
		}
		
		private List<AssertResult> getFailedTests() {
			return assertResults.stream().filter(((Predicate<AssertResult>)AssertResult::hasTestPassed).negate()).collect(Collectors.toList());
		}
		
		public String printResults() {
			return toString();
		}
		
		public void printResultsToTerminal(TerminalIO term) {
			if(name == null || getTestCount() == 0)
				return;
			
			term.logln(Level.CONFIG,
					"\tSubUnit: \"" + name + "\":\n" +
					"\t\tTests passed: " + getTestPassedCount() + "/" + getTestCount(), LangTest.class);
			
			String out = "";
			List<AssertResult> failedTests = getFailedTests();
			if(failedTests.size() > 0) {
				out += "\t\tFailed tests:";
				
				for(AssertResult failedTest:failedTests) {
					out += "\n\t\t\t" + printFailedTestResult("\t\t\t\t", failedTest);
				}
			}
			
			out += "\n";
			
			if(!out.isEmpty())
				term.logln(Level.ERROR, out, LangTest.class);
		}
		
		@Override
		public String toString() {
			if(name == null || getTestCount() == 0)
				return "";
			
			String out = "\tSubUnit: \"" + name + "\":\n" +
			             "\t\tTests passed: " + getTestPassedCount() + "/" + getTestCount() + "\n";
			
			List<AssertResult> failedTests = getFailedTests();
			if(failedTests.size() > 0) {
				out += "\t\tFailed tests:";
				
				for(AssertResult failedTest:failedTests) {
					out += "\n\t\t\t" + printFailedTestResult("\t\t\t\t", failedTest);
				}
			}
			
			out += "\n";
			
			return out;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(this == obj)
				return true;
			
			if(obj == null)
				return false;
			
			if(!(obj instanceof SubUnit))
				return false;
			
			SubUnit that = (SubUnit)obj;
			return Objects.equals(this.name, that.name);
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(name);
		}
	}
	
	private static interface AssertResult {
		boolean hasTestPassed();
		String getStackTrace();
		String getAssertTestName();
		String getMessage();
		String getActualValue();
		String getExpectedValue();
	}
	
	public static final class AssertResultError implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final InterpretingError actualValue;
		private final InterpretingError expectedValue;
		
		public AssertResultError(boolean testPassed, String stackTrace, String message, InterpretingError actualValue,
				InterpretingError expectedValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertError";
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return actualValue.getErrorCode() + " (" + actualValue.name() + ")";
		}
		
		@Override
		public String getExpectedValue() {
			return "== " + expectedValue.getErrorCode() + " (" + expectedValue.name() + ")";
		}
	}
	
	private static abstract class AssertResultDataObject implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String actualValueText;
		private final String actualValueTypeName;
		private final String expectedValueText;
		private final String expectedValueTypeName;
		private final String expectedValueOperator;
		
		public AssertResultDataObject(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue, String expectedValueOperator) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.actualValueText = actualValue == null?null:actualValue.getText();
			this.actualValueTypeName = actualValue == null?null:actualValue.getType().name();
			this.expectedValueText = expectedValue == null?null:expectedValue.getText();
			this.expectedValueTypeName = expectedValue == null?null:expectedValue.getType().name();
			this.expectedValueOperator = expectedValueOperator;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return "\"" + actualValueText + "\"" + ", Type: " + actualValueTypeName;
		}
		
		@Override
		public String getExpectedValue() {
			return expectedValueOperator + " \"" + expectedValueText + "\"" + ", Type: " + expectedValueTypeName;
		}
	}
	
	public static final class AssertResultEquals extends AssertResultDataObject {
		public AssertResultEquals(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, "==");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultEquals";
		}
	}
	
	public static final class AssertResultNotEquals extends AssertResultDataObject {
		public AssertResultNotEquals(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, "!=");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotEquals";
		}
	}
	
	public static final class AssertResultLessThan extends AssertResultDataObject {
		public AssertResultLessThan(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, "<");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultLessThan";
		}
	}
	
	public static final class AssertResultGreaterThan extends AssertResultDataObject {
		public AssertResultGreaterThan(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, ">");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultGreaterThan";
		}
	}
	
	public static final class AssertResultLessThanOrEquals extends AssertResultDataObject {
		public AssertResultLessThanOrEquals(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, "<=");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultLessThanOrEquals";
		}
	}
	
	public static final class AssertResultGreaterThanOrEquals extends AssertResultDataObject {
		public AssertResultGreaterThanOrEquals(boolean testPassed, String stackTrace, String message,
				DataObject actualValue, DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, ">=");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultGreaterThanOrEquals";
		}
	}
	
	public static final class AssertResultStrictEquals extends AssertResultDataObject {
		public AssertResultStrictEquals(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, "===");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultStrictEquals";
		}
	}
	
	public static final class AssertResultStrictNotEquals extends AssertResultDataObject {
		public AssertResultStrictNotEquals(boolean testPassed, String stackTrace, String message,
				DataObject actualValue, DataObject expectedValue) {
			super(testPassed, stackTrace, message, actualValue, expectedValue, "!==");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultStrictNotEquals";
		}
	}
	
	public static final class AssertResultTranslationValueEquals implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String translationKey;
		private final String translationValue;
		private final String expectedValue;
		
		public AssertResultTranslationValueEquals(boolean testPassed, String stackTrace, String message,
				String translationKey, String translationValue, String expectedValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.translationValue = translationValue;
			this.translationKey = translationKey;
			this.expectedValue = expectedValue;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return translationKey + ": " + (translationValue == null?"Translation key not found":
				("\"" + translationValue + "\""));
		}
		
		@Override
		public String getExpectedValue() {
			return "== \"" + expectedValue + "\"";
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultTranslationValueEquals";
		}
	}
	
	public static final class AssertResultTranslationValueNotEquals implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String translationKey;
		private final String translationValue;
		private final String expectedValue;
		
		public AssertResultTranslationValueNotEquals(boolean testPassed, String stackTrace, String message,
				String translationKey, String translationValue, String expectedValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.translationValue = translationValue;
			this.translationKey = translationKey;
			this.expectedValue = expectedValue;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return translationKey + ": " + (translationValue == null?"Translation key not found":
				("\"" + translationValue + "\""));
		}
		
		@Override
		public String getExpectedValue() {
			return "!= \"" + expectedValue + "\"";
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultTranslationValueNotEquals";
		}
	}
	
	public static final class AssertResultTranslationKeyFound implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String translationKey;
		private final String translationValue;
		
		public AssertResultTranslationKeyFound(boolean testPassed, String stackTrace, String message,
				String translationKey, String translationValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.translationValue = translationValue;
			this.translationKey = translationKey;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return translationKey + ": " + (translationValue == null?"Translation key not found":
				"Translation key found");
		}
		
		@Override
		public String getExpectedValue() {
			return "== Translation key found";
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultTranslationKeyFound";
		}
	}
	
	public static final class AssertResultTranslationKeyNotFound implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String translationKey;
		private final String translationValue;
		
		public AssertResultTranslationKeyNotFound(boolean testPassed, String stackTrace, String message,
				String translationKey, String translationValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.translationValue = translationValue;
			this.translationKey = translationKey;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return translationKey + ": " + (translationValue == null?"Translation key not found":
				"Translation key found");
		}
		
		@Override
		public String getExpectedValue() {
			return "== Translation key not found";
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultTranslationKeyNotFound";
		}
	}
	
	private static abstract class AssertResultDataObjectString implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String actualValueText;
		private final String actualValueTypeName;
		private final String expectedValue;
		
		public AssertResultDataObjectString(boolean testPassed, String stackTrace, String message,
				DataObject actualValue, String expectedValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.actualValueText = actualValue == null?null:actualValue.getText();
			this.actualValueTypeName = actualValue == null?null:actualValue.getType().name();
			this.expectedValue = expectedValue;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return "\"" + actualValueText + "\"" + ", Type: " + actualValueTypeName;
		}
		
		@Override
		public String getExpectedValue() {
			return expectedValue;
		}
	}
	
	public static final class AssertResultTypeEquals extends AssertResultDataObjectString {
		public AssertResultTypeEquals(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject.DataType expectedType) {
			super(testPassed, stackTrace, message, actualValue, "Type: == " + expectedType);
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultTypeEquals";
		}
	}
	
	public static final class AssertResultTypeNotEquals extends AssertResultDataObjectString {
		public AssertResultTypeNotEquals(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject.DataType expectedType) {
			super(testPassed, stackTrace, message, actualValue, "Type: != " + expectedType);
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultTypeNotEquals";
		}
	}
	
	public static final class AssertResultNull extends AssertResultDataObjectString {
		public AssertResultNull(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "== null");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNull";
		}
	}
	
	public static final class AssertResultNotNull extends AssertResultDataObjectString {
		public AssertResultNotNull(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "!= null");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotNull";
		}
	}
	
	public static final class AssertResultVoid extends AssertResultDataObjectString {
		public AssertResultVoid(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "== void");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultVoid";
		}
	}
	
	public static final class AssertResultNotVoid extends AssertResultDataObjectString {
		public AssertResultNotVoid(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "!= void");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotVoid";
		}
	}
	
	public static final class AssertResultFinal extends AssertResultDataObjectString {
		public AssertResultFinal(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "== final");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultFinal";
		}
	}
	
	public static final class AssertResultNotFinal extends AssertResultDataObjectString {
		public AssertResultNotFinal(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "!= final");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotFinal";
		}
	}
	
	public static final class AssertResultStatic extends AssertResultDataObjectString {
		public AssertResultStatic(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "== static");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultStatic";
		}
	}
	
	public static final class AssertResultNotStatic extends AssertResultDataObjectString {
		public AssertResultNotStatic(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			super(testPassed, stackTrace, message, actualValue, "!= static");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotStatic";
		}
	}
	
	public static final class AssertResultThrow implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final InterpretingError actualValue;
		private final InterpretingError expectedValue;
		
		public AssertResultThrow(boolean testPassed, String stackTrace, String message, InterpretingError actualValue,
				InterpretingError expectedValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertThrow";
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return actualValue == null?"nothing thrown":(actualValue.getErrorCode() + " (" + actualValue.name() + ")");
		}
		
		@Override
		public String getExpectedValue() {
			return "== " + expectedValue.getErrorCode() + " (" + expectedValue.name() + ")";
		}
	}
	
	public static class AssertResultReturn implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String actualValueText;
		private final String actualValueTypeName;
		private final String expectedValueText;
		private final String expectedValueTypeName;
		
		public AssertResultReturn(boolean testPassed, String stackTrace, String message, DataObject actualValue,
				DataObject expectedValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.actualValueText = actualValue == null?null:actualValue.getText();
			this.actualValueTypeName = actualValue == null?null:actualValue.getType().name();
			this.expectedValueText = expectedValue == null?null:expectedValue.getText();
			this.expectedValueTypeName = expectedValue == null?null:expectedValue.getType().name();
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultReturn";
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return actualValueText == null?"nothing returned":
				("\"" + actualValueText + "\"" + ", Type: " + actualValueTypeName);
		}
		
		@Override
		public String getExpectedValue() {
			return "=== \"" + expectedValueText + "\"" + ", Type: " + expectedValueTypeName;
		}
	}
	
	public static class AssertResultNoReturn implements AssertResult {
		private final boolean testPassed;
		private final String stackTrace;
		private final String message;
		private final String actualValueText;
		private final String actualValueTypeName;
		
		public AssertResultNoReturn(boolean testPassed, String stackTrace, String message, DataObject actualValue) {
			this.testPassed = testPassed;
			this.stackTrace = stackTrace;
			this.message = message;
			this.actualValueText = actualValue == null?null:actualValue.getText();
			this.actualValueTypeName = actualValue == null?null:actualValue.getType().name();
		}
		
		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNoReturn";
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return "\"" + actualValueText + "\"" + ", Type: " + actualValueTypeName;
		}
		
		@Override
		public String getExpectedValue() {
			return "== nothing returned";
		}
	}
	
	public static class AssertResultFail implements AssertResult {
		private final String stackTrace;
		private final String message;
		
		public AssertResultFail(String stackTrace, String message) {
			this.stackTrace = stackTrace;
			this.message = message;
		}
		
		@Override
		public boolean hasTestPassed() {
			return false;
		}
		
		@Override
		public String getStackTrace() {
			return stackTrace;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultFail";
		}
		
		@Override
		public String getMessage() {
			return message;
		}
		
		@Override
		public String getActualValue() {
			return null;
		}
		
		@Override
		public String getExpectedValue() {
			return null;
		}
	}
}