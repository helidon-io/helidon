/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.oci.objectstorage;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.integrations.common.rest.ApiOptionalResponse;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import static io.helidon.common.http.Http.Status.OK_200;

/**
 * Liveness check for an OCI's ObjectStorage bucket. Reads configuration to
 * obtain bucket name as well as OCI properties from '~/.oci/config'.
 */
@Liveness
@ApplicationScoped // this will be ignored if not within CDI
@BuiltInHealthCheck
public final class OciObjectStorageHealthCheck implements HealthCheck {
    private static final Logger LOGGER = Logger.getLogger(OciObjectStorageHealthCheck.class.getName());

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private String bucket;
    private String namespace;
    private int timeout;
    private OciObjectStorageRx ociObjectStorage;

    private OciObjectStorageHealthCheck() {
        this.timeout = DEFAULT_TIMEOUT_SECONDS;
        Config ociConfig = Config.create().get("oci");
        this.bucket = ociConfig.get("objectstorage.bucket").asString().orElse(null);
        this.namespace = ociConfig.get("objectstorage.namespace").asString().get();
        // This requires OCI configuration in ~/.oci/config
        this.ociObjectStorage = OciObjectStorageRx.create(ociConfig);
    }

    private OciObjectStorageHealthCheck(Builder builder) {
        this.timeout = builder.timeout;
        this.bucket = builder.bucket;
        this.namespace = builder.namespace;
        this.ociObjectStorage = builder.ociObjectStorage;

        Config ociConfig = builder.config;
        if (ociConfig == null) {
            ociConfig = Config.create().get("oci");
        }
        if (this.timeout == -1) {
            this.timeout = ociConfig.get("objectstorage.healthcheck.timeout")
                    .asInt().orElse(DEFAULT_TIMEOUT_SECONDS);
        }
        if (this.namespace == null) {
            this.namespace = ociConfig.get("objectstorage.namespace").asString().get();
        }
        if (this.bucket == null) {
            this.bucket = ociConfig.get("objectstorage.bucket").asString().orElse(null);
        }
        if (this.ociObjectStorage == null) {
            // This requires OCI configuration in ~/.oci/config
            this.ociObjectStorage = OciObjectStorageRx.create(ociConfig);
        }
    }


    /**
     * Internal validation method used for testing. Does not attempt to connect
     * to OCI.
     *
     * @return outcome of internal validation.
     */
    boolean validate() {
        try {
            Objects.requireNonNull(bucket);
            Objects.requireNonNull(namespace);
            Objects.requireNonNull(ociObjectStorage);
        } catch (NullPointerException e) {
            return false;
        }
        return timeout > 0;
    }

    /**
     * Create a new fluent API builder to configure a new health check.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance.
     *
     * @return an instance.
     */
    public static OciObjectStorageHealthCheck create() {
        return builder().build();
    }

    /**
     * Checks that the OCI Object Storage bucket is accessible, if defined. Will report
     * error only if the bucket has been defined and it is not accessible for some reason.
     * Can block since all health checks are called asynchronously.
     *
     * @return a response
     */
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("objectStorage");
        if (bucket != null) {
            // Attempt to retrieve bucket's metadata
            Single<ApiOptionalResponse<GetObjectRx.Response>> single =
                    ociObjectStorage.getBucket(GetObject.Request.builder().bucket(bucket));
            try {
                ApiOptionalResponse<GetObjectRx.Response> r = single.get(timeout, TimeUnit.SECONDS);
                builder.state(r.status().equals(OK_200));
                LOGGER.fine(() -> "OCI ObjectStorage health check for bucket " + bucket
                        + " returned status code " + r.status().code());
            } catch (Throwable t) {
                builder.state(false);
            } finally {
                builder.withData("bucket", bucket);
            }
        } else {
            LOGGER.fine("OCI ObjectStorage health check disabled when bucket not defined");
            builder.state(true);
        }
        builder.withData("namespace", namespace);
        return builder.build();
    }

    /**
     * Fluent API builder for {@link OciObjectStorageHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<OciObjectStorageHealthCheck> {

        private Config config;
        private String bucket;
        private String namespace;
        private int timeout = -1;
        private OciObjectStorageRx ociObjectStorage;

        private Builder() {
        }

        @Override
        public OciObjectStorageHealthCheck build() {
            return new OciObjectStorageHealthCheck(this);
        }

        /**
         * Set up this builder using config.
         *
         * @param config the config.
         * @return the builder.
         */
        public Builder config(Config config) {
            this.config = config;
            Config timeout = config.get("objectstorage.healthcheck.timeout");
            timeout.asInt().ifPresent(this::timeout);
            return this;
        }

        /**
         * Set timeout in millis.
         *
         * @param timeout timeout in millis.
         * @return the builder.
         */
        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set the underlying OCI ObjectStorage RX client.
         *
         * @param ociObjectStorage object storage RX client.
         * @return the builder.
         */
        public Builder ociObjectStorageRx(OciObjectStorageRx ociObjectStorage) {
            this.ociObjectStorage = ociObjectStorage;
            return this;
        }

        /**
         * Set the bucket's name.
         *
         * @param bucket bucket's name.
         * @return the builder.
         */
        public Builder bucket(String bucket) {
            this.bucket = bucket;
            return this;
        }

        /**
         * Set the namespace.
         *
         * @param namespace the namespace.
         * @return the builder.
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }
    }
}
