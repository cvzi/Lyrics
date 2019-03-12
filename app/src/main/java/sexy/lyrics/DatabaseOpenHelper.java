package sexy.lyrics;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class DatabaseOpenHelper extends SQLiteOpenHelper {
    static final String TABLE_SONGS = "songs";
    static final String TABLE_LOCAL_TRACKS = "localtracks";
    private static final String DATABASE_NAME = "database.db";
    private static final int DATABASE_VERSION = 1;

    DatabaseOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE " + TABLE_SONGS + " (geniusid INTEGER PRIMARY KEY NOT NULL, timestamp INTEGER, title TEXT, artist TEXT, json TEXT);");
        database.execSQL("CREATE TABLE " + TABLE_LOCAL_TRACKS + " (title TEXT, artist TEXT, geniusid INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /*
        Currently not needed.
         */
    }

}
