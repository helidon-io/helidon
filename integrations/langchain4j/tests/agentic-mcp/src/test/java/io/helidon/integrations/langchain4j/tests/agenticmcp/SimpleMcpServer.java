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

import io.helidon.http.HeaderValues;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.webserver.jsonrpc.JsonRpcHandlers;
import io.helidon.webserver.jsonrpc.JsonRpcRequest;
import io.helidon.webserver.jsonrpc.JsonRpcResponse;
import io.helidon.webserver.jsonrpc.JsonRpcRouting;

class SimpleMcpServer {

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
        JsonObject params = req.asJsonObject().objectValue("params").orElseThrow();
        String toolName = params.stringValue("name").orElseThrow();

        if (toolName.equals("getLatestHelidonVersion")) {
            res.result(toolCallResponse("4.2.0"));
        }
        if (toolName.equals("getInitHelidonSeProjectWithCliCmd")) {
            JsonObject arguments = params.objectValue("arguments").orElseThrow();
            String projectName = arguments.stringValue("projectName").orElseThrow();
            String version = arguments.stringValue("version").orElseThrow();
            String flavor = arguments.stringValue("flavor").orElseThrow();
            String packageName = arguments.stringValue("packageName").orElseThrow();
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
        return JsonObject.builder()
                .set("content", JsonArray.create(JsonObject.builder()
                                                    .set("type", "text")
                                                    .set("text", text)
                                                    .build()))
                .set("isError", false)
                .build();
    }

    private static void list(JsonRpcRequest req, JsonRpcResponse res) {
        res.header(HeaderValues.create("Mcp-Session-Id", "2fd8e7af-1673-49ad-83d1-7cf22f913cc5"));
        res.result(JsonObject.builder()
                           .set("tools", JsonArray.create(
                                   JsonObject.builder()
                                           .set("name", "getLatestHelidonVersion")
                                           .set("description", "Returns a version of the latest released Helidon")
                                           .set("inputSchema", JsonObject.builder()
                                                   .set("type", "object")
                                                   .set("properties", JsonObject.empty())
                                                   .build())
                                           .build(),
                                   JsonObject.builder()
                                           .set("name", "getInitHelidonSeProjectWithCliCmd")
                                           .set("description", """
                                                   Returns example of Helidon CLI command to create Helidon quickstart
                                                   example with provided projectName version, package name and Helidon
                                                   flavor as parameters.Resulting CLI command can be used for generating
                                                   new quickstart project based on Helidon SE.Version parameter
                                                   should be the latest released version unless specified otherwise.""")
                                           .set("inputSchema", JsonObject.builder()
                                                   .set("type", "object")
                                                   .set("properties", JsonObject.builder()
                                                           .set("projectName", JsonObject.builder()
                                                                   .set("description", """
                                                                           Desired quickstart project name,
                                                                           also a name of the project folder""")
                                                                   .set("type", "string")
                                                                   .build())
                                                           .set("version", JsonObject.builder()
                                                                   .set("description", """
                                                                           Version of the Helidon release to create
                                                                           a quickstart project with""")
                                                                   .set("type", "string")
                                                                   .build())
                                                           .set("flavor", JsonObject.builder()
                                                                   .set("description", """
                                                                           Desired flavor of Helidon used in the new project,
                                                                           `SE` or `MP` are the only options.""")
                                                                   .set("type", "string")
                                                                   .build())
                                                           .set("packageName", JsonObject.builder()
                                                                   .set("description", """
                                                                           Java package name of the quickstart project
                                                                           which will be created with resulting command""")
                                                                   .set("type", "string")
                                                                   .build())
                                                           .build())
                                                   .build())
                                           .build()))
                           .build())
                .send();
    }

    private static void initialize(JsonRpcRequest req, JsonRpcResponse res) {
        res.header(HeaderValues.create("Mcp-Session-Id", "2fd8e7af-1673-49ad-83d1-7cf22f913cc5"));
        res.result(JsonObject.builder()
                           .set("protocolVersion", "2025-06-18")
                           .set("capabilities", JsonObject.builder()
                                   .set("logging", JsonObject.empty())
                                   .set("prompts", JsonObject.builder()
                                           .set("listChanged", false)
                                           .build())
                                   .set("tools", JsonObject.builder()
                                           .set("listChanged", true)
                                           .build())
                                   .set("resources", JsonObject.builder()
                                           .set("listChanged", false)
                                           .set("subscribe", false)
                                           .build())
                                   .set("completions", JsonObject.empty())
                                   .build())
                           .set("serverInfo", JsonObject.builder()
                                   .set("name", "helidon-mcp-cli-expert")
                                   .set("version", "0.0.1")
                                   .build())
                           .set("instructions", "")
                           .build())
                .send();
    }

}
