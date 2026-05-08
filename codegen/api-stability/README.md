API Stability
----

API Stability lets Helidon declare the contract of a public API and lets build-time processors enforce that contract.

Helidon production modules:
- all `public` top-level types are annotated with exactly one stability annotation: `@Api.Internal`,
  `@Api.Incubating`, `@Api.Preview`, or `@Api.Stable`
- methods, constructors, and nested types may also carry their own stability annotations
  - nested declarations may keep the enclosing type stability or declare a lower one, in the order
    `Stable > Preview > Incubating > Internal`
- `@Api.Since("version")` can document when the current contract became effective
- deprecation is separate from stability; use standard `@Deprecated` together with the relevant stability annotation
- in Helidon, this is verified by the API Stability Enforcer annotation processor, configured for `default-compile`

Users:
- the API Stability annotation processor should be present in compiler configuration
- in Helidon 27+ / `main`, using `Internal` or `Incubating` APIs fails the build by default
- in Helidon 27+ / `main`, using `Preview` or `@Deprecated` APIs emits warnings by default
- the Helidon 4.x backport is intentionally different and warns by default

# Stability contracts

- `Internal` - Helidon implementation detail, not for external use; may change or disappear at any time
- `Incubating` - exploratory API; not production ready; may change or be removed in any release
- `Preview` - supported but still evolving API; may change between minor releases, but removal still goes through
  deprecation
- `Stable` - supported API; backward compatible within the current major version
- `@Deprecated` - deprecation marker, orthogonal to stability, used to signal planned removal

Only `Internal` and `Incubating` APIs may disappear inside a major release line. `Preview` and `Stable` APIs are not
removed without first being deprecated.

# Suppression in code

Suppressions can be applied on the using element or one of its containing elements, such as a method, field, top-level
type, package, or module.

`@SuppressWarnings("all")`
`@SuppressWarnings("helidon:api")`
`@SuppressWarnings("helidon:api:preview")`
`@SuppressWarnings("helidon:api:incubating")`
`@SuppressWarnings("helidon:api:internal")`
`@SuppressWarnings("deprecation")`

# Annotation processor options

Control through annotation processor flags:

For all stability levels:
`-Ahelidon.api=default` - use defaults of each stability level (default)
`-Ahelidon.api=fail` - fail for any violation
`-Ahelidon.api=warn` - warn for any violation
`-Ahelidon.api=ignore` - ignore all violations

For a specific stability level:
`-Ahelidon.api.preview=fail` 
`-Ahelidon.api.preview=warn` - default
`-Ahelidon.api.preview=ignore`

`-Ahelidon.api.incubating=fail` - default
`-Ahelidon.api.incubating=warn`
`-Ahelidon.api.incubating=ignore`

`-Ahelidon.api.internal=fail` - default
`-Ahelidon.api.internal=warn`
`-Ahelidon.api.internal=ignore`

`-Ahelidon.api.deprecated=fail`
`-Ahelidon.api.deprecated=warn` - default
`-Ahelidon.api.deprecated=ignore`
