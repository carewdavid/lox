#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#include "common.h"
#include "scanner.h"

typedef struct {
  const char *start;
  const char *current;
  int line;
} Scanner;

Scanner scanner;

void initScanner(const char *source){
  scanner.start = source;
  scanner.current = source;
  scanner.line = 1;
}

static bool isAtEnd(){
  return *scanner.current == '\0';
}

static Token makeToken(TokenType type){
  Token token;
  token.type = type;
  token.start = scanner.start;
  token.length = (int)(scanner.current - scanner.start);
  token.line = scanner.line;
  return token;
}

static Token errorToken(const char *msg){
  Token token;
  token.type = TOKEN_ERROR;
  token.start = msg;
  token.length = strlen(msg);
  token.line = scanner.line;
  return token;
}

static char advance(){
  scanner.current++;
  return scanner.current[-1];
}

static char peek(){
  return *scanner.current;
}

static char peekNext(){
  if(isAtEnd()){
    return '\0';
  }else{
    return scanner.current[1];
  }
}

/* Advance input and return true iff next character is expect. Otherwise return false *without consuming any input* */
static bool match(char expect){
  if(isAtEnd()) return false;
  if(*scanner.current != expect) return false;

  scanner.current++;
  return true;
}

/* Characters that can start an identifier */
static bool isAlpha(char c){
  return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z') || c == '_';
}

/* Consume whitespace and comments */
static void skipWhitespace(){
  for(;;){
    char c = peek();
    switch(c){
    case ' ':
    case '\t':
    case '\r':
      advance();
      break;
    case '\n':
      scanner.line++;
      advance();
      break;
    case '/':
      if(peekNext() == '/'){
	while(peek() != '\n' && !isAtEnd()) advance();
      }else{
	return;
      }
      break;
    default:
      return;
    }
  }
}

/* For strings and number literals, we're not going to do any conversions yet. Actually handling where these values go happens later,
   so for now we just save the source text. */
static Token string(){
  while(peek() != '"' && !isAtEnd()){
   
    if(peek() == '\n') scanner.line++;
    advance();
  }

  if(isAtEnd()){
    return errorToken("Unterminated string");
  }

  advance();
  return makeToken(TOKEN_STRING);
}

static Token number(){
  while(isdigit(peek())){
      advance();
  }

  /* Watch out for numbers with decimals */
  if(peek() == '.' && isdigit(peekNext())){
    advance();

    while(isdigit(peek())){
      advance();
    }
  }
    
  return makeToken(TOKEN_NUMBER);
}

static TokenType checkKeyword(int start, int length, const char *rest, TokenType type){
  if(scanner.current - scanner.start == start + length && memcmp(scanner.start + start, rest, length) == 0){
    return type;
  }

  return TOKEN_IDENTIFIER;
}

static TokenType identifierType(){
  switch(scanner.start[0]){
  case 'a': return checkKeyword(1, 2, "nd", TOKEN_AND);
  case 'c': return checkKeyword(1, 4, "lass", TOKEN_CLASS);
  case 'e': return checkKeyword(1, 3, "lse", TOKEN_ELSE);
  case 'i': return checkKeyword(1, 1, "f", TOKEN_IF);
  case 'n': return checkKeyword(1, 2, "il", TOKEN_NIL);
  case 'o': return checkKeyword(1, 1, "r", TOKEN_OR);
  case 'p': return checkKeyword(1, 4, "rint", TOKEN_PRINT);
  case 'r': return checkKeyword(1, 5, "eturn", TOKEN_RETURN);
  case 's': return checkKeyword(1, 4, "uper", TOKEN_SUPER);
  case 'v': return checkKeyword(1, 2, "ar", TOKEN_VAR);
  case 'w': return checkKeyword(1, 4, "hile", TOKEN_WHILE);
  case 'f':
    if(scanner.current - scanner.start > 1){
      switch(scanner.start[1]){
      case 'a': return checkKeyword(2, 3, "lse", TOKEN_FALSE);
      case 'o': return checkKeyword(2, 1, "r", TOKEN_FOR);
      case 'u': return checkKeyword(2, 1, "n", TOKEN_FUN);
      }
    }
    break;
  case 't':
    if(scanner.current - scanner.start > 1){
      switch(scanner.start[1]){
      case 'r': return checkKeyword(2, 2, "ue", TOKEN_TRUE);
      case 'h': return checkKeyword(2, 2, "is", TOKEN_THIS);
      }
    }
    break;
  }

  return TOKEN_IDENTIFIER;
}

static Token identifier(){
  while(isAlpha(peek()) || isdigit(peek())){
    advance();
  }

  return makeToken(identifierType());
}

Token scanToken(){
  skipWhitespace();
  
  scanner.start = scanner.current;
  if(isAtEnd()){
    return makeToken(TOKEN_EOF);
  }

  char c = advance();

  if(isdigit(c)){
    return number();
  }

  if(isAlpha(c)){
    return identifier();
  }
  
  switch(c){
  case '(': return makeToken(TOKEN_LEFT_PAREN);
  case ')': return makeToken(TOKEN_RIGHT_PAREN);
  case '{': return makeToken(TOKEN_LEFT_BRACE);
  case '}': return makeToken(TOKEN_RIGHT_BRACE);
  case ';': return makeToken(TOKEN_SEMICOLON);
  case ',': return makeToken(TOKEN_COMMA);
  case '.': return makeToken(TOKEN_DOT);
  case '-': return makeToken(TOKEN_MINUS);
  case '+': return makeToken(TOKEN_PLUS);
  case '/': return makeToken(TOKEN_SLASH);
  case '*': return makeToken(TOKEN_STAR);

  case '!':
    return makeToken(match('=') ? TOKEN_BANG_EQUAL : TOKEN_BANG);
  case '=':
    return makeToken(match('=') ? TOKEN_EQUAL_EQUAL : TOKEN_EQUAL);
  case '<':
    return makeToken(match('=') ? TOKEN_LESS_EQUAL : TOKEN_LESS);
  case '>':
    return makeToken(match('=') ? TOKEN_GREATER_EQUAL : TOKEN_GREATER);

  case '"': return string();
  }

  return errorToken("Unexpected character");
}
