package examples.account

import purelogic.*

// --- Domain ---

case class Account(balance: Int)
case class Config(maxDeposit: Int, maxWithdrawal: Int)

enum AccountEvent {
  case Deposit(amount: Int)
  case Withdraw(amount: Int)
}

type Program[A] = EventSourcingLogic[Config, AccountEvent, Account, String, A]

// --- Transition ---

given EventSourcing.Transition[AccountEvent, Account, String] with {
  def run(ev: AccountEvent) =
    ev match {
      case AccountEvent.Deposit(amount)  =>
        update(a => Account(a.balance + amount))
      case AccountEvent.Withdraw(amount) =>
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
    writeEvent(AccountEvent.Deposit(amount))
  }

  def withdraw(amount: Int): Program[Unit] = {
    ensure(amount <= read(_.maxWithdrawal), "Amount exceeds maximum withdrawal")
    writeEvent(AccountEvent.Withdraw(amount))
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
          case AccountEvent.Deposit(amount)  => println(s"  Deposited $amount")
          case AccountEvent.Withdraw(amount) => println(s"  Withdrew $amount")
        }
        println(s"\nFinal balance: ${account.balance}")

      case Left(error) =>
        println(s"Failed: $error")
    }

    println()

    // Replay events to rebuild state, then continue
    val result2 = Logic.runEventSourcing(Account(0), config) {
      replayEvents(Vector(AccountEvent.Deposit(100), AccountEvent.Deposit(50)))
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
