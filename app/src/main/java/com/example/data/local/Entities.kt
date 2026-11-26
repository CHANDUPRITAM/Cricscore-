package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "teams",
    indices = [Index(value = ["name"], unique = true)]
)
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shortCode: String,
    val photoBase64: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["teamId"])]
)
data class PlayerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val name: String,
    val role: String = "All-Rounder",
    val photoBase64: String = ""
)

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostTeam: String,
    val visitorTeam: String,
    val tossWinner: String,
    val tossDecision: String, // "BAT" or "BOWL"
    val totalOvers: Int = 10,
    val playersPerTeam: Int = 11,
    val wideReball: Boolean = true,
    val wideRuns: Int = 1,
    val noBallReball: Boolean = true,
    val noBallRuns: Int = 1,
    val lastManStands: Boolean = false,
    val isCompleted: Boolean = false,
    val resultSummary: String = "Match in progress",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ball_events",
    foreignKeys = [
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["matchId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["matchId", "sequence"])]
)
data class BallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val inningsNumber: Int, // 1 or 2
    val sequence: Int,
    val eventType: String, // EventType.name
    val strikerName: String = "",
    val nonStrikerName: String = "",
    val bowlerName: String = "",
    val runsScored: Int = 0,
    val isWide: Boolean = false,
    val isNoBall: Boolean = false,
    val isBye: Boolean = false,
    val isLegBye: Boolean = false,
    val isWicket: Boolean = false,
    val dismissalType: String? = null,
    val outBatsmanName: String? = null,
    val fielderName: String? = null,
    val newBatsmanName: String? = null,
    val swapAfterEvent: Boolean = false,
    val penaltyRuns: Int = -1, // -1 uses match rule; 0+ overrides penalty runs for wide/no-ball
    val shotX: Float? = null, // Normalized -1f..1f horizontal ground coordinate from pitch center
    val shotY: Float? = null, // Normalized -1f..1f vertical ground coordinate from pitch center
    val shotZone: String? = null, // Fielding zone name (e.g., Covers, Mid-Wicket, Long On)
    val timestamp: Long = System.currentTimeMillis()
)
