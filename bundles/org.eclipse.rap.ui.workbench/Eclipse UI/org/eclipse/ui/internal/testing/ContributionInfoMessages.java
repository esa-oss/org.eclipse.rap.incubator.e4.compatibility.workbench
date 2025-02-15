/*******************************************************************************
 * Copyright (c) 2010, 2015 IBM Corporation and others.
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
 ******************************************************************************/
package org.eclipse.ui.internal.testing;

import org.eclipse.rap.rwt.RWT;

/**
 * @since 3.6
 *
 */
//RAP [if]: need session aware NLS
//public class ContributionInfoMessages extends NLS {
public class ContributionInfoMessages {
	private static final String BUNDLE_NAME = "org.eclipse.ui.internal.testing.messages";//$NON-NLS-1$


//	public static String ContributionInfo_Editor;
//	public static String ContributionInfo_View;
//	public static String ContributionInfo_ActionSet;
//	public static String ContributionInfo_Category;
//	public static String ContributionInfo_ColorDefinition;
//	public static String ContributionInfo_Wizard;
//	public static String ContributionInfo_Perspective;
//	public static String ContributionInfo_Page;
//	public static String ContributionInfo_EarlyStartupPlugin;
//	public static String ContributionInfo_Unknown;
//	public static String ContributionInfo_Job;
//	public static String ContributionInfo_TableItem;
//	public static String ContributionInfo_TreeItem;
//	public static String ContributionInfo_Window;
//	public static String ContributionInfo_LabelDecoration;
//	public static String ContributionInfo_ViewContent;
//
//	public static String ContributionInfo_ContributedBy;

	public String ContributionInfo_Editor;
	public String ContributionInfo_View;
	public String ContributionInfo_ActionSet;
	public String ContributionInfo_Category;
	public String ContributionInfo_ColorDefinition;
	public String ContributionInfo_Wizard;
	public String ContributionInfo_Perspective;
	public String ContributionInfo_Page;
	public String ContributionInfo_EarlyStartupPlugin;
	public String ContributionInfo_Unknown;
	public String ContributionInfo_Job;
	public String ContributionInfo_TableItem;
	public String ContributionInfo_TreeItem;
	public String ContributionInfo_Window;
	public String ContributionInfo_LabelDecoration;
	public String ContributionInfo_ViewContent;

	public String ContributionInfo_ContributedBy;

// RAP [if]: need session aware NLS
//		static {
//			// load message values from bundle file
//			NLS.initializeMessages(BUNDLE_NAME, ContributionInfoMessages.class);
//		}

	/**
	 * Load message values from bundle file
	 *
	 * @return localized message
	 */
	public static ContributionInfoMessages get() {
		return RWT.NLS.getISO8859_1Encoded(BUNDLE_NAME, ContributionInfoMessages.class);
	}

}
