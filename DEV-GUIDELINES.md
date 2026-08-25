# Development Guidelines

This document defines the rules and best practices followed by project Helidon. Follow these rules when contributing,
refactoring existing code, and reviewing changes made by others.

Some rules are enforced by Checkstyle; others are checked during code review.

<a id="chapter-0"></a>
## 0. Rule governance

<a id="rule-0-1"></a>**Rule 0.1 — Document exceptions.** Exceptions to these rules must be documented clearly.

<a id="rule-0-2"></a>**Rule 0.2 — Preserve identifiers.** Rule identifiers and their explicit anchors are permanent. Do
not renumber or reuse them when rule names, text, or chapter organization changes.

<a id="rule-0-3"></a>**Rule 0.3 — Preserve removed rules.** When removing a rule, keep its identifier and anchor in place
with the text `Removed. This identifier is reserved.` An optional note may identify replacement rules.

<a id="rule-0-4"></a>**Rule 0.4 — Treat repository checks as guidelines.** A failure from the repository-configured
Checkstyle or copyright validation is a guideline violation even when this document does not enumerate the underlying
requirement.

<a id="general-coding-rules"></a>
<a id="chapter-1"></a>
## 1. General coding rules

<a id="rule-1-1"></a>**Rule 1.1 — Use unchecked throwables in APIs.** API exceptions should extend
`RuntimeException`.

- <a id="rule-1-1-1"></a>**Rule 1.1.1.** Never use `RuntimeException` directly. Create a module-appropriate descendant or
  use an existing exception declared in the module.
- <a id="rule-1-1-2"></a>**Rule 1.1.2.** Do not declare a checked exception unless an implemented or extended contract
  requires it, such as `java.io.Closeable`.
- <a id="rule-1-1-3"></a>**Rule 1.1.3.** Existing runtime exceptions such as `NoSuchElementException` and
  `IllegalStateException` may be used when they fit the problem.

<a id="rule-1-2"></a>**Rule 1.2 — Keep Helidon public APIs and SPIs non-null.** Helidon public APIs and SPIs must not
accept or return `null`.

- <a id="rule-1-2-1"></a>**Rule 1.2.1.** Refactor a public API or SPI method that accepts `null`.
    - <a id="rule-1-2-1-1"></a>**Rule 1.2.1.1.** For a setter, provide an explicit operation that removes the value, such as
      `host(String)` and `unsetHost()`.
    - <a id="rule-1-2-1-2"></a>**Rule 1.2.1.2.** For a small number of combinations, up to two, provide an overload without
      the optional parameter.
    - <a id="rule-1-2-1-3"></a>**Rule 1.2.1.3.** For more combinations, use a parameter object configured through a builder.
    - <a id="rule-1-2-1-4"></a>**Rule 1.2.1.4.** Never use `java.util.Optional` as a public API or SPI parameter type.
- <a id="rule-1-2-2"></a>**Rule 1.2.2.** Validate non-null public API and SPI parameters at the boundary, normally using
  `Objects.requireNonNull(...)` or an existing precondition helper.
- <a id="rule-1-2-3"></a>**Rule 1.2.3.** If a public API or SPI method would return `null`, use the most appropriate
  non-null representation, such as an overload, documented default, empty collection, no-op implementation, or
  `java.util.Optional`.

<a id="rule-1-3"></a>**Rule 1.3 — Do not introduce public records.** Do not introduce records as Helidon public API or
SPI types; use regular classes or interfaces instead.

<a id="rule-1-4"></a>**Rule 1.4 — Avoid intrinsic monitor locking.** Do not use `synchronized` in Helidon-owned Java
code unless a third-party contract specifically requires monitor locking.

- <a id="rule-1-4-1"></a>**Rule 1.4.1.** Use explicit locks or atomic types instead.

<a id="rule-1-5"></a>**Rule 1.5 — Use unnamed bindings.** Use `_` for deliberately unused local, lambda, exception,
loop, or pattern bindings.

<a id="rule-1-6"></a>**Rule 1.6 — Prefer enhanced switches.** Prefer switch expressions and arrow labels for new
switch code.

- <a id="rule-1-6-1"></a>**Rule 1.6.1.** Make switches exhaustive for enums and sealed hierarchies.

