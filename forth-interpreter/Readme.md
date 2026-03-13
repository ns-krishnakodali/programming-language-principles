# FORTH Interpreter

A simple FORTH language interpreter written in Haskell.

## Building

Make sure you have GHC and Cabal installed, then from the project root:

```bash
cabal update
cabal build
```

## Running the Interpreter

Run with a FORTH source file:

```bash
cabal run FORTH -- tests/t1.4TH
```

If the stack is not empty after execution, the interpreter prints a warning with the remaining stack contents.

## Running Unit Tests

Using `cabal`:

```bash
cabal test
```

Using `runhaskell`:

```bash
runhaskell ValSpec.hs
runhaskell EvalSpec.hs
runhaskell InterpretSpec.hs
```

Execute test automation and validation using

```bash
./run_tests.sh
```

## Running Functional Tests

Each `tests/tN.4TH` file has a corresponding `tests/tN.out` expected-output file. To verify:

```bash
chmod +x run_tests.sh
./run_tests.sh
```

## Supported Operations

### Arithmetic Operators

| Operator | Description                      | Example            |
|----------|----------------------------------|--------------------|
| `+`      | Addition                         | `2 3 +` → 5        |
| `-`      | Subtraction                      | `10 3 -` → 7       |
| `*`      | Multiplication                   | `2 3 *` → 6        |
| `/`      | Integer/float division           | `12 4 /` → 3       |
| `^`      | Power                            | `2 10 ^` → 1024    |
| `MOD`    | Modulo                           | `10 3 MOD` → 1     |
| `NEGATE` | Negate top of stack              | `5 NEGATE` → -5    |
| `ABS`    | Absolute value                   | `-5 ABS` → 5       |
| `MAX`    | Maximum of top two               | `3 7 MAX` → 7      |
| `MIN`    | Minimum of top two               | `3 7 MIN` → 3      |

All arithmetic operators preserve integer types when both arguments are integers. If either argument is a float, the result is a float.

### Stack Operations

| Operator | Description                          | Example                  |
|----------|--------------------------------------|--------------------------|
| `DUP`    | Duplicate top of stack               | `5 DUP` → 5 5            |
| `DROP`   | Remove top of stack                  | `1 2 DROP` → 1           |
| `SWAP`   | Swap top two elements                | `1 2 SWAP` → 2 1         |
| `OVER`   | Copy second element to top           | `1 2 OVER` → 1 2 1       |
| `ROT`    | Rotate top three (third to top)      | `1 2 3 ROT` → 2 3 1      |

### Output Operations

| Operator | Description                                      | Example             |
|----------|--------------------------------------------------|-------------------- |
| `.`      | Pop and print top of stack                       | `42 .` prints "42"  |
| `EMIT`   | Pop number and print its ASCII character         | `65 EMIT` prints "A"|
| `CR`     | Print a newline                                  | `CR`                |

### String Operations

| Operator  | Description                                     | Example                          |
|-----------|-------------------------------------------------|----------------------------------|
| `STR`     | Convert top of stack to string                  | `42 STR` → "42"                  |
| `CONCAT2` | Concatenate top 2 strings                       | `"a" "b" CONCAT2` → "ab"         |
| `CONCAT3` | Concatenate top 3 strings                       | `"a" "b" "c" CONCAT3` → "abc"    |

### Comparison Operators

| Operator | Description         | Result                  |
|----------|---------------------|-------------------------|
| `=`      | Equality            | 1 if equal, 0 otherwise |
| `<`      | Less than           | 1 if true, 0 otherwise  |
| `>`      | Greater than        | 1 if true, 0 otherwise  |

### User-Defined Functions (Bonus)

Define new words using the standard FORTH `: ... ;` syntax:

```forth
: SQUARE DUP * ;
5 SQUARE .
```

This prints `25`. User-defined functions can call other user-defined functions:

```forth
: SQUARE DUP * ;
: QUAD SQUARE SQUARE ;
2 QUAD .
```

This prints `16` (2 squared is 4, 4 squared is 16).

## Project Structure

| File              | Description                                     |
|-------------------|-------------------------------------------------|
| `Val.hs`          | Value types (Integer, Real, Id) and conversions |
| `Eval.hs`         | Built-in operator and function evaluation       |
| `Interpret.hs`    | Main interpreter loop with user-defined support |
| `Main.hs`         | Entry point, file I/O, stack-empty check        |
| `ValSpec.hs`      | Unit tests for Val module                       |
| `EvalSpec.hs`     | Unit tests for Eval module                      |
| `InterpretSpec.hs`| Unit tests for Interpret module                 |
| `tests/`          | Functional test files (.4TH) and outputs (.out) |

## Design Notes

- **Type preservation**: Integer operations on two integers produce an integer result. If either operand is a float, the result is a float. This avoids unnecessary precision loss.
- **User-defined functions**: Implemented via a pre-processing pass that extracts `: NAME ... ;` definitions into a `Map String [Val]` dictionary. During evaluation, when an identifier matches a dictionary entry, its body is expanded and evaluated inline. This supports recursive expansion (a user word can call other user words).
- **Stack order for binary operators**: Following FORTH convention, for non-commutative operators like `-` and `/`, the second element on the stack is the left operand. So `5 3 -` computes 5 − 3 = 2.
- **Error handling**: Stack underflow and type errors produce Haskell `error` calls with descriptive messages.
