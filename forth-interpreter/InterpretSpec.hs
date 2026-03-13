import Test.Hspec
import Test.QuickCheck
import Control.Exception (evaluate)
import Val
import Eval
import Interpret
import qualified Data.Map as Map

main :: IO ()
main = hspec $ do
  describe "evalF" $ do
    it "preserves output for numbers" $ do
        evalF Map.empty ([], "x") (Real 3.0) `shouldBe` ([Real 3.0], "x")

    it "passes through operators" $ do
        evalF Map.empty ([Real 2.2, Integer 2], "") (Id "*") `shouldBe` ([Real 4.4], "")

    it "propagates output" $ do
        evalF Map.empty ([Integer 2], "") (Id ".") `shouldBe` ([],"2")

    it "pushes integers onto stack" $ do
        evalF Map.empty ([], "") (Integer 5) `shouldBe` ([Integer 5], "")

    it "expands user-defined words" $ do
        let dict = Map.fromList [("DOUBLE", [Id "DUP", Id "+"])]
        evalF dict ([Integer 3], "") (Id "DOUBLE") `shouldBe` ([Integer 6], "")

  describe "extractDefs" $ do
    it "extracts a simple definition" $ do
        let tokens = map strToVal (words ": SQUARE DUP * ; 5 SQUARE .")
            (dict, remaining) = extractDefs tokens
        Map.lookup "SQUARE" dict `shouldBe` Just [Id "DUP", Id "*"]
        remaining `shouldBe` [Integer 5, Id "SQUARE", Id "."]

    it "returns empty dict when no definitions" $ do
        let tokens = map strToVal (words "2 3 +")
            (dict, remaining) = extractDefs tokens
        dict `shouldBe` Map.empty
        remaining `shouldBe` [Integer 2, Integer 3, Id "+"]

    it "handles multiple definitions" $ do
        let tokens = map strToVal (words ": A DUP ; : B SWAP ; 1 2")
            (dict, _) = extractDefs tokens
        Map.lookup "A" dict `shouldBe` Just [Id "DUP"]
        Map.lookup "B" dict `shouldBe` Just [Id "SWAP"]

  describe "interpret" $ do
    context "RPN arithmetic" $ do
        it "adds two integers" $ do
            interpret "2 3 +" `shouldBe` ([Integer 5], "")

        it "subtracts two integers" $ do
            interpret "10 3 -" `shouldBe` ([Integer 7], "")

        it "multiplies two integers" $ do
            interpret "2 3 *" `shouldBe` ([Integer 6], "")

        it "divides two integers" $ do
            interpret "12 4 /" `shouldBe` ([Integer 3], "")

        it "computes power" $ do
            interpret "2 10 ^" `shouldBe` ([Integer 1024], "")

        it "multiplies floats and integers" $ do
            interpret "2 2.2 3.4 * *" `shouldBe` ([Real 14.960001], "")

        it "chains operations" $ do
            interpret "10 2 + 3 *" `shouldBe` ([Integer 36], "")

        it "computes complex expression" $ do
            interpret "5 3 + 2 *" `shouldBe` ([Integer 16], "")

    context "stack operations" $ do
        it "DUP and multiply (square)" $ do
            interpret "5 DUP *" `shouldBe` ([Integer 25], "")

        it "SWAP changes order" $ do
            interpret "1 2 SWAP" `shouldBe` ([Integer 1, Integer 2], "")

        it "DROP removes top" $ do
            interpret "1 2 DROP" `shouldBe` ([Integer 1], "")

        it "OVER copies second" $ do
            interpret "1 2 OVER" `shouldBe` ([Integer 1, Integer 2, Integer 1], "")

    context "output" $ do
        it "prints integer" $ do
            interpret "2 6 * ." `shouldBe` ([], "12")

        it "prints multiple values" $ do
            interpret "3 . 4 ." `shouldBe` ([], "34")

        it "EMIT prints character" $ do
            interpret "65 EMIT" `shouldBe` ([], "A")

        it "CR prints newline" $ do
            interpret "42 . CR 7 ." `shouldBe` ([], "42\n7")

    context "string operations" $ do
        it "STR converts integer" $ do
            interpret "42 STR" `shouldBe` ([Id "42"], "")

        it "CONCAT2 joins two strings" $ do
            interpret "3 STR 4 STR CONCAT2 ." `shouldBe` ([], "34")

        it "CONCAT3 joins three strings" $ do
            interpret "1 STR 2 STR 3 STR CONCAT3 ." `shouldBe` ([], "123")

    context "user-defined functions (bonus)" $ do
        it "defines and uses SQUARE" $ do
            interpret ": SQUARE DUP * ; 5 SQUARE ." `shouldBe` ([], "25")

        it "defines and uses CUBE" $ do
            interpret ": CUBE DUP DUP * * ; 3 CUBE ." `shouldBe` ([], "27")

        it "uses multiple user-defined functions" $ do
            interpret ": SQUARE DUP * ; : CUBE DUP DUP * * ; 4 SQUARE . CR 3 CUBE ." `shouldBe` ([], "16\n27")

        it "user function can call another user function" $ do
            interpret ": SQUARE DUP * ; : QUAD SQUARE SQUARE ; 2 QUAD ." `shouldBe` ([], "16")

        it "user function with output" $ do
            interpret ": SHOW-SQUARE DUP * . ; 6 SHOW-SQUARE" `shouldBe` ([], "36")

    context "comparison and logic" $ do
        it "equality check" $ do
            interpret "5 5 =" `shouldBe` ([Integer 1], "")
            interpret "5 3 =" `shouldBe` ([Integer 0], "")

        it "less than" $ do
            interpret "3 5 <" `shouldBe` ([Integer 1], "")

        it "greater than" $ do
            interpret "5 3 >" `shouldBe` ([Integer 1], "")

    context "edge cases" $ do
        it "empty program" $ do
            interpret "" `shouldBe` ([], "")

        it "only spaces" $ do
            interpret "   " `shouldBe` ([], "")

        it "leaves values on stack" $ do
            interpret "1 2 3" `shouldBe` ([Integer 3, Integer 2, Integer 1], "")
