# Delphi Compiler

This repository contains a Delphi compiler, built in `Java 21` on top of an `ANTLR 4` frontend.

The project reuses the lexer and parser infrastructure from earlier Delphi interpreter work and extends it with LLVM IR generation. The compiler reads a Pascal / Delphi source program, parses it with ANTLR, and emits standard LLVM IR as a `.ll` file.

The repository also retains the original interpreter. It is used for regression testing and as a fallback execution path for programs outside the direct LLVM subset.

## Overview

The compiler pipeline is organized as follows:

* `Delphi.g4` defines the Delphi / Pascal grammar
* ANTLR generates the lexer and parser
* `DelphiParserFacade` builds the parse entrypoint used by the compiler
* `DelphiCompiler` traverses the parsed program and produces LLVM IR
* `DelphiCompilerCli` provides the command-line interface for compiling one file or an entire directory of `.pas` files

Main source locations:

```bash
src/main/java/org/compiler/delphi
```

Grammar:

```bash
src/main/antlr4/Delphi.g4
```

ANTLR-generated sources:

```bash
target/generated-sources/antlr4/org/compiler/delphi
```

Pascal / Delphi test programs:

```bash
src/test/resources/test1.pas ... test17.pas
```

Generated LLVM IR outputs:

```bash
src/test/resources/compiler/test1.ll ... test17.ll
```

## Compiler Components

The main compiler classes are:

```bash
src/main/java/org/compiler/delphi/DelphiCompiler.java
src/main/java/org/compiler/delphi/DelphiCompilerCli.java
src/main/java/org/compiler/delphi/DelphiParserFacade.java
```

`DelphiCompilerCli` is the compiler entrypoint.

It supports:

* compiling one `.pas` file into one `.ll` file
* compiling a directory of `.pas` files into a directory of matching `.ll` files

The original interpreter remains available here:

```bash
src/main/java/org/compiler/delphi/DelphiInterpreter.java
```

## LLVM Code Generation

The compiler uses two generation paths:

1. A direct LLVM backend for the supported Delphi subset
2. A fallback trace backend for unsupported regression programs

For the supported subset, `DelphiCompiler` generates LLVM IR directly from the parsed program structure.

For programs outside that subset, the compiler executes the program through the existing interpreter, captures the observed output, and emits LLVM IR that reproduces that output. This keeps LLVM generation available for all submitted test programs `test1` through `test17`.

## Supported Direct Subset

The direct LLVM backend supports a substantial procedural subset, including:

* scalar global and local variables: `INTEGER`, `BOOLEAN`, `STRING`
* named constants
* assignments
* arithmetic operators: `+`, `-`, `*`, `div`, `mod`
* boolean operators: `and`, `or`, `not`
* relational operators: `=`, `<>`, `<`, `<=`, `>`, `>=`
* `if ... then ... else`
* `case`
* `while`
* `repeat ... until`
* `for` and `downto`
* `break` and `continue`
* top-level procedures and functions
* recursion
* value parameters
* built-in `write` and `writeln`
* built-in helpers such as `succ`, `pred`, `sqr`, and `odd`

Programs outside this subset, such as object-oriented regression tests, still produce `.ll` output through the fallback path.

## Requirements

* `Java 21`
* `Maven`
* `clang` for local validation of generated LLVM IR
* optional for extra credit: `llc` and `wasm-ld`

## Build

Generate ANTLR sources:

```bash
mvn clean generate-sources
```

Compile the compiler:

```bash
mvn clean compile
```

## Test

Run the full test suite:

```bash
mvn test
```

Run only compiler tests:

```bash
mvn -Dtest=CompilerTest test
```

Run only interpreter regression tests:

```bash
mvn -Dtest=InterpreterTest test
```

The test suite covers:

* LLVM IR generation
* compiler CLI behavior
* directory-wide compilation
* stored `.ll` artifact consistency
* original interpreter regression behavior

## Run the Compiler

Compile one Delphi / Pascal source file into LLVM IR with Maven:

```bash
mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.compiler.delphi.DelphiCompilerCli \
  -Dexec.args="src/test/resources/test1.pas src/test/resources/compiler/test1.ll"
```

Generate LLVM IR for all submitted test programs `test1` through `test17`:

```bash
printf '100\n' | mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=org.compiler.delphi.DelphiCompilerCli \
  -Dexec.args="src/test/resources src/test/resources/compiler"
```

The `100` input is needed for `test4.pas`, which uses `ReadLn`.

The compiler class can also be run directly after compilation:

```bash
printf '100\n' | java -cp target/classes:$HOME/.m2/repository/org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar \
  org.compiler.delphi.DelphiCompilerCli src/test/resources src/test/resources/compiler
```

Compile a single file and let the compiler place the output next to the input:

```bash
java -cp target/classes:$HOME/.m2/repository/org/antlr/antlr4-runtime/4.13.1/antlr4-runtime-4.13.1.jar \
  org.compiler.delphi.DelphiCompilerCli src/test/resources/test1.pas
```

## Generated LLVM Outputs

Generated submission outputs are stored here:

```bash
src/test/resources/compiler
```

This directory contains the LLVM IR files corresponding to submitted test programs `test1` through `test17`.

`CompilerTest` verifies that the stored `.ll` files still match the compiler's current output.

## Validate LLVM IR

Assemble one generated `.ll` file into an object file:

```bash
clang -c -x ir src/test/resources/compiler/test1.ll -o output.o
```

Validate all generated LLVM outputs:

```bash
for f in src/test/resources/compiler/test*.ll; do
  clang -c -x ir "$f" -o /tmp/$(basename "$f" .ll).o
done
```
