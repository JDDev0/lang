# The Lang Programming Language

This project makes simple translation files Turing complete

## Features

In Lang there a many predefined functions and operators which make development of programs easy.

### LangShell (REPL)

The Lang Shell is a REPL shell with syntax highlighting and autocompletes to aid you during development.

### Combinator functions

One of the main feature of this programming language is the combinator function system. You can use more than 100 predefined combinator functions. Combinator functions can be created by partially calling and combining combinator functions

## Lang Modules

Lang modules can be used to put common code into one library which can be re-used easily.
Here is a list of all existing modules for lang:
- The [Lang Example Native Module](https://github.com/JDDev0/LangExampleNativeModule) is a reference implementation of a Lang native module. It can be used as a base for creating new modules.
- The [Lang IO Module](https://github.com/JDDev0/LangIOModule) is a Lang native module providing basic IO operations for files.

### Native Lang Modules

Lang modules can contain native code (In the standard Lang implementation native code is Java code). In the [Lang Example Native Module](https://github.com/JDDev0/LangExampleNativeModule) repository a build script can be found in order to create modules containing nativ code easily.

## Docs and examples

**Language definitions**: See the Lang docs repository for details: [Lang Docs](https://github.com/lang-programming/docs) and checkout the .lang files located in /assets/<br>
**Language Tutorial**: You can find many tutorial Lang files in /assets/tuts/.<br>
**Language code examples**: Some examples are in /assets/examples/, many more are on [Rosetta Code](https://rosettacode.org/wiki/lang).<br>

## "TermIO-Control" window commands

**Execution of Lang file**: Type "executeLang -*Path to .lang file*" in the white text input in the "TermIO-Control" window<br>
**LangShell** (REPL): Type "startShell" in the white text input in the "TermIO-Control" window<br>
**Print AST tree**: Type "printAST -*Path to .lang file*" for parsing a Lang file and printing the parsed AST tree<br>
**4K-Support**: Type "toggle4k" for a larger font in the "TermIO-Control" window and the "LangShell" window<br>

### "TermIO-Control" window commands from terminal/console

You can also run "java -jar Lang.jar -**command** **args**" in a terminal or console.<br>
If "-e CODE" is used in Linux single quotes should be used for the CODE argument to prevent the shell from parsing $-shell variables and to enable multiline code execution (e.g. "java -jar Lang.jar -e '$a = test<br>
func.println($a)')<br>
You can use the "-log" argument to enable log file logging<br>

## Building from source

- This project uses the latest version (= latest commit) of the [Lang Interpreter Project](https://github.com/lang-programming/lang-interpreter)
- If a breaking change is introduced in the lang-interpreter this project will most likely not compile with the latest released lang-interpreter version
  - In that case you should check out the [Building from source](https://github.com/lang-programming/lang-interpreter#build-from-source) section of the lang-interpreter project
  - This project is already setup to use a locally built version of the lang-interpreter if it is newer than the latest release of the lang-interpreter
