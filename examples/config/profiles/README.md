# Helidon Config Profiles Example

This example shows how to load configuration from multiple
configuration sources using profiles.

This example contains the following profiles:

1. no profile - if you start the application with no profile, the usual `src/main/resources/application.yaml` will be used
2. `local` - `src/main/resources/application-local.yaml` will be used
3. `dev` - has an explicit profile file `config-profile-dev.yaml` on classpath that defines an inlined configuration
4. `stage` - has an explicit profile file `config-profile-stage.yaml` on classpath that defines a classpath config source
4. `prod` - has an explicit profile file `config-profile-prod.yaml` on file system that defines a path config source

To switch profiles
- either use a system property `config.profile`
- or use an environment variable `HELIDON_CONFIG_PROFILE`


## How to run this example:

Build the application
```shell
mvn clean package
```

Run it with a profile
```shell
java -Dconfig.profile=prod -jar target/helidon-examples-config-profiles.jar
```

Changing the profile name should use different configuration.