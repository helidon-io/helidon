# Discovery

## Summary

Discovery is defined to be the transformation of a <dfn>discovery name</dfn>, supplied by a user or subsystem at
runtime, into an immutable, non-determinate set of <dfn>discovered resources</dfn>, such as URIs, that it notionally
describes at a given moment in time.

Helidon offers the Discovery feature as a Java API backed by potentially many implementations.

It is anticipated that the API will be used primarily by future Helidon integrations and enhancements. The API may also
be used directly by a Helidon user.

## Use Cases

The Discovery API is a manifestation of the following abstract use cases, possibly among others.

### Basic

1. A developer or subsystem asks Helidon for an implementation of Discovery without necessarily knowing in advance what implementation will be supplied.
2. Helidon provides such a Discovery.
3. The developer or subsystem asks Discovery for a set of <dfn>discovered URIs</dfn>, possibly also bearing metadata, corresponding to a <dfn>discovery name</dfn>.
4. The Discovery implementation provides such a set.
    1. If the provided set is empty or Discovery encounters a problem, the developer or subsystem uses a default value instead or stops the application, as appropriate.
5. The developer or subsystem selects an element from the provided set for use according to the application's requirements and heuristics.
6. The developer or subsystem uses the URI the selected element represents in subsequent development.

### Testing

1. A developer wishes to test a part of her application that happens to use the Discovery API (a Discovery-using component).
    1. This Discovery API usage may not be, itself, under test.
2. She ensures that a Discovery implementation is present in her project's runtime (and hence test) dependencies.
3. She configures this Discovery implementation to be disabled at test time.
    1. Note that this implies any Discovery implementation may be so configured.
4. She executes the test.
5. The Discovery-using component is, by definition, prepared for a Discovery implementation to be disabled.
    a. For example, depending on the final shape of the Discovery API, it might supply a default value at discovery
       time, or it might test the discovered set to see if it is empty before using a default value instead.
6. The disabled-at-test-time Discovery implementation returns an empty discovered set, because it was disabled by the developer.
7. The Discovery-using component is prepared for this case and uses a default value instead (see (5a.)).
8. The test runs successfully, with "real" Discovery never actually being performed.

## Requirements

The Discovery API is a manifestation of the following requirements, possibly among others. Because these are
requirements of the API, they are also requirements of all implementations.

1. Discovery is fundamentally a Java API that provides a set of possibly metadata-bearing, discovered URIs when given a
   <dfn>discovery name</dfn>. This is the <dfn>discovered set</dfn>.
2. Discovery names should be composed only from legal DNS name characters, since DNS-based Discovery implementations are
   possible. In practice this means alphabetic characters, digits, ".", and "-".
3. There must be no restriction on the schemes of URIs used by Discovery (http, https, jdbc, amqp, etc.).
4. A URI represented by an element in the discovered set may be used for any purpose by the caller.
5. The discovered set must be immutable.
6. The discovered set may be empty, depending on the final API.
    a. For example, an API that accepts a default value may end up requiring the default value's presence in the
       discovered set.
7. Any two retrievals may result in discovered sets with different elements.
8. Any two retrievals may result in identical discovered sets.
9. Discovery must permit multiple implementations (Eureka, Consul, Kubernetes, etc.).
10. The presence of two or more enabled Discovery implementations at runtime in an application is prohibited.
11. Discovered set order may or may not be significant, depending on the Discovery implementation.
12. No guarantees exist or can be made about the health or availability of an application described by a URI represented by an element of the provided set.
13. Any malfunctioning of a Discovery implementation must not prevent an application from continuing.
14. Discovery must be <dfn>opt-in</dfn>, not <dfn>opt-out</dfn>. Specifically:
    1. Discovery may be used explicitly, i.e. by a developer making method calls in a Discovery API and then making use
       of the discovered set of discovered URIs she receives.
    2. In certain Helidon integration use cases, Discovery may be used implicitly, i.e. by a facility in Helidon that
       can apply Discovery automatically, but there must be some developer-visible manifestation of Discovery in such a
       case so that Discovery remains opt-in and its effects can be anticipated.
15. Discovery must not be used to find or connect to an implementation of Discovery.

