/*******************************************************************************
 * Copyright (c) 2004, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * IBM - Initial API and implementation
 * Markus Schorn (Wind River Systems)
 * Patrik Suzzi <psuzzi@gmail.com> - Bug 460683
 *******************************************************************************/
package org.eclipse.ui.internal.progress;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.internal.lifecycle.LifeCycleUtil;
import org.eclipse.rap.rwt.internal.service.ContextProvider;
import org.eclipse.rap.rwt.service.UISession;
import org.eclipse.rap.rwt.service.UISessionEvent;
import org.eclipse.rap.rwt.service.UISessionListener;
import org.eclipse.rap.ui.internal.progress.JobCanceler;
import org.eclipse.rap.ui.internal.progress.ProgressUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.progress.IProgressConstants;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * The ProgressMonitorFocusJobDialog is a dialog that shows progress for a
 * particular job in a modal dialog so as to give a user accustomed to a modal
 * UI a more familiar feel.
 */
public class ProgressMonitorFocusJobDialog extends ProgressMonitorJobsDialog {
	Job job;
	private boolean showDialog;

	/**
	 * Create a new instance of the receiver with progress reported on the job.
	 *
	 * @param parentShell
	 *            The shell this is parented from.
	 */
	public ProgressMonitorFocusJobDialog(Shell parentShell) {
		super(parentShell == null ? ProgressManagerUtil.getNonModalShell()
				: parentShell);
        // RAP [fappel]: fix this, switched to modal since we do not have
        //               a client side window management system to keep
        //               the dialog in front...
//      setShellStyle(getDefaultOrientation() | SWT.BORDER | SWT.TITLE
//              | SWT.RESIZE | SWT.MAX | SWT.MODELESS);
		setCancelable(true);
		enableDetailsButton = true;
	}

	@Override
	protected void cancelPressed() {
		job.cancel();
		super.cancelPressed();
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(job.getName());
		shell.addTraverseListener(e -> {
			if (e.detail == SWT.TRAVERSE_ESCAPE) {
				cancelPressed();
				e.detail = SWT.TRAVERSE_NONE;
				e.doit = true;
			}
		});
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		Button runInWorkspace = createButton(
				parent,
				IDialogConstants.CLOSE_ID,
				ProgressMessages.get().ProgressMonitorFocusJobDialog_RunInBackgroundButton,
				true);
		runInWorkspace.addSelectionListener(new SelectionAdapter()
        {
		    /** {@inheritDoc} */
		    @Override
		    public void widgetSelected(SelectionEvent e)
		    {
		        Rectangle shellPosition = getShell().getBounds();
		        job.setProperty(IProgressConstants.PROPERTY_IN_DIALOG, Boolean.FALSE);
		        finishedRun();
		        ProgressManagerUtil.animateDown(shellPosition);
		    }
		});
		runInWorkspace.setCursor(arrowCursor);

		cancel = createButton(parent, IDialogConstants.CANCEL_ID,
				IDialogConstants.get().CANCEL_LABEL, false);
		cancel.setCursor(arrowCursor);

		createDetailsButton(parent);
	}

	/**
	 * Returns a listener that will close the dialog when the job completes.
	 *
	 * @return IJobChangeListener
	 */
	private IJobChangeListener createCloseListener() {
	    // RAP: Obtain localized message here as within the JobChangeAdapter#done
	    //      method there is no session context available
	    final String closeJobDialogMsg
	      = ProgressMessages.get().ProgressMonitorFocusJobDialog_CLoseDialogJob;
	      return new JobChangeAdapter() {
	            /*
	             * (non-Javadoc)
	             *
	             * @see org.eclipse.core.runtime.jobs.IJobChangeListener#done(org.eclipse.core.runtime.jobs.IJobChangeEvent)
	             */
	            @Override
				public void done(IJobChangeEvent event) {
	                // first of all, make sure this listener is removed
	                event.getJob().removeJobChangeListener(this);
	// RAP [fappel]: uses session aware approach
//	              if (!PlatformUI.isWorkbenchRunning()) {
//	                  return;
//	              }
	                Display display;
	                if( getShell() == null ) {
	                  if( !ContextProvider.hasContext() ) {
	                    return;
	                  }
	                  display = LifeCycleUtil.getSessionDisplay();
	                } else {
	                  display = getShell().getDisplay();

	                }
	                if (!ProgressUtil.isWorkbenchRunning( display ) ) {
	                    return;
	                }

	                // nothing to do if the dialog is already closed
	                if (getShell() == null) {
	                    return;
	                }
	                // RAP [fappel]: ensure mapping to context
	                RWT.getUISession( display ).exec( new Runnable() {
	                  @Override
					public void run() {
	                    final WorkbenchJob closeJob = new WorkbenchJob(closeJobDialogMsg) {
	                        /*
	                         * (non-Javadoc)
	                         *
	                         * @see org.eclipse.ui.progress.UIJob#runInUIThread(org.eclipse.core.runtime.IProgressMonitor)
	                         */
	                        @Override
							public IStatus runInUIThread(IProgressMonitor monitor) {
	                            Shell currentShell = getShell();
	                            if (currentShell == null || currentShell.isDisposed()) {
	                                return Status.CANCEL_STATUS;
	                            }
	                            finishedRun();
	                            return Status.OK_STATUS;
	                        }
	                    };
	                    closeJob.setSystem(true);
	                    closeJob.schedule();
	                  }
	                } );
	            }
	        };
	    }

