# Generic Mapping Proposal (Continued)

Provide an API to map an arbitrary Java type to another arbitrary Java type and use it in Helidon.

## Proposal

New API classes and behavior:

- `ValueProvider` - a named provider of types `Value`s (similar to a config node) 
- `Value` - a named value that has methods of `Optional` + a mapping method return typed `Value` (similar to `ConfigValue`)
    The name is useful for errors (e.g. parsing exception), as it should read similar to 
    "Failed to map QueryParam(request-count) to Integer"
- built in mappers for common types


Value provider can either use the shared `MapperManager` accessible through `MapperManager.shared()` (and configurable by user)
or an explicit mapper manager (if desired so by a component).

- `Config` should extends `ValueProvider` 
- `ConfigValue` should extend `Value`
- HTTP `Parameters` should have a new method `ValueProvider get(String name)`
  (this will enable typed access to headers, query parameters, form parameters) 
- HTTP `Path` currently has method `String param(String name)` - this should either be changed to 
    `ValueProvider param(String name)`, or a new method should be created (such as `ValueProvider parameter(String))`)
    for backward compatibility
- Built in mappers should allow modification of format through Helidon Context (using a custom classifier)
- mapping of types that are compatible (such as String -> CharSequence) should work even without a mapper
- mapping of primitive types should use mappers for their boxed types