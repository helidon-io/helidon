# Helidon MP Hello World Implicit Example

This examples shows a simple application written using Helidon MP.
It is implicit because in this example you don't write the
`main` class, instead you rely on the Microprofile Server main class.

## Build and run

```bash
mvn package
java -jar target/helidon-examples-microprofile-hello-world-implicit.jar
```

Then try the endpoints:

```
curl -X GET http://localhost:7001/helloworld
curl -X GET http://localhost:7001/helloworld/earth
curl -X GET http://localhost:7001/another
```

## CRaC

```bash
docker buildx build -t crac-helloworld . -f Dockerfile.crac
# First time ran, checkpoint is created
docker run -d --privileged -p 7001:7001 --name crac-helloworld crac-helloworld
docker stop crac-helloworld
# Second time starting from checkpoint
docker start crac-helloworld
docker logs crac-helloworld
```

```
=== Creating CRaC checkpoint ===
Checking CRIU compatibility(don't forget --privileged):
Warn  (criu/kerndat.c:1470): CRIU was built without libnftables support
Looks good.
[Tue Dec 06 20:39:31 GMT 2022] INFO: io.helidon.common.LogConfig doConfigureLogging - Logging at initialization configured using classpath: /logging.properties 
[Tue Dec 06 20:39:31 GMT 2022] INFO: org.jboss.weld.bootstrap.WeldStartup <clinit> - WELD-000900: 4.0.2 (Final) 
[Tue Dec 06 20:39:32 GMT 2022] INFO: io.helidon.microprofile.openapi.OpenApiCdiExtension <init> - OpenAPI support could not locate the Jandex index file META-INF/jandex.idx so will build an in-memory index.
This slows your app start-up and, depending on CDI configuration, might omit some type information needed for a complete OpenAPI document.
Consider using the Jandex maven plug-in during your build to create the index and add it to your app. 
[Tue Dec 06 20:39:32 GMT 2022] FINE: io.helidon.microprofile.config.ConfigCdiExtension <init> - ConfigCdiExtension instantiated 
[Tue Dec 06 20:39:32 GMT 2022] INFO: org.jboss.weld.environment.deployment.discovery.DiscoveryStrategyFactory create - WELD-ENV-000020: Using jandex for bean discovery 
[Tue Dec 06 20:39:32 GMT 2022] INFO: org.jboss.weld.bootstrap.WeldStartup startContainer - WELD-000101: Transactional services not available. Injection of @Inject UserTransaction not available. Transactional observers will be invoked synchronously. 
[Tue Dec 06 20:39:32 GMT 2022] INFO: org.jboss.weld.event.ExtensionObserverMethodImpl checkRequiredTypeAnnotations - WELD-000411: Observer method [BackedAnnotatedMethod] private io.helidon.microprofile.openapi.OpenApiCdiExtension.processAnnotatedType(@Observes ProcessAnnotatedType<X>) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds. 
[Tue Dec 06 20:39:32 GMT 2022] INFO: org.jboss.weld.event.ExtensionObserverMethodImpl checkRequiredTypeAnnotations - WELD-000411: Observer method [BackedAnnotatedMethod] public org.glassfish.jersey.ext.cdi1x.internal.ProcessAllAnnotatedTypes.processAnnotatedType(@Observes ProcessAnnotatedType<?>, BeanManager) receives events for all annotated types. Consider restricting events using @WithAnnotations or a generic type with bounds. 
[Tue Dec 06 20:39:32 GMT 2022] WARNING: org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider isJerseyOrDependencyType - Class class [I has null package 
[Tue Dec 06 20:39:32 GMT 2022] INFO: io.helidon.tracing.tracerresolver.TracerResolverBuilder build - TracerResolver not configured, tracing is disabled 
[Tue Dec 06 20:39:32 GMT 2022] INFO: io.helidon.microprofile.security.SecurityCdiExtension registerSecurity - Authentication provider is missing from security configuration, but security extension for microprofile is enabled (requires providers configuration at key security.providers). Security will not have any valid authentication provider 
[Tue Dec 06 20:39:32 GMT 2022] INFO: io.helidon.microprofile.security.SecurityCdiExtension registerSecurity - Authorization provider is missing from security configuration, but security extension for microprofile is enabled (requires providers configuration at key security.providers). ABAC provider is configured for authorization. 
[Tue Dec 06 20:39:32 GMT 2022] INFO: io.helidon.microprofile.server.ServerCdiExtension addApplication - Registering JAX-RS Application: HelidonMP 
CR: Checkpoint ...
./runOnCRaC.sh: line 30:    18 Killed                  $JAVA_HOME/bin/java -XX:CRaCCheckpointTo=cr -jar ./*.jar
[Tue Dec 06 20:39:33 GMT 2022] INFO: io.helidon.microprofile.server.ServerCdiExtension afterRestore - CRaC snapshot restored! 
[Tue Dec 06 20:39:33 GMT 2022] INFO: io.helidon.webserver.NettyWebServer lambda$start$8 - Channel '@default' started: [id: 0x5158f8bb, L:/0.0.0.0:7001] 
[Tue Dec 06 20:39:33 GMT 2022] INFO: io.helidon.microprofile.server.ServerCdiExtension startServer - Server started on http://localhost:7001 (and all other host addresses) in 55 milliseconds (since CRaC restore). 
[Tue Dec 06 20:39:34 GMT 2022] INFO: io.helidon.common.HelidonFeatures features - Helidon MP 3.0.3-SNAPSHOT features: [CDI, Config, Fault Tolerance, Health, JAX-RS, Metrics, Open API, REST Client, Security, Server, Tracing] 
[Tue Dec 06 20:39:41 GMT 2022] INFO: io.helidon.webserver.NettyWebServer lambda$start$6 - Channel '@default' closed: [id: 0x5158f8bb, L:/0.0.0.0:7001] 
[Tue Dec 06 20:39:41 GMT 2022] INFO: io.helidon.microprofile.server.ServerCdiExtension doStop - Server stopped in 8 milliseconds. 
[Tue Dec 06 20:39:41 GMT 2022] INFO: io.helidon.microprofile.cdi.HelidonContainerImpl$HelidonCdi close - WELD-ENV-002001: Weld SE container e6c58ecb-e5cb-4e0e-83f6-d1ae6b1081d0 shut down 
=== Starting directly from CRaC checkpoint ===
[Tue Dec 06 20:39:46 GMT 2022] INFO: io.helidon.microprofile.server.ServerCdiExtension afterRestore - CRaC snapshot restored! 
[Tue Dec 06 20:39:46 GMT 2022] INFO: io.helidon.webserver.NettyWebServer lambda$start$8 - Channel '@default' started: [id: 0x5158f8bb, L:/0.0.0.0:7001] 
[Tue Dec 06 20:39:46 GMT 2022] INFO: io.helidon.microprofile.server.ServerCdiExtension startServer - Server started on http://localhost:7001 (and all other host addresses) in 54 milliseconds (since CRaC restore). 
[Tue Dec 06 20:39:46 GMT 2022] INFO: io.helidon.common.HelidonFeatures features - Helidon MP 3.0.3-SNAPSHOT features: [CDI, Config, Fault Tolerance, Health, JAX-RS, Metrics, Open API, REST Client, Security, Server, Tracing] 
kec@romulus:~/idp/ora/helidon/helidon3/examples/microprofile/hello-world-implicit$ docker start crac-helloworld 
```