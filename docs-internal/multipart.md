# Helidon Multipart Support Proposal

Provide an API for the reactive webserver that enables processing multipart requests and returning multipart responses.

## Proposal

Support 2 models: buffered and reactive.

With the buffered model the request content is fully decoded in memory and all the parts are handed-over at the same time.
This model enables simple use-cases with small size parts.

With the reactive model the request content is decoded for each chunk and the user can subscribe to the stream of chunks for each part.
This model enables advanced streaming use-cases with large size parts.

Supporting multipart in the webserver means adding support for a new media type. This is done by adding a new entity type with readers and writers.

Each of the 2 models above will be represented by different entity types: `MultiPart` and `StreamingMultiPart`.

```java
// buffered model
request.content().as(MultiPart.class).thenAccept(/* ... */);
```

```java
// reactive model
request.content().as(StreamingMultiPart.class).thenAccept(/* ... */);
```

## Examples

Let's illustrate with code examples first.

### Enable Multipart Support

The readers and writers for the multipart entity types will be registered by a service, similar to how other media types are supported in the webserver.
All the examples below assume that the MultiPartSupport is registered as follow:

```java
Routing.builder()
    .register(MultiPartSupport.create())
    // user defined handlers...
    .build());
```

### Process a request with the buffered model

The following example consumes each part content as a `JsonObject`:

```java
request.content().as(MultiPart.class).thenAccept(mp -> {
    for(BodyPart bodyPart : mp.bodyParts()){
        System.out.println("File uploaded: " + bodyPart.headers().filename());
        bodyPart.content().as(JsonObject.class).thenAccept((json) -> {
            System.out.println(json.toString());
        });
    }
    response.send("Files uploaded successful");
});
```

### Return a response with the buffered model

The following example echoes the multipart request into the response:

```java
request.content().as(MultiPart.class).thenAccept(mp -> {
    response.send(mp);
});
```

The following example returns a multipart response from a list of `JsonObject`:

```java
MultiPart mp = MultiPart.create(
    Json.createObjectBuilder().add("foo", "bar").build(),
    Json.createObjectBuilder().add("alice", "bob").build());
response.send(mp);
```

### Process a request content with the reactive model

The following example consumes each part content as a `JsonObject`:

```java
req.content().as(StreamingMultiPart.class).thenAccept((multiPart) -> {
    multiPart.subscribe(new Flow.Subscriber<BodyPart>(){
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(BodyPart bodyPart) {
            bodyPart.content().as(JsonObject.class).thenAccept((str) -> {
                System.out.println("File uploaded: " + bodyPart.headers().filename());
            });
        }

        @Override
        public void onError(Throwable throwable) {
            res.status(500);
            res.send();
        }

        @Override
        public void onComplete() {
            System.out.println("sending response");
            res.send("Files uploaded successfully");
        }
    });
});
```

The following example consumes each part content using reactive stream subscribers, `ServerFileWriter` is inspired from the streaming example at `webserver/examples/streaming`:

```java
req.content().as(StreamingMultiPart.class).thenAccept((multiPart) -> {
    multiPart.subscribe(new Flow.Subscriber<BodyPart>(){
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(BodyPart bodyPart) {
            bodyPart.content().subscribe(new ServerFileWriter());
        }

        @Override
        public void onError(Throwable throwable) {
            res.status(500);
            res.send();
        }

        @Override
        public void onComplete() {
            System.out.println("sending response");
            res.send("Files uploaded successfully");
        }
    });
});
```

### Return a response with the reactive model

The following example returns a multipart response using publishers of `Datachunk`, `ServerFileReader` is inspired from the streaming example at `webserver/examples/streaming`:

