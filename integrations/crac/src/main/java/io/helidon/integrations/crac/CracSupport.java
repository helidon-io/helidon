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

import java.util.Map;
import java.util.WeakHashMap;

import io.helidon.common.resumable.Resumable;
import io.helidon.common.resumable.ResumableSupport;

import org.crac.Resource;
import org.crac.management.CRaCMXBean;

import static java.lang.System.Logger.Level.TRACE;

/**
 * Resumable implementation with CRaC.
 */
public class CracSupport implements ResumableSupport {

    private static final System.Logger LOGGER = System.getLogger(CracSupport.class.getName());
    private static final Map<Resumable, Resource> REFERENCES = new WeakHashMap<>();

    @Override
    public void register(Resumable resumable) {
        // CRaC API keeps resources as weak refs, we don't want our wrappers being GCed
        CracResumableResource resumableResource = new CracResumableResource(resumable);
        REFERENCES.put(resumable, resumableResource);
        org.crac.Core.getGlobalContext().register(resumableResource);

        if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "Registered resumable {0} as CRaC resource.",  resumable.getClass().getName());
        }
    }

    @Override
    public void checkpointResume() {
        try {
            org.crac.Core.checkpointRestore();
        } catch (org.crac.RestoreException | org.crac.CheckpointException e) {
            if (LOGGER.isLoggable(TRACE)) {
                LOGGER.log(TRACE, "CRaC checkpoint restore failed", e);
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    public long uptimeSinceResume() {
        return CRaCMXBean.getCRaCMXBean().getUptimeSinceRestore();
    }

    @Override
    public long resumeTime() {
        return CRaCMXBean.getCRaCMXBean().getRestoreTime();
    }
}
