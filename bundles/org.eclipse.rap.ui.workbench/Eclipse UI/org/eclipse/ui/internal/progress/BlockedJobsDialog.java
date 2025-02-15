/*******************************************************************************
 * Copyright (c) 2004, 2018 IBM Corporation and others.
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
package org.eclipse.ui.internal.progress;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.IconAndMessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.rap.rwt.SingletonUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * The BlockedJobsDialog class displays a dialog that provides information on
 * the running jobs.
 */
public class BlockedJobsDialog extends IconAndMessageDialog {

    /**
     * The singleton dialog instance. A singleton avoids the possibility of
     * recursive dialogs being created. The singleton is created when a dialog
     * is requested, and cleared when the dialog is disposed.
     */
// RAP [fappel]: BlockedJobsDialog needs to be session aware
//  protected static BlockedJobsDialog singleton;
  public static class BlockedJobsDialogProvider {
    public BlockedJobsDialog blockedJobsDialog;

    protected static BlockedJobsDialogProvider getInstance() {
      return SingletonUtil.getSessionInstance( BlockedJobsDialogProvider.class );
    }
  }
  private static BlockedJobsDialog getSingleton() {
    return BlockedJobsDialogProvider.getInstance().blockedJobsDialog;
  }
  private static void setSingleton( final BlockedJobsDialog dialog ) {
    BlockedJobsDialogProvider.getInstance().blockedJobsDialog = dialog;
  }

	/**
	 * The singleton dialog instance. A singleton avoids the possibility of
	 * recursive dialogs being created. The singleton is created when a dialog
	 * is requested, and cleared when the dialog is disposed.
	 */
	protected static BlockedJobsDialog singleton;

	/**
	 * The running jobs progress viewer.
	 */
	private DetailedProgressViewer viewer;

	/**
	 * The name of the task that is being blocked.
	 */
	private String blockedTaskName = ProgressMessages.get().SubTaskInfo_UndefinedTaskName;

	/**
	 * The Cancel button control.
	 */
	private Button cancelSelected;

	private IProgressMonitor blockingMonitor;

	private JobTreeElement blockedElement = new BlockedUIElement();

	/**
	 * The BlockedUIElement is the JobTreeElement that represents the blocked
	 * job in the dialog.
	 */
	private class BlockedUIElement extends JobTreeElement {
		@Override
		Object[] getChildren() {
			return ProgressManagerUtil.EMPTY_OBJECT_ARRAY;
		}

		@Override
		String getDisplayString() {
			if (blockedTaskName == null || blockedTaskName.length() == 0) {
				return ProgressMessages.get().BlockedJobsDialog_UserInterfaceTreeElement;
			}
			return blockedTaskName;
		}

		@Override
		public Image getDisplayImage() {
			return JFaceResources.getImage(ProgressManager.WAITING_JOB_KEY);
		}

		@Override
		boolean hasChildren() {
			return false;
		}

		@Override
		boolean isActive() {
			return true;
		}

		@Override
		boolean isJobInfo() {
			return false;
		}

		@Override
		public void cancel() {
			blockingMonitor.setCanceled(true);
		}

		@Override
		public boolean isCancellable() {
			return true;
		}
	}

	/**
	 * Creates a progress monitor dialog under the given shell. It also sets the
	 * dialog's message. The dialog is opened automatically after a reasonable
	 * delay. When no longer needed, the dialog must be closed by calling
	 * <code>close(IProgressMonitor)</code>, where the supplied monitor is
	 * the same monitor passed to this factory method.
	 *
	 * @param parentShell
	 *            The parent shell, or <code>null</code> to create a top-level
	 *            shell. If the parentShell is not null we will open immediately
	 *            as parenting has been determined. If it is <code>null</code>
	 *            then the dialog will not open until there is no modal shell
	 *            blocking it.
	 * @param blockedMonitor
	 *            The monitor that is currently blocked
	 * @param reason
	 *            A status describing why the monitor is blocked
	 * @param taskName
	 *            A name to give the blocking task in the dialog
	 * @return BlockedJobsDialog
	 */
	public static BlockedJobsDialog createBlockedDialog(Shell parentShell,
			IProgressMonitor blockedMonitor, IStatus reason, String taskName) {
	 // RAP [fappel]: use session aware getSingleton() call in this method

        // use an existing dialog if available
        if (getSingleton() != null) {
            return getSingleton();
        }
        setSingleton( new BlockedJobsDialog(parentShell, blockedMonitor, reason) );

        if (taskName == null || taskName.length() == 0)
            getSingleton()
                    .setBlockedTaskName(ProgressMessages.get().BlockedJobsDialog_UserInterfaceTreeElement);
        else
            getSingleton().setBlockedTaskName(taskName);

		/**
		 * If there is no parent shell we have not been asked for a parent so we
		 * want to avoid blocking. If there is a parent then it is OK to open.
		 */
		if (parentShell == null) {
			// Create the job that will open the dialog after a delay.
			WorkbenchJob dialogJob = new WorkbenchJob(
					WorkbenchMessages.get().EventLoopProgressMonitor_OpenDialogJobName) {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					if (singleton == null) {
						return Status.CANCEL_STATUS;
					}
					if (ProgressManagerUtil.rescheduleIfModalShellOpen(this)) {
						return Status.CANCEL_STATUS;
					}
					singleton.open();
					return Status.OK_STATUS;
				}
			};
			// Wait for long operation time to prevent a proliferation of
			// dialogs.
			dialogJob.setSystem(true);
			dialogJob.schedule(PlatformUI.getWorkbench().getProgressService()
					.getLongOperationTime());
		} else {
			singleton.open();
		}

