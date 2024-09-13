package org.gemoc.sync_git_submodules_branches;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gemoc.sync_git_submodules_branches.gittool.GitModuleManager;

public class SyncGitSubModulesBranchesCLI {

	public static void main(String[] args) throws Exception {
		
		   
		Options options = new Options();
		options.addOption("p", "password", true, "password for authentification (optionnal if the username is a github token)")
			.addOption("u", "user", true, "username or github token for authentification")
			.addOption("f", "folder", true, "path to folder where to do the checkout, if not set, will default to a temp folder")
			.addOption("g", "gitURL", true, "git URL that will be cloned")
			.addOption("c", "committerName", true, "name of the committer who'll sign the commit")
			.addOption("e", "committerEmail", true, "email of the committer who'll sign the commit")
			.addOption("d", "dryRun", false, "dryRun, do not commit and push the update")
			.addOption("r", "reportFile", true, "file name tha will containt the markdown report")
			.addOption("i", "inactivityThreshold", true, "number of days since the last commit of a specific branch before considering the branch as old/unmaintained/inactive (-1 for infinite duration)");
		
		
		
		CommandLineParser parser = new DefaultParser();

		//parse the options passed as command line arguments
		CommandLine cmd = parser.parse( options, args);
	     
		String userOrToken = cmd.hasOption("u") ? cmd.getOptionValue("u") : "";
		String password = cmd.hasOption("p") ? cmd.getOptionValue("p") : "";
		String parentGitURL = cmd.hasOption("g") ? cmd.getOptionValue("g") : "";
		String directoryPath = cmd.hasOption("f") ? cmd.getOptionValue("f") : "";
		String committerName = cmd.hasOption("c") ? cmd.getOptionValue("c") : "";
		String committerEmail = cmd.hasOption("e") ? cmd.getOptionValue("e") : "";
		String inactivityThreshold = cmd.hasOption("i") ? cmd.getOptionValue("i") : "90";
		String reportFilePath = cmd.hasOption("r") ? cmd.getOptionValue("r") : "syncReport.md";
		boolean dryRun = cmd.hasOption("d");
		
		if(parentGitURL.isEmpty()) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("SyncGitSubModulesBranches", options);
			System.exit(0);
		}
				
		File outputDirectory = !directoryPath.isEmpty() ? new File(directoryPath) : Files.createTempDirectory("SyncGitSubModulesBranches_").toFile();
		
		if(!directoryPath.isEmpty() && outputDirectory.exists()) {
			System.out.println("deleting "+outputDirectory.getPath());
			FileUtils.deleteDirectory(outputDirectory);
		}
		// https://www.codeaffine.com/2014/12/09/jgit-authentication/
		UsernamePasswordCredentialsProvider credProvider = new UsernamePasswordCredentialsProvider( userOrToken, password );
		GitModuleManager gitManager = new GitModuleManager(parentGitURL, outputDirectory.getAbsolutePath(), credProvider,
				committerName,
				committerEmail);
    	gitManager.gitUpdateOrClone();
    	//gitManager.listAllBranches();
    	//gitManager.listMasterSubModules();
    	//gitManager.listAllSubmodulesBranches();
    	Set<String> relevantBranches = gitManager.collectAllSubmodulesActiveRemoteBranches(Integer.parseInt(inactivityThreshold));
    	gitManager.deleteBranchesNotIn(relevantBranches);
    	gitManager.createMissingParentBranches(relevantBranches);
    	StringBuffer sb = new StringBuffer();
    	gitManager.updateAllBranchesModules(sb, dryRun);
    	FileUtils.write(outputDirectory, sb.toString(), Charset.defaultCharset());
    	writeReport(new File(reportFilePath), sb);
    	if(directoryPath.isEmpty()) {
    		// must delete the temp dir
    		System.out.println("Deleting temp directory "+outputDirectory);
    		FileUtils.deleteDirectory(outputDirectory);
    	}
	}
	
    protected static void writeReport(File reportFile, StringBuffer content) throws IOException {
    	// Ensure the parent directory exists
        File parentDir = reportFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
           parentDir.mkdirs();
        }
        FileUtils.write(reportFile, content.toString(), Charset.defaultCharset());
    }

}
