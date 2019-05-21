#include <stdio.h>
#include <stdlib.h>
#include <string.h>


#include "common.h"
#include "chunk.h"
#include "compiler.h"
#include "scanner.h"
#include "value.h"

#ifdef DEBUG_PRINT_CODE
#include "debug.h"
#endif

typedef struct {
  Token current;
  Token previous;
  bool hadError;
  bool panicMode;
} Parser;

Parser parser;

//Operator precedence levels, lowest to highest
typedef enum {
	      PREC_NONE,
	      PREC_ASSIGN,
	      PREC_OR,
	      PREC_AND,
	      PREC_EQ,
	      PREC_CMP,
	      PREC_TERM,
	      PREC_FACTOR,
	      PREC_UNARY,
	      PREC_CALL,
	      PREC_PRIMARY
} Precedence;

typedef void (*ParseFn)(bool canAssign);

typedef struct {
  ParseFn prefix;
  ParseFn infix;
  Precedence precedence;
} ParseRule;

typedef struct {
  Token name;
  int depth;
} Local;

typedef struct Compiler {
  Local locals[UINT8_COUNT];
  int localCount;
  int scopeDepth;
} Compiler;


Compiler *current = NULL;
Chunk *compilingChunk;

static Chunk *currentChunk(){
  return compilingChunk;
}

//Emit error message with given line number
static void errorAt(const Token *token, const char *message){
  //If an error has previously occured, do nothing. We already know the parser is
  //in a bad state, so there's no point
  if(parser.panicMode){
    return;
  }

  fprintf(stderr, "[line %d]] error", token->line);

  if(token->type == TOKEN_EOF){
    fprintf(stderr, " at end");
  }else if(token->type == TOKEN_ERROR){
    //Do nothing
  }else{
    fprintf(stderr, " at '%.*s'", token->length, token->start);
  }
  fprintf(stderr, ": %s\n", message);
  parser.hadError = true;
}
  
    
static void errorAtCurrent(const char *message){
  errorAt(&parser.current, message);
}

static void error(const char *message){
  errorAt(&parser.previous, message);
}


//Consume the next non-error token of input
static void advance(){
  parser.previous = parser.current;

  for(;;){
    parser.current = scanToken();
    if(parser.current.type != TOKEN_ERROR){
      break;
    }

    errorAtCurrent(parser.current.start);
  }
}

//Consume the next token only if it is the expected kind, error otherwise
static void consume(TokenType type, const char *message){
  if(parser.current.type == type){
    advance();
    return;
  }

  errorAtCurrent(message);
}

static bool check(TokenType type){
  return parser.current.type == type;
}

static bool match(TokenType type){
  if(!check(type)){
    return false;
  }else{
    advance();
    return true;
  }
}

/* Bytecode generation */

//Write to the current chunk of bytecode
static void emitByte(uint8_t byte){
  writeChunk(currentChunk(), byte, parser.previous.line);
}

static void emitBytes(uint8_t byte1, uint8_t byte2){
  emitByte(byte1);
  emitByte(byte2);
}

static void emitLoop(int loopStart){
	emitByte(OP_LOOP);

	int offset = currentChunk()->count - loopStart + 2;
	if(offset > UINT16_MAX){
		error("Loop body too large.");
	}

	emitByte((offset >> 8) & 0xff);
	emitByte(offset & 0xff);
}

static int emitJump(uint8_t instruction){
	emitByte(instruction);
	//Placeholder address since we don't know the target yet.
	emitByte(0xff);
	emitByte(0xff);
	return currentChunk()->count - 2;
}

static void emitReturn(){
  emitByte(OP_RETURN);
}

static void endCompiler(){
  emitReturn();
#ifdef DEBUG_PRINT_CODE
  if(!parser.hadError){
    disassembleChunk(currentChunk(), "code");
  }
#endif
}

static void beginScope(){
  current->scopeDepth++;
}

