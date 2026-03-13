module Main where

-- Running: runaskell Main.hs path_to_test_file

import Interpret
import System.Environment

main :: IO ()
main = do
    (fileName:tl) <- getArgs
    contents <- readFile fileName
    let (stack, output) = interpret contents 
    putStrLn output
