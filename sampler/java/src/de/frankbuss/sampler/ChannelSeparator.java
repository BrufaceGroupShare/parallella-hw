package de.frankbuss.sampler;

import java.awt.*;

public class ChannelSeparator extends Channel {

	public ChannelSeparator(Client client, String name) {
		super(client, name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void drawLabel(Graphics g, int x0, int y0, int width, int height) {
		g.setColor(getColor());
		
		Utils.drawTextInRect(g, x0, y0 + height / 4, width, height, getLabel());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void draw(Graphics aG, int aY0, int aWidth, int aHeight) {
		
	}

}
