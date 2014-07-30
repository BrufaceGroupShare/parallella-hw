#ifndef SAMPLER_H
#define SAMPLER_H

#include <linux/types.h>

// registers
#define CONTROL 0
#define WRITE_INDEX 1
#define SAMPLERATE_DIVIDER 2
#define RISING_TRIGGER_LOW 3
#define RISING_TRIGGER_HIGH 4
#define FALLING_TRIGGER_LOW 5
#define FALLING_TRIGGER_HIGH 6
#define TRIGGER_MASK_LOW 7
#define TRIGGER_MASK_HIGH 8
#define TRIGGER_PATTERN_LOW 9
#define TRIGGER_PATTERN_HIGH 10
#define TRIGGER_DELAY 11
#define TRIGGER_HOLDOFF 12
#define SAMPLE_COUNTER 13
#define INPUT_STATE_LOW 14
#define INPUT_STATE_HIGH 15
#define TEST_COUNTER 16
#define VERSION 31

// IOCTL commands
#define SAMPLER_READ_MEM 1000
#define SAMPLER_READ_REG 1001
#define SAMPLER_SET_REG 1002

typedef struct
{
	uint32_t address;
	uint32_t value;
} sampler_mem_t;

#endif
