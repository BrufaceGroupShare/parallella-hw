package de.frankbuss.sampler;

import java.awt.*;

public class Bus extends Channel {
	private int[] bitPositions;

	/**
	 * 
	 * Creates a new Bus instance.
	 * @param client
	 * @param name
	 * @param bitPositions Bit positions in one sample. LSB first.
	 */
	public Bus(Client client, String name, int[] bitPositions) {
		super(client, name);
		this.bitPositions = bitPositions;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void draw(Graphics g, int y0, int width, int height) {
		g.setColor(Color.decode("#204020"));
		g.fillRect(0, y0 + 7, width, height - 11);

		g.setColor(Color.GREEN);

		int signalHeight = client.getSignalHeight();
		y0 += (height - signalHeight) / 2;

		long[] data = new long[width];
		for (int x = 0; x < width; x++) {
			for (int i = 0; i < bitPositions.length; i++) {
				if (client.isHighAtPixel(x, bitPositions[i])) {
					data[x] |= 1l << i;
				}
			}
		}
		long lastData = data[0];
		boolean lastSignal = false;
		int lastX = 0;
		int y1 = y0 + signalHeight;
		for (int x = 0; x < width - 1; x++) {
			boolean signal = client.isSignalAtPixel(x);
			if (!signal) {
				lastX = x;
			}
			if (data[x] != lastData && signal || (lastSignal == true && signal == false)) {
				g.drawLine(lastX, y0, x, y0);
				g.drawLine(lastX, y1, x, y1);
				g.drawLine(x, y0, x, y1);
				int w = x - lastX;
				String text = Utils.toHex(lastData, bitPositions.length / 8 * 2);
				if (Utils.getTextWidth(g, text) + 10 < w) {
					Utils.drawTextInRect(g, lastX, y0, x - lastX, signalHeight, text);
				}
				lastData = data[x];
				lastX = x;
			}
			lastSignal = signal;
		}
		if (lastSignal) {
			g.drawLine(lastX, y0, width, y0);
			g.drawLine(lastX, y1, width, y1);

			int w = width - lastX;
			if (w > 100) {
				Utils.drawTextInRect(g, lastX, y0, width - lastX, signalHeight, Utils.toHex(lastData, bitPositions.length / 8 * 2));
			}
		}
	}

}
