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

/* reset analyzer */
#define CMD_RESET 0x00
/* arm trigger / run device */
#define CMD_RUN 0x01
/* ask for device id */
#define CMD_ID 0x02
/* ask for device meta data. */
#define CMD_METADATA 0x04
/* ask the device to immediately return its RLE-encoded data. */
#define CMD_RLE_FINISH_NOW 0x05

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

int main(int argc, char **argv)
{
	char rxbuffer[1024];
	char txbuffer[4096];
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
	driverTest();
	return 0;

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
			for (i = 0; i < retval; i++) {
				printf("  byte received from OLS client: %02x", (unsigned char)rxbuffer[i]);
				lastByte = (unsigned char)rxbuffer[i];
				if ((longCommand == false) && (lastByte & 0x80)) {
					longCommand = true;
					longCommandBytecount = 0;
					longCommandData[longCommandBytecount++] = lastByte;
				} else if (longCommand == true) {
					longCommandData[longCommandBytecount++] = lastByte;
					if (longCommandBytecount == 5) {
						longCommand = false;
						if (longCommandData[0] == 0x81) { //size
							sampleSize = 4 * ((int)longCommandData[2] * 256 + longCommandData[1] + 1);
							printf(" Sample size = %d", sampleSize);
						} else if (longCommandData[0] == 0x84) { //extended size
							sampleSize = 4 * ((int)longCommandData[4] * 256 * 256 * 256 + (int)longCommandData[3] * 256 * 256 + (int)longCommandData[2] * 256 + longCommandData[1] + 1);
							printf(" Sample size = %d", sampleSize);
						} else if (longCommandData[0] == 0x82) { //channel disable
							chEnable0 = (~longCommandData[1] >> 2) & 0x1;
							chEnable1 = (~longCommandData[1] >> 3) & 0x1;
							chEnable2 = (~longCommandData[1] >> 4) & 0x1;
							chEnable3 = (~longCommandData[1] >> 5) & 0x1;
							sampleBytes = chEnable0 + chEnable1 + chEnable2 + chEnable3;
							printf(" Bytes per sample = %d", sampleBytes);
						}
					}
				}
				printf("\n");
			}

			if (lastByte == CMD_ID) { // ID request
				printf("Server: Requesting ID data.\n");
				txbuffer[0] = 0x31;
				txbuffer[1] = 0x41;
				txbuffer[2] = 0x4c;
				txbuffer[3] = 0x53;
				retval = send(msgsock, txbuffer, 4, 0);
				if (retval < 0) {
					fprintf(stderr,"Server: send() failed: error %s\n", strerror(errno));
				} else {
					printf("Server: send() is OK.\n");
				}
			} else if (lastByte == CMD_METADATA) { // metadata request
				int c = 0;
				const char* deviceType = "Parallella Analyzer";
				int samplememory = 0x10000;
				int samplerate = 100*1000*1000;
				printf("Server: Requesting meta data.\n");
				/* device name */
				txbuffer[c++] = 0x01;
				while (*deviceType) {
					txbuffer[c++] = *deviceType;
					deviceType++;
				}
				txbuffer[c++] = 0x00;

				/* firmware version */
				txbuffer[c++] = 0x02;
				txbuffer[c++] = '0';
				txbuffer[c++] = '.';
				txbuffer[c++] = '1';
				txbuffer[c++] = 0x00;

				/* sample memory */
				txbuffer[c++] = 0x21;
				txbuffer[c++] = 0x00;
				txbuffer[c++] = 0x00;
				/* 7168 bytes */
				txbuffer[c++] = samplememory >> 8;
				txbuffer[c++] = samplememory & 0xff;

				/* sample rate (4MHz) */
				txbuffer[c++] = 0x23;
				txbuffer[c++] = samplerate >> 24;
				txbuffer[c++] = (samplerate >> 16) & 0xff;
				txbuffer[c++] = (samplerate >> 8) & 0xff;
				txbuffer[c++] = samplerate & 0xff;

				/* number of probes: 48 */
				txbuffer[c++] = 0x40;
				txbuffer[c++] = 8;

				/* protocol version (2 */
				txbuffer[c++] = 0x41;
				txbuffer[c++] = 0x02;

				/* end of data */
				txbuffer[c++] = 0x00;

				retval = send(msgsock, txbuffer, c, 0);
				if (retval < 0) {
					fprintf(stderr,"Server: send() failed: error %s\n", strerror(errno));
				} else {
					printf("Server: send() is OK.\n");
					close(msgsock);
					break;
				}
			} else if (lastByte == CMD_RUN) { // trigger request
				request_size = 1024;
				bytes_left = sampleBytes * sampleSize;
				printf("Server: Requesting logger data (%d bytes).\n", bytes_left);
				while (1) {
					retval = send(msgsock, txbuffer, request_size, 0);
					if (retval < 0) {
						fprintf(stderr,"Server: send() failed: error %s\n", strerror(errno));
					}
					bytes_left -= request_size;
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
