#include <stdio.h>
#include <string.h>

#include "object.h"
#include "memory.h"
#include "value.h"
#include "vm.h"

#define ALLOCATE_OBJ(type, objType) (type*)allocateObject(sizeof(type), objType)

static Obj *allocateObject(size_t size, ObjType type){
  Obj *object = (Obj*)reallocate(NULL, 0, size);
  object->type = type;
  return object;
}

static ObjString *allocateString(char *chars, int length){
  ObjString *string = ALLOCATE_OBJ(ObjString, OBJ_STRING);
  string->chars = chars;
  string->length = length;
  return string;
}

ObjString *copyString(const char *chars, int length){
  char *heapChars = ALLOCATE(char, length + 1);
  memcpy(heapChars, chars, length);
  heapChars[length] = '\0';
  return allocateString(heapChars, length);
}

void printObject(Value value){
  switch(OBJ_TYPE(value)){
  case OBJ_STRING:
    printf("%s", AS_CSTRING(value));
    break;
  }
}

ObjString *takeString(char *chars, int length){
  return allocateString(chars, length);
}
