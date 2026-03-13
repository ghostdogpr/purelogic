# Getting started

**PureLogic** is a **[Scala](https://www.scala-lang.org/) open source library** for writing **direct-style, pure business logic** using **context functions** (aka capabilities).

It is designed to be **monad-free**, meaning that effects like `Reader`, `Writer`, `State`, and `Abort` compose naturally through Scala 3's `given`/`using` mechanism instead of monad transformers or for-comprehensions.

It provides an **opinionated way to write pure domain logic** in Scala, using a limited set of primitives.

It is available for Scala 3.3.x LTS and later versions for Scala JVM, Scala.js, and Scala Native.

### Installation

Add the following dependency to your `build.sbt` (use `%%` for Scala JVM, `%%%` for Scala.js and Scala Native):

```scala
libraryDependencies += "com.github.ghostdogpr" %% "purelogic" % "0.1.0"
```

## The 4 basic effects

**PureLogic** provides 4 basic effects that can be used to compose your pure domain logic:

- `Reader[R]`: read a value of type `R` using the `read` function
- `Writer[W]`: accumulate values of type `W` using the `write` function
- `State[S]`: read and write a value of type `S` using the `get` and `set` functions
- `Abort[E]`: abort the computation with an error of type `E` using the `fail` function

You can then run a computation that uses these effects using the `Logic.run` function.

Let's look at quick example:

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
- We use `write` to log the purchase successful message.

Using these methods requires some `given` instances to be in scope. Those are provided automatically by the `Logic.run` function.

This function requires the starting state and the reader to be provided, and returns a result of type `(Vector[W], Either[E, (S, A)])`:
- `Vector[W]` is the list of values accumulated via `write`
- `Either[E, (S, A)]` is the result of the computation: it either fails with an error of type `E` or succeeds with a tuple of the final state `S` and the return value `A`