package io.helidon.webserver;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.Flow.Publisher;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 * @author rgrecour
 */
public class PublisherInputStreamTest {

    @Test
    public void chunkWith0xFFValue() {
        final byte[] bytes = new byte[]{
            0, 1, 2, 3, 4, 5, 6, (byte) 0xFF, 7, 8, 9, 10
        };
        InputStream is = new PublisherInputStream(
                new DataChunkPublisher(
                        new DataChunk[]{DataChunk.create(bytes)}));
        try {
            byte[] readBytes = new byte[bytes.length];
            is.read(readBytes);
            if (!Arrays.equals(bytes, readBytes)) {
                Assertions.fail("expected: " + Arrays.toString(bytes)
                        + ", actual: " + Arrays.toString(readBytes));
            }
        } catch (IOException ex) {
            Assertions.fail(ex);
        }
    }

    static class DataChunkPublisher implements Publisher<DataChunk> {

        private final DataChunk[] chunks;
        private volatile int delivered = 0;

        public DataChunkPublisher(DataChunk[] chunks) {
            this.chunks = chunks;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super DataChunk> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if(n > 0){
                        for(; delivered < n && delivered < chunks.length ; delivered ++){
                            subscriber.onNext(chunks[delivered]);
                        }
                        if(delivered == chunks.length){
                            subscriber.onComplete();
                        }
                    }
                }

                @Override
                public void cancel() {
                }
            });
        }
    }
}
