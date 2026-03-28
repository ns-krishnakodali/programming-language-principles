package org.compiler.delphi;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;

public class DelphiInterpreter extends DelphiBaseVisitor<Object> {

    private final Map<String, Object> globalScope = new LinkedHashMap<>();
    private final Deque<Map<String, Object>> localScopes = new ArrayDeque<>();
    private final Map<String, ClassDescriptor> classes = new LinkedHashMap<>();
    private final Map<String, Set<String>> interfaces = new LinkedHashMap<>();
    private final Map<String, MethodDescriptor> globals = new LinkedHashMap<>();
    private final Deque<DelphiObject> selfStack = new ArrayDeque<>();
    private final Map<String, Object> constants = new LinkedHashMap<>();
    private boolean constantPropagationLog = true;
    private boolean inGlobalBlock = true;
    private String currentFunctionReturnVar = null;
    private Scanner stdin;

    //  Visit ParseTree Node Implementations

    @Override
    public Object visitProgram(DelphiParser.ProgramContext ctx) {
        inGlobalBlock = true;
        pushScope();
        visit(ctx.block());
        popScope();
        return null;
    }

    @Override
    public Object visitBlock(DelphiParser.BlockContext ctx) {
        for (var child : ctx.children) {
            if (child instanceof DelphiParser.TypeDefinitionPartContext)
                visitTypeDefinitionPart((DelphiParser.TypeDefinitionPartContext) child);
        }
        for (var child : ctx.children) {
            if (child instanceof DelphiParser.ProcedureAndFunctionDeclarationPartContext)
                collectDeclaration((DelphiParser.ProcedureAndFunctionDeclarationPartContext) child);
        }
        for (var child : ctx.children) {
            if (child instanceof DelphiParser.VariableDeclarationPartContext)
                visitVariableDeclarationPart((DelphiParser.VariableDeclarationPartContext) child);
            else if (child instanceof DelphiParser.ConstantDefinitionPartContext)
                visitConstantDefinitionPart((DelphiParser.ConstantDefinitionPartContext) child);
        }
        return visit(ctx.compoundStatement());
    }

    @Override
    public Object visitTypeDefinitionPart(DelphiParser.TypeDefinitionPartContext ctx) {
        for (var typeDefinitionCtx : ctx.typeDefinition()) visitTypeDefinition(typeDefinitionCtx);
        return null;
    }

    @Override
    public Object visitTypeDefinition(DelphiParser.TypeDefinitionContext ctx) {
        String typeName = ctx.identifier().getText();
        if (ctx.type_() != null) {
            if (ctx.type_().classType() != null)
                registerClass(typeName, ctx.type_().classType());
            else if (ctx.type_().interfaceType() != null)
                registerInterface(typeName, ctx.type_().interfaceType());
        }
        return null;
    }

    @Override
    public Object visitVariableDeclarationPart(DelphiParser.VariableDeclarationPartContext ctx) {
        for (var vd : ctx.variableDeclaration()) {
            String typeStr = vd.type_().getText().toUpperCase();
            Object def = DelphiObject.defaultVal(typeStr);
            for (var id : vd.identifierList().identifier())
                declare(id.getText(), def);
        }
        return null;
    }

    @Override
    public Object visitConstantDefinitionPart(DelphiParser.ConstantDefinitionPartContext ctx) {
        for (var constantDefinitionCtx : ctx.constantDefinition()) {
            String name = constantDefinitionCtx.identifier().getText();
            Object value = evalConstant(constantDefinitionCtx.constant());
            declare(name, value);
            constants.put(name.toLowerCase(), value);
        }
        return null;
    }

    @Override
    public Object visitCompoundStatement(DelphiParser.CompoundStatementContext ctx) {
        return visit(ctx.statements());
    }

    @Override
    public Object visitStatements(DelphiParser.StatementsContext ctx) {
        for (var statementCtx : ctx.statement()) visit(statementCtx);
        return null;
    }

    @Override
    public Object visitStatement(DelphiParser.StatementContext ctx) {
        return visit(ctx.unlabelledStatement());
    }

    @Override
    public Object visitUnlabelledStatement(DelphiParser.UnlabelledStatementContext ctx) {
        if (ctx.simpleStatement() != null) return visit(ctx.simpleStatement());
        if (ctx.structuredStatement() != null) return visit(ctx.structuredStatement());
        return null;
    }

    @Override
    public Object visitSimpleStatement(DelphiParser.SimpleStatementContext ctx) {
        if (ctx.assignmentStatement() != null) return visit(ctx.assignmentStatement());
        if (ctx.procedureStatement() != null) return visit(ctx.procedureStatement());
        if (ctx.breakStatement() != null) return visit(ctx.breakStatement());
        if (ctx.continueStatement() != null) return visit(ctx.continueStatement());
        return null;
    }

    @Override
    public Object visitBreakStatement(DelphiParser.BreakStatementContext ctx) {
        throw new BreakException();
    }

    @Override
    public Object visitContinueStatement(DelphiParser.ContinueStatementContext ctx) {
        throw new ContinueException();
    }

    @Override
    public Object visitAssignmentStatement(DelphiParser.AssignmentStatementContext ctx) {
        if (constantPropagationLog) {
            Object folded = tryConstantFold(ctx.expression());
            if (folded != null) {
                System.out.println("[AST] " + ctx.variable().getText() + " := " + folded
                        + "  (folded from: " + ctx.expression().getText() + ")");
            }
        }
        Object value = visit(ctx.expression());
        doAssign(ctx.variable(), value);
        return null;
    }

