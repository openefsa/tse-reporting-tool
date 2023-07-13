package tse_summarized_information;

import java.io.File;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import app_config.AppPaths;
import app_config.PropertiesReader;
import dataset.RCLDatasetStatus;
import global_utils.Message;
import global_utils.Warnings;
import html_viewer.HtmlViewer;
import i18n_messages.TSEMessages;
import message.MessageConfigBuilder;
import message_creator.OperationType;
import progress_bar.IndeterminateProgressDialog;
import providers.IFormulaService;
import providers.ITableDaoService;
import providers.TseReportService;
import report.DisplayAckResult;
import report.DisplayAckThread;
import report.RefreshStatusThread;
import report.Report;
import report.ReportActions;
import report.ReportActions.ReportAction;
import report.ThreadFinishedListener;
import report_validator.ReportError;
import session_manager.TSERestoreableWindowDao;
import soap.DetailedSOAPException;
import table_dialog.DialogBuilder;
import table_dialog.EditorListener;
import table_dialog.RowValidatorLabelProvider;
import table_exporter.SummInfoToExcel;
import table_relations.Relation;
import table_skeleton.TableCell;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;
import table_skeleton.TableVersion;
import test_case.EnumPicker;
import test_case.NumberInputDialog;
import tse_case_report.CaseReportDialog;
import tse_components.TableDialogWithMenu;
import tse_config.CatalogLists;
import tse_config.CustomStrings;
import tse_config.DebugConfig;
import tse_main.TseFileDialog;
import tse_report.TseReport;
import tse_validator.SummarizedInfoValidator;
import tse_validator.TseReportValidator;
import window_restorer.RestoreableWindow;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;
import xml_catalog_reader.Selection;

/**
 * Class which allows adding and editing a summarized information report.
 * 
 * @author avonva
 * @author shahaal
 *
 */
public class SummarizedInfoDialog extends TableDialogWithMenu {

	protected static final Logger LOGGER = LogManager.getLogger(SummarizedInfoDialog.class);

	protected TseReportService reportService;
	protected ITableDaoService daoService;
	private IFormulaService formulaService;

	private RestoreableWindow window;
	private static final String WINDOW_CODE = "SummarizedInformation";

	protected TseReport report;

	public SummarizedInfoDialog(Shell parent, TseReportService reportService, ITableDaoService daoService,
			IFormulaService formulaService) {

		super(parent, "", false, false);

		this.reportService = reportService;
		this.daoService = daoService;
		this.formulaService = formulaService;

		// create the parent structure
		super.create();

		// default disabled
		setRowCreationEnabled(false);

		this.window = new RestoreableWindow(getDialog(), WINDOW_CODE);
		boolean restored = window.restore(TSERestoreableWindowDao.class);
		window.saveOnClosure(TSERestoreableWindowDao.class);

		// add 300 px in height
		if (!restored)
			addHeight(300);

		setEditorListener(new EditorListener() {

			@Override
			public void editStarted() {
			}

			@Override
			public void editEnded(TableRow row, TableColumn field, boolean changed) {

				if (changed) {
					switch (field.getId()) {
					/*
					 * Not used case CustomStrings.TARGET_GROUP_COL:
					 * row.remove(CustomStrings.AN_METH_TYPE_COL);
					 * row.remove(CustomStrings.AN_METH_CODE_COL); break;
					 */
					case CustomStrings.AN_METH_TYPE_COL:
						row.remove(CustomStrings.AN_METH_CODE_COL);
						break;
					default:
						break;
					}
				}
			}
		});

	}

