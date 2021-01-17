<p align="center">
    <img src="../etc/images/Primary_logo_blue.png" height="180">
</p>

# Helidon Long Running Actions

LRA compliant implementation.
https://github.com/eclipse/microprofile-lra/blob/master/spec/src/main/asciidoc/microprofile-lra-spec.adoc

Additional features beyond what is mentioned in the spec include...
    - Support for messaging
    - DB tx logging
    - HA and k8s considerations

# Messaging

The initial implementation puts requirements on the configuration convention used by the coordinator and the participants.
These requirements may be removed in the future by allowing participants to provide destination information to their coordinator(s) though it will likely still be a requirement that coordinators be preconfigured with connectors for messaging systems used by participants.
The requirement is simply that the connectors and channel names must match.
No additional config necessary.
The user may choose the granularity of the channel as far as queue, selector, etc.

todo provide example...

# Other config

mp.lra.propagation.active=true defined it spec to determine if context is propagated (this is orthogonal to actual tx type annotations)

A config property for both coordinator and participant side that allows hostnames used to be overridden to use service names.
As we are only implementing the coordinator side, that may be our only option for now.
This would apply to REST only and not messaging (at least at this time since this would actually logically be part of the messaging layer).

# DB tx logging

If a datasource named "lraloggingdatasource" (or some such) exists, log to db.
No additional config necessary.

# HA and k8s considerations

todo 

# Design aspects

The Narayana implementation is used for REST client/participant support while the messaging client/participant support and coordinator are Helidon implementations