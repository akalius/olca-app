package org.openlca.app.rcp.plugins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.IMessageManager;
import org.eclipse.ui.forms.widgets.ColumnLayout;
import org.eclipse.ui.forms.widgets.ColumnLayoutData;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.TableWrapData;
import org.eclipse.ui.forms.widgets.TableWrapLayout;
import org.openlca.app.resources.ImageType;
import org.openlca.app.util.ErrorPopup;
import org.openlca.app.util.InformationPopup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class AvailablePluginsForm {

	private static final Logger log = LoggerFactory
			.getLogger(AvailablePluginsForm.class);

	/**
	 * symbolic name to message.
	 */
	private static final HashMap<String, String> changedPlugins = new HashMap<>();

	private HashMap<String, Plugin> plugins = new HashMap<>();

	private IManagedForm mform;

	private LinkedHashMap<Composite, Plugin> pluginComps = new LinkedHashMap<>();

	private PluginsService pluginsService;

	private PluginManagerDialog pluginManagerDialog;

	public AvailablePluginsForm(PluginManagerDialog pluginManagerDialog,
			IManagedForm mform, PluginsService pluginsService) {
		this.pluginManagerDialog = pluginManagerDialog;
		this.mform = mform;
		this.pluginsService = pluginsService;
		getForm().setText("Plugins");
		getToolkit().decorateFormHeading(mform.getForm().getForm());
		TableWrapLayout layout = new TableWrapLayout();
		layout.numColumns = 1;
		getBody().setLayout(layout);
	}

	protected ScrolledForm getForm() {
		return mform.getForm();
	}

	protected Composite getBody() {
		return mform.getForm().getBody();
	}

	protected IMessageManager getMessageManager() {
		return mform.getMessageManager();
	}

	public PluginManagerDialog getPluginManagerDialog() {
		return pluginManagerDialog;
	}

	public void clearPlugins() {
		clear();
	}

	public void showPlugins(List<Plugin> plugins) {
		for (Plugin p : plugins)
			if (p.isInstallable())
				addPlugin(p);
		getForm().reflow(true);
	}

	public void addPlugin(Plugin p) {
		if (plugins.containsKey(p.getSymbolicName())) {
			log.warn("Plugin {} already listed, ignoring", p.getSymbolicName());
			return;
		}
		Composite pluginComp = getToolkit()
				.createComposite(getBody(), SWT.NONE);
		pluginComp.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
		TableWrapLayout pluginOuterLayout = new TableWrapLayout();
		pluginOuterLayout.numColumns = 2;
		pluginComp.setLayout(pluginOuterLayout);

		Label imageLabel = new Label(pluginComp, SWT.NONE);

		provideImage(pluginComp, imageLabel, p);

		Composite rightComp = getToolkit()
				.createComposite(pluginComp, SWT.NONE);
		rightComp.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));
		rightComp.setLayout(new TableWrapLayout());

		Composite headerComp = getToolkit()
				.createComposite(rightComp, SWT.NONE);
		headerComp.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB));

		ColumnLayout headerLayout = new ColumnLayout();
		headerLayout.maxNumColumns = 2;
		headerLayout.minNumColumns = 1;
		headerComp.setLayout(headerLayout);
		String nameText = p.getName();
		if (Strings.isNullOrEmpty(nameText)) {
			nameText = p.getSymbolicName();
		}
		getToolkit().createLabel(headerComp, nameText, SWT.WRAP)
				.setToolTipText(p.getSymbolicName());
		Composite buttonComposite = getToolkit().createComposite(headerComp,
				SWT.FILL);
		ColumnLayoutData buttonLayoutData = new ColumnLayoutData();
		buttonLayoutData.horizontalAlignment = ColumnLayoutData.RIGHT;
		buttonComposite.setLayoutData(buttonLayoutData);
		GridLayout buttonGrid = new GridLayout();
		buttonComposite.setLayout(buttonGrid);
		addButtons(buttonComposite, p);

		getToolkit().createLabel(
				rightComp,
				"Available version: "
						+ (Strings.isNullOrEmpty(p.getVersion()) ? "None" : p
								.getVersion()));

		String installedVersion = "Not installed";
		if (!Strings.isNullOrEmpty(p.getInstalledVersion())) {
			installedVersion = "Installed: " + p.getInstalledVersion();
		}
		getToolkit().createLabel(rightComp, installedVersion);

		getToolkit().createLabel(rightComp, p.getDescription(), SWT.WRAP);

		plugins.put(p.getSymbolicName(), p);
		pluginComps.put(pluginComp, p);
	}

	private void provideImage(Composite composite, Label imageLabel,
			Plugin plugin) {
		String imageAsB64 = plugin.getImage();
		if (Strings.isNullOrEmpty(imageAsB64)) {
			imageLabel.setImage(ImageType.LOGO_64_32.get());
			return;
		}
		try {
			byte[] decodedImage = new Base64ImageHelper()
					.decodeBase64EncodedImage(imageAsB64);
			final Image image = new Image(composite.getDisplay(),
					new ByteArrayInputStream(decodedImage));
			imageLabel.addDisposeListener(new DisposeListener() {
				@Override
				public void widgetDisposed(DisposeEvent e) {
					image.dispose();
				}
			});
			imageLabel.setImage(image);
		} catch (IOException e) {
			log.debug("failed to create image from base64 string", e);
		}
	}

	private void addButtons(Composite buttonComposite, final Plugin p) {
		boolean available = false;
		boolean installed = false;
		boolean newerAvailable = false;
		if (!Strings.isNullOrEmpty(p.getVersion())) {
			available = true;
		}
		if (!Strings.isNullOrEmpty(p.getInstalledVersion())) {
			installed = true;
		}
		if (available && installed) {
			newerAvailable = PluginsService.isNewer(p.getVersion(),
					p.getInstalledVersion());
		}

		boolean changed = changedPlugins.containsKey(p.getSymbolicName());
		if (changed) {
			getToolkit().createLabel(buttonComposite,
					changedPlugins.get(p.getSymbolicName()), SWT.WRAP);
		} else {
			if (available && !installed) {
				final Button b = getToolkit().createButton(buttonComposite,
						"Install", SWT.PUSH);
				b.addListener(SWT.Selection, new InstallOrUpdateButtonListener(
						"Installation of " + p.getSymbolicName(), p));
				b.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						b.setEnabled(false);
						pluginChanged(p, "Installing...");
					}
				});
			} else if (newerAvailable) {
				final Button b = getToolkit().createButton(buttonComposite,
						"Update", SWT.PUSH);
				b.addListener(SWT.Selection, new InstallOrUpdateButtonListener(
						"Update of " + p.getSymbolicName(), p));
				b.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						b.setEnabled(false);
						pluginChanged(p, "Updating...");
					}
				});
			}
			if (installed) {
				log.debug("Adding the remove button for {}", p);
				getToolkit().createButton(buttonComposite, "Remove", SWT.PUSH)
						.addListener(SWT.Selection, new Listener() {

							@Override
							public void handleEvent(Event event) {
								log.debug("Remove requested for {}", p);
								try {
									if (pluginsService.uninstall(
											p.getSymbolicName(),
											p.getInstalledVersion(), null)) {

										pluginChanged(p,
												"Removed, please restart");
										PluginManagerDialog.restartNecessary();
										PluginManagerDialog.reloadPlugins();
									}
								} catch (Exception e) {
									log.debug("Removal failed", e);
									getMessageManager()
											.addMessage(
													"inst",
													"Removal failed: "
															+ e.getMessage(),
													null,
													IMessageProvider.ERROR);
								}
							}
						});
			}
		}
	}

	private FormToolkit getToolkit() {
		return mform.getToolkit();
	}

	private void clear() {
		for (Composite c : pluginComps.keySet()) {
			Plugin plugin = pluginComps.get(c);
			c.dispose();
			plugins.remove(plugin.getSymbolicName());
		}
	}

	private void pluginChanged(Plugin p2, String message) {
		changedPlugins.put(p2.getSymbolicName(), message);
	}

	protected class InstallOrUpdateButtonListener implements Listener {

		private final Plugin p;
		private String jobName;

		private InstallOrUpdateButtonListener(String jobName, Plugin p) {
			this.jobName = jobName;
			this.p = p;
		}

		@Override
		public void handleEvent(Event event) {

			new InstallOrUpdateJob(jobName, p).schedule();
		}
	}

	private final class InstallOrUpdateJob extends Job {
		private Plugin p;

		private InstallOrUpdateJob(String name, Plugin p) {
			super(name);
			this.p = p;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			PluginManagerDialog.backgroundJobStarting();
			monitor.beginTask(getName(), 100);
			try {
				log.debug("About to install {}", p);
				try {
					try {
						monitor.worked(5);
						pluginsService.installOrUpdatePlugin(p);
						monitor.worked(95);
					} catch (UserMessageException e) {
						throw e;
					} catch (Exception e) {
						log.error("Installation of plugin failed", e);
						throw new UserMessageException("Installation failed: "
								+ e.getMessage());
					}

					pluginChanged(p, "Installed, please restart");
					showInstallationSuccessfulMessage();

				} catch (final UserMessageException ume) {
					showInstallationErrorMessage(ume);
				}

			} finally {
				monitor.done();
				PluginManagerDialog.backgroundJobFinishing();
			}
			return Status.OK_STATUS;
		}

		protected void showInstallationErrorMessage(
				final UserMessageException ume) {
			Display.getDefault().asyncExec(new Runnable() {

				@Override
				public void run() {
					ErrorPopup.show("Installation of " + p.getName()
							+ " failed: " + ume.getMessage());
				}
			});
		}

		protected void showInstallationSuccessfulMessage() {
			Display.getDefault().asyncExec(new Runnable() {

				@Override
				public void run() {
					InformationPopup.show("Installation of " + p.getName()
							+ " successful.");
					PluginManagerDialog.reloadPlugins();

					PluginManagerDialog.restartNecessary();
				}
			});
		}
	}

}
