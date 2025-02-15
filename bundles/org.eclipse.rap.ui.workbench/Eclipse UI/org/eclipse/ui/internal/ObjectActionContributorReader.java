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
package org.eclipse.ui.internal;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants;
import org.eclipse.ui.internal.registry.RegistryReader;

/**
 * This reader loads the popup menu manager with all the
 * popup menu contributors found in the workbench registry.
 */
public class ObjectActionContributorReader extends RegistryReader {

    private ObjectActionContributorManager manager;

    /**
     * Creates popup menu contributor from this element.
     */
    protected void processObjectContribution(IConfigurationElement element) {
        String objectClassName = element.getAttribute(IWorkbenchRegistryConstants.ATT_OBJECTCLASS);
        if (objectClassName == null) {
            logMissingAttribute(element, IWorkbenchRegistryConstants.ATT_OBJECTCLASS);
            return;
        }

        IObjectContributor contributor = new ObjectActionContributor(element);
        manager.registerContributor(contributor, objectClassName);
    }

    /**
     * Implements abstract method to handle configuration elements.
     */
    @Override
	protected boolean readElement(IConfigurationElement element) {
        String tagName = element.getName();
        if (tagName.equals(IWorkbenchRegistryConstants.TAG_OBJECT_CONTRIBUTION)) {
            processObjectContribution(element);
            return true;
        }
        if (tagName.equals(IWorkbenchRegistryConstants.TAG_VIEWER_CONTRIBUTION)) {
            return true;
        }

        return false;
    }

    /**
     * Reads the registry and registers popup menu contributors
     * found there.
     *
     * @param mng the manager to read into
     */
    public void readPopupContributors(ObjectActionContributorManager mng) {
        setManager(mng);
        IExtensionRegistry registry = Platform.getExtensionRegistry();
        // RAP [bm]: 
//        readRegistry(registry, PlatformUI.PLUGIN_ID,
//                IWorkbenchRegistryConstants.PL_POPUP_MENU);
        readRegistry(registry, PlatformUI.PLUGIN_EXTENSION_NAME_SPACE,
              IWorkbenchRegistryConstants.PL_POPUP_MENU);
        // RAPEND: [bm] 
    }

    /**
     * Set the manager to read into.
     *
     * @param mng the manager
     */
    public void setManager(ObjectActionContributorManager mng) {
        manager = mng;
    }
}
