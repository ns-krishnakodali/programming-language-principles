:- begin_tests(typeInf).
:- include(typeInf).

% typeExp tests

test(typeExp_iplus) :-
    typeExp(iplus(int, int), int).

test(typeExp_iplus_F, [fail]) :-
    typeExp(iplus(int, int), float).

test(typeExp_iplus_T, [true(T == int)]) :-
    typeExp(iplus(int, int), T).

test(typeExp_fplus, [true(T == float)]) :-
    typeExp(fplus(float, float), T).

test(typeExp_iToFloat, [true(T == float)]) :-
    typeExp(iToFloat(int), T).

test(typeExp_iless, [true(T == bool)]) :-
    typeExp(iless(int, int), T).

test(typeExp_sconcat, [true(T == string)]) :-
    typeExp(sconcat(string, string), T).

test(typeExp_and, [true(T == bool)]) :-
    typeExp(and(bool, bool), T).

test(typeStatement_gvar, [nondet, true(T == int)]) :-
    deleteGVars(),
    typeStatement(gvLet(v, T, iplus(X, Y)), unit),
    assertion(X == int), assertion(Y == int),
    gvar(v, int).

test(infer_gvar, [nondet]) :-
    infer([gvLet(v, T, iplus(X, Y))], unit),
    assertion(T == int), assertion(X == int), assertion(Y == int),
    gvar(v, int).

test(mockedFct, [nondet]) :-
    deleteGVars(),
    asserta(gvar(my_fct, [int, float])),
    typeExp(my_fct(X), T),
    assertion(X == int), assertion(T == float).

% infer tests: core statement coverage

test(infer_01_gvLet_int, [nondet, true(T == unit)]) :-
    infer([gvLet(x, _, int)], T),
    gvar(x, int).

test(infer_02_gvLet_float, [nondet]) :-
    infer([gvLet(pi, _, float)], unit),
    gvar(pi, float).

test(infer_03_gvLet_expr, [nondet]) :-
    infer([gvLet(s, _, sconcat(string, string))], unit),
    gvar(s, string).

test(infer_04_multiple_gvars, [nondet, true(T == unit)]) :-
    infer([
        gvLet(a, _, int),
        gvLet(b, _, float),
        gvLet(c, _, string)
    ], T),
    gvar(a, int), gvar(b, float), gvar(c, string).

test(infer_05_gfLet_add, [nondet]) :-
    infer([
        gfLet(add, _, [X, Y], [ expr(iplus(X, Y)) ])
    ], unit),
    gvar(add, [int, int, int]).

test(infer_06_use_defined_fct, [nondet, true(T == unit)]) :-
    infer([
        gfLet(add, _, [X, Y], [ expr(iplus(X, Y)) ]),
        gvLet(result, _, add(int, int))
    ], T),
    gvar(result, int).

test(infer_07_expr_stmt, [nondet, true(T == int)]) :-
    infer([ expr(imul(int, int)) ], T).

test(infer_08_return_stmt, [nondet, true(T == float)]) :-
    infer([ return(fplus(float, float)) ], T).

test(infer_09_if_simple, [nondet, true(T == int)]) :-
    infer([
        if(iless(int, int),
           [ expr(iplus(int, int)) ],
           [ expr(iminus(int, int)) ])
    ], T).

test(infer_10_if_returns_type, [nondet, true(T == int)]) :-
    infer([
        block([ if(and(bool, bool),
                   [ return(int) ],
                   [ return(iplus(int, int)) ]) ])
    ], T).

test(infer_11_if_mismatch, [fail]) :-
    infer([
        if(bool,
           [ expr(int) ],
           [ expr(float) ])
    ], _).

test(infer_12_letIn, [nondet, true(T == int)]) :-
    infer([
        letIn(y, _, iplus(int, int),
              [ expr(imul(y, int)) ])
    ], T).

test(infer_13_letIn_bool_body, [nondet, true(T == bool)]) :-
    infer([
        letIn(k, _, int,
              [ expr(iless(k, int)) ])
    ], T).

