package tse_amend_report;

import app_config.PropertiesReader;
import dataset.RCLDatasetStatus;
import global_utils.Message;
import global_utils.Warnings;
import i18n_messages.Messages;
import i18n_messages.TSEMessages;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import message.MessageConfigBuilder;
import message_creator.OperationType;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import progress_bar.IndeterminateProgressDialog;
import providers.ITableDaoService;
import providers.TseReportService;
import report.Report;
import report.ReportActions;
import report.ReportType;
import report.ThreadFinishedListener;
import session_manager.TSERestoreableWindowDao;
import soap.DetailedSOAPException;
import table_dialog.DialogBuilder;
import table_skeleton.TableRowList;
import tse_report.RefreshStatusThread;
import tse_report.TseReport;
import tse_summarized_information.TseReportActions;
import window_restorer.RestoreableWindow;
import xlsx_reader.TableSchemaList;

public class AmendReportListDialog extends Dialog {
   private final String windowCode;
   private RestoreableWindow window;
   private final String dcCode;
   private List<TseReport> reports;
   private TseReport aggrReport;
   private DialogBuilder viewer;
   private TseReportService reportService;
   private ITableDaoService daoService;

   public AmendReportListDialog(Shell parent, String windowCode, String dcCode, TseReportService reportService, ITableDaoService daoService) {
      super(new Shell(parent), 66800);
      this.windowCode = windowCode;
      this.dcCode = Objects.requireNonNull(dcCode);
      this.daoService = Objects.requireNonNull(daoService);
      this.reportService = Objects.requireNonNull(reportService);
      this.loadAmendedMonthlyReports();
   }

   private void loadAmendedMonthlyReports() {
      this.aggrReport = this.reportService
         .getAllReports()
         .stream()
         .map(TseReport::new)
         .filter(r -> ReportType.COLLECTION_AGGREGATION.equals(r.getType()))
         .filter(r -> r.getDcCode().equals(this.dcCode))
         .filter(r -> Integer.parseInt(r.getVersion()) > 0)
         .filter(r -> Boolean.FALSE.equals(r.getRCLStatus().isFinalized()))
         .findAny()
         .orElse(null);
      if (Objects.nonNull(this.aggrReport)) {
         this.reports = this.reportService
            .getAllReports()
            .stream()
            .map(TseReport::new)
            .filter(r -> Objects.nonNull(r.getAggregatorId()))
            .filter(Report::isVisible)
            .filter(r -> this.aggrReport.getDatabaseId() == r.getAggregatorId())
            .filter(r -> r.getDcCode().equals(this.dcCode))
            .filter(r -> Integer.parseInt(r.getVersion()) > 0)
            .filter(r -> Boolean.FALSE.equals(r.getRCLStatus().isFinalized()))
            .collect(Collectors.toList());
      } else {
         this.reports = this.reportService
            .getAllReports()
            .stream()
            .map(TseReport::new)
            .filter(r -> r.getDcCode().equals(this.dcCode))
            .filter(r -> Integer.parseInt(r.getVersion()) > 0)
            .filter(r -> Boolean.FALSE.equals(r.getRCLStatus().isFinalized()))
            .filter(r -> Boolean.FALSE.equals(RCLDatasetStatus.DRAFT.equals(r.getRCLStatus())))
            .collect(Collectors.toList());
      }
   }

