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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.preference.IPreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.internal.WorkbenchMessages;
import org.eclipse.ui.internal.misc.StatusUtil;
import org.eclipse.ui.internal.preferences.WorkbenchPreferenceExtensionNode;
import org.eclipse.ui.statushandlers.StatusManager;

/**
 * Property page node allows us to achieve presence in the property page dialog
 * without loading the page itself, thus loading the contributing plugin.
 * Only when the user selects the page will it be loaded.
 */
public class PropertyPageNode extends WorkbenchPreferenceExtensionNode {
    private RegistryPageContributor contributor;

	private IPreferencePage page;

    private Image icon;

    private Object element;

    /**
     * Create a new instance of the receiver.
     * @param contributor
     * @param element
     */
    public PropertyPageNode(RegistryPageContributor contributor,
            Object element) {
        super(contributor.getPageId(), contributor.getConfigurationElement());
        this.contributor = contributor;
        this.element = element;
    }

    /**
     * Creates the preference page this node stands for. If the page is null,
     * it will be created by loading the class. If loading fails,
     * empty filler page will be created instead.
     */
    @Override
	public void createPage() {
        try {
            page = contributor.createPage(element);
        } catch (CoreException e) {
            // Just inform the user about the error. The details are
            // written to the log by now.
            IStatus errStatus = StatusUtil.newStatus(e.getStatus(), WorkbenchMessages.get().PropertyPageNode_errorMessage);
            StatusManager.getManager().handle(errStatus, StatusManager.SHOW);
            page = new EmptyPropertyPage();
        }
        setPage(page);
    }

    @Override
	public void disposeResources() {

        if (page != null) {
            page.dispose();
            page = null;
        }
        if (icon != null) {
            icon.dispose();
            icon = null;
        }
    }

    /**
     * Returns page icon, if defined.
     */
    @Override
	public Image getLabelImage() {
        if (icon == null) {
            ImageDescriptor desc = contributor.getPageIcon();
            if (desc != null) {
                icon = desc.createImage();
            }
        }
        return icon;
    }

    /**
     * Returns page label as defined in the registry.
     */
    @Override
	public String getLabelText() {
        return contributor.getPageName();
    }

}