	@Override
	public int open() {
        int result = super.open();

        // add a listener that will close the dialog when the job completes.
        final IJobChangeListener listener = createCloseListener();
        job.addJobChangeListener(listener);
        if (job.getState() == Job.NONE) {
            // if the job completed before we had a chance to add
            // the listener, just remove the listener and return
            job.removeJobChangeListener(listener);
            finishedRun();
            cleanUpFinishedJob();
        } else {
          // RAP [fappel]: Ensure that job changed listener is removed in case
          //               of session timeout before the job ends. Note that
          //               this is still under investigation
          final UISession uiSession = RWT.getUISession();
          final AtomicReference<JobChangeAdapter> doneListener
            = new AtomicReference<JobChangeAdapter>();
          final AtomicBoolean isSessionAlive = new AtomicBoolean();
          final UISessionListener cleanupListener = new UISessionListener() {
            @Override
			public void beforeDestroy( UISessionEvent event ) {
              if( !isSessionAlive.get() ) {
                job.removeJobChangeListener( listener );
                job.removeJobChangeListener( doneListener.get() );
                job.cancel();
                job.addJobChangeListener( new JobCanceler() );
              }
            }
          };
          doneListener.set( new JobChangeAdapter() {
            @Override
			public void done( final IJobChangeEvent event ) {
              job.removeJobChangeListener( this );
              isSessionAlive.set( true );
              uiSession.removeUISessionListener( cleanupListener );
            }
          } );
          uiSession.addUISessionListener( cleanupListener );
          job.addJobChangeListener( doneListener.get() );
        }

        return result;
    }

	/**
	 * Opens this dialog for the duration that the given job is running.
	 *
	 * @param jobToWatch
	 * @param originatingShell
	 *            The shell this request was created from. Do not block on this
	 *            shell.
	 */
	public void show(Job jobToWatch, final Shell originatingShell) {
		job = jobToWatch;
		// after the dialog is opened we can get access to its monitor
		job.setProperty(IProgressConstants.PROPERTY_IN_DIALOG, Boolean.TRUE);

		setOpenOnRun(false);
		aboutToRun();

		final Object jobIsDone = new Object();
		final JobChangeAdapter jobListener = new JobChangeAdapter() {
			@Override
			public void done(IJobChangeEvent event) {
				synchronized (jobIsDone) {
					jobIsDone.notify();
				}
			}
		};
		job.addJobChangeListener(jobListener);

		// start with a quick busy indicator. Lock the UI as we
		// want to preserve modality
		BusyIndicator.showWhile(PlatformUI.getWorkbench().getDisplay(),
				() -> {
					try {
						synchronized (jobIsDone) {
							if (job.getState() != Job.NONE) {
								jobIsDone.wait(ProgressManagerUtil.SHORT_OPERATION_TIME);
							}
						}
					} catch (InterruptedException e) {
						// Do not log as this is a common operation from the
						// lock listener
					}
				});
		job.removeJobChangeListener(jobListener);

		WorkbenchJob openJob = new WorkbenchJob(
				ProgressMessages.get().ProgressMonitorFocusJobDialog_UserDialogJob) {
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {

				// if the job is done at this point, we don't need the dialog
				if (job.getState() == Job.NONE) {
					finishedRun();
					cleanUpFinishedJob();
					return Status.CANCEL_STATUS;
				}

				// now open the progress dialog if nothing else is
				if (!ProgressManagerUtil.safeToOpen(
						ProgressMonitorFocusJobDialog.this, originatingShell)) {
					return Status.CANCEL_STATUS;
				}

				// Do not bother if the parent is disposed
				if (getParentShell() != null && getParentShell().isDisposed()) {
					return Status.CANCEL_STATUS;
				}

				// RAP [DM] no ProgressMonitorUtil
//				JobMonitor jobMonitor = ProgressManager.getInstance().progressFor(job);
//				Display d = Display.getCurrent();
//				IProgressMonitorWithBlocking wrapper = ProgressMonitorUtil
//						.createAccumulatingProgressMonitor(getProgressMonitor(), d);
//				jobMonitor.addProgressListener(wrapper);
//				open();

				return Status.OK_STATUS;
			}
		};
		openJob.setSystem(true);
		openJob.schedule();

	}

	/**
	 * The job finished before we did anything so clean up the finished
	 * reference.
	 */
	private void cleanUpFinishedJob() {
		ProgressManager.getInstance().checkForStaleness(job);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Control area = super.createDialogArea(parent);
		// Give the job info as the initial details
		getProgressMonitor().setTaskName(
				ProgressManager.getInstance().getJobInfo(this.job)
                        .getDisplayString());
		return area;
	}

	@Override
	protected void createExtendedDialogArea(Composite parent) {

		showDialog = WorkbenchPlugin.getDefault().getPreferenceStore()
				.getBoolean(IPreferenceConstants.RUN_IN_BACKGROUND);
		final Button showUserDialogButton = new Button(parent, SWT.CHECK);
		showUserDialogButton
				.setText(WorkbenchMessages.get().WorkbenchPreference_RunInBackgroundButton);
		showUserDialogButton
				.setToolTipText(WorkbenchMessages.get().WorkbenchPreference_RunInBackgroundToolTip);
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		gd.horizontalAlignment = GridData.FILL;
		showUserDialogButton.setLayoutData(gd);

		showUserDialogButton.addSelectionListener(new SelectionAdapter()
        {
		    @Override
			public void widgetSelected(SelectionEvent e) {
		        showDialog = showUserDialogButton.getSelection();
		    }
        });

		super.createExtendedDialogArea(parent);
	}

	@Override
	public boolean close() {
		if (getReturnCode() != CANCEL)
			WorkbenchPlugin.getDefault().getPreferenceStore().setValue(
					IPreferenceConstants.RUN_IN_BACKGROUND, showDialog);

		return super.close();
	}
}
