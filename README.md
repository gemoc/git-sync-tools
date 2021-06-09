# git-sync-tools
Scripts used to help to synchronize git repositories when following some naming convention on branches

The main tools `sync-git-submodules-branches` allows to automate the managment of branches of a git repository thats integrates the content of several other repositories via submodules.


## Rationnale

In some large projects the code is split in several repositories. 
The developpers have to aggregate several repositories in  order to build the full aplication and run system or integration tests (.

Technologies like gitlab pipeline or jenkins works fine with multiple git branches only on a single git repository. Thus the system tests cannot easily be acheived on the aggregate for each of the development branches.

This tools aims to enable CI build (and test) for branches even if the code is distributed accross several repositories.

This makes possible checking that API/framework changes are taken into account by the main components using it.

Typical example:

- Repo-A
- Repo-B
- Repo-C
- Integration-Repo

Repo-A, Repo-B and Repo-C contain the source that need to be aggregated

Integration-Repo is a git repo with 3 submodules (one submodule for each of the Repo-x) defined in it master branch.

The goal of the script is to:
- automatically, use the latest commit of each of the master branch of the all Repo-x in the submodule ofmaster branch of Integration-Repo
- if one of the Repo-x define a branch (for example named _foo_), then create a branch in Integration-Repo with the same name (_foo_), in the branch, add a submodule for each of the Repo-x pointing either to the branch with this name or to the master branch.
- cleanup any unused branches from Integration-Repo if none of the Repo-x defines such branch
- automatically, use the latest commit of each of the indicated branches of all submodules of all branches of Integration-Repo

## Compiling

```sh
cd git-sync
mvn clean install
```
(in case of error with gpg, make sure to enable your local gpg-agent with your personal key. For example using a command such as `echo "test" | gpg --clearsign`) 


![maven workflow](https://github.com/dvojtise/git-sync/actions/workflows/maven.yml/badge.svg)


## Usage


Fisrt define the sub modules in the master branch of the integration repository. (use `git submodule add <URL of Repo-A>`)

Add a `pom.xml` in the repository (in a subfolder, eg. "scripts") with a content similar to:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>org.eclipse.gemoc-studio</groupId>
  <artifactId>sync-submodules</artifactId>
  <version>1.0-SNAPSHOT</version>

  <description>Project in charge of synchronizing branches of submodules for integration build</description>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <gituser.name>${env.GITUSER_NAME}</gituser.name>
    <gituser.password>${env.GITUSER_PASSWORD}</gituser.password>
    <gituser.email></gituser.email>
  </properties>

  <build>
    <plugins>
      <plugin>
        <groupId>org.gemoc.git-sync-tools</groupId>
        <artifactId>sync-git-submodules-branches-plugin</artifactId>
        <version>1.0.1</version>
        <configuration>
        	<parentGitURL>git@github.com:myorganisation/integration-repo.git</parentGitURL> <!-- replace here with the git url of your Integration-Repo --> 
        	<userOrToken>${gituser.name}</userOrToken>
        	<password>${gituser.password}</password>
        	<committerName>${gituser.name}</committerName>
        	<committerEmail>${gituser.email}</committerEmail>
        	<inactivityThreshold>90</inactivityThreshold> <!-- number of days without commit to consider a branch inactive-->
        </configuration>
        <executions>
          <execution>
            <id>synch</id>
            <phase>validate</phase>
            <goals>
              <goal>synch</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>    
```


then configure a CI to either:
- periodically launch this script (via a `mvn clean verify`) 
  the script must be launched with the following variable (use hidden variable as this is credentials) allowing to commit in the Integration-Repo
  (I strongly suggest using a bot account instead of your own account)
  - GITUSER_NAME
  - GITUSER_PASSWORD
- or configure each of the Repo-x to trigger a build (using for ex webhooks)


The root of the Integration-Repo can then contains a CI specific configuration file (Jenkinsfile, .gitlab-ci.yml, or github actions) to build the entire application with a checkout of all the sources from all the component repositories.

Tips: the integration repository is cloned into the `target` folder of the maven project. Doing a `mvn verify` will try to reuse the existing repository in order to save network bandwidth. 
In case of trouble, do a `mvn clean verify` to force a clone. 

## Example scenario

From the following repositories where the development  of a feature implies some changes in 3 repositories out ot 4:
![scenario-step1](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/gemoc/git-sync-tools/master/doc/plantuml/scenario_step1.plantuml)

Applying the tool (if all *feature* branches are active in the component repositories) will result in the following repositories.
It creates 1 branches in the *Integration* repository where all submodules points either to the corresponding branch or the *main* branch of the component repository.

It also takes care to update the submodules to point to the head of all considered branches. 
The CI is able to build the latest version of these 2 branches (*main* and *feature1*)

![scenario-step2](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/gemoc/git-sync-tools/master/doc/plantuml/scenario_step2.plantuml)


After some time, the *feature1* branch  becomes inactive (because its content is integrated in *main* or the development is suspended), 
and 2 new features are developed (each of them implying different repositories)  

The tool removes the *feature1* branch from the integration repository and creates 2 new branches. The CI is able to build the latest version of these 3 branches (*main*, *feature2*, and *feature3*).

![scenario-step3](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/gemoc/git-sync-tools/master/doc/plantuml/scenario_step3.plantuml)



### The GEMOC use case

In this use case, the *sync-git-submodules-branches* tool is in 2 places in order to have multibranch build pipeline enabled for both the official maintainers (ie. Eclipse commiters)
and the GEMOC community. Thus enabling circle of trust where community can still enjoy the use of continuous integration for their own development branches (*prototype_feature*) 
even if they aren't part of the eclipse organization. And Eclipse organization member can use the CI on their own branches (*official_feature*) (for example for validating pull requests 
coming from user not using a CI) 

![scenario-step3](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/gemoc/git-sync-tools/master/doc/plantuml/scenario_step3.plantuml)




