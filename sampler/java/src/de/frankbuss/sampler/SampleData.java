package de.frankbuss.sampler;

/**
 * Stores the sample data from the sampler. LSB first, if more than 8 bits per sample.
 */
public class SampleData {
	private byte[] data;
	private int bytesPerSample;
	private double startTime;
	private double endTime;

	public SampleData(byte[] data, int bytesPerSample, double startTime, double endTime) {
		this.data = data;
		this.bytesPerSample = bytesPerSample;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public boolean getBit(int sampleIndex, int bitPosition) {
		int index = sampleIndex * bytesPerSample + bitPosition / 8;
		return (data[index] & (1 << (bitPosition & 7))) > 0;
	}

	public int getSampleCount() {
		return data.length / bytesPerSample;
	}
	
	public double getStartTime() {
		return startTime;
	}

	public double getEndTime() {
		return endTime;
	}
}
