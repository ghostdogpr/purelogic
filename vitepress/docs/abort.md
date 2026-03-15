# Abort

`Abort[E]` lets you **short-circuit** a computation with an error of type `E`. Unlike throwing exceptions, the error type is tracked in the function signature and must be handled explicitly.

## Basic usage

```scala
import purelogic.*

def divide(a: Int, b: Int)(using Abort[String]): Int = {
  if (b == 0) fail("Division by zero")
  a / b
}

val result: Either[String, Int] = Abort {
  divide(10, 0)
}
// result: Left("Division by zero")
```

## Functions

### `fail`

**Aborts** the computation with an error:

```scala
fail("Something went wrong")
```

### `ensure`

Fails if a condition is **not met**:

```scala
ensure(age >= 18, "Must be an adult")
```

### `ensureNot`

Fails if a condition **is met**:

```scala
ensureNot(balance < 0, "Negative balance")
```

### `extractOption`

Extracts the value from an `Option`, or fails with the given error:

```scala
val user: User = extractOption(findUser(id), s"User $id not found")
```

### `extractEither`

Extracts the `Right` value from an `Either`, or fails with the `Left`:

```scala
val result: Int = extractEither(someEither)
```

### `extractTry`

Extracts the value from a `Try`, or fails with the `Throwable`. Requires `Abort[Throwable]`:

```scala
val result: Int = Abort.extractTry(scala.util.Try(someOperation()))
```

### `attempt`

Runs a block and catches any `Throwable`, converting it to an `Abort` failure. Requires `Abort[Throwable]`:

```scala
val result: Int = Abort.attempt {
  riskyOperation()
}
```

## Recovery

`Abort` provides several functions to **catch and handle errors** within a computation. Recovery **rolls back** state and writes to the point before the failed block.

### `recover`

Catches **all** errors and handles them with a function:

```scala
val result = Abort.recover {
  fail("oops")
  42
}(error => -1)
// result: -1
```

### `recoverKeepLog`

Like `recover`, but **keeps the writes** from the failed block instead of rolling them back.

### `recoverSome`

Catches only errors matched by a **partial function**. Unmatched errors are re-raised:

```scala
val result = Abort.recoverSome {
  fail("timeout")
  42
} {
  case "timeout" => 0
}
// "timeout" is recovered, any other error is propagated
```

### `recoverSomeKeepLog`

Like `recoverSome`, but **keeps the writes** from the failed block.

## Syntax extensions

Importing `purelogic.syntax.*` adds **`.orFail`** extension methods:

```scala
import purelogic.syntax.*

// Option[A] => A (or fail)
val user: User = findUser(id).orFail(s"User $id not found")

// Either[E, A] => A (or fail)
val result: Int = someEither.orFail

// Try[A] => A (or fail with Throwable)
val value: Int = someTry.orFail
```

## Running

`Abort(body)` returns an **`Either[E, A]`**:

```scala
val result: Either[E, A] = Abort {
  myProgram
}
```
