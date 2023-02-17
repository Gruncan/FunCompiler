grammar Calc; 

@header{
	package ast;
}


// Programs 

prog
	:	com* EOF
	;

// Commands

com
	:	PUT expr EOL         # put
	|	SET var ASSN expr EOL # set
	;

// Expressions

expr
	:	prim (operator+=(PLUS | MINUS | TIMES) prim)* # op
	;

prim		        
	:	NUM                  # num
	|	ID                   # id
	|	LPAR expr RPAR       # parens
	;
	
var
	:	ID
	;

// Lexicon

PUT	:	'put' ;
SET	:	'set' ;

ASSN	:	'=' ;
PLUS	:	'+' ;
MINUS	:	'-' ;
TIMES	:	'*' ;
LPAR	:	'(' ;
RPAR	:	')' ;

ID	:	'a'..'z' ;
NUM	:	'0'..'9'+ ;

EOL	:	'\r'? '\n' ;

SPACE	:	(' ' | '\t')+  -> skip ;
