package de.frankbuss.sampler;

import java.awt.*;
import java.util.*;

public class DigitalSignal extends Channel {
	public static final int TRIGGER_NONE = 0;
	public static final int TRIGGER_RISING_EDGE = 1;
	public static final int TRIGGER_FALLING_EDGE = 2;
	public static final int TRIGGER_LAST_ENTRY = 3;

	private int trigger;

	private int bitPosition;

	public DigitalSignal(Client client, String name, int bitPosition) {
		super(client, name);
		this.bitPosition = bitPosition;
	}

	public String getLabel() {
		String triggerName = "";
		switch (trigger) {
		case TRIGGER_NONE:
			break;
		case TRIGGER_RISING_EDGE:
			triggerName = "rising";
			break;
		case TRIGGER_FALLING_EDGE:
			triggerName = "falling";
			break;
		}
		if (triggerName.length() > 0) {
			return name + " (trigger: " + triggerName + ")";
		} else {
			return name;
		}
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

		boolean[] data = new boolean[width];
		for (int x = 0; x < width; x++) {
			data[x] = client.isHighAtPixel(x, bitPosition);
		}
		for (int x = 0; x < width - 1; x++) {
			if (client.isSignalAtPixel(x)) {
				int y1 = y0 + (data[x] ? 0 : signalHeight);
				int y2 = y0 + (data[x + 1] ? 0 : signalHeight);
				g.drawLine(x, y1, x, y2);
				g.drawLine(x, y2, x + 1, y2);
			}
		}
	}

	public void onMouseClick() {
		// reset other triggers
		ArrayList<Channel> channels = client.getChannels();
		for (Channel c : channels) {
			if (c instanceof DigitalSignal && c != this) {
				DigitalSignal ds = (DigitalSignal) c;
				ds.setTrigger(TRIGGER_NONE);
			}
		}

		// set next trigger mode
		trigger++;
		if (trigger == TRIGGER_LAST_ENTRY) {
			trigger = 0;
		}
		client.updateTrigger();
	}

	public int getTrigger() {
		return trigger;
	}

	public void setTrigger(int trigger) {
		this.trigger = trigger;
	}

	public int getBitPosition() {
		return bitPosition;
	}
}
