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
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class ClientUriQueryTest {
    private static final String RAW_QUERY = "z=1&flag&&a=first&a=second&a=third&%61=fourth&q=a+b&escaped=%2f%2F&";

    @Test
    void testSnapshotDecoratorImportIncludesParametersAddedAfterCapture() {
        var source = UriQueryWriteable.create().set("a", "1");
        UriQuery decorated = snapshotDecorator(source);
        doReturn(source.rawValue()).when(decorated).rawValue();
        source.add("token", "new");

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(decorated.rawValue(), is("a=1"));
        assertThat(client.query().getAllRaw("token"), is(List.of("new")));
        assertThat(client.query().all("token"), is(List.of("new")));
        String target = client.pathWithQueryAndFragment();
        assertThat(UriQuery.create(URI.create(target).getRawQuery()).all("token"), is(List.of("new")));

        source.getAllRaw("token").set(0, "raw-source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(client.pathWithQueryAndFragment(), is(target));
        assertThat(client.query().getAllRaw("token"), is(List.of("new")));
        assertThat(client.query().all("token"), is(List.of("new")));
        client.query().getAllRaw("token").set(0, "raw-client-only");
        client.query().all("token").set(0, "decoded-client-only");
        assertThat(source.getAllRaw("token"), is(List.of("raw-source-only")));
        assertThat(source.all("token"), is(List.of("decoded-source-only")));
    }

    @Test
    void testSnapshotDecoratorImportIncludesAddedRawAliasesAndRepeatedValues() {
        var source = UriQueryWriteable.create().set("a", "1");
        UriQuery decorated = snapshotDecorator(source);
        doReturn(source.rawValue()).when(decorated).rawValue();
        source.fromQueryString("%74oken=first&token=second&%74oken=third");

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().getAllRaw("%74oken"), is(List.of("first", "third")));
        assertThat(client.query().getAllRaw("token"), is(List.of("second")));
        assertThat(client.query().all("token"), is(List.of("first", "second", "third")));
        UriQuery outbound = UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery());
        assertThat(outbound.getAllRaw("%74oken"), is(List.of("first", "third")));
        assertThat(outbound.getAllRaw("token"), is(List.of("second")));
        assertThat(outbound.getAllRaw("a"), is(List.of("1")));
    }

    @Test
    void testSnapshotDecoratorImportIncludesAddedEmptyRawName() {
        var source = UriQueryWriteable.create().set("a", "1");
        UriQuery decorated = snapshotDecorator(source);
        doReturn(source.rawValue()).when(decorated).rawValue();
        source.add("", "first").add("", "second");

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().getAllRaw(""), is(List.of("first", "second")));
        assertThat(client.query().all(""), is(List.of("first", "second")));
        UriQuery outbound = UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery());
        assertThat(outbound.getAllRaw(""), is(List.of("first", "second")));
        assertThat(outbound.getAllRaw("a"), is(List.of("1")));
    }

    @ParameterizedTest
    @MethodSource("editedDecoratorRawValues")
    void testSnapshotDecoratorImportKeepsEditedAddedRawValues(List<String> rawValues) {
        var source = UriQueryWriteable.create().set("a", "1");
        UriQuery decorated = snapshotDecorator(source);
        doReturn(source.rawValue()).when(decorated).rawValue();
        source.add("token", "new");
        source.getAllRaw("token").clear();
        source.getAllRaw("token").addAll(rawValues);

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().contains("token"), is(true));
        assertThat(client.query().getAllRaw("token"), is(rawValues));
        assertThat(client.query().all("token"), is(List.of("new")));
        UriQuery outbound = UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery());
        assertThat(outbound.toMap().getOrDefault("token", List.of()), is(rawValues));
        assertThat(outbound.getAllRaw("a"), is(List.of("1")));
        if (rawValues.isEmpty()) {
            assertThat(client.pathWithQueryAndFragment(), is("/?a=1"));
        }
    }

    @Test
    void testSnapshotDecoratorImportPreservesFullyMatchingRawQuery() {
        String raw = "z=last&escaped=%2f%2F&%61=first&a=second&flag&&";
        UriQuery source = UriQuery.create(raw);
        UriQuery decorated = snapshotDecorator(source);

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.pathWithQueryAndFragment(), is("/?" + raw));
        assertThat(client.query().getAllRaw("escaped"), is(List.of("%2f%2F")));
        assertThat(client.query().getAllRaw("%61"), is(List.of("first")));
        assertThat(client.query().getAllRaw("a"), is(List.of("second")));
        assertThat(client.query().all("a"), is(List.of("first", "second")));
        assertThat(client.query().names(), is(Set.of("z", "escaped", "a", "flag", "")));
    }

    @Test
    void testOpaqueAliasDecoratorImportUsesLegacyRawName() {
        UriQuery source = UriQuery.create("aa=old");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        doReturn("a%61=old").when(decorated).rawValue();

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.pathWithQueryAndFragment(), is("/?aa=old"));
        assertThat(client.query().getAllRaw("aa"), is(List.of("old")));
        assertThat(client.query().all("aa"), is(List.of("old")));
    }

    @ParameterizedTest
    @MethodSource("editedDecoratorRawValues")
    void testOpaqueAliasDecoratorAppendsCurrentRawAndDecodedLists(List<String> rawValues) {
        UriQuery source = UriQuery.create("aa=old");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        doReturn("a%61=old").when(decorated).rawValue();
        source.getAllRaw("aa").clear();
        source.getAllRaw("aa").addAll(rawValues);
        var client = ClientUri.create(URI.create("https://upstream.example/%70ath?aa=existing"));

        client.writeableQuery().from(decorated);

        var expectedRaw = new ArrayList<>(List.of("existing"));
        expectedRaw.addAll(rawValues);
        assertThat(client.query().getAllRaw("aa"), is(expectedRaw));
        assertThat(client.query().all("aa"), is(List.of("existing", "old")));
        assertThat(UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery()).all("aa"), is(expectedRaw));

        source.getAllRaw("aa").add("source-only");
        source.all("aa").set(0, "decoded-source-only");
        assertThat(client.query().getAllRaw("aa"), is(expectedRaw));
        assertThat(client.query().all("aa"), is(List.of("existing", "old")));
        client.query().getAllRaw("aa").add("client-only");
        client.query().all("aa").set(0, "decoded-client-only");
        assertThat(source.getAllRaw("aa").subList(0, rawValues.size()), is(rawValues));
        assertThat(source.getAllRaw("aa").getLast(), is("source-only"));
        assertThat(source.all("aa"), is(List.of("decoded-source-only")));
    }

    @Test
    void testOpaqueAliasDecoratorFallbackDiscardsPartiallyCollectedRawValues() {
        UriQuery source = UriQuery.create("keep=source&aa=old");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        doReturn("keep=source&a%61=old").when(decorated).rawValue();
        source.getAllRaw("aa").set(0, "replacement");
        var client = ClientUri.create(URI.create("https://upstream.example/?keep=existing"));

        client.writeableQuery().from(decorated);

        assertThat(client.query().getAllRaw("keep"), is(List.of("existing", "source")));
        assertThat(client.query().all("keep"), is(List.of("existing", "source")));
        assertThat(client.query().getAllRaw("aa"), is(List.of("replacement")));
        assertThat(client.query().all("aa"), is(List.of("old")));
        assertThat(UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery()).all("aa"),
                   is(List.of("replacement")));
    }

    @Test
    void testFailedLegacyRawLookupDoesNotChangeTarget() {
        UriQuery source = UriQuery.create("keep=source&%61=old");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        doReturn("keep=source&%61=old&a%61=old").when(decorated).rawValue();
        var client = ClientUri.create(URI.create("https://upstream.example/%70ath?keep=%2f+"));

        assertThrows(NoSuchElementException.class, () -> client.writeableQuery().from(decorated));

        assertThat(client.pathWithQueryAndFragment(), is("/%70ath?keep=%2f+"));
        assertThat(client.query().names(), is(Set.of("keep")));
        assertThat(client.query().all("keep"), is(List.of("/ ")));
    }

    @Test
    void testImportedQuerySurvivesReads() {
        var query = importedQuery();

        assertThat(query.get("q"), is("a b"));
        assertThat(query.getRaw("q"), is("a%20b"));
        assertThat(query.all("a"), is(List.of("first", "second", "third", "fourth")));
        assertThat(query.getAllRaw("a"), is(List.of("first", "second", "third")));
        assertThat(query.names().contains("flag"), is(true));
        assertThat(query.contains("z"), is(true));
        assertThat(query.isEmpty(), is(false));
        assertThat(query.size(), is(6));
        assertThat(query.first("z").get(), is("1"));
        assertThat(query.allValues("a").size(), is(4));
        assertThat(query.toMap().get("q"), is(List.of("a b")));
        query.value();
        query.hashCode();
        query.toString();
        query.equals(UriQuery.create(RAW_QUERY));

        assertThat(query.rawValue(), is(RAW_QUERY));
    }

    @Test
    void testNamesMembershipUsesDecodedNamesWithoutChangingImportedQuery() {
        String raw = "z=last&n%25ame=percent&n%2Bame=plus&n+ame=space&flag&&%61=first&a=second&";
        var query = new ClientUriQuery();
        query.appendRawQuery(raw);
        var names = query.names();

        assertThat(names.contains("n%ame"), is(true));
        assertThat(names.contains("n+ame"), is(true));
        assertThat(names.contains("n ame"), is(true));
        assertThat(names.contains("a"), is(true));
        assertThat(names.contains("flag"), is(true));
        assertThat(names.contains(""), is(true));
        assertThat(names.contains("n%25ame"), is(false));
        assertThat(names.contains("n%2Bame"), is(false));
        assertThat(names.contains("%61"), is(false));
        assertThat(names.contains("missing"), is(false));
        assertThat(names.contains(null), is(false));
        assertThat(names.contains(1), is(false));
        assertThat(query.rawValue(), is(raw));
    }

    @Test
    void testAppendImportsAndEmptyImport() {
        var query = new ClientUriQuery();
        query.appendRawQuery("");
        assertThat(query.isEmpty(), is(true));

        query.appendRawQuery("b=first&flag&");
        query.appendRawQuery("b=second&x=%2f+");
        query.appendRawQuery("");

        assertThat(query.rawValue(), is("b=first&flag&&b=second&x=%2f+"));
        assertThat(query.all("b"), is(List.of("first", "second")));
        assertThat(query.get("x"), is("/ "));
    }

    @Test
    void testGenericQueryStringKeepsExistingPlusBehavior() {
        var query = new ClientUriQuery();
        query.fromQueryString("q=a+b");

        assertThat(query.get("q"), is("a b"));
        assertThat(query.rawValue(), is("q=a+b"));
    }

    @Test
    void testSetIfAbsentExistingKeepsImportedQuery() {
        var query = importedQuery();
        query.setIfAbsent("z", "ignored");

        assertThat(query.get("z"), is("1"));
        assertThat(query.rawValue(), is(RAW_QUERY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void testMutationsRetainExistingParameterBehavior(String description, Consumer<UriQueryWriteable> mutation) {
        var query = importedQuery();
        var expected = UriQueryWriteable.create();
        expected.fromQueryString(RAW_QUERY.replace("+", "%20"));

        mutation.accept(expected);
        mutation.accept(query);

        assertThat(description + " raw query", query.rawValue(), is(expected.rawValue()));
        assertThat(description + " decoded parameters", query.toMap(), is(expected.toMap()));
    }

    @Test
    void testCopyPreservesSnapshotAndIndependentParameterLists() {
        var source = importedQuery();
        var copy = new ClientUriQuery(source);

        assertThat(copy.rawValue(), is(RAW_QUERY));
        source.getAllRaw("a").set(0, "raw-source");
        source.all("a").set(0, "decoded-source");
        assertThat(copy.rawValue(), is(RAW_QUERY));
        assertThat(copy.getRaw("a"), is("first"));
        assertThat(copy.get("a"), is("first"));

        copy.set("a", "copy");
        assertThat(source.get("a"), is("decoded-source"));
    }

    @Test
    void testCopyOfMutatedQueryKeepsIndependentRawAndDecodedState() {
        var source = importedQuery();
        source.getAllRaw("a").set(0, "raw-source");
        source.all("a").set(0, "decoded-source");
        var copy = new ClientUriQuery(source);

        assertThat(copy.rawValue(), is(source.rawValue()));
        assertThat(copy.getRaw("a"), is("raw-source"));
        assertThat(copy.get("a"), is("decoded-source"));
        assertThat(copy.getAllRaw("%61"), is(List.of("fourth")));
        copy.clear();
        assertThat(source.get("a"), is("decoded-source"));
    }

    @Test
    void testImportDecoratedQueryKeepsClearedRawValues() {
        UriQuery source = UriQuery.create("token=old&keep=yes");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        decorated.getAllRaw("token").clear();

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().getAllRaw("token"), is(List.of()));
        assertThat(client.query().all("token"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), is("/?keep=yes"));

        source.getAllRaw("token").add("source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(client.query().getAllRaw("token"), is(List.of()));
        assertThat(client.query().all("token"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), is("/?keep=yes"));
        client.query().getAllRaw("token").add("client-only");
        assertThat(source.getAllRaw("token"), is(List.of("source-only")));
    }

    @Test
    void testImportDecoratedQueryKeepsReplacedRawValues() {
        UriQuery source = UriQuery.create("token=old&keep=yes");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        decorated.getAllRaw("token").set(0, "replacement");

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().getAllRaw("token"), is(List.of("replacement")));
        assertThat(client.query().all("token"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), containsString("token=replacement"));
        assertThat(client.pathWithQueryAndFragment(), containsString("keep=yes"));
        assertThat(client.pathWithQueryAndFragment(), not(containsString("token=old")));

        source.getAllRaw("token").set(0, "source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(client.query().getAllRaw("token"), is(List.of("replacement")));
        assertThat(client.query().all("token"), is(List.of("old")));
        client.query().getAllRaw("token").set(0, "client-only");
        client.query().all("token").set(0, "decoded-client-only");
        assertThat(source.getAllRaw("token"), is(List.of("source-only")));
        assertThat(source.all("token"), is(List.of("decoded-source-only")));
    }

    @Test
    void testImportDecoratedQueryKeepsEditedEncodedAliasesAndRepeatedValues() {
        UriQuery source = UriQuery.create("%61=first&a=second&%61=third&keep=yes");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        decorated.getAllRaw("%61").set(0, "raw-first");
        decorated.getAllRaw("a").clear();
        decorated.all("a").set(0, "decoded-first");

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().getAllRaw("%61"), is(List.of("raw-first", "third")));
        assertThat(client.query().getAllRaw("a"), is(List.of()));
        assertThat(client.query().all("a"), is(List.of("decoded-first", "second", "third")));
        assertThat(client.pathWithQueryAndFragment(), containsString("%61=raw-first&%61=third"));
        assertThat(client.pathWithQueryAndFragment(), not(containsString("a=second")));

        source.getAllRaw("%61").clear();
        source.all("a").clear();
        assertThat(client.query().getAllRaw("%61"), is(List.of("raw-first", "third")));
        assertThat(client.query().all("a"), is(List.of("decoded-first", "second", "third")));
    }

    @ParameterizedTest
    @MethodSource("editedDecoratorRawValues")
    void testSnapshotDecoratedQueryKeepsUnrelatedEditedRawValuesWhenNamesDiffer(List<String> rawValues) {
        UriQuery source = UriQuery.create("aa=1&token=old");
        UriQuery decorated = snapshotDecorator(source);
        doReturn("a%61=1&token=old").when(decorated).rawValue();
        source.getAllRaw("token").clear();
        source.getAllRaw("token").addAll(rawValues);

        var copy = UriQueryWriteable.create().from(decorated);
        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(copy.getAllRaw("aa"), is(List.of("1")));
        assertThat(copy.getAllRaw("token"), is(rawValues));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(copy.rawValue(), not(containsString("token=old")));
        assertThat(client.query().getAllRaw("aa"), is(List.of("1")));
        assertThat(client.query().getAllRaw("token"), is(rawValues));
        assertThat(client.query().all("token"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), not(containsString("token=old")));
        if (!rawValues.isEmpty()) {
            assertThat(client.pathWithQueryAndFragment(), containsString("token=replacement"));
        }

        source.getAllRaw("token").add("source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("token"), is(rawValues));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(client.query().getAllRaw("token"), is(rawValues));
        assertThat(client.query().all("token"), is(List.of("old")));
        copy.getAllRaw("token").add("copy-only");
        copy.all("token").clear();
        client.query().getAllRaw("token").add("client-only");
        client.query().all("token").clear();
        assertThat(source.getAllRaw("token").getLast(), is("source-only"));
        assertThat(source.all("token"), is(List.of("decoded-source-only")));
    }

    @ParameterizedTest
    @MethodSource("editedDecoratorRawValues")
    void testSnapshotDecoratedQueryKeepsEditedRawValuesWhenNamesDiffer(List<String> rawValues) {
        UriQuery source = UriQuery.create("aa=old&keep=yes");
        UriQuery decorated = snapshotDecorator(source);
        doReturn("a%61=old&keep=yes").when(decorated).rawValue();
        source.getAllRaw("aa").clear();
        source.getAllRaw("aa").addAll(rawValues);

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());
        String target = client.pathWithQueryAndFragment();
        UriQuery serialized = UriQuery.create(URI.create(target).getRawQuery());

        assertThat(target, not(containsString("old")));
        assertThat(serialized.toMap().getOrDefault("aa", List.of()), is(rawValues));
        assertThat(serialized.getAllRaw("keep"), is(List.of("yes")));
        assertThat(client.query().all("aa"), is(List.of("old")));
        assertThat(source.all("aa"), is(List.of("old")));

        source.getAllRaw("aa").add("source-only");
        source.all("aa").set(0, "decoded-source-only");
        assertThat(client.pathWithQueryAndFragment(), is(target));
        assertThat(client.query().all("aa"), is(List.of("old")));
        client.query().all("aa").set(0, "decoded-client-only");
        assertThat(source.all("aa"), is(List.of("decoded-source-only")));

        client.writeableQuery().set("aa", "client-only");
        UriQuery updated = UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery());
        assertThat(updated.all("aa"), is(List.of("client-only")));
        assertThat(source.getAllRaw("aa").getLast(), is("source-only"));
        assertThat(source.all("aa"), is(List.of("decoded-source-only")));
    }

    @Test
    void testForwardingDecoratorKeepsClearedRawMembershipOnCopyAndImport() {
        var source = UriQueryWriteable.create().set("token", "old");
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));
        source.getAllRaw("token").clear();

        var copy = UriQueryWriteable.create().from(decorated);
        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(decorated.rawValue(), is(""));
        assertThat(copy.rawValue(), is(""));
        assertThat(copy.contains("token"), is(true));
        assertThat(client.pathWithQueryAndFragment(), is("/"));
        assertThat(client.query().contains("token"), is(true));
        assertThat(client.query().names().contains("token"), is(true));
        copy.setIfAbsent("token", "replacement");
        client.writeableQuery().setIfAbsent("token", "replacement");
        assertThat(copy.getAllRaw("token"), is(List.of()));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(client.query().getAllRaw("token"), is(List.of()));
        assertThat(client.query().all("token"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), is("/"));

        source.getAllRaw("token").add("source-only");
        source.all("token").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("token"), is(List.of()));
        assertThat(copy.all("token"), is(List.of("old")));
        assertThat(client.query().getAllRaw("token"), is(List.of()));
        assertThat(client.query().all("token"), is(List.of("old")));
        copy.getAllRaw("token").add("copy-only");
        copy.all("token").clear();
        client.query().getAllRaw("token").add("client-only");
        client.query().all("token").clear();
        assertThat(source.getAllRaw("token"), is(List.of("source-only")));
        assertThat(source.all("token"), is(List.of("decoded-source-only")));
    }

    @ParameterizedTest
    @MethodSource("snapshotAliasQueries")
    void testSnapshotDecoratedQueryKeepsRepeatedAliasesAndLiteralPercentNames(String backing,
                                                                           String serialized,
                                                                           String firstName,
                                                                           String secondName) {
        UriQuery source = UriQuery.create(backing);
        UriQuery decorated = snapshotDecorator(source);
        doReturn(serialized).when(decorated).rawValue();

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(client.query().getAllRaw(firstName), is(List.of("first")));
        assertThat(client.query().getAllRaw(secondName), is(List.of("second")));
        assertThat(client.query().toMap(), is(source.toMap()));
        UriQuery outbound = UriQuery.create(URI.create(client.pathWithQueryAndFragment()).getRawQuery());
        assertThat(outbound.getAllRaw(firstName), is(List.of("first")));
        assertThat(outbound.getAllRaw(secondName), is(List.of("second")));
        source.getAllRaw(firstName).set(0, "source-only");
        assertThat(client.query().getAllRaw(firstName), is(List.of("first")));
        client.query().getAllRaw(secondName).set(0, "client-only");
        assertThat(source.getAllRaw(secondName), is(List.of("second")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"safe", ""})
    void testForwardingDecoratorImportedReplacementRemovesSurvivingRawAlias(String replacement) {
        var source = UriQueryWriteable.create();
        source.fromQueryString("%78=attacker&x=discard");
        source.getAllRaw("x").clear();
        source.all("x").clear();
        UriQuery decorated = mock(UriQuery.class, delegatesTo(source));

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());
        client.writeableQuery().set("x", replacement);

        assertThat(client.pathWithQueryAndFragment(), is("/?x=" + replacement));
        assertThat(UriQuery.create(client.query().rawValue()).all("x"), is(List.of(replacement)));
        assertThat(source.rawValue(), is("%78=attacker"));
        assertThat(source.getAllRaw("x"), is(List.of()));
        assertThat(source.all("x"), is(List.of()));

        source.getAllRaw("%78").set(0, "source-only");
        source.all("x").add("decoded-source-only");
        assertThat(client.pathWithQueryAndFragment(), is("/?x=" + replacement));
        assertThat(client.query().all("x"), is(List.of(replacement)));
    }

    @ParameterizedTest
    @MethodSource("decoratedClientQueriesWithPlusNames")
    void testCopySnapshotDecoratedClientQueryKeepsCurrentPlusValues(String raw,
                                                               Map<String, List<String>> rawValues,
                                                               List<String> decodedValues) {
        var source = ClientUri.create(URI.create("https://source.example/?" + raw));
        UriQuery decorated = snapshotDecorator(source.query());

        UriQueryWriteable copy = UriQueryWriteable.create().from(decorated);

        for (var entry : rawValues.entrySet()) {
            assertThat(entry.getKey(), copy.getAllRaw(entry.getKey()), is(entry.getValue()));
        }
        assertThat(copy.all("a b"), is(decodedValues));
        UriQuery serialized = UriQuery.create(copy.rawValue());
        for (var entry : rawValues.entrySet()) {
            assertThat(entry.getKey(), serialized.getAllRaw(entry.getKey()), is(entry.getValue()));
        }
        assertThat(source.query().rawValue(), is(raw));

        source.query().getAllRaw("a%20b").set(0, "source-only");
        source.query().all("a b").set(0, "decoded-source-only");
        for (var entry : rawValues.entrySet()) {
            assertThat(entry.getKey(), copy.getAllRaw(entry.getKey()), is(entry.getValue()));
        }
        assertThat(copy.all("a b"), is(decodedValues));
        copy.getAllRaw("a%20b").set(0, "copy-only");
        copy.all("a b").set(0, "decoded-copy-only");
        assertThat(source.query().getAllRaw("a%20b").getFirst(), is("source-only"));
        assertThat(source.query().all("a b").getFirst(), is("decoded-source-only"));
    }

    @ParameterizedTest
    @MethodSource("decoratedClientQueriesWithPlusNames")
    void testImportSnapshotDecoratedClientQueryKeepsCurrentPlusValues(String raw,
                                                                 Map<String, List<String>> rawValues,
                                                                 List<String> decodedValues) {
        var source = ClientUri.create(URI.create("https://source.example/?" + raw));
        UriQuery decorated = snapshotDecorator(source.query());

        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());
        String normalizedRaw = "a%20b=" + String.join("&a%20b=", decodedValues);

        assertThat(client.pathWithQueryAndFragment(), is("/?" + normalizedRaw));
        for (var entry : rawValues.entrySet()) {
            assertThat(entry.getKey(), client.query().getAllRaw(entry.getKey()), is(entry.getValue()));
        }
        assertThat(client.query().all("a b"), is(decodedValues));

        source.query().getAllRaw("a%20b").set(0, "source-only");
        source.query().all("a b").set(0, "decoded-source-only");
        assertThat(client.pathWithQueryAndFragment(), is("/?" + normalizedRaw));
        for (var entry : rawValues.entrySet()) {
            assertThat(entry.getKey(), client.query().getAllRaw(entry.getKey()), is(entry.getValue()));
        }
        assertThat(client.query().all("a b"), is(decodedValues));
        client.query().getAllRaw("a%20b").set(0, "client-only");
        client.query().all("a b").set(0, "decoded-client-only");
        assertThat(client.pathWithQueryAndFragment(), containsString("a%20b=client-only"));
        assertThat(source.query().getAllRaw("a%20b").getFirst(), is("source-only"));
        assertThat(source.query().all("a b").getFirst(), is("decoded-source-only"));
    }

    @Test
    void testSnapshotDecoratedClientQueryCopiesClearedPlusValuesAfterSnapshotInvalidation() {
        var source = ClientUri.create(URI.create("https://source.example/?a+b=old&keep=yes"));
        source.query().getAllRaw("a%20b").clear();
        UriQuery decorated = snapshotDecorator(source.query());

        UriQueryWriteable copy = UriQueryWriteable.create().from(decorated);
        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(source.query().rawValue(), is("keep=yes"));
        assertThat(copy.rawValue(), is("keep=yes"));
        assertThat(copy.contains("a%20b"), is(true));
        assertThat(copy.all("a b"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), is("/?keep=yes"));
        assertThat(client.query().contains("a%20b"), is(true));
        assertThat(client.query().all("a b"), is(List.of("old")));

        copy.set("a b", "copy-only");
        client.writeableQuery().set("a b", "client-only");
        assertThat(source.query().getAllRaw("a%20b"), is(List.of()));
        assertThat(source.query().all("a b"), is(List.of("old")));
    }

    @Test
    void testSnapshotDecoratedClientQueryCopiesReplacedPlusValuesAfterSnapshotInvalidation() {
        var source = ClientUri.create(URI.create("https://source.example/?a+b=old&keep=yes"));
        source.query().getAllRaw("a%20b").set(0, "replacement");
        UriQuery decorated = snapshotDecorator(source.query());

        UriQueryWriteable copy = UriQueryWriteable.create().from(decorated);
        var client = ClientUri.create(UriInfo.builder().host("upstream.example").query(decorated).build());

        assertThat(source.query().rawValue(), containsString("a%20b=replacement"));
        assertThat(copy.getAllRaw("a%20b"), is(List.of("replacement")));
        assertThat(copy.all("a b"), is(List.of("old")));
        assertThat(client.query().getAllRaw("a%20b"), is(List.of("replacement")));
        assertThat(client.query().all("a b"), is(List.of("old")));
        assertThat(client.pathWithQueryAndFragment(), containsString("a%20b=replacement"));

        source.query().getAllRaw("a%20b").set(0, "source-only");
        source.query().all("a b").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("a%20b"), is(List.of("replacement")));
        assertThat(copy.all("a b"), is(List.of("old")));
        assertThat(client.query().getAllRaw("a%20b"), is(List.of("replacement")));
        assertThat(client.query().all("a b"), is(List.of("old")));
        copy.getAllRaw("a%20b").clear();
        client.query().getAllRaw("a%20b").clear();
        assertThat(source.query().getAllRaw("a%20b"), is(List.of("source-only")));
    }

    @Test
    void testCopyClientQueryThroughPublicMutableQueryApi() {
        var source = ClientUri.create(URI.create("https://example.test/?%61=first&a=second&a+b=x+y&flag&&"));

        UriQueryWriteable copy = UriQueryWriteable.create().from(source.query());

        assertThat(copy.getAllRaw("%61"), is(List.of("first")));
        assertThat(copy.getAllRaw("a"), is(List.of("second")));
        assertThat(copy.getAllRaw("a%20b"), is(source.query().getAllRaw("a%20b")));
        assertThat(copy.getAllRaw("a%20b"), is(List.of("x%20y")));
        assertThat(copy.all("a"), is(List.of("first", "second")));
        assertThat(copy.all("a b"), is(List.of("x y")));
        assertThat(copy.all("flag"), is(List.of("")));
        assertThat(copy.all(""), is(List.of("", "")));

        copy.set("a", "copy-only");
        assertThat(source.query().all("a"), is(List.of("first", "second")));
        assertThat(source.query().rawValue(), is("%61=first&a=second&a+b=x+y&flag&&"));
    }

    @Test
    void testExternalCopyKeepsClearedRawParameterMembership() {
        var source = ClientUri.create(URI.create("https://example.test/?a=old"));
        source.query().getAllRaw("a").clear();

        UriQueryWriteable copy = UriQueryWriteable.create().from(source.query());

        assertThat(copy.contains("a"), is(true));
        assertThat(copy.getAllRaw("a"), is(List.of()));
        assertThat(copy.all("a"), is(List.of("old")));
        assertThat(copy.rawValue(), is(""));
        copy.setIfAbsent("a", "replacement");
        assertThat(copy.getAllRaw("a"), is(List.of()));
        assertThat(copy.all("a"), is(List.of("old")));

        copy.getAllRaw("a").add("copy-only");
        copy.all("a").set(0, "decoded-copy-only");
        assertThat(source.query().getAllRaw("a"), is(List.of()));
        assertThat(source.query().all("a"), is(List.of("old")));
        source.query().getAllRaw("a").add("source-only");
        source.query().all("a").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("a"), is(List.of("copy-only")));
        assertThat(copy.all("a"), is(List.of("decoded-copy-only")));
    }

    @Test
    void testExternalCopyKeepsClearedEncodedAliasesAndEmptyRawNames() {
        var source = ClientUri.create(URI.create("https://example.test/?%61=first&a=second&=empty-one&=empty-two"));
        source.query().getAllRaw("%61").clear();
        source.query().getAllRaw("").clear();
        source.query().all("a").set(0, "decoded-first");
        source.query().all("").set(0, "decoded-empty");

        UriQueryWriteable copy = UriQueryWriteable.create().from(source.query());

        assertThat(copy.contains("%61"), is(true));
        assertThat(copy.contains(""), is(true));
        assertThat(copy.getAllRaw("%61"), is(List.of()));
        assertThat(copy.getAllRaw(""), is(List.of()));
        assertThat(copy.getAllRaw("a"), is(List.of("second")));
        assertThat(copy.all("a"), is(List.of("decoded-first", "second")));
        assertThat(copy.all(""), is(List.of("decoded-empty", "empty-two")));
        assertThat(copy.names().contains("a"), is(true));
        assertThat(copy.names().contains("%61"), is(false));
        assertThat(copy.names().contains(""), is(true));
        assertThat(copy.rawValue(), is("a=second"));
        copy.setIfAbsent("%61", "replacement");
        copy.setIfAbsent("", "replacement");
        assertThat(copy.getAllRaw("%61"), is(List.of()));
        assertThat(copy.getAllRaw(""), is(List.of()));

        source.query().getAllRaw("%61").add("source-only");
        source.query().all("").clear();
        assertThat(copy.getAllRaw("%61"), is(List.of()));
        assertThat(copy.all(""), is(List.of("decoded-empty", "empty-two")));
        copy.getAllRaw("").add("copy-only");
        copy.all("a").clear();
        assertThat(source.query().getAllRaw(""), is(List.of()));
        assertThat(source.query().all("a"), is(List.of("decoded-first", "second")));
    }

    @Test
    void testExternalCopyKeepsIndependentlyEditedRawAndDecodedLists() {
        var source = ClientUri.create(URI.create("https://example.test/?q=one&%71=two&=empty"));
        source.query().getAllRaw("q").set(0, "raw-one");
        source.query().getAllRaw("%71").set(0, "raw-two");
        source.query().all("q").set(0, "decoded-one");
        source.query().all("").clear();

        UriQueryWriteable copy = UriQueryWriteable.create().from(source.query());

        assertThat(copy.getAllRaw("q"), is(List.of("raw-one")));
        assertThat(copy.getAllRaw("%71"), is(List.of("raw-two")));
        assertThat(copy.getAllRaw(""), is(List.of("empty")));
        assertThat(copy.all("q"), is(List.of("decoded-one", "two")));
        assertThat(copy.all(""), is(List.of()));

        source.query().getAllRaw("%71").set(0, "source-only");
        source.query().all("q").set(0, "decoded-source-only");
        assertThat(copy.getAllRaw("%71"), is(List.of("raw-two")));
        assertThat(copy.all("q"), is(List.of("decoded-one", "two")));
        copy.getAllRaw("q").clear();
        copy.all("q").clear();
        assertThat(source.query().getAllRaw("q"), is(List.of("raw-one")));
        assertThat(source.query().all("q"), is(List.of("decoded-source-only", "two")));
    }

    @Test
    void testCopyCapabilityReturnsIndependentParameterState() {
        String raw = "b=2&a=1&b=3&flag&&";
        var source = ClientUri.create(URI.create("https://example.test/?" + raw));

        UriQuery copy = ((UriQuery.Copyable) source.query()).copy();
        copy.getAllRaw("b").set(0, "raw-copy-only");
        copy.all("b").set(0, "decoded-copy-only");
        copy.getAllRaw("").clear();
        copy.names().remove("a");

        assertThat(source.query().getAllRaw("b"), is(List.of("2", "3")));
        assertThat(source.query().all("b"), is(List.of("2", "3")));
        assertThat(source.query().getAllRaw(""), is(List.of("", "")));
        assertThat(source.query().names().contains("a"), is(true));
        assertThat(source.query().rawValue(), is(raw));

        source.query().getAllRaw("b").set(0, "raw-source-only");
        source.query().all("b").set(0, "decoded-source-only");
        source.query().getAllRaw("").add("source-only");
        source.query().names().remove("flag");

        assertThat(copy.getAllRaw("b"), is(List.of("raw-copy-only", "3")));
        assertThat(copy.all("b"), is(List.of("decoded-copy-only", "3")));
        assertThat(copy.getAllRaw(""), is(List.of()));
        assertThat(copy.names().contains("flag"), is(true));
    }

    @Test
    void testReplacementKeepsDetachedListsAndLiveNamesBehavior() {
        var query = importedQuery();
        var oldDecoded = query.all("a");
        var oldRaw = query.getAllRaw("a");
        var names = query.names();
        var replacement = new ClientUriQuery();
        replacement.appendRawQuery("a=new&flag&&");

        query.replace(replacement);
        oldDecoded.add("detached");
        oldRaw.clear();

        assertThat(query.rawValue(), is("a=new&flag&&"));
        assertThat(query.get("a"), is("new"));

        names.remove("a");
        assertThat(query.names().contains("a"), is(false));
        assertThat(query.rawValue(), is(replacementRawAfterNamesRemoval()));
        assertThat(replacement.rawValue(), is("a=new&flag&&"));
    }

    @Test
    void testClearAndReimportIgnoreDetachedListMutation() {
        var query = importedQuery();
        var oldValues = query.all("a").subList(0, 1);
        query.clear();
        query.appendRawQuery("a=new&flag&&");
        oldValues.set(0, "detached");

        assertThat(query.rawValue(), is("a=new&flag&&"));
        assertThat(query.get("a"), is("new"));
    }

    @Test
    void testDetachedAllValuesDoNotInvalidateQuery() {
        var query = importedQuery();
        query.allValues("a").clear();

        assertThat(query.rawValue(), is(RAW_QUERY));
        assertThat(query.all("a").size(), is(4));
    }

    @Test
    void testIteratorsDetectStructuralParameterChanges() {
        var query = importedQuery();
        var iterator = query.all("a").iterator();
        query.add("a", "additional");

        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    private static ClientUriQuery importedQuery() {
        var query = new ClientUriQuery();
        query.appendRawQuery(RAW_QUERY);
        return query;
    }

    private static UriQuery snapshotDecorator(UriQuery source) {
        var decorated = mock(UriQuery.Copyable.class, delegatesTo(source));
        doAnswer(_ -> source instanceof UriQuery.Copyable copyable
                ? copyable.copy()
                : UriQueryWriteable.create().from(source)).when(decorated).copy();
        return decorated;
    }

    private static String replacementRawAfterNamesRemoval() {
        var query = UriQueryWriteable.create();
        query.fromQueryString("a=new&flag&&");
        query.names().remove("a");
        return query.rawValue();
    }

    private static Stream<List<String>> editedDecoratorRawValues() {
        return Stream.of(List.of(), List.of("replacement"));
    }

    private static Stream<Arguments> snapshotAliasQueries() {
        return Stream.of(
                Arguments.of("aa=first&%61a=second", "a%61=first&a%61=second", "aa", "%61a"),
                Arguments.of("%2561=first&%61=second", "%25%36%31=first&%61=second", "%2561", "%61"));
    }

    private static Stream<Arguments> decoratedClientQueriesWithPlusNames() {
        return Stream.of(
                Arguments.of("a+b=x", Map.of("a%20b", List.of("x")), List.of("x")),
                Arguments.of("a+b=one&a+b=two", Map.of("a%20b", List.of("one", "two")), List.of("one", "two")),
                Arguments.of("a+b=one&a%20b=two&a+b=three",
                             Map.of("a%20b", List.of("one", "two", "three")),
                             List.of("one", "two", "three")));
    }

    private static Stream<Arguments> mutations() {
        return Stream.of(
                mutation("set", query -> query.set("a", "updated")),
                mutation("setIfAbsent", query -> query.setIfAbsent("new", "value")),
                mutation("add", query -> query.add("a", "additional")),
                mutation("remove", query -> query.remove("a")),
                mutation("remove with consumer", query -> query.remove("a", values -> values.clear())),
                mutation("clear", UriQueryWriteable::clear),
                mutation("from", query -> query.from(UriQuery.create("extra=one&extra=two"))),
                mutation("fromQueryString", query -> query.fromQueryString("extra=one&extra=two")),
                mutation("decoded set", query -> query.all("a").set(0, "updated")),
                mutation("decoded add", query -> query.all("a").add("additional")),
                mutation("decoded remove", query -> query.all("a").remove("second")),
                mutation("decoded clear", query -> query.all("a").clear()),
                mutation("decoded addAll", query -> query.all("a").addAll(List.of("one", "two"))),
                mutation("decoded indexed addAll", query -> query.all("a").addAll(1, List.of("one", "two"))),
                mutation("decoded removeAll", query -> query.all("a").removeAll(List.of("first", "third"))),
                mutation("decoded retainAll", query -> query.all("a").retainAll(List.of("second"))),
                mutation("decoded removeIf", query -> query.all("a").removeIf("second"::equals)),
                mutation("decoded replaceAll", query -> query.all("a").replaceAll(value -> "updated-" + value)),
                mutation("decoded sort", query -> query.all("a").sort(String::compareTo)),
                mutation("decoded subList", query -> query.all("a").subList(1, 3).clear()),
                mutation("decoded reversed", query -> query.all("a").reversed().removeFirst()),
                mutation("decoded iterator", query -> {
                    var iterator = query.all("a").iterator();
                    iterator.next();
                    iterator.remove();
                }),
                mutation("decoded listIterator set", query -> {
                    var iterator = query.all("a").listIterator();
                    iterator.next();
                    iterator.set("updated");
                }),
                mutation("decoded listIterator add", query -> query.all("a").listIterator(1).add("additional")),
                mutation("raw set", query -> query.getAllRaw("a").set(0, "updated")),
                mutation("raw subList", query -> query.getAllRaw("a").subList(0, 2).removeIf("first"::equals)),
                mutation("raw reversed", query -> query.getAllRaw("a").reversed().clear()),
                mutation("names remove", query -> query.names().remove("a")),
                mutation("names removeIf", query -> query.names().removeIf("a"::equals)),
                mutation("names clear", query -> query.names().clear()),
                mutation("names iterator", query -> {
                    var iterator = query.names().iterator();
                    iterator.next();
                    iterator.remove();
                }),
                mutation("toMap shared list", query -> query.toMap().get("a").clear()));
    }

    private static Arguments mutation(String description, Consumer<UriQueryWriteable> mutation) {
        return Arguments.of(description, mutation);
    }
}
