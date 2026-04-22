package com.prj.keplerv0;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "kepler_game.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_CURRENCY = "currency";
    public static final String COL_CURR_ID = "id";
    public static final String COL_CURR_TYPE = "type";
    public static final String COL_CURR_BALANCE = "balance";

    public static final String TABLE_COLLECTION = "collection";
    public static final String COL_COLL_ID = "id";
    public static final String COL_COLL_CARD_NAME = "card_name";
    public static final String COL_COLL_IS_UNLOCKED = "is_unlocked";
    public static final String COL_COLL_IS_PARTIAL = "is_partial";
    public static final String COL_COLL_UNLOCK_METHOD = "unlock_method";

    private static final String CREATE_TABLE_CURRENCY =
            "CREATE TABLE " + TABLE_CURRENCY + " (" +
            COL_CURR_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_CURR_TYPE + " TEXT UNIQUE, " +
            COL_CURR_BALANCE + " INTEGER DEFAULT 0)";

    private static final String CREATE_TABLE_COLLECTION =
            "CREATE TABLE " + TABLE_COLLECTION + " (" +
            COL_COLL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_COLL_CARD_NAME + " TEXT UNIQUE, " +
            COL_COLL_IS_UNLOCKED + " INTEGER DEFAULT 0, " +
            COL_COLL_IS_PARTIAL + " INTEGER DEFAULT 0, " +
            COL_COLL_UNLOCK_METHOD + " TEXT)";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_CURRENCY);
        db.execSQL(CREATE_TABLE_COLLECTION);
        // Initialize energy points
        db.execSQL("INSERT INTO " + TABLE_CURRENCY + " (" + COL_CURR_TYPE + ", " + COL_CURR_BALANCE + ") VALUES ('ENERGY_POINTS', 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CURRENCY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COLLECTION);
        onCreate(db);
    }
}
