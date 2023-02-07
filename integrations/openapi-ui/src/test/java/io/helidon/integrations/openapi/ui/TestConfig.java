/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.openapi.ui;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.openapi.OpenAPISupport;
import io.helidon.openapi.OpenApiUi;

import io.smallrye.openapi.ui.Option;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestConfig {

    @Test
    void checkOptionsConfig() {
        String newTitle = "New Title";
        String newContext = "/overThere";
        String newFooter = "New Footer";
        Map<String, String> settings = Map.of("openapi.ui.options.title", newTitle,
                                              "openapi.ui.options.footer", newFooter,
                                              "openapi.ui.options.notThere", "anything",
                                              "openapi.web-context", newContext);

        Config config = Config.just(ConfigSources.create(settings));
        Config openApiConfig = config.get(OpenAPISupport.Builder.CONFIG_KEY);
        OpenApiUiFull.Builder uiSupportBuilder = OpenApiUiFull.builder()
                .config(openApiConfig.get(OpenApiUi.Builder.OPENAPI_UI_CONFIG_KEY));
        OpenAPISupport.builder() // Side effect: trigger conversion of string options to SmallRye Options options.
                .config(openApiConfig)
                .ui(uiSupportBuilder)
                .build();

        // Check a simple option setting.
        assertThat("Overridden title value", uiSupportBuilder.uiOptions().get(Option.title), is(newTitle));
        assertThat("Overridden footer value", uiSupportBuilder.uiOptions().get(Option.footer), is(newFooter));
    }
}
