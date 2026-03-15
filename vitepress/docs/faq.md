# FAQ

## Is PureLogic thread-safe?

`State` and `Writer` use **mutable variables** internally, scoped to the `Logic.run` (or `State.apply` / `Writer.apply`) call. They are **not designed to be shared across threads**. This is fine because PureLogic is meant for pure domain logic, which is typically single-threaded. If you need concurrency, use an effect system like ZIO or Cats Effect at the boundary and call into PureLogic from there.


## How does `Abort` work under the hood?

`Abort` uses Scala 3's `boundary`/`break` mechanism, which compiles down to a **local throw/catch**. This means aborting is very fast — there is no `Either` wrapping at each step like in monadic approaches. The `Either` is only constructed once, at the `Abort.apply` boundary.

## Can I use multiple `State` or `Reader` with different types?

Yes. You can have as many capabilities as you want, as long as their **type parameters differ**:

```scala
def program(using State[Account], State[Cart], Reader[Config]): Unit = {
  val balance = get[Account](_.balance)
  val items   = get[Cart](_.items)
  // ...
}
```

If you have two capabilities with the **same type** (e.g. two `State[Int]`), you need to name them to disambiguate (see [Capabilities](capabilities.md#_3-named-capability-instances)).

## What happens to `Writer` logs when `Abort` fails?

It depends on the **nesting order**. With the default `Logic.run` order, `Writer` is outside `Abort`, so logs accumulated **before** the failure are preserved in the result. Logs accumulated in a `recover` block are **rolled back** by default (use `recoverKeepLog` to keep them).

## Is `State` really pure?

Yes. While `State` uses **mutable variables internally**, this mutation is completely **scoped** within the `State.apply` or `Logic.run` call. From the outside, the function is pure: same inputs always produce same outputs, with no observable side effects. This is the same approach used by Haskell's `ST` monad.

## What about capture checking?

PureLogic's design based on **context functions and capabilities** is a natural fit for Scala's upcoming **capture checking** feature. Capture checking will allow the compiler to verify that capabilities don't escape their scope — for example, ensuring that a `State` reference isn't leaked outside of `Logic.run`.

PureLogic is **ready** for capture checking and it will be added when capture checking becomes more stable in future versions of Scala.
