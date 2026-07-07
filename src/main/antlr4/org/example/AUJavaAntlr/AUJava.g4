grammar AUJava;

// Parser Rules 

program
    : classDecl* EOF                                                   #ProgramDeclar
    ;

classDecl
    : (PUBLIC)? 'class' name=ID (EXTENDS parent=ID)? '{' member* '}'   #ClassDeclaration
    ;

member
    : fieldDecl                                                        #FieldMember
    | methodDecl                                                       #MethodMember
    ;

fieldDecl
    : (STATIC)? type ID ';'                                            #FieldDeclaration
    ;

paramList
    : (param (',' param)*)?                                            #Parameters
    ;

param
    : type ID                                                          #ParamDeclaration
    ;

// Special-cased so we can recognize `public static void main(String[] args)`.
// 'main' and 'String' are matched as plain ID and validated semantically
// (their text is checked in the visitor) so they don't collide with the ID rule.
mainMethodDecl
    : PUBLIC STATIC VOID name=ID '(' stringType=ID '[' ']' argsName=ID ')' block   #MainMethod
    ;

methodDecl
    : mainMethodDecl                                                   #MainMethodDeclaration
    | (STATIC)? type ID '(' paramList ')' block                        #RegularMethodDeclaration
    ;

block
    : '{' statement* '}'
    ;

statement
    : block                                                            #BlockStatement
    | localVarDecl ';'                                                 #LocalVarDeclStatement
    | assign ';'                                                       #AssignmentStatement
    | expr ';'                                                         #ExpressionStatement
    | IF '(' expr ')' thenStat=statement (ELSE elseStat=statement)?     #IfStatement
    | WHILE '(' expr ')' statement                                     #WhileStatement
    | BREAK ';'                                                        #BreakStatement
    | CONTINUE ';'                                                     #ContinueStatement
    | RETURN expr? ';'                                                 #ReturnStatement
    | 'System' '.' 'out' '.' 'println' '(' expr ')' ';'                #PrintStatement
    ;

localVarDecl
    : type ID ('=' expr)?                                              #LocalVariableDeclaration
    ;

assign
    : target=lvalue '=' expr                                           #Assignment
    ;

lvalue
    : THIS '.' ID                                                      #ThisFieldLValue
    | receiver=expr '.' field=ID                                       #FieldLValue
    | ID                                                                #IdLValue
    ;

type
    : INT                                                              #IntType
    | BOOLEAN                                                          #BooleanType
    | VOID                                                             #VoidType
    | ID                                                                #ClassType
    ;

argList
    : (expr (',' expr)*)?                                              #Arguments
    ;

expr
    : op='!' expr                                                                  #NotExpression
    | leftSide=expr op=('*' | '/' | '%') rightSide=expr                            #MulDivModExpression
    | leftSide=expr op=('+' | '-') rightSide=expr                                  #AddSubExpression
    | leftSide=expr op=('<' | '<=' | '>' | '>=') rightSide=expr                    #RelationalExpression
    | leftSide=expr op=('==' | '!=') rightSide=expr                                #EqualityExpression
    | leftSide=expr op='&&' rightSide=expr                                         #AndExpression
    | leftSide=expr op='||' rightSide=expr                                         #OrExpression
    | receiver=expr '.' method=ID '(' argList ')'                                  #MethodCallExpression
    | receiver=expr '.' field=ID
    #FieldAccessExpression
    | NEW ID '(' ')'                                                               #NewObjectExpression
    | ID '(' argList ')'                                                           #UnqualifiedCallExpression
    | THIS                                                                          #ThisExpression
    | atom                                                                          #AtomExpression
    ;

atom
    : INTEGER                                                          #IntegerAtom
    | TRUE                                                             #TrueAtom
    | FALSE                                                            #FalseAtom
    | ID                                                                #IDAtom
    | '(' expr ')'                                                     #ParentAtom
    ;

// Lexer Rules

IF        : 'if';
ELSE      : 'else';
WHILE     : 'while';
BREAK     : 'break';
CONTINUE  : 'continue';
RETURN    : 'return';
NEW       : 'new';
THIS      : 'this';
EXTENDS   : 'extends';
STATIC    : 'static';
PUBLIC    : 'public';
VOID      : 'void';
INT       : 'int';
BOOLEAN   : 'boolean';
TRUE      : 'true';
FALSE     : 'false';

ID : [a-zA-Z_][a-zA-Z0-9_]* ;
INTEGER : [0-9]+ ;

LINE_COMMENT  : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT : '/*' .*? '*/' -> skip;
WS : [ \t\r\n]+ -> skip;