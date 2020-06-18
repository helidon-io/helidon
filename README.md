<p align="center">
    <img src="./etc/images/Primary_logo_blue.png" height="180">
</p>
<p align="center">
    <a href="https://github.com/oracle/helidon/tags">
        <img src="https://img.shields.io/github/tag/oracle/helidon.svg" alt="latest version">
    </a>
    <a href="https://github.com/oracle/helidon/issues">
        <img src="https://img.shields.io/github/issues/oracle/helidon.svg" alt="latest version">
    </a>
    <a href="https://twitter.com/intent/follow?screen_name=helidon_project">
        <img src="https://img.shields.io/twitter/follow/helidon_project.svg?style=social&logo=twitter" alt="follow on Twitter">
    </a>
</p>

# Helidon: Java Libraries for Microservices

Project Helidon is a set of Java Libraries for writing microservices.
Helidon supports two programming models:

* Helidon MP: [MicroProfile](https://microprofile.io/) 3.2
* Helidon SE: a small, functional style API

In either case your application is just a Java SE program.

## License

Helidon is available under Apache License 2.0.

## Documentation

Latest documentation and javadocs are available at <https://helidon.io/docs/latest>.

## Get Started

See Getting Started at <https://helidon.io>.

## Downloads / Accessing Binaries

There are no Helidon downloads. Just use our Maven releases (GroupID `io.helidon`).
See Getting Started at <https://helidon.io>. 

## Build

You need JDK 11+ to build Helidon.

You also need Maven. We recommend 3.5 or newer.

Building the documentation requires the `dot` utility from Graphviz.
This is included in many Linux distributions. For other platforms
see <https://www.graphviz.org/>.

**Full build**
```bash
$ mvn install
```

**Checkstyle**
```bash
# Cd to the component you want to check
$ mvn validate  -Pcheckstyle
```

**Copyright**

```bash
# Cd to the component you want to check
$ mvn validate  -Pcopyright
```

**Spotbugs**

```bash
# Cd to the component you want to check
$ mvn verify  -Pspotbugs
```

**Build Scripts**

Build scripts are located in `etc/scripts`. These are primarily used by our pipeline,
but a couple are handy to use on your desktop to verify your changes. 

* `copyright.sh`: Run a full copyright check
* `checkstyle.sh`: Run a full style check

## Get Help

* See the [Helidon FAQ](https://github.com/oracle/helidon/wiki/FAQ)
* Ask questions on Stack Overflow using the [helidon tag](https://stackoverflow.com/tags/helidon)
* Join us on Slack: [#helidon-users](https://join.slack.com/t/helidon/shared_invite/enQtNDM1NjU3MjkyNDg2LWNiNGIzOGFhZDdjNzAyM2Y2MzlmMDI4NWY4YjE1OWQ2OTdkYTZkN2FlNDcxNmUyZmZmMTZhZmZhNWI2ZTI1NGI)

## Get Involved

* Learn how to [contribute](CONTRIBUTING.md)
* See [issues](https://github.com/oracle/helidon/issues) for issues you can help with

## Stay Informed

* Twitter: [@helidon_project](https://twitter.com/helidon_project)
* Blog: [Helidon on Medium](https://medium.com/helidon)
