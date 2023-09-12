/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.auths.common;

import java.lang.System.Logger.Level;
import java.util.LinkedList;
import java.util.List;

import io.helidon.http.Method;
import io.helidon.integrations.common.rest.ApiRequest;
import io.helidon.integrations.common.rest.ApiRestException;
import io.helidon.integrations.common.rest.ResponseBuilder;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.common.rest.RestApiBase;
import io.helidon.integrations.vault.VaultOptionalResponse;
import io.helidon.integrations.vault.VaultRestException;
import io.helidon.integrations.vault.VaultUtil;
import io.helidon.webclient.api.HttpClientResponse;

import jakarta.json.JsonObject;

/**
 * REST API implementation with Vault specific features.
 * Uses the correct type for exception.
 */
public class VaultRestApi extends RestApiBase {
    private static final System.Logger LOGGER = System.getLogger(VaultRestApi.class.getName());

    protected VaultRestApi(BuilderBase<?> builder) {
        super(builder);
    }

    /**
     * A new builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T emptyResponse(String path,
                                  ApiRequest<?> request,
                                  Method method,
                                  String requestId,
                                  HttpClientResponse response,
                                  ResponseBuilder<?, T, ?> responseBuilder) {

        if (responseBuilder instanceof VaultOptionalResponse.Builder) {
            // this is caused by 404, 304 etc.
            // we will try to read the entity
            try {
                JsonObject json = response.entity()
                                          .as(JsonObject.class);
                List<String> errors = VaultUtil.arrayToList(json.getJsonArray("errors"));
                return (T) ((VaultOptionalResponse.Builder<?, ?>) responseBuilder)
                        .errors(errors)
                        .headers(response.headers())
                        .status(response.status())
                        .requestId(requestId)
                        .build();
            } catch (Throwable ex) {
                LOGGER.log(Level.TRACE,
                        () -> "Failed to read response entity for status " + response.status() + ", ignoring for"
                                + " optional response",
                        ex);
                return super.emptyResponse(path, request, method, requestId, response, responseBuilder);
            }
        }
        return super.emptyResponse(path, request, method, requestId, response, responseBuilder);
    }

    @Override
    protected ApiRestException readError(String path,
                                         ApiRequest<?> request,
                                         Method method,
                                         String requestId,
                                         HttpClientResponse response,
                                         JsonObject entity) {

        String message = "Failed to invoke " + method + " on path " + path;

        List<String> vaultErrors = new LinkedList<>();
        try {
            vaultErrors.addAll(VaultUtil.arrayToList(entity.getJsonArray("errors")));
        } catch (Exception e) {
            LOGGER.log(Level.DEBUG, "Failed to read error response", e);
            vaultErrors.add("Failed to read errors, entity: " + entity);
        }

        return VaultRestException.builder()
                                 .status(response.status())
                                 .vaultErrors(vaultErrors)
                                 .message(message + ". Status " + response.status() + ". Vault errors " + vaultErrors)
                                 .apiSpecificError(vaultErrors.toString())
                                 .requestId(requestId)
                                 .headers(response.headers())
                                 .build();
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.vault.auths.common.VaultRestApi}.
     */
    public static class Builder extends BuilderBase<Builder> {
        private Builder() {
        }
    }

    /**
     * A base builder for VaultRestApi subclasses.
     *
     * @param <B> type of builder that subclasses this class
     */
    public static class BuilderBase<B extends BuilderBase<B>> extends RestApi.Builder<B, VaultRestApi> {
        @Override
        protected VaultRestApi doBuild() {
            return new VaultRestApi(this);
        }
    }
}
