/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webclient.context;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webclient.api.WebClientServiceRequest;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebClientContextPropagationTest {
    private static final HeaderName NO_DEFAULT = HeaderNames.create("x_helidon_no_default");
    private static final HeaderName TID = HeaderNames.create("x_helidon_tid");
    private static final HeaderName CID = HeaderNames.create("x_helidon_cid");

    private static WebClientContextService propagation;

    @BeforeAll
    static void init() {
        propagation = WebClientContextService.create(Config.create().get("unit-1"));
    }

    @Test
    void contextPropagationWithAllValuesNoArray() {
        String noDefault = "first";
        String tid = "second";
        String cid = "third";

        Context requestContext = Context.create();
        requestContext.register("io.helidon.webclient.context.propagation.no-default", noDefault);
        requestContext.register("io.helidon.webclient.context.propagation.tid", new String[] {tid});
        requestContext.register("io.helidon.webclient.context.propagation.cid", new String[] {cid});

        ArgumentCaptor<HeaderName> headerName = ArgumentCaptor.forClass(HeaderName.class);
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);
        ClientRequestHeaders headers = mock(ClientRequestHeaders.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.context()).thenReturn(requestContext);
        when(serviceRequest.headers()).thenReturn(headers);
        WebClientService.Chain chain = mock(WebClientService.Chain.class);
        propagation.handle(chain, serviceRequest);

        verify(headers, atLeast(3))
                .set(headerName.capture(), headerValues.capture());
        assertThat(headerName.getAllValues(), hasItems(NO_DEFAULT, TID, CID));
        assertThat(headerValues.getAllValues(), hasItems("first", "second", "third"));
    }

    @Test
    void contextPropagationWithAllValuesArray() {
        String noDefault = "first";
        String tid = "second";
        String[] cid = new String[] {"third", "fourth"};

        Context requestContext = Context.create();
        requestContext.register("io.helidon.webclient.context.propagation.no-default", noDefault);
        requestContext.register("io.helidon.webclient.context.propagation.tid", new String[] {tid});
        requestContext.register("io.helidon.webclient.context.propagation.cid", cid);

        ArgumentCaptor<HeaderName> headerName = ArgumentCaptor.forClass(HeaderName.class);
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);
        ClientRequestHeaders headers = mock(ClientRequestHeaders.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.context()).thenReturn(requestContext);
        when(serviceRequest.headers()).thenReturn(headers);
        WebClientService.Chain chain = mock(WebClientService.Chain.class);
        propagation.handle(chain, serviceRequest);

        verify(headers, atLeast(3))
                .set(headerName.capture(), headerValues.capture());
        assertThat(headerName.getAllValues(), hasItems(NO_DEFAULT, TID, CID));
        assertThat(headerValues.getAllValues(), hasItems("first",
                                                         "second",
                                                         "third",
                                                         "fourth"));

    }

    @Test
    void contextPropagationWithDefaultedValues() {
        Context requestContext = Context.create();

        ArgumentCaptor<HeaderName> headerName = ArgumentCaptor.forClass(HeaderName.class);
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);
        ClientRequestHeaders headers = mock(ClientRequestHeaders.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.context()).thenReturn(requestContext);
        when(serviceRequest.headers()).thenReturn(headers);
        WebClientService.Chain chain = mock(WebClientService.Chain.class);
        propagation.handle(chain, serviceRequest);

        verify(headers, atLeast(2))
                .set(headerName.capture(), headerValues.capture());
        assertThat(headerName.getAllValues(), hasItems(TID, CID));
        assertThat(headerValues.getAllValues(), hasItems("unknown",
                                                         "first",
                                                         "second"));
    }
}