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
package io.helidon.integrations.openapi.ui;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.openapi.OpenAPISupport;

import io.smallrye.openapi.ui.Option;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestConfig {

    @Test
    void checkOptionsConfig() {
        String newTitle = "New Title";
        String newContext = "/overThere";
        Map<String, String> settings = Map.of("openapi.ui.options.title", newTitle,
                                              "openapi.ui.options.notThere", "anything",
                                              "openapi.web-context", newContext);

        Config config = Config.create(ConfigSources.create(settings));
        Config openApiConfig = config.get(OpenAPISupport.Builder.CONFIG_KEY);
        OpenAPISupport openAPISupport = OpenAPISupport.create(openApiConfig);
        OpenApiUiSupport uiSupport = OpenApiUiSupport.builder(openAPISupport)
                .config(openApiConfig.get(OpenApiUiSupport.OPENAPI_UI_SUBCONFIG_KEY))
                .build();

        // Check a simple option setting.
        assertThat("Overridden title value", uiSupport.options().get(Option.title), is(newTitle));

        // The OpenApiUiSupport.Builder should set certain options using the value from OpenAPISupport or OpenApiUi.
        assertThat("Option for OpenAPI endpoint", uiSupport.options().get(Option.url), is(newContext));
    }
}
