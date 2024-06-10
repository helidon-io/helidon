/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh8349;

import java.util.Set;

import io.helidon.common.Weight;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@ApplicationPath("/")
@Weight(100)
public class Gh8349App1 extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        return Set.of(Gh8349Resource1.class);
    }

    @Path("/greet1")
    public static class Gh8349Resource1 {
        @POST
        @Produces(MediaType.TEXT_PLAIN)
        public String testPost(String entity) {
            return "Hello World 1!";
        }
    }
}
