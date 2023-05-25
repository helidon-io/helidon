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

package io.helidon.pico.integrations.oci.runtime;

import java.util.Arrays;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.PicoServices;

import static java.util.function.Predicate.not;

/**
 * Provides entry point access into OCI access.
 */
public final class OciExtension {
    static final LazyValue<OciConfigBean> DEFAULT_OCI_CONFIG_BEAN = LazyValue.create(() -> OciConfigBeanDefault.builder()
            .authStrategies(Arrays.stream(OciAuthenticationDetailsProvider.AuthStrategy.values())
                                    .filter(not(it -> it == OciAuthenticationDetailsProvider.AuthStrategy.AUTO))
                                    .map(OciAuthenticationDetailsProvider.AuthStrategy::id)
                                    .toList())
            .build());

    /**
     * Returns the {@link OciConfigBean} that is currently defined in the bootstrap environment. If one is not defined under
     * config {@link OciConfigBean#NAME} then a default implementation will be constructed.
     *
     * @return the bootstrap oci config bean
     */
    public static OciConfigBean ociConfig() {
        Optional<Bootstrap> bootstrap = PicoServices.globalBootstrap();
        if (bootstrap.isEmpty()) {
            return DEFAULT_OCI_CONFIG_BEAN.get();
        }

        Config config = bootstrap.get().config().orElse(null);
        if (config == null) {
            return DEFAULT_OCI_CONFIG_BEAN.get();
        }

        config = config.get(OciConfigBean.NAME);
        if (!config.exists()) {
            return DEFAULT_OCI_CONFIG_BEAN.get();
        }

        return OciConfigBeanDefault.toBuilder(config);
    }

}
