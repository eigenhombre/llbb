#include <stdio.h>
#include <stdlib.h>

int main() {
  char buffer[100];
  if (!fgets(buffer, sizeof(buffer), stdin)) {
    return 0;
  }
  return 1;
}
