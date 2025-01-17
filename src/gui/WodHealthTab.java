package gui;

import java.awt.Dimension;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

import common.Config;
import common.FoxSpacecraft;
import common.Log;
import common.Spacecraft;
import predict.PositionCalcException;
import telemetry.FoxFramePart;
import telemetry.FramePart;
import telemetry.PayloadWOD;
import uk.me.g4dpz.satellite.SatPos;

public class WodHealthTab extends HealthTab {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JLabel lblSatLatitudeValue;
	JLabel lblSatLongitudeValue;
	
	public WodHealthTab(FoxSpacecraft spacecraft) {
		super(spacecraft, DisplayModule.DISPLAY_WOD);
		
		topPanel1.add(new Box.Filler(new Dimension(14,fonth), new Dimension(1600,fonth), new Dimension(1600,fonth)));

		lblFramesDecoded = new JLabel("WOD Payloads Decoded:");
		lblFramesDecoded.setFont(new Font("SansSerif", Font.BOLD, (int)(Config.displayModuleFontSize * 14/11)));
		lblFramesDecoded.setBorder(new EmptyBorder(5, 2, 5, 5) );
		lblFramesDecoded.setForeground(textLblColor);
		topPanel1.add(lblFramesDecoded);
		lblFramesDecodedValue = new JLabel();
		lblFramesDecodedValue.setFont(new Font("SansSerif", Font.BOLD, (int)(Config.displayModuleFontSize * 14/11)));
		lblFramesDecodedValue.setBorder(new EmptyBorder(5, 2, 5, 5) );
		lblFramesDecodedValue.setForeground(textColor);
		topPanel1.add(lblFramesDecodedValue);
		
		lblResetsValue = addReset(topPanel2, "Last WOD:");
		lblUptimeValue = addUptime(topPanel2, "");
		
	//	topPanel2.add(new Box.Filler(new Dimension(14,fonth), new Dimension(400,fonth), new Dimension(1600,fonth)));
		
		lblSatLatitudeValue = addTopPanelValue(topPanel2, "Footprint   Latitude:");
		lblSatLongitudeValue = addTopPanelValue(topPanel2, "Longitude:");
	}
	
	private void displayLatLong() {
		PayloadWOD wod = (PayloadWOD)realTime;
		SatPos pos = null;
		try {
			pos = fox.getSatellitePosition(wod.getResets(), wod.getUptime());
		} catch (PositionCalcException e) {
			if (e.errorCode == FramePart.NO_TLE) {
				lblSatLatitudeValue.setText(" NO TLE");
				lblSatLongitudeValue.setText(" NO TLE");
			} else if (e.errorCode == FramePart.NO_T0) {
				lblSatLatitudeValue.setText(" T0 NOT SET");
				lblSatLongitudeValue.setText(" T0 NOT SET");
			}
		}
		if (pos != null) {
			wod.setSatPosition(pos);
			lblSatLatitudeValue.setText(" " + wod.getSatLatitudeStr());
			lblSatLongitudeValue.setText(" " + wod.getSatLongitudeStr());
		} else {
			lblSatLatitudeValue.setText(" T0 NOT SET");
			lblSatLongitudeValue.setText(" T0 NOT SET");
		}
	}

	protected void displayRow(int fromRow, int row) {
		long reset_l = (long)table.getValueAt(row, HealthTableModel.RESET_COL);
    	long uptime = (long)table.getValueAt(row, HealthTableModel.UPTIME_COL);
    	int reset = (int)reset_l;
    	//Log.println("RESET: " + reset);
    	//Log.println("UPTIME: " + uptime);
    	realTime = Config.payloadStore.getFramePart(foxId, reset, uptime, Spacecraft.WOD_LAYOUT, false);
    	if (realTime != null)
    		updateTabRT(realTime, false);
    	if (fromRow == NO_ROW_SELECTED)
    		fromRow = row;
    	if (fromRow <= row)
    		table.setRowSelectionInterval(fromRow, row);
    	else
    		table.setRowSelectionInterval(row, fromRow);
       	displayLatLong();
	}
	
	@Override
	public void parseFrames() {
		String[][] data = Config.payloadStore.getWODData(SAMPLES, fox.foxId, START_RESET, START_UPTIME, reverse);
		if (data.length > 0) {
			parseTelemetry(data);
			MainWindow.frame.repaint();
		}		
	}

	@Override
	public void run() {
		Thread.currentThread().setName("WodHealthTab");
		running = true;
		done = false;
		boolean justStarted = true;
		while(running) {
			try {
				Thread.sleep(500); // refresh data once a second
			} catch (InterruptedException e) {
				Log.println("ERROR: WodHealthTab thread interrupted");
				e.printStackTrace(Log.getWriter());
			} 	
			
			if (Config.displayRawValues != showRawValues.isSelected()) {
				showRawValues.setSelected(Config.displayRawValues);
			}
			if (foxId != 0 && Config.payloadStore.initialized()) {
				// Read the RealTime last so that at startup the Captured Date in the bottom right will be the last real time record
				if (Config.payloadStore.getUpdated(foxId, Spacecraft.WOD_LAYOUT)) {
					realTime = Config.payloadStore.getLatest(foxId, Spacecraft.WOD_LAYOUT);
					if (realTime != null) {
						if (showLatest == GraphFrame.SHOW_LIVE) {
							updateTabRT(realTime, true);
							displayLatLong();
						}
						displayFramesDecoded(Config.payloadStore.getNumberOfFrames(foxId, Spacecraft.WOD_LAYOUT));
						//System.out.println("UPDATED RT Data: ");
					} else {
						//System.out.println("NO new RT Data: ");

					}
					Config.payloadStore.setUpdated(foxId, Spacecraft.WOD_LAYOUT, false);
					MainWindow.setTotalDecodes();
					if (justStarted) {
						openGraphs(FoxFramePart.TYPE_WOD);
						justStarted = false;
					}
				}
				
				

			}
			//System.out.println("Health tab running: " + running);
		}
		done = true;
	}

}
