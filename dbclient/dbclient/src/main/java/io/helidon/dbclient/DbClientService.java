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
package io.helidon.dbclient;

import io.helidon.common.reactive.Single;

/**
 * Services can modify the data used to execute a statement as well as
 * react on a statement result.
 * <p>
 * Example of such services: tracing, metrics.
 * <p>
 * Order of execution of services is based on the order they are registered in a builder, or by their priority when
 * loaded from a Java Service loader
 */
@FunctionalInterface
public interface DbClientService {
    /**
     * Statement execution to be intercepted.
     * This method is called before the statement execution starts.
     * If there is no need to modify the context and you do not block,
     * return {@link Single#just(Object) Single.just(context)}.
     *
     * @param context Context to access data needed to process an interceptor
     * @return single that completes when this service is finished
     */
    Single<DbClientServiceContext> statement(DbClientServiceContext context);
}
