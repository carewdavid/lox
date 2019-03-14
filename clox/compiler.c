#include <stdio.h>
#include <stdlib.h>


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

typedef void (*ParseFn)();

typedef struct {
  ParseFn prefix;
  ParseFn infix;
  Precedence precedence;
} ParseRule;

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

/* Parser */
/* (And compiler, really. We do the parsing and emit bytcode all in one pass) */

static void parsePrecedence(Precedence precedence){
  advance();
  ParseFn prefixRule = getRule(parser.previous.type)->prefix;
  if(prefixRule == NULL){
    error("Expect expression");
  }
  prefixRule();

  while(precedence <= getRule(parser.current.type)->precedence){
    advance();
    ParseFn infixRule = getRule(parser.previous.type)->infix;
    infixRule();
  }
}


static void expression(){
  parsePrecedence(PREC_ASSIGN);
}


static void printStatement(){
  expression();
  consume(TOKEN_SEMICOLON, "Expect ';' after value.");
  emitByte(OP_PRINT);
}

//Evaluate an expression for side effects and discard result.
static void expressionStatement(){
    expression();
    emitByte(OP_POP);
    consume(TOKEN_SEMICOLON, "Expect ';' after expression.");
}

static void declaration(){
  statement();
}

static void statement(){
  if(match(TOKEN_PRINT)){
    printStatement();
  }else{
    expressionStatement();
  }
}
static void binary(){
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
static void literal(){
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
static void number(){
  double value = strtod(parser.previous.start, NULL);
  emitConstant(NUMBER_VAL(value));
}

//Parse a parenthesized expression
static void grouping(){
  expression();
  consume(TOKEN_RIGHT_PAREN, "Expect ')' after expression");
}

static void unary(){
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

static void string(){
  emitConstant(OBJ_VAL(copyString(parser.previous.start + 1, parser.previous.length - 2)));
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
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_IDENTIFIER      
		     { string,     NULL,    PREC_NONE },       // TOKEN_STRING          
		     { number,   NULL,    PREC_NONE },       // TOKEN_NUMBER          
		     { NULL,     NULL,    PREC_AND },        // TOKEN_AND             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_CLASS           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_ELSE            
		     { literal,     NULL,    PREC_NONE },       // TOKEN_FALSE           
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_FOR             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_FUN             
		     { NULL,     NULL,    PREC_NONE },       // TOKEN_IF              
		     { literal,     NULL,    PREC_NONE },       // TOKEN_NIL             
		     { NULL,     NULL,    PREC_OR },         // TOKEN_OR              
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
