package models

sealed abstract class GCMembership(val name: String)

sealed abstract class GCKnownMembership(name: String) extends GCMembership(name)

object GCBasicMember extends GCKnownMembership("basic")
object GCPremiumMember extends GCKnownMembership(name = "premium")

object GCUnknownMembership extends GCMembership(name = "unknown")

object GCMembership {
  def parse(s: String): GCMembership = {
    s match {
      case "basic"   ⇒ GCBasicMember
      case "premium" ⇒ GCPremiumMember
      case _         ⇒ GCUnknownMembership
    }
  }

  val kinds = List(GCBasicMember, GCPremiumMember, GCUnknownMembership)
}

