<!--@frontmatter
description: "About Helidon"
navigation:
  icon: i-lucide-lightbulb
-->
# Introduction

Helidon is a Java framework for building cloud-native applications. It provides
APIs and runtime services for creating standalone applications that are easy to
develop, deploy, observe, and scale.

> [!NOTE]
> Helidon Core is the foundational framework previously called Helidon SE.
> This documentation calls it Helidon unless it must distinguish it from
> another Helidon project. Some CLI and archetype identifiers still use the
> `se` flavor name.

Helidon applications run as standalone JVM processes, are powered by the
Helidon WebServer, and give you a direct programming model for defining routes,
services, configuration, security, observability, and other application
behavior.

Helidon 27 requires Java 26 and uses virtual threads throughout the WebServer,
so applications can use a simple blocking style without tying up platform
threads for each request.

> [!NOTE]
> You write straightforward Java code while Helidon handles high-concurrency
> request processing on virtual threads.

## Strengths

<!-- @icon-list -->
- Full control over application structure and behavior. <!-- @icon
  i-lucide-settings-2 -->
- Transparent programming model with explicit APIs. <!-- @icon i-lucide-code -->
- High-concurrency services that benefit from virtual threads. <!-- @icon
  i-lucide-gauge -->
- Small runtime footprint. <!-- @icon i-lucide-feather -->
- Few third-party dependencies. <!-- @icon i-lucide-package -->
- Lightweight HTTP services and microservices. <!-- @icon i-lucide-server -->

## First Look

A Helidon application can start an HTTP server and define a route directly in
Java:

```java {3,4}
WebServer.builder()
    .addRouting(HttpRouting.builder()
        .get("/greet", (req, res)
            -> res.send("Hello World!")))
    .build()
    .start();
```
