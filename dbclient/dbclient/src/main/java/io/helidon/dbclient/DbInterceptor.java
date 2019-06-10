/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient;

import java.util.concurrent.CompletionStage;

/**
 * Interceptor to handle work around a database statement.
 * Example of such interceptors: tracing, metrics.
 * <p>
 * Interceptors can be defined as global interceptors, interceptors for a type of a statement and interceptors for a named
 * statement.
 * These are executed in the following order:
 * <ol>
 *     <li>Named interceptors - if there are any interceptors configured for a specific statement, they are executed first</li>
 *     <li>Type interceptors - if there are any interceptors configured for a type of statement, they are executed next</li>
 *     <li>Global interceptors - if there are any interceptors configured globally, they are executed last</li>
 * </ol>
 * Order of interceptors within a group is based on the order they are registered in a builder, or by their priority when
 * loaded from a Java Service loader
 */
@FunctionalInterface
public interface DbInterceptor {
    /**
     * Statement execution to be intercepted.
     * This method is called before the statement execution starts.
     *
     * @param context Context to access data needed to process an interceptor
     * @return completion stage that completes when this interceptor is finished
     */
    CompletionStage<DbInterceptorContext> statement(DbInterceptorContext context);
}
