import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import telemServer.ServerProcess;
import telemServer.StpFileProcessException;
import telemetry.Frame;
import common.Config;
import common.Log;

/**
 * FOX 1 Telemetry Server
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
 * Version 0.6
 * Added -r option to init the secondary radiation payloads
 * 
 */

public class FoxTelemServer {

	public static String version = "Version 0.6";
	public static int port = Config.tcpPort;
	static int sequence = 0;
	private static final int MAX_SEQUENCE = 1000;// This needs to be larger than the maximum number of connections in a second so we dont get duplicate file names
	static int poolSize = 100; // max number of threads
	
	public static void main(String[] args) {
		
		// Need server Logging and Server Config.  Do not want to mix the config with FoxTelem
		Config.logging = true;
		Log.init("FoxServer");
		Log.showGuiDialogs = false;
		Log.setStdoutEcho(false); // everything goes in the server log.  Any messages to stdout or stderr are a serious bug of some kinds

		try {
			makeExceptionDir();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace(Log.getWriter());
			System.exit(1);
		}

		Log.println("Starting FoxServer: " + version);
		Log.println("Listening on port: " + 41042);

		Config.currentDir = System.getProperty("user.dir"); //m.getCurrentDir(); 
		Config.serverInit(); // initialize and create the payload store.  This runs in a seperate thread to the GUI and the decoder

		if (args.length > 0) {
			if ((args[0].equalsIgnoreCase("-h")) || (args[0].equalsIgnoreCase("-help")) || (args[0].equalsIgnoreCase("--help"))) {
				System.out.println("FoxServer [-v] [-s dir]\n-v - Version Information\n"
						+ "-s <dir> - Process all of the stp files in the specified directory and load them into the db\n"
						+ "-r - Reprocess the radiation data and generate the sescondary payloads\n");
				System.exit(0);
			}
			if ((args[0].equalsIgnoreCase("-v")) ||args[0].equalsIgnoreCase("-version")) {
				System.out.println("AMSAT Fox Server. Version " + Config.VERSION);
				System.exit(0);
			}

			if ((args[0].equalsIgnoreCase("-s")) ) {
				String dir = args[1]; 
			
				Log.println("AMSAT Fox Server. \nSTP FILE LOAD FROM DIR: " + dir);
				importStp(dir, false);
				System.exit(0);
			}

			if ((args[0].equalsIgnoreCase("-r")) ) {
			
				Log.println("AMSAT Fox Server. \nPROCESS RAD DATA: ");
				processRadData();
				System.exit(0);
			}

		}
		
		ServerSocket serverSocket = null;
        boolean listening = true;
        ExecutorService pool = null;

        try {
            serverSocket = new ServerSocket(port);
            pool = Executors.newFixedThreadPool(poolSize);
            } catch (IOException e) {
            Log.println("Could not listen on port: " + port);
            System.exit(-1);
        }

        //ServerProcess process = null;
        //Thread processThread;

        
        while (listening) {
        	try {
        		//process = new ServerProcess(serverSocket.accept(), sequence++);
        		Log.println("Waiting for connection ...");
        		pool.execute(new ServerProcess(serverSocket.accept(), sequence++));
        	}  catch (SocketTimeoutException s) {
        		Log.println("Socket timed out! - trying to continue	");
        	} catch (IOException e) {
        		// TODO Auto-generated catch block
        		e.printStackTrace(Log.getWriter());
        	}
            if (sequence == MAX_SEQUENCE)
            	sequence=0;
        }

        try {
			serverSocket.close();
			pool.shutdown();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace(Log.getWriter());
		}
    }
	
	public static void makeExceptionDir() throws IOException {
		File aFile = new File("exception");
		if(aFile.isDirectory()){
			// already exists
			return;
		} else {
			aFile.mkdir();
		}
		if(!aFile.isDirectory()){
			throw new IOException("Can not make the exception dir");
		}
	}

	private static void processRadData() {
		Config.payloadStore.initRad2();
	}
	
	/**
	 * Get a list of all the files in the STP dir and import them
	 */
	private static void importStp(String stpDir, boolean delete) {
		String dir = stpDir;
		if (!Config.logFileDirectory.equalsIgnoreCase("")) {
			dir = Config.logFileDirectory + File.separator + dir;

		}
		Log.println("IMPORT STP from " + dir);
		File folder = new File(dir);
		File[] listOfFiles = folder.listFiles();
		if (listOfFiles != null) {
			for (int i = 0; i < listOfFiles.length; i++) {
				if (listOfFiles[i].isFile() ) {
					//Log.print("Loading STP data from: " + listOfFiles[i].getName());
					try {
						Frame f = Frame.importStpFile(listOfFiles[i], true);
						if (f == null) {
							// null data - try to delete it but do nothing if we can not (so don't check the return code
							listOfFiles[i].delete();
						}
					} catch (StpFileProcessException e) {
						Log.println("STP IMPORT ERROR: " + e.getMessage());
						e.printStackTrace(Log.getWriter());
					}
					//if (f != null)
					//	Log.println(" ... " + f.getHeader().getResets() + " " + f.getHeader().getUptime());
					if (i%100 == 0)
						Log.println("Loaded: " + 100.0*i/listOfFiles.length +"%");
				}
			}
			Log.println("Files Processed: " + listOfFiles.length);
		}
	}

}