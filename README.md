repetoir
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.repetoir/com.io7m.repetoir.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.repetoir%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/com.io7m.repetoir/com.io7m.repetoir?server=https%3A%2F%2Fs01.oss.sonatype.org&style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/io7m/repetoir/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m-com/repetoir.svg?style=flat-square)](https://codecov.io/gh/io7m-com/repetoir)
![Java Version](https://img.shields.io/badge/21-java?label=java&color=e6c35c)

![com.io7m.repetoir](./src/site/resources/repetoir.jpg?raw=true)

| JVM | Platform | Status |
|-----|----------|--------|
| OpenJDK (Temurin) Current | Linux | [![Build (OpenJDK (Temurin) Current, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/repetoir/main.linux.temurin.current.yml)](https://www.github.com/io7m-com/repetoir/actions?query=workflow%3Amain.linux.temurin.current)|
| OpenJDK (Temurin) LTS | Linux | [![Build (OpenJDK (Temurin) LTS, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/repetoir/main.linux.temurin.lts.yml)](https://www.github.com/io7m-com/repetoir/actions?query=workflow%3Amain.linux.temurin.lts)|
| OpenJDK (Temurin) Current | Windows | [![Build (OpenJDK (Temurin) Current, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/repetoir/main.windows.temurin.current.yml)](https://www.github.com/io7m-com/repetoir/actions?query=workflow%3Amain.windows.temurin.current)|
| OpenJDK (Temurin) LTS | Windows | [![Build (OpenJDK (Temurin) LTS, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/repetoir/main.windows.temurin.lts.yml)](https://www.github.com/io7m-com/repetoir/actions?query=workflow%3Amain.windows.temurin.lts)|

## repetoir

A minimalist application service directory.

### Features

  * Register and deregister application services.
  * Subscribe to service events to be notified of changes.
  * Written in pure Java 21.
  * [OSGi](https://www.osgi.org/) ready.
  * [JPMS](https://en.wikipedia.org/wiki/Java_Platform_Module_System) ready.
  * ISC license.
  * High-coverage automated test suite.

### Motivation

Most applications end up using some kind of _dependency injection_. That is,
a given class in an application is not responsible for instantiating the
other classes on which it depends, but it instead has instances of those
classes injected into its constructor. Many systems provide basically
incomprehensible and error-prone annotation-based approaches that can be
considered as _push-based_; a class has its own private fields reflectively
assigned by an external system.

The `repetoir` system provides a strongly-typed, easily-understood _service
directory_ for _pull-based_ dependency injection. An application creates
a service directory (a plain Java object) on startup, manually instantiates
and registers services into it, and then passes that service directory
around the application. Parts of the application that require services
explicitly _pull_ those services from the service directory.

There is never any confusion, as is common with annotation and push-based
dependency injection systems, where and how services are being instantiated;
there is exactly one place in the code that creates services, and the
instantiations are explicit and clearly visible.

Additionally, because the system is represented by a single, very simple
interface type, there is no need to involve complicated mocking extensions
in unit tests in order to get services correctly injected into all of the
classes involves; simply create a service directory directly in the unit tests,
publish the required services to it, and pass that directory into the classes
under test.

### Building

```
$ mvn clean verify
```

### Usage

Create a service directory:

```
var directory = new RPServiceDirectory();
```

Register services:

```
interface ExampleServiceType extends RPServiceType { }
class ExampleServiceService implements ExampleServiceType { }

directory.register(ExampleServiceType.class, new ExampleServiceService());
```

Later, fetch required services:

```
ExampleServiceType service =
  directory.requireService(ExampleServiceType.class);
```

Fetch optional services:

```
interface ExampleOptionalServiceType extends RPServiceType { }

Optional<ExampleOptionalServiceType> service =
  directory.optionalService(ExampleOptionalServiceType.class);
```

It is a requirement that services implement the trivial `RPServiceType`
interface in order to be registered in a directory. Services that also
implement `AutoCloseable` will be closed when the service directory is
closed.

