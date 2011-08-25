/*******************************************************************************
 * Copyright (c) 2010 Cory Matheson and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Cory Matheson - Initial API and implementation.
 *     Eclipse Foundation - Some modification/clean up.
 *******************************************************************************/
package org.eclipse.ide4edu.javaassignment;

import org.eclipse.ide4edu.javalite.ui.view.LiteOpenWizardAction;
import org.eclipse.ui.INewWizard;

public abstract class OpenNewLiteJavaAssignmentWizardAction extends LiteOpenWizardAction {

	public OpenNewLiteJavaAssignmentWizardAction() {		
		setText("New Java Assignment"); 
		setDescription("Create a new Java assignment."); 
		setToolTipText("Create a new Java assignment.");
		
		setImageDescriptor(Activator.getImageDescriptor("newjasgmt_wiz.gif"));

		// TODO - Fix - Need our own help context
		//PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.OPEN_PROJECT_WIZARD_ACTION);
	}

	
	INewWizard createWizard() {
		return new JavaLiteAssignmentWizard();
	}	
}
