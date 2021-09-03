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

package io.helidon.integrations.oci.vault;

import java.util.Optional;
import java.util.function.Function;

import javax.json.JsonObject;

import io.helidon.common.configurable.LruCache;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRequestBase;
import io.helidon.integrations.oci.connect.OciRestApi;

class OciVaultRxImpl implements OciVaultRx {
    private final LruCache<String, String> keyIdToEndpointCache = LruCache.<String, String>builder()
            .capacity(100)
            .build();

    private final OciRestApi restApi;
    private final String secretApiVersion;
    private final String bundleApiVersion;
    private final String vaultPrefix;
    private final String retrievalPrefix;
    private final String kmsPrefix;
    private final Optional<String> cryptographicEndpoint;
    private final Optional<String> managementEndpoint;
    private final Optional<String> vaultEndpoint;
    private final Optional<String> kmsEndpoint;
    private final Optional<String> retrievalEndpoint;
    private final String vaultEndpointFormat;
    private final String kmsEndpointFormat;
    private final String retrievalEndpointFormat;

    OciVaultRxImpl(Builder builder) {
        this.restApi = builder.restApi();
        this.secretApiVersion = builder.secretApiVersion();
        this.bundleApiVersion = builder.secretBundleApiVersion();
        this.vaultPrefix = builder.vaultPrefix();
        this.retrievalPrefix = builder.retrievalPrefix();
        this.kmsPrefix = builder.kmsPrefix();
        this.vaultEndpoint = Optional.ofNullable(builder.vaultEndpoint());
        this.kmsEndpoint = Optional.ofNullable(builder.kmsEndpoint());
        this.retrievalEndpoint = Optional.ofNullable(builder.retrievalEndpoint());
        this.cryptographicEndpoint = Optional.ofNullable(builder.cryptographicEndpoint());
        this.managementEndpoint = Optional.ofNullable(builder.managementEndpoint());
        this.vaultEndpointFormat = builder.vaultEndpointFormat();
        this.kmsEndpointFormat = builder.kmsEndpointFormat();
        this.retrievalEndpointFormat = builder.retrievalEndpointFormat();
    }

    @Override
    public Single<ApiOptionalResponse<Secret>> getSecret(GetSecret.Request request) {
        String apiPath = secretApiVersion + "/secrets/" + request.secretId();

        vault(request);

        return restApi.get(apiPath,
                           request,
                           ApiOptionalResponse.<JsonObject, Secret>apiResponseBuilder()
                                   .entityProcessor(Secret::create));
    }

    @Override
    public Single<CreateSecret.Response> createSecret(CreateSecret.Request request) {
        String apiPath = secretApiVersion + "/secrets";

        vault(request);

        return restApi.invokeWithResponse(Http.Method.POST,
                                          apiPath,
                                          request,
                                          CreateSecret.Response.builder());
    }

    @Override
    public Single<ApiOptionalResponse<GetSecretBundle.Response>> getSecretBundle(GetSecretBundle.Request request) {
        String apiPath = bundleApiVersion + "/secretbundles/" + request.secretId();

        retrieval(request);

        return restApi.get(apiPath,
                           request,
                           ApiOptionalResponse.<JsonObject, GetSecretBundle.Response>apiResponseBuilder()
                                   .entityProcessor(GetSecretBundle.Response::create));
    }

    @Override
    public Single<DeleteSecret.Response> deleteSecret(DeleteSecret.Request request) {
        String apiPath = secretApiVersion + "/secrets/" + request.secretId() + "/actions/scheduleDeletion";

        vault(request);

        return restApi.post(apiPath,
                            request,
                            DeleteSecret.Response.builder());
    }

    @Override
    public Single<Encrypt.Response> encrypt(Encrypt.Request request) {
        String apiPath = secretApiVersion + "/encrypt";

        return cryptoEndpoint(request, request.keyId())
                .flatMapSingle(it -> restApi.invokeWithResponse(Http.Method.POST,
                                                                apiPath,
                                                                it,
                                                                Encrypt.Response.builder()));
    }

