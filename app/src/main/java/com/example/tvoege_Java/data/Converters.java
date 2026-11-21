package com.example.tvoege_Java.data;

import androidx.room.TypeConverter;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;

public class Converters {
    @TypeConverter
    public static String fromList(List<String> list) {
        if (list == null) return "[]";
        return new JSONArray(list).toString();
    }

    @TypeConverter
    public static List<String> toList(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(array.getString(i));
            }
        } catch (JSONException ignored) {}
        return result;
    }
}