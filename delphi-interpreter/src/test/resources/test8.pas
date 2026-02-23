program Test8;

var
  result: Integer;

function Square(n: Integer): Integer;
begin
  Square := n * n
end;

function Add(a: Integer; b: Integer): Integer;
begin
  Add := a + b
end;

procedure PrintResult(label_: String; value: Integer);
begin
  WriteLn(label_, value)
end;

begin
  result := Square(4);
  PrintResult('Square of 4: ', result);

  result := Add(7, 3);
  PrintResult('7 + 3 = ', result);

  result := Add(Square(3), Square(4));
  PrintResult('3^2 + 4^2 = ', result)
end.