	@Override
	public Menu createMenu() {

		Menu menu = super.createMenu();

		MenuItem addCase = new MenuItem(menu, SWT.PUSH);
		addCase.setText(TSEMessages.get("si.open.cases"));
		addCase.setEnabled(false);

		addTableSelectionListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent arg0) {

				// check if RGT, if this the case doesn't enable the open sample form
				TableRow rowSelected = getSelection();

				// check if row has been selected
				if (rowSelected != null) {
					// check if row is RGT
					boolean isRGT = rowSelected.getCode(CustomStrings.SUMMARIZED_INFO_TYPE)
							.equals(CustomStrings.SUMMARIZED_INFO_RGT_TYPE);

					addCase.setEnabled(!isTableEmpty() && !isRGT);
				}
			}
		});

		addCase.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				nextLevel();
			}
		});

		addRemoveMenuItem(menu);
		addCloneMenuItem(menu);

		return menu;
	}

	/**
	 * Validate the row
	 * 
	 * @param summInfo
	 * @return
	 */
	boolean validate(TableRow summInfo) {

		if (!summInfo.areMandatoryFilled() && report.isEditable()) {
			warnUser(TSEMessages.get("error.title"), TSEMessages.get("si.open.cases.error"));
			return false;
		}

		return true;
	}

	/**
	 * Open the cases dialog of the summarized information
	 */
	void openCases(SummarizedInfo summInfo) {

		Relation.emptyCache();

		// create a case passing also the report information
		CaseReportDialog dialog = new CaseReportDialog(getDialog(), this.report, summInfo, reportService, daoService,
				formulaService);

		// filter the records by the clicked summarized information
		dialog.setParentFilter(summInfo);

		dialog.open();

		// set case errors if present
		reportService.updateChildrenErrors(summInfo);

		replace(summInfo);
	}

	@Override
	public void setParentFilter(TableRow parentFilter) {

		this.report = new TseReport(parentFilter);

		// update ui with report data
		updateUI();

		super.setParentFilter(parentFilter);
	}

	/**
	 * Get the report that contains the summarized information
	 * 
	 * @return
	 */
	public TseReport getReport() {
		return report;
	}

	@Override
	public void clear() {
		super.clear();
		this.report = null;
		initUI(); // report was closed => update ui
	}

	/**
	 * Create a new row with default values
	 * 
	 * @param element
	 * @return
	 * @throws IOException
	 */
	@Override
	public TableRow createNewRow(TableSchema schema, Selection element) {

		TableCell value = new TableCell(element);

		/*
		 * monguma: removed now more RGT rows can be created // if random genotyping if
		 * (value.getCode().equals(CustomStrings.SUMMARIZED_INFO_RGT_TYPE)) { // check
		 * if already inserted for (TableRow row : this.getLoadedRows()) { if
		 * (row.getCode(CustomStrings.SUMMARIZED_INFO_TYPE).equals(CustomStrings.
		 * SUMMARIZED_INFO_RGT_TYPE)) { // cannot add two RGT!
		 * 
		 * Warnings.warnUser(getDialog(), TSEMessages.get("warning.title"),
		 * TSEMessages.get("cannot.have.two.rgt"), SWT.ICON_WARNING);
		 * 
		 * return null; } } }
		 */

		// create a new summarized information
		SummarizedInfo si = new SummarizedInfo(CustomStrings.SUMMARIZED_INFO_TYPE, value);

		try {
			Relation.injectGlobalParent(si, CustomStrings.SETTINGS_SHEET);
			Relation.injectGlobalParent(si, CustomStrings.PREFERENCES_SHEET);
			Relation.injectParent(report, si);
		} catch (Exception e) {
			LOGGER.error("Cannot create a new summarized information", e);
			e.printStackTrace();
		}

		return si;
	}

	@Override
	public String getSchemaSheetName() {
		return CustomStrings.SUMMARIZED_INFO_SHEET;
	}

	@Override
	public boolean apply(TableSchema schema, Collection<TableRow> rows, TableRow selectedRow) {
		return true;
	}

	@Override
	public Collection<TableRow> loadInitialRows(TableSchema schema, TableRow parentFilter) {
		return null;
	}

	@Override
	public void processNewRow(TableRow row) {
	}

	@Override
	public RowValidatorLabelProvider getValidator() {
		return new SummarizedInfoValidator(daoService);
	}

	@Override
	public void addWidgets(DialogBuilder viewer) {

		SelectionListener refreshStateListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				// if no report opened, stop
				if (report == null) {
					LOGGER.info("There is no report to continue upon adding widgets");
					return;
				}

				IndeterminateProgressDialog progressBar = new IndeterminateProgressDialog(getDialog(),
						SWT.APPLICATION_MODAL, TSEMessages.get("refresh.status.progress.bar.label"));

				progressBar.open();

				RefreshStatusThread refreshStatus = new RefreshStatusThread(report, reportService);

				refreshStatus.setListener(new ThreadFinishedListener() {

					@Override
					public void finished(Runnable thread) {

						getDialog().getDisplay().asyncExec(new Runnable() {

							@Override
							public void run() {
								updateUI();

								progressBar.close();

								Message log = refreshStatus.getLog();

								if (log != null)
									log.open(getDialog());
							}
						});
					}

					@Override
					public void terminated(Runnable thread, Exception e) {

						getDialog().getDisplay().asyncExec(new Runnable() {

							@Override
							public void run() {
								progressBar.close();

								Message msg = (e instanceof DetailedSOAPException)
										? Warnings.createSOAPWarning((DetailedSOAPException) e)
										: Warnings.createFatal(TSEMessages.get("refresh.status.error",
												PropertiesReader.getSupportEmail()), report);

								msg.open(getDialog());
							}
						});
					}
				});

				refreshStatus.start();
			}
		};

		SelectionListener editListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				// if no report opened, stop
				if (report == null){
					LOGGER.info("There is no report to continue for editListener");
					return;
				}

				int val = warnUser(TSEMessages.get("warning.title"), TSEMessages.get("edit.confirm"),
						SWT.ICON_WARNING | SWT.YES | SWT.NO);

				if (val == SWT.NO)
					return;

				// yes, overwrite
				report.makeEditable();
				report.update();

				// update the ui accordingly
				updateUI();
			}
		};

		SelectionListener checkListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				if (report == null){
					LOGGER.info("There is no report to continue for checkListener");
					return;
				}

				// validate and show the errors in the browser
				TseReportValidator validator = new TseReportValidator(report, reportService, daoService);
				try {

					getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_WAIT));

					// validate the report
					Collection<ReportError> errors = validator.validate();

					getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_ARROW));

					// if no errors update report status
					if (errors.isEmpty()) {
						report.setStatus(RCLDatasetStatus.LOCALLY_VALIDATED);
						report.update();
						updateUI();
						warnUser(TSEMessages.get("success.title"), TSEMessages.get("check.success"),
								SWT.ICON_INFORMATION);
					} else { // otherwise show them to the user
						validator.show(errors);
						warnUser(TSEMessages.get("error.title"), TSEMessages.get("check.report.failed"));
					}

				} catch (Exception e) {
					getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_ARROW));

					LOGGER.error("Cannot validate the report=" + report.getSenderId(), e);
					e.printStackTrace();

					warnUser(TSEMessages.get("error.title"),
							TSEMessages.get("check.report.error", PropertiesReader.getSupportEmail()));
				}
			}
		};

		SelectionListener sendListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				if (report == null){
					LOGGER.info("There is no report to continue");
					return;
				}
				boolean ok = askConfirmation(ReportAction.SEND);

				if (!ok)
					return;

				String dc = PropertiesReader.getDataCollectionCode(report.getYear());
				int val = Warnings.warnUser(getDialog(), TSEMessages.get("warning.title"),
						TSEMessages.get("send.confirm.dc", dc), SWT.ICON_WARNING | SWT.YES | SWT.NO);

				if (val != SWT.YES)
					return;

				ReportActions actions = new TseReportActions(getDialog(), report, reportService);
				actions.send(reportService.getSendMessageConfiguration(report), new Listener() {

					@Override
					public void handleEvent(Event arg01) {
						updateUI();
					}
				});
			}
		};
		
		SelectionListener exportListener = new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent arg0) {

				Shell shell = getDialog();

				if (report == null) {
					LOGGER.info("There is no report to continue for exportListener");
					Warnings.warnUser(shell, TSEMessages.get("error.title"), TSEMessages.get("report.noreport.error"));
					return;
				}

				// get records from summarized information
				Collection<TableRow> records = report.getRecords(daoService,
						TableSchemaList.getByName(CustomStrings.SUMMARIZED_INFO_SHEET));
				// get headers from summarized information
				Collection<TableColumn> headers = records.iterator().next().getVisibleColumns();
				// initiate the summarized information exporter
				SummInfoToExcel exporter = new SummInfoToExcel();
				// create the workbook with the summarised info records
				XSSFWorkbook wb = exporter.createWorkbookFromTable(headers, records);

				// save the file
				TseFileDialog fileDialog = new TseFileDialog(shell);
				String filename = TableVersion.mergeNameAndVersion(report.getSenderId(), report.getVersion());
				File exportFile = fileDialog.saveXlsx(filename);

				if (exportFile == null) {
					LOGGER.info("There is no file to export");
					return;
				}	

				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
				exporter.dumpWorkbookToAFile(shell, wb, exportFile);
				shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
			}
		};

		/*
		 * // TODO uncomment for adding Reject function SelectionListener rejectListener
		 * = new SelectionAdapter() {
		 * 
		 * @Override public void widgetSelected(SelectionEvent arg0) {
		 * 
		 * if (report == null) return;
		 * 
		 * ReportActions actions = new TseReportActions(getDialog(), report,
		 * reportService);
		 * 
		 * MessageConfigBuilder config =
		 * reportService.getSendMessageConfiguration(report);
		 * config.setOpType(OperationType.REJECT);
		 * 
		 * // reject the report and update the ui actions.perform(config, new Listener()
		 * {
		 * 
		 * @Override public void handleEvent(Event arg0) { updateUI(); } }); } };
		 */

		SelectionListener submitListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				if (report == null){
					LOGGER.info("There is no report to continue for submitListener");
					return;
				}

				ReportActions actions = new TseReportActions(getDialog(), report, reportService);

				MessageConfigBuilder config = reportService.getSendMessageConfiguration(report);
				config.setOpType(OperationType.SUBMIT);

				// reject the report and update the ui
				actions.perform(config, new Listener() {

					@Override
					public void handleEvent(Event arg01) {
						updateUI();
					}
				});
			}
		};

		SelectionListener displayAckListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				if (report == null){
					LOGGER.info("There is no report to continue for displayAckListener");
					return;
				}

				IndeterminateProgressDialog progressBar = new IndeterminateProgressDialog(getDialog(),
						SWT.APPLICATION_MODAL, TSEMessages.get("display.ack.progress.bar.label"));

				progressBar.open();

				DisplayAckThread displayAck = new DisplayAckThread(report, reportService);

				displayAck.setListener(new ThreadFinishedListener() {

					@Override
					public void finished(Runnable thread) {

						getDialog().getDisplay().asyncExec(new Runnable() {

							@Override
							public void run() {
								updateUI();

								progressBar.close();

								DisplayAckResult log = displayAck.getDisplayAckResult();

								if (log != null) {

									for (Message m : log.getMessages())
										m.open(getDialog());

									// open the ack in the browser to see it formatted
									if (log.getDownloadedAck() != null) {

										HtmlViewer viewer1 = new HtmlViewer();
										viewer1.open(log.getDownloadedAck());
									}
								}
							}
						});
					}

					@Override
					public void terminated(Runnable thread, Exception e) {
						getDialog().getDisplay().asyncExec(new Runnable() {

							@Override
							public void run() {

								progressBar.close();

								Message msg = (e instanceof DetailedSOAPException)
										? Warnings.createSOAPWarning((DetailedSOAPException) e)
										: Warnings.createFatal(TSEMessages.get("display.ack.error",
												PropertiesReader.getSupportEmail()), report);

								msg.open(getDialog());
							}
						});
					}
				});

				displayAck.start();
			}
		};

		SelectionListener amendListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {

				if (report == null){
					LOGGER.info("There is no report to continue for amendListener");
					return;
				}

				TseReportActions actions = new TseReportActions(getDialog(), report, reportService);

				Report newVersion = actions.amend();

				if (newVersion == null){
					LOGGER.info("There is no new version to continue");
					return;
				}

				// open the new version in the tool
				setParentFilter(newVersion);
			}
		};

		SelectionListener changeStatusListener = new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent arg0) {

				EnumPicker<RCLDatasetStatus> dialog = new EnumPicker<>(getDialog(), RCLDatasetStatus.class);
				dialog.setDefaultValue(report.getRCLStatus());
				dialog.open();

				RCLDatasetStatus status = (RCLDatasetStatus) dialog.getSelection();

				if (status == null){
					LOGGER.info("There is no status to continue for changeStatusListener");
					return;
				}

				// update the report status and UI
				report.setStatus(status);
				report.update();
				updateUI();
			}
		};

		/*
		 * TODO uncomment for the beta tester
		 * 
		 * SelectionListener getAcceptedDwhMsgBeta = new SelectionAdapter() {
		 * 
		 * @Override public void widgetSelected(SelectionEvent arg0) {
		 * 
		 * if (report == null) return;
		 * 
		 * ReportActions actions = new TseReportActions(getDialog(), report,
		 * reportService);
		 * 
		 * MessageConfigBuilder config =
		 * reportService.getAcceptDwhBetaMessageConfiguration(report);
		 * config.setOpType(OperationType.ACCEPTED_DWH_BETA);
		 * 
		 * // reject the report and update the ui actions.perform(config, new Listener()
		 * {
		 * 
		 * @Override public void handleEvent(Event arg0) { updateUI(); } });
		 * 
		 * } };
		 */

		SelectionListener changeMessageIdListener = new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent arg0) {

				NumberInputDialog dialog = new NumberInputDialog(getDialog());
				dialog.setDefaultValue(report.getMessageId());
				Integer messageIdNumeric = dialog.open();

				String messageId = "";
				if (messageIdNumeric != null) {
					messageId = String.valueOf(messageIdNumeric);
				}

				if (!dialog.wasCancelled()) {

					report.setMessageId(messageId);
					report.update();
					updateUI();
				}
			}
		};

		SelectionListener changeDatasetIdListener = new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent arg0) {

				NumberInputDialog dialog = new NumberInputDialog(getDialog());
				dialog.setDefaultValue(report.getId());
				Integer dataIdNumeric = dialog.open();

				String datasetId = "";
				if (dataIdNumeric != null) {
					datasetId = String.valueOf(dataIdNumeric);
				}

				if (!dialog.wasCancelled()) {
					report.setId(datasetId);
					report.update();
					updateUI();
				}
			}
		};

		viewer
			.addHelp(TSEMessages.get("si.help.title"))
			.addComposite("labelsComp", new GridLayout(1, false), new GridData(SWT.FILL, SWT.FILL, true, false))
				.addLabelToComposite("reportLabel", "labelsComp")
				.addLabelToComposite("statusLabel", "labelsComp")
				.addLabelToComposite("messageIdLabel", "labelsComp")
				.addLabelToComposite("datasetIdLabel", "labelsComp");

		if (DebugConfig.debug)
			viewer.addLabelToComposite("senderDatasetIdLabel", "labelsComp");

		// if debug add change status button
		if (DebugConfig.debug) {
			viewer.addGroup("debugPanel", TSEMessages.get("si.debug.panel"), new GridLayout(3, false), null)
					.addButtonToComposite("changeStatusBtn", "debugPanel", TSEMessages.get("si.debug.change.status"),
							changeStatusListener)
					.addButtonToComposite("changeMessageIdBtn", "debugPanel", TSEMessages.get("si.debug.change.mexid"),
							changeMessageIdListener)
					.addButtonToComposite("changeDatasetIdBtn", "debugPanel", TSEMessages.get("si.debug.change.dataid"),
							changeDatasetIdListener);
		}

		/*
		 * TODO if in beta test add the change report status button if
		 * (DebugConfig.betaTest) { viewer.addGroup("betaTesterPanel",
		 * TSEMessages.get("si.beta.tester.panel"), new GridLayout(1, false), null)
		 * .addButtonToComposite("betaAcceptedDwh", "betaTesterPanel",
		 * TSEMessages.get("si.beta.accepted.dwh"), getAcceptedDwhMsgBeta); }
		 */

		viewer.addComposite("panel", new GridLayout(1, false), new GridData(SWT.FILL, SWT.FILL, true, false))

				// add the toolbar composite
				.addGroupToComposite("buttonsComp", "panel", TSEMessages.get("si.toolbar.title"),
						new GridLayout(8, false), new GridData(SWT.FILL, SWT.FILL, true, false))

				// add the buttons to the toolbar
				.addButtonToComposite("editBtn", "buttonsComp", TSEMessages.get("si.toolbar.edit"), editListener)
				.addButtonToComposite("validateBtn", "buttonsComp", TSEMessages.get("si.toolbar.check"), checkListener)
				.addButtonToComposite("sendBtn", "buttonsComp", TSEMessages.get("si.toolbar.send"), sendListener)
				.addButtonToComposite("submitBtn", "buttonsComp", TSEMessages.get("si.toolbar.submit"), submitListener)
				.addButtonToComposite("amendBtn", "buttonsComp", TSEMessages.get("si.toolbar.amend"), amendListener)
				.addButtonToComposite("refreshBtn", "buttonsComp", TSEMessages.get("si.toolbar.refresh.status"),
						refreshStateListener)
				.addButtonToComposite("displayAckBtn", "buttonsComp", TSEMessages.get("si.toolbar.display.ack"),
						displayAckListener)
				.addButtonToComposite("exportExcelBtn", "buttonsComp", TSEMessages.get("si.toolbar.export"),
						exportListener)

				// add the row creator composite
				.addGroupToComposite("rowCreatorComp", "panel", TSEMessages.get("si.add.record"),
						new GridLayout(1, false), new GridData(SWT.FILL, SWT.FILL, true, false))
				.addRowCreatorToComposite("rowCreatorComp", TSEMessages.get("si.add.record.label"),
						CatalogLists.TSE_LIST)

				// add the table
				.addTable(CustomStrings.SUMMARIZED_INFO_SHEET, true);

		initUI();
	}

	/**
	 * Initialise the labels to their initial state
	 */
	private void initUI() {

		DialogBuilder panel = getPanelBuilder();

		// disable refresh until a report is opened
		panel.setEnabled("refreshBtn", false);
		panel.setEnabled("validateBtn", false);
		panel.setEnabled("editBtn", false);
		panel.setEnabled("sendBtn", false);
		// panel.setEnabled("rejectBtn", false);
		panel.setEnabled("submitBtn", false);
		panel.setEnabled("amendBtn", false);
		panel.setEnabled("displayAckBtn", false);
		panel.setEnabled("exportExcelBtn", false);
		panel.setEnabled("changeStatusBtn", false);
		panel.setEnabled("changeMessageIdBtn", false);
		panel.setEnabled("changeDatasetIdBtn", false);
		panel.setEnabled("betaAcceptedDwh", false);

		// add image to edit button
		Image editImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("edit-icon.png"));
		panel.addButtonImage("editBtn", editImage);

		// add image to send button
		Image validateImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("checkReport-icon.png"));
		panel.addButtonImage("validateBtn", validateImage);

		// add image to send button
		Image sendImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("send-icon.png"));
		panel.addButtonImage("sendBtn", sendImage);

		// add image to refresh button
		Image submitImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("submit-icon.png"));
		panel.addButtonImage("submitBtn", submitImage);

		// add image to send button
		Image amendImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("amend-icon.png"));
		panel.addButtonImage("amendBtn", amendImage);

		// add image to send button
		/*
		 * Image rejectImage = new Image(Display.getCurrent(),
		 * this.getClass().getClassLoader().getResourceAsStream("reject-icon.png"));
		 * panel.addButtonImage("rejectBtn", rejectImage);
		 */

		// add image to send button
		Image displayAckImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("displayAck-icon.png"));
		panel.addButtonImage("displayAckBtn", displayAckImage);

		Image exportExcelImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("export-icon.png"));
		panel.addButtonImage("exportExcelBtn", exportExcelImage);

		// add image to refresh button
		Image refreshImage = new Image(Display.getCurrent(),
				this.getClass().getClassLoader().getResourceAsStream("refresh-icon.png"));
		panel.addButtonImage("refreshBtn", refreshImage);

		panel.setLabelText("reportLabel", TSEMessages.get("si.report.void"));
		panel.setLabelText("statusLabel", TSEMessages.get("si.dataset.status", TSEMessages.get("si.no.data")));

		panel.setLabelText("messageIdLabel", TSEMessages.get("si.message.id", TSEMessages.get("si.no.data")));

		panel.setLabelText("datasetIdLabel", TSEMessages.get("si.dataset.id", TSEMessages.get("si.no.data")));

		if (DebugConfig.debug)
			panel.setLabelText("senderDatasetIdLabel",
					TSEMessages.get("si.sender.dataset.id", TSEMessages.get("si.no.data")));
	}

	/**
	 * Update the ui using the report information
	 * 
	 * @param report
	 */
	public void updateUI() {

		String reportMonth = report.getLabel(AppPaths.REPORT_MONTH_COL);
		String reportYear = report.getYear();
		String status = report.getRCLStatus().getLabel();
		String messageId = report.getMessageId();
		String datasetId = report.getId();
		String senderId = report.getSenderId();

		String reportRow;

		// add version if possible
		if (!TableVersion.isFirstVersion(report.getVersion())) {
			int revisionInt = Integer.valueOf(report.getVersion()); // remove 0 from 01..
			String revision = String.valueOf(revisionInt);
			reportRow = TSEMessages.get("si.report.opened.revision", reportYear, reportMonth, revision);
		} else {
			reportRow = TSEMessages.get("si.report.opened", reportYear, reportMonth);
		}

		String statusRow = TSEMessages.get("si.dataset.status", checkField(status, RCLDatasetStatus.DRAFT.getLabel()));

		String messageRow = TSEMessages.get("si.message.id", checkField(messageId, TSEMessages.get("si.missing.data")));

		String datasetRow = TSEMessages.get("si.dataset.id", checkField(datasetId, TSEMessages.get("si.missing.data")));

		String senderDatasetId = TSEMessages.get("si.sender.dataset.id",
				checkField(senderId, TSEMessages.get("si.missing.data")));

		DialogBuilder panel = getPanelBuilder();
		panel.setLabelText("reportLabel", reportRow);
		panel.setLabelText("statusLabel", statusRow);
		panel.setLabelText("messageIdLabel", messageRow);
		panel.setLabelText("datasetIdLabel", datasetRow);

		if (DebugConfig.debug)
			panel.setLabelText("senderDatasetIdLabel", senderDatasetId);

		// enable the table only if report status if correct
		RCLDatasetStatus datasetStatus = RCLDatasetStatus.fromString(status);
		boolean editableReport = datasetStatus.isEditable();
		panel.setTableEditable(editableReport);
		panel.setRowCreatorEnabled(editableReport);

		panel.setEnabled("editBtn", !DebugConfig.disableMainPanel && datasetStatus.canBeMadeEditable());
		panel.setEnabled("validateBtn", !DebugConfig.disableMainPanel && datasetStatus.canBeChecked());
		panel.setEnabled("sendBtn", !DebugConfig.disableMainPanel && datasetStatus.canBeSent());
		panel.setEnabled("amendBtn", !DebugConfig.disableMainPanel && datasetStatus.canBeAmended());
		panel.setEnabled("submitBtn", !DebugConfig.disableMainPanel && datasetStatus.canBeSubmitted());
		// panel.setEnabled("rejectBtn", !DebugConfig.disableMainPanel &&
		// datasetStatus.canBeRejected());
		panel.setEnabled("displayAckBtn", !DebugConfig.disableMainPanel && datasetStatus.canDisplayAck());
		panel.setEnabled("exportExcelBtn", !DebugConfig.disableMainPanel && report!=null);
		panel.setEnabled("refreshBtn", !DebugConfig.disableMainPanel && datasetStatus.canBeRefreshed());
		panel.setEnabled("changeStatusBtn", true);
		panel.setEnabled("changeMessageIdBtn", true);
		panel.setEnabled("changeDatasetIdBtn", true);

		panel.setEnabled("betaAcceptedDwh", datasetStatus.getStatus().contains("SUBMITTED"));
	}

	protected boolean askConfirmation(ReportAction action) {

		String title = TSEMessages.get("warning.title");
		String message = null;
		switch (action) {
		case SUBMIT:
			message = TSEMessages.get("submit.confirm");
			break;
		case REJECT:
			message = TSEMessages.get("reject.confirm");
			break;
		case SEND:
			message = TSEMessages.get("send.confirm");
			break;
		/*
		 * case ACCEPTED_DWH_BETA: message =
		 * TSEMessages.get("beta_accepted_dhw.confirm"); break;
		 */
		case AMEND:
			message = TSEMessages.get("amend.confirm");
			break;
		default:
			break;
		}

		if (message == null)
			return false;

		int val = Warnings.warnUser(getDialog(), title, message, SWT.ICON_WARNING | SWT.YES | SWT.NO);

		return val == SWT.YES;
	}

	/**
	 * Check if a field is null or empty. If so return the default value, otherwise
	 * return the field itself.
	 * 
	 * @param field
	 * @param defaultValue
	 * @return
	 */
	private static String checkField(String field, String defaultValue) {

		String out = null;

		if (field != null && !field.isEmpty())
			out = field;
		else
			out = defaultValue;

		return out;
	}

	@Override
	public void nextLevel() {

		TableRow row = getSelection();

		if (row == null)
			return;

		// check if row is RGT
		boolean isRGT = row.getCode(CustomStrings.SUMMARIZED_INFO_TYPE).equals(CustomStrings.SUMMARIZED_INFO_RGT_TYPE);

		// if the row is RGT dont open the sample case
		if (isRGT)
			return;

		SummarizedInfo summInfo = new SummarizedInfo(row);

		// first validate the content of the row
		if (!validate(summInfo) && isEditable())
			return;

		openCases(summInfo);

	}
}
