package com.github.rjbx.givetrack.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.util.Log;

import com.github.rjbx.givetrack.data.entry.Company;

import java.util.ArrayList;
import java.util.List;

import com.github.rjbx.givetrack.data.DatabaseContract.*;
import com.github.rjbx.givetrack.data.entry.Giving;
import com.github.rjbx.givetrack.data.entry.Record;
import com.github.rjbx.givetrack.data.entry.Search;

import timber.log.Timber;

public final class DatabaseRepository {

    static <T extends Company> List<T> getCompaniesFromType(Context context, Class<T> entryType) {
        Cursor cursor = context.getContentResolver().query(
                getCompanyUriFromType(entryType), null, null, null, null
        );
        List<T> entries = new ArrayList<>(cursor.getCount());
        if (cursor != null) cursorToCompanies(cursor, entries);
        return entries;
    }

    static <T extends Company> void setCompanies(Context context, List<T> companies) {
        ContentValues[] values = new ContentValues[companies.size()];
        for (int i = 0; i < companies.size(); i++) values[i] = companies.get(i).toContentValues();
        context.getContentResolver().bulkInsert(getCompanyUriFromType(companies.get(0).getClass()), values);
    }

    static <T extends Company> void cursorRowToCompany(Cursor cursor, T entry) {
        ContentValues values = new ContentValues();
        DatabaseUtils.cursorRowToContentValues(cursor, values);
        entry.fromContentValues(values);
    }

    static <T extends Company> void cursorToCompanies(Cursor cursor, List<T> entries) {
        if (cursor == null || !cursor.moveToFirst()) return;
        int i = 0;
        cursor.moveToFirst();
        do cursorRowToCompany(cursor, entries.get(i++));
        while (cursor.moveToNext());
    }

    static <T extends Company> Uri getCompanyUriFromType(Class<T> entryType) {
        try {
            if (entryType.isInstance(Search.class.newInstance())) return Entry.CONTENT_URI_SEARCH;
            if (entryType.isInstance(Giving.class.newInstance())) return Entry.CONTENT_URI_GIVING;
            if (entryType.isInstance(Record.class.newInstance())) return Entry.CONTENT_URI_RECORD;
        } catch (IllegalAccessException|InstantiationException e) { Timber.e(e); }
        throw new IllegalArgumentException("T must implement Company interface");
    }
}