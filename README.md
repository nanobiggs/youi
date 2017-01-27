# youi

[![Build Status](https://travis-ci.org/outr/youi.svg?branch=master)](https://travis-ci.org/outr/youi)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/c0425ea823824cd7ab60659e8b9542dc)](https://www.codacy.com/app/matthicks/youi?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=outr/youi&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/c0425ea823824cd7ab60659e8b9542dc)](https://www.codacy.com/app/matthicks/youi?utm_source=github.com&utm_medium=referral&utm_content=outr/youi&utm_campaign=Badge_Coverage)
[![Stories in Ready](https://badge.waffle.io/outr/youi.png?label=ready&title=Ready)](https://waffle.io/outr/youi)
[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/outr/youi)
[![Maven Central](https://img.shields.io/maven-central/v/io.youi/youi-core_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/io.youi/youi-core_2.12)
[![Latest version](https://index.scala-lang.org/io.youi/youi/youi-core/latest.svg)](https://index.scala-lang.org/io.youi/youi/youi-core)

Next generation user interface and application development in Scala and Scala.js for web, mobile, and desktop.

## Status

At the moment we are currently migrating hyperscala (https://github.com/outr/hyperscala) and NextUI (https://github.com/outr/nextui)
into this new framework. If you need a production-ready framework please look at those for now.

## Modules

* [core](core) - core features generally useful for web and HTTP (Scala and Scala.js)
* [communicate](communicate) - communication framework to provide type-safe communication between a client / server (Scala and Scala.js)
* [dom](dom) - features and functionality related to working with the browser's DOM (Scala.js)
* [server](server) - base functionality for a web server (Scala)
* [server-undertow](serverUndertow) - implementation of [server](server) using [Undertow](http://undertow.io/) (Scala)
* [ui](ui) - functionality for user-interface creation and management (Scala.js)

## Features for 1.0.0 (In-Progress)

* [X] Complete SSL support (binding and proxying)
* [X] Ajax Request / Response framework
* [ ] User Interface framework for Scala.js (see NextUI)
* [ ] Client / Server Page and Screen support (see Hyperscala)
* [ ] Scala JVM and JS Content Modification Streams for any XML / HTML content (see Hyperscala)
* [ ] Scala class generation from existing HTML source supporting compile-time and source generation
* [ ] Existing HTML, CSS, and JavaScript optimization, compression, and obfuscation for production use
* [ ] Image optimizer for production use supporting compile-time and run-time optimization

## Features for 0.1.0 (Released 2016.12.22)

* [X] Scala JVM and JS support
* [X] URL implementation offering good parsing and flexibility
* [X] URL interpolation at compile-time
* [X] Server abstraction with HttpRequest and HttpResponse allowing for multiple implementations
* [X] HttpHandler prioritization and flow to allow handlers to build upon each other
* [X] IPv4 and IPv6 wrapper classes
* [X] TestServerImplementation for easy unit testing
* [X] Full Cookie support
* [X] Undertow implementation for Server
* [X] Wrapper for standard ContentTypes
* [X] WebSocket support through abstraction
* [X] Proxying support and optional ProxyingSupport trait to be mixed into Server
* [X] Session support
* [X] Communication implementation supporting client and server with JVM and JS support (see Hyperscala)
