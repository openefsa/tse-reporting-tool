package tse_report;

import global_utils.Message;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import providers.ITableDaoService;
import providers.TseReportService;
import report.ThreadFinishedListener;
import xlsx_reader.TableSchemaList;

public class RefreshStatusThread extends Thread {
   private TseReportService reportService;
   private ITableDaoService daoService;
   private ThreadFinishedListener listener;
   private TseReport report;
   private Message result;

   public RefreshStatusThread(TseReport report, TseReportService reportService, ITableDaoService daoService) {
      this.report = Objects.requireNonNull(report);
      this.reportService = Objects.requireNonNull(reportService);
      this.daoService = Objects.requireNonNull(daoService);
   }

   public void setListener(ThreadFinishedListener listener) {
      this.listener = listener;
   }

   @Override
   public void run() {
      if (this.report.isAggregated()) {
         this.result = this.refreshAggregatedReport(this.report);
      } else {
         this.result = this.reportService.refreshStatus(this.report);
      }

      if (this.listener != null) {
         this.listener.finished(this);
      }
   }

   public Message getLog() {
      return this.result;
   }

   private Message refreshAggregatedReport(TseReport report) {
      TseReport aggrReport = Optional.ofNullable(this.daoService.getById(TableSchemaList.getByName("Report"), report.getAggregatorId()))
         .map(TseReport::new)
         .orElse(null);
      if (Objects.isNull(aggrReport)) {
         return this.reportService.refreshStatus(report);
      } else {
         this.result = this.reportService.refreshStatus(aggrReport);
         List<TseReport> aggregatedReports = this.daoService
            .getByStringField(TableSchemaList.getByName("Report"), "aggregatorId", String.valueOf(aggrReport.getDatabaseId()))
            .stream()
            .map(TseReport::new)
            .collect(Collectors.toList());
         aggregatedReports.forEach(rep -> {
            rep.setRCLStatus(aggrReport.getRCLStatus());
            if (aggrReport.getRCLStatus().isFinalized()) {
               rep.setAggregatorId(null);
            }

            this.daoService.update(rep);
         });
         if (aggrReport.getRCLStatus().isFinalized()) {
            this.daoService.delete(aggrReport);
         }

         this.report = aggregatedReports.stream().filter(r -> r.getDatabaseId() == this.report.getDatabaseId()).findAny().orElse(this.report);
         return this.result;
      }
   }
}
