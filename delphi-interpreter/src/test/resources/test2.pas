program Test2;

var
  i: Integer;
  total: Integer;

begin
  total := 0;

  for i := 1 to 5 do
  begin
    total := total + i;
    WriteLn('i = ', i, ', running total = ', total)
  end;

  WriteLn('Sum 1..5 = ', total);

  i := 1;
  while i <= 3 do
  begin
    WriteLn('while: ', i);
    i := i + 1
  end;

  i := 0;
  repeat
    i := i + 1;
    WriteLn('repeat: ', i)
  until i = 3
end.
