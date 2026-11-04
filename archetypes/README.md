# Archetypes Development

Helidon provides a set of archetypes to bootstrap end user's application development journey.

See https://github.com/helidon-io/helidon-build-tools/wiki/Archetype-Engine-V2

## Build

From the root `archetypes` directory, just issue

```bash
mvn clean install
```

This will build all archetypes (including legacy) and run tests.

To save time, during development cycle, you can skip test using:
```bash
mvn clean install -DskipTests -e
```

The build output shows instructions on how to use the archetypes locally.

## Smoke Tests

Generate the projects and validate the pom files.
```shell
mvn clean install -Darchetype.test.testGoal=clean -e
```

The CLI tracing provider regression tests run during `verify`. They check that
both SE and MP reject explicit Jaeger and Zipkin choices and accept OpenTelemetry,
including when the provider is omitted. To run these tests without the generated
application test matrix, from the root `archetypes` directory run:

```shell
mvn -pl archetypes clean verify -Darchetype.test.skip=true
```

Download the previous archetype test projects and diff them:
```shell
.projects-diff.sh \
  --actual=./target/tests \
  --orig=$HOME/Download/archetype-tests \
  diff_projects
```
