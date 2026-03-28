program Test15;

var
  r: Integer;

function Max(a: Integer; b: Integer): Integer;
begin
  if a > b then
    Max := a
  else
    Max := b
end;

function Min(a: Integer; b: Integer): Integer;
begin
  if a < b then
    Min := a
  else
    Min := b
end;

function Clamp(val: Integer; lo: Integer; hi: Integer): Integer;
begin
  Clamp := Max(lo, Min(val, hi))
end;

function Power(base: Integer; exp: Integer): Integer;
var
  result: Integer;
  i: Integer;
begin
  result := 1;
  for i := 1 to exp do
    result := result * base;
  Power := result
end;

function SumRange(a: Integer; b: Integer): Integer;
var
  i: Integer;
  s: Integer;
begin
  s := 0;
  for i := a to b do
    s := s + i;
  SumRange := s
end;

procedure Swap(a: Integer; b: Integer);
var
  temp: Integer;
begin
  temp := a;
  a := b;
  b := temp;
  WriteLn('Inside Swap: a = ', a, ', b = ', b)
end;

function IsEven(n: Integer): Boolean;
begin
  IsEven := (n mod 2 = 0)
end;

begin
  WriteLn('Max(3, 7) = ', Max(3, 7));
  WriteLn('Max(10, 2) = ', Max(10, 2));
  WriteLn('Min(3, 7) = ', Min(3, 7));

  WriteLn('Clamp(-5, 0, 100) = ', Clamp(-5, 0, 100));
  WriteLn('Clamp(50, 0, 100) = ', Clamp(50, 0, 100));
  WriteLn('Clamp(200, 0, 100) = ', Clamp(200, 0, 100));

  WriteLn('Power(2, 10) = ', Power(2, 10));
  WriteLn('Power(3, 4) = ', Power(3, 4));

  WriteLn('SumRange(1, 100) = ', SumRange(1, 100));

  r := 5;
  Swap(r, 10);
  WriteLn('After Swap, r = ', r);

  if IsEven(4) then
    WriteLn('4 is even')
  else
    WriteLn('4 is odd');
  if IsEven(7) then
    WriteLn('7 is even')
  else
    WriteLn('7 is odd')
end.
