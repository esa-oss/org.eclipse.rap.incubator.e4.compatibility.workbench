/*******************************************************************************
 * Copyright (c) 2005, 2017 IBM Corporation and others.
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
package org.eclipse.ui.operations;

import java.lang.reflect.InvocationTargetException;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.operations.IAdvancedUndoableOperation2;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.LegacyActionTools;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.internal.operations.TimeTriggeredProgressMonitorDialog;
import org.eclipse.ui.part.MultiPageEditorSite;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * <p>
 * OperationHistoryActionHandler implements common behavior for the undo and
 * redo actions. It supports filtering of undo or redo on a particular undo
 * context. If an undo context is not specified, or there has been no history
 * available for the specified undo context, then the workbench undo context
 * will be used.
 * </p>
 * <p>
 * OperationHistoryActionHandler provides an adapter in the info parameter of
 * the IOperationHistory undo and redo methods that is used to get UI info for
 * prompting the user during operations or operation approval. Adapters are
 * provided for org.eclipse.ui.IWorkbenchWindow, org.eclipse.swt.widgets.Shell,
 * org.eclipse.ui.IWorkbenchPart, org.eclipse.core.commands.IUndoContext, and
 * org.eclipse.runtime.IProgressMonitor.
 * </p>
 * <p>
 * OperationHistoryActionHandler assumes a linear undo/redo model. When the
 * handler is run, the operation history is asked to perform the most recent
 * undo/redo for the handler's undo context. The handler can be configured
 * (using #setPruneHistory(true)) to flush the operation undo or redo history
 * for the handler's undo context when there is no valid operation on top of the
 * history. This avoids keeping a stale history of invalid operations. By
 * default, pruning does not occur and it is assumed that clients of the
 * particular undo context are pruning the history when necessary.
 * </p>
 *
 * @since 3.1
 */
