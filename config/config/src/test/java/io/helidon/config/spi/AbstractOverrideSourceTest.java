/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.config.spi;

import java.time.Instant;
import java.util.Optional;

import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigException;
import io.helidon.config.PollingStrategies;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AbstractOverrideSource}.
 */
public class AbstractOverrideSourceTest {

    @Test
    public void testBuilderDefault() {
        AbstractOverrideSource.Builder builder = new AbstractOverrideSource.Builder(Void.class) {
            @Override
            public OverrideSource build() {
                return Optional::empty;
            }
        };

        assertThat(builder.isMandatory(), is(true));
        assertThat(builder.getChangesExecutor(), is(AbstractSource.Builder.DEFAULT_CHANGES_EXECUTOR));
        assertThat(builder.getChangesMaxBuffer(), is(Flow.defaultBufferSize()));
        assertThat(builder.getPollingStrategy(), is(PollingStrategies.nop()));
    }

    @Test
    public void testFormatDescriptionOptionalNoParams() {
        TestingOverrideSource testingOverrideSource = TestingOverrideSource.builder()
                .optional()
                .pollingStrategy((PollingStrategy) () -> null)
                .build();

        assertThat(testingOverrideSource.description(), is("TestingOverride[]?*"));
    }

    @Test
    public void testFormatDescriptionOptionalNoParamsNoPolling() {
        UidOverrideSource overrideSource = UidOverrideSource.builder()
                .uid("")
                .optional()
                .build();

        assertThat(overrideSource.description(), is("UidOverride[]?"));
    }

    @Test
    public void testFormatDescriptionOptionalWithParams() {
        UidOverrideSource overrideSource = UidOverrideSource.builder()
                .uid("PA,RAMS")
                .pollingStrategy((PollingStrategy) () -> null)
                .optional()
                .build();

        assertThat(overrideSource.description(), is("UidOverride[PA,RAMS]?*"));
    }

    @Test
    public void testFormatDescriptionMandatoryNoParams() {
        UidOverrideSource overrideSource = UidOverrideSource.builder()
                .uid("")
                .pollingStrategy((PollingStrategy) () -> null)
                .build();

        assertThat(overrideSource.description(), is("UidOverride[]*"));
    }

    @Test
    public void testFormatDescriptionMandatoryWithParams() {
        UidOverrideSource overrideSource = UidOverrideSource.builder()
                .uid("PA,RAMS")
                .pollingStrategy((PollingStrategy) () -> null)
                .build();

        assertThat(overrideSource.description(), is("UidOverride[PA,RAMS]*"));
    }

    private static class TestingOverrideSource extends AbstractOverrideSource {

        /**
         * Initializes config source from builder.
         *
         * @param builder builder to be initialized from
         */
        protected TestingOverrideSource(Builder builder) {
            super(builder);
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        protected Optional dataStamp() {
            return Optional.empty();
        }

        @Override
        protected Data<OverrideData, Instant> loadData() throws ConfigException {
            return new Data<>(null, Optional.of(Instant.MIN));
        }

        public static class Builder extends AbstractOverrideSource.Builder<TestingOverrideSource.Builder, Void> {
            protected Builder() {
                super(Void.class);
            }

            @Override
            public TestingOverrideSource build() {
                return new TestingOverrideSource(this);
            }
        }
    }

    private static class UidOverrideSource extends AbstractOverrideSource {

        private final String uid;

        public UidOverrideSource(UidBuilder builder, String uid) {
            super(builder);
            this.uid = uid;
        }

        public static UidBuilder builder() {
            return new UidBuilder();
        }

        public static UidBuilder builderWithUid(String uid) {
            return builder().uid(uid);
        }

        public static UidOverrideSource withUid(String uid) {
            return builderWithUid(uid).build();
        }

        @Override
        protected String uid() {
            return uid;
        }

        @Override
        protected Optional dataStamp() {
            return Optional.empty();
        }

        @Override
        protected Data loadData() throws ConfigException {
            return null;
        }

        public static final class UidBuilder extends AbstractOverrideSource.Builder<UidBuilder, String> {

            private String uid;

            private UidBuilder() {
                super(String.class);
            }

            public UidBuilder uid(String uid) {
                this.uid = uid;
                return this;
            }

            @Override
            public UidOverrideSource build() {
                return new UidOverrideSource(this, uid);
            }
        }

    }
}
