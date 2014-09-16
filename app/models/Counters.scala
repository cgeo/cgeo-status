package models

object Counters {

  private val counters: Map[BuildKind, Counter] =
    Map(Release -> new Counter(500),
        Deployment -> new Counter(100),
        ReleaseCandidate -> new Counter(10),
        NightlyBuild -> new Counter(10),
        Legacy -> new Counter(20),
        DeveloperBuild -> new Counter(10),
        Other -> new Counter(200))

  def reset(kind: BuildKind) {
    counters(kind).reset()
  }

  def count(kind: BuildKind) {
    counters(kind).count()
  }

  def users(kind: BuildKind) = counters(kind).users

}
