<p align="center">
    <img src="../etc/images/Primary_logo_blue.png" height="180">
</p>

# Helidon Long Running Actions

LRA compliant implementation.
https://github.com/eclipse/microprofile-lra/blob/master/spec/src/main/asciidoc/microprofile-lra-spec.adoc

Additional features beyond what is mentioned in the spec include...
    - Support for messaging
    - DB persistence of tx/LRA records and (in the case of messaging) config. 
        - Currently relational/SQL only but will provide JSON support in the future if need be.
        - Will potentially provide flatfile or other persistence support in the future if need be as well.
    - HA and k8s considerations

# Messaging 

Via the MicroProfile Messaging specification where @Incoming is a marker that mimics the behavior of JAX-RS path as closely as possible

@Incoming
@LRA

As MP Messaging does not allow for dynamic/runtime configuration of connectors nor channels, there are two  requirements...
1. The user must preconfigured the coordinator with connectors that are needed to communicate with clients. 
   This coupling is required in order to avoid the need to send sensitive connection information from the client to the coordinator.
   It is not possible for an application or subsystem to access connection factories created by the MicroProfile Messaging directly (ie from outside the @Incoming/@Outgoing),
   and so this connector configuration is simply used in order to reuse the MP config convention rather than introduce a new one just for LRA.
2. The user will configure the channel(s) used for complete, compensate, status, afterLRA, ... at their choice of granularity  (eg at the queue, selector, etc. level)
3. The LRA client (ie the the LRA client subsystem, not the application) will implicitly send the microservices channel information for LRA endpoints (complete, compensate, status, afterLRA, ...)
   and this channel configuration will be persisted at runtime by the coordinator.
4. Channel config persisted by the coordinator will be automatically purge after a period (1 hour default) of inactivity for that channel.
    
todo provide example(s)...

# Other config

mp.lra.propagation.active=true defined it spec to determine if context is propagated (this is orthogonal to actual tx type annotations)
todo insure security provider takes this config into account to propagate headers

todo A config property for both coordinator and participant side that allows hostnames used to be overridden to use service names.
As we are only implementing the coordinator side, that may be our only option for now.
This would apply to REST only and not messaging (at least at this time since this would actually logically be part of the messaging layer).

# DB tx logging

User must configure a SQL datasource named "lraloggingdatasource" (todo better name) that will be used to persist LRA records and any participants' messaging channels used.
No additional config necessary.

# HA and k8s considerations

todo 

# Design aspects

The Narayana implementation is used for REST client/participant support while the messaging client/participant support and coordinator are Helidon implementations


# Remaining work

- unit tests
- finish hooking AQ into msg interceptors
- finish kafka and hook into interceptors
- timeout measurements (uses seconds only now)
- setup tck to run on helidon client
- add config for mp.lra.propagation.active=true (applies to both rest and messaging), server names, ...
- add db logging and recovery with same back in
- add logic for tck recovery call rather than current sleep
- stress test with recovery testing
- tracing
- metrics 
- health
- proper logging
- other clients