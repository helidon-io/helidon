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

package io.helidon.webclient.api;

import java.net.URI;
import java.util.stream.Stream;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedirectSecurityStateTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("originBoundaryStates")
    void wouldCrossOriginRejectsNullInputsBeforeStateChecks(String description,
                                                            RedirectSecurityState state,
                                                            ClientUri target,
                                                            boolean expectedCrossing) {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());

        assertThat(description, state.wouldCrossOrigin(target, headers), is(expectedCrossing));
        assertAll(() -> assertThrows(NullPointerException.class, () -> state.wouldCrossOrigin(null, headers)),
                  () -> assertThrows(NullPointerException.class, () -> state.wouldCrossOrigin(target, null)));
        assertThat(description, state.wouldCrossOrigin(target, headers), is(expectedCrossing));
    }

    @Test
    void normalizesDefaultPortsAndHostNames() {
        ClientRequestOrigin implicitHttp = ClientRequestOrigin.create(uri("http://EXAMPLE.com/path"));
        ClientRequestOrigin explicitHttp = ClientRequestOrigin.create(uri("http://example.com:80/other"));
        ClientRequestOrigin implicitHttps = ClientRequestOrigin.create(uri("https://example.com/path"));
        ClientRequestOrigin explicitHttps = ClientRequestOrigin.create(uri("https://example.com:443/other"));

        assertThat(implicitHttp, is(explicitHttp));
        assertThat(implicitHttps, is(explicitHttps));
        assertThat(implicitHttp, is(not(implicitHttps)));
    }

    @Test
    void normalizesBracketedIpv6Origins() {
        ClientRequestOrigin implicit = ClientRequestOrigin.create(uri("https://[2001:db8::1]/path"));
        ClientRequestOrigin explicit = ClientRequestOrigin.create(uri("https://[2001:db8::1]:443/other"));
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderValues.create(HeaderNames.HOST, "[2001:db8::1]"));

        ClientRequestOrigin effective = ClientRequestOrigin.create(uri("https://route.invalid/path"), headers);
        ClientUri applied = effective.apply(uri("https://route.invalid/path"));

        assertThat(implicit, is(explicit));
        assertThat(implicit, is(effective));
        assertThat(implicit.toString(), is("https://[2001:db8::1]:443"));
        assertThat(applied.authority(), is("[2001:db8::1]:443"));
        assertThat(ClientRequestOrigin.create(applied), is(effective));
    }

    @Test
    void usesFinalHostAsEffectiveOriginWithoutChangingUriOrigin() {
        ClientUri requestUri = uri("https://route.invalid:8443/path");
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        headers.set(HeaderValues.create(HeaderNames.HOST, "TARGET.invalid:9443"));

        ClientRequestOrigin uriOrigin = ClientRequestOrigin.create(requestUri);
        ClientRequestOrigin effectiveOrigin = ClientRequestOrigin.create(requestUri, headers);

        assertThat(uriOrigin.toString(), is("https://route.invalid:8443"));
        assertThat(effectiveOrigin.toString(), is("https://target.invalid:9443"));
    }

    @Test
    void usesUriOriginWhenHostIsAbsent() {
        ClientUri requestUri = uri("https://route.invalid:8443/path");
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());

        ClientRequestOrigin effectiveOrigin = ClientRequestOrigin.create(requestUri, headers);

        assertThat(effectiveOrigin.toString(), is("https://route.invalid:8443"));
    }

    @Test
    void ordinaryFinalizationDoesNotCreateRedirectBoundary() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        RedirectSecurityState state = RedirectSecurityState.initial()
                .finalized(uri("https://first.invalid/path"), headers)
                .finalized(uri("https://service-rewrite.invalid/path"), headers);

        assertThat(state.redirectPending(), is(false));
        assertThat(state.crossedOrigin(), is(false));
    }

    @Test
    void comparesBothUriAndEffectiveOriginsAtRedirectBoundary() {
        ClientRequestHeaders sourceHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        sourceHeaders.set(HeaderValues.create(HeaderNames.HOST, "source.invalid"));
        RedirectSecurityState source = RedirectSecurityState.initial()
                .finalized(uri("https://route.invalid/path"), sourceHeaders);

        ClientRequestHeaders targetHeaders = ClientRequestHeaders.create(WritableHeaders.create());
        targetHeaders.set(HeaderValues.create(HeaderNames.HOST, "target.invalid"));
        RedirectSecurityState hostChanged = source.forRedirect(false)
                .finalized(uri("https://route.invalid/target"), targetHeaders);
        RedirectSecurityState routeChanged = source.forRedirect(false)
                .finalized(uri("https://other-route.invalid/target"), sourceHeaders);

        assertThat(hostChanged.crossedOrigin(), is(true));
        assertThat(routeChanged.crossedOrigin(), is(true));
    }

    @Test
    void originCrossingAndReplayStateRemainStickyAcrossHandoffs() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        RedirectSecurityState source = RedirectSecurityState.initial()
                .finalized(uri("https://source.invalid/path"), headers);
        RedirectSecurityState crossed = source.forRedirect(true)
                .finalized(uri("https://target.invalid/path"), headers);
        RedirectSecurityState handedOff = crossed.finalized(uri("https://target.invalid/other"), headers);

        assertThat(crossed.replayingEntity(), is(true));
        assertThat(handedOff.crossedOrigin(), is(true));
        assertThat(handedOff.replayingEntity(), is(true));
    }

    @Test
    void missingRedirectSourceFailsClosed() {
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        RedirectSecurityState state = RedirectSecurityState.initial()
                .forRedirect(true)
                .finalized(uri("https://target.invalid/path"), headers);

        assertThat(state.crossedOrigin(), is(true));
        assertThat(state.replayingEntity(), is(true));
    }

    private static Stream<Arguments> originBoundaryStates() {
        ClientUri source = uri("https://source.invalid/path");
        ClientUri target = uri("https://target.invalid/path");
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        RedirectSecurityState initial = RedirectSecurityState.initial();
        RedirectSecurityState pending = initial.finalized(source, headers).forRedirect(false);
        RedirectSecurityState crossed = pending.finalized(target, headers);

        return Stream.of(Arguments.of("initial", initial, source, false),
                         Arguments.of("pending same origin", pending, source, false),
                         Arguments.of("pending different origin", pending, target, true),
                         Arguments.of("already crossed", crossed, target, true));
    }

    private static ClientUri uri(String uri) {
        return ClientUri.create(URI.create(uri));
    }
}
