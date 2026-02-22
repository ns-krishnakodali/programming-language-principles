# Delphi Interpreter

Delphi is an object-oriented extension of the Pascal language. This project implements a Delphi interpreter using Java
and ANTLR 4.

The interpreter parses and executes Delphi programs by walking the parse tree generated from the grammar.

THe source code is located under the package:

```bash
org.compiler.delphi
```

---

## Grammar

The `Delphi` grammar is located at:

```bash
src/main/antlr4/Delphi.g4
```

It extends Pascal with support for:

- Classes
- Constructors and destructors
- Encapsulation
- Inheritance
- Interfaces

ANTLR generates the lexer and parser during the Maven build process.

---

## Features

The interpreter supports:

- Class definitions and object creation
- Constructors and destructors
- Method declarations and invocation
- Global procedures and functions
- Inheritance
- Interfaces
- Integer and basic built-in operations
- Terminal input and output

---

## Requirements

- Java 21
- Maven
- IntelliJ (Recommended)

---

## Project Structure

```bash
src/main/antlr4
    Delphi.g4

src/main/java
    org/compiler/delphi/
      DelphiInterpreter.java

src/test/resources
    *.pas test programs

src/test/java
    JUnit test class
```

---

## Build Instructions

Generate ANTLR sources:

```bash
mvn clean generate-sources
```

In IntelliJ, mark the directory:

```bash
target/generated-sources/antlr4
```

as **Generated Sources Root**.

Then compile the project:

```bash
mvn clean compile
```

---

## Running Tests

All test programs are located in:

```bash
src/test/resources
```

To execute the test suite:

```bash
mvn test
```

Each JUnit test executes a specific `.pas `program located in `src/test/resources` and runs it through the interpreter.

---

ANTLR source generation is handled automatically by the Maven plugin during the build process.