		return singleton;
	}

	/**
	 * The monitor is done. Clear the receiver.
	 *
	 * @param monitor
	 *            The monitor that is now cleared.
	 */
	public static void clear(IProgressMonitor monitor) {
// RAP [fappel]: use session aware getSingleton() call in this method
        if (getSingleton() == null) {
            return;
        }
        getSingleton().close(monitor);
	}

	/**
	 * Creates a progress monitor dialog under the given shell. It also sets the
	 * dialog's\ message. <code>open</code> is non-blocking.
	 *
	 * @param parentShell
	 *            The parent shell, or <code>null</code> to create a top-level
	 *            shell.
	 * @param blocking
	 *            The monitor that is blocking the job
	 * @param blockingStatus
	 *            A status describing why the monitor is blocked
	 */
	private BlockedJobsDialog(Shell parentShell, IProgressMonitor blocking,
			IStatus blockingStatus) {
		super(parentShell == null ? ProgressManagerUtil.getDefaultParent()
				: parentShell);
		blockingMonitor = blocking;
		setShellStyle(SWT.BORDER | SWT.TITLE | SWT.APPLICATION_MODAL
				| SWT.RESIZE | SWT.MAX | getDefaultOrientation());
		// no close button
		setBlockOnOpen(false);
		setMessage(blockingStatus.getMessage());
	}

	/**
	 * Creates the dialog area under the parent composite.
	 *
	 * @param parent
	 *            The parent Composite.
	 *
	 * @return parent The parent Composite.
	 */
	@Override
	protected Control createDialogArea(Composite parent) {
		setMessage(message);
		createMessageArea(parent);
		showJobDetails(parent);
		return parent;
	}

	/**
	 * Creates a dialog area in the parent composite and displays a progress
	 * tree viewer of the running jobs.
	 *
	 * @param parent
	 *            The parent Composite.
	 */
	void showJobDetails(Composite parent) {
		viewer = new DetailedProgressViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		viewer.setComparator(new ViewerComparator() {
			@Override
			@SuppressWarnings("unchecked")
			public int compare(Viewer testViewer, Object e1, Object e2) {
				return ((Comparable<Object>) e1).compareTo(e2);
			}
		});
		ProgressViewerContentProvider provider = getContentProvider();
		viewer.setContentProvider(provider);
		viewer.setInput(provider);
		viewer.setLabelProvider(new ProgressLabelProvider());
		GridData data = new GridData(GridData.GRAB_HORIZONTAL
				| GridData.GRAB_VERTICAL | GridData.FILL_BOTH);
		data.horizontalSpan = 2;
		int heightHint = convertHeightInCharsToPixels(10);
		data.heightHint = heightHint;
		viewer.getControl().setLayoutData(data);
	}

	/**
	 * Returns the content provider used for the receiver.
	 *
	 * @return ProgressTreeContentProvider
	 */
	private ProgressViewerContentProvider getContentProvider() {
		return new ProgressViewerContentProvider(viewer, true, false) {
			@Override
			public Object[] getElements(Object inputElement) {
				Object[] elements = super.getElements(inputElement);
				Object[] result = new Object[elements.length + 1];
				System.arraycopy(elements, 0, result, 1, elements.length);
				result[0] = blockedElement;
				return result;
			}
		};
	}

	/**
	 * Clears the cursors in the dialog.
	 */
	private void clearCursors() {
		clearCursor(cancelSelected);
		clearCursor(getShell());
	}

	/**
	 * Clears the cursor on the supplied control.
	 *
	 * @param control
	 */
	private void clearCursor(Control control) {
		if (control != null && !control.isDisposed()) {
			control.setCursor(null);
		}
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(ProgressMessages.get().BlockedJobsDialog_BlockedTitle);
		shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
	}

	/**
	 * This method sets the message in the message label.
	 *
	 * @param messageString
	 *            the String for the message area
	 */
	private void setMessage(String messageString) {
		// must not set null text in a label
		message = messageString == null ? "" : messageString; //$NON-NLS-1$
		if (messageLabel == null || messageLabel.isDisposed()) {
			return;
		}
		messageLabel.setText(message);
	}

	@Override
	protected Image getImage() {
		return getInfoImage();
	}

	/**
	 * Returns the progress monitor being used for this dialog. This allows
	 * recursive blockages to also respond to cancelation.
	 *
	 * @return IProgressMonitor
	 */
	public IProgressMonitor getProgressMonitor() {
		return blockingMonitor;
	}

	/**
	 * Requests that the blocked jobs dialog be closed. The supplied monitor
	 * must be the same one that was passed to the createBlockedDialog method.
	 *
	 * @param monitor
	 * @return IProgressMonitor
	 */
	public boolean close(IProgressMonitor monitor) {
		// ignore requests to close the dialog from all but the first monitor
		if (blockingMonitor != monitor) {
			return false;
		}
		return close();
	}

	@Override
	public boolean close() {
        // Clear the singleton first
// RAP [fappel]:
//      singleton = null;
        setSingleton( null );
        clearCursors();
        return super.close();
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		// Do nothing here as we want no buttons
		return parent;
	}

	/**
	 * @param taskName
	 *            The blockedTaskName to set.
	 */
	void setBlockedTaskName(String taskName) {
		this.blockedTaskName = taskName;
	}
}
