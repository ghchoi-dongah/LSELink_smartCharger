package com.dongah.smartcharger.sqlite.dto;

import android.annotation.SuppressLint;
import android.content.ContentValues;

public class BatteryEncryptKey implements DbEntity {
    private static final String tableName = "BATTERY_ENCRYPT_KEY";
    private static final String ID = "ID";
    private static final String CHARGER_ID = "CHARGER_ID";
    private static final String KEY_ID = "KEY_ID";
    private static final String ENCRYPT_PUB = "ENCRYPT_PUB";
    private static final String SIGN_DATA = "SIGN_DATA";
    private static final String VALID_TIME = "VALID_TIME";
    private static final String RET_VAL = "RET_VAL";
    private static final String REG_DT = "REG_DT";
    public static final String CREATE_SQL =
            "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                    ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    CHARGER_ID + " TEXT NOT NULL," +
                    KEY_ID + " TEXT NOT NULL," +
                    ENCRYPT_PUB + " TEXT NOT NULL," +
                    SIGN_DATA + " TEXT NOT NULL," +
                    VALID_TIME + " TEXT NOT NULL," +
                    RET_VAL + " TEXT NOT NULL," +
                    REG_DT  + " TEXT NOT NULL" +
                    ");";

    public String chargerId;
    public String keyId;
    public String encryptPub;
    public String signData;
    public String validTime;
    public String retVal;
    public String regDt;

    @Override
    public String getTableName() {
        return tableName;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        values.put(CHARGER_ID, chargerId);
        values.put(KEY_ID, keyId);
        values.put(ENCRYPT_PUB, encryptPub);
        values.put(SIGN_DATA, signData);
        values.put(VALID_TIME, validTime);
        values.put(RET_VAL, retVal);
        values.put(REG_DT, regDt);
        return values;
    }
}
