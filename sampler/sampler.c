#include <linux/device.h>
#include <linux/fs.h>
#include <linux/errno.h>
#include <linux/kernel.h>
#include <linux/platform_device.h>
#include <linux/types.h>
#include <linux/cdev.h>
#include <linux/dma-mapping.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/uaccess.h>
#include "sampler.h"

MODULE_LICENSE("Dual BSD/GPL");
MODULE_AUTHOR("Frank Buss");
MODULE_DESCRIPTION("driver for Zynq high speed 64 channels sampler entity with trigger");

#define MAX_MINOR 1
#define DRVNAME "sampler"

static int major;
static struct class *sampler_class = NULL;
static struct cdev sampler_cdev;
static struct platform_device *pdev;

void* dma_vbase;
dma_addr_t dma_handle;
int alloc_size = 1024*1024*1;

uint32_t base = 0x40000000;
uint32_t size = 0x2000000;
volatile uint32_t* samplerMem;
volatile uint32_t* regs;
struct resource* res;

static int sampler_open(struct inode *inode, struct file *file)
{
	return nonseekable_open(inode, file);
}

static int sampler_release(struct inode *inode, struct file *file)
{
	return 0;
}

static long my_ioctl(struct file *f, unsigned int cmd, unsigned long arg)
{
	sampler_mem_t mem;

	switch (cmd) {
	case SAMPLER_READ_MEM:
		if (copy_from_user(&mem, (sampler_mem_t*)arg, sizeof(sampler_mem_t))) {
			return -EACCES;
		}
		mem.value = ioread32(&samplerMem[mem.address]);
		if (copy_to_user((uint32_t*)arg, &mem, sizeof(sampler_mem_t))) {
			return -EACCES;
		}
		break;
	case SAMPLER_READ_REG:
		if (copy_from_user(&mem, (sampler_mem_t*)arg, sizeof(sampler_mem_t))) {
			return -EACCES;
		}
		mem.value = ioread32(&regs[mem.address]);
		if (copy_to_user((uint32_t*)arg, &mem, sizeof(sampler_mem_t))) {
			return -EACCES;
		}
		break;
	case SAMPLER_SET_REG:
		if (copy_from_user(&mem, (sampler_mem_t*)arg, sizeof(sampler_mem_t))) {
			return -EACCES;
		}
		iowrite32(mem.value, &regs[mem.address]);
		break;
	default:
		return -EINVAL;
	}

	return 0;
}

static const struct file_operations sampler_fileops = {
	.owner   = THIS_MODULE,
	.open    = sampler_open,
	.release = sampler_release,
	.llseek  = no_llseek,
	.unlocked_ioctl = my_ioctl
};

static int __init sampler_init(void)
{
	int rc;
	dev_t devid;
	struct device *device = NULL;

	// map 32 MB memory area
	res = request_mem_region(base, size, "sampler");
	if (!res) {
		printk("request_mem_region failed\n");
		return -ENOMEM;
	}
	samplerMem = (volatile uint32_t*) ioremap_nocache(base, size);
	regs = &samplerMem[size / 8];
	if (!regs) {
		printk("ioremap_nocache failed\n");
		return -ENOMEM;
	}

	// create device
	pdev = platform_device_alloc(DRVNAME, 0);
	if (!pdev)
		return -ENOMEM;

	rc = platform_device_add(pdev);
	if (rc) {
		platform_device_put(pdev);
		return rc;
	}

	rc = alloc_chrdev_region(&devid, 0, MAX_MINOR, "sampler");
	major = MAJOR(devid);
	if (rc < 0) {
		dev_err(&pdev->dev, "sampler chrdev_region err: %d\n", rc);
		platform_device_del(pdev);
		platform_device_put(pdev);
		return rc;
	}

	sampler_class = class_create(THIS_MODULE, DRVNAME);
	if (IS_ERR(sampler_class)) {
		dev_err(&pdev->dev, "sampler chrdev_region err: %d\n", rc);
		platform_device_del(pdev);
		platform_device_put(pdev);
		return -ENOMEM;
	}

	cdev_init(&sampler_cdev, &sampler_fileops);
	cdev_add(&sampler_cdev, devid, MAX_MINOR);

	device = device_create(sampler_class, NULL, MKDEV(major, 0), NULL, DRVNAME);
	if (IS_ERR(device)) {
		dev_err(&pdev->dev, "device_create failed\n");
		return -ENOMEM;
	}

	printk(KERN_INFO "sampler module loaded, hardware version: %08x\n", ioread32(&regs[VERSION]));
	return 0;    // Non-zero return means that the module couldn't be loaded.
}

static void __exit sampler_cleanup(void)
{
	iounmap((void*) samplerMem);
	release_mem_region(base, size);

	device_destroy(sampler_class, MKDEV(major, 0));
	cdev_del(&sampler_cdev);
	if (sampler_class) class_destroy(sampler_class);
	unregister_chrdev_region(MKDEV(major, 0), MAX_MINOR);
	platform_device_unregister(pdev);

	printk(KERN_INFO "sample cleanup\n");
}

module_init(sampler_init);
module_exit(sampler_cleanup);