test(infer_14_for, [nondet, true(T == unit)]) :-
    infer([
        for(_, int, int, [ expr(print(int)) ])
    ], T).

test(infer_15_for_bad_bounds, [fail]) :-
    infer([
        for(_, float, int, [ expr(print(int)) ])
    ], _).

test(infer_16_block, [nondet, true(T == string)]) :-
    infer([
        block([
            expr(iplus(int, int)),
            expr(sconcat(string, string))
        ])
    ], T).

test(infer_17_mixed_params, [nondet]) :-
    infer([
        gfLet(mixer, _, [X, Y],
              [ expr(fplus(iToFloat(X), Y)) ])
    ], unit),
    gvar(mixer, [int, float, float]).

test(infer_18_type_mismatch, [fail]) :-
    infer([
        gfLet(bad, _, [X],
              [ expr(iplus(X, float)) ])
    ], _).

test(infer_19_program, [nondet, true(T == unit)]) :-
    infer([
        gfLet(square, _, [N], [ expr(imul(N, N)) ]),
        gvLet(ans, _, square(int))
    ], T),
    gvar(square, [int, int]),
    gvar(ans, int).

test(infer_20_for_with_letIn, [nondet, true(T == unit)]) :-
    infer([
        for(_, int, int,
            [ letIn(tmp, _, iplus(int, int),
                    [ expr(print(tmp)) ]) ])
    ], T).

test(infer_21_nested_if, [nondet, true(T == bool)]) :-
    infer([
        block([
            if(bool,
               [ return(and(bool, bool)) ],
               [ return(or(bool, bool)) ])
        ])
    ], T).

test(infer_22_fct_calls_fct, [nondet]) :-
    infer([
        gfLet(inc, _, [X], [ expr(iplus(X, int)) ]),
        gfLet(dbl, _, [Y], [ expr(inc(inc(Y))) ])
    ], unit),
    gvar(inc, [int, int]),
    gvar(dbl, [int, int]).

test(infer_23_print, [nondet, true(T == unit)]) :-
    infer([ expr(print(float)) ], T).

test(infer_24_gvLet_mismatch, [fail]) :-
    infer([ gvLet(x, float, int) ], _).

% sum types, tuples, unpacking, match

test(bonus_25_tuple_construct, [nondet, true(T == tuple([int, float, string]))]) :-
    deleteGVars(),
    typeExp(mkTuple([int, float, string]), T).

test(bonus_26_tuple_unpack, [nondet, true(T == unit)]) :-
    infer([
        gvLetTuple([a, b], [_, _], mkTuple([int, string]))
    ], T),
    gvar(a, int), gvar(b, string).

test(bonus_27_sum_inl, [nondet]) :-
    deleteGVars(),
    typeExp(inl(int), sum(int, float)).

test(bonus_28_sum_inr, [nondet]) :-
    deleteGVars(),
    typeExp(inr(string), sum(int, string)).

% Match: v explicitly typed sum(int, string) so inr branch sees string.
test(bonus_29_match, [nondet, true(T == int)]) :-
    infer([
        gvLet(v, sum(int, string), inl(int)),
        match(v, [
            case(inl(LI), [ expr(iplus(LI, int)) ]),
            case(inr(RI), [ expr(slength(RI)) ])
        ])
    ], T).

% Same v type, but bodies disagree on result type -> match fails.
test(bonus_30_match_mismatch, [fail]) :-
    infer([
        gvLet(v, sum(int, string), inl(int)),
        match(v, [
            case(inl(A), [ expr(iplus(A, int)) ]),
            case(inr(B), [ expr(sconcat(B, string)) ])
        ])
    ], _).

test(bonus_31_nested_tuple, [nondet]) :-
    deleteGVars(),
    typeExp(mkTuple([int, mkTuple([float, string])]),
            tuple([int, tuple([float, string])])).

:- end_tests(typeInf).
