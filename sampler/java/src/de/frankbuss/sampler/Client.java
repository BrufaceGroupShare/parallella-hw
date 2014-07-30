package de.frankbuss.sampler;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.net.*;
import java.text.*;
import java.util.*;

import javax.swing.*;
import javax.swing.border.*;

import java.io.*;

public class Client extends JFrame {
	private JLabel statusLabel;
	private RunStopAction runStopAction;
	private SingleAction singleAction;
	private CursorAction cursor1Action;
	private CursorAction cursor2Action;
	private ImageIcon runStopOffImage;
	private ImageIcon runStopRunningImage;
	private ImageIcon runStopStoppedImage;
	private ImageIcon singleArmedImage;
	private ImageIcon singleOffImage;
	private ImageIcon time0Image;
	private ImageIcon cursorOffImage;

	private ChannelsView channelsView;
	private TimeLineView timeLineView;
	private ChannelLabelsView channelLabelsView;

	private ArrayList<Channel> channels;
	private SampleData sampleData;

	private double timePosition = -123e-9;
	private double timePerDivision = 200e-9;
	private double divisionsPerScreen = 10;
	private double[] zoomLevels = { 1e-9, 2e-9, 5e-9, 10e-9, 20e-9, 50e-9, 100e-9, 200e-9, 500e-9, 1e-6, 2e-6, 5e-6, 10e-6, 20e-6, 50e-6, 100e-6, 200e-6,
			500e-6, 1e-3, 2e-3, 5e-3, 10e-3, 20e-6, 50e-3, 100e-3, 200e-3, 500e-3, 1, 2, 5, 10, 20, 50, 100, 200, 500 };
	private int currentZoomLevelIndex = 7;

	private int lastSelectedCursor;
	private double cursor1time;
	private double cursor2time;

	private long risingTrigger = 0;
	private long fallingTrigger = 0;
	private boolean triggerUpdated;

	class RunStopAction extends ToolbarAction {
		private boolean on = true;
		private boolean running = false;

		@Override
		public void actionPerformed(ActionEvent aArg0) {
			if (on) {
				running = !running;
			} else {
				on = true;
				running = false;
			}
			singleAction.off();
			updateImage();
			if (running) {
				clearSampleData();
			}
		}

		@Override
		public ImageIcon getImage() {
			if (on) {
				if (running) {
					return runStopRunningImage;
				} else {
					return runStopStoppedImage;
				}
			} else {
				return runStopOffImage;
			}
		}

		public void stop() {
			running = false;
			on = true;
			updateImage();
		}

		public void run() {
			running = true;
			on = true;
			updateImage();
		}

		public void off() {
			running = false;
			on = false;
			updateImage();
		}

		public boolean isRunning() {
			return running;
		}
	};

	class SingleAction extends ToolbarAction {
		private boolean armed = false;

		@Override
		public void actionPerformed(ActionEvent aArg0) {
			if (!armed) {
				armed = true;
				runStopAction.off();
				clearSampleData();
			}
			updateImage();
		}

		@Override
		public ImageIcon getImage() {
			if (armed) {
				return singleArmedImage;
			} else {
				return singleOffImage;
			}
		}

		public void arm() {
			armed = true;
			updateImage();
		}

		public void off() {
			armed = false;
			updateImage();
		}

		public boolean isArmed() {
			return armed;
		}
	};

	class ZoomAction extends ToolbarAction {
		private ImageIcon image;
		private boolean plus;

		public ZoomAction(ImageIcon image, boolean plus) {
			this.image = image;
			this.plus = plus;
		}

		@Override
		public void actionPerformed(ActionEvent aArg0) {
			if (plus) {
				if (currentZoomLevelIndex > 0) {
					currentZoomLevelIndex--;
				}
			} else {
				if (currentZoomLevelIndex < zoomLevels.length) {
					currentZoomLevelIndex++;
				}
			}

			// calculate old center
			double timePerScreen = divisionsPerScreen * timePerDivision;
			double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
			double oldCenterTime = timePosition + timePerScreen / 2.0;

			// set new zoom factor
			timePerDivision = zoomLevels[currentZoomLevelIndex];

			// update time position so the the center position doesn't change
			timePerScreen = divisionsPerScreen * timePerDivision;
			timePerPixel = timePerScreen / ((double) channelsView.getWidth());
			timePosition = oldCenterTime - timePerScreen / 2.0;

			updateStatus();
			repaint();
		}

		@Override
		public ImageIcon getImage() {
			return image;
		}
	};

