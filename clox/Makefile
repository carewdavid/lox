CC=gcc
CFLAGS = -Wall -Wextra -g -std=c99
EXTRAFLAGS = -g -fsanitize=address -fsanitize=leak -fsanitize=undefined

%.o: %.c
	$(CC) $(CFLAGS) $(EXTAFLAGS) -c $<
lox: main.o memory.o chunk.o debug.o value.o vm.o scanner.o compiler.o object.o table.o
	$(CC) $(CFLAGS) $(EXTAFLAGS) $^ -o lox
clean:
	rm *.o
