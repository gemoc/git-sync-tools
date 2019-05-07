package fr.inria.diverse.git_sync;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import fr.inria.diverse.git_sync.gittool.GitModuleManager;

public class SyncGitSubModulesBranchesCLI {

	public static void main(String[] args) throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		String rootGitURL = "git@github.com:dvojtise/my_test_root_module.git";
		File outputDirectory = new File("target/test-target-repo");
		
		if(outputDirectory.exists()) {
			System.out.println("deleting "+outputDirectory.getPath());
			FileUtils.deleteDirectory(outputDirectory);
		}
		
		GitModuleManager gitManager = new GitModuleManager(rootGitURL, outputDirectory.getAbsolutePath());
    	gitManager.gitClone();
    	//gitManager.listAllBranches();
    	gitManager.listMasterSubModules();
    	gitManager.listAllSubmodulesBranches();
    	gitManager.collectAllSubmodulesRemoteBranches();
	}

}
