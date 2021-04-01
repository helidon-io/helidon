package io.helidon.integrations.oci.connect.spi;

import java.util.List;

import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * A Java Service Loader service for locating injectable instances.
 */
public interface InjectionProvider {
    /**
     * List of injectable types supported by this provider.
     *
     * @return list of types
     */
    List<InjectionType<?>> injectables();

    @FunctionalInterface
    interface CreateInstanceFunction<T> {
        /**
         * Create a new instance in singleton scope (or Application for CDI).
         *
         * @param restApi OCI rest API configured to the correct instance
         * @param ociConfig configuration located on the oci node
         * @return a new instance to be injected
         */
        T apply(OciRestApi restApi, Config ociConfig);
    }

    class InjectionType<T> {
        private final Class<T> type;
        private final CreateInstanceFunction<T> creator;

        private InjectionType(Class<T> type, CreateInstanceFunction<T> creator) {
            this.type = type;
            this.creator = creator;
        }

        public static <T> InjectionType<T> create(Class<T> type, CreateInstanceFunction<T> creator) {
            return new InjectionType<>(type, creator);
        }

        public Class<T> injectedType() {
            return type;
        }

        public T createInstance(OciRestApi ociRestApi, Config vaultConfig) {
            return creator.apply(ociRestApi, vaultConfig);
        }
    }
}
