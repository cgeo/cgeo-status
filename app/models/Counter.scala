package models

class Counter(sampleSize: Int) {

  private[this] val times = new Array[Long](sampleSize)
  private[this] var lastIndex = -1
  private[this] var complete = false
  private[this] var usersCount: Option[Double] = None
  private[this] val ratioNew = (5.0 / sampleSize).max(0.01).min(0.20)
  private[this] val ratioOld = 1 - ratioNew

  def reset() = synchronized {
      lastIndex = -1
      complete = false
      usersCount = None
  }

  def count() = synchronized {
    complete |= lastIndex == sampleSize - 1
    lastIndex = (lastIndex + 1) % sampleSize
    val last = times(lastIndex)
    val now = System.currentTimeMillis
    times(lastIndex) = now
    if (complete) {
      val estimate = sampleSize * Counter.MILLISECONDS_BETWEEN_STATUS_UPDATE / (now - last).max(1)
      usersCount = Some(usersCount.getOrElse(0.0) * ratioOld + estimate * ratioNew)
    }
  }

  def users: Option[Long] = usersCount.map(_.round)

}

object Counter {

  final private val MILLISECONDS_BETWEEN_STATUS_UPDATE = 1800000

}
