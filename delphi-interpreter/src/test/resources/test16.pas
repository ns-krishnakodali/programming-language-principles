program Test16;

var
  i: Integer;
  j: Integer;
  found: Boolean;

begin
  { break out of repeat-until early }
  WriteLn('Repeat with break at 4:');
  i := 0;
  repeat
    i := i + 1;
    if i = 4 then
      break;
    Write(i, ' ')
  until i = 10;
  WriteLn('');
  WriteLn('Stopped at i = ', i);

  { nested for loops - break only inner }
  WriteLn('Nested loops, inner breaks at j=3:');
  for i := 1 to 4 do
  begin
    Write('i=', i, ': ');
    for j := 1 to 5 do
    begin
      if j = 3 then
        break;
      Write(j, ' ')
    end;
    WriteLn('')
  end;

  { nested while with continue in outer, break in inner }
  WriteLn('Outer continue + inner break:');
  i := 0;
  while i < 6 do
  begin
    i := i + 1;
    if i = 3 then
      continue;
    j := 0;
    while j < 10 do
    begin
      j := j + 1;
      if j > 2 then
        break
    end;
    WriteLn('i=', i, ', inner stopped at j=', j)
  end;

  { search pattern: break when found }
  found := false;
  for i := 1 to 100 do
  begin
    if i * i > 50 then
    begin
      found := true;
      break
    end
  end;
  if found then
    WriteLn('First i where i*i > 50: ', i)
  else
    WriteLn('Not found')
end.
