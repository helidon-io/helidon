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

package io.helidon.webclient.http2;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsMaterial;
import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;
import io.helidon.webclient.api.AltSvcHeader;
import io.helidon.webclient.api.ClientConnectionTarget;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.ConnectionKey;
import io.helidon.webclient.api.DnsAddressLookup;
import io.helidon.webclient.api.Proxy;
import io.helidon.webclient.spi.DnsResolver;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class Http2AltSvcCacheTest {
    private static final Instant START = Instant.parse("2026-08-24T00:00:00Z");
    private static final DnsResolver DNS = (_, _) -> InetAddress.getLoopbackAddress();

    @Test
    void selectsSameHostH2ByLookupAndExactTarget() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target,
                     header(clock, "h3=\":7443\", h2=\":8443\"; ma=60"),
                     true,
                     false);

        Http2AltSvcCache.Selection byTarget = cache.select(target, false, _ -> false);
        Http2AltSvcCache.Candidate byLookup = cache.selectRoute(target.lookupKey(), false, _ -> false);

        assertThat(byLookup, notNullValue());
        assertThat(byLookup.proxyRoute(), is(target.proxyRoute()));
        assertThat(byTarget.originTarget(), sameInstance(target));
        assertThat(byTarget.host(), is("origin.example"));
        assertThat(byTarget.port(), is(8443));
        assertThat(byTarget.networkGeneration(), is(0L));
        assertThat(byTarget.establishAllowed(), is(true));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), sameInstance(byLookup));
        assertThat(cache.select(target, false, _ -> false), sameInstance(byTarget));
        assertThat(invalidations, is(empty()));

        cache.close();
    }

    @Test
    void sharesAdvertisementAcrossDnsCaseWithoutSharingRawTargetSelection() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget upper = target(tls, "Example.COM");
        ClientConnectionTarget lower = target(tls, "example.com");

        assertThat(upper, not(is(lower)));

        cache.record(upper, header(clock, "h2=\":8443\"; ma=3600"), true, false);
        Http2AltSvcCache.Selection upperSelection = cache.select(upper, false, _ -> false);
        Http2AltSvcCache.Selection lowerSelection = cache.select(lower, false, _ -> false);

        assertThat(upperSelection.originTarget(), sameInstance(upper));
        assertThat(lowerSelection.originTarget(), sameInstance(lower));
        assertThat(lowerSelection.sameRouteGeneration(upperSelection), is(true));
        assertThat(cache.select(upper, false, _ -> false), sameInstance(upperSelection));
        assertThat(cache.select(lower, false, _ -> false), sameInstance(lowerSelection));
        assertThat(cache.mayContain("EXAMPLE.COM"), is(true));
        assertThat(cache.selectRoute(lower.lookupKey(), false, _ -> false), notNullValue());

        cache.record(lower, header(clock, "h2=\":8443\"; ma=7200"), true, false);

        assertThat(cache.select(upper, false, _ -> false), sameInstance(upperSelection));
        assertThat(cache.select(lower, false, _ -> false), sameInstance(lowerSelection));

        cache.record(upper, header(clock, "clear"), true, false);
        cache.record(lower, header(clock, "h2=\":9443\"; ma=3600"), true, false);

        Http2AltSvcCache.Selection reverse = cache.select(upper, false, _ -> false);
        assertThat(reverse.originTarget(), sameInstance(upper));
        assertThat(reverse.port(), is(9443));

        cache.recordFailure(reverse);

        assertThat(cache.select(lower, false, _ -> true, _ -> true), nullValue());

        clock.advance(Duration.ofMinutes(5));
        Http2AltSvcCache.Selection retry = cache.select(lower, false, _ -> false);
        cache.recordMisdirected(retry);

        assertThat(cache.select(upper, false, _ -> true, _ -> true), nullValue());

        cache.close();
    }

    @Test
    void persistentMixedCaseAdvertisementGetsOneSharedNetworkGeneration() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget upper = target(tls, "Example.COM");
        ClientConnectionTarget lower = target(tls, "example.com");

        cache.record(upper, header(clock, "h2=\":8443\"; ma=3600; persist=1"), true, false);
        Http2AltSvcCache.Selection beforeNetworkChange = cache.select(lower, false, _ -> false);

        cache.networkChanged();

        Http2AltSvcCache.Selection lowerSelection = cache.select(lower, false, _ -> false);
        Http2AltSvcCache.Selection upperSelection = cache.select(upper, false, _ -> false);
        assertThat(lowerSelection.networkGeneration(), is(1L));
        assertThat(upperSelection.networkGeneration(), is(1L));
        assertThat(lowerSelection.sameRouteGeneration(upperSelection), is(true));
        assertThat(lowerSelection.sameRouteGeneration(beforeNetworkChange), is(false));

        cache.close();
    }

    @Test
    void expiredMixedCaseAdvertisementReusesOnlyTheExactRawTargetPool() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget upper = target(tls, "Example.COM");
        ClientConnectionTarget lower = target(tls, "example.com");

        cache.record(upper, header(clock, "h2=\":8443\"; ma=1"), true, false);
        Http2AltSvcCache.Selection establishedUpper = cache.select(upper, false, _ -> false);
        clock.advance(Duration.ofSeconds(2));

        assertThat(cache.select(lower, false, _ -> false, _ -> true), nullValue());
        assertThat(invalidations, is(empty()));
        assertThat(cache.select(upper,
                                false,
                                candidate -> candidate.sameRouteGeneration(establishedUpper),
                                _ -> true),
                   sameInstance(establishedUpper));

        assertThat(cache.select(lower, false, _ -> false, _ -> false), nullValue());
        assertThat(invalidations, hasSize(1));
        assertThat(establishedUpper.sameGeneration(invalidations.getFirst()), is(true));

        cache.close();
    }

    @Test
    void lookupRecoversWhenSharedNegativeEntryExpires() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "Origin.EXAMPLE");

        cache.record(target, header(clock, "h2=\":8443\"; ma=3600"), true, false);
        cache.recordFailure(cache.select(target, false, _ -> false));

        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> true), nullValue());

        clock.advance(Duration.ofMinutes(5));

        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());

        cache.close();
    }

    @Test
    void hostPresenceHintCountsLogicalRoutes() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget first = target(tls, "https", "origin.example", 443);
        ClientConnectionTarget second = target(tls, "https", "origin.example", 444);

        assertThat(cache.mayContain("origin.example"), is(false));
        assertThat(cache.mayContain("other.example"), is(false));

        cache.record(first, header(clock, "h2=\":8443\"; ma=60"), true, false);

        assertThat(cache.mayContain("origin.example"), is(true));
        assertThat(cache.mayContain("other.example"), is(false));

        cache.record(first, header(clock, "h2=\":8443\"; ma=120"), true, false);
        cache.record(first, header(clock, "h2=\":9443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection replacement = cache.select(first, false, _ -> false);
        cache.recordFailure(replacement);

        assertThat(cache.mayContain("origin.example"), is(true));

        cache.record(first, header(clock, "clear"), true, false);

        assertThat(cache.mayContain("origin.example"), is(false));

        cache.record(first, header(clock, "h2=\":8443\"; ma=60"), true, false);
        cache.record(second, header(clock, "h2=\":8443\"; ma=60"), true, false);
        cache.record(first, header(clock, "clear"), true, false);

        assertThat(cache.mayContain("origin.example"), is(true));

        cache.record(second, header(clock, "clear"), true, false);

        assertThat(cache.mayContain("origin.example"), is(false));

        cache.close();
    }

    @Test
    void sameAuthorityRefreshPreservesSelectionAndIndexesWithoutInvalidation() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection selection = cache.select(target, false, _ -> false);

        clock.advance(Duration.ofSeconds(30));
        cache.record(target, header(clock, "h2=\":8443\"; ma=120; persist=1"), true, false);

        assertThat(cache.select(target, false, _ -> false), sameInstance(selection));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());
        assertThat(selection.establishAllowed(), is(true));
        assertThat(selection.authority().toString(), is("origin.example:8443"));
        assertThat(selection.authority(), sameInstance(selection.authority()));
        assertThat(cache.mayContain(target.connectionKey().host()), is(true));
        assertThat(invalidations, is(empty()));

        clock.advance(Duration.ofSeconds(61));
        assertThat(cache.select(target, false, _ -> false), sameInstance(selection));

        cache.record(target, header(clock, "h2=\":8443\"; ma=0"), true, false);

        assertThat(cache.select(target,
                                false,
                                candidate -> candidate.sameRouteGeneration(selection)),
                   sameInstance(selection));
        assertThat(selection.establishAllowed(), is(false));
        assertThat(cache.mayContain(target.connectionKey().host()), is(true));
        assertThat(invalidations, is(empty()));

        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(cache.mayContain(target.connectionKey().host()), is(false));
        assertThat(invalidations, hasSize(1));

        cache.close();
    }

    @Test
    void sameAuthorityRefreshChangesPersistenceWithoutReplacingSelection() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection transientSelection = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "h2=\":8443\"; ma=60; persist=1"), true, false);

        assertThat(cache.select(target, false, _ -> false), sameInstance(transientSelection));

        cache.networkChanged();

        Http2AltSvcCache.Selection persistentSelection = cache.select(target, false, _ -> false);
        assertThat(persistentSelection, not(sameInstance(transientSelection)));
        assertThat(invalidations, hasSize(1));

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);

        assertThat(cache.select(target, false, _ -> false), sameInstance(persistentSelection));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());

        cache.networkChanged();

        assertThat(cache.select(target, false, _ -> true), nullValue());
        assertThat(cache.mayContain(target.connectionKey().host()), is(false));
        assertThat(invalidations, hasSize(2));

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection revived = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "h2=\":8443\"; ma=120"), true, false);

        assertThat(revived, not(sameInstance(persistentSelection)));
        assertThat(cache.select(target, false, _ -> false), sameInstance(revived));
        assertThat(invalidations, hasSize(2));

        cache.close();
    }

    @Test
    void sameAuthorityRefreshClearsNegativeStateWithoutInvalidatingReplacement() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection failed = cache.select(target, false, _ -> false);
        cache.recordFailure(failed);

        assertThat(cache.select(target, false, _ -> true), nullValue());
        assertThat(invalidations, hasSize(1));

        cache.record(target, header(clock, "h2=\":8443\"; ma=120"), true, false);

        Http2AltSvcCache.Selection retry = cache.select(target, false, _ -> false);
        assertThat(retry, not(sameInstance(failed)));
        assertThat(cache.current(retry), is(true));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());
        assertThat(invalidations, hasSize(1));

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);

        assertThat(cache.select(target, false, _ -> false), sameInstance(retry));
        assertThat(invalidations, hasSize(1));

        cache.close();
    }

    @Test
    void identityMemoFallsBackForEqualTargetAndTracksReplacementAndClear() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget target = target(tls, "origin.example");
        ClientConnectionTarget equalTarget = target(tls, "origin.example");

        assertThat(equalTarget, is(target));
        assertThat(equalTarget, not(sameInstance(target)));

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection first = cache.select(target, false, _ -> false);
        cache.record(equalTarget, header(clock, "h2=\":8443\"; ma=120"), true, false);

        assertThat(cache.select(target, false, _ -> false), sameInstance(first));
        assertThat(cache.selectRoute(equalTarget.lookupKey(), false, _ -> false), notNullValue());

        cache.record(equalTarget, header(clock, "h2=\":9443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection replacement = cache.select(target, false, _ -> false);
        cache.record(equalTarget, header(clock, "h2=\":9443\"; ma=120"), true, false);

        assertThat(replacement, not(sameInstance(first)));
        assertThat(cache.select(equalTarget, false, _ -> false), sameInstance(replacement));
        assertThat(invalidations, hasSize(1));

        cache.record(equalTarget, header(clock, "clear"), true, false);

        assertThat(cache.select(target, false, _ -> true), nullValue());
        assertThat(invalidations, hasSize(2));

        cache.record(target, header(clock, "h2=\":10443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection revived = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "h2=\":10443\"; ma=120"), true, false);

        assertThat(revived, not(sameInstance(replacement)));
        assertThat(cache.select(target, false, _ -> false), sameInstance(revived));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());
        assertThat(invalidations, hasSize(2));

        cache.close();
    }

    @Test
    void hostPresenceHintTracksRemovalLifecycle() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget unsupported = target(tls, "unsupported.example");
        ClientConnectionTarget misdirected = target(tls, "misdirected.example");
        ClientConnectionTarget expired = target(tls, "expired.example");
        Tls reloadedTls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget staleTls = target(reloadedTls, "stale-tls.example");
        ClientConnectionTarget persistent = target(tls, "persistent.example");
        ClientConnectionTarget transientTarget = target(tls, "transient.example");

        cache.record(unsupported, header(clock, "h2=\":8443\"; ma=60"), true, false);
        cache.record(unsupported, header(clock, "H2=\":8443\"; ma=60"), true, false);
        assertThat(cache.mayContain("unsupported.example"), is(false));

        cache.record(misdirected, header(clock, "h2=\":8443\"; ma=60"), true, false);
        cache.recordMisdirected(cache.select(misdirected, false, _ -> false));
        assertThat(cache.mayContain("misdirected.example"), is(false));

        cache.record(expired, header(clock, "h2=\":8443\"; ma=1"), true, false);
        clock.advance(Duration.ofSeconds(2));
        assertThat(cache.select(expired, false, _ -> false), nullValue());
        assertThat(cache.mayContain("expired.example"), is(false));

        cache.record(staleTls, header(clock, "h2=\":8443\"; ma=60"), true, false);
        reloadedTls.reload(TlsMaterial.builder().trustAll(true).build());
        assertThat(cache.select(staleTls, false, _ -> false), nullValue());
        assertThat(cache.mayContain("stale-tls.example"), is(false));

        cache.record(persistent, header(clock, "h2=\":8443\"; ma=60; persist=1"), true, false);
        cache.record(transientTarget, header(clock, "h2=\":8443\"; ma=60"), true, false);
        cache.networkChanged();

        assertThat(cache.mayContain("persistent.example"), is(true));
        assertThat(cache.mayContain("transient.example"), is(false));

        cache.close();

        assertThat(cache.mayContain("persistent.example"), is(false));
    }

    @Test
    void rejectsInsecureExplicitAndIneligibleOrigins() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        AltSvcHeader advertisement = header(clock, "h2=\":8443\"; ma=60");
        ClientConnectionTarget target = target(tls, "origin.example");

        cache.record(target, advertisement, false, false);
        assertThat(cache.select(target, false, _ -> false), nullValue());

        cache.record(target, advertisement, true, true);
        assertThat(cache.select(target, false, _ -> false), nullValue());

        cache.record(target, advertisement, true, false);
        assertThat(cache.select(target, true, _ -> true), nullValue());

        ClientConnectionTarget httpTarget = target(Tls.builder().enabled(false).build(), "http", "origin.example");
        cache.record(httpTarget, advertisement, true, false);
        assertThat(cache.select(httpTarget, false, _ -> false), nullValue());

        Proxy proxy = Proxy.builder().host("proxy.example").port(8181).build();
        ClientConnectionTarget proxyTarget = target(tls, "https", "origin.example", proxy, DNS);
        cache.record(proxyTarget, advertisement, true, false);
        assertThat(cache.select(proxyTarget, false, _ -> false), nullValue());

        Proxy noProxy = Proxy.builder()
                .host("proxy.example")
                .port(8181)
                .addNoProxy("127.0.0.1")
                .build();
        ClientConnectionTarget addressBoundTarget = target(tls,
                                                            "https",
                                                            "origin.example",
                                                            noProxy,
                                                            (_, _) -> InetAddress.ofLiteral("127.0.0.1"));
        cache.record(addressBoundTarget, advertisement, true, false);
        assertThat(addressBoundTarget.proxyRoute().addressBound(), is(true));
        assertThat(cache.select(addressBoundTarget, false, _ -> false), nullValue());

        ClientConnectionTarget udsTarget = udsTarget(tls, "origin.example");
        cache.record(udsTarget, advertisement, true, false);
        assertThat(cache.select(udsTarget, false, _ -> false), nullValue());

        cache.close();
    }

    @Test
    void replacesExistingStateForUnsupportedOrCrossHostAdvertisement() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection first = cache.select(target, false, _ -> false);

        cache.record(target, header(clock, "H2=\":9443\"; ma=60"), true, false);

        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(cache.current(first), is(false));
        assertThat(invalidations, hasSize(1));
        assertThat(first.sameGeneration(invalidations.getFirst()), is(true));

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection second = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "h2=\"other.example:8443\"; ma=60"), true, false);

        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(cache.current(second), is(false));
        assertThat(invalidations, hasSize(2));

        cache.close();
    }

    @Test
    void expiredAdvertisementReusesOnlyExactEstablishedGeneration() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=1"), true, false);
        Http2AltSvcCache.Selection fresh = cache.select(target, false, _ -> false);
        clock.advance(Duration.ofSeconds(2));

        Http2AltSvcCache.Selection reuse = cache.select(target,
                                                        false,
                                                        candidate -> candidate.sameRouteGeneration(fresh));

        assertThat(reuse.sameRouteGeneration(fresh), is(true));
        assertThat(reuse.establishAllowed(), is(false));
        cache.recordFailure(reuse);
        assertThat(cache.current(reuse), is(true));
        assertThat(cache.select(target,
                                false,
                                candidate -> candidate.sameRouteGeneration(reuse)),
                   sameInstance(reuse));

        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(cache.current(reuse), is(false));
        assertThat(invalidations, hasSize(1));
        assertThat(reuse.sameGeneration(invalidations.getFirst()), is(true));

        cache.close();
    }

    @Test
    void expiredEstablishedProbeDoesNotBlockFreshUnrelatedSelection() throws Exception {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget expiredTarget = target(tls, "expired.example");
        ClientConnectionTarget freshTarget = target(tls, "fresh.example");

        cache.record(expiredTarget, header(clock, "h2=\":8443\"; ma=1"), true, false);
        cache.record(freshTarget, header(clock, "h2=\":9443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection expired = cache.select(expiredTarget, false, _ -> false);
        Http2AltSvcCache.Selection fresh = cache.select(freshTarget, false, _ -> false);
        clock.advance(Duration.ofSeconds(2));

        CountDownLatch enteredEstablishedProbe = new CountDownLatch(1);
        CountDownLatch releaseEstablishedProbe = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Http2AltSvcCache.Selection> expiredRead = executor.submit(() -> cache.select(
                    expiredTarget,
                    false,
                    selection -> {
                        enteredEstablishedProbe.countDown();
                        try {
                            releaseEstablishedProbe.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while holding established probe", e);
                        }
                        return selection.sameRouteGeneration(expired);
                    }));

            try {
                assertThat(enteredEstablishedProbe.await(5, TimeUnit.SECONDS), is(true));
                Future<Http2AltSvcCache.Selection> freshRead = executor.submit(
                        () -> cache.select(freshTarget, false, _ -> false));
                assertThat(freshRead.get(5, TimeUnit.SECONDS), sameInstance(fresh));
            } finally {
                releaseEstablishedProbe.countDown();
            }

            assertThat(expiredRead.get(5, TimeUnit.SECONDS), sameInstance(expired));
        } finally {
            cache.close();
        }
    }

    @Test
    void replacementDuringEstablishedProbeCannotReturnStaleSelection() throws Exception {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=1"), true, false);
        Http2AltSvcCache.Selection stale = cache.select(target, false, _ -> false);
        clock.advance(Duration.ofSeconds(2));

        CountDownLatch enteredEstablishedProbe = new CountDownLatch(1);
        CountDownLatch releaseEstablishedProbe = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Http2AltSvcCache.Selection> selectionRead = executor.submit(() -> cache.select(
                    target,
                    false,
                    selection -> {
                        enteredEstablishedProbe.countDown();
                        try {
                            releaseEstablishedProbe.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while holding established probe", e);
                        }
                        return selection.sameRouteGeneration(stale);
                    }));

            try {
                assertThat(enteredEstablishedProbe.await(5, TimeUnit.SECONDS), is(true));
                Future<?> replacementWrite = executor.submit(
                        () -> cache.record(target, header(clock, "h2=\":9443\"; ma=60"), true, false));
                assertThat(replacementWrite.get(5, TimeUnit.SECONDS), nullValue());
            } finally {
                releaseEstablishedProbe.countDown();
            }

            Http2AltSvcCache.Selection replacement = cache.select(target, false, _ -> false);
            assertThat(selectionRead.get(5, TimeUnit.SECONDS), sameInstance(replacement));
            assertThat(cache.current(stale), is(false));
            assertThat(cache.current(replacement), is(true));
            assertThat(cache.select(target, false, _ -> false), sameInstance(replacement));
        } finally {
            cache.close();
        }
    }

    @Test
    void sameAuthorityRefreshDuringExpiredEstablishedProbeReturnsCoherentSelection() throws Exception {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=1"), true, false);
        Http2AltSvcCache.Selection expired = cache.select(target, false, _ -> false);
        clock.advance(Duration.ofSeconds(2));

        CountDownLatch enteredEstablishedProbe = new CountDownLatch(1);
        CountDownLatch releaseEstablishedProbe = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Http2AltSvcCache.Selection> selectionRead = executor.submit(() -> cache.select(
                    target,
                    false,
                    _ -> {
                        enteredEstablishedProbe.countDown();
                        try {
                            releaseEstablishedProbe.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while holding established probe", e);
                        }
                        return false;
                    }));

            try {
                assertThat(enteredEstablishedProbe.await(5, TimeUnit.SECONDS), is(true));
                Future<?> refreshWrite = executor.submit(
                        () -> cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false));
                assertThat(refreshWrite.get(5, TimeUnit.SECONDS), nullValue());
            } finally {
                releaseEstablishedProbe.countDown();
            }

            assertThat(selectionRead.get(5, TimeUnit.SECONDS), sameInstance(expired));
            assertThat(expired.establishAllowed(), is(true));
            assertThat(cache.current(expired), is(true));
            assertThat(cache.select(target, false, _ -> false), sameInstance(expired));
            assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());
        } finally {
            cache.close();
        }
    }

    @Test
    void tlsReloadDuringEstablishedProbeCannotReturnStaleSelection() throws Exception {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget target = target(tls, "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=1"), true, false);
        Http2AltSvcCache.Selection stale = cache.select(target, false, _ -> false);
        clock.advance(Duration.ofSeconds(2));

        CountDownLatch enteredEstablishedProbe = new CountDownLatch(1);
        CountDownLatch releaseEstablishedProbe = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Http2AltSvcCache.Selection> selectionRead = executor.submit(() -> cache.select(
                    target,
                    false,
                    selection -> {
                        enteredEstablishedProbe.countDown();
                        try {
                            releaseEstablishedProbe.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while holding established probe", e);
                        }
                        return selection.sameRouteGeneration(stale);
                    }));

            try {
                assertThat(enteredEstablishedProbe.await(5, TimeUnit.SECONDS), is(true));
                tls.reload(TlsMaterial.builder().trustAll(true).build());
            } finally {
                releaseEstablishedProbe.countDown();
            }

            assertThat(selectionRead.get(5, TimeUnit.SECONDS), nullValue());
            assertThat(cache.current(stale), is(false));
            assertThat(cache.select(target, false, _ -> true), nullValue());
        } finally {
            cache.close();
        }
    }

    @Test
    void replacementClearAndZeroMaximumAgeInvalidateMatchingGeneration() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection first = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "h2=\":8443\"; ma=120; persist=1"), true, false);
        Http2AltSvcCache.Selection extended = cache.select(target, false, _ -> false);

        assertThat(extended, sameInstance(first));
        assertThat(invalidations, is(empty()));

        cache.record(target, header(clock, "h2=\":9443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection replacement = cache.select(target, false, _ -> false);

        assertThat(replacement, not(sameInstance(first)));
        assertThat(replacement.sameRouteGeneration(first), is(false));
        assertThat(cache.current(first), is(false));
        assertThat(invalidations, hasSize(1));

        cache.record(target, header(clock, "h2=\":9443\"; ma=0"), true, false);

        Http2AltSvcCache.Selection zeroAgeReuse = cache.select(
                target,
                false,
                candidate -> candidate.sameRouteGeneration(replacement));
        assertThat(cache.current(replacement), is(true));
        assertThat(zeroAgeReuse, sameInstance(replacement));
        assertThat(zeroAgeReuse.establishAllowed(), is(false));
        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(invalidations, hasSize(2));

        cache.record(target, header(clock, "h2=\":10443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection beforeClear = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "clear"), true, false);

        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(cache.current(beforeClear), is(false));
        assertThat(invalidations, hasSize(3));

        cache.close();
    }

    @Test
    void preSendFailureIsNegativeForFiveMinutes() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=3600"), true, false);
        Http2AltSvcCache.Selection failed = cache.select(target, false, _ -> false);
        cache.recordFailure(failed);

        assertThat(cache.current(failed), is(false));
        assertThat(cache.select(target, false, _ -> true), nullValue());
        assertThat(invalidations, hasSize(1));

        clock.advance(Duration.ofMinutes(5));
        Http2AltSvcCache.Selection retry = cache.select(target, false, _ -> false);

        assertThat(retry.sameRouteGeneration(failed), is(false));
        assertThat(retry.establishAllowed(), is(true));
        assertThat(cache.current(retry), is(true));
        assertThat(cache.select(target, false, _ -> false), sameInstance(retry));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> false), notNullValue());

        cache.close();
    }

    @Test
    void staleMisdirectedResponseCannotRemoveReplacement() {
        MutableClock clock = new MutableClock(START);
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, _ -> { });
        ClientConnectionTarget target = target(Tls.builder().trustAll(true).build(), "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection stale = cache.select(target, false, _ -> false);
        cache.record(target, header(clock, "h2=\":9443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection current = cache.select(target, false, _ -> false);

        cache.recordMisdirected(stale);
        Http2AltSvcCache.Selection retained = cache.select(target, false, _ -> false);

        assertThat(retained, sameInstance(current));

        cache.recordMisdirected(current);

        assertThat(cache.select(target, false, _ -> false), nullValue());
        assertThat(cache.current(current), is(false));

        cache.close();
    }

    @Test
    void networkChangeRetainsOnlyPersistentAdvertisementWithNewGeneration() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget persistentTarget = target(tls, "persistent.example");
        ClientConnectionTarget transientTarget = target(tls, "transient.example");

        cache.record(persistentTarget, header(clock, "h2=\":8443\"; ma=60; persist=1"), true, false);
        cache.record(transientTarget, header(clock, "h2=\":9443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection persistent = cache.select(persistentTarget, false, _ -> false);
        Http2AltSvcCache.Selection transientSelection = cache.select(transientTarget, false, _ -> false);

        cache.networkChanged();

        Http2AltSvcCache.Selection retained = cache.select(persistentTarget, false, _ -> false);
        assertThat(retained.networkGeneration(), is(1L));
        assertThat(retained.sameRouteGeneration(persistent), is(false));
        assertThat(cache.select(transientTarget, false, _ -> false), nullValue());
        assertThat(cache.current(persistent), is(false));
        assertThat(cache.current(transientSelection), is(false));
        assertThat(invalidations, hasSize(2));

        cache.close();
        assertThat(cache.current(retained), is(false));
        assertThat(cache.select(persistentTarget, false, _ -> false), nullValue());
    }

    @Test
    void tlsReloadInvalidatesCapturedTargetGeneration() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        Tls tls = Tls.builder().trustAll(true).build();
        ClientConnectionTarget target = target(tls, "origin.example");

        cache.record(target, header(clock, "h2=\":8443\"; ma=60"), true, false);
        Http2AltSvcCache.Selection selection = cache.select(target, false, _ -> false);
        tls.reload(TlsMaterial.builder().trustAll(true).build());

        assertThat(cache.current(selection), is(false));
        assertThat(cache.selectRoute(target.lookupKey(), false, _ -> true), nullValue());
        assertThat(invalidations, hasSize(1));
        assertThat(selection.sameGeneration(invalidations.getFirst()), is(true));

        ClientConnectionTarget currentTarget = target(tls, "origin.example");
        assertThat(currentTarget, is(not(target)));
        assertThat(cache.select(currentTarget, false, _ -> true), nullValue());

        cache.close();
    }

    @Test
    void evictsOldestInsertedOriginAtBoundDespiteRead() {
        MutableClock clock = new MutableClock(START);
        List<Http2AltSvcCache.Generation> invalidations = new ArrayList<>();
        Http2AltSvcCache cache = Http2AltSvcCache.create(clock, invalidations::add);
        Tls tls = Tls.builder().trustAll(true).build();
        AltSvcHeader advertisement = header(clock, "h2=\":8443\"; ma=60");
        ClientConnectionTarget first = null;

        for (int index = 0; index < 1_000; index++) {
            ClientConnectionTarget target = target(tls, "origin-" + index + ".example");
            if (index == 0) {
                first = target;
            }
            cache.record(target, advertisement, true, false);
        }
        Http2AltSvcCache.Selection firstSelection = cache.select(first, false, _ -> false);
        assertThat(firstSelection, notNullValue());
        cache.record(first, advertisement, true, false);

        ClientConnectionTarget last = target(tls, "origin-1000.example");
        cache.record(last, advertisement, true, false);

        assertThat(cache.select(first, false, _ -> false), nullValue());
        assertThat(cache.select(last, false, _ -> false), notNullValue());
        assertThat(cache.mayContain(first.connectionKey().host()), is(false));
        assertThat(cache.mayContain(last.connectionKey().host()), is(true));
        assertThat(invalidations, hasSize(1));
        assertThat(firstSelection.sameGeneration(invalidations.getFirst()), is(true));

        cache.record(first, advertisement, true, false);
        Http2AltSvcCache.Selection revived = cache.select(first, false, _ -> false);
        cache.record(first, header(clock, "h2=\":8443\"; ma=120"), true, false);

        assertThat(revived, not(sameInstance(firstSelection)));
        assertThat(cache.select(first, false, _ -> false), sameInstance(revived));
        assertThat(cache.mayContain(first.connectionKey().host()), is(true));
        assertThat(invalidations, hasSize(2));

        cache.close();
    }

    private static AltSvcHeader header(Clock clock, String... values) {
        WritableHeaders<?> headers = WritableHeaders.create();
        for (String value : values) {
            headers.add(HeaderValues.create(HeaderNames.ALT_SVC, value));
        }
        return AltSvcHeader.parse(ClientResponseHeaders.create(headers), clock.instant()).orElseThrow();
    }

    private static ClientConnectionTarget target(Tls tls, String host) {
        return target(tls, "https", host);
    }

    private static ClientConnectionTarget target(Tls tls, String scheme, String host) {
        int port = "https".equals(scheme) ? 443 : 80;
        return target(tls, scheme, host, port, Proxy.noProxy(), DNS);
    }

    private static ClientConnectionTarget target(Tls tls, String scheme, String host, int port) {
        return target(tls, scheme, host, port, Proxy.noProxy(), DNS);
    }

    private static ClientConnectionTarget target(Tls tls,
                                                  String scheme,
                                                  String host,
                                                  Proxy proxy,
                                                  DnsResolver dnsResolver) {
        int port = "https".equals(scheme) ? 443 : 80;
        return target(tls, scheme, host, port, proxy, dnsResolver);
    }

    private static ClientConnectionTarget target(Tls tls,
                                                  String scheme,
                                                  String host,
                                                  int port,
                                                  Proxy proxy,
                                                  DnsResolver dnsResolver) {
        ConnectionKey connectionKey = ConnectionKey.create(scheme,
                                                           host,
                                                           port,
                                                           tls,
                                                           dnsResolver,
                                                           DnsAddressLookup.IPV4,
                                                           proxy);
        return ClientConnectionTarget.create(connectionKey, scheme);
    }

    private static ClientConnectionTarget udsTarget(Tls tls, String host) {
        ClientUri uri = ClientUri.create(URI.create("https://" + host));
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(Path.of("alt-svc-test.sock"));
        ConnectionKey connectionKey = ConnectionKey.createUnixDomainSocket(uri,
                                                                           tls,
                                                                           DNS,
                                                                           DnsAddressLookup.IPV4,
                                                                           address);
        ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());
        return ClientConnectionTarget.createUnixDomainSocket(connectionKey, uri, headers, address);
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
    }
}
