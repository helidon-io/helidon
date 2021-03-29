/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.lra.rest;

import javax.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is tracking individual collected LRA participants that
 * contain one or more non-JAX-RS participant methods in their definitions.
 */
@Provider
public class LRAParticipantRegistry {

    private final Map<String, LRAParticipant> lraParticipants;

    // required for Weld to be able to create proxy
    public LRAParticipantRegistry() {
        lraParticipants = new HashMap<>();
    }

    LRAParticipantRegistry(Map<String, LRAParticipant> lraParticipants) {
        this.lraParticipants = new HashMap<>(lraParticipants);
    }

    public LRAParticipant getParticipant(String id) {
            return lraParticipants.get(id);
    }
}
