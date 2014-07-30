// network server code based on this code:
// http://forum.gadgetfactory.net/index.php?/topic/1748-open-bench-logic-sniffer-with-64mb-capture-buffer/?p=12736
// ported to Linux by Frank Buss

#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "sampler.h"

#define DEFAULT_PORT 5000
// default TCP socket type
#define DEFAULT_PROTO SOCK_STREAM

#define CMD_READ_BUFFER 0x01
#define CMD_START_SAMPLING 0x02

int sampler;

void Usage(char *progname)
{
	fprintf(stderr,"Usage: %s -p [port_num]\n", progname);
	fprintf(stderr,"Where:\n\t- port_num is the port to listen on\n");
	fprintf(stderr,"\t- Hit Ctrl-C to terminate server program...\n");
	fprintf(stderr,"\t- The defaults port num is 5000.\n");
	exit(1);
}

uint32_t samplerRead(int cmd, uint32_t address)
{
	int retval;
	sampler_mem_t mem;
	mem.address = address;
	retval = ioctl(sampler, cmd, &mem);
	if (retval < 0) {
		fprintf(stderr,"ioctl error: %s\n", strerror(errno));
		exit(-1);
	}
	return mem.value;
}

void samplerWriteReg(uint32_t address, uint32_t value)
{
	int retval;
	sampler_mem_t mem;
	mem.address = address;
	mem.value = value;
	retval = ioctl(sampler, SAMPLER_SET_REG, &mem);
	if (retval < 0) {
		fprintf(stderr,"ioctl error: %s\n", strerror(errno));
		exit(-1);
	}
}

uint32_t samplerReadReg(uint32_t address)
{
	return samplerRead(SAMPLER_READ_REG, address);
}

uint32_t samplerReadMem(uint32_t address)
{
	return samplerRead(SAMPLER_READ_MEM, address);
}

void driverTest()
{
	int i;
	printf("hardware ID and version: %08x\n", samplerReadReg(VERSION));
	
	// stop previous sampling, if running
	samplerWriteReg(CONTROL, 0);

	// disable all triggers
	samplerWriteReg(TRIGGER_MASK_LOW, 0);
	samplerWriteReg(TRIGGER_MASK_HIGH, 0);
	samplerWriteReg(RISING_TRIGGER_LOW, 0);
	samplerWriteReg(RISING_TRIGGER_HIGH, 0);
	samplerWriteReg(FALLING_TRIGGER_LOW, 0);
	samplerWriteReg(FALLING_TRIGGER_HIGH, 0);

	// reset counters
	samplerWriteReg(SAMPLE_COUNTER, 0);
	samplerWriteReg(WRITE_INDEX, 0);
	
	// set samplerate to 33 MHz (100 / (2+1))
	samplerWriteReg(SAMPLERATE_DIVIDER, 2);
	
	// start sampling, with test counter enabled and routed to input
	samplerWriteReg(CONTROL, 13);
	
	// wait a second
	sleep(1);

	// stop sampling
	samplerWriteReg(CONTROL, 0);

	// show memory, delta should be 3
	for (i = 0; i < 10; i++) {
		uint32_t a = samplerReadMem(2*i);
		uint32_t b = samplerReadMem(2*i + 2);
		printf("mem: %i: %08x %08x, delta: %i\n", i, a, b, b-a);
	}
}

