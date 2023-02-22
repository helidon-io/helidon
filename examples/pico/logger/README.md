# pico-examples-logger

## Overview
This example compares Pico to Guice. It was taken from an example found on the internet (see references below). The example is fairly trivial, but it is sufficient to compare the similarities between the two, as well as to demonstrate the performance differences between the two.

[common](common) contains the core application logic.
[guice](guice) contains the delta for integrating to Guice.
[pico](pico) contains the delta for integrating with Pico.

Review the code to see the similarities and differences. Note that the generated source code is found under 
"./target/generated-sources". Summaries of similarities and differences are listed below.

# Building and Running
```
> mvn clean install
> ./run.sh
```

The [run.sh](./run.sh) script as shown above will spawn the Guice and Pico built applications twice (1st iteration for warmup).

<b>:DISCLAIMER:</b> **Results may vary** The below measurements were captured at the time of this revision (see git log)


# Notable

1. Pico supports jakarta.inject as well as javax.inject packaging. Guice (at the time of this writing) only supports javax.inject. This example therefore uses <i>javax.inject</i> in order to compare the two models even though it is recommended all switch to <i>jakarta.inject</i> if possible.

2. Guice is based upon reflection and at runtime uses it to determine the injection points and dependency graph. Pico is based upon compile-time code generation to generate (in code) the dependency graph. Both, however, support lazy/dynamic activation of services.

3. Pico provides the ability (as demonstrated in the [pom.xml](./pico/pom.xml)) to bind to the final injection model at assembly time. This option provides several benefits including deterministic behavior, speed & performance, and to helps ensure the completeness & validity of the entire application's dependency graph. Guice does not offer such an option.

4. Guice is considerable larger in terms of its memory consumption footprint.

```run.sh
...
Guice Main memory consumption = 13,194,048 bytes
...
Pico Main memory consumption = 7,930,352 bytes
...
```

5. Both applications are packaged with all of its transitive compile-time dependencies to showcase the differences in disk size. Pico is considerably smaller in terms of its disk consumption footprint:
```
> find . | grep dependencies | grep jar | xargs ls -l               
-rw-r--r--  1 jtrent  staff  3975690 Feb 21 20:43 ./guice/target/helidon-examples-pico-logger-guice-4.0.0-SNAPSHOT-jar-with-dependencies.jar
-rw-r--r--  1 jtrent  staff   403936 Feb 21 20:43 ./pico/target/helidon-examples-pico-logger-pico-4.0.0-SNAPSHOT-jar-with-dependencies.jar
```

6. Pico is considerably faster in terms of its end-to-end runtime. <i>Mileage will vary.</i>

```run.sh
...
Guice Main elapsed time = 293 ms
...
Pico Main elapsed time = 184 ms
...
```

7. Pico requires less coding as compared to Guice.

```
jtrent@jtrent-mac logger % find guice -type f  -not -path '*/.*' | grep -v target | wc
       4       4     230
jtrent@jtrent-mac logger % find pico -type f  -not -path '*/.*' | grep -v target | wc
       3       3     149
```

8. Pico offers lifecycle support (see jakarta.annotation.@PostConstruct, jakarta.annotation.@PreDestroy, Pico's @RunLevel
   annotations and PicoServices#shutdown()).

9. Pico generates a suggested <i>module-info.java</i> based upon analysis of your injection/dependency model (see /target/classes/module-info.java.pico).

TODO: replace this
```./target/classes/module-info.java.pico
// @Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
module helidon.examples.pico.logger.common {
    exports io.helidon.examples.pico.logger.common;
    // pico module - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    provides io.helidon.pico.Module with io.helidon.examples.pico.logger.common.picoModule;
    // pico external contract usage - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    requires helidon.examples.pico.logger.common;
    uses io.helidon.examples.pico.logger.common.CommunicationMode;
    uses io.helidon.examples.pico.logger.common.Communicator;
    uses jakarta.inject.Provider;
    uses javax.inject.Provider;
    // pico services - Generated(value = "io.helidon.pico.tools.DefaultActivatorCreator", comments = "version=1")
    requires transitive io.helidon.pico;
}
```

10. Pico can optionally generate the activators (i.e., the DI supporting classes) on an external jar module by using a maven plugin. Notice how [common](./common) is built, and then in the [pico/pom.xml](pico/pom.xml) the maven plugin uses <i>application-create</i> to create the supporting DI around it. That explains why there are no classes other than Main in the pico sub-module. Guice does not offer such an option, and instead requires the developer to write the modules declaring the DI module programmatically. 


Additionally, and more philosophical in nature, Pico strives to closely adhere to standard JSR-330 constructs as compared to Guice.
To be productive only requires the use of these packages:
* [jakarta.inject](https://javadoc.io/doc/jakarta.inject/jakarta.inject-api/latest/index.html)
* Optionally, [jakarta.annotation](https://javadoc.io/doc/jakarta.annotation/jakarta.annotation-api/latest/jakarta.annotation/jakarta/annotation/package-summary.html)
* Optionally, a few [pico API](../../pico/src/main/java/io/helidon/pico) / annotations.

# References
* https://www.baeldung.com/guice
