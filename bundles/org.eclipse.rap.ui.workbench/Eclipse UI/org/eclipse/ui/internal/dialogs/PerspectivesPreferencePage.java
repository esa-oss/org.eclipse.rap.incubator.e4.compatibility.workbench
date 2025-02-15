/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Semion Chichelnitsky (semion@il.ibm.com) - bug 278064
 *     Denis Zygann <d.zygann@web.de> - Bug 330453
 *     Lars Vogel <Lars.Vogel@vogella.com> - Bug 472654
 *******************************************************************************/

package org.eclipse.ui.internal.dialogs;

import com.ibm.icu.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.advanced.MPerspective;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.internal.IWorkbenchHelpContextIds;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.registry.PerspectiveDescriptor;
import org.eclipse.ui.internal.registry.PerspectiveRegistry;
import org.eclipse.ui.internal.util.Descriptors;
import org.eclipse.ui.internal.util.PrefUtil;
import org.eclipse.ui.internal.util.Util;

/**
 * The Workbench / Perspectives preference page.
 */
public class PerspectivesPreferencePage extends PreferencePage implements
		IWorkbenchPreferencePage {
	private IWorkbench workbench;

	private PerspectiveRegistry perspectiveRegistry;

	// List of perspectives to populate preference page
	private ArrayList<IPerspectiveDescriptor> perspectives;

	private String defaultPerspectiveId;

	private ArrayList<IPerspectiveDescriptor> perspToDelete = new ArrayList<>();

	private ArrayList<IPerspectiveDescriptor> perspToRevert = new ArrayList<>();

	private Table perspectivesTable;

	private Button revertButton;

	private Button deleteButton;

	private Button setDefaultButton;

	// widgets for open perspective mode;
	private Button openSameWindowButton;

	private Button openNewWindowButton;

	private int openPerspMode;

	// labels
	private final String OPM_TITLE = WorkbenchMessages.get().OpenPerspectiveMode_optionsTitle;

	private final String OPM_SAME_WINDOW = WorkbenchMessages.get().OpenPerspectiveMode_sameWindow;

	private final String OPM_NEW_WINDOW = WorkbenchMessages.get().OpenPerspectiveMode_newWindow;

	/**
	 * <code>Comparator</code> to compare two perspective descriptors
	 */
	private Comparator<IPerspectiveDescriptor> comparator = new Comparator<IPerspectiveDescriptor>() {
        private Collator collator = Collator.getInstance();

		@Override
		public int compare(IPerspectiveDescriptor ob1, IPerspectiveDescriptor ob2) {
			IPerspectiveDescriptor d1 = ob1;
			IPerspectiveDescriptor d2 = ob2;
            return collator.compare(d1.getLabel(), d2.getLabel());
        }
    };

	/**
	 * Creates the page's UI content.
	 */
	@Override
	protected Control createContents(Composite parent) {
		// @issue if the product subclasses this page, then it should provide
		// the help content
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent,
				IWorkbenchHelpContextIds.PERSPECTIVES_PREFERENCE_PAGE);

		Composite composite = createComposite(parent);

		createOpenPerspButtonGroup(composite);
		createCustomizePerspective(composite);

		return composite;
	}

	/**
	 * Creates the composite which will contain all the preference controls for
	 * this page.
	 *
	 * @param parent
	 *            the parent composite
	 * @return the composite for this page
	 */
	protected Composite createComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridData data = new GridData(GridData.FILL_BOTH);
		composite.setLayoutData(data);
		composite.setFont(parent.getFont());
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.verticalSpacing = 10;
		composite.setLayout(layout);
		return composite;
	}

	/**
	 * Create a composite that contains buttons for selecting the open
	 * perspective mode.
	 *
	 * @param composite
	 *            the parent composite
	 */
	protected void createOpenPerspButtonGroup(Composite composite) {

		Font font = composite.getFont();

		Group buttonComposite = new Group(composite, SWT.LEFT);
		buttonComposite.setText(OPM_TITLE);
		buttonComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		buttonComposite.setFont(composite.getFont());
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		buttonComposite.setLayout(layout);

		openSameWindowButton = new Button(buttonComposite, SWT.RADIO);
		openSameWindowButton.setText(OPM_SAME_WINDOW);
		openSameWindowButton
				.setSelection(IPreferenceConstants.OPM_ACTIVE_PAGE == openPerspMode);
		openSameWindowButton.setFont(font);
		openSameWindowButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
			public void widgetSelected(SelectionEvent e)
            {
                openPerspMode = IPreferenceConstants.OPM_ACTIVE_PAGE;
            }
        });
		openNewWindowButton = new Button(buttonComposite, SWT.RADIO);
		openNewWindowButton.setText(OPM_NEW_WINDOW);
		openNewWindowButton
				.setSelection(IPreferenceConstants.OPM_NEW_WINDOW == openPerspMode);
		openNewWindowButton.setFont(font);
		openNewWindowButton.addSelectionListener(new SelectionAdapter()
        {
            @Override
			public void widgetSelected(SelectionEvent e)
            {
                openPerspMode = IPreferenceConstants.OPM_NEW_WINDOW;
            }
        });
	}

	/**
	 * Create a table of 3 buttons to enable the user to manage customized
	 * perspectives.
	 *
	 * @param parent
	 *            the parent for the button parent
	 * @return Composite that the buttons are created in.
	 */
	protected Composite createCustomizePerspective(Composite parent) {

		Font font = parent.getFont();

		// define container & its gridding
		Composite perspectivesComponent = new Composite(parent, SWT.NONE);
		perspectivesComponent.setLayoutData(new GridData(GridData.FILL_BOTH));
		perspectivesComponent.setFont(parent.getFont());

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		perspectivesComponent.setLayout(layout);

		// Add the label
		Label label = new Label(perspectivesComponent, SWT.LEFT);
		label.setText(WorkbenchMessages.get().PerspectivesPreference_available);
		GridData data = new GridData();
		data.horizontalSpan = 2;
		label.setLayoutData(data);
		label.setFont(font);

		// Add perspectivesTable.
		perspectivesTable = new Table(perspectivesComponent, SWT.H_SCROLL | SWT.V_SCROLL
				| SWT.BORDER);
	    perspectivesTable.addSelectionListener(new SelectionAdapter()
        {
            @Override
			public void widgetSelected(SelectionEvent e)
            {
                updateButtons();
            }
        });
        perspectivesTable.setFont(font);

		data = new GridData(GridData.FILL_BOTH);
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		perspectivesTable.setLayoutData(data);

		// Populate the perspectivesTable
		IPerspectiveDescriptor[] persps = perspectiveRegistry.getPerspectives();
		perspectives = new ArrayList<>(persps.length);
		for (int i = 0; i < persps.length; i++) {
			perspectives.add(i, persps[i]);
		}
		Collections.sort(perspectives, comparator);
		defaultPerspectiveId = perspectiveRegistry.getDefaultPerspective();
		updatePerspectivesTable();

		// Create vertical button bar.
		Composite buttonBar = (Composite) createVerticalButtonBar(perspectivesComponent);
		data = new GridData(GridData.FILL_VERTICAL);
		buttonBar.setLayoutData(data);

		//Add note label
		String NOTE_LABEL = WorkbenchMessages.get().Preference_note;
		String REVERT_NOTE = WorkbenchMessages.get().RevertPerspective_note;
		Composite noteComposite = createNoteComposite(font, parent,
                NOTE_LABEL, REVERT_NOTE);
        GridData noteData = new GridData();
        noteData.horizontalSpan = 2;
        noteComposite.setLayoutData(noteData);
		return perspectivesComponent;
	}

	/**
	 * Creates a new vertical button with the given id.
	 * <p>
	 * The default implementation of this framework method creates a standard
	 * push button, registers for selection events including button presses and
	 * help requests, and registers default buttons with its shell. The button
	 * id is stored as the buttons client data.
	 * </p>
	 *
	 * @param parent
	 *            the parent composite
	 * @param label
	 *            the label from the button
	 * @param defaultButton
	 *            <code>true</code> if the button is to be the default button,
	 *            and <code>false</code> otherwise
	 * @return Button The created button.
	 */
	protected Button createVerticalButton(Composite parent, String label,
			boolean defaultButton) {
		Button button = new Button(parent, SWT.PUSH);

		button.setText(label);

		GridData data = setButtonLayoutData(button);
		data.horizontalAlignment = GridData.FILL;

		button.addSelectionListener(new SelectionAdapter()
        {
            @Override
			public void widgetSelected(SelectionEvent event)
            {
                verticalButtonPressed(event.widget);
            }
        });
		button.setToolTipText(label);
		if (defaultButton) {
			Shell shell = parent.getShell();
			if (shell != null) {
				shell.setDefaultButton(button);
			}
		}
		button.setFont(parent.getFont());
		return button;
	}

	/**
	 * Creates and returns the vertical button bar.
	 *
	 * @param parent
	 *            the parent composite to contain the button bar
	 * @return the button bar control
	 */
	protected Control createVerticalButtonBar(Composite parent) {
		// Create composite.
		Composite composite = new Composite(parent, SWT.NULL);

		// create a layout with spacing and margins appropriate for the font
		// size.
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 5;
		layout.marginHeight = 0;
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		composite.setLayout(layout);
		composite.setFont(parent.getFont());

		// Add the buttons to the button bar.
		setDefaultButton = createVerticalButton(composite, WorkbenchMessages.get().PerspectivesPreference_MakeDefault, false);
		setDefaultButton.setToolTipText(WorkbenchMessages.get().PerspectivesPreference_MakeDefaultTip);

		revertButton = createVerticalButton(composite, WorkbenchMessages.get().PerspectivesPreference_Reset, false);
		revertButton.setToolTipText(WorkbenchMessages.get().PerspectivesPreference_ResetTip);

		deleteButton = createVerticalButton(composite, WorkbenchMessages.get().PerspectivesPreference_Delete, false);
		deleteButton.setToolTipText(WorkbenchMessages.get().PerspectivesPreference_DeleteTip);
		updateButtons();

		return composite;
	}

	/**
	 * @see IWorkbenchPreferencePage
	 */
	@Override
	public void init(IWorkbench aWorkbench) {
		this.workbench = aWorkbench;
		this.perspectiveRegistry = (PerspectiveRegistry) workbench
				.getPerspectiveRegistry();
		IPreferenceStore store = WorkbenchPlugin.getDefault()
				.getPreferenceStore();
		setPreferenceStore(store);

		openPerspMode = store.getInt(IPreferenceConstants.OPEN_PERSP_MODE);
	}

	/**
	 * The default button has been pressed.
	 */
	@Override
	protected void performDefaults() {
		//Project perspective preferences
		IPreferenceStore store = WorkbenchPlugin.getDefault()
				.getPreferenceStore();

		openPerspMode = store
				.getDefaultInt(IPreferenceConstants.OPEN_PERSP_MODE);
		openSameWindowButton
				.setSelection(IPreferenceConstants.OPM_ACTIVE_PAGE == openPerspMode);
		openNewWindowButton
				.setSelection(IPreferenceConstants.OPM_NEW_WINDOW == openPerspMode);

		String currentDefault = perspectiveRegistry.getDefaultPerspective();

		int index = indexOf(currentDefault);
		if (index >= 0){
			defaultPerspectiveId = currentDefault;
			updatePerspectivesTable();
			perspectivesTable.setSelection(index);
		}

		String newDefault = PrefUtil.getAPIPreferenceStore().getDefaultString(
                IWorkbenchPreferenceConstants.DEFAULT_PERSPECTIVE_ID);

		IPerspectiveDescriptor desc = null;
        if (newDefault != null) {
			desc = workbench.getPerspectiveRegistry().findPerspectiveWithId(newDefault);
		}
        if (desc == null) {
        	newDefault = workbench.getPerspectiveRegistry().getDefaultPerspective();
        }

        defaultPerspectiveId = newDefault;
        updatePerspectivesTable();

	}

	/**
	 * Look up the index of the perpective with the given if.
	 * @param perspectiveId or <code>null</code>
	 * @return int -1 if it cannot be found
	 */
	private int indexOf(String perspectiveId) {
		if (perspectiveId == null) {
			return -1;
		}
		PerspectiveDescriptor[] descriptors =
			new PerspectiveDescriptor[perspectives.size()];
		perspectives.toArray(descriptors);
		for (int i = 0; i < descriptors.length; i++) {
			PerspectiveDescriptor descriptor = descriptors[i];
			if(descriptor.getId().equals(perspectiveId)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Return true if there are no open instances of the perspective.  If there are open
	 * instances of the perspective prompt the user and return true if the user answers "yes"
	 * to the delete prompt.
	 *
	 * @return boolean <code>true</code> if it is OK to delete the perspective
	 *         either because there are no open instances or the user has
	 *         confirmed the deletion.
	 */
	private boolean canDeletePerspective(IPerspectiveDescriptor desc) {

		MApplication application = ((Workbench) workbench).getApplication();
		EModelService modelService = application.getContext().get(EModelService.class);

		if (modelService.findElements(application, desc.getId(), MPerspective.class, null)
				.isEmpty())
			return true;

		return MessageDialog.openQuestion(
				getShell(),
				WorkbenchMessages.get().PerspectivesPreference_perspectiveopen_title,
				NLS.bind(WorkbenchMessages.get().PerspectivesPreference_perspectiveopen_message,
						desc.getLabel()));
	}

	/**
	 * Apply the user's changes if any
	 */
	@Override
	public boolean performOk() {
		// Set the default perspective
		if (!Util.equals(defaultPerspectiveId, perspectiveRegistry.getDefaultPerspective())) {
			perspectiveRegistry.setDefaultPerspective(defaultPerspectiveId);
		}

		// Don't bother figuring out which window a perspective may be open in,
		// the number of windows will be small.

		for (IPerspectiveDescriptor perspective : perspToDelete) {
			IWorkbenchWindow[] windows = workbench.getWorkbenchWindows();
			for (IWorkbenchWindow window : windows) {
				IWorkbenchPage[] pages = window.getPages();
				for (IWorkbenchPage page : pages) {
					page.closePerspective(perspective, true, false);
				}
			}
			perspectiveRegistry.deletePerspectives(perspToDelete);
		}

        // Revert perspectives
		for (IPerspectiveDescriptor perspective : perspToRevert) {
			perspectiveRegistry.revertPerspective(perspective);
		}

		IPreferenceStore store = getPreferenceStore();

		// store the open perspective mode setting
		store.setValue(IPreferenceConstants.OPEN_PERSP_MODE, openPerspMode);

		// save both the API prefs and the internal prefs
		// the API prefs are modified by
		// PerspectiveRegistry.setDefaultPerspective
		PrefUtil.savePrefs();

		return true;
	}

	/**
	 * Update the button enablement state.
	 */
	protected void updateButtons() {
		// Get selection.
		int index = perspectivesTable.getSelectionIndex();

		// Map it to the perspective descriptor
		PerspectiveDescriptor desc = null;
		if (index > -1) {
			desc = (PerspectiveDescriptor) perspectives.get(index);
		}

		// Do enable.
		if (desc != null) {
			revertButton.setEnabled(desc.isPredefined() && desc.hasCustomDefinition()
					&& !perspToRevert.contains(desc));
			deleteButton.setEnabled(!desc.isPredefined());
			setDefaultButton.setEnabled(true);
		} else {
			revertButton.setEnabled(false);
			deleteButton.setEnabled(false);
			setDefaultButton.setEnabled(false);
		}
	}

	/**
	 * Update the perspectivesTable.
	 */
	protected void updatePerspectivesTable() {
        // Populate the table with the items
		perspectivesTable.removeAll();
		for (int i = 0; i < perspectives.size(); i++) {
        	PerspectiveDescriptor persp = (PerspectiveDescriptor) perspectives.get(i);
        	newPerspectivesTableItem(persp, i, false);
        }
    }

	/**
	 * Create a new tableItem using given perspective, and set image for the new item.
	 */
	protected TableItem newPerspectivesTableItem(IPerspectiveDescriptor persp,
            int index, boolean selected) {

        ImageDescriptor image = persp.getImageDescriptor();

        TableItem item = new TableItem(perspectivesTable, SWT.NULL, index);
        if (image != null) {
            Descriptors.setImage(item, image);
        }
        String label=persp.getLabel();
        if (persp.getId().equals(defaultPerspectiveId)){
			label = NLS.bind(WorkbenchMessages.get().PerspectivesPreference_defaultLabel, label );

		}
        item.setText(label);
        item.setData(persp);
        if (selected) {
        	perspectivesTable.setSelection(index);
        }

        return item;
    }

    /**
	 * Notifies that this page's button with the given id has been pressed.
	 *
	 * @param button
	 *            the button that was pressed
	 */
	protected void verticalButtonPressed(Widget button) {
		// Get selection.
		int index = perspectivesTable.getSelectionIndex();

		// Map it to the perspective descriptor
		PerspectiveDescriptor desc = null;
		if (index > -1) {
			desc = (PerspectiveDescriptor) perspectives.get(index);
		} else {
			return;
		}

		// Take action.
		if (button == revertButton) {
			if (!perspToRevert.contains(desc)) {
				perspToRevert.add(desc);
			}
		} else if (button == deleteButton) {
			if (!perspToDelete.contains(desc)) {
				if (canDeletePerspective(desc)) {
					perspToDelete.add(desc);
					perspToRevert.remove(desc);
					perspectives.remove(desc);
					updatePerspectivesTable();
				}

			}
		} else if (button == setDefaultButton) {
			defaultPerspectiveId = desc.getId();
			updatePerspectivesTable();
			perspectivesTable.setSelection(index);
		}

		updateButtons();
	}
}
