module Eval where

-- This file contains definitions for functions and operators

import Data.Char (chr)
import Val

-- takes a string and a stack and returns the stack
eval :: String -> [Val] -> [Val]
eval "+" (Integer x : Integer y : tl) = Integer (y + x) : tl
eval "+" (x : y : tl) = Real (toFloat y + toFloat x) : tl
eval "+" _ = error "Stack underflow"
eval "-" (Integer x : Integer y : tl) = Integer (y - x) : tl
eval "-" (x : y : tl) = Real (toFloat y - toFloat x) : tl
eval "-" _ = error "Stack underflow"
eval "*" (Integer x : Integer y : tl) = Integer (x * y) : tl
eval "*" (x : y : tl) = Real (toFloat x * toFloat y) : tl
eval "*" _ = error "Stack underflow"
eval "/" (Integer 0 : _) = error "Division by zero"
eval "/" (Integer x : Integer y : tl) = Integer (div y x) : tl
eval "/" (x : y : tl) = Real (toFloat y / toFloat x) : tl
eval "/" _ = error "Stack underflow"
eval "^" (Integer x : Integer y : tl) = Integer (y ^ x) : tl
eval "^" (x : y : tl) = Real (toFloat y ** toFloat x) : tl
eval "^" _ = error "Stack underflow"
eval "DUP" (x : tl) = x : x : tl
eval "DUP" [] = error "Stack underflow"
eval "DROP" (_ : tl) = tl
eval "DROP" [] = error "Stack underflow"
eval "SWAP" (x : y : tl) = y : x : tl
eval "SWAP" _ = error "Stack underflow"
eval "OVER" (x : y : tl) = y : x : y : tl
eval "OVER" _ = error "Stack underflow"
eval "ROT" (x : y : z : tl) = z : x : y : tl
eval "ROT" _ = error "Stack underflow"
eval "STR" (x : tl) = valToStr x : tl
eval "STR" [] = error "Stack underflow"
eval "CONCAT2" (Id y : Id x : tl) = Id (x ++ y) : tl
eval "CONCAT2" (_ : _ : _) = error "CONCAT2 requires string arguments"
eval "CONCAT2" _ = error "Stack underflow"
eval "CONCAT3" (Id z : Id y : Id x : tl) = Id (x ++ y ++ z) : tl
eval "CONCAT3" (_ : _ : _ : _) = error "CONCAT3 requires string arguments"
eval "CONCAT3" _ = error "Stack underflow"
eval "=" (Integer x : Integer y : tl) = Integer (if y == x then 1 else 0) : tl
eval "=" _ = error "Stack underflow"
eval "<" (Integer x : Integer y : tl) = Integer (if y < x then 1 else 0) : tl
eval "<" _ = error "Stack underflow"
eval ">" (Integer x : Integer y : tl) = Integer (if y > x then 1 else 0) : tl
eval ">" _ = error "Stack underflow"
eval "MOD" (Integer 0 : _) = error "Division by zero"
eval "MOD" (Integer x : Integer y : tl) = Integer (mod y x) : tl
eval "MOD" _ = error "Stack underflow"
eval "NEGATE" (Integer x : tl) = Integer (-x) : tl
eval "NEGATE" (Real x : tl) = Real (-x) : tl
eval "NEGATE" _ = error "Stack underflow"
eval "ABS" (Integer x : tl) = Integer (abs x) : tl
eval "ABS" (Real x : tl) = Real (abs x) : tl
eval "ABS" _ = error "Stack underflow"
eval "MAX" (Integer x : Integer y : tl) = Integer (max y x) : tl
eval "MAX" _ = error "Stack underflow"
eval "MIN" (Integer x : Integer y : tl) = Integer (min y x) : tl
eval "MIN" _ = error "Stack underflow"
eval s l = Id s : l

-- state is a stack and string pair
evalOut :: String -> ([Val], String) -> ([Val], String)
-- print element at the top of the stack
evalOut "." (Id x : tl, out) = (tl, out ++ x)
evalOut "." (Integer i : tl, out) = (tl, out ++ (show i))
evalOut "." (Real x : tl, out) = (tl, out ++ (show x))
evalOut "." ([], _) = error "Stack underflow"
evalOut "EMIT" (Integer i : tl, out) = (tl, out ++ [chr i])
evalOut "EMIT" (Real x : tl, out) = (tl, out ++ [chr (round x)])
evalOut "EMIT" ([], _) = error "Stack underflow"
evalOut "EMIT" _ = error "EMIT requires a numeric argument"
evalOut "CR" (stack, out) = (stack, out ++ "\n")
evalOut op (stack, out) = (eval op stack, out)
