package tse_main;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import app_config.AppPaths;
import app_config.PropertiesReader;
import dataset.IDataset;
import formula.FormulaException;
import global_utils.EFSARCL;
import global_utils.FileUtils;
import global_utils.Warnings;
import html_viewer.HtmlViewer;
import i18n_messages.TSEMessages;
import providers.FormulaService;
import providers.IFormulaService;
import providers.ITableDaoService;
import providers.TableDaoService;
import providers.TseReportService;
import soap.GetAck;
import soap.GetDataset;
import soap.GetDatasetsList;
import soap.SendMessage;
import soap_interface.IGetAck;
import soap_interface.IGetDataset;
import soap_interface.IGetDatasetsList;
import soap_interface.ISendMessage;
import table_database.Database;
import table_database.DatabaseVersionException;
import table_database.ITableDao;
import table_database.TableDao;
import table_skeleton.TableCell;
import table_skeleton.TableRow;
import tse_config.CustomStrings;
import tse_config.DebugConfig;
import tse_options.PreferencesDialog;
import tse_options.SettingsDialog;
import tse_report.ReportCreatorDialog;
import user.User;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;

public class StartUI {

	private static final Logger LOGGER = LogManager.getLogger(StartUI.class);

	private Display display;

	/**
	 * Check if the mandatory fields of a generic settings table are filled or not
	 * 
	 * @param tableName
	 * @return
	 * @throws IOException
	 */
	private static boolean checkSettings(String tableName) {

		TableDao dao = new TableDao();

		Collection<TableRow> data = dao.getAll(TableSchemaList.getByName(tableName));

		if (data.isEmpty()) {
			LOGGER.info("There are no data to check");
			return false;
		}
			

		TableRow firstRow = data.iterator().next();

		// check if the mandatory fields are filled or not
		return firstRow.areMandatoryFilled();
	}

	/**
	 * Check if the settings were set or not
	 * 
	 * @return
	 * @throws IOException
	 */
	private static boolean checkSettings() {
		return checkSettings(CustomStrings.SETTINGS_SHEET);
	}

	/**
	 * Check if the preferences were set or not
	 * 
	 * @return
	 * @throws IOException
	 */
	private static boolean checkPreferences() {
		return checkSettings(CustomStrings.PREFERENCES_SHEET);
	}

	/**
	 * Login the user into the system in order to be able to perform web-service
	 * calls
	 */
	private static void loginUser() {

		// get the settings schema table
		TableSchema settingsSchema = TableSchemaList.getByName(CustomStrings.SETTINGS_SHEET);

		TableDao dao = new TableDao();

		// get the settings
		TableRow settings = dao.getAll(settingsSchema).iterator().next();

		if (settings == null) {
			LOGGER.info("There are no settings to login");
			return;
		}
	
		// get credentials
		TableCell usernameVal = settings.get(CustomStrings.SETTINGS_USERNAME);
		TableCell passwordVal = settings.get(CustomStrings.SETTINGS_PASSWORD);
		TableCell orgVal = settings.get(CustomStrings.SETTINGS_ORG_CODE);

		if (usernameVal == null || passwordVal == null){
			LOGGER.info("The username or the password is empty");
			return;
		}

		// login the user
		String username = usernameVal.getLabel();
		String password = passwordVal.getLabel();

		User user = User.getInstance();
		user.login(username, password);

		if (orgVal != null)
			user.addData(CustomStrings.SETTINGS_ORG_CODE, orgVal.getLabel());
	}

	/**
	 * Close the application (db + interface)
	 * 
	 * @param db
	 */
	private void shutdown(Database db) {

		LOGGER.info("Application closed : " + System.currentTimeMillis());

		if (display != null)
			display.dispose();

		// close the database
		if (db != null)
			db.shutdown();

		// exit the application
		System.exit(0);
	}

	/**
	 * Show an error to the user
	 * 
	 * @param errorCode
	 * @param message
	 */
	private void showInitError(String message) {
		Shell shell = new Shell(display);
		Warnings.warnUser(shell, TSEMessages.get("error.title"), message);

		shell.dispose();
		display.dispose();
	}

	private int ask(String message) {
		Shell shell = new Shell(display);
		int val = Warnings.warnUser(shell, TSEMessages.get("warning.title"), message,
				SWT.YES | SWT.NO | SWT.ICON_WARNING);

		shell.dispose();
		display.dispose();

		return val;
	}

