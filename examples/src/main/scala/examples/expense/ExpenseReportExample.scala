package examples.expense

import purelogic.*

// --- Domain ---

case class Expense(description: String, amount: BigDecimal, category: String, hasReceipt: Boolean)
case class Policy(categoryLimits: Map[String, BigDecimal], receiptThreshold: BigDecimal, budget: BigDecimal)
case class ReportState(approved: Vector[Expense], rejected: Vector[(Expense, String)], totalApproved: BigDecimal)

enum AuditEntry {
  case Approved(description: String, amount: BigDecimal)
  case Rejected(description: String, reason: String)
}

enum ReportError {
  case OverBudget(requested: BigDecimal, remaining: BigDecimal)
}

type Program[A] = Logic[Policy, AuditEntry, ReportState, ReportError, A]

// --- Logic ---

/**
  * Processes a batch of expense claims against company policy.
  *
  * Individual policy violations (missing receipt, category limit) result in a rejection but processing continues.
  * Exceeding the total budget is a hard failure that stops the entire report.
  */
object ExpenseReportExample {

  private def approve(expense: Expense): Program[Unit] = {
    update(r => r.copy(approved = r.approved :+ expense, totalApproved = r.totalApproved + expense.amount))
    write(AuditEntry.Approved(expense.description, expense.amount))
  }

  private def reject(expense: Expense, reason: String): Program[Unit] = {
    update(r => r.copy(rejected = r.rejected :+ (expense, reason)))
    write(AuditEntry.Rejected(expense.description, reason))
  }

  def review(expense: Expense): Program[Unit] = {
    val policy = read

    if (expense.amount > policy.receiptThreshold && !expense.hasReceipt) {
      reject(expense, s"Receipt required for amounts over ${policy.receiptThreshold}")
    } else {
      val overLimit = policy.categoryLimits.get(expense.category).exists(_ < expense.amount)
      if (overLimit) {
        val limit = policy.categoryLimits(expense.category)
        reject(expense, s"Exceeds ${expense.category} limit of $limit")
      } else {
        val remaining = policy.budget - get(_.totalApproved)
        ensure(expense.amount <= remaining, ReportError.OverBudget(expense.amount, remaining))
        approve(expense)
      }
    }
  }

  def processAll(expenses: List[Expense]): Program[Unit] =
    expenses.foreach(review)

  @main
  def runExpenseReport(): Unit = {
    val policy = Policy(
      categoryLimits = Map("meals" -> BigDecimal(50), "travel" -> BigDecimal(500)),
      receiptThreshold = BigDecimal(25),
      budget = BigDecimal(1000)
    )

    val expenses = List(
      Expense("Team lunch", BigDecimal(45), "meals", hasReceipt = true),
      Expense("Taxi", BigDecimal(30), "travel", hasReceipt = false),
      Expense("Flight", BigDecimal(350), "travel", hasReceipt = true),
      Expense("Lavish dinner", BigDecimal(120), "meals", hasReceipt = true),
      Expense("Hotel", BigDecimal(200), "travel", hasReceipt = true),
      Expense("Conference", BigDecimal(500), "other", hasReceipt = true)
    )

    val initial         = ReportState(Vector.empty, Vector.empty, BigDecimal(0))
    val (audit, result) = Logic.run(initial, policy) {
      processAll(expenses)
    }

    println("=== Audit Trail ===")
    audit.foreach {
      case AuditEntry.Approved(desc, amount) => println(s"  Approved: $desc ($amount)")
      case AuditEntry.Rejected(desc, reason) => println(s"  Rejected: $desc - $reason")
    }
    println()

    result match {
      case Right((state, _))                                  =>
        println(s"Approved: ${state.approved.size} expenses, total: ${state.totalApproved}")
        println(s"Rejected: ${state.rejected.size} expenses")
      case Left(ReportError.OverBudget(requested, remaining)) =>
        println(s"Processing stopped: $requested requested but only $remaining remaining")
    }
  }
}
