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

package io.helidon.messaging.external;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import io.helidon.messaging.Message;
import io.helidon.messaging.MessageConfig;
import io.helidon.messaging.MessageHeader;
import io.helidon.messaging.MessageHeaderValue;
import io.helidon.messaging.MessageHeaders;
import io.helidon.messaging.MessageMetadata;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageConfigPublicApiTest {
    @Test
    void buildsGenericPayloadWithEmptyMetadata() {
        List<String> payload = List.of("one", "two");
        MessageConfig.Builder<List<String>> builder = Message.builder(payload);
        MessageConfig<List<String>> config = builder.buildPrototype();

        Message<List<String>> message = config.build();

        assertThat(config.entity(), sameInstance(payload));
        assertThat(message.entity(), sameInstance(payload));
        assertThat(config.headers(), is(List.of()));
        assertThat(config.localMetadata(), is(Map.of()));
        assertThat(message.headers(), sameInstance(MessageHeaders.empty()));
        assertThat(message.localMetadata(), sameInstance(MessageMetadata.empty()));
    }

    @Test
    void prototypeAndCopiedBuildersKeepIndependentSnapshots() {
        MessageConfig.Builder<String> builder = Message.builder("original")
                .header("trace", "original-header")
                .localMetadata("diagnostic", "original-metadata");
        MessageConfig<String> original = builder.buildPrototype();
        MessageConfig.Builder<String> fromPrototype = MessageConfig.<String>builder().from(original);
        MessageConfig.Builder<String> fromBuilder = MessageConfig.<String>builder().from(builder);

        builder.entity("updated")
                .header("trace", "updated-header")
                .localMetadata("diagnostic", "updated-metadata");
        fromPrototype.entity("copied")
                .header("trace", "copied-header")
                .localMetadata("diagnostic", "copied-metadata");

        Message<String> originalMessage = original.build();
        Message<String> copiedMessage = fromPrototype.build();
        Message<String> builderCopy = fromBuilder.build();
        Message<String> updatedMessage = builder.build();

        assertThat(originalMessage.entity(), is("original"));
        assertThat(originalMessage.header("trace").orElseThrow(), is("original-header"));
        assertThat(originalMessage.localMetadata().text("diagnostic").orElseThrow(), is("original-metadata"));
        assertThat(builderCopy.entity(), is("original"));
        assertThat(builderCopy.headers().entries(), is(original.headers()));
        assertThat(builderCopy.localMetadata().values(), is(original.localMetadata()));
        assertThat(copiedMessage.entity(), is("copied"));
        assertThat(copiedMessage.header("trace").orElseThrow(), is("copied-header"));
        assertThat(copiedMessage.localMetadata().text("diagnostic").orElseThrow(), is("copied-metadata"));
        assertThat(updatedMessage.entity(), is("updated"));
        assertThat(updatedMessage.header("trace").orElseThrow(), is("updated-header"));
        assertThat(updatedMessage.localMetadata().text("diagnostic").orElseThrow(), is("updated-metadata"));
    }

    @Test
    void mutableBuilderCollectionsDoNotChangeBuiltSnapshots() {
        MessageHeader header = MessageHeader.create("trace", "retained");
        MessageHeaderValue metadata = MessageHeaderValue.TextValue.create("retained");
        MessageConfig.Builder<String> builder = Message.builder("payload")
                .addHeader(header)
                .localMetadata("diagnostic", metadata);
        MessageConfig<String> config = builder.buildPrototype();
        Message<String> message = builder.build();

        builder.headers().clear();
        builder.localMetadata().clear();

        assertThat(config.headers(), is(List.of(header)));
        assertThat(config.localMetadata(), is(Map.of("diagnostic", metadata)));
        assertThat(message.headers().entries(), is(List.of(header)));
        assertThat(message.localMetadata().values(), is(Map.of("diagnostic", metadata)));
        assertThat(builder.build().headers().isEmpty(), is(true));
        assertThat(builder.build().localMetadata().isEmpty(), is(true));
        assertThrows(UnsupportedOperationException.class, () -> config.headers().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.localMetadata().clear());
    }

    @Test
    void repeatedAppendsRetainHeaderOrderAndTypedMetadataOverwrites() {
        List<MessageHeader> expected = IntStream.range(0, 256)
                .mapToObj(value -> MessageHeader.create("item", value))
                .toList();
        MessageConfig.Builder<String> builder = Message.builder("payload");
        for (MessageHeader header : expected) {
            builder.addHeader(header);
        }
        builder.localMetadata("attempt", "initial")
                .localMetadata("attempt", MessageHeaderValue.IntegerValue.create(2));

        Message<String> message = builder.build();

        assertThat(message.headers().entries(), is(expected));
        assertThat(message.headerValue("item").orElseThrow(), is(MessageHeaderValue.IntegerValue.create(255)));
        assertThat(message.localMetadata().values(), is(Map.of("attempt", MessageHeaderValue.IntegerValue.create(2))));
    }

    @Test
    void copiesMergeExplicitCollectionsIntoExistingTarget() {
        MessageConfig.Builder<String> source = Message.builder("source")
                .addHeader("trace", "source")
                .localMetadata("shared", "source")
                .localMetadata("source-only", "added");
        MessageConfig<String> sourcePrototype = source.buildPrototype();
        MessageConfig<String> target = Message.builder("target")
                .addHeader("trace", "target")
                .localMetadata("shared", "target")
                .localMetadata("target-only", "retained")
                .buildPrototype();

        Message<String> fromPrototype = MessageConfig.builder(target).from(sourcePrototype).build();
        Message<String> fromBuilder = MessageConfig.builder(target).from(source).build();

        for (Message<String> message : List.of(fromPrototype, fromBuilder)) {
            assertThat(message.entity(), is("source"));
            assertThat(message.headers().entries(), is(List.of(MessageHeader.create("trace", "target"),
                                                               MessageHeader.create("trace", "source"))));
            assertThat(message.localMetadata().values(),
                       is(Map.of("shared", MessageHeaderValue.TextValue.create("source"),
                                 "source-only", MessageHeaderValue.TextValue.create("added"),
                                 "target-only", MessageHeaderValue.TextValue.create("retained"))));
        }
        assertThat(sourcePrototype.headers(), is(List.of(MessageHeader.create("trace", "source"))));
        assertThat(target.headers(), is(List.of(MessageHeader.create("trace", "target"))));
    }

    @Test
    void copyingUntouchedBuilderDoesNotEraseExplicitTargetValues() {
        Message<String> message = Message.builder("target")
                .addHeader("trace", "retained")
                .localMetadata("diagnostic", "retained")
                .from(Message.<String>builder())
                .build();

        assertThat(message.entity(), is("target"));
        assertThat(message.header("trace").orElseThrow(), is("retained"));
        assertThat(message.localMetadata().text("diagnostic").orElseThrow(), is("retained"));
    }

    @Test
    void rejectsNullEntriesIntroducedThroughMutableBuilderCollections() {
        MessageConfig.Builder<String> nullHeader = Message.builder("payload");
        nullHeader.headers().add(null);
        assertThrows(NullPointerException.class, nullHeader::buildPrototype);

        MessageConfig.Builder<String> nullName = Message.builder("payload");
        nullName.localMetadata().put(null, MessageHeaderValue.TextValue.create("value"));
        assertThrows(NullPointerException.class, nullName::build);

        MessageConfig.Builder<String> nullValue = Message.builder("payload");
        nullValue.localMetadata().put("diagnostic", null);
        assertThrows(NullPointerException.class, nullValue::buildPrototype);
    }

    @Test
    void callbackAndSupplierSettersReplaceWholeCollections() {
        MessageConfig.Builder<String> builder = Message.builder("payload")
                .header("discarded", "value")
                .localMetadata("discarded", "value")
                .headers(headers -> headers.add("callback", "header"))
                .localMetadata(metadata -> metadata.set("callback", "metadata"));
        Message<String> callbackMessage = builder.build();

        MessageHeaders suppliedHeaders = MessageHeaders.builder().add("supplier", "header").build();
        MessageMetadata suppliedMetadata = MessageMetadata.builder().set("supplier", "metadata").build();
        builder.headers(() -> suppliedHeaders).localMetadata(() -> suppliedMetadata);
        Message<String> suppliedMessage = builder.build();

        assertThat(callbackMessage.headers().entries(), is(List.of(MessageHeader.create("callback", "header"))));
        assertThat(callbackMessage.localMetadata().values(),
                   is(Map.of("callback", MessageHeaderValue.TextValue.create("metadata"))));
        assertThat(suppliedMessage.headers(), is(suppliedHeaders));
        assertThat(suppliedMessage.localMetadata(), is(suppliedMetadata));

        assertThrows(NullPointerException.class, () -> builder.headers(() -> null));
        assertThrows(NullPointerException.class, () -> builder.localMetadata(() -> null));
        assertThat(builder.build().headers(), is(suppliedHeaders));
        assertThat(builder.build().localMetadata(), is(suppliedMetadata));
    }

    @Test
    void validatesRequiredPayloadBeforeBuildingPrototype() {
        MessageConfig.Builder<String> builder = Message.builder();

        assertThrows(NullPointerException.class, builder::buildPrototype);
        builder.entity("retained");
        assertThrows(NullPointerException.class, () -> builder.entity(null));

        assertThat(builder.buildPrototype().entity(), is("retained"));
        assertThat(builder.build().entity(), is("retained"));
    }

    @Test
    void prototypeAndBuilderDiagnosticsDoNotExposeMessageContents() {
        MessageConfig.Builder<String> builder = Message.builder("secret-payload")
                .header("secret-header-name", "secret-header-value")
                .localMetadata("secret-metadata-name", "secret-metadata-value");
        MessageConfig<String> config = builder.buildPrototype();

        for (String diagnostic : List.of(builder.toString(), config.toString())) {
            assertThat(diagnostic, not(containsString("secret-payload")));
            assertThat(diagnostic, not(containsString("secret-header-name")));
            assertThat(diagnostic, not(containsString("secret-header-value")));
            assertThat(diagnostic, not(containsString("secret-metadata-name")));
            assertThat(diagnostic, not(containsString("secret-metadata-value")));
        }
    }
}
