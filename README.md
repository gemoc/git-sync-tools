# git-sync
Scripts used to help to synchronize git repositories when following some naming convention on branches


## Rationnale

In some large projects the code is split in several repositories. 
The developpers have to aggregate several repositories in  order to build the full aplication and run system tests (typically running user stories).

Technologies like gitlab pipeline or jenkins works fine with multiple git branches only on a single git repository. Thus the system test cannot easily be acheived on the aggregate for each of the development branches.

This tools aims to enable CI build (and test) for branches even if distributed accross several repositories.

Typical example:

- Repo-A
- Repo-B
- Repo-C
- Integration-Repo

Repo-A, Repo-B and Repo-C contains the source tha need to be aggregated
Integration-Repo is a git repo with 3 submodules (one submodule for each of the Repo-x) defined in it master branch.

The goal of the script is to:
- automatically, use the latest commit of each of the master branch of the all Repo-x in the submodule ofmaster branch of Integration-Repo
- if one of the Repo-x define a branch, then create a branch in Integration-Repo with the same name
- cleanu any unused branches from Integration-Repo if none of the Repo-x defines such branch

## Compiling

```sh
mvn clean install
```

## Usage


