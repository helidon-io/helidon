Service Inject Codegen
---------------

# Supported annotations

| Annotation                         | Description                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------|
| @Injection.Inject                  | Type with an injection point is a per lookup service unless it has scope annotation |
| @Injection.Describe                | Generates a service descriptor for a contract without a service implementation      |
| @Injection.PerInstance             | A service instantiated based on another service                                     |
| @Injection.Scope (meta-annotation) | Scoped service                                                                      | 

# Supported options

Options can be configured as annotation processor options, when running via annotation processor.

| Option                                          | Description                                                                                                                                                              |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `helidon.registry.autoAddNonContractInterfaces` | If set to `true`, all implemented interfaces and super types are considered a contract; by default, `@Service.Contract` or `@Service.ExternalContracts` must be in place |
| `helidon.inject.interceptionStrategy`           | How to handle generation of interceptor invokers (NONE, EXPLICIT, ALL_RUNTIME, ALL_RETAINED)                                                                             |
| `helidon.inject.scopeMetaAnnotations`           | Set of annotations that mark scopes that are not Helidon scopes                                                                                                          |
