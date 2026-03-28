program Test17;

const
  A = 10;
  B = 11;
  C = 2;
  Product = 6;

var
  v: Integer;
  w: Integer;
  x: Integer;

begin
  { constant expression: 2*(10+11) should fold to 42 }
  v := C * (A + B);
  WriteLn('v = C*(A+B) = ', v);

  { constant expression: uses named constants }
  w := A + B + C;
  WriteLn('w = A+B+C = ', w);

  { mixed: variable + constant part folds partially }
  x := 5;
  x := x + C * Product;
  WriteLn('x = 5 + C*Product = ', x);

  { pure constant arithmetic }
  v := 100 div 4;
  WriteLn('100 div 4 = ', v);

  v := 100 mod 7;
  WriteLn('100 mod 7 = ', v);

  v := 3 * 3 + 4 * 4;
  WriteLn('3*3 + 4*4 = ', v);

  { constant boolean expressions }
  if A > B then
    WriteLn('A > B')
  else
    WriteLn('A <= B');

  if A + B = 21 then
    WriteLn('A + B = 21 is true');

  { constants in loops }
  for v := 1 to A - B + 5 do
    Write(v, ' ');
  WriteLn('')
end.
