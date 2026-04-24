# Validation

## Overview

Validation checks values against constraints.

There are two ways to use validation features in Helidon SE:

1.  Have a `@Validation.Validated` annotated type and use a `TypeValidator` service to validate it
2.  Invoke the constraint checks directly using `Validators` static methods

The feature fit with our [Helidon Declarative](../se/injection/declarative.md#Dec-Validation), which is a preview feature.

## Maven Coordinates

To enable Validation, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

```xml
<dependencies>
    <dependency>
        <groupId>io.helidon.validation</groupId>
        <artifactId>helidon-validation</artifactId>    
    </dependency>
    <dependency>
        <groupId>io.helidon.webserver</groupId>
        <artifactId>helidon-webserver-validation</artifactId> 
    </dependency>
</dependencies>
```

- Helidon validation dependency.
- WebServer integration with validation, to provide correct HTTP status on validation failures

## Usage

### Validated type

A type annotated with `@Validation.Validated` will have validation code generated.

*Example of a validated type*

```java
@Validation.Validated
record MyType(@Validation.String.Pattern(".*valid.*") @Validation.NotNull String validString,
              @Validation.Integer.Min(42) int validInt) {
}
```

Such code can then be validated using a service `TypeValidation`:

*Example of validating a type*

```java
TypeValidation validator = Services.get(TypeValidation.class);
var validationResponse = validator.validate(MyType.class, new MyType("valid", 43));
```

Or using the check method(s) that throw a `ValidationException`:

*Example of validating a type that throws an exception*

```java
TypeValidation validator = Services.get(TypeValidation.class);
// throws a ValidationException if the object is invalid
validator.check(MyType.class, new MyType("valid", 43));
```

The following annotation processing setup must be done to generate the code:

```xml
<plugins>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <annotationProcessorPaths>
                <path>
                    <groupId>io.helidon.bundles</groupId>
                    <artifactId>helidon-bundles-apt</artifactId>
                    <version>${helidon.version}</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
    </plugin>
</plugins>
```

### Validate Object

An object can be validated using one of the built-in constraints through methods on

- [`io.helidon.validation.Validators`](/apidocs/io.helidon.validation/io/helidon/validation/Validators.html)

*Example of validating an object using a built-in constraint*

```java
var validationResponse = Validators.validateNotNull(anInstance);
```

*Example of validating an object using a built-in constraint that throws an exception*

```java
// throws a ValidationException if the object is invalid
Validators.checkNotNull(anInstance);
```

The low-level approach allows use of any constraint (including custom constraints) through programmatic API. Note that an instance of the validator can be cached as long as it is used for the same type and constraint configuration.

The first approach gives as a validation response:

*Example of validating an object using any constraint*

```java
var provider = Services.getNamed(ConstraintValidatorProvider.class, Validation.String.Pattern.class.getName()); 
var context = ValidationContext.create(MyType.class); 
var validator = provider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class, ".*valid.*")); 
context.check(validator, anInstance); 
var response = context.response(); 
```

- Get the constraint validation provider from the registry, named by the annotation it handles
- Create a new validation context (can be used to validate multiple constraints)
- Create a new validator for a specific type and annotation
- Check the constraint using the validator and the provided instance (instance must match the type provided in previous step)
- Get a validation response from the context

And the second throws an exception if validation failed:

*Example of validating an object using any constraint that throws an exception*

```java
var provider = Services.getNamed(ConstraintValidatorProvider.class, Validation.String.Pattern.class.getName()); 
var context = ValidationContext.create(MyType.class); 
var validator = provider.create(TypeNames.STRING, Annotation.create(Validation.String.Pattern.class, ".*valid.*")); 
context.check(validator, anInstance); 
context.throwOnFailure(); 
```

- Get the constraint validation provider from the registry, named by the annotation it handles
- Create a new validation context (can be used to validate multiple constraints)
- Create a new validator for a specific type and annotation
- Check the constraint using the validator and the provided instance (instance must match the type provided in previous step)
- Throw and exception in case any of the checks failed
