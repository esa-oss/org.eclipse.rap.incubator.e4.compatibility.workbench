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
package org.eclipse.ui.internal.progress;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.IPreferenceConstants;
import org.eclipse.ui.internal.IWorkbenchHelpContextIds;
import org.eclipse.ui.internal.WorkbenchPlugin;

/**
 * The DetailedProgressViewer is a viewer that shows the details of all in
 * progress job or jobs that are finished awaiting user input.
 *
 * @since 3.2
 *
 */
public class DetailedProgressViewer extends AbstractProgressViewer {

	//Maximum number of entries to display so that the view does not flood the UI with events
	private int maxDisplayed;

	Composite control;

	private ScrolledComposite scrolled;

	private Composite noEntryArea;

	/**
	 * Create a new instance of the receiver with a control that is a child of
	 * parent with style style.
	 *
	 * @param parent
	 * @param style
	 */
	public DetailedProgressViewer(Composite parent, int style) {
		scrolled = new ScrolledComposite(parent, SWT.V_SCROLL | style);
		// RAP [fappel]: setIncrement not available
//		int height = JFaceResources.getDefaultFont().getFontData()[0]
//				.getHeight();
//		scrolled.getVerticalBar().setIncrement(height * 2);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);

		control = new Composite(scrolled, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		control.setLayout(layout);
		control.setBackground(parent.getDisplay().getSystemColor(
				SWT.COLOR_LIST_BACKGROUND));

		control.addFocusListener(new FocusAdapter() {

			private boolean settingFocus = false;

			@Override
			public void focusGained(FocusEvent e) {
				if (!settingFocus) {
					// Prevent new focus events as a result this update
					// occurring
					settingFocus = true;
					setFocus();
					settingFocus = false;
				}
			}
		});

		control.addControlListener(new ControlListener() {
			@Override
			public void controlMoved(ControlEvent e) {
				updateVisibleItems();

			}

			@Override
			public void controlResized(ControlEvent e) {
				updateVisibleItems();
			}
		});

		PlatformUI.getWorkbench().getHelpSystem().setHelp(control,
				IWorkbenchHelpContextIds.RESPONSIVE_UI);

		scrolled.setContent(control);
		hookControl(control);

		noEntryArea = new Composite(scrolled, SWT.NONE);
		noEntryArea.setLayout(new GridLayout());
		noEntryArea.setBackground(noEntryArea.getDisplay()
				.getSystemColor(SWT.COLOR_LIST_BACKGROUND));

		Label noEntryLabel = new Label(noEntryArea, SWT.NONE);
		noEntryLabel.setText(ProgressMessages.get().ProgressView_NoOperations);
		noEntryLabel.setBackground(noEntryArea.getDisplay().getSystemColor(
				SWT.COLOR_LIST_BACKGROUND));
		GridData textData = new GridData(GridData.VERTICAL_ALIGN_BEGINNING);
		noEntryLabel.setLayoutData(textData);

		PlatformUI.getWorkbench().getHelpSystem().setHelp(noEntryLabel,
				IWorkbenchHelpContextIds.RESPONSIVE_UI);

		IPreferenceStore prefs = WorkbenchPlugin.getDefault().getPreferenceStore();
		updateMaxDisplayedValue(prefs);
		IPropertyChangeListener listener = x -> propertyChange(x);
		prefs.addPropertyChangeListener(listener);
		scrolled.addDisposeListener(x -> prefs.removePropertyChangeListener(listener));
	}

	public void propertyChange(PropertyChangeEvent event) {
		if (!IPreferenceConstants.MAX_PROGRESS_ENTRIES.equals(event.getProperty())) {
			return;
		}
		updateMaxDisplayedValue(WorkbenchPlugin.getDefault().getPreferenceStore());
    }

	private void updateMaxDisplayedValue(IPreferenceStore prefs) {
		int newValue = Math.max(1, prefs.getInt(IPreferenceConstants.MAX_PROGRESS_ENTRIES));
		setMaxDisplayed(newValue);
	}

	@Override
	public void add(JobTreeElement... elements) {
		ViewerComparator sorter = getComparator();

		// Use a Set in case we are getting something added that exists
		Set<JobTreeElement> newItems = new LinkedHashSet<>(elements.length);

		Control[] existingChildren = control.getChildren();
		for (Control child : existingChildren) {
			if (child.getData() != null) {
				newItems.add((JobTreeElement) child.getData());
			}
		}

		for (JobTreeElement element : elements) {
			if (element != null) {
				newItems.add(element);
			}
		}

		JobTreeElement[] infos = new JobTreeElement[newItems.size()];
		newItems.toArray(infos);

		if (sorter != null) {
			sorter.sort(this, infos);
		}

		// Update with the new elements to prevent flash
		for (Control child : existingChildren) {
			((ProgressInfoItem) child).dispose();
		}

		int totalSize = Math.min(newItems.size(), getMaxDisplayed());

		for (int i = 0; i < totalSize; i++) {
			ProgressInfoItem item = createNewItem(infos[i]);
			item.setColor(i);
		}

		updateForShowingProgress();
	}

	/**
	 * Update for the progress being displayed.
	 */
	private void updateForShowingProgress() {
		if (control.getChildren().length > 0) {
			updateSize();
			scrolled.setContent(control);
		} else {
			scrolled.setMinSize(null);
			scrolled.setContent(noEntryArea);
		}
		control.requestLayout();
	}

	/**
	 * Create a new item for info.
	 *
	 * @param info
	 * @return ProgressInfoItem
	 */
	private ProgressInfoItem createNewItem(JobTreeElement info) {
		final ProgressInfoItem item = new ProgressInfoItem(control, SWT.NONE,
				info);

		item.addControlListener(new ControlListener() {
			@Override
			public void controlMoved(ControlEvent e) {
				updateVisibleProgressItems(item);
			}

			@Override
			public void controlResized(ControlEvent e) {
				updateVisibleProgressItems(item);
			}
		});

		item.setIndexListener(new ProgressInfoItem.IndexListener() {
			@Override
			public void selectNext() {
				DetailedProgressViewer.this.selectNext(item);

			}

			@Override
			public void selectPrevious() {
				DetailedProgressViewer.this.selectPrevious(item);

			}

			@Override
			public void select() {
				for (Control element : control.getChildren()) {
					ProgressInfoItem child = (ProgressInfoItem) element;
					if (!item.equals(child)) {
						child.selectWidgets(false);
					}
				}
				item.selectWidgets(true);

			}
		});

		return item;
	}

	/**
	 * Select the previous item in the receiver.
	 *
	 * @param item
	 */
	protected void selectPrevious(ProgressInfoItem item) {
		Control[] children = control.getChildren();
		for (int i = 0; i < children.length; i++) {
			ProgressInfoItem child = (ProgressInfoItem) children[i];
			if (item.equals(child)) {
				ProgressInfoItem previous;
				if (i == 0) {
					previous = (ProgressInfoItem) children[children.length - 1];
				} else {
					previous = (ProgressInfoItem) children[i - 1];
				}

				item.selectWidgets(false);
				previous.selectWidgets(true);
				return;
			}
		}
	}

	/**
	 * Select the next item in the receiver.
	 *
	 * @param item
	 */
	protected void selectNext(ProgressInfoItem item) {
		Control[] children = control.getChildren();
		for (int i = 0; i < children.length; i++) {
			ProgressInfoItem child = (ProgressInfoItem) children[i];
			if (item.equals(child)) {
				ProgressInfoItem next;
				if (i == children.length - 1) {
					next = (ProgressInfoItem) children[0];
				} else {
					next = (ProgressInfoItem) children[i + 1];
				}
				item.selectWidgets(false);
				next.selectWidgets(true);

				return;
			}
		}

	}

	@Override
	protected Widget doFindInputItem(Object element) {
		return null;
	}

	@Override
	protected Widget doFindItem(Object element) {
		for (Control control : control.getChildren()) {
			if (control.isDisposed()
					|| control.getData() == null) {
				continue;
			}
			if (control.getData().equals(element)) {
				return control;
			}
		}
		return null;
	}

	@Override
	protected void doUpdateItem(Widget item, Object element, boolean fullMap) {
		if (usingElementMap()) {
			unmapElement(item);
		}
		item.dispose();

		add((JobTreeElement) element);
	}

	@Override
	public Control getControl() {
		return scrolled;
	}

	@Override
	protected List getSelectionFromWidget() {
		return new ArrayList<>(0);
	}

	@Override
	protected void inputChanged(Object input, Object oldInput) {
		super.inputChanged(input, oldInput);
		refreshAll();
	}

	@Override
	protected void internalRefresh(Object element) {
		if (element == null) {
			return;
		}

		if (element.equals(getRoot())) {
			refreshAll();
			return;
		}
		Widget widget = findItem(element);
		if (widget == null) {
			add((JobTreeElement) element);
			return;
		}
		((ProgressInfoItem) widget).refresh();

		updateSize();
	}

	@Override
	public void remove(JobTreeElement... elements) {

		for (Object element : elements) {
			JobTreeElement treeElement = (JobTreeElement) element;
			// Make sure we are not keeping this one
			if (FinishedJobs.getInstance().isKept(treeElement)) {
				Widget item = doFindItem(element);
				if (item != null) {
					((ProgressInfoItem) item).refresh();
				}

			} else {
				Widget item = doFindItem(treeElement);
				if (item == null) {
					// Is the parent showing?
					Object parent = treeElement.getParent();
					if (parent != null)
						item = doFindItem(parent);
				}
				if (item != null) {
					unmapElement(element);
					item.dispose();
				}
			}
		}

		Control[] existingChildren = control.getChildren();
		for (int i = 0; i < existingChildren.length; i++) {
			ProgressInfoItem item = (ProgressInfoItem) existingChildren[i];
			item.setColor(i);
		}
		updateForShowingProgress();
	}

	@Override
	public void reveal(Object element) {

	}

	@Override
	protected void setSelectionToWidget(List l, boolean reveal) {

	}

	/**
	 * Cancel the current selection
	 *
	 */
	public void cancelSelection() {

	}

	/**
	 * Set focus on the current selection.
	 *
	 */
	public void setFocus() {
		Control[] children = control.getChildren();
		if (children.length > 0) {
			((ProgressInfoItem)children[0]).setButtonFocus();
		} else {
			noEntryArea.setFocus();
		}
	}

	/**
	 * Refresh everything as the root is being refreshed.
	 */
	private void refreshAll() {

		Object[] infos = getSortedChildren(getRoot());

		for (Control control : control.getChildren()) {
			control.dispose();

		}

		int maxLength = Math.min(infos.length, getMaxDisplayed());
		// Create new ones if required
		for (int i = 0; i < maxLength; i++) {
			ProgressInfoItem item = createNewItem((JobTreeElement) infos[i]);
			item.setColor(i);
		}

		updateForShowingProgress();
	}

	/**
	 * Set the virtual items to be visible or not depending on the displayed
	 * area.
	 */
	private void updateVisibleItems() {
		updateVisibleProgressItems(control.getChildren());
	}

	private void updateVisibleProgressItems(Control... progressInfoItems) {
		int top = scrolled.getOrigin().y;
		int bottom = top + scrolled.getParent().getBounds().height;
		for (Control control : progressInfoItems) {
			ProgressInfoItem item = (ProgressInfoItem) control;
			item.setDisplayed(top, bottom);
		}
	}

	/**
	 * Update the minimum size for scrolled composite.
	 */
	private void updateSize() {
		Point size = control.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		size.x += IDialogConstants.HORIZONTAL_SPACING;
		size.y += IDialogConstants.VERTICAL_SPACING;
		scrolled.setMinSize(size);
	}

	public ProgressInfoItem[] getProgressInfoItems() {
		Control[] children = control.getChildren();
		ProgressInfoItem[] progressInfoItems = new ProgressInfoItem[children.length];
		System.arraycopy(children, 0, progressInfoItems, 0, children.length);
		return progressInfoItems;
	}

	/**
	 * @return Returns the maximal number of displayed items.
	 */
	public int getMaxDisplayed() {
		return maxDisplayed;
	}

	/**
	 * @param maxDisplayed
	 *            The maximal number of displayed items.
	 */
	public void setMaxDisplayed(int maxDisplayed) {
		this.maxDisplayed = maxDisplayed;
	}

}
