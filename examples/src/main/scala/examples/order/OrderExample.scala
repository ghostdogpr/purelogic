package examples.order

import purelogic.*

// --- Domain ---

case class Pricing(discountPct: Int, maxItems: Int)
case class Env(pricing: Pricing, allowBackorder: Boolean)
case class Cart(items: Vector[String], total: BigDecimal)

enum Event {
  case ItemAdded(sku: String, price: BigDecimal)
  case DiscountApplied(percent: Int)
  case BackorderSkipped(sku: String)
}

enum Error {
  case InvalidSku(sku: String)
  case TooManyItems(max: Int)
  case OutOfStock(sku: String)
}

type Program[A] = Logic[Env, Event, Cart, Error, A]

// --- Logic ---

/**
  * A checkout workflow that adds items to a cart and applies a discount.
  *
  * Validates SKUs against a catalog, enforces item limits, and handles backorder logic based on environment config.
  */
object OrderExample {
  private val catalog: Map[String, BigDecimal] =
    Map("A" -> BigDecimal(10), "B" -> BigDecimal(5), "C" -> BigDecimal(2))

  private val inStock: Set[String] = Set("A", "C")

  def addItem(sku: String): Program[Unit] = {
    val env      = read
    val maxItems = env.pricing.maxItems
    val price    = extractOption(catalog.get(sku), Error.InvalidSku(sku))

    ensure(get(_.items.size) < maxItems, Error.TooManyItems(maxItems))

    if (!env.allowBackorder && !inStock.contains(sku)) {
      fail(Error.OutOfStock(sku))
    } else if (!inStock.contains(sku)) {
      write(Event.BackorderSkipped(sku))
    }

    update(cart => cart.copy(items = cart.items :+ sku, total = cart.total + price))
    write(Event.ItemAdded(sku, price))
  }

  def applyDiscount: Program[Unit] = {
    val pct = read(_.pricing.discountPct)
    if (pct > 0) {
      update(cart => cart.copy(total = cart.total - (cart.total * BigDecimal(pct) / BigDecimal(100))))
      write(Event.DiscountApplied(pct))
    }
  }

  def checkout: Program[Unit] = {
    addItem("A")
    addItem("C")
    applyDiscount
  }

  @main
  def runOrderExample(): Unit = {
    val env              = Env(Pricing(discountPct = 10, maxItems = 5), allowBackorder = true)
    val (events, result) = Logic.run(Cart(Vector.empty, BigDecimal(0)), env) {
      checkout
    }

    println("=== Events ===")
    events.foreach {
      case Event.ItemAdded(sku, price) => println(s"  Added $sku ($price)")
      case Event.DiscountApplied(pct)  => println(s"  Applied $pct% discount")
      case Event.BackorderSkipped(sku) => println(s"  Skipped backorder for $sku")
    }
    println()

    result match {
      case Right((cart, _)) =>
        println(s"Items: ${cart.items.mkString(", ")}")
        println(s"Total: ${cart.total}")
      case Left(error)      =>
        println(s"Checkout failed: $error")
    }
  }
}
