//////////////////////////////////////////////////////////////
//
// A visitor for code generation for Fun.
//
// Based on a previous version developed by
// David Watt and Simon Gay (University of Glasgow).
//
//////////////////////////////////////////////////////////////

package fun;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.*;

import java.util.List;

import ast.*;

public class FunEncoderVisitor extends AbstractParseTreeVisitor<Void> implements FunVisitor<Void> {

	private SVM obj = new SVM();

	private int globalvaraddr = 0;
	private int localvaraddr = 0;
	private int currentLocale = Address.GLOBAL;

	private SymbolTable<Address> addrTable =
	   new SymbolTable<Address>();

	private void predefine () {
	// Add predefined procedures to the address table.
		addrTable.put("read",
		   new Address(SVM.READOFFSET, Address.CODE));
		addrTable.put("write",
		   new Address(SVM.WRITEOFFSET, Address.CODE));
	}

	public SVM getSVM() {
	    return obj;
	}

	/**
	 * Visit a parse tree produced by the {@code prog}
	 * labeled alternative in {@link FunParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitProg(FunParser.ProgContext ctx) {
	    predefine();
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    int calladdr = obj.currentOffset();
	    obj.emit12(SVM.CALL, 0);
	    obj.emit1(SVM.HALT);
	    List<FunParser.Proc_declContext> proc_decl = ctx.proc_decl();
	    for (FunParser.Proc_declContext pd : proc_decl)
		visit(pd);
	    int mainaddr = addrTable.get("main").offset;
	    obj.patch12(calladdr, mainaddr);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proc}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitProc(FunParser.ProcContext ctx) {
	    String id = ctx.ID().getText();
	    Address procaddr = new Address(obj.currentOffset(), Address.CODE);
	    addrTable.put(id, procaddr);
	    addrTable.enterLocalScope();
	    currentLocale = Address.LOCAL;
	    localvaraddr = 2;
	    // ... allows 2 words for link data
	    FunParser.Formal_decl_seqContext fd = ctx.formal_decl_seq();
	    if (fd != null)
		visit(fd);
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    visit(ctx.seq_com());
	    obj.emit11(SVM.RETURN, 0);
	    addrTable.exitLocalScope();
	    currentLocale = Address.GLOBAL;
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code func}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFunc(FunParser.FuncContext ctx) {
	    String id = ctx.ID().getText();
	    Address procaddr = new Address(obj.currentOffset(), Address.CODE);
	    addrTable.put(id, procaddr);
	    addrTable.enterLocalScope();
	    currentLocale = Address.LOCAL;
	    localvaraddr = 2;
	    // ... allows 2 words for link data
	    FunParser.Formal_decl_seqContext fd = ctx.formal_decl_seq();
	    if (fd != null)
		visit(fd);
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    visit(ctx.seq_com());
	    obj.emit11(SVM.RETURN, 1);
	    addrTable.exitLocalScope();
	    currentLocale = Address.GLOBAL;
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code formal}
	 * labeled alternative in {@link FunParser#formal_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFormalseq(FunParser.FormalseqContext ctx) {
	    for (FunParser.Formal_declContext fc : ctx.formal_decl())
		visit(fc);
	    return null;
	}

        /**
	 * Visit a parse tree produced by the {@code formal}
	 * labeled alternative in {@link FunParser#formal_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFormal(FunParser.FormalContext ctx) {
	    FunParser.TypeContext tc = ctx.type();
	    String id = ctx.ID().getText();
	    addrTable.put(id, new Address(localvaraddr++, Address.LOCAL));
	    obj.emit11(SVM.COPYARG, 1); 
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code var}
	 * labeled alternative in {@link FunParser#var_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitVar(FunParser.VarContext ctx) {
	    visit(ctx.expr());
	    String id = ctx.ID().getText();
	    switch (currentLocale) {
	    case Address.LOCAL:
		addrTable.put(id, new Address(
					      localvaraddr++, Address.LOCAL));
		break;
	    case Address.GLOBAL:
		addrTable.put(id, new Address(
					      globalvaraddr++, Address.GLOBAL));
	    }
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code bool}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitBool(FunParser.BoolContext ctx) {
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code int}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitInt(FunParser.IntContext ctx) {
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code assn}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitAssn(FunParser.AssnContext ctx) {
	    visit(ctx.expr());
	    String id = ctx.ID().getText();
	    Address varaddr = addrTable.get(id);
	    switch (varaddr.locale) {
	    case Address.GLOBAL:
		obj.emit12(SVM.STOREG,varaddr.offset);
		break;
	    case Address.LOCAL:
		obj.emit12(SVM.STOREL,varaddr.offset);
	    }
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proccall}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitProccall(FunParser.ProccallContext ctx) {
	    FunParser.Actual_seqContext actuals = ctx.actual_seq();
	    if (actuals != null)
		visit(ctx.actual_seq());
	    String id = ctx.ID().getText();
	    Address procaddr = addrTable.get(id);
	    // Assume procaddr.locale == CODE.
	    obj.emit12(SVM.CALL,procaddr.offset);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code if}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitIf(FunParser.IfContext ctx) {
	    visit(ctx.expr());
	    int condaddr = obj.currentOffset();
	    obj.emit12(SVM.JUMPF, 0);
	    if (ctx.c2 == null) { // IF without ELSE
		visit(ctx.c1);
		int exitaddr = obj.currentOffset();
		obj.patch12(condaddr, exitaddr);
	    }
	    else {                // IF ... ELSE
		visit(ctx.c1);
		int jumpaddr = obj.currentOffset();
		obj.emit12(SVM.JUMP, 0);
		int elseaddr = obj.currentOffset();
		obj.patch12(condaddr, elseaddr);
		visit(ctx.c2);
		int exitaddr = obj.currentOffset();
		obj.patch12(jumpaddr, exitaddr);
	    }
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code while}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitWhile(FunParser.WhileContext ctx) {
	    int startaddr = obj.currentOffset();
	    visit(ctx.expr());
	    int condaddr = obj.currentOffset();
	    obj.emit12(SVM.JUMPF, 0);
	    visit(ctx.seq_com());
	    obj.emit12(SVM.JUMP, startaddr);
	    int exitaddr = obj.currentOffset();
	    obj.patch12(condaddr, exitaddr);
	    return null;
	}

	// EXTENSION
	/*
	 *               CODE TEMPLATE FOR REPEAT-UNTIL
	 *                                   # start address
	 * LOADC expr
	 * JUMPT end                         # jump to end if expression is true
	 * <command body of repeat until>
	 * JUMP start                        # loop back to start
	 *                                   # end address
	 */
	@Override
	public Void visitRepeat_until(FunParser.Repeat_untilContext ctx) {
		int startAddr = this.obj.currentOffset();
		super.visit(ctx.expr());

		int condAddr = this.obj.currentOffset();
		this.obj.emit12(SVM.JUMPT, 0);
		super.visit(ctx.seq_com());

		this.obj.emit12(SVM.JUMP, startAddr);
		int exitAddr = this.obj.currentOffset();
		this.obj.patch12(condAddr, exitAddr);

		return null;
	}


