/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Class FaultToleranceTest.
 */
public abstract class FaultToleranceTest {

    private static SeContainer cdiContainer;

    private static final int NUMBER_OF_THREADS = 20;

    private static Executor executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    @BeforeAll
    public static void startCdiContainer() {
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        cdiContainer = initializer.initialize();
    }

    @AfterAll
    public static void shutDownCdiContainer() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }

    protected <T> T newBean(Class<T> beanClass) {
        return CDI.current().select(beanClass).get();
    }

    public static void printStatus(String message, String status) {
        System.out.println(message + " -> " + status + " [Thread: "
                           + Thread.currentThread().getName() + "]");
    }

    @SuppressWarnings("unchecked")
    static <T> CompletableFuture<T>[] getConcurrentCalls(Supplier<T> supplier, int size) {
        return Stream.generate(
            () -> CompletableFuture.supplyAsync(supplier, executor)
        ).limit(size).toArray(CompletableFuture[]::new);
    }

    static Set<String> getThreadNames(CompletableFuture<String>[] calls) throws Exception {
        return Arrays.asList(calls).stream().map(c -> {
            try {
                return c.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }
}
