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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.config.Config;
import io.helidon.health.common.BuiltInHealthCheck;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.objectstorage.GetBucket;
import io.helidon.integrations.oci.objectstorage.OciObjectStorage;
import io.helidon.integrations.oci.objectstorage.OciObjectStorageRx;

import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    private final List<String> buckets;
    private final String namespace;
    private final OciObjectStorage ociObjectStorage;

    @Inject
    OciObjectStorageHealthCheck(@ConfigProperty(name = "oci.objectstorage.namespace") String namespace,
                                @ConfigProperty(name = "oci.objectstorage.healthchecks") List<String> buckets,
                                OciObjectStorage ociObjectStorage) {
        this.buckets = buckets;
        this.namespace = namespace;
        this.ociObjectStorage = ociObjectStorage;
    }

    private OciObjectStorageHealthCheck(Builder builder) {
        this.buckets = builder.buckets;
        this.namespace = builder.namespace;
        this.ociObjectStorage = builder.ociObjectStorage;
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
     * Checks that the OCI Object Storage buckets are accessible. Will report a status code
     * or an error message for each bucket as data. Can block since all health checks are
     * called asynchronously.
     *
     * @return a response
     */
    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("objectStorage");

        boolean status = true;
        for (String bucket : buckets) {
            try {
                ApiOptionalResponse<GetBucket.Response> r = ociObjectStorage.getBucket(GetBucket.Request.builder()
                        .namespace(namespace)
                        .bucket(bucket));
                LOGGER.fine(() -> "OCI ObjectStorage health check for bucket " + bucket
                        + " returned status code " + r.status().code());
                builder.withData(bucket, r.status().code());
                status = status && r.status().equals(OK_200);
            } catch (Throwable t) {
                LOGGER.fine(() -> "OCI ObjectStorage health check for bucket " + bucket
                        + " exception " + t.getMessage());
                status = false;
                builder.withData(bucket, t.getMessage());
            }
        }

        builder.state(status);
        return builder.build();
    }

    /**
     * Fluent API builder for {@link OciObjectStorageHealthCheck}.
     */
    public static final class Builder implements io.helidon.common.Builder<OciObjectStorageHealthCheck> {

        private String namespace;
        private OciObjectStorageRx ociObjectStorageRx;
        private OciObjectStorage ociObjectStorage;
        private final List<String> buckets = new ArrayList<>();

        private Builder() {
        }

        @Override
        public OciObjectStorageHealthCheck build() {
            Objects.requireNonNull(namespace);
            if (ociObjectStorageRx == null) {
                ociObjectStorageRx = OciObjectStorageRx.builder()
                        .namespace(namespace)
                        .build();
            }
            this.ociObjectStorage = OciObjectStorage.create(ociObjectStorageRx);
            return new OciObjectStorageHealthCheck(this);
        }

        /**
         * Set up this builder using config.
         *
         * @param config the config.
         * @return the builder.
         */
        public Builder config(Config config) {
            config.get("oci.objectstorage.namespace").asString().ifPresent(this::namespace);
            config.get("oci.objectstorage.healthchecks").asList(String.class).ifPresent(this.buckets::addAll);
            return this;
        }

        /**
         * Set the underlying OCI ObjectStorage RX client.
         *
         * @param ociObjectStorage object storage RX client.
         * @return the builder.
         */
        public Builder ociObjectStorage(OciObjectStorageRx ociObjectStorage) {
            this.ociObjectStorageRx = ociObjectStorage;
            return this;
        }

        /**
         * Add a bucket to the list.
         *
         * @param bucket bucket's name.
         * @return the builder.
         */
        public Builder addBucket(String bucket) {
            this.buckets.add(bucket);
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
