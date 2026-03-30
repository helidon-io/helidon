API Stability
----

API Stability allows Helidon maintainers to mark APIs with one of the stability annotations, to declare its expected use
and state.

Helidon:
- all `public` top-level types in Helidon production modules are annotated with one `Api` stability annotation
- methods, constructors, and nested types may also carry their own stability annotations
- they may also be annotated with `@Api.Since("version")` to announce the version that the stability state changed
- in Helidon, this is verified by an API Stability Enforcer annotation processor, that must be configured for `default-compile`

Users:
- API Stability annotation processor should be present in user's projects in compiler configuration
- user's build will fail if they use `Internal` or `Incubating` APIs, unless they suppress warnings, or configure the annotation processor to ignore/warn
- user's build will print warnings if they use `Preview` or `Deprecated` APIs (also can be suppressed)

# Stability states and transition

## States:

- `Incubating` - "playground" for new features and APIs
- `Preview` - features and APIs we plan as stable, to get feedback from community
- `Stable` - stable APIs
- `Deprecated` - deprecated APIs, will be removed in a major release of Helidon
- `Internal` - internal APIs, do not use

Initial state: Any state mentioned above - all `public` top-level types must be annotated with one of the above annotations;
methods, constructors, and nested types may also be annotated

Final state: API is removed from project

## Transitions:

- `Incubating` (can transition in any release of Helidon, including patch versions)
  - `Preview` - next stage, we plan to release this feature eventually as stable
  - `Stable` - we gathered enough feedback and consider this to be a stable API
  - `Deprecated` - we deprecate the API (exceptional use, in most cases we would just remove the API)
  - removed - we remove the API
- `Preview` (can transition in a minor release of Helidon):
  - `Stable` - we gathered enough feedback and consider this to be a stable feature 
  - `Deprecated` - we deprecate the API and will remove it in a future major version
- `Stable` (can transition in a minor release of Helidon):
  - `Deprecated` - the API is now deprecated, and will be removed in a future major version
- `Deprecated`:
  - `Stable` - can transition in any release of Helidon
  - removed - can transition only in a major release of Helidon; final state, API is gone from the project
  - any other state - can transition only in a major release of Helidon 

**IMPORTANT NOTE:**

Only APIs marked as `Internal` and `Incubating` may be removed within a major release of Helidon (minor, dot, patch versions);
Only APIs marked as `Deprecated` may be removed when a new major release of Helidon is done;
Other public APIs cannot be removed;

# Suppression in code

Suppressions can be applied on the using element or one of its containing elements, such as a method, field, top-level
type, package, or module.

`@SuppressWarnings("all")`
`@SuppressWarnings("helidon:api")`
`@SuppressWarnings("helidon:api:preview")`
`@SuppressWarnings("helidon:api:incubating")`
`@SuppressWarnings("helidon:api:internal")`
`@SuppressWarnings("helidon:api:deprecated")`
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
