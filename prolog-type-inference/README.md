# OCaml-Style Type Inference in Prolog

A type inference engine for a subset of OCaml, implemented in SWI-Prolog using unification.

## Requirements

Install SWI-Prolog:

```bash
# Ubuntu / Debian
sudo apt-get install swi-prolog

# macOS
brew install swi-prolog
```

## Running the Interpreter

Start SWI-Prolog from the project directory:

```bash
swipl
```

Load the inference engine:

```prolog
?- consult('typeInf.pl').
```

Then query types directly:

```prolog
?- typeExp(iplus(X, Y), T).
X = Y, Y = T, T = int.

?- infer([gvLet(x, _, int), gvLet(y, _, fplus(float, float))], T).
T = unit.
```

Use `trace.` before a query to step through execution, and `notrace.` to stop.

## Running Unit Tests

Run all tests:

```bash
swipl -g "consult('typeInf.plt'), run_tests, halt"
```

Or from inside the SWI-Prolog REPL:

```prolog
?- consult('typeInf.plt'), run_tests.
```

Run a single test:

```prolog
?- run_tests(typeInf:bonus_29_match).
```

Reload after edits:

```prolog
?- load_test_files([]).
?- run_tests.
```

## Supported Statements

| Statement                                   | OCaml equivalent                    | Result type         |
|---------------------------------------------|-------------------------------------|---------------------|
| `gvLet(Name, T, Expr)`                      | `let name = expr`                   | `unit`              |
| `gfLet(Name, RetT, [Params], [Body])`       | `let name p1 p2 ... = body`         | `unit`              |
| `expr(E)`                                   | expression as statement             | type of `E`         |
| `return(E)`                                 | explicit return                     | type of `E`         |
| `if(Cond, [Then], [Else])`                  | `if cond then ... else ...`         | branch type         |
| `letIn(Name, T, Expr, [Body])`              | `let name = expr in body`           | type of body        |
| `for(V, Start, End, [Body])`                | `for v = start to end do ... done`  | `unit`              |
| `block([Stmts])`                            | `begin s1; s2; ... end`             | type of last stmt   |

A code block is a Prolog list of statements; its type is the type of the last statement.

## Supported Types

| Type              | Description                               |
|-------------------|-------------------------------------------|
| `int`, `float`    | Numeric primitives                        |
| `string`          | Strings                                   |
| `bool`            | Booleans (from comparisons, `and`, `or`)  |
| `unit`            | For statements with no value              |
| `[T1, T2, ..., R]`| Function type (last element is return)    |
| `tuple([T1,...])` | Tuple type (bonus)                        |
| `sum(T1, T2)`     | Sum type (bonus)                          |

## Built-in Functions

| Category    | Functions                                                          |
|-------------|--------------------------------------------------------------------|
| Int arith   | `iplus`, `iminus`, `imul`, `idiv`, `imod`, `ineg`                  |
| Float arith | `fplus`, `fminus`, `fmul`, `fdiv`, `fneg`                          |
| Conversions | `fToInt`, `iToFloat`, `sToInt`, `iToStr`                           |
| Int compare | `iless`, `igreater`, `ieq`, `ineq`, `ileq`, `igeq` → `bool`        |
| Float compare | `fless`, `fgreater`, `feq` → `bool`                              |
| String      | `sconcat`, `slength`                                               |
| Boolean     | `and`, `or`, `not`                                                 |
| IO          | `print`, `println`, `readInt`, `readStr`                           |

## Bonus Features

| Construct                                           | Description                    |
|-----------------------------------------------------|--------------------------------|
| `mkTuple([E1, E2, ...])`                            | Tuple construction             |
| `gvLetTuple([n1, n2], [T1, T2], Expr)`              | Tuple unpacking assignment     |
| `inl(E)`, `inr(E)`                                  | Sum type injections            |
| `match(Expr, [case(Pat, [Body]), ...])`             | Pattern matching on sum types  |

## Project Structure

| File          | Description                                                |
|---------------|------------------------------------------------------------|
| `typeInf.pl`  | Core inference engine (`typeExp`, `typeStatement`, `infer`)|
| `typeInf.plt` | Unit tests (42 tests across expressions, statements, bonus)|

## Design Notes

- **Inference via unification**: Prolog variables represent unknown types; unification during expression matching fills them in. For example, `typeExp(iplus(X, Y), T)` unifies `X`, `Y`, `T` all to `int`.
- **Function calls as signatures**: A call `f(a, b)` is matched by appending its result type and unifying against the stored signature `[T_a, T_b, T_ret]`. This works for both built-ins (`fType`) and user-defined functions (stored as `gvar` entries).
- **Global variable scope**: `gvLet`, `gfLet`, `letIn`, and `gvLetTuple` assert entries into the dynamic `gvar/2` predicate. `infer/2` calls `deleteGVars/0` first to clear state. `letIn` retracts its binding after its body is typed to preserve lexical scoping.
- **Cut in `gvLet`**: The `bType(T), !` commits to the first valid basic type, preventing infinite backtracking through recursive type definitions (lists, tuples, sums) when downstream statements fail.
- **Match statement**: Each case pattern must unify with the scrutinee type, and every case body must produce the same result type. Sum-typed values should be declared with their full `sum(T1, T2)` type for the `inr` branch to be inferable.
- **Error handling**: Type errors cause the relevant predicate to fail. Tests marked `[fail]` verify that ill-typed programs are correctly rejected.
