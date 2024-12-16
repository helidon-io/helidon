# Helidon OCI Archetype Application

This example demonstrates Helidon's integration with Oracle Cloud Infrastructure (OCI) services using the OCI SDK. It shows the following:

1. Server code generation
2. Client code generation
3. Integration with the OCI SDK
4. OCI IAM Authentication: User Principal and Instance Principal
5. MicroProfile Metrics published to OCI Monitoring Service
6. Logs published to OCI Logging Service
7. OCI Custom Logs Monitoring using Unified Monitoring Agent
8. Health and Liveness checks
9. Configuration profiles for switching between `config-file` and `instance-principals` configurations

This project demonstrates OpenApi-driven development approach where the practice of designing and building APIs is done first, 
then creating the rest of an application around them is implemented next. Below are the modules that are part of this project:

1. [Spec](src/spec/README.md) - Contains OpenApi v3 specification documentation that will be used as input for
   both the [server](src/server/README.md) and [client](src/client/README.md) modules in generating server and client side code
2. [Server](src/server/README.md) - Generates server-side JAX-RS service and model source code which are used to implement
   business logic for the [server](src/server/README.md) application
3. [Client](src/client/README.md) - Generates client-side microprofile rest client and model source code which can be
   injected/initialized into the client application for the purpose of accessing the [server](src/server/README.md) application


Here's a high level diagram of how this project will work:

                      +-------------------------------------+
                      | OpenApi Specification Documentation |
                      +-------------------------------------+
                                         ^   
                                         |
                            +------------+------------+
                            |                         |
                            |                         |
                   +--------+--------+       +--------+--------+
                   | Generate Client |       | Generate Server |
                   |   Source Code   |       |   Source Code   |
                   +--------+--------+       +--------+--------+
                            |                         | 
                            |                         |
                            v                         v
                  +--------------------+     +-------------------+
                  |                    |     | Sever Application |
                  | Client Application +---->|    port:8080      |
                  |                    |     |    path: /greet   |
                  +--------------------+     +-------------------+
