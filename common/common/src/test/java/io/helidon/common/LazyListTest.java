/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.common;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

public class LazyListTest {
    @Test
    void getSizeAdd() {
        String text = "Helidon";
        List<AtomicInteger> calledCounters =
                IntStream.rangeClosed(1, 4)
                        .boxed()
                        .map(integer -> new AtomicInteger())
                        .collect(Collectors.toList());

        List<LazyValue<String>> lazyValues = IntStream.rangeClosed(0, 3)
                .mapToObj(i -> LazyValue.create(() -> {
                    calledCounters.get(i).incrementAndGet();
                    return text + i;
                })).collect(Collectors.toList());

        List<String> lazyList = LazyList.create(lazyValues.subList(0, 3));

        assertCounters(calledCounters, 0, 0, 0, 0);

        assertThat(lazyList.size(), is(3));
        assertThat(lazyList.get(0), is(text + "0"));

        assertCounters(calledCounters, 1, 0, 0, 0);

        assertThat(lazyList.get(1), is(text + "1"));

        assertCounters(calledCounters, 1, 1, 0, 0);
        lazyList.add("notLazy");
        assertCounters(calledCounters, 1, 1, 0, 0);

        assertThat(lazyList.get(2), is(text + "2"));
        assertCounters(calledCounters, 1, 1, 1, 0);
        assertThat(lazyList.size(), is(4));
        assertCounters(calledCounters, 1, 1, 1, 0);


        ((LazyList<String>) lazyList).add(lazyValues.get(3));
        assertCounters(calledCounters, 1, 1, 1, 0);
        assertThat(lazyList.size(), is(5));
        assertCounters(calledCounters, 1, 1, 1, 0);
        assertThat(lazyList.get(3), is("notLazy"));
        assertCounters(calledCounters, 1, 1, 1, 0);

        assertThat(lazyList.get(4), is(text + "3"));
        assertCounters(calledCounters, 1, 1, 1, 1);

    }

    @Test
    void iterator() {
        String text = "Helidon";
        List<AtomicInteger> calledCounters =
                IntStream.rangeClosed(0, 3)
                        .mapToObj(i -> new AtomicInteger())
                        .collect(Collectors.toList());

        List<LazyValue<String>> lazyValues = IntStream.rangeClosed(0, 3)
                .mapToObj(i -> LazyValue.create(() -> {
                    calledCounters.get(i).incrementAndGet();
                    return text + i;
                })).collect(Collectors.toList());

        List<String> lazyList = LazyList.create(lazyValues);

        Iterator<String> lazyIterator = lazyList.iterator();

        assertCounters(calledCounters, 0, 0, 0, 0);
        assertThat(lazyIterator.hasNext(), is(equalTo(true)));
        assertCounters(calledCounters, 0, 0, 0, 0);

        assertThat(lazyIterator.next(), is(equalTo(text + "0")));
        assertCounters(calledCounters, 1, 0, 0, 0);
        assertThat(lazyIterator.hasNext(), is(equalTo(true)));
        assertCounters(calledCounters, 1, 0, 0, 0);

        assertThat(lazyIterator.next(), is(equalTo(text + "1")));
        assertCounters(calledCounters, 1, 1, 0, 0);
        assertThat(lazyIterator.hasNext(), is(equalTo(true)));
        assertCounters(calledCounters, 1, 1, 0, 0);

        assertThat(lazyIterator.next(), is(equalTo(text + "2")));
        assertCounters(calledCounters, 1, 1, 1, 0);
        assertThat(lazyIterator.hasNext(), is(equalTo(true)));
        assertCounters(calledCounters, 1, 1, 1, 0);

        assertThat(lazyIterator.next(), is(equalTo(text + "3")));
        assertCounters(calledCounters, 1, 1, 1, 1);
        assertThat(lazyIterator.hasNext(), is(equalTo(false)));
        assertCounters(calledCounters, 1, 1, 1, 1);
    }

    @Test
    void forEach() {
        String text = "Helidon";
        List<AtomicInteger> calledCounters =
                IntStream.rangeClosed(0, 3)
                        .mapToObj(i -> new AtomicInteger())
                        .collect(Collectors.toList());

        List<LazyValue<String>> lazyValues = IntStream.rangeClosed(0, 3)
                .mapToObj(i -> LazyValue.create(() -> {
                    calledCounters.get(i).incrementAndGet();
                    return text + i;
                })).collect(Collectors.toList());

        List<String> lazyList = LazyList.create(lazyValues);

        assertCounters(calledCounters, 0, 0, 0, 0);

        int i = 0;
        Integer[] shiftArray = {0, 0, 0, 0};
        for (String val : lazyList) {
            shiftArray[i] = 1;
            assertCounters(calledCounters, shiftArray);
            assertThat(val, is(equalTo(text + i)));
            i++;
        }
    }

    private void assertCounters(List<AtomicInteger> calledCounters, Integer... expected) {
        assertThat(calledCounters.stream().map(AtomicInteger::get).collect(Collectors.toList()), contains(expected));
    }
}
