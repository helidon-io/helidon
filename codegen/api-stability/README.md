API Stability
----

Deprecation is left to the `javac` to handle.

Suppression in code:

`@SuppressWarnings("helidon:api:preview")`
`@SuppressWarnings("helidon:api:incubating")`
`@SuppressWarnings("helidon:api:private")`

Suppression through annotation processor flags:

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

`-Ahelidon.api.private=fail` - default
`-Ahelidon.api.private=warn`
`-Ahelidon.api.private=ignore`
