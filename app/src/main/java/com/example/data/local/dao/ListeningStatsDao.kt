package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ListeningStat
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningStatsDao {
    @Query("SELECT * FROM listening_stats ORDER BY dateKey DESC LIMIT :limit")
    fun getRecentStats(limit: Int = 14): Flow<List<ListeningStat>>

    @Query("SELECT COALESCE(SUM(totalSeconds), 0) FROM listening_stats")
    fun getTotalListenedSeconds(): Flow<Long>

    @Query("SELECT * FROM listening_stats WHERE dateKey = :dateKey")
    suspend fun getStatForDay(dateKey: String): ListeningStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStat(stat: ListeningStat)
}
