package com.dongah.smartcharger.sqlite.dto;

import android.content.ContentValues;

public interface DbEntity {
    String getTableName();
    ContentValues toContentValues();
}
