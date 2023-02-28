# The Lang Programming Language

This project makes simple translation files Turing complete

## Features

In lang there a many predefined functions and operators which make development of programs easy.

### LangShell (REPL)

The Lang Shell is a REPL shell with syntax highlighting and autocompletes to aid you during development.

### Combinator functions

One of the main feature of this programming language is the combinator function system. You can use more than 100 predefined combinator functions. Combinator functions can be created by partially calling and combining combinator functions

## Lang Modules

Lang modules can be used to put common code into one library which can be re-used easily.
Here is a list of all existing modules for lang:
- The [Lang Example Native Module](https://github.com/JDDev0/LangExampleNativeModule) is a reference implementation of a lang native module. It can be used as a base for creating new modules.
- The [Lang IO Module](https://github.com/JDDev0/LangIOModule) is a lang native module providing basic IO operations for files.

### Native Lang Modules

Lang modules can contain native code (In the standard lang implementation native code is Java code). In the [Lang Example Native Module](https://github.com/JDDev0/LangExampleNativeModule) repository a build script can be found in order to create modules containing nativ code easily.

## Docs and examples

**Language definitions**: See the lang docs repository for details: [Lang Docs](https://github.com/lang-programming/docs) and checkout the .lang files located in /assets/<br>
**Language Tutorial**: You can find many tutorial lang files in /assets/tuts/.<br>
**Language code examples**: Some examples are in /assets/examples/, many more are on [Rosetta Code](https://rosettacode.org/wiki/lang).<br>

## "TermIO-Control" window commands

**Execution of lang file**: Type "executeLang -*Path to .lang file*" in the white text input in the "TermIO-Control" window<br>
**LangShell** (REPL): Type "startShell" in the white text input in the "TermIO-Control" window<br>
**Print AST tree**: Type "printAST -*Path to .lang file*" for parsing a Lang file and printing the parsed AST tree<br>
**4K-Support**: Type "toggle4k" for a larger font in the "TermIO-Control" window and the "LangShell" window<br>

### "TermIO-Control" window commands from terminal/console

You can also run "java -jar Lang.jar -**command** **args**" in a terminal or console.<br>
If "-e CODE" is used in Linux single quotes should be used for the CODE argument to prevent the shell from parsing $-shell variables and to enable multiline code execution (e.g. "java -jar Lang.jar -e '$a = test<br>
func.println($a)')<br>
You can use the "-nolog" argument to disable log file logging<br>

## Breaking changes

- **v1.0.0**:
  - func.arrayDelete was renamed to func.arrayReset
  - func.arrayClear was removed
  - The 2 args version of func.arrayMake was removed
  - func.copyAfterFP was removed (Use static vars or call-by-pointer instead)
  - func.split was changed and is not backwards compatible
  - Pointer redirection is no longer supported
  - Array variable names starting with "&LANG_" are no longer allowed
  - Var/Array pointer names
  - Many deprecated methods and classes in the Lang class won't work as expected
  - Array names written in a text would now print the array's content instead of the variable name (Variable name of an array can be gotten by "\\&array" \[Escaping of "&"\])
  - The Lang class is now final
  - "return $... = ", "return &... = ", and "return fp.... = " will now be parsed as an assignment (If you want to return something with " = ", you have to escape the "=": "return ... \\= ...")
  - linker function will no longer return 0 or -1 for successful or unsuccessful execution
  - Change of condition operator precedence and associativity
  - "$LANG\_COMPILER\_VERSION" was removed and replaced with "$LANG\_VERSION"
  - "func.isCompilerVersionNewer()" was removed and replaced with "func.isLangVersionNewer()"
  - "func.isCompilerVersionOlder()" was removed and replaced with "func.isLangVersionOlder()"
  - "func.exec()" can access variables defined in the caller's scope
  - "$LANG\_ERRNO" is now static: If "$LANG\_ERRNO" is set inside a function, "$LANG\_ERRNO" in the caller scope will also be this value
  - Pointer redirection of lang vars is no longer possible
  - Whitespaces after "(" and before ")" in the argument parsing of function calls will now be ignored

