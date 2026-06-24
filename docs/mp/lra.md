# LRA

## Overview

Distributed transactions for microservices are known as SAGA design patterns and
are defined by the [MicroProfile Long Running Actions
specification][microprofile-lon]. Unlike well known XA protocol, LRA is
asynchronous and therefore much more scalable. Every LRA JAX-RS resource
([participant](#participant)) defines endpoints to be invoked when transaction
needs to be *completed* or *compensated*.

## Maven Coordinates

To enable Long Running Actions, add the following dependency to your project’s
`pom.xml` (see [Managing Dependencies](../dependency-management.md)).

```xml [pom.xml]
<dependencies>
  <dependency>
    <groupId>io.helidon.microprofile.lra</groupId>
    <artifactId>helidon-microprofile-lra</artifactId>
  </dependency>
  <!-- Support for Narayana coordinator -->
  <dependency>
    <groupId>io.helidon.lra</groupId>
    <artifactId>helidon-lra-coordinator-narayana-client</artifactId>
  </dependency>
</dependencies>
```

## Usage

The LRA transactions need to be coordinated over REST API by the LRA
coordinator. [Coordinator](#coordinator) keeps track of all transactions and
calls the `@Compensate` or `@Complete` endpoints for all participants involved
in the particular transaction. LRA transaction is first started, then joined by
[participant](#participant). The participant reports the successful finish of
the transaction by calling it complete. The coordinator then calls the JAX-RS
*complete* endpoint that was registered during the join of each
[participant](#participant). As the completed or compensated participants don’t
have to be on same instance, the whole architecture is highly scalable.

![Complete](../images/lra/lra-complete-lb.svg)

If an error occurs during the LRA transaction, the participant reports a
cancellation of LRA to the coordinator. [Coordinator](#coordinator) calls
compensate on all the joined participants.

![Cancel](../images/lra/lra-compensate-lb-error.svg)

When a participant joins the LRA with timeout defined `@LRA(value =
LRA.Type.REQUIRES_NEW, timeLimit = 5, timeUnit = ChronoUnit.MINUTES)`, the
coordinator compensates if the timeout occurred before the close is reported by
the participants.

![Timeout](../images/lra/lra-compensate-lb-timeout.svg)

## API

### Participant

The Participant, or Compensator, is an LRA resource with at least one of the
JAX-RS(or non-JAX-RS) methods annotated with [@Compensate][compensate] or
[@AfterLRA][afterlra].

### @LRA

See the [Javadoc][lra-javadoc].

Marks JAX-RS method which should run in LRA context and needs to be accompanied
by at least minimal set of mandatory participant
methods([Compensate](#compensate) or [AfterLRA](#afterlra)).

LRA options:

- [value][value]
  - [REQUIRED][required] join incoming LRA or create and join new
  - [REQUIRES_NEW][requires-new] create and join new LRA
  - [MANDATORY][mandatory] join incoming LRA or fail
  - [SUPPORTS][supports] join incoming LRA or continue outside LRA context
  - [NOT_SUPPORTED][not-supported] always continue outside LRA context
  - [NEVER][never] Fail with 412 if executed in LRA context
  - [NESTED][nested] create and join new LRA nested in the incoming LRA context
- [timeLimit][timelimit] max time limit before LRA gets cancelled automatically
  by [coordinator](#coordinator)
- [timeUnit][timeunit] time unit if the timeLimit value
- [end][end] when false LRA is not closed after successful method execution
- [cancelOn][cancelon] which HTTP response codes of the method causes LRA to
  cancel
- [cancelOnFamily][cancelonfamily] which family of HTTP response codes causes
  LRA to cancel

Method parameters:

- Header [LRA_HTTP_CONTEXT_HEADER][lra-http-context] - ID of the LRA transaction

```java
@PUT
@LRA(value = LRA.Type.REQUIRES_NEW,
     timeLimit = 500,
     timeUnit = ChronoUnit.MILLIS)
@Path("start-example")
public Response startLra(
        @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
        String data) {
    return Response.ok().build();
}
```

### @Compensate

See the [Javadoc][compensate].

> [!CAUTION]
> Expected to be called by LRA [coordinator](#coordinator) only!

Compensate method is called by a [coordinator](#coordinator) when LRA is
cancelled, usually by error during execution of method body of [@LRA annotated
method](#lra). If the method responds with 500 or 202, coordinator will
eventually try the call again. If participant has [@Status annotated
method](#status), [coordinator](#coordinator) retrieves the status to find out
if retry should be done.

#### JAX-RS variant with supported LRA context values:

- Header [LRA_HTTP_CONTEXT_HEADER][lra-http-context] - ID of the LRA transaction
- Header [LRA_HTTP_PARENT_CONTEXT_HEADER][lra-http-parent] - parent LRA ID in
  case of nested LRA

```java
@PUT
@Path("/compensate")
@Compensate
public Response compensateWork(
        @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
        @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
    return LRAResponse.compensated();
}
```

#### Non JAX-RS variant with supported LRA context values:

- URI with LRA ID

```java
@Compensate
public void compensate(URI lraId) {
}
```

### @Complete

See the [Javadoc][lra-complete].

> [!CAUTION]
> Expected to be called by LRA [coordinator](#coordinator) only!

Complete method is called by [coordinator](#coordinator) when LRA is
successfully closed. If the method responds with 500 or 202, coordinator will
eventually try the call again. If participant has [@Status annotated
method](#status), [coordinator](#coordinator) retrieves the status to find out
if retry should be done.

#### JAX-RS variant with supported LRA context values:

- Header [LRA_HTTP_CONTEXT_HEADER][lra-http-context] - ID of the LRA transaction
- Header [LRA_HTTP_PARENT_CONTEXT_HEADER][lra-http-parent] - parent LRA ID in
  case of nested LRA

```java
@PUT
@Path("/complete")
@Complete
public Response complete(
        @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
        @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentLraId) {
    return LRAResponse.completed();
}
```

#### Non JAX-RS variant with supported LRA context values:

- URI with LRA ID

```java
@Complete
public void complete(URI lraId) {
}
```

### @Forget

See the [Javadoc][lra-forget].

> [!CAUTION]
> Expected to be called by LRA [coordinator](#coordinator) only!

[Complete](#complete) and [compensate](#compensate) methods can fail(500) or
report that compensation/completion is in progress(202). In such case
participant needs to be prepared to report its status over [@Status annotated
method](#status) to [coordinator](#coordinator). When
[coordinator](#coordinator) decides all the participants have finished, method
annotated with @Forget is called.

#### JAX-RS variant with supported LRA context values:

- Header [LRA_HTTP_CONTEXT_HEADER][lra-http-context] - ID of the LRA transaction
- Header [LRA_HTTP_PARENT_CONTEXT_HEADER][lra-http-parent] - parent LRA ID in
  case of nested LRA

```java
@DELETE
@Path("/forget")
@Forget
public Response forget(
        @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId,
        @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parent) {
    return Response.noContent().build();
}
```

#### Non JAX-RS variant with supported LRA context values:

- URI with LRA ID

```java
@Forget
public void forget(URI lraId) {
}
```

### @Leave

See the [Javadoc][lra-leave].

Method annotated with @Leave called with LRA context(with header
[LRA_HTTP_CONTEXT_HEADER][lra-http-context]) informs [coordinator](#coordinator)
that current participant is leaving the LRA. Method body is executed after leave
signal is sent. As a result, participant methods complete and compensate won’t
be called when the particular LRA ends.

- Header [LRA_HTTP_CONTEXT_HEADER][lra-http-context] - ID of the LRA transaction

```java
@PUT
@Path("/leave")
@Leave
public Response leaveLRA(
        @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraIdtoLeave) {
    return Response.ok().build();
}
```

### @Status

See the [Javadoc][lra-status].

> [!CAUTION]
> Expected to be called by LRA [coordinator](#coordinator) only!

If the coordinator’s call to the participant’s method fails, then it will retry
the call. If the participant is not idempotent, then it may need to report its
state to coordinator by declaring method annotated with @Status for reporting if
previous call did change participant status. [Coordinator](#coordinator) can
call it and decide if compensate or complete retry is needed.

#### JAX-RS variant with supported LRA context values:

- Header [LRA_HTTP_CONTEXT_HEADER][lra-http-context] - ID of the LRA transaction
- [ParticipantStatus][participantstatu] - Status of the participant reported to
  [coordinator](#coordinator)

```java
@GET
@Path("/status")
@Status
public Response reportStatus(
        @HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
    return Response.ok(ParticipantStatus.FailedToCompensate)
        .build();
}
```

#### Non JAX-RS variant with supported LRA context values:

- URI with LRA ID
- [ParticipantStatus][participantstatu] - Status of the participant reported to
  [coordinator](#coordinator)

```java
@Status
public Response reportStatus(URI lraId) {
    return Response.ok(ParticipantStatus.FailedToCompensate)
        .build();
}
```

### @AfterLRA

See the [Javadoc][afterlra].

> [!CAUTION]
> Expected to be called by LRA [coordinator](#coordinator) only!

Method annotated with [@AfterLRA][afterlra] in the same class as the one with
@LRA annotation gets invoked after particular LRA finishes.

#### JAX-RS variant with supported LRA context values:

- Header [LRA_HTTP_ENDED_CONTEXT_HEADER][lra-http-ended-c] - ID of the finished
  LRA transaction
- Header [LRA_HTTP_PARENT_CONTEXT_HEADER][lra-http-parent] - parent LRA ID in
  case of nested LRA
- [LRAStatus][lrastatus] - Final status of the LRA ([Cancelled][cancelled],
  [Closed][closed], [FailedToCancel][failedtocancel],
  [FailedToClose][failedtoclose])

```java
@PUT
@Path("/finished")
@AfterLRA
public Response whenLRAFinishes(
        @HeaderParam(LRA_HTTP_ENDED_CONTEXT_HEADER) URI lraId,
        @HeaderParam(LRA_HTTP_PARENT_CONTEXT_HEADER) URI parentLraId,
        LRAStatus status) {
    return Response.ok().build();
}
```

#### Non JAX-RS variant with supported LRA context values:

- URI with finished LRA ID
- [LRAStatus][lrastatus] - Final status of the LRA ([Cancelled][cancelled],
  [Closed][closed], [FailedToCancel][failedtocancel],
  [FailedToClose][failedtoclose])

```java
public void whenLRAFinishes(URI lraId, LRAStatus status) {
}
```

## Configuration

Optional configuration options:

| Key                                     | Type    | Default value                           | Description                                                                                                               |
|-----------------------------------------|---------|-----------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `mp.lra.coordinator.url`                | string  | `http://localhost:8070/lra-coordinator` | Url of coordinator.                                                                                                       |
| `mp.lra.coordinator.propagation.active` | boolean |                                         | Propagate LRA headers `LRA_HTTP_CONTEXT_HEADER` and `LRA_HTTP_PARENT_CONTEXT_HEADER` through non-LRA endpoints.           |
| `mp.lara.participant.url`               | string  |                                         | Url of the LRA enabled service overrides standard base uri, so coordinator can call load-balancer instead of the service. |
| `mp.lra.coordinator.timeout`            | string  |                                         | Timeout for synchronous communication with coordinator.                                                                   |
| `mp.lra.coordinator.timeout-unit`       | string  |                                         | Timeout unit for synchronous communication with coordinator.                                                              |

Example of LRA configuration:

<!--@mdc ::code-callout -->
```yaml
mp.lra:
  coordinator.url: http://localhost:8070/lra-coordinator <1>
  propagation.active: true <2>
  participant.url: https://coordinator.visible.host:443/awesomeapp <3>
```
1. Url of coordinator
2. Propagate LRA headers `LRA_HTTP_CONTEXT_HEADER` and
   `LRA_HTTP_PARENT_CONTEXT_HEADER` through non-LRA endpoints
3. Url of the LRA enabled service overrides standard base uri, so coordinator can
   call load-balancer instead of the service
<!--@mdc :: -->

For more information continue to [MicroProfile Long Running Actions
specification][microprofile-lon].

## Examples

The following example shows how a simple LRA participant starts and joins a
transaction after calling the '/start-example' resource. When startExample
method finishes successfully, close is reported to [coordinator](#coordinator)
and `/complete-example` endpoint is called by coordinator to confirm successful
closure of the LRA.

If an exception occurs during startExample method execution, coordinator
receives cancel call and `/compensate-example` is called by coordinator to
compensate for cancelled LRA transaction.

Example of simple LRA participant:

<!--@mdc ::code-callout{collapsed} -->
```java
@PUT
@LRA(LRA.Type.REQUIRES_NEW) // <1>
@Path("start-example")
public Response startExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId, //<2>
                             String data) {
    if (data.contains("BOOM")) {
        throw new RuntimeException("BOOM 💥"); // <3>
    }

    LOGGER.info("Data " + data + " processed 🏭");
    return Response.ok().build(); // <4>
}

@PUT
@Complete // <5>
@Path("complete-example")
public Response completeExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
    LOGGER.log(Level.INFO, "LRA ID: {0} completed 🎉", lraId);
    return LRAResponse.completed();
}

@PUT
@Compensate // <6>
@Path("compensate-example")
public Response compensateExample(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
    LOGGER.log(Level.SEVERE, "LRA ID: {0} compensated 🦺", lraId);
    return LRAResponse.compensated();
}
```
1. This JAX-RS PUT method will start new LRA transactions and join it before
   method body gets executed
2. LRA ID assigned by coordinator to this LRA transaction
3. When method execution finishes exceptionally, cancel signal for this
   particular LRA is sent to coordinator
4. When method execution finishes successfully, complete signal for this
   particular LRA is sent to coordinator
5. Method which will be called by coordinator when LRA is completed
6. Method which will be called by coordinator when LRA is canceled
<!--@mdc :: -->

## Testing

Testing of JAX-RS resources with LRA can be challenging as LRA participant
running in parallel with the test is needed.

Helidon provides test coordinator which can be started automatically with
additional socket on a random port within your own Helidon application. You only
need one extra test dependency to enable test coordinator in your
[@HelidonTest](testing/testing.md).

Dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.microprofile.lra</groupId>
  <artifactId>helidon-microprofile-lra-testing</artifactId>
  <scope>test</scope>
</dependency>
```

Considering that you have LRA enabled JAX-RS resource you want to test.

Example JAX-RS resource with LRA:

<!--@mdc ::code-collapse -->
```java
@ApplicationScoped
@Path("/test")
public class WithdrawResource {

    private final List<String> completedLras = new CopyOnWriteArrayList<>();
    private final List<String> cancelledLras = new CopyOnWriteArrayList<>();

    @PUT
    @Path("/withdraw")
    @LRA(LRA.Type.REQUIRES_NEW)
    public Response withdraw(
            @HeaderParam(LRA.LRA_HTTP_CONTEXT_HEADER) Optional<URI> lraId,
            String content) {
        if ("BOOM".equals(content)) {
            throw new IllegalArgumentException("BOOM");
        }
        return Response.ok().build();
    }

    @Complete
    public void complete(URI lraId) {
        completedLras.add(lraId.toString());
    }

    @Compensate
    public void rollback(URI lraId) {
        cancelledLras.add(lraId.toString());
    }

    public List<String> getCompletedLras() {
        return completedLras;
    }
}
```
<!--@mdc :: -->

Helidon test with enabled CDI discovery can look like this.

HelidonTest with LRA test support:

<!--@mdc ::code-callout{collapsed} -->
```java
@HelidonTest
//@AddBean(WithdrawResource.class) //<1>
@AddBean(TestLraCoordinator.class) //<2>
public class LraTest {

    @Inject
    private WithdrawResource withdrawTestResource;

    @Inject
    private TestLraCoordinator coordinator; //<3>

    @Inject
    private WebTarget target;

    @Test
    public void testLraComplete() {
        try (Response res = target
                .path("/test/withdraw")
                .request()
                .put(Entity.entity("test", MediaType.TEXT_PLAIN_TYPE))) {
            assertThat(res.getStatus(), is(200));
            String lraId = res.getHeaderString(LRA.LRA_HTTP_CONTEXT_HEADER);
            Lra lra = coordinator.lra(lraId); //<4>
            assertThat(lra.status(), is(LRAStatus.Closed)); //<5>
            assertThat(withdrawTestResource.getCompletedLras(), contains(lraId));
        }
    }
}
```
1. Resource is discovered automatically
2. Test coordinator needs to be added manually
3. Injecting test coordinator to access state of LRA managed by coordinator
   mid-test
4. Retrieving LRA managed by coordinator by LraId
5. Asserting LRA state in coordinator
<!--@mdc :: -->

LRA testing feature has the following default configuration:

- port: `0` - coordinator is started on random port(Helidon LRA participant is
  capable to discover test coordinator automatically)
- bind-address: `localhost` - bind address of the coordinator
- helidon.lra.coordinator.persistence: `false` - LRAs managed by test
  coordinator are not persisted
- helidon.lra.participant.use-build-time-index: `false` - Participant annotation
  inspection ignores Jandex index files created in build time, it helps to avoid
  issues with additional test resources

Testing LRA coordinator is started on additional named socket
`test-lra-coordinator` configured with default index `500`. Default index can be
changed with system property `helidon.lra.coordinator.test-socket.index`.

Example: `-Dhelidon.lra.coordinator.test-socket.index=20`.

HelidonTest override LRA test feature default settings:

<!--@mdc ::code-callout -->
```java
@HelidonTest
@AddBean(TestLraCoordinator.class)
@AddConfig(key = "server.sockets.500.port", value = "8070") //<1>
@AddConfig(key = "server.sockets.500.host", value = "custom.bind.name") //<2>
@AddConfig(key = "helidon.lra.coordinator.persistence", value = "true") //<3>
@AddConfig(key = "helidon.lra.participant.use-build-time-index", value = "true") //<4>
public class LraCustomConfigTest {
}
```
1. Start test LRA coordinator always on the same port 8070(default is random
   port)
2. Test LRA coordinator socket bind address (default is localhost)
3. Persist LRA managed by coordinator(default is false)
4. Use build time Jandex index(default is false)
<!--@mdc :: -->

When CDI bean auto-discovery is not desired, LRA and Config CDI extensions needs
to be added manually.

HelidonTest setup with disabled discovery:

```java
@HelidonTest
@DisableDiscovery
@AddJaxRs
@AddBean(TestLraCoordinator.class)
@AddExtension(LraCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@AddBean(WithdrawResource.class)
public class LraNoDiscoveryTest {
}
```

## Coordinator

Coordinator is a service that tracks all LRA transactions and calls the
`compensate` REST endpoints of the participants when the LRA transaction gets
cancelled or completes (in case it gets closed). In addition, participant also
keeps track of timeouts, retries participant calls, and assigns LRA ids.

Helidon LRA supports following coordinators:
- [MicroTx LRA coordinator][microtx-lra-coor]
- Helidon LRA coordinator
- [Narayana coordinator](https://narayana.io/lra).

### MicroTx Coordinator

Oracle Transaction Manager for Microservices - [MicroTx][microtx-lra-coor] is an
enterprise grade transaction manager for microservices, among other it manages
LRA transactions and is compatible with Narayana LRA clients.

MicroTx LRA coordinator is compatible with Narayana clients when
`narayanaLraCompatibilityMode` is on, you need to add another dependency to
enable Narayana client:

Dependency needed for using Helidon LRA with Narayana compatible coordinator:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.lra</groupId>
  <artifactId>helidon-lra-coordinator-narayana-client</artifactId>
</dependency>
```

Run MicroTx in Docker:

```shell [Terminal]
docker container run --name otmm -v "$(pwd)":/app/config \
-w /app/config -p 8080:8080/tcp --env CONFIG_FILE=tcs.yaml \
--add-host host.docker.internal:host-gateway -d tmm:<version>
```

To use MicroTx with Helidon LRA participant, `narayanaLraCompatibilityMode`
needs to be enabled.

Configure MicroTx for development:

<!--@mdc ::code-callout -->
```yaml
tmmAppName: tcs
tmmConfiguration:
  listenAddr: 0.0.0.0:8080
  internalAddr: http://host.docker.internal:8080
  externalUrl: http://lra-coordinator.acme.com:8080
  xaCoordinator:
    enabled: false
  lraCoordinator:
    enabled: true
  tccCoordinator:
    enabled: false
  storage:
    type: memory
  authentication:
    enabled: false
  authorization:
    enabled: false
  serveTLS:
    enabled: false
  narayanaLraCompatibilityMode:
    enabled: true #<1>
```
1. Enable Narayana compatibility mode
<!--@mdc :: -->

### Helidon Coordinator

> [!CAUTION]
> Test tool, usage in production is not advised.

Build and run Helidon LRA coordinator:

```shell [Terminal]
docker build -t helidon/lra-coordinator https://github.com/helidon-io/helidon.git#:lra/coordinator/server
docker run --name lra-coordinator --network="host" helidon/lra-coordinator
```

Helidon LRA coordinator is compatible with Narayana clients, you need to add a
dependency for Narayana client:

Dependency needed for using Helidon LRA with Narayana compatible coordinator:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.lra</groupId>
  <artifactId>helidon-lra-coordinator-narayana-client</artifactId>
</dependency>
```

### Narayana

[Narayana](https://narayana.io) is a transaction manager supporting LRA. To use
Narayana LRA coordinator with Helidon LRA client you need to add a dependency
for Narayana client:

Dependency needed for using Helidon LRA with Narayana coordinator:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.lra</groupId>
  <artifactId>helidon-lra-coordinator-narayana-client</artifactId>
</dependency>
```

The simplest way to run Narayana LRA coordinator locally:

Downloading and running Narayana LRA coordinator:

```shell [Terminal]
curl https://repo1.maven.org/maven2/org/jboss/narayana/rts/lra-coordinator-quarkus/5.11.1.Final/lra-coordinator-quarkus-5.11.1.Final-runner.jar \
-o narayana-coordinator.jar
java -Dquarkus.http.port=8070 -jar narayana-coordinator.jar
```

Narayana LRA coordinator is running by default under `lra-coordinator` context,
with port `8070` defined in the snippet above you need to configure your Helidon
LRA app as follows:
`mp.lra.coordinator.url=http://localhost:8070/lra-coordinator`

## Reference

- [MicroProfile LRA GitHub Repository][microprofile-lra]
- [MicroProfile Long Running Actions specification][microprofile-lon]
- [MicroProfile LRA Javadoc][microprofile-lra-2]
- [Helidon LRA Client Javadoc][helidon-lra-clie]
- [MicroTx - Oracle Transaction Manager for Microservices][microtx-lra-coor]

[microprofile-lon]: https://download.eclipse.org/microprofile/microprofile-lra-2.0/microprofile-lra-spec-2.0.html
[compensate]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/Compensate.html
[afterlra]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/AfterLRA.html
[lra-javadoc]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html
[value]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#value--
[required]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#REQUIRED
[requires-new]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#REQUIRES_NEW
[mandatory]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#MANDATORY
[supports]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#SUPPORTS
[not-supported]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#NOT_SUPPORTED
[never]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#NEVER
[nested]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.Type.html#NESTED
[timelimit]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#timeLimit--
[timeunit]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#timeUnit--
[end]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#end--
[cancelon]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#cancelOn--
[cancelonfamily]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#cancelOnFamily--
[lra-http-context]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#LRA_HTTP_CONTEXT_HEADER
[lra-http-parent]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#LRA_HTTP_PARENT_CONTEXT_HEADER
[lra-complete]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/Complete.html
[lra-forget]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/Forget.html
[lra-leave]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/Leave.html
[lra-status]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/Status.html
[participantstatu]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ParticipantStatus.html
[lra-http-ended-c]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/ws/rs/LRA.html#LRA_HTTP_ENDED_CONTEXT_HEADER
[lrastatus]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/LRAStatus.html
[cancelled]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/LRAStatus.html#Cancelled
[closed]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/LRAStatus.html#Closed
[failedtocancel]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/LRAStatus.html#FailedToCancel
[failedtoclose]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/annotation/LRAStatus.html#FailedToClose
[microtx-lra-coor]: https://docs.oracle.com/en/database/oracle/transaction-manager-for-microservices/index.html
[microprofile-lra]: https://github.com/eclipse/microprofile-lra
[microprofile-lra-2]: https://download.eclipse.org/microprofile/microprofile-lra-1.0-RC3/apidocs/org/eclipse/microprofile/lra/
[helidon-lra-clie]: https://helidon.io/docs/v4/apidocs/io.helidon.lra.coordinator.client/module-summary.html
