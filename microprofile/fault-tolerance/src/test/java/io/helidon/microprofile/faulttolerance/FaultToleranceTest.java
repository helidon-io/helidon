/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.cdi.HelidonContainer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Class FaultToleranceTest.
 */
public abstract class FaultToleranceTest {

    private static SeContainer cdiContainer;

    private static final int NUMBER_OF_THREADS = 20;

    private static Executor executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    @BeforeAll
    public static void startCdiContainer() {
        cdiContainer = HelidonContainer.instance().start();
    }

    @AfterAll
    public static void shutDownCdiContainer() {
        if (cdiContainer != null) {
            cdiContainer.close();
        }
    }

    /**
     * Clears all internal handlers before running each test. Latest FT spec has
     * clarified that each method of each class that uses a bulkhead/breaker has
     * its own state (in application scope). Most of our unit tests assume
     * independence so we clear this state before running each test.
     */
    @BeforeEach
    public void resetHandlers() {
        CommandRunner.clearFtHandlersMap();
    }

    protected static <T> T newBean(Class<T> beanClass) {
        return CDI.current().select(beanClass).get();
    }

    protected static <T> T newNamedBean(Class<T> beanClass) {
        return CDI.current().select(beanClass, NamedLiteral.of(beanClass.getSimpleName())).get();
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

    @SuppressWarnings("unchecked")
    static <T> Future<T>[] getAsyncConcurrentCalls(Supplier<Future<T>> supplier, int size) {
        return Stream.generate(() -> supplier.get()).limit(size).toArray(Future[]::new);
    }

    static Set<String> getThreadNames(Future<String>[] calls) {
        return Arrays.asList(calls).stream().map(c -> {
            try {
                return c.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());
    }
}
