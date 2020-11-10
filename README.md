# git-sync
Scripts used to help to synchronize git repositories when following some naming convention on branches


## Rationnale

In some large projects the code is split in several repositories. 
The developpers have to aggregate several repositories in  order to build the full aplication and run system tests (typically running user stories).

Technologies like gitlab pipeline or jenkins works fine with multiple git branches only on a single git repository. Thus the system test cannot easily be acheived on the aggregate for each of the development branches.

This tools aims to enable CI build (and test) for branches even if distributed accross several repositories.

## Compiling

```sh
mvn clean install
```

## Usage


