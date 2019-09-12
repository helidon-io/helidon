# Helidon Service Configuration Framework

NOTE: All projects under this directory are deprecated and are slated
for removal.

The Helidon Service Configuration Framework abstracts the
configuration of a Java object or set of objects (a service) from the
system-dependent configuration sources it might otherwise need, like particular environment
variables or system properties or configuration files.

The Helidon Service Configuration Framework is _not_ another general
purpose configuration framework, but exists rather to support such
frameworks.

For example, an end user running code in Oracle's Application
Container Cloud Service (a "system") may be able to acquire
configuration needed for a service her code uses from certain already
populated, well-known environment variables.  On the other hand, if
she runs the same code in Heroku, different environment variables may
be present, and if she runs the same code in a simple container
platform such as Oracle's Kubernetes Engine, no such environment
variables may be present, but relevant configuration files may be.
The Helidon Service Configuration Framework abstracts away and
normalizes such differences so that her code does not have to be aware
of which system it is running on.

In some cases, this abstraction mechanism can fill in "missing"
configuration information.  For example, if it is determined that the
end user's code is running in a local test environment, then perhaps
certain database connectivity information can be synthesized for an
in-memory database whose properties will appear to have been set
automatically.

The Helidon Service Configuration Framework consists of a core API
subproject (`serviceconfiguration-api`), several implementations of
"systems" (`serviceconfiguration-system-oracle-accs`,
`serviceconfiguration-system-kubernetes`) and several implementations
of configurations for various services on those various systems
(`serviceconfiguration-hikaricp-accs`,
`serviceconfiguration-hikaricp-localhost`).

There is also a subproject that makes the Helidon Service
Configuration Framework into a
[MicroProfile Config `ConfigSource`](https://static.javadoc.io/org.eclipse.microprofile.config/microprofile-config-api/1.2.1/org/eclipse/microprofile/config/spi/ConfigSource.html)
 : `serviceconfiguration-config-source`.
