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

package io.helidon.integrations.oci.connect;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;

/**
 * OCI connectivity configuration based on instance principal.
 * This is used when running within OCI VMs.
 *
 * TODO THIS CLASS IS NOT YET IMPLEMENTED AND WILL THROW AN EXCEPTION
 */
public class OciConfigInstancePrincipal extends OciConfigPrincipalBase implements OciConfigProvider {
    private static final String DEFAULT_METADATA_SERVICE_URL = "http://169.254.169.254/opc/v2/";

    private final AtomicReference<OciSignatureData> currentSignatureData = new AtomicReference<>();
    private final String region;
    private final String tenancyId;

    private OciConfigInstancePrincipal(Builder builder) {
        super(builder);

        this.region = null;
        this.tenancyId = null;
    }

    // this method blocks when trying to connect to a remote IP address
    static boolean isAvailable() {
        return WebClient.builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .baseUri(DEFAULT_METADATA_SERVICE_URL)
                .followRedirects(true)
                .keepAlive(false)
                .build()
                .get()
                .request()
                .map(response -> response.status() == Http.Status.FORBIDDEN_403)
                .onErrorResume(it -> false)
                .await();
    }

    /**
     * Create a new instance from environment.
     *
     * @return a new instance
     */
    public static OciConfigInstancePrincipal create() {
        throw new UnsupportedOperationException("OCI instance principal authentication is not yet supported");
    }

    @Override
    public OciSignatureData signatureData() {
        return currentSignatureData.get();
    }

    @Override
    public String region() {
        return region;
    }

    @Override
    public String tenancyOcid() {
        return tenancyId;
    }

    @Override
    public Single<OciSignatureData> refresh() {
        return OciConfigProvider.super.refresh();
    }

    /**
     * Fluent API builder for {@link io.helidon.integrations.oci.connect.OciConfigInstancePrincipal}.
     */
    public static class Builder extends OciConfigPrincipalBase.Builder<Builder>
            implements io.helidon.common.Builder<OciConfigInstancePrincipal> {
        @Override
        public OciConfigInstancePrincipal build() {
            return new OciConfigInstancePrincipal(this);
        }
    }
}
