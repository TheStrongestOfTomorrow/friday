package com.friday.assistant.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Friday — Custom Command DAO
 *
 * Data access object for the custom_commands table.
 * Provides CRUD operations for user-defined voice commands.
 */
@Dao
public interface CustomCommandDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CustomCommand command);

    @Update
    void update(CustomCommand command);

    @Delete
    void delete(CustomCommand command);

    @Query("SELECT * FROM custom_commands ORDER BY createdAt DESC")
    List<CustomCommand> getAllCommands();

    @Query("SELECT * FROM custom_commands WHERE triggerPhrase = :phrase LIMIT 1")
    CustomCommand getCommandByPhrase(String phrase);

    @Query("SELECT * FROM custom_commands WHERE triggerPhrase LIKE '%' || :partialPhrase || '%'")
    List<CustomCommand> searchCommands(String partialPhrase);

    @Query("DELETE FROM custom_commands")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM custom_commands")
    int count();
}
