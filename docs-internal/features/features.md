# Features
Helidon feature serve two main purposes:

1. Startup list (and possibly tree) to show which features are used
2. Mechanism for CLI/starter to correctly add features to a new/existing project
3. Configuration metadata (to create configuration templates, support IDE etc.)

## Startup list

*Requirements*

1. Simple list of top-level features printed when Helidon is started
2. Full tree if requested 
3. Works both in MP and in SE
~~4. A feature may be in more than one node of the detailed tree~~ 
~~1. Security/Authentication/HTTP-Basic~~
~~2. Security/Outbound/HTTP-Basic~~
4. Security features will use group (Providers) instead of what was written above 
5. Some features are SE specific, some MP specific some work in both worlds
  1. SE specific: SE Metrics
  2. MP specific: Fault Tolerance, MP Metrics
  3. Both: Config/YAML
~~6. Features details may be known only after feature initialization (HTTP-Basic will know if it supports outbound only once configured)~~
6. Features will be listed depending on what is on the classpath (not necessarily based on what is configured with WebServer)

## CLI

Constraints should allow us to create metadata to be able to construct a valid application dependencies by selecting features.

**Requirements**

- Simple Dependency - managed through maven (if this feature is added, transitive dependencies are added) 
- Feature requires an **implementation** 
    - If this feature is added, another feature MUST be added as well
    - Example: Tracing requires either Jaeger, Zipkin, or TracerResolver
- Feature supports **plugins**
    - If this feature is added, other feature(s) MAY be added as well
    - Example: Config allows addition of YAML, HOCON, git, etcd
- Feature may trigger addition of another feature (**automatic** features)
    - When this feature/library and another feature(s)/libraries are added, another feature/library MUST be added as well
    - Example: when Security and Jersey are on classpath, Security integration with Jersey must be added
    - Example: when Tracing implementation (either Zipkin or Jaeger) and WebClient are added, WebClient tracing MUSt be added as well
    - Example: when Metrics and WebClient are added, WebClient metrics will be added as well 

CLI should:
- Update dependencies in `pom.xml` (or gradle build) 
    - Do not support multi-module maven projects
    - Creates import of bom (and maybe dependencies?)
    - Add required dependencies for the features added through CLI
- Update configuration, options:
    - need to support yaml, properties, json -> keep order of properties!
    - either modify a configuration file in-place
    - or have a metaconfiguration and add file for each feature that is configurable
    - same as before, just use directory config source that reads all files in a dir as config sources
        - this would not require any inline code changes, only adding/removing files, with a small perf overhead of reading 
            more files on startup
- Update code
    - this should not be required at all for MP, except for adding new files (hopefully) 
    - in SE, we would need to update sources, which is very tricky - so only done
        for initial project structure. For new features, maybe we should print
        the required changes to stdout

Each feature that is available should have the following mappings:
- dependency (what to add to pom.xml)
- user flow (questions to ask that may modify outcome - template questions)
    - mapped through feature path
    - may be mapped through type for external types (such as JDBC drivers)
- configuration (what to add to configuration file(s))
    - may use options from user flow
- code (file templates)
    - may use options from user flow
- testing
    - each feature may have tests - this is probably not CLI related, but for our own use
    - should try all combinations  
    - create project
    - add/remove features
    - build it for each
    - run it for each
    - maybe compare add/removal with generated projects, or just unit test (result of editing produces same result as archetype) 
     
```
cli add jaeger
cli add web-client
cli add security
```

## Implementation options

As discussed on 2nd April 2020:

- There is a global feature file `features.yaml` (see example in this directory) that: 
       - lists all features and their dependencies
       - contains names only
       - contains details about Helidon features
       - may reference external features (such as JDBC drivers)
~~- `module-info.java` of each feature module is annotated with an annotation that contains:~~
~~we cannot use `module-info.java` as that is only available when running on module path.~~
- `package-info.java` of each feature package is annotated with an annotation that contains:
    - name of the feature (must match `features.yaml`)
    - whether the feature is `SE`, `MP` or both flavors
    - see `package-info.java` in this directory
- the module containing `features.yaml` will process the file and locate the features
    found through annotations to construct a database file `feature-db.json` to be 
    deployed to maven central. This file will contain the whole feature tree including:
    - features discovered through `service` keyword
    - Maven coordinates for each feature discovered through feature name
    - description of each feature
    - whether it is valid for SE/MP  