	class CursorAction extends ToolbarAction {
		private ImageIcon onImage;
		private ImageIcon offImage;
		private int index;
		private boolean on;

		public CursorAction(ImageIcon onImage, ImageIcon offImage, int index) {
			this.onImage = onImage;
			this.offImage = offImage;
			this.index = index;
		}

		@Override
		public void actionPerformed(ActionEvent aArg0) {
			on = true;
			onCursor(index);
			updateImage();
		}

		@Override
		public ImageIcon getImage() {
			return on ? onImage : offImage;
		}

		public void off() {
			on = false;
			updateImage();
		}

		public boolean isOn() {
			return on;
		}
	};

	class CursorOffAction extends ToolbarAction {
		@Override
		public void actionPerformed(ActionEvent aArg0) {
			cursor1Action.off();
			cursor2Action.off();
			updateStatus();
			repaint();
		}

		@Override
		public ImageIcon getImage() {
			return cursorOffImage;
		}
	};

	class Time0Action extends ToolbarAction {
		@Override
		public void actionPerformed(ActionEvent aArg0) {
			double timePerScreen = divisionsPerScreen * timePerDivision;
			timePosition = -timePerScreen / 2;
			updateStatus();
			repaint();
		}

		@Override
		public ImageIcon getImage() {
			return time0Image;
		}
	};

