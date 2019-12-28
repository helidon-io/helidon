/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link DefaultAuditProvider}.
 */
class DefaultAuditProviderTest {
    @Test
    void testMessageFormatting() {
        DefaultAuditProvider provider = DefaultAuditProvider.create(Config.empty());
        AuditEvent event = createEvent("Unit tests: first: \"%s\"; second: \"%s\"",
                                       List.of(AuditEvent.AuditParam.plain("first", "data"),
                                              AuditEvent.AuditParam.sensitive("second", "secret")));

        String message = provider.formatMessage(event);

        assertThat(message, is("Unit tests: first: \"data\"; second: \"(sensitive)\""));
    }

    @Test
    void testMessageFormattingTooManyParams() {
        DefaultAuditProvider provider = DefaultAuditProvider.create(Config.empty());
        AuditEvent event = createEvent("Unit tests: first: \"%s\"; second: \"%s\"",
                                       List.of(AuditEvent.AuditParam.plain("first", "data"),
                                              AuditEvent.AuditParam.sensitive("second", "secret"),
                                              AuditEvent.AuditParam.plain("third", "thirdData")));

        String message = provider.formatMessage(event);

        assertThat(message, is("Unit tests: first: \"data\"; second: \"(sensitive)\""));
    }

    @Test
    void testMessageFormattingNotEnoughParams() {
        DefaultAuditProvider provider = DefaultAuditProvider.create(Config.empty());
        AuditEvent event = createEvent("Unit tests: first: \"%s\"; second: \"%s\"",
                                       List.of(AuditEvent.AuditParam.plain("first", "data")));

        String message = provider.formatMessage(event);

        assertThat(message, startsWith("Formatting failed for format: Unit tests: first: \"%s\"; second: \"%s\", parameters: "));
    }

    private AuditEvent createEvent(String messageFormat, List<AuditEvent.AuditParam> params) {
        return new AuditEvent() {
            @Override
            public String eventType() {
                return "unit-test";
            }

            @Override
            public Optional<Throwable> throwable() {
                return Optional.empty();
            }

            @Override
            public List<AuditParam> params() {
                return params;
            }

            @Override
            public String messageFormat() {
                return messageFormat;
            }

            @Override
            public AuditSeverity severity() {
                return null;
            }
        };
    }
}