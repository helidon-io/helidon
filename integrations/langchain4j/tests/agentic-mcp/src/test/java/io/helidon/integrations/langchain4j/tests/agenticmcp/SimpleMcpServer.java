/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.tests.agenticmcp;

import java.util.Map;

import io.helidon.http.HeaderValues;
import io.helidon.webserver.jsonrpc.JsonRpcHandlers;
import io.helidon.webserver.jsonrpc.JsonRpcRequest;
import io.helidon.webserver.jsonrpc.JsonRpcResponse;
import io.helidon.webserver.jsonrpc.JsonRpcRouting;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;

class SimpleMcpServer {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());

    static JsonRpcRouting create() {
        return JsonRpcRouting.builder()
                .register("/cli", JsonRpcHandlers.builder()
                        .method("initialize", SimpleMcpServer::initialize)
                        .method("notifications/initialized", (req, res) -> res.send())
                        .method("tools/list", SimpleMcpServer::list)
                        .method("tools/call", SimpleMcpServer::call)
                        .build())
                .build();
    }

    private static void call(JsonRpcRequest req, JsonRpcResponse res) {
        res.header(HeaderValues.create("Mcp-Session-Id", "2fd8e7af-1673-49ad-83d1-7cf22f913cc5"));
        var params = req.asJsonObject().getJsonObject("params");
        var toolName = params.getString("name");
        var arguments = params.getJsonObject("arguments");

        if (toolName.equals("getLatestHelidonVersion")) {
            res.result(toolCallResponse("4.2.0"));
        }
        if (toolName.equals("getInitHelidonSeProjectWithCliCmd")) {
            var projectName = arguments.getString("projectName");
            var version = arguments.getString("version");
            var flavor = arguments.getString("flavor");
            var packageName = arguments.getString("packageName");
            res.result(toolCallResponse(String.format("""
                                                              helidon init --batch \\
                                                              --version %s \\
                                                              --name %s \\
                                                              -Dpackage=%s  \\
                                                              -Dflavor=%s \\
                                                              -Dapp-type=quickstart
                                                              """, version, projectName, packageName, flavor)));
        }
        res.send();
    }

    private static JsonObject toolCallResponse(String text) {
        return JSON.createObjectBuilder()
                .add("content", JSON.createArrayBuilder()
                        .add(JSON.createObjectBuilder()
                                     .add("type", "text")
                                     .add("text", text)
                        )
                )
                .add("isError", false).build();
    }

    private static void list(JsonRpcRequest req, JsonRpcResponse res) {
        res.header(HeaderValues.create("Mcp-Session-Id", "2fd8e7af-1673-49ad-83d1-7cf22f913cc5"));
        res.result(JSON.createObjectBuilder()
                           .add("tools", JSON.createArrayBuilder()
                                   .add(JSON.createObjectBuilder()
                                                .add("name", "getLatestHelidonVersion")
                                                .add("description", "Returns a version of the latest released Helidon")
                                                .add("inputSchema", JSON.createObjectBuilder()
                                                        .add("type", "object")
                                                        .add("properties", EMPTY_JSON_OBJECT)
                                                )
                                   )
                                   .add(JSON.createObjectBuilder()
                                                .add("name", "getInitHelidonSeProjectWithCliCmd")
                                                .add("description", """
                                                        Returns example of Helidon CLI command to create Helidon quickstart
                                                        example with provided projectName version, package name and Helidon
                                                        flavor as parameters.Resulting CLI command can be used for generating
                                                        new quickstart project based on Helidon SE.Version parameter
                                                        should be the latest released version unless specified otherwise.""")
                                                .add("inputSchema", JSON.createObjectBuilder()
                                                        .add("type", "object")
                                                        .add("properties", JSON.createObjectBuilder()
                                                                .add("projectName", JSON.createObjectBuilder()
                                                                        .add("description", """
                                                                                Desired quickstart project name,
                                                                                also a name of the project folder""")
                                                                        .add("type", "string")
                                                                )
                                                                .add("version", JSON.createObjectBuilder()
                                                                        .add("description", """
                                                                                Version of the Helidon release to create
                                                                                a quickstart project with""")
                                                                        .add("type", "string")
                                                                )
                                                                .add("flavor", JSON.createObjectBuilder()
                                                                        .add("description", """
                                                                                Desired flavor of Helidon used in the new project,
                                                                                `SE` or `MP` are the only options.""")
                                                                        .add("type", "string")
                                                                )
                                                                .add("packageName", JSON.createObjectBuilder()
                                                                        .add("description", """
                                                                                Java package name of the quickstart project
                                                                                which will be created with resulting command""")
                                                                        .add("type", "string")
                                                                )
                                                        )
                                                )
                                   )
                           )
                           .build()).send();
    }

    private static void initialize(JsonRpcRequest req, JsonRpcResponse res) {
        res.header(HeaderValues.create("Mcp-Session-Id", "2fd8e7af-1673-49ad-83d1-7cf22f913cc5"));
        res.result(JSON.createObjectBuilder()
                           .add("protocolVersion", "2025-06-18")
                           .add("capabilities", JSON.createObjectBuilder()
                                   .add("logging", EMPTY_JSON_OBJECT)
                                   .add("prompts", JSON.createObjectBuilder()
                                           .add("listChanged", false))
                                   .add("tools", JSON.createObjectBuilder()
                                           .add("listChanged", true))
                                   .add("resources", JSON.createObjectBuilder()
                                           .add("listChanged", false)
                                           .add("subscribe", false))
                                   .add("completions", EMPTY_JSON_OBJECT))
                           .add("serverInfo", JSON.createObjectBuilder()
                                   .add("name", "helidon-mcp-cli-expert")
                                   .add("version", "0.0.1"))
                           .add("instructions", "").build()).send();
    }

}
