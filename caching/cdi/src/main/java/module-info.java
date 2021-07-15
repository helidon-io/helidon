module io.helidon.caching.cdi {
    requires jakarta.enterprise.cdi.api;
    requires jakarta.inject.api;
    requires jakarta.interceptor.api;
    requires java.annotation;
    requires transitive io.helidon.caching.annotation;
    requires io.helidon.caching;
    requires java.logging;

    exports io.helidon.caching.cdi;

    provides javax.enterprise.inject.spi.Extension with io.helidon.caching.cdi.CachingCdiExtension;
}