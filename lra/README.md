<p align="center">
    <img src="../etc/images/Primary_logo_blue.png" height="180">
</p>

# Helidon Long Running Actions

LRA compliant implementation.
https://github.com/eclipse/microprofile-lra/blob/master/spec/src/main/asciidoc/microprofile-lra-spec.adoc

Additional features beyond what is mentioned in the spec include...
    - Support for messaging
    - DB tx logging

# Messaging

The initial implementation puts requirements on the configuration convention used by the coordinator and the participants.
These requirements may be removed in the future by allowing participants to provide destination information to their coordinator(s) though it will likely still be a requirement that coordinators be preconfigured with connectors for messaging systems used by participants.
The requirement is simply that the connectors and channel names must match.
No additional config necessary.
The user may choose the granularity of the channel as far as queue, selector, etc.

# DB tx logging

If a datasource named "lraloggingdatasource" (or some such) exists, log to db.
No additional config necessary.