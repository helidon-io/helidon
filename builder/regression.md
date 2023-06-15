Builder regressions (intentional)

- no support for abstract classes
- no support for nullable - if not optional, then required
- no support for add to map that is part of a map
- cannot customize implementation types (always ArrayList, LinkedHashSet, LinkedHashMap)
- extending an annotation is not supported, use delegation instead of inheritance


Todos
- add support to validate allowedValues from ConfiguredOption
  - `MyConfigBeanBluprint` in tests
- add support for adding default methods to builder (e.g. when having third party superclass)
  - `HelidonOpenApiConfigBlueprint` in tests
