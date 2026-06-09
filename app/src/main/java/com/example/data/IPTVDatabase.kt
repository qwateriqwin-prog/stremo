package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val sourceUrl: String? = null,
    val filePath: String? = null,
    val customUserAgent: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["isFavorite"])]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playlistId: Int,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val isFavorite: Boolean = false,
    val orderIndex: Int = 0
)

@Dao
interface IPTVDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Query("SELECT * FROM channels ORDER BY orderIndex ASC, id ASC")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY orderIndex ASC, id ASC")
    fun getChannelsForPlaylist(playlistId: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFav WHERE id = :channelId")
    suspend fun updateFavoriteStatus(channelId: Int, isFav: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsForPlaylist(playlistId: Int)
}

@Database(entities = [PlaylistEntity::class, ChannelEntity::class], version = 1, exportSchema = false)
abstract class IPTVDatabase : RoomDatabase() {
    abstract fun iptvDao(): IPTVDao

    companion object {
        @Volatile
        private var INSTANCE: IPTVDatabase? = null

        fun getInstance(context: android.content.Context): IPTVDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    IPTVDatabase::class.java,
                    "iptv_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
