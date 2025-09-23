package tse_amend_report;

import java.time.LocalDate;
import java.util.Objects;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;
import table_skeleton.TableRow;

public class MassAmendReportLabelProvider extends ColumnLabelProvider {
   private String key;

   public MassAmendReportLabelProvider(String key) {
      this.key = key;
   }

   @Override
   public void addListener(ILabelProviderListener arg0) {
   }

   @Override
   public void dispose() {
   }

   @Override
   public boolean isLabelProperty(Object arg0, String arg1) {
      return false;
   }

   @Override
   public void removeListener(ILabelProviderListener arg0) {
   }

   @Override
   public Image getImage(Object arg0) {
      return null;
   }

   @Override
   public String getText(Object arg0) {
      TableRow tableRow = (TableRow)arg0;
      String var4 = this.key;
      switch (this.key.hashCode()) {
         case 3704893:
            if (var4.equals("year")) {
               return String.valueOf(tableRow.getCode("reportYear"));
            }
            break;
         case 104080000:
            if (var4.equals("month")) {
               String text = String.valueOf(tableRow.getCode("reportMonth"));
               if (Objects.nonNull(text)) {
                  text = LocalDate.of(2000, Integer.parseInt(text), 1).getMonth().toString();
                  text = text.substring(0, 1) + text.substring(1).toLowerCase();
               }

               return text;
            }
            break;
         case 957831062:
            if (var4.equals("country")) {
               return String.valueOf(tableRow.getLabel("country"));
            }
      }

      String text = "";
      return text;
   }
}
