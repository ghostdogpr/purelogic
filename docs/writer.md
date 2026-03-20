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

## Running

`Writer(body)` returns a tuple of the **accumulated values** and the result:

```scala
val (logs: Vector[W], result: A) = Writer {
  myProgram
}
```
