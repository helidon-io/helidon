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

package io.helidon.docs.mp.guides.lc4j.memory;

import io.helidon.docs.includes.guides.lc4j.memory.PirateService;
// tag::snippet_1[]
import jakarta.inject.Inject;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/chat")
public class PirateResource {

    @Inject
    PirateService pirateService;

    @POST
    public String chat(@HeaderParam("conversation-id") String conversationId,
                       String message) {
        return pirateService.chat(conversationId, "Frank", message);
    }
}
// end::snippet_1[]