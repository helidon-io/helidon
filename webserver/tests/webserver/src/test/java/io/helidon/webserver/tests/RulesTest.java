/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRules;

import static io.helidon.common.testing.http.junit5.HttpHeaderMatcher.hasHeader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class RulesTest extends RoutingTestBase {

    RulesTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/my path", (req, res) -> res.send("done"))
                .get("/českáCesta", (req, res) -> res.send("done"))
                // shortcut methods with path matchers
                .get("/wildcard_*", (req, res) -> res.send("wildcard_test1"))
                .post("/wildcard/*", (req, res) -> res.send("wildcard_test2"))
                // shortcut methods with path
                .get("/get", (req, res) -> res.send("get"))
                .post("/post", (req, res) -> res.send("post"))
                .put("/put", (req, res) -> res.send("put"))
                .delete("/delete", (req, res) -> res.send("delete"))
                .head("/head", (req, res) -> res.send("head"))
                .options("/options", (req, res) -> res.send("options"))
                .trace("/trace", (req, res) -> res.send("trace"))
                .patch("/patch", (req, res) -> res.send("patch"))
                .any("/any", (req, res) -> res.send("any"))
                // shortcut methods using multiple handlers
                .get("/get_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("get_multi"))
                .post("/post_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("post_multi"))
                .put("/put_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("put_multi"))
                .delete("/delete_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("delete_multi"))
                .head("/head_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("head_multi"))
                .options("/options_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("options_multi"))
                .trace("/trace_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("trace_multi"))
                .patch("/patch_multi", RoutingTestBase::multiHandler, (req, res) -> res.send("patch_multi"))
                // shortcut methods with no path pattern
                .get((req, res) -> res.send("get_catchall"))
                .post((req, res) -> res.send("post_catchall"))
                .put((req, res) -> res.send("put_catchall"))
                .delete((req, res) -> res.send("delete_catchall"))
                .head((req, res) -> res.send("head_catchall"))
                .options((req, res) -> res.send("options_catchall"))
                .trace((req, res) -> res.send("trace_catchall"))
                .patch((req, res) -> res.send("patch_catchall"));
    }
}
