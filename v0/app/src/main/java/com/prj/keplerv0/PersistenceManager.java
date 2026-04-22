package com.prj.keplerv0;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashSet;
import java.util.Set;

public class PersistenceManager {

    private static PersistenceManager instance;
    private final DatabaseHelper dbHelper;

    private PersistenceManager(Context context) {
        dbHelper = new DatabaseHelper(context.getApplicationContext());
    }

    public static synchronized PersistenceManager getInstance(Context context) {
        if (instance == null) {
            instance = new PersistenceManager(context);
        }
        return instance;
    }

    // --- Currency Methods ---

    public int getEnergyPoints() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int balance = 0;
        Cursor cursor = db.query(DatabaseHelper.TABLE_CURRENCY, new String[]{DatabaseHelper.COL_CURR_BALANCE},
                DatabaseHelper.COL_CURR_TYPE + "=?", new String[]{"ENERGY_POINTS"},
                null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            balance = cursor.getInt(0);
            cursor.close();
        }
        return balance;
    }

    public void addEnergyPoints(int amount) {
        int current = getEnergyPoints();
        setEnergyPoints(current + amount);
    }

    public boolean spendEnergyPoints(int amount) {
        int current = getEnergyPoints();
        if (current >= amount) {
            setEnergyPoints(current - amount);
            return true;
        }
        return false;
    }

    private void setEnergyPoints(int balance) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_CURR_BALANCE, balance);
        db.update(DatabaseHelper.TABLE_CURRENCY, values, DatabaseHelper.COL_CURR_TYPE + "=?", new String[]{"ENERGY_POINTS"});
    }

    // --- Collection Methods ---

    public void unlockCardFully(String cardName, String method) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_COLL_CARD_NAME, cardName);
        values.put(DatabaseHelper.COL_COLL_IS_UNLOCKED, 1);
        values.put(DatabaseHelper.COL_COLL_IS_PARTIAL, 0); // promote
        values.put(DatabaseHelper.COL_COLL_UNLOCK_METHOD, method);
        
        db.insertWithOnConflict(DatabaseHelper.TABLE_COLLECTION, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean unlockCardPartially(String cardName) {
        if (isFullyUnlocked(cardName)) return false;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        // check if already partial
        Cursor cursor = db.query(DatabaseHelper.TABLE_COLLECTION, new String[]{DatabaseHelper.COL_COLL_IS_PARTIAL},
                DatabaseHelper.COL_COLL_CARD_NAME + "=?", new String[]{cardName}, null, null, null);
        
        boolean alreadyPartial = false;
        if (cursor != null && cursor.moveToFirst()) {
            alreadyPartial = cursor.getInt(0) == 1;
            cursor.close();
        }
        
        if (alreadyPartial) return false;

        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_COLL_CARD_NAME, cardName);
        values.put(DatabaseHelper.COL_COLL_IS_UNLOCKED, 0);
        values.put(DatabaseHelper.COL_COLL_IS_PARTIAL, 1);
        values.put(DatabaseHelper.COL_COLL_UNLOCK_METHOD, "GAZING");

        db.insertWithOnConflict(DatabaseHelper.TABLE_COLLECTION, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    public boolean isFullyUnlocked(String cardName) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        boolean unlocked = false;
        Cursor cursor = db.query(DatabaseHelper.TABLE_COLLECTION, new String[]{DatabaseHelper.COL_COLL_IS_UNLOCKED},
                DatabaseHelper.COL_COLL_CARD_NAME + "=?", new String[]{cardName}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            unlocked = cursor.getInt(0) == 1;
            cursor.close();
        }
        return unlocked;
    }

    public Set<String> getFullyUnlockedCards() {
        Set<String> cards = new HashSet<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_COLLECTION, new String[]{DatabaseHelper.COL_COLL_CARD_NAME},
                DatabaseHelper.COL_COLL_IS_UNLOCKED + "=1", null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                cards.add(cursor.getString(0));
            }
            cursor.close();
        }
        return cards;
    }

    public Set<String> getPartiallyUnlockedCards() {
        Set<String> cards = new HashSet<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_COLLECTION, new String[]{DatabaseHelper.COL_COLL_CARD_NAME},
                DatabaseHelper.COL_COLL_IS_PARTIAL + "=1 AND " + DatabaseHelper.COL_COLL_IS_UNLOCKED + "=0", null, null, null, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                cards.add(cursor.getString(0));
            }
            cursor.close();
        }
        return cards;
    }
}
