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

static int constantInstruction(char *name, Chunk *chunk, int offset){
  uint8_t constant = chunk->code[offset+1];
  printf("%-16s %4d '", name, constant);
  printValue(chunk->constants.values[constant]);
  printf("'\n");
  return offset + 2;
}
  
int disassembleInstruction(Chunk *chunk, int offset){
  printf("%04d ", offset);
  uint8_t instr = chunk->code[offset];
  switch(instr){
  case OP_RETURN:
    return simpleInstruction("OP_RETURN", offset);
  case OP_CONSTANT:
    return constantInstruction("OP_CONSTANT", chunk, offset);
  default:
    printf("Unknown opcode %d\n", instr);
    return offset + 1;
  }
}
