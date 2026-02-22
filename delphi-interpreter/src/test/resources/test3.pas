program Test3;

type
  TCounter = class
  private
    FValue: Integer;
  public
    constructor Create(InitVal: Integer);
    procedure Increment;
    procedure Decrement;
    function GetValue: Integer;
    destructor Destroy;
  end;

var
  c: TCounter;

constructor TCounter.Create(InitVal: Integer);
begin
  FValue := InitVal
end;

procedure TCounter.Increment;
begin
  FValue := FValue + 1
end;

procedure TCounter.Decrement;
begin
  FValue := FValue - 1
end;

function TCounter.GetValue: Integer;
begin
  GetValue := FValue
end;

destructor TCounter.Destroy;
begin
  WriteLn('TCounter destroyed')
end;

begin
  c := TCounter.Create(5);
  WriteLn('Initial: ', c.GetValue);
  c.Increment;
  c.Increment;
  WriteLn('After 2 increments: ', c.GetValue);
  c.Decrement;
  WriteLn('After 1 decrement: ', c.GetValue);
  c.Destroy
end.
