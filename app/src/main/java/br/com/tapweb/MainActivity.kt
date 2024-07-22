package br.com.tapweb

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.Base64

class MainActivity : AppCompatActivity() {

    lateinit var myWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myWebView = findViewById(R.id.webview)
        myWebView.webViewClient = WebViewClientImpl(this)
        myWebView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: String, lineNumber: Int, sourceID: String) {
                Log.d("TAPWEB", "$message -- From line $lineNumber of $sourceID")
            }
        }

        val webSettings = myWebView.settings
        webSettings.javaScriptEnabled = true
        webSettings.allowFileAccess = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE

        myWebView.addJavascriptInterface(WebAppInterface(this@MainActivity), "Android")

        if (savedInstanceState == null) {
            val path = getPath()
            if (path != null) {
                myWebView.loadUrl(path)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        myWebView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        myWebView.restoreState(savedInstanceState)
    }

    private fun getPath(): String? {
        try {
            val uri = intent.data
            if (uri != null) {
                val path = uri.path
                val queryParams = uri.query.toString()

                return "https://tapweb.com.br$path?$queryParams"
            }

            return "https://tapweb.com.br";
        } catch (error: Error) {
            return "https://tapweb.com.br";
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

}

class WebAppInterface(private val mContext: Activity) {

    @JavascriptInterface
    fun logado(username: String?, password: String?, recaptcha: String?) {
        if (username == null || password == null || recaptcha == null) {
            return
        }

        //save the values in SharedPrefs
        val sharedPref: SharedPreferences = mContext.getPreferences(Context.MODE_PRIVATE);
        val editor = sharedPref.edit()

        editor.putString("username", username)
        editor.putString("password", password)
        editor.putString("recaptcha", recaptcha)
        editor.apply()
    }

    @JavascriptInterface
    fun logout() {
        val sharedPref: SharedPreferences = mContext.getPreferences(Context.MODE_PRIVATE);
        val editor = sharedPref.edit()

        editor.putString("username", "")
        editor.putString("password", "")
        editor.putString("recaptcha", "")
        editor.apply()
    }

    @JavascriptInterface
    fun getPreferences(): String {
        class Login(username: String, password: String, recaptcha: String) {}

        val sharedPref = mContext.getPreferences(Context.MODE_PRIVATE)

        var username = sharedPref.getString("username", "")
        var password = sharedPref.getString("password", "")
        var recaptcha = sharedPref.getString("recaptcha", "")

        if (username.isNullOrEmpty()) {
            username = ""
        }

        if (password.isNullOrEmpty()) {
            password = ""
        }
        if (recaptcha.isNullOrEmpty()) {
            recaptcha = ""
        }

        return "$username / $password / auto_android"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @JavascriptInterface
    fun download(base64Data: String, fileName: String) {
        val hasPermission = checkFilePermissions()

        if (hasPermission) {
            val folders = ContextCompat.getExternalFilesDirs(mContext, null)

            if (folders.isNotEmpty()) {
                val folder = folders[0]

                if (folder.exists() && folder.canRead() && folder.canWrite()) {
                    val file = File("$folder/$fileName")
                    if (file.exists()) {
                        file.delete()
                    }
                    file.createNewFile()

                    val imageByteArray = Base64.getMimeDecoder().decode(base64Data.split(",")[1])
                    file.writeBytes(imageByteArray)

                    val path = FileProvider.getUriForFile(
                        mContext,
                        mContext.applicationContext.packageName + ".provider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                    if (fileName.contains(".xls") || fileName.contains(".xlsx")) {
                        // Excel file
                        intent.setDataAndType(path, "application/vnd.ms-excel");
                    } else if (
                        fileName.contains(".jpg") ||
                        fileName.contains(".jpeg") ||
                        fileName.contains(".png")
                    ) {
                        // JPG file
                        intent.setDataAndType(path, "image/jpeg");
                    } else {
                        intent.setDataAndType(path, "*/*");
                    }

                    try {
                        mContext.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                    }
                }
            }
        }
    }

    private fun checkFilePermissions(): Boolean {
        val readPermission = ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE)
        val writePermission = ActivityCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (readPermission != PackageManager.PERMISSION_GRANTED || writePermission != PackageManager.PERMISSION_GRANTED) {
            val storagePermissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            ActivityCompat.requestPermissions(mContext, storagePermissions, 666)

            return false
        }

        return true
    }

}