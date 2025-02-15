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
package org.eclipse.ui.actions;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.jface.action.Action;

import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.internal.IWorkbenchHelpContextIds;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * Opens a new window. The initial perspective
 * for the new window will be the same type as
 * the active perspective in the window which this
 * action is running in. The default input for the
 * new window's page is application-specific.
 */
public class OpenInNewWindowAction extends Action implements
        ActionFactory.IWorkbenchAction {

    /**
     * The workbench window; or <code>null</code> if this
     * action has been <code>dispose</code>d.
     */
    private IWorkbenchWindow workbenchWindow;

    private IAdaptable pageInput;

    /**
     * Creates a new <code>OpenInNewWindowAction</code>. Sets
     * the new window page's input to be an application-specific
     * default.
     *
     * @param window the workbench window containing this action
     */
    public OpenInNewWindowAction(IWorkbenchWindow window) {
        this(window, ((Workbench) window.getWorkbench()).getDefaultPageInput());
		setActionDefinitionId(IWorkbenchCommandConstants.WINDOW_NEW_WINDOW);
    }

    /**
     * Creates a new <code>OpenInNewWindowAction</code>.
     *
     * @param window the workbench window containing this action
     * @param input the input for the new window's page
     */
    public OpenInNewWindowAction(IWorkbenchWindow window, IAdaptable input) {
        super(WorkbenchMessages.get().OpenInNewWindowAction_text);
        if (window == null) {
            throw new IllegalArgumentException();
        }
        this.workbenchWindow = window;
        // @issue missing action id
        setToolTipText(WorkbenchMessages.get().OpenInNewWindowAction_toolTip);
        pageInput = input;
        window.getWorkbench().getHelpSystem().setHelp(this,
				IWorkbenchHelpContextIds.OPEN_NEW_WINDOW_ACTION);
    }

    /**
     * Set the input to use for the new window's page.
     *
     * @param input the input
     */
    public void setPageInput(IAdaptable input) {
        pageInput = input;
    }

    /**
     * The implementation of this <code>IAction</code> method
     * opens a new window. The initial perspective
     * for the new window will be the same type as
     * the active perspective in the window which this
     * action is running in.
     */
    @Override
	public void run() {
        if (workbenchWindow == null) {
            // action has been disposed
            return;
        }
        try {
            String perspId;

            IWorkbenchPage page = workbenchWindow.getActivePage();
            if (page != null && page.getPerspective() != null) {
				perspId = page.getPerspective().getId();
			} else {
				perspId = workbenchWindow.getWorkbench()
                        .getPerspectiveRegistry().getDefaultPerspective();
			}

            workbenchWindow.getWorkbench().openWorkbenchWindow(perspId,
                    pageInput);
        } catch (WorkbenchException e) {
			StatusUtil.handleStatus(e.getStatus(),
					WorkbenchMessages.get().OpenInNewWindowAction_errorTitle
							+ ": " + e.getMessage(), //$NON-NLS-1$
					StatusManager.SHOW);
        }
    }

    @Override
	public void dispose() {
        workbenchWindow = null;
    }
}
