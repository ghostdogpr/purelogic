# Getting started

**PureLogic** is a **[Scala](https://www.scala-lang.org/) open source library** for writing **direct-style, pure business logic** using **context functions** (aka capabilities).

It is designed to be **monad-free**, meaning that effects like `Reader`, `Writer`, `State`, and `Abort` compose naturally through Scala 3's `given`/`using` mechanism instead of monad transformers or for-comprehensions.

It provides an **opinionated way to write pure domain logic** in Scala, using a limited set of primitives.

It is available for Scala 3.3.x LTS and later versions for Scala JVM, Scala.js, and Scala Native.
