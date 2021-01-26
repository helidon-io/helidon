package io.helidon.integrations.oci.objectstorage;


import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import io.helidon.integrations.oci.OciException;

import java.util.Objects;

/**
 * Class OciObjectStorage.
 */
public class OciObjectStorage {
    private AuthenticationDetailsProvider authenticationDetailsProvider;
    private ObjectStorage objectStorage;

    /**
     * Constructor for OciObjectStorage.
     *
     * @param builder following the builder pattern.
     */
    public OciObjectStorage(Builder builder) {
        authenticationDetailsProvider = builder.authenticationDetailsProvider;
        try {
            objectStorage = ObjectStorageClient.builder().build(authenticationDetailsProvider);
        } catch (BmcException e) {
            throw new OciException("Wrong client setup", e);
        }
    }

    /**
     * Get the builder.
     *
     * @return the construction builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the used Authentication Details Provider.
     *
     * @return
     */
    public AuthenticationDetailsProvider getAuthenticationDetailsProvider() {
        return authenticationDetailsProvider;
    }

    /**
     * Get the set up Object Storage.
     *
     * @return ObjectStorage.
     */
    public ObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public static class Builder implements io.helidon.common.Builder<OciObjectStorage> {

        private AuthenticationDetailsProvider authenticationDetailsProvider;

        private Builder() {
        }

        /**
         * Builder for the wrapper class.
         *
         * @return wrapper.
         */
        public OciObjectStorage build() {
            Objects.requireNonNull(authenticationDetailsProvider,
                    "Must set the Authentication Details Provider before building");
            return new OciObjectStorage(this);
        }

        /**
         * Submit the Authentication Details Provider.
         *
         * @param authenticationDetailsProvider
         * @return Builder.
         */
        public Builder authenticationDetailsProvider(AuthenticationDetailsProvider authenticationDetailsProvider) {
            this.authenticationDetailsProvider = authenticationDetailsProvider;
            return this;
        }
    }
}
