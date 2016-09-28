package sexy.lyrics;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseOpenHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "database.db";
    public static final int DATABASE_VERSION = 1;

    public static final String TABLE_SONGS = "songs";
    public static final String TABLE_LOCALTRACKS = "localtracks";

    public DatabaseOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE " + TABLE_SONGS + " (geniusid INTEGER PRIMARY KEY NOT NULL, timestamp INTEGER, title TEXT, artist TEXT, json TEXT);");
        database.execSQL("CREATE TABLE " + TABLE_LOCALTRACKS + " (title TEXT, artist TEXT, geniusid INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

}
