module Main where

import Interpret
import Val
import System.Environment

main :: IO ()
main = do
    (fileName:_) <- getArgs
    contents <- readFile fileName
    let (stack, output) = interpret contents
    putStr output
    case stack of
        [] -> return ()
        _  -> putStrLn ("Stack not empty: " ++ show stack)
