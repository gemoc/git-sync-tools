package fr.inria.diverse.git_sync.gittool;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;

public class GitModuleManager {

	String gitRemoteURL;
	String localGitFolder;
	CredentialsProvider credentialProvider;

	Set<String> remoteBranchesNames = new HashSet<String>();

	public GitModuleManager(String gitRemoteURL, String localGitFolder, CredentialsProvider credentialProvider) {
		this.gitRemoteURL = gitRemoteURL;
		this.localGitFolder = localGitFolder;
		this.credentialProvider = credentialProvider;
	}

	public void gitClone() throws InvalidRemoteException, TransportException, GitAPIException {
		File localPath = new File(localGitFolder);
		System.out.println("Cloning from " + gitRemoteURL + " to " + localPath);
		try (Git result = Git.cloneRepository().setURI(gitRemoteURL).setDirectory(localPath).setCloneSubmodules(true)
				.setCloneAllBranches(true).call()) {
			// Note: the call() returns an opened repository already which needs to be
			// closed to avoid file handle leaks!
			System.out.println("Having repository: " + result.getRepository().getDirectory());
		}
	}

	public void listAllBranches() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try (Repository repository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			System.out.println("Current parent branches");
			// the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
			Ref head = repository.exactRef("refs/heads/master");
			System.out.println("\tRef of refs/heads/master: " + head);

			System.out.println("\tcurrent branch: " + repository.getBranch());

			try (Git git = new Git(repository)) {
				/*
				 * List<Ref> call = git.branchList().call(); for (Ref ref : call) {
				 * System.out.println("\tBranch: " + ref + " " + ref.getName() + " " +
				 * ref.getObjectId().getName()); }
				 * 
				 * System.out.println("\tNow including remote branches:");
				 */
				List<Ref> call = git.branchList().setListMode(ListMode.ALL).call();
				for (Ref ref : call) {
					System.out.println("\tBranch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());
				}
			}
		}
	}

