# Generic Mapping Proposal

This change was merged into master.
Module: `common/mapper`
Since: Helidon 1.2.2
Please consult javadocs for threadContext documentation.

Provide an API to map an arbitrary Java type to another arbitrary Java type.

## Proposal

The API consist of the following classes (may be changed due to implementation details):
- `MapperManager` - the entry point to mapping of types
- `Mapper` - a class capable of converting one type to another
- `MapperProvider` - SPI class to support providers loaded through Java Service loader or configured through a builder
- `MapperException` - `RuntimeException` thrown when a mapping is missing or the mapping itself failed

This API should be also added to `Config` as an additional source of mappings.

### Mapper Manager

Mapping provides tools to map a type to another type.

The types may be
 - a java class (`String.class`)
 - a generic type (`io.helidon.common.GenericType` for any java type, such as `Supplier<String>`)
 
Mappings can be provided either through Java Service loader, or through
 explicitly configured providers using a builder.
 
The mapping function gets either a pair of `Class` objects, or a pair of `GenericType` objects
 that defines the `SOURCE` and the `TARGET` of the mapping.
 
For `Class` parameters, lookup is done as follows:
1. Ask each mapping provider if such a pair of classes is supported, if yes, use the first mapper
2. Convert each class to `GenericType` and ask each mapping provider if supported, if yes, use the first mapper

For `GenericType` parameters, lookup is done as follows:
1. Ask each mapping provider if such a pair of types is supported, if yes, use the first mapper
2. If both generic types represent a `Class`, convert each to a `Class` and 
    ask each mapping provider if supported, if yes, use the first mapper
    
The results are cached (so lookup for a defined pair is done only once).
In case no mapper is found, the result should be cached as well.

The main API class is `MapperManager`.
```java
/**
 * Map from source to target.
 *
 * @param source     object to map
 * @param sourceType type of the source object (to locate the mapper)
 * @param targetType type of the target object (to locate the mapper)
 * @return result of the mapping
 * @throws io.helidon.common.mapping.MapperException in case the mapper was not found or failed
 */
<SOURCE, TARGET> TARGET map(SOURCE source, GenericType<SOURCE> sourceType, GenericType<TARGET> targetType);

/**
 * Map from source to target.
 *
 * @param source     object to map
 * @param sourceType class of the source object (to locate the mapper)
 * @param targetType class of teh target object (to locate the mapper)
 * @return result of the mapping
 * @throws io.helidon.common.mapping.MapperException in case the mapper was not found or failed
 */
<SOURCE, TARGET> TARGET map(SOURCE source, Class<SOURCE> sourceType, Class<TARGET> targetType);
```
 
`MapperManager` can be created using the usual `Builder`:
```java
/**
 * Replace the service loader with the one provided.
 * @param serviceLoader fully configured service loader to be used to load mapper providers
 * @return updated builder instance
 */
Builder mapperProviders(HelidonServiceLoader<MapperProvider> serviceLoader);

/**
 * Add a new {@link io.helidon.common.mapping.spi.MapperProvider} to the list of providers loaded from
 *  system service loader.
 *  
 * @param provider prioritized mapper provider to use
 * @return updated builder instance
 */
Builder addMapperProvider(MapperProvider provider);

/**
 * Add a mapper to the list of mapper. 
 * 
 * @param mapper the mapper to map source instances to target instances
 * @param sourceType class of the source instance
 * @param targetType class of the target instance
 * @param <S> type of source
 * @param <T> type of target
 * @return updated builder instance
 */
<S, T> Builder addMapper(Mapper<S, T> mapper, Class<S> sourceType, Class<T> targetType);

/**
 * Add a mapper to the list of mapper.
 *
 * @param mapper the mapper to map source instances to target instances
 * @param sourceType generic type of the source instance
 * @param targetType generic type of the target instance
 * @param <S> type of source
 * @param <T> type of target
 * @return updated builder instance
 */
<S, T> Builder addMapper(Mapper<S, T> mapper, GenericType<S> sourceType, GenericType<T> targetType);
```

### Mapper implementation
`Mapper` is the class doing the actual work of mapping one type to another.
Mappers are either provided by user when creating the `MapperManager` or obtained
from `MapperProvider` services.

`Mapper`:
```java
/**
 * Map an instance of source type to an instance of target type.
 *
 * @param source object to map
 * @return result of the mapping
 */
TARGET map(SOURCE source);
```
 
The `Mapper` provides unidirectional mapping - there can be a mapper
from `String` to `Long` and another one from `Long` to `String`.

