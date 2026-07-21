# Writer

`Writer[W]` lets you **accumulate values** of type `W` during a computation. This is useful for collecting events, audit logs, diagnostics, or any output that builds up as your logic runs.

## Basic usage

```scala
import purelogic.*

def process(using Writer[String]): Int = {
  write("Starting")
  write("Processing")
  42
}

val (logs, result) = Writer {
  process
}
// logs: Vector("Starting", "Processing")
// result: 42
```

## Functions

### `write`

Appends a **single value** to the log:

```scala
write("Order validated")
```

### `writeAll`

Appends **multiple values** at once:

```scala
writeAll(List("Step 1", "Step 2", "Step 3"))
```

### `clear`

**Clears** all accumulated values:

```scala
write("this will be gone")
clear
write("fresh start")
// only "fresh start" will be in the log
```

### `capture`

Runs a block in a **nested scope**, returning both the captured writes and the result. The captured writes are also **forwarded** to the outer writer:

```scala
def process(using Writer[String]): Unit = {
  write("before")
  val (captured, result) = capture {
    write("inside")
    42
  }
  write("after")
  // captured: Vector("inside")
  // outer log: Vector("before", "inside", "after")
}
```

### `snapshot` / `rollback`

`snapshot` captures the current contents of the log, and `rollback` restores it to a captured snapshot, discarding any writes made since. Values accumulated **before** the snapshot are preserved, so this is a targeted reset rather than a full `clear`.

```scala
def process(using Writer[String]): Unit = {
  write("before")
  val snap = snapshot
  write("inside")
  rollback(snap)
  // log: Vector("before")
}
```

These are the low-level primitives that [`recover`](abort.md) builds on. Reach for them directly when you need custom recovery semantics that the built-in `recover` family does not cover. A common example is a lookahead that should leave no trace on success but keep its writes when a committed failure escapes:

```scala
def isSuccessful(block: Abort[String] ?=> Unit)(using Writer[String], State[Unit], Abort[String]): Boolean = {
  val snap   = snapshot
  val result = recoverSome {
    block
    true
  } { case "recoverable" => false }
  rollback(snap) // only reached on success or a handled failure
  result
}
```

If the block succeeds or fails recoverably, its writes are rolled back. If it fails with a committed error, the failure escapes before `rollback` runs, so those writes stay in the log for the outer scope to observe.

## Running

`Writer(body)` returns a tuple of the **accumulated values** and the result:

```scala
val (logs: Vector[W], result: A) = Writer {
  myProgram
}
```
