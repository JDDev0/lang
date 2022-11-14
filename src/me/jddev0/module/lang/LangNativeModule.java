package me.jddev0.module.lang;

import java.util.List;

/**
 * Lang-Module<br>
 * Lang native module interface
 * 
 * @author JDDev0
 * @version v1.0.0
 */
public abstract class LangNativeModule {
	protected LangInterpreter interpreter;
	protected LangModule module;
	
	final void setInterpreter(LangInterpreter interpreter) {
		this.interpreter = interpreter;
	}
	
	final void setModule(LangModule module) {
		this.module = module;
	}
	
	/**
	 * Will be called if the module is loaded
	 * 
	 * @param args provided to "func.loadModule()" [modulePath included] separated with ARGUMENT_SEPARATORs
	 * @param interpreter (LII can be created with new LangInterpreterInterface(interpreter))
	 * @return Will be returned for the "linker.loadModule()" call
	 */
	public abstract DataObject load(List<DataObject> args, final int SCOPE_ID);
	
	/**
	 * Will be called if the module is unloaded
	 * 
	 * @param args provided to "func.unloadModule()" [modulePath included] separated with ARGUMENT_SEPARATORs
	 * @param interpreter (LII can be created with new LangInterpreterInterface(interpreter))
	 * @return Will be returned for the "linker.unloadModule()" call
	 */
	public abstract DataObject unload(List<DataObject> args, final int SCOPE_ID);
}