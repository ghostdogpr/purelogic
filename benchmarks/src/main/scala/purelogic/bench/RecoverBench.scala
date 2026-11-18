package purelogic.bench

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.*

import purelogic.{fail, get, recover, recoverKeepLog, update, write, Logic}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class RecoverBench {
  @Param(Array("10000", "30000"))
  var events: Int = 0

  @Param(Array("5"))
  var recoversPerEvent: Int = 0

  private def run(program: Logic[Unit, Int, Int, String, Int]): Int = {
    val (log, result) = Logic.run(0, ())(program)
    result match {
      case Right((state, _)) => state + log.length
      case Left(_)           => -1
    }
  }

  private def handlersSucceed(events: Int, recoversPerEvent: Int): Logic[Unit, Int, Int, String, Int] = {
    var i = 0
    while (i < events) {
      write(i)
      var j = 0
      while (j < recoversPerEvent) {
        recover[Int, Int, String, Unit](update[Int](_ + 1))(_ => ())
        j += 1
      }
      i += 1
    }
    get
  }

  private def handlersFail(events: Int, recoversPerEvent: Int): Logic[Unit, Int, Int, String, Int] = {
    var i = 0
    while (i < events) {
      write(i)
      var j = 0
      while (j < recoversPerEvent) {
        recover[Int, Int, String, Unit] {
          write(-i)
          fail("boom")
        }(_ => ())
        j += 1
      }
      i += 1
    }
    get
  }

  private def handlersSucceedKeepLog(events: Int, recoversPerEvent: Int): Logic[Unit, Int, Int, String, Int] = {
    var i = 0
    while (i < events) {
      write(i)
      var j = 0
      while (j < recoversPerEvent) {
        recoverKeepLog[Int, Int, String, Unit](update[Int](_ + 1))(_ => ())
        j += 1
      }
      i += 1
    }
    get
  }

  @Benchmark
  def recoverSuccess(): Int = run(handlersSucceed(events, recoversPerEvent))

  @Benchmark
  def recoverFailure(): Int = run(handlersFail(events, recoversPerEvent))

  @Benchmark
  def recoverKeepLogSuccess(): Int = run(handlersSucceedKeepLog(events, recoversPerEvent))
}
