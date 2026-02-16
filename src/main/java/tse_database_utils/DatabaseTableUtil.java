package tse_database_utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import table_database.Database;

public class DatabaseTableUtil {

	private String tableName;
	private static final Logger LOGGER = LogManager.getLogger(DatabaseTableUtil.class);
	
	public DatabaseTableUtil(String tableName) {
		this.tableName = tableName;
	}
	
	public boolean checkColumnExists(String columnName) throws SQLException {
		LOGGER.info("-------------------");
		LOGGER.info("Executing check query with params:\ncol: ".concat(columnName).concat("\ntabName: ".concat(tableName)));
		LOGGER.info("Getting DB Connection...");
		
		Connection conn = Database.getConnection();
		
		LOGGER.info("Checking existance of col: ".concat(columnName));		
	    String sql = "SELECT c.columnname " +
	                 "FROM sys.syscolumns c " +
	                 "JOIN sys.systables t ON c.referenceid = t.tableid " +
	                 "WHERE t.tablename = ? AND c.columnname = ?";

	    try (PreparedStatement ps = conn.prepareStatement(sql)) {
	        
	        ps.setString(1, tableName.toUpperCase());
	        ps.setString(2, columnName.toUpperCase());

			
	        try (ResultSet rs = ps.executeQuery()) {
	        	Boolean returned = rs.next(); 
	        	LOGGER.info("Check Returned: ".concat(returned.toString()));
	        	return returned;
	        } catch (Exception e) {
	        	LOGGER.error("Check Returned: false (".concat(e.getLocalizedMessage().concat(")")));
	        	return false;
	        }
	    }
	}
	
	public boolean addColumn(String columnName, String columnType) {
		try {
			LOGGER.info("-------------------");
			LOGGER.info("Executing ADD query with params:\ncol: ".concat(columnName).concat("\ntabName: ".concat(tableName)).concat("\ncolType: ".concat(columnType)));
			LOGGER.info("Getting DB Connection...");
			Connection conn = Database.getConnection();
			
			String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", 
					tableName.toUpperCase(), 
					columnName.toUpperCase(),
					columnType.toUpperCase()
	        );
			
			LOGGER.info("Creating Statement...");
			try (Statement statement = conn.createStatement()) {
				LOGGER.info("Running Query...");
				statement.executeUpdate(sql);
				LOGGER.info("Column \"".concat(columnName).concat("\" added successfully."));
	            return true; 
	        } catch (Exception e)
			{
	        	LOGGER.error("Error while adding column: (".concat(e.getLocalizedMessage().concat(")")));
	        	return false;
			}
			
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			LOGGER.error("Error while getting connection: (".concat(e.getLocalizedMessage().concat(")")));
			return false;
		}
		
	}
	
	
}
