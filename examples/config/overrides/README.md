# Helidon Config Overrides Example

This example shows how to load configuration from multiple 
configuration sources, specifically where one of the sources _overrides_ other
sources.

The application treats 
[`resources/application.yaml`](./src/main/resources/application.yaml) and 
[`conf/priority-config.yaml`](./conf/priority-config.yaml)
as the config sources for the its configuration. 

The `application.yaml` file is packaged along with the application code, while
the files in `conf` would be provided during deployment to tailor the behavior
of the application to that specific environment.

The application also loads 
[`conf/overrides.properties`](./conf/overrides.properties) but as an 
_override_ config
source. This file contains key _expressions_ (including wildcards) and values which
take precedence over the settings in the original config sources.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-config-overrides.jar
```
