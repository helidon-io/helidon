/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico.config.testsubjects;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.pico.builder.api.ConfiguredOption;
import io.helidon.pico.config.api.ConfigBean;

/**
 * Configuration for {@link ASingletonConfiguredService}.
 *
 * This extends {@link MySimpleConfig} to ensure that the configuration is a
 * singleton at runtime, furnishing the default config bean if no "real" configuration exists.
 */
@ConfigBean(key = "my-singleton-config",
            repeatable = false,
            defaultConfigBeanUsingDefaults = true,
            drivesActivation = false)
public interface ASingletonConfig extends MySimpleConfig {

    @Override
    @ConfiguredOption("8080")
    int port();

    @ConfiguredOption(value = "127.0.0.1", required = true)
    String hostAddress();

    @ConfiguredOption(required = false)
    char[] password();

    Optional<MySimpleConfig> theSimpleConfig();
    List<MySimpleConfig> listOfSimpleConfig();
    Set<MySimpleConfig> setOfSimpleConfig();
    Map<String, MySimpleConfig> mapOfSimpleConfig();

    // TODO: these keys should be relative to our config bean:

//    @ConfiguredOption(key = "my-singleton-config")
    Optional<ASingletonConfig> theSingletonConfig();

//    @ConfiguredOption(key = "my-singleton-config")
    List<ASingletonConfig> listOfSingletonConfigConfig();

//    @ConfiguredOption(key = "my-singleton-config")
    Set<ASingletonConfig> setOfSingletonConfig();

    @ConfiguredOption(key = "my-singleton-config")
    Map<String, ASingletonConfig> mapOfSingletonConfig();

}
