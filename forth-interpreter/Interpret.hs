module Interpret where

import Data.Map (Map)
import qualified Data.Map as Map
import Eval
import Flow
import Val

type Dict = Map String [Val]

extractDefs :: [Val] -> (Dict, [Val])
extractDefs = go Map.empty []
  where
    go dict acc [] = (dict, reverse acc)
    go dict acc (Id ":" : Id name : rest) =
      let (body, remaining) = collectBody [] rest
       in go (Map.insert name body dict) acc remaining
    go dict acc (t : rest) = go dict (t : acc) rest

    collectBody acc (Id ";" : rest) = (reverse acc, rest)
    collectBody acc (t : rest) = collectBody (t : acc) rest
    collectBody _ [] = error "Unterminated function definition"

evalF :: Dict -> ([Val], String) -> Val -> ([Val], String)
evalF dict s (Id op) = case Map.lookup op dict of
  Just body -> foldl (evalF dict) s body
  Nothing -> evalOut op s
evalF _ (s, out) x = (x : s, out)

interpret :: String -> ([Val], String)
interpret text =
  let tokens = text |> words |> map strToVal
      (dict, mainTokens) = extractDefs tokens
   in foldl (evalF dict) ([], "") mainTokens
