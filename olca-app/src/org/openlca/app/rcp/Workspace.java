package org.openlca.app.rcp;

import java.io.File;
import java.net.URI;
import java.net.URL;

import org.eclipse.core.runtime.Platform;
import org.openlca.app.AppArg;
import org.openlca.app.Config;
import org.openlca.core.DataDir;
import org.openlca.core.library.LibraryDir;

/**
 * The workspace configuration of openLCA. The workspace is located in the
 * folder "openLCA-data" in the user's home directory (system property
 * user.home).
 */
public class Workspace {

	private static File dir;

	/**
	 * Get the workspace directory. Returns null if the workspace was not yet
	 * initialized.
	 */
	public static File getDir() {
		if (dir == null)
			init();
		return dir;
	}

	public static LibraryDir getLibraryDir() {
		var libDir = new File(dir, "libraries");
		return new LibraryDir(libDir);
	}

	/**
	 * Initializes the workspace of the application. Should be called only once
	 * when the application bundle starts.
	 */
	static File init() {
		try {
			Platform.getInstanceLocation().release();
			File dir = getDirFromCommandLine();
			if (dir == null) {
				dir = Config.WORK_SPACE_IN_USER_DIR
					? getFromUserHome()
					: getFromInstallLocation();
			}
			DataDir.setRoot(dir);
			URL workspaceUrl = new URL("file", null, dir.getAbsolutePath());
			Platform.getInstanceLocation().set(workspaceUrl, true);
			Workspace.dir = dir;
			return dir;
		} catch (Exception e) {
			// no logging here as the logger is not yet configured
			e.printStackTrace();
			return null;
		}
	}

	private static File getFromUserHome() {
		String prop = System.getProperty("user.home");
		File userDir = new File(prop);
		File dir = new File(userDir, Config.WORK_SPACE_FOLDER_NAME);
		if (!dir.exists())
			dir.mkdirs();
		return dir;
	}

	private static File getFromInstallLocation() throws Exception {
		URI uri = Platform.getInstallLocation().getURL().toURI();
		File installDir = new File(uri);
		File dir = new File(installDir, Config.WORK_SPACE_FOLDER_NAME);
		if (!dir.exists())
			dir.mkdirs();
		return dir;
	}

	private static File getDirFromCommandLine() {
		try {
			String path = AppArg.DATA_DIR.getValue();
			if (path == null)
				return null;
			File file = new File(path);
			if (file.canWrite() && file.isDirectory())
				return file;
			return null;
		} catch (Exception e) {
			// no logging here as the logger is not yet configured
			e.printStackTrace();
			return null;
		}
	}

}
