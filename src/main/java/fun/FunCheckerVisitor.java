
package fun;

import ast.FunParser;
import ast.FunVisitor;
import fun.types.Mapping;
import fun.types.Pair;
import fun.types.Primitive;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

import java.util.List;

/**
 * A visitor for contextual analysis of Fun.
 * Based on a previous version developed by
 * David Watt and Simon Gay (University of Glasgow).
 */
public class FunCheckerVisitor extends AbstractParseTreeVisitor<Type> implements FunVisitor<Type> {

    // Contextual errors

    private static final Mapping
            NOT_TYPE = new Mapping(Type.BOOL, Type.BOOL),
            COMP_TYPE = new Mapping(new Pair(Type.INT, Type.INT), Type.BOOL),
            ARITH_TYPE = new Mapping(new Pair(Type.INT, Type.INT), Type.INT),
            MAIN_TYPE = new Mapping(Type.VOID, Type.VOID);
    // Constructor
    private final CommonTokenStream tokens;
    private final SymbolTable<Type> typeTable = new SymbolTable<>();
    private int errorCount = 0;

    public FunCheckerVisitor(CommonTokenStream tokens) {
        this.tokens = tokens;
    }


    // Scope checking
    private void reportError(String message, ParserRuleContext ctx) {
        // Print an error message relating to the given
        // part of the AST.
        Interval interval = ctx.getSourceInterval();
        Token start = this.tokens.get(interval.a);
        Token finish = this.tokens.get(interval.b);

        int startLine = start.getLine();
        int startCol = start.getCharPositionInLine();
        int finishLine = finish.getLine();
        int finishCol = finish.getCharPositionInLine();

        System.err.println(startLine + ":" + startCol + "-" + finishLine + ":" + finishCol + " " + message);
        this.errorCount++;
    }

    public int getNumberOfContextualErrors() {
        // Return the total number of errors so far detected.
        return this.errorCount;
    }

    private void predefine() {
        // Add predefined procedures to the type table.
        this.typeTable.put("read", new Mapping(Type.VOID, Type.INT));
        this.typeTable.put("write", new Mapping(Type.INT, Type.VOID));
    }

    private void define(String id, Type type, ParserRuleContext decl) {
        // Add id with its type to the type table, checking
        // that id is not already declared in the same scope.
        boolean ok = this.typeTable.put(id, type);

        if (!ok) this.reportError(id + " is redeclared", decl);
    }

    // Type checking
    private Type retrieve(String id, ParserRuleContext occ) {
        // Retrieve id's type from the type table.
        Type type = this.typeTable.get(id);
        if (type == null) {
            this.reportError(id + " is undeclared", occ);
            return Type.ERROR;
        } else
            return type;
    }

    private void checkType(Type typeExpected, Type typeActual, ParserRuleContext construct) {
        // Check that a construct's actual type matches
        // the expected type.
        if (!typeActual.equiv(typeExpected))
            this.reportError("type is " + typeActual + ", should be " + typeExpected, construct);
    }

    private Type checkCall(String id, Type typeArg, ParserRuleContext call) {
        // Check that a procedure call identifies a procedure
        // and that its argument type matches the procedure's
        // type. Return the type of the procedure call.
        Type typeProc = this.retrieve(id, call);

        if (!(typeProc instanceof Mapping mapping)) {
            this.reportError(id + " is not a procedure", call);
            return Type.ERROR;
        } else {
            this.checkType(mapping.domain, typeArg, call);
            return mapping.range;
        }
    }

    private Type checkUnary(Mapping typeOp, Type typeArg, ParserRuleContext op) {
        // Check that a unary operator's operand type matches
        // the operator's type. Return the type of the operator application.
        if (!(typeOp.domain instanceof Primitive))
            this.reportError("unary operator should have 1 operand", op);
        else
            this.checkType(typeOp.domain, typeArg, op);
        return typeOp.range;
    }

    private Type checkBinary(Mapping typeOp, Type typeArg1, Type typeArg2, ParserRuleContext op) {
        // Check that a binary operator's operand types match
        // the operator's type. Return the type of the operator application.
        if (!(typeOp.domain instanceof Pair pair))
            this.reportError("binary operator should have 2 operands", op);
        else {
            this.checkType(pair.first, typeArg1, op);
            this.checkType(pair.second, typeArg2, op);
        }
        return typeOp.range;
    }

