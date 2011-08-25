/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ide4edu.newWizardPages;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.wizards.ClassPathDetector;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.wizards.JavaCapabilityConfigurationPage;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;
import org.eclipse.ui.ide.IDE;

/**
 * The second page of the New Java project wizard. It allows to configure the
 * build path and output location. As addition to the
 * {@link JavaCapabilityConfigurationPage}, the wizard page does an early
 * project creation (so that linked folders can be defined) and, if an existing
 * external location was specified, detects the class path.
 * 
 * <p>
 * Clients may instantiate or subclass.
 * </p>
 * 
 * @since 3.4
 */
public class PageTwo extends JavaCapabilityConfigurationPage {

	private static final String FILENAME_PROJECT = ".project"; //$NON-NLS-1$
	private static final String FILENAME_CLASSPATH = ".classpath"; //$NON-NLS-1$

	private final PageOne fFirstPage;

	private URI fCurrProjectLocation; // null if location is platform location
	private IProject fCurrProject;

	private boolean fKeepContent;

	private File fDotProjectBackup;
	private File fDotClasspathBackup;
	private Boolean fIsAutobuild;
	private HashSet<IFileStore> fOrginalFolders;

	/**
	 * Constructor for the {@link PageTwo}.
	 * 
	 * @param mainPage
	 *            the first page of the wizard
	 */
	public PageTwo(PageOne mainPage) {
		fFirstPage = mainPage;
		fCurrProjectLocation = null;
		fCurrProject = null;
		fKeepContent = false;

		fDotProjectBackup = null;
		fDotClasspathBackup = null;
		fIsAutobuild = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.jdt.ui.wizards.JavaCapabilityConfigurationPage#useNewSourcePage
	 * ()
	 */
	@Override
	protected final boolean useNewSourcePage() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.dialogs.IDialogPage#setVisible(boolean)
	 */
	@Override
	public void setVisible(boolean visible) {
		boolean isShownFirstTime = visible && fCurrProject == null;
		if (visible) {
			if (isShownFirstTime) { // entering from the first page
				createProvisonalProject();
			}
		} else {
			if (getContainer().getCurrentPage() == fFirstPage) { // leaving back
																	// to the
																	// first
																	// page
				removeProvisonalProject();
			}
		}
		super.setVisible(visible);
		if (isShownFirstTime) {
			setFocus();
		}
	}

	private boolean hasExistingContent(URI realLocation) throws CoreException {
		IFileStore file = EFS.getStore(realLocation);
		return file.fetchInfo().exists();
	}

	private IStatus changeToNewProject() {
		class UpdateRunnable implements IRunnableWithProgress {
			public IStatus infoStatus = Status.OK_STATUS;

			public void run(IProgressMonitor monitor)
					throws InvocationTargetException, InterruptedException {
				try {
					if (fIsAutobuild == null) {
						fIsAutobuild = Boolean.valueOf(CoreUtility
								.setAutoBuilding(false));
					}
					infoStatus = updateProject(monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} catch (OperationCanceledException e) {
					throw new InterruptedException();
				} finally {
					monitor.done();
				}
			}
		}
		UpdateRunnable op = new UpdateRunnable();
		try {
			getContainer().run(true, false,
					new WorkspaceModifyDelegatingOperation(op));
			return op.infoStatus;
		} catch (InvocationTargetException e) {
			final String title = NewWizardMessages.NewJavaProjectWizardPageTwo_error_title;
			final String message = NewWizardMessages.NewJavaProjectWizardPageTwo_error_message;
			ExceptionHandler.handle(e, getShell(), title, message);
		} catch (InterruptedException e) {
			// cancel pressed
		}
		return null;
	}

	private static URI getRealLocation(String projectName, URI location) {
		if (location == null) { // inside workspace
			try {
				URI rootLocation = ResourcesPlugin.getWorkspace().getRoot()
						.getLocationURI();

				location = new URI(rootLocation.getScheme(), null, Path
						.fromPortableString(rootLocation.getPath())
						.append(projectName).toString(), null);
			} catch (URISyntaxException e) {
				Assert.isTrue(false, "Can't happen"); //$NON-NLS-1$
			}
		}
		return location;
	}

	/*
	 * updates project and if necessary, creates it!
	 */
	private final IStatus updateProject(IProgressMonitor monitor)
			throws CoreException, InterruptedException {
		IStatus result = StatusInfo.OK_STATUS;
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		try {
			monitor.beginTask(
					NewWizardMessages.NewJavaProjectWizardPageTwo_operation_initialize,
					7);
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}

			String projectName = fFirstPage.getProjectName();

			fCurrProject = ResourcesPlugin.getWorkspace().getRoot()
					.getProject(projectName);
			fCurrProjectLocation = fFirstPage.getProjectLocationURI();

			URI realLocation = getRealLocation(projectName,
					fCurrProjectLocation);
			fKeepContent = hasExistingContent(realLocation);

			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}

			if (fKeepContent) {
				rememberExistingFiles(realLocation);
				rememberExisitingFolders(realLocation);
			}

			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}

			try {
				createProject(fCurrProject, fCurrProjectLocation,
						new SubProgressMonitor(monitor, 2));
			} catch (CoreException e) {
				if (e.getStatus().getCode() == IResourceStatus.FAILED_READ_METADATA) {
					result = new StatusInfo(
							IStatus.INFO,
							Messages.format(
									NewWizardMessages.NewJavaProjectWizardPageTwo_DeleteCorruptProjectFile_message,
									e.getLocalizedMessage()));

					deleteProjectFile(realLocation);
					if (fCurrProject.exists())
						fCurrProject.delete(true, null);

					createProject(fCurrProject, fCurrProjectLocation, null);
				} else {
					throw e;
				}
			}

			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}

