package lila.evaluation

case class PlayerFlags(
    suspiciousErrorRate: Boolean,
    alwaysHasAdvantage: Boolean,
    highBlurRate: Boolean,
    moderateBlurRate: Boolean,
    highlyConsistentPlyTimes: Boolean,
    moderatelyConsistentPlyTimes: Boolean,
    noFastPlies: Boolean,
    suspiciousHoldAlert: Boolean
)
