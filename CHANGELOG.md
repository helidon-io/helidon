
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [2.3.3]

This is a bug fix release of Helidon. It contains bug and performance fixes. We recommend all Helidon 2.x users upgrade to this release.

### Compatibility

2.3.3 is API compatible with 2.3.0.

### CHANGES

- WebServer and WebClient race conditions fixed [3351](https://github.com/oracle/helidon/pull/3351)
- WebServer: Fix missing CORS headers if response has 404 status (#3206) [3280](https://github.com/oracle/helidon/pull/3280)
- WebClient: HTTP to HTTPS request hang fix [3351](https://github.com/oracle/helidon/pull/3351)
- Tracing: Modified Jaeger logic to not close scopes before switching threads [3274](https://github.com/oracle/helidon/pull/3274)
- Security: Fix NPE in outbound of JWT provider. (#3295) [3297](https://github.com/oracle/helidon/pull/3297)
- Reactive: 3129 3216 BEP refactor backport [3327](https://github.com/oracle/helidon/pull/3327)
- MicroProfile: Use MP config instead of Config.create() in MP components. [3291](https://github.com/oracle/helidon/pull/3291)
- Metrics: Performance: Improve some interceptor code paths in metrics (#3251) [3328](https://github.com/oracle/helidon/pull/3328)
- Metrics: Metrics and routings fixes (#3260) [3324](https://github.com/oracle/helidon/pull/3324)
- Metrics: Add the KPI metrics handler (with no qualifying path) exactly once each routing (#3255) [3282](https://github.com/oracle/helidon/pull/3282)
- Metrics: Suppress empty labels in exemplars which result in {}; yield a truly empty string instead [3281](https://github.com/oracle/helidon/pull/3281)
- Fault Tolerance: Explicitly destroy bean instance obtained from CDI object [3274](https://github.com/oracle/helidon/pull/3274)
- Docs: Helidon config documentation updates (#3187) [3284](https://github.com/oracle/helidon/pull/3284)
- Dependencies: Upgrade org.glassfish:jakarta.el to 3.0.4 [3331](https://github.com/oracle/helidon/pull/3331)
- Dependencies: Upgrade helidon-build-tools to 2.2.2. Fixes issue with Maven 3.8 [TBD](https://github.com/oracle/helidon/pull/TBD)

## [2.3.2]

This is a bug fix release of Helidon. It contains bug fixes and minor enhancements. We recommend all Helidon 2.x users upgrade to this release.

### Compatibility

2.3.2 is API compatible with 2.3.0.

### CHANGES

- Health: Health check for OCI ObjectStorage that works in SE and MP [3157](https://github.com/oracle/helidon/pull/3157)
- Logging: Fix Bug #3032 with SLF4J dependancy [3047](https://github.com/oracle/helidon/pull/3047)
- Metrics: Correct the reported Prometheus metric type for ConcurrentGauge metrics [3160](https://github.com/oracle/helidon/pull/3160)
- Metrics: Fix incorrect handling of omitted display name (and description) revealed by gRPC [3178](https://github.com/oracle/helidon/pull/3178)
- Security: Allowed cipher suite can now be specified for WebServer and WebClient [3144](https://github.com/oracle/helidon/pull/3144)
- Security: Audit does not format message if not loggable. [3156](https://github.com/oracle/helidon/pull/3156) [3162](https://github.com/oracle/helidon/pull/3162)
- Tests: Fixed failing mp-graphql integration test when using JDK17 (#3100) [3176](https://github.com/oracle/helidon/pull/3176)
- Tracing: Improved handling of Jaeger spans/scopes across threads [3134](https://github.com/oracle/helidon/pull/3134)
- Utils: Loom support moved to ThreadPoolSupplier [3164](https://github.com/oracle/helidon/pull/3164)
- Vault: Custom path for Vault auth methods [3161](https://github.com/oracle/helidon/pull/3161)
- WebServer: Reactive streams compliant BareResponseImpl [3153](https://github.com/oracle/helidon/pull/3153)
- WebServer: Set content-length to 0 if empty response [3135](https://github.com/oracle/helidon/pull/3135)
- Docs: Fixed typos in link [3167](https://github.com/oracle/helidon/pull/3167)
- Docs: New note about the generation of passphrase protected PKCS#1 keys [3145](https://github.com/oracle/helidon/pull/3145)
- Documentation SE : OIDC Postman extension [2740](https://github.com/oracle/helidon/pull/2740)
- Examples: Fix for exec:java to exec:exec [3148](https://github.com/oracle/helidon/pull/3148)
- Examples: Remove jakarta.activation dependency from examples and archetypes [3155](https://github.com/oracle/helidon/pull/3155)

## [2.3.1]

2.3.1 is a bug fix release of Helidon. It also includes some minor enhancements.

### MP Config Updates

Features from MP Config 2.0 that are backward compatible were added in this release:

1. Configuration profiles were added. You can now define a configuration profile (such as `dev`) using configuration property `mp.config.profile`. If such is defined, config will load default properties `microprofile-config-${profile}.properties`, and it will look for properties prefixed with `%${profile}-` first.
2. `OptionalInt`, `OptionalLong`, and `OptionalDouble` were added as supported types

### Compatibility

2.3.1 is API compatible with 2.3.0.

There has been one minor package change. `Base64Value` has been moved from `Helidon Integrations Common REST` module
to the module `Helidon Common`. Due to that action, import has changed from
`io.helidon.integrations.common.rest.Base64Value` to `io.helidon.common.Base64Value`, but the class is the same.

### CHANGES

- WebServer: Introduce backpressure to webserver [3108](https://github.com/oracle/helidon/pull/3108)
- WebServer: support colons in passwords [3045](https://github.com/oracle/helidon/pull/3045)
- WebServer: Ignore path parameters when matching routes [3097](https://github.com/oracle/helidon/pull/3097)
- WebServer: Honor bind address and host in configuration [3105](https://github.com/oracle/helidon/pull/3105)
- WebServer: Encode paths returned as part of 404 responses [3048](https://github.com/oracle/helidon/pull/3048)
- WebServer: A few more cases of HTML encoding in error messages [3051](https://github.com/oracle/helidon/pull/3051)
- WebServer: Adds convenience method to clear/invalidate a cookie [3037](https://github.com/oracle/helidon/pull/3037)
- WebServer: 3086: Fix SSE event sending [3087](https://github.com/oracle/helidon/pull/3087)
- WebClient: Host IPv6 matching is now cached [3028](https://github.com/oracle/helidon/pull/3028)
- Tracing: Publish internal Jaeger tracing metrics via Helidon's metrics system [3000](https://github.com/oracle/helidon/pull/3000)
- Test: Add support for YAML configuration in Helidon Junit 5 testing [3025](https://github.com/oracle/helidon/pull/3025)
- Security: Helidon crypto module [2989](https://github.com/oracle/helidon/pull/2989)
- Security: Add optional support to security providers [3039](https://github.com/oracle/helidon/pull/3039)
- Reactive: Fix  Intermittent MultiFlatMapPublisherTest#multi [3068](https://github.com/oracle/helidon/pull/3068)
- MicroProfile Server: Reactive routing via cdi provider [3050](https://github.com/oracle/helidon/pull/3050)
- Metrics: Adjust JSON timer histogram output using the units (if any) that were set on the timer [3132](https://github.com/oracle/helidon/pull/3132)
- Metrics: Add extended key performance indicators metrics support [3021](https://github.com/oracle/helidon/pull/3021)
- Messaging: Messaging connector type check [3044](https://github.com/oracle/helidon/pull/3044)
- JPA: Ensure container-managed EntityManagerFactory contextual references are eagerly inflated [3018](https://github.com/oracle/helidon/pull/3018)
- JDBC: Allow underscores in JDBC param names [3023](https://github.com/oracle/helidon/pull/3023)
- JAX-RS: Single injection manager in use, we can only shut it down once. [3101](https://github.com/oracle/helidon/pull/3101)
- JAX-RS: Allow sharing of same Jersey's injection manager across multiple JAX-RS applications [2988](https://github.com/oracle/helidon/pull/2988)
- GraphQL: Upgrade GraphQL to support mp-graphql spec 1.1.0 [3065](https://github.com/oracle/helidon/pull/3065)
- GraphQL: Minor update to README and add missing @NonNull on TaskApi [3091](https://github.com/oracle/helidon/pull/3091)
- Examples: Silence Weld's INFO logging on quickstart examples [3131](https://github.com/oracle/helidon/pull/3131)
- Docs: Upgrade javadoc-plugin. Silence  error for org.jboss.logging.annotations.Message$Format [3077](https://github.com/oracle/helidon/pull/3077)
- Docs: Update Config Guide to describe how to change pom for custom main class [3083](https://github.com/oracle/helidon/pull/3083)
- Docs: Fix various formatting and processing errors in doc files [3036](https://github.com/oracle/helidon/pull/3036)
- Docs: Add missing javadoc to module-info files [3020](https://github.com/oracle/helidon/pull/3020)
- Docs: added attribute for javadoc-base-url-api to fix broken links [3034](https://github.com/oracle/helidon/pull/3034)
- Dependencies: Upgrade jgit to 5.11.1.202105131744-r [3040](https://github.com/oracle/helidon/pull/3040)
- Dependencies: Upgrade jboss classfilewriter [3074](https://github.com/oracle/helidon/pull/3074)
- Dependencies: Upgrade etcd4j to 2.18.0 [3043](https://github.com/oracle/helidon/pull/3043)
- Dependencies: Update SmallRye OpenAPI to 1.2.3 for Helidon 2.x [3080](https://github.com/oracle/helidon/pull/3080)
- Dependencies: Updates Eclipselink to 2.7.8 [3042](https://github.com/oracle/helidon/pull/3042)
- Dependencies: Suppress jgit false positive [3015](https://github.com/oracle/helidon/pull/3015)
- Config: MP Config profiles [3096](https://github.com/oracle/helidon/pull/3096)
- Config: Deprecated classes made public on oversight [3038](https://github.com/oracle/helidon/pull/3038)

## [2.3.0]

2.3.0 is a minor release of Helidon that contains bug fixes and enhancements. Notable enhancements:

* HashiCorp Vault support (Experimental)
* Oracle OCI Vault support (Experimental)
* Oracle OCI Object Storage support (Experimental)
* Neo4J support (Experimental)
* Micrometer metrics support (Experimental)
* gRPC native image support
* Reloadable WebServer TLS certificates
* New metric: Exemplars
* Scheduling feature to schedule periodic tasks 
* Performance improvements for JAX-RS applications with large number of concurrent connections

Experimental features are tested, supported and ready for use. But their APIs are subject to change.

Users of the current OCI Object Storage extension (`helidon-integrations-cdi-oci-objectstorage`) are encouraged
to look at the new OCI Object Storage support (`helidon-integrations-oci-objectstorage`). The old OCI object
storage support will be deprecated in a future release.

### Compatibility

2.3.0 is API compatible with 2.2.0.

There is a behavior change related to Java marshalling in gRPC. `io.helidon.grpc.core.JavaMarshaller` has
been deprecated and disabled by default. It's use is not recommended for production and it will be removed
in Helidon 3.0. If you need to use the JavaMarshaller you can re-enable it by setting the
`grpc.marshaller.java.enabled` configuration property to true.


### CHANGES    

- Config: Meta-Configuration support for Helidon MP. [2767](https://github.com/oracle/helidon/pull/2767)
- Config: Support for custom default config file suffixes [2717](https://github.com/oracle/helidon/pull/2717)
- DBClient: Removed query parameter check that is too strict. [2693](https://github.com/oracle/helidon/pull/2693)
- DBClient: DB Client unwrap support for JDBC and MongoDB internals [2970](https://github.com/oracle/helidon/pull/2970)
- Fault Tolerance: Fix delay computation in Retry policies [2938](https://github.com/oracle/helidon/pull/2938)
- GraphQL: Support java.util.Date [2706](https://github.com/oracle/helidon/pull/2706)
- JAX-RS: Fix NPE with async Jersey resource [2911](https://github.com/oracle/helidon/pull/2911)
- JAX-RS: Handle startups with request scopes available from CDI [2933](https://github.com/oracle/helidon/pull/2933)
- Media Support: Improve exception message and javadoc for MediaSupport. [2888](https://github.com/oracle/helidon/pull/2888)
- Metrics: Add exemplar support to histogram (and histogram part of timer) Prometheus output [2912](https://github.com/oracle/helidon/pull/2912)
- Metrics: Add missing metrics coverage and ensure proper coverage of future metrics in gRPC [2818](https://github.com/oracle/helidon/pull/2818)
- Metrics: Allow use of config to set up built-in disk space and heap memory health checks [2934](https://github.com/oracle/helidon/pull/2934)
- Metrics: Implement Micrometer integration, with some new abstractions for interceptors and retrofit to MP metrics [2873](https://github.com/oracle/helidon/pull/2873) [2930](https://github.com/oracle/helidon/pull/2930)
- Metrics: Redesign of how MP metrics interceptors work; support async JAX-RS endpoints for additional metrics [2868](https://github.com/oracle/helidon/pull/2868)
- Messaging: JMS: duable consumer support [3007](https://github.com/oracle/helidon/pull/3007)
- Misc: Fixed all service providers to be consistent [2926](https://github.com/oracle/helidon/pull/2926)
- Native Image: ClassCastException during Graal native image and ReST Client [2917](https://github.com/oracle/helidon/pull/2917)
- Native Image: Fix for Jersey native image. [2811](https://github.com/oracle/helidon/pull/2811)
- Native Image: Fix for native image. [2753](https://github.com/oracle/helidon/pull/2753)
- Native Image: Fix injection of metrics registry in native image [2916](https://github.com/oracle/helidon/pull/2916)
- Native Image: Substitute JAAS Subject context lookup in native image [2782](https://github.com/oracle/helidon/pull/2782)
- Native Image: Weld feature disconnected from runtime. [2906](https://github.com/oracle/helidon/pull/2906)
- Native Image: gRPC native image support in SE [2733](https://github.com/oracle/helidon/pull/2733)
- Neo4j integration [2692](https://github.com/oracle/helidon/pull/2692)
- OCI Integration: Common classes for JSON based REST APIs for integration. [2879](https://github.com/oracle/helidon/pull/2879)
- OCI Instgance Principal Authentication [3001](https://github.com/oracle/helidon/pull/3001)
- OpenAPI: Remove need to instantiate Application classes twice for OpenAPI support [2829](https://github.com/oracle/helidon/pull/2829)
- Performance: Implementation of ResponseWriter using pooled Netty buffers [2805](https://github.com/oracle/helidon/pull/2805)
- Performance: New implementation of WorkQueue that allows concurrent enqueuing of tasks [2859](https://github.com/oracle/helidon/pull/2859)
- Reactive: Multi to ByteChannel [2864](https://github.com/oracle/helidon/pull/2864)
- Scheduled tasks [2301](https://github.com/oracle/helidon/pull/2301)
- Security: HTTP Signature fix to work according to specification. [2884](https://github.com/oracle/helidon/pull/2884)
- Security: HTTP Basic auth Hash equals robustness [2871](https://github.com/oracle/helidon/pull/2871)
- Vault/Config: Add encryption, digest and secret [2872](https://github.com/oracle/helidon/pull/2872)
- Vault: Hashicorp vault integration [2895](https://github.com/oracle/helidon/pull/2895)
- Vault/Object Storage: Oracle OCI integration for Vault and Object Storage [2894](https://github.com/oracle/helidon/pull/2894) [2973](https://github.com/oracle/helidon/pull/2973)
- WebClient: Content-length set to 0 for PUT and POST if no entity has been sent [2924](https://github.com/oracle/helidon/pull/2924)
- WebClient: Using an interface instead of an enum implementing it. [2875](https://github.com/oracle/helidon/pull/2875)
- WebClient: URI handling improved [3004](https://github.com/oracle/helidon/pull/3004))
- WebServer: Correctly handling content type of icons. [2905](https://github.com/oracle/helidon/pull/2905)
- WebServer: Ensure that the context is set throughout the request lifecycle [2898](https://github.com/oracle/helidon/pull/2898)
- WebServer: Fixes problems with HTTP/2 connections  [2920](https://github.com/oracle/helidon/pull/2920)
- WebServer: Reloadable WebServer TLS during runtime [2900](https://github.com/oracle/helidon/pull/2900)
- WebServer: Static content support module. [2705](https://github.com/oracle/helidon/pull/2705)
- gRPC: Add support for gRPC reflection [2822](https://github.com/oracle/helidon/pull/2822)
- gRPC: JavaMarshaller deprecation [2975](https://github.com/oracle/helidon/pull/2975)
- Build: Add plugin to spotbugs-maven-plugin [2878](https://github.com/oracle/helidon/pull/2878)
- Build: Checkstyle now checks class javadocs. [2935](https://github.com/oracle/helidon/pull/2935)
- Build: Faster license filtering [2749](https://github.com/oracle/helidon/pull/2749)
- Build: Remove the Helidon specific info from application effective pom [2783](https://github.com/oracle/helidon/pull/2783)
- Build: New copyright checker, enforcer plugin, service file generator [2993](https://github.com/oracle/helidon/pull/2993)
- Dependencies: Graalvm upgrade (Merge do not squash) [2710](https://github.com/oracle/helidon/pull/2710)
- Dependencies: Upgrade Hibernate and jaxb-runtime used by maven-jaxb2-plugin. For JD… [2862](https://github.com/oracle/helidon/pull/2862)
- Dependencies: Upgrade Jersey and MP Rest client [2971](https://github.com/oracle/helidon/pull/2971)
- Dependencies: Upgrade Netty to 4.1.63.Final [2915](https://github.com/oracle/helidon/pull/2915)
- Examples: Fixes and cleanup [2891](https://github.com/oracle/helidon/pull/2891) [2768](https://github.com/oracle/helidon/pull/2768) [2931](https://github.com/oracle/helidon/pull/2931) [2858](https://github.com/oracle/helidon/pull/2858)
- Examples: More fixes and cleanup [2830](https://github.com/oracle/helidon/pull/2830) [2936](https://github.com/oracle/helidon/pull/2936) [2901](https://github.com/oracle/helidon/pull/2901) [2656](https://github.com/oracle/helidon/pull/2656)
- Examples: WebServer Threadpool example  [2836](https://github.com/oracle/helidon/pull/2836)
- Examples: multiple ports [2834](https://github.com/oracle/helidon/pull/2834)
- Docs: Fix #2809: h2 dependency in DB Client doc [2832](https://github.com/oracle/helidon/pull/2832)
- Docs: Fixed Javadoc links in document [2819](https://github.com/oracle/helidon/pull/2819)
- Docs: New section in docs for bean validation [2792](https://github.com/oracle/helidon/pull/2792)
- Docs: OCI and Vault documentation [2979](https://github.com/oracle/helidon/pull/2979)
- Docs: consitently document maven coordinates [2921](https://github.com/oracle/helidon/pull/2921) [2974](https://github.com/oracle/helidon/pull/2974)
- Docs: various updates [2958](https://github.com/oracle/helidon/pull/2958) [2960](https://github.com/oracle/helidon/pull/2960) [3003](https://github.com/oracle/helidon/pull/3003)
- Tests: Fix imports to use Hamcrest and Junit5 instead of Junit4 [2902](https://github.com/oracle/helidon/pull/2902)
- Tests: Fixed interfering tests in MP config. [2714](https://github.com/oracle/helidon/pull/2714)
- Tests: Move Multi reactive streams tck to standalone test project [2762](https://github.com/oracle/helidon/pull/2762)
- Tests: MultiByteChannel test RC fix [2843](https://github.com/oracle/helidon/pull/2843)

### Thanks!

Thanks to community members dansiviter and martin-sladecek for helping to make this release possible.

## [2.2.2]

2.2.2 is a bugfix release of Helidon. In addition to fixing various bugs it contains a security
fix for an issue concerning the use of security annotations (such as @Denyall, @RolesAllowed)
on JAX-RS Applications and sub-resource locators that are discovered by CDI. See issue
[2903](https://github.com/oracle/helidon/issues/2903) for more details.

This upgrade is recommended for all users of Helidon 2.

### Compatibility

2.2.2 is API compatible with 2.2.0.

### CHANGES    

- WebClient: keep-alive minor improvements [2882](https://github.com/oracle/helidon/pull/2882)
- WebClient: Keep alive default value changed to true [2775](https://github.com/oracle/helidon/pull/2775) 
- Security: Obtain the actual class when instance is injected by the CDI [2897](https://github.com/oracle/helidon/pull/2897)
- MicroProfile Server: Remove standard output. [2780](https://github.com/oracle/helidon/pull/2780)
- Metrics: Avoid NaN values causing problems in metrics output [2812](https://github.com/oracle/helidon/pull/2812)
- Logging: Slf4j MDC context propagation with null MDC map fixed [2861](https://github.com/oracle/helidon/pull/2861)
- JAX-RS: Use one of Jersey's implementations of PropertiesDelegate [2756](https://github.com/oracle/helidon/pull/2756)
- Fault Tolerance: New RequestScopeHelper class to track request scopes across threads [2856](https://github.com/oracle/helidon/pull/2856)
- Dependencies: Upgrade Netty to 4.1.59.Final [2793](https://github.com/oracle/helidon/pull/2793)


## [2.2.1]

2.2.1 is a bugfix release of Helidon. It contains bug fixes, performance fixes and dependency upgrades.

### Compatibility

2.2.1 is API compatible with 2.2.0.

### CHANGES    

- Config: Fix issue with null value in JSON. [2723](https://github.com/oracle/helidon/pull/2723)
- Config: Fix null array values in HOCON/JSON config parser. [2731](https://github.com/oracle/helidon/pull/2731)
- Dependencies: Re-organize dependencyManagement  [2646](https://github.com/oracle/helidon/pull/2646)
- Dependencies: Update Jackson to 2.12.1 [2690](https://github.com/oracle/helidon/pull/2690)
- Dependencies: Upgrade Netty to 4.1.58 [2678](https://github.com/oracle/helidon/pull/2678)
- Dependencies: Upgrade Weld [2668](https://github.com/oracle/helidon/pull/2668)
- Dependencies: Upgrade grpc to v1.35.0 [2713](https://github.com/oracle/helidon/pull/2713)
- Dependencies: Upgrade to Jersey 2.33 and set of client property [2727](https://github.com/oracle/helidon/pull/2727)
- Dependencies: Upgrades OCI SDK to version 1.31.0 [2699](https://github.com/oracle/helidon/pull/2699)
- Examples: Fix TODO application: [2708](https://github.com/oracle/helidon/pull/2708)
- Examples: Fix WebServer Basics example [2634](https://github.com/oracle/helidon/pull/2634)
- Examples: Fixed different output in DbClient SE archetype [2703](https://github.com/oracle/helidon/pull/2703)
- Examples: PokemonService template fixed in SE Database Archetype. [2701](https://github.com/oracle/helidon/pull/2701)
- Fault Tolerance: Do not attempt to access the request context in Fallback callback [2748](https://github.com/oracle/helidon/pull/2748)
- Jersey: Allow override of Jersey property via config [2737](https://github.com/oracle/helidon/pull/2737)
- K8s: Update k8s descriptors to avoid using deprecated APIs [2719](https://github.com/oracle/helidon/pull/2719)
- Metrics: Support async invocations using optional synthetic SimplyTimed behavior [2745](https://github.com/oracle/helidon/pull/2745)
- Micronaut extensions: micronaut data with ucp [2572](https://github.com/oracle/helidon/pull/2572)
- Performance: New implementation of LazyValue [2738](https://github.com/oracle/helidon/pull/2738)
- Performance: Properly release underlying buffer before passing it to WebSocket handler [2715](https://github.com/oracle/helidon/pull/2715)
- Performance: improvements to queue(s) management in Webserver [2704](https://github.com/oracle/helidon/pull/2704)
- Reactive: Concat array enhancement [2508](https://github.com/oracle/helidon/pull/2508)
- Rest Client: Rest client async header propagation with usage of Helidon Context [2735](https://github.com/oracle/helidon/pull/2735)
- Security: Added overall timeout to evictable cache [2659](https://github.com/oracle/helidon/pull/2659)
- Testing: Proves that environment variable overrides work with our MicroProfile Config implementation [2648](https://github.com/oracle/helidon/pull/2648)
- Testing: Separate execution of DataChunkReleaseTest in its own VM [2716](https://github.com/oracle/helidon/pull/2716)
- WebServer: Lays the groundwork for permitting other Netty transports [2478](https://github.com/oracle/helidon/pull/2478)
- Build: Manage versions of version plugin and helidon-maven-plugin [2626](https://github.com/oracle/helidon/pull/2626)


## [2.2.0]

2.2.0 is a minor release of Helidon. It contains bug fixes and enhancements. Notable enhancements:

- Experimental: Micronaut extension support [2467](https://github.com/oracle/helidon/pull/2467)
- Experimental: Graph QL Support  [2504](https://github.com/oracle/helidon/pull/2504)
- Experimental: Loom (VirtualThread) support in Helidon MP [2417](https://github.com/oracle/helidon/pull/2417)

Experimental features are ready for use, but their APIs should be considered unstable and 
subject to change. 

### Compatibility

2.2.0 is API compatible with 2.1.0.

### CHANGES    

- WebServer: Support for a max payload limit on client requests [2491](https://github.com/oracle/helidon/pull/2491)
- WebServer: Add a shutdown hook to executor service in JerseySupport [2505](https://github.com/oracle/helidon/pull/2505)
- WebServer: Fix Request chunks emitted after response is completed are not released [2605](https://github.com/oracle/helidon/pull/2605)
- WebServer: Support for HTTP pipelining [2591](https://github.com/oracle/helidon/pull/2591)
- WebClient: Methods contentLength and transferEncoding added [2490](https://github.com/oracle/helidon/pull/2490)
- WebClient: Double query escaping in WebClient fixed [2513](https://github.com/oracle/helidon/pull/2513)
- Tracing: add tags before span start in jersey ClientTracingFilter #1942 [2512](https://github.com/oracle/helidon/pull/2512)
- Tracing: Make sure span.finish() is not called twice [2466](https://github.com/oracle/helidon/pull/2466)
- Tests: Fix pom.xml of helidon-microprofile-tests-junit5 [2485](https://github.com/oracle/helidon/pull/2485)
- Tests: Convert tests to use the new infrastructure [2498](https://github.com/oracle/helidon/pull/2498)
- Tests: Adds a unit test proving that multiple persistence units can be injected [2503](https://github.com/oracle/helidon/pull/2503)
- Tests: Address intermittent test failures [2517](https://github.com/oracle/helidon/pull/2517)
- Security: Using the default keystore type instead of JKS [2536](https://github.com/oracle/helidon/pull/2536)
- Security: Support for overriding configuration of security providers [2511](https://github.com/oracle/helidon/pull/2511)
- Security: Global context and its use in Security [2549](https://github.com/oracle/helidon/pull/2549)
- Security: Fixed docs and impl - outbound security [2538](https://github.com/oracle/helidon/pull/2538)
- Security: Honoring disabled security through configuration for OIDC support [2577](https://github.com/oracle/helidon/pull/2577)
- Micronaut integration [2467](https://github.com/oracle/helidon/pull/2467)  [2602](https://github.com/oracle/helidon/pull/2602)
- Metrics: Add vetoed-bean support to metrics CDI extension [2507](https://github.com/oracle/helidon/pull/2507)
- Messaging: Messaging discrepancies [2453](https://github.com/oracle/helidon/pull/2453)
- Messaging: Make JmsMessage builder methods accessible [2617](https://github.com/oracle/helidon/pull/2617)
- Messaging: JMS Reactive Messaging connector [2282](https://github.com/oracle/helidon/pull/2282)
- Messaging: AQ acknowledgement propagation fix [2623](https://github.com/oracle/helidon/pull/2623)
- Messaging: Native image compatible Kafka connector #2346 [2555](https://github.com/oracle/helidon/pull/2555)
- Messaging: Jms connector SE api  [2543](https://github.com/oracle/helidon/pull/2543)
- Media: Json stream writers [2523](https://github.com/oracle/helidon/pull/2523)
- Loom: Initial stab at incorporating some Loom features into Helidon in a way that will work under JDK 11 or 16-loom-ea [2417](https://github.com/oracle/helidon/pull/2417)
- Logging: MDC logging support [2479](https://github.com/oracle/helidon/pull/2479)
- Logging: Examples of logging with Helidon [2533](https://github.com/oracle/helidon/pull/2533)
- JPA: PostgreSQL driver support for JPA/Hibernate in native image [2596](https://github.com/oracle/helidon/pull/2596)
- JPA: MySQL driver support for JPA/Hibernate in native image [2557](https://github.com/oracle/helidon/pull/2557)
- Health: Add messaging health dependency to bom [2556](https://github.com/oracle/helidon/pull/2556)
- Fault Tolerance: Revised logic for handling request scopes and contexts in Jersey/HK2 [2534](https://github.com/oracle/helidon/pull/2534)
- Fault Tolerance: Request scope propagation for non-async calls in FT [2495](https://github.com/oracle/helidon/pull/2495)
- Fault Tolerance: Do not create and cache handlers for fallbacks [2546](https://github.com/oracle/helidon/pull/2546)
- GraphQL: Experimental Graph QL Support  [2504](https://github.com/oracle/helidon/pull/2504)
- Examples: Make example Kafka working on MAC [2541](https://github.com/oracle/helidon/pull/2541)
- Examples: Fix uses of jlink-image directories to use new "-jri" suffix. [2469](https://github.com/oracle/helidon/pull/2469)
- Examples: Using config reference instead of ${ALIAS= [2579](https://github.com/oracle/helidon/pull/2579)
- Examples: Logging setup in examples and templates [2583](https://github.com/oracle/helidon/pull/2583)
- Docs: Updated path reference to fix the broken link. [2509](https://github.com/oracle/helidon/pull/2509)
- Docs: Update jlink guide with .jmod requirement. [2535](https://github.com/oracle/helidon/pull/2535)
- Docs: Update doc's microprofile version to 3.3 [2488](https://github.com/oracle/helidon/pull/2488)
- Docs: Some minor doc improvements [2527](https://github.com/oracle/helidon/pull/2527)
- Docs: SE intro: fixed typo [2545](https://github.com/oracle/helidon/pull/2545)
- Docs: Increase visibility of Getting started in Overview [2568](https://github.com/oracle/helidon/pull/2568)
- Docs: Improve doc for built-in health check thresholds, etc. [2559](https://github.com/oracle/helidon/pull/2559)
- Docs: Helidon SE OIDC Security provider guide [2310](https://github.com/oracle/helidon/pull/2310)
- Docs: Helidon MP OIDC Security provider guide [2311](https://github.com/oracle/helidon/pull/2311)
- Docs: Fix instructions for enabling JSON-P for Health-Checks. [2528](https://github.com/oracle/helidon/pull/2528)
- Docs: Updating JTA and JPA guides [2570](https://github.com/oracle/helidon/pull/2570)
- Docs: Misc fixes [2616](https://github.com/oracle/helidon/pull/2616) [2615](https://github.com/oracle/helidon/pull/2615)  [2573](https://github.com/oracle/helidon/pull/2573)
- Dependencies: Exclude jakarta.ejb-api [2595](https://github.com/oracle/helidon/pull/2595)
- Dependencies: remove weld-probe-core [2506](https://github.com/oracle/helidon/pull/2506)
- Dependencies: Upgrade Hibernate to 5.4.25.Final [2575](https://github.com/oracle/helidon/pull/2575)
- Dependencies: Upgrade GraalVM [2510](https://github.com/oracle/helidon/pull/2510) [2514](https://github.com/oracle/helidon/pull/2514)
- Dependencies: Upgrade apache-httpclient, google-api-client and snakeyaml [2482](https://github.com/oracle/helidon/pull/2482)
- Dependencies: Upgrade Guava version to 30.0-jre [2620](https://github.com/oracle/helidon/pull/2620)
- Dependencies: Update ojdbc deps [2530](https://github.com/oracle/helidon/pull/2530)
- Dependencies: Move scala dep from bom [2542](https://github.com/oracle/helidon/pull/2542)
- Dependencies: Add dependency-check-maven plugin [2472](https://github.com/oracle/helidon/pull/2472)
- Dependencies: Import ojdbc deps with bom [2588](https://github.com/oracle/helidon/pull/2588)

## [2.1.0]

2.1.0 is a minor release of Helidon. It contains bug fixes and enhancements. Notable changes:

- MicroProfile 3.3 support
- New Helidon SE Fault Tolerance implementation. This also replaces Hystrix in the MicroProfile
  Fault Tolerance implementation.

### Compatibility

2.1.0 is API compatible with previous releases of 2.0. There are some minor
behavioral changes:

- Security: To remove accidental propagation of identity, all security providers that support outbound
  security were updated to only do outbound security when configured so. All of these providers
  now have an `outbound` configuration section that can define outbound targets. Documentation of
  providers was updated to match this new approach and is available in both MP and SE docs.
- Custom jlink images produced by the helidon-mave-plugin now have a -jri suffix.
  So the path to start your application looks something like:
  target/helidon-quickstart-se-jri/bin/start
    
### CHANGES    

- Config: Git config native-image support [2400](https://github.com/oracle/helidon/pull/2400)
- Config: Injection of web target with correct endpoint. [2380](https://github.com/oracle/helidon/pull/2380)
- Config: Config now supports merging of objects and lists [2448](https://github.com/oracle/helidon/pull/2448)
- DBClient: integration tests for MySQL, MadiaDB, PostgreSQL and MS SQL [2383](https://github.com/oracle/helidon/pull/2383)
- DBClient: Fix NPE on error in tx [2286](https://github.com/oracle/helidon/pull/2437)
- DataSource: Use Config.getPropertyNames() instead of retrieving getPropertyNames() from ConfigSources [2322](https://github.com/oracle/helidon/pull/2322)
- Fault Tolerance: Implementation of MP FT 2.1.1 using FT SE [2348](https://github.com/oracle/helidon/pull/2348)
- Fault Tolerance: Limit module visibility by making several classes package private [2359](https://github.com/oracle/helidon/pull/2359)
- Fault Tolerance: Name support for all those operations that implement FtHandler [2404](https://github.com/oracle/helidon/pull/2404)
- Fault Tolerance: Updated test to verify @CircuitBreaker does not interfere with request scope [2387](https://github.com/oracle/helidon/pull/2387)
- Fault Tolerance: Updates to FT tests to take advantage of @HelidonTest [2370](https://github.com/oracle/helidon/pull/2370)
- JAX-RS: Allow user-provided CDI extensions to veto JAX-RS classes  [2429](https://github.com/oracle/helidon/pull/2429)
- JAX-RS: Jersey update to version 2.32 [2406](https://github.com/oracle/helidon/pull/2406)
- Media: DataChunkedInputStream deadlock protection removed [2401](https://github.com/oracle/helidon/pull/2401)
- Media: Update MediaType, lazy non known type, read-only parameters map [2308](https://github.com/oracle/helidon/pull/2308)
- Media: media-jackson native-image support [2385](https://github.com/oracle/helidon/pull/2385)
- Messaging: Messaging health check [2352](https://github.com/oracle/helidon/pull/2352)
- Messaging: Log Kafka sending error which caused channel cancel [2447](https://github.com/oracle/helidon/pull/2447)
- Metrics: Avoid race condition by using ConcurrentHashMap [2435](https://github.com/oracle/helidon/pull/2435)
- Metrics: Update release of Prometheus Java client (in 2.x) [2419](https://github.com/oracle/helidon/pull/2419)
- Native Image: When class is annotated with @MappedSuperclass, its private fields can't be accessed from JPA with native image  [2127](https://github.com/oracle/helidon/pull/2127)
- Native image: Tyrus server support for native-image in SE [2097](https://github.com/oracle/helidon/pull/2097)
- Native image: Windows native-image fix [2336](https://github.com/oracle/helidon/pull/2336)
- Native image: Use latest version for helidon plugin to include native image fix. [2374](https://github.com/oracle/helidon/pull/2374)
- OpenAPI: Use CDI to instantiate Application classes to invoke getClasses [2325](https://github.com/oracle/helidon/pull/2325)
- Reactive: Multi discrepancies [2413](https://github.com/oracle/helidon/pull/2413)
- Reactive: Single.never not singleton [2349](https://github.com/oracle/helidon/pull/2349)
- Security: Added support for remote host and port to jersey and security 2.x [2368](https://github.com/oracle/helidon/pull/2368)
- Security: Disable automatic propagation from security providers [2357](https://github.com/oracle/helidon/pull/2357)
- Security: OIDC fixes [2378](https://github.com/oracle/helidon/pull/2378)
- Security: Support for methods in outbound targets. [2335](https://github.com/oracle/helidon/pull/2335)
- Testing: Intermittent test failure fix [2347](https://github.com/oracle/helidon/pull/2347)
- Testing: MP Testing [2353](https://github.com/oracle/helidon/pull/2353)
- Testing: Update surefire and failsafe to 3.0.0-M5 [2307](https://github.com/oracle/helidon/pull/2307)
- Testing: Junit4 upgrade [2450](https://github.com/oracle/helidon/pull/2450)
- Tracing: Update to later release of MP OpenTracing [2313](https://github.com/oracle/helidon/pull/2313)
- WebClient: Changes of the request via WebClientService are now propagated [2321](https://github.com/oracle/helidon/pull/2321)
- WebClient: Webclient uses relative uri in request instead of full uri [2309](https://github.com/oracle/helidon/pull/2309)
- WebServer: ClassPathContentHandler can survive tmp folder cleanup [2361](https://github.com/oracle/helidon/pull/2361)
- WebServer: Enable support for HTTP compression in the webserver [2379](https://github.com/oracle/helidon/pull/2379)
- WebServer: Secure static content [2411](https://github.com/oracle/helidon/pull/2411)
- WebServer: WebSocket Extensions [1934](https://github.com/oracle/helidon/pull/1934)
- WebServer: Public API to get absolute URI of the request [2441](https://github.com/oracle/helidon/pull/2441)
- WebServer: Attempt to start a stopped server will fail with IllegalStateException [2439](https://github.com/oracle/helidon/pull/2439)
- gRPC: Fix issue with gRPC clients and services where method signatures have types with nested generics [2283](https://github.com/oracle/helidon/pull/2283)
- gRPC: Update gRPC Version [2388](https://github.com/oracle/helidon/pull/2388)
- CDI: Service class fixed. [2317](https://github.com/oracle/helidon/pull/2317)
- CDI: CDI.current().getBeanManager() now available during shutdown [2438](https://github.com/oracle/helidon/pull/2438)
- Examples: Fix 2391 Bug in generated Dockerfile.native from quickstart-mp [2393](https://github.com/oracle/helidon/pull/2393)
- Examples: Update standalone quickstarts. [2360](https://github.com/oracle/helidon/pull/2360)
- Examples: Updated maven surefire plugin to helidon-standalone-quickstart-mp [1849](https://github.com/oracle/helidon/pull/1849)
- Documentation improvements: [2428](https://github.com/oracle/helidon/pull/2428) [2399](https://github.com/oracle/helidon/pull/2399) [2410](https://github.com/oracle/helidon/pull/2410) [2390](https://github.com/oracle/helidon/pull/2390) [2329](https://github.com/oracle/helidon/pull/2329) [2397](https://github.com/oracle/helidon/pull/2397) [2367](https://github.com/oracle/helidon/pull/2367) [2363](https://github.com/oracle/helidon/pull/2363) [2351](https://github.com/oracle/helidon/pull/2351) [2328](https://github.com/oracle/helidon/pull/2328) [2457](https://github.com/oracle/helidon/pull/2457) [2443](https://github.com/oracle/helidon/pull/2443)

## [2.0.2]

2.0.2 is a bug fix release to Helidon 2.0. It contains bug fixes and minor enhancements.
This release upgrades MicroProfile support to Health 2.2 and Metrics 2.3.2.

This release contains experimental support for fault tolerance APIs in Helidon SE. These
APIs are unstable and will likely change in future releases. 

### Changes

- WebServer: Added support for HTTP PATCH on Helidon SE [2284](https://github.com/oracle/helidon/pull/2284)
- WebServer: Redundant filter execution fix [2276](https://github.com/oracle/helidon/pull/2276)
- WebServer: Use all configured fields. [2192](https://github.com/oracle/helidon/pull/2192)
- WebServer: Upgrade to Netty 4.1.51 [2204](https://github.com/oracle/helidon/pull/2204)
- WebServer: Form params ampersand and no value fix [2227](https://github.com/oracle/helidon/pull/2227)
- WebServer: Fix failures caused by memory leak when sending bad requests. [2260](https://github.com/oracle/helidon/pull/2260)
- WebServer: Configuration of server socket(s) [2189](https://github.com/oracle/helidon/pull/2189)
- WebServer: to enable netty log handler output, please use logger `io.helidon.webserver.NettyWebServer$NettyLog` and set it to `FINEST` level
- WebClient: Native smoke test for webclient [2112](https://github.com/oracle/helidon/pull/2112)
- WebClient: Intermittent test failure with keep-alive enabled fix [2238](https://github.com/oracle/helidon/pull/2238)
- WebClient: redirection to path fix [2150](https://github.com/oracle/helidon/pull/2150)
- WebClient: request id added and logging messages updated [2257](https://github.com/oracle/helidon/pull/2257)
- WebClient: support keep-alive [2139](https://github.com/oracle/helidon/pull/2139)
- WebClient: to enable netty log handler output, please use logger `io.helidon.webclient.NettyClientInitializer$ClientNettyLog` and set it to `FINEST` level
- Security injection in application scope. [2154](https://github.com/oracle/helidon/pull/2154)
- Security: Fix for P-521 curve - wrong id 2.x [2281](https://github.com/oracle/helidon/pull/2281)
- Security can now be disabled. [2157](https://github.com/oracle/helidon/pull/2157)
- Security CDI extension has higher priority [2299](https://github.com/oracle/helidon/pull/2299)
- Reactive: Allow cancellation of Future's wrapped in Single's and vice versa [2288](https://github.com/oracle/helidon/pull/2288)
- Reactive: Flaky MultiFromNotTrustedInputStreamTckTest fix [2218](https://github.com/oracle/helidon/pull/2218)
- Reactive: Debugging log operator [1874](https://github.com/oracle/helidon/pull/1874)
- Reactive: Single add forSingle and exceptionallyAccept [2121](https://github.com/oracle/helidon/pull/2121)
- Reactive: Kafka connector topic pattern [2242](https://github.com/oracle/helidon/pull/2242)
- Reactive: Buffer single-byte writes in MultiFromOutputStream for better performance [2133](https://github.com/oracle/helidon/pull/2133)
- Reactive: Backpressure counter race condition fix [2250](https://github.com/oracle/helidon/pull/2250)
- Native image update [2078](https://github.com/oracle/helidon/pull/2078)
- Native Image: Support for non-String parameters in JAX-RS for native image [2303](https://github.com/oracle/helidon/pull/2303)
- Metrics: Remove FinalRegistry and its use for the base registry; part of move to MP metrics 2.3 [2188](https://github.com/oracle/helidon/pull/2188)
- Metrics: Missing metric [2235](https://github.com/oracle/helidon/pull/2235)
- Metrics: Fixes a few problems in metrics [2240](https://github.com/oracle/helidon/pull/2240)
- Metrics: Add support for MP Metrics 2.3 [2245](https://github.com/oracle/helidon/pull/2245)
- Messaging: Kafka message default ack npe fix [2252](https://github.com/oracle/helidon/pull/2252)
- Media Support: Upgrade Jackson to 2.11.1 [2162](https://github.com/oracle/helidon/pull/2162)
- Meida Support: Upgrade Yasson to 1.0.8 [2254](https://github.com/oracle/helidon/pull/2254)
- Media Support: Multipart postfix fix [2233](https://github.com/oracle/helidon/pull/2233)
- Media Support: Multipart decoder rework [2193](https://github.com/oracle/helidon/pull/2193)
- Media Support: Media support for forms improved [2144](https://github.com/oracle/helidon/pull/2144)
- Media Support: Flush streamed datachunk one by one [2129](https://github.com/oracle/helidon/pull/2129)
- JPA: Upgrading to the latest version of the Eclipselink Maven plugin and aligning Eclipselink version to correspond with it. [2138](https://github.com/oracle/helidon/pull/2138)
- JPA: Upgrade Hibernate to 5.4.18.Final [2212](https://github.com/oracle/helidon/pull/2212)
- JPA: Use Session.getDatasourceLogin() instead of Session.getLogin() in CDISEPlatform.java [2263](https://github.com/oracle/helidon/pull/2263)
- Health: Support MP health 2.2 [2264](https://github.com/oracle/helidon/pull/2264)
- Health: Health check now non-blocking using fault tolerance async and timeout [2237](https://github.com/oracle/helidon/pull/2237)
- Fault tolerance for SE (experimental) [2120](https://github.com/oracle/helidon/pull/2120)
- Fault Tolerance: Location of the default value for header validation changed [2228](https://github.com/oracle/helidon/pull/2228)
- Fault Tolerance: Make sure request scope is propagated to newly created threads in FT [2229](https://github.com/oracle/helidon/pull/2229)
- Examples: Add app.yaml to quickstart archetypes. Update k8s support in docs. [2141](https://github.com/oracle/helidon/pull/2141)
- Examples: Update microprofile examples to use microprofile-config.properties [2163](https://github.com/oracle/helidon/pull/2163)
- Examples: Swapped ports in mutual TLS example [2184](https://github.com/oracle/helidon/pull/2184)
- Examples: Quickstart gradle fixes [2214](https://github.com/oracle/helidon/pull/2214)
- Examples: Change Dockerfiles to use maven.test.skip (not skipTests) [2135](https://github.com/oracle/helidon/pull/2135)
- Examples: Add -Declipselink.weave.skip to quickstart Dockerfiles [2146](https://github.com/oracle/helidon/pull/2146)
- Docs: update MP tutorial to be correct for Helidon 2.0 [2152](https://github.com/oracle/helidon/pull/2152)
- Docs: replaced table with new cards/icons [2185](https://github.com/oracle/helidon/pull/2185)
- Docs: fix minor issues #2081 #427 [2119](https://github.com/oracle/helidon/pull/2119)
- Docs: Move CLI documentation to a single page under About [2148](https://github.com/oracle/helidon/pull/2148)
- Docs: Minor doc fixes #2093 #2086 #2085 [2118](https://github.com/oracle/helidon/pull/2118)
- Docs: Migration Guides: add section for Getters returning Optional. Fix code blocks. [2217](https://github.com/oracle/helidon/pull/2217)
- Docs: Corrected dependency information in the JPA guide for MP. [2198](https://github.com/oracle/helidon/pull/2198)
- Docs: #2221 Fixes API reference in Metrics docs [2232](https://github.com/oracle/helidon/pull/2232)
- Docs: tls updates [2256](https://github.com/oracle/helidon/pull/2256)
- CLI: Create cli-data for snapshot builds [2137](https://github.com/oracle/helidon/pull/2137)
- CDI: Upgrade Weld to 3.1.4 [2177](https://github.com/oracle/helidon/pull/2177)
- CDI: Support for bean producers in different package than beans that have … [2241](https://github.com/oracle/helidon/pull/2241)
- CDI: Introduces an ExecutorServices implementation to Helidon's repackaged Weld implementation to ensure context classloaders are never null [2269](https://github.com/oracle/helidon/pull/2269)
- Archetypes: Properly use path() when creating invocations [2116](https://github.com/oracle/helidon/pull/2116)
- Archetypes: Added <relativePath/> to avoid warnings  [2117](https://github.com/oracle/helidon/pull/2117)
- Tests: Weaken test assertion for executions in very slow systems [2287](https://github.com/oracle/helidon/pull/2287)
- Tests: Set junit4 version where used. Fixes #2289 [2290](https://github.com/oracle/helidon/pull/2290)
- Tests: Removed unnecessary plugin versions, now inherited. [2181](https://github.com/oracle/helidon/pull/2181)


## [2.0.1]

2.0.1 is a minor bug fix release to Helidon 2.0.0. This release fixes a
key issue in Config as well as some issues in documentation and
example archetypes.

### Fixes

- Config: Support for SE mappers in MP. [2091](https://github.com/oracle/helidon/pull/2091)
- Archetype fails on maven version detection on some Linux distributions [2102](https://github.com/oracle/helidon/issues/2102) [2106](https://github.com/oracle/helidon/pull/2106)
- Archetypes: Some fixes and improvements to the db archetypes [2100](https://github.com/oracle/helidon/pull/2100)
- GraalVM: Update docs, READMEs, Dockerfiles to graalvm 20.1.0 [2103](https://github.com/oracle/helidon/pull/2103)
- Docs: add basic documentation for CLI [2101](https://github.com/oracle/helidon/pull/2101) [2094](https://github.com/oracle/helidon/pull/2094)

## [2.0.0]

Helidon 2.0.0 is a major release that includes significant new features and fixes.
As a major release it also includes some backward incompatible API changes.
See section below.

### Notable New Features

- Helidon MP GraalVM Native Image support (in addition to existing Helidon SE GraalVM Native Image support)
- Jakarta WebSocket support
- CORS support
- Easy generation of jlink custom runtime images including support for CDS archives for improved startup performance
- Move to Java 11 APIs
- Move to Jakarta EE APIs
- Improved discovery and handling of JAX-RS applications
- New MediaSupport API
- Plus many other minor features and fixes

The following are early access features that are ready for developer use:

- Helidon SE reactive Web Client
- Helidon SE reactive DB Client
- MicroProfile Reactive Streams Operators
- MicroProfile Reactive Streams Messaging
- The Helidon CLI for creating new projects and fast iterative development

For more information see our documentation at 
[Helidon 2.0.0 Documentation ](https://helidon.io/docs/v2/#/about/01_overview)

### Changes

Changes between 2.0.0-RC2 and 2.0.0:

- Config: SE Config can be created from MP Config  [2060](https://github.com/oracle/helidon/pull/2060)
- Native image: Upgrade svm, annotation for reflection [2070](https://github.com/oracle/helidon/pull/2070)
- Native image: JPA tests and native-image build changes [2014](https://github.com/oracle/helidon/pull/2014)
- Native image updates [2050](https://github.com/oracle/helidon/pull/2050)
- Native Image: Oracle DB with JPA [2044](https://github.com/oracle/helidon/pull/2044)
- WebServer: Content now extends Multi [2043](https://github.com/oracle/helidon/pull/2043)
- MediaSupport: media support deprecations cleanup [2068](https://github.com/oracle/helidon/pull/2068)
- Reactive: Cancelling race condition fix [2027](https://github.com/oracle/helidon/pull/2027)
- Security: Secure and httpOnly used correctly in SetCookie [2056](https://github.com/oracle/helidon/pull/2056)
- Archetypes: Update MP db archetype to compile with native image. [2073](https://github.com/oracle/helidon/pull/2073)
- Archetypes: DB Archetypes: README updates for native support [2065](https://github.com/oracle/helidon/pull/2065)
- Archetypes: Remove unused import from template [2048](https://github.com/oracle/helidon/pull/2048)
- Documentation: update for AOT. [2045](https://github.com/oracle/helidon/pull/2045)
- Documentation: Collapsible docs menus / integrate build-tools 2.0.0 [2067](https://github.com/oracle/helidon/pull/2067)
- Documentation: Docs cleanup [2074](https://github.com/oracle/helidon/pull/2074)
- Documentation: Various AsciiDoctor warning fixes [2072](https://github.com/oracle/helidon/pull/2072)
- Documentation: intro for JWT auth - link to mp spec [2069](https://github.com/oracle/helidon/pull/2069)
- Documentation: Fixed broken links; added new  [2071](https://github.com/oracle/helidon/pull/2071)
- Documentatino: SE Messaging doc [2029](https://github.com/oracle/helidon/pull/2029)
- Documentation: WebClient doc update [2064](https://github.com/oracle/helidon/pull/2064)
- Documentation: fixed broken links in the intro [2022](https://github.com/oracle/helidon/pull/2022)


### Backward incompatible changes

In order to stay current with dependencies and also refine our APIs we have 
introduced some backward incompatible changes in this release. For details
see the
[Helidon 2.0 MP Migration Guide](https://helidon.io/docs/v2/#/mp/guides/15_migration)
and the
[Helidon 2.0 SE Migration Guide](https://helidon.io/docs/v2/#/se/guides/15_migration)

#### Thank You!

Thanks to community members [dansiviter](https://github.com/dansiviter), [graemerocher](https://github.com/graemerocher) ,
and [akarnokd](https://github.com/akarnokd) for their contributions to this release.

## [2.0.0-RC2]

### Notes

This is the second release candidate of Helidon 2.0.

### Notable New Features

This release focuses on documentation, bug fixes, performance fixes and cleanup.

Also, a number of deprecated methods have been removed from this release. See
"Remove deprecations" in the list below.

### Changes

- CORS: Change CORS config key from path-prefix to path-expr; method names also [1807](https://github.com/oracle/helidon/pull/1807)
- Config: Decrypt AES method made visible [2003](https://github.com/oracle/helidon/pull/2003)
- Config: Fix #1802 - Allow use of filters and mappers when converting MP to He… [1803](https://github.com/oracle/helidon/pull/1803)
- Config: Log warning if a known configuration file exists and we have no parser [1853](https://github.com/oracle/helidon/pull/1853)
- Config: Using SafeConstructor with YAML parsing. [2019](https://github.com/oracle/helidon/pull/2019)
- Config: Fixes StackOverflowError unearthed by new MicroProfile Config implementation [1760](https://github.com/oracle/helidon/pull/1760)
- DBClient: doc for issue 1629 [1652](https://github.com/oracle/helidon/pull/1652)
- DBClient: api update to reactive [1828](https://github.com/oracle/helidon/pull/1828)
- DBClient: JSON-P Streaming support [1796](https://github.com/oracle/helidon/pull/1796)
- FaultTolerance: Wait for thread completion only if interrupted flag set [1843](https://github.com/oracle/helidon/pull/1843)
- Features: Experimental feature support. [2018](https://github.com/oracle/helidon/pull/2018)
- Features: Feature refactoring [1944](https://github.com/oracle/helidon/pull/1944)
- Health check fixed [1809](https://github.com/oracle/helidon/pull/1809)
- JAX-RS @Provider autodiscovery [1880](https://github.com/oracle/helidon/pull/1880)
- JDBC: Updated UCP version to always be in sync with Oracle's OJDBC8 version since the two artifacts are mutually dependent [1831](https://github.com/oracle/helidon/pull/1831)
- Jersey: connector to Helidon WebClient [1932](https://github.com/oracle/helidon/pull/1932)
- Jersey: Correctly disable Jersey WADL support and built-in providers. [1971](https://github.com/oracle/helidon/pull/1971)
- Jersey: Make the helidon-jersey-connector module discoverable by Jersey if available [2008](https://github.com/oracle/helidon/pull/2008)
- Jersey: Upgrading Jersey to version 2.31 [1887](https://github.com/oracle/helidon/pull/1887)
- Kafka specific message [1890](https://github.com/oracle/helidon/pull/1890)
- Media Support: DataChunkInputStream more then one close does not throw exception [1904](https://github.com/oracle/helidon/pull/1904)
- Media Support: lazy accepted types parsing [1921](https://github.com/oracle/helidon/pull/1921)
- Media Support: flattening [1899](https://github.com/oracle/helidon/pull/1899)
- Media Support: methods [1905](https://github.com/oracle/helidon/pull/1905)
- Media Support: DataChunk ByteBuffer array [1877](https://github.com/oracle/helidon/pull/1877)
- Media Support: DataChunkInputStream can cause deadlock if handled by the same thread… [1825](https://github.com/oracle/helidon/pull/1825)
- Media Support: DataChunkInputStream char duplication fix [1824](https://github.com/oracle/helidon/pull/1824)
- Media Support: Fix handling of generics when reading objects [1769](https://github.com/oracle/helidon/pull/1769)
- Media Support: Service loader added to MediaContext [1861](https://github.com/oracle/helidon/pull/1861)
- Messaging: Messaging with Kafka examples [2016](https://github.com/oracle/helidon/pull/2016)
- Metrics: Fail deployment for Gauges in RequestScoped beans [1978](https://github.com/oracle/helidon/pull/1978)
- Metrics: Remove MP Metrics 1.0-to-2.0 bridge component and related classes [1879](https://github.com/oracle/helidon/pull/1879)
- MicroProfile: Changed name of SyntheticApplication to HelidonMP [1812](https://github.com/oracle/helidon/pull/1812)
- MicroProfile: Container startup issue fixed. [1912](https://github.com/oracle/helidon/pull/1912)
- Modules: Module and Java 12+ friendly way of defining classes for Weld Proxies. [1967](https://github.com/oracle/helidon/pull/1967)
- Native Image: Database native support update [2028](https://github.com/oracle/helidon/pull/2028)
- Native Image: MP native image no longer misses bean types for proxies. [1988](https://github.com/oracle/helidon/pull/1988)
- Native image fixes for new Jersey version. [1910](https://github.com/oracle/helidon/pull/1910)
- Native image: AOT (native image) documentation for Helidon. [1989](https://github.com/oracle/helidon/pull/1989)
- Native image: Remove obsolete configuration file. [1927](https://github.com/oracle/helidon/pull/1927)
- Native image: Welcome file for classpath static content in native image [1980](https://github.com/oracle/helidon/pull/1980)
- Reactive: Use EmittingPublisher in OutputStreamPublisher to remove busy waiting [1900](https://github.com/oracle/helidon/pull/1900)
- Reactive: Alias for concatArray [1826](https://github.com/oracle/helidon/pull/1826)
- Reactive: BufferedEmittingPublisher as replacement for OriginThreadPublisher [1830](https://github.com/oracle/helidon/pull/1830)
- Reactive: BufferedEmittingPublisher draining race condition fix [1928](https://github.com/oracle/helidon/pull/1928)
- Reactive: Deprecated Multi#from [1888](https://github.com/oracle/helidon/pull/1888)
- Reactive: Emit after close fix [1856](https://github.com/oracle/helidon/pull/1856)
- Reactive: EmittingPublisher cleanup [1923](https://github.com/oracle/helidon/pull/1923)
- Reactive: Multi await feature for intentional blocking [1664](https://github.com/oracle/helidon/pull/1664)
- Reactive: Multi from InputStream [1770](https://github.com/oracle/helidon/pull/1770)
- Reactive: Multi onComplete operators [1806](https://github.com/oracle/helidon/pull/1806)
- Reactive: MultiFromOutputStream blocking close refactor [1943](https://github.com/oracle/helidon/pull/1943)
- Reactive: ReadableByteChannelPublisher executor leak [1924](https://github.com/oracle/helidon/pull/1924)
- Reactive: Reimplement Concat with varargs [1815](https://github.com/oracle/helidon/pull/1815)
- Reactive: Remove OriginThreadPublisher [1859](https://github.com/oracle/helidon/pull/1859)
- Reactive: ResponseCloser now supports Single [1883](https://github.com/oracle/helidon/pull/1883)
- Reactive: Revert filters as function instead of reactive processors. [1917](https://github.com/oracle/helidon/pull/1917)
- Reactive: SE Reactive Messaging [1636](https://github.com/oracle/helidon/pull/1636)
- Reactive: Single onCancel and OnComplete fixes [1814](https://github.com/oracle/helidon/pull/1814)
- Reactive: Trigger Single stream only in terminal ops [1864](https://github.com/oracle/helidon/pull/1864)
- Reactive: Trigger Single to CS conversion on first CS method call [1886](https://github.com/oracle/helidon/pull/1886)
- Security: Rearrange the messages which report missing config  [1810](https://github.com/oracle/helidon/pull/1810)
- Security: Security level memory leak [2013](https://github.com/oracle/helidon/pull/2013)
- Security: master: correctly validate mandatory JWT claims. [2011](https://github.com/oracle/helidon/pull/2011)
- Tracing performance optimization. [1916](https://github.com/oracle/helidon/pull/1916)
- WebClient: API changed from CompletionStage to Single [1832](https://github.com/oracle/helidon/pull/1832)
- WebClient: API update [1870](https://github.com/oracle/helidon/pull/1870)
- WebClient: automatic system loader [1903](https://github.com/oracle/helidon/pull/1903)
- WebClientL minor proxy fixes [1792](https://github.com/oracle/helidon/pull/1792)
- WebClient: SSL in WebClient and WebServer changed to TLS [2006](https://github.com/oracle/helidon/pull/2006)
- WebClient: TemporalUnit methods removed [2004](https://github.com/oracle/helidon/pull/2004)
- WebClient/DBClient: Alignment of client service APIs between Db and Web Clients. [1863](https://github.com/oracle/helidon/pull/1863)
- WebServer API to use Single and Multi [1882](https://github.com/oracle/helidon/pull/1882)
- WebServer configuration changes [1766](https://github.com/oracle/helidon/pull/1766)
- WebServer: Allows proxying of ServerRequest v2.x/master [1878](https://github.com/oracle/helidon/pull/1878)
- WebServer: Check Netty validation of headers before processing request [1827](https://github.com/oracle/helidon/pull/1827)
- WebServer: Fix #1711 StaticContentHandler fails with encoded URLs [1811](https://github.com/oracle/helidon/pull/1811)
- WebServer: Lazy list for even lazier acceptedTypes parsing [1940](https://github.com/oracle/helidon/pull/1940)
- WebServer: Multipart [1787](https://github.com/oracle/helidon/pull/1787)
- WebServer: Mutual TLS implementation, example and tests [1992](https://github.com/oracle/helidon/pull/1992)
- WebServer: Socket configuration changes. [1844](https://github.com/oracle/helidon/pull/1844)
- WebServer: Static content handlers now use explicit writers. [1922](https://github.com/oracle/helidon/pull/1922)
- WebServer: Updated SSL Configuration for WebServer [1852](https://github.com/oracle/helidon/pull/1852)
- WebSockets example: HTML page for user experience [1946](https://github.com/oracle/helidon/pull/1946)
- gRPC: client API improvements (2.0) [1851](https://github.com/oracle/helidon/pull/1851)
- gRPC: Add JSONB support to gRPC [1836](https://github.com/oracle/helidon/pull/1836)
- gRPC: Minor gRPC 2.0 fixes and improvements [1959](https://github.com/oracle/helidon/pull/1959)
- gRPC: Minor gRPC fixes (2.0) [1951](https://github.com/oracle/helidon/pull/1951)
- gRPC: Revert "Minor gRPC fixes (2.0)" [1956](https://github.com/oracle/helidon/pull/1956)
- Archetypes: (and other examples) typo [1981](https://github.com/oracle/helidon/pull/1981)
- Archetypes: catalog [1898](https://github.com/oracle/helidon/pull/1898)
- Archetypes: Fixed tag in catalog [2036](https://github.com/oracle/helidon/pull/2036)
- Archetypes: New DB archetype for SE [1982](https://github.com/oracle/helidon/pull/1982)
- Archetypes: New application types (templates) for the CLI [1797](https://github.com/oracle/helidon/pull/1797)
- Archetypes: Quickstart archetypes for SE and MP [2035](https://github.com/oracle/helidon/pull/2035)
- Archetypes: Renamed archetype to helidon-bare-mp [2021](https://github.com/oracle/helidon/pull/2021)
- Archetypes: Renamed basic archetype to bare [1839](https://github.com/oracle/helidon/pull/1839)
- Archetypes: Use helidon-archetype packaging [1889](https://github.com/oracle/helidon/pull/1889)
- Archetypes: isolate the helidon-maven-plugin extension  [2023](https://github.com/oracle/helidon/pull/2023)
- Deprecations: Remove deprecations [1884](https://github.com/oracle/helidon/pull/1884) [1885](https://github.com/oracle/helidon/pull/1885) [1892](https://github.com/oracle/helidon/pull/1892)
- Docs: helidon intro 1625 [1701](https://github.com/oracle/helidon/pull/1701)
- Docs: helidon se intro 1626 [1729](https://github.com/oracle/helidon/pull/1729)
- Docs: Add CORS MP and SE doc [1895](https://github.com/oracle/helidon/pull/1895)
- Docs: Fix broken link in prerequisite page to quickstart example [1996](https://github.com/oracle/helidon/pull/1996)
- Docs: Fix formatting issues with included guides [1771](https://github.com/oracle/helidon/pull/1771)
- Docs: Fixed SE Config Guide typos from issue 1649 [1993](https://github.com/oracle/helidon/pull/1993)
- Docs: Fixed tables in Security Providers - issue 1599 [1848](https://github.com/oracle/helidon/pull/1848)
- Docs: Helidon MP 2.0 Intro [1653](https://github.com/oracle/helidon/pull/1653)
- Docs: Mp config docs [1935](https://github.com/oracle/helidon/pull/1935)
- Docs: New doc section for FT documenting internal config properties [2020](https://github.com/oracle/helidon/pull/2020)
- Docs: Update 3rd party attributions. Renamed 3RD-PARTY to THIRD_PARTY_LICEN… [2024](https://github.com/oracle/helidon/pull/2024)
- Docs: Update NOTICE.txt and include in artifacts [1945](https://github.com/oracle/helidon/pull/1945)
- Docs: Update SE docs for changed APIs [1970](https://github.com/oracle/helidon/pull/1970)
- Docs: Updated migration guides. [1920](https://github.com/oracle/helidon/pull/1920)
- Docs: WebClient Intro doc updated with changes [1659](https://github.com/oracle/helidon/pull/1659)
- Docs: migration guides [1829](https://github.com/oracle/helidon/pull/1829)
- Docs: updated mp jpa intro - added link to mp jpa guide and API [1953](https://github.com/oracle/helidon/pull/1953)
- Tests: Add tolerance to metrics tests; widen tolerance used during pipeline runs (2.x) [1999](https://github.com/oracle/helidon/pull/1999)
- Tests: Decrease the number of events of KafkaCdiExtensionTest [1763](https://github.com/oracle/helidon/pull/1763)
- Tests: Fix intermittent test failure in GrpcServiceTest. [1947](https://github.com/oracle/helidon/pull/1947)
- Tests: Fix test failing on windows [1919](https://github.com/oracle/helidon/pull/1919)
- Tests: Fixed archetype test based on API changes [1818](https://github.com/oracle/helidon/pull/1818)
- Tests: MP Messaging hamcrestination [1765](https://github.com/oracle/helidon/pull/1765)
- Tests: Move test dependencies into main pom [1938](https://github.com/oracle/helidon/pull/1938)
- Tests: Moving 2 tests from KafkaMP to KafkaSE [1918](https://github.com/oracle/helidon/pull/1918)
- Tests: Properly stop wiremock after rest-client tcks [1939](https://github.com/oracle/helidon/pull/1939)
- Tests: Redesign in Kafka tests [1847](https://github.com/oracle/helidon/pull/1847)
- Tests: Removes mp-test-libs bundle [1813](https://github.com/oracle/helidon/pull/1813)
- Tests: Sometimes KafkaCdiExtensionTest fails [1816](https://github.com/oracle/helidon/pull/1816)
- Examples: Add trademark notices for Pokemon [2034](https://github.com/oracle/helidon/pull/2034)
- Examples: WIP: New JPA example and archetype using Pokemons [1933](https://github.com/oracle/helidon/pull/1933)

### Backward incompatible changes

#### WebClient and WebServer TLS class names aligned
Class configuration names for TLS are now aligned between WebClient and WebServer

| Old Name                 | New Name           |
| ------------------------ | ------------------ |
| `Ssl`                    | `WebClientTls`     |
| `TlsConfig`              | `WebServerTls`     |


#### Thank You!

Thanks to community members [dansiviter](https://github.com/dansiviter) and [graemerocher](https://github.com/graemerocher) 
for their contributions to this release.

## [2.0.0-RC1]

### Notes

This is the first release candidate of Helidon 2.0.

### Notable New Features

This release primarily focuses on finalizing APIs for 2.0. It also includes a number
of performance and bug fixes. We expect APIs to be pretty stable between this 
release and the final 2.0.0 release.

### Changes

- CORS: Change CORS config key from path-prefix to path-expr; method names also [1807](https://github.com/oracle/helidon/pull/1807)
- Config: Fix #1802 - Allow use of filters and mappers when converting MP to He… [1803](https://github.com/oracle/helidon/pull/1803)
- Config: Log warning if a known configuration file exists and we have no parser [1853](https://github.com/oracle/helidon/pull/1853)
- Config: Rearrange the messages which report missing config  [1810](https://github.com/oracle/helidon/pull/1810)
- DBClient: DB client api update to reactive [1828](https://github.com/oracle/helidon/pull/1828)
- DBClient: JSON-P Streaming support [1796](https://github.com/oracle/helidon/pull/1796)
- FaultTolerance: Wait for thread completion only if interrupted flag set [1843](https://github.com/oracle/helidon/pull/1843)
- Health: Health check fixed [1809](https://github.com/oracle/helidon/pull/1809)
- JAX-RS: JAX-RS @Provider autodiscovery [1880](https://github.com/oracle/helidon/pull/1880)
- JDBC: Updated UCP version to always be in sync with Oracle's OJDBC8 version since the two artifacts are mutually dependent [1831](https://github.com/oracle/helidon/pull/1831)
- MediaSupport: DataChunkInputStream char duplication fix [1824](https://github.com/oracle/helidon/pull/1824)
- MediaSupport: Fix handling of generics when reading objects [1769](https://github.com/oracle/helidon/pull/1769)
- MediaSupport: Media support flattening [1899](https://github.com/oracle/helidon/pull/1899)
- MediaSupport: Media support methods [1905](https://github.com/oracle/helidon/pull/1905)
- MediaSupport: Revert context to original read only parameters. [1819](https://github.com/oracle/helidon/pull/1819)
- MediaSupport: Service loader added to MediaContext [1861](https://github.com/oracle/helidon/pull/1861)
- Metrics: Remove MP Metrics 1.0-to-2.0 bridge component and related classes [1879](https://github.com/oracle/helidon/pull/1879)
- MicroProfile: Changed name of SyntheticApplication to HelidonMP [1812](https://github.com/oracle/helidon/pull/1812)
- MicroProfile: Container startup issue fixed. [1912](https://github.com/oracle/helidon/pull/1912)
- NativeImage:Native image fixes for new Jersey version. [1910](https://github.com/oracle/helidon/pull/1910)
- OCI ObjectStore: Fixes StackOverflowError unearthed by new MicroProfile Config implementation [1760](https://github.com/oracle/helidon/pull/1760)
- Performance: DataChunk ByteBuffer array [1877](https://github.com/oracle/helidon/pull/1877)
- Performance: Media lazy accepted types parsing [1921](https://github.com/oracle/helidon/pull/1921)
- Performance: Tracing performance optimization. [1916](https://github.com/oracle/helidon/pull/1916)
- Reactive: Alias for concatArray [1826](https://github.com/oracle/helidon/pull/1826)
- Reactive: BufferedEmittingPublisher as replacement for OriginThreadPublisher [1830](https://github.com/oracle/helidon/pull/1830)
- Reactive: Decrease the number of events of KafkaCdiExtensionTest [1763](https://github.com/oracle/helidon/pull/1763)
- Reactive: Deprecated Multi#from [1888](https://github.com/oracle/helidon/pull/1888)
- Reactive: Emit after close fix [1856](https://github.com/oracle/helidon/pull/1856)
- Reactive: Kafka specific message [1890](https://github.com/oracle/helidon/pull/1890)
- Reactive: Multi await feature for intentional blocking [1664](https://github.com/oracle/helidon/pull/1664)
- Reactive: Multi from InputStream [1770](https://github.com/oracle/helidon/pull/1770)
- Reactive: Multi onComplete operators [1806](https://github.com/oracle/helidon/pull/1806)
- Reactive: Redesign in Kafka tests [1847](https://github.com/oracle/helidon/pull/1847)
- Reactive: Reimplement Concat with varargs [1815](https://github.com/oracle/helidon/pull/1815)
- Reactive: Remove OriginThreadPublisher [1859](https://github.com/oracle/helidon/pull/1859)
- Reactive: ResponseCloser now supports Single [1883](https://github.com/oracle/helidon/pull/1883)
- Reactive: Revert filters as function instead of reactive processors. [1917](https://github.com/oracle/helidon/pull/1917)
- Reactive: SE Reactive Messaging [1636](https://github.com/oracle/helidon/pull/1636)
- Reactive: Single onCancel and OnComplete fixes [1814](https://github.com/oracle/helidon/pull/1814)
- Reactive: Sometimes KafkaCdiExtensionTest fails [1816](https://github.com/oracle/helidon/pull/1816)
- Reactive: Trigger Single stream only in terminal ops [1864](https://github.com/oracle/helidon/pull/1864)
- Reactive: Trigger Single to CS conversion on first CS method call [1886](https://github.com/oracle/helidon/pull/1886)
- Reactive: Use EmittingPublisher in OutputStreamPublisher to remove busy waiting [1900](https://github.com/oracle/helidon/pull/1900)
- Security: Fixed tables in Security Providers - issue 1599 [1848](https://github.com/oracle/helidon/pull/1848)
- WebClient DBClient: Alignment of client service APIs between Db and Web Clients. [1863](https://github.com/oracle/helidon/pull/1863)
- WebClient: API changed from CompletionStage to Single [1832](https://github.com/oracle/helidon/pull/1832)
- WebClient: WebClient API update [1870](https://github.com/oracle/helidon/pull/1870)
- WebClient: automatic system loader [1903](https://github.com/oracle/helidon/pull/1903)
- WebClient: minor proxy fixes [1792](https://github.com/oracle/helidon/pull/1792)
- WebServer: Check Netty validation of headers before processing request [1827](https://github.com/oracle/helidon/pull/1827)
- WebServer: DataChunkInputStream can cause deadlock if handled by the same thread… [1825](https://github.com/oracle/helidon/pull/1825)
- WebServer: DataChunkInputStream more then one close does not throw exception [1904](https://github.com/oracle/helidon/pull/1904)
- WebServer: Fix #1711 StaticContentHandler fails with encoded URLs [1811](https://github.com/oracle/helidon/pull/1811)
- WebServer: Multipart [1787](https://github.com/oracle/helidon/pull/1787)
- WebServer: Socket configuration changes. [1844](https://github.com/oracle/helidon/pull/1844)
- WebServer: Updated SSL Configuration for WebServer [1852](https://github.com/oracle/helidon/pull/1852)
- WebServer: WebServer API to use Single and Multi [1882](https://github.com/oracle/helidon/pull/1882)
- WebServer: configuration changes [1766](https://github.com/oracle/helidon/pull/1766)
- gRPC: Add JSONB support to gRPC [1836](https://github.com/oracle/helidon/pull/1836)
- gRPC: gRPC client API improvements (2.0) [1851](https://github.com/oracle/helidon/pull/1851)
- Build: enable unstable pipeline status [1768](https://github.com/oracle/helidon/pull/1768)
- CLI: Archetype catalog [1898](https://github.com/oracle/helidon/pull/1898)
- CLI: Configure helidon-maven-plugin extension for all apps [1896](https://github.com/oracle/helidon/pull/1896)
- CLI: New application types (templates) for the CLI [1797](https://github.com/oracle/helidon/pull/1797)
- CLI: Renamed basic archetype to bare [1839](https://github.com/oracle/helidon/pull/1839)
- CLI: Use helidon-archetype packaging [1889](https://github.com/oracle/helidon/pull/1889)
- Dependencies: Upgrading Jersey to version 2.31 [1887](https://github.com/oracle/helidon/pull/1887)
- Deprecations: Remove deprecations [1884](https://github.com/oracle/helidon/pull/1884) [1885](https://github.com/oracle/helidon/pull/1885) [1892](https://github.com/oracle/helidon/pull/1892)
- Documentatino: Fix formatting issues with included guides [1771](https://github.com/oracle/helidon/pull/1771)
- Documentation: Add CORS MP and SE doc [1895](https://github.com/oracle/helidon/pull/1895)
- Documentation: Docs helidon intro 1625 [1701](https://github.com/oracle/helidon/pull/1701)
- Documentation: Docs helidon se intro 1626 [1729](https://github.com/oracle/helidon/pull/1729)
- Documentation: Updated migration guides. [1920](https://github.com/oracle/helidon/pull/1920)
- Documentation: migration guides [1829](https://github.com/oracle/helidon/pull/1829)
- Tests: Fix test failing on windows [1919](https://github.com/oracle/helidon/pull/1919)

### Backward incompatible changes

#### gRPC: Renamed several annotations and classes

As part of gRPC API cleanup, we have renamed the following annotations and classes:

| Old Name                 | New Name           |
| ------------------------ | ------------------ |
| `@RpcService`            | `@Grpc`            |
| `@RpcMethod`             | `@GrpcMethod`      |
| `@GrpcServiceProxy`      | `@GrpcProxy`       |
| `GrpcClientProxyBuilder` | `GrpcProxyBuilder` |

While in general we prefer not to break backwards compatibility by renaming public API
classes, we felt that in this case the change was warranted and acceptable, for several reasons:

1. gRPC API was marked experimental in Helidon 1.x
2. While using gRPC in our own applications, we have realized that the code in some cases
   does not read as well as it should, and that some class and annotation names should be changed
   to improve both internal API consistency and readability

We apologize for the inconvenience, but we do feel that the impact of the changes is minimal
and that the changes will be beneficial in the long run.     

#### Internal `helidon-common-metrics` and Related Classes Removed
Later releases of Helidon 1.x included the `helidon-common-metrics` component and related
classes which provided a common interface to ease the transition from MicroProfile 
Metrics 1.x to 2.x. 
Although intended for use only by Helidon subsystems rather than 
by developers and users, the component and its contents had to be public so multiple Helidon 
subsystems could use them. 
Therefore, user code might have used these elements.

This release removes this common interface and associated classes. 
Any user code that used these internal classes can use the corresponding supported classes in 
`io.helidon.metrics:helidon-metrics` and MicroProfile Metrics 2.0 instead. 

|Helidon Artifact |Interfaces/Classes |
|--------------|----------------|
|`io.helidon.common:helidon-common-metrics` |Entire artifact, including all `io.helidon.common.metrics...` classes |
|`io.helidon.metrics:helidon-metrics` |`HelidonMetadata` |
| |`InternalBridgeImpl` |
| |`InternalMetadataBuilderImpl` |
| |`InternalMetadataImpl` |
| |`InternalMetricIDImpl` |

## [2.0.0-M3]

### Notes

This is the third milestone release of Helidon 2.0. It contains significant new features,
enhancements and fixes. It also contains backward incompatible changes (see section below).
This milestone release is provided for customers that want early access to Helidon 2.0. It
should be considered unstable and is not intended for production use. APIs and features might
not be fully tested and are subject to change. Documentation is incomplete. Those looking
for a stable release should use a 1.4 release.

### Notable New Features

* Messaging API for SE
* Kafka connector
* CORS support
* Move to Jakarta EE dependencies
* New MediaSupport API

### Changes

- Build: New build infra [1605](https://github.com/oracle/helidon/pull/1605)
- CORS: Add CORS support for SE and MP applications to 2.x [1633](https://github.com/oracle/helidon/pull/1633)
- CORS: Add CORS support to built-in Helidon services [1670](https://github.com/oracle/helidon/pull/1670)
- CORS: Examples [1727](https://github.com/oracle/helidon/pull/1727) [1747](https://github.com/oracle/helidon/pull/1747)
- CORS: processing should only be enabled on successful responses [1683](https://github.com/oracle/helidon/pull/1683)
- CORS: Avoid setting response headers twice [1679](https://github.com/oracle/helidon/pull/1679)
- CORS: Fix CorsSupport default behavior [1714](https://github.com/oracle/helidon/pull/1714)
- CORS: Fix incorrect matching of resource methods annotated with @OPTIONS [1744](https://github.com/oracle/helidon/pull/1744)
- CORS: Fix isActive computation [1682](https://github.com/oracle/helidon/pull/1682)
- CORS: Improve handling of missing config nodes used to init CORS objects [1709](https://github.com/oracle/helidon/pull/1709)
- CORS: Make clear which methods expect mapped vs. lower-level CORS config nodes [1700](https://github.com/oracle/helidon/pull/1700)
- CORS: Unbundle MP CORS from MP bundle; bolster utility method [1672](https://github.com/oracle/helidon/pull/1672)
- Dependencies: Bump version.plugin.helidon to 2.0.0-M2 [1535](https://github.com/oracle/helidon/pull/1535)
- Dependencies: Dependency convergence + jakarta libraries. [1600](https://github.com/oracle/helidon/pull/1600)
- Dependencies: Update to jakarta GAV for CORS MP [1660](https://github.com/oracle/helidon/pull/1660)
- Dependencies: Upgrade grpc to follow Netty version used by Helidon. [1614](https://github.com/oracle/helidon/pull/1614)
- Docker: Don't remove lib/liblcms.so on graalvm docker image minimization [1577](https://github.com/oracle/helidon/pull/1577)
- Docker: Upgrade docker image to graalvm-ce-java11-linux-amd64-20.0.0 [1569](https://github.com/oracle/helidon/pull/1569)
- Docs: Add custom runtime image guide, maven guide, gradle guide, refactored native image guide.  [1639](https://github.com/oracle/helidon/pull/1639)
- Docs: Helidon Reactive documentation [1483](https://github.com/oracle/helidon/pull/1483)
- Docs: Under Construction pages, icons [1586](https://github.com/oracle/helidon/pull/1586)
- Docs: Update javadoc links in documentation to work with new javadoc layout [1754](https://github.com/oracle/helidon/pull/1754)
- Docs: Updated Config SPI documentation. [1691](https://github.com/oracle/helidon/pull/1691)
- Docs: [WIP] Documentation refactoring [1514](https://github.com/oracle/helidon/pull/1514)
- Docs: update slack link to use slack.helidon.io [1665](https://github.com/oracle/helidon/pull/1665)
- Examples: 2.0 update example Dockerfiles [1706](https://github.com/oracle/helidon/pull/1706)
- Examples: DB Example now correctly non-blocking [1584](https://github.com/oracle/helidon/pull/1584)
- Examples: Fixes for MP quickstart in native image. [1564](https://github.com/oracle/helidon/pull/1564)
- Examples: Mp messaging example [1479](https://github.com/oracle/helidon/pull/1479)
- Examples: Update examples [1715](https://github.com/oracle/helidon/pull/1715)
- Examples: Update quickstarts (including MP) to use new GraalVM 20 docker image. [1576](https://github.com/oracle/helidon/pull/1576)
- Examples: use application pom, clean up READMEs [1680](https://github.com/oracle/helidon/pull/1680)
- GRPC: Move gRPC integration tests to correct location [1621](https://github.com/oracle/helidon/pull/1621)
- GRPC: Port change for #1618 to 2.0 [1686](https://github.com/oracle/helidon/pull/1686)
- JDBC: Updated Oracle JDBC driver Maven coordinates. [1723](https://github.com/oracle/helidon/pull/1723)
- JPA: Fixed JPA example [1746](https://github.com/oracle/helidon/pull/1746)
- JPA: Removes assertions that do not hold when alternatives and specialization are in play [1556](https://github.com/oracle/helidon/pull/1556)
- MP Config: MP Config fixes [1721](https://github.com/oracle/helidon/pull/1721)
- MP Config: Support for mutable config sources to remove regression (even though … [1667](https://github.com/oracle/helidon/pull/1667)
- MediaSupport: MediaSupport usage redesigned [1720](https://github.com/oracle/helidon/pull/1720)
- Messaging: Channel properties must override connector config [1616](https://github.com/oracle/helidon/pull/1616)
- Messaging: Kafka support [1510](https://github.com/oracle/helidon/pull/1510)
- Metrics: Improve metrics interceptor performance; avoid MetricID creations - 2.x [1602](https://github.com/oracle/helidon/pull/1602)
- Metrics: Removed unnecessary synchronization in metrics registry [1575](https://github.com/oracle/helidon/pull/1575)
- MicroProfile: Fix ordering of static content and Jersey on server startup [1675](https://github.com/oracle/helidon/pull/1675)
- MicroProfile: Restart support for CDI regardless of mode used. [1613](https://github.com/oracle/helidon/pull/1613)
- Native Image: Enable reflection init of NioSocketChannel with native image [1722](https://github.com/oracle/helidon/pull/1722)
- Native Image: MP native image updates [1694](https://github.com/oracle/helidon/pull/1694)
- Native Image: OpenAPI support for native-image [1553](https://github.com/oracle/helidon/pull/1553)
- OpenAPI: Do not use cached URLs for locating the Jandex indexes [1590](https://github.com/oracle/helidon/pull/1590)
- OpenAPI: Include dynamically-registered apps in just-in-time jandex index 2.x [1555](https://github.com/oracle/helidon/pull/1555)
- OpenAPI: Order the apps to run by class name to provide more predictable ordering [1593](https://github.com/oracle/helidon/pull/1593)
- Reactive: Function compatible mapper [1637](https://github.com/oracle/helidon/pull/1637)
- Reactive: New implementation of PublisherInputStream that improves performance and fixes race conditions [1690](https://github.com/oracle/helidon/pull/1690)
- Reactive: OutputStreamPublisher now handles close on its own. [1732](https://github.com/oracle/helidon/pull/1732)
- Reactive: Reactive cleanup [1572](https://github.com/oracle/helidon/pull/1572)
- Reactive: Route cancellation to a background thread for the TCK's sake [1608](https://github.com/oracle/helidon/pull/1608)
- Reactive: Try a different TestNG annotation for the TCK test [1571](https://github.com/oracle/helidon/pull/1571)
- Reactive: Add more time to MultiTimeoutFallbackTckTest [1717](https://github.com/oracle/helidon/pull/1717)
- Reactive: Apply bugfixes from #1511 [1543](https://github.com/oracle/helidon/pull/1543)
- Reactive: FIXMEs and signature corrections [1579](https://github.com/oracle/helidon/pull/1579)
- Reactive: Fix Multi.flatMap losing items on the fastpath when conditions are not met [1669](https://github.com/oracle/helidon/pull/1669)
- Reactive: Fix Multi.timeout onSubscribe/schedule race violating the spec [1674](https://github.com/oracle/helidon/pull/1674)
- Reactive: Fluent conversion via compose() & to() [1592](https://github.com/oracle/helidon/pull/1592)
- Reactive: Implement observeOn + TCK tests [1546](https://github.com/oracle/helidon/pull/1546)
- Reactive: Implement retry() + TCK tests [1541](https://github.com/oracle/helidon/pull/1541)
- Reactive: Implement timeout() + TCK tests [1532](https://github.com/oracle/helidon/pull/1532)
- Reactive: Reduce the overhead of flatMapIterable + minor fixes [1563](https://github.com/oracle/helidon/pull/1563)
- Reactive: Reimplement Microprofile-RS, fix operators, add from(CS) [1511](https://github.com/oracle/helidon/pull/1511)
- Reactive: Reorganize methods, remove Single.mapMany [1603](https://github.com/oracle/helidon/pull/1603)
- Reactive: Verify flatMap doesn't reorder items from the same source [1687](https://github.com/oracle/helidon/pull/1687)
- Reactive: Workaround for an RS 1.0.3 TCK bug [1568](https://github.com/oracle/helidon/pull/1568)
- Security: Scope audience and scopes now correctly appended. [1728](https://github.com/oracle/helidon/pull/1728)
- Security: Support for IDCS specific feature in OIDC config. [1688](https://github.com/oracle/helidon/pull/1688)
- Test: Add support for native-image tests to smoketest script [1622](https://github.com/oracle/helidon/pull/1622)
- Test: Fix keys and certs used in gRPC TLS/SSL tests [1597](https://github.com/oracle/helidon/pull/1597)
- Test: Tweak smoketest.sh script and fix typos in MP quickstart readme [1550](https://github.com/oracle/helidon/pull/1550)
- Test: Unit test fix to work with daylight savings. [1598](https://github.com/oracle/helidon/pull/1598)
- Test: Update bookstore test to work with modulepath [1617](https://github.com/oracle/helidon/pull/1617)
- Tests: Improved testing for DataChunkInputStream [1692](https://github.com/oracle/helidon/pull/1692)
- Tracing: Support for TracerResolver. [1574](https://github.com/oracle/helidon/pull/1574)
- WebClient: Proxy changed from interface to class [1537](https://github.com/oracle/helidon/pull/1537)
- WebClient: Several minor fixes and improvements to WebClient [1751](https://github.com/oracle/helidon/pull/1751)
- WebClient: WebClient example added [1539](https://github.com/oracle/helidon/pull/1539)
- WebClient: WebClient used in SE examples and tests [1646](https://github.com/oracle/helidon/pull/1646)
- WebServer: Changed the default context root for Websocket endpoints [1624](https://github.com/oracle/helidon/pull/1624)
- WebServer: Fixed static content handling bug. [1642](https://github.com/oracle/helidon/pull/1642)
- WebServer: Updated WebSocket docs based on latest changes [1631](https://github.com/oracle/helidon/pull/1631)
- WebServer: Using wrapped executor for Jersey async. [1647](https://github.com/oracle/helidon/pull/1647)

#### Thank You!

Thanks to community member David Karnok [akarnokd](https://github.com/akarnokd) for his
significant contributions to our reactive support.

### Backward incompatible changes

#### Removal of processor-like operators
The formerly public `Flow.Processor` implementations performing common operations have been removed. 
Users should use the respective operators from `Single` and `Multi` instead:

```java
// before
Flow.Publisher<Integer> source = ...
MappingProcessor<Integer, String> mapper = new MappingProcessor<>(Integer::toString);
source.subscribe(mapper);
mapper.subscribe(subscriber);

// after
Flow.Publisher<Integer> source = ...
Multi.from(source)
     .map(Integer::toString)
     .subscribe(subscriber)
```

#### Removal of Flows
The class was providing basic `Flow.Publisher` implementations. Users should pick one of the static methods of 
`Single` or `Multi` instead, which provide the additional benefits of having fluent operators available to them for 
assembling reactive flows conveniently:
```java
// before
Flow.Publisher<Integer> just = Flows.singletonPublisher(1);
Flow.Publisher<Object> empty = Flows.emptyPublisher();

// after
Multi<Integer> just1 = Multi.singleton(1);
Single<Integer> just2 = Single.just(1);

Multi<Object> empty1 = Multi.empty();
Single<Object> empty2 = Single.empty();
```

#### MediaSupport refactored
The `MediaSupport` class has been used as holder object of media operator contexts. Now, the name has changed to `MediaContext`, 
and`MediaSupport` will be the name given to the interface which defines media support for given type (readers, writers etc.)  
The Classes `JsonProcessing`, `JsonBinding` and `Jackson` are now renamed to `JsonpSupport`, `JsonbSupport` and `JacksonSupport` 
and are implementing the `MediaSupport` interface.

```java
//before
JsonProcessing jsonProcessing = new JsonProcessing();
MediaSupport mediaSupport = MediaSupport.builder()
    .registerReader(jsonProcessing.newReader())
    .registerWriter(jsonProcessing.newWriter())
    .build();

WebServer.builder()
    .mediaSupport(mediaSupport)
    .build();

//after
WebServer.builder()
    .addMediaSupport(JsonpSupport.create()) //registers reader and writer for Json-P
    .build()
```

#### Also See M1 and M2 Notes

## [2.0.0-M2] 

### Notes

This is the second milestone release of Helidon 2.0. It contains significant new features,
enhancements and fixes. It also contains backward incompatible changes (see section below).
This milestone release is provided for customers that want early access to Helidon 2.0. It
should be considered unstable and is not intended for production use. APIs and features might
not be fully tested and are subject to change. Documentation is incomplete. Those looking for
a stable release should use a 1.4 release.

### Notable New Features

* Helidon Web Client
* MicroProfile Reactive Streams Operators
* MicroProfile Reactive Messaging
* Multi and Single extended with the set of new reactive operators
* Preliminary WebSocket support
* Preliminary jlink image support

### Changes

- Config: Configuration changes [1357](https://github.com/oracle/helidon/pull/1357)
- Config: Default config now loads without duplicates for MP [1369](https://github.com/oracle/helidon/pull/1369)
- Config: Fix merging of value nodes in config 2.0 [1488](https://github.com/oracle/helidon/pull/1488)
- Config: Removed null params and return types from Config API. [1345](https://github.com/oracle/helidon/pull/1345)
- Config: Resource from config refactoring. [1530](https://github.com/oracle/helidon/pull/1530)
- Config: Stopped executor service will not cause an error in polling strategy. [1484](https://github.com/oracle/helidon/pull/1484)
- Config: cache is not using SoftReference anymore. [1494](https://github.com/oracle/helidon/pull/1494)
- Config: change support refactoring [1417](https://github.com/oracle/helidon/pull/1417)
- DB Client: Mappers for database date/time/timestamps. [1485](https://github.com/oracle/helidon/pull/1485)
- Docker Image: Added libstdc++-6-dev to the image [1414](https://github.com/oracle/helidon/pull/1414)
- Examples: Remove MP quickstart GreetApplication#getClasses method for auto-discovery [1395](https://github.com/oracle/helidon/pull/1395)
- Examples: Gradle file cleanup. Fix deprecations. [1354](https://github.com/oracle/helidon/pull/1354)
- Examples: Add OpenAPI annotations to MP quickstart for input and responses on updateGreeting [1394](https://github.com/oracle/helidon/pull/1394)
- JAX-RS: Safeguard against JAX-RS app modifications after start. [1486](https://github.com/oracle/helidon/pull/1486)
- JAX-RS: Upgrade Jersey [1438](https://github.com/oracle/helidon/pull/1438)
- JPA: Resolves JTA/JPA transaction synchronization issues [1473](https://github.com/oracle/helidon/pull/1473)
- JPA: Ensures that JtaDataSource instances that are acquired when a transaction is already active have their Synchronizations registered appropriately [1497](https://github.com/oracle/helidon/pull/1497)
- JPA: Added further tests in TestJpaTransactionScopedSynchronizedEntityManager.java [1453](https://github.com/oracle/helidon/pull/1453)
- JPA: Added more JPA tests [1355](https://github.com/oracle/helidon/pull/1355)
- JPA: Added small test to verify database changes in existing JPA test [1471](https://github.com/oracle/helidon/pull/1471)
- Javadoc: Java 11 javadoc fixes. Turn on failOnError [1386](https://github.com/oracle/helidon/pull/1386)
- Media Support: New media support API. [1356](https://github.com/oracle/helidon/pull/1356)
- Media Support: Adds Config into MediaSupport#builder() method [1403](https://github.com/oracle/helidon/pull/1403)
- Messaging: MP Reactive Messaging impl [1287](https://github.com/oracle/helidon/pull/1287)
- Messaging: Fix completable queue and clean-up of messaging tests [1499](https://github.com/oracle/helidon/pull/1499)
- Metrics: Prometheus format problems (master) [1440](https://github.com/oracle/helidon/pull/1440)
- MicroProfile Server now properly fails when CDI is started externally. [1371](https://github.com/oracle/helidon/pull/1371)
- Native Image: JPA and JTA for native image [1478](https://github.com/oracle/helidon/pull/1478)
- OpenAPI: Openapi custom context root 2.x [1521](https://github.com/oracle/helidon/pull/1521)
- OpenAPI: Remove dependency on Jackson via SmallRye -2.x [1458](https://github.com/oracle/helidon/pull/1458)
- OpenAPI: Support multiple apps in OpenAPI document [1493](https://github.com/oracle/helidon/pull/1493)
- OpenAPI: Use CONFIG rather than FINE logging for jandex indexes used - 2.x [1405](https://github.com/oracle/helidon/pull/1405)
- OpenAPI: Use smallrye openapi 1.2.0 (in 2.x) [1422](https://github.com/oracle/helidon/pull/1422)
- Reactive: Add Helidon-Reactive Scrabble benchmark [1482](https://github.com/oracle/helidon/pull/1482)
- Reactive: Add JMH Benchmark baseline to reactive [1462](https://github.com/oracle/helidon/pull/1462)
- Reactive: Add Multi.range and Multi.rangeLong + TCK tests [1475](https://github.com/oracle/helidon/pull/1475)
- Reactive: Expand TestSubscriber's API, fix a bug in MultiFirstProcessor [1463](https://github.com/oracle/helidon/pull/1463)
- Reactive: Fix expected exception [1472](https://github.com/oracle/helidon/pull/1472)
- Reactive: Fix reactive mapper publisher tck test [1447](https://github.com/oracle/helidon/pull/1447)
- Reactive: Implement Multi.interval() + TCK tests [1526](https://github.com/oracle/helidon/pull/1526)
- Reactive: Implement Single.flatMapIterable + TCK tests [1517](https://github.com/oracle/helidon/pull/1517)
- Reactive: Implement defaultIfEmpty() + TCK tests [1520](https://github.com/oracle/helidon/pull/1520)
- Reactive: Implement defer() + TCK tests [1503](https://github.com/oracle/helidon/pull/1503)
- Reactive: Implement from(Stream) + TCK tests [1525](https://github.com/oracle/helidon/pull/1525)
- Reactive: Implement reduce() + TCK tests [1504](https://github.com/oracle/helidon/pull/1504)
- Reactive: Implement switchIfEmpty + TCK tests [1527](https://github.com/oracle/helidon/pull/1527)
- Reactive: Implement takeUntil + TCK tests [1502](https://github.com/oracle/helidon/pull/1502)
- Reactive: Implement timer() + TCK tests [1516](https://github.com/oracle/helidon/pull/1516)
- Reactive: Improve SingleJust + TCK [1410](https://github.com/oracle/helidon/pull/1410)
- Reactive: Move onXXX from Subscribable to Single + TCK [1477](https://github.com/oracle/helidon/pull/1477)
- Reactive: Reactive Streams impl [1282](https://github.com/oracle/helidon/pull/1282)
- Reactive: Reimplement ConcatPublisher + TCK tests [1452](https://github.com/oracle/helidon/pull/1452)
- Reactive: Reimplement Multi.dropWhile + TCK test [1464](https://github.com/oracle/helidon/pull/1464)
- Reactive: Reimplement Multi.first + TCK test [1466](https://github.com/oracle/helidon/pull/1466)
- Reactive: Reimplement Multi.flatMapIterable + TCK test [1467](https://github.com/oracle/helidon/pull/1467)
- Reactive: Reimplement Multi.just(T[]), add Multi.singleton(T) + TCK [1461](https://github.com/oracle/helidon/pull/1461)
- Reactive: Reimplement Single.flatMap(->Publisher) + TCK test [1465](https://github.com/oracle/helidon/pull/1465)
- Reactive: Reimplement Single.from + TCK tests [1481](https://github.com/oracle/helidon/pull/1481)
- Reactive: Reimplement Single.map + TCK test [1456](https://github.com/oracle/helidon/pull/1456)
- Reactive: Reimplement many operators + TCK tests [1442](https://github.com/oracle/helidon/pull/1442)
- Reactive: Reimplement onErrorResume[With] + TCK tests [1489](https://github.com/oracle/helidon/pull/1489)
- Reactive: Reimplement the Multi.map() operator + TCK test [1411](https://github.com/oracle/helidon/pull/1411)
- Reactive: Rewrite collect, add juc.Collector overload + TCK tests [1459](https://github.com/oracle/helidon/pull/1459)
- Security: public fields for IdcsMtRoleMapperProvider.MtCacheKey [1409](https://github.com/oracle/helidon/pull/1409)
- Security: Fail fast when policy validation fails because of setup/syntax. [1491](https://github.com/oracle/helidon/pull/1491)
- Security: PermitAll overridden by JWT [1359](https://github.com/oracle/helidon/pull/1359)
- WebClient: Webclient implementation (#1205) [1431](https://github.com/oracle/helidon/pull/1431)
- WebServer: Adds a default send(Throwable) method to ServerResponse.java as the first step in providing an easy facility for reporting exceptions during HTTP processing [1378](https://github.com/oracle/helidon/pull/1378)
- WebServer: SetCookie test for parse method [1529](https://github.com/oracle/helidon/pull/1529)
- WebSocket: Integration of WebSockets POC into Helidon 2.0 [1280](https://github.com/oracle/helidon/pull/1280)
- jlink: jlink-image support. [1398](https://github.com/oracle/helidon/pull/1398)
- jlink: Update standalone quickstarts to support jlink-image. [1424](https://github.com/oracle/helidon/pull/1424)

#### Thank You!

Thanks to community member David Karnok [akarnokd](https://github.com/akarnokd) for his
significant contributions to our reactive support.

### Backward incompatible changes

#### Resource class when loaded from Config
The configuration approach to `Resource` class was using prefixes which was not aligned with our approach to configuration.
All usages were refactored as follows:

1. The `Resource` class expects a config node `resource` that will be used to read it
2. The feature set remains unchanged - we support path, classpath, url, content as plain text, and content as base64
3. Classes using resources are changed as well, such as `KeyConfig` - see details below

##### OidcConfig
Configuration has been updated to use the new `Resource` approach:

1. `oidc-metadata.resource` is the new key for loading `oidc-metadata` from local resource
2. `sign-jwk.resource` is the new key for loading signing JWK resource

##### JwtProvider and JwtAuthProvider
Configuration has been updated to use the new `Resource` approach:

1. `jwk.resource` is the new key for loading JWK for verifying signatures
2. `jwt.resource` is also used for outbound as key for loading JWK for signing tokens

##### GrpcTlsDescriptor
Configuration has been updated to use the new `Resource` approach:

1. `tls-cert.resource` is the new key for certificate
2. `tls-key.resource` is the new key for private key
3. `tl-ca-cert` is the the new key for certificate 

##### KeyConfig

The configuration has been updated to have a nicer tree structure:

Example of a public key from keystore:
```yaml
ssl:
  keystore:
    cert.alias: "service_cert"
    resource.path: "src/test/resources/keystore/keystore.p12"
    type: "PKCS12"
    passphrase: "password"
```

Example of a private key from keystore:
```yaml
ssl:
  keystore:
    key:
      alias: "myPrivateKey"
      passphrase: "password"
    resource.path: "src/test/resources/keystore/keystore.p12"
    type: "PKCS12"
    passphrase: "password"
```

Example of a pem resource with private key and certificate chain:
```yaml
ssl:
  pem:
    key:
      passphrase: "password"
      resource.resource-path: "keystore/id_rsa.p8"
    cert-chain:
      resource.resource-path: "keystore/public_key_cert.pem"
```

## [2.0.0-M1] 

### Notes

This is the first milestone release of Helidon 2.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes
(see [section](#backward-incompatible-changes) below ). This milestone release
is provided for customers that want early access to Helidon 2.0. It should be
considered unstable and is not intended for production use. APIs and features
might not be fully tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use a 1.4 release.

Notable changes:

- New Helidon DB Client
- Preliminary GraalVM native-image support for Helidon MP (in addition
  to existing support for Helidon SE)
- Improved discovery and handling of JAX-RS applications
- Dropped Java 8 support and moved to Java 11 APIs

### Improvements

- Helidon DB Client [657](https://github.com/oracle/helidon/pull/657) [1329](https://github.com/oracle/helidon/pull/1329)
- Native image: Helidon MP support [1328](https://github.com/oracle/helidon/pull/1328) [1295](https://github.com/oracle/helidon/pull/1295) [1259](https://github.com/oracle/helidon/pull/1259)
- Config: Helidon Config now implements MicroProfile config, so you can cast between these two types
- Security: Basic auth and OIDC in MP native image [1330](https://github.com/oracle/helidon/pull/1330)
- Security: JWT and OIDC security providers now support groups claim. [1324](https://github.com/oracle/helidon/pull/1324)
- Support for Helidon Features [1240](https://github.com/oracle/helidon/pull/1240)
- Java 11: [1232](https://github.com/oracle/helidon/pull/1232) [1187](https://github.com/oracle/helidon/pull/1187) [1222](https://github.com/oracle/helidon/pull/1222)

### Fixes

- JAX-RS: Better recovery for invalid URLs and content types [1246](https://github.com/oracle/helidon/pull/1246)
- JAX-RS: Fix synthetic app creation + functional test. [1323](https://github.com/oracle/helidon/pull/1323)
- Config: replace reflection with service loader [1102](https://github.com/oracle/helidon/pull/1102)
- Config: now uses common media type module instead of FileTypeDetector. [1332](https://github.com/oracle/helidon/pull/1332)
- JPA: Permit non-XA DataSources to take part in JTA transactions [1316](https://github.com/oracle/helidon/pull/1316)
- JPA: Fixes an issue with multiple persistence units [1342](https://github.com/oracle/helidon/pull/1342)
- JPA: Address several rollback-related issues [1146](https://github.com/oracle/helidon/pull/1146)
- JPA: Relax requirements on container-managed persistence units which can now be resource-local [1243](https://github.com/oracle/helidon/pull/1243)
- OpenAPI: Support multiple jandex files on the classpath [1320](https://github.com/oracle/helidon/pull/1320)
- Security: Fixed issues with IDCS and OIDC provider. [1313](https://github.com/oracle/helidon/pull/1313)
- Security: Removed support for entity modification from security. [1263](https://github.com/oracle/helidon/pull/1263)
- Security: Allow usage of exceptions for security in Jersey. [1227](https://github.com/oracle/helidon/pull/1227)
- Security: Outbound with OIDC provider no longer causes an UnsupportedOperationE… [1226](https://github.com/oracle/helidon/pull/1226)
- gRPC: Minor changes [1299](https://github.com/oracle/helidon/pull/1299)
- Quickstart: Fix application jar class-path for SNAPSHOT versioned dependencies [1297](https://github.com/oracle/helidon/pull/1297)
- Use Helidon service loader. [1334](https://github.com/oracle/helidon/pull/1334)
- Fix optional files in bare archetypes. (#1250) [1321](https://github.com/oracle/helidon/pull/1321)
- Make Multi streams compatible with Reactive Streams [1260](https://github.com/oracle/helidon/pull/1260)
- Remove Valve [1279](https://github.com/oracle/helidon/pull/1279)
- IDE support: Missing requires of JAX-RS API [1271](https://github.com/oracle/helidon/pull/1271)
- Metrics: fix unable to find reusable metric with tags [1244](https://github.com/oracle/helidon/pull/1244)
- Support for lazy values and changed common modules to use it. [1228](https://github.com/oracle/helidon/pull/1228)
- Upgrade jacoco to version 0.8.5 to avoid jdk11 issue [1281](https://github.com/oracle/helidon/pull/1281)
- Upgrade tracing libraries [1230](https://github.com/oracle/helidon/pull/1230)
- Upgrade config libraries  [1159](https://github.com/oracle/helidon/pull/1159)
- Upgrade Netty to 4.1.45 [1309](https://github.com/oracle/helidon/pull/1309)
- Upgrade Google libraries for Google login provider. [1229](https://github.com/oracle/helidon/pull/1229)
- Upgrade H2, HikariCP, Jedis, OCI SDK versions [1198](https://github.com/oracle/helidon/pull/1198)
- Upgrade to FT 2.0.2 and Failsafe 2.2.3 [1204](https://github.com/oracle/helidon/pull/1204)


### Backward incompatible changes

In order to stay current with dependencies, and also refine our APIs we have 
introduced some backward incompatible changes in this release. Most of the changes
are mechanical in nature: changing package names, changing GAV coordinates, etc.
Here are the details:

#### Java Runtime

- Java 8 support has been dropped. Java 11 or newer is now required.

#### Common
- Removed `io.helidon.reactive.Flow`, please use `java.util.concurrent.Flow`
- Removed `io.helidon.common.CollectionsHelper`, please use factory methods of `Set`, `Map` and `List`
- Removed `io.helidon.common.OptionalHelper`, please use methods of `java.util.Optional`
- Removed `io.helidon.common.StackWalker`, please use `java.lang.StackWalker`
- Removed `io.helidon.common.InputStreamHelper`, please use `java.io.InputStream` methods
- Removed dependency on Project Reactor

#### Tracing
- We have upgraded to OpenTracing version 0.33.0 that is not backward compatible, the following breaking changes exist
    (these are OpenTracing changes, not Helidon changes):
    1. `TextMapExtractAdapter` and `TextMapInjectAdapter` are now `TextMapAdapter`
    2. module name changed from `opentracing.api` to `io.opentracing.api` (same for `noop` and `util`)
    3. `SpanBuilder` no longer has `startActive` method, you need to use `Tracer.activateSpan(Span)`
    4. `ScopeManager.activate(Span, boolean)` is replaced by `ScopeManager.activate(Span)` - second parameter is now always 
            `false`
    5. `Scope ScopeManager.active()` is removed - replaced by `Span Tracer.activeSpan()`
- If you use the `TracerBuilder` abstraction in Helidon and have no custom Spans, there is no change required    

#### Config

##### Helidon MP
When using MP Config through the API, there are no backward incompatible changes in Helidon.

##### Helidon SE Config Usage

The following changes are relevant when using Helidon Config:

1. File watching is now done through a `ChangeWatcher` - use of `PollingStrategies.watch()` needs to be refactored to
    `FileSystemWatcher.create()` and the method to configure it on config source builder has changed to 
    `changeWatcher(ChangeWatcher)`
2. Methods on `ConfigSources` now return specific builders (they use to return `AbstractParsableConfigSource.Builder` with
    a complex type declaration). If you store such a builder in a variable, either change it to the correct type, or use `var`
3. Some APIs were cleaned up to be aligned with the development guidelines of Helidon. When using Git config source, or etcd
    config source, the factory methods moved to the config source itself, and the builder now accepts all configuration
    options through methods
4. The API of config source builders has been cleaned, so now only methods that are relevant to a specific config source type
    can be invoked on such a builder. Previously you could configure a polling strategy on a source that did not support 
    polling
5. There is a small change in behavior of Helidon Config vs. MicroProfile config: 
    The MP TCK require that system properties are fully mutable (e.g. as soon as the property is changed, it
    must be used), so MP Config methods work in this manner (with a certain performance overhead).
    Helidon Config treats System properties as a mutable config source, with a (optional) time based polling strategy. So
    the change is reflected as well, though not immediately (this is only relevant if you use change notifications). 
6. `CompositeConfigSource` has been removed from `Config`. If you need to configure `MerginStrategy`, you can do it now on 
    `Config` `Builder`

##### Helidon SE Config Extensibility

1. Meta configuration has been refactored to be done through `ServiceLoader` services. If you created
a custom `ConfigSource`, `PollingStrategy` or `RetryPolicy`, please have a look at the new documentation.
2. To implement a custom config source, you need to choose appropriate (new) interface(s) to implement. This is the choice:
    From "how we obtain the source of data" point of view:
    * `ParsableSource` - for sources that provide bytes (used to be reader, now `InputStream`)
    * `NodeConfigSource` - for sources that provide a tree structure directly
    * `LazyConfigSource` - for sources that cannot read the full config tree in advance
    From mutability point of view (immutable config sources can ignore this):
    * `PollableSource` - a config source that is capable of identifying a change based on a data "stamp"
    * `WatchableSource` - a config source using a target that can be watched for changes without polling (such as `Path`)
    * `EventConfigSource` - a config source that can trigger change events on its own 
3. `AbstractConfigSource` and `AbstractConfigSourceBuilder` are now in package `io.helidon.config`
4. `ConfigContext` no longer contains method to obtain a `ConfigParser`, as this is no longer responsibility of 
    a config source
5.  Do not throw an exception when config source does not exist, just return
    an empty `Optional` from `load` method, or `false` from `exists()` method
6.  Overall change support is handled by the config module and is no longer the responsibility
    of the config source, just implement appropriate SPI methods if changes are supported,
    such as `PollableSource.isModified(Object stamp)`
 

#### Metrics
Helidon now supports only MicroProfile Metrics 2.x. Modules for Metrics 1.x were removed, and 
modules for 2.x were renamed from `metrics2` to `metrics`.

#### Security

- When OIDC provider is configured to use cookie (default configuration) to carry authentication information,
    the cookie `Same-Site` is now set to `Lax` (used to be `Strict`). This is to prevent infinite redirects, as 
    browsers would refuse to set the cookie on redirected requests (due to this setting).
    Only in case the frontend host and identity host match, we leave `Strict` as the default


#### Microprofile Bundles
We have removed all versioned MP bundles (i.e. `helidon-microprofile-x.x`, and introduced
unversioned core and full bundles:

- `io.helidon.microprofile.bundles:helidon-microprofile-core` - contains only MP Server
   and Config. Allows you to add only the specifications needed by your application.
- `io.helidon.microprofile.bundles:helidon-microprofile` - contains the latest full 
   MicroProfile version implemented by Helidon
   
You can find more information in this blog post:
[New Maven bundles](https://medium.com/helidon/microprofile-3-2-and-helidon-mp-1-4-new-maven-bundles-a9f2bdc1b5eb)

#### Helidon CDI and MicroProfile Server

- You cannot start CDI container yourself (this change is required so we can 
      support GraalVM `native-image`)
    - You can only run a single instance of Server in a JVM
    - If you use `SeContainerInitializer` you would get an exception
        - this can be worked around by configuration property `mp.initializer.allow=true`, and warning can be removed
            using `mp.initializer.no-warn=true`
        - once `SeContainerInitializer` is used, you can no longer use MP with `native-image` 
- You can no longer provide a `Context` instance, root context is now built-in
- `MpService` and `MpServiceContext` have been removed
    - methods from context have been moved to `JaxRsCdiExtension` and `ServerCdiExtension` that can be accessed
        from CDI extension through `BeanManager.getExtension`.
    - methods `register` can be used on current `io.helidon.context.Context`
    - `MpService` equivalent is a CDI extension. All Helidon services were refactored to CDI extension 
        (you can use these for reference)
- `Server.cdiContainer` is removed, use `CDI.current()` instead

#### Startup
New recommended option to start Helidon MP:
1. Use class `io.helidon.microprofile.cdi.Main`
2. Use meta configuration option when advanced configuration of config is required (e.g. `meta-config.yaml`)
3. Put `logging.properties` on the classpath or in the current directory to be automatically picked up to configure 
    Java util logging
    
`io.helidon.microprofile.server.Main` is still available, just calls `io.helidon.microprofile.cdi.Main` and is deprecated.
`io.helidon.microprofile.server.Server` is still available, though the features are much reduced


#### MicroProfile JWT-Auth
If a JAX-RS application exists that is annotated with `@LoginConfig` with value `MP-JWT`, the correct authentication
provider is added to security.
The startup would fail if the provider is required yet not configured.

#### Security in MP
If there is no authentication provider configured, authentication will always fail.
If there is no authorization provider configured, ABAC provider will be configured.
(original behavior - these were configured if there was no provider configured overall) 

### Other Behavior changes

- JAX-RS applications now work similar to how they work in application servers
    - if there is an `Application` subclass that returns anything from
      `getClasses` or `getSingletons`, it is used as is
    - if there is an `Application` subclass that returns empty sets from these methods,
      all available resource classes will be part of such an application
    - if there is no `Application` subclass, a synthetic application will be
      created with all available resource classes
    - `Application` subclasses MUST be annotated with `@ApplicationScoped`,
      otherwise they are ignored


[2.3.3]: https://github.com/oracle/helidon/compare/2.3.2...2.3.3
[2.3.2]: https://github.com/oracle/helidon/compare/2.3.1...2.3.2
[2.3.1]: https://github.com/oracle/helidon/compare/2.3.0...2.3.1
[2.3.0]: https://github.com/oracle/helidon/compare/2.2.2...2.3.0
[2.2.2]: https://github.com/oracle/helidon/compare/2.2.1...2.2.2
[2.2.1]: https://github.com/oracle/helidon/compare/2.2.0...2.2.1
[2.2.0]: https://github.com/oracle/helidon/compare/2.1.0...2.2.0
[2.1.0]: https://github.com/oracle/helidon/compare/2.0.2...2.1.0
[2.0.2]: https://github.com/oracle/helidon/compare/2.0.1...2.0.2
[2.0.1]: https://github.com/oracle/helidon/compare/2.0.0...2.0.1
[2.0.0]: https://github.com/oracle/helidon/compare/2.0.0-RC2...2.0.0
[2.0.0-RC2]: https://github.com/oracle/helidon/compare/2.0.0-RC1...2.0.0-RC2
[2.0.0-RC1]: https://github.com/oracle/helidon/compare/2.0.0-M3...2.0.0-RC1
[2.0.0-M3]: https://github.com/oracle/helidon/compare/2.0.0-M2...2.0.0-M3
[2.0.0-M2]: https://github.com/oracle/helidon/compare/2.0.0-M1...2.0.0-M2
[2.0.0-M1]: https://github.com/oracle/helidon/compare/1.4.0...2.0.0-M1
