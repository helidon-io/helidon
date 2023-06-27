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

package io.helidon.pico.configdriven.tests.config;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import javax.net.ssl.SSLContext;

import io.helidon.builder.api.Prototype;
import io.helidon.common.LazyValue;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.configdriven.api.ConfigBean;

/**
 * aka WebServerTls.
 *
 * A class wrapping transport layer security (TLS) configuration for
 * WebServer sockets.
 */
@ConfigBean
@Configured(root = true, prefix = "tls")
@Prototype.Blueprint
interface FakeWebServerTlsConfigBlueprint {
    String PROTOCOL = "TLS";
    // secure random cannot be stored in native image, it must be initialized at runtime
    LazyValue<Random> RANDOM = LazyValue.create(SecureRandom::new);

    /**
     * This constant is a context classifier for the x509 client certificate if it is present. Callers may use this
     * constant to lookup the client certificate associated with the current request context.
     */
    String CLIENT_X509_CERTIFICATE = FakeWebServerTlsConfigBlueprint.class.getName() + ".client-x509-certificate";

    @ConfiguredOption
    Set<String> enabledTlsProtocols();

    // TODO: had to make this Optional - we might need something like 'ExternalConfigBean' for this case ?
    Optional<SSLContext> sslContext();

    @Prototype.Singular("cipher")
    @ConfiguredOption(key = "cipher")
        //    Set<String> cipherSuite();
    List<String> cipherSuite();

    /**
     * Whether this TLS config has security enabled (and the socket is going to be
     * protected by one of the TLS protocols), or no (and the socket is going to be plain).
     *
     * @return {@code true} if this configuration represents a TLS configuration, {@code false} for plain configuration
     */
    @ConfiguredOption("false")
    boolean enabled();

}
