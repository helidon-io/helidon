<p align="center">
    <img src="../etc/images/Primary_logo_blue.png" height="180">
</p>

# Helidon Examples

Welcome to the Helidon Examples! If this is your first experience with
Helidon we recommend you start with our
[quickstart](https://helidon.io/docs/latest/#/getting-started/02_base-example).
That will quickly get you going with your first Helidon application.

After that you can come back here and dig into the examples. To access
these examples we recommend checking out from a released tag. For example:

```
git clone git@github.com:oracle/helidon.git
cd helidon
git checkout tags/0.10.5
```

Our examples are Maven projects and can be built and run with
Java 8 or Java 11 -- so make sure you have those:

```
java -version
mvn -version
```

# Building an Example

Each example has a `README` that you will follow. To build most examples
just `cd` to the directory and run `mvn package`:

```
cd examples/microprofile/hello-world-explicit
mvn package
```
Usually you can then run the example using:

```
mvn exec:java
```

But always see the example's `README` for details.

# Examples

|Directory                     | Description |
|:------------------------------|:-------------|
| config | Helidon SE config examples |
| security | Helidon SE security examples |
| webserver | Helidon SE webserver examples |
| microprofile | Helidon MP examples |
| quickstarts | Quickstart examples. These are the same examples used by the Helidon quickstart archetypes |
| integrations | CDI extensions examples. |
| todo-app | A more complex example consisting of front end and back end services |

