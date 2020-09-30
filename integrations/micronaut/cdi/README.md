CDI integration with Micronaut
---

#Introduction

Goals of this integration:

- Allow invocation of Micronaut interceptors on CDI beans
- Allow injection of Micronaut beans into CDI beans

Non-goals:

- Injection of CDI beans into Micronaut beans
- No support for request scope in Micronaut

#Design

What I need to do
 - find all interceptors handled by micronaut
 - find all beans handled by micronaut
 - add interceptor bindings to the CDI bean
 - prepare all execution metadata for that interceptor (executable method)
 - add producers for Micronaut based beans

#Usage

The following must be done to use this integration:

- The dependency `` must be on classpath for annotation processing
- 