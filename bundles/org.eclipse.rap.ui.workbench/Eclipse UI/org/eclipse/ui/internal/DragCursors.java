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
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.Display;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.ISharedImages;

/**
 * Provides the set of cursors used for drag-and-drop.
 */
public class DragCursors {
    public static final int INVALID = 0;

    public static final int LEFT = 1;

    public static final int RIGHT = 2;

    public static final int TOP = 3;

    public static final int BOTTOM = 4;

    public static final int CENTER = 5;

    public static final int OFFSCREEN = 6;

    public static final int FASTVIEW = 7;

    private static final Cursor cursors[] = new Cursor[8];

    public static int positionToDragCursor(int swtPositionConstant) {
        switch (swtPositionConstant) {
        case SWT.LEFT:
            return LEFT;
        case SWT.RIGHT:
            return RIGHT;
        case SWT.TOP:
            return TOP;
        case SWT.BOTTOM:
            return BOTTOM;
        case SWT.CENTER:
            return CENTER;
        }

        return INVALID;
    }

    /**
     * Converts a drag cursor (LEFT, RIGHT, TOP, BOTTOM, CENTER) into an SWT constant
     * (SWT.LEFT, SWT.RIGHT, SWT.TOP, SWT.BOTTOM, SWT.CENTER)
     *
     * @param dragCursorId
     * @return an SWT.* constant
     */
    public static int dragCursorToSwtConstant(int dragCursorId) {
        switch (dragCursorId) {
        case LEFT:
            return SWT.LEFT;
        case RIGHT:
            return SWT.RIGHT;
        case TOP:
            return SWT.TOP;
        case BOTTOM:
            return SWT.BOTTOM;
        case CENTER:
            return SWT.CENTER;
        }

        return SWT.DEFAULT;
    }

	/**
     * Return the cursor for a drop scenario, as identified by code. Code must be one of INVALID,
     * LEFT, RIGHT, TOP, etc. If the code is not found default to INVALID.
     *
     * @param code the code
     * @return the cursor
	 */
    public static Cursor getCursor(int code) {
        Display display = Display.getCurrent();
        if (cursors[code] == null) {
            ImageDescriptor source = null;
            ImageDescriptor mask = null;
            switch (code) {
            case LEFT:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_LEFT_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_LEFT_MASK);
                cursors[LEFT] = new Cursor(display, SWT.CURSOR_HAND);
                break;
            case RIGHT:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_RIGHT_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_RIGHT_MASK);
                cursors[RIGHT] = new Cursor(display, SWT.CURSOR_HAND);
                break;
            case TOP:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_TOP_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_TOP_MASK);
                cursors[TOP] = new Cursor(display, SWT.CURSOR_HAND);
                break;
            case BOTTOM:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_BOTTOM_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_BOTTOM_MASK);
                cursors[BOTTOM] = new Cursor(display, SWT.CURSOR_HAND);
                break;
            case CENTER:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_STACK_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_STACK_MASK);
                cursors[CENTER] = new Cursor(display, SWT.CURSOR_HAND);
                break;
            case OFFSCREEN:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_OFFSCREEN_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_OFFSCREEN_MASK);
                cursors[OFFSCREEN] = new Cursor(display, SWT.CURSOR_NO);
                break;
            case FASTVIEW:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_TOFASTVIEW_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_TOFASTVIEW_MASK);
                cursors[FASTVIEW] = new Cursor(Display.getCurrent(), SWT.CURSOR_HAND);
                break;
            default:
            case INVALID:
                source = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_INVALID_SOURCE);
                mask = WorkbenchImages
                        .getImageDescriptor(ISharedImages.IMG_OBJS_DND_INVALID_MASK);
                cursors[INVALID] = new Cursor(display,SWT.CURSOR_NO);
                break;
            }
        }
        return cursors[code];
    }

    /**
     * Disposes all drag-and-drop cursors.
     */
    public static void dispose() {
        for (int idx = 0; idx < cursors.length; idx++) {
            cursors[idx].dispose();
            cursors[idx] = null;
        }
    }
}
