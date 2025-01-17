package telemServer;

import java.text.ParseException;
import java.util.Date;
import java.util.TimeZone;

import common.Log;
import common.Spacecraft;
import common.FoxSpacecraft;
import gui.DisplayModule;
import telemetry.BitArrayLayout;
import telemetry.FoxFramePart;
import telemetry.FramePart;
import telemetry.LayoutLoadException;
import telemetry.PayloadDbStore;
import telemetry.PayloadMaxValues;
import telemetry.PayloadMinValues;
import telemetry.PayloadRadExpData;
import telemetry.PayloadRtValues;
import telemetry.PayloadStore;

public class WebHealthTab {
	FoxSpacecraft fox;
	PayloadDbStore payloadDbStore;
	PayloadRtValues payloadRt;
	PayloadMaxValues payloadMax;
	PayloadMinValues payloadMin;

	BitArrayLayout rtlayout;
	BitArrayLayout maxlayout;
	BitArrayLayout minlayout;

	String[] topModuleNames = new String[20];
	int[] topModuleLines = new int[20];
	String[] bottomModuleNames = new String[10];
	int[] bottomModuleLines = new int[10];
	int numOfTopModules = 1;
	int numOfBottomModules = 1;
	int port = 8080; // port to pass onto further calls

	public WebHealthTab(PayloadDbStore pdb, FoxSpacecraft f, int p) throws LayoutLoadException {
		fox = f;
		if (fox == null) throw new LayoutLoadException("Spacecraft is not valid");
		port = p;
		payloadDbStore = pdb;
		rtlayout = fox.getLayoutByName(Spacecraft.REAL_TIME_LAYOUT);
		maxlayout = fox.getLayoutByName(Spacecraft.MAX_LAYOUT);
		minlayout = fox.getLayoutByName(Spacecraft.MIN_LAYOUT);
		analyzeModules(rtlayout, maxlayout,minlayout, 0);
	}
	
	public void setRtPayload(PayloadRtValues rt) {payloadRt = rt;}
	public void setMaxPayload(PayloadMaxValues max) {payloadMax = max;}
	public void setMinPayload(PayloadMinValues min) {payloadMin = min;}
	
	public String toCsvString(String fieldName, boolean convert, int num, int fromReset, int fromUptime) {
		String s = "";
		//s = s + "<style> td { border: 5px } th { background-color: lightgray; border: 3px solid lightgray; } td { padding: 5px; vertical-align: top; background-color: darkgray } </style>";	
		//s = s + "<h3>Fox "+ fox.getIdString()+" - " + fieldName +"</h3>"
		//		+ "<table><tr><th>Reset</th> <th>Uptime </th> <th>" + fieldName + "</th> </tr>";
		double[][] graphData = payloadDbStore.getRtGraphData(fieldName, num, fox, fromReset, fromUptime, false, true);
		if (graphData != null) {
			for (int i=0; i< graphData[0].length; i++) {
			//	s = s + "<tr>";
				s = s + (int)graphData[PayloadStore.RESETS_COL][i] + "," +
						(int)graphData[PayloadStore.UPTIME_COL][i] + "," +
						graphData[PayloadStore.DATA_COL][i] + "/n";
				
			}
		}
		s = s + "</table>";
		return s;
	}
	
	public String toGraphString(String fieldName, boolean convert, int num, int fromReset, int fromUptime) {
		String s = "";
		s = s + "<style> td { border: 5px } th { background-color: lightgray; border: 3px solid lightgray; } td { padding: 5px; vertical-align: top; background-color: darkgray } </style>";	
		s = s + "<h1 class='entry-title'>Fox "+ fox.getIdString()+" - " + fieldName +"</h1>"
				+ "<table><tr><th>Reset</th> <th>Uptime </th> <th>" + fieldName + "</th> </tr>";
		
		double[][] graphData = null;

		if (rtlayout.hasFieldName(fieldName))
			graphData = payloadDbStore.getRtGraphData(fieldName, num, fox, fromReset, fromUptime, false, true);
		else if (minlayout.hasFieldName(fieldName))
			graphData = payloadDbStore.getMinGraphData(fieldName, num, fox, fromReset, fromUptime, false, true);
		else if (maxlayout.hasFieldName(fieldName))
			graphData = payloadDbStore.getMaxGraphData(fieldName, num, fox, fromReset, fromUptime, false, true);
		
		if (graphData != null) {
			for (int i=0; i< graphData[0].length; i++) {
				s = s + "<tr>";
				s = s + "<td>"+(int)graphData[PayloadStore.RESETS_COL][i] + "</td>" +
						"<td>"+(int)graphData[PayloadStore.UPTIME_COL][i] + "</td>" +
						"<td>"+graphData[PayloadStore.DATA_COL][i] + "</td>";
				s = s + "</tr>";
			}
		}
		s = s + "</table>";
		return s;
	}
	
