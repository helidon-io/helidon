<p align="center">
    <img src="./etc/images/Primary_logo_blue.png">
</p>
<p align="center">
    <a href="https://github.com/heldon-io/helidon/tags">
        <img src="https://img.shields.io/github/tag/helidon-io/helidon.svg" alt="latest version">
    </a>
    <a href="https://github.com/helidon-io/helidon/issues">
        <img src="https://img.shields.io/github/issues/helidon-io/helidon.svg" alt="latest version">
    </a>
    <a href="https://twitter.com/intent/follow?screen_name=helidon_project">
        <img src="https://img.shields.io/twitter/follow/helidon_project.svg?style=social&logo=twitter" alt="follow on Twitter">
    </a>
</p>

# Helidon: Java Libraries for Microservices

Project Helidon is a set of Java Libraries for writing microservices.
Helidon supports two programming models:

* Helidon MP: [MicroProfile 6.0](https://github.com/eclipse/microprofile/releases/tag/6.0)
* Helidon SE: a small, functional style API

In either case your application is a Java SE program running on the
new Helidon Níma WebServer that has been written from the ground up to
use Java 21 Virtual Threads. With Helidon 4 you get the high throughput of a reactive server with the simplicity of thread-per-request style programming.

The Helidon SE API in Helidon 4 has changed significantly from Helidon 3. The use of virtual threads has enabled these APIs to change from asynchronous to blocking. This results in much simpler code that is easier to write, maintain, debug and understand. Earlier Helidon SE code will require modification to run on these new APIs. For more information see the [Helidon SE Upgrade Guide](https://helidon.io/docs/v4/#/se/guides/upgrade_4x).

Helidon 4 supports MicroProfile 6. This means your existing Helidon MP 3.x applications will run on Helidon 4 with only minor modifications. And since Helidon’s MicroProfile server is based on the new Níma WebServer you get all the benefits of running on virtual threads. For more information see the [Helidon MP Upgrade Guide](https://helidon.io/docs/v4/#/mp/guides/upgrade_4x).

New to Helidon? Then jump in and [get started](https://helidon.io/docs/v4/#/about/prerequisites).

Java 21 is required to use Helidon 4.


## License

Helidon is available under Apache License 2.0.

## Documentation

Latest documentation and javadocs are available at <https://helidon.io/docs/latest>.

Helidon White Paper is available [here](https://www.oracle.com/a/ocom/docs/technical-brief--helidon-report.pdf).

## Get Started

See Getting Started at <https://helidon.io>.

## Downloads / Accessing Binaries

There are no Helidon downloads. Just use our Maven releases (GroupID `io.helidon`).
See Getting Started at <https://helidon.io>. 

## Helidon CLI

macOS:
```bash
curl -O https://helidon.io/cli/latest/darwin/helidon
chmod +x ./helidon
sudo mv ./helidon /usr/local/bin/
```

Linux:
```bash
curl -O https://helidon.io/cli/latest/linux/helidon
chmod +x ./helidon
sudo mv ./helidon /usr/local/bin/
```

Windows:
```bat
PowerShell -Command Invoke-WebRequest -Uri "https://helidon.io/cli/latest/windows/helidon.exe" -OutFile "C:\Windows\system32\helidon.exe"
```

See this [document](HELIDON-CLI.md) for more info.

## Build

You need JDK 21 to build Helidon 4.

You also need Maven. We recommend 3.8.0 or newer.

**Full build**
```bash
$ mvn install
```

**Checkstyle**
```bash
# cd to the component you want to check
$ mvn validate  -Pcheckstyle
```

**Copyright**

```bash
# cd to the component you want to check
$ mvn validate  -Pcopyright
```

**Spotbugs**

```bash
# cd to the component you want to check
$ mvn verify  -Pspotbugs
```

**Documentatonn**

```bash
# At the root of the project
$ mvn site
```

**Build Scripts**

Build scripts are located in `etc/scripts`. These are primarily used by our pipeline,
but a couple are handy to use on your desktop to verify your changes. 

* `copyright.sh`: Run a full copyright check
* `checkstyle.sh`: Run a full style check

## Get Help

* See the [Helidon FAQ](https://github.com/oracle/helidon/wiki/FAQ)
* Ask questions on Stack Overflow using the [helidon tag](https://stackoverflow.com/tags/helidon)
* Join us on Slack: [#helidon-users](http://slack.helidon.io)

## Get Involved

* Learn how to [contribute](CONTRIBUTING.md)
* See [issues](https://github.com/oracle/helidon/issues) for issues you can help with

## Stay Informed

* Twitter: [@helidon_project](https://twitter.com/helidon_project)
* Blog: [Helidon on Medium](https://medium.com/helidon)
