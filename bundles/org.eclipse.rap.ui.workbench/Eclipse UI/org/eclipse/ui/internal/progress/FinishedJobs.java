/*******************************************************************************
 * Copyright (c) 2003, 2015 IBM Corporation and others.
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.core.commands.common.EventManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.rap.rwt.SingletonUtil;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.progress.IProgressConstants;

/**
 * This singleton remembers all JobTreeElements that should be preserved (e.g.
 * because their associated Jobs have the "keep" property set).
 */
public final class FinishedJobs extends EventManager {

	/*
	 * Interface for notify listeners.
	 */
	public interface KeptJobsListener {

		/**
		 * A job to be kept has finished
		 *
		 * @param jte
		 */
		void finished(JobTreeElement jte);

		/**
		 * A kept job has been removed.
		 *
		 * @param jte {@code null} if all elements were removed
		 */
		void removed(JobTreeElement jte);
	}

// RAP [fappel]: FinishedJobs need to be session aware
//  private static FinishedJobs theInstance;

	private IJobProgressManagerListener listener;

	private Set<JobTreeElement> keptjobinfos = new LinkedHashSet<>();

	private Map<JobTreeElement, Long> finishedTime = new HashMap<>();

// RAP [fappel]:
//	private static JobTreeElement[] emptyInfos;
	private static JobTreeElement[] emptyInfos = new JobTreeElement[0];

// RAP [fappel]: FinishedJobs need to be session aware
//		public static synchronized FinishedJobs getInstance() {
	public static FinishedJobs getInstance() {
//		  if (theInstance == null) {
//		    theInstance = new FinishedJobs();
//		    emptyInfos = new JobTreeElement[0];
//		  }
//		  return theInstance;
		return SingletonUtil.getSessionInstance(FinishedJobs.class);
		}

	private FinishedJobs() {
		listener = new IJobProgressManagerListener() {
			@Override
			public void addJob(JobInfo info) {
				checkForDuplicates(info);
			}

			@Override
			public void addGroup(GroupInfo info) {
				checkForDuplicates(info);
			}

			@Override
			public void refreshJobInfo(JobInfo info) {
				checkTasks(info);
			}

			@Override
			public void refreshGroup(GroupInfo info) {
			}

			@Override
			public void refreshAll() {
			}

			@Override
			public void removeJob(JobInfo info) {
				if (keep(info)) {
					checkForDuplicates(info);
					add(info);
				}
			}

			@Override
			public void removeGroup(GroupInfo group) {
			}

			@Override
			public boolean showsDebug() {
				return false;
			}
		};
		ProgressManager.getInstance().addListener(listener);
	}

