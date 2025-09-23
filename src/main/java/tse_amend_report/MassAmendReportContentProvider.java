package tse_amend_report;

import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import table_skeleton.TableRowList;

public class MassAmendReportContentProvider implements IStructuredContentProvider {
   @Override
   public void dispose() {
   }

   @Override
   public void inputChanged(Viewer arg0, Object arg1, Object arg2) {
   }

   @Override
   public Object[] getElements(Object arg0) {
      if (arg0 instanceof TableRowList) {
         TableRowList list = (TableRowList)arg0;
         return list.toArray();
      } else {
         return null;
      }
   }
}
