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
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.openapi.OpenAPISupport;
import io.helidon.openapi.OpenApiUi;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(GreetResource.class)
@AddBean(GreetingProvider.class)
class CdiDefaultsTest {

    private static final String NON_DEFAULT_OPENAPI_WEB_CONTEXT = "/my-openapi";

    @Inject
    private WebTarget webTarget;

    @Test
    void testDefaultPathWithSuffix() {
        CdiTestsUtil.checkForPath(webTarget, OpenAPISupport.DEFAULT_WEB_CONTEXT + OpenApiUi.UI_WEB_SUBCONTEXT);
    }

    @Test
    void testDefaultPathWithoutSuffix() {
        CdiTestsUtil.checkForPath(webTarget, OpenAPISupport.DEFAULT_WEB_CONTEXT);
    }

}
