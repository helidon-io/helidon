# Upgrading GraalVM version

## Maven dependency
Go to `dependencies/pom.xml` and update the property `version.lib.graalvm` to the desired version.

This defines the dependencies used by Helidon native image feature used during native image build.

## Docker images
In `etc/dockerfiles`, update the version:

1. `build.sh` - update the `GRAALVM_VERSION` variable to the desired version
2. `Dockerfile.jdk11-graalvm` update the curl command to point to the correct tar
3. `Dockerfile.jdk11-graalvm-maven` update the `FROM` command to use the latest image
4. Run `build.sh` to create the docker images and push them to docker hub

## Examples
Fix `Dockerfile.native` and `README.md` in all examples that have them.

Update `FROM helidon/jdk11-graalvm-maven:version as build` to the desired version in Dockerfile.

## Examples
Fix `Dockerfile.native.mustache` and `README.md` in all archetypes that have them.

Update `FROM helidon/jdk11-graalvm-maven:version as build` to the desired version in Dockerfile.

## Documentation
Update `docs/common/guides/graalnative.adoc` - fix link and file name

## Tests
Update `etc/scripts/includes/pipeline-env.sh` to use the desired version 
(this requires work on build environment - this version must be installed)