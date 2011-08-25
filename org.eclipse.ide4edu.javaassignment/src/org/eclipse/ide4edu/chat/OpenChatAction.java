/*******************************************************************************
 * Copyright (c) 2010 Cory Matheson and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Cory Matheson - initial API and implementation
 *     Eclipse Foundation - Some modification to initial contribution.
 *******************************************************************************/
package org.eclipse.ide4edu.chat;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IViewActionDelegate;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * This class is an abstract superclass for the JavaLite actions that
 * provides some of the common {@link WizardDialog} behaviour.
 * <p>
 * This implementation new code that is loosely based on (inspired, influenced
 * by) the implementation of
 * {@link org.eclipse.jdt.ui.actions.AbstractOpenWizardAction}.
 */
public class OpenChatAction extends Action implements IViewActionDelegate{	
	private IWorkbenchWindow window;
	
	public OpenChatAction() {	
		this.window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		setText("Chat with TA"); 
		setDescription("Open IM or IRC to chat with TA"); 
		setToolTipText("Open Chat"); 
		
	}

	public void run() {
		INewWizard wizard = createWizard();
		wizard.init(PlatformUI.getWorkbench(), getSelection());

		WizardDialog dialog = new WizardDialog(window.getShell(), wizard);
		PixelConverter converter = new PixelConverter(JFaceResources.getDialogFont());
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), 0);
		dialog.create();
		int result = dialog.open();

		notifyResult(result == Window.OK);
	}
	
	INewWizard createWizard()
	{
		return new IRCConnect();
	}

	/**
	 * Returns the current selection if it is an {@link IStructuredSelection}. 
	 * If the current selection is not an {@link IStructuredSelection}, then
	 * an empty instance of {@link IStructuredSelection} is returned.
	 * 
	 * @return An instance of {@link IStructuredSelection}.
	 */
	protected IStructuredSelection getSelection() {
		ISelection selection = window.getSelectionService().getSelection();
		if (selection instanceof IStructuredSelection) {
			return (IStructuredSelection) selection;
		}
		return StructuredSelection.EMPTY;
	}

	@Override
	public void run(IAction action) {
		// TODO Auto-generated method stub
		INewWizard wizard = createWizard();
		wizard.init(PlatformUI.getWorkbench(), getSelection());

		WizardDialog dialog = new WizardDialog(window.getShell(), wizard);
		PixelConverter converter = new PixelConverter(JFaceResources.getDialogFont());
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), 0);
		dialog.create();
		int result = dialog.open();

		notifyResult(result == Window.OK);
		
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init(IViewPart view) {
		
		IActionBars bars=view.getViewSite().getActionBars();
		IMenuManager mgr=bars.getMenuManager();
		mgr.add(new OpenChatAction());
		
		System.out.println("view part id in action"+view.getViewSite().getId());
		// TODO Auto-generated method stub
		
	}
}
