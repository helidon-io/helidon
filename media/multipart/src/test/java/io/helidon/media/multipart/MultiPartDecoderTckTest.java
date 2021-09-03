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
 *
 */

package io.helidon.media.multipart;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Flow;
import java.util.stream.LongStream;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;

import org.reactivestreams.tck.TestEnvironment;
import org.reactivestreams.tck.flow.FlowPublisherVerification;

import static io.helidon.media.multipart.BodyPartTest.MEDIA_CONTEXT;

public class MultiPartDecoderTckTest extends FlowPublisherVerification<ReadableBodyPart> {

    public MultiPartDecoderTckTest() {
        super(new TestEnvironment(200));
    }

    static Flow.Publisher<DataChunk> upstream(final long l) {
        return Multi.create(LongStream.rangeClosed(1, l)
                .mapToObj(i -> {
                            String chunk = "";
                            if (i == 1L) {
                                chunk = "--boundary\n";
                            }
                            chunk += "Content-Id: part" + l + "\n"
                                    + "\n"
                                    + "body " + l + "\n"
                                    + "--boundary";
                            if (i == l) {
                                chunk += "--";
                            } else {
                                chunk += "\n";
                            }
                            return DataChunk.create(ByteBuffer.wrap(chunk.getBytes(StandardCharsets.UTF_8)));
                        }
                ));
    }

    @Override
    public Flow.Publisher<ReadableBodyPart> createFlowPublisher(final long l) {
        MultiPartDecoder decoder = MultiPartDecoder.create("boundary", MEDIA_CONTEXT.readerContext());
        upstream(l).subscribe(decoder);
        return Multi.create(decoder).map(part -> {
            part.content().forEach(chunk -> {});
            return part;
        });
    }

    @Override
    public Flow.Publisher<ReadableBodyPart> createFailedFlowPublisher() {
        return null;
    }
}
