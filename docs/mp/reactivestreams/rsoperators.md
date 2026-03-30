# MicroProfile Reactive Streams Operators

## Contents

- [Overview](#overview)
- [Maven Coordinates](#maven-coordinates)
- [Usage](#usage)
- [Reference](#reference)

## Overview

Helidon implements [MicroProfile Reactive Streams Operators](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/microprofile-reactive-streams-operators-spec-3.0.html) specification which defines reactive operators and provides a standardized tool for manipulation with [Reactive Streams](https://www.reactive-streams.org/). You can use MicroProfile Reactive Streams Operators when you want to maintain source-level portability between different implementations.

## Maven Coordinates

To enable {feature-name}, either add a dependency on the [helidon-microprofile bundle](../../mp/introduction/microprofile.md) or add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../../about/managing-dependencies.md)).

``` xml
<dependency>
   <groupId>io.helidon.microprofile.reactive-streams</groupId>
   <artifactId>helidon-microprofile-reactive-streams</artifactId>
</dependency>
```

## Usage

The MicroProfile Reactive Streams Operators specification provides a set of operators within stages, as well as the builders used to prepare graphs of stages from which streams can be built.

*Example of simple closed graph usage:*

``` java
AtomicInteger sum = new AtomicInteger();

ReactiveStreams.of("1", "2", "3", "4", "5")
        .limit(3)
        .map(Integer::parseInt)
        .forEach(sum::addAndGet)
        .run()
        .whenComplete((r, t) -> System.out.println("Sum: " + sum.get()));

// >Sum: 6
```

|  |  |
|----|----|
| fromIterable | Create new PublisherBuilder from supplied Iterable |
| of | Create new PublisherBuilder emitting supplied elements |
| ofNullable | Empty stream if supplied item is null |
| iterate | Create infinite stream with every next item created by supplied operator from previous item |
| generate | Create infinite stream with every item created by invocation of supplier |
| empty | Create new PublisherBuilder emitting as a first thing complete signal |
| failed | Create new PublisherBuilder emitting as a first thing error signal |
| concat | Concat two streams |
| coupled | Two parallel streams sharing cancel, onError and onComplete signals |
| limit | Limit the size of the stream, when limit is reached completes |
| peek | Invoke consumer for every item passing this operator |
| filter | Drop item when expression result to false |
| map | Transform items |
| flatMap | Flatten supplied stream to current stream |
| flatMapIterable | Flatten supplied iterable to current stream |
| flatMapCompletionStage | Map elements to completion stage and wait for each to be completed, keeps the order |
| flatMapRSPublisher | Map elements to Publishers and flatten this sub streams to original stream |
| takeWhile | Let items pass until expression is true, first time its false completes |
| dropWhile | Drop items until expression is true, first time its false let everything pass |
| skip | Drop first n items |
| distinct | Let pass only distinct items |
| via | Connect supplied processor to current stream return supplied processor |
| onError | Invoke supplied consumer when onError signal received |
| onErrorResume | Emit one last supplied item when onError signal received |
| onErrorResumeWith | When onError signal received continue emitting from supplied publisher builder |
| onErrorResumeWithRsPublisher | When onError signal received continue emitting from supplied publisher |
| onComplete | Invoke supplied runnable when onComplete signal received |
| onTerminate | Invoke supplied runnable when onComplete or onError signal received |
| ifEmpty | Executes given `java.lang.Runnable` when stream is finished without value(empty stream). |
| to | Connect this stream to supplied subscriber |
| toList | Collect all intercepted items to List |
| collect | Collect all intercepted items with provided collector |
| forEach | Invoke supplied Consumer for each intercepted item |
| ignore | Ignore all onNext signals, wait for onComplete |
| reduce | Reduction with provided expression |
| cancel | Cancel stream immediately |
| findFirst | Return first intercepted element |

Operators(Stages) {#terms}

### Graphs

[Graphs](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/microprofile-reactive-streams-operators-spec-3.0.html#_graphs) are pre-prepared stream builders with [stages](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/microprofile-reactive-streams-operators-spec-3.0.html#_stages), which can be combined to closed graph with methods `via` and `to`.

*Combining the graphs and running the stream:*

``` java
// Assembly of stream, nothing is streamed yet
PublisherBuilder<String> publisherStage =
        ReactiveStreams.of("foo", "bar")
                .map(String::trim);

ProcessorBuilder<String, String> processorStage =
        ReactiveStreams.<String>builder()
                .map(String::toUpperCase);

SubscriberBuilder<String, Void> subscriberStage =
        ReactiveStreams.<String>builder()
                .map(s -> "Item received: " + s)
                .forEach(System.out::println);

// Execution of pre-prepared stream
publisherStage
        .via(processorStage)
        .to(subscriberStage).run();

// >Item received:FOO
// >Item received: BAR
```

## Reference

- [MicroProfile Reactive Streams Operators Specification](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/microprofile-reactive-streams-operators-spec-3.0.html)
- [MicroProfile Reactive Streams Operators JavaDoc](https://download.eclipse.org/microprofile/microprofile-reactive-streams-operators-3.0/apidocs)
- [MicroProfile Reactive Streams Operators on GitHub](https://github.com/eclipse/microprofile-reactive-streams-operators)
