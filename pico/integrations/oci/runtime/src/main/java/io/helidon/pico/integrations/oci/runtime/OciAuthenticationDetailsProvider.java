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
import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.common.types.AnnotationAndValue;
import io.helidon.pico.api.Bootstrap;
import io.helidon.pico.api.ContextualServiceQuery;
import io.helidon.pico.api.InjectionPointInfo;
import io.helidon.pico.api.InjectionPointProvider;
import io.helidon.pico.api.PicoServices;
import io.helidon.pico.api.ServiceInfoBasics;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import static io.helidon.common.types.AnnotationAndValueDefault.findFirst;

@Singleton
@Weight(ServiceInfoBasics.DEFAULT_PICO_WEIGHT)
class OciAuthenticationDetailsProvider implements InjectionPointProvider<AbstractAuthenticationDetailsProvider> {
    static final LazyValue<OciConfigBean> DEFAULT_OCI_CONFIG_BEAN = LazyValue.create(() -> OciConfigBeanDefault.builder()
            .authStrategies(Arrays.stream(AuthStrategy.values()).map(AuthStrategy::id).toList())
            .build());

    OciAuthenticationDetailsProvider() {
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> first(ContextualServiceQuery query) {
        String requestedNamedProfile = toNamedProfile(query.injectionPointInfo().orElse(null));
        OciConfigBean ociConfig = ociConfig();
        return Optional.of(select(requestedNamedProfile, ociConfig));
    }

    static AbstractAuthenticationDetailsProvider select(String requestedNamedProfile,
                                                        OciConfigBean ociConfig) {
        // TODO:
        return null;
    }

    static String toNamedProfile(InjectionPointInfo ipi) {
        if (ipi == null) {
            return null;
        }

        Optional<? extends AnnotationAndValue> named = findFirst(Named.class.getName(), ipi.annotations());
        if (named.isEmpty()) {
            return null;
        }

        String nameProfile = named.get().value().orElse(null);
        if (nameProfile == null || nameProfile.isBlank()) {
            return null;
        }

        return nameProfile.trim();
    }

    static OciConfigBean ociConfig() {
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


    enum AuthStrategy {
        AUTO("auto"),
        CONFIG("config"),
        CONFIG_FILE("config-file"),
        INSTANCE_PRINCIPALS("instance-principals"),
        RESOURCE_PRINCIPAL("resource-principal");

        private final String id;

        AuthStrategy(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        static Optional<AuthStrategy> fromNameOrId(String nameOrId) {
            try {
                return Optional.of(valueOf(nameOrId));
            } catch (Exception e) {
                return Arrays.stream(AuthStrategy.values())
                        .filter(it -> nameOrId.equals(it.id()))
                        .findFirst();
            }
        }
    }

}
