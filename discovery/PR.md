# Discovery

## Summary

*Discovery* transforms a supplied *discovery name* into an immutable *discovered set* of immutable resources suitable for it at a given moment of runtime.

In the first iteration of this feature, the resources are metadata-bearing URIs (*discovered URIs*), and can be used for any purpose. They are most often used to help connect to microservices on the network that may come and go over time.

Discovery is fundamentally a specification, with a corresponding vendor-independent Java API, that can be implemented by any number of implementations.

## Pull Request

This pull request introduces the Discovery specification. Pull requests featuring implementations, and implicit integrations, will follow later.

## Requirements and Terminology

These requirements informed the design of the Discovery specification. They, the full specification, and the terminology used to describe them, are present, normatively, in the API specification. They are reproduced here in the form originally agreed to.

1. *Discovery* is fundamentally a Java API that provides a set of metadata-bearing *discovered URIs* when given a *discovery name* and a *default URI*. This provided set is the *discovered set*.
2. A discovered URI represented by an element in the discovered set may be used for any purpose by the caller.
3. A discovered URI must be immutable. Metadata borne by a discovered URI must be immutable and must be `String`-typed. A discovered URI may bear no metadata.
4. Two discovered URIs are considered *equal* if and only if the URIs they represent are equal. Notably, metadata is not included in equality calculations.
5. A discovered set must be immutable.
6. A discovered set must include a discovered URI that represents the default URI supplied to the operation that produced it. (A discovered set therefore will never be empty.)
7. Any two discovery operations may result in disjoint discovered sets.
8. Any two discovery operations may result in identical discovered sets.
9. Discovery must permit multiple implementations (*e. g.* Eureka, Consul, DNS, Kubernetes, etc.).
10. The presence of two or more *enabled* Discovery implementations at runtime in an application is prohibited.
11. The order of a discovered set may or may not be significant, depending on the Discovery implementation. A discovered set's order is *stable*, however.
12. No guarantees exist, or can be inferred, about the health or availability of an application described by a discovered URI.
13. Discovery names should be composed only from legal DNS hostname characters, since DNS-based Discovery implementations should be possible. In practice, this means alphabetic characters, digits, "`.`", and "`-`".
14. There must be no restriction on the schemes or general format of URIs used or supplied by Discovery (`http`, `https`, `mailto`, `jdbc`, `amqp`, etc.).
15. Any malfunctioning of a Discovery implementation must not prevent an application from continuing.
16. Discovery must be *opt-in*, not *opt-out*. Specifically:
    1. Discovery may be used *explicitly*, *i.e.* by a developer issuing deliberate method calls to the Discovery API and then making use of the discovered set of discovered URIs she receives.
    2. Discovery may be used *implicitly*, *i.e.* by future facilities in Helidon that can apply Discovery automatically, but there must still be some developer-visible manifestation of Discovery in such a case so that its effects can be anticipated.
17. Discovery must not be used to find, or connect to, an implementation of Discovery.

## Use Cases

These use cases informed the design of Discovery. They are deliberately in a form that is technology agnostic.

### Basic, Explicit

1. A developer or subsystem asks Helidon for a Discovery implementation without necessarily knowing in advance what implementation will be supplied.
2. Helidon provides such a Discovery.
3. The developer or subsystem asks Discovery for a set (the *discovered set*) of metadata-bearing URIs (*discovered URIs*) corresponding to a *discovery name*.
    a. She also supplies a *default URI* to use in case there is an error, or in case there would be no suitable discovered set otherwise.
4. The Discovery implementation provides such a discovered set.
    a. At a minimum, the discovered set contains a representation of the default URI.
5. The developer or subsystem selects an element from the discovered set for use according to the application's requirements and heuristics.
6. The developer or subsystem uses the URI the selected discovered URI represents in subsequent development.

### Testing, Explicit

1. A developer wishes to test a part of her application that happens to use the Discovery API (a *Discovery-using component*).
    a. This Discovery API usage may not be, itself, under test.
2. She ensures that a Discovery implementation is present in her project's runtime (and hence test) dependencies.
3. She configures this Discovery implementation to be disabled at test time, possibly because the test environment does not supply required external resources.
    a. Note that this implies any Discovery implementation may be so configured.
4. She executes the test.
5. The Discovery-using component is, by definition, prepared for a Discovery implementation to be disabled.
    a. For example, depending on the final shape of the Discovery API, it might supply a default value at discovery time, or it might test the discovered set to see if it is empty before using a default value instead.
6. The Discovery-using component is prepared for this case and uses a default value instead (see (5a.)).
7. The test runs successfully, with "real" Discovery never actually being performed.