    /**
     * Visit a parse tree produced by the {@code prog}
     * labeled alternative in {@link FunParser#program}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitProg(FunParser.ProgContext ctx) {
        this.predefine();
        super.visitChildren(ctx);

        Type tMain = this.retrieve("main", ctx);
        this.checkType(MAIN_TYPE, tMain, ctx);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code proc}
     * labeled alternative in {@link FunParser#proc_decl}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitProc(FunParser.ProcContext ctx) {
        this.typeTable.enterLocalScope();
        Type t;
        FunParser.Formal_declContext fd = ctx.formal_decl();

        if (fd != null)
            t = visit(fd);
        else
            t = Type.VOID;

        Type procType = new Mapping(t, Type.VOID);
        this.define(ctx.ID().getText(), procType, ctx);

        List<FunParser.Var_declContext> var_decl = ctx.var_decl();

        for (FunParser.Var_declContext vd : var_decl) super.visit(vd);

        this.visit(ctx.seq_com());
        this.typeTable.exitLocalScope();
        this.define(ctx.ID().getText(), procType, ctx);

        return null;
    }

    /**
     * Visit a parse tree produced by the {@code func}
     * labeled alternative in {@link FunParser#proc_decl}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitFunc(FunParser.FuncContext ctx) {
        this.typeTable.enterLocalScope();
        Type t1 = super.visit(ctx.type());
        Type t2;
        FunParser.Formal_declContext fd = ctx.formal_decl();

        if (fd != null)
            t2 = super.visit(fd);
        else
            t2 = Type.VOID;

        Type functype = new Mapping(t2, t1);
        this.define(ctx.ID().getText(), functype, ctx);
        List<FunParser.Var_declContext> var_decl = ctx.var_decl();

        for (FunParser.Var_declContext vd : var_decl) super.visit(vd);

        super.visit(ctx.seq_com());
        Type returnType = super.visit(ctx.expr());
        this.checkType(t1, returnType, ctx);
        this.typeTable.exitLocalScope();
        this.define(ctx.ID().getText(), functype, ctx);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code formal}
     * labeled alternative in {@link FunParser#formal_decl}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitFormal(FunParser.FormalContext ctx) {
        FunParser.TypeContext tc = ctx.type();
        Type t;

        if (tc != null) {
            t = super.visit(tc);
            this.define(ctx.ID().getText(), t, ctx);
        } else
            t = Type.VOID;

        return t;
    }

    /**
     * Visit a parse tree produced by the {@code var}
     * labeled alternative in {@link FunParser#var_decl}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitVar(FunParser.VarContext ctx) {
        Type t1 = super.visit(ctx.type());
        Type t2 = super.visit(ctx.expr());

        this.define(ctx.ID().getText(), t1, ctx);
        this.checkType(t1, t2, ctx);

        return null;
    }

    /**
     * Visit a parse tree produced by the {@code bool}
     * labeled alternative in {@link FunParser#type}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitBool(FunParser.BoolContext ctx) {
        return Type.BOOL;
    }

    /**
     * Visit a parse tree produced by the {@code int}
     * labeled alternative in {@link FunParser#type}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitInt(FunParser.IntContext ctx) {
        return Type.INT;
    }

    /**
     * Visit a parse tree produced by the {@code assn}
     * labeled alternative in {@link FunParser#com}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitAssn(FunParser.AssnContext ctx) {
        Type tVar = this.retrieve(ctx.ID().getText(), ctx);
        Type t = super.visit(ctx.expr());
        this.checkType(tVar, t, ctx);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code proccall}
     * labeled alternative in {@link FunParser#com}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitProccall(FunParser.ProccallContext ctx) {
        Type t = super.visit(ctx.actual());
        Type tres = this.checkCall(ctx.ID().getText(), t, ctx);

        if (!tres.equiv(Type.VOID))
            this.reportError("procedure should be void", ctx);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code if}
     * labeled alternative in {@link FunParser#com}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitIf(FunParser.IfContext ctx) {
        Type t = super.visit(ctx.expr());
        super.visit(ctx.c1);

        if (ctx.c2 != null)
            super.visit(ctx.c2);
        this.checkType(Type.BOOL, t, ctx);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code while}
     * labeled alternative in {@link FunParser#com}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitWhile(FunParser.WhileContext ctx) {
        Type t = super.visit(ctx.expr());
        super.visit(ctx.seq_com());
        this.checkType(Type.BOOL, t, ctx);
        return null;
    }

    @Override
    public Type visitRepeat_until(FunParser.Repeat_untilContext ctx) {
        Type t = super.visit(ctx.sec_expr());
        super.visit(ctx.seq_com());
        this.checkType(Type.INT, t, ctx);
        return null;
    }

    @Override
    public Type visitSwitch(FunParser.SwitchContext ctx) {
        Type t = super.visit(ctx.expr());
        for (FunParser.Sw_caseContext sw_case : ctx.sw_case()) {
            Type type = super.visit(sw_case);
            System.out.println(type);
            this.checkType(t, type, ctx);
        }

        super.visit(ctx.sw_default());

        return null;

    }

    @Override
    public Type visitCase(FunParser.CaseContext ctx) {
        Type t = super.visit(ctx.expr());
        super.visit(ctx.seq_com());
        return t;
    }

    @Override
    public Type visitDefault(FunParser.DefaultContext ctx) {
        super.visit(ctx.seq_com());
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code seq}
     * labeled alternative in {@link FunParser#seq_com}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitSeq(FunParser.SeqContext ctx) {
        super.visitChildren(ctx);
        return null;
    }


    /**
     * Visit a parse tree produced by {@link FunParser#expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitExpr(FunParser.ExprContext ctx) {
        Type t1 = super.visit(ctx.e1);
        if (ctx.e2 != null) {
            Type t2 = super.visit(ctx.e2);
            return checkBinary(COMP_TYPE, t1, t2, ctx);
            // COMP_TYPE is INT x INT -> BOOL
            // checkBinary checks that t1 and t2 are INT and returns BOOL
            // If necessary it produces an error message.
        } else {
            return t1;
        }
    }

    /**
     * Visit a parse tree produced by {@link FunParser#sec_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public Type visitSec_expr(FunParser.Sec_exprContext ctx) {
        Type t1 = super.visit(ctx.e1);

        if (ctx.e2 != null) {
            Type t2 = super.visit(ctx.e2);
            return this.checkBinary(ARITH_TYPE, t1, t2, ctx);
        } else {
            return t1;
        }
    }

    /**
     * Visit a parse tree produced by the {@code false}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitFalse(FunParser.FalseContext ctx) {
        return Type.BOOL;
    }

    /**
     * Visit a parse tree produced by the {@code true}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitTrue(FunParser.TrueContext ctx) {
        return Type.BOOL;
    }

    /**
     * Visit a parse tree produced by the {@code num}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitNum(FunParser.NumContext ctx) {
        return Type.INT;
    }

    /**
     * Visit a parse tree produced by the {@code id}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitId(FunParser.IdContext ctx) {
        return this.retrieve(ctx.ID().getText(), ctx);
    }

    /**
     * Visit a parse tree produced by the {@code funccall}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitFunccall(FunParser.FunccallContext ctx) {
        Type t = super.visit(ctx.actual());
        Type tres = this.checkCall(ctx.ID().getText(), t, ctx);

        if (tres.equiv(Type.VOID))
            this.reportError("procedure should be non-void", ctx);
        return tres;
    }

    /**
     * Visit a parse tree produced by the {@code not}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitNot(FunParser.NotContext ctx) {
        Type t = super.visit(ctx.prim_expr());
        return this.checkUnary(NOT_TYPE, t, ctx);
    }

    /**
     * Visit a parse tree produced by the {@code parens}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitParens(FunParser.ParensContext ctx) {
        return super.visit(ctx.expr());
    }

    /**
     * Visit a parse tree produced by {@link FunParser#actual}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Type visitActual(FunParser.ActualContext ctx) {
        FunParser.ExprContext ec = ctx.expr();
        Type t;

        if (ec != null) {
            t = super.visit(ec);
        } else
            t = Type.VOID;
        return t;
    }

}
