package fr.inria.diverse.git_sync;

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
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.jgit.api.errors.GitAPIException;

import fr.inria.diverse.git_sync.gittool.GitModuleManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Goal which updates a git repo having submodules in order to:
 * - have a branch corresponding to each available submodule branches
 * - update heads of the root repo to the head of each submodules
 *
 */
@Mojo( name = "touch", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class SyncGitSubmodulesBranchesMojo
    extends AbstractMojo
{
    /**
     * Location of the file.
     */
    @Parameter( defaultValue = "${project.build.directory}", property = "outputDir", required = true )
    private File outputDirectory;
    
    @Parameter(property="rootGitURL", required = true, readonly = true)
    private String rootGitURL;

    public void execute()
        throws MojoExecutionException
    {
    	getLog().debug( "###############################################");
    	getLog().info( "rootGitURL="+rootGitURL);
    	
    	GitModuleManager gitManager = new GitModuleManager(rootGitURL, outputDirectory.getAbsolutePath());
    	try {
			gitManager.gitClone();
		} catch (GitAPIException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			getLog().error(e1);
		}
    	
        File f = outputDirectory;

        if ( !f.exists() )
        {
            f.mkdirs();
        }

        File touch = new File( f, "touch.txt" );

        FileWriter w = null;
        try
        {
            w = new FileWriter( touch );

            w.write( "touch.txt" );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error creating file " + touch, e );
        }
        finally
        {
            if ( w != null )
            {
                try
                {
                    w.close();
                }
                catch ( IOException e )
                {
                    // ignore
                }
            }
        }
    }
}
