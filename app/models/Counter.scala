package models

class Counter(sampleSize: Int) {

  private[this] val times = new Array[Long](sampleSize)
  private[this] var lastIndex = 0
  private[this] var complete = false
  private[this] var usersCount = 0.0
  private[this] val ratioNew = (5.0 / sampleSize).max(0.01).min(0.20)
  private[this] val ratioOld = 1 - ratioNew

  def reset() = synchronized {
      lastIndex = 0
      complete = false
      usersCount = 0
  }

  def count() = synchronized {
    lastIndex = (lastIndex + 1) % sampleSize
    complete |= lastIndex == 0
    val now = System.currentTimeMillis
    times(lastIndex) = now
    if (complete) {
      val last = times((lastIndex + sampleSize - 1) % sampleSize)
      val estimate = (sampleSize - 1.0) * Counter.MILLISECONDS_BETWEEN_STATUS_UPDATE / (last - now).max(1)
      usersCount = usersCount * ratioOld + estimate * ratioNew
    }
  }

  def users: Option[Long] = if (complete) Some(usersCount.round) else None

}

object Counter {

  final private val MILLISECONDS_BETWEEN_STATUS_UPDATE = 1800000

}
