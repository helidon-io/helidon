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

package io.helidon.security.providers.oidc.common;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.Errors;
import io.helidon.webclient.WebClient;

final class OidcMetadata {
    private static final Logger LOGGER = Logger.getLogger(OidcMetadata.class.getName());
    private static final String DEFAULT_OIDC_METADATA_URI = "/.well-known/openid-configuration";

    private final JsonObject oidcMetadata;
    private final URI identityUri;

    private OidcMetadata(Builder builder) {
        this.oidcMetadata = builder.metadata;
        this.identityUri = builder.identityUri;
    }

    URI getOidcEndpoint(Errors.Collector collector,
                        URI currentValue,
                        String metaKey,
                        String defaultUri) {

        // is it explicitly configured?
        if (currentValue != null) {
            LOGGER.finest(() -> metaKey + " explicitly configured: " + currentValue);
            return currentValue;
        }

        URI foundValue = null;

        // do we have OIDC metadata
        if (oidcMetadata == null) {
            // no OIDC metadata, use default
            if (identityUri == null) {
                collector.fatal("Identity URI is not defined, cannot provide endpoint for " + metaKey);
                return null;
            }
        } else {
            // get it from metadata
            String jsonValue = oidcMetadata.getString(metaKey, null);
            if (jsonValue != null) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest(metaKey + " loaded from well known metadata: " + jsonValue);
                }
                foundValue = URI.create(jsonValue);
            }
        }

        if (foundValue != null) {
            return foundValue;
        }

        // not found in metadata, or metadata does not exist, use default value
        if (defaultUri == null) {
            collector.fatal(metaKey + " default URI is not defined and URI was not in OIDC metadata");
            return null;
        }

        // safely use default
        return identityUri.resolve(defaultUri);
    }

    static Builder builder() {
        return new Builder();
    }

    public Optional<String> getString(String key) {
        if (oidcMetadata == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(oidcMetadata.getString(key, null));
    }

    static class Builder implements io.helidon.common.Builder<OidcMetadata> {
        private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
        private boolean enableRemoteLoad;
        private JsonObject metadata;
        private WebClient webClient;
        private Errors.Collector collector = Errors.collector();
        private URI identityUri;

        private Builder() {
        }

        @Override
        public OidcMetadata build() {
            if (metadata == null) {
                if (enableRemoteLoad) {
                    load();
                }
            }
            return new OidcMetadata(this);
        }

        Builder remoteEnabled(boolean enableRemoteLoad) {
            this.enableRemoteLoad = enableRemoteLoad;
            return this;
        }

        Builder json(JsonObject jsonObject) {
            this.metadata = jsonObject;
            return this;
        }

        Builder webClient(WebClient webClient) {
            this.webClient = webClient;
            return this;
        }

        Builder collector(Errors.Collector collector) {
            this.collector = collector;
            return this;
        }

        Builder identityUri(URI identityUri) {
            this.identityUri = identityUri;
            return this;
        }

        private void load() {
            URI wellKnown = identityUri.resolve(DEFAULT_OIDC_METADATA_URI);

            try {
                this.metadata = webClient.get()
                        .uri(wellKnown)
                        .request(JsonObject.class)
                        .await(Duration.ofSeconds(20));

                LOGGER.finest(() -> "OIDC Metadata loaded from well known URI: " + wellKnown);
            } catch (Exception e) {
                collector.fatal(e, "Failed to load metadata: " + e.getClass().getName()
                        + ": " + e.getMessage()
                        + " from " + wellKnown);
            }
        }
    }
}
