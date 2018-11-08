# Helidon Javadocs

Aggregated javadocs for the Helidon Components.

## Requirements

The Maven `sources.jar` are required to aggregate the javadocs.
When building locally against SNAPSHOT, you can generate the `sources.jar` for all
components by doing a top level build with `-Psources`.

## Building the javadocs

```bash
# Cd to the project root
$ mvn install  -Psources
$ cd javadocs ; mvn generate-sources
```
