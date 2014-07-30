package de.frankbuss.sampler;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;

public class ChannelLabelsView extends JPanel implements MouseListener {

	private Client client;

	public ChannelLabelsView(Client client) {
		this.client = client;
		setBackground(client.getDiagramBackground());
		addMouseListener(this);
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Utils.setAntiAlias(g);
		ArrayList<Channel> channels = client.getChannels();
		int y = client.getTimelineHeight();
		for (Channel channel : channels) {
			g.setColor(channel.getColor());
			channel.drawLabel(g, 0, y, getWidth(), client.getChannelHeight());
			y += client.getChannelHeight();
		}
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(200, 10);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(200, Integer.MAX_VALUE);
	}

	@Override
	public Dimension getPreferredSize() {
		return getMaximumSize();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		ArrayList<Channel> channels = client.getChannels();
		int y = client.getTimelineHeight();
		for (Channel channel : channels) {
			Rectangle r = new Rectangle(0, y, getWidth(), client.getChannelHeight());
			if (r.contains(e.getX(), e.getY())) {
				channel.onMouseClick();
				repaint();
				return;
			}
			y += client.getChannelHeight();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseEntered(MouseEvent aArg0) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseExited(MouseEvent aArg0) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mousePressed(MouseEvent aArg0) {
		// TODO Auto-generated method stub

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseReleased(MouseEvent aArg0) {
		// TODO Auto-generated method stub

	}

}
