package purelogic.bench

import java.util.concurrent.TimeUnit

import cats.MonadError
import cats.data.{Chain, ReaderWriterStateT}
import cats.mtl.{Ask, Stateful, Tell}
import cats.syntax.all.*
import kyo.{Scope as _, *}
import org.openjdk.jmh.annotations.*
import turbolift.!!
import turbolift.Extensions.*
import turbolift.effects.{ErrorEffectExt, ReaderEffect, StateEffect, WriterEffectExt}
import turbolift.typeclass.{AccumZero, One}
import zio.ZEnvironment
import zio.prelude.fx.ZPure

import purelogic.{fail, get, read, set, write, Logic}

@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class RwstBench {
  @Param(Array("1000", "10000"))
  var size: Int = 0

  private val env: Int          = 7
  private val initialState: Int = 0
  private val boom              = new RuntimeException("boom")

  private def pureLogicProgram(n: Int): Logic[Int, Int, Int, Throwable, Int] = {
    (0 until n).foreach { _ =>
      val r    = read
      val s    = get
      val add  = 1
      val next = s + r + add
      set(next)
      write(next)
      if (false) fail(boom)
    }
    get
  }

  @Benchmark
  def purelogic(): Int = {
    val (_, result) = Logic.run(initialState, env)(pureLogicProgram(size))
    result match {
      case Right((state, _)) => state
      case Left(_)           => -1
    }
  }

  private def zPureProgram(n: Int): ZPure[Int, Int, Int, Int, Throwable, Int] =
    ZPure
      .foreachDiscard(0 until n) { _ =>
        for r <- ZPure.service[Int, Int]
        s     <- ZPure.get[Int]
        add    = 1
        next   = s + r + add
        _     <- ZPure.set(next)
        _     <- ZPure.log(next)
        _     <- if (false) ZPure.fail(boom) else ZPure.unit
        yield ()
      }
      .flatMap(_ => ZPure.get[Int])

  @Benchmark
  def zpure(): Int = {
    val (_, result) = zPureProgram(size).provideEnvironment(ZEnvironment(env)).runAll(initialState)
    result match {
      case Right((state, _)) => state
      case Left(_)           => -1
    }
  }

  private type CatsRwst[A] =
    ReaderWriterStateT[Either[Throwable, *], Int, Chain[Int], Int, A]

  private def catsRwstProgram(n: Int): CatsRwst[Int] = {
    val step: CatsRwst[Unit] =
      for {
        r   <- ReaderWriterStateT.ask: CatsRwst[Int]
        s   <- ReaderWriterStateT.get: CatsRwst[Int]
        add  = 1
        next = s + r + add
        _   <- ReaderWriterStateT.set(next): CatsRwst[Unit]
        _   <- ReaderWriterStateT.tell(Chain.one(next)): CatsRwst[Unit]
        _   <- if (false) ReaderWriterStateT.liftF(Left(boom)): CatsRwst[Unit]
               else ReaderWriterStateT.pure(()): CatsRwst[Unit]
      } yield ()

    List.range(0, n).traverse_(_ => step).flatMap(_ => ReaderWriterStateT.get)
  }

  @Benchmark
  def catsRwst(): Int = {
    val result = catsRwstProgram(size).run(env, initialState)
    result match {
      case Right((_, state, _)) => state
      case Left(_)              => -1
    }
  }

  private def catsMtlProgram[F[_]](n: Int)(using Ask[F, Int], Tell[F, Chain[Int]], Stateful[F, Int], MonadError[F, Throwable]): F[Int] = {
    val step: F[Unit] =
      for {
        r   <- Ask[F, Int].ask
        s   <- Stateful[F, Int].get
        add  = 1
        next = s + r + add
        _   <- Stateful[F, Int].set(next)
        _   <- Tell[F, Chain[Int]].tell(Chain.one(next))
        _   <- if (false) MonadError[F, Throwable].raiseError(boom)
               else MonadError[F, Throwable].unit
      } yield ()

    List.range(0, n).traverse_(_ => step).flatMap(_ => Stateful[F, Int].get)
  }

  @Benchmark
  def catsMtl(): Int = {
    val result = catsMtlProgram[CatsRwst](size).run(env, initialState)
    result match {
      case Right((_, state, _)) => state
      case Left(_)              => -1
    }
  }

  private def kyoProgram(n: Int): Int < (Env[Int] & Var[Int] & Emit[Int] & Abort[Throwable]) = {
    val loop = Loop.repeat(n)(
      for {
        r   <- Env.get[Int]
        s   <- Var.get[Int]
        add  = 1
        next = s + r + add
        _   <- Var.set(next)
        _   <- Emit.value(next)
        _   <- Abort.get(if (false) Left(boom) else Right(()))
      } yield ()
    )
    loop.flatMap(_ => Var.get[Int])
  }

  @Benchmark
  def kyo(): Int = {
    val handled = Abort.run(Emit.run(Var.run(initialState)(Env.run(env)(kyoProgram(size))))).eval
    handled match {
      case Result.Success((_, state)) => state
      case _                          => -1
    }
  }

  private object TL {
    case object R extends ReaderEffect[Int]
    case object S extends StateEffect[Int]
    case object W extends WriterEffectExt[Vector[Int], Int]
    case object E extends ErrorEffectExt[Throwable, Throwable]
  }

  private def turboliftProgram(n: Int) = {
    val loop = (0 until n).foreachEff { _ =>
      for {
        r   <- TL.R.ask
        s   <- TL.S.get
        add  = 1
        next = s + r + add
        _   <- TL.S.put(next)
        _   <- TL.W.tell(next)
        _   <- if (false) TL.E.raise(boom) else !!.unit
      } yield ()
    }
    loop.flatMap(_ => TL.S.get)
  }

  private given AccumZero[Vector[Int], Int] = AccumZero.forVector[Int]
  private given One[Throwable, Throwable]   = One.instance((t: Throwable) => t)

  @Benchmark
  def turbolift(): Int = {
    val result =
      turboliftProgram(size)
        .handleWith(TL.W.handlers.local)
        .handleWith(TL.S.handlers.local(initialState))
        .handleWith(TL.R.handlers.default(env))
        .handleWith(TL.E.handlers.first)
        .run
    result match {
      case Right((_, state)) => state
      case Left(_)           => -1
    }
  }
}
