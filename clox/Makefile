CC=gcc
CFLAGS = -Wall -Wextra -g -std=c99

%.o: %.c
	$(CC) $(CFLAGS) -c $<
lox: main.o memory.o chunk.o debug.o value.o vm.o
	$(CC) $(CFLAGS) $^ -o lox
clean:
	rm *.o
