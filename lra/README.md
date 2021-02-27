# Helidon Long Running Actions

Microprofile LRA compliant implementation...
https://github.com/eclipse/microprofile-lra/blob/master/spec/src/main/asciidoc/microprofile-lra-spec.adoc

Additional features beyond what is mentioned in the spec include...
    - Support for messaging with AQ and Kafka
    - DB persistence of tx/LRA records and (in the case of messaging) config. 
        - Currently relational/SQL only but can provide JSON support in the future if desired.
        - Will potentially provide flatfile or other persistence support in the future if need be as well.
    - HA and k8s considerations

The Narayana implementation is used for REST client/participant support while the messaging client/participant support and coordinator are Helidon implementations.

# Messaging 

The support for MicroProfile Messaging specification mimics the behavior of JAX-RS path as closely as possible 
For example, any aspect that applies to LRA related JAX-RS headers applies to messaging properties.
When annotated with @LRA the @Incoming annotation will adhere to the characteristics of that LRA annotation type and 
the @Outgoing messages will propagate any LRA, etc..  

Despite being in the "ws.rs" package, the org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER value
will be reused as the key for AQ message properties and Kafka headers that will be used for LRA functionality.

The only signatures that will be supported in the initial release are those that take and/or return Message objects 
Ie, no Publisher/PublisherBuilder nor Subscriber/SubscriberBuilder nor CompletionStage nor raw payloads
Inversely put, only these signatures will be supported...
public Message messagingMethod(Message msg) 
public void messagingMethod(Message msg) 
public Message messagingMethod(AqMessage<T> msg) 
public void messagingMethod(AqMessage<T> msg) 
public Message messagingMethod(KafkaMessage msg) 
public void messagingMethod(KafkaMessage msg) 

As MP (Messaging) does not allow for dynamic/runtime/mutable configuration of connectors nor channels, there are some related requirements on the user...
1. The user must preconfigured the coordinator with connectors that are needed to communicate with clients. 
   This coupling is required in order to avoid the need to send sensitive connection information from the client to the coordinator.
   It is not possible for an application or subsystem to access connection factories created by the MicroProfile Messaging directly (ie from outside the @Incoming/@Outgoing),
   and so this connector configuration is simply used in order to reuse the MP config convention rather than introduce a new one just for LRA.
2. The user may configure the channel(s) used for complete, compensate, status, afterLRA, ... at their choice of granularity  (eg at the queue, selector, etc. level)
3. The LRA client (ie the the LRA client subsystem, not the application) will implicitly send the microservices channel information for LRA endpoints (complete, compensate, status, afterLRA, ...)
   and this channel configuration will be persisted at runtime by the coordinator.
4. Channel config persisted by the coordinator will be automatically purge after a period (1 day by default) of inactivity for that channel.
    
The "HELIDONLRAOPERATION" key is sent on all messages so that customer applications can use selectors, filters, ...

todo Add samples from example app here...


# Other config

`mp.lra.propagation.active=true` defined in the LRA spec is used to determine if context is propagated with outgoing requests. 
It's actually unclear in the spec what the default value should be. We will default to false.  This applies to both REST and messaging.

`lra.coordinator.url` is not defined in the LRA spec but is the property used by Narayana client in order to locate the 
 LRA Coordinator and so we will reuse it in Helidon. This applies to only REST.

# DB tx logging

User must configure a SQL datasource named "lraloggingdatasource" (todo better name) that will be used to persist LRA records and any participants' messaging channels used.
No additional config necessary.

# HA and k8s considerations

Regarding HA of tx records, by virtual of logging to DB, we inherit this from the underlying DB architecture. 
Likewise by supporting a Kubernetes runtime environment, we inherit HA as far as service discovery, routing etc. (see config section)







# Design notes and Futures (notes above this point are intended for doc (once they are refined) and below this point are internal)

Will not be provided in SE unless there is future demand.

Non-java clients will be future work and will start with .NET

`lra.participant.url` is not defined in the LRA spec nor in Narayana. 
 TBD determined whether we can actually provide this since we re-use the Narayana client. 
 It does not appear that we could reuse server.port + server.host for this.
 This is being provided in particular for Kubernetes environments where services are referenced by service name rather than implicitly.
 When the client/participant joins the LRA, it will provide this url as it's endpoint(s) location.
 The same principle holds for referencing the coordinator using `lra.coordinator.url`. This applies to only REST
 
 
# Remaining work

- startup order - messages received before LRA hooks are registered
- unit testing, re-establish tck test run (original setup has changed), HA, k8s, etc. testing, stress test with recovery testing - continuous
- for messaging: pass outgoing/reply channel info during join so that "reply" suffix naming is not mandatory
- propagation/mp.lra.propagation.active
- verify leave 
- verify nonjaxrs endpoints, may need portable extension 
- add db logging and recovery with same back in and log messaging config
- make rest calls async 
- better UUID
- timeout measurements (uses seconds only now)
- setup tck to run on helidon client, add logic for tck recovery call rather than current sleep
- add config/logic for mp.lra.propagation.active=true for messaging, server names, ...
- tracing
- metrics 
- health
- break out messagingclient and coordinator modules for each vendor (ie aq and kafka pluggable) 
- internalize rest filters (ie eliminate need for explicitly registering in app)
- Validate Incoming and Outgoing methods. Only signatures that take or receive Message or payload objects are supported. 
  Ie, no Publisher/PublisherBuilder nor Subscriber/SubscriberBuilder
- Find a way to have rest and messaging clients coexist in same MS (currently the rest client process/validation process prohibits this)
- Insure security provider takes into account mp.lra.propagation.active to propagate headers 