```java
StreamingMultiPart mp = StreamingMultiPart.builder()
    .bodyPart(BodyPart.create(
        BodyPartHeaders.builder().
            .contentType(MediaType.APPLICATION_JSON)
            .filename("file1")
            .build(),
        new ServerFileReader(filePath1))
    .bodyPart(BodyPart.create(
        BodyPartHeaders.builder().
            .contentType(MediaType.APPLICATION_JSON)
            .filename("file2")
            .build(),
        new ServerFileReader(filePath2))
    .build();
response.send(mp);
```

## API

```java
public interface BodyPart {

    Content content();

    BodyPartHeaders headers();

    static <T> BodyPart create(T entity){
        return builder().
            .entity(entity)
            .build();
    }

    static BodyPart create(BodyPartHeaders headers, Publisher<DataChunk> content){
        return builder()
            .headers(headers)
            .content(content)
            .build();
    }

    static Builder builder(){
        return new BodyPartBuilder();
    }

    static interface Builder extends io.helidon.common.Builder<BodyPart> {

        <T> Builder entity(T entity);

        Builder headers(BodyPartHeaders header);

        Builder content(Publisher<DataChunk> content);

        BodyPart build();
    }
}
```

```java
public interface StreamingMultiPart extends BodyPart, Publisher<BodyPart> {

    static <T> StreamingMultiPart create(T ... entities){
        Builder builder = builder();
        for(T entity : entities){
            builder.bodyPart(BodyPart.create(entity));
        }
        return builder.build();
    }

    static StreamingMultiPart create(Publisher<BodyPart> publisher){
        return builder().bodyParts(publisher).builder();
    }

    static StreamingMultiPart create(BodyPart ... bodyParts){
        Builder builder = builder();
        for(BodyPart bodyPart : bodyParts){
            builder.bodyPart(bodyPart);
        }
        return builder.build();
    }

    static Builder builder(){
        return new StreamingMultiPartBuilder();
    }

    static interface Builder extends io.helidon.common.Builder<StreamingMultiPart> {

        Builder bodyPart(BodyPart bodyPart);

        Builder bodyParts(BodyPart ... bodyParts);

        Builder bodyParts(Publisher<BodyPart> bodyParts);

        StreamingMultiPart build();
    }
}
```

```java
public interface MultiPart {

    Iterable<BodyPart> bodyParts();

    static <T> MultiPart create(T ... entities){
        Builder builder = builder();
        for(T entity : entities){
            builder.bodyPart(BodyPart.create(entity));
        }
        return builder.build();
    }

    static Builder builder(){
        return new MultiPartBuilder();
    }

    static interface Builder extends StreamingMultiPart.Builder {

        MultiPart build();
    }
}
```

```java
public class BodyPartHeaders extends Parameters {

    String name();

    String filename();

    String contentType();

    String contentTransferEncoding();

    Charset charset();

    long size();

    static Builder builder(){
        return new BodyPartHeadersBuilder();
    }

    static interface Builder extends io.helidon.common.Builder<BodyPartHeaders> {

        Builder name(String name);

        Builder filename(String filename);

        Builder contentType(String contentType);

        Builder contentTransferEncoding(String contentTransferEncoding);

        Builder charset(Charset charset);

        Buidler size(long size);

        Builder header(String name, String value);

        Builder headers(Map<String, String> headers);

        BodyPartHeaders build();
    }
}
```

```java
public class MultiPartSupport implements Service, Handler {
    // ...
}
```

## Error Handling

Error handling should not be different than for any other media type already supported in Helidon.

The errors will be delegated to the content subscribers and to the webserver error handlers chain.

## HTTP2

We should be able to support the buffered model with HTTP2.

The reactive model relies on generic HTTP/2 streaming support which has some known issues currently.

## Generic type mapping

Having support for generic type mapping could help simplify the use-case of reading a multipart as a list of the same entity type.

E.g. the example below consumes all parts as a `List<JsonObject>`

```java
request.content().as(new GenericType<List<JsonObject>>(){}).thenAccept(/* ... */);
```

Note that this would rely on generic type mapping support in the webserver which is not implemented yet.
This will likely be supported in a future release.

## Open Issues
