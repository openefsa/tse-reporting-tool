package tse_amend_report;

import data_collection.DcfDataCollectionsList;
import data_collection.GetAvailableDataCollections;
import data_collection.IDataCollectionsDialog;
import data_collection.IDcfDataCollection;
import data_collection.IDcfDataCollectionsList;
import dataset.RCLDatasetStatus;
import global_utils.Warnings;
import i18n_messages.Messages;
import i18n_messages.TSEMessages;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.widgets.Shell;
import providers.ITableDaoService;
import providers.TseReportService;
import report.Report;
import soap.DetailedSOAPException;
import tse_data_collection.TSEDataCollectionsListDialog;
import tse_report.TseReport;

public class ReportAmendDialog {
   private static final String AMEND_REPORT_LIST_DIALOG_WINDOW_CODE = "MassAmendReportsListDialog";
   private final Logger LOGGER;
   private final Shell shell;
   private final TseReportService reportService;
   private final ITableDaoService daoService;

   public ReportAmendDialog(Shell shell, TseReportService reportService, ITableDaoService daoService) {
      this.shell = Objects.requireNonNull(shell);
      this.reportService = Objects.requireNonNull(reportService);
      this.daoService = Objects.requireNonNull(daoService);
      this.LOGGER = LogManager.getLogger(this.getClass());
   }

   public void open() throws DetailedSOAPException {
      this.shell.setCursor(this.shell.getDisplay().getSystemCursor(1));
      IDcfDataCollectionsList<IDcfDataCollection> dcWithAmends = this.getDataCollectionsWithAmendedReports();
      if (dcWithAmends.isEmpty()) {
         Warnings.create(Messages.get("dc.no.amends.found")).open(this.shell);
      } else {
         IDataCollectionsDialog dcDialog = this.getDataCollectionsDialog(this.shell, dcWithAmends, "dc.dialog.button.amend.datasets");
         IDcfDataCollection selectedDc = dcDialog.open("dc.dialog.amend.title");
         if (!Objects.isNull(selectedDc)) {
            AmendReportListDialog amendedReportsDialog = this.getAmendedReportsDialog(this.shell, selectedDc.getCode());
            amendedReportsDialog.open();
         }
      }
   }

   private IDcfDataCollectionsList<IDcfDataCollection> getDataCollectionsWithAmendedReports() throws DetailedSOAPException {
      IDcfDataCollectionsList var3;
      try {
         List<String> dcCodes = this.reportService
            .getAllReports()
            .stream()
            .map(TseReport::new)
            .filter(r -> Integer.parseInt(r.getVersion()) > 0)
            .filter(r -> Boolean.FALSE.equals(r.getRCLStatus().isFinalized()))
            .filter(r -> Boolean.FALSE.equals(RCLDatasetStatus.DRAFT.equals(r.getRCLStatus())))
            .map(Report::getDcCode)
            .distinct()
            .collect(Collectors.toList());
         var3 = GetAvailableDataCollections.getAvailableDcList()
            .stream()
            .filter(dc -> dcCodes.contains(dc.getCode()))
            .collect(Collectors.toCollection(DcfDataCollectionsList::new));
      } finally {
         this.shell.setCursor(this.shell.getDisplay().getSystemCursor(0));
      }

      return var3;
   }

   public IDataCollectionsDialog getDataCollectionsDialog(Shell shell1, IDcfDataCollectionsList<IDcfDataCollection> list, String buttonTextKey) {
      this.LOGGER.debug("Creating the DataCollectionsListDialog");
      return new TSEDataCollectionsListDialog(shell1, list, buttonTextKey);
   }

   public AmendReportListDialog getAmendedReportsDialog(Shell shell, String dcCode) {
      this.LOGGER.debug("Creating the MassAmendReportDialog");
      return new AmendReportListDialog(shell, "MassAmendReportsListDialog", dcCode, this.reportService, this.daoService);
   }

   public void end() {
      String title = TSEMessages.get("success.title");
      String message = TSEMessages.get("download.success");
      int style = 2;
      Warnings.warnUser(this.shell, title, message, style);
   }
}
