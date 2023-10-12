/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.examples.webserver.basics;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

/**
 * Skeleton example of catalog resource use in {@link Main} class.
 */
public class Catalog implements HttpService {

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::list)
                .get("/{id}", (req, res) -> getSingle(res, req.path().pathParameters().get("id")));
    }

    private void list(ServerRequest request, ServerResponse response) {
        response.send("1, 2, 3, 4, 5");
    }

    private void getSingle(ServerResponse response, String id) {
        response.send("Item: " + id);
    }
}