	/**
	 * Start the TSE data reporting tool interface & database
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String args[]) throws IOException {

		try {
			StartUI main = new StartUI();
			Database db = main.launch();
			main.shutdown(db);
		} catch (Throwable e) {
			LOGGER.fatal("Generic error occurred", e);
			e.printStackTrace();

			Warnings.createFatal(TSEMessages.get("generic.error", PropertiesReader.getSupportEmail()))
					.open(new Shell());
		}
	}

	private Database launch() {

		// application start-up message. Usage of System.err used for red chars
		LOGGER.info("Application started : " + System.currentTimeMillis());

		// connect to the database application
		Database db = new Database();

		try {
			db.connect();
		} catch (IOException e) {
			LOGGER.fatal("Database not found or incompatible", e);
			showInitError(TSEMessages.get("db.init.error", e.getMessage()));
			return null;
		}

		try {

			FileUtils.createFolder(CustomStrings.PREFERENCE_FOLDER);

			// Initialise the library
			EFSARCL.init();

			// check also custom files
			EFSARCL.checkConfigFiles(CustomStrings.PREDEFINED_RESULTS_FILE, AppPaths.CONFIG_FOLDER);

		} catch (IOException | SQLException e) {
			LOGGER.fatal("Cannot Initialise the EFSARCL library and accessory files", e);
			showInitError(TSEMessages.get("efsa.rcl.init.error", e.getMessage()));
			return db;
		} catch (DatabaseVersionException e) {

			LOGGER.error("Old version of the database found", e);
			e.printStackTrace();

			int val = ask(TSEMessages.get("db.need.removal", PropertiesReader.getSupportEmail()));

			// close application
			if (val == SWT.NO)
				return db;

			// delete the database
			try {

				// delete the old database
				db.delete();

				// reconnect to the database and
				// create a new one
				db.connect();

			} catch (IOException e1) {
				LOGGER.fatal("Unable to delete database", e1);
				e1.printStackTrace();
				
				showInitError(TSEMessages.get("db.removal.error"));

				return db;
			}
		}

		// create the main panel
		display = new Display();
		final Shell shell = new Shell(display);

		// set the application name in the shell
		shell.setText(PropertiesReader.getAppName() + " " + PropertiesReader.getAppVersion());

		// init services
		ITableDao dao = new TableDao();
		ITableDaoService daoService = new TableDaoService(dao);
		IFormulaService formulaService = new FormulaService(daoService);

		IGetAck getAck = new GetAck();
		IGetDatasetsList<IDataset> getDatasetsList = new GetDatasetsList<>();
		ISendMessage sendMessage = new SendMessage();
		IGetDataset getDataset = new GetDataset();

		TseReportService reportService = new TseReportService(getAck, getDatasetsList, sendMessage, getDataset,
				daoService, formulaService);

		// open the main panel

		try {
			MainPanel mainPanel = new MainPanel(shell, reportService, daoService, formulaService);
			mainPanel.create();
		} catch (Throwable e) {
			LOGGER.fatal("Generic error occurred", e);
			e.printStackTrace();

			Warnings.createFatal(TSEMessages.get("generic.error", PropertiesReader.getSupportEmail())).open(shell);

			return null;
		}

		// set the application icon into the shell
		Image image = new Image(Display.getCurrent(),
				ClassLoader.getSystemResourceAsStream(PropertiesReader.getAppIcon()));

		if (image != null)
			shell.setImage(image);

		// open also an help view for showing general help
		if (!DebugConfig.debug) {
			HtmlViewer help = new HtmlViewer();
			help.open(PropertiesReader.getStartupHelpURL());
		}

		// open the shell to the user
		shell.open();

		// check preferences
		if (!checkPreferences()) {
			PreferencesDialog pref = new PreferencesDialog(shell);
			pref.open();

			TableRow preferences = daoService.getAll(TableSchemaList.getByName(CustomStrings.PREFERENCES_SHEET)).get(0);

			// if the preferences were not set
			try {
				if (!reportService.getMandatoryFieldNotFilled(preferences).isEmpty()) {
					// close the application
					return db;
				}
			} catch (FormulaException e) {
				LOGGER.error("Cannot check if preferences were set", e);
				e.printStackTrace();
			}
		}

		// check settings
		if (!checkSettings()) {
			SettingsDialog settingsDialog = new SettingsDialog(shell, reportService, daoService);
			settingsDialog.open();

			TableRow settings = daoService.getAll(TableSchemaList.getByName(CustomStrings.SETTINGS_SHEET)).get(0);

			try {
				if (!reportService.getMandatoryFieldNotFilled(settings).isEmpty())
					return db;
			} catch (FormulaException e) {
				LOGGER.error("Cannot check if settings were set", e);
				e.printStackTrace();
			}

			LOGGER.debug("Opening new report dialog");

			// force the user to create a report after inserting preferences/settings
			ReportCreatorDialog dialog = new ReportCreatorDialog(shell, reportService);
			dialog.setButtonText(TSEMessages.get("new.report.button"));
			dialog.open();

		} else {
			// if settings are not opened, then login the user
			// with the current credentials
			loginUser();
		}

		// Event loop
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch())
				display.sleep();
		}

		return db;
	}
}
