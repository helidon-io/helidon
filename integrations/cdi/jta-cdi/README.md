# Helidon JTA CDI Integration

The Helidon JTA CDI Integration project performs the
CDI-provider-agnostic work of integrating a JTA implementation into standalone CDI
applications (including those based on Helidon MicroProfile).  It is
one of two projects that together make up overall JTA support for
standalone CDI applications.

To function properly, this project also requires:

* a CDI-provider-specific counterpart, such as the
  [`jta-weld` project](../jta-weld)

## Installation

Ensure that the Helidon JTA CDI Integration project and its runtime
dependencies are present on your application's runtime classpath.

Please see the
[`examples/integrations/cdi/jpa`](../../../examples/integrations/cdi/jpa/)
project for a working `pom.xml` file that uses this project.
