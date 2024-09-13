package org.gemoc.sync_git_submodules_branches.gittool;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Main class implementing the features of the git sync tool
 */
public class GitModuleManager {

	Logger logger = LoggerFactory.getLogger(GitModuleManager.class);

	
	String gitRemoteURL;
	String localGitFolder;
	CredentialsProvider credentialProvider;
	String masterBranchName = "master";
	PersonIdent defaultCommitter = null;


	/**
	 * Constructor
	 * 
	 * @param gitRemoteURL url of git that need to be updated
	 * @param localGitFolder folder containing the local copy
	 * @param credentialProvider provider of credential for pushing to the remote repository
	 * @param committerName name to use when commiting
	 * @param committerEmail email to use when commiting
	 */
	public GitModuleManager(String gitRemoteURL, String localGitFolder, 
			CredentialsProvider credentialProvider, 
			String committerName,
			String committerEmail) {
		this.gitRemoteURL = gitRemoteURL;
		this.localGitFolder = localGitFolder;
		this.credentialProvider = credentialProvider;
		if(!(committerName== null || committerName.isEmpty()) && ! (committerEmail == null || committerEmail.isEmpty())) {
			this.defaultCommitter = new PersonIdent(committerName, committerEmail);
		}
	}

	/**
	 * Clone the gitRemoteURL repository to localGitFolder
	 * 
	 * @throws InvalidRemoteException
	 * @throws TransportException
	 * @throws GitAPIException
	 * @throws IOException
	 */
	public void gitClone() throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		File localPath = new File(localGitFolder);
		logger.info("Cloning from " + gitRemoteURL + " to " + localPath);
		try (Git result = Git.cloneRepository().setURI(gitRemoteURL).setDirectory(localPath).setCloneSubmodules(true)
				.setCloneAllBranches(true).call()) {
			// Note: the call() returns an opened repository already which needs to be
			// closed to avoid file handle leaks!
			logger.info("Having repository: " + result.getRepository().getDirectory());
			this.masterBranchName = result.getRepository().getBranch();
			logger.info("master branch name: " + this.masterBranchName);
			
		}
	}
	
	public void gitUpdate() throws IOException, WrongRepositoryStateException, InvalidConfigurationException, InvalidRemoteException, CanceledException, RefNotFoundException, RefNotAdvertisedException, NoHeadException, TransportException, GitAPIException {
		File localPath = new File(localGitFolder);
		logger.info("Updating " + localPath + " from " + gitRemoteURL);
		try (Git result = Git.open(localPath)) {
			String url = result.getRepository().getConfig().getString("remote", "origin", "url");
			if(gitRemoteURL.equals(url)) {
				logger.info("Checkout "+masterBranchName+" branch from existing repository: " + result.getRepository().getDirectory());
				result.checkout().setName(masterBranchName).call();
				logger.info("Pulling existing repository: " + result.getRepository().getDirectory());
				PullResult res = result.pull().setRecurseSubmodules(FetchRecurseSubmodulesMode.YES).call();
				if(!res.isSuccessful()){
					logger.error("Failed to pull repository\n Please delete folder "+localGitFolder+" to perform a full clone.");
					logger.error("fetch result: "+res.getFetchResult().getMessages());
					logger.error("merge result: "+res.getMergeResult().getMergeStatus());
					logger.error("rebase result: "+res.getRebaseResult().getStatus());
					throw new WrongRepositoryStateException("Failed to pull repository");
				}
			} else {
				logger.error("Existing folder doesn't point to the same url ("+url+")\n Please delete folder "+localGitFolder+" to perform a full clone.");
				throw new InvalidRemoteException("Existing folder doesn't point to the same url ("+url+") Please delete this folder to perform a full clone.");
			}
		}
	}
	
	public void gitUpdateOrClone() throws WrongRepositoryStateException, InvalidConfigurationException, InvalidRemoteException, CanceledException, RefNotFoundException, RefNotAdvertisedException, NoHeadException, TransportException, IOException, GitAPIException {
		File localPath = new File(localGitFolder);
		if(localPath.exists() && localPath.isDirectory()) {
			gitUpdate();
		} else {
			gitClone();
		}
	}

	public void listAllBranches() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try (Repository repository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			logger.debug("Current parent branches");
			// the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
			Ref head = repository.exactRef("refs/heads/master");
			logger.debug("\tRef of refs/heads/master: " + head);

			logger.debug("\tcurrent branch: " + repository.getBranch());

			try (Git git = new Git(repository)) {
				/*
				 * List<Ref> call = git.branchList().call(); for (Ref ref : call) {
				 * logger.info("\tBranch: " + ref + " " + ref.getName() + " " +
				 * ref.getObjectId().getName()); }
				 * 
				 * logger.info("\tNow including remote branches:");
				 */
				List<Ref> call = git.branchList().setListMode(ListMode.ALL).call();
				for (Ref ref : call) {
					logger.info("\tBranch: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());
				}
			}
		}
	}

	public void listSubModules() throws IOException, GitAPIException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try (Repository repository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			logger.info("Listing submodules on branch " + repository.getBranch()+" :");

			try (Git parentgit = new Git(repository)) {
				Map<String, SubmoduleStatus> submodules = parentgit.submoduleStatus().call();
				for (String submoduleName : submodules.keySet()) {
					logger.info("\t" + submoduleName);
				}
				if(submodules.keySet().isEmpty()) {
					logger.warn("No Submodules defined in branch "+repository.getBranch());
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
						logger.info("submodule " + walk.getModuleName());
						List<Ref> call = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
						for (Ref ref : call) {
							logger.info("\tBranch: " + ref + " " + ref.getName() + " "
									+ ref.getObjectId().getName() + " " + ref.isSymbolic());

						}
					}
				}
			}
		}
	}

	/**
	 * Collect the name of all branches that are active in any of the submodules declared in the main branch of the root repository
	 */
	public Set<String> collectAllSubmodulesActiveRemoteBranches(int inactivityThreshold) throws IOException, GitAPIException {
		Set<String> remoteBranchesNames = new HashSet<String>();
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		
		SimpleDateFormat shortDateFormat = new SimpleDateFormat("yyyy-MM-dd");
		ZonedDateTime now = ZonedDateTime.now();
		ZonedDateTime inactivityThresholdDate = now.plusDays(-inactivityThreshold);
		boolean useInactivityThreshold = inactivityThreshold >= 0; 

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {

			try (SubmoduleWalk walk = SubmoduleWalk.forIndex(parentRepository)) {
				while (walk.next()) {
					Repository submoduleRepository = walk.getRepository();
					try (Git submodulegit = Git.wrap(submoduleRepository)) {
						logger.info("remote branches in submodule " + walk.getModuleName() + ":");
						List<Ref> branches = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
						for (Ref branch : branches) {
							if (branch.getName().startsWith("refs/remotes/origin/")) {
								String branchName = branch.getName().substring("refs/remotes/origin/".length());
								// find branch age
								RevWalk walkSubModuleGit = new RevWalk(submodulegit.getRepository());
								RevCommit latestCommit = walkSubModuleGit.parseCommit(branch.getObjectId());
								
								//RevCommit latestCommit = submodulegit.log().setMaxCount(1).call().iterator().next();
								Date latestCommitDate = latestCommit.getAuthorIdent().getWhen();
								if (useInactivityThreshold) {
									boolean isActiveBranch = !latestCommitDate.toInstant().isBefore(inactivityThresholdDate.toInstant());
									if(isActiveBranch) {
										remoteBranchesNames.add(branchName);
									}
									logger.info(String.format("\t%-32s is %8s since %s \t", branchName,
											isActiveBranch
													? "ACTIVE"
													: "INACTIVE",
											shortDateFormat.format(latestCommitDate),
											latestCommit.getShortMessage()));
								} else {
									logger.info("\t" + branchName);
									remoteBranchesNames.add(branchName);
								}
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
					if (ref.getName().startsWith("refs/remotes/origin/")) {
						String branchName = ref.getName().substring("refs/remotes/origin/".length());
						if (!relevantBranches.contains(branchName) && !branchName.equals(masterBranchName)) {
							logger.info("Pushing deletion of branch " + ref.getName() );
							// delete locally
							parentgit.branchDelete().setBranchNames(ref.getName()).setForce(true).call();
							// delete remotely too
							RefSpec refSpec = new RefSpec().setSource(null).setDestination("refs/heads/" + branchName);
							Iterable<PushResult> res = parentgit.push()
									.setRefSpecs(refSpec)
									.setRemote("origin")
									.setCredentialsProvider(credentialProvider).call();
							for (PushResult pushRes : res) {
								for (RemoteRefUpdate refUpdate : pushRes.getRemoteUpdates()) {
									if (refUpdate.getStatus() != RemoteRefUpdate.Status.OK) {
										logger.error("\t\tFailed to push deletion of "+ref.getName()+ " : " + refUpdate.getMessage() + " ; " + pushRes.getRemoteUpdates());
									}
								}
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
					if (ref.getName().startsWith("refs/remotes/origin/")) {
						String branchName = ref.getName().substring("refs/remotes/origin/".length());
						parentBranches.add(branchName);
					}
				}
				Set<String> missingParentBranches = new HashSet<String>();
				missingParentBranches.addAll(relevantBranches);
				missingParentBranches.removeAll(parentBranches);
				logger.info("Missing parent branches :" + missingParentBranches);
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
				logger.info("Removing branch before");
				parentgit.branchDelete()
					.setBranchNames(missingParentBranch)
					.setForce(true)
					.call();
				break;
			}
		}

		// create local branch
		parentgit.branchCreate().setName(missingParentBranch).call();

		logger.info("Pushing new branch "+missingParentBranch+"...");
		// push branch to remote
		Iterable<PushResult> res = parentgit.push().setRemote("origin")
				.setRefSpecs(new RefSpec(missingParentBranch + ":" + missingParentBranch))
				.setCredentialsProvider(credentialProvider).call();

		for (PushResult pushRes : res) {
			//logger.info(pushRes + " ; " + pushRes.getMessages() + " ; " + pushRes.getRemoteUpdates());
			for (RemoteRefUpdate refUpdate : pushRes.getRemoteUpdates()) {
				if (refUpdate.getStatus() != RemoteRefUpdate.Status.OK) {
					logger.error("\t\tFailed to push new branch "+missingParentBranch+ " : " + refUpdate.getMessage() + " ; " + pushRes.getRemoteUpdates());
				} else {
//					// some msg from the remote git repo
//					if(pushRes.getMessages() !=  null && ! pushRes.getMessages().isEmpty()) {
//						logger.info(pushRes.getMessages());
//					}
				}
			}
			validateRemoteRefUpdates("push new remote branch", pushRes.getRemoteUpdates());
		}
	}

	/**
	 * 
	 * @param dryRun report only, do not perform changes
	 * @throws IOException
	 * @throws GitAPIException
	 * @throws GitSyncError
	 * @throws ConfigInvalidException
	 */
	public void updateAllBranchesModules(StringBuffer reportBuffer, boolean dryRun) throws IOException, GitAPIException, GitSyncError, ConfigInvalidException {
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		try (Repository parentRepository = builder.setMustExist(true).setGitDir(new File(localGitFolder + "/.git"))
				.readEnvironment() // scan environment GIT_* variables
				.findGitDir() // scan up the file system tree
				.build()) {
			try (Git parentgit = new Git(parentRepository)) {
				List<Ref> call = parentgit.branchList().setListMode(ListMode.REMOTE).call();
				for (Ref ref : call) {
					if (ref.getName().startsWith("refs/remotes/origin/")) {
						updateBranchesForModules(parentgit,
								ref.getName().substring("refs/remotes/origin/".length()),
								reportBuffer,
								dryRun);
					}
				}
			}
		}
	}
	

	/**
	 * 
	 * 
	 * @param parentgit
	 * @param consideredBranch
	 * @throws GitAPIException
	 * @throws GitSyncError
	 * @throws IOException
	 * @throws ConfigInvalidException
	 */
	public void updateBranchesForModules(Git parentgit, String consideredBranch, StringBuffer reportBuffer, boolean dryRun)
			throws GitAPIException, GitSyncError, IOException, ConfigInvalidException {
		logger.info("updateBranchesForModules branch = " + consideredBranch);
		reportBuffer.append(String.format("**Branch %s**\n",  consideredBranch));
		reportBuffer.append("\n"
				+ "| Module                           | Branch           |\n"
				+ "|:----------                       |:----------       |\n");
		// switch parentGit to branch
		checkoutBranch(parentgit, consideredBranch);
		
		
		// for each submodule check if it must use master or specific branch
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(parentgit.getRepository())) {
			while (walk.next()) {
				Repository submoduleRepository = walk.getRepository();
				try (Git submodulegit = Git.wrap(submoduleRepository)) {
					// logger.info("remote branches in submodule "+walk.getModuleName()+":");
					String trackedBranchName = masterBranchName;
					Ref trackedBranchRef = null;
					List<Ref> branches = submodulegit.branchList().setListMode(ListMode.REMOTE).call();
					for (Ref ref : branches) {
						if (ref.getName().startsWith("refs/remotes/origin/")) {
							String branchName = ref.getName().substring("refs/remotes/origin/".length());
							if (branchName.equals(consideredBranch)) {
								trackedBranchName = consideredBranch;
								trackedBranchRef = ref;
								break;
							}
							if(trackedBranchRef == null && branchName.equals(trackedBranchName)) {
								// use the default branch is necessary
								trackedBranchRef = ref;
							}
						}
					}
					logger.info(String.format("  tracking module %-32s on branch "+trackedBranchName, walk.getModuleName()));
					
						
					// Make sure the parent repo knows that its submodule now tracks a branch:
					FileBasedConfig modulesConfig = new FileBasedConfig(new File(
							parentgit.getRepository().getWorkTree(), Constants.DOT_GIT_MODULES), parentgit.getRepository().getFS());
					modulesConfig.load();
					modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, walk.getModulesPath(),
							ConfigConstants.CONFIG_BRANCH_SECTION, trackedBranchName);
					modulesConfig.save();
					// Make sure your submodule is actually at the latest of that branch:
					checkoutBranch(submodulegit, trackedBranchName);
					// record the new state of your submodule in your parent repo:
					logger.debug("\t\tgit add " + walk.getModulesPath());
					parentgit.add()
						.addFilepattern(walk.getModulesPath())
						.call();
					logger.debug("\t\tgit add " + Constants.DOT_GIT_MODULES);
					parentgit.add()
						.addFilepattern(Constants.DOT_GIT_MODULES)
						.call();
					
					Status status = parentgit.status().call();
					if(!status.isClean()) {
						logger.debug("\t\tAdded: " + status.getAdded());
						logger.debug("\t\tChanged: " + status.getChanged());
						logger.debug("\t\tConflicting: " + status.getConflicting());
		                logger.debug("\t\tConflictingStageState: " + status.getConflictingStageState());
		                logger.debug("\t\tIgnoredNotInIndex: " + status.getIgnoredNotInIndex());
		                logger.debug("\t\tMissing: " + status.getMissing());
		                logger.debug("\t\tModified: " + status.getModified());
		                logger.debug("\t\tRemoved: " + status.getRemoved());
		                logger.debug("\t\tUntracked: " + status.getUntracked());
		                logger.debug("\t\tUntrackedFolders: " + status.getUntrackedFolders());
					}
					String branchModifier = "";
					if(status.getAdded().size() + status.getChanged().size() +status.getRemoved().size() > 0) {
						String msg;
						PersonIdent committer;
						if(trackedBranchRef != null) {
							RevWalk walkSubModuleGit = new RevWalk(submodulegit.getRepository());
							RevCommit latestCommit = walkSubModuleGit.parseCommit(trackedBranchRef.getObjectId());
							//logger.info(String.format("\t\t%s %s", latestCommit.getAuthorIdent().getEmailAddress(), latestCommit.getShortMessage()));
							msg = String.format("[%s#%s] %s\n\n%s",
									walk.getModuleName(),
									trackedBranchName,
									latestCommit.getShortMessage(),
									"Updating submodule "+walk.getModuleName()+" to track head of branch "+trackedBranchName
									);
							committer = latestCommit.getAuthorIdent();
						} else {
							msg = "Updating submodule "+walk.getModuleName()+" to track head of branch "+trackedBranchName;
							committer = defaultCommitter;
						}
						branchModifier = "🔄";
						if(! dryRun) {
							logger.debug("\t\tgit commit -m \""+msg+"\"");
							parentgit.commit()
								.setMessage(msg)
								.setAllowEmpty(false)
								.setCommitter(committer)
								.call();
						} else {
							logger.info("\t\t[DRYRUN] git commit -m \""+msg+"\"");
						}
					}
					reportBuffer.append(String.format("| %-32s | %s %-16s |\n", walk.getModuleName(),branchModifier, trackedBranchName));
				}
			}
			
			/*Collection<String> submoduleUpdateRes = new SubmoduleUpdateCommand(parentgit.getRepository()).call();
			for (String s : submoduleUpdateRes) {
				logger.info(
						"\tupdating submodules: " + s);
			}*/
			if(!dryRun) {
				Iterable<PushResult> pushResps = parentgit.push()
					.setCredentialsProvider(credentialProvider)
					.call();
				for (PushResult pushRes : pushResps) {
					for (RemoteRefUpdate pushResult : pushRes.getRemoteUpdates()) {
						if(pushResult.getStatus() == RemoteRefUpdate.Status.OK) {
							logger.info("push branch "+consideredBranch+" => "+RemoteRefUpdate.Status.OK);
						} else if(pushResult.getStatus() == RemoteRefUpdate.Status.UP_TO_DATE) {
							logger.info("nothing to push for branch "+consideredBranch+" => "+RemoteRefUpdate.Status.UP_TO_DATE);
							
						} else {
							logger.error("PB pushing branch "+consideredBranch+" => "+pushRes.getMessages()+"\" "+pushResult);
						}
					}
					validateRemoteRefUpdates("push submodule tracking branch", pushRes.getRemoteUpdates());
				}
			} else {
				logger.info("\t\t[DRYRUN] not pushing branch "+consideredBranch);
			}
			reportBuffer.append("\n");
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
			if (ref.getName().startsWith("refs/remotes/origin/")) {
				String remotebranchName = ref.getName().substring("refs/remotes/origin/".length());
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
		throw new GitSyncError("Checkout failed, No branch local or remote branch named "+branchName+" found in "+git.getRepository().getWorkTree());
	}

	/**
	 * Check references updates for any errors
	 *
	 * @param errorPrefix The error prefix for any error message
	 * @param refUpdates  A collection of remote references updates
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
