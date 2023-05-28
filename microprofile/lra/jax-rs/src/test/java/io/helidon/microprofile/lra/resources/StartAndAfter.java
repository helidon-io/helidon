/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.lra.resources;

import java.net.URI;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.lra.annotation.ws.rs.LRA;

import static org.eclipse.microprofile.lra.annotation.ws.rs.LRA.LRA_HTTP_CONTEXT_HEADER;

@ApplicationScoped
@Path(StartAndAfter.PATH_BASE)
public class StartAndAfter extends CommonAfter {

    public static final String PATH_BASE = "start-and-after";
    public static final String PATH_START_LRA = "start";
    public static final String CS_START_LRA = PATH_BASE + PATH_START_LRA;

    @PUT
    @LRA(LRA.Type.REQUIRES_NEW)
    @Path(StartAndAfter.PATH_START_LRA)
    public void doInTransaction(@HeaderParam(LRA_HTTP_CONTEXT_HEADER) URI lraId) {
        basicTest.getCompletable(CS_START_LRA).complete(lraId);
    }

}
