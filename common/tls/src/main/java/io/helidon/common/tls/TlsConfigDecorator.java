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

package io.helidon.common.tls;

import javax.net.ssl.SSLParameters;

import io.helidon.builder.api.Prototype;

class TlsConfigDecorator implements Prototype.BuilderDecorator<TlsConfig.BuilderBase<?, ?>> {

    @Override
    public void decorate(TlsConfig.BuilderBase<?, ?> target) {
        sslParameters(target);
        TlsManager theManager = target.manager().orElse(null);
        if (theManager == null) {
            theManager = new ConfiguredTlsManager();
            target.manager(theManager);
        }
    }

    static void sslParameters(TlsConfig.BuilderBase<?, ?> target) {
        if (target.sslParameters().isPresent()) {
            return;
        }
        SSLParameters parameters = new SSLParameters();

        if (!target.applicationProtocols().isEmpty()) {
            parameters.setApplicationProtocols(target.applicationProtocols().toArray(new String[0]));
        }
        if (!target.enabledProtocols().isEmpty()) {
            parameters.setProtocols(target.enabledProtocols().toArray(new String[0]));
        }
        if (!target.enabledCipherSuites().isEmpty()) {
            parameters.setCipherSuites(target.enabledCipherSuites().toArray(new String[0]));
        }
        if (Tls.ENDPOINT_IDENTIFICATION_NONE.equals(target.endpointIdentificationAlgorithm())) {
            parameters.setEndpointIdentificationAlgorithm("");
        } else {
            parameters.setEndpointIdentificationAlgorithm(target.endpointIdentificationAlgorithm());
        }

        switch (target.clientAuth()) {
        case REQUIRED -> parameters.setNeedClientAuth(true);
        case OPTIONAL -> parameters.setWantClientAuth(true);
        default -> {
        }
        }

        target.sslParameters(parameters);
    }

}
