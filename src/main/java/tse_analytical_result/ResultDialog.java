package tse_analytical_result;

import java.io.IOException;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import app_config.AppPaths;
import dataset.RCLDatasetStatus;
import global_utils.Warnings;
import i18n_messages.TSEMessages;
import providers.IFormulaService;
import providers.ITableDaoService;
import providers.PredefinedResultService;
import providers.TseReportService;
import report.Report;
import session_manager.TSERestoreableWindowDao;
import table_dialog.DialogBuilder;
import table_dialog.RowValidatorLabelProvider;
import table_relations.Relation;
import table_skeleton.TableRow;
import table_skeleton.TableRowList;
import tse_case_report.CaseReport;
import tse_components.TableDialogWithMenu;
import tse_config.CustomStrings;
import tse_summarized_information.SummarizedInfo;
import tse_validator.ResultValidator;
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
public class ResultDialog extends TableDialogWithMenu {

	static final Logger LOGGER = LogManager.getLogger(ResultDialog.class);
	private static final String WINDOW_CODE = "AnalyticalResult";

	private final TseReportService reportService;
	private final ITableDaoService daoService;

	private final Report report;
	private final SummarizedInfo summInfo;
	private final CaseReport caseInfo;

	public ResultDialog(Shell parent, Report report, SummarizedInfo summInfo, CaseReport caseInfo,
			TseReportService reportService, ITableDaoService daoService, IFormulaService formulaService) {

		super(parent, TSEMessages.get("result.title"), true, false);

		this.report = report;
		this.summInfo = summInfo;
		this.caseInfo = caseInfo;
		this.reportService = reportService;
		this.daoService = daoService;
		// create the dialog
		super.create();

		RestoreableWindow window = new RestoreableWindow(getDialog(), WINDOW_CODE);
		boolean restored = window.restore(TSERestoreableWindowDao.class);
		window.saveOnClosure(TSERestoreableWindowDao.class);

		// add 300 px in height
		if (!restored)
			addHeight(300);

		PredefinedResultService resultService = new PredefinedResultService(daoService, formulaService);
		setEditorListener(new ResultEditorListener(resultService, summInfo, caseInfo));

		updateUI();
	}

	public void askForDefault() {
		boolean hasNoChildren = !reportService.hasChildren(caseInfo, TableSchemaList.getByName(CustomStrings.RESULT_SHEET));
		if (isEditable() && hasNoChildren) {
			// for RGT create directly the record
			if (!this.summInfo.isRGT()) {
				int val = Warnings.warnUser(
						getDialog(),
						TSEMessages.get("warning.title"),
						TSEMessages.get("result.confirm.default"),
						SWT.YES | SWT.NO | SWT.ICON_QUESTION
				);
				LOGGER.info("Add default results to the list? {}", (val == SWT.YES));
				if (val == SWT.NO)
					return;
			}

			try {
				TableRowList results = reportService.createDefaultResults(report, summInfo, caseInfo);
				this.setRows(results);
				// warn user only if not RGT
				if (!this.summInfo.isRGT()) {
					warnUser(TSEMessages.get("warning.title"), TSEMessages.get("result.check.default"), SWT.ICON_WARNING);
				}
				LOGGER.info("Created {} default results for case {}", results.size(), caseInfo.getDatabaseId());
			} catch (IOException e) {
				LOGGER.error("Cannot create predefined results for caseId {} {}", caseInfo.getDatabaseId(), e);
			}
		}
	}

	/**
	 * make table non editable if needed
	 */
	private void updateUI() {
		LOGGER.info("Updating GUI");
		DialogBuilder panel = getPanelBuilder();
		String status = report.getLabel(AppPaths.REPORT_STATUS);
		RCLDatasetStatus datasetStatus = RCLDatasetStatus.fromString(status);
		boolean editableReport = datasetStatus.isEditable();
		panel.setTableEditable(editableReport);
		panel.setRowCreatorEnabled(editableReport);
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
		TableRow row = new TableRow(schema);
		Relation.injectParents(row, report, summInfo, caseInfo);
		LOGGER.info("Creating a new row for analytical results: {}", row.getDatabaseId());
		return row;
	}

	@Override
	public String getSchemaSheetName() {
		return CustomStrings.RESULT_SHEET;
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
		TableRowList results = this.getRows();
		if (results.size() <= 1)
			return;

		int max = Integer.MIN_VALUE;
		for (TableRow result : results) {
			if (result.getDatabaseId() == row.getDatabaseId())
				continue;

			String seq = result.getLabel(CustomStrings.AN_PORT_SEQ_COL);
			int candidateMax = 0;
			try {
				candidateMax = Integer.parseInt(seq);
			} catch (NumberFormatException e) {
				LOGGER.error("Trying to parse new row but could not get integer value from seq ", e);
				e.printStackTrace();
			}

			if (candidateMax > max)
				max = candidateMax;
		}

		row.put(CustomStrings.AN_PORT_SEQ_COL, max + 1);

		daoService.update(row);
		this.refresh(row);
	}

	@Override
	public RowValidatorLabelProvider getValidator() {
		return new ResultValidator();
	}

	@Override
	public void nextLevel() {
	}
	
	@Override
	public Menu createMenu() {
		Menu menu = super.createMenu();
		addRemoveMenuItem(menu);
		// addCloneMenuItem(menu);
		return menu;
	}

	@Override
	public void addWidgets(DialogBuilder viewer) {
		String sampleId = caseInfo.getLabel(CustomStrings.SAMPLE_ID_COL);
		String animalId = caseInfo.getLabel(CustomStrings.ANIMAL_ID_COL);
		String caseId = caseInfo.getLabel(CustomStrings.NATIONAL_CASE_ID_COL);

		String sampleIdRow = TSEMessages.get("result.sample.id", sampleId);
		String animalIdRow = TSEMessages.get("result.animal.id", animalId);
		String caseIdRow = TSEMessages.get("result.case.id", caseId);

		viewer.addHelp(TSEMessages.get("result.help.title"))
			.addComposite("labelsComp", new GridLayout(1, false), null);

		if (!sampleId.isEmpty())
			viewer.addLabelToComposite("sampLabel", sampleIdRow, "labelsComp");

		if (!animalId.isEmpty())
			viewer.addLabelToComposite("animalLabel", animalIdRow, "labelsComp");

		if (!caseId.isEmpty())
			viewer.addLabelToComposite("caseIdLabel", caseIdRow, "labelsComp");

		viewer.addRowCreator(TSEMessages.get("result.add.record"))
			.addTable(CustomStrings.RESULT_SHEET, true, report, summInfo, caseInfo); 
	}

}
