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

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.openapi.OpenApiUi;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

@HelidonTest
@AddBean(GreetService.class)
@AddBean(GreetingProvider.class)
@AddConfig(key = "openapi.web-context", value = CdiNonDefaultOpenApiWebContextTests.NON_DEFAULT_OPENAPI_WEB_CONTEXT)
class CdiNonDefaultOpenApiWebContextTests {

    static final String NON_DEFAULT_OPENAPI_WEB_CONTEXT = "/my-openapi";

    @Inject
    private WebTarget webTarget;

    @Test
    void testNonDefaultOpenApiPathWithSuffix() {
        CdiTestsUtil.checkForPath(webTarget, NON_DEFAULT_OPENAPI_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT);
    }

    @Test
    void testNonDefaultOpenApiPathWithoutSuffix() {
        CdiTestsUtil.checkForPath(webTarget, NON_DEFAULT_OPENAPI_WEB_CONTEXT);
    }
}
