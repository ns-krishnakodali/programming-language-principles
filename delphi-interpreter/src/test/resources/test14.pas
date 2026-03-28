program Test14;

var
  counter: Integer;

procedure Outer;
var
  outerVal: Integer;

  procedure Inner;
  var
    innerVal: Integer;
  begin
    innerVal := 10;
    counter := counter + 1;
    WriteLn('  Inner: innerVal = ', innerVal, ', counter = ', counter)
  end;

begin
  outerVal := 5;
  WriteLn('Outer: outerVal = ', outerVal);
  Inner;
  Inner;
  WriteLn('Outer: counter after Inner calls = ', counter)
end;

function Factorial(n: Integer): Integer;
begin
  if n <= 1 then
    Factorial := 1
  else
    Factorial := n * Factorial(n - 1)
end;

function Fibonacci(n: Integer): Integer;
begin
  if n <= 0 then
    Fibonacci := 0
  else if n = 1 then
    Fibonacci := 1
  else
    Fibonacci := Fibonacci(n - 1) + Fibonacci(n - 2)
end;

procedure PrintN(n: Integer; msg: String);
var
  i: Integer;
begin
  for i := 1 to n do
    WriteLn('  ', i, ': ', msg)
end;

begin
  counter := 0;

  Outer;
  WriteLn('counter = ', counter);

  WriteLn('5! = ', Factorial(5));
  WriteLn('10! = ', Factorial(10));

  WriteLn('Fib(0) = ', Fibonacci(0));
  WriteLn('Fib(1) = ', Fibonacci(1));
  WriteLn('Fib(7) = ', Fibonacci(7));
  WriteLn('Fib(10) = ', Fibonacci(10));

  PrintN(3, 'hello')
end.
