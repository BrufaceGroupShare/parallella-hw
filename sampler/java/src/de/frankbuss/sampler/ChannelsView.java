package de.frankbuss.sampler;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import javax.swing.*;

public class ChannelsView extends JPanel implements MouseMotionListener, MouseListener {

	private Client client;
	private int lastX;

	public ChannelsView(Client client) {
		this.client = client;
		setBackground(client.getDiagramBackground());
		addMouseMotionListener(this);
		addMouseListener(this);
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Utils.setAntiAlias(g);

		// draw cursors
		int width = getWidth();
		g.setColor(Color.YELLOW);
		for (int x = 0; x < width; x++) {
			if (client.isCursorAtPixel(x)) {
				g.drawLine(x, 0, x, getHeight());
			}
		}

		// draw channels
		ArrayList<Channel> channels = client.getChannels();
		int y = 0;
		for (Channel channel : channels) {
			channel.draw(g, y, getWidth(), client.getChannelHeight());
			y += client.getChannelHeight();
		}
	}

	@Override
	public Dimension getMinimumSize() {
		return new Dimension(10, Integer.MAX_VALUE);
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
	}

	@Override
	public Dimension getPreferredSize() {
		return getMaximumSize();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseDragged(MouseEvent e) {
		int dx = lastX - e.getX();
		lastX = e.getX();
		client.dragChannels(dx);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseMoved(MouseEvent e) {
		lastX = e.getX();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		client.setCursorTime(e.getX());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseEntered(MouseEvent aArg0) {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseExited(MouseEvent aArg0) {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mousePressed(MouseEvent aArg0) {
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mouseReleased(MouseEvent aArg0) {
	}
}
