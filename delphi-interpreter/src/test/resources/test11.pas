program Test11;

var
  i: Integer;
  total: Integer;

begin
  { break in while loop - stop at 5 }
  i := 1;
  while i <= 10 do
  begin
    if i = 5 then
      break;
    WriteLn('while i = ', i);
    i := i + 1
  end;
  WriteLn('Exited while at i = ', i);

  { break in for loop - stop at 3 }
  for i := 1 to 10 do
  begin
    if i = 3 then
      break;
    WriteLn('for i = ', i)
  end;
  WriteLn('Exited for at i = ', i);

  { break in downto for loop }
  for i := 10 downto 1 do
  begin
    if i = 7 then
      break;
    WriteLn('downto i = ', i)
  end;
  WriteLn('Exited downto at i = ', i);

  { break in nested loop - only inner breaks }
  total := 0;
  for i := 1 to 3 do
  begin
    WriteLn('outer i = ', i);
    total := 0;
    while total < 100 do
    begin
      total := total + 10;
      if total = 30 then
        break
    end;
    WriteLn('  inner total = ', total)
  end
end.
