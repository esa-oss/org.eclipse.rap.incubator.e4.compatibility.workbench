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
 *******************************************************************************/
package org.eclipse.ui.internal.dialogs;

import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.internal.IWorkbenchHelpContextIds;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.activities.ws.WorkbenchTriggerPoints;
import org.eclipse.ui.wizards.IWizardCategory;
import org.eclipse.ui.wizards.IWizardDescriptor;

/**
 *	New wizard selection tab that allows the user to either select a
 *	registered 'New' wizard to be launched, or to select a solution or
 *	projects to be retrieved from an available server.  This page
 *	contains two visual tabs that allow the user to perform these tasks.
 *
 *  Temporarily has two inner pages.  The new format page is used if the system
 *  is currently aware of activity categories.
 */
class NewWizardSelectionPage extends WorkbenchWizardSelectionPage {
    private IWizardCategory wizardCategories;

    // widgets
    private NewWizardNewPage newResourcePage;

    private IWizardDescriptor [] primaryWizards;

	private boolean projectsOnly;

	private boolean canFinishEarly = false, hasPages = true;

    /**
     * Create an instance of this class.
     *
     * @param workbench the workbench
     * @param selection the current selection
     * @param root the wizard root element
     * @param primary the primary wizard elements
     * @param projectsOnly if only projects should be shown
     */
    public NewWizardSelectionPage(IWorkbench workbench,
			IStructuredSelection selection, IWizardCategory root,
			IWizardDescriptor[] primary, boolean projectsOnly) {
        super("newWizardSelectionPage", workbench, selection, null, WorkbenchTriggerPoints.NEW_WIZARDS);//$NON-NLS-1$

        setTitle(WorkbenchMessages.get().NewWizardSelectionPage_description);
        wizardCategories = root;
        primaryWizards = primary;
        this.projectsOnly = projectsOnly;
	}

    /**
     * Makes the next page visible.
     */
    public void advanceToNextPageOrFinish() {
    		if (canFlipToNextPage()) {
				getContainer().showPage(getNextPage());
			} else if (canFinishEarly()) {
    			if (getWizard().performFinish()) {
					((WizardDialog)getContainer()).close();
				}
    		}
    }

    @Override
	public void createControl(Composite parent) {
        IDialogSettings settings = getDialogSettings();
        newResourcePage = new NewWizardNewPage(this, wizardCategories,
				primaryWizards, projectsOnly);
        newResourcePage.setDialogSettings(settings);

        Control control = newResourcePage.createControl(parent);
        getWorkbench().getHelpSystem().setHelp(control,
				IWorkbenchHelpContextIds.NEW_WIZARD_SELECTION_WIZARD_PAGE);
        setControl(control);
    }

    /**
     * Since Finish was pressed, write widget values to the dialog store so that they
     *will persist into the next invocation of this wizard page
     */
    protected void saveWidgetValues() {
        newResourcePage.saveWidgetValues();
    }

    @Override
	public boolean canFlipToNextPage() {
        // if the current page advertises that it does have pages then ask it via the super call
    		if (hasPages) {
				return super.canFlipToNextPage();
			}
    		return false;
    }

    /**
     * Sets whether the selected wizard advertises that it does provide pages.
     *
     * @param newValue whether the selected wizard has pages
     * @since 3.1
     */
	public void setHasPages(boolean newValue) {
		hasPages = newValue;
	}

	/**
	 * Sets whether the selected wizard advertises that it can finish early.
	 *
	 * @param newValue whether the selected wizard can finish early
	 * @since 3.1
	 */
	public void setCanFinishEarly(boolean newValue) {
		canFinishEarly = newValue;
	}

	/**
	 * Answers whether the currently selected page, if any, advertises that it may finish early.
	 *
	 * @return whether the page can finish early
	 * @since 3.1
	 */
	public boolean canFinishEarly() {
		return canFinishEarly;
	}
}
