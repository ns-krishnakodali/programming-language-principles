import Control.Exception (evaluate)
import Test.Hspec
import Test.QuickCheck
import Val

main :: IO ()
main = hspec $ do
  describe "strToVal" $ do
    it "converts a positive integer" $ do
      strToVal "2" `shouldBe` Integer 2

    it "converts a negative integer" $ do
      strToVal "-5" `shouldBe` Integer (-5)

    it "converts zero" $ do
      strToVal "0" `shouldBe` Integer 0

    it "converts a large integer" $ do
      strToVal "1000000" `shouldBe` Integer 1000000

    it "converts a float" $ do
      strToVal "2.0" `shouldBe` Real 2.0

    it "converts a negative float" $ do
      strToVal "-3.14" `shouldBe` Real (-3.14)

    it "converts a string" $ do
      strToVal "x2" `shouldBe` Id "x2"

    it "converts an operator" $ do
      strToVal "+" `shouldBe` Id "+"
      strToVal "*" `shouldBe` Id "*"

    it "converts words to Id" $ do
      strToVal "hello" `shouldBe` Id "hello"
      strToVal "DUP" `shouldBe` Id "DUP"

  describe "toFloat" $ do
    it "preserves real" $ do
      toFloat (Real 2.0) `shouldBe` (2.0 :: Float)

    it "converts integers" $ do
      toFloat (Integer 2) `shouldBe` (2.0 :: Float)

    it "converts negative integers" $ do
      toFloat (Integer (-3)) `shouldBe` (-3.0 :: Float)

    it "converts zero" $ do
      toFloat (Integer 0) `shouldBe` (0.0 :: Float)

    it "errors on non-numbers" $ do
      evaluate (toFloat (Id "x")) `shouldThrow` errorCall "Not convertible to float"

  describe "valToStr" $ do
    it "converts integer to string" $ do
      valToStr (Integer 42) `shouldBe` Id "42"

    it "converts negative integer to string" $ do
      valToStr (Integer (-7)) `shouldBe` Id "-7"

    it "converts real to string" $ do
      valToStr (Real 3.14) `shouldBe` Id "3.14"

    it "preserves Id strings" $ do
      valToStr (Id "hello") `shouldBe` Id "hello"

    it "converts zero" $ do
      valToStr (Integer 0) `shouldBe` Id "0"
