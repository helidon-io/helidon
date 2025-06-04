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

package io.helidon.webclient.context;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.webclient.WebClientRequestHeaders;
import io.helidon.webclient.WebClientServiceRequest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebClientContextPropagationTest {
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

        ArgumentCaptor<String> headerName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);
        WebClientRequestHeaders headers = mock(WebClientRequestHeaders.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.context()).thenReturn(requestContext);
        when(serviceRequest.headers()).thenReturn(headers);
        propagation.request(serviceRequest);

        verify(headers, atLeast(3)).put(headerName.capture(), headerValues.capture());
        assertThat(headerName.getAllValues(), hasItems("x_helidon_no_default", "x_helidon_tid", "x_helidon_cid"));
        assertThat(headerValues.getAllValues(), hasItems("first",
                                                         "second",
                                                         "third"));
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

        ArgumentCaptor<String> headerName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);
        WebClientRequestHeaders headers = mock(WebClientRequestHeaders.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.context()).thenReturn(requestContext);
        when(serviceRequest.headers()).thenReturn(headers);
        propagation.request(serviceRequest);

        verify(headers, atLeast(3)).put(headerName.capture(), headerValues.capture());
        assertThat(headerName.getAllValues(), hasItems("x_helidon_no_default", "x_helidon_tid", "x_helidon_cid"));
        assertThat(headerValues.getAllValues(), hasItems("first",
                                                         "second",
                                                         "third",
                                                         "fourth"));

    }

    @Test
    void contextPropagationWithDefaultedValues() {
        Context requestContext = Context.create();

        ArgumentCaptor<String> headerName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValues = ArgumentCaptor.forClass(String.class);
        WebClientRequestHeaders headers = mock(WebClientRequestHeaders.class);
        WebClientServiceRequest serviceRequest = mock(WebClientServiceRequest.class);
        when(serviceRequest.context()).thenReturn(requestContext);
        when(serviceRequest.headers()).thenReturn(headers);
        propagation.request(serviceRequest);

        verify(headers, atLeast(2)).put(headerName.capture(), headerValues.capture());
        assertThat(headerName.getAllValues(), hasItems("x_helidon_tid", "x_helidon_cid"));
        assertThat(headerValues.getAllValues(), hasItems("unknown",
                                                         "first",
                                                         "second"));
    }
}