<a id="chapter-2"></a>
## 2. Class member order

<a id="rule-2-1"></a>**Rule 2.1 — Order class members consistently.** Declare class members in the following order,
which matches the repository [IntelliJ IDEA code style](etc/codestyle/idea-code-style.xml):

1. Static final fields, ordered by visibility: public, protected, package-private, then private.
2. Non-final static fields, ordered by visibility: public, protected, package-private, then private.
3. Static initializer blocks.
4. Final instance fields, ordered by visibility: public, protected, package-private, then private.
5. Non-final instance fields, ordered by visibility: public, protected, package-private, then private.
6. Any remaining fields.
7. Instance initializer blocks.
8. Constructors.
9. Non-private static methods.
10. Non-private instance methods.
11. Private static methods.
12. Private instance methods.
13. Nested enums.
14. Nested interfaces.
15. Static nested classes.
16. Non-static nested classes.

Within one of these groups, no additional ordering by name or visibility is required unless the group explicitly defines
a visibility order.

<a id="chapter-3"></a>
## 3. Imports and Javadoc

<a id="rule-3-1"></a>**Rule 3.1 — Use imported simple names outside Javadoc.** Outside Javadoc, use an imported simple
type name unless a name conflict or Java syntax requires a fully qualified name. The `uses` and `provides` directives in
`module-info.java` are an explicit exception and must use fully qualified service types under [Rule 9.4](#rule-9-4).

<a id="rule-3-2"></a>**Rule 3.2 — Do not import types only for Javadoc.** Use the fully qualified name in Javadoc
instead.

<a id="package-and-module-structure"></a>
<a id="chapter-4"></a>
## 4. Package and module structure

<a id="rule-4-1"></a>**Rule 4.1 — Use a flat package structure.**

- <a id="rule-4-1-1"></a>**Rule 4.1.1.** Do not introduce `internal`, `private`, or equivalent hidden package layers.
- <a id="rule-4-1-2"></a>**Rule 4.1.2.** Each Maven and JPMS module has a single implementation package.
    - <a id="rule-4-1-2-1"></a>**Rule 4.1.2.1.** Each Maven JAR module used outside testing is also a JPMS module.
- <a id="rule-4-1-3"></a>**Rule 4.1.3.** A module may have an additional `spi` package for service-provider interfaces.
- <a id="rule-4-1-4"></a>**Rule 4.1.4.** Do not relax production member visibility solely to make it accessible to tests.
- <a id="rule-4-1-5"></a>**Rule 4.1.5.** Treat every public class and public method as maintained Helidon API.
- <a id="rule-4-1-6"></a>**Rule 4.1.6.** Do not rely on JPMS to enforce visibility.
- <a id="rule-4-1-7"></a>**Rule 4.1.7.** If a set of classes needs a separate package, consider moving that concern to a
  separate module.

For example, the ABAC security providers are separate modules instead of separate packages in one module. This helps keep
different concerns separate.

<a id="rule-4-2"></a>**Rule 4.2 — Keep directory, module, and package naming connected.**

- <a id="rule-4-2-1"></a>**Rule 4.2.1.** Use the module name as the module directory name.
    - <a id="rule-4-2-1-1"></a>**Rule 4.2.1.1.** A POM-packaging aggregator is a project module.
- <a id="rule-4-2-2"></a>**Rule 4.2.2 — Maven coordinates.**
    - <a id="rule-4-2-2-1"></a>**Rule 4.2.2.1.** Use a group ID in the `io.helidon.${project_module}` hierarchy, such as
      `io.helidon.reactive.webserver` or `io.helidon.config`.
    - <a id="rule-4-2-2-2"></a>**Rule 4.2.2.2.** Use artifact ID `helidon-${module_name}`; project modules use
      `helidon-${module_name}-project`.
    - <a id="rule-4-2-2-3"></a>**Rule 4.2.2.3.** Always inherit the version.
- <a id="rule-4-2-3"></a>**Rule 4.2.3.** Use package name
  `io.helidon.${project_module}.${module_name}`, such as `io.helidon.security` or
  `io.helidon.security.providers.common`.

<a id="configuration-and-programmatic-api"></a>
<a id="chapter-5"></a>
## 5. Configuration and programmatic API

<a id="rule-5-1"></a>**Rule 5.1 — Provide programmatic configuration.** Everything configurable must also be
available programmatically through builders.

- <a id="rule-5-1-1"></a>**Rule 5.1.1.** Components that can only be code-generated and must use inversion of control are
  exempt.

<a id="rule-5-2"></a>**Rule 5.2 — Provide configuration for builder capabilities.** Everything available through
builders should also be available through configuration, except when that would require runtime reflection.

<a id="rule-5-3"></a>**Rule 5.3 — Accept component-node configuration.** A `Config` parameter should represent the
node containing the component's configuration, such as `ServerConfiguration` in WebServer.

<a id="rule-5-4"></a>**Rule 5.4 — Follow configuration-key conventions.**

- <a id="rule-5-4-1"></a>**Rule 5.4.1.** Use lowercase words separated by dashes, such as `token-endpoint-uri`, not
  `tokenEndpointUri`.
- <a id="rule-5-4-2"></a>**Rule 5.4.2.** Keys may be nested, such as `outbound-token.name` and
  `outbound-token.algorithm`.
- <a id="rule-5-4-3"></a>**Rule 5.4.3.** Classify component properties as required, defaulted, or optional.
    - <a id="rule-5-4-3-1"></a>**Rule 5.4.3.1 — Required.** The component fails to build when the property is missing.
    - <a id="rule-5-4-3-2"></a>**Rule 5.4.3.2 — Defaulted.** The property has a well-defined and documented default value.
    - <a id="rule-5-4-3-3"></a>**Rule 5.4.3.3 — Optional.** The component has well-defined and documented behavior when the
      property is absent, such as disabling tracing when no endpoint is configured.

See [Builders](#chapter-8) and the [helidon-builder documentation](builder/README.md).

<a id="getters-and-setters"></a>
<a id="chapter-6"></a>
## 6. Getters and setters

<a id="rule-6-1"></a>**Rule 6.1 — Omit accessor verbs.** Property accessors do not use a verb. For a `port` property,
use `port(int newPort)` and `int port()`.

<a id="rule-6-2"></a>**Rule 6.2 — Prefer verb-free boolean accessors.** Use names such as `authenticate(boolean)` and
`boolean authenticate()` by default.

- <a id="rule-6-2-1"></a>**Rule 6.2.1.** Use a verb such as `isAuthenticated()` or `shouldAuthenticate()` when needed for
  clarity.

Example: [io.helidon.security.providers.oidc.common.OidcConfig](security/providers/oidc-common/src/main/java/io/helidon/security/providers/oidc/common/OidcConfig.java).

<a id="fluent-api"></a>
<a id="chapter-7"></a>
## 7. Fluent API

<a id="rule-7-1"></a>**Rule 7.1 — Use fluent APIs where applicable.**

- <a id="rule-7-1-1"></a>**Rule 7.1.1.** Use fluent APIs in builders.
- <a id="rule-7-1-2"></a>**Rule 7.1.2.** Use fluent APIs for control methods, such as
  `Server server = Server.create().start()`.

<a id="builders"></a>
<a id="chapter-8"></a>
## 8. Builders

See [helidon-builder](builder/README.md) for module details and
[helidon-builder-api](builder/api/README.md) for API and naming rules.

<a id="rule-8-1"></a>**Rule 8.1 — Use Blueprint.** Use `@Prototype.Blueprint` for all builders and prototype APIs.

- <a id="rule-8-1-1"></a>**Rule 8.1.1.** Consult the project architect about an exception. The outcome may be processor
  support, redesign, or a documented exception.

<a id="rule-8-2"></a>**Rule 8.2 — Keep implementation details out of Blueprint interfaces.** Do not declare
constants, fields, or implementation helpers in a `@Prototype.Blueprint` interface because its members and nested types
become public API.

- <a id="rule-8-2-1"></a>**Rule 8.2.1.** Put such implementation details in a package-private support class in the same
  package.

<a id="rule-8-3"></a>**Rule 8.3 — Use builders for parameterized construction.** Use builders to create instances
that need construction parameters.

- <a id="rule-8-3-1"></a>**Rule 8.3.1.** Do not use public constructors for Helidon public API classes.
- <a id="rule-8-3-2"></a>**Rule 8.3.2 — Constructor exceptions.**
    - <a id="rule-8-3-2-1"></a>**Rule 8.3.2.1.** A public constructor may be required by a documented external API, SPI, or
      integration contract.
    - <a id="rule-8-3-2-2"></a>**Rule 8.3.2.2.** Exception types may follow normal exception constructor conventions.

<a id="rule-8-4"></a>**Rule 8.4 — Follow builder-backed type conventions.** A class or interface using a builder must
follow these rules:

- <a id="rule-8-4-1"></a>**Rule 8.4.1.** Use a hidden constructor, private or protected, preserving the option to switch
  to an interface.
- <a id="rule-8-4-2"></a>**Rule 8.4.2.** Provide `public static Builder builder()`.
- <a id="rule-8-4-3"></a>**Rule 8.4.3.** Declare every field obtained from the builder as `final`.
- <a id="rule-8-4-4"></a>**Rule 8.4.4.** A builder may provide `builder(...)` overloads for mandatory or very common
  parameters.
    - <a id="rule-8-4-4-1"></a>**Rule 8.4.4.1.** Provide at most two `builder(...)` methods per class.
- <a id="rule-8-4-5"></a>**Rule 8.4.5.** A no-argument `create()` factory, when present, must delegate to
  `builder().build()`.
- <a id="rule-8-4-6"></a>**Rule 8.4.6.** A `create(io.helidon.config.Config)` factory, when present, must delegate to
  `builder().config(config).build()`.
- <a id="rule-8-4-7"></a>**Rule 8.4.7.** Other factories for predefined instances must use the builder internally.
- <a id="rule-8-4-8"></a>**Rule 8.4.8.** Name a nested builder class `Builder`.
    - <a id="rule-8-4-8-1"></a>**Rule 8.4.8.1.** A top-level builder name must identify the built type, such as
      `FooBarBuilder`.

Example: [io.helidon.common.mapper.MappersConfigBlueprint](common/mapper/src/main/java/io/helidon/common/mapper/MappersConfigBlueprint.java).

<a id="jpms"></a>
<a id="chapter-9"></a>
## 9. JPMS

<a id="rule-9-1"></a>**Rule 9.1 — Describe released Java modules.** Each released Java module has a `module-info.java`.

<a id="rule-9-2"></a>**Rule 9.2 — Declare provided services only in `module-info.java`.** A Maven plugin generates
`META-INF/services`; checked-in service descriptors in released modules fail the build.

<a id="rule-9-3"></a>**Rule 9.3 — Document modules.** Add Javadoc to `module-info.java`.

<a id="rule-9-4"></a>**Rule 9.4 — Qualify service types.** Use fully qualified class names for provided and used
services.

<a id="testing"></a>
<a id="chapter-10"></a>
## 10. Testing

<a id="rule-10-1"></a>**Rule 10.1 — Test observable behavior.** Tests must verify observable behavior rather than
implementation details.

- <a id="rule-10-1-1"></a>**Rule 10.1.1.** Structural assertions are appropriate only when the structure itself is the
  contract under test.

<a id="rule-10-2"></a>**Rule 10.2 — Do not reflect into private state.** Tests must not use reflection to access private
implementation state.

<a id="rule-10-3"></a>**Rule 10.3 — Keep test scaffolding out of production code.** Do not add members or classes to
production code solely to support tests, except in modules whose published purpose is test support.

<a id="rule-10-4"></a>**Rule 10.4 — Use JUnit 5 with Hamcrest assertions.**

<a id="rule-10-5"></a>**Rule 10.5 — Use the Hamcrest assertion approach.** Hamcrest assertions provide clearer failure
diagnostics than boolean JUnit assertions.

- <a id="rule-10-5-1"></a>**Rule 10.5.1.** Use `assertThat(actualValue, matcher(expectedValue))`.
- <a id="rule-10-5-2"></a>**Rule 10.5.2.** Use the main static import
  `org.hamcrest.MatcherAssert.assertThat`.

Common matchers from `org.hamcrest.CoreMatchers` include:

- `is()` for equality, such as `assertThat(value, is(true))`
- `notNullValue()`
- `nullValue()`
- `startsWith(String)`
- `endsWith(String)`

<a id="rule-10-6"></a>**Rule 10.6 — Limit JUnit assertion exceptions.** The following JUnit 5 assertions may be used:

- <a id="rule-10-6-1"></a>**Rule 10.6.1.** `assertAll` for multiple assertions where more than one may fail.
- <a id="rule-10-6-2"></a>**Rule 10.6.2.** `assertThrows` for an expected exception.

For example, this assertion provides little diagnostic information:

```java
assertTrue(ex.getMessage().contains("'" + config.key() + "'"));
```

Its failure reports only `Expected: true, actual: false`. A Hamcrest assertion provides the expected and actual text:

```java
assertThat(ex.getMessage(), containsString("'" + config.key() + "'"));
```

```text
Expected: a string containing "'list-1'"
but: was "Requested value for configuration key 'list-1.1' is not present in the configuration."
```

<a id="maven"></a>
<a id="chapter-11"></a>
## 11. Maven

<a id="rule-11-1"></a>**Rule 11.1 — Manage all third-party versions.**

- <a id="rule-11-1-1"></a>**Rule 11.1.1.** Manage plugin versions.
- <a id="rule-11-1-2"></a>**Rule 11.1.2.** Manage dependency versions in `dependencies/pom.xml`.

<a id="rule-11-2"></a>**Rule 11.2 — Follow the third-party update process.** Adding or upgrading a third-party
dependency requires an internal process and can delay merging.

<a id="rule-11-3"></a>**Rule 11.3 — Omit managed Helidon versions.** Do not specify a version when referencing another
Helidon module; `bom/pom.xml` manages those versions.

<a id="rule-11-4"></a>**Rule 11.4 — Name every module POM.** Every module `pom.xml` defines a `name`.

- <a id="rule-11-4-1"></a>**Rule 11.4.1.** Name a project module `Helidon ${module_name} Project`.
- <a id="rule-11-4-2"></a>**Rule 11.4.2.** Name a Java module `Helidon ${module_name}`.
- <a id="rule-11-4-3"></a>**Rule 11.4.3.** Keep the name short enough for readable reactor output.
- <a id="rule-11-4-4"></a>**Rule 11.4.4.** Treat the value as a name, not a sentence.

<a id="rule-11-5"></a>**Rule 11.5 — Include user-facing Java modules in the BOM.** Add every Java module expected to
be used by users to [bom/pom.xml](bom/pom.xml).

<a id="rule-11-6"></a>**Rule 11.6 — Preserve direct module choice when adding bundles.**

- <a id="rule-11-6-1"></a>**Rule 11.6.1.** Avoid bundling third-party dependencies that may add unexpected libraries,
  such as the Google Login provider.
- <a id="rule-11-6-2"></a>**Rule 11.6.2.** Put SE bundles under `bundles/` with group ID `io.helidon.bundles`.
- <a id="rule-11-6-3"></a>**Rule 11.6.3.** Keep bundle directories scoped to active Helidon bundle families.
- <a id="rule-11-6-4"></a>**Rule 11.6.4.** Bundles are for end users, not internal use.

<a id="rule-11-7"></a>**Rule 11.7 — Use `provided` scope for third-party specification APIs.** The exception is a
module that implements the specification.

- <a id="rule-11-7-1"></a>**Rule 11.7.1.** Analyze module dependencies and choose the matching Maven scope and
  `module-info.java` declaration.
- <a id="rule-11-7-2"></a>**Rule 11.7.2 — Map Maven scopes to JPMS declarations.**
    - <a id="rule-11-7-2-1"></a>**Rule 11.7.2.1.** `compile` maps to `requires`.
    - <a id="rule-11-7-2-2"></a>**Rule 11.7.2.2.** `optional` maps to `requires static`.
    - <a id="rule-11-7-2-3"></a>**Rule 11.7.2.3.** `provided` maps to `requires`.
    - <a id="rule-11-7-2-4"></a>**Rule 11.7.2.4.** `runtime` maps to `requires` or `requires static` according to the runtime
      requirement. `requires static` only works when another used module requires the dependency; otherwise it is absent
      from the module path even when present on the class path.
- <a id="rule-11-7-3"></a>**Rule 11.7.3.** Use `transitive` for dependencies that appear in the module's public API.

<a id="rule-11-8"></a>**Rule 11.8 — Scope Helidon-module dependencies carefully.** Pay particular attention to
optional integrations.
