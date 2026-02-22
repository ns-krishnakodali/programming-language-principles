program Test1;

var
  x: Integer;
  y: Integer;
  sum: Integer;

begin
  x := 10;
  y := 20;
  sum := x + y;
  WriteLn('Sum: ', sum);
  WriteLn('Product: ', x * y);
  if sum > 25 then
    WriteLn('Sum is greater than 25')
  else
    WriteLn('Sum is not greater than 25')
end.
