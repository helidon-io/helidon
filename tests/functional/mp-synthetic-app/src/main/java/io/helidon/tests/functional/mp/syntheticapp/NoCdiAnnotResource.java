/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.functional.mp.syntheticapp;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Example JAX-RS resource that would be automatically picked-up to create a JAX-RS Synthetic Application.
 */
@Path("/mp/synthetic/path")
public class NoCdiAnnotResource {
    @GET
    public String testEndpoint() {
        return "path-tested";
    }
}