static void endScope(){
  current->scopeDepth--;

  while(current->localCount > 0 &&
	current->locals[current->localCount - 1].depth > current->scopeDepth){
    emitByte(OP_POP);
    current->localCount--;
  }
}

static void expression();
static void declaration();
static void statement();

static ParseRule *getRule();
static void parsePrecedence();

static uint8_t makeConstant(Value value){
  int constant = addConstant(currentChunk(), value);
  if(constant > UINT8_MAX){
    error("Too many constants in one chunk");
    return 0;
  }
  return (uint8_t) constant;
}
  
static void emitConstant(Value value){
  emitBytes(OP_CONSTANT, makeConstant(value));
}

static void patchJump(int offset){
	int jump = currentChunk()->count - offset - 2;
	if(jump > UINT16_MAX){
		error("Jump too large.");
	}

	currentChunk()->code[offset] = (jump >> 8) & 0xff;
	currentChunk()->code[offset + 1] = jump & 0xff;
}

static void initCompiler(Compiler *compiler){
  compiler->localCount = 0;
  compiler->scopeDepth = 0;
  current = compiler;
}

/* Parser */
/* (And compiler, really. We do the parsing and emit bytcode all in one pass) */

static void parsePrecedence(Precedence precedence){
  advance();
  ParseFn prefixRule = getRule(parser.previous.type)->prefix;
  if(prefixRule == NULL){
    error("Expect expression");
  }
  bool canAssign = precedence <= PREC_ASSIGN;
  prefixRule(canAssign);

  while(precedence <= getRule(parser.current.type)->precedence){
    advance();
    ParseFn infixRule = getRule(parser.previous.type)->infix;
    infixRule(canAssign);
  }

  if(canAssign && match(TOKEN_EQUAL)){
    error("Invalid assignment target.");
    expression();
  }
}

static uint8_t identifierConstant(Token *name){
  return makeConstant(OBJ_VAL(copyString(name->start, name->length)));
}

static bool identifiersEqual(Token *a, Token *b){
  if(a->length != b->length) return false;
  return memcmp(a->start, b->start, a->length) == 0;
}

static int resolveLocal(Compiler *compiler, Token *name){
  for(int i = compiler->localCount - 1; i >= 0; i--){
    Local *local = &compiler->locals[i];
    if(identifiersEqual(&local->name, name)){
      if(local->depth == -1){
	error("Cannot read local variable in its own initializer.");
      }
	return i;
    }
  }
  return -1;
}
      

static void addLocal(Token name){
  if(current->localCount == UINT8_COUNT){
    error("Too many local variables in function.");
    return;
  }
  Local *local = &current->locals[current->localCount++];
  local->name = name;
  local->depth = -1;
}

static void declareVariable(){
  if(current->scopeDepth == 0){
    return;
  }

  Token *name = &parser.previous;
  for(int i = current->localCount; i >= 0; i--){
    Local *local = &current->locals[i];
    if(local->depth != -1 && local->depth < current->scopeDepth){
      break;
    }
    if(identifiersEqual(name, &local->name)){
      error("Variable with this name already declared in this scope.");
    }
  }
  addLocal(*name);
}


static uint8_t parseVariable(const char *errorMessage){
  consume(TOKEN_IDENTIFIER, errorMessage);
  declareVariable();
  if(current->scopeDepth > 0){
    return 0;
  }
  return identifierConstant(&parser.previous);
}

static void finishInitialize(){
  if(current->scopeDepth == 0){
    return;
  }
  current->locals[current->localCount - 1].depth = current->scopeDepth;
}

static void defineVariable(uint8_t global){
  //Local variables are just temp values on the stack: if we're not in the global scope,
  //there's no need to emit special bytecode
  if(current->scopeDepth > 0){
    finishInitialize();
    return;
  }
  emitBytes(OP_DEFINE_GLOBAL, global);
}

