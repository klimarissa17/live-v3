package org.icpclive.api

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import org.icpclive.util.*
import java.awt.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

public enum class MedalTiebreakMode {
    NONE,
    ALL
}

@Serializable
public data class MedalType(
    val name: String,
    val count: Int,
    val minScore: Double = Double.MIN_VALUE,
    val tiebreakMode: MedalTiebreakMode = MedalTiebreakMode.ALL
)

@Serializable
public enum class ContestResultType {
    ICPC, IOI
}

public enum class ScoreMergeMode {
    /**
     * For each tests group in the problem, get maximum score over all submissions.
     */
    MAX_PER_GROUP,

    /**
     * Get maximum total score over all submissions
     */
    MAX_TOTAL,

    /**
     * Get score from last submission
     */
    LAST,

    /**
     * Get score from last submissions, ignoring submissions, which didn't pass preliminary testing (e.g. on sample tests)
     */
    LAST_OK,

    /**
     * Get the sum of scores over all submissions
     */
    SUM
}

@Serializable
public data class ProblemInfo(
    @SerialName("letter") val displayName: String,
    @SerialName("name") val fullName: String,
    val id: Int,
    val ordinal: Int,
    val contestSystemId: String,
    val minScore: Double? = null,
    val maxScore: Double? = null,
    @Serializable(ColorSerializer::class) val color: Color? = null,
    val scoreMergeMode: ScoreMergeMode? = null,
) {
    internal companion object {
        val logger = getLogger(ProblemInfo::class)
    }
}

@Serializable
public enum class ContestStatus {
    BEFORE, RUNNING, OVER, FINALIZED;

    internal companion object {
        fun byCurrentTime(startTime: Instant, contestLength: Duration): ContestStatus {
            val offset = Clock.System.now() - startTime
            return when {
                offset < Duration.ZERO -> BEFORE
                offset < contestLength -> RUNNING
                else -> OVER
            }
        }
    }
}


@Serializable
public sealed class MediaType {
    public abstract val isMedia: Boolean

    @Serializable
    @SerialName("Photo")
    public data class Photo(val url: String, override val isMedia: Boolean = true) : MediaType()

    @Serializable
    @SerialName("Object")
    public data class Object(val url: String, override val isMedia: Boolean = true) : MediaType()

    @Serializable
    @SerialName("Video")
    public data class Video(val url: String, override val isMedia: Boolean = true) : MediaType()

    /**
     * WebRTC proxy connection
     * @see <a href="https://github.com/kbats183/webrtc-proxy">https://github.com/kbats183/webrtc-proxy</a>
     */
    @Serializable
    @SerialName("WebRTCProxyConnection")
    public data class WebRTCProxyConnection(val url: String, val audioUrl: String? = null, override val isMedia: Boolean = true) : MediaType()

    /**
     * WebRTC grabber connection
     * https://github.com/irdkwmnsb/webrtc-grabber
     */
    @Serializable
    @SerialName("WebRTCGrabberConnection")
    public data class WebRTCGrabberConnection(
        val url: String,
        val peerName: String,
        val streamType: String,
        val credential: String?,
        override val isMedia: Boolean = true
    ) :
        MediaType()

    @Serializable
    @SerialName("TaskStatus")
    public data class TaskStatus(val teamId: Int) : MediaType() {
        override val isMedia: Boolean = false
    }

    public fun noMedia(): MediaType = when (this) {
        is Photo -> copy(isMedia = false)
        is Video -> copy(isMedia = false)
        is Object -> copy(isMedia = false)
        is WebRTCProxyConnection -> copy(isMedia = false)
        is WebRTCGrabberConnection -> copy(isMedia = false)
        else -> this
    }
}

@Serializable
public enum class TeamMediaType {
    @SerialName("camera")
    CAMERA,

    @SerialName("screen")
    SCREEN,

    @SerialName("record")
    RECORD,

    @SerialName("photo")
    PHOTO,

    @SerialName("reactionVideo")
    REACTION_VIDEO,

    @SerialName("achievement")
    ACHIEVEMENT,
}