    @Override
    public Object visitProcedureStatement(DelphiParser.ProcedureStatementContext ctx) {
        List<Object> args = new ArrayList<>();
        if (ctx.parameterList() != null)
            for (var ap : ctx.parameterList().actualParameter())
                args.add(visit(ap.expression()));

        List<String> chain = varChain(ctx.variable());
        if (chain.isEmpty()) return null;

        if (chain.size() >= 2) {
            Object obj = resolveChainForMethodCall(chain);
            if (obj instanceof DelphiObject) {
                return callMethod((DelphiObject) obj, chain.getLast(), args);
            }
        }

        String name = chain.getFirst().toLowerCase();
        if (handleBuiltin(name, args, ctx)) return null;

        MethodDescriptor methodDescriptor = globals.get(name);
        if (methodDescriptor != null) return callGlobal(methodDescriptor, args);
        return null;
    }

    @Override
    public Object visitStructuredStatement(DelphiParser.StructuredStatementContext ctx) {
        if (ctx.compoundStatement() != null) return visit(ctx.compoundStatement());
        if (ctx.conditionalStatement() != null) return visit(ctx.conditionalStatement());
        if (ctx.repetetiveStatement() != null) return visit(ctx.repetetiveStatement());
        if (ctx.withStatement() != null) return visit(ctx.withStatement());
        return null;
    }

    @Override
    public Object visitIfStatement(DelphiParser.IfStatementContext ctx) {
        if (isTruth(visit(ctx.expression()))) visit(ctx.statement(0));
        else if (ctx.ELSE() != null) visit(ctx.statement(1));
        return null;
    }

    @Override
    public Object visitWhileStatement(DelphiParser.WhileStatementContext ctx) {
        pushScope();
        try {
            while (isTruth(visit(ctx.expression()))) {
                try {
                    visit(ctx.statement());
                } catch (ContinueException ignored) {
                } catch (BreakException e) {
                    break;
                }
            }
        } finally {
            popScope();
        }
        return null;
    }

    @Override
    public Object visitRepeatStatement(DelphiParser.RepeatStatementContext ctx) {
        pushScope();
        try {
            do {
                try {
                    visit(ctx.statements());
                } catch (ContinueException ignored) {
                } catch (BreakException e) {
                    return null;
                }
            } while (!isTruth(visit(ctx.expression())));
        } finally {
            popScope();
        }
        return null;
    }

    @Override
    public Object visitForStatement(DelphiParser.ForStatementContext ctx) {
        String var = ctx.identifier().getText();

        int from = toInt(visit(ctx.forList().initialValue().expression()));
        int to = toInt(visit(ctx.forList().finalValue().expression()));

        boolean down = ctx.forList().DOWNTO() != null;
        pushScope();
        try {
            if (!down) {
                for (int idx = from; idx <= to; idx++) {
                    set(var, idx);
                    try {
                        visit(ctx.statement());
                    } catch (ContinueException ignored) {
                    } catch (BreakException e) {
                        break;
                    }
                }
            } else {
                for (int idx = from; idx >= to; idx--) {
                    set(var, idx);
                    try {
                        visit(ctx.statement());
                    } catch (ContinueException ignored) {
                    } catch (BreakException e) {
                        break;
                    }
                }
            }
        } finally {
            popScope();
        }
        return null;
    }

    @Override
    public Object visitCaseStatement(DelphiParser.CaseStatementContext ctx) {
        Object valueObj = visit(ctx.expression());
        for (var elementCtx : ctx.caseListElement()) {
            for (var constant : elementCtx.constList().constant()) {
                if (eq(valueObj, evalConstant(constant))) {
                    visit(elementCtx.statement());
                    return null;
                }
            }
        }
        if (ctx.ELSE() != null && ctx.statements() != null) visit(ctx.statements());
        return null;
    }

    @Override
    public Object visitWithStatement(DelphiParser.WithStatementContext ctx) {
        Object obj = resolveVar(ctx.recordVariableList().variable(0));
        if (obj instanceof DelphiObject) {
            selfStack.push((DelphiObject) obj);
            visit(ctx.statement());
            selfStack.pop();
        } else {
            visit(ctx.statement());
        }
        return null;
    }

    @Override
    public Object visitExpression(DelphiParser.ExpressionContext ctx) {
        Object leftExprObj = visit(ctx.simpleExpression());
        if (ctx.relationaloperator() == null) return leftExprObj;
        Object rightExprObj = visit(ctx.expression());

        var op = ctx.relationaloperator();
        if (op.EQUAL() != null) return eq(leftExprObj, rightExprObj);
        if (op.NOT_EQUAL() != null) return !eq(leftExprObj, rightExprObj);
        double l = toDouble(leftExprObj), r = toDouble(rightExprObj);

        if (op.LT() != null) return l < r;
        if (op.LE() != null) return l <= r;
        if (op.GT() != null) return l > r;
        if (op.GE() != null) return l >= r;
        if (op.IN() != null) {
            Object rr = visit(ctx.expression());
            return rr instanceof Set && ((Set<?>) rr).contains(leftExprObj);
        }
        return false;
    }

    @Override
    public Object visitSimpleExpression(DelphiParser.SimpleExpressionContext ctx) {
        Object leftTermObj = visit(ctx.term());
        if (ctx.additiveoperator() == null) return leftTermObj;
        Object rightTermObj = visit(ctx.simpleExpression());

        var op = ctx.additiveoperator();
        if (op.PLUS() != null) return add(leftTermObj, rightTermObj);
        if (op.MINUS() != null) return sub(leftTermObj, rightTermObj);
        if (op.OR() != null) return isTruth(leftTermObj) || isTruth(rightTermObj);
        return leftTermObj;
    }

