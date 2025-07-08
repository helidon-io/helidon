# Releasing Helidon

These are the steps for doing a release of Helidon. These steps 
will use release 0.7.0 in examples. Of course, you are not releasing
0.7.0, so make sure to change that release number to your release
number when copy/pasting.

## Overview

The Helidon release workflow is triggered when a change is pushed to
a branch that starts with `release-`. The release workflow performs
a Maven release to the [Central Publishing Portal](https://central.sonatype.org/publish/publish-portal-guide/).
It does not currently do a GitHub release, so you must do that manually
using a script. Here is the overall flow:

1. Create a local release branch
2. Update CHANGELOG and verify version is correct
3. Push release branch to upstream, release workflow runs
4. Verify bits in Central Deployment repository and then publish them
5. Create GitHub release
6. Increment version in main and update changelog

## Steps in detail

```shell
# Set this to the version you are releasing
export VERSION="0.7.0"
```

1. Create local release branch
    ```shell
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

3. Push local release branch to upstream. This will trigger a release workflow.

    ```
    git push origin release-${VERSION}
    ```

4. Wait for release build to complete:

   https://github.com/helidon-io/helidon/actions/workflows/release.yaml

5. Check Central Portal for deployment
    1. In browser go to: https://central.sonatype.com/publishing and login as helidonrobot.
    2. Click on Deployments tab and you should see the Deployment listed (io-helidon-x.y.z)
    3. Status should be "Validated". You can explore the Deployment Info to see staged artifacts
    
6. Test staged bits    
    1. Do quick smoke test by trying an archetype that is in the staging repo (see staging repository profile at end of this document)
    
        ```shell
        mvn -U archetype:generate -DinteractiveMode=false \
            -DarchetypeGroupId=io.helidon.archetypes \
            -DarchetypeArtifactId=helidon-quickstart-se \
            -DarchetypeVersion=${VERSION} \
            -DgroupId=io.helidon.examples \
            -DartifactId=quickstart-se \
            -Dpackage=io.helidon.examples.quickstart.se \
            -Pcentral.manual.testing
        
        cd quickstart-se
        
        mvn package -Pcentral.manual.testing
        ```
    2.  Download artifact bundle from GitHub Release workflow run (io-helidon-artifacts-x.y.z) and do any additional testing on the bundle such as virus scans.
       
7. Release publishing: In the portal UI select the deployment then click Publish
   1. It might take a while (possibly an hour) before the release appears in Maven Central
   2. To check on progress look at https://repo1.maven.org/maven2/io/helidon/helidon-bom/
       
8. Create GitHub release
   1. Create a fragment of the change log that you want used for the release
      description on the GitHub Releases page. Assume it is in `/tmp/change-frag.md`
   2. Set your API key (you generate this on your GitHub Settings):
      ```shell
      export GITHUB_API_KEY=<longhexnumberhere.....>
      ```
   3. Run script to create release in GitHub:
      ```shell
      etc/scripts/github-release.sh --changelog=/tmp/change-frag.md --version=${VERSION}
      ```
   4. Go to https://github.com/oracle/helidon/releases and verify release looks like
      you expect. You can edit it if you need to.

9. Update version and CHANGELOG in main
   1. Create post release branch: `git checkout -b post-release-${VERSION}`
   2. Copy CHANGELOG from your release branch. Add empty Unrelease section.
   3. Update SNAPSHOT version number. Remember to use your version number!
      ```shell
      etc/scripts/release.sh --version=0.7.1-SNAPSHOT update_version
      ```
      If you perfromed a Milestone release you will likely leave the 
      SNAPSHOT version in main alone.
   4. Add and commit changes then push
      ```shell
      git push origin post-release-${VERSION}
      ```
   5. Create PR and merge into main

10. Now go to helidon-site and look at the RELEASE.md there to release the website with updated docs

# Staging Repository Profile

To pull artifacts from the Central Portal staging repository add this to your `settings.xml`:

The BEARER_TOKEN must be that for the user that uploaded the release -- typically helidonrobot.
For general information concerning BEARER_TOKEN see 
* https://central.sonatype.org/publish/generate-portal-token/
* https://central.sonatype.org/publish/publish-portal-api/#authentication-authorization
* https://central.sonatype.org/publish/publish-portal-api/#manually-testing-a-deployment-bundle

```xml
  <servers>
   <server>
      <id>central.manual.testing</id>
      <configuration>
         <httpHeaders>
            <property>
               <name>Authorization</name>
               <value>Bearer ${BEARER_TOKEN}</value>
            </property>
         </httpHeaders>
      </configuration>
   </server>
</servers>

<profiles>
<profile>
   <id>central.manual.testing</id>
   <repositories>
      <repository>
         <id>central.manual.testing</id>
         <name>Central Testing repository</name>
         <url>https://central.sonatype.com/api/v1/publisher/deployments/download</url>
      </repository>
   </repositories>
   <pluginRepositories>
      <pluginRepository>
         <id>central.manual.testing</id>
         <name>Central Testing repository</name>
         <url>https://central.sonatype.com/api/v1/publisher/deployments/download</url>
      </pluginRepository>
   </pluginRepositories>
</profile>
</profiles>
```
