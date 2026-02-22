program Test5;

type
  TAnimal = class
  private
    FName: String;
  public
    constructor Create(Name: String);
    function GetName: String;
    procedure Speak;
  end;

  TDog = class(TAnimal)
  private
    FBreed: String;
  public
    constructor Create(Name: String; Breed: String);
    function GetBreed: String;
    procedure Speak;
  end;

  TCat = class(TAnimal)
  public
    constructor Create(Name: String);
    procedure Speak;
  end;

var
  d: TDog;
  c: TCat;

constructor TAnimal.Create(Name: String);
begin
  FName := Name
end;

function TAnimal.GetName: String;
begin
  GetName := FName
end;

procedure TAnimal.Speak;
begin
  WriteLn(FName, ' makes a sound')
end;

constructor TDog.Create(Name: String; Breed: String);
begin
  FName  := Name;
  FBreed := Breed
end;

function TDog.GetBreed: String;
begin
  GetBreed := FBreed
end;

procedure TDog.Speak;
begin
  WriteLn(FName, ' barks! Breed: ', FBreed)
end;

constructor TCat.Create(Name: String);
begin
  FName := Name
end;

procedure TCat.Speak;
begin
  WriteLn(FName, ' meows')
end;

begin
  d := TDog.Create('Rex', 'Labrador');
  c := TCat.Create('Whiskers');

  WriteLn('Dog name: ', d.GetName);
  WriteLn('Dog breed: ', d.GetBreed);
  d.Speak;

  WriteLn('Cat name: ', c.GetName);
  c.Speak
end.
