# Archetypes Development

Helidon provides a set of archetypes to bootstrap end user's application development journey. They are implemented here based on 
[Archetype V2 engine] (https://github.com/helidon-io/helidon-build-tools/wiki/Archetype-Engine-V2)

## Build

From the root `archetypes` directory, just issue

```bash
mvn clean install
```

This will build all archetypes (including legacy) and run tests. To save time, during development cycle, you can skip test using

```bash
mvn clean install  -Darchetype.test.skip=true
```

## Test

Archetypes build generates `cli-data` that you can provide to installed [Helidon CLI](../README.md#helidon-cli).

```bash
helidon init --reset --url file:///<path-to>/helidon/archetypes/helidon/target/cli-data
```

Once the archetype is selected the other options have defaults (and allows user to make selections) and the project is generated in a directory named after the `artifactId` value.

```bash
cd `artifactId`
```

Now, just follow instructions in the generated README file.
