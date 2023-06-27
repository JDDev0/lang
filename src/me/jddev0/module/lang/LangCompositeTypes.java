package me.jddev0.module.lang;

import java.util.Arrays;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraint;
import me.jddev0.module.lang.DataObject.StructObject;

/**
 * Lang-Module<br>
 * Definition of Lang composite types
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public class LangCompositeTypes {
	private static final DataTypeConstraint TYPE_CONSTRAINT_OPTIONAL_TEXT = DataTypeConstraint.fromAllowedTypes(Arrays.asList(
			DataType.NULL, DataType.TEXT
	));
	
	private static final DataTypeConstraint TYPE_CONSTRAINT_DOUBLE_ONLY = DataTypeConstraint.fromAllowedTypes(Arrays.asList(
			DataType.DOUBLE
	));
	
	private static final DataTypeConstraint TYPE_CONSTRAINT_INT_ONLY = DataTypeConstraint.fromAllowedTypes(Arrays.asList(
			DataType.INT
	));
	
	public static final StructObject STRUCT_STACK_TRACE_ELEMENT = new StructObject(new String[] {
			"$path",
			"$file",
			"$lineNumber",
			"$functionName",
			"$modulePath",
			"$moduleFile"
	}, new DataTypeConstraint[] {
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_INT_ONLY,
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT
	});
	public static StructObject createStackTraceElement(String path, String file, int lineNumber, String functionName,
			String modulePath, String moduleFile) {
		return new StructObject(LangCompositeTypes.STRUCT_STACK_TRACE_ELEMENT, new DataObject[] {
				new DataObject(path),
				new DataObject(file),
				new DataObject().setInt(lineNumber),
				new DataObject(functionName),
				new DataObject(modulePath),
				new DataObject(moduleFile)
		});
	}
	
	public static final StructObject STRUCT_COMPLEX = new StructObject(new String[] {
			"$real",
			"$imag"
	}, new DataTypeConstraint[] {
			TYPE_CONSTRAINT_DOUBLE_ONLY,
			TYPE_CONSTRAINT_DOUBLE_ONLY
	});
	public static StructObject createComplex(double real, double imag) {
		return new StructObject(LangCompositeTypes.STRUCT_COMPLEX, new DataObject[] {
				new DataObject().setDouble(real),
				new DataObject().setDouble(imag)
		});
	}
	
	public static final StructObject STRUCT_PAIR = new StructObject(new String[] {
			"$first",
			"$second"
	});
	public static StructObject createPair(DataObject first, DataObject second) {
		return new StructObject(LangCompositeTypes.STRUCT_PAIR, new DataObject[] {
				first,
				second
		});
	}
	
	private LangCompositeTypes() {}
}