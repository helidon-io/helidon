/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.examples.basics;

import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Skeleton example of catalog resource use in {@link Main} class.
 */
public class Catalog implements Service {

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/", this::list)
             .get("/{id}", (req, res) -> getSingle(res, req.path().param("id")));
    }

    private void list(ServerRequest request, ServerResponse response) {
        response.send("1, 2, 3, 4, 5");
    }

    private void getSingle(ServerResponse response, String id) {
        response.send("Item: " + id);
    }
}
