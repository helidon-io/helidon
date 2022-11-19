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

package io.helidon.pico.builder.config.test.testsubjects;

import java.util.Map;

import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.pico.builder.config.ConfigBean;
import io.helidon.pico.builder.config.testsubjects.CommonConfig;

@ConfigBean(drivesActivation = false)
public interface ClientConfig extends CommonConfig {

    @ConfiguredOption("default")
    @Override
    String name();

    int serverPort();

    Map<String, String> headers();

}