#pragma GCC diagnostic ignored "-Wunused-parameter"
static void or_(bool canAssign){
	int elseJump = emitJump(OP_JUMP_IF_FALSE);
	int endJump = emitJump(OP_JUMP);

	patchJump(elseJump);
	emitByte(OP_POP); //Jump for short-circuit evaluation

	parsePrecedence(PREC_OR);
	patchJump(endJump);
}

#pragma GCC diagnostic ignored "-Wunused-parameter"
static void and_(bool canAssign){
	int endJump = emitJump(OP_JUMP_IF_FALSE);
	emitByte(OP_POP); //Jump for short-circuit evaluation
	parsePrecedence(PREC_AND);
	patchJump(endJump);
}


static void expression(){
  parsePrecedence(PREC_ASSIGN);
}

static void block(){
  while(!check(TOKEN_RIGHT_BRACE) && !check(TOKEN_EOF)){
    declaration();
  }
  consume(TOKEN_RIGHT_BRACE, "Expect '}' after block.");
}


static void printStatement(){
  expression();
  consume(TOKEN_SEMICOLON, "Expect ';' after value.");
  emitByte(OP_PRINT);
}

//Return the parser to a valid state after an error
static void synchronize(){
  parser.panicMode = false;

  //Consume and discard code up to the beginning of the next statement
  while(parser.current.type != TOKEN_EOF){
    if(parser.previous.type == TOKEN_SEMICOLON){
      return;
    }

    switch(parser.current.type){
    case TOKEN_CLASS:
    case TOKEN_FUN:
    case TOKEN_VAR:
    case TOKEN_FOR:
    case TOKEN_IF:
    case TOKEN_WHILE:
    case TOKEN_PRINT:
    case TOKEN_RETURN:
      return;
    default:
      ;
    }
    advance();
  }
}

//Evaluate an expression for side effects and discard result.
static void expressionStatement(){
    expression();
    emitByte(OP_POP);
    consume(TOKEN_SEMICOLON, "Expect ';' after expression.");
}

static void whileStatement(){
	int loopStart = currentChunk()->count;

	consume(TOKEN_LEFT_PAREN, "Expect '(' after 'while'.)");
	expression();
	consume(TOKEN_RIGHT_PAREN, "Expect ')' after condition.");

	int exitJump = emitJump(OP_JUMP_IF_FALSE);

	emitByte(OP_POP);
	statement();

	emitLoop(loopStart);

	patchJump(exitJump);
	emitByte(OP_POP);
}



static void ifStatement(){
	//Condition
	consume(TOKEN_LEFT_PAREN, "Expect '(' after 'if'.");
	expression();
	consume(TOKEN_RIGHT_PAREN, "Expect ')' after condition.");

	//Then branch
	int thenJump = emitJump(OP_JUMP_IF_FALSE);
	emitByte(OP_POP);
	statement();

	int elseJump = emitJump(OP_JUMP);

	patchJump(thenJump);
	emitByte(OP_POP);

	//Else branch
	if(match(TOKEN_ELSE)){
		statement();
	}
	patchJump(elseJump);


}

static void varDeclaration(){
  uint8_t global = parseVariable("Expect variable name.");
  if(match(TOKEN_EQUAL)){
    expression();
  }else{
    emitByte(OP_NIL);
  }

  consume(TOKEN_SEMICOLON, "Expect ';' after variable declaration.");
  defineVariable(global);
}

