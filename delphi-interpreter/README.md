# Delphi Interpreter and LLVM Compiler

This project keeps the existing Delphi interpreter from Projects 1 and 2 and adds a second backend that compiles a supported Delphi/Pascal subset to LLVM IR.

The frontend is still based on `ANTLR 4` and `Java 21`. The parser/lexer are reused as-is; no ANTLR-generated sources are manually edited.

## Source Layout

Main sources live under:

```bash
src/main/java/org/compiler/delphi
```

Grammar:

```bash
src/main/antlr4/Delphi.g4
```

Generated parser sources are produced by Maven under:

```bash
target/generated-sources/antlr4/org/compiler/delphi
```

Compiled generated classes end up under:

```bash
target/classes/org/compiler/delphi
```

Sample generated LLVM IR files are checked in under:

```bash
examples/llvm
```

## Backends

### Interpreter

The original interpreter is still present in:

```bash
src/main/java/org/compiler/delphi/DelphiInterpreter.java
```

It continues to support the Project 1 / Project 2 execution path and is still covered by the existing test suite.

### LLVM Compiler

The new compiler backend is implemented in:

```bash
src/main/java/org/compiler/delphi/DelphiCompiler.java
src/main/java/org/compiler/delphi/DelphiCompilerMain.java
src/main/java/org/compiler/delphi/DelphiFrontend.java
```

`DelphiCompilerMain` compiles a `.pas` file into a `.ll` file.

## Compiler Subset

The LLVM backend intentionally implements a procedural subset that covers roughly 70% of the language work from the earlier projects:

- Global and local scalar variables: `INTEGER`, `BOOLEAN`, `STRING`
- Global and local named constants
- Assignments
- Integer arithmetic: `+`, `-`, `*`, `div`, `mod`
- Boolean operators: `and`, `or`, `not`
- Relational operators: `=`, `<>`, `<`, `<=`, `>`, `>=`
- `if ... then ... else`
- `case`
- `while`, `repeat ... until`, `for`, `downto`
- `break` and `continue`
- Top-level procedures and functions
- Recursive functions
- Value parameters
- Built-in `write` and `writeln`
- Built-in helper functions: `succ`, `pred`, `sqr`, `odd`

Unsupported in the compiler backend:

- Classes, constructors, destructors, inheritance, interfaces
- Nested procedures/functions
- `var` parameters
- Arrays, records, sets, files, pointers, `with`
- `read` / `readln`
- `REAL`
- String comparison/concatenation in code generation
- `goto`

Unsupported compiler features fail with a clear `CompilerException` instead of emitting invalid IR.

## Requirements

- Java 21
- Maven
- `clang` if you want to validate or assemble generated LLVM IR locally
- Optional for WASM follow-up: `llc` and `wasm-ld`

## Build

Generate parser sources:

```bash
mvn clean generate-sources
```

Compile everything:

```bash
mvn clean compile
```

Run the full test suite:

```bash
mvn test
```

The test suite now covers both:

- Existing interpreter behavior (`InterpreterTest`)
- LLVM IR generation and unsupported-feature rejection (`CompilerTest`)

## Generate LLVM IR

Compile a Delphi/Pascal file into LLVM IR:

```bash
mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.compiler.delphi.DelphiCompilerMain \
  -Dexec.args="src/test/resources/test1.pas output.ll"
```

You can also invoke the class directly after compilation:

```bash
java -cp target/classes:$HOME/.m2/repository/org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar \
  org.compiler.delphi.DelphiCompilerMain src/test/resources/test1.pas output.ll
```

If no output path is provided, the compiler writes a sibling `.ll` file next to the input.

## Checked-In LLVM Examples

The following sample Delphi programs have checked-in LLVM IR outputs:

- `src/test/resources/test1.pas` -> `examples/llvm/test1.ll`
- `src/test/resources/test2.pas` -> `examples/llvm/test2.ll`
- `src/test/resources/test8.pas` -> `examples/llvm/test8.ll`
- `src/test/resources/test10.pas` -> `examples/llvm/test10.ll`
- `src/test/resources/test15.pas` -> `examples/llvm/test15.ll`
- `src/test/resources/test17.pas` -> `examples/llvm/test17.ll`

These example `.ll` files were also assembled locally with `clang -c -x ir ...` to catch IR syntax issues.

## Validate or Assemble LLVM IR

To assemble a generated `.ll` file into an object file:

```bash
clang -c -x ir output.ll -o output.o
```

On this machine, `clang` is available and was used to validate the checked-in `.ll` examples.

## Optional WASM Follow-Up

The required implementation in this repository stops at LLVM IR generation. If you want to continue to WebAssembly, a typical toolchain flow is:

```bash
llc -march=wasm32 -filetype=obj output.ll -o output.o
wasm-ld --no-entry --export=main --allow-undefined -o output.wasm output.o
```

Notes:

- `llc` and `wasm-ld` were not installed in the current environment, so this step was not executed here.
- The current IR uses `printf` for `write` / `writeln`, so a browser-ready runtime would need a compatible libc/WASI path or a different output strategy for host I/O.

## Existing Interpreter Coverage

The original interpreter tests still exercise:

- Arithmetic and conditionals
- Loops, `break`, `continue`
- Classes and object behavior
- Inheritance and interfaces
- Procedures/functions and parameter passing
- Static scoping
- Recursion
- Constant propagation

Existing functionality from Projects 1 and 2 remains intact.
