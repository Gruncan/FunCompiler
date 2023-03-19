package fun;

import ast.FunParser;
import ast.FunVisitor;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * A visitor for code generation for Fun.
 * Based on a previous version developed by
 * David Watt and Simon Gay (University of Glasgow).
 */
public class FunEncoderVisitor extends AbstractParseTreeVisitor<Void> implements FunVisitor<Void> {

    private final SVM obj = new SVM();
    private final SymbolTable<Address> addrTable = new SymbolTable<>();
    private int globalVarAddr = 0;
    private int currentLocale = Address.GLOBAL;
    private int localVarAddr = 0;

    private void predefine() {
        // Add predefined procedures to the address table.
        this.addrTable.put("read", new Address(SVM.READ_OFF_SET, Address.CODE));
        this.addrTable.put("write", new Address(SVM.WRITE_OFF_SET, Address.CODE));
    }

    public SVM getSVM() {
        return this.obj;
    }

    /**
     * Visit a parse tree produced by the {@code prog}
     * labeled alternative in {@link FunParser#program}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitProg(FunParser.ProgContext ctx) {
        this.predefine();
        List<FunParser.Var_declContext> var_decl = ctx.var_decl();

        for (FunParser.Var_declContext vd : var_decl) super.visit(vd);

        int callAddr = this.obj.currentOffset();
        this.obj.emit12(SVM.CALL, 0);
        this.obj.emit1(SVM.HALT);

        List<FunParser.Proc_declContext> proc_decl = ctx.proc_decl();

        for (FunParser.Proc_declContext pd : proc_decl) super.visit(pd);

        int mainAddr = this.addrTable.get("main").offset;
        this.obj.patch12(callAddr, mainAddr);
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
    public Void visitProc(FunParser.ProcContext ctx) {
        String id = ctx.ID().getText();
        Address procaddr = new Address(this.obj.currentOffset(), Address.CODE);
        this.addrTable.put(id, procaddr);
        this.addrTable.enterLocalScope();
        this.currentLocale = Address.LOCAL;
        this.localVarAddr = 2;

        // ... allows 2 words for link data
        FunParser.Formal_declContext fd = ctx.formal_decl();
        if (fd != null) super.visit(fd);

        List<FunParser.Var_declContext> var_decl = ctx.var_decl();
        for (FunParser.Var_declContext vd : var_decl) super.visit(vd);

        super.visit(ctx.seq_com());
        this.obj.emit11(SVM.RETURN, 0);
        this.addrTable.exitLocalScope();
        this.currentLocale = Address.GLOBAL;
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
    public Void visitFunc(FunParser.FuncContext ctx) {
        String id = ctx.ID().getText();
        Address procAddr = new Address(this.obj.currentOffset(), Address.CODE);
        this.addrTable.put(id, procAddr);
        this.addrTable.enterLocalScope();
        this.currentLocale = Address.LOCAL;
        this.localVarAddr = 2;

        // ... allows 2 words for link data
        FunParser.Formal_declContext fd = ctx.formal_decl();
        if (fd != null) super.visit(fd);

        List<FunParser.Var_declContext> var_decl = ctx.var_decl();
        for (FunParser.Var_declContext vd : var_decl) super.visit(vd);

        super.visit(ctx.seq_com());
        super.visit(ctx.expr());
        this.obj.emit11(SVM.RETURN, 1);
        this.addrTable.exitLocalScope();
        this.currentLocale = Address.GLOBAL;
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
    public Void visitFormal(FunParser.FormalContext ctx) {
        FunParser.TypeContext tc = ctx.type();
        if (tc != null) {
            String id = ctx.ID().getText();
            this.addrTable.put(id, new Address(this.localVarAddr++, Address.LOCAL));
            this.obj.emit11(SVM.COPYARG, 1);
        }
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code var}
     * labeled alternative in {@link FunParser#var_decl}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitVar(FunParser.VarContext ctx) {
        super.visit(ctx.expr());
        String id = ctx.ID().getText();
        switch (this.currentLocale) {
            case Address.LOCAL -> this.addrTable.put(id, new Address(this.localVarAddr++, Address.LOCAL));
            case Address.GLOBAL -> this.addrTable.put(id, new Address(this.globalVarAddr++, Address.GLOBAL));
        }
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
    public Void visitBool(FunParser.BoolContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code int}
     * labeled alternative in {@link FunParser#type}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitInt(FunParser.IntContext ctx) {
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code assn}
     * labeled alternative in {@link FunParser#com}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitAssn(FunParser.AssnContext ctx) {
        super.visit(ctx.expr());
        String id = ctx.ID().getText();
        Address varAddr = this.addrTable.get(id);
        switch (varAddr.locale) {
            case Address.GLOBAL -> this.obj.emit12(SVM.STOREG, varAddr.offset);
            case Address.LOCAL -> this.obj.emit12(SVM.STOREL, varAddr.offset);
        }
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
    public Void visitProccall(FunParser.ProccallContext ctx) {
        super.visit(ctx.actual());
        String id = ctx.ID().getText();
        Address procAddr = this.addrTable.get(id);

        // Assume procaddr.locale == CODE.
        this.obj.emit12(SVM.CALL, procAddr.offset);
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
    public Void visitIf(FunParser.IfContext ctx) {
        super.visit(ctx.expr());
        int condAddr = this.obj.currentOffset();
        this.obj.emit12(SVM.JUMPF, 0);

        if (ctx.c2 == null) { // IF without ELSE
            super.visit(ctx.c1);
            int exitAddr = this.obj.currentOffset();
            this.obj.patch12(condAddr, exitAddr);

        } else {                // IF ... ELSE
            super.visit(ctx.c1);
            int jumpAddr = this.obj.currentOffset();
            this.obj.emit12(SVM.JUMP, 0);

            int elseAddr = this.obj.currentOffset();
            this.obj.patch12(condAddr, elseAddr);
            super.visit(ctx.c2);

            int exitAddr = this.obj.currentOffset();
            this.obj.patch12(jumpAddr, exitAddr);
        }
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
    public Void visitWhile(FunParser.WhileContext ctx) {
        int startAddr = this.obj.currentOffset();
        super.visit(ctx.expr());

        int condAddr = this.obj.currentOffset();
        this.obj.emit12(SVM.JUMPF, 0);
        super.visit(ctx.seq_com());

        this.obj.emit12(SVM.JUMP, startAddr);
        int exitAddr = this.obj.currentOffset();
        this.obj.patch12(condAddr, exitAddr);

        return null;
    }

    @Override
    public Void visitRepeat_until(FunParser.Repeat_untilContext ctx) {
        // Evaluating expression and storing in i
        super.visit(ctx.sec_expr());
        String id = "_i"; // Impossible to override other variables since illegal naming
        this.addrTable.put(id, new Address(this.globalVarAddr++, Address.GLOBAL));
        Address iAddr = this.addrTable.get(id);
        int startAddr = this.obj.currentOffset();

        this.obj.emit12(SVM.STOREG, iAddr.offset);
        this.obj.emit12(SVM.LOADC, 0);
        this.obj.emit12(SVM.LOADG, iAddr.offset);


        this.obj.emit1(SVM.CMPLT);
        int condAddr = this.obj.currentOffset();
        this.obj.emit12(SVM.JUMPF, 0); // To be patched

        super.visit(ctx.seq_com());

        this.obj.emit12(SVM.LOADG, iAddr.offset);
        this.obj.emit12(SVM.LOADC, 1);
        this.obj.emit1(SVM.SUB);

        this.obj.emit12(SVM.JUMP, startAddr);

        int exitAddr = this.obj.currentOffset();
        this.obj.patch12(condAddr, exitAddr);

        // removes index variable
        this.addrTable.remove(id);

        return null;
    }

    @Override
    public Void visitSwitch(FunParser.SwitchContext ctx) {
        super.visit(ctx.expr());
        // TODO Check its not already in use, with things like loop etc
        String id = "_i"; // Impossible to override other variables since illegal naming
        this.addrTable.put(id, new Address(this.globalVarAddr++, Address.GLOBAL));
        Address iAddr = this.addrTable.get(id);
        this.obj.emit12(SVM.STOREG, iAddr.offset);

        int[] patches = new int[ctx.sw_case().size()];
        for (int i = 0; i < patches.length; i++) {
            super.visit(ctx.sw_case(i));
            patches[i] = this.obj.currentOffset() - 3;
        }
        super.visit(ctx.sw_default());

        int endAddr = this.obj.currentOffset();


        for (int patchLoc : patches) {
            this.obj.patch12(patchLoc, endAddr);
        }


        return null;
    }

    @Override
    public Void visitCase(FunParser.CaseContext ctx) {
        // TODO handle option or the 2

        // TODO Check its not already in use, with things like loop etc
        String id = "_i"; // Impossible to override other variables since illegal naming
        Address iAddr = this.addrTable.get(id);

        FunParser.LitContext litContext = ctx.lit();
        FunParser.RangeContext rangeContext = ctx.range();

        List<Integer> conditions = new ArrayList<>();
        if (rangeContext == null) {
            super.visit(litContext); // Pushes on to stack
            this.obj.emit12(SVM.LOADG, iAddr.offset);
            this.obj.emit1(SVM.CMPEQ);
        } else {
            super.visit(rangeContext); // Pushes n1-1 then n2+1 to stack
            this.obj.emit12(SVM.LOADG, iAddr.offset);
            this.obj.emit1(SVM.CMPGT);
            conditions.add(this.obj.currentOffset());
            this.obj.emit12(SVM.JUMPF, 0); // to be patched

            this.obj.emit12(SVM.LOADG, iAddr.offset);
            this.obj.emit1(SVM.CMPLT);
        }

        conditions.add(this.obj.currentOffset());
        this.obj.emit12(SVM.JUMPF, 0); // To be patched

        super.visit(ctx.seq_com());

        this.obj.emit12(SVM.JUMP, 0); // After finished jump to end

        int exitAddr = this.obj.currentOffset();
        for (int condAddr : conditions) {
            this.obj.patch12(condAddr, exitAddr); // Patching to next case or default
        }
        return null;
    }

    @Override
    public Void visitDefault(FunParser.DefaultContext ctx) {
        super.visit(ctx.seq_com());
        return null;
    }

    @Override
    public Void visitRange(FunParser.RangeContext ctx) {
        super.visit(ctx.n1);
        this.obj.emit12(SVM.LOADC, 1);
        this.obj.emit1(SVM.SUB);
        super.visit(ctx.n2);
        this.obj.emit12(SVM.LOADC, 1);
        this.obj.emit1(SVM.ADD);
        return null;
    }

    @Override
    public Void visitLiteral(FunParser.LiteralContext ctx) {
        super.visitChildren(ctx);
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
    public Void visitSeq(FunParser.SeqContext ctx) {
        this.visitChildren(ctx);
        return null;
    }

    /**
     * Visit a parse tree produced by {@link FunParser#expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitExpr(FunParser.ExprContext ctx) {
        super.visit(ctx.e1);
        if (ctx.e2 != null) {
            super.visit(ctx.e2);
            switch (ctx.op.getType()) {
                case FunParser.EQ -> this.obj.emit1(SVM.CMPEQ);
                case FunParser.LT -> this.obj.emit1(SVM.CMPLT);
                case FunParser.GT -> this.obj.emit1(SVM.CMPGT);
            }
        }
        return null;
    }

    /**
     * Visit a parse tree produced by {@link FunParser#sec_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitSec_expr(FunParser.Sec_exprContext ctx) {
        super.visit(ctx.e1);
        if (ctx.e2 != null) {
            super.visit(ctx.e2);
            switch (ctx.op.getType()) {
                case FunParser.PLUS -> this.obj.emit1(SVM.ADD);
                case FunParser.MINUS -> this.obj.emit1(SVM.SUB);
                case FunParser.TIMES -> this.obj.emit1(SVM.MUL);
                case FunParser.DIV -> this.obj.emit1(SVM.DIV);
            }
        }
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code false}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitFalse(FunParser.FalseContext ctx) {
        this.obj.emit12(SVM.LOADC, 0);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code true}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitTrue(FunParser.TrueContext ctx) {
        this.obj.emit12(SVM.LOADC, 1);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code num}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitNum(FunParser.NumContext ctx) {
        int value = Integer.parseInt(ctx.NUM().getText());
        this.obj.emit12(SVM.LOADC, value);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code id}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitId(FunParser.IdContext ctx) {
        String id = ctx.ID().getText();
        Address varAddr = this.addrTable.get(id);
        switch (varAddr.locale) {
            case Address.GLOBAL -> this.obj.emit12(SVM.LOADG, varAddr.offset);
            case Address.LOCAL -> this.obj.emit12(SVM.LOADL, varAddr.offset);
        }
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code funccall}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitFunccall(FunParser.FunccallContext ctx) {
        super.visit(ctx.actual());
        String id = ctx.ID().getText();
        Address funcAddr = this.addrTable.get(id);
        // Assume that funcAddr.locale == CODE.
        this.obj.emit12(SVM.CALL, funcAddr.offset);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code not}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitNot(FunParser.NotContext ctx) {
        super.visit(ctx.prim_expr());
        this.obj.emit1(SVM.INV);
        return null;
    }

    /**
     * Visit a parse tree produced by the {@code parens}
     * labeled alternative in {@link FunParser#prim_expr}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitParens(FunParser.ParensContext ctx) {
        super.visit(ctx.expr());
        return null;
    }

    /**
     * Visit a parse tree produced by {@link FunParser#actual}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public Void visitActual(FunParser.ActualContext ctx) {
        FunParser.ExprContext ec = ctx.expr();

        if (ec != null) super.visit(ec);

        return null;
    }

}
