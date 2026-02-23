program Test9;

type
  TPerson = class
  private
    FName: String;
    FAge: Integer;
  public
    constructor Create(Name: String; Age: Integer);
    procedure Greet;
    function GetName: String;
    function GetAge: Integer;
  end;

var
  p: TPerson;

constructor TPerson.Create(Name: String; Age: Integer);
begin
  FName := Name;
  FAge  := Age
end;

procedure TPerson.Greet;
begin
  WriteLn('Hello, my name is ', FName, ' and I am ', FAge, ' years old.')
end;

function TPerson.GetName: String;
begin
  GetName := FName
end;

function TPerson.GetAge: Integer;
begin
  GetAge := FAge
end;

begin
  p := TPerson.Create('Alice', 30);
  p.Greet;
  WriteLn('Name: ', p.GetName);
  WriteLn('Age: ', p.GetAge)
end.
