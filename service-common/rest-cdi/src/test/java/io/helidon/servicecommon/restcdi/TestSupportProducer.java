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
package io.helidon.servicecommon.restcdi;

import io.helidon.config.mp.MpConfig;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

/**
 * Producer of the test service so the resource can inject the service.
 */
@ApplicationScoped
public class TestSupportProducer {

    static final String CONFIG_KEY = "test";

    @Produces
    static public ConfiguredTestSupport getConfiguredTestSupport() {
        Config mpConfig = ConfigProvider.getConfig();
        return ConfiguredTestSupport.builder().config(
                        MpConfig.toHelidonConfig(mpConfig).get(CONFIG_KEY))
                .build();
    }
}
