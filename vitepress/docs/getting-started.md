# Getting started

**PureLogic** is a **[Scala](https://www.scala-lang.org/) open source library** for writing **direct-style, pure business logic** using **context functions** (aka capabilities).

It is designed to be **monad-free**, meaning that effects like `Reader`, `Writer`, `State`, and `Abort` compose naturally through Scala 3's `given`/`using` mechanism instead of monad transformers or for-comprehensions.

It provides an **opinionated way to write pure domain logic** in Scala, using a limited set of primitives.

It has **zero dependencies** and is available for Scala 3.3.x LTS and later versions for Scala JVM, Scala.js, and Scala Native.

## Installation

Add the following dependency to your `build.sbt` (use `%%` for Scala JVM, `%%%` for Scala.js and Scala Native):

```scala
libraryDependencies += "com.github.ghostdogpr" %% "purelogic" % "0.2.0"
```

For most of the library, you will only need a single import:

```scala
import purelogic.*
```

## A quick example

Let's jump right into some code:

```scala
import purelogic.*

case class Account(balance: Int)
case class Config(price: Int)

def buy(quantity: Int) =
  Logic.run(state = Account(50), reader = Config(10)) {
    val price   = read(_.price) * quantity
    val balance = get(_.balance)
    if (balance < price) fail("Insufficient balance")
    set(Account(balance - price))
    write("Purchase successful")
  }

println(buy(2))  // (Vector(Purchase successful),Right((Account(30),())))
println(buy(10)) // (Vector(),Left(Insufficient balance))
```

Let's break down what's happening here.
- We use `read` to access our `Config` object and extract the `price` field.
- We use `get` to access our `Account` object and extract the `balance` field.
- We use `fail` to abort the computation if the balance is insufficient.
- We use `set` to update the `Account` object with the new balance.
- We use `write` to log a "Purchase successful" message.

Using all these methods requires some `given` instances to be in scope. Those are provided automatically by the `Logic.run` function.

This function requires the starting `Account` and the `Config` to be provided, and returns a result of type `(Vector[String], Either[String, (Account, Unit)])`:
- `Vector[String]` is the list of values accumulated via `write`
- `Either[String, (Account, Unit)]` is the result of the computation: it either fails with an error of type `String` or succeeds with a tuple of the final `Account` and the return value `Unit`

The beauty of it? We **didn't need to pass any parameters around**. Let's now rewind and understand how it really works.

## How it works

Let's say you are writing pure domain logic (see [why you should keep your domain logic pure](why-purelogic.md)). You need to write a bunch of functions and in many of them, you need to access your `Config` object that contains some settings.

You could of course add a `Config` parameter to all of your functions and pass it around:
```scala
def doSomething(a: A, config: Config): Result = ???
def validateSomething(b: B, config: Config): Unit = ???
def updateSomething(c: C, config: Config): Result = ???
```

Let's say you now also need to modify some `User` object.
```scala
def doSomething(a: A, config: Config, user: User): (Result, User) = ???
def validateSomething(b: B, config: Config): Unit = ???
def updateSomething(c: C, config: Config, user: User): (Result, User) = ???
```
As you can see, you now need to pass the `User` object to all of your functions and return it as well. This is not very scalable and your code will become less and less readable.

This is where **PureLogic** comes in. Let's change the `Config` example:
```scala
def doSomething(a: A)(using Reader[Config]): Result = ???
```
Instead of an explicit `Config` parameter, we now have a `Reader` parameter that is **passed implicitly**. Anywhere inside `doSomething` (or any function that has a `Reader` parameter), we can now use the `read` function to access the `Config` object (as well as convenient helpers like `read(_.someFieldInsideConfig)`).

Starting with Scala 3, there is a different notation you can use for these parameters: **context functions**.
```scala
def doSomething(a: A): Reader[Config] ?=> Result = ???
```
That is slightly shorter, but a nice perk is that if you have a lot of functions with the same capabilities, you can create a type alias for them:
```scala
type Program[A] = Reader[Config] ?=> A
```
And then use it like this:
```scala
def doSomething(a: A): Program[Result] = ???
```
But we must provide the `Config` object at some point. We do this at the top level of the program by wrapping it with `Reader.apply`:
```scala
val result = 
  Reader(Config(10)) {
    doSomething(a)
  }
```
Wrapping the program with `Reader.apply` provides the given `Config` object to `doSomething` and that context will be passed implicitly to all functions inside it that have a `Reader` parameter.

Let's now add the `State` capability to our example:
```scala
def doSomething(a: A)(using Reader[Config], State[User]): Result = ???
// or
def doSomething(a: A): (Reader[Config], State[User]) ?=> Result = ???
// or with `type Program[A] = (Reader[Config], State[User]) ?=> A`
def doSomething(a: A): Program[Result] = ???
```
Now we can access the `User` object using the `get` function and update it using the `set` or `update` functions.

`doSomething` now also requires a `User` object to be provided and returned. This is done simply by wrapping the program with `State.apply`:
```scala
val (updatedUser, result) = 
  State(initialUser) {
    Reader(Config(10)) {
      doSomething(a)
    }
  }
```
Wrapping the program with `State.apply` provides the given `User` object to `doSomething` and that context will be passed implicitly to all functions inside it that have a `State` parameter. `State` also provides a `set` function that allows changing the state. The new state is returned after the computation.

But what was `Logic.run` in our first example? Simply a convenience function that wraps a program that contains the 4 basic capabilities: `Reader`, `Writer`, `State`, and `Abort`.
```scala
Logic.run(initialState, reader)(f)
// is equivalent to
Reader(reader)(Writer(Abort(State(initialState)(f))))
```

Note that by changing the order of the wrappers, you can change the order of the capabilities being applied, which affects the return type of the final computation.
- `Reader.apply` returns just the result `A`
- `Writer.apply` returns a tuple `(Vector[W], A)`
- `State.apply` returns a tuple `(S, A)`
- `Abort.apply` returns an `Either[E, A]`

Because of the order above, `Logic.run` returns a tuple `(Vector[W], Either[E, (S, A)])`. But you can change it, for example if you move the `Writer` inside the `Abort`, the `Vector[W]` will be inside the `Either`.

For more details about each of the capabilities, check out the [Capabilities](capabilities.md) page.

## Next steps

Make sure to read the [Why PureLogic?](why-purelogic.md) page to understand when this library is useful and see a comparison with traditional monadic approaches.

Check out the [examples](https://github.com/ghostdogpr/purelogic/tree/master/examples/src/main/scala/examples) in GitHub to see how to use the library in practice.

If you want to know more about direct-style effects using capabilities, I recommend these two blog posts:
- [Effects as Capabilities](https://nrinaudo.github.io/articles/capabilities.html) by Nicolas Rinaudo
- [The Effect Pattern and Effect Systems in Scala](https://rockthejvm.com/articles/the-effect-pattern) by Riccardo Cardin