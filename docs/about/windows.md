# Helidon on Windows

Generally, the Helidon documentation assumes that you’re using a Unix-like operating system such as Linux or macOS. If you want to use Helidon on Windows, then review the following document for tips on the differences that you should be aware of.

## System Requirements

If you plan to develop Helidon projects and applications on Windows, make sure your environment meets the following requirements:

- All of the requirements listed in [Getting Started](prerequisites.md)
- *If using the [Helidon CLI](cli.md) (which is recommended)*, Visual C+\\ Redistributable Runtime. Download at [Microsoft Visual C++ Redistributable latest supported downloads](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170#visual-c-redistributable-v14)

The following are recommended but not required:

- curl
- PowerShell (*not* Windows PowerShell)
- Windows Terminal

> [!TIP]
> If you do not have `curl` installed, in PowerShell, you can use `Invoke-WebRequest` instead.
