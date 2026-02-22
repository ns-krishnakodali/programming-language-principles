program Test7;

type
  IPrintable = interface
    procedure Print;
    function GetDescription: String;
  end;

  TBook = class
  private
    FTitle: String;
    FPages: Integer;
  public
    constructor Create(Title: String; Pages: Integer);
    procedure Print;
    function GetDescription: String;
    function GetPages: Integer;
  end;

  TMovie = class
  private
    FTitle: String;
    FDuration: Integer;
  public
    constructor Create(Title: String; Duration: Integer);
    procedure Print;
    function GetDescription: String;
    function GetDuration: Integer;
  end;

var
  b: TBook;
  m: TMovie;

constructor TBook.Create(Title: String; Pages: Integer);
begin
  FTitle := Title;
  FPages := Pages
end;

procedure TBook.Print;
begin
  WriteLn('Book: ', FTitle, ' (', FPages, ' pages)')
end;

function TBook.GetDescription: String;
begin
  GetDescription := 'Book titled ' + FTitle
end;

function TBook.GetPages: Integer;
begin
  GetPages := FPages
end;

constructor TMovie.Create(Title: String; Duration: Integer);
begin
  FTitle    := Title;
  FDuration := Duration
end;

procedure TMovie.Print;
begin
  WriteLn('Movie: ', FTitle, ' (', FDuration, ' mins)')
end;

function TMovie.GetDescription: String;
begin
  GetDescription := 'Movie titled ' + FTitle
end;

function TMovie.GetDuration: Integer;
begin
  GetDuration := FDuration
end;

begin
  b := TBook.Create('The Pragmatic Programmer', 352);
  m := TMovie.Create('Inception', 148);

  b.Print;
  WriteLn(b.GetDescription);
  WriteLn('Pages: ', b.GetPages);

  m.Print;
  WriteLn(m.GetDescription);
  WriteLn('Duration: ', m.GetDuration, ' mins')
end.
