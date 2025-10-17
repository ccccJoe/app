/*
 * File: Converters.kt
 * Description: Room TypeConverters for lists and enums used by entities.
 * Author: SIMS Team
 */
package com.simsapp.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simsapp.data.local.entity.DigitalAssetItem

/**
 * Converters
 *
 * Provides JSON-based conversions for Room to persist complex types like
 * lists and enums in a single column safely.
 */
class Converters {
    private val gson = Gson()

    /**
     * Convert list of strings to JSON string for storage.
     */
    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.let { gson.toJson(it) }

    /**
     * Convert JSON string back to list of strings.
     */
    @TypeConverter
    fun toStringList(json: String?): List<String>? = json?.let {
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson<List<String>>(it, type)
    }

    /**
     * Convert list of longs to JSON string for storage.
     */
    @TypeConverter
    fun fromLongList(list: List<Long>?): String? = list?.let { gson.toJson(it) }

    /**
     * Convert JSON string back to list of longs.
     */
    @TypeConverter
    fun toLongList(json: String?): List<Long>? = json?.let {
        val type = object : TypeToken<List<Long>>() {}.type
        gson.fromJson<List<Long>>(it, type)
    }

    /**
     * Convert list of DigitalAssetItem to JSON string for storage.
     */
    @TypeConverter
    fun fromDigitalAssetItemList(list: List<DigitalAssetItem>?): String? = list?.let { gson.toJson(it) }

    /**
     * Convert JSON string back to list of DigitalAssetItem.
     */
    @TypeConverter
    fun toDigitalAssetItemList(json: String?): List<DigitalAssetItem>? = json?.let {
        val type = object : TypeToken<List<DigitalAssetItem>>() {}.type
        gson.fromJson<List<DigitalAssetItem>>(it, type)
    }
}