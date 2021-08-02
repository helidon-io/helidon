module io.helidon.caching {
    requires java.logging;

    requires io.helidon.common.serviceloader;
    requires io.helidon.common.configurable;

    requires transitive io.helidon.common.reactive;
    requires transitive io.helidon.config;

    exports io.helidon.caching;
    exports io.helidon.caching.spi;

    uses io.helidon.caching.spi.CacheProvider;
}