/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import io.helidon.common.CollectionsHelper;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.ProviderSelectionPolicy;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link CompositeOutboundProvider}.
 */
public abstract class CompositePolicyTest {
    abstract ProviderSelectionPolicy getPsp();

    abstract Security getSecurity();

    private OutboundSecurityProvider getOutbound() {
        return getPsp().selectOutboundProviders().get(0);
    }

    private AuthenticationProvider getAuthentication() {
        return getPsp().selectProvider(AuthenticationProvider.class).get();
    }

    private AuthorizationProvider getAuthorization() {
        return getPsp().selectProvider(AuthorizationProvider.class).get();
    }

    // test with full security
    @Test
    public void testSuccessSecurity() {
        SecurityContext context = getSecurity().contextBuilder("testSuccessSecurity").build();
        SecurityEnvironment.Builder envBuilder = context.getEnv().derive()
                .path("/jack")
                .addAttribute("resourceType", "service");
        context.setEnv(envBuilder);

        AuthenticationResponse atnResponse = context.authenticate();

        assertThat(atnResponse, notNullValue());
        assertThat(atnResponse.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));

        Subject subject = atnResponse.getService().get();
        assertThat(subject.getPrincipal().getName(), is("resource-aService"));

        OutboundSecurityResponse outboundResponse = context.outboundClientBuilder()
                .outboundEnvironment(envBuilder)
                .outboundEndpointConfig(EndpointConfig.create())
                .buildAndGet();

        assertThat(outboundResponse.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));

        Map<String, List<String>> headers = outboundResponse.getRequestHeaders();
        assertThat(headers.size(), is(2));

        List<String> path = headers.get("path");
        List<String> resource = headers.get("resource");

        assertThat(path, notNullValue());
        assertThat(path.size(), is(1));
        assertThat(resource, notNullValue());
        assertThat(resource.size(), is(1));

        String pathHeader = path.iterator().next();
        String resourceHeader = resource.iterator().next();

        assertThat(pathHeader, is("path-jack"));
        assertThat(resourceHeader, is("resource-aService"));
    }

    @Test
    public void testAtz() {
        AuthorizationResponse response = SecurityResponse
                .get(getAuthorization().authorize((context("/atz/permit", "atz/permit"))));

        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));

        response = SecurityResponse
                .get(getAuthorization().authorize((context("/atz/abstain", "atz/permit"))));

        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));

        response = SecurityResponse
                .get(getAuthorization().authorize((context("/atz/abstain", "atz/abstain"))));

        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.FAILURE));
    }

    @Test
    public void testAtnAllSuccess() throws ExecutionException, InterruptedException {
        AuthenticationResponse response = getAuthentication().authenticate(context("/jack", "service"))
                .toCompletableFuture()
                .get();
        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));

        Subject user = response.getUser().get();
        assertThat(user.getPrincipal().getName(), is("path-jack"));
        Subject service = response.getService().get();
        assertThat(service.getPrincipal().getName(), is("resource-aService"));
    }

    @Test
    public void testAtnAllSuccessServiceFirst() throws ExecutionException, InterruptedException {
        AuthenticationResponse response = getAuthentication().authenticate(context("/service", "jack"))
                .toCompletableFuture()
                .get();
        assertThat(response, notNullValue());
        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));
        Subject user = response.getUser().get();
        assertThat(user.getPrincipal().getName(), is("resource-jack"));
        Subject service = response.getService().get();
        assertThat(service.getPrincipal().getName(), is("path-aService"));
    }

    @Test
    public void testOutboundSuccess() throws ExecutionException, InterruptedException {
        ProviderRequest context = context("/jack", "service");

        assertThat(getOutbound().isOutboundSupported(context, context.getEnv(), context.getEndpointConfig()), is(true));

        OutboundSecurityResponse response = getOutbound().outboundSecurity(context,
                                                                           context.getEnv(),
                                                                           context.getEndpointConfig()).toCompletableFuture()
                .get();

        assertThat(response.getStatus(), is(SecurityResponse.SecurityStatus.SUCCESS));

        Map<String, List<String>> headers = response.getRequestHeaders();
        assertThat(headers.size(), is(2));

        List<String> path = headers.get("path");
        List<String> resource = headers.get("resource");

        assertThat(path, notNullValue());
        assertThat(path.size(), is(1));
        assertThat(resource, notNullValue());
        assertThat(resource.size(), is(1));

        String pathHeader = path.iterator().next();
        String resourceHeader = resource.iterator().next();

        assertThat(pathHeader, is("path-jack"));
        assertThat(resourceHeader, is("resource-aService"));
    }

    private ProviderRequest context(String path, String resource) {
        ProviderRequest mock = Mockito.mock(ProviderRequest.class);
        SecurityEnvironment se = SecurityEnvironment.builder(SecurityTime.create())
                .path(path)
                .headers(CollectionsHelper.mapOf())
                .addAttribute("resourceType", resource)
                .build();

        when(mock.getEnv()).thenReturn(se);

        return mock;
    }
}
