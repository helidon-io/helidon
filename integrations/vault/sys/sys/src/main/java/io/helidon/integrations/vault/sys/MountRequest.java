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

package io.helidon.integrations.vault.sys;

import java.time.Duration;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import io.helidon.integrations.common.rest.ApiJsonBuilder;
import io.helidon.integrations.vault.AuthMethod;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultRequest;

/**
 * A builder for {@link SysRx#enableEngine(io.helidon.integrations.vault.sys.EnableEngine.Request)}
 * , and
 * {@link SysRx#enableAuth(io.helidon.integrations.vault.sys.EnableAuth.Request)} methods.
 */
abstract class MountRequest<T extends MountRequest<T>> extends VaultRequest<T> {
    private final Config config = new Config();

    private String path;

    /**
     * Default constructor.
     */
    protected MountRequest() {
    }

    /**
     * Specifies the path where the engine/method will be mounted.
     * If no path is defined, the engine/method default path will be used.
     *
     * @param path path to configure
     * @return updated request
     */
    public T path(String path) {
        this.path = path;
        return me();
    }

    /**
     * Specifies the human-friendly description of the mount.
     *
     * @param description description
     * @return updated request
     */
    public T description(String description) {
        return add("description", description);
    }

    /**
     * Default lease duration.
     *
     * @param defaultLeaseTtl lease time to live
     * @return updated request
     */
    public T defaultLeaseTtl(Duration defaultLeaseTtl) {
        this.config.defaultLeaseTtl(defaultLeaseTtl);
        return me();
    }

    /**
     * Maximum lease duration.
     *
     * @param maxLeaseTtl maximum lease time to live
     * @return updated request
     */
    public T maxLeaseTtl(Duration maxLeaseTtl) {
        this.config.maxLeaseTtl(maxLeaseTtl);
        return me();
    }

    /**
     * Disable caching.
     * <p>
     * Defaults to {@code false}.
     *
     * @param forceNoCache whether to disable caching
     * @return updated request
     */
    public T forceNoCache(boolean forceNoCache) {
        this.config.forceNoCache(forceNoCache);
        return me();
    }

    /**
     * Add header to whitelist and pass from the request to the plugin.
     *
     * @param passThroughHeader name of the header
     * @return updated request
     */
    public T addPassThroughHeader(String passThroughHeader) {
        this.config.addPassThroughRequestHeader(passThroughHeader);
        return me();
    }

    /**
     * Header to whitelist, allowing a plugin to include them in the response.
     *
     * @param allowedResponseHeader name of the header
     * @return updated request
     */
    public T addAllowedResponseHeader(String allowedResponseHeader) {
        this.config.addAllowedResponseHeader(allowedResponseHeader);
        return me();
    }

    String path() {
        if (path == null) {
            throw new VaultApiException(getClass().getSimpleName() + " path must be defined");
        }
        return path;
    }

    T authMethod(AuthMethod<?> method) {
        return add("type", method.type());
    }

    @Override
    protected void postBuild(JsonBuilderFactory factory, JsonObjectBuilder payload) {
        JsonObject configJson = config.toJson(factory).get();
        if (configJson.isEmpty()) {
            payload.addNull("config");
        } else {
            payload.add("config", configJson);
        }
    }

    /**
     * Set default mount path.
     * @param defaultMount default mount path
     */
    protected void defaultPath(String defaultMount) {
        if (path == null) {
            path = defaultMount;
        }
    }

    private static class Config extends ApiJsonBuilder<Config> {
        Config defaultLeaseTtl(Duration duration) {
            return add("default_lease_ttl", VaultRequest.durationToTtl(duration));
        }

        Config maxLeaseTtl(Duration duration) {
            return add("max_lease_ttl", VaultRequest.durationToTtl(duration));
        }

        Config forceNoCache(boolean forceNoCache) {
            return add("force_no_cache", forceNoCache);
        }

        Config addPassThroughRequestHeader(String headerName) {
            return addToArray("passthrough_request_headers", headerName);

        }

        Config addAllowedResponseHeader(String headerName) {
            return addToArray("allowed_response_headers", headerName);
        }
    }
}
