# Capture checking

PureLogic supports Scala's experimental capture checking. When you enable it in your application, the compiler checks that a capability cannot escape the scope that provides it. A returned closure can retain a capability directly, while a lazy computation can retain one indirectly. The compiler rejects either form when it outlives the computation that provides the capability.

PureLogic itself is compiled with capture checking enabled. You can use the library without enabling it in your application; the additional compiler checks are optional.

## Enabling capture checking

Add the following option to your application's `build.sbt`:

```scala
scalacOptions += "-language:experimental.captureChecking"
```

Capture checking is still an experimental Scala feature, so its syntax and diagnostics can change between compiler versions. See Scala's [capture checking documentation](https://docs.scala-lang.org/scala3/reference/experimental/capture-checking/index.html) for more detail.

## Keeping capabilities in scope

A capability can escape through any returned value that retains it. Returning a closure that calls `State.update` is a direct example. The same leak can be harder to spot in a lazy collection.

Suppose `orders` is a `List[Order]` and `Order.toCsv` serializes one order. The following export appears to record an audit entry while generating each CSV row:

```scala
import purelogic.{Writer, write}

val (audit, csvRows) = Writer {
  orders.iterator.map { order =>
    write(s"exported ${order.id}")
    order.toCsv
  }
}
```

`Iterator.map` is lazy, so `Writer` returns an empty `audit` before the mapping runs. Consuming `csvRows` later writes to an internal log whose result has already been returned. Those entries never appear in `audit`.

With capture checking enabled, the compiler rejects this code because `csvRows` would carry the `Writer` capability outside its scope. Materialize the rows before the `Writer` block returns:

```scala
val (audit, csvRows) = Writer {
  orders.iterator.map { order =>
    write(s"exported ${order.id}")
    order.toCsv
  }.toVector
}
```

`Writer` is only one case. Capture checking also rejects returned closures and other values that retain a `Reader`, `State`, `Abort`, or `EventSourcing` capability. Nested computations can still use capabilities supplied by an enclosing scope.

## Event-sourcing transitions

An [event-sourcing transition](event-sourcing.md#defining-a-transition) returns `(State[S], Abort[Err]) ?-> Unit`. The `?->` arrow denotes a pure context function: it can use the `State` and `Abort` parameters it receives, but cannot capture additional tracked capabilities such as an enclosing `Reader` or `Writer`.

Leave the result type of `Transition.run` inferred, as in the event-sourcing examples. If you write the type explicitly with capture checking enabled, use `?->` rather than `?=>`.

This restriction is not a proof of determinism. Calls to clocks, random-number generators, and other untracked effects still compile. Transitions must avoid those effects for replay to reproduce the same state.

## Separation checking

Scala's separate `-language:experimental.separationChecking` option is not supported by PureLogic yet. `State`, `Writer`, and `EventSourcing` are declared exclusive, but nested operations such as `recover` pass the same capability both as a context parameter and inside the body. Separation checking rejects this overlap.

Capture checking alone does not make `State` or `Writer` safe to share across threads. Keep each computation's mutable capabilities within that computation, as described in the [thread-safety FAQ](faq.md#is-purelogic-thread-safe).
