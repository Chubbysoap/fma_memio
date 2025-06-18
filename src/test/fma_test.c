#include <stdint.h>
#include <stdio.h>

#define FMA_BASE_ADDR 0x10000
#define A_REG (FMA_BASE_ADDR + 0x0)
#define B_REG (FMA_BASE_ADDR + 0x4)
#define C_REG (FMA_BASE_ADDR + 0x8)
#define RESULT_REG (FMA_BASE_ADDR + 0xC)
#define BUSY_REG (FMA_BASE_ADDR + 0x10)

void write_reg(uint64_t addr, uint32_t value) {
  volatile uint32_t *ptr = (uint32_t *)addr;
  *ptr = value;
}

uint32_t read_reg(uint64_t addr) {
  volatile uint32_t *ptr = (uint32_t *)addr;
  return *ptr;
}

int main() {
  // Test inputs: a = 2.0, b = 3.0, c = 4.0 (in IEEE 754 32-bit float)
  uint32_t a = 0x40000000; // 2.0
  uint32_t b = 0x40400000; // 3.0
  uint32_t c = 0x40800000; // 4.0

  // Write inputs
  write_reg(A_REG, a);
  write_reg(B_REG, b);
  write_reg(C_REG, c);

  // Wait for computation
  while (read_reg(BUSY_REG)) {}

  // Read result (expected: 2.0 * 3.0 + 4.0 = 10.0 = 0x41200000)
  uint32_t result = read_reg(RESULT_REG);
  printf("Result: 0x%x\n", result);

  return 0;
}