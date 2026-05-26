package tse_options;

import java.io.IOException;
import java.util.Collection;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.xml.sax.SAXException;

import amend_manager.AmendException;
import app_config.PropertiesReader;
import dataset.RCLDatasetStatus;
import formula.FormulaException;
import global_utils.Message;
import global_utils.Warnings;
import i18n_messages.TSEMessages;
import message.MessageConfigBuilder;
import message.SendMessageException;
import message_creator.OperationType;
import providers.ITableDaoService;
import providers.TseReportService;
import report.ReportException;
import soap.DetailedSOAPException;
import table_dialog.DialogBuilder;
import table_dialog.RowValidatorLabelProvider;
import table_skeleton.TableCell;
import table_skeleton.TableRow;
import table_skeleton.TableVersion;
import tse_config.CustomStrings;
import tse_config.TSEWarnings;
import tse_report.TseReport;
import tse_validator.SimpleRowValidatorLabelProvider;
import user.User;
import xlsx_reader.TableSchema;
import xml_catalog_reader.Selection;

/**
 * Dialog which is used to set global settings for the application
 * @author avonva
 * @author shahaal
 *
 */
public class SettingsDialog extends OptionsDialog {

	private static final Logger LOGGER = LogManager.getLogger(SettingsDialog.class);
	
	public static final String WINDOW_CODE = "Settings";
	
	private TseReportService reportService;
	private ITableDaoService daoService;
	
	public SettingsDialog(Shell parent, TseReportService reportService, ITableDaoService daoService) {
		super(parent, TSEMessages.get("settings.title"), WINDOW_CODE);
		this.reportService = reportService;
		this.daoService = daoService;
	}

	@Override
	public String getSchemaSheetName() {
		return CustomStrings.SETTINGS_SHEET;
	}
	
	@Override
	public boolean apply(TableSchema schema, Collection<TableRow> rows, TableRow selectedRow) {

		boolean closeWindow = super.apply(schema, rows, selectedRow);
		
		TableRow settings = rows.iterator().next();
		
		if (settings == null) {
			LOGGER.info("No settings found to apply");
			return closeWindow;
		}
		
		login(settings);
		
		return closeWindow;
	}
	
	private static boolean login(TableRow settings) {
		
		// get credentials
		TableCell usernameVal = settings.get(CustomStrings.SETTINGS_USERNAME);
		TableCell passwordVal = settings.get(CustomStrings.SETTINGS_PASSWORD);
		TableCell orgVal = settings.get(CustomStrings.SETTINGS_ORG_CODE);

		if (usernameVal == null || passwordVal == null) {
			LOGGER.info("The username or password is empty");
			return false;
		}
		
		// login the user
		String username = usernameVal.getLabel();
		String password = passwordVal.getLabel();

		User.getInstance().login(username, password);
		
		if (orgVal != null)
			User.getInstance().addData(CustomStrings.SETTINGS_ORG_CODE, orgVal.getLabel());
		
		return true;
	}
	
	@Override
	public Menu createMenu() {
		
		Menu menu = new Menu(getDialog());
		MenuItem test = new MenuItem(menu, SWT.PUSH);
		test.setText(TSEMessages.get("settings.test.connection"));
		
		// check if it is possible to make a get dataset
		// list request and possibly download the
		// test report if present. If not present, it
		// creates the test report and send it to the dcf
		test.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				testConnection();
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {}
		});
		
		return menu;
	}

	@Override
	public TableRow createNewRow(TableSchema schema, Selection type) {
		return null;
	}

	@Override
	public void processNewRow(TableRow row) {}
	
	@Override
	public RowValidatorLabelProvider getValidator() {
		return new SimpleRowValidatorLabelProvider();
	}
	

	/**
	 * Test the connection with the inserted credentials
	 */
	void testConnection() {
		
		getPanelBuilder().selectRow(0);
		TableRow settings = getPanelBuilder().getTableElements().iterator().next();

		boolean mandatoryFilled = false;

		try {
			mandatoryFilled = settings == null || reportService.getMandatoryFieldNotFilled(settings).isEmpty();
		} catch (FormulaException e1) {
			LOGGER.error("Error in getting mandatory fields not filled");
			e1.printStackTrace();
		}
		
		if (!mandatoryFilled) {
			
			LOGGER.error("Cannot perform test connection. Credentials may be missing.");
			
			Warnings.warnUser(getDialog(), 
					TSEMessages.get("error.title"), 
					TSEMessages.get("settings.test.connection.warning"));
			return;
		}
		
		Message m = Warnings.create(TSEMessages.get("warning.title"), 
				TSEMessages.get("test.connection.confirm"), 
				SWT.ICON_QUESTION | SWT.YES | SWT.NO);
		
		int val = m.open(getDialog());
		
		if (val != SWT.YES)
			return;
		
		// the message xml builder requires data saved in the
		// database because of the RELATION formulas
		daoService.update(settings);

		// login the user if not done before
		boolean ok = login(getSelection());
		
		if (!ok)
			return;
		
		LOGGER.info("Test connection started");
		
		// change the cursor to wait
		getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
		
		Message msg = ActionsTestConnection.manageTestConnectionWithReport(reportService, daoService);

		LOGGER.info("Test connection successfully completed");
		
		getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			
		// if we have an error message stop and show the error
		if (msg != null) {
			msg.open(getDialog());
		}
	}

	
	@Override
	public void addWidgets(DialogBuilder viewer) {
		
		MouseListener listener = new MouseListener() {
			
			@Override
			public void mouseUp(MouseEvent arg0) {
				viewer.setPasswordVisibility(CustomStrings.SETTINGS_PASSWORD, false);
			}
			
			@Override
			public void mouseDown(MouseEvent arg0) {
				viewer.setPasswordVisibility(CustomStrings.SETTINGS_PASSWORD, true);
			}
			
			@Override
			public void mouseDoubleClick(MouseEvent arg0) {}
		};
		
		SelectionAdapter testConnectionListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				testConnection();
			}
		};
		
		// add image to edit button
		Image testConnImg = new Image(getDialog().getDisplay(), this.getClass()
				.getClassLoader().getResourceAsStream("test-connection.png"));
		
		Image showPwdImg = new Image(getDialog().getDisplay(), this.getClass()
				.getClassLoader().getResourceAsStream("show-password.png"));
		
		viewer.addHelp(TSEMessages.get("settings.help.title"))
			.addGroup("toolbar", TSEMessages.get("si.toolbar.title"), new GridLayout(2, false), null)
			.addButtonToComposite("showPwdBtn", "toolbar", TSEMessages.get("settings.show.password"), listener)
			.addButtonImage("showPwdBtn", showPwdImg)
			.addButtonToComposite("testConBtn", "toolbar", TSEMessages.get("settings.test.connection"), testConnectionListener)
			.addButtonImage("testConBtn", testConnImg)
			.addTable(CustomStrings.SETTINGS_SHEET, true);
	}

	@Override
	public void nextLevel() {
		// TODO Auto-generated method stub
		
	}
}