    @Override
    public Object visitTerm(DelphiParser.TermContext ctx) {
        Object leftTermObj = visit(ctx.signedFactor());
        if (ctx.multiplicativeoperator() == null) return leftTermObj;
        Object rightTermObj = visit(ctx.term());

        var op = ctx.multiplicativeoperator();
        if (op.STAR() != null) return mul(leftTermObj, rightTermObj);
        if (op.SLASH() != null) return toDouble(leftTermObj) / toDouble(rightTermObj);
        if (op.DIV() != null) return toInt(leftTermObj) / toInt(rightTermObj);
        if (op.MOD() != null) return toInt(leftTermObj) % toInt(rightTermObj);
        if (op.AND() != null) return isTruth(leftTermObj) && isTruth(rightTermObj);
        return leftTermObj;
    }

    @Override
    public Object visitSignedFactor(DelphiParser.SignedFactorContext ctx) {
        Object visitedObj = visit(ctx.factor());
        return ctx.MINUS() != null ? negate(visitedObj) : visitedObj;
    }

    @Override
    public Object visitFactor(DelphiParser.FactorContext ctx) {
        if (ctx.functionDesignator() != null) return visit(ctx.functionDesignator());
        if (ctx.variable() != null) return resolveVar(ctx.variable());
        if (ctx.expression() != null) return visit(ctx.expression());
        if (ctx.unsignedConstant() != null) return visitUnsignedConstant(ctx.unsignedConstant());
        if (ctx.NOT() != null) return !isTruth(visit(ctx.factor()));
        if (ctx.bool_() != null) return ctx.bool_().TRUE() != null;
        return null;
    }

    @Override
    public Object visitUnsignedConstant(DelphiParser.UnsignedConstantContext ctx) {
        if (ctx.unsignedNumber() != null) return evalUnsignedNumber(ctx.unsignedNumber());
        if (ctx.string() != null) return stripQuotes(ctx.string().getText());
        if (ctx.constantChr() != null) return evalChr(ctx.constantChr());
        return null;
    }

    @Override
    public Object visitFunctionDesignator(DelphiParser.FunctionDesignatorContext ctx) {
        List<Object> args = new ArrayList<>();
        if (ctx.parameterList() != null)
            for (var ap : ctx.parameterList().actualParameter())
                args.add(visit(ap.expression()));

        List<String> chain = varChain(ctx.variable());
        if (chain.isEmpty()) return null;

        if (chain.size() >= 2) {
            String lastPart = chain.getLast();

            if (chain.size() == 2) {
                ClassDescriptor cd = classes.get(chain.getFirst());
                if (cd != null) return construct(cd, lastPart, args);
            }

            Object obj = resolveChainForMethodCall(chain);
            if (obj instanceof DelphiObject) {
                return callMethod((DelphiObject) obj, lastPart, args);
            }
        }

        String name = chain.getFirst().toLowerCase();
        Object builtinObj = builtinFunc(name, args);
        if (builtinObj != null) return builtinObj;

        MethodDescriptor methodDescriptor = globals.get(name);
        if (methodDescriptor != null) return callGlobal(methodDescriptor, args);
        return null;
    }

    // Constant propagation

    public void setConstantPropagationLog(boolean enabled) {
        this.constantPropagationLog = enabled;
    }

    public Object tryConstantFold(DelphiParser.ExpressionContext ctx) {
        return foldExpression(ctx);
    }

    public String printAST(DelphiParser.AssignmentStatementContext ctx) {
        String varName = ctx.variable().getText();
        Object folded = tryConstantFold(ctx.expression());
        if (folded != null) return varName + " = " + folded;
        return varName + " = " + ctx.expression().getText();
    }

    // Private methods

    private Scanner getStdin() {
        if (stdin == null) {
            stdin = new Scanner(System.in);
        }
        return stdin;
    }

    private void pushScope() {
        localScopes.push(new LinkedHashMap<>());
    }

    private void popScope() {
        localScopes.pop();
    }

    private void set(String name, Object value) {
        String key = name.toLowerCase();
        for (Map<String, Object> scope : localScopes) {
            if (scope.containsKey(key)) {
                scope.put(key, value);
                syncResultAlias(key, value);
                if (globalScope.containsKey(key)) {
                    globalScope.put(key, value);
                }
                return;
            }
        }
        if (!selfStack.isEmpty()) {
            DelphiObject self = selfStack.peek();
            if (self.fields.containsKey(key)) {
                self.fields.put(key, value);
                return;
            }
        }
        if (globalScope.containsKey(key)) {
            globalScope.put(key, value);
            for (Map<String, Object> s : localScopes) {
                if (s.containsKey(key)) {
                    s.put(key, value);
                    break;
                }
            }
            return;
        }
        if (!localScopes.isEmpty()) {
            localScopes.peek().put(key, value);
        } else {
            globalScope.put(key, value);
        }
    }

    private void syncResultAlias(String keyJustSet, Object value) {
        if (currentFunctionReturnVar == null) return;
        Map<String, Object> peekMap = localScopes.peek();
        if (peekMap == null) return;
        if (keyJustSet.equals("result") && peekMap.containsKey(currentFunctionReturnVar)) {
            peekMap.put(currentFunctionReturnVar, value);
        } else if (keyJustSet.equals(currentFunctionReturnVar) && peekMap.containsKey("result")) {
            peekMap.put("result", value);
        }
    }

    private Object get(String name) {
        String nameLC = name.toLowerCase();
        for (Map<String, Object> scope : localScopes) {
            if (scope.containsKey(nameLC)) return scope.get(nameLC);
        }
        if (!selfStack.isEmpty()) {
            DelphiObject self = selfStack.peek();
            if (self.fields.containsKey(nameLC)) return self.fields.get(nameLC);
        }
        if (globalScope.containsKey(nameLC)) return globalScope.get(nameLC);
        return null;
    }

