program Test12;

var
  i: Integer;
  total: Integer;

begin
  { continue in for loop - skip even numbers }
  WriteLn('Odd numbers 1..10:');
  for i := 1 to 10 do
  begin
    if i mod 2 = 0 then
      continue;
    Write(i, ' ')
  end;
  WriteLn('');

  { continue in while loop - skip multiples of 3 }
  WriteLn('Non-multiples of 3, 1..12:');
  i := 0;
  while i < 12 do
  begin
    i := i + 1;
    if i mod 3 = 0 then
      continue;
    Write(i, ' ')
  end;
  WriteLn('');

  { continue in repeat loop - skip when i = 3 }
  WriteLn('Repeat skip 3:');
  i := 0;
  repeat
    i := i + 1;
    if i = 3 then
      continue;
    Write(i, ' ')
  until i = 5;
  WriteLn('');

  { continue + break together in for loop }
  WriteLn('Skip even, break at 7:');
  for i := 1 to 10 do
  begin
    if i = 7 then
      break;
    if i mod 2 = 0 then
      continue;
    Write(i, ' ')
  end;
  WriteLn('')
end.
