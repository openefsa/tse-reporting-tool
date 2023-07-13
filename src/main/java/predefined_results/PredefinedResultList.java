package predefined_results;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import app_config.BooleanValue;

public class PredefinedResultList extends ArrayList<PredefinedResult> {
	private static final Logger LOGGER = LogManager.getLogger(PredefinedResultList.class);
	private static final long serialVersionUID = -6372192676663884532L;

	private static PredefinedResultList predefinedResultsCache;
	
	/**
	 * Get a predefined result using the record type and the samp an asses fields
	 * @param recordType
	 * @param sampEventAsses
	 * @return
	 */
	public PredefinedResult get(String recordType, String source, boolean confirmatoryTested, String sampEventAsses) {
		for (PredefinedResult prh : this) {
			String thisRecordType = prh.get(PredefinedResultHeader.RECORD_TYPE);
			String thisSource = prh.get(PredefinedResultHeader.SOURCE);
			String thisSampEventAsses = prh.get(PredefinedResultHeader.SAMP_EVENT_ASSES);
			String thisConfTested = prh.get(PredefinedResultHeader.CONFIRMATORY_EXECUTED);
			
			boolean confCheck = (BooleanValue.isTrue(thisConfTested) && confirmatoryTested)
							|| (BooleanValue.isFalse(thisConfTested) && !confirmatoryTested);

			if (isFieldEqual(thisRecordType, recordType)
					&& isFieldEqual(thisSource, source)
					&& isFieldEqual(thisSampEventAsses, sampEventAsses)
					&& confCheck) {
				return prh;
			}
		}
		
		return null;
	}
	
	/**
	 * Check if field a is equal to field b or not
	 * @param a
	 * @param b
	 * @return
	 */
	private static boolean isFieldEqual(String predefResValue, String rowValue) {
		// always match an empty field in the configuration
		if (predefResValue == null || predefResValue.isEmpty() || predefResValue.equals("null")) {
			return true;
		}
		
		// no match if our value is null
		if (rowValue == null)
			return false;
		
		return rowValue.equals(predefResValue);
	}

	/**
	 * Get all the predefined results
	 * @author shahaal
	 * @return
	 * @throws IOException
	 */
	public static PredefinedResultList getAll() {
		// if first time
		if (predefinedResultsCache == null) {
			predefinedResultsCache = new PredefinedResultList();
			
			//solve memory leak
			try (PredefinedResultsReader reader = new PredefinedResultsReader()){
				reader.readFirstSheet();
				predefinedResultsCache = reader.getResults();
			} catch (IOException e) {
				LOGGER.error("Cannot retrieve predefined results list", e);
				e.printStackTrace();
			}
		}

		return predefinedResultsCache;
	}
}
