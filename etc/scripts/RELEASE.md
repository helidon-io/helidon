
# Releasing Helidon

These are the steps for doing a release of Helidon. These steps 
will use release 0.7.0 in examples. Of course you are not releasing
0.7.0, so make sure to change that release number to your release
number when copy/pasting.

# Overview

The Helidon release pipeline is triggered when a change is pushed to
a branch that starts with `release-`. The release pipeline performs
a Maven release to the Nexus staging repository. It does not currently
do a GitHub release, so you must do that manually using a script. Here
is the overall flow:

1. Create a local release branch
2. Update CHANGELOG and verify version is correct
3. Push release branch to upstream, release pipeline runs
4. Verify bits in Nexus staging repository and then release them
5. Create GitHub release
6. Increment version in master and update changelog

# Steps in detail

```
# Set this to the version you are releasing
export VERSION="0.7.0"
```


1. Create local release branch
    ```
    git clone git@github.com:oracle/helidon.git
    git checkout -b release-${VERSION}
    ```
2. Update local release branch
   1. Update version if needed (see step 7.3). For Milestone releases
      (2.0.0-M1) you'll want to change the version to something like
      2.0.0-M1-SNAPSHOT. For GA releases you probably don't need to
      touch the version at all (since it is probably already
      2.0.0-SNAPSHOT).
   2. Update `CHANGELOG`
      1. Move "Unreleased" to new section for the `0.7.0` release
      2. Update new section with latest info
      3. Add release to dictionary at bottom of CHANGELOG
   3. Commit changes locally

3. Push local release branch to upstream. This will trigger a release build in Wercker.

    ```
    git push origin release-${VERSION}
    ```

4. Wait for build pipeline in Wercker to complete

   https://app.wercker.com/Helidon/helidon/runs

5. Check nexus staging repository
    1. In browser go to: https://oss.sonatype.org/#view-repositories and login
       as helidonrobot.
    2. On left click "Staging Repositories"
    3. Scroll down list of repositories and find `iohelidon-`. It should be Status closed.
    
6. Test staged bits    
    1. Do quick smoke test by trying an archetype that is in the staging
       repo (see staging repository profile at end of this document)
    
        ```
        mvn -U archetype:generate -DinteractiveMode=false \
            -DarchetypeGroupId=io.helidon.archetypes \
            -DarchetypeArtifactId=helidon-quickstart-se \
            -DarchetypeVersion=${VERSION} \
            -DgroupId=io.helidon.examples \
            -DartifactId=quickstart-se \
            -Dpackage=io.helidon.examples.quickstart.se \
            -Possrh-staging
        
        cd quickstart-se
        
        mvn package -Possrh-staging
        ```
        
    2. Do full smoke test using test script (this requires staging profile to
       be configured):
       ```
       smoketest.sh --giturl=https://github.com/oracle/helidon.git --version=${VERSION} --clean --staged full
       ```
    3. The smoketest script will leave its work in `/var/tmp/helidon-smoke.XXXX`.
       Go there, into the quickstarts and test the native builds and Docker
       builds (for Docker builds you'll need to update the pom to include
       the staging repositories.
       
6. Release repository: Select repository then click Release (up at the top)
   1. In description you can put something like "Helidon 0.7.0 Release"
   2. It might take a while (possibly hours) before the release appears in Maven Central
   3. To check on progress look at https://repo1.maven.org/maven2/io/helidon/helidon-bom/
       
6. Create GitHub release
   1. Create a fragment of the change log that you want used for the release
      description on the GitHub Releases page. Assume it is in `/tmp/change-frag.md`
   2. Set your API key (you generate this on your GitHub Settings):
      ```
      export GITHUB_API_KEY=<longhexnumberhere.....>
      ```
   3. Run script to create release in GitHub:
      ```
      etc/scripts/github-release.sh --changelog=/tmp/change-frag.md --version=${VERSION}
      ```
   4. Go to https://github.com/oracle/helidon/releases and verify release looks like
      you expect. You can edit it if you need to.

7. Update version and CHANGELOG in master
   1. Create post release branch: `git checkout -b post-release-${VERSION}`
   2. Copy CHANGELOG from your release branch. Add empty Unrelease section.
   3. Update SNAPSHOT version number. Remember to use your version number!
      ```
      etc/scripts/release.sh --version=0.7.1-SNAPSHOT update_version
      ```
      If you perfromed a Milestone release you will likely leave the 
      SNAPSHOT version in master alone.
   4. Add and commit changes then push
      ```
      git push origin post-release-${VERSION}
      ```
   5. Create PR and merge into master

8. Now go to helidon-site and look at the RELEASE.md there to release the website with updated docs

# Staging Repository Profile

To pull artifacts from the sonatype staging repository add this profile to your `settings.xml`:

```
      <profile>
           <id>ossrh-staging</id>
           <activation>
               <activeByDefault>false</activeByDefault>
           </activation>
           <repositories>
               <repository>
                   <id>ossrh-staging</id>
                   <name>OSS Sonatype Staging</name>
                   <url>https://oss.sonatype.org/content/groups/staging/</url>
                   <snapshots>
                       <enabled>false</enabled>
                   </snapshots>
                   <releases>
                       <enabled>true</enabled>
                   </releases>
               </repository>
           </repositories>
           <pluginRepositories>
               <pluginRepository>
                   <id>ossrh-staging</id>
                   <name>OSS Sonatype Staging</name>
                   <url>https://oss.sonatype.org/content/groups/staging/</url>
                   <snapshots>
                       <enabled>false</enabled>
                   </snapshots>
                   <releases>
                       <enabled>true</enabled>
                   </releases>
               </pluginRepository>
           </pluginRepositories>
       </profile>
```
