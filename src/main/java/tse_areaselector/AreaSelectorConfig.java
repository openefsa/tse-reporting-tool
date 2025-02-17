package tse_areaselector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

import org.apache.commons.compress.utils.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import global_utils.Message;
import global_utils.Warnings;
import user_interface.ProxySettingsDialog;

public class AreaSelectorConfig {

	private static final Logger LOGGER = LogManager.getLogger(AreaSelectorConfig.class);

	private String AREACFG_CONFIG_PATH = "config/areaConfig.xml";
	private String PARENT_AREACFG_CONFIG_PATH = ".." + System.getProperty("file.separator") 
	+ AREACFG_CONFIG_PATH;
	
	private String AREARESOURCES_PATH = "areaSelectorResources/";
	private String SAMPLISTFILE_PATH = "picklists/sampAreaLists.xml";
	
	public static final String AREA_INDEX = "Area.Index";
	
	private Shell dialogShell;
	
	public AreaSelectorConfig() {
		
	}
	
	public AreaSelectorConfig(String basePath) {
		this.AREACFG_CONFIG_PATH = basePath + AREACFG_CONFIG_PATH;
	}
	
	public Integer getAreaSelectorIndexInt() throws IOException {

		String name;
		try {
			name = getValue(AREACFG_CONFIG_PATH, AREA_INDEX);
		} catch (IOException e) {
			LOGGER.error("Error in getting value for Area Selector Index: ", e);
			name = getFromParent(AREA_INDEX);
		}
		
		try
		{
			return Integer.parseInt(name);	
		}
		catch (Exception e) {
			LOGGER.error("Error in getting value for Area Selector Index: ", e);
			return 0;
		}
		
	}
	
	public String getAreaSelectorIndex() throws IOException {

		String name;
		try {
			name = getValue(AREACFG_CONFIG_PATH, AREA_INDEX);
		} catch (IOException e) {
			LOGGER.error("Error in getting value for Area Selector Index: ", e);
			name = getFromParent(AREA_INDEX);
		}

		return name;
	}

	public void setAreaSelectorIndex(Integer index) throws IOException {
		String config = getPath();
		setValue(config, AREA_INDEX, index.toString());
	}
	
	private boolean isInCurrentFolder() {
		File file = new File(AREACFG_CONFIG_PATH);
		LOGGER.info("The configuration file is in the current folder: " + file.exists());
		return file.exists();
	}
	
	private String getPath() {
		if (isInCurrentFolder())
			return AREACFG_CONFIG_PATH;
		else
			return PARENT_AREACFG_CONFIG_PATH;
	}
	
	public String getConfigPath() {
		return this.AREACFG_CONFIG_PATH;
	}
	
	private String getFromParent(String key) throws IOException {
		return getValue(PARENT_AREACFG_CONFIG_PATH, key);
	}
	
	
	public Properties getProperties(String filename) throws IOException {
		Properties properties = new Properties();

		try(InputStream stream = new FileInputStream(filename)) {
			properties.loadFromXML(stream);
		}
		LOGGER.info("Application properties from the xml file: ", properties);
		return properties;
	}
	
	private String getValue(String propertiesFilename, String property) throws IOException {
		
		Properties prop = getProperties(propertiesFilename);
		
		if (prop == null)
			return null;
		
		String value = prop.getProperty(property);
		
		return value;
	}
	
	/**
	 * Set a value in the file
	 * @param propertiesFilename
	 * @param property
	 * @param value
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private void setValue(String propertiesFilename, String property, String value) throws IOException {
		Properties prop = getProperties(propertiesFilename);
		prop.setProperty(property, value);
		
		try(FileOutputStream outputStream = new FileOutputStream(propertiesFilename);) {
			prop.storeToXML(outputStream, null);
		}
		
		overwriteXML(getAreaSelectorIndexInt());
	}
	
	private void overwriteXML(Integer selectedVersion)
	{
		String pathToSelected = AREARESOURCES_PATH + selectedVersion.toString() + ".xml";
	
		
		byte[] filecontents = {};
		try {
			 filecontents = Files.readAllBytes(Paths.get(pathToSelected));
		} catch (IOException e) {
			LOGGER.error(e.getMessage());
		}
		
		
		try (FileOutputStream fos = new FileOutputStream(SAMPLISTFILE_PATH)) {
			   fos.write(filecontents);
			} catch (FileNotFoundException e) {
				LOGGER.error(e.getMessage());
			} catch (IOException e) {
				LOGGER.error(e.getMessage());
			}

		
		
		
	}
	
	
}