    @Override
    public Single<Decrypt.Response> decrypt(Decrypt.Request request) {
        String apiPath = secretApiVersion + "/decrypt";

        return cryptoEndpoint(request, request.keyId())
                .flatMapSingle(it -> restApi.invokeWithResponse(Http.Method.POST,
                                                                apiPath,
                                                                it,
                                                                Decrypt.Response.builder()));
    }

    @Override
    public Single<Sign.Response> sign(Sign.Request request) {
        String apiPath = secretApiVersion + "/sign";

        return cryptoEndpoint(request, request.keyId())
                .flatMapSingle(it -> restApi.invokeWithResponse(Http.Method.POST,
                                                                apiPath,
                                                                it,
                                                                Sign.Response.builder()));
    }

    @Override
    public Single<Verify.Response> verify(Verify.Request request) {
        String apiPath = secretApiVersion + "/verify";

        return cryptoEndpoint(request, request.keyId())
                .flatMapSingle(it -> restApi.invokeWithResponse(Http.Method.POST,
                                                                apiPath,
                                                                it,
                                                                Verify.Response.builder()));
    }

    @Override
    public Single<ApiOptionalResponse<GetKey.Response>> getKey(GetKey.Request request) {
        String apiPath = secretApiVersion + "/keys/" + request.keyId();

        managementEndpoint.ifPresentOrElse(request::endpoint, () -> {
            throw new OciApiException("Management endpoint must be configured for key operations");
        });

        return restApi.get(apiPath,
                           request,
                           ApiOptionalResponse.<JsonObject, GetKey.Response>apiResponseBuilder()
                                   .entityProcessor(GetKey.Response::create));
    }

    @Override
    public Single<ApiOptionalResponse<GetVault.Response>> getVault(GetVault.Request request) {
        String apiPath = secretApiVersion + "/vaults/" + request.vaultId();

        kms(request);

        return restApi.get(apiPath,
                           request,
                           ApiOptionalResponse.<JsonObject, GetVault.Response>apiResponseBuilder()
                                   .entityProcessor(GetVault.Response::create));
    }

    private <T extends OciRequestBase<T>> Single<T> cryptoEndpoint(T request, String keyId) {
        if (request.endpoint().isPresent()) {
            return Single.just(request);
        }
        return cryptographicEndpoint.map(Single::just)
                .orElseGet(() -> keyIdToEndpointCache.get(keyId)
                        .map(Single::just)
                        .orElseGet(() -> getKey(GetKey.Request.create(keyId))
                                .flatMapSingle(mapIfPresent("Could not locate key " + keyId))
                                .map(GetKey.Response::vaultId)
                                .flatMapSingle(vaultId -> getVault(GetVault.Request.create(vaultId))
                                        .flatMapSingle(mapIfPresent("Could not locate vault " + vaultId)))
                                .map(GetVault.Response::cryptoEndpoint)
                                .peek(cryptoEndpoint -> keyIdToEndpointCache.put(keyId, cryptoEndpoint))))
                .map(request::endpoint);
    }

    private <T> Function<ApiOptionalResponse<T>, Single<T>> mapIfPresent(String errorMessage) {
        return apiOptional -> {
            Optional<T> entity = apiOptional.entity();

            return entity.map(Single::just)
                    .orElseGet(() -> Single.error(new OciApiException(errorMessage)));
        };
    }

    private void kms(OciRequestBase<?> request) {
        kmsEndpoint.ifPresent(request::endpoint);
        request.hostPrefix(kmsPrefix);
        request.hostFormat(kmsEndpointFormat);
    }

    private void vault(OciRequestBase<?> request) {
        vaultEndpoint.ifPresent(request::endpoint);
        request.hostPrefix(vaultPrefix);
        request.hostFormat(vaultEndpointFormat);
    }

    private void retrieval(OciRequestBase<?> request) {
        retrievalEndpoint.ifPresent(request::endpoint);
        request.hostPrefix(retrievalPrefix + "." + vaultPrefix);
        request.hostFormat(retrievalEndpointFormat);
    }
}
