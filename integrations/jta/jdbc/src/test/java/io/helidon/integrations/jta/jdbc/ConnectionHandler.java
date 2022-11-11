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
import java.sql.DatabaseMetaData;
import java.sql.Statement;

class ConnectionHandler extends DelegatingHandler<Connection> {


    /*
     * Constructors.
     */


    ConnectionHandler(Connection delegate) {
        this(null, delegate);
    }
    
    ConnectionHandler(Handler handler, Connection delegate) {
        super(new UnwrapHandler(new CreateChildProxyHandler(handler,
                                                            (p, m, a) -> {
                                                                if (m.getDeclaringClass() == Connection.class) {
                                                                    Class<?> returnType = m.getReturnType();
                                                                    String name = m.getName();
                                                                    if (Statement.class.isAssignableFrom(returnType)
                                                                        && (name.equals("createStatement")
                                                                            || name.startsWith("prepare"))) {
                                                                        return
                                                                            new StatementHandler((Connection) p,
                                                                                                 (Statement) m.invoke(delegate,
                                                                                                                      a));
                                                                    } else if (DatabaseMetaData.class == returnType
                                                                               && name.equals("getMetaData")) {
                                                                        return
                                                                            new DatabaseMetaDataHandler((Connection) p,
                                                                                                        (DatabaseMetaData)
                                                                                                        m.invoke(delegate, a));
                                                                    }
                                                                }
                                                                return null;
                                                            }),
                                delegate),
              delegate,
              m -> m.getDeclaringClass() == Connection.class);
    }

}
