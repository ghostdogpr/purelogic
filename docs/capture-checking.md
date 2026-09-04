# Capture checking

PureLogic supports Scala's experimental capture checking. When you enable it in your application, the compiler checks that a capability cannot escape the scope that provides it. For example, code cannot return a closure that keeps modifying a `State` after `State.apply` or `Logic.run` has finished.

PureLogic itself is compiled with capture checking enabled. You can use the library without enabling it in your application; the additional compiler checks are optional.

## Enabling capture checking

Add the following option to your application's `build.sbt`:

```scala
scalacOptions += "-language:experimental.captureChecking"
```

Capture checking is still an experimental Scala feature, so its syntax and diagnostics can change between compiler versions. See Scala's [capture checking documentation](https://docs.scala-lang.org/scala3/reference/experimental/capture-checking/index.html) for more detail.

## Keeping capabilities in scope

A `State` capability gives code access to the state of one computation. Returning a function that captures it would let callers keep changing that state after the computation has returned:

```scala
import purelogic.State

val (finalState, incrementLater) = State(0) {
  val state = summon[State[Int]]
  () => {
    state.update(_ + 1)
    state.get
  }
}
```

Without capture checking, this compiles: `finalState` is `0`, but calls to `incrementLater()` return `1`, then `2`. With capture checking enabled, the compiler rejects the closure because the captured capability outlives its scope.

Returning an ordinary value is allowed. A function can also capture a value read from the state, without retaining the capability itself:

```scala
val (finalState, readLater) = State(0) {
  val value = summon[State[Int]].get
  () => value
}

readLater() // 0
```

The same scope checks apply to `Reader`, `Writer`, `Abort`, and `EventSourcing`. Nested computations can still use capabilities supplied by an enclosing scope.

## Event-sourcing transitions

An [event-sourcing transition](event-sourcing.md#defining-a-transition) returns `(State[S], Abort[Err]) ?-> Unit`. The `?->` arrow denotes a pure context function: it can use the `State` and `Abort` parameters it receives, but cannot capture additional tracked capabilities such as an enclosing `Reader` or `Writer`.

Leave the result type of `Transition.run` inferred, as in the event-sourcing examples. If you write the type explicitly with capture checking enabled, use `?->` rather than `?=>`.

This restriction is not a proof of determinism. Calls to clocks, random-number generators, and other untracked effects still compile. Transitions must avoid those effects for replay to reproduce the same state.

## Separation checking

Scala's separate `-language:experimental.separationChecking` option is not supported by PureLogic yet. `State`, `Writer`, and `EventSourcing` are declared exclusive, but nested operations such as `recover` pass the same capability both as a context parameter and inside the body. Separation checking rejects this overlap.

Capture checking alone does not make `State` or `Writer` safe to share across threads. Keep each computation's mutable capabilities within that computation, as described in the [thread-safety FAQ](faq.md#is-purelogic-thread-safe).