	public Client() {
		setTitle("Sampler Client");
		setLocationByPlatform(true);

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setSize(1200, 800);
		setIconImage(createImageIcon("icons/ProgramIcon.png").getImage());

		JPanel statusPanel = new JPanel();
		statusPanel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
		statusPanel.setPreferredSize(new Dimension(getWidth(), 20));
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusLabel = new JLabel(" ");
		statusLabel.setFont(statusLabel.getFont().deriveFont(15.0f));
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		statusPanel.add(statusLabel);

		JToolBar toolbar = new JToolBar();
		toolbar.setRollover(true);
		toolbar.setFloatable(false);

		runStopOffImage = createImageIcon("icons/RunStopOff.png");
		runStopRunningImage = createImageIcon("icons/RunStopRunning.png");
		runStopStoppedImage = createImageIcon("icons/RunStopStopped.png");
		singleArmedImage = createImageIcon("icons/SingleArmed.png");
		singleOffImage = createImageIcon("icons/SingleOff.png");
		time0Image = createImageIcon("icons/Time0.png");
		cursorOffImage = createImageIcon("icons/CursorOff.png");

		runStopAction = new RunStopAction();
		singleAction = new SingleAction();

		toolbar.add(runStopAction);
		toolbar.add(singleAction);
		toolbar.addSeparator();
		toolbar.add(new ZoomAction(createImageIcon("icons/ZoomPlus.png"), true));
		toolbar.add(new ZoomAction(createImageIcon("icons/ZoomMinus.png"), false));
		Time0Action time0 = new Time0Action();
		toolbar.add(time0);
		toolbar.addSeparator();
		cursor1Action = new CursorAction(createImageIcon("icons/Cursor1On.png"), createImageIcon("icons/Cursor1Off.png"), 0);
		cursor2Action = new CursorAction(createImageIcon("icons/Cursor2On.png"), createImageIcon("icons/Cursor2Off.png"), 1);
		toolbar.add(cursor1Action);
		toolbar.add(cursor2Action);
		toolbar.add(new CursorOffAction());

		channelsView = new ChannelsView(this);
		timeLineView = new TimeLineView(this);
		channelLabelsView = new ChannelLabelsView(this);

		JPanel mainView = new JPanel(new BorderLayout());
		JPanel timeLineChannelsView = new JPanel(new BorderLayout());
		mainView.add(channelLabelsView, BorderLayout.WEST);
		timeLineChannelsView.add(timeLineView, BorderLayout.NORTH);
		timeLineChannelsView.add(channelsView, BorderLayout.CENTER);
		mainView.add(timeLineChannelsView, BorderLayout.CENTER);

		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.add(toolbar, BorderLayout.NORTH);
		contentPane.add(mainView, BorderLayout.CENTER);
		contentPane.add(statusPanel, BorderLayout.SOUTH);

		final int bytesPerSample = 8;
		clearSampleData();

		channels = new ArrayList<Channel>();

		channels.add(new ChannelSeparator(this, "address/data"));
		channels.add(new DigitalSignal(this, "S02", 34));
		channels.add(new DigitalSignal(this, "RW", 38));
		channels.add(new DigitalSignal(this, "BA", 36));
		channels.add(new Bus(this, "address", new int[] { 10, 13, 12, 15, 14, 17, 16, 19, 18, 21, 20, 23, 22, 25, 24, 27 }));
		channels.add(new Bus(this, "data", new int[] { 26, 29, 28, 1, 30, 33, 32, 35 }));

		channels.add(new ChannelSeparator(this, "cartridge"));
		channels.add(new DigitalSignal(this, "ROML", 42));
		channels.add(new DigitalSignal(this, "ROMH", 45));
		channels.add(new DigitalSignal(this, "IO1", 7));
		channels.add(new DigitalSignal(this, "IO2", 43));
		channels.add(new DigitalSignal(this, "GAME", 41));
		channels.add(new DigitalSignal(this, "EXROM", 40));

		channels.add(new ChannelSeparator(this, "Kerberos"));
		channels.add(new DigitalSignal(this, "RAM cs", 11));
		channels.add(new DigitalSignal(this, "flash cs", 46));

		channels.add(new ChannelSeparator(this, "other"));
		channels.add(new DigitalSignal(this, "dot clock", 4));
		channels.add(new DigitalSignal(this, "NMI", 47));
		channels.add(new DigitalSignal(this, "IRQ", 39));
		channels.add(new DigitalSignal(this, "RESET", 44));
		channels.add(new DigitalSignal(this, "DMA", 37));

		for (int i = 0; i < 48; i++) {
			//channels.add(new DigitalSignal(this, i + " XXXXXXXXXX", i));
		}
		//		channels.add(new DigitalSignal(this, "game", 25));
		//		channels.add(new DigitalSignal(this, "exrom", 26));

		updateStatus();
		time0.actionPerformed(null);

		setContentPane(contentPane);
		setVisible(true);

		new Thread() {
			private boolean running = false;

			@Override
			public void run() {
				try {
					Socket socket = new Socket("192.168.11.105", 5000);
					OutputStream out = socket.getOutputStream();
					InputStream in = socket.getInputStream();
					while (true) {
						if (!running || triggerUpdated) {
							// send trigger and start sampling
							byte[] send = new byte[17];
							int j = 0;
							send[j++] = 0x02;
							long rising = risingTrigger;
							for (int i = 0; i < 8; i++) {
								send[j++] = (byte) (rising & 0xff);
								rising >>>= 8;
							}
							long falling = fallingTrigger;
							for (int i = 0; i < 8; i++) {
								send[j++] = (byte) (falling & 0xff);
								falling >>>= 8;
							}
							out.write(send);
							running = true;
							triggerUpdated = false;
						}

						// read sample status and data
						out.write(0x01);
						boolean timeout = in.read() == 0;
						byte[] samples = new byte[262144];
						int bytesToRead = 262144;
						int start = 0;
						while (true) {
							int len = in.read(samples, start, bytesToRead);
							bytesToRead -= len;
							if (bytesToRead == 0)
								break;
							start += len;
						}
						if (!timeout) {
							if (runStopAction.isRunning() || singleAction.isArmed()) {
								double sampleTimeHalf = 32768.0 / 100e6 / 2.0;
								sampleData = new SampleData(samples, bytesPerSample, -sampleTimeHalf, sampleTimeHalf);
								if (singleAction.isArmed()) {
									runStopAction.stop();
									singleAction.off();
								}
								repaint();
							}
							running = false;
						}
						if (runStopAction.isRunning()) {
							running = false;
						}
						sleep(100);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();
	}

	protected ImageIcon createImageIcon(String path) {
		java.net.URL imgURL = getClass().getResource(path);
		if (imgURL != null) {
			return new ImageIcon(imgURL);
		} else {
			System.err.println("file not found: " + path);
			return null;
		}
	}

	public ArrayList<Channel> getChannels() {
		return channels;
	}

	public static void main(String args[]) {
		new Client();
	}

	public int getChannelHeight() {
		return 40;
	}

	public int getSignalHeight() {
		return 16;
	}

	public Color getDiagramBackground() {
		return Color.BLACK;
	}

	public int getTimelineHeight() {
		return 50;
	}

	public SampleData getSampleData() {
		return sampleData;
	}

	public boolean isHighAtPixel(int x, int bitPosition) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
		double timeAtPixel = timePosition + timePerPixel * ((double) x);

		double timePerSample = (sampleData.getEndTime() - sampleData.getStartTime()) / ((double) sampleData.getSampleCount());
		int sampleIndex = (int) ((timeAtPixel - sampleData.getStartTime()) / timePerSample);
		if (sampleIndex < 0 || sampleIndex >= sampleData.getSampleCount()) {
			return false;
		}
		return sampleData.getBit(sampleIndex, bitPosition);
	}

	public boolean isSignalAtPixel(int x) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
		double timeAtPixel = timePosition + timePerPixel * ((double) x);

		double timePerSample = (sampleData.getEndTime() - sampleData.getStartTime()) / ((double) sampleData.getSampleCount());
		int sampleIndex = (int) ((timeAtPixel - sampleData.getStartTime()) / timePerSample);
		return sampleIndex >= 0 && sampleIndex < sampleData.getSampleCount() - 40; // TODO: wrong values for the last few samples
	}

	public Color getMajorTickColor() {
		return Color.WHITE;
	}

	private boolean isTickAtPixel(int x, double division) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
		double startTime = Math.round(timePosition / timePerDivision) * timePerDivision;
		double pixelOffset = (timePosition - startTime) / timePerPixel;
		double pixelsPerDivision = ((double) channelsView.getWidth()) / division;
		for (int i = (int) -division; i < division + division; i++) {
			double xd = pixelsPerDivision * ((double) i) - pixelOffset;
			if ((int) xd == x)
				return true;
		}
		return false;
	}

	public boolean isMinorTickAtPixel(int x) {
		return isTickAtPixel(x, divisionsPerScreen * 10);
	}

	public boolean isMajorTickAtPixel(int x) {
		return isTickAtPixel(x, divisionsPerScreen);
	}

	public double getTimeAtPixel(int x) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
		return timePosition + ((double) x) * timePerPixel;
	}

