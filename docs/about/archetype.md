# Helidon Application Bootstrapping

## Introduction

[Helidon CLI](cli.md) provides a convenient way to bootstrap Helidon applications. It allows you to choose from a set of archetypes i.e. application with pre-defined feature sets and lets you customize it by providing a host of options.

## Archetypes

Helidon provides the following set of archetypes to bootstrap your application development journey.

### QuickStart

This option creates a Helidon project that includes multiple REST operations along with default observability setup and a set of dependencies to enable ease of development e.g. in case of Helidon MP, it uses `helidon-microprofile` bundle instead of minimal `helidon-microprofile-core` bundle.

### Database

This option builds on `QuickStart` to demonstrate how to integrate with database (in-memory H2, by default). In case of, Helidon SE that uses the DbClient API while for Helidon MP that uses JPA.

### OCI

This option builds on `QuickStart` to demonstrate integration with Oracle Cloud Infrastructure (OCI) services using the OCI SDK. Generated project showcases OpenApi-driven development approach where the practice of designing and building APIs is done first, then creating the rest of an application around them is implemented next. This is available for Helidon MP only.

### Custom

This option enables user to create Helidon project of their choice, suitable to start from scratch i.e. bare minimum, if default values are chosen Or choose from many options available.

## Generated Application Structure

You can scaffold a new Maven project based on these archetypes. See [Helidon CLI](cli.md) and our [Helidon SE QuickStart Guide](../se/guides/quickstart.md) or [Helidon MP QuickStart Guide](../mp/guides/quickstart.md) for more information.

Once the archetype is selected, the other options have defaults and the project is generated in a directory named after the `artifactId` value. It mainly contains the following:

- Maven structure
- skeletal application code
- associated unit test code
- example Dockerfile files
- application configuration file(s)
- instructions to build and run application/test

## Using Generated Application

The easiest way to get started is to follow the instructions in the `README` file and familiarize with layout and features provided to build upon them. In addition, look at the `pom.xml` files. You can find a suitable Helidon parent `pom` file that will enable you to use the different dependencies managed and provided by Helidon.
