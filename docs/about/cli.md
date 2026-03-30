# Helidon CLI

The Helidon command-line interface (CLI) helps you create and manage Helidon projects. You can use it to generate a new project, get project details, and build your application.

The Helidon CLI also includes a development loop feature that automatically recompiles and restarts your application, so you can iterate quickly while you edit your code.

## Install the Helidon CLI

The Helidon CLI is a standalone executable for Linux, macOS, and Windows systems. Download the executable and install it in a location that is accessible from your PATH.

### Prerequisites

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

Make sure `java` and `mvn` are in your PATH:

``` bash
java -version
mvn --version
```

### Install the Helidon CLI on Linux

1.  Run the following commands to download the Helidon CLI binary, make it executable, and then move it to a location on your PATH:

    ``` bash
    curl -L -O https://helidon.io/cli/latest/linux/helidon
    chmod +x ./helidon
    sudo mv ./helidon /usr/local/bin/
    ```

2.  Confirm the installation was successful by running `helidon version`.

### Install the Helidon CLI on macOS

1.  Run the following commands to download the Helidon CLI binary, make it executable, and then move it to a location on your PATH:

    ``` bash
    curl -L -O https://helidon.io/cli/latest/darwin/helidon
    chmod +x ./helidon
    sudo mv ./helidon /usr/local/bin/
    ```

2.  Confirm the installation was successful by running `helidon version`.

If you get a "the developer cannot be verified" warning when you run the Helidon CLI, it is because the Helidon CLI has not been signed and notarized yet. You can disable this check by running: `xattr -d com.apple.quarantine helidon`, which removes the quarantine attribute on the file.

### Install the Helidon CLI on Windows

1.  Install PowerShell and Visual C++ Redistributable Runtime. See [Helidon on Windows](windows.md).
2.  Run the following command to download and install the Helidon CLI on Windows systems:

    ``` powershell
    PowerShell -Command Invoke-WebRequest -Uri "https://helidon.io/cli/latest/windows/helidon.exe" -OutFile "C:\Windows\system32\helidon.exe"
    ```

3.  Confirm the installation was successful by running `helidon version`.

## Usage

After you install the Helidon CLI, you can use it to manage your Helidon projects. Some examples are provided below. You can also run `helidon help` to get a list of the available commands.

### Create a New Project

You can use the Helidon CLI to quickly create a new Helidon project.

1.  Open a command-line interface and navigate to the directory where you want to create the project.
2.  Run `helidon init`.
3.  Choose a **Helidon Version**.
4.  Choose a **Helidon Flavor**: SE or MP.
5.  Choose an **Application Type**. If you chose Helidon SE, the OCI option is not available.
6.  Select a JSON library. If you chose Helidon MP, the JSON-P option is not available.
7.  Customize your project by specifying a `groupId`, an `artifactId`, a `version`, and a `package name`.

The Helidon CLI will create a new project folder in your current directory.

### Enable Development Loop

You can use the Helidon CLI development loop feature to test changes to your application as you make them. When the development loop is active, the Helidon CLI will automatically recompile and restart your application so you can see the effects of your changes immediately.

1.  Navigate to the Helidon project’s home directory.
2.  Run `helidon dev` to enable the development loop.
3.  In another terminal window or an IDE, make and save changes to your application’s source code.

The build runs after each change. If you make an invalid change, the build fails until the error is fixed.

To stop the development loop, enter `Ctrl+C`.

## Helidon CLI Demo

Watch the following demo to see some of the functionality of the Helidon CLI.

<figure>
<img src="../images/cli/Helidon_cli.gif" alt="CLI Demo" />
</figure>
