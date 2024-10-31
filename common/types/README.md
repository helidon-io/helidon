Types
----

Language types abstraction used during annotation processing (and instead of reflection if needed at runtime).
As types are required for annotation processors, they cannot be generated using annotation processors for builder.
To work around this cyclic dependency problem, there is a module `builder/tests/common-types` that contains the correct
blueprints and static methods to generate the code required for this module.
If a change is needed, generate the code using that module, and copy all the types (blueprint, static methods, and generated classes) here.


# TypeName

TypeName represents a type (class, interface, record). 
Its `equals` and `hashCode` methods ignore generics (i.e. `Supplier<String>` and `Supplier<Integer>` are equal and have
the same hashCode - type erasure like behavior).

If there is a requirement to compare based on generic declaration, use `ResolvedType`.

## Handling of generics

Depending on how a type name is created, the generic information may be available:

1. `TypeName.create(SomeType.class)` - contains "raw" information - package name, class name
2. `TypeName.create(Type)` - when created from a `io.helidon.common.GenericType`, or `java.lang.reflect.ParameterizedType`, the type will contain type arguments (i.e. for `GenericType<List<String>>` there will be a type `List` with type argument `String`)
3. Through codegen factories (annotation processing, classpath scanning, reflection) - see below 

TypeName is created for:

1. Type declaration (`class MyClass...` - regardless of generics) - raw type name, accessible through `TypeInfo.rawType()`, or `TypeInfo.typeName()` if the type info was created for a raw type
2. Type declaration (`class MyClass<X extends CharSequence>`) - with all declared type parameters, accessible through `TypeInfo.declaredType()`
3. A type usage (`implements Supplier<X>`) for the example above - with all type parameter information, accessible through `TypeInfo.typeName()` on type info of superclass or implemented interface
4. Wildcard usage (`List<? super X>`) in parameter arguments

Raw type:
```yaml
package: "com.example"
class-name: "MyClass"
```

Declared type (`MyClass<X extends CharSequence & Serializable>`) :
```yaml
package: "com.example"
class-name: "MyClass"
type-parameters: # list of type names 
  - class-name: "X"
    generic: true
    upper-bounds: # list of type names - if not present, `Object` is expected (for ? extends X)
      - class-name: "CharSequence"
      - class-name: "Serializable"
    lower-bounds: # list of type names - if not present, no lower bounds (for ? super X)
    
```
Type usage (`implements Supplier<X>`):
```yaml
package: "java.util.function"
class-name: "Supplier"
type-parameters: # list of type names
  - class-name: "X"
    generic: true
    upper-bounds:
      - class-name: "CharSequence"
      - class-name: "Serializable"
```

Type usage (`implements Supplier<CharSequence>`):
```yaml
package: "java.util.function"
class-name: "Supplier"
type-parameters: # list of type names
  - class-name: "CharSequence"
```

Wildcard usage (`List<? extends CharSequence>`):
```yaml
package: "java.util"
class-name: "List"
type-parameters: # list of type names
  - class-name: "CharSequence"
    package-name: "java.lang"
    generic: true
    wildcard: true
    upper-bounds:
      - class-name: "CharSequence"
```


Wildcard usage (`List<? super String>`):
```yaml
package: "java.util"
class-name: "List"
type-parameters: # list of type names
  - class-name: "?"
    generic: true
    wildcard: true
    lower-bounds:
      - class-name: "String"
```

