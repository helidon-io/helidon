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

import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;

/**
 * Reactive API for OCI Object Storage.
 */
public interface OciObjectStorageRx {
    /**
     * Version of Secret API supported by this client.
     */
    String API_VERSION = "20160918";

    /**
     * Host name prefix.
     */
    String API_HOST_PREFIX = "objectstorage";

    /**
     * Host format of API server.
     */
    String API_HOST_FORMAT = "%s://%s.%s.%s";

    /**
     * Create a new fluent API builder for OCI object storage.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create OCI Object Storage using the default {@link io.helidon.integrations.oci.connect.OciRestApi}.
     *
     * @return OCI object storage instance connecting based on {@code DEFAULT} profile
     */
    static OciObjectStorageRx create() {
        return builder().build();
    }

    /**
     * Create OCI Object Storage based on configuration.
     *
     * @param config configuration on the node of OCI configuration
     * @return OCI object storage instance configured from the configuration
     * @see OciObjectStorageRx.Builder#config(io.helidon.config.Config)
     */
    static OciObjectStorageRx create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Gets the metadata and body of an object.
     *
     * @param request get object request
     * @return future with response or error
     */
    Single<ApiOptionalResponse<GetObjectRx.Response>> getObject(GetObject.Request request);

    /**
     * Creates a new object or overwrites an existing object with the same name. The maximum object size allowed by PutObject
     * is 50 GiB.
     *
     * @param request put object request
     * @param publisher publisher of object's data
     * @return future with response or error
     */
    Single<PutObject.Response> putObject(PutObject.Request request, Flow.Publisher<DataChunk> publisher);

    /**
     * Deletes an object.
     * @param request delete object request
     * @return future with response or error
     */
    Single<DeleteObject.Response> deleteObject(DeleteObject.Request request);

    /**
     * Rename an object in the given Object Storage namespace.
     * See <a href="https://docs.oracle.com/iaas/Content/Object/Tasks/managingobjects.htm#namerequirements">Object Names</a>.
     *
     * @param request rename object request
     * @return future with response or error
     */
    Single<RenameObject.Response> renameObject(RenameObject.Request request);

    /**
     * Fluent API Builder for {@link io.helidon.integrations.oci.objectstorage.OciObjectStorageRx}.
     */
    class Builder implements io.helidon.common.Builder<OciObjectStorageRx> {
        private final OciRestApi.Builder apiBuilder = OciRestApi.builder();

        private String hostPrefix = API_HOST_PREFIX;
        private String namespace;
        private String endpoint;
        private OciRestApi restApi;

        private Builder() {
        }

        @Override
        public OciObjectStorageRx build() {
            if (restApi == null) {
                restApi = apiBuilder.build();
            }
            return new OciObjectStorageRxImpl(this);
        }

        /**
         * Update from configuration. The configuration must be located on the {@code OCI} root configuration
         * node.
         *
         * @param config configuration
         * @return updated builder
         */
        public Builder config(Config config) {
            apiBuilder.config(config);
            config.get("objectstorage.host-prefix").asString().ifPresent(this::hostPrefix);
            config.get("objectstorage.endpoint").asString().ifPresent(this::endpoint);
            config.get("objectstorage.namespace").asString().ifPresent(this::namespace);
            return this;
        }

        /**
         * Instance of rest API to use.
         *
         * @param restApi rest API
         * @return updated builder
         */
        public Builder restApi(OciRestApi restApi) {
            this.restApi = restApi;
            return this;
        }

        /**
         * Host prefix to use for object storage,
         * defaults to {@value API_HOST_PREFIX}.
         *
         * @param prefix prefix to use
         * @return updated builder
         */
        public Builder hostPrefix(String prefix) {
            this.hostPrefix = prefix;
            return this;
        }

        /**
         * Explicit endpoint to use.
         *
         * @param endpoint endpoint
         * @return updated builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Object storage namespace to use.
         *
         * @param namespace object storage namespace
         * @return updated buidler
         */
        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Update the rest access builder to modify defaults.
         *
         * @param builderConsumer consumer of the builder
         * @return updated builder
         */
        public Builder updateRestApi(Consumer<OciRestApi.Builder> builderConsumer) {
            builderConsumer.accept(apiBuilder);
            return this;
        }

        OciRestApi restApi() {
            return restApi;
        }

        Optional<String> namespace() {
            return Optional.ofNullable(namespace);
        }

        String hostPrefix() {
            return hostPrefix;
        }

        String endpoint() {
            return endpoint;
        }
    }
}
