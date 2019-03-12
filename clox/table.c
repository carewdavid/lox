#include <stdlib.h>
#include <string.h>

#include "memory.h"
#include "object.h"
#include "table.h"
#include "value.h"

#define TABLE_MAX_LOAD 0.75

void initTable(Table *table){
  table->count = 0;
  table->capacity = 0;
  table->entries = NULL;
}

void freeTable(Table *table){
  FREE_ARRAY(Entry, table->entries, table->capacity);
  initTable(table);
}

//Use linear probing to find a slot for <key>
static Entry *findEntry(Entry *entries, int capacity, ObjString *key){
  uint32_t index = key->hash % capacity;
  Entry *tombstone = NULL;
  
  for(;;){
    Entry *entry = &entries[index];
    if(entry->key == NULL){
      if(IS_NIL(entry->value)){
	return tombstone != NULL ? tombstone : entry;
      }else{
	if(tombstone == NULL){
	  tombstone = entry;
	}
      }
    }else if(entry->key == key){
      return entry;
    }
    index = (index + 1) % capacity;
  }
}

static void adjustCapacity(Table *table, int newCapacity){
  //Allocate and initialize new array of entries
  Entry *entries = ALLOCATE(Entry, newCapacity);
  for(int i = 0; i < newCapacity; i++){
    entries[i].key = NULL;
    entries[i].value = NIL_VAL();
  }

  //Recalculate positions of existing entries
  table->count = 0;
  for(int i = 0; i < table->capacity; i++){
    Entry *entry = &table->entries[i];
    if(entry->key == NULL){
      continue;
    }

    Entry *dest = findEntry(entries, newCapacity, entry->key);
    dest->key = entry->key;
    dest->value = entry->value;
    table->count++;
  }

  FREE_ARRAY(Entry, table->entries, table->capacity);
  table->entries = entries;
  table->capacity = newCapacity;
}

//Insert <value> in the table. Return true is it was already present.
bool tableSet(Table *table, ObjString *key, Value value){
  if(table->count + 1 > table->capacity * TABLE_MAX_LOAD){
    int capacity = GROW_CAPACITY(table->capacity);
    adjustCapacity(table, capacity);
  }
  
  Entry *entry = findEntry(table->entries, table->capacity, key);

  bool isNewKey = entry->key == NULL;
  if(isNewKey && IS_NIL(entry->value)){
    table->count++;
  }

  entry->key = key;
  entry->value = value;
  return isNewKey;
}

void tableAddAll(Table *from, Table *to){
  for(int i = 0; i < from->capacity; i++){
    Entry *entry = &from->entries[i];
    if(entry->key != NULL){
      tableSet(to, entry->key, entry->value);
    }
  }
}

bool tableGet(Table *table, ObjString *key, Value *value){
  if(table->entries == NULL){
    return false;
  }
  Entry *entry = findEntry(table->entries, table->capacity, key);
  if(entry->key == NULL){
    return false;
  }

  *value = entry->value;
  return true;
}

bool tableDelete(Table *table, ObjString *key){
  if(table->count == 0){
    return false;
  }

  Entry *entry = findEntry(table->entries, table->capacity, key);
  if(entry->key == NULL){
    return false;
  }

  entry->key = NULL;
  //Put a placeholder value in so probing still works
  entry->value = BOOL_VAL(true);

  return true;
}

ObjString *tableFindString(Table *table, const char *chars, int length, uint32_t hash){
  if(table->entries == NULL){
    return NULL;
  }

  uint32_t index = hash % table->capacity;

  for(;;){
    Entry *entry = &table->entries[index];
    if(entry->key == NULL){
      if(IS_NIL(entry->value)){
	return NULL;
      }
    }else if(entry->key->length == length &&
	     entry->key->hash == hash &&
	     memcmp(entry->key->chars, chars, length) == 0){
      return entry->key;
    }
    index = (index + 1) % table->capacity;
  }
}