void startSampling(uint64_t fallingTrigger, uint64_t risingTrigger)
{
	int i;
	printf("hardware ID and version: %08x\n", samplerReadReg(VERSION));
	
	// stop previous sampling, if running
	samplerWriteReg(CONTROL, 0);

	// set triggers
	samplerWriteReg(TRIGGER_MASK_LOW, 0);
	samplerWriteReg(TRIGGER_MASK_HIGH, 0);
	samplerWriteReg(RISING_TRIGGER_LOW, risingTrigger & 0xffffffff);
	samplerWriteReg(RISING_TRIGGER_HIGH, (risingTrigger >> 32) & 0xffffffff);
	samplerWriteReg(FALLING_TRIGGER_LOW, fallingTrigger & 0xffffffff);
	samplerWriteReg(FALLING_TRIGGER_HIGH, (fallingTrigger >> 32) & 0xffffffff);

	// reset counters
	samplerWriteReg(SAMPLE_COUNTER, 0);
	samplerWriteReg(WRITE_INDEX, 0);
	
	// set samplerate to 100 MHz (100 / (0+1))
	samplerWriteReg(SAMPLERATE_DIVIDER, 0);

	if (risingTrigger == ((uint64_t) 0) && fallingTrigger == ((uint64_t) 0)) {
		// set trigger delay to full sample memory
		samplerWriteReg(TRIGGER_DELAY, 32768);

		// trigger immediatly
		samplerWriteReg(TRIGGER_HOLDOFF, 0);

		// start sampling, with trigger bit triggered, if no trigger was set
		samplerWriteReg(CONTROL, 3);
	} else {
		// set trigger delay to middle of sample memory
		samplerWriteReg(TRIGGER_DELAY, 16384);

		// trigger not before half of the memory is full
		samplerWriteReg(TRIGGER_HOLDOFF, 16384);

		// start sampling
		samplerWriteReg(CONTROL, 1);
	}
}