	public String toString() {
		String s = "";
		PayloadRadExpData radPayload = payloadDbStore.getLatestRad(fox.foxId);
		String mode = FoxSpacecraft.determineModeString((PayloadRtValues)payloadRt, (PayloadMaxValues)payloadMax, (PayloadMinValues)payloadMin, radPayload);
		if (payloadRt != null) {
			
			s = s + "<h1 class='entry-title'>Fox "+ fox.getIdString()+"</h1>";
			// We set the style of table 1 for the telemetry.  Table 2 is the inner table for the max/min/rt rows
		s = s + "<style> table.table1 td { border: 5px solid lightgray; } "
				+ "table.table1 th { background-color: lightgray; border: 3px solid lightgray; } "
				+ "table.table1 td { padding: 5px; vertical-align: top; background-color: darkgray } </style>";	
		s = s + "<style> table.table2 td { border: 0px solid darkgray; } "
				+ "table.table2 th { background-color: darkgray; border: 1px solid darkgray; } "
				+ "table.table2 td { padding: 0px; vertical-align: top; background-color: darkgray } </style>";	
		
		s = s + "<h3>REAL TIME Telemetry   Reset: " + payloadRt.getResets() + " Uptime: " + payloadRt.getUptime();				
		s = s + " Mode: " + mode + "<br>";
		s = s + "</h3>";
		if (payloadRt.getCaptureDate() != null)
			s = s + " Received: " + formatCaptureDate(payloadRt.getCaptureDate()) + "<br>";
		
		s = s + "Last MAX Reset: " + payloadMax.getResets() + " Uptime: " + payloadMax.getUptime() + ".";
		s = s + " Last MIN Reset: " + payloadMin.getResets() + " Uptime: " + payloadMin.getUptime() + ".</br><p>";
		
		
		s = s + "<table class='table1'>";
		
		s = s + "<tr bgcolor=silver>";
		for (int i=1; i < 4; i++) {
			s = s + "<th><strong>" + topModuleNames[i] + "<strong>"
			+ "</th>";
		}
		s = s + "</tr>";
		s = s + "<tr>";
		for (int i=1; i < 4; i++) {
			s = s + buildModule(i);		
		}
		s = s + "</tr>";
		s = s + "<tr bgcolor=silver>";
		for (int i=4; i < 6; i++) {
			s = s + "<th><strong>" + topModuleNames[i] + "</strong>"
			+ "</th>";
		}
		s = s + "</tr><tr>";
		for (int i=4; i < 6; i++) {	
			s = s + buildModule(i);	
		}
		s = s + "</tr>";
		s = s + "<tr bgcolor=silver>";
		for (int i=6; i < 9; i++) {
			s = s + "<th><strong>" + topModuleNames[i] + "</strong>"
			+ "</th>";
		}
		s = s + "</tr><tr>";
		for (int i=6; i < 9; i++) {
			s = s + buildModule(i);	
		}
		s = s + "</tr>";
		s = s + "<tr bgcolor=silver>";
		for (int i=9; i < 12; i++) {
			s = s + "<th><strong>" + topModuleNames[i] + "</strong>"
			+ "</th>";
		}
		s = s + "</tr><tr>";
		for (int i=9; i < 12; i++) {
			s = s + buildModule(i);	
		}
		s = s + "</tr>";
		s= s+ "</table>";
		}
		return s;
	}
	
	private String buildModule(int i) {
		String s = "";
		s = s + "<td><table class='table2'>";
		s = s + "<tr><th></th><th>RT</th><th>MIN</th><th>MAX</th></tr>";
		try {
			s = s + addModuleLines(topModuleNames[i], topModuleLines[i], rtlayout, payloadRt);
		    if (maxlayout != null) 
		    	s = s + addModuleLines(topModuleNames[i], topModuleLines[i], maxlayout, payloadMax);
		    if (minlayout != null) 
		    	s = s + addModuleLines(topModuleNames[i], topModuleLines[i], minlayout, payloadMin);
		} catch (LayoutLoadException e) {
			e.printStackTrace(Log.getWriter());
		}

		s = s + "</table></td>";
		return s;
	}
	
