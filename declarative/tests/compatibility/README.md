<!--
Copyright (c) 2026 Oracle and/or its affiliates.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Declarative binary compatibility

These tests exercise declarative types compiled with Helidon 4 on the current
Helidon runtime.

`legacy-4-module` deliberately has no reactor parent. It imports the released
Helidon 4 dependency BOM and uses that same release's annotation processors,
compiling both its sources and generated classes for Java 21. Keep its
`helidon.compatibility.version` pinned to the Helidon 4 release under test.

`app` consumes the compiled legacy module without recompiling its sources or
regenerating its declarative classes. It inherits the current reactor's dependency
management and generates an application binding using the current service plugin.
The running application therefore uses current Helidon libraries with the legacy
classes and service descriptors.

The tests exercise HTTP endpoints and clients, fault tolerance, validation,
scheduling, metrics, tracing, CORS, and WebSocket endpoints and clients. Assertions
check current runtime behavior, including expected fault-tolerance exceptions;
they do not require current observability output to retain Helidon 4 formatting.

Additional cases exercise asynchronous fault tolerance, generated object validators
and return-value constraints, computed client headers, optional query parameters,
void and generic REST results, generated JSON converters and builders, and service
lifecycle, qualifiers, factories, per-lookup scope, and event delivery.

Data is outside this fixture's scope. Declarative gRPC, GraphQL, and OpenAPI
generation added after Helidon 4.5.4 cannot be tested using this legacy compiler.

The reactor includes these modules through the `tests` profile.
