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

package io.helidon.webclient.security;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webclient.spi.WebClientServiceProvider;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@Testing.Test(perMethod = true)
class WebClientSecurityProviderTest {

    @Test
    void explicitProviderOverridesServiceLoaderProvider() {
        AtomicInteger createCount = new AtomicInteger();
        WebClientService replacementService = (chain, request) -> chain.proceed(request);
        WebClientServiceProvider replacementProvider = new WebClientServiceProvider() {
            @Override
            public String configKey() {
                return "security";
            }

            @Override
            public WebClientService create(Config config, String name) {
                createCount.incrementAndGet();
                return replacementService;
            }
        };
        Services.set(WebClientServiceProvider.class, replacementProvider);

        Http1Client client = Http1Client.builder().build();
        try {
            assertThat(client.prototype().services(), hasItem(sameInstance(replacementService)));
            assertThat("Replacement provider create count", createCount.get(), is(1));
        } finally {
            client.closeResource();
        }
    }
}
