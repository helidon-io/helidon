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
package io.helidon.db;

/**
 * Interceptor to handle work around a database statement.
 * Example of such interceptors: tracing, metrics.
 */
@FunctionalInterface
public interface DbInterceptor {
    /**
     * Statement execution to be intercepted.
     * This method is called before the statement execution starts.
     *
     * @param context Context to access data needed to process an interceptor
     */
    void statement(DbInterceptorContext context);
}
