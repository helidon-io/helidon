Helidon 3.0
---

# Jakarta packages

## MicroProfile 5.0

Specs missing an RC release:

- MP Reactive Stream Operators (https://github.com/eclipse/microprofile-reactive-streams-operators/issues/169)
- MP Reactive Messaging (https://github.com/eclipse/microprofile-reactive-messaging/issues/137)
- MP GraphQL (https://github.com/eclipse/microprofile-graphql/issues/386)
- MP LRA (https://github.com/eclipse/microprofile-lra/issues/351)

Implementations:
- Jersey not yet in central, I have snapshot based on RC (wrong version of jax-rs)
  - based on 3.1.0 of JAX-RS which is not in central

## Other

grpc generates sources with `@javax.annotation.Generated`
 - work around using `javax.annotation` dependency
 - requested support from Aleks Seovic and stack overflow

# Java 17

## Spotbugs
- We need at least release 4.4.2, as `java.lang.Class` is considered mutable and fails our build.

# SE

## Routing

Resources

