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
package org.eclipse.ui.internal.browser;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.dynamichelpers.ExtensionTracker;
import org.eclipse.core.runtime.dynamichelpers.IExtensionChangeHandler;
import org.eclipse.core.runtime.dynamichelpers.IExtensionTracker;
import org.eclipse.rap.rwt.SingletonUtil;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.AbstractWorkbenchBrowserSupport;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants;

/**
 * Implements the support interface and delegates the calls to the active
 * support if contributed via the extension point, or the default support
 * otherwise.
 *
 * @since 3.1
 */
public class WorkbenchBrowserSupport extends AbstractWorkbenchBrowserSupport {

// RAP [rh] replaced application-scoped instance field with session-singleton
//		private static WorkbenchBrowserSupport instance;

	private static final class InstanceHolder {
		public static WorkbenchBrowserSupport getInstance() {
			return SingletonUtil.getSessionInstance(WorkbenchBrowserSupport.class);
		}
	}
// RAP [rh] end RAP specific

	private IWorkbenchBrowserSupport activeSupport;

	private boolean initialized;

    private String desiredBrowserSupportId;

	private IExtensionChangeHandler handler = new IExtensionChangeHandler() {

        @Override
		public void addExtension(IExtensionTracker tracker,IExtension extension) {
            //Do nothing
        }

        @Override
		public void removeExtension(IExtension source, Object[] objects) {
			for (Object object : objects) {
				if (object == activeSupport) {
					dispose();
					// remove ourselves - we'll be added again in initalize if
					// needed
					PlatformUI.getWorkbench().getExtensionTracker()
							.unregisterHandler(handler);
				}
			}
		}
	};

	/**
	 * Cannot be instantiated from outside.
	 */
	private WorkbenchBrowserSupport() {
	}

	/**
	 * Returns the shared instance.
	 *
	 * @return shared instance
	 */
	public static IWorkbenchBrowserSupport getInstance() {
// RAP [rh] replace application-scoped singleton with session-singleton
//		if (instance == null) {
//			instance = new WorkbenchBrowserSupport();
//		}
//		return instance;
		return InstanceHolder.getInstance();
	}

	@Override
	public IWebBrowser createBrowser(int style, String browserId, String name,
			String tooltip) throws PartInitException {
		return getActiveSupport()
				.createBrowser(style, browserId, name, tooltip);
	}

	@Override
	public IWebBrowser createBrowser(String browserId) throws PartInitException {
		return getActiveSupport().createBrowser(browserId);
	}

	@Override
	public boolean isInternalWebBrowserAvailable() {
		return getActiveSupport().isInternalWebBrowserAvailable();
	}

	private IWorkbenchBrowserSupport getActiveSupport() {
		if (initialized == false) {
			loadActiveSupport();
		}
		// ensure we always have an active instance
		if (activeSupport == null) {
			activeSupport = new DefaultWorkbenchBrowserSupport();
		}
		return activeSupport;
	}

    /**
     * Answers whether the system has a non-default browser installed.
     *
     * @return whether the system has a non-default browser installed
     */
    public boolean hasNonDefaultBrowser() {
        return !(getActiveSupport() instanceof DefaultWorkbenchBrowserSupport);
    }

	private void loadActiveSupport() {
		BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
			@Override
			public void run() {
// RAP [rh]: ep namespace differs from bundle id (bug #249708)
//              IConfigurationElement[] elements = Platform
//                      .getExtensionRegistry().getConfigurationElementsFor(
//                              PlatformUI.PLUGIN_ID,
//                              IWorkbenchRegistryConstants.PL_BROWSER_SUPPORT);
				IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(
                                PlatformUI.PLUGIN_EXTENSION_NAME_SPACE,
                                IWorkbenchRegistryConstants.PL_BROWSER_SUPPORT);
				IConfigurationElement elementToUse = null;

                if (desiredBrowserSupportId != null) {
					elementToUse = findDesiredElement(elements);
				} else {
					elementToUse = getElementToUse(elements);
				}
				if (elementToUse != null) {
					initialized = initializePluggableBrowserSupport(elementToUse);
				}
			}

            /**
             * Search for the element whose extension has ID equal to that of
             * the field desiredBrowserSupport.
             *
             * @param elements
             *            the elements to search
             * @return the element or <code>null</code>
             */
            private IConfigurationElement findDesiredElement(IConfigurationElement [] elements) {
                for (IConfigurationElement element : elements) {
                    if (desiredBrowserSupportId.equals(element.getDeclaringExtension().getUniqueIdentifier())) {
						return element;
					}
                }
                return null;
            }

            private IExtensionPoint getExtensionPoint() {
// RAP [rh]: ep namespace differs from bundle id (bug #249708)
//              return Platform.getExtensionRegistry()
//						.getExtensionPoint(PlatformUI.PLUGIN_ID, IWorkbenchRegistryConstants.PL_BROWSER_SUPPORT);
                return Platform.getExtensionRegistry()
						.getExtensionPoint(PlatformUI.PLUGIN_EXTENSION_NAME_SPACE, IWorkbenchRegistryConstants.PL_BROWSER_SUPPORT);
            }

			private IConfigurationElement getElementToUse(
					IConfigurationElement[] elements) {
				if (elements.length == 0) {
					return null;
				}
				IConfigurationElement defaultElement = null;
				IConfigurationElement choice = null;
				// find the first default element and
				// the first non-default element. If non-default
				// is found, pick it. Otherwise, use default.
				for (IConfigurationElement element : elements) {
					if (element.getName().equals(IWorkbenchRegistryConstants.TAG_SUPPORT)) {
						String def = element.getAttribute(IWorkbenchRegistryConstants.ATT_DEFAULT);
						if (def != null && Boolean.valueOf(def).booleanValue()) {
							if (defaultElement == null) {
								defaultElement = element;
							}
						} else {
							// non-default
							if (choice == null) {
								choice = element;
							}
						}
					}
				}
				if (choice == null) {
					choice = defaultElement;
				}
				return choice;
			}

			private boolean initializePluggableBrowserSupport(
					IConfigurationElement element) {
				// Instantiate the browser support
				try {
					activeSupport = (AbstractWorkbenchBrowserSupport) WorkbenchPlugin
							.createExtension(element, IWorkbenchRegistryConstants.ATT_CLASS);
					// start listening for removals
					IExtensionTracker extensionTracker = PlatformUI.getWorkbench().getExtensionTracker();
                    extensionTracker.registerHandler(handler, ExtensionTracker
                            .createExtensionPointFilter(getExtensionPoint()));
					// register the new browser support for removal
					// notification
					extensionTracker
							.registerObject(element.getDeclaringExtension(),
									activeSupport, IExtensionTracker.REF_WEAK);
					return true;
				} catch (CoreException e) {
					WorkbenchPlugin
							.log("Unable to instantiate browser support" + e.getStatus(), e);//$NON-NLS-1$
				}
				return false;
			}

		});
	}

    /**
     * For debug purposes only.
     *
     * @param desiredBrowserSupportId the desired browser system id
     */
    public void setDesiredBrowserSupportId(String desiredBrowserSupportId) {
        dispose(); // prep for a new help system
        this.desiredBrowserSupportId = desiredBrowserSupportId;
    }

    /**
     * Dispose of the active support.
     */
    protected void dispose() {
        activeSupport = null;
        initialized = false;
    }
}
