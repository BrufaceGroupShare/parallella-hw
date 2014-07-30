package de.frankbuss.sampler;

import java.awt.*;

import javax.swing.*;

public abstract class ToolbarAction extends AbstractAction {
	public void updateImage() {
		ImageIcon newValue = (ImageIcon) getValue(AbstractAction.SMALL_ICON);
		firePropertyChange(AbstractAction.SMALL_ICON, null, newValue);
	}

	public abstract ImageIcon getImage();

	@Override
	public Object getValue(String key) {
		if (key.equals(AbstractAction.SMALL_ICON)) {
			return getImage();
		} else {
			return super.getValue(key);
		}
	}
}
