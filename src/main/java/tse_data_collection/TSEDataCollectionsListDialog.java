package tse_data_collection;

import data_collection.DataCollectionsListDialog;
import data_collection.IDcfDataCollection;
import data_collection.IDcfDataCollectionsList;
import org.eclipse.swt.widgets.Shell;
import session_manager.TSERestoreableWindowDao;
import window_restorer.RestoreableWindow;

public class TSEDataCollectionsListDialog extends DataCollectionsListDialog {
   private static final String WINDOW_CODE = "DataCollectionsListDialog";
   private RestoreableWindow window;

   public TSEDataCollectionsListDialog(Shell parent, IDcfDataCollectionsList<IDcfDataCollection> list, String buttonTextKey) {
      super(parent, list, 66800, buttonTextKey);
   }

   @Override
   protected void createContents(Shell shell) {
      super.createContents(shell);
      this.window = new RestoreableWindow(shell, "DataCollectionsListDialog");
      this.window.restore(TSERestoreableWindowDao.class);
      this.window.saveOnClosure(TSERestoreableWindowDao.class);
   }
}
