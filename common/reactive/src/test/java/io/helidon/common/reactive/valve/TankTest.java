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

package io.helidon.common.reactive.valve;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static io.helidon.common.reactive.valve.TestUtils.generate;
import static io.helidon.common.reactive.valve.TestUtils.generateList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TankTest {

    @Test
    void readPrefilled() throws Exception {
        Tank<Integer> tank = new Tank<>(100);
        generate(0, 30, tank::add);
        CompletableFuture<List<Integer>> cf = tank.collect(Collectors.toList()).toCompletableFuture();
        assertThat(cf.isDone(), is(false));

        tank.close();
        assertThat(cf.get(), is(generateList(0, 30)));
    }

    @Test
    void diferentTypeOfInsert() throws Exception {
        Tank<Integer> tank = new Tank<>(30);
        generate(0, 30, tank::add);
        CompletableFuture<List<Integer>> cf = tank.collect(Collectors.toList()).toCompletableFuture();
        tank.add(30);
        assertThat(tank.offer(31), is(true));
        tank.put(32);
        tank.addAll(generateList(33, 40));
        tank.close();
        assertThat(cf.get(), is(generateList(0, 40)));
    }

    @Test
    void pauseResume1() throws Exception {
        Tank<Integer> tank = new Tank<>(300);
        generate(0, 30, tank::add);
        CompletableFuture<List<Integer>> cf = tank.filter(i -> {
            if (i == 10) {
                tank.pause();
            }
            return true;
        })
                                                  .collect(Collectors.toList())
                                                  .toCompletableFuture();
        generate(30, 40, tank::add);
        tank.resume();
        generate(40, 50, tank::add);
        tank.close();
        assertThat(cf.get(), is(generateList(0, 50)));
    }

    @Test
    void pauseResume2() throws Exception {
        Tank<Integer> tank = new Tank<>(300);
        CompletableFuture<List<Integer>> cf = tank.filter(i -> {
            if (i == 5) {
                tank.pause();
            }
            return true;
        })
                                                  .collect(Collectors.toList())
                                                  .toCompletableFuture();
        generate(0, 10, tank::add);
        tank.resume();
        generate(10, 20, tank::add);
        tank.close();
        assertThat(cf.get(), is(generateList(0, 20)));
    }

    @Test
    void offerToFull() throws Exception {
        Tank<Integer> tank = new Tank<>(10);
        generate(0, 10, tank::add);
        assertThat(tank.offer(10), is(false));
        ForkJoinTask<Boolean> f = ForkJoinPool.commonPool()
                                              .submit(() -> tank.offer(10, 10, TimeUnit.SECONDS));
        CompletableFuture<List<Integer>> cf = tank.collect(Collectors.toList()).toCompletableFuture();
        assertThat(f.get(), is(true));
        assertThat(tank.offer(11), is(true));

        tank.close();
        assertThat(cf.get(), is(generateList(0, 12)));
    }

    @Test
    void noInsertAfterClose() throws Exception {
        Tank<Integer> tank = new Tank<>(100);
        generate(0, 10, tank::add);
        tank.close();
        assertThat(tank.offer(10), is(false));
        assertThrows(IllegalStateException.class, () -> tank.add(11));
        assertThrows(IllegalStateException.class, () -> tank.put(12));
        CompletableFuture<List<Integer>> cf = tank.collect(Collectors.toList()).toCompletableFuture();
        assertThat(cf.get(), is(generateList(0, 10)));
    }

    @Test
    void insertFromDrainHandler() throws Exception {
        Tank<Integer> tank = new Tank<>(100);
        CompletableFuture<List<Integer>> cf = tank.collect(Collectors.toList()).toCompletableFuture();
        tank.whenDrain(() -> generate(0, 10, tank::add));
        tank.close();
        assertThat(cf.get(), is(generateList(0, 10)));
    }

    @Test
    void insertFromDrainHandlerToFull() throws Exception {
        Tank<Integer> tank = new Tank<>(10);
        generate(0, 10, tank::add);
        tank.whenDrain(() -> generate(10, 15, tank::add));
        CompletableFuture<List<Integer>> cf = tank.collect(Collectors.toList()).toCompletableFuture();
        tank.close();
        assertThat(cf.get(), is(generateList(0, 15)));
    }

}
