package org.eclipse.ide4edu.javaassignment;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.ui.wizards.JavaCapabilityConfigurationPage;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

public class AssignmentNature implements IProjectNature {

	@Override
	public void configure() throws CoreException {
		// TODO Auto-generated method stub		
		IWorkbenchWindow window= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		//IActionBars bars = window.
		
	}

	@Override
	public void deconfigure() throws CoreException {
		// TODO Auto-generated method stub

	}

	@Override
	public IProject getProject() {
		// TODO Auto-generated method stub
		return null;		
	}

	@Override
	public void setProject(IProject project) {
		// TODO Auto-generated method stub

	}

}