	/*
	 *               CODE TEMPLATE FOR SWITCH
	 *  LOADC  expr
	 *  STOREG expr                      # store the expr into globals
	 *
	 *                                   # case #1
	 *  LOADC literal                    # load the case literal
	 *  LOADG expr                       # load the expr back into the stack
	 *  CMPEQ                            # check if equal
	 *  JUMPF  nxt                       # jump to next case/default
	 *  <command body of case>
	 *  JUMP   end                       # jump to end of switch
	 *                                   # next address
	 *  <other cases matching the above>
	 *                                   # next address
	 *                                   # default
	 *  <command body of default>
	 *                                   # end address
	 *
	 */
	@Override
	public Void visitSwitch(FunParser.SwitchContext ctx) {
		super.visit(ctx.expr());
		String id = "_i"; // Impossible to override other variables since illegal naming
		// Handles nesting of switch statements by saving previous address if exists
		Address oldAddr = this.addrTable.get(id);
		this.addrTable.overwritePut(id, new Address(this.globalvaraddr++, Address.GLOBAL));
		Address iAddr = this.addrTable.get(id);

		this.obj.emit12(SVM.STOREG, iAddr.offset);

		int[] patches = new int[ctx.sw_case().size()];
		for (int i = 0; i < patches.length; i++) {
			super.visit(ctx.sw_case(i));
			// 3 bits for jump instruction, cl stores next instruction position,
			// therefore -3 to go to previous instruction (the jump to be patched)
			patches[i] = this.obj.currentOffset() - 3;
		}
		super.visit(ctx.sw_default());

		int endAddr = this.obj.currentOffset();

		for (int patchLoc : patches) {
			this.obj.patch12(patchLoc, endAddr);
		}

		// Puts previous switch guard back for nested switch statements
		this.addrTable.overwritePut(id, oldAddr);
		this.globalvaraddr--;
		return null;
	}

