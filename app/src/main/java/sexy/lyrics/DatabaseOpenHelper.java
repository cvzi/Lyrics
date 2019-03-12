package sexy.lyrics;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class DatabaseOpenHelper extends SQLiteOpenHelper {
    static final String TABLE_SONGS = "songs";
    static final String TABLE_LOCAL_TRACKS = "localtracks";
    private static final String DATABASE_NAME = "database.db";
    private static final int DATABASE_VERSION = 1;

    static final String FIELD_TITLE = "title";
    static final String FIELD_ARTIST = "artist";
    static final String FIELD_GENIUS_ID = "geniusid";
    static final String FIELD_TIMESTAMP = "timestamp";
    static final String FIELD_JSON = "json";

    DatabaseOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE "
                + TABLE_SONGS + " ( "
                + FIELD_GENIUS_ID + " INTEGER PRIMARY KEY NOT NULL, "
                + FIELD_TIMESTAMP + " INTEGER, "
                + FIELD_TITLE + " TEXT, "
                + FIELD_ARTIST + " TEXT, "
                + FIELD_JSON + " TEXT);");
        database.execSQL("CREATE TABLE "
                + TABLE_LOCAL_TRACKS + " ("
                + FIELD_TITLE + " TEXT, "
                + FIELD_ARTIST + " TEXT, "
                + FIELD_GENIUS_ID + " INTEGER);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /*
        Currently not needed.
         */
    }

}
