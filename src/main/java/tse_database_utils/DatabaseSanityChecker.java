package tse_database_utils;

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
			
			if (!reportTableUtil.checkColumnExists("AGGREGATORID")) {
				reportTableUtil.addColumn("AGGREGATORID", "VARCHAR(1000)");
			}
			if (!reportTableUtil.checkColumnExists("ISAGGREGATED")) {
				reportTableUtil.addColumn("ISAGGREGATED", "VARCHAR(1000)");
			}
			if (!reportTableUtil.checkColumnExists("DCCODE")) {
				reportTableUtil.addColumn("DCCODE", "VARCHAR(1000)");
			}
			if (!reportTableUtil.checkColumnExists("TYPE")) {
				reportTableUtil.addColumn("TYPE", "VARCHAR(1000)");
			}
			
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

}
