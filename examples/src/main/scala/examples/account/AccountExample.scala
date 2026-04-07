package examples.account

import purelogic.*

// --- Domain ---

case class Account(balance: Int)
case class Config(maxDeposit: Int, maxWithdrawal: Int)

enum AccountEvent {
  case Deposited(amount: Int)
  case Withdrawn(amount: Int)
}

type Program[A] = EventSourcingLogic[Config, AccountEvent, Account, String, A]

// --- Transition ---

given EventSourcing.Transition[AccountEvent, Account, String] with {
  def run(ev: AccountEvent): (State[Account], Abort[String]) ?=> Unit =
    ev match {
      case AccountEvent.Deposited(amount)  =>
        update(a => Account(a.balance + amount))
      case AccountEvent.Withdrawn(amount) =>
        ensure(get.balance >= amount, "Insufficient balance")
        update(a => Account(a.balance - amount))
    }
}

// --- Logic ---

/**
  * A bank account example demonstrating the event sourcing capability.
  *
  * State changes are only possible through events, and each event goes through a transition that validates and applies
  * the change. Events are recorded only if the transition succeeds.
  */
object AccountExample {

  def deposit(amount: Int): Program[Unit] = {
    ensure(amount <= read(_.maxDeposit), "Amount exceeds maximum deposit")
    writeEvent(AccountEvent.Deposited(amount))
  }

  def withdraw(amount: Int): Program[Unit] = {
    ensure(amount <= read(_.maxWithdrawal), "Amount exceeds maximum withdrawal")
    writeEvent(AccountEvent.Withdrawn(amount))
  }

  @main
  def runAccountExample(): Unit = {
    val config = Config(maxDeposit = 1000, maxWithdrawal = 500)

    // Run a series of operations
    val result = Logic.runEventSourcing(Account(100), config) {
      deposit(50)
      withdraw(30)
      deposit(200)
    }

    result match {
      case Right((events, account, _)) =>
        println("=== Events ===")
        events.foreach {
          case AccountEvent.Deposited(amount)  => println(s"  Deposited $amount")
          case AccountEvent.Withdrawn(amount) => println(s"  Withdrew $amount")
        }
        println(s"\nFinal balance: ${account.balance}")

      case Left(error) =>
        println(s"Failed: $error")
    }

    println()

    // Replay events to rebuild state, then continue
    val result2 = Logic.runEventSourcing(Account(0), config) {
      replayEvents(Vector(AccountEvent.Deposited(100), AccountEvent.Deposited(50)))
      println(s"Balance after replay: ${get.balance}")
      withdraw(30)
    }

    result2 match {
      case Right((events, account, _)) =>
        println(s"New events after replay: $events")
        println(s"Final balance: ${account.balance}")

      case Left(error) =>
        println(s"Failed: $error")
    }

    println()

    // Demonstrate a failed transaction
    val result3 = Logic.runEventSourcing(Account(100), config) {
      deposit(2000)
    }

    println(s"Over-limit deposit: $result3")
  }
}
