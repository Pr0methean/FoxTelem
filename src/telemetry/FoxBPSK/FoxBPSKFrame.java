package telemetry.FoxBPSK;

import common.Config;
import common.FoxSpacecraft;

import java.io.BufferedReader;
import java.io.IOException;
import common.Log;
import common.Spacecraft;
import decoder.Decoder;
import telemetry.BitArrayLayout;
import telemetry.FoxFramePart;
import telemetry.FoxPayloadStore;
import telemetry.Frame;
import telemetry.HighSpeedTrailer;
import telemetry.PayloadMaxValues;
import telemetry.PayloadMinValues;
import telemetry.PayloadRadExpData;
import telemetry.PayloadRtValues;
import telemetry.PayloadUwExperiment;
import telemetry.PayloadWOD;
import telemetry.PayloadWODRad;
import telemetry.PayloadWODUwExperiment;

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
	 */
	public class FoxBPSKFrame extends Frame {
		
		public static final int MAX_FRAME_SIZE = 476;
		public static final int MAX_HEADER_SIZE = 8;
		public static final int PAYLOAD_SIZE = 78;
		public static final int MAX_PAYLOAD_SIZE = MAX_FRAME_SIZE - MAX_HEADER_SIZE; 
		public static final int MAX_TRAILER_SIZE = 96;
		
		public static final int NUMBER_DEFAULT_PAYLOADS = 6; 
		
		public static final int ALL_WOD_FRAME = 0;
		public static final int REALTIME_FRAME = 1;
		public static final int MINMAX_FRAME = 2;
		public static final int REALTIME_BEACON = 3;
		public static final int WOD_BEACON = 4;
		public static final int CAN_PACKET_SCIENCE_FRAME = 5;
		public static final int CAN_PACKET_CAMERA_FRAME = 6;
		public static final int WOD_CAN_FRAME = 7;
		public static final int BCN_WOD_CAN1  = 8;
		public static final int HEALTH_CAN_MINMAX  = 9;
		public static final int HEALTH_CAN  = 10;
		public static final int TYPES_OF_FRAME = 11;
		
		private boolean canPacketFrame = false;

		public FoxFramePart[] payload;
		
		HighSpeedTrailer trailer = null;

		int numberBytesAdded = 0;
		
		public FoxBPSKFrame() {
			super();
			bytes = new byte[MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE];
		}

		public FoxBPSKFrame(BufferedReader input) throws IOException {
			super(input);
			load(input);
		}
		
		public FoxBPSKHeader getHeader() { return (FoxBPSKHeader)header; }
		
		int debugCount = 0;
		public void addNext8Bits(byte b) {
			if (Config.debugBytes) {
				String debug = (Decoder.plainhex(b));
				debugCount++;
				Log.print(debug);
				if (debugCount % 40 == 0) Log.println("");;
			}

			if (corrupt) return;
			if (numberBytesAdded < MAX_HEADER_SIZE) {
				if (header == null)
					header = new FoxBPSKHeader();
				header.addNext8Bits(b);
			} else if (numberBytesAdded == MAX_HEADER_SIZE) {
				// first non header byte
				header.copyBitsToFields(); // make sure the id is populated
				fox = (FoxSpacecraft) Config.satManager.getSpacecraft(header.id);
				if (fox != null) {
					initPayloads(header.id, header.getType());
					if (payload[0] == null) {
						if (Config.debugFrames)
							Log.errorDialog("ERROR","FOX ID: " + header.id + " Type: " + header.getType() + " not valid. Decode not possible.\n"
									+ "Turn off Debug Frames to prevent this message in future.");
						else
							Log.println("FOX ID: " + header.id + " Type: " + header.getType() + " not valid. Decode not possible.");

						corrupt = true;
						return;
					}
					if (Config.debugFrames)
						Log.println(header.toString());
				} else {
					if (Config.debugFrames)
						Log.errorDialog("ERROR","FOX ID: " + header.id + " is not configured in the spacecraft directory.  Decode not possible.\n"
								+ "Turn off Debug Frames to prevent this message in future.");
					else
						Log.println("FOX ID: " + header.id + " is not configured in the spacecraft directory.  Decode not possible.");							
					corrupt = true;
					return;
				}
				payload[0].addNext8Bits(b); // add the first byte to the first payload
			} else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE) {
				payload[0].addNext8Bits(b);
			} else if (canPacketFrame)
				payload[0].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*2)
				payload[1].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*3)
				payload[2].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*4)
				payload[3].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*5)
				payload[4].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + PAYLOAD_SIZE*6)
				payload[5].addNext8Bits(b);
			else if (numberBytesAdded < MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE)
				;//trailer.addNext8Bits(b); //FEC ;
			else
				Log.println("ERROR: attempt to add byte past end of frame");

			if (Config.debugBytes) {
				if ((numberBytesAdded - MAX_HEADER_SIZE) % PAYLOAD_SIZE == 0)
					Log.println("");
			}
			bytes[numberBytesAdded] = b;
			numberBytesAdded++;
		}
		
		/**
		 *  Here is how the frames are defined in the IHU for Husky:
		 *  
              //0 ALL_WOD_FRAME -- 3 HK WOD, 3 CAN WOD
            {WOD_CAN_PAYLOAD6, WOD_HK_PAYLOAD5,  WOD_CAN_PAYLOAD6, WOD_HK_PAYLOAD5,  WOD_CAN_PAYLOAD6, WOD_HK_PAYLOAD5},

            //1 HEALTH_FRAME -- 2 HK WOD, 2 CAN WOD, 1 CAN, 1 HK
            {WOD_CAN_PAYLOAD6,WOD_HK_PAYLOAD5,WOD_CAN_PAYLOAD6,WOD_HK_PAYLOAD5, HK_PAYLOAD1,HEALTH_CAN_PAYLOAD4},

            //2 MINMAX_FRAME -- 2 HK WOD, 2 CAN WOD, 1 MIN, 1 MAX
            {WOD_CAN_PAYLOAD6,WOD_HK_PAYLOAD5,WOD_CAN_PAYLOAD6,WOD_HK_PAYLOAD5,MAX_VALS_PAYLOAD2,MIN_VALS_PAYLOAD3},

            //3 HEALTH_BEACON -- 1 HK, 5 HK WOD
            {HK_PAYLOAD1, WOD_HK_PAYLOAD5,  WOD_HK_PAYLOAD5, WOD_HK_PAYLOAD5,  WOD_HK_PAYLOAD5, WOD_HK_PAYLOAD5},

            //4 WOD_BEACON -- 6 HK WOD
            {WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5, WOD_HK_PAYLOAD5, WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5},

            //5 SCIENCE_FRAME -- 6 CAN
            {HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4},

            //6 CAMERA_FRAME -- 6 CAN
            {HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4},

            //7 WOD_CAN_FRAME -- 2 WOD HK, 2 WOD CAN, 2 CAN
            {WOD_CAN_PAYLOAD6, WOD_HK_PAYLOAD5,WOD_CAN_PAYLOAD6,WOD_HK_PAYLOAD5,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4},

            //8 BCN_WOD_CAN1 -- 2 WOD HK, 1 WOD CAN, 3 CAN
            {WOD_CAN_PAYLOAD6, WOD_HK_PAYLOAD5,WOD_HK_PAYLOAD5,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4},

            //9 HEALTH_CAN_MINMAX -- 1 HK, 1 Min, 1 Max, 1 CAN, 1 WOD HK, 1 WOD CAN
            {HK_PAYLOAD1, MIN_VALS_PAYLOAD3, MAX_VALS_PAYLOAD2,WOD_CAN_PAYLOAD6,WOD_HK_PAYLOAD5,WOD_CAN_PAYLOAD6},

            //10 HEALTH_CAN -- 1 HK, 3 CAN, 1 WOD CAN, 1 WOD HK
            {HK_PAYLOAD1, HEALTH_CAN_PAYLOAD4, HEALTH_CAN_PAYLOAD4,HEALTH_CAN_PAYLOAD4,WOD_HK_PAYLOAD5,WOD_CAN_PAYLOAD6},
		 *
		 * @param type
		 */
		private void initPayloads(int foxId, int type) {
			BitArrayLayout WOD_CAN_PAYLOAD6 = Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT);
			BitArrayLayout HEALTH_CAN_PAYLOAD4 = Config.satManager.getLayoutByName(header.id, Spacecraft.RAD_LAYOUT);
			
			switch (type) {
			case ALL_WOD_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				for (int i=0; i<NUMBER_DEFAULT_PAYLOADS; i+=2 ) {
					if (foxId == FoxSpacecraft.HUSKY_SAT) {
						payload[i] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
					} else {
						payload[i] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));						
					}
					payload[i+1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				}
				break;
			case REALTIME_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[0] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				} else
					payload[0] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[2] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				} else
					payload[2] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadRtValues(Config.satManager.getLayoutByName(header.id, Spacecraft.REAL_TIME_LAYOUT));
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[5] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				} else
					payload[5] = new PayloadRadExpData(Config.satManager.getLayoutByName(header.id, Spacecraft.RAD_LAYOUT));
				break;
			case MINMAX_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[0] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				} else
					payload[0] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				if (foxId == FoxSpacecraft.HUSKY_SAT) {
					payload[2] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				} else
					payload[2] = new PayloadWODRad(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_RAD_LAYOUT));
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadMaxValues(Config.satManager.getLayoutByName(header.id, Spacecraft.MAX_LAYOUT));
				payload[5] = new PayloadMinValues(Config.satManager.getLayoutByName(header.id, Spacecraft.MIN_LAYOUT));
				break;
			case REALTIME_BEACON:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				payload[0] = new PayloadRtValues(Config.satManager.getLayoutByName(header.id, Spacecraft.REAL_TIME_LAYOUT));
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[2] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[5] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				break;
			case WOD_BEACON:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				for (int i=0; i<NUMBER_DEFAULT_PAYLOADS; i++ ) {
					payload[i] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				}
				break;
			case CAN_PACKET_SCIENCE_FRAME:
				payload = new FoxFramePart[1];
				payload[0] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				canPacketFrame = true;
				break;
			case CAN_PACKET_CAMERA_FRAME:
				payload = new FoxFramePart[1];
				payload[0] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				canPacketFrame = true;
				break;
			case WOD_CAN_FRAME:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				payload[0] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[2] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				payload[3] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[4] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				payload[5] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				break;
			case BCN_WOD_CAN1:
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				payload[0] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				payload[1] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[2] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[3] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				payload[4] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				payload[5] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				break;
			case HEALTH_CAN_MINMAX :
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				payload[0] = new PayloadRtValues(Config.satManager.getLayoutByName(header.id, Spacecraft.REAL_TIME_LAYOUT));
				payload[1] = new PayloadMinValues(Config.satManager.getLayoutByName(header.id, Spacecraft.MIN_LAYOUT));
				payload[2] = new PayloadMaxValues(Config.satManager.getLayoutByName(header.id, Spacecraft.MAX_LAYOUT));
				payload[3] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				payload[4] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[5] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				break;
			case HEALTH_CAN  :
				payload = new FoxFramePart[NUMBER_DEFAULT_PAYLOADS];
				payload[0] = new PayloadRtValues(Config.satManager.getLayoutByName(header.id, Spacecraft.REAL_TIME_LAYOUT));
				payload[1] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				payload[2] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				payload[3] = new PayloadUwExperiment(HEALTH_CAN_PAYLOAD4, header.id, header.uptime, header.resets);
				payload[4] = new PayloadWOD(Config.satManager.getLayoutByName(header.id, Spacecraft.WOD_LAYOUT));
				payload[5] = new PayloadWODUwExperiment(WOD_CAN_PAYLOAD6, header.id, header.uptime, header.resets);
				break;
			default:
				// Need some error handling here in case the frame is invalid
				// Though in testing we get a crash, which is perhaps what we want, as this should never be reachable
				break;
			}
		}

		public boolean savePayloads(FoxPayloadStore payloadStore) {
			int serial = 0;
			header.copyBitsToFields(); // make sure we have defaulted the extended FoxId correctly
			for (int i=0; i<payload.length; i++ ) {
				if (payload[i] != null) {
					payload[i].copyBitsToFields();
					if (payload[i] instanceof PayloadUwExperiment) { 
						((PayloadUwExperiment)payload[i]).savePayloads(payloadStore, serial);
						serial = serial + ((PayloadUwExperiment)payload[i]).canPackets.size();
					} else if (payload[i] instanceof PayloadWODUwExperiment) { 
						((PayloadWODUwExperiment)payload[i]).savePayloads(payloadStore, serial);
						serial = serial + ((PayloadWODUwExperiment)payload[i]).canPackets.size();
					} else
						if (!payloadStore.add(header.getFoxId(), header.getUptime(), header.getResets(), payload[i]))
							return false;
				}
			}
			return true;			
		}
		
		public static int getMaxDataBytes() {
			return MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE;
		}
		
		public static int getMaxBytes() {
			return MAX_HEADER_SIZE + MAX_PAYLOAD_SIZE + MAX_TRAILER_SIZE;
		}

		/**
		 * Get a buffer containing all of the CAN Packets in this frame.  There may be multiple payloads that have CAN Packets,
		 * so we need to check all of them.  First we gather the bytes from each payload in the PCAN format.  We return an 
		 * array of those byte arrays.  The calling routine will send each PCAN packet individually
		 */
		public byte[][] getPayloadBytes() {

			byte[][] allBuffers = null;

			Spacecraft sat = Config.satManager.getSpacecraft(foxId);
			if (sat.sendToLocalServer()) {
				int totalBuffers = 0;
				for (int i=0; i< payload.length; i++) {
					// if this payload should be output then add to the byte buffer
					if (payload[i] instanceof PayloadUwExperiment) {
						byte[][] buffer = ((PayloadUwExperiment)payload[i]).getCANPacketBytes(); 
						totalBuffers += buffer.length; 
					}
					if (payload[i] instanceof PayloadWODUwExperiment) {
						byte[][] buffer = ((PayloadWODUwExperiment)payload[i]).getCANPacketBytes(); 
						totalBuffers += buffer.length; 
					}
				}
					
				allBuffers = new byte[totalBuffers][];
				int startPosition = 0;
				for (int p=0; p< payload.length; p++) {
					// if this payload should be output then add its byte buffers to the output
					if (payload[p] instanceof PayloadUwExperiment) {
						byte[][] buffer = ((PayloadUwExperiment)payload[p]).getCANPacketBytes(); 
						for (int j=0; j < buffer.length; j++) {
							allBuffers[j + startPosition] = buffer[j];
						}
						startPosition += buffer.length;
					}
					if (payload[p] instanceof PayloadWODUwExperiment) {
						byte[][] buffer = ((PayloadWODUwExperiment)payload[p]).getCANPacketBytes(); 
						for (int j=0; j < buffer.length; j++) {
							allBuffers[j + startPosition] = buffer[j];
						}
						startPosition += buffer.length;
					}
				}

			}
			return allBuffers;				
		}

		public String toString() {
			String s = new String();
			s = "\n" + header.toString();
			
			if (payload != null) {
				for (int i=0; i < payload.length; i++) {
					s = s + "\n"+ payload[i].toString() +
					"\n"; 
				}
			} 
			
			return s;
		}
}
