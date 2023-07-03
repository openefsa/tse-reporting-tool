package tse_main.listeners;

import i18n_messages.TSEMessages;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import providers.TseReportService;
import table_skeleton.TableRow;
import tse_config.CustomStrings;
import tse_main.MainPanel;
import tse_report.ReportListDialog;
import tse_report.TseReport;

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
        MessageBox mb = new MessageBox(shell, SWT.ICON_QUESTION | SWT.OK | SWT.CANCEL);
        mb.setText("My info");
        mb.setMessage(TSEMessages.get("copy.report.warning"));

        // if the user press cancel then return
        if (mb.open() == SWT.CANCEL)
            return;

        // copy the report into the opened report
        TableRow sourceReport = dialog.getSelectedReport();
        if (sourceReport == null){
            LOGGER.info("There is no report to continue");
            return;
        }

        LOGGER.info("Copying report data from reportId={} to reportId={}",
                sourceReport.getCode(CustomStrings.SENDER_DATASET_ID_COL),
                mainPanel.getOpenedReport().getSenderId());
        reportService.copyReport(new TseReport(sourceReport), mainPanel.getOpenedReport());
        LOGGER.info("Copied report data from reportId={} to reportId={}",
                sourceReport.getCode(CustomStrings.SENDER_DATASET_ID_COL),
                mainPanel.getOpenedReport().getSenderId());

        shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
        mainPanel.refresh();
        shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent arg0) {
    }
}
