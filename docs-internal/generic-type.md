# Helidon Generic Type Support Proposal

Provide a utility class to express generic type literals and provide API guidance
 for the Helidon APIs (config, webserver etc).

## Problematic

Currently (v0.10.5) entity mapping is done with `as` methods that accept a `Class<T>` argument:

```java
request.content().as(Dog.class).thenAccept((Dog dog) -> { /* ... */ });
config.get("doggy").as(Dog.class).ifPresent((Dog dog) -> { /* ... */ });
```

Generic type mapping is in most cases needed for collections, `List` and `Map`.

This can be solved by having more variants of the `as` methods:

```java
config.get("doggies").asList(Dog.class).ifPresent((List<Dog> dogs) -> { /* ... */ });
```

This model enables the most common use-cases of generic type mapping, however it
 limits the generic type mapping to just `List<T>` and `Map<T>` ; there is simply no
 way of mapping a custom parameterized type (e.g. `Pet<Dog>`).

In Java there is no way to express a generic type as a literal. I.e `List<Dog>.class` is not valid.
This is a known problem that has been solved by many popular Java frameworks.

E.g.
- `javax.ws.rs.GenericType`
- `javax.enterprise.util.TypeLiteral`

## Proposal

Make a copy of `javax.ws.rs.GenericType` under `io.helidon.common.GenericType`.

Overload the existing `as(Class<T> type)` methods with `as(GenericType<T> type)`.

Retain or add the shorthand `asList(Class<T> type)` to satisfy the most common use-cases.
 Remove every other shorthand if any.

### Examples

Get the request content as `List<JsonObject>`:

```java
request.content().asList(JsonObject.class)
    .thenAccept((List<JsonObject> jsons) -> { /* ... */ });
```

Get the request content as `Pet<Dog>`:

```java
request.content().as(new GenericType<Pet<Dog>>(){})
    .thenAccept((Pet<Dog> pet) ->  { /* ... */ });
```

## Notes

### java.lang.reflect.Type

Changing `as(Class<T> type)` for `as(Type type)` could potentially work as a single
 signature for both use-cases.

```java
as(Dog.class)
as(new GenericType<Pet<Dog>>(){}.getType())
```

However since `Type` is not parameterized we would loose the ability to provide
 a fluent API. The example below does not compile:

```java
// does not compile
request.content().as(new GenericType<Pet<Dog>>(){}.getType())
    .thenAccept((Pet<Dog> pet) ->  { /* ... */ });
```