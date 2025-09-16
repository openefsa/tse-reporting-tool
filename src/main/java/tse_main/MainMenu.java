package tse_main;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.xml.sax.SAXException;

import amend_manager.AmendException;
import app_config.PropertiesReader;
import dataset.Dataset;
import formula.FormulaException;
import global_utils.Message;
import global_utils.Warnings;
import i18n_messages.TSEMessages;
import message.MessageConfigBuilder;
import message_creator.OperationType;
import providers.IFormulaService;
import providers.IReportService;
import providers.ITableDaoService;
import providers.TseReportService;
import report.EFSAReport;
import report.Report;
import report.ReportException;
import report.ReportSendOperation;
import report_downloader.TseReportDownloader;
import report_downloader.TseReportImporter;
import soap.DetailedSOAPException;
import table_database.TableDao;
import table_skeleton.TableRow;
import table_skeleton.TableVersion;
import test_case.EnumPicker;
import tse_amend_report.ReportAmendDialog;
import tse_areaselector.AreaListSelectorDialog;
import tse_config.CustomStrings;
import tse_config.DebugConfig;
import tse_main.listeners.CopySelectionListener;
import tse_options.PreferencesDialog;
import tse_options.SettingsDialog;
import tse_report.ReportCreatorDialog;
import tse_report.ReportListDialog;
import tse_report.TseReport;
import user_interface.ProxySettingsDialog;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;

