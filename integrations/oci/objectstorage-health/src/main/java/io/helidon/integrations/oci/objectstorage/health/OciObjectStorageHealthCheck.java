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

package io.helidon.integrations.oci.objectstorage.health;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.objectstorage.GetBucket;
import io.helidon.integrations.oci.objectstorage.GetBucketRx;
import io.helidon.integrations.oci.objectstorage.OciObjectStorageRx;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import static io.helidon.common.http.Http.Status.OK_200;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Liveness check for an OCI's ObjectStorage bucket. Reads configuration to
 * obtain bucket name as well as OCI properties from '~/.oci/config'.
 */
@Liveness
@ApplicationScoped // this will be ignored if not within CDI
@BuiltInHealthCheck
public final class OciObjectStorageHealthCheck implements HealthCheck {
    private static final Logger LOGGER = Logger.getLogger(OciObjectStorageHealthCheck.class.getName());

    @Inject
    @ConfigProperty(name = "oci.objectstorage.bucket")
    private String bucket;

    @Inject
    @ConfigProperty(name = "oci.objectstorage.namespace")
    private String namespace;

    @Inject
    @ConfigProperty(name = "oci.objectstorage.health.timeout-millis", defaultValue = "10000")
    private long timeoutMillis;

    private Duration timeout;

    private OciObjectStorageRx ociObjectStorage;

    private OciObjectStorageHealthCheck() {
    }

    private OciObjectStorageHealthCheck(Builder builder) {
        this.timeout = builder.timeout;
        this.bucket = builder.bucket;
        this.namespace = builder.namespace;

        if (builder.ociObjectStorage != null) {
            this.ociObjectStorage = builder.ociObjectStorage;
        } else {
            Objects.requireNonNull(this.bucket);
            Objects.requireNonNull(this.namespace);
            // This requires OCI configuration in ~/.oci/config
            this.ociObjectStorage = OciObjectStorageRx.builder()
                    .namespace(namespace)
                    .build();
        }
    }

    @PostConstruct
    private void initialize() {
        Objects.requireNonNull(this.bucket);
        Objects.requireNonNull(this.namespace);
        this.timeout = Duration.of(timeoutMillis, MILLIS);
        // This requires OCI configuration in ~/.oci/config
        this.ociObjectStorage = OciObjectStorageRx.builder()
                .namespace(namespace)
                .build();
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
        return timeout.toMillis() > 0;
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
     * @param config the config.
     * @return an instance.
     */
    public static OciObjectStorageHealthCheck create(Config config) {
        return builder().config(config).build();
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
            Single<ApiOptionalResponse<GetBucketRx.Response>> single =
                    ociObjectStorage.getBucket(GetBucket.Request.builder().bucket(bucket));
            try {
                ApiOptionalResponse<GetBucketRx.Response> r = single.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
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

        private static final Duration DEFAULT_TIMEOUT_SECONDS = Duration.of(10, SECONDS);

        private String bucket;
        private String namespace;
        private Duration timeout = DEFAULT_TIMEOUT_SECONDS;
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
            config.get("oci.objectstorage.healthcheck.timeout-millis").asLong().ifPresent(this::timeoutMillis);
            config.get("oci.objectstorage.namespace").asString().ifPresent(this::namespace);
            config.get("oci.objectstorage.bucket").asString().ifPresent(this::bucket);
            return this;
        }

        /**
         * Set timeout.
         *
         * @param timeout the timeout.
         * @return the builder.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set timeout in millis.
         *
         * @param millis the timeout in millis.
         * @return the builder.
         */
        public Builder timeoutMillis(long millis) {
            timeout(Duration.of(millis, MILLIS));
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