The knowledge of the `SOURCE` and `TARGET` types comes from the developer
providing these mappers - there is no possibility to register a `Mapper`
without explicitly defining the types it is registered for. 
 
### Mapper provider implementations 
 
Service implementation can use `@Priority` or implement `io.helidon.common.Prioritized`
 to define its priority (lower number will be used first)
 
Service implementation requires implementation for `Class` types,
    may also implement support for `GenericType`.
    
The main interface for SPI is `MapperProvider`:
```java
/**
 * Find a mapper that is capable of mapping from source to target classes.
 *
 * @param sourceClass class of the source
 * @param targetClass class of the target
 * @param <SOURCE> type of the source
 * @param <TARGET> type of the target
 * @return a mapper that is capable of mapping (or converting) sources to targets
 */
<SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass);

/**
 * Find a mapper that is capable of mapping from source to target types.
 * This method supports mapping to/from types that contain generics.
 *
 * @param sourceType generic type of the source
 * @param targetType generic type of the target
 * @param <SOURCE> type of the source
 * @param <TARGET> type of the target
 * @return a mapper that is capable of mapping (or converting) sources to targets
 */
default <SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(GenericType<SOURCE> sourceType, GenericType<TARGET> targetType) {
    return Optional.empty();
}
``` 

### Built-in mapper
We may provide (as a separate library?) a set of built-in generic mappers, especially for primitive types.
For each pair define here, we should have
 - bi-directional mapping between the types
 - mapping of `List` <-> `List` 
 - mapping of `List` <-> `Set`
 - mapping of `Set` <-> `Set`
 - mapping of `Array` <-> `List`
 - mapping of `Stream` <-> `Stream`
 
Suggested supported mapping pairs (primitive types should be supported as well):
 - `String` to the same types as defined in `ConfigMappers.initBuiltInMappers` except for `Map` and `Properties` 
 - `BigInteger`, `Long` - should throw an exception if too big
 - `BigInteger`, `Integer` - should throw an exception if too big
 - `BigInteger`, `BigDecimal` 
 - `Long`, `Integer` - should throw an exception if too big
 - `Integer`, `Short` - should throw an exception if too big
 
Other reasonable mapping pairs can be added.
 
## Examples

### Using the MapperManager
The following example creates the `MapperManager` from Java Service loader
```java
// creates a mapper manager from system service loader
MapperManager mm = MapperManager.create();

// this will work if a String to Long mapper is configured
Long longValue = mm.map("1094444", String.class, Long.class);

// this will work if a List<String> to List<Long> mapper is configured
List<String> stringList = CollectionsHelper.listOf("140", "145");
List<Long> longList = mm.map(stringList, new GenericType<List<String>>(){}, new GenericType<List<Long>>(){});
```

### Creating a MapperProvider
The following example creates a service implementation that supports mapping to/from `String` and `Long` and a mapping
from `List<String>` to `List<Long>`:

```java
private static final Class<Long> LONG_CLASS = Long.class;
private static final GenericType<List<Long>> LONG_LIST = new GenericType<List<Long>>() { };
private static final Class<String> STRING_CLASS = String.class;
private static final GenericType<List<String>> STRING_LIST = new GenericType<List<String>>() { };

@Override
public <SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(Class<SOURCE> sourceClass, Class<TARGET> targetClass) {
    if (sourceClass.equals(LONG_CLASS) && targetClass.equals(STRING_CLASS)) {
        return Optional.of((Mapper<SOURCE, TARGET>) longToString());
    }
    if (sourceClass.equals(STRING_CLASS) && targetClass.equals(LONG_CLASS)) {
        return Optional.of((Mapper<SOURCE, TARGET>) stringToLong());
    }
    return Optional.empty();
}

@Override
public <SOURCE, TARGET> Optional<Mapper<SOURCE, TARGET>> mapper(GenericType<SOURCE> sourceType,
                                                                GenericType<TARGET> targetType) {
    if (sourceType.equals(STRING_LIST) && targetType.equals(LONG_LIST)) {
        return Optional.of((Mapper<SOURCE, TARGET>) stringListToLongList());
    }
    return Optional.empty();
}

private Mapper<List<String>, List<Long>> stringListToLongList() {
    return strings -> strings.stream()
            .map(Long::parseLong)
            .collect(Collectors.toList());
}

private Mapper<String, Long> stringToLong() {
    return Long::parseLong;
}

private Mapper<Long, String> longToString() {
    return String::valueOf;
}
```