/**
 * Create the main menu of the application in the given shell
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class MainMenu {
	static final Logger LOGGER = LogManager.getLogger(MainMenu.class);

	protected TseReportService reportService;
	protected ITableDaoService daoService;
	protected IFormulaService formulaService;

	protected MainPanel mainPanel;
	protected Shell shell;

	private Menu main;
	private Menu fileMenu;

	private MenuItem file;
	private MenuItem preferences;
	private MenuItem settings;
	private MenuItem proxyConfig;
	private MenuItem catalogueSelector;

	protected MenuItem newReport;
	protected MenuItem openReport;
	protected MenuItem closeReport;
	protected MenuItem importReport;
	protected MenuItem copyReport;
	protected MenuItem downloadReport;
	protected MenuItem amendReports;
	protected MenuItem exportReport;
	protected MenuItem exitApplication;

	// TODO to finish
	// private MenuItem importExcelReport;

	public MainMenu(MainPanel mainPanel, Shell shell, TseReportService reportService, ITableDaoService daoService,
			IFormulaService formulaService) {
		this.shell = shell;
		this.mainPanel = mainPanel;
		this.reportService = reportService;
		this.daoService = daoService;
		this.formulaService = formulaService;
	}

	public void create() {
		main = new Menu(shell, SWT.BAR);
		fileMenu = new Menu(shell, SWT.DROP_DOWN);

		file = new MenuItem(main, SWT.CASCADE);
		file.setText(TSEMessages.get("file.item"));
		file.setMenu(fileMenu);

				// enable report only if there is a report in the database
		fileMenu.addListener(SWT.Show, arg0 -> {
				TableSchema schema = TseReport.getReportSchema();
				if (schema == null) {
					LOGGER.info("Schema was not found in report.");
					return;
				}

				TableDao dao = new TableDao();
				boolean hasReport = !dao.getAll(schema).isEmpty();
				boolean isReportOpened = mainPanel.getOpenedReport() != null;
				boolean editable = isReportOpened && mainPanel.getOpenedReport().isEditable();

				newReport.setEnabled(!isReportOpened);
				openReport.setEnabled(hasReport);
				closeReport.setEnabled(isReportOpened);
				downloadReport.setEnabled(!DebugConfig.disableFileFuncs && !isReportOpened);

				// can only export valid reports
				exportReport.setEnabled(isReportOpened && mainPanel.getOpenedReport().getRCLStatus().isValid());
				importReport.setEnabled(!DebugConfig.disableFileFuncs && editable);
			copyReport.setEnabled(isReportOpened && editable);

				// TODO enable import excel report if not report is currently opened
				// importExcelReport.setEnabled(editable);
		});

		preferences = new MenuItem(main, SWT.PUSH);
		preferences.setText(TSEMessages.get("pref.item"));

		settings = new MenuItem(main, SWT.PUSH);
		settings.setText(TSEMessages.get("settings.item"));

		proxyConfig = new MenuItem(main, SWT.PUSH);
		proxyConfig.setText(TSEMessages.get("proxy.config.item"));

		proxyConfig.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				ProxySettingsDialog dialog = new ProxySettingsDialog(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
				try {
					dialog.open();
				} catch (Exception e) {
					LOGGER.error("Cannot open proxy dialog", e);
					e.printStackTrace();

					Message m = Warnings.createFatal(
							TSEMessages.get("proxy.config.file.not.found.error", PropertiesReader.getSupportEmail()));

					m.open(shell);
				}
			}
		});
		
		catalogueSelector = new MenuItem(main, SWT.PUSH);
		catalogueSelector.setText(TSEMessages.get("arealist.config.item"));

		catalogueSelector.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				AreaListSelectorDialog dialog = new AreaListSelectorDialog(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
				try {
					dialog.open();
				} catch (Exception e) {
					LOGGER.error("Cannot open proxy dialog", e);
					e.printStackTrace();

					Message m = Warnings.createFatal(
							TSEMessages.get("proxy.config.file.not.found.error", PropertiesReader.getSupportEmail()));

					m.open(shell);
				}
			}
		});

		// add buttons to the file menu
		newReport = new MenuItem(fileMenu, SWT.PUSH);
		newReport.setText(TSEMessages.get("new.report.item"));
		newReport.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				LOGGER.debug("Opening new report dialog");

				ReportCreatorDialog dialog = new ReportCreatorDialog(shell, reportService);
				dialog.setButtonText(TSEMessages.get("new.report.button"));
				dialog.open();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		openReport = new MenuItem(fileMenu, SWT.PUSH);
		openReport.setText(TSEMessages.get("open.report.item"));
		openReport.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening open report dialog");
				ReportListDialog dialog = new ReportListDialog(shell, TSEMessages.get("open.report.title"));
				dialog.setButtonText(TSEMessages.get("open.report.button"));
				dialog.open();

				TseReport report = dialog.getSelectedReport();
				if (report == null){
					LOGGER.info("There is no report to continue");
					return;
				}
				LOGGER.info("Opening report=" + report.getSenderId());

				mainPanel.setEnabled(true);
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
				mainPanel.openReport(report);
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		closeReport = new MenuItem(fileMenu, SWT.PUSH);
		closeReport.setText(TSEMessages.get("close.report.item"));
		closeReport.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.info("Closing report={}", mainPanel.getOpenedReport().getSenderId());
				mainPanel.closeReport();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		// by default we do not have a report opened at the beginning
		closeReport.setEnabled(false);

		// add buttons to the file menu
		importReport = new MenuItem(fileMenu, SWT.PUSH);
		importReport.setText(TSEMessages.get("import.report.item"));
		importReport.setEnabled(false);
		importReport.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening import report dialog");
				ReportListDialog dialog = new ReportListDialog(shell, TSEMessages.get("import.report.title"));
				dialog.setButtonText(TSEMessages.get("import.report.button"));
				dialog.open();

				// show dialog that the current report will be overwritten
				MessageBox mb = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
				mb.setText("My info");
				mb.setMessage(TSEMessages.get("import.report.warning"));

				// if the user press cancel then return
				if (mb.open() == SWT.CANCEL)
					return;

				// import the report into the opened report
				TableRow report = dialog.getSelectedReport();
				if (report == null){
					LOGGER.info("There is no report to continue");
					return;
				}

				// copy the report summarized information into the opened one
				TableSchema childSchema = TableSchemaList.getByName(CustomStrings.SUMMARIZED_INFO_SHEET);
				if (childSchema == null) {
					LOGGER.info("Could not find schema");
					return;
				}

				LOGGER.info("Importing summarized information from report="
						+ report.getCode(CustomStrings.SENDER_DATASET_ID_COL) + " to report="
						+ mainPanel.getOpenedReport().getSenderId());
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
				TseSummarizedInfoImporter importer = new TseSummarizedInfoImporter(daoService, formulaService);
				// copy the data into the selected report
				importer.copyByParent(childSchema, report, mainPanel.getOpenedReport());

				mainPanel.refresh();
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		// add Copy Report button to the file menu
		copyReport = new MenuItem(fileMenu, SWT.PUSH);
		copyReport.setText(TSEMessages.get("copy.report.item"));
		copyReport.setEnabled(false);
		copyReport.addSelectionListener(new CopySelectionListener(reportService, mainPanel, shell));

		/*
		 * TODO to be concluded the import excel function
		 * 
		 * // add buttons to the file menu this.importExcelReport = new
		 * MenuItem(fileMenu, SWT.PUSH);
		 * this.importExcelReport.setText(TSEMessages.get("import.excel_report.button"))
		 * ; this.importExcelReport.setEnabled(false);
		 * this.importExcelReport.addSelectionListener(new SelectionListener() {
		 * 
		 * @Override public void widgetSelected(SelectionEvent arg0) {
		 * 
		 * //choose the excel file to import TseFileDialog fileDialog = new
		 * TseFileDialog(shell); File excelFile = fileDialog.loadExcel();
		 * 
		 * // take the info of the current opened report TseReport report =
		 * mainPanel.getOpenedReport();
		 * 
		 * if (report == null) return;
		 * 
		 * LOGGER.debug("Importing excel in report " + report.getSenderId());
		 * MessageConfigBuilder messageConfig =
		 * reportService.getSendMessageConfiguration(report);
		 * messageConfig.setOpType(OperationType.INSERT);
		 * 
		 * // TODO this part of messageConfig should be replaced with the one received
		 * from the ExcelToXml class //
		 * System.out.println("msg config "+messageConfig.getMessageConfig().
		 * toXml(true));
		 * 
		 * try { reportService.export(report, messageConfig); } catch (IOException |
		 * ParserConfigurationException | SAXException | ReportException |
		 * AmendException e) { e.printStackTrace(); LOGGER.error("Export report failed",
		 * e); }
		 * 
		 * ExcelXmlConverter converter = new ExcelXmlConverter();
		 * 
		 * try { File file=converter.convertXExcelToXml(excelFile);
		 * 
		 * if (file == null) return;
		 * 
		 * TseReportImporter imp = new TseReportImporter(reportService, daoService);
		 * imp.importFirstDatasetVersion(file);
		 * 
		 * } catch (XMLStreamException | IOException | FormulaException | ParseException
		 * | ParserConfigurationException | TransformerException e) {
		 * e.printStackTrace(); }
		 * 
		 * LOGGER.debug("Opening import report dialog");
		 * 
		 * }
		 * 
		 * @Override public void widgetDefaultSelected(SelectionEvent arg0) {
		 * 
		 * } });
		 */

		downloadReport = new MenuItem(fileMenu, SWT.PUSH);
		downloadReport.setText(TSEMessages.get("download.report.item"));
		downloadReport.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening download report dialog");
				TseReportDownloader downloader = new TseReportDownloader(shell, reportService, daoService);
				try {
					downloader.download();
				} catch (DetailedSOAPException e) {
					LOGGER.error("Download report failed", e);
					e.printStackTrace();
					Warnings.showSOAPWarning(shell, e);
				}
			}
		});

		amendReports = new MenuItem(fileMenu, SWT.PUSH);
		amendReports.setText(TSEMessages.get("amend.report.item"));
	    amendReports.addSelectionListener(new SelectionListener() {
			@Override
	        public void widgetSelected(SelectionEvent arg0) {
	            MainMenu.LOGGER.debug("Opening submit amendments dialog");
	            ReportAmendDialog dataCollectionAmender = new ReportAmendDialog(shell, reportService, daoService);
	            try {
	              dataCollectionAmender.open();
	            } catch (DetailedSOAPException e) {
	              MainMenu.LOGGER.error("Amend reports failed", (Throwable)e);
	              e.printStackTrace();
	              Warnings.showSOAPWarning(shell, e);
	            } 
	          }

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		exportReport = new MenuItem(fileMenu, SWT.PUSH);
		exportReport.setText(TSEMessages.get("export.report.item"));
		exportReport.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening export report dialog");
				TseReport report = mainPanel.getOpenedReport();
				if (report == null) {
					LOGGER.info("There is no report to continue");
					Warnings.warnUser(shell, TSEMessages.get("error.title"), TSEMessages.get("report.noreport.error"));
					return;
				}

				// save the file
				TseFileDialog fileDialog = new TseFileDialog(shell);
				String filename = TableVersion.mergeNameAndVersion(report.getSenderId(), report.getVersion());
				File exportFile = fileDialog.saveXml(filename);
				if (exportFile == null) {
					LOGGER.info("Could not find file to export");
					return;
				}

				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

				ReportSendOperation opSendType = null;
				try {
					Dataset dataset = reportService.getDataset(report);
					opSendType = reportService.getSendOperation(report, dataset);
				} catch (DetailedSOAPException e1) {
					LOGGER.error("Error in Report Send Operation", e1);
					shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
					e1.printStackTrace();

					Warnings.showSOAPWarning(shell, e1);

					Message m = Warnings.create(TSEMessages.get("export.report.no.connection"));
					m.open(shell);
				} finally {
					shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
				}

				OperationType opType = null;

				// if no connection open the dialog
				if (opSendType == null) {
					EnumPicker<OperationType> dialog = new EnumPicker<>(shell, OperationType.class);
					dialog.setTitle(TSEMessages.get("export.report.op.title"));
					dialog.setConfirmText(TSEMessages.get("export.report.op.confirm"));
					dialog.open();

					opType = (OperationType) dialog.getSelection();
				} else {
					opType = opSendType.getOpType();
				}

				if (opType == null) {
					LOGGER.info("Operation Type not found");
					return;
				}	

				LOGGER.info("Exporting report=" + report.getSenderId());

				MessageConfigBuilder messageConfig = reportService.getSendMessageConfiguration(report);
				messageConfig.setOpType(opType);
				messageConfig.setOut(exportFile);

				try {
					reportService.export(report, messageConfig);
				} catch (IOException | ParserConfigurationException | SAXException | ReportException e) {
					LOGGER.error("Export report failed", e);
					e.printStackTrace();
				} catch (AmendException e) {
					LOGGER.error("Export report failed", e);
					e.printStackTrace();
					Warnings.warnUser(shell, TSEMessages.get("error.title"), TSEMessages.get("report.empty.error"));
				}

				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		exitApplication = new MenuItem(fileMenu, SWT.PUSH);
		exitApplication.setText(TSEMessages.get("close.app.item"));
		exitApplication.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				shell.close();
				shell.dispose();
			}
		});

		// open preferences
		preferences.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening preferences dialog");
				PreferencesDialog dialog = new PreferencesDialog(shell);
				dialog.open();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		// open settings
		settings.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening settings dialog");
				SettingsDialog dialog = new SettingsDialog(shell, reportService, daoService);
				dialog.open();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		if (DebugConfig.debug) {
			addDebugItems();
		}

		// set the menu
		shell.setMenuBar(main);
	}

	/**
	 * Add some debug functionalities
	 */
	private void addDebugItems() {
		MenuItem reportVersions = new MenuItem(fileMenu, SWT.PUSH);
		reportVersions.setText("[DEBUG] Print report versions");
		reportVersions.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening 'Print report versions' dialog");
				TseReport report = mainPanel.getOpenedReport();
				if (report == null){
					LOGGER.info("There is no report to continue");
					return;
				}
				LOGGER.debug("Report versions=" + report.getAllVersions(daoService));
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		MenuItem exportReport1 = new MenuItem(fileMenu, SWT.PUSH);
		exportReport1.setText("[DEBUG] Export report");
		exportReport1.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening 'Export report' dialog");
				TseReport report = mainPanel.getOpenedReport();
				if (report == null) {
					LOGGER.info("There is no report to continue");
					Warnings.warnUser(shell, TSEMessages.get("error.title"), TSEMessages.get("report.noreport.error"));
					return;
				}

				EnumPicker<OperationType> dialog = new EnumPicker<>(shell, OperationType.class);
				dialog.open();

				OperationType opType = (OperationType) dialog.getSelection();
				if (opType == null) {
					LOGGER.info("Operation Type not found");
					return;
				}	

				LOGGER.debug("Exporting report " + report.getSenderId());
				MessageConfigBuilder messageConfig = reportService.getSendMessageConfiguration(report);
				messageConfig.setOpType(opType);
				try {
					reportService.export(report, messageConfig);
				} catch (IOException | ParserConfigurationException | SAXException | ReportException
						| AmendException e) {
					LOGGER.error("Export report failed", e);
					e.printStackTrace();
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		MenuItem deleteReport = new MenuItem(fileMenu, SWT.PUSH);
		deleteReport.setText("[DEBUG] Delete report");
		deleteReport.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				ReportListDialog dialog = new ReportListDialog(shell, "Delete a report");
				dialog.setButtonText("Delete");
				dialog.open();
				LOGGER.debug("Opening 'Delete report' dialog");

				TseReport report = dialog.getSelectedReport();
				if (report == null) {
					LOGGER.info("There is no report to continue");
					return;
				}

				LOGGER.debug("Report " + report.getSenderId() + " deleted from disk");
				report.delete();
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		MenuItem getDc = new MenuItem(fileMenu, SWT.PUSH);
		getDc.setText("[DEBUG] Print current data collection");
		getDc.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening 'Print current data collection' dialog");
				TseReport report = mainPanel.getOpenedReport();
				String dcCode = report == null
						? PropertiesReader.getDataCollectionCode()
						: PropertiesReader.getDataCollectionCode(report.getYear());
				LOGGER.debug("The tool points to the data collection=" + dcCode);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});

		MenuItem importReport1 = new MenuItem(fileMenu, SWT.PUSH);
		importReport1.setText("[DEBUG] Import first version .xml report");
		importReport1.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				LOGGER.debug("Opening 'Import first version .xml report' dialog");
				TseFileDialog fileDialog = new TseFileDialog(shell);
				File file1 = fileDialog.loadXml();
				if (file1 == null) {
					LOGGER.info("File not found to import");
					return;
				}

				try {
					TseReportImporter imp = new TseReportImporter(reportService, daoService);
					imp.importFirstDatasetVersion(file1);
				} catch (XMLStreamException | IOException | FormulaException | ParseException e) {
					LOGGER.error("Import report failed", e);
					e.printStackTrace();
				}
			}
		});
	}
}
