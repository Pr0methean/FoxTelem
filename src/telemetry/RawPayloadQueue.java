package telemetry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.JOptionPane;

import common.Config;
import common.Log;
import common.Spacecraft;
import common.TlmServer;
import gui.MainWindow;
import telemetry.FoxBPSK.FoxBPSKFrame;

/**
 * 
 * FOX 1 Telemetry Decoder
 * @author chris.e.thompson g0kla/ac2cz
 *
 * Copyright (C) 2015 amsat.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Class to hold a queue of payloads so they can be sent to a local server.  The primary use case is
 * to echo CAN packets to a local copy of COSMOS for UW sat.
 *
 */
public class RawPayloadQueue extends RawQueue {
	public static String RAW_SLOW_SPEED_FRAMES_FILE = "rawDUVpayloads.log";
	public static String RAW_HIGH_SPEED_FRAMES_FILE = "rawHSpayloads.log";
	public static String RAW_PSK_FRAMES_FILE = "rawPSKpayloads.log";
	
	TlmServer localServer;
	
	public RawPayloadQueue() {
		init();
	}
	
	public void init() {
		localServer = new TlmServer(Config.primaryServer, Config.serverPort);
		rawSlowSpeedFrames = new ConcurrentLinkedQueue<Frame>();
		rawHighSpeedFrames = new ConcurrentLinkedQueue<Frame>();
		rawPSKFrames = new ConcurrentLinkedQueue<Frame>();
		try {
			synchronized(this) { // lock will be load the files
				load(RAW_SLOW_SPEED_FRAMES_FILE, Frame.DUV_FRAME);
				load(RAW_HIGH_SPEED_FRAMES_FILE, Frame.HIGH_SPEED_FRAME);
				load(RAW_PSK_FRAMES_FILE, Frame.PSK_FRAME);
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(MainWindow.frame,
					e.toString(),
					"ERROR Raw Frames Queue File not found",
					JOptionPane.ERROR_MESSAGE) ;
			e.printStackTrace(Log.getWriter());
		} catch (IOException e) {
			JOptionPane.showMessageDialog(MainWindow.frame,
					e.toString(),
					"I/O ERROR Loading Stored Raw Frames Queue",
					JOptionPane.ERROR_MESSAGE) ;
			e.printStackTrace(Log.getWriter());
		}
	}
	
	public boolean add(Frame f) throws IOException {
		if (f instanceof SlowSpeedFrame ) {
				updatedSlowQueue = true;
				save(f, RAW_SLOW_SPEED_FRAMES_FILE);
				return rawSlowSpeedFrames.add(f);
			
		} else if (f instanceof FoxBPSKFrame ) {
				updatedPSKQueue = true;
				save(f, RAW_PSK_FRAMES_FILE);
				return rawPSKFrames.add(f);
		} else {
				updatedHSQueue = true;
				save(f, RAW_HIGH_SPEED_FRAMES_FILE);
				return rawHighSpeedFrames.add(f);
		}		
	}
	
	public void delete() {
		try {
			SatPayloadStore.remove(SatPayloadTable.getDir() + RAW_SLOW_SPEED_FRAMES_FILE);
			SatPayloadStore.remove(SatPayloadTable.getDir() + RAW_HIGH_SPEED_FRAMES_FILE);
			SatPayloadStore.remove(SatPayloadTable.getDir() + RAW_PSK_FRAMES_FILE);
			init();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(MainWindow.frame,
					ex.toString(),
					"Error Deleting Local Server Upload Queues, check permissions on files:\n" +
					RAW_SLOW_SPEED_FRAMES_FILE + "\n" +
					RAW_PSK_FRAMES_FILE + "\n" +
					RAW_HIGH_SPEED_FRAMES_FILE,
					JOptionPane.ERROR_MESSAGE) ;
		}

	}
	
	@Override
	public void run() {
		running = true;
		@SuppressWarnings("unused") // might use this, so supress warning
		boolean success = true;
		while(running) {
			
			try {
				Thread.sleep(1000); // refresh data periodically
			} catch (InterruptedException e) {
				Log.println("ERROR: server payload queue thread interrupted");
				e.printStackTrace(Log.getWriter());
			}
			
			// try to send these frames to the server
			// We attempt to send the first one, if unsuccessful, we try the backup server.  If still unsuccessful we drop out
			// and try next time, unless sendToBoth is set, in which case we just send to both servers
			while (rawSlowSpeedFrames.size() > 0) {
				success = sendFrame(rawSlowSpeedFrames, RAW_SLOW_SPEED_FRAMES_FILE);
				try {
					Thread.sleep(100); // pause so that the server can keep up
				} catch (InterruptedException e) {
					Log.println("ERROR: local server DUV frame queue thread interrupted");
					e.printStackTrace(Log.getWriter());
				} 	
			}
			while (rawHighSpeedFrames.size() > 0) {
				success = sendFrame(rawHighSpeedFrames, RAW_HIGH_SPEED_FRAMES_FILE);
				try {
					Thread.sleep(100); // pause so that the server can keep up
				} catch (InterruptedException e) {
					Log.println("ERROR: local server HS frame queue thread interrupted");
					e.printStackTrace(Log.getWriter());
				}
			}
			while (rawPSKFrames.size() > 0) {
				success = sendFrame(rawPSKFrames, RAW_PSK_FRAMES_FILE);
				try {
					Thread.sleep(100); // pause so that the server can keep up
				} catch (InterruptedException e) {
					Log.println("ERROR: server PSK frame queue thread interrupted");
					e.printStackTrace(Log.getWriter());
				}
			}

		}
		Log.println("Local Server Queue thread ended");
	}
	
	private boolean sendFrame(ConcurrentLinkedQueue<Frame> frames, String file) {
		boolean success = false;
		
		int protocol = TlmServer.TCP;
		if (Config.satManager != null)
			try {
				if (frames.peek() != null) {
					Spacecraft sat = Config.satManager.getSpacecraft(frames.peek().foxId);
					if (sat.sendToLocalServer()) {
						localServer.setHostName(sat.localServer);
						localServer.setPort(sat.localServerPort);
						Log.println("Trying Local Server: TCP://" + sat.localServer + ":" + sat.localServerPort);
						byte[][] buffer = frames.peek().getPayloadBytes();
						for (byte[] b : buffer)
							localServer.sendToServer(b, protocol);
						success = true;
					}
				}
			} catch (UnknownHostException e) {
				Log.println("Could not connect to local server");
				//e.printStackTrace(Log.getWriter());
			} catch (IOException e) {
				Log.println("IO Exception with local server");
				//e.printStackTrace(Log.getWriter());
			}
		if (success) // then at least one of the transmissions was successful
			try {
				//Log.println("Sent frame " + frames.get(0).header.toString());
				deleteAndSave(frames, file);
			} catch (IOException e) {
				Log.errorDialog("ERROR", "Could not remove raw frames from the local queue file:\n" + file + "\n"
						+ " The frame will be sent again.  If this error repeats you may need to remove the queue file manually");
				e.printStackTrace(Log.getWriter());
			}
		return success; // return true if one succeeded
	}
}
