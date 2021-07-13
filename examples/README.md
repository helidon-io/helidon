<p align="center">
    <img src="../etc/images/Primary_logo_blue.png" height="180">
</p>

# Helidon Examples

Welcome to the Helidon Examples! If this is your first experience with
Helidon we recommend you start with our
[quickstart](https://helidon.io/docs/v2/#/about/03_prerequisites)
That will quickly get you going with your first Helidon application.

After that you can come back here and dig into the examples. To access
these examples we recommend checking out from a released tag. For example:

```
git clone git@github.com:oracle/helidon.git
cd helidon
git checkout tags/2.0.0
```

Our examples are Maven projects and can be built and run with
Java 11 or newer -- so make sure you have those:

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

Usually the example will produce an application jar file that you can run:

```
java -jar target/example-name.jar
```

But always see the example's `README` for details.
