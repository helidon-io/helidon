# Helidon Multipart Support Proposal

Provide an API for the reactive webserver that enables processing multipart requests and returning multipart responses.

## Proposal

Support 2 models: buffered and reactive.

With the buffered model the request content is fully decoded in memory and all the parts are handed-over at the same time.
This model enables simple use-cases with small size parts.

With the reactive model the request content is decoded for each chunk and the user can subscribe to the stream of chunks for each part.
This model enables advanced streaming use-cases with large size parts.

Supporting multipart in the webserver means adding support for a new media type. This is done by adding a new entity type with readers and writers.

Each of the 2 models above will be represented by different entity types: `BufferedMultiPart` and `MultiPart`.

```java
// buffered model
request.content().as(BufferedMultiPart.class).thenAccept(/* ... */);
```

```java
// reactive model
request.content().as(MultiPart.class).thenAccept(/* ... */);
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
request.content().as(BufferedMultiPart.class).thenAccept(mp -> {
    for(BodyPart bodyPart : mp.bodyParts()){
        JsonObject json = bodyPart.content().as(JsonObject.class);
        System.out.println("File uploaded: " + bodyPart.headers().filename());
        System.out.println(json.toString());
    }
    response.send("Files uploaded successful");
});
```

### Return a response with the buffered model

The following example echoes the multipart request into the response:

```java
request.content().as(BufferedMultiPart.class).thenAccept(mp -> {
    response.send(mp.content());
});
```

The following example returns a multipart response from a list of `JsonObject`:

```java
BufferedMultiPart mp = BufferedMultiPart.create(
    Json.createObjectBuilder().add("foo", "bar").build(),
    Json.createObjectBuilder().add("alice", "bob").build());
response.send(mp.content());
```

### Process a request content with the reactive model

The following example consumes each part content as a `JsonObject`:

```java
req.content().as(MultiPart.class).thenAccept((multiPart) -> {
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
req.content().as(MultiPart.class).thenAccept((multiPart) -> {
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
MultiPart mp = MultiPart.builder()
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
response.send(mp.content());
```

## API

```java
public interface BodyPart {

    ServerRequest request();

    ServerResponse response();

    Content content();

    BodyPartHeaders headers();

    BodyPart parent();

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

        BodyPart builder();
    }
}
```

```java
public interface MultiPart extends BodyPart, Publisher<BodyPart> {

    static <T> MultiPart create(T ... entities){
        Builder builder = builder();
        for(T entity : entities){
            builder.bodyPart(BodyPart.create(entity));
        }
        return builder.build();
    }

    static MultiPart create(Publisher<BodyPart> publisher){
        return builder().bodyParts(publisher).builder();
    }

    static MultiPart create(BodyPart ... bodyParts){
        Builder builder = builder();
        for(BodyPart bodyPart : bodyParts){
            builder.bodyPart(bodyPart);
        }
        return builder.build();
    }

    static Builder builder(){
        return new MultiPartBuilder();
    }

    static interface Builder extends io.helidon.common.Builder<MultiPart> {

        Builder bodyPart(BodyPart bodyPart);

        Builder bodyParts(BodyPart ... bodyParts);

        Builder bodyParts(Publisher<BodyPart> bodyParts);

        MultiPart build();
    }
}
```

```java
public interface BufferedMultiPart {

    Content content();

    Iterable<BodyPart> bodyParts();

    static <T> MultiPart create(T ... entities){
        Builder builder = builder();
        for(T entity : entities){
            builder.bodyPart(BodyPart.create(entity));
        }
        return builder.build();
    }

    static Builder builder(){
        return new BufferedMultiPartBuilder();
    }

    static interface Builder extends MultiPart.Builder {

        BufferedMultiPart build();
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

## Open Issues

- Should we support deserializing from the top level entity, e.g. as a `List<File>` ?
  - This requires generic type mapping
- How to handle errors raised during body part processing ?
  - This can be done with reactive model using a body part subscriber
- How to support HTTP2 ?