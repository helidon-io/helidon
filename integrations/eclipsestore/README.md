# Eclipse Store integration with Helidon

This projects adds [Eclipse Store](https://https://eclipsestore.io/) support to Helidon.

[Eclipse Store](https://eclipsestore.io/) is the successor of [Microstream](https://microstream.one), both of which are integrated into Helidon as
extensions now, enabling smooth transition.

The official [Eclipse Store documentation](https://docs.eclipsestore.io) can be found here.

## helidon-integrations-eclipsestore

Adds basic support for Eclipse Store

### Prerequisites

Use the following maven dependency

```
<dependency>
	<groupId>io.helidon.integrations.eclipsestore</groupId>
	<artifactId>helidon-integrations-eclipsestore</artifactId>
</dependency>
```

### API

Use the EmbeddedStorageManagerBuilder to create a Eclipse Store instance:

```
EmbeddedStorageManager embeddedStorageManager = EmbeddedStorageManagerBuilder
		.builder()
		.build();
```

Configuration can either be done by the builders methods or by supplying a helidon configuration node

```
Config config = Config.create();

EmbeddedStorageManager embeddedStorageManager = EmbeddedStorageManagerBuilder
	.builder()
	.config(config)
	.build();
```

for a list of all possible properties
see [Eclipse Store configuration properties](https://docs.eclipsestore.io/manual/storage/configuration/properties.html)

### CDI extension for Eclipse Store

the example below shows how to create a Eclipse Store instance using a provided configuration.

```
private EmbeddedStorageManager storage;

@Inject
public YourConstructor(@EclipseStoreStorage(configNode = "org.eclipse.store.storage.greetings")EmbeddedStorageManager storage) {
		super();
		this.storage = storage;
}
```

## helidon-integrations-eclipsestore-cache

Adds basic support for the Eclipse Store JCache implementation

### Prerequisites

Use the following maven dependency

```
<dependency>         
	<groupId>io.helidon.integrations.eclipsestore</groupId>
	<artifactId>helidon-integrations-eclipsestore-cache</artifactId>
</dependency>
```

### API

Use the CacheBuilder to create Eclipse Store JCache instance:

Create a CacheConfiguration first

```
CacheConfiguration<Integer, String> cacheConfig = EclipseStoreCacheConfigurationBuilder
	.builder(config.get("cache"), Integer.class, String.class).build();
```

Then build the cache

```
Cache<Integer, String> cache = CacheBuilder.builder(cacheConfig, Integer.class, String.class).build("myCache");
```

Configuration can either be done by the EclipseStoreCacheConfigurationBuilder or by supplying a helidon configuration node

```
Config config = Config.create();

Cache<Integer, String> cache = CacheBuilder.create("myCache", config, Integer.class, String.class);

```

for a list of all possible properties
see [Eclipse Store Cache configuration properties](https://docs.eclipsestore.io)

### CDI extension for Eclipse Store

the example below shows how to create a Eclipse Store Cache instance using a provided configuration.

```
private Cache<Integer, String> cache;

@Inject
public YourConstructor(@EclipseStoreCache(configNode = "org.eclipse.store.cache", name = "myCache") Cache<Integer, String> cache) {
	this.cache = cache;
}
```

## helidon-integrations-eclipsestore-health

This module provides helpers to create basic health checks for Eclipse Store

### Prerequisites

Use the following maven dependency

```
<dependency>
	<groupId>io.helidon.integrations.eclipsestore</groupId>
	<artifactId>helidon-integrations-eclipsestore-health</artifactId>
</dependency>
```

### Usage

Register an instance of EclipseStoreHealthCheck to your server to provide a HealthCheck for a specific eclipse store instance.

## helidon-integrations-eclipsestore-metrics

This module provides helpers to create a set of default metrics for Eclipse Store

### Prerequisites

Use the following maven dependency

```
<dependency>
	<groupId>io.helidon.integrations.eclipsestore</groupId>
	<artifactId>helidon-integrations-eclipsestore-metrics</artifactId>
</dependency>
```

### Usage

The EclipseStoreMetricsSupport class provides a set of default metrics
