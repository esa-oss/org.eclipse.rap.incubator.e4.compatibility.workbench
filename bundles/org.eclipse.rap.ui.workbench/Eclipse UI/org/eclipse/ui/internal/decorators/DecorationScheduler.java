/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
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
 *     Francis Upton <francisu@ieee.org> -
 *     		Fix for Bug 216667 [Decorators] DecorationScheduler hangs onto objects forever sometimes
 *******************************************************************************/
package org.eclipse.ui.internal.decorators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.DecorationContext;
import org.eclipse.jface.viewers.IDecorationContext;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * The DecorationScheduler is the class that handles the decoration of elements
 * using a background thread.
 */
public class DecorationScheduler {

	static final ILabelProviderListener[] EMPTY_LISTENER_LIST = new ILabelProviderListener[0];

	// When decorations are computed they are added to this cache via
	// decorated() method
	Map resultCache = new HashMap();

	// Objects that need an icon and text computed for display to the user
	List awaitingDecoration = new ArrayList();

	// Objects that are awaiting a label update.
	Set pendingUpdate = new HashSet();

	// Key to lock write access to the pending update set
	Object pendingKey = new Object();

	Map awaitingDecorationValues = new HashMap();

	DecoratorManager decoratorManager;

	boolean shutdown = false;

	Job decorationJob;

	UIJob updateJob;

	private Collection removedListeners = Collections.synchronizedSet(new HashSet());

	private Job clearJob;

	// RAP [rh] need display to be able to later fake service context
	private final Display display;

	// Static used for the updates to indicate an update is required
	static final int NEEDS_INIT = -1;

	/** Amount of time to delay the update notification when max reached. */
	static final int UPDATE_DELAY = 100;

	/**
	 * Return a new instance of the receiver configured for the supplied
	 * DecoratorManager.
	 *
	 * @param manager
	 */
	DecorationScheduler(DecoratorManager manager) {
		decoratorManager = manager;
		//RAP [rh] assign current display
		display = Display.getCurrent();
		createDecorationJob();
	}

	/**
	 * Decorate the text for the receiver. If it has already been done then
	 * return the result, otherwise queue it for decoration.
	 *
	 * @return String
	 * @param text
	 * @param element
	 * @param adaptedElement
	 *            The adapted value of element. May be null.
	 * @param context
	 *            the decoration context
	 */

	public String decorateWithText(String text, Object element, Object adaptedElement, IDecorationContext context) {

		DecorationResult decoration = getResult(element, adaptedElement, context);

		if (decoration == null) {
			return text;
		}

		return decoration.decorateWithText(text);

	}

	/**
	 * Queue the element and its adapted value if it has not been already.
	 *
	 * @param element
	 * @param adaptedElement
	 *            The adapted value of element. May be null.
	 * @param forceUpdate
	 *            If true then a labelProviderChanged is fired whether
	 *            decoration occurred or not.
	 * @param undecoratedText
	 *            The original text for the element if it is known.
	 * @param context
	 *            The decoration context
	 */

	synchronized void queueForDecoration(Object element, Object adaptedElement, boolean forceUpdate,
			String undecoratedText, IDecorationContext context) {

		Assert.isNotNull(context);
		DecorationReference reference = (DecorationReference) awaitingDecorationValues.get(element);
		if (reference != null) {
			if (forceUpdate) {// Make sure we don't loose a force
				reference.setForceUpdate(forceUpdate);
			}
			reference.addContext(context);
		} else {
			reference = new DecorationReference(element, adaptedElement, context);
			reference.setForceUpdate(forceUpdate);
			reference.setUndecoratedText(undecoratedText);
			awaitingDecorationValues.put(element, reference);
			awaitingDecoration.add(element);
			if (shutdown) {
				return;
			}
			if (decorationJob.getState() == Job.SLEEPING) {
                // RAP [rh] fake service context
				RWT.getUISession(display).exec(() -> decorationJob.wakeUp());
            }
			// RAP [rh] fake service context

			RWT.getUISession(display).exec(() -> decorationJob.schedule());

		}

	}

	/**
	 * Decorate the supplied image, element and its adapted value.
	 *
	 * @return Image
	 * @param image
	 * @param element
	 * @param adaptedElement
	 *            The adapted value of element. May be null.
	 * @param context
	 *            the decoration context
	 * @param manager
	 *
	 */
	public Image decorateWithOverlays(Image image, Object element, Object adaptedElement, IDecorationContext context,
			ResourceManager manager) {

		DecorationResult decoration = getResult(element, adaptedElement, context);

		if (decoration == null) {
			return image;
		}
		return decoration.decorateWithOverlays(image, manager);
	}