public abstract class OperationHistoryActionHandler extends Action implements
		ActionFactory.IWorkbenchAction, IAdaptable {

	private static final int MAX_LABEL_LENGTH = 33;

	private class PartListener implements IPartListener {
		/**
		 * @see IPartListener#partActivated(IWorkbenchPart)
		 */
		@Override
		public void partActivated(IWorkbenchPart part) {
		}

		/**
		 * @see IPartListener#partBroughtToTop(IWorkbenchPart)
		 */
		@Override
		public void partBroughtToTop(IWorkbenchPart part) {
		}

		/**
		 * @see IPartListener#partClosed(IWorkbenchPart)
		 */
		@Override
		public void partClosed(IWorkbenchPart part) {
			if (site != null && part.equals(site.getPart())) {
				dispose();
				// Special case for MultiPageEditorSite
				// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=103379
			} else if ((site instanceof MultiPageEditorSite)
					&& (part.equals(((MultiPageEditorSite) site)
							.getMultiPageEditor()))) {
				dispose();
			}
		}

		/**
		 * @see IPartListener#partDeactivated(IWorkbenchPart)
		 */
		@Override
		public void partDeactivated(IWorkbenchPart part) {
		}

		/**
		 * @see IPartListener#partOpened(IWorkbenchPart)
		 */
		@Override
		public void partOpened(IWorkbenchPart part) {
		}

	}

	private class HistoryListener implements IOperationHistoryListener {
		@Override
		public void historyNotification(final OperationHistoryEvent event) {
			IWorkbenchWindow workbenchWindow = getWorkbenchWindow();
			if (workbenchWindow == null)
				return;

			Display display = workbenchWindow.getWorkbench().getDisplay();
			if (display == null)
				return;

			switch (event.getEventType()) {
			case OperationHistoryEvent.OPERATION_ADDED:
			case OperationHistoryEvent.OPERATION_REMOVED:
			case OperationHistoryEvent.UNDONE:
			case OperationHistoryEvent.REDONE:
				if (event.getOperation().hasContext(undoContext)) {
					display.asyncExec(() -> update());
				}
				break;
			case OperationHistoryEvent.OPERATION_NOT_OK:
				if (event.getOperation().hasContext(undoContext)) {
					display.asyncExec(() -> {
						if (pruning) {
							IStatus status = event.getStatus();
							/*
							 * Prune the history unless we can determine
							 * that this was a cancelled attempt. See
							 * https://bugs.eclipse.org/bugs/show_bug.cgi?id=101215
							 */
							if (status == null
									|| status.getSeverity() != IStatus.CANCEL) {
								flush();
							}
							// not all flushes will trigger an update so
							// force it here
							update();
						} else {
							update();
						}
					});
				}
				break;
			case OperationHistoryEvent.OPERATION_CHANGED:
				if (event.getOperation() == getOperation()) {
					display.asyncExec(() -> update());
				}
				break;
			}
		}
	}

	private boolean pruning = false;

	private IPartListener partListener = new PartListener();

	private IOperationHistoryListener historyListener = new HistoryListener();

	private TimeTriggeredProgressMonitorDialog progressDialog;

	private IUndoContext undoContext = null;

	IWorkbenchPartSite site;

	/**
	 * Construct an operation history action for the specified workbench window
	 * with the specified undo context.
	 *
	 * @param site -
	 *            the workbench part site for the action.
	 * @param context -
	 *            the undo context to be used
	 */
	OperationHistoryActionHandler(IWorkbenchPartSite site, IUndoContext context) {
		// string will be reset inside action
		super(""); //$NON-NLS-1$
		this.site = site;
		undoContext = context;
		site.getPage().addPartListener(partListener);
		getHistory().addOperationHistoryListener(historyListener);
		// An update must be forced in case the undo limit is 0.
		// see bug #89707
		update();
	}

	@Override
	public void dispose() {

		IOperationHistory history = getHistory();
		if (history != null) {
			history.removeOperationHistoryListener(historyListener);
		}

		if (isInvalid()) {
			return;
		}

		site.getPage().removePartListener(partListener);
		site = null;
		progressDialog = null;
		// We do not flush the history for our undo context because it may be
		// used elsewhere. It is up to clients to clean up the history
		// appropriately.
		// We do null out the context to signify that this handler is no longer
		// accessing the history.
		undoContext = null;
	}

	/*
	 * Flush the history associated with this action.
	 */
	abstract void flush();

	/*
	 * Return the string describing the command, including the binding for the
	 * operation label.
	 */
	abstract String getCommandString();

	/*
	 * Return the string describing the command for a tooltip, including the
	 * binding for the operation label.
	 */
	abstract String getTooltipString();

	/*
	 * Return the simple string describing the command, with no binding to any
	 * operation.
	 */
	abstract String getSimpleCommandString();

	/*
	 * Return the simple string describing the tooltip, with no binding to any
	 * operation.
	 */
	abstract String getSimpleTooltipString();

	/*
	 * Return the operation history we are using.
	 */
	IOperationHistory getHistory() {
		if (PlatformUI.getWorkbench() == null) {
			return null;
		}

		return PlatformUI.getWorkbench().getOperationSupport()
				.getOperationHistory();
	}

	/*
	 * Return the current operation.
	 */
	abstract IUndoableOperation getOperation();

	@Override
	public final void run() {
		if (isInvalid()) {
			return;
		}

		Shell parent = getWorkbenchWindow().getShell();
		progressDialog = new TimeTriggeredProgressMonitorDialog(parent,
				getWorkbenchWindow().getWorkbench().getProgressService()
						.getLongOperationTime());
		IRunnableWithProgress runnable = pm -> {
try {
		runCommand(pm);
} catch (ExecutionException e) {
		if (pruning) {
			flush();
		}
		throw new InvocationTargetException(e);
}
};
		try {
			boolean runInBackground = false;
			if (getOperation() instanceof IAdvancedUndoableOperation2) {
				runInBackground = ((IAdvancedUndoableOperation2) getOperation())
						.runInBackground();
			}
			progressDialog.run(runInBackground, true, runnable);
		} catch (InvocationTargetException e) {
			Throwable t = e.getTargetException();
			if (t == null) {
				reportException(e);
			} else {
				reportException(t);
			}
		} catch (InterruptedException e) {
			// Operation was cancelled and acknowledged by runnable with this
			// exception.
			// Do nothing.
		} catch (OperationCanceledException e) {
			// the operation was cancelled. Do nothing.
		} finally {
			progressDialog = null;
		}
	}

	abstract IStatus runCommand(IProgressMonitor pm) throws ExecutionException;

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter.equals(IUndoContext.class)) {
			return adapter.cast(undoContext);
		}
		if (adapter.equals(IProgressMonitor.class)) {
			if (progressDialog != null) {
				return adapter.cast(progressDialog.getProgressMonitor());
			}
		}
		if (site != null) {
			if (adapter.equals(Shell.class)) {
				return adapter.cast(getWorkbenchWindow().getShell());
			}
			if (adapter.equals(IWorkbenchWindow.class)) {
				return adapter.cast(getWorkbenchWindow());
			}
			if (adapter.equals(IWorkbenchPart.class)) {
				return adapter.cast(site.getPart());
			}
			// Refer all other requests to the part itself.
			// See https://bugs.eclipse.org/bugs/show_bug.cgi?id=108144
			IWorkbenchPart part = site.getPart();
			if (part != null) {
				return Adapters.adapt(part, adapter);
			}
		}
		return null;
	}

	/*
	 * Return the workbench window for this action handler
	 */
	private IWorkbenchWindow getWorkbenchWindow() {
		if (site != null) {
			return site.getWorkbenchWindow();
		}
		return null;
	}

	/**
	 * The undo and redo subclasses should implement this.
	 *
	 * @return - a boolean indicating enablement state
	 */
	abstract boolean shouldBeEnabled();

	/**
	 * Set the context shown by the handler. Normally the context is set up when
	 * the action handler is created, but the context can also be changed
	 * dynamically.
	 *
	 * @param context
	 *            the context to be used for the undo history
	 */
	public void setContext(IUndoContext context) {
		// optimization - do nothing if there was no real change
		if (context == undoContext) {
			return;
		}
		undoContext = context;
		update();
	}

	/**
	 * Specify whether the action handler should actively prune the operation
	 * history when invalid operations are encountered. The default value is
	 * <code>false</code>.
	 *
	 * @param prune
	 *            <code>true</code> if the history should be pruned by the
	 *            handler, and <code>false</code> if it should not.
	 *
	 */
	public void setPruneHistory(boolean prune) {
		pruning = prune;
	}

	/**
	 * Update enabling and labels according to the current status of the
	 * operation history.
	 */
	public void update() {
		if (isInvalid()) {
			return;
		}

		boolean enabled = shouldBeEnabled();
		String text, tooltipText;
		if (enabled) {
			tooltipText = NLS.bind(getTooltipString(), getOperation()
					.getLabel());
			text = NLS.bind(getCommandString(),
					LegacyActionTools.escapeMnemonics(shortenText(getOperation().getLabel()))) + '@';
		} else {
			tooltipText = NLS.bind(
					WorkbenchMessages.get().Operations_undoRedoCommandDisabled,
					getSimpleTooltipString());
			text = getSimpleCommandString();
			/*
			 * if there is nothing to do and we are pruning the history, flush
			 * the history of this context.
			 */
			if (undoContext != null && pruning) {
				flush();
			}
		}
		setText(text);
		setToolTipText(tooltipText);
		setEnabled(enabled);
	}

	/*
	 * Shorten the specified command label if it is too long
	 */
	private String shortenText(String message) {
		int length = message.length();
		if (length > MAX_LABEL_LENGTH) {
			StringBuilder result = new StringBuilder();
			int end = MAX_LABEL_LENGTH / 2 - 1;
			result.append(message.substring(0, end));
			result.append("..."); //$NON-NLS-1$
			result.append(message.substring(length - end));
			return result.toString();
		}
		return message;
	}

	/*
	 * Report the specified exception to the log and to the user.
	 */
	final void reportException(Throwable t) {
		// get any nested exceptions
		Throwable nestedException = StatusUtil.getCause(t);
		Throwable exception = (nestedException == null) ? t : nestedException;

		// Messages
		String exceptionMessage = exception.getMessage();
		if (exceptionMessage == null) {
			exceptionMessage = WorkbenchMessages.get().WorkbenchWindow_exceptionMessage;
		}
		IStatus status = StatusUtil.newStatus(WorkbenchPlugin.PI_WORKBENCH,
				exceptionMessage, exception);

		// Log and show the problem
		WorkbenchPlugin.log(exceptionMessage, status);
		StatusUtil.handleStatus(status, StatusManager.SHOW,
				getWorkbenchWindow().getShell());
	}

	/*
	 * Answer true if the receiver is not valid for running commands, accessing
	 * the history, etc.
	 */
	final boolean isInvalid() {
		return undoContext == null || site == null;
	}

	/*
	 * Get the undo context that should be used.
	 */
	final IUndoContext getUndoContext() {
		return undoContext;
	}
}
