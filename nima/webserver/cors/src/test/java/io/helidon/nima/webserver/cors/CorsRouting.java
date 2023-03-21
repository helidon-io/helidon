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

package io.helidon.nima.webserver.cors;

import io.helidon.common.http.Http;
import io.helidon.common.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.cors.CrossOriginConfig;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webserver.http.HttpRules;

import static io.helidon.nima.webserver.cors.CorsTestServices.SERVICE_3;

abstract class CorsRouting {
    @SetUpRoute
    static void routing(HttpRules routing) {
        CrossOriginConfig cors3COC = CrossOriginConfig.builder()
                .allowOrigins("http://foo.bar", "http://bar.foo")
                .allowMethods("DELETE", "PUT")
                .build();

        /*
         * Use the default config for the service at "/greet" and then programmatically add the config for /cors3.
         */
        CorsSupport.Builder corsSupportBuilder = CorsSupport.builder().name("CORS SE " + SERVICE_3);
        corsSupportBuilder.addCrossOrigin(SERVICE_3.path(), cors3COC);

        /*
         * Use the loaded config to build a CrossOriginConfig for /cors4.
         */
        /*
         * Load a specific config for "/othergreet."
         */
        Config twoCORSConfig = Config.just(ConfigSources.classpath("twoCORS.yaml"));
        Config twoCORSMappedConfig = twoCORSConfig.get("cors-2-setup");
        if (!twoCORSMappedConfig.exists()) {
            throw new IllegalArgumentException("Expected config 'cors-2-setup' from twoCORS.yaml not found");
        }
        Config somewhatRestrictedConfig = twoCORSConfig.get("somewhat-restrictive");
        if (!somewhatRestrictedConfig.exists()) {
            throw new IllegalArgumentException("Expected config 'somewhat-restrictive' from twoCORS.yaml not found");
        }
        Config corsMappedSetupConfig = Config.create().get("cors-setup");
        if (!corsMappedSetupConfig.exists()) {
            throw new IllegalArgumentException("Expected config 'cors-setup' from default app config not found");
        }

        routing.register(TestUtil.GREETING_PATH,
                         CorsSupport.createMapped(corsMappedSetupConfig),
                         new TestUtil.GreetService())
                .register(TestUtil.OTHER_GREETING_PATH,
                          CorsSupport.createMapped(twoCORSMappedConfig),
                          new TestUtil.GreetService("Other Hello"))
                .any(TestHandlerRegistration.CORS4_CONTEXT_ROOT,
                     CorsSupport.create(somewhatRestrictedConfig), // handler settings from config subnode
                     (req, resp) -> resp.status(Http.Status.OK_200).send())
                .get(TestHandlerRegistration.CORS4_CONTEXT_ROOT,                       // handler settings in-line
                     CorsSupport.builder()
                             .allowOrigins("*")
                             .allowMethods("GET")
                             .build(),
                     (req, resp) -> resp.status(Http.Status.OK_200).send());
    }
}
