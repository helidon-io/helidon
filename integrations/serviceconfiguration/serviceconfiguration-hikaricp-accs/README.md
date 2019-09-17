# Service Configuration for HikariCP on Application Container Cloud Service

NOTE: This project is deprecated and slated for removal.

This project implements a Helidon Service Configuration Framework
`ServiceConfiguration` that exposes configuration information suitable
for the [Hikari connection pool](http://brettwooldridge.github.io/HikariCP/)
 when the system in effect is [Oracle's Application Container Cloud Service](https://cloud.oracle.com/acc).

## Usage

Ensure that both of the following artifacts are present on your runtime classpath:
. `serviceconfiguration-hikaricp-accs`
. `serviceconfiguration-system-oracle-accs`

When your program is running on the Application Container Cloud
Service platform, Hikari connection pool information will be made
available to programs using the Helidon Service Configuration
Framework.
