package com.example.ui.viewmodel

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.PartyEntity
import com.example.data.repository.AccountingRepository
import com.example.util.WhatsAppDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactInfo(
    val name: String,
    val phone: String
)

class PartyViewModel(
    private val repository: AccountingRepository
) : ViewModel() {

    val allParties: StateFlow<List<PartyEntity>> = repository.allParties.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    private val _selectedParty = MutableStateFlow<PartyEntity?>(null)
    val selectedParty: StateFlow<PartyEntity?> = _selectedParty.asStateFlow()

    fun selectParty(party: PartyEntity?) {
        _selectedParty.value = party
    }

    fun insertParty(party: PartyEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.insertParty(party)
                _userMessage.emit("تمت إضافة الطرف بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل إضافة الطرف: ${e.message}")
            }
        }
    }

    fun updateParty(party: PartyEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repository.updateParty(party)
                _userMessage.emit("تم تحديث بيانات الطرف بنجاح")
                onSuccess()
            } catch (e: Exception) {
                _userMessage.emit("فشل تحديث بيانات الطرف: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PartyContactPicker"

        /**
         * Resolves contact name and phone number from a contact Uri returned by PickContact().
         * Extracts ContactsContract.CommonDataKinds.Phone.NUMBER and DISPLAY_NAME.
         * Sanitizes and intelligently normalizes the extracted phone number.
         */
        fun extractContact(context: Context, contactUri: Uri): ContactInfo? {
            var displayName = ""
            var rawPhoneNumber = ""
            var contactId: String? = null

            try {
                // 1. Query contactUri for basic info and ID
                context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        if (idIdx != -1) contactId = cursor.getString(idIdx)
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx).orEmpty()
                    }
                }

                // 2. Query CommonDataKinds.Phone for the phone number
                if (!contactId.isNullOrBlank()) {
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId),
                        null
                    )?.use { phoneCursor ->
                        if (phoneCursor.moveToFirst()) {
                            val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val pNameIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            if (numIdx != -1) rawPhoneNumber = phoneCursor.getString(numIdx).orEmpty()
                            if (displayName.isBlank() && pNameIdx != -1) {
                                displayName = phoneCursor.getString(pNameIdx).orEmpty()
                            }
                        }
                    }
                } else {
                    // Fallback: If contactUri is a direct Phone URI
                    context.contentResolver.query(
                        contactUri,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        ),
                        null,
                        null,
                        null
                    )?.use { directCursor ->
                        if (directCursor.moveToFirst()) {
                            val numIdx = directCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val nameIdx = directCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            if (numIdx != -1) rawPhoneNumber = directCursor.getString(numIdx).orEmpty()
                            if (displayName.isBlank() && nameIdx != -1) displayName = directCursor.getString(nameIdx).orEmpty()
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_CONTACTS permission denied or missing: ${e.message}")
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving contact: ${e.message}", e)
                return null
            }

            val sanitizedPhone = WhatsAppDispatcher.sanitizePhoneNumber(rawPhoneNumber)
            return ContactInfo(
                name = displayName.trim(),
                phone = sanitizedPhone
            )
        }
    }
}