	@Override
	public Void visitCase(FunParser.CaseContext ctx) {
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
	// END OF EXTENSION


	/**
	 * Visit a parse tree produced by the {@code seq}
	 * labeled alternative in {@link FunParser#seq_com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitSeq(FunParser.SeqContext ctx) {
	    visitChildren(ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitExpr(FunParser.ExprContext ctx) {
	    visit(ctx.e1);
	    if (ctx.e2 != null) {
		visit(ctx.e2);
		switch (ctx.op.getType()) {
		case FunParser.EQ:
		    obj.emit1(SVM.CMPEQ);
		    break;
		case FunParser.LT:
		    obj.emit1(SVM.CMPLT);
		    break;
		case FunParser.GT:
		    obj.emit1(SVM.CMPGT);
		    break;
		}
	    }
	    return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#sec_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitSec_expr(FunParser.Sec_exprContext ctx) {
	    visit(ctx.e1);
	    if (ctx.e2 != null) {
		visit(ctx.e2);
		switch (ctx.op.getType()) {
		case FunParser.PLUS:
		    obj.emit1(SVM.ADD);
		    break;
		case FunParser.MINUS:
		    obj.emit1(SVM.SUB);
		    break;
		case FunParser.TIMES:
		    obj.emit1(SVM.MUL);
		    break;
		case FunParser.DIV:
		    obj.emit1(SVM.DIV);
		    break;
		}
	    }
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code false}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFalse(FunParser.FalseContext ctx) {
	    obj.emit12(SVM.LOADC, 0);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code true}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitTrue(FunParser.TrueContext ctx) {
	    obj.emit12(SVM.LOADC, 1);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code num}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitNum(FunParser.NumContext ctx) {
	    int value = Integer.parseInt(ctx.NUM().getText());
	    obj.emit12(SVM.LOADC, value);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code id}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitId(FunParser.IdContext ctx) {
	    String id = ctx.ID().getText();
	    Address varaddr = addrTable.get(id);
	    switch (varaddr.locale) {
	    case Address.GLOBAL:
		obj.emit12(SVM.LOADG,varaddr.offset);
		break;
	    case Address.LOCAL:
		obj.emit12(SVM.LOADL,varaddr.offset);
	    }
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code funccall}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitFunccall(FunParser.FunccallContext ctx) {
	    FunParser.Actual_seqContext actuals = ctx.actual_seq();
	    if (actuals != null)
		visit(ctx.actual_seq());
	    String id = ctx.ID().getText();
	    Address funcaddr = addrTable.get(id);
	    // Assume that funcaddr.locale == CODE.
	    obj.emit12(SVM.CALL,funcaddr.offset);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code not}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitNot(FunParser.NotContext ctx) {
	    visit(ctx.prim_expr());
	    obj.emit1(SVM.INV); 
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Void visitParens(FunParser.ParensContext ctx) {
	    visit(ctx.expr());
	    return null;
	}


    /**
	 * Visit a parse tree produced by the {@code actualseq}
	 * labeled alternative in {@link FunParser#actual_seq}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
    
	public Void visitActualseq(FunParser.ActualseqContext ctx) {
	    for (FunParser.ExprContext fc : ctx.expr())
		visit(fc);
	    return null;
    }
    

}
