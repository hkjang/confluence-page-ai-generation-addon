package com.koreacb.confluence.aigeneration.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import javax.inject.Named;

@Named("jsonHelper")
public class JsonHelper {
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();
    private static final Gson GSON_PRETTY = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting().create();

    public static String toJson(Object obj) { return GSON.toJson(obj); }
    public static String toPrettyJson(Object obj) { return GSON_PRETTY.toJson(obj); }
    public static <T> T fromJson(String json, Class<T> clazz) { return GSON.fromJson(json, clazz); }
    public Gson getGson() { return GSON; }
}
