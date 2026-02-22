program Test4;

type
  TBankAccount = class
  private
    FOwner: String;
    FBalance: Integer;
  public
    constructor Create(Owner: String; InitBalance: Integer);
    procedure Deposit(Amount: Integer);
    procedure Withdraw(Amount: Integer);
    function GetBalance: Integer;
    function GetOwner: String;
  end;

var
  acc: TBankAccount;
  amount: Integer;

constructor TBankAccount.Create(Owner: String; InitBalance: Integer);
begin
  FOwner   := Owner;
  FBalance := InitBalance
end;

procedure TBankAccount.Deposit(Amount: Integer);
begin
  FBalance := FBalance + Amount;
  WriteLn('Deposited: ', Amount)
end;

procedure TBankAccount.Withdraw(Amount: Integer);
begin
  if Amount > FBalance then
    WriteLn('Insufficient funds')
  else
  begin
    FBalance := FBalance - Amount;
    WriteLn('Withdrawn: ', Amount)
  end
end;

function TBankAccount.GetBalance: Integer;
begin
  GetBalance := FBalance
end;

function TBankAccount.GetOwner: String;
begin
  GetOwner := FOwner
end;

begin
  acc := TBankAccount.Create('Alice', 1000);
  WriteLn('Owner: ', acc.GetOwner);
  WriteLn('Balance: ', acc.GetBalance);
  WriteLn('Enter deposit amount:');
  ReadLn(amount);
  acc.Deposit(amount);
  acc.Withdraw(300);
  acc.Withdraw(5000);
  WriteLn('Final balance: ', acc.GetBalance)
end.
