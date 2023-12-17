package io.helidon.inject.runtime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.helidon.inject.ServiceProvider;

public class ProviderUtil {
    /**
     * Provides a {@link io.helidon.inject.ServiceProvider#description()}, falling back to {@link #toString()} on the passed
     * provider argument.
     *
     * @param provider the provider
     * @return the description
     */
    static String toDescription(Object provider) {
        if (provider instanceof Optional) {
            provider = ((Optional<?>) provider).orElse(null);
        }

        if (provider instanceof ServiceProvider) {
            return ((ServiceProvider<?>) provider).description();
        }
        return String.valueOf(provider);
    }

    /**
     * Provides a {@link ServiceProvider#description()}, falling back to {@link #toString()} on the passed
     * provider argument.
     *
     * @param coll the collection of providers
     * @return the description
     */
    static List<String> toDescriptions(Collection<?> coll) {
        return coll.stream()
                .map(ProviderUtil::toDescription)
                .toList();
    }
}
