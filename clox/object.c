#include <stdio.h>
#include <string.h>

#include "object.h"
#include "memory.h"
#include "value.h"
#include "vm.h"
#include "table.h"

#define ALLOCATE_OBJ(type, objType) (type*)allocateObject(sizeof(type), objType)

//FNV-1 hash algorithm
static uint32_t hashString(const char *key, int length){
  uint32_t hash = 2166136261u;
  for(int i = 0; i < length; i++){
    hash ^= key[i];
    hash *= 16777619;
  }
  return hash;
}
  
static Obj *allocateObject(size_t size, ObjType type){
  Obj *object = (Obj*)reallocate(NULL, 0, size);
  object->type = type;
  object->next = vm.objects;
  vm.objects = object;
  return object;
}

static ObjString *allocateString(char *chars, int length, uint32_t hash){
  ObjString *string = ALLOCATE_OBJ(ObjString, OBJ_STRING);
  string->chars = chars;
  string->length = length;
  string->hash = hash;
  tableSet(&vm.strings, string, NIL_VAL);
  return string;
}

ObjString *copyString(const char *chars, int length){
  char *heapChars = ALLOCATE(char, length + 1);
  uint32_t hash = hashString(chars, length);
  ObjString *interned = tableFindString(&vm.strings, chars, length, hash);
  if(interned != NULL){
    return interned;
  }
  memcpy(heapChars, chars, length);
  heapChars[length] = '\0';
  return allocateString(heapChars, length, hash);
}

void printObject(Value value){
  switch(OBJ_TYPE(value)){
  case OBJ_STRING:
    printf("%s", AS_CSTRING(value));
    break;
  }
}

ObjString *takeString(char *chars, int length){
  uint32_t hash = hashString(chars, length);
  ObjString *interned = tableFindString(&vm.strings, chars, length, hash);
  if(interned != NULL){
    //We already have a copy of the string; we don't need this one
    FREE_ARRAY(char, chars, length + 1);
    return interned;
  }
  return allocateString(chars, length, hash);
}
