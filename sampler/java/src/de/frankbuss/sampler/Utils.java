package de.frankbuss.sampler;

import java.awt.*;
import java.awt.geom.*;
import java.text.*;

public class Utils {
	private static DecimalFormat formatter;

	public static int getTextWidth(Graphics g, String text) {
		Graphics2D g2d = (Graphics2D) g;
		FontMetrics fm = g2d.getFontMetrics();
		return (int) fm.getStringBounds(text, g2d).getWidth();
	}

	public static void drawTextInRect(Graphics g, int x, int y, int width, int height, String text) {
		Graphics2D g2d = (Graphics2D) g;
		FontMetrics fm = g2d.getFontMetrics();
		Rectangle2D r = fm.getStringBounds(text, g2d);
		x += (width - (int) r.getWidth()) / 2;
		y += (height - (int) r.getHeight()) / 2 + fm.getAscent();
		g.drawString(text, x, y);
	}

	public static String toHex(long data, int digits) {
		String result = Long.toHexString(data);
		while (result.length() < digits)
			result = "0" + result;
		return result;
	}

	public static void setAntiAlias(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	public static String formatTime(double time) {
		if (formatter == null) {
			formatter = new DecimalFormat("0.###");
			DecimalFormatSymbols dfs = new DecimalFormatSymbols();
			dfs.setDecimalSeparator('.');
			formatter.setDecimalFormatSymbols(dfs);
		}
		String unit = "s";
		double scale = 1;
		double absTime = Math.abs(time * 1.01);
		if (absTime < 1e-15) {
			unit = "s";
			scale = 1;
		} else if (absTime < 1e-9) {
			unit = "ps";
			scale = 1e12;
		} else if (absTime < 1e-6) {
			unit = "ns";
			scale = 1e9;
		} else if (absTime < 1e-3) {
			unit = "µs";
			scale = 1e6;
		} else if (absTime < 1) {
			unit = "ms";
			scale = 1e3;
		}
		return formatter.format(time * scale) + " " + unit;
	}

}