static void forStatement(){
	beginScope();

	consume(TOKEN_LEFT_PAREN, "Expect '(' after 'for'.");

	//Initializer
	if(match(TOKEN_VAR)){
		varDeclaration();
	}else if(match(TOKEN_SEMICOLON)){
		//No initializer
	}else{
		expressionStatement();
	}

	int loopStart = currentChunk()->count;

	//Condition
	int exitJump = -1;
	if(!match(TOKEN_SEMICOLON)){
		expression();
		consume(TOKEN_SEMICOLON, "Expect ';' after loop condition.");
		//Check condition
		exitJump = emitJump(OP_JUMP_IF_FALSE);
		emitByte(OP_POP);
	}
		
	//Update
	if(!match(TOKEN_RIGHT_PAREN)){
		//Jump to start of loop body
		int bodyJump = emitJump(OP_JUMP);

		//Compile increment expression
		int incrementStart = currentChunk()->count;
		expression();
		//But we only need the side effects so discard the value
		emitByte(OP_POP);
		consume(TOKEN_RIGHT_PAREN, "Expect ')' after for clauses.");


		emitLoop(loopStart);
		loopStart = incrementStart;
		patchJump(bodyJump);
	}

	statement();

	emitLoop(loopStart);

	if(exitJump != -1){
		patchJump(exitJump);
		emitByte(OP_POP);
	}

	endScope();
}

static void declaration(){
  if(match(TOKEN_VAR)){
    varDeclaration();
  }else{
    statement();
  }

  if(parser.panicMode){
    synchronize();
  }
}

static void statement(){
  if(match(TOKEN_PRINT)){
    printStatement();
  }else if(match(TOKEN_LEFT_BRACE)){
    beginScope();
    block();
    endScope();
  }else if(match(TOKEN_WHILE)){
	  whileStatement();
  }else if(match(TOKEN_FOR)){
	  forStatement();
  }else if(match(TOKEN_IF)){
	  ifStatement();
  }else{
    expressionStatement();
  }
}

#pragma GCC diagnostic ignored "-Wunused-parameter"
static void binary(bool canAssign){
  TokenType opType = parser.previous.type;

  //Compile right operand
  ParseRule *rule = getRule(opType);
  parsePrecedence((Precedence)(rule->precedence + 1));

  switch(opType){
  case TOKEN_PLUS:
    emitByte(OP_ADD);
    break;
  case TOKEN_MINUS:
    emitByte(OP_SUBTRACT);
    break;
  case TOKEN_STAR:
    emitByte(OP_MULTIPLY);
    break;
  case TOKEN_SLASH:
    emitByte(OP_DIVIDE);
    break;
  case TOKEN_BANG_EQUAL:
    emitBytes(OP_EQUAL, OP_NOT);
    break;
  case TOKEN_EQUAL_EQUAL:
    emitByte(OP_EQUAL);
    break;
  case TOKEN_GREATER:
    emitByte(OP_GREATER);
    break;
  case TOKEN_GREATER_EQUAL:
    emitBytes(OP_GREATER, OP_NOT);
    break;
  case TOKEN_LESS:
    emitByte(OP_LESS);
    break;
  case TOKEN_LESS_EQUAL:
    emitBytes(OP_LESS, OP_NOT);
    break;
  default:
    return;
  }
}

//Parse boolean and nil literals
#pragma GCC diagnostic ignored "-Wunused-parameter"
static void literal(bool canAssign){
  switch(parser.previous.type){
  case TOKEN_NIL:
    emitByte(OP_NIL);
    break;
  case TOKEN_TRUE:
    emitByte(OP_TRUE);
    break;
  case TOKEN_FALSE:
    emitByte(OP_FALSE);
    break;
  default:
    return; //Unreachable
  }
}

//Parse a number literal
#pragma GCC diagnostic ignored "-Wunused-parameter"
static void number(bool canAssign){
  double value = strtod(parser.previous.start, NULL);
  emitConstant(NUMBER_VAL(value));
}

//Parse a parenthesized expression
#pragma GCC diagnostic ignored "-Wunused-parameter"
static void grouping(bool canAssign){
  expression();
  consume(TOKEN_RIGHT_PAREN, "Expect ')' after expression");
}

#pragma GCC diagnostic ignored "-Wunused-parameter"
static void unary(bool canAssign){
  TokenType opType = parser.previous.type;

  //Parse operand
  parsePrecedence(PREC_UNARY);

  //Then emit the operator--we're working off of a stack here
  switch(opType){
  case TOKEN_MINUS:
    emitByte(OP_NEGATE);
    break;
  case TOKEN_BANG:
    emitByte(OP_NOT);
    break;
  default:
    return;
  }
}

