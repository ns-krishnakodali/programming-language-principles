:- dynamic(gvar/2).
:- discontiguous(typeStatement/2).
:- discontiguous(bType/1).

% Expression inference

typeExp(Name, T) :-
    atom(Name),
    gvar(Name, T),
    !.

typeExp(mkTuple(Elems), tuple(Types)) :-
    is_list(Elems), !,
    typeExpList(Elems, Types).

typeExp(inl(E), sum(TLeft, _TRight)) :-
    !, typeExp(E, TLeft).

typeExp(inr(E), sum(_TLeft, TRight)) :-
    !, typeExp(E, TRight).

typeExp(Fct, T) :-
    \+ var(Fct), \+ atom(Fct),
    functor(Fct, Fname, _),
    !,
    Fct =.. [Fname|Args],
    append(Args, [T], CallSig),
    functionType(Fname, DefSig),
    typeExpList(CallSig, DefSig).

typeExp(T, T).

typeExpList([], []).
typeExpList([H1|T1], [H2|T2]) :-
    typeExp(H1, H2),
    typeExpList(T1, T2).

% Statements

typeStatement(gvLet(Name, T, Expr), unit) :-
    atom(Name),
    typeExp(Expr, T),
    bType(T), !,
    asserta(gvar(Name, T)).

typeStatement(gfLet(Name, RetType, Params, Body), unit) :-
    atom(Name),
    is_list(Params), is_list(Body),
    typeCode(Body, RetType),
    append(Params, [RetType], FSig),
    asserta(gvar(Name, FSig)).

typeStatement(expr(E), T)   :- typeExp(E, T).
typeStatement(return(E), T) :- typeExp(E, T).

typeStatement(if(Cond, ThenBlk, ElseBlk), T) :-
    is_list(ThenBlk), is_list(ElseBlk),
    typeExp(Cond, bool),
    typeCode(ThenBlk, T),
    typeCode(ElseBlk, T).

typeStatement(letIn(Name, T, Expr, Body), BodyT) :-
    atom(Name),
    is_list(Body),
    typeExp(Expr, T),
    asserta(gvar(Name, T)),
    typeCode(Body, BodyT),
    retract(gvar(Name, T)).

typeStatement(for(LoopVar, Start, End, Body), unit) :-
    is_list(Body),
    typeExp(Start, int),
    typeExp(End, int),
    LoopVar = int,
    typeCode(Body, _).

typeStatement(block(Stmts), T) :-
    is_list(Stmts),
    typeCode(Stmts, T).

typeStatement(gvLetTuple(Names, Types, Expr), unit) :-
    is_list(Names), is_list(Types),
    length(Names, N), length(Types, N),
    typeExp(Expr, tuple(Types)),
    assertTupleVars(Names, Types).

assertTupleVars([], []).
assertTupleVars([N|Ns], [T|Ts]) :-
    atom(N),
    asserta(gvar(N, T)),
    assertTupleVars(Ns, Ts).

typeStatement(match(Expr, Cases), T) :-
    is_list(Cases), Cases \= [],
    typeExp(Expr, ScrutType),
    matchCases(Cases, ScrutType, T).

matchCases([], _, _).
matchCases([case(Pat, Body)|Rest], ScrutType, T) :-
    is_list(Body),
    typeExp(Pat, ScrutType),
    typeCode(Body, T),
    matchCases(Rest, ScrutType, T).

% Code blocks: type is the type of the last statement

typeCode([S], T) :- typeStatement(S, T).
typeCode([S1, S2|Rest], T) :-
    typeStatement(S1, _),
    typeCode([S2|Rest], T).

% Top level (Entry point)

infer(Code, T) :-
    is_list(Code),
    deleteGVars(),
    typeCode(Code, T).

deleteGVars() :- retractall(gvar(_, _)).

% Basic types

bType(int).
bType(float).
bType(string).
bType(bool).
bType(unit).
bType([H])    :- bType(H).
bType([H|T])  :- bType(H), bType(T).
bType(tuple(L))    :- is_list(L), allBType(L).
bType(sum(T1, T2)) :- bType(T1), bType(T2).

allBType([]).
allBType([H|T]) :- bType(H), allBType(T).

% Built-in function signatures (last element is return type)

fType(iplus,  [int, int, int]).
fType(iminus, [int, int, int]).
fType(imul,   [int, int, int]).
fType(idiv,   [int, int, int]).
fType(imod,   [int, int, int]).
fType(ineg,   [int, int]).

fType(fplus,  [float, float, float]).
fType(fminus, [float, float, float]).
fType(fmul,   [float, float, float]).
fType(fdiv,   [float, float, float]).
fType(fneg,   [float, float]).

fType(fToInt,   [float, int]).
fType(iToFloat, [int, float]).

fType(iless,    [int, int, bool]).
fType(igreater, [int, int, bool]).
fType(ieq,      [int, int, bool]).
fType(ineq,     [int, int, bool]).
fType(ileq,     [int, int, bool]).
fType(igeq,     [int, int, bool]).

fType(fless,    [float, float, bool]).
fType(fgreater, [float, float, bool]).
fType(feq,      [float, float, bool]).

fType(sconcat, [string, string, string]).
fType(slength, [string, int]).
fType(sToInt,  [string, int]).
fType(iToStr,  [int, string]).

fType(and, [bool, bool, bool]).
fType(or,  [bool, bool, bool]).
fType(not, [bool, bool]).

fType(print,   [_, unit]).
fType(println, [_, unit]).
fType(readInt, [int]).
fType(readStr, [string]).

% Function lookup: user-defined first, then built-ins

functionType(Name, Sig) :-
    gvar(Name, Sig),
    is_list(Sig).

functionType(Name, Sig) :-
    fType(Name, Sig), !.
