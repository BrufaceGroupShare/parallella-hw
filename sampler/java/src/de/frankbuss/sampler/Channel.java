package de.frankbuss.sampler;

import java.awt.*;
import java.awt.geom.*;

public abstract class Channel {
	protected Client client;
	protected String name = "Test";

	public Channel(Client client, String name) {
		this.client = client;
		this.name = name;
	}

	public abstract void draw(Graphics g, int y0, int width, int height);
	
	public String getLabel() {
		return name;
	}

	public Color getColor() {
		return Color.WHITE;
	}

	public void drawLabel(Graphics g, int x0, int y0, int width, int height) {
		g.setColor(getColor());
		
		g.drawRect(x0+10,  y0, width-20, height);
		Utils.drawTextInRect(g, x0, y0, width, height, getLabel());
	}

	public void onMouseClick() {
	}

}