    private void declare(String name, Object value) {
        String key = name.toLowerCase();
        if (inGlobalBlock && localScopes.size() == 1) {
            globalScope.put(key, value);
            if (Objects.nonNull(localScopes.peek())) {
                localScopes.peek().put(key, value);
            }
        } else if (localScopes.isEmpty()) {
            globalScope.put(key, value);
        } else {
            localScopes.peek().put(key, value);
        }
    }

    private void syncGlobalsToLocalMirrors() {
        for (Map<String, Object> scope : localScopes) {
            for (String key : scope.keySet()) {
                if (globalScope.containsKey(key)) {
                    scope.put(key, globalScope.get(key));
                }
            }
        }
    }

    private Object resolveChainForMethodCall(List<String> chain) {
        Object cur = get(chain.getFirst());
        for (int idx = 1; idx < chain.size() - 1; idx++) {
            cur = resolveOneStep(cur, chain.get(idx));
        }
        return cur;
    }

    private void registerClass(String name, DelphiParser.ClassTypeContext ctx) {
        ClassDescriptor classDescriptor = new ClassDescriptor(name);
        if (ctx.ancestorList() != null && !ctx.ancestorList().identifier().isEmpty())
            classDescriptor.parentName = ctx.ancestorList().identifier(0).getText();

        String visibility = "PUBLIC";
        for (var sec : ctx.classBody().classMemberSection()) {
            if (sec.visibilitySection() != null) {
                visibility = sec.visibilitySection().visibilitySpecifier().getText().toUpperCase();
                for (var member : sec.visibilitySection().classMemberDecl())
                    applyMember(classDescriptor, member, visibility);
            } else if (sec.classMemberDecl() != null) {
                applyMember(classDescriptor, sec.classMemberDecl(), visibility);
            }
        }
        classes.put(name.toLowerCase(), classDescriptor);
    }

    private void applyMember(ClassDescriptor cd, DelphiParser.ClassMemberDeclContext mem, String vis) {
        if (mem.fieldDecl() != null) {
            String typeStr = mem.fieldDecl().type_().getText();
            for (var id : mem.fieldDecl().identifierList().identifier()) {
                cd.fieldTypes.put(id.getText().toLowerCase(), typeStr);
                cd.fieldVis.put(id.getText().toLowerCase(), vis);
            }
        } else if (mem.methodDecl() != null) {
            var md = mem.methodDecl();
            String memberName = md.identifier().getText().toLowerCase();
            MethodDescriptor methodDescriptor = new MethodDescriptor(memberName);
            methodDescriptor.isFunction = md.FUNCTION() != null;
            if (methodDescriptor.isFunction && md.resultType() != null)
                methodDescriptor.returnType = md.resultType().getText();
            if (md.formalParameterList() != null) extractParams(methodDescriptor, md.formalParameterList());
            cd.methods.put(memberName, methodDescriptor);
        } else if (mem.constructorDecl() != null) {
            String memberName = mem.constructorDecl().identifier().getText().toLowerCase();
            MethodDescriptor methodDescriptor = new MethodDescriptor(memberName);
            if (mem.constructorDecl().formalParameterList() != null)
                extractParams(methodDescriptor, mem.constructorDecl().formalParameterList());
            cd.methods.put(memberName, methodDescriptor);
        } else if (mem.destructorDecl() != null) {
            String memberName = mem.destructorDecl().identifier().getText().toLowerCase();
            cd.methods.put(memberName, new MethodDescriptor(memberName));
        }
    }

    private void registerInterface(String name, DelphiParser.InterfaceTypeContext ctx) {
        Set<String> methods = new LinkedHashSet<>();
        for (var mem : ctx.interfaceBody().interfaceMemberDecl())
            methods.add(mem.identifier().getText().toLowerCase());
        interfaces.put(name.toLowerCase(), methods);
    }

    private void collectDeclaration(DelphiParser.ProcedureAndFunctionDeclarationPartContext ctx) {
        if (ctx.procedureDeclaration() != null) {
            var pd = ctx.procedureDeclaration();
            String full = pd.methodIdentifier().getText().toLowerCase();
            MethodDescriptor methodDescriptor = new MethodDescriptor(full);
            methodDescriptor.procedureCtx = pd;
            if (pd.formalParameterList() != null) extractParams(methodDescriptor, pd.formalParameterList());
            globals.put(full, methodDescriptor);
            attachToClass(full, methodDescriptor);
        } else if (ctx.functionDeclaration() != null) {
            var fd = ctx.functionDeclaration();
            String full = fd.methodIdentifier().getText().toLowerCase();
            MethodDescriptor methodDescriptor = new MethodDescriptor(full);
            methodDescriptor.isFunction = true;
            methodDescriptor.returnType = fd.resultType().getText();
            methodDescriptor.functionCtx = fd;
            if (fd.formalParameterList() != null) extractParams(methodDescriptor, fd.formalParameterList());
            globals.put(full, methodDescriptor);
            attachToClass(full, methodDescriptor);
        } else if (ctx.constructorImplementation() != null) {
            var ci = ctx.constructorImplementation();
            String full = ci.methodIdentifier().getText().toLowerCase();
            MethodDescriptor methodDescriptor = new MethodDescriptor(full);
            methodDescriptor.constructorCtx = ci;
            if (ci.formalParameterList() != null) extractParams(methodDescriptor, ci.formalParameterList());
            globals.put(full, methodDescriptor);
            attachToClass(full, methodDescriptor);
        } else if (ctx.destructorImplementation() != null) {
            var di = ctx.destructorImplementation();
            String full = di.methodIdentifier().getText().toLowerCase();
            MethodDescriptor methodDescriptor = new MethodDescriptor(full);
            methodDescriptor.destructorCtx = di;
            if (di.formalParameterList() != null) extractParams(methodDescriptor, di.formalParameterList());
            globals.put(full, methodDescriptor);
            attachToClass(full, methodDescriptor);
        }
    }

