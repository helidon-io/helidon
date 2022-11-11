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
import java.util.Set;

class DatabaseMetaDataHandler extends DelegatingHandler<DatabaseMetaData> {

    DatabaseMetaDataHandler(Connection proxiedCreator, DatabaseMetaData delegate) {
        this(null, proxiedCreator, delegate);
    }

    DatabaseMetaDataHandler(Handler handler, Connection proxiedCreator, DatabaseMetaData delegate) {
        super(new UnwrapHandler(new ReturnProxiedCreatorHandler(handler, proxiedCreator, Set.of("getConnection")),
                                delegate),
              delegate,
              m -> m.getDeclaringClass() == DatabaseMetaData.class);
    }

}
