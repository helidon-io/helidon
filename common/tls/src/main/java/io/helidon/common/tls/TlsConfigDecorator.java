/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.net.ssl.SSLParameters;

import io.helidon.builder.api.Prototype;
import io.helidon.common.tls.spi.TlsManagerProvider;
import io.helidon.config.Config;
import io.helidon.config.ConfigBuilderSupport;

class TlsConfigDecorator implements Prototype.BuilderDecorator<TlsConfig.BuilderBase<?, ?>> {

    @Override
    public void decorate(TlsConfig.BuilderBase<?, ?> target) {
        if (target.enabled() && target.manager().orElse(null) instanceof DisabledTlsManager disabledManager) {
            target.manager(ConfigBuilderSupport.discoverService(target.config().orElse(disabledManager.config),
                                                               "manager",
                                                               TlsManagerProvider.class,
                                                               TlsManager.class,
                                                               target.managerDiscoverServices(),
                                                               Optional.empty())
                                   .orElseGet(ConfiguredTlsManager::new));
        }
        if (target.enabled() && target.sslContext().isPresent()) {
            var sslContext = target.sslContext().orElseThrow();
            List<String> incompatibleOptions = new ArrayList<>();
            target.manager()
                    .filter(manager -> !(manager instanceof ExplicitContextTlsManager)
                            && manager.getClass() != ConfiguredTlsManager.class)
                    .ifPresent(_ -> incompatibleOptions.add("manager"));
            target.privateKey().ifPresent(_ -> incompatibleOptions.add("private-key"));
            if (!target.privateKeyCertChain().isEmpty()) {
                incompatibleOptions.add("private-key-cert-chain");
            }
            if (!target.trust().isEmpty()) {
                incompatibleOptions.add("trust");
            }
            target.secureRandom().ifPresent(_ -> incompatibleOptions.add("secure-random"));
            target.secureRandomProvider().ifPresent(_ -> incompatibleOptions.add("secure-random-provider"));
            target.secureRandomAlgorithm().ifPresent(_ -> incompatibleOptions.add("secure-random-algorithm"));
            target.keyManagerFactoryAlgorithm().ifPresent(_ -> incompatibleOptions.add("key-manager-factory-algorithm"));
            target.keyManagerFactoryProvider().ifPresent(_ -> incompatibleOptions.add("key-manager-factory-provider"));
            target.trustManagerFactoryAlgorithm().ifPresent(_ -> incompatibleOptions.add("trust-manager-factory-algorithm"));
            target.trustManagerFactoryProvider().ifPresent(_ -> incompatibleOptions.add("trust-manager-factory-provider"));
            if (target.trustAll()) {
                incompatibleOptions.add("trust-all");
            }
            target.internalKeystoreType().ifPresent(_ -> incompatibleOptions.add("internal-keystore-type"));
            target.internalKeystoreProvider().ifPresent(_ -> incompatibleOptions.add("internal-keystore-provider"));
            target.revocation().ifPresent(_ -> incompatibleOptions.add("revocation"));
            if (!TlsConfigSupport.CustomMethods.DEFAULT_PROTOCOL.equals(target.protocol())) {
                incompatibleOptions.add("protocol");
            }
            target.provider().ifPresent(_ -> incompatibleOptions.add("provider"));
            if (target.sessionCacheSize() != TlsConfigSupport.CustomMethods.DEFAULT_SESSION_CACHE_SIZE) {
                incompatibleOptions.add("session-cache-size");
            }
            if (!target.sessionTimeout()
                    .equals(Duration.parse(TlsConfigSupport.CustomMethods.DEFAULT_SESSION_TIMEOUT))) {
                incompatibleOptions.add("session-timeout");
            }
            if (!incompatibleOptions.isEmpty()) {
                throw new IllegalArgumentException("Explicit SSLContext cannot be combined with TLS options: "
                                                           + String.join(", ", incompatibleOptions));
            }
            target.manager()
                    .filter(ExplicitContextTlsManager.class::isInstance)
                    .map(ExplicitContextTlsManager.class::cast)
                    .filter(manager -> manager.sslContext() == sslContext)
                    .orElseGet(() -> {
                        var manager = new ExplicitContextTlsManager(sslContext);
                        target.manager(manager);
                        return manager;
                    });
        }
        sslParameters(target);
        TlsManager theManager = target.manager().orElse(null);
        if (theManager == null
                || (target.sslContext().isEmpty() && theManager instanceof ExplicitContextTlsManager)) {
            theManager = target.enabled()
                    ? new ConfiguredTlsManager()
                    : new DisabledTlsManager(target.config().orElseGet(Config::empty));
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

    private static final class DisabledTlsManager extends ConfiguredTlsManager {
        private final Config config;

        private DisabledTlsManager(Config config) {
            this.config = config;
        }
    }

}
