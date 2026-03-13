# Revision history for FORTH

## 0.1.0.0 -- 2024-01-01

* Initial implementation with *, DUP, and . (print)
* Added +, -, /, ^ (power) operators
* Added EMIT, CR, STR, CONCAT2, CONCAT3 built-in functions
* Added DROP, SWAP, OVER, ROT stack operations
* Added =, <, >, MOD, NEGATE, ABS, MAX, MIN operations
* Added user-defined functions via : NAME ... ; syntax
* Stack not-empty detection at end of execution
* 10 functional test files (t1.4TH through t10.4TH)
* Comprehensive HSpec unit tests for Val, Eval, and Interpret modules
