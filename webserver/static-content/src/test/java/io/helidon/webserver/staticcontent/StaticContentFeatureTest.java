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

package io.helidon.webserver.staticcontent;

import java.util.Set;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.spi.ServerFeature.ServerFeatureContext;
import io.helidon.webserver.spi.ServerFeature.SocketBuilders;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaticContentFeatureTest {

    @Test
    void testCrossOriginSourcingFeatureDefaultAndHandlerOverride() {
        StaticContentFeature feature = StaticContentFeature.create(builder -> builder
                .preCompressedCrossOriginSourcingEnabled(true)
                .addClasspath(classpath -> classpath
                        .context("/inherited")
                        .location("/web"))
                .addClasspath(classpath -> classpath
                        .context("/overridden")
                        .location("/web")
                        .preCompressedCrossOriginSourcingEnabled(false)));

        ServerFeatureContext featureContext = mock(ServerFeatureContext.class);
        SocketBuilders socketBuilders = mock(SocketBuilders.class);
        HttpRouting.Builder routing = mock(HttpRouting.Builder.class);
        when(featureContext.sockets()).thenReturn(Set.of());
        when(featureContext.socketExists(WebServer.DEFAULT_SOCKET_NAME)).thenReturn(true);
        when(featureContext.socket(WebServer.DEFAULT_SOCKET_NAME)).thenReturn(socketBuilders);
        when(socketBuilders.httpRouting()).thenReturn(routing);

        feature.setup(featureContext);

        ArgumentCaptor<HttpService> inheritedService = ArgumentCaptor.forClass(HttpService.class);
        verify(routing).register(eq("/inherited"), inheritedService.capture());
        ClassPathContentHandler inherited = (ClassPathContentHandler) inheritedService.getValue();
        assertThat(inherited.preCompressedCrossOriginSourcingEnabled(), is(true));

        ArgumentCaptor<HttpService> overriddenService = ArgumentCaptor.forClass(HttpService.class);
        verify(routing).register(eq("/overridden"), overriddenService.capture());
        ClassPathContentHandler overridden = (ClassPathContentHandler) overriddenService.getValue();
        assertThat(overridden.preCompressedCrossOriginSourcingEnabled(), is(false));
    }

    @Test
    void testDirectServiceDefaultsCrossOriginSourcingToFalse() {
        ClassPathContentHandler handler = (ClassPathContentHandler) StaticContentFeature.createService(
                ClasspathHandlerConfig.builder()
                        .location("/web")
                        .build());

        assertThat(handler.preCompressedCrossOriginSourcingEnabled(), is(false));
    }
}
