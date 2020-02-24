/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.rest.client.example.basic;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.helidon.webclient.FileSubscriber;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

/**
 * Example of how to upload and download file.
 */
public class UploadAndDownloadExample {

    void upload(Path filePath, String uri) {
        WebClient client = WebClient.create();

        client.put()
                .uri(uri)
                .submit(Paths.get("someFile.txt"))
                .thenApply(WebClientResponse::status)
                .thenAccept(System.out::println);
    }

    void download(String uri, Path filePath) {
        WebClient client = WebClient.create();
        FileSubscriber sub = FileSubscriber.create(filePath);

        client.get()
                .uri(uri)
                .request()
                .thenApply(WebClientResponse::content)
                .thenAccept(sub::subscribeTo)
                .thenAccept(o -> System.out.println("Download completed"));
    }
}
