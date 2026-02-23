program Test10;

const
  Max = 3;

var
  i: Integer;
  grade: Integer;

begin
  for i := 1 to Max do
  begin
    case i of
      1: WriteLn('one');
      2: WriteLn('two');
      3: WriteLn('three')
    end
  end;

  grade := 2;
  case grade of
    1: WriteLn('Grade: Poor');
    2: WriteLn('Grade: Average');
    3: WriteLn('Grade: Good');
  else
    WriteLn('Grade: Unknown')
  end
end.
