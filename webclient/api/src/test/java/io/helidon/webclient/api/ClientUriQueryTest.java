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
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.common.uri.UriQuery;
import io.helidon.common.uri.UriQueryWriteable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientUriQueryTest {
    private static final String RAW_QUERY = "z=1&flag&&a=first&a=second&a=third&%61=fourth&q=a+b&escaped=%2f%2F&";

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
    void testCopyClientQueryThroughPublicMutableQueryApi() {
        var source = ClientUri.create(URI.create("https://example.test/?%61=first&a=second&a+b=x+y&flag&&"));

        UriQueryWriteable copy = UriQueryWriteable.create().from(source.query());

        assertThat(copy.getAllRaw("%61"), is(List.of("first")));
        assertThat(copy.getAllRaw("a"), is(List.of("second")));
        assertThat(copy.getAllRaw("a+b"), is(List.of("x+y")));
        assertThat(copy.all("a"), is(List.of("first", "second")));
        assertThat(copy.all("a b"), is(List.of("x y")));
        assertThat(copy.all("flag"), is(List.of("")));
        assertThat(copy.all(""), is(List.of("", "")));

        copy.set("a", "copy-only");
        assertThat(source.query().all("a"), is(List.of("first", "second")));
        assertThat(source.query().rawValue(), is("%61=first&a=second&a+b=x+y&flag&&"));
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

    private static String replacementRawAfterNamesRemoval() {
        var query = UriQueryWriteable.create();
        query.fromQueryString("a=new&flag&&");
        query.names().remove("a");
        return query.rawValue();
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
