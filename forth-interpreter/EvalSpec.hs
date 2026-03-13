-- HSpec tests for Eval.hs
-- Execute: runhaskell EvalSpec.hs

import Control.Exception (evaluate)
import Eval
import Test.Hspec
import Test.QuickCheck
import Val

main :: IO ()
main = hspec $ do
  describe "eval" $ do
    context "+" $ do
      it "adds integers" $ do
        eval "+" [Integer 3, Integer 2] `shouldBe` [Integer 5]

      it "adds integer and float" $ do
        eval "+" [Integer 2, Real 3.0] `shouldBe` [Real 5.0]
        eval "+" [Real 3.0, Integer 2] `shouldBe` [Real 5.0]

      it "adds floats" $ do
        eval "+" [Real 1.5, Real 2.5] `shouldBe` [Real 4.0]

      it "preserves rest of stack" $ do
        eval "+" [Integer 1, Integer 2, Integer 99] `shouldBe` [Integer 3, Integer 99]

      it "errors on too few arguments" $ do
        evaluate (eval "+" []) `shouldThrow` errorCall "Stack underflow"
        evaluate (eval "+" [Integer 2]) `shouldThrow` errorCall "Stack underflow"

    context "-" $ do
      it "subtracts integers" $ do
        eval "-" [Integer 3, Integer 5] `shouldBe` [Integer 2]

      it "subtracts in correct order (second - first)" $ do
        eval "-" [Integer 5, Integer 3] `shouldBe` [Integer (-2)]

      it "subtracts with floats" $ do
        eval "-" [Real 1.5, Real 5.0] `shouldBe` [Real 3.5]

      it "errors on too few arguments" $ do
        evaluate (eval "-" []) `shouldThrow` errorCall "Stack underflow"

    context "*" $ do
      it "multiplies integers" $ do
        eval "*" [Integer 2, Integer 3] `shouldBe` [Integer 6]

      it "multiplies floats" $ do
        eval "*" [Integer 2, Real 3.0] `shouldBe` [Real 6.0]
        eval "*" [Real 3.0, Integer 3] `shouldBe` [Real 9.0]
        eval "*" [Real 4.0, Real 3.0] `shouldBe` [Real 12.0]

      it "errors on too few arguments" $ do
        evaluate (eval "*" []) `shouldThrow` errorCall "Stack underflow"
        evaluate (eval "*" [Integer 2]) `shouldThrow` errorCall "Stack underflow"

    context "/" $ do
      it "divides integers" $ do
        eval "/" [Integer 3, Integer 12] `shouldBe` [Integer 4]

      it "integer division truncates" $ do
        eval "/" [Integer 3, Integer 10] `shouldBe` [Integer 3]

      it "divides with floats" $ do
        eval "/" [Real 2.0, Real 5.0] `shouldBe` [Real 2.5]

      it "errors on division by zero" $ do
        evaluate (eval "/" [Integer 0, Integer 5]) `shouldThrow` errorCall "Division by zero"

      it "errors on too few arguments" $ do
        evaluate (eval "/" []) `shouldThrow` errorCall "Stack underflow"

    context "^" $ do
      it "computes integer power" $ do
        eval "^" [Integer 3, Integer 2] `shouldBe` [Integer 8]

      it "computes 2^10" $ do
        eval "^" [Integer 10, Integer 2] `shouldBe` [Integer 1024]

      it "computes x^0 = 1" $ do
        eval "^" [Integer 0, Integer 5] `shouldBe` [Integer 1]

      it "computes float power" $ do
        eval "^" [Real 2.0, Real 3.0] `shouldBe` [Real 9.0]

      it "errors on too few arguments" $ do
        evaluate (eval "^" []) `shouldThrow` errorCall "Stack underflow"

    context "DUP" $ do
      it "duplicates values" $ do
        eval "DUP" [Integer 2] `shouldBe` [Integer 2, Integer 2]
        eval "DUP" [Real 2.2] `shouldBe` [Real 2.2, Real 2.2]
        eval "DUP" [Id "x"] `shouldBe` [Id "x", Id "x"]

      it "errors on empty stack" $ do
        evaluate (eval "DUP" []) `shouldThrow` errorCall "Stack underflow"

    context "DROP" $ do
      it "drops top element" $ do
        eval "DROP" [Integer 1, Integer 2] `shouldBe` [Integer 2]

      it "drops from single-element stack" $ do
        eval "DROP" [Integer 1] `shouldBe` []

      it "errors on empty stack" $ do
        evaluate (eval "DROP" []) `shouldThrow` errorCall "Stack underflow"

    context "SWAP" $ do
      it "swaps top two elements" $ do
        eval "SWAP" [Integer 1, Integer 2] `shouldBe` [Integer 2, Integer 1]

      it "preserves rest of stack" $ do
        eval "SWAP" [Integer 1, Integer 2, Integer 3] `shouldBe` [Integer 2, Integer 1, Integer 3]

      it "errors on too few arguments" $ do
        evaluate (eval "SWAP" []) `shouldThrow` errorCall "Stack underflow"
        evaluate (eval "SWAP" [Integer 1]) `shouldThrow` errorCall "Stack underflow"

    context "OVER" $ do
      it "copies second element to top" $ do
        eval "OVER" [Integer 1, Integer 2] `shouldBe` [Integer 2, Integer 1, Integer 2]

      it "errors on too few arguments" $ do
        evaluate (eval "OVER" []) `shouldThrow` errorCall "Stack underflow"

    context "ROT" $ do
      it "rotates top three elements" $ do
        eval "ROT" [Integer 1, Integer 2, Integer 3] `shouldBe` [Integer 3, Integer 1, Integer 2]

      it "errors on too few arguments" $ do
        evaluate (eval "ROT" []) `shouldThrow` errorCall "Stack underflow"

    context "STR" $ do
      it "converts integer to string" $ do
        eval "STR" [Integer 42] `shouldBe` [Id "42"]

      it "converts real to string" $ do
        eval "STR" [Real 3.14] `shouldBe` [Id "3.14"]

      it "preserves Id strings" $ do
        eval "STR" [Id "hello"] `shouldBe` [Id "hello"]

      it "errors on empty stack" $ do
        evaluate (eval "STR" []) `shouldThrow` errorCall "Stack underflow"

    context "CONCAT2" $ do
      it "concatenates two strings" $ do
        eval "CONCAT2" [Id "world", Id "hello"] `shouldBe` [Id "helloworld"]

      it "concatenates in correct order" $ do
        eval "CONCAT2" [Id "b", Id "a"] `shouldBe` [Id "ab"]

      it "preserves rest of stack" $ do
        eval "CONCAT2" [Id "b", Id "a", Integer 1] `shouldBe` [Id "ab", Integer 1]

      it "errors on non-string arguments" $ do
        evaluate (eval "CONCAT2" [Integer 1, Id "a"]) `shouldThrow` errorCall "CONCAT2 requires string arguments"

      it "errors on too few arguments" $ do
        evaluate (eval "CONCAT2" []) `shouldThrow` errorCall "Stack underflow"
        evaluate (eval "CONCAT2" [Id "x"]) `shouldThrow` errorCall "Stack underflow"

    context "CONCAT3" $ do
      it "concatenates three strings" $ do
        eval "CONCAT3" [Id "c", Id "b", Id "a"] `shouldBe` [Id "abc"]

      it "errors on non-string arguments" $ do
        evaluate (eval "CONCAT3" [Integer 1, Id "b", Id "a"]) `shouldThrow` errorCall "CONCAT3 requires string arguments"

      it "errors on too few arguments" $ do
        evaluate (eval "CONCAT3" []) `shouldThrow` errorCall "Stack underflow"

    context "=" $ do
      it "returns 1 for equal integers" $ do
        eval "=" [Integer 5, Integer 5] `shouldBe` [Integer 1]

      it "returns 0 for unequal integers" $ do
        eval "=" [Integer 3, Integer 5] `shouldBe` [Integer 0]

    context "<" $ do
      it "returns 1 when second < first" $ do
        eval "<" [Integer 5, Integer 3] `shouldBe` [Integer 1]

      it "returns 0 otherwise" $ do
        eval "<" [Integer 3, Integer 5] `shouldBe` [Integer 0]

    context ">" $ do
      it "returns 1 when second > first" $ do
        eval ">" [Integer 3, Integer 5] `shouldBe` [Integer 1]

      it "returns 0 otherwise" $ do
        eval ">" [Integer 5, Integer 3] `shouldBe` [Integer 0]

    context "MOD" $ do
      it "computes modulo" $ do
        eval "MOD" [Integer 3, Integer 10] `shouldBe` [Integer 1]

      it "errors on division by zero" $ do
        evaluate (eval "MOD" [Integer 0, Integer 5]) `shouldThrow` errorCall "Division by zero"

    context "NEGATE" $ do
      it "negates integer" $ do
        eval "NEGATE" [Integer 5] `shouldBe` [Integer (-5)]

      it "negates real" $ do
        eval "NEGATE" [Real 3.0] `shouldBe` [Real (-3.0)]

    context "ABS" $ do
      it "absolute value of negative integer" $ do
        eval "ABS" [Integer (-5)] `shouldBe` [Integer 5]

      it "absolute value of positive is unchanged" $ do
        eval "ABS" [Integer 7] `shouldBe` [Integer 7]

    context "MAX" $ do
      it "returns maximum of two integers" $ do
        eval "MAX" [Integer 3, Integer 7] `shouldBe` [Integer 7]

    context "MIN" $ do
      it "returns minimum of two integers" $ do
        eval "MIN" [Integer 3, Integer 7] `shouldBe` [Integer 3]

    context "unknown identifier" $ do
      it "pushes Id onto stack" $ do
        eval "hello" [] `shouldBe` [Id "hello"]
        eval "xyz" [Integer 1] `shouldBe` [Id "xyz", Integer 1]

  describe "evalOut" $ do
    context "." $ do
      it "prints top of stack" $ do
        evalOut "." ([Id "x"], "") `shouldBe` ([], "x")
        evalOut "." ([Integer 2], "") `shouldBe` ([], "2")
        evalOut "." ([Real 2.2], "") `shouldBe` ([], "2.2")

      it "appends to existing output" $ do
        evalOut "." ([Integer 5], "prev") `shouldBe` ([], "prev5")

      it "errors on empty stack" $ do
        evaluate (evalOut "." ([], "")) `shouldThrow` errorCall "Stack underflow"

    context "EMIT" $ do
      it "emits character for ASCII code" $ do
        evalOut "EMIT" ([Integer 65], "") `shouldBe` ([], "A")
        evalOut "EMIT" ([Integer 72], "") `shouldBe` ([], "H")

      it "emits space character" $ do
        evalOut "EMIT" ([Integer 32], "") `shouldBe` ([], " ")

      it "appends to existing output" $ do
        evalOut "EMIT" ([Integer 65], "pre") `shouldBe` ([], "preA")

      it "errors on empty stack" $ do
        evaluate (evalOut "EMIT" ([], "")) `shouldThrow` errorCall "Stack underflow"

    context "CR" $ do
      it "prints newline" $ do
        evalOut "CR" ([Integer 1], "") `shouldBe` ([Integer 1], "\n")

      it "appends newline to existing output" $ do
        evalOut "CR" ([], "hello") `shouldBe` ([], "hello\n")

      it "works with empty stack" $ do
        evalOut "CR" ([], "") `shouldBe` ([], "\n")

    it "eval pass-through" $ do
      evalOut "*" ([Real 2.0, Integer 2], "blah") `shouldBe` ([Real 4.0], "blah")
      evalOut "+" ([Integer 3, Integer 4], "x") `shouldBe` ([Integer 7], "x")
      evalOut "DUP" ([Integer 5], "") `shouldBe` ([Integer 5, Integer 5], "")
