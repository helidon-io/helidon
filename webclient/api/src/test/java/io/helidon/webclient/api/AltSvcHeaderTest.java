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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class AltSvcHeaderTest {
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-23T00:01:30Z");

    @Test
    void distinguishesAbsentMalformedAndClearUpdates() {
        assertThat(AltSvcHeader.create(responseHeaders(), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("CLEAR"), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=:443"), RECEIVED_AT).isEmpty(), is(true));

        AltSvcHeader clear = AltSvcHeader.create(responseHeaders(" \tclear\t "), RECEIVED_AT).orElseThrow();
        assertThat(clear.clear(), is(true));
        assertThat(clear.alternatives(), is(empty()));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\"unterminated", "clear"), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void parsesEveryFieldLineAndQuotedCommaAtomically() {
        AltSvcHeader parsed = AltSvcHeader.create(
                responseHeaders("h3=\":443\"; ma=60; persist=1",
                                "w%3Dx%3Ay#z=\"other.example:8443\"; note=\"a,b\""),
                RECEIVED_AT).orElseThrow();

        assertThat(parsed.clear(), is(false));
        assertThat(parsed.alternatives(), hasSize(2));
        AltSvcHeader.Alternative first = parsed.alternatives().get(0);
        assertThat(first.protocolId(), is("h3"));
        assertThat(first.host().isEmpty(), is(true));
        assertThat(first.port(), is(443));
        assertThat(first.expirationTime(), is(RECEIVED_AT.plusSeconds(60)));
        assertThat(first.persist(), is(true));

        AltSvcHeader.Alternative second = parsed.alternatives().get(1);
        assertThat(second.protocolId(), is("w=x:y#z"));
        assertThat(second.host().orElseThrow(), is("other.example"));
        assertThat(second.port(), is(8443));
        assertThat(second.persist(), is(false));
    }

    @Test
    void ignoresBoundedEmptyCommaListElements() {
        AltSvcHeader parsed = AltSvcHeader.create(
                responseHeaders(", , h3=\":443\",,\t,", ", h2=\":8443\", ,"),
                RECEIVED_AT).orElseThrow();

        assertThat(parsed.alternatives(), hasSize(2));
        assertThat(parsed.alternatives().get(0).protocolId(), is("h3"));
        assertThat(parsed.alternatives().get(1).protocolId(), is("h2"));
    }

    @Test
    void requiresClearAsCompleteFieldValue() {
        AltSvcHeader clear = AltSvcHeader.create(responseHeaders(" \tclear\t "), RECEIVED_AT).orElseThrow();

        assertThat(clear.clear(), is(true));
        assertThat(clear.alternatives(), is(empty()));
        assertThat(AltSvcHeader.create(responseHeaders(",, \tclear\t, ,"), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h2=\":8443\", clear"), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("clear", "h2=\":8443\""), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void preservesUnsupportedAlternativesAndDoesNotDecodeAuthority() {
        AltSvcHeader.Alternative alternative = AltSvcHeader.create(
                        responseHeaders("unrelated=\"%68ost.example:9443\""),
                        RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(alternative.protocolId(), is("unrelated"));
        assertThat(alternative.host().orElseThrow(), is("%68ost.example"));
        assertThat(alternative.port(), is(9443));
    }

    @Test
    void mapsDecodedProtocolOctetsWithoutTextTranscoding() {
        AltSvcHeader.Alternative alternative = AltSvcHeader.create(responseHeaders("%00%FF=\":443\""), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(alternative.protocolId().length(), is(2));
        assertThat((int) alternative.protocolId().charAt(0), is(0));
        assertThat((int) alternative.protocolId().charAt(1), is(255));

        AltSvcHeader.Alternative percent = AltSvcHeader.create(responseHeaders("x%25y=\":443\""), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        assertThat(percent.protocolId(), is("x%y"));

        assertThat(AltSvcHeader.create(responseHeaders("h%=\":443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h%3=\":443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h%GG=\":443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h%32=\":443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h%3a=\":443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("%ff=\":443\""), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void enforcesDecodedProtocolLength() {
        assertThat(AltSvcHeader.create(responseHeaders("a".repeat(255) + "=\":443\""), RECEIVED_AT).isPresent(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("a".repeat(256) + "=\":443\""), RECEIVED_AT).isEmpty(),
                   is(true));
    }

    @Test
    void validatesPortOnlyDnsAndIpv6Authorities() {
        AltSvcHeader parsed = AltSvcHeader.create(
                responseHeaders("h3=\":443\", h2=\"other.example:8443\", h3-29=\"[2001:db8::1]:9443\","
                                        + " h3-v1=\"[v1.fe80]:10443\""),
                RECEIVED_AT).orElseThrow();

        assertThat(parsed.alternatives().get(0).host().isEmpty(), is(true));
        assertThat(parsed.alternatives().get(1).host().orElseThrow(), is("other.example"));
        assertThat(parsed.alternatives().get(2).host().orElseThrow(), is("2001:db8::1"));
        assertThat(parsed.alternatives().get(3).host().orElseThrow(), is("v1.fe80"));
        assertThat(parsed.alternatives().get(3).port(), is(10443));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\"2001:db8::1:443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\"[2001:db8::1]443\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":0\""), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":65536\""), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void appliesDefaultMaxAgeAndSaturatedAge() {
        AltSvcHeader.Alternative defaultAge = AltSvcHeader.create(responseHeaders("h3=\":443\""), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        assertThat(defaultAge.expirationTime(), is(RECEIVED_AT.plus(Duration.ofHours(24))));

        WritableHeaders<?> agedHeaders = altSvcHeaders("h3=\":443\"; ma=60");
        agedHeaders.add(HeaderValues.create(HeaderNames.AGE, "999999999999999999999999999999"));
        AltSvcHeader.Alternative expired = AltSvcHeader.create(ClientResponseHeaders.create(agedHeaders), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        assertThat(expired.expirationTime(), is(RECEIVED_AT));

        AltSvcHeader.Alternative saturated = AltSvcHeader.create(
                        responseHeaders("h3=\":443\"; ma=999999999999999999999999999999"),
                        RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        long maximumFreshness = Duration.between(RECEIVED_AT, Instant.MAX).toSeconds();
        assertThat(saturated.expirationTime(), is(RECEIVED_AT.plusSeconds(maximumFreshness)));
    }

    @Test
    void accountsForTheGreaterOfAgeAndDate() {
        WritableHeaders<?> headers = altSvcHeaders("h3=\":443\"; ma=120");
        headers.add(HeaderValues.create(HeaderNames.AGE, "30"));
        headers.add(HeaderValues.create(HeaderNames.DATE, "Sun, 23 Aug 2026 00:00:00 GMT"));

        AltSvcHeader.Alternative alternative = AltSvcHeader.create(ClientResponseHeaders.create(headers), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(alternative.expirationTime(), is(RECEIVED_AT.plusSeconds(30)));
    }

    @Test
    void materializesMemoizedGrammarForEachResponse() {
        String value = "h3=\":443\"; ma=120; persist=1";
        Instant laterReceivedAt = RECEIVED_AT.plusSeconds(150);

        AltSvcHeader.Alternative first = AltSvcHeader.create(
                        timedResponseHeaders(value, "45", "Sun, 23 Aug 2026 00:01:00 GMT"),
                        RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        AltSvcHeader.Alternative second = AltSvcHeader.create(
                        timedResponseHeaders(value, "20", "Sun, 23 Aug 2026 00:03:30 GMT"),
                        laterReceivedAt)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(first.expirationTime(), is(RECEIVED_AT.plusSeconds(75)));
        assertThat(second.expirationTime(), is(laterReceivedAt.plusSeconds(90)));
        assertThat(first.persist(), is(true));
        assertThat(second.persist(), is(true));
    }

    @Test
    void recomputesAgeForAMemoizedDate() {
        String value = "h3=\":443\"; ma=300";
        String date = "Sun, 23 Aug 2026 00:00:00 GMT";
        Instant laterReceivedAt = RECEIVED_AT.plusSeconds(90);
        Instant expectedExpiration = Instant.parse("2026-08-23T00:05:00Z");

        AltSvcHeader.Alternative first = AltSvcHeader.create(timedResponseHeaders(value, "0", date), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        AltSvcHeader.Alternative second = AltSvcHeader.create(timedResponseHeaders(value, "0", date), laterReceivedAt)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(first.expirationTime(), is(expectedExpiration));
        assertThat(second.expirationTime(), is(expectedExpiration));
    }

    @Test
    void memoizedInvalidDateStillHonorsAge() {
        String value = "h3=\":443\"; ma=120";

        AltSvcHeader.Alternative first = AltSvcHeader.create(
                        timedResponseHeaders(value, "10", "not a date"), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        AltSvcHeader.Alternative second = AltSvcHeader.create(
                        timedResponseHeaders(value, "85", "not a date"), RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(first.expirationTime(), is(RECEIVED_AT.plusSeconds(110)));
        assertThat(second.expirationTime(), is(RECEIVED_AT.plusSeconds(35)));
    }

    @Test
    void memoizesExactMalformedAndClearGrammar() {
        ClientResponseHeaders malformed = responseHeaders("h3=:443");
        assertThat(AltSvcHeader.create(malformed, RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(malformed, RECEIVED_AT).isEmpty(), is(true));

        ClientResponseHeaders clearHeaders = responseHeaders(" \tclear\t ");
        AltSvcHeader firstClear = AltSvcHeader.create(clearHeaders, RECEIVED_AT).orElseThrow();
        AltSvcHeader secondClear = AltSvcHeader.create(clearHeaders, RECEIVED_AT).orElseThrow();
        assertThat(firstClear.clear(), is(true));
        assertThat(secondClear.clear(), is(true));
    }

    @Test
    void changingOneFieldLineReplacesTheGrammarMemo() {
        AltSvcHeader initial = AltSvcHeader.create(
                responseHeaders("h3=\":443\"", "h2=\":8443\""),
                RECEIVED_AT).orElseThrow();
        AltSvcHeader changed = AltSvcHeader.create(
                responseHeaders("h3=\":443\"", "h2=\"other.example:9443\""),
                RECEIVED_AT).orElseThrow();
        AltSvcHeader restored = AltSvcHeader.create(
                responseHeaders("h3=\":443\"", "h2=\":8443\""),
                RECEIVED_AT).orElseThrow();

        assertThat(initial.alternatives().get(1).port(), is(8443));
        assertThat(changed.alternatives().get(1).host().orElseThrow(), is("other.example"));
        assertThat(changed.alternatives().get(1).port(), is(9443));
        assertThat(restored.alternatives().get(1).port(), is(8443));
    }

    @Test
    void preservesFieldLineBoundariesAcrossMemoReplacement() {
        ClientResponseHeaders malformed = responseHeaders("h3=\"unterminated", "clear");
        assertThat(AltSvcHeader.create(malformed, RECEIVED_AT).isEmpty(), is(true));

        AltSvcHeader clear = AltSvcHeader.create(responseHeaders("clear"), RECEIVED_AT).orElseThrow();
        assertThat(clear.clear(), is(true));

        assertThat(AltSvcHeader.create(malformed, RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void decodesAndValidatesQuotedParameterValues() {
        AltSvcHeader.Alternative alternative = AltSvcHeader.create(
                        responseHeaders("h3=\":443\"; ma=\"6\\0\"; persist=\"1\"; note=\"a\\\"b\""),
                        RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();

        assertThat(alternative.expirationTime(), is(RECEIVED_AT.plusSeconds(60)));
        assertThat(alternative.persist(), is(true));

        String rawNull = "h3=\":443\"; note=\"a" + '\0' + "b\"";
        assertThat(AltSvcHeader.create(responseHeaders(rawNull), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\"; note=\"a\rb\""), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        String escapedNull = "h3=\":443\"; note=\"a\\" + '\0' + "b\"";
        assertThat(AltSvcHeader.create(responseHeaders(escapedNull), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void rejectsMalformedMembersAndDuplicateMaxAgeAsOneUpdate() {
        AltSvcHeader.Alternative withOws = AltSvcHeader.create(
                        responseHeaders(" \th3=\":443\" \t;\t ma=60\t "),
                        RECEIVED_AT)
                .orElseThrow()
                .alternatives()
                .getFirst();
        assertThat(withOws.protocolId(), is("h3"));
        assertThat(withOws.expirationTime(), is(RECEIVED_AT.plusSeconds(60)));

        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2=:8443"), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2 =\":8443\""), RECEIVED_AT).isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2= \"other.example:8443\""), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2=\":8443\"; ma =60"), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\"\u000B, h2=\":8443\""), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2=\"bad/path:8443\""), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2=\"[not-an-ip]:8443\""), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\", h2=\"bücher.example:8443\""), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\"; ma=60; MA=30"), RECEIVED_AT).isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\"; broken"), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void keepsEmptyParameterElementsMalformed() {
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\";; ma=60"), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\"; ma=60;; persist=1"), RECEIVED_AT)
                           .isEmpty(),
                   is(true));
        assertThat(AltSvcHeader.create(responseHeaders("h3=\":443\"; ma=60;"), RECEIVED_AT).isEmpty(), is(true));
    }

    @Test
    void boundsIgnoredEmptyElements() {
        String accepted = ",".repeat(32) + "h3=\":443\"";
        assertThat(AltSvcHeader.create(responseHeaders(accepted), RECEIVED_AT).isPresent(), is(true));

        String flood = ",".repeat(33) + "h3=\":443\"";
        assertThat(AltSvcHeader.create(responseHeaders(flood), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders(flood + ",clear"), RECEIVED_AT).isEmpty(), is(true));
        assertThat(AltSvcHeader.create(responseHeaders(",".repeat(16) + "h3=\":443\"",
                                                     ",".repeat(17) + "h2=\":8443\""),
                                      RECEIVED_AT).isEmpty(),
                   is(true));
    }

    @Test
    void boundsTheAtomicAlternativeSetAcrossFieldLines() {
        List<String> alternatives = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            alternatives.add("h" + index + "=\":" + (8000 + index) + "\"");
        }
        assertThat(AltSvcHeader.create(responseHeaders(String.join(",", alternatives)), RECEIVED_AT)
                           .orElseThrow()
                           .alternatives(),
                   hasSize(32));

        alternatives.add("h32=\":8032\"");
        assertThat(AltSvcHeader.create(responseHeaders(String.join(",", alternatives)), RECEIVED_AT).isEmpty(),
                   is(true));
        alternatives.add("clear");
        assertThat(AltSvcHeader.create(responseHeaders(String.join(",", alternatives)), RECEIVED_AT).isEmpty(),
                   is(true));
    }

    private static ClientResponseHeaders responseHeaders(String... altSvcValues) {
        return ClientResponseHeaders.create(altSvcHeaders(altSvcValues));
    }

    private static ClientResponseHeaders timedResponseHeaders(String altSvcValue, String age, String date) {
        WritableHeaders<?> headers = altSvcHeaders(altSvcValue);
        headers.add(HeaderValues.create(HeaderNames.AGE, age));
        headers.add(HeaderValues.create(HeaderNames.DATE, date));
        return ClientResponseHeaders.create(headers);
    }

    private static WritableHeaders<?> altSvcHeaders(String... values) {
        WritableHeaders<?> headers = WritableHeaders.create();
        for (String value : values) {
            headers.add(HeaderValues.create(HeaderNames.ALT_SVC, value));
        }
        return headers;
    }
}
