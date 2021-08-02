module io.helidon.caching.coherence {
    requires io.helidon.caching;
    requires io.helidon.common.configurable;
    requires com.oracle.coherence.ce;
    requires java.logging;

    exports io.helidon.caching.coherence;

    provides io.helidon.caching.spi.CacheProvider with io.helidon.caching.coherence.CoherenceProvider;
}