	/**
	 * Returns true if JobInfo indicates that it must be kept.
	 */
	static boolean keep(JobInfo info) {
		Job job = info.getJob();
		if (job != null) {
			Object prop = job.getProperty(ProgressManagerUtil.KEEP_PROPERTY);
			if (prop instanceof Boolean) {
				if (((Boolean) prop).booleanValue()) {
					return true;
				}
			}

			prop = job.getProperty(ProgressManagerUtil.KEEPONE_PROPERTY);
			if (prop instanceof Boolean) {
				if (((Boolean) prop).booleanValue()) {
					return true;
				}
			}

			IStatus status = job.getResult();
			if (status != null && status.getSeverity() == IStatus.ERROR) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Register for notification.
	 */
	public void addListener(KeptJobsListener l) {
		addListenerObject(l);
	}

	/**
	 * Deregister for notification.
	 */
	void removeListener(KeptJobsListener l) {
		removeListenerObject(l);
	}

	private void checkForDuplicates(GroupInfo info) {
		for (Object child : info.getChildren()) {
			if (child instanceof JobInfo) {
				checkForDuplicates((JobInfo) child);
			}
		}
	}

	private void checkForDuplicates(JobTreeElement info) {
		JobTreeElement[] toBeRemoved = findJobsToRemove(info);
		if (toBeRemoved != null) {
			for (JobTreeElement element : toBeRemoved) {
				remove(element);
			}
		}
	}

	/**
	 * Add given Job to list of kept jobs.
	 */
	private void add(JobInfo info) {
		boolean fire = false;

		if (!keptjobinfos.contains(info)) {
			keptjobinfos.add(info);

			long now = System.currentTimeMillis();
			finishedTime.put(info, Long.valueOf(now));

			JobTreeElement parent = info.getParent();
			if (!(parent == null || keptjobinfos.contains(parent))) {
				keptjobinfos.add(parent);
				finishedTime.put(parent, Long.valueOf(now));
			}

			fire = true;
		}

		if (fire) {
			for (Object listener : getListeners()) {
				KeptJobsListener jv = (KeptJobsListener) listener;
				jv.finished(info);
			}
		}
	}

	static void disposeAction(JobTreeElement jte) {
		if (jte.isJobInfo()) {
			JobInfo ji = (JobInfo) jte;
			Job job = ji.getJob();
			if (job != null) {
				Object prop = job
						.getProperty(IProgressConstants.ACTION_PROPERTY);
				if (prop instanceof ActionFactory.IWorkbenchAction) {
					((ActionFactory.IWorkbenchAction) prop).dispose();
				}
			}
		}
	}

	private JobTreeElement[] findJobsToRemove(JobTreeElement info) {

		if (info.isJobInfo()) {
			Job myJob = ((JobInfo) info).getJob();

			if (myJob != null) {

				Object prop = myJob.getProperty(ProgressManagerUtil.KEEPONE_PROPERTY);
				if (prop instanceof Boolean && ((Boolean) prop).booleanValue()) {
					ArrayList<JobTreeElement> found = null;
					JobTreeElement[] all;
					all = keptjobinfos.toArray(new JobTreeElement[keptjobinfos.size()]);
					for (JobTreeElement jobTreeElement : all) {
						if (jobTreeElement != info && jobTreeElement.isJobInfo()) {
							Job job = ((JobInfo) jobTreeElement).getJob();
							if (job != null && job != myJob
									&& job.belongsTo(myJob)) {
								if (found == null) {
									found = new ArrayList<>();
								}
								found.add(jobTreeElement);
							}
						}
					}
					if (found != null) {
						return found
								.toArray(new JobTreeElement[found.size()]);
					}
				}
			}
		}
		return null;
	}

	private void checkTasks(JobInfo info) {
		if (keep(info)) {
			TaskInfo tinfo = info.getTaskInfo();
			if (tinfo != null) {
				JobTreeElement[] toBeRemoved = null;
				boolean fire = false;
				JobInfo element = tinfo.getParent();
				if (element == info && !keptjobinfos.contains(tinfo)) {
					toBeRemoved = findJobsToRemove(element);
					keptjobinfos.add(tinfo);
					finishedTime.put(tinfo, Long.valueOf(System.currentTimeMillis()));
				}

				if (toBeRemoved != null) {
					for (JobTreeElement jobTreeElement : toBeRemoved) {
						remove(jobTreeElement);
					}
				}

				if (fire) {
					for (Object listener : getListeners()) {
						KeptJobsListener jv = (KeptJobsListener) listener;
						jv.finished(info);
					}
				}
			}
		}
	}

	public void removeErrorJobs() {
		JobTreeElement[] infos = getKeptElements();
		for (JobTreeElement info : infos) {
			if (info.isJobInfo()) {
				JobInfo info1 = (JobInfo) info;
				Job job = info1.getJob();
				if (job != null) {
					IStatus status = job.getResult();
					if (status != null && status.getSeverity() == IStatus.ERROR) {
						JobTreeElement topElement = info1
								.getParent();
						if (topElement == null) {
							topElement = info1;
						}
						FinishedJobs.getInstance().remove(topElement);
					}
				}
			}
		}
	}

	boolean remove(JobTreeElement jte) {
		boolean fire = false;
		boolean removed = false;

		if (keptjobinfos.remove(jte)) {
			removed = true;
			finishedTime.remove(jte);
			disposeAction(jte);

			// delete all elements that have jte as their direct or indirect
			// parent
			JobTreeElement jtes[] = keptjobinfos.toArray(new JobTreeElement[keptjobinfos.size()]);
			for (JobTreeElement jobTreeElement : jtes) {
				JobTreeElement parent = jobTreeElement.getParent();
				if (parent != null) {
					if (parent == jte || parent.getParent() == jte) {
						if (keptjobinfos.remove(jobTreeElement)) {
							disposeAction(jobTreeElement);
						}
						finishedTime.remove(jobTreeElement);
					}
				}
			}
			fire = true;
		}

		if (fire) {
			// notify listeners
			for (Object listener : getListeners()) {
				KeptJobsListener jv = (KeptJobsListener) listener;
				jv.removed(jte);
			}
		}
		return removed;
	}

	/**
	 * Returns all kept elements.
	 */
	JobTreeElement[] getKeptElements() {
		JobTreeElement[] all;
		if (keptjobinfos.isEmpty()) {
			return emptyInfos;
		}

		synchronized (keptjobinfos) {
			all = keptjobinfos
					.toArray(new JobTreeElement[keptjobinfos.size()]);
		}

		return all;
	}

	/**
	 * Get the date that indicates the finish time.
	 *
	 * @param jte
	 * @return Date
	 */
	public Date getFinishDate(JobTreeElement jte) {
		Long o = finishedTime.get(jte);
		if (o != null) {
			return new Date(o.longValue());
		}
		return null;
	}

	/**
	 * Return whether or not the kept infos have the element.
	 *
	 * @param element
	 * @return boolean
	 */
	public boolean isKept(JobTreeElement element) {
		return keptjobinfos.contains(element);
	}

	/**
	 * Clear all kept jobs.
	 */
	public void clearAll() {
		synchronized (keptjobinfos) {
			JobTreeElement[] all = keptjobinfos
					.toArray(new JobTreeElement[keptjobinfos.size()]);
			for (JobTreeElement jobTreeElement : all) {
				disposeAction(jobTreeElement);
			}
			keptjobinfos.clear();
			finishedTime.clear();
		}

		// notify listeners
		for (Object listener : getListeners()) {
			KeptJobsListener jv = (KeptJobsListener) listener;
			jv.removed(null);
		}
	}

	/**
	 * Return the set of kept jobs.
	 * @return Set
	 */
	Set<JobTreeElement> getKeptAsSet() {
		return keptjobinfos;
	}
}
