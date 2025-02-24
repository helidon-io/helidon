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

package io.helidon.integrations.jta.cdi;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.resumable.Resumable;

import com.arjuna.ats.arjuna.coordinator.TxControl;

class NarayanaResumableResource implements Resumable {

    private static final Logger LOGGER = Logger.getLogger(NarayanaResumableResource.class.getName());

    @Override
    public void suspend() {
        LOGGER.log(Level.FINE, "Disabling Narayana before suspend");
        TxControl.disable(true);
    }

    @Override
    public void resume() {
        LOGGER.log(Level.FINE, "Enabling Narayana after resume");
        TxControl.enable();
    }
}
