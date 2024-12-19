Service Codegen
---------------

# Supported annotations

| Annotation           | Description                                                                         |
|----------------------|-------------------------------------------------------------------------------------|
| @Service.Provider    | Generates a core service descriptor                                                 |
| @Service.Inject      | Type with an injection point is a per lookup service unless it has scope annotation |
| @Service.Describe    | Generates a service descriptor for a contract without a service implementation      |
| @Service.PerInstance | A service instantiated based on another service                                     |
| @Service.Scope       | Scoped service (meta-annotation)                                                    |

# Supported options

Options can be configured as annotation processor options, when running via annotation processor.

| Option                                          | Default    | Description                                                                                                                                                              |
|-------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `helidon.registry.autoAddNonContractInterfaces` | `true`     | If set to `true`, all implemented interfaces and super types are considered a contract; by default, `@Service.Contract` or `@Service.ExternalContracts` must be in place |
| `helidon.registry.interceptionStrategy`         | `EXPLICIT` | How to handle generation of interceptor invokers (NONE, EXPLICIT, ALL_RUNTIME, ALL_RETAINED)                                                                             |
| `helidon.registry.scopeMetaAnnotations`         | N/A        | Set of annotations that mark scopes that are not Helidon scopes                                                                                                          