    private void attachToClass(String full, MethodDescriptor m) {
        String[] parts = full.split("\\.");
        if (parts.length == 2) {
            ClassDescriptor cd = classes.get(parts[0]);
            if (cd != null) cd.methods.put(parts[1], m);
        }
    }

    private void extractParams(MethodDescriptor m, DelphiParser.FormalParameterListContext fpl) {
        for (var sec : fpl.formalParameterSection()) {
            boolean isVar = sec.VAR() != null;
            if (sec.parameterGroup() != null) {
                String typeStr = sec.parameterGroup().typeIdentifier().getText();
                for (var id : sec.parameterGroup().identifierList().identifier()) {
                    m.paramNames.add(id.getText().toLowerCase());
                    m.paramTypes.add(typeStr);
                    m.paramIsVar.add(isVar);
                }
            }
        }
    }


    private Object evalConstant(DelphiParser.ConstantContext ctx) {
        if (ctx.unsignedNumber() != null) {
            Object numVal = evalUnsignedNumber(ctx.unsignedNumber());
            if (ctx.sign() != null && ctx.sign().MINUS() != null) return negate(numVal);
            return numVal;
        }
        if (ctx.string() != null) return stripQuotes(ctx.string().getText());
        if (ctx.constantChr() != null) return evalChr(ctx.constantChr());
        if (ctx.identifier() != null) {
            Object v = get(ctx.identifier().getText());
            if (v == null) v = constants.get(ctx.identifier().getText().toLowerCase());
            return (ctx.sign() != null && ctx.sign().MINUS() != null) ? negate(v) : v;
        }
        return null;
    }

    private Object evalUnsignedNumber(DelphiParser.UnsignedNumberContext ctx) {
        if (ctx.unsignedInteger() != null) return Integer.parseInt(ctx.unsignedInteger().getText());
        return Double.parseDouble(ctx.unsignedReal().getText());
    }

    private Object evalChr(DelphiParser.ConstantChrContext ctx) {
        return String.valueOf((char) Integer.parseInt(ctx.unsignedInteger().getText()));
    }

    private void doAssign(DelphiParser.VariableContext varCtx, Object value) {
        List<String> chain = varChain(varCtx);
        if (chain.isEmpty()) return;
        if (chain.size() == 1) {
            set(chain.getFirst(), value);
            return;
        }

        Object cur = get(chain.getFirst());
        for (int idx = 1; idx < chain.size() - 1; idx++) {
            if (cur instanceof DelphiObject) cur = ((DelphiObject) cur).fields.get(chain.get(idx));
            else cur = null;
        }
        String last = chain.getLast();
        if (cur instanceof DelphiObject) ((DelphiObject) cur).fields.put(last, value);
    }

    private List<String> varChain(DelphiParser.VariableContext varCtx) {
        List<String> chain = new ArrayList<>();
        int idIdx = 0;
        List<DelphiParser.IdentifierContext> ids = varCtx.identifier();
        if (ids.isEmpty()) return chain;
        chain.add(ids.get(idIdx++).getText().toLowerCase());
        for (int idx = 0; idx < varCtx.getChildCount(); idx++) {
            var child = varCtx.getChild(idx);
            if (child instanceof TerminalNode) {
                int type = ((TerminalNode) child).getSymbol().getType();
                if (type == DelphiParser.DOT && idIdx < ids.size()) {
                    chain.add(ids.get(idIdx++).getText().toLowerCase());
                }
            }
        }
        return chain;
    }

    private boolean handleBuiltin(String name, List<Object> args, DelphiParser.ProcedureStatementContext ctx) {
        return switch (name) {
            case "writeln" -> {
                System.out.println(joinArgs(args));
                yield true;
            }
            case "write" -> {
                System.out.print(joinArgs(args));
                yield true;
            }
            case "readln", "read" -> {
                if (ctx != null && ctx.parameterList() != null) {
                    String line = getStdin().hasNextLine() ? getStdin().nextLine().trim() : "0";
                    for (var ap : ctx.parameterList().actualParameter()) {
                        DelphiParser.ExpressionContext expr = ap.expression();
                        if (expr.simpleExpression() != null
                                && expr.simpleExpression().term() != null
                                && expr.simpleExpression().term().signedFactor() != null
                                && expr.simpleExpression().term().signedFactor().factor() != null
                                && expr.simpleExpression().term().signedFactor().factor().variable() != null) {
                            DelphiParser.VariableContext vc =
                                    expr.simpleExpression().term().signedFactor().factor().variable();
                            Object parsed;
                            try {
                                parsed = Integer.parseInt(line);
                            } catch (NumberFormatException e) {
                                try {
                                    parsed = Double.parseDouble(line);
                                } catch (NumberFormatException e2) {
                                    parsed = line;
                                }
                            }
                            doAssign(vc, parsed);
                        }
                    }
                }
                yield true;
            }
            case "inc" -> {
                if (!args.isEmpty() && ctx != null && ctx.parameterList() != null) {
                    var ap = ctx.parameterList().actualParameter(0);
                    DelphiParser.ExpressionContext expr = ap.expression();
                    if (expr.simpleExpression() != null
                            && expr.simpleExpression().term() != null
                            && expr.simpleExpression().term().signedFactor() != null
                            && expr.simpleExpression().term().signedFactor().factor() != null
                            && expr.simpleExpression().term().signedFactor().factor().variable() != null) {
                        DelphiParser.VariableContext vc =
                                expr.simpleExpression().term().signedFactor().factor().variable();
                        int increment = args.size() > 1 ? toInt(args.get(1)) : 1;
                        doAssign(vc, toInt(args.getFirst()) + increment);
                    }
                }
                yield true;
            }
            case "dec" -> {
                if (!args.isEmpty() && ctx != null && ctx.parameterList() != null) {
                    var ap = ctx.parameterList().actualParameter(0);
                    DelphiParser.ExpressionContext expr = ap.expression();
                    if (expr.simpleExpression() != null
                            && expr.simpleExpression().term() != null
                            && expr.simpleExpression().term().signedFactor() != null
                            && expr.simpleExpression().term().signedFactor().factor() != null
                            && expr.simpleExpression().term().signedFactor().factor().variable() != null) {
                        DelphiParser.VariableContext vc =
                                expr.simpleExpression().term().signedFactor().factor().variable();
                        int decrement = args.size() > 1 ? toInt(args.get(1)) : 1;
                        doAssign(vc, toInt(args.getFirst()) - decrement);
                    }
                }
                yield true;
            }
            default -> false;
        };
    }

