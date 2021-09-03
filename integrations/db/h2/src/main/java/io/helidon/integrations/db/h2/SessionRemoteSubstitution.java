/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.integrations.db.h2;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.h2.engine.ConnectionInfo;
import org.h2.engine.SessionInterface;
import org.h2.engine.SessionRemote;

/**
 * Substitution that makes sure we only support remote sessions in h2.
 */
@TargetClass(className = "org.h2.engine.SessionRemote")
public final class SessionRemoteSubstitution {
    @Alias
    private ConnectionInfo connectionInfo;

    /**
     * Only allow connection to remote server.
     *
     * @param openNew ignored
     * @return this instance connected to server
     * @throws java.lang.UnsupportedOperationException in case in-memory db is requested
     */
    @Substitute
    public SessionInterface connectEmbeddedOrServer(boolean openNew) {
        ConnectionInfo ci = connectionInfo;
        if (ci.isRemote()) {
            connectServer(ci);
            return SessionRemote.class.cast(this);
        }
        throw new UnsupportedOperationException("Cannot connect to embedded server in native image");
    }

    @Alias
    private void connectServer(ConnectionInfo ci) {
    }
}
