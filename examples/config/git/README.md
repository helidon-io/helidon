# Helidon Config Git Example

This example shows how to load configuration from a Git repository
and switch which branch to load from at runtime.

## Prerequisites

The example assumes that the GitHub repository <https://github.com/helidonrobot/test-config>
has a branch named `test` that contains `application.conf` which sets the key
`greeting` to value `hello`. (The Helidon team has created and populated this 
repository.)

The code in [`Main.java`](./src/main/java/io/helidon/config/examples/git/Main.java)
uses the environment variable `ENVIRONMENT_NAME` to fetch the branch name
in the GitHub repository to use; it uses `master` by default (which does _not_ 
contain the expected value).

The example application constructs a `Config` instance from that file in the 
GitHub repository and branch, prints out the value for key `greeting`, and 
checks to make sure the value is the expected `hello`.

## Build and run

```bash
mvn package
export ENVIRONMENT_NAME=test
java -jar target/helidon-examples-config-git.jar
```