    private String joinArgs(List<Object> args) {
        StringBuilder sb = new StringBuilder();
        for (Object obj : args) sb.append(format(obj));
        return sb.toString();
    }

    private String format(Object obj) {
        if (obj == null) return "nil";
        if (obj instanceof Double) {
            double d = (Double) obj;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return obj.toString();
    }

    private Object builtinFunc(String name, List<Object> args) {
        Object a0 = args.isEmpty() ? null : args.getFirst();
        return switch (name) {
            case "ord" -> a0 == null ? 0 : toInt(a0);
            case "chr" -> a0 == null ? "" : String.valueOf((char) toInt(a0));
            case "succ" -> a0 == null ? 0 : toInt(a0) + 1;
            case "pred" -> a0 == null ? 0 : toInt(a0) - 1;
            case "abs" -> a0 == null ? 0 : Math.abs(toDouble(a0));
            case "sqrt" -> a0 == null ? 0.0 : Math.sqrt(toDouble(a0));
            case "trunc" -> a0 == null ? 0 : (int) toDouble(a0);
            case "round" -> a0 == null ? 0 : (int) Math.round(toDouble(a0));
            case "length" -> a0 == null ? 0 : a0.toString().length();
            case "inttostr" -> a0 == null ? "" : String.valueOf(toInt(a0));
            case "strtoint" -> a0 == null ? 0 : Integer.parseInt(a0.toString().trim());
            case "odd" -> a0 != null && (toInt(a0) % 2 != 0);
            case "sqr" -> a0 == null ? 0 : toInt(a0) * toInt(a0);
            case "readln", "read" -> getStdin().hasNextLine() ? getStdin().nextLine() : "";
            default -> null;
        };
    }

    private Object resolveVar(DelphiParser.VariableContext varCtx) {
        List<String> chain = varChain(varCtx);
        if (chain.isEmpty()) return null;
        Object cur = get(chain.getFirst());
        for (int idx = 1; idx < chain.size(); idx++) {
            cur = resolveOneStep(cur, chain.get(idx));
        }
        return cur;
    }

    private Object resolveOneStep(Object cur, String member) {
        if (cur instanceof DelphiObject obj) {
            if (obj.fields.containsKey(member)) {
                return obj.fields.get(member);
            }
            MethodDescriptor methodDescriptor = obj.clazz.lookupMethod(member);
            if (methodDescriptor != null) {
                return callMethod(obj, member, new ArrayList<>());
            }
        }
        return null;
    }

    private DelphiObject construct(ClassDescriptor cd, String constructorName, List<Object> args) {
        wireParent(cd);
        if (cd.parentName != null) {
            Set<String> required = interfaces.get(cd.parentName.toLowerCase());
            if (required != null) checkImplements(cd, cd.parentName);
        }

        DelphiObject delphiObj = new DelphiObject(cd);
        MethodDescriptor constructor = cd.lookupMethod(constructorName);
        if (constructor != null && constructor.constructorCtx == null) {
            String key = cd.name.toLowerCase() + "." + constructorName.toLowerCase();
            MethodDescriptor g = globals.get(key);
            if (g != null && g.constructorCtx != null) constructor = g;
        }
        if (constructor != null && constructor.constructorCtx != null) {
            selfStack.push(delphiObj);
            pushScope();
            bindArgs(constructor, args);
            try {
                visit(constructor.constructorCtx.block());
            } catch (ReturnException ignored) {
            } finally {
                popScope();
                selfStack.pop();
            }
        }
        return delphiObj;
    }

    private void wireParent(ClassDescriptor cd) {
        if (cd.parentName == null || cd.parent != null) return;
        ClassDescriptor p = classes.get(cd.parentName.toLowerCase());
        if (p != null) {
            wireParent(p);
            cd.parent = p;
            for (var e : p.methods.entrySet())
                cd.methods.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    private Object callMethod(DelphiObject obj, String methodName, List<Object> args) {
        MethodDescriptor methodDescriptor = obj.clazz.lookupMethod(methodName);
        if (methodDescriptor == null) return null;
        boolean savedInGlobal = inGlobalBlock;
        inGlobalBlock = false;
        selfStack.push(obj);
        pushScope();
        declare("self", obj);
        bindArgs(methodDescriptor, args);
        Object result = null;
        String savedReturnVar = currentFunctionReturnVar;
        try {
            if (methodDescriptor.constructorCtx != null) visit(methodDescriptor.constructorCtx.block());
            else if (methodDescriptor.destructorCtx != null) visit(methodDescriptor.destructorCtx.block());
            else if (methodDescriptor.procedureCtx != null) visit(methodDescriptor.procedureCtx.block());
            else if (methodDescriptor.functionCtx != null) {
                String rv = simpleName(methodDescriptor.functionCtx.methodIdentifier().getText());
                Object defaultRet = DelphiObject.defaultVal(methodDescriptor.returnType);
                declare(rv, defaultRet);
                declare("result", defaultRet);
                currentFunctionReturnVar = rv;
                visit(methodDescriptor.functionCtx.block());
                result = get(rv);
            }
        } catch (ReturnException re) {
            result = re.value;
        } finally {
            currentFunctionReturnVar = savedReturnVar;
            inGlobalBlock = savedInGlobal;
            popScope();
            selfStack.pop();
        }
        return result;
    }

    private Object callGlobal(MethodDescriptor m, List<Object> args) {
        Deque<Map<String, Object>> savedLocalScopes = new ArrayDeque<>(localScopes);
        localScopes.clear();

        boolean savedInGlobal = inGlobalBlock;
        inGlobalBlock = false;

        pushScope();
        bindArgs(m, args);

        for (Map.Entry<String, Object> entry : globalScope.entrySet()) {
            if (Objects.nonNull(localScopes.peek()) && !localScopes.peek().containsKey(entry.getKey())) {
                localScopes.peek().put(entry.getKey(), entry.getValue());
            }
        }

        Object result = null;
        String savedReturnVar = currentFunctionReturnVar;
        try {
            if (m.procedureCtx != null) visit(m.procedureCtx.block());
            else if (m.constructorCtx != null) visit(m.constructorCtx.block());
            else if (m.destructorCtx != null) visit(m.destructorCtx.block());
            else if (m.functionCtx != null) {
                String rv = simpleName(m.functionCtx.methodIdentifier().getText());
                Object defaultRet = DelphiObject.defaultVal(m.returnType);
                declare(rv, defaultRet);
                declare("result", defaultRet);
                currentFunctionReturnVar = rv;
                visit(m.functionCtx.block());
                result = get(rv);
            }
        } catch (ReturnException re) {
            result = re.value;
        } finally {
            currentFunctionReturnVar = savedReturnVar;
            inGlobalBlock = savedInGlobal;
            popScope();
            localScopes.clear();
            for (var s : savedLocalScopes) localScopes.addLast(s);
            syncGlobalsToLocalMirrors();
        }
        return result;
    }

    private void bindArgs(MethodDescriptor md, List<Object> args) {
        for (int idx = 0; idx < md.paramNames.size() && idx < args.size(); idx++)
            declare(md.paramNames.get(idx), args.get(idx));
    }

    private String simpleName(String full) {
        int dot = full.lastIndexOf('.');
        return (dot >= 0 ? full.substring(dot + 1) : full).toLowerCase();
    }

    private Object foldExpression(DelphiParser.ExpressionContext ctx) {
        Object left = foldSimpleExpression(ctx.simpleExpression());
        if (left == null) return null;
        if (ctx.relationaloperator() == null) return left;
        Object right = foldExpression(ctx.expression());
        if (right == null) return null;

        var op = ctx.relationaloperator();
        if (op.EQUAL() != null) return eq(left, right);
        if (op.NOT_EQUAL() != null) return !eq(left, right);
        double l = toDouble(left), r = toDouble(right);
        if (op.LT() != null) return l < r;
        if (op.LE() != null) return l <= r;
        if (op.GT() != null) return l > r;
        if (op.GE() != null) return l >= r;
        return null;
    }

    private Object foldSimpleExpression(DelphiParser.SimpleExpressionContext ctx) {
        Object left = foldTerm(ctx.term());
        if (left == null) return null;
        if (ctx.additiveoperator() == null) return left;
        Object right = foldSimpleExpression(ctx.simpleExpression());
        if (right == null) return null;

        var op = ctx.additiveoperator();
        if (op.PLUS() != null) return add(left, right);
        if (op.MINUS() != null) return sub(left, right);
        return null;
    }

    private Object foldTerm(DelphiParser.TermContext ctx) {
        Object left = foldSignedFactor(ctx.signedFactor());
        if (left == null) return null;
        if (ctx.multiplicativeoperator() == null) return left;
        Object right = foldTerm(ctx.term());
        if (right == null) return null;

        var op = ctx.multiplicativeoperator();
        if (op.STAR() != null) return mul(left, right);
        if (op.SLASH() != null) {
            double d = toDouble(right);
            return d == 0 ? null : toDouble(left) / d;
        }
        if (op.DIV() != null) {
            int val = toInt(right);
            return val == 0 ? null : toInt(left) / val;
        }
        if (op.MOD() != null) {
            int val = toInt(right);
            return val == 0 ? null : toInt(left) % val;
        }
        return null;
    }

    private Object foldSignedFactor(DelphiParser.SignedFactorContext ctx) {
        Object val = foldFactor(ctx.factor());
        if (val == null) return null;
        return ctx.MINUS() != null ? negate(val) : val;
    }

    private Object foldFactor(DelphiParser.FactorContext ctx) {
        if (ctx.unsignedConstant() != null) {
            if (ctx.unsignedConstant().unsignedNumber() != null)
                return evalUnsignedNumber(ctx.unsignedConstant().unsignedNumber());
            if (ctx.unsignedConstant().string() != null)
                return stripQuotes(ctx.unsignedConstant().string().getText());
            return null;
        }
        if (ctx.expression() != null) return foldExpression(ctx.expression());
        if (ctx.bool_() != null) return ctx.bool_().TRUE() != null;
        if (ctx.variable() != null && ctx.functionDesignator() == null) {
            List<String> chain = varChain(ctx.variable());
            if (chain.size() == 1) {
                return constants.get(chain.getFirst());
            }
        }
        return null;
    }

    private Object add(Object lObj, Object rObj) {
        if (lObj instanceof String || rObj instanceof String) return format(lObj) + format(rObj);
        if (lObj instanceof Double || rObj instanceof Double) return toDouble(lObj) + toDouble(rObj);
        return toInt(lObj) + toInt(rObj);
    }

    private Object sub(Object lObj, Object rObj) {
        if (lObj instanceof Double || rObj instanceof Double) return toDouble(lObj) - toDouble(rObj);
        return toInt(lObj) - toInt(rObj);
    }

    private Object mul(Object lObj, Object rObj) {
        if (lObj instanceof Double || rObj instanceof Double) return toDouble(lObj) * toDouble(rObj);
        return toInt(lObj) * toInt(rObj);
    }

    private Object negate(Object obj) {
        if (obj instanceof Double) return -(Double) obj;
        if (obj instanceof Integer) return -(Integer) obj;
        return obj;
    }

    private boolean isTruth(Object obj) {
        return switch (obj) {
            case null -> false;
            case Boolean b -> b;
            case Integer i -> i != 0;
            case Double aDouble -> aDouble != 0.0;
            case String s -> !s.isEmpty();
            default -> true;
        };
    }

    private boolean eq(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        if (obj1 instanceof Number && obj2 instanceof Number) return toDouble(obj1) == toDouble(obj2);
        return obj1.equals(obj2);
    }

    private int toInt(Object obj) {
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Double) return ((Double) obj).intValue();
        if (obj instanceof Boolean) return (Boolean) obj ? 1 : 0;
        if (obj instanceof String) {
            try {
                return Integer.parseInt(obj.toString().trim());
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private double toDouble(Object obj) {
        if (obj instanceof Double) return (Double) obj;
        if (obj instanceof Integer) return ((Integer) obj).doubleValue();
        if (obj instanceof Boolean) return (Boolean) obj ? 1.0 : 0.0;
        if (obj instanceof String) {
            try {
                return Double.parseDouble(obj.toString().trim());
            } catch (Exception e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String stripQuotes(String str) {
        if (str != null && str.length() >= 2 && str.startsWith("'") && str.endsWith("'"))
            return str.substring(1, str.length() - 1).replace("''", "'");
        return str;
    }

    private void checkImplements(ClassDescriptor classDescriptor, String interfaceName) {
        Set<String> required = interfaces.get(interfaceName.toLowerCase());
        if (required == null) return;
        for (String method : required) {
            if (classDescriptor.lookupMethod(method) == null)
                throw new RuntimeException(
                        classDescriptor.name + " does not implement interface method: " + method);
        }
    }

    static class DelphiObject {
        ClassDescriptor clazz;
        Map<String, Object> fields = new LinkedHashMap<>();

        DelphiObject(ClassDescriptor classDescriptor) {
            this.clazz = classDescriptor;
            for (ClassDescriptor cd : classDescriptor.lineage()) {
                for (Map.Entry<String, String> entry : cd.fieldTypes.entrySet()) {
                    fields.put(entry.getKey(), defaultVal(entry.getValue()));
                }
            }
        }

        static Object defaultVal(String type) {
            if (type == null) return null;
            return switch (type.toUpperCase()) {
                case "INTEGER" -> 0;
                case "REAL" -> 0.0;
                case "BOOLEAN" -> false;
                case "STRING" -> "";
                default -> null;
            };
        }
    }

    static class ClassDescriptor {
        String name;
        String parentName;
        ClassDescriptor parent;
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        Map<String, String> fieldVis = new LinkedHashMap<>();
        Map<String, MethodDescriptor> methods = new LinkedHashMap<>();

        ClassDescriptor(String name) {
            this.name = name;
        }

        List<ClassDescriptor> lineage() {
            LinkedList<ClassDescriptor> list = new LinkedList<>();
            ClassDescriptor c = this;
            while (c != null) {
                list.addFirst(c);
                c = c.parent;
            }
            return list;
        }

        MethodDescriptor lookupMethod(String name) {
            ClassDescriptor c = this;
            while (c != null) {
                MethodDescriptor methodDescriptor = c.methods.get(name.toLowerCase());
                if (methodDescriptor != null) return methodDescriptor;
                c = c.parent;
            }
            return null;
        }
    }

    static class MethodDescriptor {
        String name;
        boolean isFunction;
        String returnType;
        List<String> paramNames = new ArrayList<>();
        List<String> paramTypes = new ArrayList<>();
        List<Boolean> paramIsVar = new ArrayList<>();
        DelphiParser.ConstructorImplementationContext constructorCtx;
        DelphiParser.DestructorImplementationContext destructorCtx;
        DelphiParser.FunctionDeclarationContext functionCtx;
        DelphiParser.ProcedureDeclarationContext procedureCtx;

        MethodDescriptor(String name) {
            this.name = name;
        }
    }

    static class ReturnException extends RuntimeException {
        Object value;

        ReturnException(Object v) {
            super(null, null, true, false);
            this.value = v;
        }
    }

    static class BreakException extends RuntimeException {
        BreakException() {
            super(null, null, true, false);
        }
    }

    static class ContinueException extends RuntimeException {
        ContinueException() {
            super(null, null, true, false);
        }
    }
}
