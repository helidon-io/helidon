# Service Configuration `ConfigSource` Implementation

This project implements a MicroProfile Config `ConfigSource` in terms
of the Helidon Service Configuration API.

## Installation

Ensure that the `serviceconfiguration-config-source` artifact and its
dependencies are on your runtime classpath.

## Usage

You use the Service Configuration `ConfigSource` Implementation
indirectly: by using the normal[MicroProfile Config APIs](https://javadoc.io/doc/org.eclipse.microprofile.config/microprofile-config-api/1.2.1).
 The presence of the Service Configuration `ConfigSource` implementation
 artifact on your runtime classpath is enough to cause it to be consulted in
 the normal course of acquiring configuration information through the
 MicroProfile Config API.
