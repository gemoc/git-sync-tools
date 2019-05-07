package fr.inria.diverse.git_sync.gittool;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleWalk;

public class GitModuleManager {

	String gitRemoteURL;
	String localGitFolder;
	
	Set<String> remoteBranchesNames = new HashSet<String>();
		
	public GitModuleManager(String gitRemoteURL, String localGitFolder) {
		this.gitRemoteURL = gitRemoteURL;
		this.localGitFolder = localGitFolder;
	}
	
	public void gitClone() throws InvalidRemoteException, TransportException, GitAPIException {
		File localPath = new File(localGitFolder);
		System.out.println("Cloning from " + gitRemoteURL + " to " + localPath);
		try (Git result = Git.cloneRepository()
                .setURI(gitRemoteURL)
                .setDirectory(localPath)
                .setCloneSubmodules(true)
                .setCloneAllBranches(true)
                .call()) {
	        // Note: the call() returns an opened repository already which needs to be closed to avoid file handle leaks!
	        System.out.println("Having repository: " + result.getRepository().getDirectory());
		}
	}
	
	public void listAllBranches() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setMustExist( true )
        		.setGitDir(new File(localGitFolder+"/.git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
            System.out.println("Having repository: " + repository.getDirectory());

            // the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
            Ref head = repository.exactRef("refs/heads/master");
            System.out.println("Ref of refs/heads/master: " + head);

            System.out.println("current branch: " +repository.getBranch());
            
            
            try (Git git = new Git(repository)) {
	            List<Ref> call = git.branchList().call();
	            for (Ref ref : call) {
	                System.out.println("Branch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());
	            }
	
	            System.out.println("Now including remote branches:");
	            call = git.branchList().setListMode(ListMode.ALL).call();
	            for (Ref ref : call) {
	                System.out.println("Branch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());
	            }
	        }
        }
	}
	
	public void listMasterSubModules() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try (Repository repository = builder.setMustExist( true )
        		.setGitDir(new File(localGitFolder+"/.git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {

            System.out.println("current branch: " +repository.getBranch());
            
            try (Git parentgit = new Git(repository)) {
            	Map<String,SubmoduleStatus> submodules = parentgit.submoduleStatus().call();
            	for(String submoduleName : submodules.keySet()) {
                	System.out.println("\t"+submoduleName);
            	}
	        }
        }
	}
	
	public void listAllSubmodulesBranches() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
			
		try (Repository parentRepository = builder.setMustExist( true )
        		.setGitDir(new File(localGitFolder+"/.git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
            
            try(SubmoduleWalk walk = SubmoduleWalk.forIndex( parentRepository )){
	            while( walk.next() ) {
	              Repository submoduleRepository = walk.getRepository();
	              try(Git submodulegit = Git.wrap( submoduleRepository )){
	            	  System.out.println("submodule "+walk.getModuleName());
	            	  List<Ref> call = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
	            	  for (Ref ref : call) {
	  	                System.out.println("\tBranch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName() +" " +ref.isSymbolic());
	  	                
	  	            }
	              }
	            }
            }
        }
	}
	
	/**
	 * 
	 * @throws IOException
	 * @throws GitAPIException
	 */
	public Set<String> collectAllSubmodulesRemoteBranches() throws IOException, GitAPIException {
		Set<String> remoteBranchesNames = new HashSet<String>();
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
			
		try (Repository parentRepository = builder.setMustExist( true )
        		.setGitDir(new File(localGitFolder+"/.git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
            
            try(SubmoduleWalk walk = SubmoduleWalk.forIndex( parentRepository )){
	            while( walk.next() ) {
	              Repository submoduleRepository = walk.getRepository();
	              try(Git submodulegit = Git.wrap( submoduleRepository )){
	            	  System.out.println("remote branches in submodule "+walk.getModuleName()+":");
	            	  List<Ref> call = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
	            	  for (Ref ref : call) {
	  	                if(ref.getName().startsWith("refs/remotes")) {
	  	                	String branchName = ref.getName().substring(ref.getName().lastIndexOf("/")+1); 
	  	                	System.out.println("\t"+branchName);
	  	                	remoteBranchesNames.add(branchName);
	  	                }
	  	            }
	              }
	            }
            }
        }
		return remoteBranchesNames;
	}	
	
	/**
	 * remove local and remote branches not in the given set
	 * @throws IOException 
	 * @throws GitAPIException 
	 */
	public void deleteBranchesNotIn(Set<String> relevantBranches) throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		
		try (Repository parentRepository = builder.setMustExist( true )
        		.setGitDir(new File(localGitFolder+"/.git"))
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build()) {
            
			try (Git parentgit = new Git(parentRepository)) {
            	Map<String,SubmoduleStatus> submodules = parentgit.submoduleStatus().call();
            	for(String submoduleName : submodules.keySet()) {
                	System.out.println("\t"+submoduleName);
            	}
	        }
        }
	}
	
}
