/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.jta.jdbc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

final class ResultSetHandler extends CompositeInvocationHandler<ResultSet> {

    ResultSetHandler(Statement proxiedCreator,
                     ResultSet delegate,
                     BiConsumer<? super ResultSet, ? super Throwable> errorNotifier) {
        this(proxiedCreator, () -> delegate, errorNotifier);
    }
    
    ResultSetHandler(Statement proxiedCreator,
                     Supplier<? extends ResultSet> delegateSupplier,
                     BiConsumer<? super ResultSet, ? super Throwable> errorNotifier) {
        super(delegateSupplier,
              List.of(new ObjectMethods<>(delegateSupplier, errorNotifier),
                      new ReturnProxiedCreatorHandler<>(proxiedCreator, delegateSupplier, Set.of("getStatement"), errorNotifier)));
    }
  
}
