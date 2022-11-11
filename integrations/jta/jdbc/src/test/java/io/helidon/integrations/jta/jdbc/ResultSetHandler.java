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
import java.util.Set;

class ResultSetHandler extends DelegatingHandler<ResultSet> {

    ResultSetHandler(Statement proxiedCreator, ResultSet delegate) {
        this(null, proxiedCreator, delegate);
    }

    ResultSetHandler(Handler handler, Statement proxiedCreator, ResultSet delegate) {
        super(new UnwrapHandler(new ReturnProxiedCreatorHandler(handler, proxiedCreator, "getStatement"),
                                delegate),
              delegate,
              m -> m.getDeclaringClass() == ResultSet.class);
    }

}
