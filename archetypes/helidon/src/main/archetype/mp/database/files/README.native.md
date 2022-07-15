The generation of native binaries requires an installation of GraalVM 22.1.0+.

In order to produce a native binary, you must run the {{db}} Database as a separate process
and use a network connection for access. The simplest way to do this is by starting a Docker
container as follows:

```
{{readme-native-docker}}
```

The resulting container will listen to port 1521 for network connections.
Switch property `javax.sql.DataSource.test.dataSource.url` in `microprofile-config.properties`
to use a TCP connection:

```
{{readme-native-url}}
```

Next, uncomment the following dependency in your project's pom file:

```
<dependency>
    <groupId>io.helidon.integrations.db</groupId>
    <artifactId>{{integration-artifactId}}</artifactId>
</dependency>
```

With all these changes, re-build your project and verify that all tests are passing.
Finally, you can build a native binary using Maven as follows:

```
mvn -Pnative-image install -DskipTests
```

The generation of the executable binary may take several minutes to complete
depending on your hardware and operating system --with Linux typically outperforming other
platforms. When completed, the executable file will be available
under the `target` directory and be named after the artifact ID you have chosen during the
project generation phase.