	public void listMasterSubModules() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try (Repository repository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			System.out.println("current branch: " + repository.getBranch());

			try (Git parentgit = new Git(repository)) {
				Map<String, SubmoduleStatus> submodules = parentgit.submoduleStatus().call();
				for (String submoduleName : submodules.keySet()) {
					System.out.println("\t" + submoduleName);
				}
			}
		}
	}

	public void listAllSubmodulesBranches() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			try (SubmoduleWalk walk = SubmoduleWalk.forIndex(parentRepository)) {
				while (walk.next()) {
					Repository submoduleRepository = walk.getRepository();
					try (Git submodulegit = Git.wrap(submoduleRepository)) {
						System.out.println("submodule " + walk.getModuleName());
						List<Ref> call = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
						for (Ref ref : call) {
							System.out.println("\tBranch: " + ref + " " + ref.getName() + " "
									+ ref.getObjectId().getName() + " " + ref.isSymbolic());

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

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			try (SubmoduleWalk walk = SubmoduleWalk.forIndex(parentRepository)) {
				while (walk.next()) {
					Repository submoduleRepository = walk.getRepository();
					try (Git submodulegit = Git.wrap(submoduleRepository)) {
						System.out.println("remote branches in submodule " + walk.getModuleName() + ":");
						List<Ref> call = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
						for (Ref ref : call) {
							if (ref.getName().startsWith("refs/remotes")) {
								String branchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
								System.out.println("\t" + branchName);
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
	 * 
	 * @throws Exception
	 */
	public void deleteBranchesNotIn(Set<String> relevantBranches) throws Exception {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			try (Git parentgit = new Git(parentRepository)) {
				List<Ref> call = parentgit.branchList().setListMode(ListMode.REMOTE).call();
				for (Ref ref : call) {
					if (ref.getName().startsWith("refs/remotes")) {
						String branchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
						if (!relevantBranches.contains(branchName)) {
							System.out.println("\tdeleting branch " + ref.getName() + "  " + ref);
							// delete locally
							parentgit.branchDelete().setBranchNames(ref.getName()).setForce(true).call();
							// delete remotely too
							RefSpec refSpec = new RefSpec().setSource(null).setDestination("refs/heads/" + branchName);
							Iterable<PushResult> res = parentgit.push().setRefSpecs(refSpec).setRemote("origin")
									.setCredentialsProvider(credentialProvider).call();
							for (PushResult pushRes : res) {
								System.out.println(
										pushRes + " ; " + pushRes.getMessages() + " ; " + pushRes.getRemoteUpdates());
								validateRemoteRefUpdates("del remote branch", pushRes.getRemoteUpdates());
							}
						}

					}
				}
			}
		}
	}

	public void createMissingParentBranches(Set<String> relevantBranches)
			throws IOException, GitAPIException, GitSyncError {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {
			Set<String> parentBranches = new HashSet<String>();
			try (Git parentgit = new Git(parentRepository)) {
				List<Ref> call = parentgit.branchList().setListMode(ListMode.REMOTE).call();
				for (Ref ref : call) {
					if (ref.getName().startsWith("refs/remotes")) {
						String branchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
						parentBranches.add(branchName);
					}
				}
				Set<String> missingParentBranches = new HashSet<String>();
				missingParentBranches.addAll(relevantBranches);
				missingParentBranches.removeAll(parentBranches);
				System.out.println("Missing parent branches :" + missingParentBranches);
				for (String missingParentBranch : missingParentBranches) {
					createBranchForModules(parentgit, missingParentBranch);
				}
			}
		}
	}

	public void createBranchForModules(Git parentgit, String missingParentBranch)
			throws GitAPIException, GitSyncError, IOException {
		// make sure the local branch is not there
		List<Ref> refs = parentgit.branchList().call();
		for (Ref ref : refs) {
			if (ref.getName().equals("refs/heads/" + missingParentBranch)) {
				System.out.println("Removing branch before");
				parentgit.branchDelete().setBranchNames(missingParentBranch).setForce(true).call();
				break;
			}
		}

		// create local branch
		parentgit.branchCreate().setName(missingParentBranch).call();

		// push branch to remote
		Iterable<PushResult> res = parentgit.push().setRemote("origin")
				.setRefSpecs(new RefSpec(missingParentBranch + ":" + missingParentBranch))
				.setCredentialsProvider(credentialProvider).call();

		for (PushResult pushRes : res) {
			System.out.println(pushRes + " ; " + pushRes.getMessages() + " ; " + pushRes.getRemoteUpdates());
			validateRemoteRefUpdates("push new remote branch", pushRes.getRemoteUpdates());
		}
	}

	public void updateAllBranchesModules() throws IOException, GitAPIException, GitSyncError, ConfigInvalidException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {
			try (Git parentgit = new Git(parentRepository)) {
				List<Ref> call = parentgit.branchList().setListMode(ListMode.REMOTE).call();
				for (Ref ref : call) {
					if (ref.getName().startsWith("refs/remotes")) {
						updateBranchesForModules(parentgit,
								ref.getName().substring(ref.getName().lastIndexOf("/") + 1));
					}
				}
			}
		}
	}

	public void updateBranchesForModules(Git parentgit, String consideredBranch)
			throws GitAPIException, GitSyncError, IOException, ConfigInvalidException {
		System.out.println("updateBranchesForModules branch=" + consideredBranch);
		// switch parentGit to branch
		checkoutBranch(parentgit, consideredBranch);
		
		
		// for each submodule check if it must use master or specific branch
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(parentgit.getRepository())) {
			while (walk.next()) {
				Repository submoduleRepository = walk.getRepository();
				try (Git submodulegit = Git.wrap(submoduleRepository)) {
					// System.out.println("remote branches in submodule "+walk.getModuleName()+":");
					boolean needToTrackConsideredBranch = false;
					String trackedBranch = "master";
					List<Ref> call = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
					for (Ref ref : call) {
						if (ref.getName().startsWith("refs/remotes")) {
							String branchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
							if (branchName.equals(consideredBranch)) {
								needToTrackConsideredBranch = true;
								trackedBranch = consideredBranch;
								break;
							}
						}
					}
					System.out.println("\tneed to track module " + walk.getModuleName() + " on branch " + trackedBranch);
						
					
					FileBasedConfig modulesConfig = new FileBasedConfig(new File(
							parentgit.getRepository().getWorkTree(), Constants.DOT_GIT_MODULES), parentgit.getRepository().getFS());
					modulesConfig.load();
					modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, walk.getModulesPath(),
							ConfigConstants.CONFIG_BRANCH_SECTION, trackedBranch);
					modulesConfig.save();
				}
			}
		}
	}
	
	/**
	 * checkout the local branch or get the corresponding remote one 
	 * @param git
	 * @param branchName
	 * @throws GitAPIException 
	 * @throws GitSyncError 
	 */
	public void checkoutBranch(Git git, String branchName) throws GitAPIException, GitSyncError {
		List<Ref> refs = git.branchList().call();
		for (Ref ref : refs) {
			if (ref.getName().equals("refs/heads/" + branchName)) {
				// a local branch exists
				git.checkout()
					.setName(branchName)
					.call();
				return;
			}
		}
		// else look for a remote branch with this name
		List<Ref> call = git.branchList().setListMode(ListMode.REMOTE).call();
		for (Ref ref : call) {
			if (ref.getName().startsWith("refs/remotes")) {
				String remotebranchName = ref.getName().substring(ref.getName().lastIndexOf("/") + 1);
				if (branchName.equals(remotebranchName)) {
					git.checkout()
						.setName(branchName)
						.setCreateBranch(true)
						.setUpstreamMode(SetupUpstreamMode.TRACK)
				        .setStartPoint(ref.getName().replaceFirst("refs/remotes/", ""))
						.call();
					return;
				}
			}
		}
		throw new GitSyncError("Checout failed, No branch local or remote branch named "+branchName+" found");
	}

	/**
	 * Check references updates for any errors
	 *
	 * @param errorPrefix The error prefix for any error message
	 * @param refUpdates  A collection of remote references updates
	 * @throws Exception
	 */
	public static void validateRemoteRefUpdates(String errorPrefix, Collection<RemoteRefUpdate> refUpdates)
			throws GitSyncError {
		for (RemoteRefUpdate refUpdate : refUpdates) {
			RemoteRefUpdate.Status status = refUpdate.getStatus();

			if (status == RemoteRefUpdate.Status.REJECTED_NODELETE || status == RemoteRefUpdate.Status.NON_EXISTING
					|| status == RemoteRefUpdate.Status.NOT_ATTEMPTED
					|| status == RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD
					|| status == RemoteRefUpdate.Status.REJECTED_OTHER_REASON
					|| status == RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED) {
				throw new GitSyncError(String.format("%s - Status '%s'", errorPrefix, status.name()));
			}
		}
	}

}
