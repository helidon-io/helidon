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

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.h2.engine.ConnectionInfo;
import org.h2.engine.Session;

/**
 * Remove support for in-memory databases in native-image.
 */
@TargetClass(className = "org.h2.engine.Engine")
public final class EngineSubstitution {

    /**
     * Just throws an exception in native image.
     * Works as defined in h2 on hotspot.
     *
     * @param connectionInfo connection info
     * @return nothing, as alwas throws an exception in native-image
     *
     * @see org.h2.engine.Engine#createSession(org.h2.engine.ConnectionInfo)
     */
    @Delete
    public Session createSession(ConnectionInfo connectionInfo) {
        throw new UnsupportedOperationException("In-memory database is not available in native-image");
    }

    @Substitute
    void close(String name) {
        // as we do not open any databases, we do not need to close them
    }
}
