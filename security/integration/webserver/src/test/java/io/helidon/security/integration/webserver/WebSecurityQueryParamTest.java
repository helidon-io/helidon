/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.integration.webserver;

import java.util.List;
import java.util.regex.Pattern;

import io.helidon.common.http.Parameters;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.util.TokenHandler;
import io.helidon.webserver.ServerRequest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for extraction of query parameters from request.
 */
public class WebSecurityQueryParamTest {
    @Test
    public void testQueryParams() {
        SecurityHandler securityHandler = SecurityHandler.create()
                .queryParam(
                        "jwt",
                        TokenHandler.builder()
                                .tokenHeader("BEARER_TOKEN")
                                .tokenPattern(Pattern.compile("bearer (.*)"))
                                .build())
                .queryParam(
                        "name",
                        TokenHandler.builder()
                                .tokenHeader("NAME_FROM_REQUEST")
                                .build());

        ServerRequest req = Mockito.mock(ServerRequest.class);

        Parameters params = Mockito.mock(Parameters.class);
        when(params.all("jwt")).thenReturn(List.of("bearer jwt_content"));
        when(params.all("name")).thenReturn(List.of("name_content"));
        when(req.queryParams()).thenReturn(params);

        SecurityContext context = Mockito.mock(SecurityContext.class);
        SecurityEnvironment env = SecurityEnvironment.create();
        when(context.env()).thenReturn(env);

        // context is a stub
        securityHandler.extractQueryParams(context, req);
        // captor captures the argument
        ArgumentCaptor<SecurityEnvironment> newHeaders = ArgumentCaptor.forClass(SecurityEnvironment.class);
        verify(context).env(newHeaders.capture());
        // now validate the value we were called with
        env = newHeaders.getValue();
        assertThat(env.headers().get("BEARER_TOKEN"), is(List.of("jwt_content")));
        assertThat(env.headers().get("NAME_FROM_REQUEST"), is(List.of("name_content")));
    }
}
