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

package io.helidon.common.uri;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class UriQueryCopyTest {
    @Test
    void testDecoratorCopyKeepsMixedSerializedAliasesAndUnrelatedRawEdits() {
        UriQuery delegate = UriQuery.create("a%20b=first&a%20b=second&z=keep&token=old");
        UriQuery source = new QueryDecorator(delegate, "z=keep&a+b=first&a%20b=second&flag&token=old");
        source.getAllRaw("token").set(0, "replacement");
        var copy = UriQueryWriteable.create().set("z", "existing").from(source);

        assertThat(copy.getAllRaw("a+b"), is(List.of("first")));
        assertThat(copy.getAllRaw("a%20b"), is(List.of("second")));
        assertThat(copy.getAllRaw("z"), is(List.of("existing", "keep")));
        assertThat(copy.getAllRaw("flag"), is(List.of("")));
        assertThat(copy.all("a b"), is(List.of("first", "second")));
        assertThat(copy.getAllRaw("token"), is(List.of("replacement")));
        assertThat(copy.all("token"), is(List.of("old")));

        delegate.getAllRaw("a%20b").clear();
        delegate.all("a b").clear();
        assertThat(copy.getAllRaw("a+b"), is(List.of("first")));
        assertThat(copy.getAllRaw("a%20b"), is(List.of("second")));
        assertThat(copy.all("a b"), is(List.of("first", "second")));
        copy.getAllRaw("z").clear();
        assertThat(delegate.getAllRaw("z"), is(List.of("keep")));
    }

    @ParameterizedTest
    @MethodSource("editedRawValues")
    void testDecoratorCopyKeepsUnrelatedEditedRawValuesWhenNamesDiffer(List<String> rawValues) {
        UriQuery delegate = UriQuery.create("aa=1&token=old");
        UriQuery source = new QueryDecorator(delegate, "a%61=1&token=old");
        source.getAllRaw("token").clear();
        source.getAllRaw("token").addAll(rawValues);
        var copy = UriQueryWriteable.create().set("aa", "existing").from(source);

        assertThat(copy.getAllRaw("a%61"), is(List.of("1")));
        assertThat(copy.getAllRaw("aa"), is(List.of("existing")));
        assertThat(copy.all("aa"), is(List.of("existing", "1")));
        assertThat(copy.contains("token"), is(true));
        assertThat(copy.getAllRaw("token"), is(rawValues));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(copy.rawValue(), not(containsString("token=old")));

        source.getAllRaw("token").add("source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("token"), is(rawValues));
        assertThat(copy.all("token"), is(List.of("old")));
        copy.getAllRaw("token").add("copy-only");
        copy.all("token").set(0, "decoded-copy-only");
        assertThat(source.getAllRaw("token").getLast(), is("source-only"));
        assertThat(source.all("token"), is(List.of("decoded-source-only")));

        var appended = UriQueryWriteable.create().set("token", "existing").from(source);
        assertThat(appended.getAllRaw("token").getFirst(), is("existing"));
        assertThat(appended.getAllRaw("token").subList(1, appended.getAllRaw("token").size()),
                   is(source.getAllRaw("token")));
        assertThat(appended.all("token"), is(List.of("existing", "decoded-source-only")));
    }

    @Test
    void testForwardingDecoratorCopyKeepsClearedRawMembership() {
        var delegate = UriQueryWriteable.create().set("token", "old");
        UriQuery source = new QueryDecorator(delegate, delegate::rawValue);
        source.getAllRaw("token").clear();

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(source.rawValue(), is(""));
        assertThat(copy.rawValue(), is(""));
        assertThat(copy.names(), is(Set.of("token")));
        assertThat(copy.contains("token"), is(true));
        copy.setIfAbsent("token", "replacement");
        assertThat(copy.getAllRaw("token"), is(List.of()));
        assertThat(copy.all("token"), is(List.of("old")));

        source.getAllRaw("token").add("source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("token"), is(List.of()));
        assertThat(copy.all("token"), is(List.of("old")));
        copy.getAllRaw("token").add("copy-only");
        copy.all("token").clear();
        assertThat(source.getAllRaw("token"), is(List.of("source-only")));
        assertThat(source.all("token"), is(List.of("decoded-source-only")));
    }

    @Test
    void testForwardingDecoratorCopyKeepsClearedAliasAlongsideSurvivingAlias() {
        var delegate = UriQueryWriteable.create();
        delegate.fromQueryString("%61=first&a=second");
        UriQuery source = new QueryDecorator(delegate, delegate::rawValue);
        source.getAllRaw("a").clear();

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.rawValue(), is("%61=first"));
        assertThat(copy.getAllRaw("%61"), is(List.of("first")));
        assertThat(copy.contains("a"), is(true));
        copy.setIfAbsent("a", "replacement");
        assertThat(copy.getAllRaw("a"), is(List.of()));
        assertThat(copy.all("a"), is(List.of("first", "second")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"safe", ""})
    void testForwardingDecoratorCopyReplacementRemovesSurvivingRawAlias(String replacement) {
        var delegate = UriQueryWriteable.create();
        delegate.fromQueryString("%78=attacker&x=discard");
        delegate.getAllRaw("x").clear();
        delegate.all("x").clear();
        UriQuery source = new QueryDecorator(delegate, delegate::rawValue);

        var copy = UriQueryWriteable.create().from(source);
        copy.set("x", replacement);

        assertThat(copy.rawValue(), is("x=" + replacement));
        assertThat(UriQuery.create(copy.rawValue()).all("x"), is(List.of(replacement)));
        assertThat(source.rawValue(), is("%78=attacker"));
        assertThat(source.getAllRaw("x"), is(List.of()));
        assertThat(source.all("x"), is(List.of()));

        source.getAllRaw("%78").set(0, "source-only");
        source.all("x").add("decoded-source-only");
        assertThat(copy.getAllRaw("x"), is(List.of(replacement)));
        assertThat(copy.all("x"), is(List.of(replacement)));
    }

    @Test
    void testDecoratorCopyDoesNotRestoreClearedRawValues() {
        UriQuery source = new QueryDecorator(UriQuery.create("token=old&keep=yes"));
        source.getAllRaw("token").clear();

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.contains("token"), is(true));
        assertThat(copy.getAllRaw("token"), is(List.of()));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(copy.rawValue(), is("keep=yes"));

        source.getAllRaw("token").add("source-only");
        copy.getAllRaw("token").add("copy-only");
        assertThat(source.getAllRaw("token"), is(List.of("source-only")));
        assertThat(copy.getAllRaw("token"), is(List.of("copy-only")));
    }

    @Test
    void testDecoratorCopyUsesReplacementRawValues() {
        UriQuery source = new QueryDecorator(UriQuery.create("token=old&keep=yes"));
        source.getAllRaw("token").set(0, "replacement");
        var copy = UriQueryWriteable.create().set("keep", "existing").from(source);

        assertThat(copy.getAllRaw("token"), is(List.of("replacement")));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(copy.getAllRaw("keep"), is(List.of("existing", "yes")));

        source.getAllRaw("token").set(0, "source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("token"), is(List.of("replacement")));
        assertThat(copy.all("token"), is(List.of("old")));
    }

    @Test
    void testDecoratorCopyKeepsEditedAliasesAndEmptyRawEntries() {
        UriQuery source = new QueryDecorator(UriQuery.create("%61=first&a=second&%61=third&=empty&flag"));
        source.getAllRaw("%61").set(0, "replacement");
        source.getAllRaw("a").clear();
        source.getAllRaw("").clear();
        source.all("a").set(0, "decoded-first");

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.getAllRaw("%61"), is(List.of("replacement", "third")));
        assertThat(copy.getAllRaw("a"), is(List.of()));
        assertThat(copy.getAllRaw(""), is(List.of()));
        assertThat(copy.getAllRaw("flag"), is(List.of()));
        assertThat(copy.contains("a"), is(true));
        assertThat(copy.contains(""), is(true));
        assertThat(copy.all("a"), is(List.of("decoded-first", "second", "third")));
        assertThat(copy.rawValue(), is("%61=replacement&%61=third"));
    }

    @Test
    void testCopyUsesRawValuesEditedBeforeCopy() {
        UriQuery source = UriQuery.create("a=old");
        source.getAllRaw("a").set(0, "new");

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.getAllRaw("a"), is(List.of("new")));
        assertThat(copy.all("a"), is(List.of("old")));
        assertThat(copy.rawValue(), is("a=new"));

        source.getAllRaw("a").set(0, "source-only");
        source.all("a").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("a"), is(List.of("new")));
        assertThat(copy.all("a"), is(List.of("old")));
    }

    @Test
    void testCopyDoesNotRestoreRawValuesClearedBeforeCopy() {
        UriQuery source = UriQuery.create("a=old&b=keep");
        source.getAllRaw("a").clear();

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.getAllRaw("a"), is(List.of()));
        assertThat(copy.all("a"), is(List.of("old")));
        assertThat(copy.rawValue(), is("b=keep"));

        source.getAllRaw("a").add("source-only");
        assertThat(copy.getAllRaw("a"), is(List.of()));
        copy.getAllRaw("a").add("copy-only");
        assertThat(source.getAllRaw("a"), is(List.of("source-only")));
    }

    @Test
    void testCopyKeepsEditedRawAliasesSeparateFromDecodedValues() {
        UriQuery source = UriQuery.create("%61=first&a=second&%61=third");
        source.getAllRaw("%61").set(0, "raw-first");
        source.getAllRaw("a").clear();
        source.all("a").set(0, "decoded-first");

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.getAllRaw("%61"), is(List.of("raw-first", "third")));
        assertThat(copy.getAllRaw("a"), is(List.of()));
        assertThat(copy.all("a"), is(List.of("decoded-first", "second", "third")));
        assertThat(copy.rawValue(), is("%61=raw-first&%61=third"));

        source.getAllRaw("%61").clear();
        source.all("a").clear();
        assertThat(copy.getAllRaw("%61"), is(List.of("raw-first", "third")));
        assertThat(copy.all("a"), is(List.of("decoded-first", "second", "third")));
        copy.getAllRaw("%61").add("copy-only");
        copy.all("a").add("decoded-copy-only");
        assertThat(source.getAllRaw("%61"), is(List.of()));
        assertThat(source.all("a"), is(List.of()));
    }

    @Test
    void testEncodedNamesRetainRawAliasesAndDecodedOrder() {
        UriQuery source = UriQuery.create("%61=first&a=second&%61=third&k%26%3D=v%26%3D");

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.getAllRaw("%61"), is(List.of("first", "third")));
        assertThat(copy.getAllRaw("a"), is(List.of("second")));
        assertThat(copy.all("a"), is(List.of("first", "second", "third")));
        assertThat(copy.getAllRaw("k%26%3D"), is(List.of("v%26%3D")));
        assertThat(copy.all("k&="), is(List.of("v&=")));
    }

    @Test
    void testPlusNamesAndValuesRetainRawEncoding() {
        UriQuery source = UriQuery.create("a+b=x+y&a%2Bb=x%2By&escaped=%2f%2F");

        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.getAllRaw("a+b"), is(List.of("x+y")));
        assertThat(copy.getAllRaw("a%2Bb"), is(List.of("x%2By")));
        assertThat(copy.getAllRaw("escaped"), is(List.of("%2f%2F")));
        assertThat(copy.all("a b"), is(List.of("x y")));
        assertThat(copy.all("a+b"), is(List.of("x+y")));
    }

    @Test
    void testCopyAppendsAndKeepsIndependentDecodedLists() {
        UriQuery source = UriQuery.create("%61=first&a=second");
        source.all("a").set(0, "decoded-source");
        var copy = UriQueryWriteable.create();
        copy.add("existing", "value");
        copy.add("a", "original");

        copy.from(source);

        assertThat(copy.get("existing"), is("value"));
        assertThat(copy.all("a"), is(List.of("original", "decoded-source", "second")));
        assertThat(copy.getAllRaw("%61"), is(List.of("first")));
        assertThat(copy.getAllRaw("a"), is(List.of("original", "second")));

        source.all("a").clear();
        source.getAllRaw("%61").clear();
        assertThat(copy.all("a"), is(List.of("original", "decoded-source", "second")));
        assertThat(copy.getAllRaw("%61"), is(List.of("first")));
        copy.all("a").add("copy-only");
        copy.getAllRaw("%61").add("copy-only");
        assertThat(source.all("a"), is(List.of()));
        assertThat(source.getAllRaw("%61"), is(List.of()));
    }

    @Test
    void testFlagsAndEmptyTokensKeepIndependentDecodedVectors() {
        String raw = "&flag&&empty=&flag&";
        UriQuery source = UriQuery.create(raw);
        UriQueryWriteable copy = UriQueryWriteable.create().from(source);

        assertThat(copy.names(), is(Set.of("", "flag", "empty")));
        assertThat(copy.getAllRaw(""), is(List.of()));
        assertThat(copy.getAllRaw("flag"), is(List.of()));
        assertThat(copy.getAllRaw("empty"), is(List.of("")));
        assertThat(copy.all(""), is(List.of()));
        assertThat(copy.all("flag"), is(List.of()));
        assertThat(copy.all("empty"), is(List.of("")));
        assertThat(copy.rawValue(), is("empty="));
    }

    @Test
    void testEmptySourceDoesNotAddEmptyParameter() {
        var copy = UriQueryWriteable.create();
        copy.from(UriQuery.empty());

        assertThat(copy.isEmpty(), is(true));
        assertThat(copy.names(), is(Set.of()));
        assertThat(copy.rawValue(), is(""));

        copy.add("existing", "value");
        copy.from(UriQuery.empty());
        assertThat(copy.rawValue(), is("existing=value"));
        assertThat(copy.names(), is(Set.of("existing")));
    }

    @Test
    void testGenericCopyUpdatesInitializedAliasIndex() {
        var copy = UriQueryWriteable.create();
        copy.fromQueryString("%61=old");
        copy.set("a", "existing");

        copy.from(UriQuery.create("%62=first&b=second"));
        copy.set("b", "replacement");

        assertThat(copy.all("b"), is(List.of("replacement")));
        assertThat(copy.getAllRaw("b"), is(List.of("replacement")));
        assertThat(copy.rawValue(), is("a=existing&b=replacement"));
    }

    private static Stream<List<String>> editedRawValues() {
        return Stream.of(List.of(), List.of("replacement"));
    }

    private record QueryDecorator(UriQuery delegate, Supplier<String> rawQuery) implements UriQuery {
        private QueryDecorator(UriQuery delegate) {
            this(delegate, delegate.rawValue());
        }

        private QueryDecorator(UriQuery delegate, String rawQuery) {
            this(delegate, () -> rawQuery);
        }

        @Override
        public String rawValue() {
            return rawQuery.get();
        }

        @Override
        public String value() {
            return delegate.value();
        }

        @Override
        public String getRaw(String name) {
            return delegate.getRaw(name);
        }

        @Override
        public List<String> getAllRaw(String name) {
            return delegate.getAllRaw(name);
        }

        @Override
        public List<String> all(String name) {
            return delegate.all(name);
        }

        @Override
        public List<Value<String>> allValues(String name) {
            return delegate.allValues(name);
        }

        @Override
        public String get(String name) {
            return delegate.get(name);
        }

        @Override
        public OptionalValue<String> first(String name) {
            return delegate.first(name);
        }

        @Override
        public boolean contains(String name) {
            return delegate.contains(name);
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public Set<String> names() {
            return delegate.names();
        }

        @Override
        public String component() {
            return delegate.component();
        }
    }
}