@Serializable
public data class TeamInfo(
    val id: Int,
    @SerialName("name") val fullName: String,
    @SerialName("shortName") val displayName: String,
    val contestSystemId: String,
    val groups: List<String>,
    val hashTag: String?,
    val medias: Map<TeamMediaType, MediaType>,
    val isHidden: Boolean,
    val isOutOfContest: Boolean,
    val organizationId: String?,
    val customFields: Map<String, String> = emptyMap(),
)

@Serializable
public data class GroupInfo(
    val cdsId: String,
    val displayName: String,
    val isHidden: Boolean,
    val isOutOfContest: Boolean,
    val awardsGroupChampion: Boolean = !isHidden,
)

@Serializable
public data class OrganizationInfo(
    val cdsId: String,
    val displayName: String,
    val fullName: String,
    val logo: MediaType?,
)

@Serializable
public enum class PenaltyRoundingMode {
    @SerialName("each_submission_down_to_minute")
    /**
     * Round time of all submissions from the beginning of the contest down to whole minute, and then sum them
     */
    EACH_SUBMISSION_DOWN_TO_MINUTE,

    /**
     * Sum time of all submissions from the beginning of the contest and then round it down to whole minute
     */
    @SerialName("sum_down_to_minute")
    SUM_DOWN_TO_MINUTE,

    /**
     * Sum time of all submissions without rounding
     */
    @SerialName("sum_in_seconds")
    SUM_IN_SECONDS,

    /**
     * Get time of last submission as penalty
     */
    @SerialName("last")
    LAST,

    /**
     * Don't have any penalty as a tie-breaker
     */
    @SerialName("zero")
    ZERO,
}


@Target(AnnotationTarget.PROPERTY)
@RequiresOptIn(level = RequiresOptIn.Level.ERROR, message = "This api is not efficient in most cases, consider using corresponding map instead")
public annotation class InefficientContestInfoApi

@Serializable
@OptIn(InefficientContestInfoApi::class)
public data class ContestInfo(
    val name: String,
    val status: ContestStatus,
    val resultType: ContestResultType,
    @SerialName("startTimeUnixMs")
    @Serializable(with = UnixMillisecondsSerializer::class)
    val startTime: Instant,
    @SerialName("contestLengthMs")
    @Serializable(with = DurationInMillisecondsSerializer::class)
    val contestLength: Duration,
    @SerialName("freezeTimeMs")
    @Serializable(with = DurationInMillisecondsSerializer::class)
    val freezeTime: Duration,
    @InefficientContestInfoApi @SerialName("problems") val problemList: List<ProblemInfo>,
    @InefficientContestInfoApi @SerialName("teams") val teamList: List<TeamInfo>,
    @InefficientContestInfoApi @SerialName("groups") val groupList: List<GroupInfo>,
    @InefficientContestInfoApi @SerialName("organizations") val organizationList: List<OrganizationInfo>,
    val penaltyRoundingMode: PenaltyRoundingMode,
    @SerialName("holdBeforeStartTimeMs")
    @Serializable(with = DurationInMillisecondsSerializer::class)
    val holdBeforeStartTime: Duration? = null,
    val emulationSpeed: Double = 1.0,
    val medals: List<MedalType> = emptyList(),
    val penaltyPerWrongAttempt: Duration = 20.minutes,
    @Transient
    val cdsSupportsFinalization: Boolean = false,
) {
    public val currentContestTime: Duration
        get() = when (status) {
            ContestStatus.BEFORE -> Duration.ZERO
            ContestStatus.RUNNING -> (Clock.System.now() - startTime) * emulationSpeed
            ContestStatus.OVER, ContestStatus.FINALIZED -> contestLength
        }
    val groups: Map<String, GroupInfo> by lazy { groupList.associateBy { it.cdsId } }
    val teams: Map<Int, TeamInfo> by lazy { teamList.associateBy { it.id } }
    val cdsTeams: Map<String, TeamInfo> by lazy { teamList.associateBy { it.contestSystemId } }
    val organizations: Map<String, OrganizationInfo> by lazy { organizationList.associateBy { it.cdsId } }
    val problems: Map<Int, ProblemInfo> by lazy { problemList.associateBy { it.id } }
    val scoreboardProblems: List<ProblemInfo> by lazy { problemList.sortedBy { it.ordinal } }
}