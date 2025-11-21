package com.instructure.annotations

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object PDFUtils {

    fun downloadPdf(url: String, docName: String, context: Context): File {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        val pdfFile = File(context.getExternalFilesDir(null), docName)

        if(!pdfFile.exists()) {
            response.body?.byteStream().use { input ->
                FileOutputStream(pdfFile).use { output ->
                    input?.copyTo(output)
                }
            }
        }
        return pdfFile
    }


    fun openPdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
        }
    }
}