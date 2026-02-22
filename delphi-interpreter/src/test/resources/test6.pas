program Test6;

type
  TShape = class
  private
    FColor: String;
  public
    constructor Create(Color: String);
    function GetColor: String;
    function Area: Integer;
    procedure Describe;
  end;

  TRectangle = class(TShape)
  private
    FWidth: Integer;
    FHeight: Integer;
  public
    constructor Create(Color: String; Width: Integer; Height: Integer);
    function Area: Integer;
    procedure Describe;
  end;

  TSquare = class(TRectangle)
  public
    constructor Create(Color: String; Side: Integer);
    procedure Describe;
  end;

var
  r: TRectangle;
  s: TSquare;

constructor TShape.Create(Color: String);
begin
  FColor := Color
end;

function TShape.GetColor: String;
begin
  GetColor := FColor
end;

function TShape.Area: Integer;
begin
  Area := 0
end;

procedure TShape.Describe;
begin
  WriteLn('Shape, color: ', FColor)
end;

constructor TRectangle.Create(Color: String; Width: Integer; Height: Integer);
begin
  FColor  := Color;
  FWidth  := Width;
  FHeight := Height
end;

function TRectangle.Area: Integer;
begin
  Area := FWidth * FHeight
end;

procedure TRectangle.Describe;
begin
  WriteLn('Rectangle ', FWidth, 'x', FHeight, ', color: ', FColor, ', area: ', FWidth * FHeight)
end;

constructor TSquare.Create(Color: String; Side: Integer);
begin
  FColor  := Color;
  FWidth  := Side;
  FHeight := Side
end;

procedure TSquare.Describe;
begin
  WriteLn('Square side=', FWidth, ', color: ', FColor, ', area: ', FWidth * FHeight)
end;

begin
  r := TRectangle.Create('Red', 4, 6);
  s := TSquare.Create('Blue', 5);

  r.Describe;
  WriteLn('Rectangle color: ', r.GetColor);
  WriteLn('Rectangle area: ', r.Area);

  s.Describe;
  WriteLn('Square color: ', s.GetColor);
  WriteLn('Square area: ', s.Area)
end.
