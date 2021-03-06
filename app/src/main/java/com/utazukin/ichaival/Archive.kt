/*
 * Ichaival - Android client for LANraragi https://github.com/Utazukin/Ichaival/
 * Copyright (C) 2019 Utazukin
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.utazukin.ichaival

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.json.JSONObject

class Archive(json: JSONObject) {
    val title: String = json.getString("title")
    val id: String = json.getString("arcid")
    val tags: Map<String, List<String>>
    var isNew = false
    var dateAdded = 0
        private set
    val numPages: Int
        get() = DatabaseReader.getPageCount(id)

    private val extractActor by lazy {
        GlobalScope.actor<ExtractMsg>(Dispatchers.Default, capacity = Channel.UNLIMITED) {
            val emptyList = listOf<String>()
            var pages: List<String>? = null
            for (msg in channel) {
                pages = when (msg) {
                    is QueueExtract -> msg.action(msg.id)
                    is GetPages -> {
                        msg.response.complete(pages ?: emptyList)
                        null
                    }
                }
            }
        }
    }

    init {
        val tagString: String = json.getString("tags")
        val tagList: List<String> = tagString.split(",")
        val mutableTags = mutableMapOf<String, MutableList<String>>()
        for (tag: String in tagList) {
            val trimmed = tag.trim()
            if (trimmed.contains(":")) {
                val split = trimmed.split(":")
                val namespace = split[0]
                if (namespace == "date_added")
                    dateAdded = split[1].toInt()
                else {
                    if (!mutableTags.containsKey(namespace))
                        mutableTags[namespace] = mutableListOf()
                    mutableTags[namespace]?.add(split[1])
                }
            }
            else if (!tag.isEmpty()) {
                if (!mutableTags.containsKey("global"))
                    mutableTags["global"] = mutableListOf()
                mutableTags["global"]?.add(trimmed)
            }
        }
        tags = mutableTags

        isNew = json.getString("isnew") == "block"
    }

    suspend fun extract() = DatabaseReader.getPageList(id, extractActor)

    fun invalidateCache() {
        DatabaseReader.invalidateImageCache(id)
    }

    fun hasPage(page: Int) : Boolean {
        return numPages < 0 || (page in 0 until numPages)
    }

    suspend fun getPageImage(page: Int) : String? {
        return downloadPage(page)
    }

    private suspend fun downloadPage(page: Int) : String? {
        val pages = DatabaseReader.getPageList(id, extractActor)
        return if (page < pages.size) DatabaseReader.getRawImageUrl(pages[page]) else null
    }

    fun containsTag(tag: String) : Boolean {
        if (tag.contains(":")) {
            val split = tag.split(":")
            val namespace = split[0].trim()
            var normalized = split[1].trim().replace("_", " ").toLowerCase()
            val exact = normalized.startsWith("\"") && normalized.endsWith("\"")
            if (exact)
                normalized = normalized.removeSurrounding("\"")
            val nTags = getTags(namespace)
            return nTags != null && nTags.any { if (exact) it.toLowerCase() == normalized else it.toLowerCase().contains(normalized) }
        }
        else {
            val normalized = tag.trim().replace("_", " ").toLowerCase()
            for (pair in tags) {
                if (pair.value.any { it.toLowerCase().contains(normalized)})
                    return true
            }
        }
        return false
    }

    private fun getTags(namespace: String) : List<String>? {
        return if (tags.containsKey(namespace)) tags[namespace] else null
    }
}