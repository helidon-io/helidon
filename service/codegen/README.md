Service Codegen
---------------

# Supported annotations

| Annotation        | Processed by     | Description                         |
|-------------------|------------------|-------------------------------------|
| @Service.Provider | ServiceExtension | Generates a core service descriptor |


# Supported options
Options can be configured as annotation processor options, when running via annotation processor.

| Option                                          | Used by          | Description                                                                                                                                                              |
|-------------------------------------------------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `helidon.registry.autoAddNonContractInterfaces` | ServiceExtension | If set to `true`, all implemented interfaces and super types are considered a contract; by default, `@Service.Contract` or `@Service.ExternalContracts` must be in place |