	private String addModuleLines(String topModuleName, int topModuleLine, BitArrayLayout rt, FramePart payloadRt) throws LayoutLoadException {
		
		String s = "";
		for (int j=0; j<rt.NUMBER_OF_FIELDS; j++) {
			if (rt.module[j].equals(topModuleName)) {
				//Log.println("Adding:" + rt.shortName[j]);
				if (rt.moduleLinePosition[j] > topModuleLine) throw new LayoutLoadException("Found error in Layout File: "+ rt.fileName +
						".\nModule: " + topModuleName +
						" has " + topModuleLine + " lines, so we can not add " + rt.shortName[j] + " on line " + rt.moduleLinePosition[j]);
				
				if (!rt.fieldName[j].startsWith("IHUDiag")) {
				s = s + "<tr>";
				
				s= s + "<td>";
				
				s = s + "<a href=/tlm/graph.php?"
						+ "sat=" + fox.foxId+"&field=" + rt.fieldName[j]
								+ "&raw=conv"  
								+ "&reset=0"
								+ "&uptime=0"
								+ "&rows=100"
								+ "&port=" + port
								+ ">" + rt.shortName[j] + "</a>";
				s = s + formatUnits(rt.fieldUnits[j]) + "</td>";

				if (rt.moduleDisplayType[j] == DisplayModule.DISPLAY_RT_ONLY)
					s= s + "<td align=left colspan=3>";
				else
					s= s + "<td align=center > ";
				
				s = s + payloadRt.getStringValue(rt.fieldName[j], fox)  + "</td>"; 

				
				// Min
				if (rt.moduleDisplayType[j] != DisplayModule.DISPLAY_RT_ONLY) {
					s = s + "<td align=center >";
					if (rt.moduleDisplayType[j] == DisplayModule.DISPLAY_ALL_SWAP_MINMAX) {
						if (payloadMax != null)
							s = s + payloadMax.getStringValue(rt.fieldName[j], fox);
					} else if (payloadMin != null)
						s = s + payloadMin.getStringValue(rt.fieldName[j], fox);
				}
				
				// Max
				if (rt.moduleDisplayType[j] != DisplayModule.DISPLAY_RT_ONLY) {
					s = s + "</td><td align=center >";
					if (rt.moduleDisplayType[j] == DisplayModule.DISPLAY_ALL_SWAP_MINMAX) {
						if (payloadMin != null)
							s = s + payloadMin.getStringValue(rt.fieldName[j], fox);
					} else if (payloadMax != null)
						s = s + payloadMax.getStringValue(rt.fieldName[j], fox);
				}
				s = s + "</td></tr>";
				}
			}
		}
		
		return s;

	}
	
	private String formatUnits(String unit) {
		if (unit.equals("-") || unit.equalsIgnoreCase(BitArrayLayout.NONE)) return "";
		unit = " ("+unit+")";
		return unit;
				
	}
	
	private String formatCaptureDate(String u) {
		Date result = null;
		String reportDate = null;
		try {
			FoxFramePart.fileDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			result = FoxFramePart.fileDateFormat.parse(u);	
			FoxFramePart.reportDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
			reportDate = FoxFramePart.reportDateFormat.format(result);

		} catch (ParseException e) {
			reportDate = "unknown";				
		} catch (NumberFormatException e) {
			reportDate = "unknown";				
		} catch (Exception e) {
			reportDate = "unknown";				
		}

		return reportDate;
	}

	//FIXME - This is copied from ModuleTab and should be in a shared place
	protected void analyzeModules(BitArrayLayout rt, BitArrayLayout max, BitArrayLayout min, int moduleType) throws LayoutLoadException {
		// First get a quick list of all the modules names and sort them into top/bottom
		for (int i=0; i<rt.NUMBER_OF_FIELDS; i++) {
			if (!rt.module[i].equalsIgnoreCase(BitArrayLayout.NONE)) {
				if (!containedIn(topModuleNames, rt.module[i])) {
					topModuleNames[rt.moduleNum[i]] = rt.module[i];
					numOfTopModules++;
				}
				topModuleLines[rt.moduleNum[i]]++;
			}
		}
		if (max != null)
			for (int i=0; i<max.NUMBER_OF_FIELDS; i++) {
				if (!max.module[i].equalsIgnoreCase(BitArrayLayout.NONE)) {
					if (!containedIn(topModuleNames, max.module[i])) {
						topModuleNames[max.moduleNum[i]] = max.module[i];
						numOfTopModules++;
					}
					topModuleLines[max.moduleNum[i]]++;
				}
			}
		if (min != null)
			for (int i=0; i<min.NUMBER_OF_FIELDS; i++) {
				if (!min.module[i].equalsIgnoreCase(BitArrayLayout.NONE)) {
					if (!containedIn(topModuleNames, min.module[i])) {
						topModuleNames[min.moduleNum[i]] = min.module[i];
						numOfTopModules++;
					}
					topModuleLines[min.moduleNum[i]]++;
				}

			}

	}
	
	private boolean containedIn(String[] array, String item) {
		for(String s : array) {
			if (s!=null)
				if (s.equals(item)) return true;
		}
		return false;
	}
}
