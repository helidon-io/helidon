# Helidon Stream Readers and Writers Support Proposal

Provide an API that adds reading and writing support for a stream of objects. Evaluate the
posibility of building multipart support on top of these readers and writers.

## Proposal

Applications can extend Helidon SE by providing their own  readers and writers. In essence, 
a reader is a mapping of the form
`Publisher<DataChunk> -> T` and a writer is a mapping of the form `T -> Publisher<DataChunk>`. 
This notion can be extended to support object streams by defining a stream reader as 
`Publisher<DataChunk> -> Publisher<T>` and a stream writer as `Publisher<T> -> Publisher<DataChunk>`.

The way a stream of objects is parsed or serialized depends on the media type associated with
the request or response. For example, in a response of type `application/json`, a stream of
objects can be serialized as an array; in a response of type `application/stream+json` using
an object/record separator. The use of streaming types such as `application/stream+json` may
have some additional benefits for clients that support streaming.

Using stream readers and writers enables application code to receiver or provide `Publisher<T>`'s
directly without the need to serialize the stream into data chunks. Moreover, a low-level multipart API
can be built on top of these stream readers and writers simply by adding new types such as
`FormParam` and consuming or returning `Publisher<FormParam>`.

## Example 

An application returns a stream of JSON objects and uses `application/json` as media type. In the
example below, ten JSON objects are streamed to a subscriber provided by Helidon:

```java
        // Create JSON object
         JsonObject msg = JSON.createObjectBuilder()
                 .add("message", "This is a message")
                 .build();
 
         // Produce response as stream of objects
         response.send(subscriber -> subscriber.onSubscribe(
                 new Flow.Subscription() {
                     @Override
                     public void request(long n) {
                         for (int i = 0; i < 10; i++) {
                             subscriber.onNext(msg);
                         }
                         subscriber.onComplete();
                     }
 
                     @Override
                     public void cancel() {
                     }
                 }), JsonObject.class);
```

This example requires the registration of a new stream writer as shown below:

```java
     response.registerStreamWriter(
                type -> type.isAssignableFrom(JsonObject.class)
                        && request.headers().isAccepted(MediaType.APPLICATION_JSON),
                MediaType.APPLICATION_JSON,
                new JsonArrayStreamWriter<>(request, response, JsonObject.class));
```

The class `JsonArrayStreamWriter` interjects data chunks for `[` , `,` and `]` as JSON array
delimiters and uses an existing JSON writer to serialize each JSON object. Note that
the acceptance predicate inspects the `Accept` header and the object type.

Similarly, an application can read a stream of JSON objects by subscribing to a
`Publisher<JsonObject>` returned by Helidon as follows:

```java
        request.content().asPublisherOf(JsonObject.class).subscribe(
                new Flow.Subscriber<JsonObject>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                    }

                    @Override
                    public void onNext(JsonObject item) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                    }
                }); 
```

## Multipart Support

Streaming readers and writers could be implemented to support the media types `multipart/form-data`
and `application/x-www-form-urlencoded`. Given,

```java
public class FormParam<T> {

    private String name;
    private T value;

    public FormParam(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }
}
```

We need to use `GenericType<T>` to ensure that type information is available to the Helidon
runtime as follows:

```java
        request.content()
                .asPublisherOf(new GenericType<FormParam<String>>(){})
                .subscribe(new Flow.Subscriber<FormParam<String>>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                    }

                    @Override
                    public void onNext(FormParam<String> item) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                    }

                    @Override
                    public void onComplete() {
                    }
                });
```

Note that this represents a low-level API to process forms. A higher-level API should be
available to collect all params and provide them as a single `Form` object to the 
application. For example,

```java
request.content().as(Form.class).thenAccept(form -> ... );
```

Helidon should provide additional support for those cases where streaming and the use
of publishers and subscribers is unnecessary.
