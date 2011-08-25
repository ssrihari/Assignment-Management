package org.eclipse.ide4edu.chat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class OpenChatHandler extends AbstractHandler {

	private IWorkbenchWindow window;

	public OpenChatHandler() {
		window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
	}

	public Object execute(ExecutionEvent event) throws ExecutionException {

		IResource res = extractSelection(window.getSelectionService()
				.getSelection());
		IProject curProj = res.getProject();
		IResource TAContactFile = curProj.findMember("/src/TAContact.txt");
		if (TAContactFile != null) {
			System.out.print("File exists!");
			String path = TAContactFile.getLocation().toString();
			try {

				FileReader input = new FileReader(path);
				BufferedReader bufRead = new BufferedReader(input);
				String text = "";
				text += bufRead.readLine();
				bufRead.close();
				System.out.println("TAC contents:" + text);
				// Call apt wiz with credentials

				if (text.contains("irc"))
					IRCWizOpen(text);
				else if (text.contains("gmail"))
					GChatOpen();
				else
					System.out.println("Not enough info");

			} catch (ArrayIndexOutOfBoundsException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		else
			System.out.print("No TAC File!");

		
		return null;
	}

	IResource extractSelection(ISelection sel) {
		if (!(sel instanceof IStructuredSelection))
			return null;
		IStructuredSelection ss = (IStructuredSelection) sel;
		Object element = ss.getFirstElement();
		if (element instanceof IResource)
			return (IResource) element;
		if (!(element instanceof IAdaptable))
			return null;
		IAdaptable adaptable = (IAdaptable) element;
		Object adapter = adaptable.getAdapter(IResource.class);
		return (IResource) adapter;
	}

	private void GChatOpen() {

		XMPPConnect wizard = new XMPPConnect();//(text.substring(text.indexOf('@')),0);
		wizard.init(PlatformUI.getWorkbench(), getSelection());
		WizardDialog dialog = new WizardDialog(window.getShell(), wizard);
		PixelConverter converter = new PixelConverter(
				JFaceResources.getDialogFont());
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), 0);
		dialog.create();
		int result = dialog.open();
	
	}

	private void IRCWizOpen(String text) {
		
		IRCConnect wizard = new IRCConnect(text.substring(text.indexOf('@')),0);
		wizard.init(PlatformUI.getWorkbench(), getSelection());
		WizardDialog dialog = new WizardDialog(window.getShell(), wizard);
		PixelConverter converter = new PixelConverter(
				JFaceResources.getDialogFont());
		dialog.setMinimumPageSize(converter.convertWidthInCharsToPixels(70), 0);
		dialog.create();
		int result = dialog.open();

	}

	protected IStructuredSelection getSelection() {
		ISelection selection = window.getSelectionService().getSelection();
		if (selection instanceof IStructuredSelection) {
			return (IStructuredSelection) selection;
		}
		return StructuredSelection.EMPTY;
	}

}
