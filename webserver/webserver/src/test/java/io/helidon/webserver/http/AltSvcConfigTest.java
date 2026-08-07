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

package io.helidon.webserver.http;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AltSvcConfigTest {
    @Test
    void shouldRenderDefaultHeaderValue() {
        AltSvc altSvc = AltSvc.builder().build();

        assertThat(altSvc.headerValue(8443), is("h3=\":8443\""));
    }

    @Test
    void shouldDisambiguateLiteralPercentFromEscapedProtocolBytes() {
        assertThat(AltSvc.builder().protocol("h3").build().headerValue(8443), is("h3=\":8443\""));
        assertThat(AltSvc.builder().protocol("h%33").build().headerValue(8443), is("h%2533=\":8443\""));
    }

    @Test
    void shouldRenderOpaqueNonAsciiProtocolByte() {
        AltSvc altSvc = AltSvc.builder()
                .protocol("\u00e9")
                .build();

        assertThat(altSvc.headerValue(8443), is("%E9=\":8443\""));
    }

    @Test
    void shouldAcceptWhitespaceOnlyProtocolFromConfig() {
        AltSvc altSvc = AltSvc.create(Config.just(ConfigSources.create(Map.of("protocol", " \t"))));

        assertThat(altSvc.protocol(), is(" \t"));
        assertThat(altSvc.headerValue(8443), is("%20%09=\":8443\""));
    }

    @Test
    void shouldAcceptProtocolLengthBoundaries() {
        String maximumProtocol = "a".repeat(255);

        assertThat(AltSvc.builder().protocol("a").build().headerValue(8443), is("a=\":8443\""));
        assertThat(AltSvc.builder().protocol(maximumProtocol).build().headerValue(8443),
                   is(maximumProtocol + "=\":8443\""));
    }

    @Test
    void shouldRenderOptionalParameters() {
        AltSvc altSvc = AltSvc.builder()
                .port(9443)
                .maxAge(Duration.ofMillis(1500))
                .persist(true)
                .build();

        assertThat(altSvc.headerValue(8443), is("h3=\":9443\"; ma=1; persist=1"));
    }

    @Test
    void shouldRejectInvalidListenerPort() {
        AltSvc altSvc = AltSvc.builder().build();

        IllegalArgumentException belowRange = assertThrows(IllegalArgumentException.class,
                                                           () -> altSvc.headerValue(-1));
        IllegalArgumentException aboveRange = assertThrows(IllegalArgumentException.class,
                                                           () -> altSvc.header(65_536));

        assertThat(belowRange.getMessage(), is("Alt-Svc port must be between 1 and 65535."));
        assertThat(aboveRange.getMessage(), is("Alt-Svc port must be between 1 and 65535."));
    }

    @Test
    void shouldUseExplicitPortWhenListenerHasNoPort() {
        AltSvc altSvc = AltSvc.builder()
                .port(9443)
                .build();

        assertThat(altSvc.headerValue(-1), is("h3=\":9443\""));
    }

    @Test
    void shouldCacheHeaderForOneListener() {
        AltSvc altSvc = AltSvc.builder().build();

        assertSame(altSvc.header(8443), altSvc.header(8443));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> altSvc.header(9443));
        assertThat(exception.getMessage(),
                   is("Alt-Svc instance is already bound to listener port 8443, cannot use listener port 9443."));
    }

    @Test
    void shouldClearOptionalParametersExplicitly() {
        AltSvc altSvc = AltSvc.builder()
                .port(9443)
                .clearPort()
                .maxAge(Duration.ofSeconds(30))
                .clearMaxAge()
                .build();

        assertThat(altSvc.port().isEmpty(), is(true));
        assertThat(altSvc.maxAge().isEmpty(), is(true));
        assertThat(altSvc.headerValue(8443), is("h3=\":8443\""));
    }

    @Test
    void shouldRejectNullApiArguments() {
        AltSvc.Builder builder = AltSvc.builder();

        assertThrows(NullPointerException.class, () -> AltSvc.create((Config) null));
        assertThrows(NullPointerException.class,
                     () -> AltSvc.create((Consumer<AltSvc.Builder>) null));
        assertThrows(NullPointerException.class, () -> builder.config(null));
        assertThrows(NullPointerException.class, () -> builder.update(null));
        assertThrows(NullPointerException.class, () -> builder.protocol(null));
        assertThrows(NullPointerException.class, () -> builder.maxAge((Duration) null));
    }

    @Test
    void shouldRetainNonNullProtocolWhenDisabled() {
        AltSvc altSvc = AltSvc.builder().enabled(false).build();

        assertThat(altSvc.protocol(), is("h3"));
    }

    @Test
    void shouldRejectNegativeMaxAgeWhenEnabled() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> AltSvc.builder()
                                                                  .maxAge(Duration.ofSeconds(-1))
                                                                  .build());

        assertThat(exception.getMessage(), is("Alt-Svc maxAge cannot be negative."));
    }

    @Test
    void shouldRejectEmptyProtocolWhenEnabled() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> AltSvc.builder()
                                                                  .protocol("")
                                                                  .build());

        assertThat(exception.getMessage(), is("Alt-Svc protocol cannot be empty when enabled."));
    }

    @Test
    void shouldRejectProtocolCharacterOutsideByteRange() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> AltSvc.builder()
                                                                  .protocol("\u0100")
                                                                  .build());

        assertThat(exception.getMessage(),
                   is("Alt-Svc protocol contains a character outside the byte range when enabled."));
    }

    @Test
    void shouldRejectProtocolLongerThan255Bytes() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                                          () -> AltSvc.builder()
                                                                  .protocol("a".repeat(256))
                                                                  .build());

        assertThat(exception.getMessage(),
                   is("Alt-Svc protocol exceeds the 255-byte ALPN limit when enabled."));
    }

    @Test
    void shouldSkipSemanticValidationWhenDisabled() {
        assertDoesNotThrow(() -> AltSvc.builder()
                .enabled(false)
                .protocol("")
                .port(-1)
                .maxAge(Duration.ofSeconds(-1))
                .build());
    }
}
