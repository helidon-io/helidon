/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.util.function.Consumer;

import io.helidon.common.tls.Tls;

/**
 * The default implementation (service loader and provider-driven) of {@link OciCertificatesTlsManager}.
 *
 * @see DefaultOciCertificatesTlsManagerProvider
 */
// TODO:
class DefaultOciCertificatesTlsManager implements OciCertificatesTlsManager {

    private final OciCertificatesTlsManagerConfig cfg;

    DefaultOciCertificatesTlsManager(OciCertificatesTlsManagerConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public OciCertificatesTlsManagerConfig prototype() {
        return null;
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public String type() {
        return null;
    }

    @Override
    public boolean reload() {
        return false;
    }

    @Override
    public void register(Consumer<Tls> tlsConsumer) {

    }

    @Override
    public Tls tls() {
        return null;
    }

}