	/**
	 * Return the DecorationResult for element. If there isn't one queue for
	 * decoration and return <code>null</code>.
	 *
	 * @param element
	 *            The element to be decorated. If it is <code>null</code>
	 *            return <code>null</code>.
	 * @param adaptedElement
	 *            It's adapted value.
	 * @param context
	 *            The deocration context
	 * @return DecorationResult or <code>null</code>
	 */
	private DecorationResult getResult(Object element, Object adaptedElement, IDecorationContext context) {

		// We do not support decoration of null
		if (element == null) {
			return null;
		}

		DecorationResult decoration = internalGetResult(element, context);

		if (decoration == null) {
			queueForDecoration(element, adaptedElement, false, null, context);
			return null;
		}
		return decoration;

	}

	private DecorationResult internalGetResult(Object element,
			IDecorationContext context) {
		Map results = (Map) resultCache.get(context);
		if (results != null) {
			return (DecorationResult) results.get(element);
		}
		return null;
	}

	protected void internalPutResult(Object element,
			IDecorationContext context, DecorationResult result) {
		Map results = (Map) resultCache.get(context);
		if (results == null) {
			results = new HashMap();
			resultCache.put(context, results);
		}
		results.put(element, result);
	}

	/**
	 * Execute a label update using the pending decorations.
	 */
	synchronized void decorated() {

		// Don't bother if we are shutdown now
		if (shutdown) {
			return;
		}

		// Lazy initialize the job
		if (updateJob == null) {
			updateJob = getUpdateJob();
		}

		// RAP [rh] fake service context
		RWT.getUISession(display).exec(new Runnable() {
			@Override
			public void run() {
				// Give it a bit of a lag for other updates to occur
				updateJob.schedule(UPDATE_DELAY);
			}
		});
	}

	/**
	 * Shutdown the decoration.
	 */
	synchronized void shutdown() {
		shutdown = true;
	}

	/**
	 * Get the next resource to be decorated.
	 *
	 * @return IResource
	 */
	synchronized DecorationReference nextElement() {

		if (shutdown || awaitingDecoration.isEmpty()) {
			return null;
		}
		Object element = awaitingDecoration.remove(0);

		return (DecorationReference) awaitingDecorationValues.remove(element);
	}

	/**
	 * Create the Thread used for running decoration.
	 */
	private void createDecorationJob() {
		decorationJob = new Job(WorkbenchMessages.get().DecorationScheduler_CalculationJobName) {

			/*
			 * (non-Javadoc)
			 *
			 * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.
			 * IProgressMonitor)
			 */
			@Override
			public IStatus run(final IProgressMonitor monitor) {
				final IStatus[] result = { null };
				// RAP [rh] fake service context
				RWT.getUISession(display).exec(new Runnable() {
					@Override
					public void run() {
						result[0] = doRun(monitor);
					}
				});
				return result[0];
			}

			public IStatus doRun(IProgressMonitor monitor) {

				synchronized (DecorationScheduler.this) {
					if (shutdown) {
						return Status.CANCEL_STATUS;
					}
				}

				while (updatesPending()) {

					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// Cancel and try again if there was an error
						// RAP [rh] fake service context
						RWT.getUISession(display).exec(new Runnable() {
							@Override
							public void run() {
								decorationJob.schedule();
							}
						});
						return Status.CANCEL_STATUS;
					}
				}
				// RAP [if] Scheduler could be shutdown at this point
				synchronized (DecorationScheduler.this) {
					if (shutdown) {
						return Status.CANCEL_STATUS;
					}
				}

				SubMonitor subMonitor = SubMonitor.convert(monitor);
				subMonitor.setTaskName(WorkbenchMessages.get().DecorationScheduler_CalculatingTask);
				// will block if there are no resources to be decorated
				DecorationReference reference;

				while ((reference = nextElement()) != null) {

					SubMonitor loopMonitor = subMonitor.setWorkRemaining(100).split(1);
					Object element = reference.getElement();
					boolean force = reference.shouldForceUpdate();
					IDecorationContext[] contexts = reference.getContexts();
					loopMonitor.setWorkRemaining(contexts.length);
					for (IDecorationContext context : contexts) {
						ensureResultCached(element, force, context);
						loopMonitor.split(1);
					}
					// Only notify listeners when we have exhausted the
					// queue of decoration requests.
					synchronized (DecorationScheduler.this) {
						if (awaitingDecoration.isEmpty()) {
							decorated();
						}
					}
				}
				return Status.OK_STATUS;
			}

			/**
			 * Ensure that a result is cached for the given element and context
			 *
			 * @param element
			 *            the elements
			 * @param force
			 *            whether an update should be forced
			 * @param context
			 *            the decoration context
			 */
			private void ensureResultCached(Object element, boolean force,
					IDecorationContext context) {
				boolean elementIsCached = internalGetResult(element, context) != null;
				if (elementIsCached) {
					synchronized (pendingKey) {
						pendingUpdate.add(element);
					}

				}

				if (!elementIsCached) {
					DecorationBuilder cacheResult = new DecorationBuilder(context);
					// Calculate the decoration
					decoratorManager.getLightweightManager().getDecorations(element, cacheResult);

					// If we should update regardless then put a result
					// anyways
					if (cacheResult.hasValue() || force) {

						// Synchronize on the result lock as we want to
						// be sure that we do not try and decorate during
						// label update servicing.
						// Note: resultCache and pendingUpdate modifications
						// must be done atomically.

						// Add the decoration even if it's empty in
						// order to indicate that the decoration is
						// ready
						internalPutResult(element, context, cacheResult.createResult());

						// Add an update for only the original element
						// to
						// prevent multiple updates and clear the cache.
						synchronized (pendingKey) {
							pendingUpdate.add(element);
						}


					}
				}
			}

			@Override
			public boolean belongsTo(Object family) {
				return DecoratorManager.FAMILY_DECORATE == family;
			}

			@Override
			public boolean shouldRun() {
				final boolean[] result = { false };
				// RAP [rh] fake service context
				RWT.getUISession(display).exec(new Runnable() {
					@Override
					public void run() {
						// result[ 0 ] = PatformUI.isWorkbenchRunning();
						result[0] = !shutdown && PlatformUI.isWorkbenchRunning();
					}
				});
				return result[0];
			}
		};

