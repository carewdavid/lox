#include <stdio.h>
#include "debug.h"

void disassembleChunk(Chunk *chunk, const char *name){
  printf("== %s ==\n", name);
  for(int offset = 0; offset < chunk->count;){
    offset = disassembleInstruction(chunk, offset);
  }
}
  
static int simpleInstruction(char *name, int offset){
  printf("%s\n", name);
  return offset + 1;
}
  
int disassembleInstruction(Chunk *chunk, int offset){
  printf("%04d ", offset);
  uint8_t instr = chunk->code[offset];
  switch(instr){
  case OP_RETURN:
    return simpleInstruction("OP_RETURN", offset);
  default:
    printf("Unknown opcode %d\n", instr);
    return offset + 1;
  }
}