int main(int argc, char **argv)
{
	char rxbuffer[1024];
	char txbuffer[262144];
	char *ip_address = NULL;
	unsigned short port = DEFAULT_PORT;
	int retval;
	int fromlen;
	int i;
	int socket_type = DEFAULT_PROTO;
	struct sockaddr_in local, from;
	int listen_socket, msgsock;
	int BytesWritten, BytesReceived;
	unsigned char lastByte = 0;
	bool longCommand = false;
	int longCommandBytecount = 0;
	unsigned char longCommandData[5];
	int sampleSize = 0;
	unsigned char chEnable0, chEnable1, chEnable2, chEnable3;
	int sampleBytes = 0;
	int request_size, bytes_left;

	sampler = open("/dev/sampler", O_RDONLY, 0);
	if (sampler < 0) {
		fprintf(stderr,"error opening /dev/sampler: %s\n", strerror(errno));
		return -1;
	}
//	driverTest();
//	return 0;

	// turn off output buffering
	setvbuf(stdout, 0, _IONBF, 0);

	/* Parse arguments, if there are arguments supplied */
	if (argc > 1) {
		for(i=1; i<argc; i++) {
			// switches or options...
			if ((argv[i][0] == '-') || (argv[i][0] == '/')) {
				switch(argv[i][1]) {
				case 'p':
					port = atoi(argv[++i]);
					break;
					// No match...
				default:
					Usage(argv[0]);
					break;
				}
			} else
				Usage(argv[0]);
		}
	}

	if (port == 0) {
		Usage(argv[0]);
	}

	local.sin_family = AF_INET;
	local.sin_addr.s_addr = (!ip_address) ? INADDR_ANY:inet_addr(ip_address);

	/* Port MUST be in Network Byte Order */
	local.sin_port = htons(port);
	// TCP socket
	listen_socket = socket(AF_INET, socket_type,0);

	if (listen_socket < 0) {
		fprintf(stderr,"Server: socket() failed with error %s\n", strerror(errno));
		return -1;
	} else
		printf("Server: socket() is OK.\n");

	if (bind(listen_socket, (struct sockaddr*)&local, sizeof(local)) < 0) {
		fprintf(stderr,"Server: bind() failed with error %s\n", strerror(errno));
		return -1;
	} else
		printf("Server: bind() is OK.\n");

	if (listen(listen_socket,5) < 0) {
		fprintf(stderr,"Server: listen() failed with error %s\n", strerror(errno));
		return -1;
	} else
		printf("Server: listen() is OK.\n");

	printf("Server: I'm listening and waiting connection on port %d\n", port);
	printf("Server: Use CTRL-C to stop server\n");

	while(1) {
		fromlen =sizeof(from);

		msgsock = accept(listen_socket, (struct sockaddr*)&from, &fromlen);
		if (msgsock < 0) {
			fprintf(stderr,"Server: accept() error %s\n", strerror(errno));
			return -1;
		}
		//printf("Server: accepted connection from %s, port %d\n", inet_ntoa(from.sin_addr), htons(from.sin_port)) ;
		printf("Server: accepted connection\n");

		while (1) {
			retval = recv(msgsock, rxbuffer, sizeof(rxbuffer), 0);
			if (retval < 0) {
				fprintf(stderr,"Server: recv() failed: error %s\n", strerror(errno));
				close(msgsock);
				break;
			} else
				printf("Server: recv() is OK.\n");

			if (retval == 0) {
				printf("Server: Client closed connection.\n");
				printf("Server: Use CTRL-C to stop server\n");
				close(msgsock);
				break;
			}
			printf("Server: Received %d bytes from client\n", retval);
			if (rxbuffer[0] == CMD_START_SAMPLING) {
				uint64_t rising = 0;
				uint64_t falling = 0;
				int j = 1;
				printf("bytes received: %d\n", retval);
				for (i = 0; i < 8; i++) {
					rising >>= 8;
					rising |= ((uint64_t)rxbuffer[j++]) << 56;
				}
				for (i = 0; i < 8; i++) {
					falling >>= 8;
					falling |= ((uint64_t)rxbuffer[j++]) << 56;
				}

				// sample 32 k samples
				startSampling(falling, rising);
				
			}
			if (rxbuffer[0] == CMD_READ_BUFFER) {
				char* sendptr = txbuffer;
				uint32_t start;
				
				// wait for end
				for (i = 0; i < 10; i++) {
					if ((samplerReadReg(CONTROL) & 1) == 0) break;
					usleep(10000);
				}
				
				if (samplerReadReg(CONTROL) & 1) {
					// send timeout, if not triggered
					printf("timeout\n");
					txbuffer[0] = 0;
					send(msgsock, txbuffer, 1, 0);
				} else {
					// no timeout
					printf("triggered\n");
					txbuffer[0] = 1;
					send(msgsock, txbuffer, 1, 0);
				}

				// send data
				bytes_left = 262144;
				printf("Server: Requesting logger data (%d bytes).\n", bytes_left);

				start = samplerReadReg(WRITE_INDEX);
				printf("start: %04x\n", start);
				printf("samples: %04x\n", samplerReadReg(SAMPLE_COUNTER));
				printf("holdoff: %04x\n", samplerReadReg(TRIGGER_HOLDOFF));
				printf("delay: %04x\n", samplerReadReg(TRIGGER_DELAY));
				printf("control: %08x\n", samplerReadReg(CONTROL));
				for (i = 0; i < bytes_left / 8; i++) {
					uint32_t a;
					uint32_t b;
					if (start >= 0x8000) start = 0;
					a = samplerReadMem(2*start);
					b = samplerReadMem(2*start + 1);
					start++;
					txbuffer[i * 8] = a & 0xff;
					txbuffer[i * 8 + 1] = (a >> 8) & 0xff;
					txbuffer[i * 8 + 2] = (a >> 16) & 0xff;
					txbuffer[i * 8 + 3] = (a >> 24) & 0xff;
					txbuffer[i * 8 + 4] = b & 0xff;
					txbuffer[i * 8 + 5] = (b >> 8) & 0xff;
					txbuffer[i * 8 + 6] = (b >> 16) & 0xff;
					txbuffer[i * 8 + 7] = (b >> 24) & 0xff;
				}
				while (1) {
					retval = send(msgsock, sendptr, bytes_left, 0);
					if (retval < 0) {
						fprintf(stderr,"Server: send() failed: error %s\n", strerror(errno));
					}
					bytes_left -= retval;
					sendptr += retval;
					if (bytes_left == 0) {
						printf("Server: All logger data sent to OLS client.\n");
						break;
					}
				}
			}
		}
	}
	return 0;
}