#pragma GCC diagnostic ignored "-Wunused-parameter"
static void string(bool canAssign){
  emitConstant(OBJ_VAL(copyString(parser.previous.start + 1, parser.previous.length - 2)));
}

static void namedVariable(Token name, bool canAssign){
  int arg = resolveLocal(current, &name);
  uint8_t getOp, setOp;
  if(arg != -1){
    getOp = OP_GET_LOCAL;
    setOp = OP_SET_LOCAL;
  }else{
    arg = identifierConstant(&name);
    getOp = OP_GET_GLOBAL;
    setOp = OP_SET_GLOBAL;
  }
  
  if(canAssign && match(TOKEN_EQUAL)){
    expression();
    emitBytes(setOp, (uint8_t)arg);
  }else{
    emitBytes(getOp, (uint8_t)arg);
  }
}

static void variable(bool canAssign){
  namedVariable(parser.previous, canAssign);
}


//Some things you just have to copy and paste, like this big table of parsing rules
//                     prefix    infix    precedence
ParseRule rules[] = {                                              
		     { grouping, NULL,    PREC_CALL },       // TOKEN_LEFT_PAREN      
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_RIGHT_PAREN     
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_LEFT_BRACE
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_RIGHT_BRACE     
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_COMMA           
		     { NULL,     NULL,    PREC_CALL },       // TOKEN_DOT             
		     { unary,    binary,  PREC_TERM },       // TOKEN_MINUS           
		     { NULL,     binary,  PREC_TERM },       // TOKEN_PLUS            
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_SEMICOLON       
		     { NULL,     binary,  PREC_FACTOR },     // TOKEN_SLASH           
		     { NULL,     binary,  PREC_FACTOR },     // TOKEN_STAR            
		     { unary,     NULL,    PREC_NONE },       // TOKEN_BANG            
		     { NULL,     binary,    PREC_EQ},   // TOKEN_BANG_EQUAL      
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_EQUAL           
		     { NULL,     binary,    PREC_EQ},   // TOKEN_EQUAL_EQUAL     
		     { NULL,     binary,    PREC_CMP}, // TOKEN_GREATER         
		     { NULL,     binary,    PREC_CMP}, // TOKEN_GREATER_EQUAL   
		     { NULL,     binary,    PREC_CMP}, // TOKEN_LESS            
		     { NULL,     binary,    PREC_CMP}, // TOKEN_LESS_EQUAL      
		     { variable,     NULL,    PREC_NONE },       // TOKEN_IDENTIFIER      
		     { string,     NULL,    PREC_NONE },       // TOKEN_STRING          
		     { number,   NULL,    PREC_NONE },       // TOKEN_NUMBER          
		     { NULL,     and_,    PREC_AND },        // TOKEN_AND             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_CLASS           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_ELSE            
		     { literal,     NULL,    PREC_NONE },       // TOKEN_FALSE           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_FOR             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_FUN             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_IF              
		     { literal,     NULL,    PREC_NONE },       // TOKEN_NIL             
		     { NULL,     or_,    PREC_OR },         // TOKEN_OR              
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_PRINT           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_RETURN          
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_SUPER           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_THIS            
		     { literal,     NULL,    PREC_NONE },       // TOKEN_TRUE            
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_VAR             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_WHILE           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_ERROR           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_EOF             
};           

static ParseRule *getRule(TokenType type){
  return &rules[type];
}

bool compile(const char *source, Chunk *chunk){
  initScanner(source);
  Compiler compiler;
  initCompiler(&compiler);

  compilingChunk = chunk;
  parser.hadError = false;
  parser.panicMode = false;

  advance();

  while(!match(TOKEN_EOF)){
    declaration();
  }
  endCompiler();

  return !parser.hadError;
}
