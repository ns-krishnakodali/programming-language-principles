# Delphi Interpreter

Delphi is an object-oriented extension of the Pascal language. This project implements a Delphi interpreter using `Java`
and `ANTLR 4`.

The interpreter parses and executes Delphi programs by walking the parse tree generated from the grammar.

The source code is located under the package:

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
- While-do, for-do, and repeat-until loops
- Break and continue keywords
- User-defined procedures and functions with formal parameters

`ANTLR` generates the lexer and parser during the Maven build process.

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
- While-do and for-do loops with break and continue
- Repeat-until loops with break and continue
- Static scoping for procedures and functions
- Recursive function calls
- Formal parameter passing in procedures and functions
- Constant propagation

---

## Scoping

The interpreter implements static (lexical) scoping:

- The program's main block defines the global scope
- Each procedure or function call creates a new local scope
- Functions can only see their own locals and global variables
- Intermediate caller scopes are not visible to the callee
- While, for, and repeat loops each create their own sub-scope
- Scope chains are maintained and restored correctly across recursive calls

---

## Break and Continue

The `break` and `continue` keywords are supported inside all loop types:

- `break` exits the innermost enclosing loop immediately
- `continue` skips to the next iteration of the innermost loop
- Both work correctly in nested loop scenarios where only the inner loop is affected

---

## Constant Propagation

A simple version of constant propagation is implemented. Expressions consisting entirely of compile-time constants and
literal values are evaluated at parse time. For example, given:

```pascal
const
  A = 10;
  B = 11;
  C = 2;
```

The expression `C * (A + B)` is folded to `42` during interpretation. The `tryConstantFold` and `printAST` methods on
the interpreter can be used to inspect folded results.

---

## Formal Parameter Passing

Procedures and functions support typed formal parameter declarations. Parameters are correctly scoped within the
function's local scope and do not leak into the global scope. The interpreter tracks parameter names, types, and
var/value mode for each declared parameter.

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
      DelphiMain.java

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

Each JUnit test executes a specific `.pas` program located in `src/test/resources` and runs it through the interpreter.

---

## Test Coverage

| Test   | Covers                                                    |
|--------|-----------------------------------------------------------|
| test1  | Arithmetic, if-then-else                                  |
| test2  | For loop, while loop, repeat-until                        |
| test3  | Class, constructor, destructor, methods                   |
| test4  | Class with ReadLn input, method calls                     |
| test5  | Inheritance, method override                              |
| test6  | Multi-level inheritance, polymorphic describe             |
| test7  | Interface declaration, multiple classes                   |
| test8  | Global functions with parameters, nested calls            |
| test9  | Class with constructor parameters, field access           |
| test10 | Case statement, constants, for loop                       |
| test11 | Break in while, for, downto, and nested loops             |
| test12 | Continue in for, while, and repeat loops                  |
| test13 | Static scoping across procedure and function calls        |
| test14 | Nested procedures, recursion (Factorial, Fibonacci)       |
| test15 | Formal parameters, composed calls, boolean functions      |
| test16 | Break and continue in repeat-until and nested loops       |
| test17 | Constant propagation with named constants and expressions |

---

ANTLR source generation is automatically handled by the Maven plugin during the build process based on the grammar.
