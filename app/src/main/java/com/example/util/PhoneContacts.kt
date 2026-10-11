package com.example.util

import android.content.Context
import android.provider.ContactsContract

/**
 * The phone's own contacts, for a Text/Call card whose person isn't in Google Contacts (2026-10-11: the owner's
 * contacts live on the phone, e.g. "Tyler Griffin (New number)"). Needs READ_CONTACTS (asked for on first use).
 */
object PhoneContacts {
    /** (label, number) for everyone whose name contains [name] (or, if nobody, its first word), each number once. */
    fun numbersFor(context: Context, name: String): List<Pair<String, String>> {
        val words = name.trim()
        if (words.isBlank()) return emptyList()
        val found = query(context, words)
        return found.ifEmpty { words.split(" ").firstOrNull()?.takeIf { it.length >= 3 && it != words }?.let { query(context, it) } ?: emptyList() }
    }

    private fun query(context: Context, part: String): List<Pair<String, String>> = try {
        val out = LinkedHashMap<String, Pair<String, String>>()
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$part%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3) ?: "").toString()
                val key = number.filter { it.isDigit() }.takeLast(10)
                if (key.isNotEmpty() && key !in out) out[key] = "$display ($type)" to number
            }
        }
        out.values.toList()
    } catch (_: Exception) {
        emptyList()
    }
}
