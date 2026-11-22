package com.example.tvoege_Java.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {QuestionEntity.class}, version = 2, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {
    private static final String DB_NAME = "tvoege_db";

    /**
     * Версия 2: id вопроса стал стабильным (без autoGenerate), поэтому убираем AUTOINCREMENT.
     * Данные и прогресс ответов сохраняются, таблица пересоздаётся с новой схемой.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `questions_new` ("
                    + "`id` INTEGER NOT NULL, "
                    + "`taskId` INTEGER NOT NULL, "
                    + "`text` TEXT, "
                    + "`rule` TEXT, "
                    + "`correctAnswer` TEXT, "
                    + "`wrongAnswers` TEXT, "
                    + "`correctCount` INTEGER NOT NULL, "
                    + "`wrongCount` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");
            db.execSQL("INSERT INTO `questions_new` "
                    + "(`id`, `taskId`, `text`, `rule`, `correctAnswer`, `wrongAnswers`, "
                    + "`correctCount`, `wrongCount`) "
                    + "SELECT `id`, `taskId`, `text`, `rule`, `correctAnswer`, `wrongAnswers`, "
                    + "`correctCount`, `wrongCount` FROM `questions`");
            db.execSQL("DROP TABLE `questions`");
            db.execSQL("ALTER TABLE `questions_new` RENAME TO `questions`");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_taskId` ON `questions` (`taskId`)");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public abstract QuestionDao questionDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    ).addMigrations(MIGRATION_1_2).build();
                }
            }
        }
        return INSTANCE;
    }
}
