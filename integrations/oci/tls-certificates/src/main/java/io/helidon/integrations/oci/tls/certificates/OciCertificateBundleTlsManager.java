/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.tls.TlsManager;
import io.helidon.config.Config;

/**
 * A {@link TlsManager} that loads a certificate and its matching private key from an OCI-managed certificate bundle.
 */
public interface OciCertificateBundleTlsManager
        extends TlsManager, RuntimeType.Api<OciCertificateBundleTlsManagerConfig> {

    /**
     * Creates a default manager instance.
     *
     * @return a default instance
     */
    static OciCertificateBundleTlsManager create() {
        return builder().build();
    }

    /**
     * Creates a configured manager instance.
     *
     * @param config the config
     * @return a configured instance
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    static OciCertificateBundleTlsManager create(io.helidon.common.config.Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a configured manager instance.
     *
     * @param config the config
     * @return a configured instance
     */
    static OciCertificateBundleTlsManager create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a manager from its prototype.
     *
     * @param config the manager config
     * @return a configured instance
     */
    static OciCertificateBundleTlsManager create(OciCertificateBundleTlsManagerConfig config) {
        return new DefaultOciCertificateBundleTlsManager(config);
    }

    /**
     * Creates a manager builder.
     *
     * @return a builder
     */
    static OciCertificateBundleTlsManagerConfig.Builder builder() {
        return OciCertificateBundleTlsManagerConfig.builder();
    }

    /**
     * Creates a manager configured by a builder consumer.
     *
     * @param consumer builder consumer
     * @return a configured instance
     */
    static OciCertificateBundleTlsManager create(Consumer<OciCertificateBundleTlsManagerConfig.Builder> consumer) {
        var builder = OciCertificateBundleTlsManagerConfig.builder();
        consumer.accept(builder);
        return builder.build();
    }
}
