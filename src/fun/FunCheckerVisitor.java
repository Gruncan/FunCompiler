//////////////////////////////////////////////////////////////
//
// A visitor for contextual analysis of Fun.
//
// Based on a previous version developed by
// David Watt and Simon Gay (University of Glasgow).
//
//////////////////////////////////////////////////////////////

package fun;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.runtime.misc.*;

import ast.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FunCheckerVisitor extends AbstractParseTreeVisitor<Type> implements FunVisitor<Type> {

	// Contextual errors

	private int errorCount = 0;

	private CommonTokenStream tokens;

	// Constructor

	public FunCheckerVisitor(CommonTokenStream toks) {
	    tokens = toks;
	}

	private void reportError (String message,
	                          ParserRuleContext ctx) {
	// Print an error message relating to the given 
	// part of the AST.
	    Interval interval = ctx.getSourceInterval();
	    Token start = tokens.get(interval.a);
	    Token finish = tokens.get(interval.b);
	    int startLine = start.getLine();
	    int startCol = start.getCharPositionInLine();
	    int finishLine = finish.getLine();
	    int finishCol = finish.getCharPositionInLine();
	    System.err.println(startLine + ":" + startCol + "-" +
                               finishLine + ":" + finishCol
		   + " " + message);
		errorCount++;
	}

	public int getNumberOfContextualErrors () {
	// Return the total number of errors so far detected.
		return errorCount;
	}


	// Scope checking

	private SymbolTable<Type> typeTable =
	   new SymbolTable<Type>();

	private void predefine () {
	// Add predefined procedures to the type table.
		typeTable.put("read",
			      new Type.Mapping(Type.EMPTY, Type.INT));
		ArrayList<Type> writeParams = new ArrayList<Type>();
		writeParams.add(Type.INT);
		typeTable.put("write",
			      new Type.Mapping(new Type.Sequence(writeParams), Type.VOID));
	}

	private void define (String id, Type type,
	                     ParserRuleContext decl) {
	// Add id with its type to the type table, checking 
	// that id is not already declared in the same scope.
		boolean ok = typeTable.put(id, type);
		if (!ok)
			reportError(id + " is redeclared", decl);
	}

	private Type retrieve (String id, ParserRuleContext occ) {
	// Retrieve id's type from the type table.
		Type type = typeTable.get(id);
		if (type == null) {
			reportError(id + " is undeclared", occ);
			return Type.ERROR;
		} else
			return type;
	}

	// Type checking

	private static final Type.Mapping
	   NOTTYPE = new Type.Mapping(Type.BOOL, Type.BOOL),
	   COMPTYPE = new Type.Mapping(
	      new Type.Pair(Type.INT, Type.INT), Type.BOOL),
	   ARITHTYPE = new Type.Mapping(
	      new Type.Pair(Type.INT, Type.INT), Type.INT),
	    MAINTYPE = new Type.Mapping(Type.EMPTY, Type.VOID);

	private void checkType (Type typeExpected,
	                        Type typeActual,
	                        ParserRuleContext construct) {
	// Check that a construct's actual type matches 
	// the expected type.
		if (! typeActual.equiv(typeExpected))
			reportError("type is " + typeActual
			   + ", should be " + typeExpected,
			   construct);
	}

	private Type checkCall (String id, Type typeArg,
	                        ParserRuleContext call) {
	// Check that a procedure call identifies a procedure 
	// and that its argument type matches the proecure's 
	// type. Return the type of the procedure call.
		Type typeProc = retrieve(id, call);
		if (! (typeProc instanceof Type.Mapping)) {
			reportError(id + " is not a procedure", call);
			return Type.ERROR;
		} else {
			Type.Mapping mapping = (Type.Mapping)typeProc;
			checkType(mapping.domain, typeArg, call);
			return mapping.range;
		}
	}

	private Type checkUnary (Type.Mapping typeOp,
	                         Type typeArg,
	                         ParserRuleContext op) {
	// Check that a unary operator's operand type matches 
	// the operator's type. Return the type of the operator 
	// application.
		if (! (typeOp.domain instanceof Type.Primitive))
			reportError(
			   "unary operator should have 1 operand",
			   op);
		else
			checkType(typeOp.domain, typeArg, op);
		return typeOp.range;
	}

	private Type checkBinary (Type.Mapping typeOp,
	                          Type typeArg1, Type typeArg2,
	                          ParserRuleContext op) {
	// Check that a binary operator's operand types match 
	// the operator's type. Return the type of the operator 
	// application.
		if (! (typeOp.domain instanceof Type.Pair))
			reportError(
			   "binary operator should have 2 operands",
			   op);
		else {
			Type.Pair pair =
			   (Type.Pair)typeOp.domain;
			checkType(pair.first, typeArg1, op);
			checkType(pair.second, typeArg2, op);
		}
		return typeOp.range;
	}

	/**
	 * Visit a parse tree produced by the {@code prog}
	 * labeled alternative in {@link FunParser#program}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitProg(FunParser.ProgContext ctx) {
	    predefine();
	    visitChildren(ctx);
	    Type tmain = retrieve("main", ctx);
	    checkType(MAINTYPE, tmain, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proc}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitProc(FunParser.ProcContext ctx) {
	    typeTable.enterLocalScope();
	    Type t;
	    FunParser.Formal_decl_seqContext fd = ctx.formal_decl_seq();
	    if (fd != null)
		t = visit(fd);
	    else
		t = Type.EMPTY;
	    Type proctype = new Type.Mapping(t, Type.VOID);
	    define(ctx.ID().getText(), proctype, ctx);
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    visit(ctx.seq_com());
	    typeTable.exitLocalScope();
	    define(ctx.ID().getText(), proctype, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code func}
	 * labeled alternative in {@link FunParser#proc_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFunc(FunParser.FuncContext ctx) {
	    typeTable.enterLocalScope();
	    Type t1 = visit(ctx.type());
	    Type t2;
	    FunParser.Formal_decl_seqContext fd = ctx.formal_decl_seq();
	    if (fd != null)
		t2 = visit(fd);
	    else
		t2 = Type.EMPTY;
	    Type functype = new Type.Mapping(t2, t1);
	    define(ctx.ID().getText(), functype, ctx);
	    List<FunParser.Var_declContext> var_decl = ctx.var_decl();
	    for (FunParser.Var_declContext vd : var_decl)
		visit(vd);
	    visit(ctx.seq_com());
	    typeTable.exitLocalScope();
	    define(ctx.ID().getText(), functype, ctx);
	    return null;
	}
    
        /**
	 * Visit a parse tree produced by the {@code formalseq}
	 * labeled alternative in {@link FunParser#formal_decl_seq}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
    public Type visitFormalseq(FunParser.FormalseqContext ctx) {
	ArrayList<Type> types = new ArrayList<Type>();
	for (FunParser.Formal_declContext fc : ctx.formal_decl())
	    types.add(visit(fc));
	return new Type.Sequence(types);
    }

    
	/**
	 * Visit a parse tree produced by the {@code formal}
	 * labeled alternative in {@link FunParser#formal_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFormal(FunParser.FormalContext ctx) {
	    FunParser.TypeContext tc = ctx.type();
	    Type t = visit(tc);
	    define(ctx.ID().getText(), t, ctx);
	    return t;
	}

	/**
	 * Visit a parse tree produced by the {@code var}
	 * labeled alternative in {@link FunParser#var_decl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitVar(FunParser.VarContext ctx) {
	    Type t1 = visit(ctx.type());
	    Type t2 = visit(ctx.expr());
	    define(ctx.ID().getText(), t1, ctx);
	    checkType(t1, t2, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code bool}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitBool(FunParser.BoolContext ctx) {
	    return Type.BOOL;
	}

	/**
	 * Visit a parse tree produced by the {@code int}
	 * labeled alternative in {@link FunParser#type}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitInt(FunParser.IntContext ctx) {
	    return Type.INT;
	}

	/**
	 * Visit a parse tree produced by the {@code assn}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitAssn(FunParser.AssnContext ctx) {
	    Type tvar = retrieve(ctx.ID().getText(), ctx);
	    Type t = visit(ctx.expr());
	    checkType(tvar, t, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code proccall}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitProccall(FunParser.ProccallContext ctx) {
	    FunParser.Actual_seqContext actuals = ctx.actual_seq();
	    Type t;
	    if (actuals != null)
		t = visit(actuals);
	    else
		t = new Type.Sequence(new ArrayList<Type>());
	    Type tres = checkCall(ctx.ID().getText(), t, ctx);
	    if (! tres.equiv(Type.VOID))
		reportError("procedure should be void", ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code if}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitIf(FunParser.IfContext ctx) {
	    Type t = visit(ctx.expr());
	    visit(ctx.c1);
	    if (ctx.c2 != null)
		visit(ctx.c2);
	    checkType(Type.BOOL, t, ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by the {@code while}
	 * labeled alternative in {@link FunParser#com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitWhile(FunParser.WhileContext ctx) {
	    Type t = visit(ctx.expr());
	    visit(ctx.seq_com());
	    checkType(Type.BOOL, t, ctx);
	    return null;
	}

	//EXTENSION
	@Override
	public Type visitRepeat_until(FunParser.Repeat_untilContext ctx) {
		Type t = super.visit(ctx.expr());
		super.visit(ctx.seq_com());
		this.checkType(Type.BOOL, t, ctx);
		return null;
	}

	@Override
	public Type visitSwitch(FunParser.SwitchContext ctx) {
		Type t = super.visit(ctx.expr());
		Set<Integer> masterSet = new HashSet<>();
		for (FunParser.Sw_caseContext sw_case : ctx.sw_case()) {
			Type type = super.visit(sw_case);
			this.checkType(t, type, ctx);

			int currentLength = masterSet.size();
			Set<Integer> toAddSet = this.checkSwitchOverlap(sw_case);
			int toAddLength = toAddSet.size();
			masterSet.addAll(toAddSet);

			boolean hasOverlapped = (currentLength + toAddLength) != masterSet.size();
			if (hasOverlapped)
				this.reportError(String.format("Switch case guard (%s) has already been used!", toAddSet), sw_case);
		}
		super.visit(ctx.sw_default());
		return null;
	}


	private Set<Integer> checkSwitchOverlap(FunParser.Sw_caseContext sw_case) {
		for (ParseTree child : sw_case.children) {
			if (child instanceof FunParser.RangeContext rangeContext) {
				// Type has already been checked and verified to be INT
				String n1 = ((FunParser.NumContext) rangeContext.n1).NUM().toString();
				String n2 = ((FunParser.NumContext) rangeContext.n2).NUM().toString();
				int i1 = Integer.parseInt(n1);
				int i2 = Integer.parseInt(n2);
				// Create set of ints from i1-i2
				return IntStream.rangeClosed(i1, i2)
						.boxed().collect(Collectors.toSet());
			} else if (child instanceof FunParser.NumContext numContext) {
				String n = numContext.NUM().toString();
				// Return the single number
				return Collections.singleton(Integer.parseInt(n));
			} else if (child instanceof FunParser.TrueContext trueContext) {
				String s = trueContext.TRUE().toString();
				boolean t = Boolean.parseBoolean(s);
				// Convert bool into int
				return Collections.singleton(t ? 1 : 0);
			} else if (child instanceof FunParser.FalseContext falseContext) {
				String s = falseContext.FALSE().toString();
				boolean t = Boolean.parseBoolean(s);
				// Convert bool into int
				return Collections.singleton(t ? 1 : 0);
			}
		}
		return Collections.emptySet();
	}

	@Override
	public Type visitCase(FunParser.CaseContext ctx) {
		FunParser.RangeContext range = ctx.range();
		FunParser.LitContext lit = ctx.lit();
		if (range == null)
			return super.visit(lit);
		else
			return super.visit(range);
	}

	@Override
	public Type visitDefault(FunParser.DefaultContext ctx) {
		super.visit(ctx.seq_com());
		return null;
	}

	@Override
	public Type visitRange(FunParser.RangeContext ctx) {
		Type t1 = super.visit(ctx.n1);
		Type t2 = super.visit(ctx.n2);
		this.checkType(Type.INT, t1, ctx);
		this.checkType(Type.INT, t2, ctx);
		return Type.INT;
	}

	@Override
	public Type visitLiteral(FunParser.LiteralContext ctx) {
		return super.visitChildren(ctx);
	}
	// END OF EXTENSION

	/**
	 * Visit a parse tree produced by the {@code seq}
	 * labeled alternative in {@link FunParser#seq_com}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitSeq(FunParser.SeqContext ctx) {
	    visitChildren(ctx);
	    return null;
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitExpr(FunParser.ExprContext ctx) {
	    Type t1 = visit(ctx.e1);
	    if (ctx.e2 != null) {
		Type t2 = visit(ctx.e2);
		return checkBinary(COMPTYPE, t1, t2, ctx);
	    }
	    else {
		return t1;
	    }
	}

	/**
	 * Visit a parse tree produced by {@link FunParser#sec_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitSec_expr(FunParser.Sec_exprContext ctx) {
	    Type t1 = visit(ctx.e1);
	    if (ctx.e2 != null) {
		Type t2 = visit(ctx.e2);
		return checkBinary(ARITHTYPE, t1, t2, ctx);
	    }
	    else {
		return t1;
	    }
	}

	/**
	 * Visit a parse tree produced by the {@code false}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFalse(FunParser.FalseContext ctx) {
	    return Type.BOOL;
	}

	/**
	 * Visit a parse tree produced by the {@code true}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitTrue(FunParser.TrueContext ctx) {
	    return Type.BOOL;
	}

	/**
	 * Visit a parse tree produced by the {@code num}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitNum(FunParser.NumContext ctx) {
	    return Type.INT;
	}

	/**
	 * Visit a parse tree produced by the {@code id}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitId(FunParser.IdContext ctx) {
	    return retrieve(ctx.ID().getText(), ctx);
	}

	/**
	 * Visit a parse tree produced by the {@code funccall}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitFunccall(FunParser.FunccallContext ctx) {
	    FunParser.Actual_seqContext actuals = ctx.actual_seq();
	    Type t;
	    if (actuals != null)
		t = visit(actuals);
	    else
		t = new Type.Sequence(new ArrayList<Type>());
	    Type tres = checkCall(ctx.ID().getText(), t, ctx);
	    if (tres.equiv(Type.VOID))
		reportError("function should be non-void", ctx);
	    return tres;
	}

	/**
	 * Visit a parse tree produced by the {@code not}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitNot(FunParser.NotContext ctx) {
	    Type t = visit(ctx.prim_expr());
	    return checkUnary(NOTTYPE, t, ctx);
	}

	/**
	 * Visit a parse tree produced by the {@code parens}
	 * labeled alternative in {@link FunParser#prim_expr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitParens(FunParser.ParensContext ctx) {
	    return visit(ctx.expr());
	}

        

        /**
	 * Visit a parse tree produced by the {@code actualseq}
	 * labeled alternative in {@link FunParser#actual_seq}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	public Type visitActualseq(FunParser.ActualseqContext ctx) {
	    ArrayList<Type> types = new ArrayList<Type>();
	    for (FunParser.ExprContext fc : ctx.expr())
		types.add(visit(fc));
	    return new Type.Sequence(types);
    }
    

}
