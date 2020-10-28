/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

/**
 * A default implementation of {@link Context} to be supplied to {@link ExecutionContext}.
 * Any other implementations should extend this.
 */
public class DefaultContext
        implements Context {

    private ThreadLocal<Throwable> currentThrowable = new ThreadLocal<>();

    /**
     * Private no-args constructor.
     */
    private DefaultContext() {
    }

    /**
     * Create a new {@link DefaultContext}.
     * @return  a new {@link DefaultContext}
     */
    public static DefaultContext create() {
        return new DefaultContext();
    }

    @Override
    public void addPartialResultsException(Throwable throwable) {
        currentThrowable.set(throwable);
    }

    @Override
    public Throwable partialResultsException() {
        return currentThrowable.get();
    }

    @Override
    public void removePartialResultsException() {
         currentThrowable.remove();
    }
}
