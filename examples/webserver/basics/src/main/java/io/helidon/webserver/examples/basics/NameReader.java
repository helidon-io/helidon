package io.helidon.webserver.examples.basics;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyReaderContext;

import java.util.concurrent.Flow;

public class NameReader implements MessageBodyReader<Name> {

    private NameReader() {}

    static NameReader create() {
        return new NameReader();
    }

    @Override
    public <U extends Name> Single<U> read(Flow.Publisher<DataChunk> publisher, GenericType<U> type,
                                           MessageBodyReaderContext context) {
        return (Single<U>)ContentReaders.readString(publisher, context.charset()).map(Name::new);
    }

    @Override
    public boolean accept(GenericType<?> type, MessageBodyReaderContext context) {
        return context.contentType()
                .map(ct -> MediaType.parse("application/name").equals(ct))
                .map(acceptable -> acceptable && Name.class.isAssignableFrom(type.rawType()))
                .orElse(false);
    }
}




