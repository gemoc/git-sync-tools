# git-sync-tools
Scripts used to help to synchronize git repositories when following some naming convention on branches

The main tools `sync-git-submodules-branches` allows to automate the managment of branches of a git repository thats integrates the content of several other repositories via submodules.


## Rationnale

In some large projects the code is split in several repositories. 
The developpers have to aggregate several repositories in  order to build the full aplication and run system or integration tests (.

Technologies like gitlab pipeline or jenkins works fine with multiple git branches only on a single git repository. Thus the system tests cannot easily be acheived on the aggregate for each of the development branches.

This tools aims to enable CI build (and test) for branches even if distributed accross several repositories.

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
        <groupId>fr.inria.diverse</groupId>
        <artifactId>git-sync</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <configuration>
        	<parentGitURL>git@github.com:myorganisation/integration-repo.git</parentGitURL> <!-- replace here with the git url of your Integration-Repo --> 
        	<userOrToken>${gituser.name}</userOrToken>
        	<password>${gituser.password}</password>
        	<committerName>${gituser.name}</committerName>
        	<committerEmail>${gituser.email}</committerEmail>
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
  	<pluginRepositories>
		<pluginRepository>
			<id>maven-inria-release</id>
			<name>maven.inria.fr</name>
			<releases>
				<enabled>true</enabled>
				<updatePolicy>always</updatePolicy>
				<checksumPolicy>warn</checksumPolicy>
			</releases>
			<snapshots>
				<enabled>false</enabled>
				<updatePolicy>always</updatePolicy>
				<checksumPolicy>fail</checksumPolicy>
			</snapshots>
			<url>http://maven.inria.fr/artifactory/public-release</url>
			<layout>default</layout>
		</pluginRepository>
		<pluginRepository>
			<id>maven-inria-snapshot</id>
			<name>maven.inria.fr</name>
			<releases>
				<enabled>false</enabled>
				<updatePolicy>always</updatePolicy>
				<checksumPolicy>warn</checksumPolicy>
			</releases>
			<snapshots>
				<enabled>true</enabled>
				<updatePolicy>always</updatePolicy>
				<checksumPolicy>fail</checksumPolicy>
			</snapshots>
			<url>http://maven.inria.fr/artifactory/public-snapshot</url>
			<layout>default</layout>
		</pluginRepository>
	</pluginRepositories>
</project>    
```


then configure a CI to either:
- periodically launch this script (via a `mvn clean verify`) 
  the script must be launched with the following variable (use hidden variable as this is credentials) allowing to commit in the Integration-Repo
  (I strongly suggest using a bot account instead of your own account)
  - GITUSER_NAME
  - GITUSER_PASSWORD
- or configure each of the Repo-x to trigger a build (using for ex webhooks)


The root of the Integration-Repo can then contains a CI specific configuration file (Jenkinsfile or .gitlab-ci.yml) to build the entire application with a checkout of all the sources.


## Example scenario

From the following repositories,
![scenario-step1](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/gemoc/git-sync-tools/master/doc/plantuml/scenario_step1.plantuml)

Applying the tool (if all branches are active in the submodules) will result in the following repositories.

![scenario-step2](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/gemoc/git-sync-tools/master/doc/plantuml/scenario_step2.plantuml)