	public int getPixelAtTime(double time) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double width = ((double) channelsView.getWidth());
		double timePerPixel = timePerScreen / width;
		double pixel = (time - timePosition) / timePerPixel;
		if (pixel > width)
			pixel = width + 1;
		if (pixel < 0)
			pixel = -2;
		return (int) pixel;
	}

	public boolean isCursorAtPixel(int x) {
		if (cursor1Action.isOn()) {
			if (getPixelAtTime(cursor1time) == x) {
				return true;
			}
		}
		if (cursor2Action.isOn()) {
			if (getPixelAtTime(cursor2time) == x) {
				return true;
			}
		}
		return false;
	}

	public String getMajorTickTextAtPixel(int x) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
		double startTime = Math.round(timePosition / timePerDivision) * timePerDivision;
		double pixelOffset = (timePosition - startTime) / timePerPixel;
		double pixelsPerDivision = ((double) channelsView.getWidth()) / divisionsPerScreen;
		for (int i = -1; i < divisionsPerScreen + 1; i++) {
			double xd = pixelsPerDivision * ((double) i) - pixelOffset;
			if ((int) xd == x) {
				double time = startTime + timePerDivision * ((double) i);
				return Utils.formatTime(time);
			}
		}
		return "";
	}

	public void dragChannels(int dx) {
		double timePerScreen = divisionsPerScreen * timePerDivision;
		double timePerPixel = timePerScreen / ((double) channelsView.getWidth());
		timePosition += timePerPixel * ((double) dx);
		repaint();
	}

	public void updateStatus() {
		String cursorText = "";
		if (cursor1Action.isOn() && cursor2Action.isOn()) {
			cursorText = ", cursor dt: " + Utils.formatTime(Math.abs(cursor1time - cursor2time));
		}
		statusLabel.setText(" time per division: " + Utils.formatTime(timePerDivision) + cursorText);
	}

	public void updateTrigger() {
		risingTrigger = 0;
		fallingTrigger = 0;
		for (Channel c : channels) {
			if (c instanceof DigitalSignal) {
				DigitalSignal ds = (DigitalSignal) c;
				if (ds.getTrigger() == DigitalSignal.TRIGGER_RISING_EDGE) {
					risingTrigger |= 1l << ds.getBitPosition();
				}
				if (ds.getTrigger() == DigitalSignal.TRIGGER_FALLING_EDGE) {
					fallingTrigger |= 1l << ds.getBitPosition();
				}
			}
		}
		triggerUpdated = true;
	}

	public void clearSampleData() {
		sampleData = new SampleData(new byte[0], 8, 0, 1);
		repaint();
	}

	public void onCursor(int index) {
		lastSelectedCursor = index;
	}

	public void setCursorTime(int x) {
		if (lastSelectedCursor == 0 && cursor1Action.isOn()) {
			cursor1time = getTimeAtPixel(x);
		}
		if (lastSelectedCursor == 1 && cursor2Action.isOn()) {
			cursor2time = getTimeAtPixel(x);
		}
		updateStatus();
		repaint();
	}
}
