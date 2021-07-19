module io.helidon.caching.memory {
    requires io.helidon.caching;
    requires io.helidon.common.configurable;
    requires java.logging;
    exports io.helidon.caching.memory;

    provides io.helidon.caching.spi.CacheProvider with io.helidon.caching.memory.MemoryCacheProvider;
}