/*******************************************************************************
 * Copyright (c) 2010 The Eclipse Foundation and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ide4edu.javaassignment;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.ide4edu.chat.IRCConnect;
import org.eclipse.ide4edu.javalite.constructors.NewJavaProjectConstructor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;

public class JavaLiteAssignmentWizard extends Wizard implements INewWizard {

	NewJavaProjectConstructor constructor = new NewJavaProjectConstructor();
	private IWorkbench workbench;

	public JavaLiteAssignmentWizard() {
		Activator.getDefault();
		// TODO Find an icon to call our own.
		setDefaultPageImageDescriptor(Activator.getImageDescriptor("icons/wizban/newjprj_wiz.png"));
		//setDialogSettings(JavaPlugin.getDefault().getDialogSettings());
		setWindowTitle("Create a new Java Assignment"); 
	}

	public void addPages() {
		WizardPage page = new WizardPage("Project Info") {

			public void createControl(Composite parent) {
				final Composite composite= new Composite(parent, SWT.NULL);
				composite.setLayout(new GridLayout(2, false));
				composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

				Label label = new Label(composite, SWT.NONE);
				label.setText("Assignment name:");
				
				final Text text = new Text(composite, SWT.BORDER);
				GridData layoutData = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
				layoutData.grabExcessHorizontalSpace = true;
				text.setLayoutData(layoutData);
				
				text.addModifyListener(new ModifyListener() {					
					public void modifyText(ModifyEvent e) {
						constructor.setProjectName(text.getText());
						updatePageState();
					}
				});
				setTitle("Basic Project Information");
				setControl(composite);
			}
			
			private void updatePageState() {
				setErrorMessage(getConstructorErrorMessage());
				updateWizardState();
			}
		};
		addPage(page);
	}		

	private String getConstructorErrorMessage() {
		IStatus status = constructor.getStatus();
		if (status.isOK()) return null;
		return status.getMessage();
	}
	
	private void updateWizardState() {
		getContainer().updateButtons();
	}
	
	public boolean canFinish() {
		return constructor.getStatus().isOK();
	}
	

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.IWizard#performFinish()
	 */
	public boolean performFinish() {
		try {
			IJavaProject project = constructor.construct(new NullProgressMonitor());
			// TODO What if the constructor fails?
			BasicNewResourceWizard.selectAndReveal(project.getProject(), workbench.getActiveWorkbenchWindow());
			
			IRCConnect z= new IRCConnect();
			z.performFinish();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		return true;
	}

	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;		
	}
}