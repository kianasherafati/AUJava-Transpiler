grammar AUJava;

//  PARSER RULES


program
    : classDeclaration+ EOF                                                    # ProgramRule
    ;

classDeclaration
    : 'public' 'class' 'Main' '{' mainClassMember* '}'                         # MainClassDeclaration
    | 'class' Identifier ('extends' Identifier)? '{' classMember* '}'          # NormalClassDeclaration
    ;

// Members allowed only inside the single "public class Main"
mainClassMember
    : fieldDeclaration                                                        # MainFieldMember
    | methodDeclaration                                                        # MainMethodMember
    | mainMethodDeclaration                                                    # MainEntryMember
    ;

// Members allowed inside a regular (non-Main) class
classMember
    : fieldDeclaration                                                         # RegularFieldMember
    | methodDeclaration                                                        # RegularMethodMember
    ;

mainMethodDeclaration
    : 'public' 'static' 'void' 'main' '(' 'String' '[' ']' Identifier ')' block # MainEntryPointDecl
    ;

fieldDeclaration
    : 'static'? type Identifier ';'                                           # FieldDeclRule
    ;

methodDeclaration
    : 'static'? type Identifier '(' paramList? ')' block                      # MethodDeclRule
    ;

paramList
    : param (',' param)*                                                      # ParamListRule
    ;

param
    : type Identifier                                                         # ParamRule
    ;

type
    : 'int'                                                                    # IntTypeRule
    | 'boolean'                                                                # BooleanTypeRule
    | 'void'                                                                   # VoidTypeRule
    | Identifier                                                               # ClassTypeRule
    ;

block
    : '{' statement* '}'                                                       # BlockRule
    ;

statement
    : block                                                                    # NestedBlockStatement
    | localVarDeclaration                                                      # LocalVarDeclStatement
    | ifStatement                                                              # IfStatementRule
    | whileStatement                                                           # WhileStatementRule
    | 'break' ';'                                                              # BreakStatementRule
    | 'continue' ';'                                                           # ContinueStatementRule
    | 'return' expression? ';'                                                 # ReturnStatementRule
    | assignment ';'                                                           # AssignStatementRule
    | printlnCall ';'                                                          # PrintlnStatementRule
    | expression ';'                                                           # ExpressionStatementRule
    ;

localVarDeclaration
    : type Identifier ('=' expression)? ';'                                    # LocalVarDeclRule
    ;

ifStatement
    : 'if' '(' expression ')' block ('else' block)?                            # IfElseRule
    ;

whileStatement
    : 'while' '(' expression ')' block                                         # WhileLoopRule
    ;

assignment
    : designator '=' expression                                                # AssignmentRule
    ;

// Assignable targets: a bare name, this.field, or a chain of field accesses
designator
    : Identifier                                                               # SimpleDesignatorRule
    | 'this' '.' Identifier                                                    # ThisFieldDesignatorRule
    | designator '.' Identifier                                                # ChainedFieldDesignatorRule
    ;

printlnCall
    : 'System' '.' 'out' '.' 'println' '(' expression ')'                      # PrintlnRule
    ;

expression
    : primary                                                                  # PrimaryExpressionRule
    | 'new' Identifier '(' argList? ')'                                        # NewObjectExpressionRule
    | expression '.' Identifier '(' argList? ')'                               # MethodCallExpressionRule
    | expression '.' Identifier                                                # FieldAccessExpressionRule
    | '!' expression                                                           # NotExpressionRule
    | '-' expression                                                           # UnaryMinusExpressionRule
    | expression op=('*'|'/') expression                                       # MulDivExpressionRule
    | expression op=('+'|'-') expression                                       # AddSubExpressionRule
    | expression op=('<'|'<='|'>'|'>=') expression                             # RelationalExpressionRule
    | expression op=('=='|'!=') expression                                     # EqualityExpressionRule
    | expression '&&' expression                                               # LogicalAndExpressionRule
    | expression '||' expression                                               # LogicalOrExpressionRule
    ;

primary
    : Identifier                                                               # IdentifierPrimaryRule
    | 'this'                                                                   # ThisPrimaryRule
    | IntegerLiteral                                                           # IntLiteralPrimaryRule
    | ('true' | 'false')                                                       # BoolLiteralPrimaryRule
    | '(' expression ')'                                                       # ParenPrimaryRule
    ;

argList
    : expression (',' expression)*                                            # ArgListRule
    ;

// LEXER RULES

IntegerLiteral
    : [0-9]+
    ;

Identifier
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;