   protected void createContents(Shell shell) {
      SelectionListener sendListener = new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent arg0) {
            AmendReportListDialog.this.sendReports();
         }
      };
      SelectionListener submitListener = new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent arg0) {
            AmendReportListDialog.this.submit();
         }
      };
      SelectionListener refreshStateListener = new SelectionAdapter() {
         @Override
         public void widgetSelected(SelectionEvent arg0) {
            AmendReportListDialog.this.refreshStatuses();
         }
      };
      this.viewer = new DialogBuilder(shell);
      this.viewer
         .addComposite("panel", new GridLayout(1, false), new GridData(4, 4, true, false))
         .addGroupToComposite("buttonsComp", "panel", "Toolbar", new GridLayout(8, false), new GridData(4, 4, true, false))
         .addLabelToComposite("datasetLabel", "panel")
         .addLabelToComposite("statusLabel", "panel")
         .addButtonToComposite("sendBtn", "buttonsComp", "Send", sendListener)
         .addButtonToComposite("submitBtn", "buttonsComp", "Submit", submitListener)
         .addButtonToComposite("refreshBtn", "buttonsComp", "Refresh Status", refreshStateListener);
      this.addTableToComposite();
      this.initUI();
      this.updateUI();
      shell.pack();
      this.window = new RestoreableWindow(shell, this.windowCode);
      this.window.restore(TSERestoreableWindowDao.class);
      this.window.saveOnClosure(TSERestoreableWindowDao.class);
   }

   private void addTableToComposite() {
      Composite composite = new Composite(this.viewer.getComposite(), 0);
      composite.setLayout(new GridLayout());
      composite.setLayoutData(new GridData(4, 4, true, true));
      TableViewer table = new TableViewer(composite, 35584);
      table.getTable().setHeaderVisible(true);
      table.getTable().setLayoutData(new GridData(4, 4, true, true));
      table.setContentProvider(new MassAmendReportContentProvider());
      table.getTable().setLinesVisible(true);
      String[][] headers = new String[][]{
         {"year", Messages.get("ma.header.year")}, {"month", Messages.get("ma.header.month")}, {"country", Messages.get("ma.header.country")}
      };

      for (String[] header : headers) {
         TableViewerColumn col = new TableViewerColumn(table, 2048);
         col.getColumn().setText(header[1]);
         col.setLabelProvider(new MassAmendReportLabelProvider(header[0]));
         col.getColumn().setWidth(150);
      }

      table.setInput(new TableRowList(this.reports));
   }

   private void initUI() {
      this.viewer.setEnabled("refreshBtn", this.canRefreshStatus());
      this.viewer.setEnabled("sendBtn", this.canAllBeSent() && !this.listIsEmpty());
      this.viewer.setEnabled("submitBtn", this.canAllBeSubmitted());
      Image sendImage = new Image(Display.getCurrent(), this.getClass().getClassLoader().getResourceAsStream("send-icon.png"));
      this.viewer.addButtonImage("sendBtn", sendImage);
      Image submitImage = new Image(Display.getCurrent(), this.getClass().getClassLoader().getResourceAsStream("submit-icon.png"));
      this.viewer.addButtonImage("submitBtn", submitImage);
      Image refreshImage = new Image(Display.getCurrent(), this.getClass().getClassLoader().getResourceAsStream("refresh-icon.png"));
      this.viewer.addButtonImage("refreshBtn", refreshImage);
      this.refreshLabels();
   }

   public void updateUI() {
      this.viewer.setEnabled("sendBtn", this.canAllBeSent());
      this.viewer.setEnabled("submitBtn", this.canAllBeSubmitted());
      this.viewer.setEnabled("refreshBtn", this.canRefreshStatus());
      this.refreshLabels();
   }

   public void open() {
      Shell shell = new Shell(this.getParent(), 66800);
      shell.setLayout(new GridLayout(1, false));
      shell.setLayoutData(new GridData(16777216, 16777216, true, false));
      shell.setText(Messages.get("ma.dialog.title"));
      shell.setImage(this.getParent().getImage());
      this.createContents(shell);
      shell.open();
      Display display = this.getParent().getDisplay();

      while (!shell.isDisposed()) {
         if (!display.readAndDispatch()) {
            display.sleep();
         }
      }
   }

   public void close() {
      this.getParent().close();
   }

   public void refreshLabels() {
      this.viewer.setLabelText("datasetLabel", TSEMessages.get("si.dataset.id", TSEMessages.get("si.no.data")));
      this.viewer.setLabelText("statusLabel", TSEMessages.get("si.dataset.status", TSEMessages.get("si.no.data")));
      if (!this.reports.isEmpty()) {
         if (Objects.isNull(this.aggrReport)) {
            this.viewer.setLabelText("statusLabel", TSEMessages.get("si.dataset.status", this.reports.get(0).getRCLStatus().toString()));
            if (this.reports.size() == 1) {
               this.viewer.setLabelText("datasetLabel", TSEMessages.get("si.dataset.id", this.reports.get(0).getId()));
            }
         } else {
            this.viewer.setLabelText("datasetLabel", TSEMessages.get("si.dataset.id", this.aggrReport.getId()));
            this.viewer.setLabelText("statusLabel", TSEMessages.get("si.dataset.status", this.aggrReport.getRCLStatus().toString()));
         }
      }
   }

   private boolean canAllBeSent() {
      return !this.listIsEmpty() && this.reports.stream().allMatch(tseReport -> tseReport.getRCLStatus().equals(RCLDatasetStatus.LOCALLY_VALIDATED));
   }

   private boolean canAllBeSubmitted() {
      return !this.listIsEmpty() && this.reports.stream().allMatch(report -> report.getRCLStatus().canBeSubmitted());
   }

   private boolean canRefreshStatus() {
      return !this.listIsEmpty() && this.reports.stream().anyMatch(report -> report.getRCLStatus().canBeRefreshed());
   }

   private boolean listIsEmpty() {
      return this.reports.isEmpty();
   }

   private void sendReports() {
      this.viewer.setEnabled("sendBtn", false);
      if (this.reports.size() > 1) {
         this.aggrReport = this.reportService.createAggregatedReport(this.reports);
      }

      TseReport report = Objects.isNull(this.aggrReport) ? this.reports.get(0) : this.aggrReport;
      ReportActions actions = new TseReportActions(this.getParent(), report, this.reportService);
      actions.send(this.reportService.getSendMessageConfiguration(report), arg01 -> this.onSendReportComplete(report));
   }

   private void onSendReportComplete(TseReport report) {
      if (Boolean.FALSE.equals(ReportType.COLLECTION_AGGREGATION.equals(report.getType()))) {
         this.loadAmendedMonthlyReports();
         this.updateUI();
      } else {
         TseReport aggrReport = report.getAllVersions(this.daoService).stream().peek(r -> {
            if (Boolean.FALSE.equals(report.getVersion().equals(r.getVersion()))) {
               this.daoService.delete((Report)r);
            }
         }).filter(r -> report.getVersion().equals(r.getVersion())).map(TseReport.class::cast).findAny().get();
         this.reports.forEach(r -> {
            r.setRCLStatus(aggrReport.getRCLStatus());
            r.setAggregatorId(aggrReport.getDatabaseId());
            this.daoService.update(r);
         });
         this.loadAmendedMonthlyReports();
         this.updateUI();
      }
   }

   private void refreshStatuses() {
      final Shell shell = this.getParent();
      final TseReport report = this.reports.get(0);
      final IndeterminateProgressDialog progressBar = new IndeterminateProgressDialog(shell, 65536, TSEMessages.get("refresh.status.progress.bar.label"));
      progressBar.open();
      final RefreshStatusThread refreshStatus = new RefreshStatusThread(report, this.reportService, this.daoService);
      refreshStatus.setListener(
         new ThreadFinishedListener() {
            @Override
            public void finished(Runnable thread) {
               shell.getDisplay().asyncExec(() -> {
                  progressBar.close();
                  AmendReportListDialog.this.loadAmendedMonthlyReports();
                  AmendReportListDialog.this.updateUI();
                  Message log = refreshStatus.getLog();
                  if (log != null) {
                     log.open(shell);
                  }

                  if (AmendReportListDialog.this.listIsEmpty()) {
                     AmendReportListDialog.this.close();
                  }
               });
            }

            @Override
            public void terminated(Runnable thread, Exception e) {
               shell.getDisplay()
                  .asyncExec(
                     () -> {
                        progressBar.close();
                        Message msg = e instanceof DetailedSOAPException
                           ? Warnings.createSOAPWarning((DetailedSOAPException)e)
                           : Warnings.createFatal(TSEMessages.get("refresh.status.error", PropertiesReader.getSupportEmail()), report);
                        msg.open(shell);
                     }
                  );
            }
         }
      );
      refreshStatus.start();
   }

   private void submit() {
      if (!this.reports.isEmpty()) {
         this.getParent().setCursor(this.getParent().getDisplay().getSystemCursor(1));
         TseReport report = Optional.of(this.reports.get(0))
            .filter(Report::isAggregated)
            .map(r -> this.daoService.getById(TableSchemaList.getByName("Report"), r.getAggregatorId()))
            .map(TseReport::new)
            .orElse(this.reports.get(0));
         ReportActions actions = new TseReportActions(this.getParent(), report, this.reportService);
         MessageConfigBuilder config = this.reportService.getSendMessageConfiguration(report);
         config.setOpType(OperationType.SUBMIT);
         actions.perform(config, arg01 -> {
            this.reports.forEach(r -> {
               r.setRCLStatus(report.getRCLStatus());
               this.daoService.update(r);
            });
            this.loadAmendedMonthlyReports();
            this.updateUI();
            this.getParent().setCursor(this.getParent().getDisplay().getSystemCursor(0));
         });
      }
   }
}
