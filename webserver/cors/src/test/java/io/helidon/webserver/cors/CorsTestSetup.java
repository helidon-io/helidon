/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import java.time.Duration;

import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;

final class CorsTestSetup {
    private static final String CORS4_CONTEXT_ROOT = "/cors4";

    private CorsTestSetup() {
    }

    static CorsFeature feature() {
        return CorsFeature.create(builder -> builder
                .enabled(true)
                .addDefaults(false)
                .addPath(CorsPathConfig.builder()
                                 .pathPattern(TestUtil.path(TestUtil.GREETING_PATH, CorsTestServices.SERVICE_1))
                                 .build())
                .addPath(CorsPathConfig.builder()
                                 .pathPattern(TestUtil.path(TestUtil.GREETING_PATH, CorsTestServices.SERVICE_2))
                                 .clearAllowOrigins()
                                 .addAllowOrigin("http://foo.bar")
                                 .addAllowOrigin("http://bar.foo")
                                 .clearAllowMethods()
                                 .addAllowMethod(Method.DELETE)
                                 .addAllowMethod(Method.PUT)
                                 .clearAllowHeaders()
                                 .addAllowHeader("X-bar")
                                 .addAllowHeader("X-foo")
                                 .allowCredentials(true)
                                 .maxAge(Duration.ofSeconds(-1))
                                 .build())
                .addPath(CorsPathConfig.builder()
                                 .pathPattern(TestUtil.path(TestUtil.OTHER_GREETING_PATH, CorsTestServices.SERVICE_2))
                                 .clearAllowOrigins()
                                 .addAllowOrigin("http://otherfoo.bar")
                                 .addAllowOrigin("http://otherbar.foo")
                                 .clearAllowMethods()
                                 .addAllowMethod(Method.DELETE)
                                 .addAllowMethod(Method.PUT)
                                 .clearAllowHeaders()
                                 .addAllowHeader("X-otherBar")
                                 .addAllowHeader("X-otherFoo")
                                 .allowCredentials(true)
                                 .maxAge(Duration.ofSeconds(-1))
                                 .build())
                .addPath(CorsPathConfig.builder()
                                 .pathPattern(CORS4_CONTEXT_ROOT)
                                 .clearAllowOrigins()
                                 .addAllowOrigin("http://foo.bar")
                                 .addAllowOrigin("http://bar.foo")
                                 .clearAllowMethods()
                                 .addAllowMethod(Method.PUT)
                                 .build()));
    }

    static void routing(HttpRules rules) {
        rules.register(TestUtil.GREETING_PATH, new TestUtil.GreetService())
                .register(TestUtil.OTHER_GREETING_PATH, new TestUtil.GreetService("Other Hello"))
                .any(CORS4_CONTEXT_ROOT, (req, resp) -> resp.status(Status.OK_200).send())
                .get(CORS4_CONTEXT_ROOT, (req, resp) -> resp.status(Status.OK_200).send());
    }
}
