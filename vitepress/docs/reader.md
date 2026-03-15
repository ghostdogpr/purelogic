# Reader

`Reader[R]` provides **read-only access** to a value of type `R`. This is typically used for configuration, environment, or any context that your logic needs to read but never modify.

## Basic usage

```scala
import purelogic.*

case class Config(maxRetries: Int, timeout: Int)

def process(using Reader[Config]): String = {
  val retries = read(_.maxRetries)
  s"Will retry $retries times"
}

val result = Reader(Config(maxRetries = 3, timeout = 5000)) {
  process
}
// result: "Will retry 3 times"
```

## Functions

### `read`

Returns the current reader value:

```scala
val config: Config = read
```

You can also pass a **projection function** to extract a field directly:

```scala
val retries: Int = read(_.maxRetries)
```

### `local`

Runs a block with a **modified** reader value. The original value is restored after the block completes:

```scala
def process(using Reader[Config]): String = {
  val outer = read(_.maxRetries) // 3
  val inner = local(_.copy(maxRetries = 10)) {
    read(_.maxRetries) // 10
  }
  val after = read(_.maxRetries) // 3
  s"$outer, $inner, $after"
}
```

### `focus`

Runs a block with a **narrowed** reader. This is useful when calling a function that only needs part of your reader:

```scala
def retryLogic(using Reader[Int]): Unit = {
  val maxRetries = read
  // ...
}

def process(using Reader[Config]): Unit = {
  focus(_.maxRetries) {
    retryLogic // only sees an Int, not the full Config
  }
}
```

## Running

`Reader(value)(body)` provides the value and returns the **result directly** (no wrapping):

```scala
val result: A = Reader(myConfig) {
  myProgram
}
```
