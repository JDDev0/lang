package me.jddev0.module.lang;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import me.jddev0.module.io.TerminalIO;
import me.jddev0.module.io.TerminalIO.Level;
import me.jddev0.module.lang.LangInterpreter.DataObject;
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
		units.forEach(unit -> unit.printResultsToTerminal(term));
		
		term.logln(Level.INFO, "------------------------------------------", LangTest.class);
		term.logln(Level.CONFIG, "Summary:\nTests passed: " + getTestPassedCount() + "/" + getTestCount(), LangTest.class);
		
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
							out += "\n\t\t" + failedTest.getAssertTestName() + ":\n" +
							       "\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
							       "\t\t\tExcepted: " + failedTest.getExpectedValue();
						}
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\n\t\tSubUnit: \"" + subUnit.getName() + "\"";
					for(AssertResult failedTest:failedTests) {
						out += "\n\t\t\t" + failedTest.getAssertTestName() + ":\n" +
						       "\t\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
						       "\t\t\t\tExcepted: " + failedTest.getExpectedValue();
					}
				}
			}
		}
		
		if(!out.isEmpty())
			term.logln(Level.ERROR, out, LangTest.class);
	}
	
	@Override
	public String toString() {
		String out = units.stream().map(Unit::printResults).collect(Collectors.joining());
		
		out += "------------------------------------------\n" +
		       "Summary:\n" +
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
							out += "\n\t\t" + failedTest.getAssertTestName() + ":\n" +
							       "\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
							       "\t\t\tExcepted: " + failedTest.getExpectedValue();
						}
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\n\t\tSubUnit: \"" + subUnit.getName() + "\"";
					for(AssertResult failedTest:failedTests) {
						out += "\n\t\t\t" + failedTest.getAssertTestName() + ":\n" +
						       "\t\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
						       "\t\t\t\tExcepted: " + failedTest.getExpectedValue();
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
				out += "Failed tests:\n";
			for(SubUnit subUnit:subUnits) {
				List<AssertResult> failedTests = subUnit.getFailedTests();
				
				if(subUnit.getName() == null) {
					if(failedTests.size() > 0) {
						for(AssertResult failedTest:failedTests) {
							out += "\t" + failedTest.getAssertTestName() + ":\n" +
							       "\t\tActual:   " + failedTest.getActualValue() + "\n" +
							       "\t\tExcepted: " + failedTest.getExpectedValue() + "\n";
						}
						
						out += "\n";
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\tSubUnit: \"" + subUnit.getName() + "\"\n";
					for(AssertResult failedTest:failedTests) {
						out += "\t\t" + failedTest.getAssertTestName() + ":\n" +
						       "\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
						       "\t\t\tExcepted: " + failedTest.getExpectedValue() + "\n";
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
			}
		}
		
		@Override
		public String toString() {
			if(getTestCount() == 0)
				return "";
			
			String out = "Unit: " + (name == null?"noname":("\"" + name + "\"")) + ":\n" +
			             "Tests passed: " + getTestPassedCount() + "/" + getTestCount() + "\n";
			
			if(getTestPassedCount() != getTestCount())
				out += "Failed tests:\n";
			for(SubUnit subUnit:subUnits) {
				List<AssertResult> failedTests = subUnit.getFailedTests();
				
				if(subUnit.getName() == null) {
					if(failedTests.size() > 0) {
						for(AssertResult failedTest:failedTests) {
							out += "\t" + failedTest.getAssertTestName() + ":\n" +
							       "\t\tActual:   " + failedTest.getActualValue() + "\n" +
							       "\t\tExcepted: " + failedTest.getExpectedValue() + "\n";
						}
						
						out += "\n";
					}
					
					continue;
				}
				
				if(failedTests.size() > 0) {
					out += "\tSubUnit: \"" + subUnit.getName() + "\"\n";
					for(AssertResult failedTest:failedTests) {
						out += "\t\t" + failedTest.getAssertTestName() + ":\n" +
						       "\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
						       "\t\t\tExcepted: " + failedTest.getExpectedValue() + "\n";
					}
					
					out += "\n";
				}
			}
			
			if(subUnits.size() > 1) {
				out += "SubUnits:\n";
				
				for(SubUnit subUnit:subUnits)
					out += subUnit.printResults();
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
				out += "\t\tFailed tests:\n";
				
				for(AssertResult failedTest:failedTests) {
					out += "\t\t\t" + failedTest.getAssertTestName() + ":\n" +
					       "\t\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
					       "\t\t\t\tExcepted: " + failedTest.getExpectedValue() + "\n";
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
				out += "\t\tFailed tests:\n";
				
				for(AssertResult failedTest:failedTests) {
					out += "\t\t\t" + failedTest.getAssertTestName() + ":\n" +
					       "\t\t\t\tActual:   " + failedTest.getActualValue() + "\n" +
					       "\t\t\t\tExcepted: " + failedTest.getExpectedValue() + "\n";
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
		String getAssertTestName();
		String getActualValue();
		String getExpectedValue();
	}
	
	public static final class AssertResultError implements AssertResult {
		private final boolean testPassed;
		private final InterpretingError actualValue;
		private final InterpretingError expectedValue;
		
		public AssertResultError(boolean testPassed, InterpretingError actualValue, InterpretingError expectedValue) {
			this.testPassed = testPassed;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
		}

		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertError";
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
		private final DataObject actualValue;
		private final DataObject expectedValue;
		private final String expectedValueOperator;
		
		public AssertResultDataObject(boolean testPassed, DataObject actualValue, DataObject expectedValue, String expectedValueOperator) {
			this.testPassed = testPassed;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
			this.expectedValueOperator = expectedValueOperator;
		}

		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getActualValue() {
			return "\"" + actualValue.getText() + "\"" + ", Type: " + actualValue.getType().name();
		}
		
		@Override
		public String getExpectedValue() {
			return expectedValueOperator + " \"" + expectedValue.getText() + "\"" + ", Type: " + expectedValue.getType().name();
		}
	}
	
	public static final class AssertResultEquals extends AssertResultDataObject {
		public AssertResultEquals(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, "==");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultEquals";
		}
	}
	
	public static final class AssertResultNotEquals extends AssertResultDataObject {
		public AssertResultNotEquals(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, "!=");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotEquals";
		}
	}
	
	public static final class AssertResultLessThan extends AssertResultDataObject {
		public AssertResultLessThan(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, "<");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultLessThan";
		}
	}
	
	public static final class AssertResultGreaterThan extends AssertResultDataObject {
		public AssertResultGreaterThan(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, ">");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultGreaterThan";
		}
	}
	
	public static final class AssertResultLessThanOrEquals extends AssertResultDataObject {
		public AssertResultLessThanOrEquals(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, "<=");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultLessThanOrEquals";
		}
	}
	
	public static final class AssertResultGreaterThanOrEquals extends AssertResultDataObject {
		public AssertResultGreaterThanOrEquals(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, ">=");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultGreaterThanOrEquals";
		}
	}
	
	public static final class AssertResultStrictEquals extends AssertResultDataObject {
		public AssertResultStrictEquals(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, "===");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultStrictEquals";
		}
	}
	
	public static final class AssertResultStrictNotEquals extends AssertResultDataObject {
		public AssertResultStrictNotEquals(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			super(testPassed, actualValue, expectedValue, "!==");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultStrictNotEquals";
		}
	}
	
	private static abstract class AssertResultDataObjectString implements AssertResult {
		private final boolean testPassed;
		private final DataObject actualValue;
		private final String expectedValue;
		
		public AssertResultDataObjectString(boolean testPassed, DataObject actualValue, String expectedValue) {
			this.testPassed = testPassed;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
		}

		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getActualValue() {
			return "\"" + actualValue.getText() + "\"" + ", Type: " + actualValue.getType().name();
		}
		
		@Override
		public String getExpectedValue() {
			return expectedValue;
		}
	}
	
	public static final class AssertResultNull extends AssertResultDataObjectString {
		public AssertResultNull(boolean testPassed, DataObject actualValue) {
			super(testPassed, actualValue, "== null");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNull";
		}
	}
	
	public static final class AssertResultNotNull extends AssertResultDataObjectString {
		public AssertResultNotNull(boolean testPassed, DataObject actualValue) {
			super(testPassed, actualValue, "!= null");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotNull";
		}
	}
	
	public static final class AssertResultVoid extends AssertResultDataObjectString {
		public AssertResultVoid(boolean testPassed, DataObject actualValue) {
			super(testPassed, actualValue, "== void");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultVoid";
		}
	}
	
	public static final class AssertResultNotVoid extends AssertResultDataObjectString {
		public AssertResultNotVoid(boolean testPassed, DataObject actualValue) {
			super(testPassed, actualValue, "!= void");
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNotVoid";
		}
	}
	
	public static final class AssertResultThrow implements AssertResult {
		private final boolean testPassed;
		private final InterpretingError actualValue;
		private final InterpretingError expectedValue;
		
		public AssertResultThrow(boolean testPassed, InterpretingError actualValue, InterpretingError expectedValue) {
			this.testPassed = testPassed;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
		}

		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertThrow";
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
		private final DataObject actualValue;
		private final DataObject expectedValue;
		
		public AssertResultReturn(boolean testPassed, DataObject actualValue, DataObject expectedValue) {
			this.testPassed = testPassed;
			this.actualValue = actualValue;
			this.expectedValue = expectedValue;
		}

		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultReturn";
		}
		
		@Override
		public String getActualValue() {
			return actualValue == null?"nothing returned":("\"" + actualValue.getText() + "\"" + ", Type: " + actualValue.getType().name());
		}
		
		@Override
		public String getExpectedValue() {
			return "=== \"" + expectedValue.getText() + "\"" + ", Type: " + expectedValue.getType().name();
		}
	}
	
	public static class AssertResultNoReturn implements AssertResult {
		private final boolean testPassed;
		private final DataObject actualValue;
		
		public AssertResultNoReturn(boolean testPassed, DataObject actualValue) {
			this.testPassed = testPassed;
			this.actualValue = actualValue;
		}

		@Override
		public boolean hasTestPassed() {
			return testPassed;
		}
		
		@Override
		public String getAssertTestName() {
			return "assertResultNoReturn";
		}
		
		@Override
		public String getActualValue() {
			return "\"" + actualValue.getText() + "\"" + ", Type: " + actualValue.getType().name();
		}
		
		@Override
		public String getExpectedValue() {
			return "=== nothing returned";
		}
	}
}