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

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2Route;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.POST;

@Testcontainers(disabledWithoutDocker = true)
class H2SpecIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2SpecIT.class);

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("runH2Spec")
    void h2spec(String caseName, String desc, String id, String err, String skipped) {
        LOGGER.info("{}: \n - {} \nID: {}", caseName, desc, id);
        if (err != null) {
            Assertions.fail(err);
        }
        if (skipped != null) {
            Assumptions.abort(skipped);
        }
    }

    private static Stream<Arguments> runH2Spec() {

        HttpRouting.Builder router = HttpRouting.builder();
        router.route(Http2Route.route(GET, "/", (req, res) -> {
            res.send("Hi Frank!");
        }));

        router.route(Http2Route.route(POST, "/", (req, res) -> {
            req.content().consume();
            res.send("pong");
        }));

        WebServer server = WebServer.builder()
                .addProtocol(Http2Config.builder()
                                     .sendErrorDetails(true)
                                     // 5.1.2 https://github.com/summerwind/h2spec/issues/136
                                     .maxConcurrentStreams(10)
                                     .build())
                .routing(router)
                .build();

        int port = server.start().port();

        try (var cont = new GenericContainer<>(
                new ImageFromDockerfile().withDockerfile(Path.of("./Dockerfile")))
                .withAccessToHost(true)
                .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(365)))
                .withLogConsumer(outputFrame -> LOGGER.info(outputFrame.getUtf8StringWithoutLineEnding()))
                .waitingFor(Wait.forLogMessage(".*Finished in.*", 1))) {

            org.testcontainers.Testcontainers.exposeHostPorts(port);
            cont.withCommand("/usr/local/bin/h2spec "
                                     + "-h host.testcontainers.internal "
                                     + "--junit-report junit-report.xml "
                                     // h2spec creates dummy test headers x-dummy0 with generated content of length configured by parameter --max-header-length
                                     // default value is 4000 to fit just under the default protocol max table size(4096) with margin of 96
                                     // as we are using custom host name 'host.testcontainers.internal' authority header is longer than usual 'localhost'
                                     // also random port can have more chars than the usual 8080
                                     + "--max-header-length " + (4000
                                                - ("host.testcontainers.internal".length() - "localhost".length())
                                                - (String.valueOf(port).length() - "8080".length()))
                                     + " -p " + port)
                    .withStartupAttempts(1)
                    .start();

            cont.copyFileFromContainer("/junit-report.xml", "./target/h2spec-report.xml");
            return cont.copyFileFromContainer("/junit-report.xml", H2SpecIT::parseReport);
        } finally {
            server.stop();
        }

    }

    private static Stream<Arguments> parseReport(InputStream is) throws Exception {
        var a = new ArrayList<Arguments>();
        var dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
        var suitList = dom.getDocumentElement()
                .getElementsByTagName("testsuite");

        for (int i = 0; i < suitList.getLength(); i++) {
            var suitEl = (Element) suitList.item(i);
            var suitName = suitEl.getAttribute("name");
            var caseList = suitEl.getElementsByTagName("testcase");
            for (int j = 0; j < caseList.getLength(); j++) {
                var caseEl = (Element) caseList.item(j);
                var className = caseEl.getAttribute("classname");
                var id = caseEl.getAttribute("package");
                a.add(Arguments.of(suitName,
                                   className,
                                   id,
                                   getChildElValue(caseEl, "error", "failure"),
                                   getChildElValue(caseEl, "skipped")));
            }
        }
        return a.stream();
    }

    private static String getChildElValue(Element caseEl, String... nodeNames) {
        for (int k = 0; k < caseEl.getChildNodes().getLength(); k++) {
            Node node = caseEl.getChildNodes().item(k);
            for (var nodeName : nodeNames) {
                if (nodeName.equals(node.getNodeName())) {
                    return node.getTextContent();
                }
            }
        }
        return null;
    }

}
