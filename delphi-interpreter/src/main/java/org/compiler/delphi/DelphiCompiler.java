package org.compiler.delphi;

import org.antlr.v4.runtime.tree.ParseTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DelphiCompiler {
    private final Map<String, ConstantValue> globalConstants = new LinkedHashMap<>();
    private final Map<String, VariableSymbol> globalVariables = new LinkedHashMap<>();
    private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
    private final LinkedHashMap<String, StringConstant> stringConstants = new LinkedHashMap<>();

    private final StringBuilder functionSection = new StringBuilder();
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private final Deque<LoopContext> loops = new ArrayDeque<>();

    private FunctionEmitter currentFunction;
    private String fmtInt;
    private String fmtStr;
    private String fmtBoolTrue;
    private String fmtBoolFalse;
    private String newlineStr;

    public String compile(ParseTree tree) {
        if (!(tree instanceof DelphiParser.ProgramContext program)) {
            throw error("Expected a Delphi program parse tree");
        }
        reset();
        validateProgram(program);
        internRuntimeStrings();
        collectTopLevel(program.block());
        emitUserFunctions();
        emitMain(program.block());
        return buildModule();
    }

    private void reset() {
        globalConstants.clear();
        globalVariables.clear();
        functions.clear();
        stringConstants.clear();
        scopes.clear();
        loops.clear();
        functionSection.setLength(0);
        currentFunction = null;
        fmtInt = null;
        fmtStr = null;
        fmtBoolTrue = null;
        fmtBoolFalse = null;
        newlineStr = null;
    }

    private void validateProgram(DelphiParser.ProgramContext program) {
        if (program.programHeading().UNIT() != null) {
            throw unsupported("UNIT compilation is not supported");
        }
    }

    private void internRuntimeStrings() {
        fmtInt = internCString("%d");
        fmtStr = internCString("%s");
        fmtBoolTrue = internCString("true");
        fmtBoolFalse = internCString("false");
        newlineStr = internCString("\n");
    }

    private void collectTopLevel(DelphiParser.BlockContext block) {
        for (ParseTree child : block.children) {
            if (child instanceof DelphiParser.TypeDefinitionPartContext) {
                throw unsupported("Type, class, interface, array, and record compilation is not supported");
            }
            if (child instanceof DelphiParser.ConstantDefinitionPartContext constantsPart) {
                collectConstants(constantsPart, globalConstants);
            } else if (child instanceof DelphiParser.VariableDeclarationPartContext variablesPart) {
                collectGlobalVariables(variablesPart);
            } else if (child instanceof DelphiParser.ProcedureAndFunctionDeclarationPartContext declPart) {
                collectFunction(declPart);
            }
        }
    }

    private void collectConstants(
            DelphiParser.ConstantDefinitionPartContext ctx,
            Map<String, ConstantValue> destination
    ) {
        for (DelphiParser.ConstantDefinitionContext constantDef : ctx.constantDefinition()) {
            String name = lc(constantDef.identifier().getText());
            ConstantValue value = evalConstant(constantDef.constant(), destination);
            destination.put(name, value);
        }
    }

    private void collectGlobalVariables(DelphiParser.VariableDeclarationPartContext ctx) {
        for (DelphiParser.VariableDeclarationContext decl : ctx.variableDeclaration()) {
            ValueType type = parseSupportedType(decl.type_());
            for (DelphiParser.IdentifierContext id : decl.identifierList().identifier()) {
                String name = lc(id.getText());
                globalVariables.put(name, new VariableSymbol(name, type, "@" + sanitizeGlobal(name)));
            }
        }
    }

    private void collectFunction(DelphiParser.ProcedureAndFunctionDeclarationPartContext ctx) {
        if (ctx.procedureDeclaration() != null) {
            DelphiParser.ProcedureDeclarationContext proc = ctx.procedureDeclaration();
            String name = simpleMethodName(proc.methodIdentifier());
            FunctionSymbol function = new FunctionSymbol(name, "@" + sanitizeFunction(name), ValueType.VOID, false);
            readParameters(function, proc.formalParameterList());
            function.procedureCtx = proc;
            functions.put(name, function);
            return;
        }

        if (ctx.functionDeclaration() != null) {
            DelphiParser.FunctionDeclarationContext fn = ctx.functionDeclaration();
            String name = simpleMethodName(fn.methodIdentifier());
            FunctionSymbol function = new FunctionSymbol(
                    name,
                    "@" + sanitizeFunction(name),
                    parseSupportedType(fn.resultType()),
                    true
            );
            readParameters(function, fn.formalParameterList());
            function.functionCtx = fn;
            functions.put(name, function);
            return;
        }

        throw unsupported("Constructors and destructors are not supported in the LLVM compiler backend");
    }

    private void readParameters(FunctionSymbol function, DelphiParser.FormalParameterListContext list) {
        if (list == null) return;
        for (DelphiParser.FormalParameterSectionContext section : list.formalParameterSection()) {
            if (section.VAR() != null || section.FUNCTION() != null || section.PROCEDURE() != null) {
                throw unsupported("Only value parameters are supported by the LLVM compiler backend");
            }
            ValueType type = parseSupportedType(section.parameterGroup().typeIdentifier());
            for (DelphiParser.IdentifierContext id : section.parameterGroup().identifierList().identifier()) {
                function.parameters.add(new Parameter(lc(id.getText()), type));
            }
        }
    }

    private void emitUserFunctions() {
        for (FunctionSymbol function : functions.values()) {
            emitFunction(function);
        }
    }

    private void emitFunction(FunctionSymbol symbol) {
        currentFunction = new FunctionEmitter(symbol);
        scopes.clear();
        loops.clear();

        beginScope();
        functionHeader(symbol);
        currentFunction.emitLabel("entry");

        for (Parameter parameter : symbol.parameters) {
            String ptr = currentFunction.nextTemp("addr");
            currentFunction.emit(ptr + " = alloca " + parameter.type.llvmType());
            currentFunction.emit("store " + parameter.type.llvmType() + " %" + parameter.name
                    + ", ptr " + ptr);
            declare(new VariableSymbol(parameter.name, parameter.type, ptr));
        }

        if (symbol.isFunction) {
            String retPtr = currentFunction.nextTemp("ret");
            currentFunction.emit(retPtr + " = alloca " + symbol.returnType.llvmType());
            currentFunction.emit("store " + symbol.returnType.llvmType() + " " + defaultValue(symbol.returnType)
                    + ", ptr " + retPtr);
            declare(new VariableSymbol(symbol.name, symbol.returnType, retPtr));
            declare(new VariableSymbol("result", symbol.returnType, retPtr));
        }

        DelphiParser.BlockContext block = symbol.isFunction
                ? symbol.functionCtx.block()
                : symbol.procedureCtx.block();
        prepareLocalBlock(block, false);
        compileCompoundStatement(block.compoundStatement());

        if (!currentFunction.terminated) {
            emitDefaultReturn(symbol);
        }

        functionSection.append(currentFunction.finish());
        currentFunction = null;
        scopes.clear();
        loops.clear();
    }

    private void emitMain(DelphiParser.BlockContext block) {
        FunctionSymbol main = new FunctionSymbol("main", "@main", ValueType.INTEGER, true);
        currentFunction = new FunctionEmitter(main);
        scopes.clear();
        loops.clear();

        beginScope();
        functionSection.append("define i32 @main() {\n");
        currentFunction.emitLabel("entry");

        for (VariableSymbol global : globalVariables.values()) {
            declare(global);
        }
        for (Map.Entry<String, ConstantValue> entry : globalConstants.entrySet()) {
            declare(new ConstantSymbol(entry.getKey(), entry.getValue()));
        }

        prepareLocalBlock(block, true);
        compileCompoundStatement(block.compoundStatement());

        if (!currentFunction.terminated) {
            currentFunction.emit("ret i32 0");
            currentFunction.terminated = true;
        }

        functionSection.append(currentFunction.finish());
        currentFunction = null;
        scopes.clear();
        loops.clear();
    }

    private void functionHeader(FunctionSymbol symbol) {
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < symbol.parameters.size(); i++) {
            Parameter parameter = symbol.parameters.get(i);
            if (i > 0) params.append(", ");
            params.append(parameter.type.llvmType()).append(" %").append(parameter.name);
        }
        functionSection.append("define ")
                .append(symbol.returnType.llvmType())
                .append(' ')
                .append(symbol.llvmName)
                .append('(')
                .append(params)
                .append(") {\n");
    }

    private void emitDefaultReturn(FunctionSymbol symbol) {
        if (symbol.returnType == ValueType.VOID) {
            currentFunction.emit("ret void");
            currentFunction.terminated = true;
            return;
        }

        if (symbol.llvmName.equals("@main")) {
            currentFunction.emit("ret i32 0");
            currentFunction.terminated = true;
            return;
        }

        VariableSymbol retVar = expectVariable(symbol.name);
        ValueRef value = loadVariable(retVar);
        currentFunction.emit("ret " + value.type.llvmType() + " " + value.ir);
        currentFunction.terminated = true;
    }

    private void prepareLocalBlock(DelphiParser.BlockContext block, boolean allowTopLevelDeclarations) {
        Map<String, ConstantValue> localConstants = new LinkedHashMap<>();
        for (ParseTree child : block.children) {
            if (child instanceof DelphiParser.TypeDefinitionPartContext) {
                throw unsupported("Local type definitions are not supported by the LLVM compiler backend");
            }
            if (child instanceof DelphiParser.ProcedureAndFunctionDeclarationPartContext) {
                if (!allowTopLevelDeclarations) {
                    throw unsupported("Nested procedures/functions are not supported by the LLVM compiler backend");
                }
                continue;
            }
            if (child instanceof DelphiParser.ConstantDefinitionPartContext constantsPart) {
                collectConstants(constantsPart, localConstants);
            } else if (child instanceof DelphiParser.VariableDeclarationPartContext variablesPart) {
                allocateLocals(variablesPart);
            }
        }

        for (Map.Entry<String, ConstantValue> entry : localConstants.entrySet()) {
            declare(new ConstantSymbol(entry.getKey(), entry.getValue()));
        }
    }

    private void allocateLocals(DelphiParser.VariableDeclarationPartContext ctx) {
        for (DelphiParser.VariableDeclarationContext decl : ctx.variableDeclaration()) {
            ValueType type = parseSupportedType(decl.type_());
            for (DelphiParser.IdentifierContext id : decl.identifierList().identifier()) {
                String name = lc(id.getText());
                String ptr = currentFunction.nextTemp(name + ".addr");
                currentFunction.emit(ptr + " = alloca " + type.llvmType());
                currentFunction.emit("store " + type.llvmType() + " " + defaultValue(type) + ", ptr " + ptr);
                declare(new VariableSymbol(name, type, ptr));
            }
        }
    }

    private void compileCompoundStatement(DelphiParser.CompoundStatementContext ctx) {
        compileStatements(ctx.statements());
    }

    private void compileStatements(DelphiParser.StatementsContext ctx) {
        for (DelphiParser.StatementContext statement : ctx.statement()) {
            if (currentFunction.terminated) break;
            compileStatement(statement);
        }
    }

    private void compileStatement(DelphiParser.StatementContext ctx) {
        if (ctx.label() != null) {
            throw unsupported("Labels and goto are not supported by the LLVM compiler backend");
        }
        compileUnlabelled(ctx.unlabelledStatement());
    }

    private void compileUnlabelled(DelphiParser.UnlabelledStatementContext ctx) {
        if (ctx.simpleStatement() != null) {
            compileSimpleStatement(ctx.simpleStatement());
        } else if (ctx.structuredStatement() != null) {
            compileStructuredStatement(ctx.structuredStatement());
        }
    }

    private void compileSimpleStatement(DelphiParser.SimpleStatementContext ctx) {
        if (ctx.assignmentStatement() != null) {
            compileAssignment(ctx.assignmentStatement());
        } else if (ctx.procedureStatement() != null) {
            compileProcedureStatement(ctx.procedureStatement());
        } else if (ctx.breakStatement() != null) {
            compileBreak();
        } else if (ctx.continueStatement() != null) {
            compileContinue();
        } else if (ctx.gotoStatement() != null) {
            throw unsupported("Goto is not supported by the LLVM compiler backend");
        }
    }

    private void compileStructuredStatement(DelphiParser.StructuredStatementContext ctx) {
        if (ctx.compoundStatement() != null) {
            compileCompoundStatement(ctx.compoundStatement());
        } else if (ctx.conditionalStatement() != null) {
            compileConditional(ctx.conditionalStatement());
        } else if (ctx.repetetiveStatement() != null) {
            compileRepetitive(ctx.repetetiveStatement());
        } else if (ctx.withStatement() != null) {
            throw unsupported("with statements are not supported by the LLVM compiler backend");
        }
    }

    private void compileConditional(DelphiParser.ConditionalStatementContext ctx) {
        if (ctx.ifStatement() != null) {
            compileIf(ctx.ifStatement());
        } else if (ctx.caseStatement() != null) {
            compileCase(ctx.caseStatement());
        }
    }

    private void compileRepetitive(DelphiParser.RepetetiveStatementContext ctx) {
        if (ctx.whileStatement() != null) {
            compileWhile(ctx.whileStatement());
        } else if (ctx.repeatStatement() != null) {
            compileRepeat(ctx.repeatStatement());
        } else if (ctx.forStatement() != null) {
            compileFor(ctx.forStatement());
        }
    }

    private void compileAssignment(DelphiParser.AssignmentStatementContext ctx) {
        VariableSymbol target = resolveAssignable(ctx.variable());
        ValueRef value = compileExpression(ctx.expression());
        ensureAssignable(target.type, value.type, ctx.variable().getText());
        currentFunction.emit("store " + target.type.llvmType() + " " + value.ir + ", ptr " + target.pointer);
    }

    private void compileProcedureStatement(DelphiParser.ProcedureStatementContext ctx) {
        String name = simpleVariableName(ctx.variable());
        List<ValueRef> args = compileArguments(ctx.parameterList());

        if (name.equals("write") || name.equals("writeln")) {
            emitWrite(args, name.equals("writeln"));
            return;
        }

        FunctionSymbol function = functions.get(name);
        if (function == null) {
            throw unsupported("Unsupported procedure call: " + ctx.getText());
        }

        if (function.parameters.size() != args.size()) {
            throw error("Argument count mismatch for " + name);
        }
        String call = emitCall(function, args);
        if (function.returnType != ValueType.VOID) {
            // value intentionally discarded
            currentFunction.emit("; discarded return value from " + function.name);
        }
        currentFunction.emit(call);
    }

    private void compileBreak() {
        LoopContext loop = loops.peek();
        if (loop == null) {
            throw error("break used outside of a loop");
        }
        currentFunction.emit("br label %" + loop.breakLabel);
        currentFunction.terminated = true;
    }

    private void compileContinue() {
        LoopContext loop = loops.peek();
        if (loop == null) {
            throw error("continue used outside of a loop");
        }
        currentFunction.emit("br label %" + loop.continueLabel);
        currentFunction.terminated = true;
    }

    private void compileIf(DelphiParser.IfStatementContext ctx) {
        ValueRef condition = asBoolean(compileExpression(ctx.expression()), ctx.expression().getText());
        String thenLabel = currentFunction.nextLabel("if.then");
        String elseLabel = ctx.ELSE() != null ? currentFunction.nextLabel("if.else") : null;
        String endLabel = currentFunction.nextLabel("if.end");

        currentFunction.emit("br i1 " + condition.ir + ", label %" + thenLabel + ", label %"
                + (elseLabel != null ? elseLabel : endLabel));
        currentFunction.terminated = true;

        currentFunction.emitLabel(thenLabel);
        compileStatement(ctx.statement(0));
        if (!currentFunction.terminated) {
            currentFunction.emit("br label %" + endLabel);
            currentFunction.terminated = true;
        }

        if (ctx.ELSE() != null) {
            currentFunction.emitLabel(elseLabel);
            compileStatement(ctx.statement(1));
            if (!currentFunction.terminated) {
                currentFunction.emit("br label %" + endLabel);
                currentFunction.terminated = true;
            }
        }

        currentFunction.emitLabel(endLabel);
    }

    private void compileCase(DelphiParser.CaseStatementContext ctx) {
        ValueRef caseValue = compileExpression(ctx.expression());
        if (caseValue.type != ValueType.INTEGER && caseValue.type != ValueType.BOOLEAN) {
            throw unsupported("Only integer and boolean case expressions are supported");
        }

        String endLabel = currentFunction.nextLabel("case.end");
        String elseLabel = ctx.ELSE() != null ? currentFunction.nextLabel("case.else") : endLabel;
        String nextTest = currentFunction.nextLabel("case.test");
        currentFunction.emit("br label %" + nextTest);
        currentFunction.terminated = true;

        for (DelphiParser.CaseListElementContext element : ctx.caseListElement()) {
            currentFunction.emitLabel(nextTest);
            String matchLabel = currentFunction.nextLabel("case.match");
            String fallthroughLabel = currentFunction.nextLabel("case.next");
            emitCaseMatch(caseValue, element.constList(), matchLabel, fallthroughLabel);

            currentFunction.emitLabel(matchLabel);
            compileStatement(element.statement());
            if (!currentFunction.terminated) {
                currentFunction.emit("br label %" + endLabel);
                currentFunction.terminated = true;
            }
            nextTest = fallthroughLabel;
        }

        currentFunction.emitLabel(nextTest);
        currentFunction.emit("br label %" + elseLabel);
        currentFunction.terminated = true;

        if (ctx.ELSE() != null && ctx.statements() != null) {
            currentFunction.emitLabel(elseLabel);
            compileStatements(ctx.statements());
            if (!currentFunction.terminated) {
                currentFunction.emit("br label %" + endLabel);
                currentFunction.terminated = true;
            }
        }

        currentFunction.emitLabel(endLabel);
    }

    private void emitCaseMatch(
            ValueRef caseValue,
            DelphiParser.ConstListContext constList,
            String matchLabel,
            String fallthroughLabel
    ) {
        List<DelphiParser.ConstantContext> constants = constList.constant();
        for (int i = 0; i < constants.size(); i++) {
            ConstantValue constant = evalConstant(constants.get(i), visibleConstants());
            ensureAssignable(caseValue.type, constant.type, constants.get(i).getText());
            String cmp = currentFunction.nextTemp("casecmp");
            String next = i == constants.size() - 1 ? fallthroughLabel : currentFunction.nextLabel("case.or");
            currentFunction.emit(cmp + " = icmp eq " + caseValue.type.llvmType() + " " + caseValue.ir
                    + ", " + constant.ir());
            currentFunction.emit("br i1 " + cmp + ", label %" + matchLabel + ", label %" + next);
            currentFunction.terminated = true;
            if (i < constants.size() - 1) {
                currentFunction.emitLabel(next);
            }
        }
    }

    private void compileWhile(DelphiParser.WhileStatementContext ctx) {
        String condLabel = currentFunction.nextLabel("while.cond");
        String bodyLabel = currentFunction.nextLabel("while.body");
        String endLabel = currentFunction.nextLabel("while.end");

        currentFunction.emit("br label %" + condLabel);
        currentFunction.terminated = true;

        currentFunction.emitLabel(condLabel);
        ValueRef condition = asBoolean(compileExpression(ctx.expression()), ctx.expression().getText());
        currentFunction.emit("br i1 " + condition.ir + ", label %" + bodyLabel + ", label %" + endLabel);
        currentFunction.terminated = true;

        loops.push(new LoopContext(endLabel, condLabel));
        currentFunction.emitLabel(bodyLabel);
        compileStatement(ctx.statement());
        loops.pop();
        if (!currentFunction.terminated) {
            currentFunction.emit("br label %" + condLabel);
            currentFunction.terminated = true;
        }

        currentFunction.emitLabel(endLabel);
    }

    private void compileRepeat(DelphiParser.RepeatStatementContext ctx) {
        String bodyLabel = currentFunction.nextLabel("repeat.body");
        String condLabel = currentFunction.nextLabel("repeat.cond");
        String endLabel = currentFunction.nextLabel("repeat.end");

        currentFunction.emit("br label %" + bodyLabel);
        currentFunction.terminated = true;

        loops.push(new LoopContext(endLabel, condLabel));
        currentFunction.emitLabel(bodyLabel);
        compileStatements(ctx.statements());
        loops.pop();
        if (!currentFunction.terminated) {
            currentFunction.emit("br label %" + condLabel);
            currentFunction.terminated = true;
        }

        currentFunction.emitLabel(condLabel);
        ValueRef condition = asBoolean(compileExpression(ctx.expression()), ctx.expression().getText());
        currentFunction.emit("br i1 " + condition.ir + ", label %" + endLabel + ", label %" + bodyLabel);
        currentFunction.terminated = true;

        currentFunction.emitLabel(endLabel);
    }

    private void compileFor(DelphiParser.ForStatementContext ctx) {
        String varName = lc(ctx.identifier().getText());
        VariableSymbol loopVar = expectVariable(varName);
        if (loopVar.type != ValueType.INTEGER) {
            throw unsupported("For loops currently require an integer loop variable");
        }

        ValueRef initial = asInteger(compileExpression(ctx.forList().initialValue().expression()),
                ctx.forList().initialValue().getText());
        ValueRef bound = asInteger(compileExpression(ctx.forList().finalValue().expression()),
                ctx.forList().finalValue().getText());

        String counterPtr = currentFunction.nextTemp("for.counter");
        String boundPtr = currentFunction.nextTemp("for.bound");
        currentFunction.emit(counterPtr + " = alloca i32");
        currentFunction.emit(boundPtr + " = alloca i32");
        currentFunction.emit("store i32 " + initial.ir + ", ptr " + counterPtr);
        currentFunction.emit("store i32 " + bound.ir + ", ptr " + boundPtr);

        String condLabel = currentFunction.nextLabel("for.cond");
        String bodyLabel = currentFunction.nextLabel("for.body");
        String stepLabel = currentFunction.nextLabel("for.step");
        String endLabel = currentFunction.nextLabel("for.end");

        currentFunction.emit("br label %" + condLabel);
        currentFunction.terminated = true;

        boolean down = ctx.forList().DOWNTO() != null;

        currentFunction.emitLabel(condLabel);
        String counterVal = currentFunction.nextTemp("for.cur");
        String boundVal = currentFunction.nextTemp("for.max");
        String cond = currentFunction.nextTemp("for.cmp");
        currentFunction.emit(counterVal + " = load i32, ptr " + counterPtr);
        currentFunction.emit(boundVal + " = load i32, ptr " + boundPtr);
        currentFunction.emit(cond + " = icmp " + (down ? "sge" : "sle") + " i32 " + counterVal + ", " + boundVal);
        currentFunction.emit("br i1 " + cond + ", label %" + bodyLabel + ", label %" + endLabel);
        currentFunction.terminated = true;

        loops.push(new LoopContext(endLabel, stepLabel));
        currentFunction.emitLabel(bodyLabel);
        String visibleCounter = currentFunction.nextTemp("for.visible");
        currentFunction.emit(visibleCounter + " = load i32, ptr " + counterPtr);
        currentFunction.emit("store i32 " + visibleCounter + ", ptr " + loopVar.pointer);
        compileStatement(ctx.statement());
        loops.pop();
        if (!currentFunction.terminated) {
            currentFunction.emit("br label %" + stepLabel);
            currentFunction.terminated = true;
        }

        currentFunction.emitLabel(stepLabel);
        String currentCounter = currentFunction.nextTemp("for.step.cur");
        String nextCounter = currentFunction.nextTemp("for.step.next");
        currentFunction.emit(currentCounter + " = load i32, ptr " + counterPtr);
        currentFunction.emit(nextCounter + " = " + (down ? "sub" : "add") + " i32 " + currentCounter + ", 1");
        currentFunction.emit("store i32 " + nextCounter + ", ptr " + counterPtr);
        currentFunction.emit("br label %" + condLabel);
        currentFunction.terminated = true;

        currentFunction.emitLabel(endLabel);
    }

    private List<ValueRef> compileArguments(DelphiParser.ParameterListContext parameterList) {
        List<ValueRef> args = new ArrayList<>();
        if (parameterList == null) return args;
        for (DelphiParser.ActualParameterContext parameter : parameterList.actualParameter()) {
            args.add(compileExpression(parameter.expression()));
        }
        return args;
    }

    private ValueRef compileExpression(DelphiParser.ExpressionContext ctx) {
        ValueRef left = compileSimpleExpression(ctx.simpleExpression());
        if (ctx.relationaloperator() == null) return left;

        ValueRef right = compileExpression(ctx.expression());
        var op = ctx.relationaloperator();
        if (left.type == ValueType.STRING || right.type == ValueType.STRING) {
            throw unsupported("String comparisons are not supported by the LLVM compiler backend");
        }
        if (left.type != right.type) {
            ensureAssignable(left.type, right.type, ctx.getText());
        }

        String temp = currentFunction.nextTemp("cmp");
        String predicate;
        if (op.EQUAL() != null) {
            predicate = left.type == ValueType.BOOLEAN ? "eq" : "eq";
        } else if (op.NOT_EQUAL() != null) {
            predicate = left.type == ValueType.BOOLEAN ? "ne" : "ne";
        } else {
            ValueRef lInt = asInteger(left, ctx.simpleExpression().getText());
            ValueRef rInt = asInteger(right, ctx.expression().getText());
            predicate = switch (op.getText().toLowerCase(Locale.ROOT)) {
                case "<" -> "slt";
                case "<=" -> "sle";
                case ">" -> "sgt";
                case ">=" -> "sge";
                default -> throw unsupported("Unsupported relational operator: " + op.getText());
            };
            currentFunction.emit(temp + " = icmp " + predicate + " i32 " + lInt.ir + ", " + rInt.ir);
            return new ValueRef(ValueType.BOOLEAN, temp);
        }
        currentFunction.emit(temp + " = icmp " + predicate + " " + left.type.llvmType() + " "
                + left.ir + ", " + right.ir);
        return new ValueRef(ValueType.BOOLEAN, temp);
    }

    private ValueRef compileSimpleExpression(DelphiParser.SimpleExpressionContext ctx) {
        ValueRef left = compileTerm(ctx.term());
        if (ctx.additiveoperator() == null) return left;

        ValueRef right = compileSimpleExpression(ctx.simpleExpression());
        if (ctx.additiveoperator().OR() != null) {
            ValueRef lBool = asBoolean(left, ctx.term().getText());
            ValueRef rBool = asBoolean(right, ctx.simpleExpression().getText());
            String temp = currentFunction.nextTemp("or");
            currentFunction.emit(temp + " = or i1 " + lBool.ir + ", " + rBool.ir);
            return new ValueRef(ValueType.BOOLEAN, temp);
        }

        ValueRef lInt = asInteger(left, ctx.term().getText());
        ValueRef rInt = asInteger(right, ctx.simpleExpression().getText());
        String temp = currentFunction.nextTemp("add");
        String opcode = ctx.additiveoperator().PLUS() != null ? "add" : "sub";
        currentFunction.emit(temp + " = " + opcode + " i32 " + lInt.ir + ", " + rInt.ir);
        return new ValueRef(ValueType.INTEGER, temp);
    }

    private ValueRef compileTerm(DelphiParser.TermContext ctx) {
        ValueRef left = compileSignedFactor(ctx.signedFactor());
        if (ctx.multiplicativeoperator() == null) return left;

        if (ctx.multiplicativeoperator().AND() != null) {
            ValueRef lBool = asBoolean(left, ctx.signedFactor().getText());
            ValueRef rBool = asBoolean(compileTerm(ctx.term()), ctx.term().getText());
            String temp = currentFunction.nextTemp("and");
            currentFunction.emit(temp + " = and i1 " + lBool.ir + ", " + rBool.ir);
            return new ValueRef(ValueType.BOOLEAN, temp);
        }

        ValueRef lInt = asInteger(left, ctx.signedFactor().getText());
        ValueRef rInt = asInteger(compileTerm(ctx.term()), ctx.term().getText());
        String temp = currentFunction.nextTemp("mul");
        String opcode;
        if (ctx.multiplicativeoperator().STAR() != null) {
            opcode = "mul";
        } else if (ctx.multiplicativeoperator().DIV() != null) {
            opcode = "sdiv";
        } else if (ctx.multiplicativeoperator().MOD() != null) {
            opcode = "srem";
        } else {
            throw unsupported("Only integer *, div, mod, and boolean and are supported");
        }
        currentFunction.emit(temp + " = " + opcode + " i32 " + lInt.ir + ", " + rInt.ir);
        return new ValueRef(ValueType.INTEGER, temp);
    }

    private ValueRef compileSignedFactor(DelphiParser.SignedFactorContext ctx) {
        ValueRef value = compileFactor(ctx.factor());
        if (ctx.MINUS() == null) return value;
        ValueRef asInt = asInteger(value, ctx.getText());
        String temp = currentFunction.nextTemp("neg");
        currentFunction.emit(temp + " = sub i32 0, " + asInt.ir);
        return new ValueRef(ValueType.INTEGER, temp);
    }

    private ValueRef compileFactor(DelphiParser.FactorContext ctx) {
        if (ctx.functionDesignator() != null) {
            return compileFunctionCall(ctx.functionDesignator());
        }
        if (ctx.variable() != null) {
            Symbol symbol = resolveSymbol(simpleVariableName(ctx.variable()));
            if (symbol instanceof ConstantSymbol constant) {
                return constant.value.toValueRef();
            }
            if (symbol instanceof VariableSymbol variable) {
                return loadVariable(variable);
            }
            throw error("Unknown symbol: " + ctx.variable().getText());
        }
        if (ctx.expression() != null) {
            return compileExpression(ctx.expression());
        }
        if (ctx.unsignedConstant() != null) {
            return compileUnsignedConstant(ctx.unsignedConstant());
        }
        if (ctx.NOT() != null) {
            ValueRef value = asBoolean(compileFactor(ctx.factor()), ctx.factor().getText());
            String temp = currentFunction.nextTemp("not");
            currentFunction.emit(temp + " = xor i1 " + value.ir + ", true");
            return new ValueRef(ValueType.BOOLEAN, temp);
        }
        if (ctx.bool_() != null) {
            return new ValueRef(ValueType.BOOLEAN, ctx.bool_().TRUE() != null ? "true" : "false");
        }
        throw unsupported("Unsupported factor: " + ctx.getText());
    }

    private ValueRef compileUnsignedConstant(DelphiParser.UnsignedConstantContext ctx) {
        if (ctx.unsignedNumber() != null) {
            return new ValueRef(ValueType.INTEGER, ctx.unsignedNumber().getText());
        }
        if (ctx.string() != null) {
            return new ValueRef(ValueType.STRING, pointerToCString(stripQuotes(ctx.string().getText())));
        }
        if (ctx.constantChr() != null) {
            String value = String.valueOf((char) Integer.parseInt(ctx.constantChr().unsignedInteger().getText()));
            return new ValueRef(ValueType.STRING, pointerToCString(value));
        }
        throw unsupported("nil is not supported by the LLVM compiler backend");
    }

    private ValueRef compileFunctionCall(DelphiParser.FunctionDesignatorContext ctx) {
        String name = simpleVariableName(ctx.variable());
        List<ValueRef> args = compileArguments(ctx.parameterList());

        ValueRef builtin = emitBuiltinFunction(name, args);
        if (builtin != null) return builtin;

        FunctionSymbol function = functions.get(name);
        if (function == null) {
            throw unsupported("Unsupported function call: " + ctx.getText());
        }
        if (function.parameters.size() != args.size()) {
            throw error("Argument count mismatch for " + name);
        }
        if (function.returnType == ValueType.VOID) {
            throw error("Procedure " + name + " cannot be used in an expression");
        }

        String temp = currentFunction.nextTemp("call");
        currentFunction.emit(temp + " = " + buildCall(function, args));
        return new ValueRef(function.returnType, temp);
    }

    private ValueRef emitBuiltinFunction(String name, List<ValueRef> args) {
        return switch (name) {
            case "succ" -> {
                ValueRef arg = asInteger(expectArg(name, args, 1), name);
                String temp = currentFunction.nextTemp("succ");
                currentFunction.emit(temp + " = add i32 " + arg.ir + ", 1");
                yield new ValueRef(ValueType.INTEGER, temp);
            }
            case "pred" -> {
                ValueRef arg = asInteger(expectArg(name, args, 1), name);
                String temp = currentFunction.nextTemp("pred");
                currentFunction.emit(temp + " = sub i32 " + arg.ir + ", 1");
                yield new ValueRef(ValueType.INTEGER, temp);
            }
            case "sqr" -> {
                ValueRef arg = asInteger(expectArg(name, args, 1), name);
                String temp = currentFunction.nextTemp("sqr");
                currentFunction.emit(temp + " = mul i32 " + arg.ir + ", " + arg.ir);
                yield new ValueRef(ValueType.INTEGER, temp);
            }
            case "odd" -> {
                ValueRef arg = asInteger(expectArg(name, args, 1), name);
                String rem = currentFunction.nextTemp("odd.rem");
                String cmp = currentFunction.nextTemp("odd.cmp");
                currentFunction.emit(rem + " = srem i32 " + arg.ir + ", 2");
                currentFunction.emit(cmp + " = icmp ne i32 " + rem + ", 0");
                yield new ValueRef(ValueType.BOOLEAN, cmp);
            }
            default -> null;
        };
    }

    private ValueRef expectArg(String name, List<ValueRef> args, int count) {
        if (args.size() != count) {
            throw error("Builtin " + name + " expects " + count + " argument(s)");
        }
        return args.get(0);
    }

    private void emitWrite(List<ValueRef> args, boolean newline) {
        for (ValueRef arg : args) {
            if (arg.type == ValueType.INTEGER) {
                currentFunction.emit(printfCall(pointerToGlobal(fmtInt), "i32 " + arg.ir));
            } else if (arg.type == ValueType.STRING) {
                currentFunction.emit(printfCall(pointerToGlobal(fmtStr), "ptr " + arg.ir));
            } else if (arg.type == ValueType.BOOLEAN) {
                String boolStr = currentFunction.nextTemp("boolstr");
                currentFunction.emit(boolStr + " = select i1 " + arg.ir + ", ptr "
                        + pointerToGlobal(fmtBoolTrue) + ", ptr " + pointerToGlobal(fmtBoolFalse));
                currentFunction.emit(printfCall(pointerToGlobal(fmtStr), "ptr " + boolStr));
            } else {
                throw unsupported("Unsupported write/writeln argument type");
            }
        }
        if (newline) {
            currentFunction.emit(printfCall(pointerToGlobal(fmtStr), "ptr " + pointerToGlobal(newlineStr)));
        }
    }

    private String printfCall(String formatPtr, String typedValue) {
        return "call i32 (ptr, ...) @printf(ptr " + formatPtr + ", " + typedValue + ")";
    }

    private String emitCall(FunctionSymbol function, List<ValueRef> args) {
        return buildCall(function, args);
    }

    private String buildCall(FunctionSymbol function, List<ValueRef> args) {
        StringBuilder builder = new StringBuilder("call ")
                .append(function.returnType.llvmType())
                .append(' ')
                .append(function.llvmName)
                .append('(');
        for (int i = 0; i < function.parameters.size(); i++) {
            if (i > 0) builder.append(", ");
            Parameter parameter = function.parameters.get(i);
            ValueRef arg = args.get(i);
            ensureAssignable(parameter.type, arg.type, function.name);
            builder.append(parameter.type.llvmType()).append(' ').append(arg.ir);
        }
        builder.append(')');
        return builder.toString();
    }

    private ValueRef loadVariable(VariableSymbol variable) {
        String temp = currentFunction.nextTemp("load");
        currentFunction.emit(temp + " = load " + variable.type.llvmType() + ", ptr " + variable.pointer);
        return new ValueRef(variable.type, temp);
    }

    private VariableSymbol resolveAssignable(DelphiParser.VariableContext variable) {
        String name = simpleVariableName(variable);
        Symbol symbol = resolveSymbol(name);
        if (symbol instanceof VariableSymbol v) return v;
        if (symbol instanceof ConstantSymbol) {
            throw error("Cannot assign to constant " + name);
        }
        throw error("Unknown variable " + name);
    }

    private VariableSymbol expectVariable(String name) {
        Symbol symbol = resolveSymbol(name);
        if (symbol instanceof VariableSymbol variable) return variable;
        throw error("Unknown variable " + name);
    }

    private Symbol resolveSymbol(String name) {
        String key = lc(name);
        for (Map<String, Symbol> scope : scopes) {
            Symbol symbol = scope.get(key);
            if (symbol != null) return symbol;
        }
        VariableSymbol global = globalVariables.get(key);
        if (global != null) return global;
        ConstantValue constant = globalConstants.get(key);
        if (constant != null) return new ConstantSymbol(key, constant);
        return null;
    }

    private Map<String, ConstantValue> visibleConstants() {
        Map<String, ConstantValue> visible = new LinkedHashMap<>(globalConstants);
        Iterator<Map<String, Symbol>> iterator = scopes.descendingIterator();
        while (iterator.hasNext()) {
            Map<String, Symbol> scope = iterator.next();
            for (Map.Entry<String, Symbol> entry : scope.entrySet()) {
                if (entry.getValue() instanceof ConstantSymbol constant) {
                    visible.put(entry.getKey(), constant.value);
                }
            }
        }
        return visible;
    }

    private void beginScope() {
        scopes.push(new LinkedHashMap<>());
    }

    private void declare(Symbol symbol) {
        if (scopes.isEmpty()) beginScope();
        scopes.peek().put(symbol.name, symbol);
    }

    private ValueType parseSupportedType(DelphiParser.Type_Context ctx) {
        if (ctx.simpleType() == null || ctx.simpleType().typeIdentifier() == null) {
            throw unsupported("Only scalar INTEGER, BOOLEAN, and STRING variables are supported");
        }
        return parseSupportedType(ctx.simpleType().typeIdentifier());
    }

    private ValueType parseSupportedType(DelphiParser.ResultTypeContext ctx) {
        return parseSupportedType(ctx.typeIdentifier());
    }

    private ValueType parseSupportedType(DelphiParser.TypeIdentifierContext ctx) {
        String typeName = ctx.getText().toUpperCase(Locale.ROOT);
        return switch (typeName) {
            case "INTEGER" -> ValueType.INTEGER;
            case "BOOLEAN" -> ValueType.BOOLEAN;
            case "STRING" -> ValueType.STRING;
            default -> throw unsupported("Unsupported type in compiler backend: " + typeName);
        };
    }

    private ConstantValue evalConstant(DelphiParser.ConstantContext ctx, Map<String, ConstantValue> constants) {
        if (ctx.unsignedNumber() != null) {
            int value = Integer.parseInt(ctx.unsignedNumber().getText());
            if (ctx.sign() != null && ctx.sign().MINUS() != null) value = -value;
            return new ConstantValue(ValueType.INTEGER, String.valueOf(value));
        }
        if (ctx.string() != null) {
            return new ConstantValue(ValueType.STRING, pointerToCString(stripQuotes(ctx.string().getText())));
        }
        if (ctx.constantChr() != null) {
            String value = String.valueOf((char) Integer.parseInt(ctx.constantChr().unsignedInteger().getText()));
            return new ConstantValue(ValueType.STRING, pointerToCString(value));
        }
        if (ctx.identifier() != null) {
            ConstantValue prior = constants.get(lc(ctx.identifier().getText()));
            if (prior == null) {
                throw error("Unknown constant " + ctx.identifier().getText());
            }
            if (ctx.sign() != null && ctx.sign().MINUS() != null) {
                if (prior.type != ValueType.INTEGER) {
                    throw unsupported("Unary minus is only supported for integer constants");
                }
                return new ConstantValue(ValueType.INTEGER, String.valueOf(-Integer.parseInt(prior.raw)));
            }
            return prior;
        }
        throw unsupported("Unsupported constant form: " + ctx.getText());
    }

    private ValueRef asInteger(ValueRef value, String source) {
        if (value.type != ValueType.INTEGER) {
            throw error("Expected integer expression for " + source + " but found " + value.type);
        }
        return value;
    }

    private ValueRef asBoolean(ValueRef value, String source) {
        if (value.type == ValueType.BOOLEAN) return value;
        if (value.type == ValueType.INTEGER) {
            String temp = currentFunction.nextTemp("truthy");
            currentFunction.emit(temp + " = icmp ne i32 " + value.ir + ", 0");
            return new ValueRef(ValueType.BOOLEAN, temp);
        }
        throw error("Expected boolean-compatible expression for " + source + " but found " + value.type);
    }

    private void ensureAssignable(ValueType expected, ValueType actual, String context) {
        if (expected != actual) {
            throw error("Type mismatch in " + context + ": expected " + expected + " but found " + actual);
        }
    }

    private String buildModule() {
        StringBuilder module = new StringBuilder();
        module.append("; Generated by the Delphi LLVM compiler backend\n")
                .append("declare i32 @printf(ptr noundef, ...)\n\n");

        for (StringConstant constant : stringConstants.values()) {
            module.append('@').append(constant.name)
                    .append(" = private unnamed_addr constant [")
                    .append(constant.length)
                    .append(" x i8] c\"")
                    .append(constant.escaped)
                    .append("\"\n");
        }
        if (!stringConstants.isEmpty()) module.append('\n');

        for (VariableSymbol global : globalVariables.values()) {
            module.append(global.pointer)
                    .append(" = global ")
                    .append(global.type.llvmType())
                    .append(' ')
                    .append(defaultValue(global.type))
                    .append('\n');
        }
        if (!globalVariables.isEmpty()) module.append('\n');

        module.append(functionSection);
        return module.toString();
    }

    private String pointerToCString(String text) {
        String handle = internCString(text);
        return pointerToGlobal(handle);
    }

    private String internCString(String text) {
        String escaped = escapeCString(text);
        return stringConstants.computeIfAbsent(text, key ->
                new StringConstant(".str." + stringConstants.size(), escaped, cStringLength(text))
        ).name;
    }

    private String pointerToGlobal(String globalName) {
        StringConstant stringConstant = null;
        for (StringConstant candidate : stringConstants.values()) {
            if (candidate.name.equals(globalName)) {
                stringConstant = candidate;
                break;
            }
        }
        if (stringConstant == null) {
            throw error("Unknown string constant " + globalName);
        }
        return "getelementptr inbounds ([" + stringConstant.length + " x i8], ptr @" + stringConstant.name
                + ", i64 0, i64 0)";
    }

    private int cStringLength(String text) {
        int length = 1;
        for (int i = 0; i < text.length(); i++) {
            length++;
        }
        return length;
    }

    private String escapeCString(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 32 && ch <= 126 && ch != '\\' && ch != '"') {
                builder.append(ch);
            } else {
                builder.append(String.format("\\%02X", (int) ch));
            }
        }
        builder.append("\\00");
        return builder.toString();
    }

    private String simpleVariableName(DelphiParser.VariableContext ctx) {
        if (ctx.identifier().size() != 1 || ctx.getChildCount() != 1) {
            throw unsupported("Object field and method access are not supported by the LLVM compiler backend");
        }
        return lc(ctx.identifier(0).getText());
    }

    private String simpleMethodName(DelphiParser.MethodIdentifierContext ctx) {
        if (ctx.identifier().size() != 1) {
            throw unsupported("Class methods are not supported by the LLVM compiler backend");
        }
        return lc(ctx.identifier(0).getText());
    }

    private String sanitizeGlobal(String name) {
        return "g_" + name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String sanitizeFunction(String name) {
        return "pas_" + name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String defaultValue(ValueType type) {
        return switch (type) {
            case INTEGER -> "0";
            case BOOLEAN -> "false";
            case STRING -> "null";
            case VOID -> throw error("void has no default value");
        };
    }

    private String stripQuotes(String str) {
        if (str.length() >= 2 && str.startsWith("'") && str.endsWith("'")) {
            return str.substring(1, str.length() - 1).replace("''", "'");
        }
        return str;
    }

    private String lc(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    private CompilerException unsupported(String message) {
        return new CompilerException(message);
    }

    private CompilerException error(String message) {
        return new CompilerException(message);
    }

    private enum ValueType {
        INTEGER("i32"),
        BOOLEAN("i1"),
        STRING("ptr"),
        VOID("void");

        private final String llvmType;

        ValueType(String llvmType) {
            this.llvmType = llvmType;
        }

        public String llvmType() {
            return llvmType;
        }
    }

    private record ValueRef(ValueType type, String ir) {
    }

    private record ConstantValue(ValueType type, String raw) {
        private ValueRef toValueRef() {
            return new ValueRef(type, ir());
        }

        private String ir() {
            return raw;
        }
    }

    private abstract static class Symbol {
        final String name;

        Symbol(String name) {
            this.name = name;
        }
    }

    private static final class ConstantSymbol extends Symbol {
        private final ConstantValue value;

        private ConstantSymbol(String name, ConstantValue value) {
            super(name);
            this.value = value;
        }
    }

    private static final class VariableSymbol extends Symbol {
        private final ValueType type;
        private final String pointer;

        private VariableSymbol(String name, ValueType type, String pointer) {
            super(name);
            this.type = type;
            this.pointer = pointer;
        }
    }

    private static final class Parameter {
        private final String name;
        private final ValueType type;

        private Parameter(String name, ValueType type) {
            this.name = name;
            this.type = type;
        }
    }

    private static final class FunctionSymbol {
        private final String name;
        private final String llvmName;
        private final ValueType returnType;
        private final boolean isFunction;
        private final List<Parameter> parameters = new ArrayList<>();
        private DelphiParser.ProcedureDeclarationContext procedureCtx;
        private DelphiParser.FunctionDeclarationContext functionCtx;

        private FunctionSymbol(String name, String llvmName, ValueType returnType, boolean isFunction) {
            this.name = name;
            this.llvmName = llvmName;
            this.returnType = returnType;
            this.isFunction = isFunction;
        }
    }

    private static final class LoopContext {
        private final String breakLabel;
        private final String continueLabel;

        private LoopContext(String breakLabel, String continueLabel) {
            this.breakLabel = breakLabel;
            this.continueLabel = continueLabel;
        }
    }

    private static final class StringConstant {
        private final String name;
        private final String escaped;
        private final int length;

        private StringConstant(String name, String escaped, int length) {
            this.name = name;
            this.escaped = escaped;
            this.length = length;
        }
    }

    private static final class FunctionEmitter {
        private final FunctionSymbol symbol;
        private final StringBuilder body = new StringBuilder();
        private int tempCounter = 0;
        private int labelCounter = 0;
        private boolean terminated = false;

        private FunctionEmitter(FunctionSymbol symbol) {
            this.symbol = symbol;
        }

        private String nextTemp(String hint) {
            return "%" + hint.replaceAll("[^A-Za-z0-9_]", "_") + "." + tempCounter++;
        }

        private String nextLabel(String hint) {
            return hint.replaceAll("[^A-Za-z0-9_]", "_") + "." + labelCounter++;
        }

        private void emit(String line) {
            body.append("  ").append(line).append('\n');
        }

        private void emitLabel(String label) {
            body.append(label).append(":\n");
            terminated = false;
        }

        private String finish() {
            return body.append("}\n\n").toString();
        }
    }

    public static final class CompilerException extends RuntimeException {
        public CompilerException(String message) {
            super(message);
        }
    }
}