			initializeBuildPath(JavaCore.create(fCurrProject),
					new SubProgressMonitor(monitor, 2));
			configureJavaProject(new SubProgressMonitor(monitor, 3)); // create
																		// the
																		// Java
																		// project
																		// to
																		// allow
																		// the
																		// use
																		// of
																		// the
																		// new
																		// source
																		// folder
																		// page
		} finally {
			monitor.done();
		}
		return result;
	}

	/**
	 * Evaluates the new build path and output folder according to the settings
	 * on the first page. The resulting build path is set by calling
	 * {@link #init(IJavaProject, IPath, IClasspathEntry[], boolean)}. Clients
	 * can override this method.
	 * 
	 * @param javaProject
	 *            the new project which is already created when this method is
	 *            called.
	 * @param monitor
	 *            the progress monitor
	 * @throws CoreException
	 *             thrown when initializing the build path failed
	 */
	protected void initializeBuildPath(IJavaProject javaProject,
			IProgressMonitor monitor) throws CoreException {
		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}
		monitor.beginTask(
				NewWizardMessages.NewJavaProjectWizardPageTwo_monitor_init_build_path,
				2);

		try {
			IClasspathEntry[] entries = null;
			IPath outputLocation = null;
			IProject project = javaProject.getProject();

			if (fKeepContent) {
				if (!project.getFile(FILENAME_CLASSPATH).exists()) {
					final ClassPathDetector detector = new ClassPathDetector(
							fCurrProject, new SubProgressMonitor(monitor, 2));
					entries = detector.getClasspath();
					outputLocation = detector.getOutputLocation();
					if (entries.length == 0)
						entries = null;
				} else {
					monitor.worked(2);
				}
			} else {
				List<IClasspathEntry> cpEntries = new ArrayList<IClasspathEntry>();
				IWorkspaceRoot root = project.getWorkspace().getRoot();

				IClasspathEntry[] sourceClasspathEntries = fFirstPage
						.getSourceClasspathEntries();
				for (int i = 0; i < sourceClasspathEntries.length; i++) {
					IPath path = sourceClasspathEntries[i].getPath();
					if (path.segmentCount() > 1) {
						IFolder folder = root.getFolder(path);
						CoreUtility.createFolder(folder, true, true,
								new SubProgressMonitor(monitor, 1));
					}
					cpEntries.add(sourceClasspathEntries[i]);
				}

				cpEntries.addAll(Arrays.asList(fFirstPage
						.getDefaultClasspathEntries()));

				entries = cpEntries.toArray(new IClasspathEntry[cpEntries
						.size()]);

				outputLocation = fFirstPage.getOutputLocation();
				if (outputLocation.segmentCount() > 1) {
					IFolder folder = root.getFolder(outputLocation);
					CoreUtility.createDerivedFolder(folder, true, true,
							new SubProgressMonitor(monitor, 1));
				}
			}
			if (monitor.isCanceled()) {
				throw new OperationCanceledException();
			}

			init(javaProject, outputLocation, entries, false);
		} finally {
			monitor.done();
		}
	}

	private void deleteProjectFile(URI projectLocation) throws CoreException {
		IFileStore file = EFS.getStore(projectLocation);
		if (file.fetchInfo().exists()) {
			IFileStore projectFile = file.getChild(FILENAME_PROJECT);
			if (projectFile.fetchInfo().exists()) {
				projectFile.delete(EFS.NONE, null);
			}
		}
	}

	@SuppressWarnings("restriction")
	private void rememberExisitingFolders(URI projectLocation) {
		fOrginalFolders = new HashSet<IFileStore>();

		try {
			IFileStore[] children = EFS.getStore(projectLocation).childStores(
					EFS.NONE, null);
			for (int i = 0; i < children.length; i++) {
				IFileStore child = children[i];
				IFileInfo info = child.fetchInfo();
				if (info.isDirectory() && info.exists()
						&& !fOrginalFolders.contains(child.getName())) {
					fOrginalFolders.add(child);
				}
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}

	private void restoreExistingFolders(URI projectLocation) {
		try {
			IFileStore[] children = EFS.getStore(projectLocation).childStores(
					EFS.NONE, null);
			for (int i = 0; i < children.length; i++) {
				IFileStore child = children[i];
				IFileInfo info = child.fetchInfo();
				if (info.isDirectory() && info.exists()
						&& !fOrginalFolders.contains(child)) {
					child.delete(EFS.NONE, null);
					fOrginalFolders.remove(child);
				}
			}

			for (Iterator<IFileStore> iterator = fOrginalFolders.iterator(); iterator
					.hasNext();) {
				IFileStore deleted = iterator.next();
				deleted.mkdir(EFS.NONE, null);
			}
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
	}

	private void rememberExistingFiles(URI projectLocation)
			throws CoreException {
		fDotProjectBackup = null;
		fDotClasspathBackup = null;

		IFileStore file = EFS.getStore(projectLocation);
		if (file.fetchInfo().exists()) {
			IFileStore projectFile = file.getChild(FILENAME_PROJECT);
			if (projectFile.fetchInfo().exists()) {
				fDotProjectBackup = createBackup(projectFile, "project-desc"); //$NON-NLS-1$
			}
			IFileStore classpathFile = file.getChild(FILENAME_CLASSPATH);
			if (classpathFile.fetchInfo().exists()) {
				fDotClasspathBackup = createBackup(classpathFile,
						"classpath-desc"); //$NON-NLS-1$
			}
		}
	}

	private void restoreExistingFiles(URI projectLocation,
			IProgressMonitor monitor) throws CoreException {
		int ticks = ((fDotProjectBackup != null ? 1 : 0) + (fDotClasspathBackup != null ? 1
				: 0)) * 2;
		monitor.beginTask("", ticks); //$NON-NLS-1$
		try {
			IFileStore projectFile = EFS.getStore(projectLocation).getChild(
					FILENAME_PROJECT);
			projectFile.delete(EFS.NONE, new SubProgressMonitor(monitor, 1));
			if (fDotProjectBackup != null) {
				copyFile(fDotProjectBackup, projectFile,
						new SubProgressMonitor(monitor, 1));
			}
		} catch (IOException e) {
			IStatus status = new Status(
					IStatus.ERROR,
					JavaUI.ID_PLUGIN,
					IStatus.ERROR,
					NewWizardMessages.NewJavaProjectWizardPageTwo_problem_restore_project,
					e);
			throw new CoreException(status);
		}
		try {
			IFileStore classpathFile = EFS.getStore(projectLocation).getChild(
					FILENAME_CLASSPATH);
			classpathFile.delete(EFS.NONE, new SubProgressMonitor(monitor, 1));
			if (fDotClasspathBackup != null) {
				copyFile(fDotClasspathBackup, classpathFile,
						new SubProgressMonitor(monitor, 1));
			}
		} catch (IOException e) {
			IStatus status = new Status(
					IStatus.ERROR,
					JavaUI.ID_PLUGIN,
					IStatus.ERROR,
					NewWizardMessages.NewJavaProjectWizardPageTwo_problem_restore_classpath,
					e);
			throw new CoreException(status);
		}
	}

	private File createBackup(IFileStore source, String name)
			throws CoreException {
		try {
			File bak = File.createTempFile("eclipse-" + name, ".bak"); //$NON-NLS-1$//$NON-NLS-2$
			copyFile(source, bak);
			return bak;
		} catch (IOException e) {
			IStatus status = new Status(
					IStatus.ERROR,
					JavaUI.ID_PLUGIN,
					IStatus.ERROR,
					Messages.format(
							NewWizardMessages.NewJavaProjectWizardPageTwo_problem_backup,
							name), e);
			throw new CoreException(status);
		}
	}

	private void copyFile(IFileStore source, File target) throws IOException,
			CoreException {
		InputStream is = source.openInputStream(EFS.NONE, null);
		FileOutputStream os = new FileOutputStream(target);
		copyFile(is, os);
	}

	private void copyFile(File source, IFileStore target,
			IProgressMonitor monitor) throws IOException, CoreException {
		FileInputStream is = new FileInputStream(source);
		OutputStream os = target.openOutputStream(EFS.NONE, monitor);
		copyFile(is, os);
	}

	private void copyFile(InputStream is, OutputStream os) throws IOException {
		try {
			byte[] buffer = new byte[8192];
			while (true) {
				int bytesRead = is.read(buffer);
				if (bytesRead == -1)
					break;

				os.write(buffer, 0, bytesRead);
			}
		} finally {
			try {
				is.close();
			} finally {
				os.close();
			}
		}
	}

	private InputStream textContentStream() {
		String contents = "Space for text content";
		return new ByteArrayInputStream(contents.getBytes());
	}

	private InputStream classContentStream(String className)
			throws CoreException {

		StringBuffer buf = new StringBuffer();
		final String lineDelim = "\n"; // OK, since content is formatted afterwards //$NON-NLS-1$
		buf.append("public class " + className + "{"); //$NON-NLS-1$
		buf.append(lineDelim);
		buf.append("public static void main("); //$NON-NLS-1$			
		buf.append("String[] args) {"); //$NON-NLS-1$
		buf.append(lineDelim);
		buf.append("}"); //$NON-NLS-1$
		buf.append(lineDelim);
		buf.append("}"); //$NON-NLS-1$
		String contents = buf.toString();
		return new ByteArrayInputStream(contents.getBytes());
	}

	private void throwCoreException(String message) throws CoreException {
		IStatus status = new Status(IStatus.ERROR,
				"org.eclipse.ide4edu.javaassignment", IStatus.OK, message, null);
		throw new CoreException(status);
	}
	
	
	private InputStream TAContactContentStream()
	{
		StringBuffer buf = new StringBuffer();
		final String lineDelim = "\n"; // OK, since content is formatted afterwards //$NON-NLS-1$
		buf.append("TAname@irc.freenode.net/#eclipse-TAChat"); 
		String contents = buf.toString();
		return new ByteArrayInputStream(contents.getBytes());
	}
	

	public void createAllMarkedResources() {
		try {
			if (fFirstPage.fResourcesGroup.isAnswerSelected())
				createResourceFile("Answer.java", classContentStream("Answer"));
			if (fFirstPage.fResourcesGroup.isQuestionSelected())
				createResourceFile("Question.txt", textContentStream());
			if (fFirstPage.fResourcesGroup.isTAContactSelected())
				createResourceFile("TAContact.txt", TAContactContentStream());
			if (fFirstPage.fResourcesGroup.isCodeSamplesSelected())
				createResourceFile("CodeSamples.java",
						classContentStream("CodeSamples"));

		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void openResource(final IFile resource) {
		final IWorkbenchPage activePage = JavaPlugin.getActivePage();
		if (activePage != null) {
			final Display display = getShell().getDisplay();
			if (display != null) {
				display.asyncExec(new Runnable() {
					public void run() {
						try {
							IDE.openEditor(activePage, resource, true);
						} catch (PartInitException e) {
							JavaPlugin.log(e);
						}
					}
				});
			}
		}
	}

	public void createResourceFile(String fileName, InputStream stream)
			throws CoreException {		
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		String containerName = fFirstPage.getProjectName() + "/src/";
		IResource resource = root.findMember(new Path(containerName));

		if (!resource.exists() || !(resource instanceof IContainer)) {
			throwCoreException("Container \"" + containerName
					+ "\" does not exist.");
		}
		IContainer container = (IContainer) resource;
		final IFile file = container.getFile(new Path(fileName));
		try {
			if (file.exists()) {
				file.setContents(stream, true, true, null);
			} else {
				file.create(stream, true, null);
				openResource(file);
			}
			stream.close();
		} catch (IOException e) {
		}
	}

	/**
	 * Called from the wizard on finish.
	 * 
	 * @param monitor
	 *            the progress monitor
	 * @throws CoreException
	 *             thrown when the project creation or configuration failed
	 * @throws InterruptedException
	 *             thrown when the user cancelled the project creation
	 */
	public void performFinish(IProgressMonitor monitor) throws CoreException,
			InterruptedException {
		try {
			monitor.beginTask(
					NewWizardMessages.NewJavaProjectWizardPageTwo_operation_create,
					3);
			if (fCurrProject == null) {
				updateProject(new SubProgressMonitor(monitor, 1));
			}
			String newProjectCompliance = fKeepContent ? null : fFirstPage
					.getCompilerCompliance();
			configureJavaProject(newProjectCompliance, new SubProgressMonitor(
					monitor, 2));
			/*setting additional Java Assignment nature*/
			IProject project= getJavaProject().getProject();
			addJavaAssignmentNature(project, new SubProgressMonitor(monitor, 1));			
			createAllMarkedResources();

		} finally {
			monitor.done();
			fCurrProject = null;
			if (fIsAutobuild != null) {
				CoreUtility.setAutoBuilding(fIsAutobuild.booleanValue());
				fIsAutobuild = null;
			}
		}
	}

	
	public static void addJavaAssignmentNature(IProject project, IProgressMonitor monitor) throws CoreException {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}	
		if (!project.hasNature("org.eclipse.ide4edu.javaassignment.asgmtNature")) {
			IProjectDescription description = project.getDescription();
			String[] prevNatures= description.getNatureIds();
			String[] newNatures= new String[prevNatures.length + 1];
			System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
			newNatures[prevNatures.length]= "org.eclipse.ide4edu.javaassignment.asgmtNature";
			description.setNatureIds(newNatures);
			project.setDescription(description, monitor);			
		} else {
			if (monitor != null) {
				monitor.worked(1);
			}
		}
	}
	
	
	/**
	 * Creates the provisional project on which the wizard is working on. The
	 * provisional project is typically created when the page is entered the
	 * first time. The early project creation is required to configure linked
	 * folders.
	 * 
	 * @return the provisional project
	 */
	protected IProject createProvisonalProject() {
		IStatus status = changeToNewProject();
		if (status != null && !status.isOK()) {
			ErrorDialog.openError(getShell(),
					NewWizardMessages.NewJavaProjectWizardPageTwo_error_title,
					null, status);
		}
		return fCurrProject;
	}

	/**
	 * Removes the provisional project. The provisional project is typically
	 * removed when the user cancels the wizard or goes back to the first page.
	 */
	protected void removeProvisonalProject() {
		if (!fCurrProject.exists()) {
			fCurrProject = null;
			return;
		}

		IRunnableWithProgress op = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor)
					throws InvocationTargetException, InterruptedException {
				doRemoveProject(monitor);
			}
		};

		try {
			getContainer().run(true, true,
					new WorkspaceModifyDelegatingOperation(op));
		} catch (InvocationTargetException e) {
			final String title = NewWizardMessages.NewJavaProjectWizardPageTwo_error_remove_title;
			final String message = NewWizardMessages.NewJavaProjectWizardPageTwo_error_remove_message;
			ExceptionHandler.handle(e, getShell(), title, message);
		} catch (InterruptedException e) {
			// cancel pressed
		}
	}

	private final void doRemoveProject(IProgressMonitor monitor)
			throws InvocationTargetException {
		final boolean noProgressMonitor = (fCurrProjectLocation == null); // inside
																			// workspace
		if (monitor == null || noProgressMonitor) {
			monitor = new NullProgressMonitor();
		}
		monitor.beginTask(
				NewWizardMessages.NewJavaProjectWizardPageTwo_operation_remove,
				3);
		try {
			try {
				URI projLoc = fCurrProject.getLocationURI();

				boolean removeContent = !fKeepContent
						&& fCurrProject
								.isSynchronized(IResource.DEPTH_INFINITE);
				if (!removeContent) {
					restoreExistingFolders(projLoc);
				}
				fCurrProject.delete(removeContent, false,
						new SubProgressMonitor(monitor, 2));

				restoreExistingFiles(projLoc,
						new SubProgressMonitor(monitor, 1));
			} finally {
				CoreUtility.setAutoBuilding(fIsAutobuild.booleanValue()); // fIsAutobuild
																			// must
																			// be
																			// set
				fIsAutobuild = null;
			}
		} catch (CoreException e) {
			throw new InvocationTargetException(e);
		} finally {
			monitor.done();
			fCurrProject = null;
			fKeepContent = false;
		}
	}

	/**
	 * Called from the wizard on cancel.
	 */
	public void performCancel() {
		if (fCurrProject != null) {
			removeProvisonalProject();
		}
	}
}
