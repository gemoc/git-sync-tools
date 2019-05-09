package fr.inria.diverse.git_sync;

import java.io.File;
import java.util.Set;

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

import fr.inria.diverse.git_sync.gittool.GitModuleManager;

/**
 * Goal which updates a git repo having submodules in order to:
 * - have a branch corresponding to each available submodule branches
 * - update heads of the root repo to the head of each submodules
 *
 */
@Mojo( name = "synch", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class SyncGitSubmodulesBranchesMojo
    extends AbstractMojo
{
    /**
     * Location of the file.
     */
    @Parameter( defaultValue = "${project.build.directory}/syncgitsubmodules_repo", property = "outputDir", required = true )
    private File outputDirectory;
    
    @Parameter(property="parentGitURL", required = true)
    private String parentGitURL;
    
    @Parameter(property="userOrToken", required = true)
    private String userOrToken;
    
    @Parameter(defaultValue = "", property="password", required = true)
    private String password;

    public void execute()
        throws MojoExecutionException
    {
    	getLog().debug( "###############################################");
    	getLog().info( "parentGitURL="+parentGitURL);
    	
		// https://www.codeaffine.com/2014/12/09/jgit-authentication/
		UsernamePasswordCredentialsProvider credProvider = new UsernamePasswordCredentialsProvider( userOrToken, password );
		GitModuleManager gitManager = new GitModuleManager(parentGitURL, outputDirectory.getAbsolutePath(), credProvider);
    	try {
			gitManager.gitClone();
			gitManager.listSubModules();
	    	Set<String> relevantBranches = gitManager.collectAllSubmodulesRemoteBranches();
	    	gitManager.deleteBranchesNotIn(relevantBranches);
	    	gitManager.createMissingParentBranches(relevantBranches);
	    	gitManager.updateAllBranchesModules();
		} catch (Exception e) {
			getLog().error( e);
			throw new MojoExecutionException(e.getMessage(), e);
		}
    	
 
    }
}