## Handling features in CLI
The CLI needs to somehow handle cases of optional and mandatory sub-features.

### Plugins
Zero or more plugins to add.

**Option 1**
```text
helidon add Security
  
Choose "Providers" to add (multiple-choice):
1 (x): ABAC
2 ( ): Http-Basic-Auth
3 ( ): ...
```  

**Option 2**
```text
helidon add Security
helidon add Security/Providers/ABAC
helidon add Security/Providers/Http-Basic-Auth
```

### Implementations
An implementation is required.

**Option 1**
```text
helidon add Tracing
  
Choose an implementation to add (radiobutton):
1 (*): Jaeger
2 ( ): TracerResolver
3 ( ): Zipkin
```  

**Option 2**
```text
helidon add Tracing - does not work or adds default?
helidon add Tracing/Zipkin
helidon add Tracing/Jaeger - fails if executed after previous line
```

### Drivers
One or more drivers are needed
In both cases, adds all integrations.    

**Option 1**
```text
helidon add DbClient
  
Choose a DbClient Type to add (one or more):
1 (x): JDBC
2 (x): Mongo

Choose a JDBC Driver to add (one or more):
1 (x): Oracle
2 ( ): MySQL
3 ( ): H2
```  

**Option 2**
```text
helidon add DbClient - does not work, or add default?
helidon add DbClient/Mongo
helidon add DbClient/Jdbc - does not work, or add default JDBC driver?
helidon add DbClient/Jdbc/H2
helidon add DbClient/Jdbc/Oracle
helidon add DbClient/Jdbc/MySQL 
```

The JDBC Driver is an external extension
Service is "java.sql.Driver"
We would need to have a list of:
```yaml
service: "java.sql.Driver"
drivers:
    - name: "H2"
      gav: "..."
```
or similar


**All features**
```text
SE & MP
---------------------------------------------------------
Config/Encryption
Config/HOCON
Config/Object Mapping
Config/YAML
Config/etcd
Config/git
Health/Built-ins
OpenAPI
Security/Authentication/Basic-Auth
Security/Authentication/Digest-Auth
Security/Authentication/Google-Login
Security/Authentication/Header
Security/Authentication/Http-Sign
Security/Authentication/JWT
Security/Authentication/OIDC
Security/Authorization/ABAC
Security/Authorization/ABAC/Policy
Security/Authorization/ABAC/Policy/EL
Security/Authorization/ABAC/Role
Security/Authorization/ABAC/Scope
Security/Authorization/ABAC/Time
Security/Integration/Jersey
Security/Integration/Jersey Client
Security/Integration/WebServer
Security/Integration/gRPC Client
Security/Integration/gRPC Server
Security/Outbound/Basic-Auth
Security/Outbound/Google-Login
Security/Outbound/Header
Security/Outbound/Http-Sign
Security/Outbound/JWT
Security/Outbound/OIDC
Security/Role-Mapper/IDCS
Security/Role-Mapper/IDCS-Multitenant
Tracing/Integration/Jersey
Tracing/Integration/Jersey Client
Tracing/Jaeger
Tracing/Zipkin
gRPC Client/Metrics
gRPC Server/Metrics

MP
---------------------------------------------------------
FaultTolerance
Health
JAX-RS
Messaging
Metrics
Security
Security/MP/JWT-Auth
Security/MP/OIDC
Server
Server/AccessLog
Tracing
WebSocket

SE
---------------------------------------------------------
Config
DbClient
DbClient/HealthCheck
DbClient/JDBC
DbClient/Metrics
DbClient/MongoDB
DbClient/Tracing
Health
Metrics
OpenAPI
Security
Tracing
WebClient
WebClient/Metrics
WebClient/Security
WebClient/Tracing
WebServer
WebServer/AccessLog
WebServer/JSON-B
WebServer/JSON-P
WebServer/Jackson
WebServer/Prometheus
gRPC Client
gRPC Server
```

Next steps:
- try to model the complicated features
- POC
- scope for 2.0 - some of the ideas are great, but 3.0
- have a look on directory config source or create one - to just add a config file somewhere to be picked up without editing meta-config or a yaml file 
- generated output should probably be json
- we can use yaml to create the file in source tree