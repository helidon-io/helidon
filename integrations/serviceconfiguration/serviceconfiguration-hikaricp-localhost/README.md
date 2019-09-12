# Service Configuration for HikariCP on `localhost`

NOTE: This project is deprecated and slated for removal.

This project implements a Helidon Service Configuration Framework
`ServiceConfiguration` that exposes configuration information suitable
for the [Hikari connection pool](http://brettwooldridge.github.io/HikariCP/)
 when the system in effect is your local unit testing environment.

## Usage

Ensure the `serviceconfiguration-hikaricp-localhost` artifact is
present on your runtime classpath.  When your program is running in
your local unit testing environment, Hikari connection pool
information will be made available to programs using the Helidon
Service Configuration Framework.
