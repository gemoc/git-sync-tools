package org.gemoc.sync_git_submodules_branches;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;

import org.apache.commons.io.FileUtils;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gemoc.sync_git_submodules_branches.gittool.GitModuleManager;

/**
 * Goal that updates a git repository having submodules in order to:
 * - have a branch corresponding to each available submodule branches
 * - update heads of the root repository to the head of each submodules
 *
 */
@Mojo( name = "synch", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class SyncGitSubmodulesBranchesMojo
    extends AbstractMojo
{
    /**
     * Location of the git repository.
     */
    @Parameter( defaultValue = "${project.build.directory}/syncgitsubmodules_repo", property = "outputDir", required = true )
    private File outputDirectory;
    
    /**
     * Location of the report file.
     */
    @Parameter( defaultValue = "${project.build.directory}/syncReport.md", property = "reportFile", required = true )
    private File reportFile;
    
    @Parameter(property="parentGitURL", required = true)
    private String parentGitURL;
    
    @Parameter(property="userOrToken")
    private String userOrToken;
    
    @Parameter(defaultValue = "", property="password")
    private String password;

    
    @Parameter(defaultValue = "", property="committerEmail")
    private String committerEmail;
    
    @Parameter(defaultValue = "", property="committerName")
    private String committerName;
    
    @Parameter(defaultValue = "false", property="dryRun")
    private boolean dryRun;
    
    /**
     * number of days since the last commit of a specific branch 
     * The branch will be considered old/unmaintained /inactive
     * if all branches (except main/master branch) are inactive, then the branch is removed from the main integration repository 
     */
    @Parameter(defaultValue = "90", property = "inactivityThreshold")
    private Integer inactivityThreshold;
    
    public void execute()
        throws MojoExecutionException
    {
    	getLog().debug( "###############################################");
    	getLog().info( "parentGitURL="+parentGitURL);
    	getLog().info( "committerName="+committerName);
    	getLog().info( "committerEmail="+committerEmail);
    	getLog().info( "inactivityThreshold="+inactivityThreshold+" days");
    	
		// https://www.codeaffine.com/2014/12/09/jgit-authentication/
    	if(userOrToken == null || password == null) {
    		throw new MojoExecutionException("Missing user name or password for authentification");
    	}
    	getLog().info("Using UsernamePassword authentification");
		UsernamePasswordCredentialsProvider credProvider = new UsernamePasswordCredentialsProvider( userOrToken, password );
		GitModuleManager gitManager = new GitModuleManager(parentGitURL, outputDirectory.getAbsolutePath(), credProvider,
				committerName,
				committerEmail);
    	try {
			gitManager.gitUpdateOrClone();
			gitManager.listSubModules();
	    	Set<String> relevantBranches = gitManager.collectAllSubmodulesActiveRemoteBranches(inactivityThreshold);
	    	gitManager.deleteBranchesNotIn(relevantBranches);
	    	gitManager.createMissingParentBranches(relevantBranches);
	    	StringBuffer sb = new StringBuffer();
	    	gitManager.updateAllBranchesModules(sb, dryRun);
	    	writeReport(sb);
	    	
		} catch (Exception e) {
			getLog().error( e);
			throw new MojoExecutionException(e.getMessage(), e);
		}
    	
 
    }
    
    protected void writeReport(StringBuffer content) throws MojoExecutionException {
    	// Ensure the parent directory exists
        File parentDir = reportFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
           parentDir.mkdirs();
        }
        try {
        	FileUtils.write(reportFile, content.toString(), Charset.defaultCharset());
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing to file", e);
        }
    }
}
