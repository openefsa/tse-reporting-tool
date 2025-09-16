package tse_report;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import dataset.DatasetList;
import dataset.IDataset;
import i18n_messages.TSEMessages;
import session_manager.TSERestoreableWindowDao;
import table_database.TableDao;
import table_dialog.DialogBuilder;
import table_dialog.RowValidatorLabelProvider;
import table_dialog.TableDialog;
import table_skeleton.TableRow;
import tse_config.CustomStrings;
import window_restorer.RestoreableWindow;
import xlsx_reader.TableSchema;
import xml_catalog_reader.Selection;

public class ReportListDialog extends TableDialog {
	
	private static final Logger LOGGER = LogManager.getLogger(ReportListDialog.class);

	private RestoreableWindow window;
	private static final String WINDOW_CODE = "ReportList";

	private TseReport selectedReport;

	public ReportListDialog(Shell parent, String title) {
		super(parent, title, true, true);

		// create the parent structure
		super.create();

		this.window = new RestoreableWindow(getDialog(), WINDOW_CODE);
		window.restore(TSERestoreableWindowDao.class);
		window.saveOnClosure(TSERestoreableWindowDao.class);

	}

	@Override
	public String getSchemaSheetName() {
		return CustomStrings.REPORT_SHEET;
	}

	@Override
	public Collection<TableRow> loadInitialRows(TableSchema schema, TableRow parentTable) {

		TableDao dao = new TableDao();
		Collection<TableRow> reports = dao.getAll(schema);

		// convert to dataset list
		DatasetList tseReports = new DatasetList();
		for (TableRow r : reports) {
			TseReport report = new TseReport(r);
			if (report.isVisible()) {
				tseReports.add(report);
			}
		}

		// get only last versions
		DatasetList lastVersions = tseReports.filterOldVersions();

		// sort the list
		lastVersions.sort();

		reports.clear();

		// convert back to table row
		for (IDataset dataset : lastVersions) {

			TseReport tseReport = (TseReport) dataset;

			reports.add(new TableRow(tseReport));
		}

		return reports;
	}

	@Override
	public boolean apply(TableSchema schema, Collection<TableRow> rows, TableRow selectedRow) {

		if (selectedRow == null) {
			LOGGER.info("There is no selected row");
			warnUser(TSEMessages.get("error.title"), TSEMessages.get("report.not.selected"));
			return false;
		}

		this.selectedReport = new TseReport(selectedRow);

		return true;
	}

	public TseReport getSelectedReport() {
		return selectedReport;
	}

	@Override
	public Menu createMenu() {
		return null;
	}

	@Override
	public TableRow createNewRow(TableSchema schema, Selection type) {
		return null;
	}

	@Override
	public void processNewRow(TableRow row) {
	}

	@Override
	public RowValidatorLabelProvider getValidator() {
		return null;
	}

	@Override
	public void addWidgets(DialogBuilder viewer) {
		viewer.addHelp(getDialog().getText(), true).addTable(CustomStrings.REPORT_SHEET, false);
	}

	@Override
	public void nextLevel() {
		this.apply(this.getSchema(), null, this.getSelection());
		this.getDialog().dispose();
	}

}
