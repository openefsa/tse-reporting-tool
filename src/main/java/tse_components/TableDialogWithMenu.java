package tse_components;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import global_utils.Warnings;
import i18n_messages.TSEMessages;
import table_dialog.HelpViewer;
import table_dialog.RowCreatorViewer;
import table_dialog.TableDialog;
import table_dialog.TableView;
import table_skeleton.TableColumn;
import table_skeleton.TableRow;
import tse_case_report.CaseReportDialog;
import tse_config.DebugConfig;

/**
 * Generic class that provides an interface to create {@link TableRow} objects
 * through their schema.
 * 
 * @author avonva
 * @author shahaal
 *
 */
public abstract class TableDialogWithMenu extends TableDialog {
	
	static final Logger LOGGER = LogManager.getLogger(TableDialogWithMenu.class);

	/**
	 * Create a dialog with a {@link HelpViewer}, a {@link TableView} and possibly a
	 * {@link RowCreatorViewer} if {@code addSelector} is set to true. It also
	 * allows adding and removing rows from the table
	 * 
	 * @param parent      the shell parent
	 * @param title       the title of the pop up (used only if {@code createPopUp}
	 *                    is true)
	 * @param message     the help message
	 * @param editable    if the table can be edited or not
	 * @param addSelector if the {@link RowCreatorViewer} should be added or not
	 */
	public TableDialogWithMenu(Shell parent, String title, boolean createPopUp, boolean addSaveBtn) {
		super(parent, title, createPopUp, addSaveBtn);
	}

	@Override
	public Menu createMenu() {

		Menu menu = new Menu(getDialog());
		
		String lineSeparator = System.getProperty("line.separator");

		if (DebugConfig.debug) {
			MenuItem button = new MenuItem(menu, SWT.PUSH);
			button.setText("[DEBUG] Print row");
			button.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent arg0) {

					TableRow row = getSelection();

					if (row == null) {
						LOGGER.info("Upon selecting widget 'Print row', row is empty.");
						return;
					}					
					
					LOGGER.info("Row: " + lineSeparator, row);
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
				}
			});

			MenuItem button5 = new MenuItem(menu, SWT.PUSH);
			button5.setText("[DEBUG] Print row solved");
			button5.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent arg0) {

					TableRow row = getSelection();

					if (row == null) {
						LOGGER.info("Upon selecting widget 'Print row solved', row is empty.");
						return;
					}	

					row.updateFormulas();

					LOGGER.info("Row: " + lineSeparator, row);
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
				}
			});

			MenuItem button6 = new MenuItem(menu, SWT.PUSH);
			button6.setText("[DEBUG] Print row schema");
			button6.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent arg0) {

					TableRow row = getSelection();

					if (row == null) {
						LOGGER.info("Upon selecting widget 'Print row schema', row is empty.");
						return;
					}

					LOGGER.info("Row schema: " + lineSeparator, row.getSchema());
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
				}
			});

			MenuItem button2 = new MenuItem(menu, SWT.PUSH);
			button2.setText("[DEBUG] Check mandatory fields");
			button2.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent arg0) {

					TableRow row = getSelection();

					if (row == null) {
						LOGGER.info("Upon selecting widget 'Check mandatory fields', row is empty.");
						return;
					}

					LOGGER.info("Row properties that are mandatory filled: " + lineSeparator, row.areMandatoryFilled());
	

					for (TableColumn col : getSchema()) {
						LOGGER.info(col.getId() + "; mandatory= " + col.isMandatory(row) + " with formula "
								+ col.getMandatoryFormula());
					}
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
				}
			});

			MenuItem button3 = new MenuItem(menu, SWT.PUSH);
			button3.setText("[DEBUG] Check editability");
			button3.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent arg0) {

					TableRow row = getSelection();

					if (row == null) {
						LOGGER.info("Upon selecting widget 'Check editability', row is empty.");
						return;
					}

					for (TableColumn col : getSchema()) {
						LOGGER.info(col.getId() + "; editable=" + col.isEditable(row) + " with formula "
								+ col.getEditableFormula());
					}
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
				}
			});

			MenuItem button4 = new MenuItem(menu, SWT.PUSH);
			button4.setText("[DEBUG] Check visibility");
			button4.addSelectionListener(new SelectionListener() {

				@Override
				public void widgetSelected(SelectionEvent arg0) {

					TableRow row = getSelection();

					if (row == null) {
						LOGGER.info("Upon selecting widget 'Check visibility', row is empty.");
						return;
					}

					for (TableColumn col : getSchema()) {
						LOGGER.info(col.getId() + "; visible=" + col.isVisible(row) + " with formula "
								+ col.getVisibleFormula());
					}
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent arg0) {
				}
			});
		}

		return menu;
	}

	/**
	 * Add the remove menu item to the menu
	 * 
	 * @param menu
	 * @return
	 */
	public MenuItem addRemoveMenuItem(Menu menu) {

		MenuItem remove = new MenuItem(menu, SWT.PUSH);
		remove.setText(TSEMessages.get("delete.records"));
		remove.setEnabled(false);

		addTableSelectionListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent arg0) {
				remove.setEnabled(!isTableEmpty() && isEditable());
			}
		});

		remove.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent arg0) {

				int val = Warnings.warnUser(getDialog(), TSEMessages.get("warning.title"),
						TSEMessages.get("delete.confirm"), SWT.ICON_WARNING | SWT.YES | SWT.NO);

				if (val == SWT.YES) {
					getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
					removeSelectedRow();
					getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_ARROW));
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});
        LOGGER.debug("Adding Remove menu item to menu.");
		return remove;
	}

	/**
	 * Add the clone menu item to the menu
	 * 
	 * @author shahaal
	 * @param menu
	 * @return
	 */
	public MenuItem addCloneMenuItem(Menu menu) {

		MenuItem clone = new MenuItem(menu, SWT.PUSH);
		clone.setText(TSEMessages.get("clone.records"));
		clone.setEnabled(false);

		addTableSelectionListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent arg0) {
				clone.setEnabled(!isTableEmpty() && isEditable());
			}
		});

		clone.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent arg0) {

				getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
				cloneSelectedRow();
				getDialog().setCursor(getDialog().getDisplay().getSystemCursor(SWT.CURSOR_ARROW));

			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
			}
		});
		LOGGER.debug("Adding Clone menu item to menu.");
		return clone;
	}
}
