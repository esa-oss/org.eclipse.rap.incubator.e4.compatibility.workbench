/*******************************************************************************
 * Copyright (c) 2005, 2015 IBM Corporation and others.
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
package org.eclipse.ui.internal.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.internal.IWorkbenchHelpContextIds;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.util.PrefUtil;

/**
 * Action to open the dynamic help.
 *
 * @since 3.1
 */
public class DynamicHelpAction extends Action implements IWorkbenchAction {
	/**
	 * The workbench window; or <code>null</code> if this action has been
	 * <code>dispose</code>d.
	 */
	private IWorkbenchWindow workbenchWindow;

	/**
	 * Zero-arg constructor to allow cheat sheets to reuse this action.
	 */
	public DynamicHelpAction() {
		this(PlatformUI.getWorkbench().getActiveWorkbenchWindow());
	}

	/**
	 * Constructor for use by ActionFactory.
	 *
	 * @param window
	 *            the window
	 */
	public DynamicHelpAction(IWorkbenchWindow window) {
		if (window == null) {
			throw new IllegalArgumentException();
		}
		this.workbenchWindow = window;
		setActionDefinitionId(IWorkbenchCommandConstants.HELP_DYNAMIC_HELP);

		// support for allowing a product to override the text for the action
		String overrideText = PrefUtil.getAPIPreferenceStore().getString(
				IWorkbenchPreferenceConstants.DYNAMIC_HELP_ACTION_TEXT);
		if ("".equals(overrideText)) { //$NON-NLS-1$
			setText(appendAccelerator(WorkbenchMessages.get().DynamicHelpAction_text));
			setToolTipText(WorkbenchMessages.get().DynamicHelpAction_toolTip);
		} else {
			setText(appendAccelerator(overrideText));
			setToolTipText(Action.removeMnemonics(overrideText));
		}
		window.getWorkbench().getHelpSystem().setHelp(this,
				IWorkbenchHelpContextIds.DYNAMIC_HELP_ACTION);
	}

	private String appendAccelerator(String text) {
		// We know that on Windows context help key is F1
		// and cannot be changed by the user.
		//
		// Commented out due to the problem described in
		// Bugzilla bug #95057

		//if (Platform.getWS().equals(Platform.WS_WIN32))
		//	return text + "\t" + KeyStroke.getInstance(SWT.F1).format(); //$NON-NLS-1$
		return text;
	}

	@Override
	public void run() {
		if (workbenchWindow == null) {
			// action has been disposed
			return;
		}
		// This may take a while, so use the busy indicator
		BusyIndicator.showWhile(null, () -> workbenchWindow.getWorkbench().getHelpSystem()
				.displayDynamicHelp());
	}

	@Override
	public void dispose() {
		workbenchWindow = null;
	}

}
