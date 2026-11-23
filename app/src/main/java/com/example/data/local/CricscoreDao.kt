package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CricscoreDao {

    // Teams
    @Query("SELECT * FROM teams ORDER BY name ASC")
    fun observeAllTeams(): Flow<List<TeamEntity>>

    @Query("SELECT * FROM teams WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getTeamByName(name: String): TeamEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTeam(team: TeamEntity): Long

    @Update
    suspend fun updateTeam(team: TeamEntity)

    @Query("DELETE FROM teams WHERE id = :teamId")
    suspend fun deleteTeam(teamId: Long)

    // Players
    @Query("SELECT * FROM players ORDER BY name ASC")
    fun observeAllPlayers(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY name ASC")
    fun observePlayersForTeam(teamId: Long): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players WHERE teamId = :teamId AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getPlayerInTeam(teamId: Long, name: String): PlayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: PlayerEntity): Long

    @Update
    suspend fun updatePlayer(player: PlayerEntity)

    @Query("DELETE FROM players WHERE id = :playerId")
    suspend fun deletePlayer(playerId: Long)

    // Matches
    @Query("SELECT * FROM matches ORDER BY updatedAt DESC")
    fun observeAllMatches(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    fun observeMatch(matchId: Long): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE id = :matchId LIMIT 1")
    suspend fun getMatchById(matchId: Long): MatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchEntity): Long

    @Update
    suspend fun updateMatch(match: MatchEntity)

    @Query("DELETE FROM matches WHERE id = :matchId")
    suspend fun deleteMatch(matchId: Long)

    @Query("UPDATE matches SET totalOvers = :newOvers, updatedAt = :updatedAt WHERE id = :matchId")
    suspend fun updateMatchOvers(matchId: Long, newOvers: Int, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE matches SET wideReball = :wideReball, wideRuns = :wideRuns, " +
            "noBallReball = :noBallReball, noBallRuns = :noBallRuns, " +
            "lastManStands = :lastManStands, updatedAt = :updatedAt WHERE id = :matchId"
    )
    suspend fun updateMatchRules(
        matchId: Long,
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE matches SET isCompleted = :isCompleted, resultSummary = :summary, updatedAt = :updatedAt WHERE id = :matchId")
    suspend fun updateMatchStatus(
        matchId: Long,
        isCompleted: Boolean,
        summary: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    // Ball Events
    @Query("SELECT * FROM ball_events ORDER BY matchId ASC, sequence ASC")
    fun observeAllEvents(): Flow<List<BallEventEntity>>

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY sequence ASC")
    fun observeEventsForMatch(matchId: Long): Flow<List<BallEventEntity>>

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY sequence ASC")
    suspend fun getEventsForMatch(matchId: Long): List<BallEventEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM ball_events WHERE matchId = :matchId")
    suspend fun getMaxSequence(matchId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BallEventEntity): Long

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY sequence DESC LIMIT 1")
    suspend fun getLastEventForMatch(matchId: Long): BallEventEntity?

    @Query("DELETE FROM ball_events WHERE id = :eventId")
    suspend fun deleteEvent(eventId: Long)
}
