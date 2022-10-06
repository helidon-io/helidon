package io.helidon.nima.webserver.http;

import java.util.function.Supplier;

import io.helidon.nima.webserver.ServerLifecycle;

/**
 * Can be registered with {@link io.helidon.nima.webserver.http.HttpRouting.Builder#addFeature(java.util.function.Supplier)}.
 * Encapsulates a set of endpoints, services and/or filters.
 * <p>
 * Feature is similar to {@link io.helidon.nima.webserver.http.HttpService} but gives more freedom in setup.
 * Main difference is that a feature can add {@link io.helidon.nima.webserver.http.Filter Filters} and it cannot be
 * registered on a path (that is left to the discretion of the feature developer).
 */
public interface HttpFeature extends Supplier<HttpFeature>, ServerLifecycle {
    @Override
    default HttpFeature get() {
        // this is here to allow methods that accept both an instance and a builder
        return this;
    }

    /**
     * Method to set up a feature.
     * @param routing routing builder
     */
    void setup(HttpRouting.Builder routing);
}
