Codegen
----

Code generation and code processing tools of Helidon.

We see the following three environments that are used for code processing:

1. Annotation processors
2. Classpath scanning
3. Reflection based in a running VM

In Helidon, we do as much as possible using annotation processing and source code generation. 
For the cases where we need to analyze external libraries, or arbitrary code, we use classpath scanning (such as from a Maven plugin or command line tool).
Reflection can only be used in Helidon Microprofile, or in selected modules that are intentionally reflection based (this should be currently limited to Config object mapping module).


# Modules

This top level module contains the following modules:

- `helidon-codegen` - API and SPI and utilities that are shared between possible environments
- `helidon-codegen-apt` - implementations specific to annotation processing
- `helidon-codegen-scan` - implementations specific to classpath scanning
- `class-model` - class code generation abstraction, that provides builders to create a new source file
- `compiler` - wrapper around Java compiler that is running within the current VM
- `helidon-copyright` - Helidon specific implementation of copyright handler, used by Helidon project itself to generate sources

## Codegen abstraction (module `helidon-codegen`)

Codegen provides types that each code generation implementation can code against, without the need to hard code against
annotation processing or classpath scanning.

Main entry point is the `CodegenContext` that provides:

- Current module info (if available) - this is a read-only representation of a module info, to validate `provides` etc.
- `CodegenFiler` - filer abstraction, to generate source files and resources
- `CodegenLogger` - logger abstraction, with implementation for annotation processor `Messager`, Maven `Log` and `System.Logger`
- `CodegenScope` - to provide information on the scope we are processing (expecting production or test)
- possibility to obtain `TypeInfo` (backed by appropriate `TypeInfoFactoryBase`)
- access to `ElementMapper`, `TypeMapper`, and `AnnotationMapper` used in those factories
- `CodegenOptions` - configuration options provided either from Maven plugin, Annotation processing options, or command line arguments

### Tools

- `CodegenUtil` - methods useful when generating code (such as capitalization of first letter, constant name from method name etc.)
- `CopyrightHandler` - API and SPI to generate correct copyright statements
- `ElementInfoPredicates` - predicates to filter `TypedElementInfo`, such as only getting public, static etc. elements
- `GeneratedAnnotationHandler` - API and SPI to generate correct `@Generated` annotation
- `ModuleInfoSourceParser` - to parse source code of module-info.java
- `TypesCodeGen` - tool to generate source code to create instances of common types

## Class model (module `helidon-codegen-class-model`)

Class model provides APIs to construct a class in memory, and then write it out (using for example `CodegenFiler`) as source file.

Start with `ClassModel.builder()`.

