program Test13;

var
  g: Integer;

procedure ShowGlobal;
begin
  WriteLn('Global g = ', g)
end;

procedure ModifyGlobal;
begin
  g := g + 100;
  WriteLn('Inside ModifyGlobal, g = ', g)
end;

function ReadGlobal: Integer;
begin
  ReadGlobal := g
end;

procedure CallerWithLocal;
var
  localVar: Integer;
begin
  localVar := 999;
  g := 50;
  WriteLn('Before call, g = ', g);
  ModifyGlobal;
  WriteLn('After ModifyGlobal, g = ', g);
  WriteLn('localVar unchanged = ', localVar)
end;

procedure TestScopeChain;
var
  x: Integer;
begin
  x := 10;
  g := 200;
  WriteLn('TestScopeChain: x = ', x, ', g = ', g);
  ShowGlobal
end;

begin
  g := 0;
  WriteLn('Initial g = ', g);

  ShowGlobal;

  g := 42;
  WriteLn('g via function = ', ReadGlobal);

  CallerWithLocal;
  WriteLn('After CallerWithLocal, g = ', g);

  TestScopeChain;
  WriteLn('After TestScopeChain, g = ', g)
end.
