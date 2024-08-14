#include <stdio.h>
#include <stdlib.h>

int main() {
  char buffer[100];
  long number;
  char* endptr;
  if (!fgets(buffer, sizeof(buffer), stdin)) {
    return 0;
  }
  number = strtol(buffer, &endptr, 10);
  if (buffer == endptr) {
    return 0;
  }
  if (*endptr != '\0') {
    return 0;
  }
  return number;
}
