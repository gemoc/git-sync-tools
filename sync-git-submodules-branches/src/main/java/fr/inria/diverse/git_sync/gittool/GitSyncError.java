package fr.inria.diverse.git_sync.gittool;

public class GitSyncError extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2696986781486097409L;

	public GitSyncError(String format) {
		super(format);
	}

}
