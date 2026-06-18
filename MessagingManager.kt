package com.mycontrol.mdm.managers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.provider.Telephony

class MessagingManager(private val context: Context) {
    
    fun getSmsInbox(): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                null, null, null,
                "date DESC LIMIT 1000"
            )
            cursor?.use {
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val readIdx = it.getColumnIndex("read")
                
                while (it.moveToNext()) {
                    messages.add(mapOf(
                        "address" to (if (addrIdx >= 0) it.getString(addrIdx) else ""),
                        "body" to (if (bodyIdx >= 0) it.getString(bodyIdx) else ""),
                        "date" to (if (dateIdx >= 0) it.getLong(dateIdx) else 0L),
                        "read" to (if (readIdx >= 0) it.getInt(readIdx) == 1 else false)
                    ))
                }
            }
        } catch (e: Exception) {}
        return messages
    }

    fun getSmsSent(): List<Map<String, Any?>> {
        val messages = mutableListOf<Map<String, Any?>>()
        try {
            val cursor = context.contentResolver.query(
                Uri.parse("content://sms/sent"),
                null, null, null,
                "date DESC LIMIT 1000"
            )
            cursor?.use {
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                
                while (it.moveToNext()) {
                    messages.add(mapOf(
                        "address" to (if (addrIdx >= 0) it.getString(addrIdx) else ""),
                        "body" to (if (bodyIdx >= 0) it.getString(bodyIdx) else ""),
                        "date" to (if (dateIdx >= 0) it.getLong(dateIdx) else 0L)
                    ))
                }
            }
        } catch (e: Exception) {}
        return messages
    }

    fun sendSms(phone: String, message: String): Boolean {
        return try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phone, null, message, null, null)
            true
        } catch (e: Exception) { false }
    }

    fun getCallLog(): List<Map<String, Any?>> {
        val calls = mutableListOf<Map<String, Any?>>()
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                null, null, null,
                "${CallLog.Calls.DATE} DESC LIMIT 1000"
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                
                while (it.moveToNext()) {
                    calls.add(mapOf(
                        "number" to (if (numIdx >= 0) it.getString(numIdx) else ""),
                        "name" to (if (nameIdx >= 0) it.getString(nameIdx) else ""),
                        "type" to (if (typeIdx >= 0) it.getInt(typeIdx) else 0),
                        "duration" to (if (durIdx >= 0) it.getLong(durIdx) else 0L),
                        "date" to (if (dateIdx >= 0) it.getLong(dateIdx) else 0L)
                    ))
                }
            }
        } catch (e: Exception) {}
        return calls
    }

    fun getContacts(): List<Map<String, Any?>> {
        val contacts = mutableListOf<Map<String, Any?>>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null
            )
            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                
                while (it.moveToNext()) {
                    val contactId = if (idIdx >= 0) it.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else ""
                    
                    val phones = mutableListOf<String>()
                    val emails = mutableListOf<String>()

                    // Get phones
                    val phoneCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId), null
                    )
                    phoneCursor?.use { pc ->
                        val phoneIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (pc.moveToNext()) {
                            if (phoneIdx >= 0) phones.add(pc.getString(phoneIdx) ?: "")
                        }
                    }

                    // Get emails
                    val emailCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                        arrayOf(contactId), null
                    )
                    emailCursor?.use { ec ->
                        val emailIdx = ec.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                        while (ec.moveToNext()) {
                            if (emailIdx >= 0) emails.add(ec.getString(emailIdx) ?: "")
                        }
                    }

                    contacts.add(mapOf(
                        "name" to name,
                        "phones" to phones,
                        "emails" to emails
                    ))
                }
            }
        } catch (e: Exception) {}
        return contacts
    }
}
