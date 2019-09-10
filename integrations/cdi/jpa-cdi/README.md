# Helidon JPA CDI Integration

The Helidon JPA CDI Integration project performs the
provider-independent work of integrating JPA into standalone CDI
applications (including those based on Helidon MicroProfile).  It is
one of several projects that together make up overall JPA support for
standalone CDI applications.

To function properly, this project also requires:

* a JPA-provider-specific library to assist the JPA provider in
  determining what kind of environment it is running in, such as the
  [`eclipselink-cdi` project](../eclipselink-cdi)
* a library capable of integrating `DataSource` instances into CDI,
  such as the [`datasource-hikaricp` project](../datasource-hikaricp)
* a suitable JDBC-compliant database driver library

## Installation

Ensure that the Helidon JPA CDI Integration project and its runtime
dependencies are present on your application's runtime classpath.

Please see the
[`examples/integrations/cdi/jpa`](../../../examples/integrations/cdi/jpa/)
project for a working `pom.xml` file that uses this project.

