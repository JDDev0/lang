package me.jddev0.module.lang;

import java.util.Arrays;

import me.jddev0.module.lang.DataObject.DataType;
import me.jddev0.module.lang.DataObject.DataTypeConstraint;
import me.jddev0.module.lang.DataObject.StructObject;

public class LangCompositeTypes {
	private static final DataTypeConstraint TYPE_CONSTRAINT_OPTIONAL_TEXT = DataTypeConstraint.fromAllowedTypes(Arrays.asList(
			DataType.NULL, DataType.TEXT
	));
	
	public static final StructObject STRUCT_STACK_TRACE_ELEMENT = new StructObject(new String[] {
			"$path",
			"$file",
			"$functionName",
			"$modulePath",
			"$moduleFile"
	}, new DataTypeConstraint[] {
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT,
			TYPE_CONSTRAINT_OPTIONAL_TEXT
	});
	public static StructObject createStackTraceElement(String path, String file, String functionName, String modulePath,
			String moduleFile) {
		return new StructObject(LangCompositeTypes.STRUCT_STACK_TRACE_ELEMENT, new DataObject[] {
				new DataObject(path),
				new DataObject(file),
				new DataObject(functionName),
				new DataObject(modulePath),
				new DataObject(moduleFile)
		});
	}
	
	private LangCompositeTypes() {}
}