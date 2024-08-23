package org.mcphackers.launchwrapper.protocol;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.mcphackers.launchwrapper.util.Util;

public class ListLevelsURLConnection extends URLConnection {

	public static final String EMPTY_LEVEL = "-";
	private File gameDir;

	public ListLevelsURLConnection(URL url, File gameDir) {
		super(url);
		this.gameDir = gameDir;
	}

	@Override
	public void connect() throws IOException {
	}

	public InputStream getInputStream() throws IOException {
		File levels = new File(gameDir, "levels");
		if(!levels.exists())
			levels.mkdirs();
		File levelNames = new File(levels, "levels.txt");
		int maxLevels = 5;
		String[] lvlNames = new String[maxLevels];
		for(int i = 0; i < maxLevels; i++) {
			lvlNames[i] = EMPTY_LEVEL;
		}
		if(levelNames.exists()) {
			FileInputStream levelNamesStream = new FileInputStream(levelNames);
			lvlNames = new String(Util.readStream(levelNamesStream)).split(";");
			levelNamesStream.close();
		}
		boolean levelsChanged = false;
		for(int i = 0; i < maxLevels; i++) {
			File level = new File(levels, "level" + i + ".dat");
			if(!level.exists()) {
				if(!lvlNames[i].equals(EMPTY_LEVEL)) {
					levelsChanged = true;
					lvlNames[i] = EMPTY_LEVEL;
				}
			} else if(lvlNames[i].equals(EMPTY_LEVEL)) {
				levelsChanged = true;
				lvlNames[i] = "Unnamed level";
			}
		}
		String lvls = "";
		for(int i = 0; i < maxLevels; i++) {
			lvls += lvlNames[i] + ";";
		}
		byte[] lvlsData = lvls.getBytes();
		if(levelsChanged) {
			FileOutputStream outputNames = new FileOutputStream(levelNames);
			outputNames.write(lvlsData);
			outputNames.close();
		}
		return new ByteArrayInputStream(lvlsData);
	}

}
