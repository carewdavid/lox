#include <stdio.h>
#include <stdlib.h>


#include "common.h"
#include "chunk.h"
#include "compiler.h"
#include "scanner.h"
#include "value.h"

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
}

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
  //TODO
}

static void expression(){
  parsePrecedence(PREC_ASSIGN);
}

//Parse a number literal
static void number(){
  double value = strtod(parser.previous.start, NULL);
  emitConstant(value);
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
  default:
    return;
  }
}


bool compile(const char *source, Chunk *chunk){
  initScanner(source);
  parser.hadError = false;
  parser.panicMode = false;

  advance();
  expression();
  consume(TOKEN_EOF, "Expect end of expression");
  endCompiler();

  return !parser.hadError;
}
