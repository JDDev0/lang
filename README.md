# The Lang Programming Language

This project makes simple translation files Turing complete

### "TermIO-Control" window commands

**Execution of lang file**: Type "executeLang -*Path to .lang file*" in the white text input in the "TermIO-Control" window<br>
**LangShell**: Type "startShell" in the white text input in the "TermIO-Control" window<br>
**Language definitions**: See comment in src/me/jddev0/module/io/Lang.java and checkout assets/features.lang (It contains a basic overview of the programming language with explanations)<br>
**Language Tutorial**: You can find many tutorial lang files in /assets/tuts/.<br>
**Print AST tree**: Type "printAST -*Path to .lang file*" for parsing a Lang file and printing the parsed AST tree<br>
**4K-Support**: Type "toggle4k" for a larger font in the "TermIO-Control" window and the "LangShell" window<br>

#### "TermIO-Control" window commands from terminal/console

You can also run "java -jar Lang.jar -**command** **args**" in a terminal or console.<br>
If "-e CODE" is used in Linux single quotes should be used for the CODE argument to prevent the shell from parsing $-shell variables and to enable multiline code execution (e.g. "java -jar Lang.jar -e '$a = test<br>
func.println($a)')<br>

### Breaking changes

- **v1.0.0**:
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

