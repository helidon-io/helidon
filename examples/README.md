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

If you edit a `README.adoc` file for an example or guide, please [see below](#for-guide-editors).

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

# For guide editors

## Special handling for `README.adoc`
At this writing GitHub does not support AsciiDoc includes. In order for the guides 
to render properly on GitHub, we preprocess the guide `README.adoc` files to 
pull in the included text during the build and we store this `preprocessed` format
in the repository.

If you edit a `README.adoc` file for an example or a guide you need to do two
additional steps.

### Run `mvn sitegen:naturalize-adoc` before editing `README.adoc`
The preprocessed format is awkward for iteratively editing and previewing
the `README.adoc` file and the files it includes. Run 
`mvn sitegen:naturalize-adoc` to convert the preprocessed format to _natural_
format using conventional AsciiDoc `include::` directives. 

Edit this file and any files it includes normally. You can add, remove, or
modify the `include::` directives as normal, and for example
AsciiDoc viewers can display the file as you edit it. You can also build the Helidon
doc site locally and run a lightweight
webserver on your system to display the Helidon web site and view the guides.

### Run `mvn sitegen:preprocess-adoc` before adding, committing, and pushing
If you edit the `README.adoc` file -- or any of the files that it directly or
indirectly includes -- be sure to run `mvn sitegen:preprocess-adoc` when you
have finished making changes to the content. This brings the `README.adoc` file
up-to-date, pulling in the content in the current version of the
included files and converting the file into the preprocessed format.

After you run the plug-in be sure to `git add` the `README.adoc` file so it is
part of your `git commit`. 

### Pipline build verification of `README.adoc`
Each pipeline build runs `mvn sitegen::preprocess-adoc` again and compares
that output with the content of `README.adoc` and fails if they differ. 
This makes sure that you have preprocessed and committed `README.adoc` if you 
change it or any of the included content.