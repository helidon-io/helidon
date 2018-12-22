
# Helidon Config Git Example

This example shows how to load configuration from a Git repository
and switch which branch to load from at runtime.

## Build

```
mvn package
```

## Run

```
export ENVIRONMENT_NAME=test
mvn exec:java
```

Note that the application determines which Git branch to load from
based on the `ENVIRONMENT_NAME` environment variable.
