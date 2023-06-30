
# Changelog

All notable changes to this project will be documented in this file.

This project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

For Helidon 2.x releases please see [Helidon 2.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-2.x/CHANGELOG.md)

For Helidon 1.x releases please see [Helidon 1.x CHANGELOG.md](https://github.com/oracle/helidon/blob/helidon-1.x/CHANGELOG.md)

## [3.2.2]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.  Helidon 3 requires Java 17 or newer.

- Config: Filter complex environment properties configuration [6994](https://github.com/helidon-io/helidon/pull/6994)
- DB: Update serial config for ojdbc driver [7116](https://github.com/helidon-io/helidon/pull/7116)
- JWT: Security propagation is now disabled with not configured [6806](https://github.com/helidon-io/helidon/pull/6806)
- LRA: Fix LRA Logging [6733](https://github.com/helidon-io/helidon/pull/6733)
- LRA: LRA coordinator docker fix [6725](https://github.com/helidon-io/helidon/pull/6725)
- Messaging: 6847 WLS JMS connector doesn't support named factory bean  [6922](https://github.com/helidon-io/helidon/pull/6922)
- Metrics: Improved performance of metric lookups in MetricProducer [6842](https://github.com/helidon-io/helidon/pull/6842)
- Metrics: Remove metrics app registry clear-out code from metrics extension; add doc [6956](https://github.com/helidon-io/helidon/pull/6956)
- MicroProfile: Support for injection of ServerRequest and ServerResponse also via CDI [6798](https://github.com/helidon-io/helidon/pull/6798)
- MultiPart: Avoid calling MimeParser.offer with empty buffers [6898](https://github.com/helidon-io/helidon/pull/6898)
- MultiPart: MultiPart Builder improvements  [6900](https://github.com/helidon-io/helidon/pull/6900)
- Tracing: Add support for multiple baggage items [7022](https://github.com/helidon-io/helidon/pull/7022)
- Tracing: Fix OpenTracingSpan Baggage propagation issue [6987](https://github.com/helidon-io/helidon/pull/6987)
- Tracing: Make Jaeger Tracer OpenTelemetry Agent aware. [6537](https://github.com/helidon-io/helidon/pull/6537)
- Tracing: Make Zipkin baggage aware [7004](https://github.com/helidon-io/helidon/pull/7004)
- Tracing: Move tracer tags to process [7027](https://github.com/helidon-io/helidon/pull/7027)
- WebServer: Avoid reflecting back user data coming from exception messages [6988](https://github.com/helidon-io/helidon/pull/6988)
- WebServer: Fix websocket close event propagation on unclean disconnect [7013](https://github.com/helidon-io/helidon/pull/7013)
- WebServer: Make ByteBufferDataChunk.isReleased and ByteBufDataChunk.isReleased thread-safe [6899](https://github.com/helidon-io/helidon/pull/6899)
- WebServer: Release Netty ByteBuf after it is consumed by Tyrus [7042](https://github.com/helidon-io/helidon/pull/7042)
- WebServer: Reset channel auto-read config before upgrading websocket connection [7050](https://github.com/helidon-io/helidon/pull/7050)
- Build: Update validate workflow to use Oracle jdk [6934](https://github.com/helidon-io/helidon/pull/6934)
- Build: release workflow [7098](https://github.com/helidon-io/helidon/pull/7098) [7104](https://github.com/helidon-io/helidon/pull/7104)
- Dependencies: Integrate build tools 3.0.5 [6903](https://github.com/helidon-io/helidon/pull/6903)
- Dependencies: Upgrade Jackson to 2.15.2 [7124](https://github.com/helidon-io/helidon/pull/7124)
- Dependencies: Upgrade graphql to 18.6 [6974](https://github.com/helidon-io/helidon/pull/6974)
- Dependencies: Upgrade grpc, guava, netty, snappy-java, use slim neo4j driver [7057](https://github.com/helidon-io/helidon/pull/7057)
- Dependencies: Upgrade maven-dependency-plugin to 3.6.0 [6912](https://github.com/helidon-io/helidon/pull/6912)
- Dependencies: upgrade Weld [6794](https://github.com/helidon-io/helidon/pull/6794)
- Docs: Documentation enhancements for WebClient [6736](https://github.com/helidon-io/helidon/pull/6736)
- Docs: Draft of the integration doc for 3.x [6864](https://github.com/helidon-io/helidon/pull/6864)
- Docs: Fix table formatting [6821](https://github.com/helidon-io/helidon/pull/6821)
- Examples: Fix params of @ExampleObject annotations in examples (#6040) [6782](https://github.com/helidon-io/helidon/pull/6782)
- Test: 6524 Intermittent watermark test fix [6835](https://github.com/helidon-io/helidon/pull/6835)
- Test: Add @target(ElementType.METHOD) for annotation @mptest [6490](https://github.com/helidon-io/helidon/pull/6490)
- Test: CipherSuiteTest intermittent failure [6949](https://github.com/helidon-io/helidon/pull/6949)

## [3.2.1]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.  Helidon 3 requires Java 17 or newer.

This release upgrades Kafka Clients from 2.8.1 to 3.4.0. If you encounter issues running against older Kafka servers or the OCI Streaming Service you might need to use the work-around docomented in [6718](https://github.com/helidon-io/helidon/issues/6718)

- FT: Switch metrics API jar scope to compile from provided [6666](https://github.com/helidon-io/helidon/pull/6666)
- JPA: Adds an enabled flag to JpaExtension to permit subsequent refactoring and replacement [6512](https://github.com/helidon-io/helidon/pull/6512)
- JPA: Adds more classes as part of overall JPA refactoring effort [6584](https://github.com/helidon-io/helidon/pull/6584)
- JPA: Improving JPA pom.xml as part of overall JPA refactoring [6508](https://github.com/helidon-io/helidon/pull/6508)
- MicroProfile: RestClient and FT intergration changes [6665](https://github.com/helidon-io/helidon/pull/6665)
- Native image: native-image configuration for Jackson in Helidon MP. [6607](https://github.com/helidon-io/helidon/pull/6607)
- Security: Unauthenticated status code fix  [6482](https://github.com/helidon-io/helidon/pull/6482)
- Tracing: Add baggage to Helidon Span [6692](https://github.com/helidon-io/helidon/pull/6692)
- Tracing: Support for different propagators for Jaeger OpenTelemetry [6611](https://github.com/helidon-io/helidon/pull/6611)
- WebClient: Add option to disable DNS Resolver for WebClient [6492](https://github.com/helidon-io/helidon/pull/6492)
- WebClient: Proxy now properly selects proxy settings from system properties [6526](https://github.com/helidon-io/helidon/pull/6526)
- WebServer: Fixed problem in AUTO_FLUSH backpressure strategy  [6556](https://github.com/helidon-io/helidon/pull/6556)
- WebServer: Response should not be chunked if there is no entity [6637](https://github.com/helidon-io/helidon/pull/6637)
- WebServer: Use checkNested(Throwable) for req.next(Throwable) [6699](https://github.com/helidon-io/helidon/pull/6699)
- Build: Use GitHub Action for helidon-3.x branch [6500](https://github.com/helidon-io/helidon/pull/6500)
- Builds: update helidon-version-is-release in doc files when updating release to fix links [6689](https://github.com/helidon-io/helidon/pull/6689)
- Dependencies: Kafka bump up 2.8.1 > 3.4.0 [6706](https://github.com/helidon-io/helidon/pull/6706)
- Dependencies: Update grpc-java version to 1.54.1 and clean up grpc tests [6685](https://github.com/helidon-io/helidon/pull/6685)
- Dependencies: Upgrade graphql to 17.5 [6533](https://github.com/helidon-io/helidon/pull/6533)
- Dependencies: Upgrade hibernate to 6.1.7.Final and eclipselink asm to 9.4.0 [6514](https://github.com/helidon-io/helidon/pull/6514)
- Dependencies: upgrade netty to 4.1.90.Final [6511](https://github.com/helidon-io/helidon/pull/6511)
- Docs: Fix Openapi links issue 6605 [6678](https://github.com/helidon-io/helidon/pull/6678)
- Docs: Update documentation of composite provider flag. (#6597) [6635](https://github.com/helidon-io/helidon/pull/6635)
- Docs: Updated doc to reflect current support for FT thread pool properties [6621](https://github.com/helidon-io/helidon/pull/6621)
- Examples: Add await timeout and replace thenAccept to forSingle  [6558](https://github.com/helidon-io/helidon/pull/6558)
- Examples: Create file validations [6609](https://github.com/helidon-io/helidon/pull/6609)
- Examples: Fix Helidon Archetype generates broken projects  [6721](https://github.com/helidon-io/helidon/pull/6721)
- Examples: archetypes generating poorly formatted code [6623](https://github.com/helidon-io/helidon/pull/6623)
- Examples: Update streaming example to use IoMulti [6604](https://github.com/helidon-io/helidon/pull/6604)
- Test: Use Hamcrest assertions instead of JUnit [6449](https://github.com/helidon-io/helidon/pull/6449) [6638](https://github.com/helidon-io/helidon/pull/6638)

## [3.2.0]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.  Helidon 3 requires Java 17 or newer.

This release upgrades SnakeYaml from 1.32 to 2.0. Because of incompatible API changes in SnakeYaml 2 it is possible your application might be impacted if you use SnakeYaml directly. While we reccomend you do the upgrade, if that is not possible you may force downgrade SnakeYaml to 1.32 and Helidon 3.2.0 will still work.

### CHANGES

- Config: Escape the key when copying a config node [6304](https://github.com/helidon-io/helidon/pull/6304)
- JMS: JNDI destination support [6301](https://github.com/helidon-io/helidon/pull/6301)
- JPA: Minor JPA cleanups [6435](https://github.com/helidon-io/helidon/pull/6435)
- JTA: Fixes erroneous closing behavior in JtaConnection.java [6321](https://github.com/helidon-io/helidon/pull/6321)
- Logging: Remove FileHandler from logging.properties [6363](https://github.com/helidon-io/helidon/pull/6363)
- Metrics: Change default exemplar behavior to conform to OpenMetrics spec; allow users to choose former non-standard behavior [6387](https://github.com/helidon-io/helidon/pull/6387)
- MultiPart: Fix MultiPartDecoder lazy inner publisher subscription [6225](https://github.com/helidon-io/helidon/pull/6225)
- MultiPart: WritableMultiPart create methods fixed [6390](https://github.com/helidon-io/helidon/pull/6390)
- Native image: Dockerfile.native fixes. [6424](https://github.com/helidon-io/helidon/pull/6424)
- Native image: Fix native-image build-time initialization [6426](https://github.com/helidon-io/helidon/pull/6426) [6438](https://github.com/helidon-io/helidon/pull/6438)
- Security: OIDC original uri resolving leaving out query params [6342](https://github.com/helidon-io/helidon/pull/6342)
- WebServer: Support for non-GET HTTP/2 upgrades [6383](https://github.com/helidon-io/helidon/pull/6383)
- Build: Use https in pom.xml schemaLocation [6313](https://github.com/helidon-io/helidon/pull/6313) and others
- Dependencies: Adapt to SnakeYAML 2.0 changes [5793](https://github.com/helidon-io/helidon/pull/5793)
- Dependencies: Upgrade OCI SDK to 3.8.0 [6427](https://github.com/helidon-io/helidon/pull/6427)
- Docs: Fix `{h1-prefix}` unreplaced token in SE metrics guide preamble [6409](https://github.com/helidon-io/helidon/pull/6409)
- Docs: Remove claim that metrics are automatically propagated from the webserver to the webclient [6319](https://github.com/helidon-io/helidon/pull/6319)
- Docs: TOC - #5828 [6270](https://github.com/helidon-io/helidon/pull/6270)
- Docs: Toc tasks from #5828 [6146](https://github.com/helidon-io/helidon/pull/6146)
- Docs: Typo in metrics guide 3.x [6271](https://github.com/helidon-io/helidon/pull/6271)
- Docs: [3.x] Describe disabling config token replacement [6166](https://github.com/helidon-io/helidon/pull/6166)
- Examples: Update mustache format in archetype files [6287](https://github.com/helidon-io/helidon/pull/6287)
- Tests: Fix RC in JMS error test [6376](https://github.com/helidon-io/helidon/pull/6376)
- Tests: Fix intermittent issue on OciMetricsSupportTest [6177](https://github.com/helidon-io/helidon/pull/6177)
- Tests: JMS intermittent test fix [6393](https://github.com/helidon-io/helidon/pull/6393)
- Tests: Use Hamcrest assertions instead of JUnit [6292](https://github.com/helidon-io/helidon/pull/6292) and others

## [3.1.2]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.  Helidon 3 requires Java 17 or newer.

### CHANGES

- Config: Configuration fixes [6150](https://github.com/helidon-io/helidon/pull/6150)
- OpenAPI: Fix OpenAPI UI options processing [6110](https://github.com/helidon-io/helidon/pull/6110)
- Security: OIDC logout functionality fixed [6118](https://github.com/helidon-io/helidon/pull/6118)
- Security: OIDC tenant SPI default priority changed [6127](https://github.com/helidon-io/helidon/pull/6127)
- Tracing: Fix parent handling in OpenTelemetry (#6092) [6128](https://github.com/helidon-io/helidon/pull/6128)
- WebServer: BodyPart deprecate name() and filename() ; add isNamed [6097](https://github.com/helidon-io/helidon/pull/6097)
- Dependencies: Update build-tools to 3.0.4 [6139](https://github.com/helidon-io/helidon/pull/6139)
- Docs: changes for tracing and tracing guide [6113](https://github.com/helidon-io/helidon/pull/6113)

## [3.1.1]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.  Helidon 3 requires Java 17 or newer.

### CHANGES

- Config: Add built-in support for converting config strings to enum values [5732](https://github.com/helidon-io/helidon/pull/5732)
- JPA: Adds connection unwrapping abilities to CDISEPlatform.java [5790](https://github.com/helidon-io/helidon/pull/5790)
- JTA: Introduces JtaConnection.java [5905](https://github.com/helidon-io/helidon/pull/5905)
- JTA: Introduces LocalXAResource and a few support classes in jta/jdbc [5733](https://github.com/helidon-io/helidon/pull/5733)
- Messaging: 6035 AQ connector @ConnectorAttribute [6036](https://github.com/helidon-io/helidon/pull/6036)
- Messaging: WLS JMS Object-Based Security [5854](https://github.com/helidon-io/helidon/pull/5854)
- Metrics: Fix improper handling of metrics global tags [5812](https://github.com/helidon-io/helidon/pull/5812)
- MicroProfile: Fix order of initialization of tracing and security. [5987](https://github.com/helidon-io/helidon/pull/5987)
- OCI: Add Helidon Metrics integration with OCI [5829](https://github.com/helidon-io/helidon/pull/5829)
- OCI: Add OCI MP Archetype [5939](https://github.com/helidon-io/helidon/pull/5939)
- OCI: Register OciMetricsSupport service only when enable flag is set to true [6032](https://github.com/helidon-io/helidon/pull/6032)
- Security: Accidentally removed updateRequest method returned [5844](https://github.com/helidon-io/helidon/pull/5844)
- Security: Default tenant is not included for propagation [5898](https://github.com/helidon-io/helidon/pull/5898)
- Security: Oidc tenant name now properly escaped [5872](https://github.com/helidon-io/helidon/pull/5872)
- Security: Support for customization of 'logout uri' in OIDC provider [5784](https://github.com/helidon-io/helidon/pull/5784)
- WebServer: 100 continue triggered by content request [5714](https://github.com/helidon-io/helidon/pull/5714)
- WebServer: Add allow-list handling to requested URI behavior [5668](https://github.com/helidon-io/helidon/pull/5668)
- WebServer: Suppress incorrect start-up log message related to requested URI discovery [5862](https://github.com/helidon-io/helidon/pull/5862)
- WebServer: Switch default back-pressure strategy to AUTO_FLUSH from LINEAR #5943 [5944](https://github.com/helidon-io/helidon/pull/5944)
- WebSocket: Enhancement to allow different WebSocket applications to be registered on different ports. [5822](https://github.com/helidon-io/helidon/pull/5822)
- Build: Cleanup Helidon BOM by removing obsolete and internal artifacts [6017](https://github.com/helidon-io/helidon/pull/6017)
- Dependencies: Bump testng from 7.5 to 7.7.0 [5918](https://github.com/helidon-io/helidon/pull/5918)
- Dependencies: Neo4j Driver update [5752](https://github.com/helidon-io/helidon/pull/5752)
- Dependencies: Upgrade jersey to 3.0.9 [5787](https://github.com/helidon-io/helidon/pull/5787)
- Dependencies: Upgrade OCI SDK to 3.2.1 [5954](https://github.com/helidon-io/helidon/pull/5954)
- Docs: DOC add Histogram to SE Metrics [6059](https://github.com/helidon-io/helidon/pull/6059)
- Docs: Doc fixes for Issue 4673 [5614](https://github.com/helidon-io/helidon/pull/5614)
- Docs: Documentation updates to correct wrong instructions for HOCON config parsing [5972](https://github.com/helidon-io/helidon/pull/5972)
- Docs: Fix for #5771 - updates to SE WebServer toc [5772](https://github.com/helidon-io/helidon/pull/5772)
- Docs: Fix incorrectly reverted icons [5761](https://github.com/helidon-io/helidon/pull/5761)
- Docs: New subsection describing enhancement to support WebSocket application bindings on different ports [5835](https://github.com/helidon-io/helidon/pull/5835)
- Docs: TOC updates to include additional levels  [6003](https://github.com/helidon-io/helidon/pull/6003)
- Docs: Updated sitegen.yaml for #5076 [5952](https://github.com/helidon-io/helidon/pull/5952)
- Docs: Updates to MP TOCs [5923](https://github.com/helidon-io/helidon/pull/5923)
- Docs: WLS connector doc typo [5803](https://github.com/helidon-io/helidon/pull/5803)
- Docs: fix tracing docs with incorrect webclient artifact ids [6029](https://github.com/helidon-io/helidon/pull/6029)
- Examples: Archetype generates wrong Jaeger configuration (SE) [5920](https://github.com/helidon-io/helidon/pull/5920)
- Examples: Fix parent poms in example [5736](https://github.com/helidon-io/helidon/pull/5736)
- Test: Follow-up to PR #5822 [5845](https://github.com/helidon-io/helidon/pull/5845)
- Test: Use Hamcrest assertions instead of JUnit [5962](https://github.com/helidon-io/helidon/pull/5962) and Others


## [3.1.0]

This is a minor release of Helidon and is recommended for all users of Helidon 3. In addition to bug fixes and minor enhancements, this release contains two dependency upgrades that could have a small impact on compatibility. These are:

* [OCI SDK 3.0](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk3.htm). Note that if you are using Helidon MP OCI SDK integration then you will not need to specify an HTTP Client Library as the Helidon support will do that for you.
* [GraalVM Native Image 22.3.0](https://www.graalvm.org/release-notes/22_3/).

### CHANGES

- Common: Add info to CharBuf exceptions [5375](https://github.com/helidon-io/helidon/pull/5375)
- Common: Fix inconsistent status name [5642](https://github.com/helidon-io/helidon/pull/5642)
- Common: WebServer.Builder media support methods with Supplier variants [5632](https://github.com/helidon-io/helidon/pull/5632)
- Config: Provide MP config profile support for application.yaml (#5565) [5585](https://github.com/helidon-io/helidon/pull/5585)
- DBClient: Handle exception on inTransaction apply [5699](https://github.com/helidon-io/helidon/pull/5699)
- Docker: remove -Ddocker.build=true [5484](https://github.com/helidon-io/helidon/pull/5484)
- Fault Tolerance: Additional @Retry strategies [5165](https://github.com/helidon-io/helidon/pull/5165)
- Grpc: Grpc component Does not handle package directive in proto files. [5283](https://github.com/helidon-io/helidon/pull/5283)
- JPA: Improves integrations/jdbc/jdbc to better support future JPA improvements [5654](https://github.com/helidon-io/helidon/pull/5654)
- LRA: LRA false warning  [5555](https://github.com/helidon-io/helidon/pull/5555)
- Messaging: JMS connector update [5327](https://github.com/helidon-io/helidon/pull/5327)
- Messaging: Kafka producer nacking fix 5510 [5524](https://github.com/helidon-io/helidon/pull/5524)
- Messaging: Message body operator matching with parameters [5574](https://github.com/helidon-io/helidon/pull/5574)
- Metrics: Fix incorrect tags comparison when trying to match metric IDs [5544](https://github.com/helidon-io/helidon/pull/5544)
- MicroProfile: Add null check to MP Server.Builder.config() (#5363) [5372](https://github.com/helidon-io/helidon/pull/5372)
- Native Image: Adding feature file for MP native image. [5652](https://github.com/helidon-io/helidon/pull/5652)
- OCI: Replace OCI Java SDK shaded jar with v3 for OCI integration [5704](https://github.com/helidon-io/helidon/pull/5704)
- OpenAPI: Add OpenAPI U/I integration [3.x] [5568](https://github.com/helidon-io/helidon/pull/5568)
- OpenAPI: Fix error in OpenAPI handling of `default` values' types [5289](https://github.com/helidon-io/helidon/pull/5289)
- Reactive: Multi.forEachCS [5527](https://github.com/helidon-io/helidon/pull/5527)
- Security: Jwt now support multiple issuers and multi issuer validation [5648](https://github.com/helidon-io/helidon/pull/5648)
- Security: Jwt scope handling extended over array support [5521](https://github.com/helidon-io/helidon/pull/5521)
- Security: OIDC multi-tenant and lazy loading implementation [5619](https://github.com/helidon-io/helidon/pull/5619)
- Security: Use only public APIs to read PKCS#1 keys (#5240) [5258](https://github.com/helidon-io/helidon/pull/5258)
- Tracing: Client tracing interceptor no longer clears exception [5601](https://github.com/helidon-io/helidon/pull/5601)
- WebClient: Add relativeUris flag in OidcConfig to allow Oidc webclient to use relative path on the request URI [5335](https://github.com/helidon-io/helidon/pull/5335)
- WebServer: Broad changes to handle case properly in headers and parameters [5221](https://github.com/helidon-io/helidon/pull/5221)
- WebServer: Log an entry in warning level for a 400 or 413 response [5295](https://github.com/helidon-io/helidon/pull/5295)
- WebServer: Log simple message for a 400 or a 413 and more under FINE [5355](https://github.com/helidon-io/helidon/pull/5355)
- WebServer: NullPointerException when there is an illegal character in the request [5471](https://github.com/helidon-io/helidon/pull/5471)
- Webserver: Support for requested URI for web server requests. [5330](https://github.com/helidon-io/helidon/pull/5330)
- Dependencies: Bump-up reactive messaging/ops to 3.0 [5525](https://github.com/helidon-io/helidon/pull/5525)
- Dependencies: Fix Guava version to match that required by the grpc-java libraries [5503](https://github.com/helidon-io/helidon/pull/5503)
- Dependencies: Upgrade GraalVM native image to 22.3.0 [5308](https://github.com/helidon-io/helidon/pull/5308)
- Dependencies: Upgrade Netty to 4.1.86.Final [5703](https://github.com/helidon-io/helidon/pull/5703)
- Dependencies: Upgrade PostgreSQL JDBC driver dependency to 42.4.3 [5560](https://github.com/helidon-io/helidon/pull/5560)
- Dependencies: Upgrade Helidon build-tools to 3.0.3 [5726](https://github.com/helidon-io/helidon/pull/5726)
- Dependencies: Upgrade grpc-java to 1.49.2 [5348](https://github.com/helidon-io/helidon/pull/5348)
- Dependencies: Upgrade to jackson-databind-2.13.4.2 via bom 2.13.4.20221013 [5302](https://github.com/helidon-io/helidon/pull/5302)
- Docs: Declare `h1-prefix` as early as possible so it is used for the title prefix [5667](https://github.com/helidon-io/helidon/pull/5667)
- Docs: Document config.require-encryption [5188](https://github.com/helidon-io/helidon/pull/5188)
- Docs: Fix inadvertent changes in attributes.adoc [5334](https://github.com/helidon-io/helidon/pull/5334)
- Docs: JAXRS doc updates for 3611 [5225](https://github.com/helidon-io/helidon/pull/5225)
- Docs: Openapi generator doc 3.x [5263](https://github.com/helidon-io/helidon/pull/5263)
- Docs: flatMapCompletionStage javadoc fix [5622](https://github.com/helidon-io/helidon/pull/5622)
- Examples: Make JSON-B a default option for Helidon MP projects (backport) [5208](https://github.com/helidon-io/helidon/pull/5208)
- Examples: OpenAPI generator examples [5649](https://github.com/helidon-io/helidon/pull/5649)
- Examples: Remove license report from maven lifecycle [5244](https://github.com/helidon-io/helidon/pull/5244)
- Examples: Use property to skip execution of eclipselink weave [5313](https://github.com/helidon-io/helidon/pull/5313)
- Examples: database choices should be before packaging (backport) [5294](https://github.com/helidon-io/helidon/pull/5294)
- Tests: Use Hamcrest assertions instead of JUnit (#1749) [5189](https://github.com/helidon-io/helidon/pull/5189) and others
- Tests: Fix Intermittent TestJBatchEndpoint.runJob [5557](https://github.com/helidon-io/helidon/pull/5557)
- Tests: Fix intermittent jBatch test [5247](https://github.com/helidon-io/helidon/pull/5247)
- Tests: Simplify named socket WebTarget injection in Tests [5269](https://github.com/helidon-io/helidon/pull/5269)
- Tests: Use Hamcrest assertions instead of JUnit in tests/functional/jax-rs-multiple-apps (#1749) [5634](https://github.com/helidon-io/helidon/pull/5634)
- Tests: Various metrics test improvements to avoid intermittent failures [5621](https://github.com/helidon-io/helidon/pull/5621)

## [3.0.2]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3.

### CHANGES

- CORS: Fix CORS annotation handling error in certain cases [5104](https://github.com/helidon-io/helidon/pull/5104)
- Config: @ConfigProperties without bean defining annotation - 3x [4853](https://github.com/helidon-io/helidon/pull/4853)
- DBClient: Issue #4719 - Helidon DBClient does not trigger an Exception when no sane DB connection can be obtained [4771](https://github.com/helidon-io/helidon/pull/4771)
- Health: Use lazy values to initialized HealthSupport FT handlers [5146](https://github.com/helidon-io/helidon/pull/5146)
- JAX-RS: Make Application subclasses available via our context during Feature executions (3.x) [4786](https://github.com/helidon-io/helidon/pull/4786)
- Jersey: Register a low-priority exception mapper to log internal errors  [5073](https://github.com/helidon-io/helidon/pull/5073)
- Messaging: AQ Connector - JEP 290 [4672](https://github.com/helidon-io/helidon/pull/4672)
- Metrics: Fix OpenMetrics formatting error (3.x) [4900](https://github.com/helidon-io/helidon/pull/4900)
- MicroProfile: Fix identification of parallel startup of CDI [4993](https://github.com/helidon-io/helidon/pull/4993)
- MicroProfile: MP path based static content should use index.html (3.x) [4736](https://github.com/helidon-io/helidon/pull/4736)
- Native-image: add serial config required for oracle driver (3.x) [4960](https://github.com/helidon-io/helidon/pull/4960)
- Reactive: MultiFromBlockingInputStream RC fix 3x [3597](https://github.com/helidon-io/helidon/pull/3597)
- RestClient proxy test exclusion removed [4769](https://github.com/helidon-io/helidon/pull/4769)
- Security: Access token refresh - 3.x [4821](https://github.com/helidon-io/helidon/pull/4821)
- Security: JWT-Auth implementation encrypted token recognition fixed [4811](https://github.com/helidon-io/helidon/pull/4811)
- WebClient: DNS resolver should not be possible to set per request [4814](https://github.com/helidon-io/helidon/pull/4814)
- WebClient: Dns resolver type method on webclient builder [4838](https://github.com/helidon-io/helidon/pull/4838)
- WebClient: Round Robin added as DNS resolver option [4798](https://github.com/helidon-io/helidon/pull/4798)
- WebServer: Default header size increased to 16K - Helidon 3.x [5016](https://github.com/helidon-io/helidon/pull/5016)
- WebServer: Watermarked response backpressure 3x [4724](https://github.com/helidon-io/helidon/pull/4724)
- Dependencies: Update graphql-java to 17.4 [4966](https://github.com/helidon-io/helidon/pull/4966)
- Dependencies: Upgrade Hibernate to 6.1.4.Final, EclipseLink to 3.0.3 [5100](https://github.com/helidon-io/helidon/pull/5100)
- Dependencies: Upgrade Postgre driver [4742](https://github.com/helidon-io/helidon/pull/4742)
- Dependencies: Upgrade protobuf-java [5133](https://github.com/helidon-io/helidon/pull/5133)
- Dependencies: Upgrade reactive-streams to 1.0.4 [5045](https://github.com/helidon-io/helidon/pull/5045)
- Dependencies: Upgrade snakeyaml to 1.32 [4922](https://github.com/helidon-io/helidon/pull/4922)
- Dependencies: Upgrades OCI to 2.45.0 on helidon-3.x [4827](https://github.com/helidon-io/helidon/pull/4827)
- Docs: Fix for issue 4793 in tracing doc [4795](https://github.com/helidon-io/helidon/pull/4795)
- Docs: editorial updates to DB Client guide [4499](https://github.com/helidon-io/helidon/pull/4499)
- Docs: 4184 logging for an MP app  [4903](https://github.com/helidon-io/helidon/pull/4903)
- Docs: Documentation minor fix [4902](https://github.com/helidon-io/helidon/pull/4902)
- Docs: Fix broken links for Oracle Universal Connection Pool (#4781) [4862](https://github.com/helidon-io/helidon/pull/4862)
- Docs: Fix broken links for ServerThreadPoolSupplier and ScheduledThreadPoolSupplier in mp/fault-tolerance.adoc (#4899) [4909](https://github.com/helidon-io/helidon/pull/4909)
- Docs: Fix invalid example in se/config/advanced-configuration.adoc (#4775) [4920](https://github.com/helidon-io/helidon/pull/4920)
- Docs: Fix misplaced attribute settings [4954](https://github.com/helidon-io/helidon/pull/4954)
- Docs: Fix the structure of the table in the section "Traced spans" (#4792) [4859](https://github.com/helidon-io/helidon/pull/4859)
- Docs: Replace deprecated ServerConfiguration.builder() on WebServer.builder() in docs - backport 3.x (#5025) [5117](https://github.com/helidon-io/helidon/pull/5117)
- Docs: Update a few icons in the docs [4937](https://github.com/helidon-io/helidon/pull/4937)
- Docs: Updated supported version of MicroProfile to 5.0 [4917](https://github.com/helidon-io/helidon/pull/4917)
- Docs: update old K8s deployment yaml [4760](https://github.com/helidon-io/helidon/pull/4760)
- Docs: updates for PR 4520 language review [4731](https://github.com/helidon-io/helidon/pull/4731)
- Examples: 4857 arch fix minor issues  (backport) [4874](https://github.com/helidon-io/helidon/pull/4874)
- Examples: Formatting of generated Helidon SE quickstart (backport) [4969](https://github.com/helidon-io/helidon/pull/4969)
- Examples: Quickstart MP with Jackson fix JSON message [4905](https://github.com/helidon-io/helidon/pull/4905)
- Examples: Remove module-info files from examples (3.x) [4894](https://github.com/helidon-io/helidon/pull/4894)
- Examples: Tracing config updates in archetype [5137](https://github.com/helidon-io/helidon/pull/5137)
- Examples: WebClient dependency in generated Helidon SE Quickstart should be in test scope (backport) [5020](https://github.com/helidon-io/helidon/pull/5020)
- Examples: change beans.xml [4845](https://github.com/helidon-io/helidon/pull/4845)
- Examples: k8s and v8o support in archetype (3.x) [4887](https://github.com/helidon-io/helidon/pull/4887)
- Examples: Do not generate CDS archive when using Dockerfile.jlink [5158](https://github.com/helidon-io/helidon/pull/5158)
- Test: 4980 EchoServiceTest timeout [5005](https://github.com/helidon-io/helidon/pull/5005)
- Test: 5068 mock connector beans xml 3x [5069](https://github.com/helidon-io/helidon/pull/5069)
- Test: Add robustness to some of the timing-susceptible metrics tests; add util matcher with retry [5047](https://github.com/helidon-io/helidon/pull/5047)
- Test: Add some retries because post-request metrics updates occur after the response is sent [5136](https://github.com/helidon-io/helidon/pull/5136)
- Test: Do not use retry to fix the test; stats are approximate; just check for existence, not values [5112](https://github.com/helidon-io/helidon/pull/5112)
- Test: Fix FT intermittent failure - archetype build (backport) [4934](https://github.com/helidon-io/helidon/pull/4934)
- Test: Fix for failing GraphQL tests on Windows [4732](https://github.com/helidon-io/helidon/pull/4732)
- Test: HelidonTest doesn't start container properly with TestInstance.Lifecycle.PER_CLASS #4663 [4865](https://github.com/helidon-io/helidon/pull/4865)
- Test: Intermittent test fix, using random port for tests. [4800](https://github.com/helidon-io/helidon/pull/4800)
- Test: Issue 4740 - JPA integration tests should run on every build [5027](https://github.com/helidon-io/helidon/pull/5027)
- Test: Special Windows build Config TCK profile no longer needed [4816](https://github.com/helidon-io/helidon/pull/4816)
- Test: Use Hamcrest assertions instead of JUnit in tests [5057](https://github.com/helidon-io/helidon/pull/5057) (and others)

## [3.0.1]

This is a bugfix release of Helidon and is recommended for all users of Helidon 3. 

### CHANGES

- Common: Change log level to fine for exception caught while intercepting [4640](https://github.com/oracle/helidon/pull/4640)
- Config: Map injection behavior restored [4653](https://github.com/oracle/helidon/pull/4653)
- Config: Obtaining parent dir for watcher service fixed [4665](https://github.com/oracle/helidon/pull/4665)
- Config: Unescape the keys when config is returned as a map [4715](https://github.com/oracle/helidon/pull/4715)
- Deps: Upgrade protobuf to support osx-aarch_64 architecture [4662](https://github.com/oracle/helidon/pull/4662)
- FT: Changes to FT implementation to support interception of proxy methods [4650](https://github.com/oracle/helidon/pull/4650)
- Messaging: JMS Shim wraps null message fix [4671](https://github.com/oracle/helidon/pull/4671)
- Metrics: Config metadata should be optional and provided [4728](https://github.com/oracle/helidon/pull/4728)
- OCI: Undo manual shaded jar tasks [4619](https://github.com/oracle/helidon/pull/4619)
- Security: Configuration parameter 'cookie-encryption-password' takes only a single character rather than a string [4675](https://github.com/oracle/helidon/pull/4675)
- Test: Coordinator test fix [4610](https://github.com/oracle/helidon/pull/4610)
- Test: Fix location of JDK to use a link and not installation [4661](https://github.com/oracle/helidon/pull/4661)
- Test: Fixed orphaned modules. [4658](https://github.com/oracle/helidon/pull/4658)
- WebServer: WebServerTls parts should not be initialized when disabled [4652](https://github.com/oracle/helidon/pull/4652)
- WebSocket: Updated WebSocketHandler to correctly propagate query params from weberver to Tyrus [4647](https://github.com/oracle/helidon/pull/4647)
- Build: Fix all copyright warnings. [4660](https://github.com/oracle/helidon/pull/4660)
- Build: Fix release.sh to update version in attributes.adoc  [4689](https://github.com/oracle/helidon/pull/4689)
- Docs: various documentation updates [4727](https://github.com/oracle/helidon/pull/4727) [4627](https://github.com/oracle/helidon/pull/4627) [4707](https://github.com/oracle/helidon/pull/4707) [4713](https://github.com/oracle/helidon/pull/4713) [4714](https://github.com/oracle/helidon/pull/4714) [4623](https://github.com/oracle/helidon/pull/4623) [4726](https://github.com/oracle/helidon/pull/4726) [4689](https://github.com/oracle/helidon/pull/4689) [4687](https://github.com/oracle/helidon/pull/4687) [4692](https://github.com/oracle/helidon/pull/4692) [4676](https://github.com/oracle/helidon/pull/4676) [4637](https://github.com/oracle/helidon/pull/4637) [4723](https://github.com/oracle/helidon/pull/4723) [4730](https://github.com/oracle/helidon/pull/4730)
- Examples: Add examples for SE and MP to update counters of HTTP response status ranges (1xx, 2xx, etc.) [4617](https://github.com/oracle/helidon/pull/4617)
- Examples: Fix JDK base image in Dockerfiles [4634](https://github.com/oracle/helidon/pull/4634)
- Examples: Remove redundant __pkg__ folder in a project generated by helidon CLI [4642](https://github.com/oracle/helidon/pull/4642)

## [3.0.0]

We are pleased to announce Helidon 3.0.0 a major release that includes significant new features and fixes. As a major release it also includes some backward incompatible API changes.

### Notable Changes

- MicroProfile 5.0
- Jakarta EE 9.1 with `javax` to `jakarta` Java package namespace change
- Java 17 minimum JDK. Java 11 no longer supported.
- JEP-290 security hardening
- Updated Helidon SE routing API
- Numerous other enhancements and fixes

### Upgrading from Helidon 2

For information concerning upgrading your Helidon 2 application to Helidon 3 please see:

* [Helidon MP 3.x Upgrade Guide](https://helidon.io/docs/v3/#/mp/guides/migration_3x)
* [Helidon SE 3.x Upgrade Guide](https://helidon.io/docs/v3/#/se/guides/migration_3x)

### CHANGES from RC2

- JAX-RS: Set executor of CompletableFuture for response with no content. [4540](https://github.com/oracle/helidon/pull/4540) (Thank you @MadsBrun).
- Dependencies: Integrate Build Tools 3.0.0 [TBD](https://github.com/oracle/helidon/pull/TBD) [4580](https://github.com/oracle/helidon/pull/4580)
- Dependencies: Upgrade Tyrus. [4597](https://github.com/oracle/helidon/pull/4597)
- Archetype: Enable Database MP tests  [4570](https://github.com/oracle/helidon/pull/4570)
- Archetypes: Generate docker files for quickstart and database [4581](https://github.com/oracle/helidon/pull/4581)
- Archetypes: Generated GreetingResource contains incorrect javadoc [4599](https://github.com/oracle/helidon/pull/4599)
- Examples: Fix READMEs required jdk version [4572](https://github.com/oracle/helidon/pull/4572)
- Examples: Fix gRPC examples that are failing. [4585](https://github.com/oracle/helidon/pull/4585)
- Build: Remove -x option in CI scripts [4593](https://github.com/oracle/helidon/pull/4593)
- Doc updates: [4598](https://github.com/oracle/helidon/pull/4598) [4609](https://github.com/oracle/helidon/pull/4609) [4594](https://github.com/oracle/helidon/pull/4594) [4507](https://github.com/oracle/helidon/pull/4507) [4606](https://github.com/oracle/helidon/pull/4606) [4590](https://github.com/oracle/helidon/pull/4590) [4608](https://github.com/oracle/helidon/pull/4608) [4542](https://github.com/oracle/helidon/pull/4542) [4565](https://github.com/oracle/helidon/pull/4565) [4566](https://github.com/oracle/helidon/pull/4566) [4564](https://github.com/oracle/helidon/pull/4564) [4584](https://github.com/oracle/helidon/pull/4584) [4545](https://github.com/oracle/helidon/pull/4545) [4561](https://github.com/oracle/helidon/pull/4561) [4595](https://github.com/oracle/helidon/pull/4595) [4605](https://github.com/oracle/helidon/pull/4605) [4574](https://github.com/oracle/helidon/pull/4574) [4569](https://github.com/oracle/helidon/pull/4569) [4517](https://github.com/oracle/helidon/pull/4517) [4579](https://github.com/oracle/helidon/pull/4579)

## [3.0.0-RC2]

This is the second RC release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
(see Compatibility below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes from 2.x:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-RC2 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`
- Deprecated APIs have been removed

### CHANGES from RC1

Changes from RC1:

- Archetypes: 3.0 archetypes minor issues : Multiple fix to templates [4556](https://github.com/oracle/helidon/pull/4556)
- Archetypes: Fix Ambiguous dependencies for type PersistenceUnitInfo [4568](https://github.com/oracle/helidon/pull/4568)
- Dependencies: Bump Reactive Messaging API to 3.0-RC3 to allow CCR [4535](https://github.com/oracle/helidon/pull/4535)
- Dependencies: Integrate build tools 3.0.0-RC2 [4562](https://github.com/oracle/helidon/pull/4562)
- Deserialization configuration update. [4533](https://github.com/oracle/helidon/pull/4533)
- Native Image: Add module info to native image extensions. [4529](https://github.com/oracle/helidon/pull/4529)
- Documentation updates: [4546](https://github.com/oracle/helidon/pull/4546) [4541](https://github.com/oracle/helidon/pull/4541) [4478](https://github.com/oracle/helidon/pull/4478) [4558](https://github.com/oracle/helidon/pull/4558) [4520](https://github.com/oracle/helidon/pull/4520) [4560](https://github.com/oracle/helidon/pull/4560) [4506](https://github.com/oracle/helidon/pull/4506) [4543](https://github.com/oracle/helidon/pull/4543) [4557](https://github.com/oracle/helidon/pull/4557) [4563](https://github.com/oracle/helidon/pull/4563) [4521](https://github.com/oracle/helidon/pull/4521) [4536](https://github.com/oracle/helidon/pull/4536) [4515](https://github.com/oracle/helidon/pull/4515) [4513](https://github.com/oracle/helidon/pull/4513) [4525](https://github.com/oracle/helidon/pull/4525) [4527](https://github.com/oracle/helidon/pull/4527) [4505](https://github.com/oracle/helidon/pull/4505) [4555](https://github.com/oracle/helidon/pull/4555) [4469](https://github.com/oracle/helidon/pull/4469) [4477](https://github.com/oracle/helidon/pull/4477) [4528](https://github.com/oracle/helidon/pull/4528)

## [3.0.0-RC1]

This is the first RC release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
(see Compatibility below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes from 2.x:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-RC1 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`
- Deprecated APIs have been removed

### CHANGES from M2

- WebServer: Routing 3 [4236](https://github.com/oracle/helidon/pull/4236)
- WebServer: Removed deprecated classes from WebServer, mostly static content. [4376](https://github.com/oracle/helidon/pull/4376)
- WebServer: Remove deprecated method from `HelidonRestServiceSupport` [4393](https://github.com/oracle/helidon/pull/4393)
- WebServer: Nocache [4258](https://github.com/oracle/helidon/pull/4258)
- Tracing: Tracing abstraction for Helidon [4458](https://github.com/oracle/helidon/pull/4458)
- Tracing: Add OpenTelemetry support [4518](https://github.com/oracle/helidon/pull/4518)
- Test: ThreadPoolTest rc fix [4460](https://github.com/oracle/helidon/pull/4460)
- Test: Removed final from proxied types, these could no longer be proxied. [4489](https://github.com/oracle/helidon/pull/4489)
- Test: Re-enable Packaging Tests [4412](https://github.com/oracle/helidon/pull/4412)
- Test: Mock connector [4457](https://github.com/oracle/helidon/pull/4457)
- Test: Load both bean definitions of the same type instead of choosing just one [4494](https://github.com/oracle/helidon/pull/4494)
- Test: Intermittent SubscriberPublMsgToPaylRetComplVoidBean test fix [4474](https://github.com/oracle/helidon/pull/4474)
- Test: Intermittent SubscriberPublMsgToMsgRetComplBean test fix [4491](https://github.com/oracle/helidon/pull/4491)
- Test: Use 127.0.0.1 for client connections in test (instead of 0.0.0.0) [4279](https://github.com/oracle/helidon/pull/4279)
- Test: Add test-nightly.sh [4278](https://github.com/oracle/helidon/pull/4278)
- Security: RoleContainer support added [4271](https://github.com/oracle/helidon/pull/4271)
- Security: Remove deprecated method from Jwt. [4400](https://github.com/oracle/helidon/pull/4400)
- Security: OIDC update to support HTTPS identity provider on plain socket Helidon [4222](https://github.com/oracle/helidon/pull/4222)
- Security: HTTP Signatures: Fixed deprecation and modified defaults. [4399](https://github.com/oracle/helidon/pull/4399)
- Security: Change defaults for Open ID Connect [4335](https://github.com/oracle/helidon/pull/4335)
- Security: Added X509 certificate context key when client certificate is present and pem trust store configuration [4185](https://github.com/oracle/helidon/pull/4185)
- Security: Add IDCS related info to MP Security example [4343](https://github.com/oracle/helidon/pull/4343)
- Reactive: Multi support in Reactive Messaging 3.0 [4475](https://github.com/oracle/helidon/pull/4475)
- OCI: Makes OCI CDI extension work with OCI's shaded full jar [4498](https://github.com/oracle/helidon/pull/4498) [4531](https://github.com/oracle/helidon/pull/4531) [4483](https://github.com/oracle/helidon/pull/4483) [4488](https://github.com/oracle/helidon/pull/4488) [4522](https://github.com/oracle/helidon/pull/4522)
- OCI: Integration Examples Update and Deprecation [4516](https://github.com/oracle/helidon/pull/4516)
- Misc: Remove experimental flags from relevant modules. [4361](https://github.com/oracle/helidon/pull/4361)
- Metrics: Properly count completed and failed thread pool tasks (master) [4247](https://github.com/oracle/helidon/pull/4247)
- Metrics: Deal with deprecations in metrics [4397](https://github.com/oracle/helidon/pull/4397)
- Metrics: Correct Prometheus output for timer and JSON output for SimpleTimer [4243](https://github.com/oracle/helidon/pull/4243)
- Metrics: Convert  `synchronized` to use semaphores [4284](https://github.com/oracle/helidon/pull/4284)
- Metrics: Address deprecations in MP metrics [4500](https://github.com/oracle/helidon/pull/4500)
- Messaging: Messaging Deprecations cleanup [4454](https://github.com/oracle/helidon/pull/4454)
- Messaging: Kafka nack support [4443](https://github.com/oracle/helidon/pull/4443)
- Messaging: JMS configurable producer properties #3980 [4272](https://github.com/oracle/helidon/pull/4272)
- LRA: LRA Deprecations removal [4449](https://github.com/oracle/helidon/pull/4449)
- JPA: Updates persistence.xmls to 3.0. Re-enables JPA tests. Removes eclips… [4446](https://github.com/oracle/helidon/pull/4446)
- gRPC: Removed support for JavaMarshaller as default marshaller [4423](https://github.com/oracle/helidon/pull/4423)
- gRPC: Fixing module-info files so that gRPC works when using Java modules [4235](https://github.com/oracle/helidon/pull/4235)
- GraphQL: Update to mp-graphql 2.0 [4190](https://github.com/oracle/helidon/pull/4190)
- FT: Update feature catalog with FT for SE (master) [4351](https://github.com/oracle/helidon/pull/4351)
- FT: Port of a few Fault Tolerance related PRs to master [4255](https://github.com/oracle/helidon/pull/4255)
- FT: Fixed problem in DelayRetryPolicyTest that would cause all delays to be zero (master) [4221](https://github.com/oracle/helidon/pull/4221)
- FT: Avoid calling getPackage() on an annotation type (master) [4350](https://github.com/oracle/helidon/pull/4350)
- FT: Added config support for bulkheads, breakers, timeouts and retries (master) [4357](https://github.com/oracle/helidon/pull/4357)
- Examples: Uses lowercase for database column names in se database archetype to fix issue #4187 [4273](https://github.com/oracle/helidon/pull/4273)
- Examples: Gradle: Add helidon test dependency. Add task dependency for jandex [4231](https://github.com/oracle/helidon/pull/4231)
- Examples: Use standard graalvm base image in Dockerfile.native [4501](https://github.com/oracle/helidon/pull/4501)
- Docs: [New Docs PR] - Helidon MP WebSocket 4213 [4442](https://github.com/oracle/helidon/pull/4442)
- Docs: [New Docs PR] - Helidon MP Scheduling #4206 [4456](https://github.com/oracle/helidon/pull/4456)
- Docs: [New Docs PR] - Helidon MP OpenTracing #4199 [4428](https://github.com/oracle/helidon/pull/4428)
- Docs: [New Docs PR] - Helidon MP OpenAPI #4209 [4421](https://github.com/oracle/helidon/pull/4421)
- Docs: [New Docs PR] - Helidon MP Bean Validation #4202 [4426](https://github.com/oracle/helidon/pull/4426)
- Docs: WebServer documentation [4461](https://github.com/oracle/helidon/pull/4461)
- Docs: Updated doc for FT MP using new template [4438](https://github.com/oracle/helidon/pull/4438)
- Docs: Updated FT SE documentation [4465](https://github.com/oracle/helidon/pull/4465)
- Docs: New formatting for Helidon Health Checks SE document [4495](https://github.com/oracle/helidon/pull/4495)
- Docs: New formatting for GraphQL MP document [4514](https://github.com/oracle/helidon/pull/4514)
- Docs: New formatting for CORS SE documentation [4470](https://github.com/oracle/helidon/pull/4470)
- Docs: New doc file that briefly describes the JAX-RS Client API [4451](https://github.com/oracle/helidon/pull/4451)
- Docs: MP CORS doc update [4511](https://github.com/oracle/helidon/pull/4511)
- Docs: Latest sitegen and functional docs improvements [4463](https://github.com/oracle/helidon/pull/4463)
- Docs: Java doc typo fixing [4473](https://github.com/oracle/helidon/pull/4473)
- Docs: Helidon MP RestClient documentation [4448](https://github.com/oracle/helidon/pull/4448)
- Docs: Helidon MP Health new style documentation  [4387](https://github.com/oracle/helidon/pull/4387)
- Docs: Fix config doc annotations for OpenAPI builder [4419](https://github.com/oracle/helidon/pull/4419)
- Docs: Fix Javadoc links [4479](https://github.com/oracle/helidon/pull/4479)
- Docs: Documentation : Helidon DB Client Guide [2673](https://github.com/oracle/helidon/pull/2673)
- Docs: Change last use of '{...}' for a placeholder to `*` [4455](https://github.com/oracle/helidon/pull/4455)
- Docs: Add default value constants and use in config doc [4408](https://github.com/oracle/helidon/pull/4408)
- Docs: 4367 doc update for java marshaller deprecation [4441](https://github.com/oracle/helidon/pull/4441)
- Docs: Update OCI integration documentation to reflect use of new OCI SDK CDI Extension [4409](https://github.com/oracle/helidon/pull/4409)
- Docs: Add tuning guides [4156](https://github.com/oracle/helidon/pull/4156)
- Dependencies: Yasson version increased to 2.0.4 [4260](https://github.com/oracle/helidon/pull/4260)
- Dependencies: Upgrades Jandex to 2.4.3.Final [4487](https://github.com/oracle/helidon/pull/4487)
- Dependencies: Upgrades HikariCP to 5.0.1 [4519](https://github.com/oracle/helidon/pull/4519)
- Dependencies: Upgrades Hibernate to version 6.1.1.Final [4484](https://github.com/oracle/helidon/pull/4484)
- Dependencies: Upgrades H2 to 2.1.212 [4298](https://github.com/oracle/helidon/pull/4298)
- Dependencies: Revert back to using javax.validation as jakarta.validation is not supported by micronaut [4476](https://github.com/oracle/helidon/pull/4476)
- Dependencies: Restoring dependency with Tyrus 2.0.1 [4429](https://github.com/oracle/helidon/pull/4429)
- Dependencies: Bump derby from 10.13.1.1 to 10.14.2.0 in /examples/jbatch [4453](https://github.com/oracle/helidon/pull/4453)
- Dependencies: Upgrade Netty to 4.1.77.Final [4252](https://github.com/oracle/helidon/pull/4252)
- Dependencies: Upgrade Jackson to 2.13.2.2 [4178](https://github.com/oracle/helidon/pull/4178)
- Dependencies: Move mockito dependency management to root pom [4285](https://github.com/oracle/helidon/pull/4285)
- Dependencies: upgrade google api client [4297](https://github.com/oracle/helidon/pull/4297)
- DataSource: Adds XA support to Helidon's UCP integration [4291](https://github.com/oracle/helidon/pull/4291)
- Config: Support Hocon/Json Configuration Source for MP [4347](https://github.com/oracle/helidon/pull/4347)
- Config: Metadata argument number changed [4386](https://github.com/oracle/helidon/pull/4386)
- Config: Deserialization disabled by default. [4334](https://github.com/oracle/helidon/pull/4334)
- Config: Config metadata update [4403](https://github.com/oracle/helidon/pull/4403)
- Config: Add helidon-config-yaml-mp as a dependency in helidon-microprofile-config [4379](https://github.com/oracle/helidon/pull/4379)
- Common: Removal of deprecated code in common context [4388](https://github.com/oracle/helidon/pull/4388)
- Common: Removal of deprecated code in common configurable [4405](https://github.com/oracle/helidon/pull/4405)
- Common: Enable thread pool growth at threshold instead of above it (master) [4248](https://github.com/oracle/helidon/pull/4248)
- Common: Remove dependency on helidon-common-reactive from config (#4225) [4229](https://github.com/oracle/helidon/pull/4229)
- Cleanup: Fix code with 'TODO 3.0.0-JAKARTA' comment [4447](https://github.com/oracle/helidon/pull/4447)
- CORS: Preserve order of mapped cross-origin config path entries from config; add test [4431](https://github.com/oracle/helidon/pull/4431)
- CORS: Correct return of path from MP CORS request adapter; add test [4435](https://github.com/oracle/helidon/pull/4435)
- CDI: Updates all beans.xml files to version 3.0 [4404](https://github.com/oracle/helidon/pull/4404)

## [3.0.0-M2]

This is the second milestone release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
(see [Compatibility](#compatibility) below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-M2 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`

### CHANGES from M1

- BV: Support of Bean Validation in Helidon MP #721 [3780](https://github.com/oracle/helidon/pull/3780)
- CDI: Abstract decorator class now gets correct proxy name in Weld [4148](https://github.com/oracle/helidon/pull/4148)
- CORS support to OidcSupport (#3844) [3871](https://github.com/oracle/helidon/pull/3871)
- CORS: CORS `@CrossOrigin` checking and request-time performance improvements [3932](https://github.com/oracle/helidon/pull/3932)
- CORS: Compare origin URLs based on protocol, host and port [3934](https://github.com/oracle/helidon/pull/3934)
- Common: Improve threadNamePrefix defaulting to be more informative (#4165) [4166](https://github.com/oracle/helidon/pull/4166)
- Config: Fix retention of @Configured [4115](https://github.com/oracle/helidon/pull/4115)
- Config: Support for Hocon inclusion of files without an extensions [4168](https://github.com/oracle/helidon/pull/4168)
- Config: Turn off reference resolution/substitution at parser level [4175](https://github.com/oracle/helidon/pull/4175)
- Config: Fixed problem supporting config profiles with JSON and HOCON [3958](https://github.com/oracle/helidon/pull/3958)
- Config: fixed manifest and documentation for Helidon Config Encryption [4013](https://github.com/oracle/helidon/pull/4013)
- Config: hocon include [3998](https://github.com/oracle/helidon/pull/3998)
- Fault Tolerance: Fixed a few problems with Fallback and Multi's [4176](https://github.com/oracle/helidon/pull/4176)
- GraphQL: Enable mp-graphql tck with 2.0-RC3 of Microprofile GraphQL Spec [4147](https://github.com/oracle/helidon/pull/4147)
- Health: HEAD support to health endpoints [3936](https://github.com/oracle/helidon/pull/3936)
- JAX-RS Client: Jersey JAX-RS client context propagation #3762 [3865](https://github.com/oracle/helidon/pull/3865)
- JAX-RS: Follow up to PR 3921 that adds support for base interfaces as well [3982](https://github.com/oracle/helidon/pull/3982)
- JAX-RS: Search for @Path annotations in base classes [3921](https://github.com/oracle/helidon/pull/3921)
- LRA Custom headers propagation #3702 (#3768) [3877](https://github.com/oracle/helidon/pull/3877)
- Media: Fix body part header encoding [3969](https://github.com/oracle/helidon/pull/3969)
- Media: MimeParser parses closing boundary as normal boundary [3968](https://github.com/oracle/helidon/pull/3968)
- Media: Remove buffered multipart [4103](https://github.com/oracle/helidon/pull/4103)
- Media: Update MediaType parser to handle parameter without value [3999](https://github.com/oracle/helidon/pull/3999)
- Messaging - Fix badly subscribed connector to processor signature [3957](https://github.com/oracle/helidon/pull/3957)
- Messaging - signature detection fix #3883 3x [3973](https://github.com/oracle/helidon/pull/3973)
- MicroProfile: Messaging 3.0 [4091](https://github.com/oracle/helidon/pull/4091)
- Native image: Native image configuration reflection update for Jaeger [4117](https://github.com/oracle/helidon/pull/4117)
- OpenAPI: Replace dep on SmallRye pom with deps on 2 artifacts (3.0) [4010](https://github.com/oracle/helidon/pull/4010)
- Security: Correctly resolve OIDC metadata. (#3985) [4006](https://github.com/oracle/helidon/pull/4006)
- Security: Do not fail when expected audience is null [4160](https://github.com/oracle/helidon/pull/4160)
- Security: Fix JwtProvider wrong error message [4136](https://github.com/oracle/helidon/pull/4136)
- Security: Fixed builder from configuration in OutboundTargetDefinition [3919](https://github.com/oracle/helidon/pull/3919)
- Security: Injection of empty SecurityContext [4161](https://github.com/oracle/helidon/pull/4161)
- Security: New security response mapper mechanism [4093](https://github.com/oracle/helidon/pull/4093)
- Tests: Fixed dbclient integration tests build issues. [4100](https://github.com/oracle/helidon/pull/4100)
- Tests: possible fix for HttpPipelineTest hang  [4143](https://github.com/oracle/helidon/pull/4143)
- Tracing: Disable paths such as /metrics and /health from tracing [3977](https://github.com/oracle/helidon/pull/3977)
- Tracing: Enable OpenTracing 2.0 TCKs [3962](https://github.com/oracle/helidon/pull/3962)
- Tracing: Fix #3966 set collectorUri() with URL with no port number adds port  [3993](https://github.com/oracle/helidon/pull/3993)
- WebClient: Case insensitive client request headers fix [4108](https://github.com/oracle/helidon/pull/4108)
- WebClient: MDC propagation [4109](https://github.com/oracle/helidon/pull/4109)
- WebClient: hang fix [3997](https://github.com/oracle/helidon/pull/3997)
- WebServer: Allow a list of path patterns to be specified for exclusion in the access log[3959](https://github.com/oracle/helidon/pull/3959)
- WebServer: Do not log full stack traces in SEVERE when a connection reset is received [3918](https://github.com/oracle/helidon/pull/3918)
- WebServer: Explicit 404 in Jersey no longer calls next() [3976](https://github.com/oracle/helidon/pull/3976)
- WebServer: Upgrade WebSocket from Java HttpClient [3994](https://github.com/oracle/helidon/pull/3994)
- Dependencies: Jersey version bump to 3.0.4 [3884](https://github.com/oracle/helidon/pull/3884)
- Dependencies: MySQL JDBC driver updated to 8.0.28 and PostgreSQL JDBC driver updated to 42.3.3. [4098](https://github.com/oracle/helidon/pull/4098)
- Dependencies: Upgrade Jackson Databind to 2.13.2.1 (BOM 2.13.2.20220324) [4028](https://github.com/oracle/helidon/pull/4028)
- Dependencies: Upgrade Netty to 4.1.76.Final [4121](https://github.com/oracle/helidon/pull/4121)
- Dependencies: Upgrade grpc-java to 1.45.1 [4150](https://github.com/oracle/helidon/pull/4150)
- Dependencies: Upgrade snakeyaml and typesafe-config [3941](https://github.com/oracle/helidon/pull/3941)
- Docs: Add discussion of Helidon-specific config settings to MP OpenAPI doc [3955](https://github.com/oracle/helidon/pull/3955)
- Docs: Describe more Scheduled properties [4043](https://github.com/oracle/helidon/pull/4043)
- Docs: Fix guide broken links and typos 4119 [4133](https://github.com/oracle/helidon/pull/4133)
- Docs: Fix guide. Change JPA Scope. [4170](https://github.com/oracle/helidon/pull/4170)
- Docs: JBatch guide for issue 2785 [3895](https://github.com/oracle/helidon/pull/3895)
- Docs: README: Update JDK and mvn versions for build [3946](https://github.com/oracle/helidon/pull/3946)
- Docs: SecMP JWT Auth and Rest Client spec links updated [4005](https://github.com/oracle/helidon/pull/4005)
- Docs: fixed links in the cards for extensions [3930](https://github.com/oracle/helidon/pull/3930)
- Docs: updated link to the new spec for issue 3899 [3909](https://github.com/oracle/helidon/pull/3909)
- Examples: Fix deps in jpa examples and do other cleanup [4132](https://github.com/oracle/helidon/pull/4132)
- Examples: JBatch example for [3927](https://github.com/oracle/helidon/pull/3927)
- Examples: Quickstart cleanup, using @HelidonTest in MP. (#4011) [4014](https://github.com/oracle/helidon/pull/4014)
- Examples: Removed incorrect call to indexOf [3920](https://github.com/oracle/helidon/pull/3920)
- Examples: Update quickstart Dockerfiles  [4088](https://github.com/oracle/helidon/pull/4088)
- Examples: Use OBJECT schema type with requiredProperties in Quickstart MP [4151](https://github.com/oracle/helidon/pull/4151)

## [3.0.0-M1]

This is the first milestone release of Helidon 3.0. It contains significant new
features, enhancements and fixes. It also contains backward incompatible changes.
( see [Compatibility](#compatibility) below). This milestone release is provided for customers that
want early access to Helidon 3.0 for experimentation. It should be considered
unstable and is not intended for production use. APIs and features might not be
fully implemented and tested and are subject to change. Documentation is incomplete.
Those looking for a stable release should use Helidon 2.

Notable changes:

- MicroProfile 5.0 support
- javax to jakarta Java package namespace change for Jakarta 9 APIs
- Java 11 no longer supported. Java 17 or newer is required.

### Compatibility

3.0.0-M1 contains the following incompatibilities with 2.x:

- Java 17 or newer is required
- Java package namespace for Jakarta APIs has changed from `javax` to `jakarta`

### CHANGES

- Common: Builder API updates. [3641](https://github.com/oracle/helidon/pull/3641)
- Config: 3542 iterable configuration option [3600](https://github.com/oracle/helidon/pull/3600)
- Config: MicroProfile Config 3.0 [3644](https://github.com/oracle/helidon/pull/3644)
- Config: Support for mutable file based MP config sources. (#3666) [3730](https://github.com/oracle/helidon/pull/3730)
- DBClient: Fix dbclient threading issues when DML operations are executed multiple times in a tight loop [3849](https://github.com/oracle/helidon/pull/3849)
- Fault Tolerance: Implementation of Fault tolerance 4.0  [3664](https://github.com/oracle/helidon/pull/3664)
- Fault Tolerance: Improved support for cancellation of FT handlers [3680](https://github.com/oracle/helidon/pull/3680)
- Fault Tolerance: Only deactivate request context if it was inactive before migrating it (master) [3825](https://github.com/oracle/helidon/pull/3825)
- Grpc: Remove SecurityManager and AccessController dependencies in Grpc [3685](https://github.com/oracle/helidon/pull/3685)
- Health: Add support for Health 4.0 [3707](https://github.com/oracle/helidon/pull/3707)
- JAX-RS: Handle creation of InjectionManager when parent is a HelidonInjectionManager [3755](https://github.com/oracle/helidon/pull/3755)
- JAX-RS: Special treatment for ParamConverterProviders with multiple apps (master) [3857](https://github.com/oracle/helidon/pull/3857)
- Java 17: change sources to Java 17 [3633](https://github.com/oracle/helidon/pull/3633)
- LRA: Bump up to jakartified LRA 2.0 [3769](https://github.com/oracle/helidon/pull/3769)
- LRA: Missing wget in the coordinator's docker base image [3703](https://github.com/oracle/helidon/pull/3703)
- Logging: HelidonFormatter constructor made public [3609](https://github.com/oracle/helidon/pull/3609)
- Metrics: Add incorrectly-omitted tags to OpenMetrics (Prometheus) output for timers [3643](https://github.com/oracle/helidon/pull/3643)
- Metrics: Fix some remaining problems with disabling metrics, mostly deferring access to RegistryFactory (3.0) [3665](https://github.com/oracle/helidon/pull/3665)
- Metrics: Implement metrics for thread pool suppliers (3.0) [3652](https://github.com/oracle/helidon/pull/3652)
- Metrics: Metrics 4.0 implementation [3847](https://github.com/oracle/helidon/pull/3847)
- Metrics: Move scheduling of metrics periodic updater so it is run in MP as well as in SE  (3.x) [3733](https://github.com/oracle/helidon/pull/3733)
- Metrics: Prepare RegistryFactory lazily to use the most-recently-assigned MetricsSettings (3.0) [3661](https://github.com/oracle/helidon/pull/3661)
- Metrics: SamplerType now public. [3788](https://github.com/oracle/helidon/pull/3788)
- Metrics: Suppress warning when metrics PeriodicExecutor is stopped multiple times (e.g., during tests) [3617](https://github.com/oracle/helidon/pull/3617)
- Metrics: add missing tags for Prometheus output of timers [3647](https://github.com/oracle/helidon/pull/3647)
- MicroProfile: Update MP specs to latest versions [3670](https://github.com/oracle/helidon/pull/3670) [3743](https://github.com/oracle/helidon/pull/3743)
- MicroProfile: jakarta packages [3635](https://github.com/oracle/helidon/pull/3635)
- Native-image: Update native-image.properties for webserver and Netty [3772](https://github.com/oracle/helidon/pull/3772)
- OpenAPI: Catch all exceptions, not just IOException, when unable to read Jandex file [3626](https://github.com/oracle/helidon/pull/3626)
- OpenAPI: Redesign the per-application OpenAPI processing [3615](https://github.com/oracle/helidon/pull/3615)
- OpenAPI: Support MP OpenAPI 3.0 [3692](https://github.com/oracle/helidon/pull/3692)
- Reactive: Multi defaultIfEmpty [3592](https://github.com/oracle/helidon/pull/3592)
- Scheduling: SE Scheduling remove synchronized [3564](https://github.com/oracle/helidon/pull/3564)
- Security: Fix proxy configuration. (#3749) [3761](https://github.com/oracle/helidon/pull/3761)
- Security: JWK keys lazy load [3751](https://github.com/oracle/helidon/pull/3751)
- Security: JWT Auth 2.0 impl [3872](https://github.com/oracle/helidon/pull/3872)
- Security: SignedJwt's parseToken() expects characters from base64 instead of ba… [3722](https://github.com/oracle/helidon/pull/3722)
- Security: Upgrade MicroProfile JWT to 2.0 from 2.0-RC2 [3779](https://github.com/oracle/helidon/pull/3779)
- Test: TestNG support (#3263) [3672](https://github.com/oracle/helidon/pull/3672)
- Tracing: Close active span if an exception is thrown in client request processing [3725](https://github.com/oracle/helidon/pull/3725)
- WebClient: Do not create close listener handlers for every new request (master) [3859](https://github.com/oracle/helidon/pull/3859)
- WebClient: Netty order of writes #3674 [3681](https://github.com/oracle/helidon/pull/3681)
- WebClient: Propagate any existing server context into a Webclient reactive code (master) [3804](https://github.com/oracle/helidon/pull/3804)
- WebClient: event group initialization changed [3820](https://github.com/oracle/helidon/pull/3820)
- WebServer: 3640 Netty mixed writing (#3671) [3679](https://github.com/oracle/helidon/pull/3679)
- WebServer: Allow compression to be enabled together with HTTP/2 [3700](https://github.com/oracle/helidon/pull/3700)
- WebServer: Ensure all thread pools created by Helidon are named [3794](https://github.com/oracle/helidon/pull/3794)
- WebServer: Fix wrong connection close (#3830) [3866](https://github.com/oracle/helidon/pull/3866)
- WebServer: New default for io.netty.allocator.maxOrder (master) [3826](https://github.com/oracle/helidon/pull/3826) [3834](https://github.com/oracle/helidon/pull/3834)
- WebServer: Swallowed error fix [3791](https://github.com/oracle/helidon/pull/3791)
- WebServer: Provide access to client x509 certificate when under mTLS [4185](https://github.com/oracle/helidon/pull/4185)
- Webclient: New flag to force the use of relative URIs in WebClient  [3614](https://github.com/oracle/helidon/pull/3614)
- Dependencies: Bump up cron utils [3677](https://github.com/oracle/helidon/pull/3677)
- Dependencies: Upgrade Neo4j to 4.4.3. for Helidon 3.x [3863](https://github.com/oracle/helidon/pull/3863)
- Dependencies: Upgrade grpc-java to 1.44.0 [3856](https://github.com/oracle/helidon/pull/3856)
- Dependencies: Upgrade logback to 1.2.10 [3889](https://github.com/oracle/helidon/pull/3889)
- Dependencies: Upgrades H2 to 2.0.206. [3744](https://github.com/oracle/helidon/pull/3744)
- Dependencies: Upgrades Netty to 4.1.73.Final [3797](https://github.com/oracle/helidon/pull/3797)
- Dependencies: Upgrades log4j to 2.17.1 [3777](https://github.com/oracle/helidon/pull/3777)
- Doc and JavaDoc fixes for #3747 and #3687. [3767](https://github.com/oracle/helidon/pull/3767)
- Docs: Documentation SE : Webclient guide [3189](https://github.com/oracle/helidon/pull/3189)
- Docs: LRA doc fix artefact and group ids [3688](https://github.com/oracle/helidon/pull/3688)
- Docs: New section about injection managers in docs (master) [3858](https://github.com/oracle/helidon/pull/3858)
- Docs: Update external Jakarta EE javadoc links [3709](https://github.com/oracle/helidon/pull/3709)
- Docs: javadoc: Add additional microprofile crossreferences [3715](https://github.com/oracle/helidon/pull/3715)
- Docs: macos quarantine and JDK version [3878](https://github.com/oracle/helidon/pull/3878)
- Examples: Archetype v2 [3874](https://github.com/oracle/helidon/pull/3874)
- Examples: Fix examples/grpc/metrics project with native-image #3759 [3803](https://github.com/oracle/helidon/pull/3803)
- Examples: Support Gradle application plugin in quickstart examples [3612](https://github.com/oracle/helidon/pull/3612)
- Examples: Update bare-mp archetype to use microprofile-core [3795](https://github.com/oracle/helidon/pull/3795)


[3.2.2]: https://github.com/helidon-io/helidon/compare/3.2.1...3.2.2
[3.2.1]: https://github.com/helidon-io/helidon/compare/3.2.0...3.2.1
[3.2.0]: https://github.com/helidon-io/helidon/compare/3.1.2...3.2.0
[3.1.2]: https://github.com/helidon-io/helidon/compare/3.1.1...3.1.2
[3.1.1]: https://github.com/helidon-io/helidon/compare/3.1.0...3.1.1
[3.1.0]: https://github.com/helidon-io/helidon/compare/3.0.2...3.1.0
[3.0.2]: https://github.com/helidon-io/helidon/compare/3.0.1...3.0.2
[3.0.1]: https://github.com/helidon-io/helidon/compare/3.0.0...3.0.1
[3.0.0]: https://github.com/helidon-io/helidon/compare/3.0.0-RC2...3.0.0
[3.0.0-RC2]: https://github.com/helidon-io/helidon/compare/3.0.0-RC1...3.0.0-RC2
[3.0.0-RC1]: https://github.com/helidon-io/helidon/compare/3.0.0-M2...3.0.0-RC1
[3.0.0-M2]: https://github.com/helidon-io/helidon/compare/3.0.0-M1...3.0.0-M2
[3.0.0-M1]: https://github.com/helidon-io/helidon/compare/2.4.0...3.0.0-M1
