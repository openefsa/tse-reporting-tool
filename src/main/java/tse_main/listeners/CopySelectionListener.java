package tse_main.listeners;

import i18n_messages.TSEMessages;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import providers.TseReportService;
import table_skeleton.TableRow;
import tse_main.MainPanel;
import tse_report.ReportListDialog;
import tse_report.TseReport;

import static org.eclipse.swt.SWT.*;
import static tse_config.CustomStrings.SENDER_DATASET_ID_COL;

public class CopySelectionListener implements SelectionListener {
    private static final Logger LOGGER = LogManager.getLogger(CopySelectionListener.class);

    private final TseReportService reportService;
    private final MainPanel mainPanel;
    private final Shell shell;

    public CopySelectionListener(TseReportService reportService, MainPanel mainPanel, Shell shell) {
        this.reportService = reportService;
        this.mainPanel = mainPanel;
        this.shell = shell;
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
        ReportListDialog dialog = new ReportListDialog(shell, TSEMessages.get("copy.report.title"));
        dialog.setButtonText(TSEMessages.get("copy.report.button"));
        dialog.open();

        // show dialog that the current report will be overwritten
        MessageBox mb = new MessageBox(shell, ICON_QUESTION | OK | CANCEL);
        mb.setText("My info");
        mb.setMessage(TSEMessages.get("copy.report.warning"));

        // if the user press cancel then return
        if (mb.open() == CANCEL)
            return;

        // copy the report into the opened report
        TableRow sourceReport = dialog.getSelectedReport();
        if (sourceReport == null){
            LOGGER.info("There is no report to continue");
            return;
        }

        TseReport currentReport = mainPanel.getOpenedReport();
        LOGGER.info("Copying report data from reportId={} to reportId={}", sourceReport.getCode(SENDER_DATASET_ID_COL), currentReport.getSenderId());
        reportService.copyReport(new TseReport(sourceReport), currentReport);
        LOGGER.info("Copied report data from reportId={} to reportId={}", sourceReport.getCode(SENDER_DATASET_ID_COL), currentReport.getSenderId());

        shell.setCursor(shell.getDisplay().getSystemCursor(CURSOR_WAIT));
        mainPanel.refresh();
        shell.setCursor(shell.getDisplay().getSystemCursor(CURSOR_ARROW));
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent arg0) {
        LOGGER.info("default selected {}", arg0);
    }
}
