module Val where

import Data.Maybe (isJust)
import Text.Read (readMaybe)

data Val
  = Integer Int
  | Real Float
  | Id String
  deriving (Show, Eq)

-- converts string to Val
strToVal :: String -> Val
strToVal s = case readMaybe s :: Maybe Int of
  Just i -> Integer i
  Nothing -> case readMaybe s :: Maybe Float of
    Just f -> Real f
    Nothing -> Id s

-- converts to Float if Real or Integer, error otherwise
toFloat :: Val -> Float
toFloat (Real x) = x
toFloat (Integer i) = fromIntegral i
toFloat (Id _) = error "Not convertible to float"

valToStr :: Val -> Val
valToStr (Integer i) = Id (show i)
valToStr (Real x) = Id (show x)
valToStr (Id s) = Id s
