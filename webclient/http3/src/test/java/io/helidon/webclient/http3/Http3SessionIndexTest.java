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

package io.helidon.webclient.http3;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http3SessionIndexTest {
    @Test
    void shouldRejectNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new Http3SessionIndex<>(0));
    }

    @Test
    void shouldRejectMismatchedTlsGenerationWithoutChangingIndexes() {
        Http3SessionIndex<Integer, Object, String, String> index = new Http3SessionIndex<>(2);
        Object first = new Object();
        index.putIfAbsent(1, first, "route-1", "tls-1", 1);

        assertThrows(IllegalStateException.class,
                     () -> index.putIfAbsent(2, new Object(), "failed-route", "tls-1", 2));

        assertThat(index.get(1), sameInstance(first));
        assertThat(index.get(2), nullValue());
        assertThat(index.removeRoute("failed-route"), is(empty()));
        assertThat(index.removeStaleTlsGeneration("tls-1", 1), is(empty()));

        Object second = new Object();
        Http3SessionIndex.Insertion<Integer, Object> secondInsertion =
                index.putIfAbsent(2, second, "route-2", "tls-2", 1);
        assertThat(secondInsertion.evicted(), nullValue());
        Object third = new Object();
        Http3SessionIndex.Insertion<Integer, Object> thirdInsertion =
                index.putIfAbsent(3, third, "route-3", "tls-3", 1);
        assertThat(thirdInsertion.evicted().key(), is(1));
        assertThat(thirdInsertion.evicted().value(), sameInstance(first));
        assertThat(index.get(2), sameInstance(second));
        assertThat(index.get(3), sameInstance(third));
    }

    @Test
    void shouldEvictOldestSessionAndKeepIndexesConsistent() {
        Http3SessionIndex<Integer, Object, String, String> index = new Http3SessionIndex<>(3);
        Object first = new Object();
        Object second = new Object();
        Object third = new Object();
        Object fourth = new Object();

        index.putIfAbsent(1, first, "route-1", "tls-1", 1);
        index.putIfAbsent(2, second, "route-2", "tls-1", 1);
        index.putIfAbsent(3, third, "route-3", "tls-2", 1);
        Http3SessionIndex.Insertion<Integer, Object> insertion =
                index.putIfAbsent(4, fourth, "route-4", "tls-2", 1);

        assertThat(insertion.existing(), nullValue());
        assertThat(insertion.evicted().key(), is(1));
        assertThat(insertion.evicted().value(), sameInstance(first));
        assertThat(index.get(1), nullValue());
        assertThat(index.get(2), sameInstance(second));
        assertThat(index.get(3), sameInstance(third));
        assertThat(index.get(4), sameInstance(fourth));
        assertThat(index.removeRoute("route-1"), is(empty()));
        assertThat(index.removeStaleTlsGeneration("tls-1", 1), is(empty()));
        assertThat(index.removeRoute("route-2").stream().map(Http3SessionIndex.Entry::key).toList(), contains(2));
        assertThat(index.removeStaleTlsGeneration("tls-2", 2)
                           .stream()
                           .map(Http3SessionIndex.Entry::key)
                           .sorted()
                           .toList(),
                   contains(3, 4));
        assertThat(index.get(2), nullValue());
        assertThat(index.get(3), nullValue());
        assertThat(index.get(4), nullValue());
        assertThat(index.clear(), is(empty()));
    }

    @Test
    void shouldRemoveOnlyTheRequestedRouteAndTlsGeneration() {
        Http3SessionIndex<Integer, Object, String, String> index = new Http3SessionIndex<>(8);
        Object routeOneFirst = new Object();
        Object routeOneSecond = new Object();
        Object routeTwoCurrent = new Object();
        Object routeTwoSecond = new Object();
        index.putIfAbsent(1, routeOneFirst, "route-1", "tls-1", 1);
        index.putIfAbsent(2, routeOneSecond, "route-1", "tls-2", 1);
        index.putIfAbsent(3, routeTwoCurrent, "route-2", "tls-1", 1);
        index.putIfAbsent(4, routeTwoSecond, "route-2", "tls-1", 1);

        List<Http3SessionIndex.Entry<Integer, Object>> removedRoute = index.removeRoute("route-1");
        assertThat(removedRoute.stream().map(Http3SessionIndex.Entry::key).sorted().toList(), contains(1, 2));
        assertThat(index.get(1), nullValue());
        assertThat(index.get(2), nullValue());
        assertThat(index.get(3), sameInstance(routeTwoCurrent));
        assertThat(index.get(4), sameInstance(routeTwoSecond));
        assertThat(index.removeStaleTlsGeneration("tls-1", 1), is(empty()));

        List<Http3SessionIndex.Entry<Integer, Object>> removedTls = index.removeStaleTlsGeneration("tls-1", 2);
        assertThat(removedTls.stream().map(Http3SessionIndex.Entry::key).sorted().toList(), contains(3, 4));
        assertThat(index.get(3), nullValue());
        assertThat(index.get(4), nullValue());

        Object reloaded = new Object();
        index.putIfAbsent(5, reloaded, "route-2", "tls-1", 2);
        assertThat(index.get(5), sameInstance(reloaded));
    }

    @Test
    void shouldRetireOneTlsBucketAcrossTenThousandRoutes() {
        int routeCount = 10_000;
        String selectedTls = "selected-tls";
        Http3SessionIndex<Integer, Object, Integer, String> index = new Http3SessionIndex<>(routeCount);
        Object first = new Object();
        Object last = new Object();
        for (int i = 0; i < routeCount; i++) {
            Object value = new Object();
            if (i == 0) {
                value = first;
            } else if (i == routeCount - 1) {
                value = last;
            }
            index.putIfAbsent(i, value, i, selectedTls, 7);
        }

        assertThat(index.removeStaleTlsGeneration(selectedTls, 7), is(empty()));
        assertThat(index.get(0), sameInstance(first));
        assertThat(index.get(routeCount - 1), sameInstance(last));

        List<Http3SessionIndex.Entry<Integer, Object>> removed = index.removeStaleTlsGeneration(selectedTls, 8);
        assertThat(removed, hasSize(routeCount));
        assertThat(index.get(0), nullValue());
        assertThat(index.get(routeCount - 1), nullValue());

        Object reloaded = new Object();
        index.putIfAbsent(routeCount, reloaded, routeCount, selectedTls, 8);
        assertThat(index.get(routeCount), sameInstance(reloaded));
        assertThat(index.removeRoute(routeCount).stream().map(Http3SessionIndex.Entry::key).toList(),
                   contains(routeCount));

        Object nextGeneration = new Object();
        index.putIfAbsent(routeCount + 1, nextGeneration, routeCount + 1, selectedTls, 9);
        assertThat(index.get(routeCount + 1), sameInstance(nextGeneration));
    }

    @Test
    void shouldReturnExistingIdentityWithoutChangingIndexes() {
        Http3SessionIndex<Integer, Object, String, String> index = new Http3SessionIndex<>(2);
        Object existing = new Object();
        index.putIfAbsent(1, existing, "route-1", "tls-1", 1);

        Http3SessionIndex.Insertion<Integer, Object> insertion =
                index.putIfAbsent(1, new Object(), "route-2", "tls-2", 2);

        assertThat(insertion.existing(), sameInstance(existing));
        assertThat(insertion.evicted(), nullValue());
        assertThat(index.get(1), sameInstance(existing));
        assertThat(index.removeRoute("route-2"), is(empty()));
        assertThat(index.removeStaleTlsGeneration("tls-2", 3), is(empty()));
        assertThat(index.removeRoute("route-1").stream().map(Http3SessionIndex.Entry::value).toList(),
                   contains(existing));
        assertThat(index.get(1), nullValue());
    }
}
