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

package io.helidon.webclient.http3;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientAltSvcConfig;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.api.ResolvedClientTarget;
import io.helidon.webclient.api.SniMode;
import io.helidon.webclient.api.WebClientProtocolResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http3DiscoveryTest {
    private static final Instant START = Instant.parse("2026-09-03T12:00:00Z");

    @Test
    void shouldLearnPortOnlyAlternative() {
        try (RequestContext context = RequestContext.create("https://example.com:8443")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9443\""));

            assertAlternative(learnedTarget(discovery, context), "example.com", 9443);
        }
    }

    @Test
    void shouldFilterSharedAlternativesByExactProtocolAndOriginHost() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8443", clock)) {
            Http3Discovery discovery = context.discovery();

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h2=\":9440\", H3=\":9441\", "
                                        + "h3=\"other.example:9442\", h3=\":9443\""),
                         clock.nextObservation());

            assertAlternative(learnedTarget(discovery, context), "example.com", 9443);

            recordAltSvc(discovery, context.key(), altSvc("H3=\":9444\""), clock.nextObservation());
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }


    @Test
    void shouldMaintainRouteIndependentAutomaticTargetHint() {
        try (RequestContext context = RequestContext.create("https://example.com:8443")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextHint hint = context.key().hint();

            assertThat(discovery.hasAutomaticTarget(hint), is(false));

            recordAltSvc(discovery, context, altSvc("h3=\":9443\""));
            assertThat(discovery.hasAutomaticTarget(hint), is(true));

            recordAltSvc(discovery, context, altSvc("clear"));
            assertThat(discovery.hasAutomaticTarget(hint), is(false));
        }
    }

    @Test
    void automaticTargetHintAllowsExactSessionProbe() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8443", clock)) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextHint hint = context.key().hint();
            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9443\"; ma=1"),
                         clock.nextObservation());

            assertThat(discovery.hasAutomaticTarget(hint), is(true));
            clock.advance(Duration.ofSeconds(1));
            assertThat(discovery.hasAutomaticTarget(hint), is(true));
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            Http3Discovery.Selection established = discovery
                    .automaticTarget(context.key(), true, _ -> true)
                    .orElseThrow();
            assertThat(established.establishAllowed(), is(false));

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9443\"; ma=60"),
                         clock.nextObservation());
            assertThat(discovery.hasAutomaticTarget(hint), is(true));
            discovery.recordFailure(automaticTarget(discovery, context).orElseThrow());
            assertThat(discovery.hasAutomaticTarget(hint), is(true));
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldLearnSameAuthorityIpv6Alternative() {
        try (RequestContext context = RequestContext.create("https://[2001:db8::1]:8444")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\"[2001:db8::1]:9444\""));

            Http3Discovery.Target target = learnedTarget(discovery, context);
            assertAlternative(target, "2001:db8::1", 9444);
            assertThat(target.altUsed(), is("[2001:db8::1]:9444"));
        }
    }

    @Test
    void shouldIgnoreDifferentAuthorityAlternative() {
        try (RequestContext context = RequestContext.create("https://example.com:8445")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\"other.example.com:9445\""));

            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldIsolateAlternativesByEffectiveHostAuthority() {
        try (RequestContext context = RequestContext.create("https://transport.example:8445")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextKey tenantKey = new Http3Discovery.EndpointContextKey(
                    context.key().connectionKey(),
                    context.key().protocolConfig(),
                    context.key().scheme(),
                    UriAuthority.create("tenant.example:8445"),
                    context.key().tlsGeneration(),
                    context.key().observerIdentity());

            discovery.recordAltSvc(tenantKey, altSvc("h3=\":9445\""));

            Http3Discovery.Target target = discovery.automaticTarget(tenantKey, true, _ -> false)
                    .orElseThrow()
                    .target();
            assertAlternative(target, "tenant.example", 9445);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldIsolateAlternativesByObserverIdentity() {
        try (RequestContext context = RequestContext.create("https://example.com:8445")) {
            Http3Discovery discovery = context.discovery();
            Object firstIdentity = new EqualIdentity();
            Object secondIdentity = new EqualIdentity();
            Http3Discovery.EndpointContextKey firstKey = new Http3Discovery.EndpointContextKey(
                    context.key().connectionKey(),
                    context.key().protocolConfig(),
                    context.key().scheme(),
                    context.key().authority(),
                    context.key().tlsGeneration(),
                    firstIdentity);
            Http3Discovery.EndpointContextKey secondKey = new Http3Discovery.EndpointContextKey(
                    context.key().connectionKey(),
                    context.key().protocolConfig(),
                    context.key().scheme(),
                    context.key().authority(),
                    context.key().tlsGeneration(),
                    secondIdentity);

            discovery.recordAltSvc(firstKey, altSvc("h3=\":9445\""));

            assertAlternative(discovery.automaticTarget(firstKey, true, _ -> false).orElseThrow().target(),
                              "example.com",
                              9445);
            assertThat(discovery.automaticTarget(secondKey, true, _ -> false).isEmpty(), is(true));
        }
    }

    @Test
    void shouldIsolateAlternativesByEffectiveAltSvcPolicy() {
        try (RequestContext context = RequestContext.create("https://example.com:8445")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextKey enabledKey = context.key();
            Http3Discovery.EndpointContextKey disabledKey = new Http3Discovery.EndpointContextKey(
                    enabledKey.connectionKey(),
                    enabledKey.protocolConfig(),
                    enabledKey.scheme(),
                    enabledKey.authority(),
                    enabledKey.tlsGeneration(),
                    false,
                    enabledKey.observerIdentity(),
                    enabledKey.proxyRoute());

            discovery.recordAltSvc(enabledKey, altSvc("h3=\":9445\""));
            discovery.recordAltSvc(disabledKey, altSvc("h3=\":9446\""));

            assertAlternative(discovery.automaticTarget(enabledKey, true, _ -> false).orElseThrow().target(),
                              "example.com",
                              9445);
            assertThat(discovery.automaticTarget(disabledKey, true, _ -> false).isEmpty(), is(true));
            assertThat(discovery.hasAutomaticTarget(disabledKey.hint()), is(false));
        }
    }

    @Test
    void shouldUseFirstFreshUsableHttp3Alternative() {
        try (RequestContext context = RequestContext.create("https://example.com:8446")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery,
                         context,
                         altSvc("h2=\":9446\", h3=\":9447\"; ma=0, h3=\":9449\"; ma=60"));

            assertAlternative(learnedTarget(discovery, context), "example.com", 9449);
        }
    }

    @Test
    void newerAdvertisementWinsOverDelayedReplacement() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Instant older = START.plusSeconds(5);
            Instant newer = START.plusSeconds(10);

            recordAltSvc(discovery, context.key(), altSvc("h3=\":9443\"; ma=60"), newer);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":8443\"; ma=60"), older);

            assertThat(learnedTarget(discovery, context).peerPort(), is(9443));

            Instant latest = newer.plusSeconds(1);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":10443\"; ma=60"), latest);

            assertThat(learnedTarget(discovery, context).peerPort(), is(10443));
        }
    }

    @Test
    void newerAdvertisementWinsOverDelayedWithdrawal() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Instant withdrawalObservedAt = START.plusSeconds(5);
            Instant advertisementObservedAt = START.plusSeconds(10);

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9443\"; ma=60"),
                         advertisementObservedAt);
            Http3Discovery.Selection selection = automaticTarget(discovery, context).orElseThrow();
            recordAltSvc(discovery, context.key(), altSvc("clear"), withdrawalObservedAt);

            assertThat(automaticTarget(discovery, context).orElseThrow(), is(selection));
            assertThat(selection.target().peerPort(), is(9443));
            assertThat(discovery.current(selection), is(true));
        }
    }

    @Test
    void withdrawalWinsTiesAndRejectsDelayedAdvertisement() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Instant first = START.plusSeconds(5);

            recordAltSvc(discovery, context.key(), altSvc("h3=\":8443\"; ma=60"), first);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":9443\"; ma=60"), first);
            assertThat(learnedTarget(discovery, context).peerPort(), is(8443));

            recordAltSvc(discovery, context.key(), altSvc("H3=\":9443\"; ma=60"), first);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":10443\"; ma=60"), first);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            Instant second = first.plusSeconds(1);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":11443\"; ma=60"), second);
            recordAltSvc(discovery, context.key(), altSvc("clear"), second);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":12443\"; ma=60"), first);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":13443\"; ma=60"), second);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            Instant third = second.plusSeconds(1);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":14443\"; ma=60"), third);
            assertThat(learnedTarget(discovery, context).peerPort(), is(14443));
        }
    }

    @Test
    void failureAndMisdirectedResponseRejectDelayedAdvertisements() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextKey failedKey = key(context, "failed.example:8446");
            Http3Discovery.EndpointContextKey misdirectedKey = key(context, "misdirected.example:8446");

            recordAltSvc(discovery, failedKey, altSvc("h3=\":8443\"; ma=3600"), START);
            Http3Discovery.Selection failed = discovery.automaticTarget(failedKey, true, _ -> false).orElseThrow();
            clock.advance(Duration.ofSeconds(10));
            discovery.recordFailure(failed);

            Instant delayedFailure = START.plusSeconds(5);
            recordAltSvc(discovery, failedKey, altSvc("h3=\":9443\"; ma=3600"), delayedFailure);
            assertThat(discovery.automaticTarget(failedKey, true, _ -> false).isEmpty(), is(true));

            Instant afterFailure = clock.instant().plusSeconds(1);
            recordAltSvc(discovery, failedKey, altSvc("h3=\":10443\"; ma=3600"), afterFailure);
            assertThat(discovery.automaticTarget(failedKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(10443));

            recordAltSvc(discovery, misdirectedKey, altSvc("h3=\":8443\"; ma=3600"), clock.nextObservation());
            Http3Discovery.Selection misdirected = discovery.automaticTarget(misdirectedKey, true, _ -> false)
                    .orElseThrow();
            clock.advance(Duration.ofSeconds(10));
            discovery.recordMisdirected(misdirected);

            Instant delayedMisdirected = clock.instant().minusSeconds(5);
            recordAltSvc(discovery,
                         misdirectedKey,
                         altSvc("h3=\":9443\"; ma=3600"),
                         delayedMisdirected);
            assertThat(discovery.automaticTarget(misdirectedKey, true, _ -> false).isEmpty(), is(true));

            Instant afterMisdirected = clock.instant().plusSeconds(1);
            recordAltSvc(discovery,
                         misdirectedKey,
                         altSvc("h3=\":10443\"; ma=3600"),
                         afterMisdirected);
            assertThat(discovery.automaticTarget(misdirectedKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(10443));
        }
    }

    @Test
    void failureObservationSurvivesNaturalExpiry() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context.key(), altSvc("h3=\":8443\"; ma=1"), START);
            Http3Discovery.Selection selection = automaticTarget(discovery, context).orElseThrow();
            clock.advance(Duration.ofMillis(500));
            discovery.recordFailure(selection);
            clock.advance(Duration.ofMinutes(5));

            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            Instant delayed = START.plusMillis(250);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":9443\"; ma=3600"), delayed);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            Instant newer = START.plusMillis(750);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":10443\"; ma=3600"), newer);
            assertThat(automaticTarget(discovery, context).orElseThrow().target().peerPort(), is(10443));
        }
    }

    @Test
    void connectionFailureAfterAdvertisementExpiryRejectsOlderObservation() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context.key(), altSvc("h3=\":8443\"; ma=1"), START);
            Http3Discovery.Selection selection = automaticTarget(discovery, context).orElseThrow();
            Instant delayed = START.plusMillis(500);

            clock.advance(Duration.ofSeconds(2));
            discovery.recordFailure(selection);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":9443\"; ma=3600"), delayed);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            Instant newer = clock.instant().plusSeconds(1);
            recordAltSvc(discovery, context.key(), altSvc("h3=\":10443\"; ma=3600"), newer);
            assertThat(automaticTarget(discovery, context).orElseThrow().target().peerPort(), is(10443));
        }
    }

    @Test
    void networkChangeOrdersKnownPersistentAndPreviouslyUnseenRoutes() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextKey transientKey = key(context, "transient.example:8446");
            Http3Discovery.EndpointContextKey persistentKey = key(context, "persistent.example:8446");
            Http3Discovery.EndpointContextKey unseenKey = key(context, "unseen.example:8446");

            recordAltSvc(discovery, transientKey, altSvc("h3=\":7443\"; ma=60"), START);
            recordAltSvc(discovery,
                         persistentKey,
                         altSvc("h3=\":8443\"; ma=60; persist=1"),
                         START);
            clock.advance(Duration.ofSeconds(10));
            discovery.networkChanged();

            Instant delayed = START.plusSeconds(5);
            recordAltSvc(discovery, transientKey, altSvc("h3=\":9443\"; ma=60"), delayed);
            recordAltSvc(discovery, persistentKey, altSvc("h3=\":9443\"; ma=60"), delayed);
            recordAltSvc(discovery, unseenKey, altSvc("h3=\":9443\"; ma=60"), delayed);
            recordAltSvc(discovery, unseenKey, altSvc("h3=\":10443\"; ma=60"), clock.instant());

            assertThat(discovery.automaticTarget(transientKey, true, _ -> false).isEmpty(), is(true));
            assertThat(discovery.automaticTarget(persistentKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(8443));
            assertThat(discovery.automaticTarget(unseenKey, true, _ -> false).isEmpty(), is(true));

            Instant newer = clock.instant().plusSeconds(1);
            recordAltSvc(discovery, transientKey, altSvc("h3=\":10443\"; ma=60"), newer);
            recordAltSvc(discovery, persistentKey, altSvc("h3=\":11443\"; ma=60"), newer);
            recordAltSvc(discovery, unseenKey, altSvc("h3=\":12443\"; ma=60"), newer);

            assertThat(discovery.automaticTarget(transientKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(10443));
            assertThat(discovery.automaticTarget(persistentKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(11443));
            assertThat(discovery.automaticTarget(unseenKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(12443));
        }
    }

    @Test
    void caseVariantsShareObservationOrderWithoutChangingExactSelectionKey() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextKey upper = keyWithConnectionHost(context, "Example.COM");
            Http3Discovery.EndpointContextKey lower = keyWithConnectionHost(context, "example.com");
            Instant older = START.plusSeconds(5);
            Instant newer = START.plusSeconds(10);

            assertThat(upper.equals(lower), is(false));
            recordAltSvc(discovery, upper, altSvc("h3=\":8443\"; ma=60"), newer);
            recordAltSvc(discovery, lower, altSvc("h3=\":9443\"; ma=60"), older);

            Http3Discovery.Selection selection = discovery.automaticTarget(lower, true, _ -> false).orElseThrow();
            assertThat(selection.target().peerPort(), is(8443));
            assertThat(selection.key() == lower, is(true));

            recordAltSvc(discovery, lower, altSvc("clear"), newer);
            recordAltSvc(discovery, upper, altSvc("h3=\":10443\"; ma=60"), newer);
            assertThat(discovery.automaticTarget(upper, true, _ -> false).isEmpty(), is(true));

            Instant latest = newer.plusSeconds(1);
            recordAltSvc(discovery, lower, altSvc("h3=\":11443\"; ma=60"), latest);
            Http3Discovery.Selection revived = discovery.automaticTarget(upper, true, _ -> false).orElseThrow();
            assertThat(revived.target().peerPort(), is(11443));
            assertThat(revived.key() == upper, is(true));
        }
    }

    @Test
    void deterministicClockControlsExpirationAndNegativeBackoff() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8446", clock)) {
            Http3Discovery discovery = context.discovery();
            Instant observedAt = clock.nextObservation();
            recordAltSvc(discovery, context.key(), altSvc("h3=\":9443\"; ma=3600"), observedAt);
            Http3Discovery.Selection selection = automaticTarget(discovery, context).orElseThrow();

            discovery.recordFailure(selection);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            clock.advance(Duration.ofMinutes(5));
            assertThat(automaticTarget(discovery, context).orElseThrow().target().peerPort(), is(9443));

            Instant expiringAt = clock.nextObservation();
            recordAltSvc(discovery, context.key(), altSvc("h3=\":10443\"; ma=1"), expiringAt);
            Http3Discovery.Selection expiring = automaticTarget(discovery, context).orElseThrow();
            assertThat(expiring.establishAllowed(), is(true));
            clock.advance(Duration.ofSeconds(1));
            assertThat(expiring.establishAllowed(), is(false));
        }
    }

    @Test
    void shouldRetainKnownGoodDirectTargetWhenAlternativeIsCleared() {
        try (RequestContext context = RequestContext.create("https://example.com:8447")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.Selection direct = requestTarget(discovery, context).orElseThrow();
            discovery.recordSuccess(direct);
            recordAltSvc(discovery, context, altSvc("h3=\":9447\""));
            recordAltSvc(discovery, context, altSvc("clear"));

            assertThat(learnedTarget(discovery, context).alternative(), is(false));
        }
    }

    @Test
    void shouldReplaceAlternativeWithValidUnsupportedAdvertisement() {
        try (RequestContext context = RequestContext.create("https://example.com:8449")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9449\""));
            recordAltSvc(discovery, context, altSvc("h2=\":9450\""));

            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldReuseExistingAlternativeAfterAdvertisementExpires() {
        try (RequestContext context = RequestContext.create("https://example.com:8457")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9457\"; ma=0"));

            Http3Discovery.Selection selection = discovery
                    .automaticTarget(context.key(), true, _ -> true)
                    .orElseThrow();
            assertThat(selection.target().peerPort(), is(9457));
            assertThat(selection.establishAllowed(), is(false));

            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldFallBackToKnownGoodDirectTargetWhileAlternativeIsNegative() {
        try (RequestContext context = RequestContext.create("https://example.com:8451")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.Selection direct = requestTarget(discovery, context).orElseThrow();
            discovery.recordSuccess(direct);
            recordAltSvc(discovery, context, altSvc("h3=\":9451\""));
            Http3Discovery.Selection alternative = automaticTarget(discovery, context).orElseThrow();

            discovery.recordFailure(alternative);
            Http3Discovery.Selection directRetry = automaticTarget(discovery, context).orElseThrow();
            assertThat(directRetry.target().alternative(), is(false));
            discovery.recordFailure(directRetry);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldInvalidateConcurrentSelectionsAfterFailure() {
        try (RequestContext context = RequestContext.create("https://example.com:8460")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9460\""));
            Http3Discovery.Selection failed = automaticTarget(discovery, context).orElseThrow();
            Http3Discovery.Selection concurrent = automaticTarget(discovery, context).orElseThrow();

            discovery.recordFailure(failed);

            assertThat(discovery.current(concurrent), is(false));
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldKeepSelectionCurrentWhenAdvertisementRefreshesSameTarget() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8461", clock)) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=30"),
                         clock.nextObservation());
            Http3Discovery.Selection selection = automaticTarget(discovery, context).orElseThrow();

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=60; persist=1"),
                         clock.nextObservation());
            clock.advance(Duration.ofSeconds(31));

            assertThat(discovery.current(selection), is(true));
            assertThat(selection.establishAllowed(), is(true));
            Http3Discovery.Selection refreshed = automaticTarget(discovery, context).orElseThrow();
            assertThat(refreshed.target(), is(selection.target()));

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=1"),
                         clock.nextObservation());
            clock.advance(Duration.ofSeconds(1));
            assertThat(discovery.current(refreshed), is(true));
            assertThat(refreshed.establishAllowed(), is(false));

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9462\""),
                         clock.nextObservation());
            assertThat(discovery.current(selection), is(false));
        }
    }

    @Test
    void sameTargetRefreshPreservesAlternativeAndDirectNegativeBackoff() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8461", clock)) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.Selection direct = requestTarget(discovery, context).orElseThrow();
            discovery.recordSuccess(direct);
            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=60"),
                         clock.nextObservation());
            Http3Discovery.Selection alternative = automaticTarget(discovery, context).orElseThrow();

            discovery.recordFailure(alternative);
            Http3Discovery.Selection directRetry = automaticTarget(discovery, context).orElseThrow();
            discovery.recordFailure(directRetry);
            clock.advance(Duration.ofNanos(1));

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=600; persist=1"),
                         clock.nextObservation());

            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void withdrawalPreservesDirectFailureBackoff() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8461", clock)) {
            Http3Discovery discovery = context.discovery();
            discovery.recordFailure(requestTarget(discovery, context).orElseThrow());
            clock.advance(Duration.ofNanos(1));

            recordAltSvc(discovery, context.key(), altSvc("clear"), clock.nextObservation());

            assertThat(requestTarget(discovery, context).isEmpty(), is(true));
            clock.advance(Duration.ofMinutes(5));
            assertThat(requestTarget(discovery, context).isPresent(), is(true));
        }
    }

    @Test
    void changedAlternativePreservesDirectFailureBackoff() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8461", clock)) {
            Http3Discovery discovery = context.discovery();
            discovery.recordFailure(requestTarget(discovery, context).orElseThrow());
            clock.advance(Duration.ofNanos(1));
            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=600"),
                         clock.nextObservation());
            assertThat(automaticTarget(discovery, context).orElseThrow().target().peerPort(), is(9461));

            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9462\"; ma=600"),
                         clock.nextObservation());
            Http3Discovery.Selection replacement = automaticTarget(discovery, context).orElseThrow();
            assertThat(replacement.target().peerPort(), is(9462));
            discovery.recordFailure(replacement);

            assertThat(requestTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void misdirectedAlternativePreservesDirectFailureBackoff() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8461", clock)) {
            Http3Discovery discovery = context.discovery();
            discovery.recordFailure(requestTarget(discovery, context).orElseThrow());
            clock.advance(Duration.ofNanos(1));
            recordAltSvc(discovery,
                         context.key(),
                         altSvc("h3=\":9461\"; ma=600"),
                         clock.nextObservation());
            Http3Discovery.Selection alternative = automaticTarget(discovery, context).orElseThrow();

            discovery.recordMisdirected(alternative);

            assertThat(requestTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldBoundExactKeyMemoAndRetireEvictedRawRoutes() {
        MutableClock clock = new MutableClock(START);
        List<Http3Discovery.EndpointContextKey> invalidatedKeys = new ArrayList<>();
        try (RequestContext context = RequestContext.create("https://example.com:8461", clock)) {
            Http3Discovery discovery = Http3Discovery.create(clock,
                                                             (key, _) -> invalidatedKeys.add(key));
            String[] variants = {
                    "example.com",
                    "Example.com",
                    "eXample.com",
                    "exAmple.com",
                    "exaMple.com"
            };
            Http3Discovery.EndpointContextKey[] keys = new Http3Discovery.EndpointContextKey[variants.length];
            for (int index = 0; index < variants.length; index++) {
                keys[index] = keyWithConnectionHost(context, variants[index]);
            }
            recordAltSvc(discovery,
                         keys[0],
                         altSvc("h3=\":9461\"; ma=600"),
                         clock.nextObservation());

            Http3Discovery.Selection beforeEviction = discovery.automaticTarget(keys[0], true, _ -> false)
                    .orElseThrow();
            for (int index = 1; index < keys.length; index++) {
                discovery.automaticTarget(keys[index], true, _ -> false).orElseThrow();
            }

            assertThat(invalidatedKeys, is(List.of(keys[0])));
            assertThat(discovery.current(beforeEviction), is(false));
            Http3Discovery.Selection retained = discovery.automaticTarget(keys[4], true, _ -> true)
                    .orElseThrow();
            assertThat(discovery.current(retained), is(true));
            assertThat(retained.target().peerPort(), is(9461));
            assertThat(invalidatedKeys, is(List.of(keys[0])));

            Http3Discovery.Selection afterEviction = discovery.automaticTarget(keys[0], true, _ -> false)
                    .orElseThrow();

            assertThat(invalidatedKeys, is(List.of(keys[0], keys[1])));
            assertThat(discovery.current(beforeEviction), is(false));
            assertThat(discovery.current(afterEviction), is(true));
            assertThat(afterEviction.key() == keys[0], is(true));
        }
    }

    @Test
    void shouldIgnoreLateCallbacksAfterReplacementAndEviction() {
        try (RequestContext context = RequestContext.create("https://example.com:8452")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.Selection stale = requestTarget(discovery, context).orElseThrow();
            recordAltSvc(discovery, context, altSvc("h3=\":9452\""));
            recordAltSvc(discovery, context, altSvc("clear"));
            discovery.recordFailure(stale);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));

            for (int i = 1; i <= 10_000; i++) {
                Http3Discovery.EndpointContextKey otherKey = new Http3Discovery.EndpointContextKey(
                        context.key().connectionKey(),
                        context.key().protocolConfig(),
                        context.key().scheme(),
                        UriAuthority.create("other-" + i + ".example.com:8452"),
                        context.key().tlsGeneration(),
                        context.key().observerIdentity());
                discovery.requestTarget(otherKey, context.uri(), true, true, _ -> false);
            }
            discovery.recordSuccess(stale);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldBoundTombstonesAndEvictOldestBeforeAnActiveRoute() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8452", clock)) {
            Http3Discovery discovery = context.discovery();
            AltSvcHeader clear = parsed(altSvc("clear"), START);
            Http3Discovery.EndpointContextKey first = null;
            Http3Discovery.EndpointContextKey last = null;

            for (int index = 0; index <= 10_000; index++) {
                Http3Discovery.EndpointContextKey key = key(context, "withdrawn-" + index + ".example:8452");
                if (index == 0) {
                    first = key;
                }
                if (index == 10_000) {
                    last = key;
                }
                discovery.recordAltSvc(key, clear, START.plusSeconds(index));
            }

            recordAltSvc(discovery, first, altSvc("h3=\":9443\"; ma=3600"), START);
            recordAltSvc(discovery,
                         last,
                         altSvc("h3=\":10443\"; ma=3600"),
                         START.plusSeconds(10_000));

            assertThat(discovery.automaticTarget(first, true, _ -> false).orElseThrow().target().peerPort(),
                       is(9443));
            assertThat(discovery.automaticTarget(last, true, _ -> false).isEmpty(), is(true));
        }
    }

    @Test
    void newWithdrawalAtCapacityEvictsOldestActiveRouteAndRetainsItsBarrier() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8452", clock)) {
            Http3Discovery discovery = context.discovery();
            AltSvcHeader advertisement = parsed(altSvc("h3=\":9443\"; ma=3600"), START);
            Http3Discovery.EndpointContextKey first = null;
            Http3Discovery.EndpointContextKey last = null;

            for (int index = 0; index < 10_000; index++) {
                Http3Discovery.EndpointContextKey key = key(context, "active-" + index + ".example:8452");
                if (index == 0) {
                    first = key;
                }
                if (index == 9_999) {
                    last = key;
                }
                discovery.recordAltSvc(key, advertisement, START.plusNanos(index + 1));
            }

            Http3Discovery.EndpointContextKey withdrawn = key(context, "withdrawn.example:8452");
            Instant withdrawal = START.plusSeconds(10);
            discovery.recordAltSvc(withdrawn, parsed(altSvc("clear"), withdrawal), withdrawal);

            assertThat(discovery.automaticTarget(first, true, _ -> false).isEmpty(), is(true));
            assertThat(discovery.automaticTarget(last, true, _ -> false).isPresent(), is(true));

            Instant delayed = withdrawal.minusSeconds(1);
            recordAltSvc(discovery, withdrawn, altSvc("h3=\":10443\"; ma=3600"), delayed);
            assertThat(discovery.automaticTarget(withdrawn, true, _ -> false).isEmpty(), is(true));

            Instant newer = withdrawal.plusSeconds(1);
            recordAltSvc(discovery, withdrawn, altSvc("h3=\":11443\"; ma=3600"), newer);
            assertThat(discovery.automaticTarget(withdrawn, true, _ -> false).orElseThrow().target().peerPort(),
                       is(11443));
        }
    }

    @Test
    void shouldKeepOnlyPersistentAdvertisementAfterNetworkChange() {
        try (RequestContext context = RequestContext.create("https://example.com:8453")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9453\"; persist=1"));
            Http3Discovery.Selection alternative = automaticTarget(discovery, context).orElseThrow();
            discovery.recordFailure(alternative);

            discovery.networkChanged();
            assertThat(learnedTarget(discovery, context).peerPort(), is(9453));

            recordAltSvc(discovery, context, altSvc("h3=\":9454\""));
            discovery.networkChanged();
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void networkChangeDropsPersistentRouteFromStaleTlsGeneration() {
        MutableClock clock = new MutableClock(START);
        try (RequestContext context = RequestContext.create("https://example.com:8453", clock)) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.EndpointContextKey staleKey = context.key();
            recordAltSvc(discovery,
                         staleKey,
                         altSvc("h3=\":9453\"; ma=600; persist=1"),
                         clock.nextObservation());
            Http3Discovery.Selection staleSelection = discovery.automaticTarget(staleKey, true, _ -> false)
                    .orElseThrow();

            Tls tls = staleKey.connectionKey().tls();
            tls.reload(TlsMaterial.builder().trustAll(true).build());
            Http3Discovery.EndpointContextKey currentKey = new Http3Discovery.EndpointContextKey(
                    staleKey.connectionKey(),
                    staleKey.protocolConfig(),
                    staleKey.scheme(),
                    staleKey.authority(),
                    tls.generation(),
                    staleKey.observerIdentity());
            recordAltSvc(discovery,
                         currentKey,
                         altSvc("h3=\":9553\"; ma=600; persist=1"),
                         clock.nextObservation());

            clock.advance(Duration.ofSeconds(1));
            discovery.networkChanged();

            assertThat(discovery.current(staleSelection), is(false));
            assertThat(discovery.automaticTarget(staleKey, true, _ -> false).isEmpty(), is(true));
            assertThat(discovery.hasAutomaticTarget(staleKey.hint()), is(false));
            assertThat(discovery.automaticTarget(currentKey, true, _ -> false).orElseThrow().target().peerPort(),
                       is(9553));
            assertThat(discovery.hasAutomaticTarget(currentKey.hint()), is(true));
        }
    }

    @Test
    void shouldIgnoreDirectAttemptFailureFromBeforeNetworkChange() {
        try (RequestContext context = RequestContext.create("https://example.com:8455")) {
            Http3Discovery discovery = context.discovery();
            Http3Discovery.Selection stale = requestTarget(discovery, context).orElseThrow();

            discovery.networkChanged();
            discovery.recordFailure(stale);

            assertThat(requestTarget(discovery, context).isPresent(), is(true));
        }
    }

    @Test
    void shouldRemoveOnlyCurrentAlternativeAfterMisdirectedResponse() {
        try (RequestContext context = RequestContext.create("https://example.com:8454")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9454\""));
            Http3Discovery.Selection old = automaticTarget(discovery, context).orElseThrow();
            recordAltSvc(discovery, context, altSvc("h3=\":9455\""));

            discovery.recordMisdirected(old);
            assertThat(learnedTarget(discovery, context).peerPort(), is(9455));

            Http3Discovery.Selection current = automaticTarget(discovery, context).orElseThrow();
            discovery.recordMisdirected(current);
            assertThat(automaticTarget(discovery, context).isEmpty(), is(true));
        }
    }

    @Test
    void shouldIsolateDiscoveryStateByTlsReloadGeneration() {
        try (RequestContext context = RequestContext.create("https://example.com:8458")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9458\""));

            Tls tls = context.key().connectionKey().tls();
            tls.reload(TlsMaterial.builder().trustAll(true).build());
            Http3Discovery.EndpointContextKey reloadedKey = new Http3Discovery.EndpointContextKey(
                    context.key().connectionKey(),
                    context.key().protocolConfig(),
                    context.key().scheme(),
                    context.key().authority(),
                    tls.generation(),
                    context.key().observerIdentity());

            assertThat(automaticTarget(discovery, context).isPresent(), is(true));
            assertThat(discovery.automaticTarget(reloadedKey, true, _ -> false).isEmpty(), is(true));
        }
    }

    @Test
    void shouldIsolateDiscoveryStateByTlsIdentity() {
        try (RequestContext context = RequestContext.create("https://example.com:8460")) {
            Http3Discovery discovery = context.discovery();
            recordAltSvc(discovery, context, altSvc("h3=\":9460\""));

            ConnectionKey originalConnectionKey = context.key().connectionKey();
            Tls originalTls = originalConnectionKey.tls();
            Tls replacementTls = Tls.builder()
                    .sslContext(originalTls.sslContext())
                    .sslParameters(originalTls.sslParameters())
                    .build();
            assertThat(replacementTls.equals(originalTls), is(true));
            ConnectionKey replacementConnectionKey = ConnectionKey.create(context.uri(),
                                                                           replacementTls,
                                                                           originalConnectionKey.dnsResolver(),
                                                                           originalConnectionKey.dnsAddressLookup(),
                                                                           originalConnectionKey.proxy());
            Http3Discovery.EndpointContextKey replacementKey = new Http3Discovery.EndpointContextKey(
                    replacementConnectionKey,
                    context.key().protocolConfig(),
                    context.key().scheme(),
                    context.key().authority(),
                    replacementTls.generation(),
                    context.key().observerIdentity());

            assertThat(discovery.automaticTarget(replacementKey, true, _ -> false).isEmpty(), is(true));
        }
    }

    @Test
    void shouldIgnoreAltSvcResponseFromEarlierTlsGeneration() {
        try (RequestContext context = RequestContext.createWithAltSvc("https://example.com:8461")) {
            Http3ClientImpl client = (Http3ClientImpl) context.client();
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) context.client().get("/hello");
            Tls tls = request.tls();
            request.selectedProxyRoute(context.key().proxyRoute());
            HttpClientResponse response = mock(HttpClientResponse.class);
            when(response.status()).thenReturn(Status.OK_200);
            when(response.headers()).thenReturn(altSvc("h3=\":9461\""));
            ResolvedClientTarget responseTarget = resolvedTarget(client,
                                                                 request,
                                                                 context.uri(),
                                                                 request.headers());
            tls.reload(TlsMaterial.builder().trustAll(true).build());

            client.responseReceived(protocolResponse(responseTarget, response));

            Http3Discovery.EndpointContextKey reloadedKey = new Http3Discovery.EndpointContextKey(
                    context.key().connectionKey(),
                    context.key().protocolConfig(),
                    context.key().scheme(),
                    context.key().authority(),
                    tls.generation(),
                    context.key().observerIdentity());
            assertThat(context.discovery().automaticTarget(reloadedKey, true, _ -> false).isEmpty(), is(true));
        }
    }

    @Test
    void shouldHonorConfiguredTlsForHostAuthorityAlternative() {
        try (RequestContext context = RequestContext.createWithAltSvcHostHeaderSni("https://example.com:8463")) {
            Http3ClientImpl client = (Http3ClientImpl) context.client();
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) context.client().get("/hello");
            request.header(HeaderNames.HOST, "tenant.example:8463");
            request.selectedProxyRoute(context.key().proxyRoute());
            HttpClientResponse response = mock(HttpClientResponse.class);
            when(response.status()).thenReturn(Status.OK_200);
            when(response.headers()).thenReturn(altSvc("h3=\":9463\""));

            client.responseReceived(protocolResponse(client,
                                                      request,
                                                      context.uri(),
                                                      request.headers(),
                                                      response));

            Http3Discovery.EndpointContextKey tenantKey = new Http3Discovery.EndpointContextKey(
                    client.connectionKey(request, context.uri(), request.headers()),
                    client.protocolConfig(),
                    context.uri().scheme(),
                    UriAuthority.create("tenant.example:8463"),
                    request.tls().generation(),
                    context.key().observerIdentity());
            assertThat(request.selectedProxyRoute().orElseThrow(), is(context.key().proxyRoute()));
            Http3Discovery.Target tenantTarget = context.discovery()
                    .automaticTarget(tenantKey, true, _ -> false)
                    .orElseThrow()
                    .target();
            assertAlternative(tenantTarget, "tenant.example", 9463);
        }
    }

    @Test
    void shouldUseDispatchedAuthorityForAltSvcDiscovery() {
        try (RequestContext context = RequestContext.createWithAltSvc("https://example.com:8465")) {
            Http3ClientImpl client = (Http3ClientImpl) context.client();
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) context.client().get("/hello");
            request.header(HeaderNames.HOST, "stale.invalid:8465");
            request.selectedProxyRoute(context.key().proxyRoute());

            WritableHeaders<?> dispatchedHeaders = WritableHeaders.create();
            dispatchedHeaders.set(HeaderNames.HOST, "example.com:8465");
            ClientRequestHeaders effectiveHeaders = ClientRequestHeaders.create(dispatchedHeaders);
            Http3ClientResponseImpl response = new Http3ClientResponseImpl(
                    Duration.ofSeconds(1),
                    Http3Client.PROTOCOL_ID,
                    Status.OK_200,
                    effectiveHeaders,
                    altSvc("h3=\":9465\""),
                    CompletableFuture.completedFuture(ClientResponseTrailers.create(WritableHeaders.create())),
                    null,
                    MediaContext.create(),
                    ClientUri.create(URI.create("https://example.com:8465/hello")),
                    new CompletableFuture<>(),
                    () -> {
                    },
                    _ -> {
                    },
                    1024);

            client.responseReceived(protocolResponse(client,
                                                      request,
                                                      context.uri(),
                                                      effectiveHeaders,
                                                      response));

            assertAlternative(learnedTarget(context.discovery(), context), "example.com", 9465);
        }
    }

    @Test
    void shouldFallBackToUriOriginForMalformedDispatchedHostAltSvcDiscovery() {
        try (RequestContext context = RequestContext.createWithAltSvc("https://example.com:8466")) {
            Http3ClientImpl client = (Http3ClientImpl) context.client();
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) context.client().get("/hello");
            request.selectedProxyRoute(context.key().proxyRoute());

            WritableHeaders<?> dispatchedHeaders = WritableHeaders.create();
            dispatchedHeaders.set(HeaderNames.HOST, "example.com:84x");
            ClientRequestHeaders effectiveHeaders = ClientRequestHeaders.create(dispatchedHeaders);
            Http3ClientResponseImpl response = new Http3ClientResponseImpl(
                    Duration.ofSeconds(1),
                    Http3Client.PROTOCOL_ID,
                    Status.OK_200,
                    effectiveHeaders,
                    altSvc("h3=\":9466\""),
                    CompletableFuture.completedFuture(ClientResponseTrailers.create(WritableHeaders.create())),
                    null,
                    MediaContext.create(),
                    ClientUri.create(URI.create("https://example.com:8466/hello")),
                    new CompletableFuture<>(),
                    () -> {
                    },
                    _ -> {
                    },
                    1024);

            client.responseReceived(protocolResponse(client,
                                                      request,
                                                      context.uri(),
                                                      effectiveHeaders,
                                                      response));

            assertAlternative(learnedTarget(context.discovery(), context), "example.com", 9466);
        }
    }

    @Test
    void shouldLearnAlternativeForBracketedIpv6Origin() {
        try (RequestContext context = RequestContext.createWithAltSvc("https://[2001:db8::1]:8464")) {
            Http3ClientImpl client = (Http3ClientImpl) context.client();
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) context.client().get("/hello");
            HttpClientResponse response = mock(HttpClientResponse.class);
            when(response.status()).thenReturn(Status.OK_200);
            when(response.headers()).thenReturn(altSvc("h3=\":9464\""));
            request.selectedProxyRoute(context.key().proxyRoute());

            client.responseReceived(protocolResponse(client,
                                                      request,
                                                      context.uri(),
                                                      request.headers(),
                                                      response));

            assertAlternative(learnedTarget(context.discovery(), context), "2001:db8::1", 9464);
        }
    }

    private static Http3Discovery.Target learnedTarget(Http3Discovery discovery, RequestContext context) {
        return automaticTarget(discovery, context)
                .orElseThrow()
                .target();
    }

    private static Optional<Http3Discovery.Selection> automaticTarget(Http3Discovery discovery,
                                                                       RequestContext context) {
        return discovery.automaticTarget(context.key(), true, _ -> false);
    }

    private static Optional<Http3Discovery.Selection> requestTarget(Http3Discovery discovery,
                                                                     RequestContext context) {
        return discovery.requestTarget(context.key(), context.uri(), true, true, _ -> false);
    }

    private static Http3Discovery.EndpointContextKey key(RequestContext context, String authority) {
        return new Http3Discovery.EndpointContextKey(context.key().connectionKey(),
                                                     context.key().protocolConfig(),
                                                     context.key().scheme(),
                                                     UriAuthority.create(authority),
                                                     context.key().tlsGeneration(),
                                                     context.key().observerIdentity());
    }

    private static Http3Discovery.EndpointContextKey keyWithConnectionHost(RequestContext context, String host) {
        ConnectionKey original = context.key().connectionKey();
        ConnectionKey connectionKey = ConnectionKey.create(context.key().scheme(),
                                                           host,
                                                           original.port(),
                                                           original.tls(),
                                                           original.dnsResolver(),
                                                           original.dnsAddressLookup(),
                                                           original.proxy());
        return new Http3Discovery.EndpointContextKey(connectionKey,
                                                     context.key().protocolConfig(),
                                                     context.key().scheme(),
                                                     UriAuthority.create(host + ':' + original.port()),
                                                     context.key().tlsGeneration(),
                                                     context.key().observerIdentity());
    }

    private static void recordAltSvc(Http3Discovery discovery,
                                     RequestContext context,
                                     ClientResponseHeaders headers) {
        discovery.recordAltSvc(context.key(), headers);
    }

    private static void recordAltSvc(Http3Discovery discovery,
                                     Http3Discovery.EndpointContextKey key,
                                     ClientResponseHeaders headers,
                                     Instant receivedAt) {
        AltSvcHeader parsed = AltSvcHeader.create(headers, receivedAt).orElseThrow();
        discovery.recordAltSvc(key, parsed, receivedAt);
    }

    private static void assertAlternative(Http3Discovery.Target target,
                                          String expectedHost,
                                          int expectedPort) {
        assertThat(target.peerHost(), is(expectedHost));
        assertThat(target.peerPort(), is(expectedPort));
        assertThat(target.alternative(), is(true));
    }

    private static ClientResponseHeaders altSvc(String... values) {
        WritableHeaders<?> headers = WritableHeaders.create();
        for (String value : values) {
            headers.add(HeaderValues.create(HeaderNames.ALT_SVC, value));
        }
        return ClientResponseHeaders.create(headers);
    }

    private static AltSvcHeader parsed(ClientResponseHeaders headers, Instant receivedAt) {
        return AltSvcHeader.create(headers, receivedAt).orElseThrow();
    }

    private static WebClientProtocolResponse protocolResponse(Http3ClientImpl client,
                                                              Http3ClientRequestImpl request,
                                                              ClientUri uri,
                                                              ClientRequestHeaders effectiveHeaders,
                                                              HttpClientResponse response) {
        return protocolResponse(resolvedTarget(client, request, uri, effectiveHeaders), response);
    }

    private static WebClientProtocolResponse protocolResponse(ResolvedClientTarget target,
                                                              HttpClientResponse response) {
        return WebClientProtocolResponse.create(target,
                                                false,
                                                Http3Client.PROTOCOL_ID,
                                                response.status(),
                                                response.headers(),
                                                Instant.now());
    }

    private static ResolvedClientTarget resolvedTarget(Http3ClientImpl client,
                                                       Http3ClientRequestImpl request,
                                                       ClientUri uri,
                                                       ClientRequestHeaders effectiveHeaders) {
        ConnectionKey connectionKey = client.connectionKey(request, uri, effectiveHeaders);
        ClientConnectionTarget target = ClientConnectionTarget.create(connectionKey,
                                                                       uri,
                                                                       effectiveHeaders,
                                                                       request.selectedProxyRoute().orElseThrow());
        return target.resolve();
    }

    private record RequestContext(Http3Client client,
                                  ClientUri uri,
                                  Http3Discovery.EndpointContextKey key,
                                  Http3Discovery discovery) implements AutoCloseable {
        private static RequestContext create(String baseUri) {
            return create(baseUri, false, false, null);
        }

        private static RequestContext create(String baseUri, Clock clock) {
            return create(baseUri, false, false, clock);
        }

        private static RequestContext createWithAltSvc(String baseUri) {
            return create(baseUri, true, false, null);
        }

        private static RequestContext createWithAltSvcHostHeaderSni(String baseUri) {
            return create(baseUri, true, true, null);
        }

        private static RequestContext create(String baseUri,
                                             boolean altSvcEnabled,
                                             boolean hostHeaderSni,
                                             Clock clock) {
            var builder = Http3Client.builder()
                    .baseUri(baseUri)
                    .shareConnectionCache(false)
                    .proxy(Proxy.noProxy())
                    .tls(Tls.builder().trustAll(true).build());
            if (altSvcEnabled) {
                builder.altSvc(ClientAltSvcConfig.create());
            }
            if (hostHeaderSni) {
                builder.sni(it -> it.mode(SniMode.HOST_HEADER));
            }
            Http3Client client = builder.build();
            Http3ClientImpl clientImpl = (Http3ClientImpl) client;
            Http3ClientRequestImpl request = (Http3ClientRequestImpl) client.get("/hello");
            ClientUri uri = request.resolvedUri();
            Http3Discovery.EndpointContextKey key = new Http3Discovery.EndpointContextKey(
                    clientImpl.connectionKey(request, uri, request.headers()),
                    clientImpl.protocolConfig(),
                    uri.scheme(),
                    UriAuthority.create(uri.authority()),
                    request.tls().generation(),
                    HttpTransportObserver.noop());
            Http3Discovery discovery = clock == null
                    ? clientImpl.connectionCache().discovery()
                    : Http3Discovery.create(clock, (_, _) -> { });
            return new RequestContext(client, uri, key, discovery);
        }

        @Override
        public void close() {
            client.closeResource();
        }
    }

    private static final class EqualIdentity {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof EqualIdentity;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (ZoneOffset.UTC.equals(zone)) {
                return this;
            }
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        private Instant nextObservation() {
            Instant observedAt = instant;
            instant = instant.plusNanos(1);
            return observedAt;
        }
    }

}
