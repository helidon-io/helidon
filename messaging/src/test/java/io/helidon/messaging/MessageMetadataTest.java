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

package io.helidon.messaging;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageMetadataTest {
    @Test
    void storesSingleValuesUnderExactNames() {
        MessageMetadata metadata = MessageMetadata.builder()
                .set("failure", "first")
                .set("Failure", "case-sensitive")
                .set("failure", HeaderValue.integer(42))
                .build();

        assertThat(metadata.size(), is(2));
        assertThat(metadata.isEmpty(), is(false));
        assertThat(metadata.contains("failure"), is(true));
        assertThat(metadata.contains("Failure"), is(true));
        assertThat(metadata.contains("missing"), is(false));
        assertThat(metadata.value("failure").orElseThrow(), is(HeaderValue.integer(42)));
        assertThat(metadata.text("Failure").orElseThrow(), is("case-sensitive"));
        assertThat(metadata.value("missing").isEmpty(), is(true));
        assertThat(metadata.text("missing").isEmpty(), is(true));
        assertThrows(IllegalStateException.class, () -> metadata.text("failure"));
    }

    @Test
    void snapshotsBuilderAndExposesImmutableValues() {
        MessageMetadata.Builder builder = MessageMetadata.builder().set("a", "original");
        MessageMetadata metadata = builder.build();
        builder.set("a", "changed").set("later", "value");

        assertThat(metadata.values(), is(Map.of("a", HeaderValue.text("original"))));
        assertThrows(UnsupportedOperationException.class,
                     () -> metadata.values().put("mutable", HeaderValue.text("false")));
    }

    @Test
    void mergesReplacesRemovesAndClearsValues() {
        MessageMetadata added = MessageMetadata.builder()
                .set("replace", "new")
                .set("added", HeaderValue.booleanValue(true))
                .build();
        MessageMetadata.Builder builder = MessageMetadata.builder()
                .set("retained", "value")
                .set("replace", "old")
                .addAll(added);

        assertThat(builder.build().values(),
                   is(Map.of("retained", HeaderValue.text("value"),
                             "replace", HeaderValue.text("new"),
                             "added", HeaderValue.booleanValue(true))));

        builder.remove("replace");
        assertThat(builder.build().contains("replace"), is(false));
        assertThat(builder.clear().build(), sameInstance(MessageMetadata.empty()));
    }

    @Test
    void hasValueEqualityAndValueIndependentToString() {
        MessageMetadata first = MessageMetadata.builder().set("secret", "first").build();
        MessageMetadata equal = MessageMetadata.builder().set("secret", "first").build();
        MessageMetadata different = MessageMetadata.builder().set("secret", "second").build();

        assertThat(first, is(equal));
        assertThat(first.hashCode(), is(equal.hashCode()));
        assertThat(first, not(different));
        assertThat(first.toString(), is("MessageMetadata[size=1]"));
        assertThat(different.toString(), is(first.toString()));
        assertThat(MessageMetadata.empty().toString(), is("MessageMetadata[size=0]"));
    }

    @Test
    void validatesArgumentsWithoutChangingBuilder() {
        MessageMetadata.Builder builder = MessageMetadata.builder().set("a", "original");

        assertThrows(NullPointerException.class, () -> builder.set(null, "value"));
        assertThrows(NullPointerException.class, () -> builder.set("a", (String) null));
        assertThrows(NullPointerException.class, () -> builder.set("a", (HeaderValue) null));
        assertThrows(NullPointerException.class, () -> builder.addAll(null));
        assertThrows(NullPointerException.class, () -> builder.remove(null));
        assertThrows(NullPointerException.class, () -> MessageMetadata.empty().contains(null));
        assertThrows(NullPointerException.class, () -> MessageMetadata.empty().value(null));
        assertThrows(NullPointerException.class, () -> MessageMetadata.empty().text(null));

        assertThat(builder.build().values(), is(Map.of("a", HeaderValue.text("original"))));
    }
}
