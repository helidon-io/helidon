<!--@frontmatter
description: "About Helidon MP"
navigation:
  icon: i-lucide-lightbulb
-->
# Introduction

Helidon is a Java framework for building cloud-native applications. It provides
APIs and runtime services for creating standalone applications that are easy to
develop, deploy, observe, and scale.

Helidon MP adds a MicroProfile programming model on top of Helidon. It supports
MicroProfile and Jakarta EE APIs, uses annotation-driven components with
dependency injection, and gives developers a familiar way to build REST
services, configuration, security, health checks, metrics, and other
microservice features.

Helidon MP applications run as standalone JVM processes, are powered by the
Helidon WebServer, and use Java 21 virtual threads throughout the WebServer.

> [!NOTE]
> You get the productivity of MicroProfile and Jakarta EE APIs with the low
> overhead of a Helidon runtime.

## Strengths

<!-- @icon-list -->
- MicroProfile APIs for common microservice concerns. <!-- @icon i-lucide-layers-3 -->
- Jakarta EE annotations for REST services and data access. <!-- @icon i-lucide-braces -->
- Dependency injection for composing application components. <!-- @icon i-lucide-git-branch -->
- Standalone JVM applications with no application server dependency. <!-- @icon i-lucide-box -->
- High-concurrency services that benefit from virtual threads. <!-- @icon i-lucide-gauge -->
- Helidon WebServer runtime with cloud-native observability. <!-- @icon i-lucide-activity -->

## First Look

A Helidon MP application can expose a REST endpoint using Jakarta RESTful Web
Services annotations:

```java {1,3}
@Path("hello")
public class HelloWorld {
    @GET
    public String hello() {
        return "Hello World";
    }
}
```
