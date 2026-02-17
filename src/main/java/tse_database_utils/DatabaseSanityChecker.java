package tse_database_utils;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import table_database.Database;
import tse_main.StartUI;

public class DatabaseSanityChecker {

	private Database db;
	private static final Logger LOGGER = LogManager.getLogger(DatabaseSanityChecker.class);
	
	public DatabaseSanityChecker(Database db) {
		this.db = db;
	}
	
	public boolean performSanityCheck() {
		try {
			DatabaseTableUtil reportTableUtil = new DatabaseTableUtil("REPORT");
			DatabaseTableUtil sumReportInfoTableUtil = new DatabaseTableUtil("SUMMARIZEDINFORMATION");
			boolean dbChangesPerformed = false;
			
			LOGGER.info("------------------------");
			LOGGER.info("Starting Check of REPORT");
			LOGGER.info("------------------------");
			
			if (!reportTableUtil.checkColumnExists("AGGREGATORID")) {
				reportTableUtil.addColumn("AGGREGATORID", "VARCHAR(1000)");
				dbChangesPerformed = true;
			}
			if (!reportTableUtil.checkColumnExists("ISAGGREGATED")) {
				reportTableUtil.addColumn("ISAGGREGATED", "VARCHAR(1000)");
				dbChangesPerformed = true;
			}
			if (!reportTableUtil.checkColumnExists("DCCODE")) {
				reportTableUtil.addColumn("DCCODE", "VARCHAR(1000)");
				dbChangesPerformed = true;
			}
			if (!reportTableUtil.checkColumnExists("TYPE")) {
				reportTableUtil.addColumn("TYPE", "VARCHAR(1000)");
				dbChangesPerformed = true;
			}
			
			LOGGER.info("------------------------");
			LOGGER.info("Starting Check of SUMMARIZEDINFORMATION");
			LOGGER.info("------------------------");
			
			if (!sumReportInfoTableUtil.checkColumnExists("ISAMENDED")) {
				sumReportInfoTableUtil.addColumn("ISAMENDED", "VARCHAR(1000)");
				dbChangesPerformed = true;
			}
			
			if (dbChangesPerformed)
			{
				LOGGER.info("Restarting DB to apply changes.");
				db.shutdown();
				db.connect();
				LOGGER.info("DB Successfully restarted ");
			}
			
			return true;
		} catch (SQLException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
	}

}
