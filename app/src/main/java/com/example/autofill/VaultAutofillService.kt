package com.example.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.R
import com.example.data.AppDatabase
import com.example.data.VaultItemEntity
import com.example.security.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class VaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "VaultAutofillService"
    }

    private data class FormFields(
        var usernameId: AutofillId? = null,
        var passwordId: AutofillId? = null,
        var webDomain: String? = null
    )

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val fillContexts = request.fillContexts
        if (fillContexts.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val structure = fillContexts.last().structure
        val formFields = FormFields()

        // Traverse the view hierarchy to identify username and password fields
        val windowCount = structure.windowNodeCount
        for (i in 0 until windowCount) {
            val windowNode = structure.getWindowNodeAt(i)
            traverseNode(windowNode.rootViewNode, formFields)
        }

        if (formFields.usernameId == null && formFields.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        val targetPackage = structure.activityComponent?.packageName ?: ""
        val targetDomain = formFields.webDomain ?: ""

        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val allCredentials = db.vaultDao().getAllItemsDirect()

                if (allCredentials.isEmpty()) {
                    callback.onSuccess(null)
                    return@launch
                }

                // Filter credentials matching the target package, website domain, or general list
                val matchedCredentials = allCredentials.filter { item ->
                    val url = item.urlOrPackage.lowercase()
                    val title = item.title.lowercase()
                    val pkgMatch = targetPackage.isNotEmpty() && url.contains(targetPackage.lowercase())
                    val domainMatch = targetDomain.isNotEmpty() && (url.contains(targetDomain.lowercase()) || targetDomain.lowercase().contains(url))
                    val titleMatch = (targetPackage.isNotEmpty() && targetPackage.lowercase().contains(title)) ||
                            (targetDomain.isNotEmpty() && targetDomain.lowercase().contains(title))
                    pkgMatch || domainMatch || titleMatch
                }.ifEmpty {
                    // If no specific package match, show top credentials (up to 4)
                    allCredentials.take(4)
                }

                val responseBuilder = FillResponse.Builder()

                for (item in matchedCredentials) {
                    val remoteView = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                        setTextViewText(R.id.autofill_title, item.title)
                        setTextViewText(R.id.autofill_username, item.username)
                        setImageViewResource(R.id.autofill_icon, R.drawable.vaultpass_icon_1791043186011)
                    }

                    val datasetBuilder = Dataset.Builder(remoteView)

                    formFields.usernameId?.let { uId ->
                        datasetBuilder.setValue(uId, AutofillValue.forText(item.username))
                    }

                    formFields.passwordId?.let { pId ->
                        val plainPassword = CryptoManager.decrypt(item.encryptedPassword)
                        datasetBuilder.setValue(pId, AutofillValue.forText(plainPassword))
                    }

                    responseBuilder.addDataset(datasetBuilder.build())
                }

                // Set save info so Android can prompt to save credentials entered in external apps
                val requiredIds = mutableListOf<AutofillId>()
                formFields.usernameId?.let { requiredIds.add(it) }
                formFields.passwordId?.let { requiredIds.add(it) }

                if (requiredIds.isNotEmpty()) {
                    val saveInfo = SaveInfo.Builder(
                        SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME,
                        requiredIds.toTypedArray()
                    ).build()
                    responseBuilder.setSaveInfo(saveInfo)
                }

                callback.onSuccess(responseBuilder.build())
            } catch (e: Exception) {
                Log.e(TAG, "Error processing fill request", e)
                callback.onSuccess(null)
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val contexts = request.fillContexts
        if (contexts.isEmpty()) {
            callback.onSuccess()
            return
        }

        val structure = contexts.last().structure
        val formFields = FormFields()
        val windowCount = structure.windowNodeCount
        for (i in 0 until windowCount) {
            traverseNode(structure.getWindowNodeAt(i).rootViewNode, formFields)
        }

        val packageName = structure.activityComponent?.packageName ?: ""
        val targetDomain = formFields.webDomain ?: packageName

        var usernameValue = ""
        var passwordValue = ""

        // Extract values from node if available
        for (i in 0 until windowCount) {
            extractValues(structure.getWindowNodeAt(i).rootViewNode, formFields) { u, p ->
                if (u.isNotEmpty()) usernameValue = u
                if (p.isNotEmpty()) passwordValue = p
            }
        }

        if (usernameValue.isNotBlank() && passwordValue.isNotBlank()) {
            serviceScope.launch {
                try {
                    val db = AppDatabase.getInstance(applicationContext)
                    val title = targetDomain.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                    val item = VaultItemEntity(
                        title = if (title.isNotBlank()) title else "Saved Account",
                        username = usernameValue,
                        encryptedPassword = CryptoManager.encrypt(passwordValue),
                        urlOrPackage = targetDomain,
                        category = "Logins",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    db.vaultDao().insert(item)
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving autofill credential", e)
                }
            }
        }

        callback.onSuccess()
    }

    private fun traverseNode(node: AssistStructure.ViewNode, form: FormFields) {
        if (form.webDomain.isNullOrEmpty() && !node.webDomain.isNullOrEmpty()) {
            form.webDomain = node.webDomain
        }

        val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = node.hint?.toString()?.lowercase() ?: ""

        val isUsername = hints.contains(View.AUTOFILL_HINT_USERNAME.lowercase()) ||
                hints.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS.lowercase()) ||
                idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login") ||
                hintText.contains("user") || hintText.contains("email")

        val isPassword = hints.contains(View.AUTOFILL_HINT_PASSWORD.lowercase()) ||
                hints.contains("newpassword") || hints.contains("new_password") ||
                idEntry.contains("password") || idEntry.contains("pass") ||
                hintText.contains("password")

        if (isUsername && form.usernameId == null) {
            form.usernameId = node.autofillId
        }
        if (isPassword && form.passwordId == null) {
            form.passwordId = node.autofillId
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), form)
        }
    }

    private fun extractValues(
        node: AssistStructure.ViewNode,
        form: FormFields,
        onValues: (username: String, pass: String) -> Unit
    ) {
        val text = node.autofillValue?.textValue?.toString()
            ?: node.text?.toString() ?: ""

        if (node.autofillId == form.usernameId && text.isNotEmpty()) {
            onValues(text, "")
        }
        if (node.autofillId == form.passwordId && text.isNotEmpty()) {
            onValues("", text)
        }

        for (i in 0 until node.childCount) {
            extractValues(node.getChildAt(i), form, onValues)
        }
    }
}
