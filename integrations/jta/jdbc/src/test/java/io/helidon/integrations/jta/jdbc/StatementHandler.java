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

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.Set;

class StatementHandler extends DelegatingHandler<Statement> {

    StatementHandler(Connection proxiedCreator, Statement delegate) {
        this(null, proxiedCreator, delegate);
    }

    StatementHandler(Handler handler, Connection proxiedCreator, Statement delegate) {
        super(
            new UnwrapHandler(
                new ReturnProxiedCreatorHandler(
                    new CreateChildProxyHandler(handler,
                                                (p, m, a) -> {
                                                    switch (m.getName()) {
                                                    case "executeQuery":
                                                    case "getGeneratedKeys":
                                                    case "getResultSet":
                                                        return
                                                            new ResultSetHandler((Statement) p,
                                                                                 (ResultSet) m.invoke(delegate, a));
                                                    default:
                                                        return null;
                                                    }
                    }),
                    proxiedCreator,
                    "getConnection"),
                delegate),
            delegate,
            m -> Statement.class.isAssignableFrom(m.getDeclaringClass()));
    }

}
