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

package io.helidon.integrations.oci.cdi;

import java.util.List;
import java.util.ServiceLoader;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.integrations.oci.connect.OciRestApi;
import io.helidon.integrations.oci.connect.spi.InjectionProvider;
import io.helidon.microprofile.cdi.RuntimeStart;

public class OciCdiExtension implements Extension {
    private Config config;

    private void configure(@Observes @RuntimeStart Config config) {
        this.config = config.get("oci");
    }

    void registerProducers(@Observes AfterBeanDiscovery abd) {
        abd.addBean()
                .types(OciRestApi.class)
                .beanClass(OciCdiExtension.class)
                .scope(ApplicationScoped.class)
                .createWith(ignored -> OciRestApi.create(config));

        List<InjectionProvider> providers = HelidonServiceLoader
                .builder(ServiceLoader.load(InjectionProvider.class))
                .build()
                .asList();

        for (InjectionProvider provider : providers) {
            abd.addBean()
                    .types(provider.types())
                    .beanClass(OciCdiExtension.class)
                    .scope(ApplicationScoped.class)
                    .createWith(ignored -> {
                        OciRestApi restApi = CDI.current()
                                .select(OciRestApi.class)
                                .get();

                        return provider.createInstance(restApi, config);
                    });
        }
    }
}
