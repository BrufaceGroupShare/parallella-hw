package de.frankbuss.sampler;

import java.awt.*;
import java.util.*;

import javax.swing.*;

public class TimeLineView extends JPanel {

	private Client client;

	public TimeLineView(Client client) {
		this.client = client;
		setBackground(client.getDiagramBackground());
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Utils.setAntiAlias(g);
		int width = getWidth();
		int height = getHeight();
		g.setColor(client.getMajorTickColor());
		for (int x = 0; x < width; x++) {
			if (client.isMajorTickAtPixel(x)) {
				g.drawLine(x, height - 8, x, height);
				int x0 = x - 50;
				int x1 = x + 50;
				if (x0 > 0 && x1 < width - 1) {
					Utils.drawTextInRect(g, x0, 0, x1 - x0, height - 10, client.getMajorTickTextAtPixel(x));
				}
			}
			if (client.isMinorTickAtPixel(x)) {
				g.drawLine(x, height - 4, x, height);
			}
			
			if (client.isCursorAtPixel(x)) {
				int x0 = x - 50;
				int x1 = x + 50;
				Utils.drawTextInRect(g, x0, 10, x1 - x0, height, Utils.formatTime(client.getTimeAtPixel(x)));
			}
		}
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(10, client.getTimelineHeight());
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, client.getTimelineHeight());
	}

	@Override
	public Dimension getPreferredSize() {
		return getMaximumSize();
	}
}
