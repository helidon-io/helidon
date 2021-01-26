/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.HashParameters;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyWriterContext;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class UnstableTempTest {

    private static final Logger LOGGER = Logger.getLogger(UnstableTempTest.class.getName());

    private static final String JAR_NAME = "test.jar";
    private static final String FILE_NAME = "test.js";
    private static final String FILE_CONTENT = "alert(\"Hello, World!\");";
    private static Path tmpDir;

    @BeforeAll
    static void beforeAll() throws IOException, URISyntaxException {
        tmpDir = Paths.get(UnstableTempTest.class.getResource("").toURI()).resolve("tmp");
        Files.createDirectories(tmpDir);
    }

    @Test
    void cleanedTmpDuringRuntime() throws IOException {
        List<String> contents = new ArrayList<>(2);

        Path jar = createJar();
        URL jarUrl = new URL("jar:file:" + jar.toUri().getPath() + "!/" + FILE_NAME);
        LOGGER.fine(() -> "Generated test jar url: " + jarUrl.toString());
        ClassPathContentHandler classPathContentHandler =
                new ClassPathContentHandler(null,
                        null,
                        new ContentTypeSelector(null),
                        "/",
                        tmpDir,
                        Thread.currentThread().getContextClassLoader());

        // Empty headers
        RequestHeaders headers = mock(RequestHeaders.class);
        when(headers.isAccepted(any())).thenReturn(true);
        when(headers.acceptedTypes()).thenReturn(Collections.emptyList());
        ResponseHeaders responseHeaders = mock(ResponseHeaders.class);

        ServerRequest request = Mockito.mock(ServerRequest.class);
        Mockito.when(request.headers()).thenReturn(headers);
        ServerResponse response = Mockito.mock(ServerResponse.class);
        MessageBodyWriterContext ctx = MessageBodyWriterContext.create(HashParameters.create());
        ctx.registerFilter(dataChunkPub -> {
            String fileContent = new String(Single.create(dataChunkPub).await().bytes());
            contents.add(fileContent);
            return Single.just(DataChunk.create(ByteBuffer.wrap(fileContent.getBytes())));
        });
        Mockito.when(response.headers()).thenReturn(responseHeaders);
        Mockito.when(response.send(Mockito.any(Function.class))).then(mock -> {
            Function<MessageBodyWriterContext, Flow.Publisher<DataChunk>> argument = mock.getArgument(0);
            return Single.create(argument.apply(ctx)).onError(throwable -> throwable.printStackTrace());
        });

        classPathContentHandler.sendJar(Http.Method.GET, FILE_NAME, jarUrl, request, response);
        deleteTmpFiles();
        classPathContentHandler.sendJar(Http.Method.GET, FILE_NAME, jarUrl, request, response);

        assertThat(contents, containsInAnyOrder(FILE_CONTENT, FILE_CONTENT));
    }

    private void deleteTmpFiles() throws IOException {
        LOGGER.fine(() -> "Cleaning temp dir: " + tmpDir);
        Files.list(tmpDir)
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        fail("Unable to delete " + path.getFileName(), e);
                    }
                });
    }

    private Path createJar() {
        try {
            Path testJar = Path.of(UnstableTempTest.class.getResource("").toURI()).resolve(JAR_NAME);
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            JarOutputStream target = new JarOutputStream(new FileOutputStream(testJar.toFile()), manifest);
            JarEntry entry = new JarEntry(FILE_NAME);
            BufferedOutputStream bos = new BufferedOutputStream(target);
            target.putNextEntry(entry);
            bos.write(FILE_CONTENT.getBytes());
            bos.close();
            target.close();
            return testJar;
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
