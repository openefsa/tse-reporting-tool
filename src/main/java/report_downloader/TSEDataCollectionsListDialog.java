package report_downloader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import data_collection.DataCollectionsListDialog;
import data_collection.IDcfDataCollection;
import data_collection.IDcfDataCollectionsList;
import session_manager.TSERestoreableWindowDao;
import window_restorer.RestoreableWindow;

public class TSEDataCollectionsListDialog extends DataCollectionsListDialog {

	private static final String WINDOW_CODE = "DataCollectionsListDialog";
	private RestoreableWindow window;
	
	public TSEDataCollectionsListDialog(Shell parent, IDcfDataCollectionsList<IDcfDataCollection> list, String buttonTextKey) {
		super(parent, list, SWT.SHELL_TRIM | SWT.APPLICATION_MODAL, buttonTextKey);
	}
	
	@Override
	protected void createContents(Shell shell, String buttonTextKey) {
		
		super.createContents(shell, buttonTextKey);
		
		window = new RestoreableWindow(shell, WINDOW_CODE);
		window.restore(TSERestoreableWindowDao.class);
		window.saveOnClosure(TSERestoreableWindowDao.class);
	}
}