		decorationJob.setSystem(true);
		decorationJob.setPriority(Job.DECORATE);
		decorationJob.schedule();
	}

	/**
	 * Return whether or not we are waiting on updated
	 *
	 * @return <code>true</code> if there are updates waiting to be served
	 */
	protected boolean updatesPending() {
		// RAP [rh] stop updating when session is invalidated (see bug 280363)
		if (shutdown)
			return false;
		// RAP END
		if (updateJob != null && updateJob.getState() != Job.NONE) {
			return true;
		}
		if (clearJob != null && clearJob.getState() != Job.NONE) {
			return true;
		}
		return false;
	}

	/**
	 * An external update request has been made. Clear the results as they are
	 * likely obsolete now.
	 */
	void clearResults() {
		if (clearJob == null) {
			clearJob = getClearJob();
		}
		clearJob.schedule();
	}

	private Job getClearJob() {
		Job clear = new Job(WorkbenchMessages.get().DecorationScheduler_ClearResultsJob) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				resultCache.clear();
				return Status.OK_STATUS;
			}

			@Override
			public boolean shouldRun() {
				final boolean[] result = { false };
				// RAP [rh] fake service context
				RWT.getUISession(display).exec(new Runnable() {
					@Override
					public void run() {
						result[0] = PlatformUI.isWorkbenchRunning();
					}
				});
				return result[0];
			}

		};
		clear.setSystem(true);

		return clear;
	}

	/**
	 * Get the update WorkbenchJob.
	 *
	 * @return WorkbenchJob
	 */
	private WorkbenchJob getUpdateJob() {
		WorkbenchJob job = new WorkbenchJob(WorkbenchMessages.get().DecorationScheduler_UpdateJobName) {

			int currentIndex = NEEDS_INIT;

			LabelProviderChangedEvent labelProviderChangedEvent;

			ILabelProviderListener[] listeners;

			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {

				synchronized (DecorationScheduler.this) {
					if (shutdown) {
						return Status.CANCEL_STATUS;
					}
				}

				// If this is the first one check again in case
				// someone has already cleared it out.
				if (currentIndex == NEEDS_INIT) {
					if (hasPendingUpdates()) {
					    resetState();
						return Status.OK_STATUS;
					}
					setUpUpdates();
				}

				if (listeners.length == 0) {
				    resetState();
				    return Status.OK_STATUS;
				}

				monitor.beginTask(WorkbenchMessages.get().DecorationScheduler_UpdatingTask, IProgressMonitor.UNKNOWN);

				long startTime = System.currentTimeMillis();
				while (currentIndex < listeners.length) {
					ILabelProviderListener listener = listeners[currentIndex];
					currentIndex++;

					// If it was removed in the meantime then skip it.
					if (!removedListeners.contains(listener)) {
						decoratorManager.fireListener(
								labelProviderChangedEvent, listener);
					}

					// If it is taking long enough for the user to notice then
					// cancel the updates.
					if ((System.currentTimeMillis() - startTime) >= UPDATE_DELAY / 2) {
						break;
					}
				}

				monitor.done();

				if (currentIndex >= listeners.length) {
				    resetState();
					if (!hasPendingUpdates()) {
						decorated();
					}
					labelProviderChangedEvent = null;
					listeners = EMPTY_LISTENER_LIST;
				} else {
					// RAP [rh] fake service context

					RWT.getUISession(display).exec(new Runnable() {
						@Override
						public void run() {
							schedule(UPDATE_DELAY);// Reschedule if we are not done
						}
					});
				}
				return Status.OK_STATUS;
			}

            /**
             * Clear any cached information.
             */
            private void resetState() {
                currentIndex = NEEDS_INIT;// Reset
                removedListeners.clear();
                // Other decoration requests may have occurred due to
                // updates or we may have timed out updating listeners.
                // Only clear the results if there are none pending.
                if (awaitingDecoration.isEmpty()) {
                    resultCache.clear();
                }
            }

			private void setUpUpdates() {
				// Get the elements awaiting update and then
				// clear the list
				removedListeners.clear();
				currentIndex = 0;
				synchronized (pendingKey) {
					Object[] elements = pendingUpdate.toArray(new Object[pendingUpdate.size()]);
					pendingUpdate.clear();
					labelProviderChangedEvent = new LabelProviderChangedEvent(decoratorManager, elements);
				}
				listeners = decoratorManager.getListeners();
			}

			@Override
			public boolean belongsTo(Object family) {
				return DecoratorManager.FAMILY_DECORATE == family;
			}

			@Override
			public boolean shouldRun() {
				final boolean[] result = { false };
				// RAP [rh] fake service context
				RWT.getUISession(display).exec(new Runnable() {
					@Override
					public void run() {
						result[0] = PlatformUI.isWorkbenchRunning();
					}
				});
				return result[0];
			}
		};

		job.setSystem(true);
		return job;
	}

	/**
	 * Return whether or not there is a decoration for this element ready.
	 *
	 * @param element
	 * @param context
	 *            The decoration context
	 * @return boolean true if the element is ready.
	 */
	public boolean isDecorationReady(Object element, IDecorationContext context) {
		return internalGetResult(element, context) != null;
	}

	/**
	 * Return the background Color for element. If there is no result cue for
	 * decoration and return null, otherwise return the value in the result.
	 *
	 * @param element
	 *            The Object to be decorated
	 * @param adaptedElement
	 * @return Color or <code>null</code> if there is no value or if it is has
	 *         not been decorated yet.
	 */
	public Color getBackgroundColor(Object element, Object adaptedElement) {
		DecorationResult decoration = getResult(element, adaptedElement, DecorationContext.DEFAULT_CONTEXT);

		if (decoration == null) {
			return null;
		}
		return decoration.getBackgroundColor();
	}

	/**
	 * Return the font for element. If there is no result cue for decoration and
	 * return null, otherwise return the value in the result.
	 *
	 * @param element
	 *            The Object to be decorated
	 * @param adaptedElement
	 * @return Font or <code>null</code> if there is no value or if it is has
	 *         not been decorated yet.
	 */
	public Font getFont(Object element, Object adaptedElement) {
		DecorationResult decoration = getResult(element, adaptedElement, DecorationContext.DEFAULT_CONTEXT);

		if (decoration == null) {
			return null;
		}
		return decoration.getFont();
	}

	/**
	 * Return the foreground Color for element. If there is no result cue for
	 * decoration and return null, otherwise return the value in the result.
	 *
	 * @param element
	 *            The Object to be decorated
	 * @param adaptedElement
	 * @return Color or <code>null</code> if there is no value or if it is has
	 *         not been decorated yet.
	 */
	public Color getForegroundColor(Object element, Object adaptedElement) {
		DecorationResult decoration = getResult(element, adaptedElement, DecorationContext.DEFAULT_CONTEXT);

		if (decoration == null) {
			return null;
		}
		return decoration.getForegroundColor();
	}

	/**
	 * Return whether or not any updates are being processed/
	 *
	 * @return boolean
	 */
	public boolean processingUpdates() {
		return !hasPendingUpdates() && !awaitingDecoration.isEmpty();
	}

	/**
	 * A listener has been removed. If we are updating then skip it.
	 *
	 * @param listener
	 */
	void listenerRemoved(ILabelProviderListener listener) {
		if (updatesPending()) {// Only keep track of them if there are updates
			// pending
			removedListeners.add(listener);
		}
		if (!updatesPending()) {
			removedListeners.remove(listener);
		}

	}

	/**
	 * Return whether or not there are any updates pending.
	 *
	 * @return boolean <code>true</code> if the updates are empty
	 */
	boolean hasPendingUpdates() {
		synchronized (pendingKey) {
			return pendingUpdate.isEmpty();
		}

	}
}
