#Microstream integration into Helidon
This projects add [Microstream](https://microstream.one) support to Helidon.

The offical [Microstream documentation](https://manual.docs.microstream.one/) can be found here.

##helidon-integrations-microstream
Adds basic support for Microstream

###Prerequisites
Use the following maven dependency

```
<dependency>
	<groupId>io.helidon.integrations.microstream</groupId>
	<artifactId>helidon-integrations-microstream</artifactId>
</dependency>
```

###API
Use the EmbeddedStorageManagerBuilder to create a Microstream instance:

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

for a list of all possible properties see [Microstream configuration properties](https://manual.docs.microstream.one/data-store/configuration/properties)

###CDI extension for Microstream

the example below shows how to create a Microstream instance using a provided configuration.

```
private EmbeddedStorageManager storage;

@Inject
public YourConstructor(@MicrostreamStorage(configNode = "one.microstream.storage.greetings")EmbeddedStorageManager storage) {
		super();
		this.storage = storage;
}
```


##helidon-integrations-microstream-cache
Adds basic support for the Microstream JCache implementation

###Prerequisites
Use the following maven dependency

```
<dependency>         
	<groupId>io.helidon.integrations.microstream</groupId>
	<artifactId>helidon-integrations-microstream-cache</artifactId>
</dependency>
```

###API
Use the CacheBuilder to create Microstream JCache instance:

Create a CacheConfiguration first

```
CacheConfiguration<Integer, String> cacheConfig = MicrostreamCacheConfigurationBuilder
	.builder(config.get("cache"), Integer.class, String.class).build();
```
Then build the cache

```
Cache<Integer, String> cache = CacheBuilder.builder(cacheConfig, Integer.class, String.class).build("myCache");
```

Configuration can either be done by the MicrostreamCacheConfigurationBuilder or by supplying a helidon configuration node 

```
Config config = Config.create();

Cache<Integer, String> cache = CacheBuilder.create("myCache", config, Integer.class, String.class);

```

for a list of all possible properties see [Microstream Cache configuration properties](https://manual.docs.microstream.one/cache/configuration/properties)

###CDI extention for Microstream
the example below shows how to create a Microstream-Cache instance using a provided configuration.

```
private Cache<Integer, String> cache;

@Inject
public YourConstructor(@MicrostreamCache(configNode = "one.microstream.cache", name = "myCache") Cache<Integer, String> cache) {
	this.cache = cache;
}
```

##helidon-integrations-microstream-health
This module provides helpers to create basic health checks for Microstream 

###Prerequisites
Use the following maven dependency

```
<dependency>
	<groupId>io.helidon.integrations.microstream</groupId>
	<artifactId>helidon-integrations-microstream-health</artifactId>
</dependency>
```

###Usage
Register an instance of MicrostreamHealthCheck to your server to provide a HealtCheck for a specific microstream instance.

##helidon-integrations-microstream-metrics
This module provides helpers to create a set of default metrics for Microstream 

###Prerequisites
Use the following maven dependency

```
<dependency>
	<groupId>io.helidon.integrations.microstream</groupId>
	<artifactId>helidon-integrations-microstream-metrics</artifactId>
</dependency>
```

###Usage
The MicrostreamMetricsSupport class provides a set of default metrics