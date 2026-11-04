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

package io.helidon.archetypes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.build.archetype.engine.v2.ArchetypeEngineV2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class GeneratedProjectTest {
    @TempDir
    Path tempDir;

    @Test
    void jwtProviderContainsTokenConfiguration() throws IOException {
        Path project = generate(Map.of("security", "true", "security.atn", "jwt"));
        try (InputStream input = Files.newInputStream(project.resolve("src/main/resources/backend-service-jwt.yaml"))) {
            Map<?, ?> config = new Yaml().load(input);
            Map<?, ?> security = (Map<?, ?>) config.get("security");
            List<?> providers = (List<?>) security.get("providers");
            assertThat(providers.size(), is(1));
            Map<?, ?> provider = (Map<?, ?>) providers.getFirst();
            assertThat(provider.size(), is(1));
            assertThat(provider.get("jwt"), instanceOf(Map.class));
            Map<?, ?> jwt = (Map<?, ?>) provider.get("jwt");
            Map<?, ?> token = (Map<?, ?>) jwt.get("atn-token");
            assertThat(token.get("jwk.resource.resource-path"), is("verifying-jwk.json"));
            assertThat(token.get("jwt-audience"), is("http://example.helidon.io"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void healthModuleMatchesBuiltinDependency(boolean builtin) throws IOException {
        Path project = generate(Map.of("health", "true",
                                       "health.builtin", Boolean.toString(builtin),
                                       "metrics", "true",
                                       "metrics.builtin", "true",
                                       "jpms", "true"));
        String module = Files.readString(project.resolve("src/main/java/module-info.java"));
        String pom = Files.readString(project.resolve("pom.xml"));
        assertThat(module, containsString("requires io.helidon.webserver.observe.health;"));
        assertThat(pom, containsString("<artifactId>helidon-webserver-observe-health</artifactId>"));
        assertThat(module.contains("requires io.helidon.health.checks;"), is(builtin));
        assertThat(pom.contains("<artifactId>helidon-health-checks</artifactId>"), is(builtin));
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void dockerBuildContextAndRuntimePaths(boolean multiModule, boolean jlink) throws IOException {
        Path project = generate(Map.of("docker", "true",
                                       "docker.jlink-image", Boolean.toString(jlink),
                                       "multi-module", Boolean.toString(multiModule)));
        String modulePrefix = multiModule ? "app/" : "";
        Path application = multiModule ? project.resolve("app") : project;
        assertThat(Files.exists(application.resolve("pom.xml")), is(true));
        assertThat(Files.exists(project.resolve(".dockerignore")), is(true));
        assertThat(Files.exists(project.resolve("dockerignore")), is(false));
        String dockerfile = Files.readString(project.resolve("Dockerfile"));
        assertThat(dockerfile, not(containsString("-Pjlink-image")));
        assertThat(dockerfile, containsString("COPY --from=build /helidon/" + modulePrefix
                                                     + "target/review-project.jar ./"));
        assertThat(dockerfile, containsString("CMD [\"java\", \"-jar\", \"review-project.jar\"]"));
        assertBuildContext(project, dockerfile);
        if (multiModule) {
            assertThat(dockerfile, containsString("ADD app/pom.xml app/pom.xml"));
            assertThat(dockerfile, containsString("ADD app/src app/src"));
            assertThat(dockerfile, containsString("-pl app -am"));
            assertThat(Files.exists(application.resolve("Dockerfile")), is(false));
        }
        Path jlinkFile = project.resolve("Dockerfile.jlink");
        assertThat(Files.exists(jlinkFile), is(jlink));
        if (jlink) {
            String jlinkDockerfile = Files.readString(jlinkFile);
            assertThat(jlinkDockerfile, not(is(dockerfile)));
            assertThat(jlinkDockerfile, containsString("-Pjlink-image -DskipJavaImage"));
            assertThat(jlinkDockerfile, containsString("-Pjlink-image \\\n"));
            assertThat(jlinkDockerfile, containsString("COPY --from=build /helidon/" + modulePrefix
                                                                  + "target/review-project-jri ./"));
            assertThat(jlinkDockerfile, containsString("ENTRYPOINT [\"/bin/bash\", \"/helidon/bin/start\"]"));
            assertThat(jlinkDockerfile, not(containsString("\"-jar\"")));
            assertBuildContext(project, jlinkDockerfile);
        }
    }

    private static void assertBuildContext(Path project, String dockerfile) {
        dockerfile.lines()
                .filter(line -> line.startsWith("ADD "))
                .forEach(line -> assertThat(line, Files.exists(project.resolve(line.split("\\s+")[1])), is(true)));
    }

    private Path generate(Map<String, String> options) {
        Map<String, String> values = new HashMap<>(Map.of("flavor", "se",
                                                        "app-type", "custom",
                                                        "media", "json",
                                                        "media.json-lib", "jsonp",
                                                        "security", "false",
                                                        "db", "false",
                                                        "extra", "none",
                                                        "metrics", "false",
                                                        "health", "false",
                                                        "tracing", "false"));
        values.put("docker", "false");
        values.put("k8s", "false");
        values.put("jpms", "false");
        values.put("multi-module", "false");
        values.put("module-name", "app");
        values.put("groupId", "com.example");
        values.put("artifactId", "review-project");
        values.put("version", "1.0.0-SNAPSHOT");
        values.put("package", "com.example.review");
        values.putAll(options);
        return ArchetypeEngineV2.builder()
                .cwd(Path.of("target", "archetype"))
                .batch(true)
                .externalValues(values)
                .output(() -> tempDir.resolve("project"))
                .build()
                .generate();
    }
}
