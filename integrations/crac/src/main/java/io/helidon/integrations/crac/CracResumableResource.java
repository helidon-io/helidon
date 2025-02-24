/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.crac;

import io.helidon.common.resumable.Resumable;

import org.crac.Context;
import org.crac.Resource;

import static java.lang.System.Logger.Level.TRACE;

class CracResumableResource implements Resource {

    private static final System.Logger LOGGER = System.getLogger(CracResumableResource.class.getName());

    private final Resumable resumable;

    CracResumableResource(Resumable resumable) {
        this.resumable = resumable;
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Suspending resumable {0} as CRaC resource.", resumable.getClass().getName());
        }
        resumable.suspend();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Resuming resumable {0} as CRaC resource.", resumable.getClass().getName());
        }
        resumable.resume();
    }
}
