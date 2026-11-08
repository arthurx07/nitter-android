package com.example.nitter

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.net.URI

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var menuButton: ImageButton

    private val instances = listOf(
        "https://nitter.click",
        "https://nitter.mizuki.st",
        "https://nitter.mie.foundation",
        "https://nitter.jaydenha.uk",
        "https://nitter.tiekoetter.com",
        "https://nitter.privacyredirect.com",
        "https://nitter.r4fo.com",
        "https://nitter.netbub.com",
        "https://nitter.pp.ua",
        "https://shitter.thepixora.com",
        "https://nitter.kareem.one",
        "https://nitter.xitter.cc",
        "https://status.d420.de"
    )

    private var currentInstanceIndex = 0
    private val currentInstance: String
        get() = instances[currentInstanceIndex]

    private val PREFS_NAME = "nitter_config"
    private val KEY_BOOKMARKS = "user_bookmarks"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wipe any leftover data from prior unexpected terminations
        wipeAllData()

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true

                allowFileAccess = false
                allowContentAccess = false
                setGeolocationEnabled(false)
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                mediaPlaybackRequiresUserGesture = true

                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            }

            // Block third-party cookies globally
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Prevent loading non-HTTPS schemas (e.g. file://, intent://) in the WebView
                    return !url.startsWith("https://")
                }

                // Auto-fallback: Switch to next instance if main frame fails to load
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        Toast.makeText(this@MainActivity, "Instance unreachable. Switching instance...", Toast.LENGTH_SHORT).show()
                        switchToNextInstance()
                    }
                }
            }
        }

        // Programmatically build a Floating Menu Button
        menuButton = ImageButton(this).apply {
            // Dark translucent circular button
            val background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CCFF6C60")) // Semi-transparent Nitter red
            }
            setBackground(background)
            setImageResource(android.R.drawable.ic_menu_more)
            setColorFilter(Color.WHITE)
            setPadding(32, 32, 32, 32)
            setOnClickListener { view -> showPopupMenu(view) }
        }

        // Layout assembly
        val container = FrameLayout(this)
        container.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 48, 48) // Margin from right & bottom
        }

        container.addView(menuButton, btnParams)
        setContentView(container)

        // Adjust top/bottom margins for status & navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            val webViewParams = webView.layoutParams as? FrameLayout.LayoutParams
            webViewParams?.let {
                it.topMargin = systemBars.top
                it.bottomMargin = systemBars.bottom
                it.leftMargin = systemBars.left
                it.rightMargin = systemBars.right
                webView.layoutParams = it
            }

            val btnLayout = menuButton.layoutParams as? FrameLayout.LayoutParams
            btnLayout?.let {
                it.bottomMargin = systemBars.bottom + 48
                it.rightMargin = systemBars.right + 48
                menuButton.layoutParams = it
            }

            insets
        }

        // Back button navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::webView.isInitialized && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl(currentInstance)
    }

    // Action Menu

    private fun showPopupMenu(anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Switch Instance (curr: ${URI(currentInstance).host})")
        popup.menu.add(0, 2, 1, "Bookmarks")
        popup.menu.add(0, 3, 2, "Add Bookmark")
        popup.menu.add(0, 4, 3, "Reload Page")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> showInstanceDialog()
                2 -> showBookmarksDialog()
                3 -> promptAddBookmark()
                4 -> webView.reload()
            }
            true
        }
        popup.show()
    }

    // Instance Management

    private fun showInstanceDialog() {
        val hosts = instances.map { URI(it).host }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Nitter Instance")
            .setSingleChoiceItems(hosts, currentInstanceIndex) { dialog, which ->
                currentInstanceIndex = which
                loadUrlOnCurrentInstance(webView.url)
                dialog.dismiss()
            }
            .show()
    }

    private fun switchToNextInstance() {
        currentInstanceIndex = (currentInstanceIndex + 1) % instances.size
        loadUrlOnCurrentInstance(webView.url)
    }

    private fun loadUrlOnCurrentInstance(targetUrl: String?) {
        val path = if (!targetUrl.isNullOrEmpty() && targetUrl.startsWith("https://")) {
            try {
                val uri = URI(targetUrl)
                uri.rawPath + if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            } catch (_: Exception) { "" }
        } else ""

        webView.loadUrl("$currentInstance$path")
    }

    // Bookmarks Management

    private fun getSavedBookmarks(): MutableList<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultSet = setOf("GrapheneOS")
        return prefs.getStringSet(KEY_BOOKMARKS, defaultSet)?.toMutableList() ?: mutableListOf()
    }

    private fun saveBookmarks(bookmarks: List<String>) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_BOOKMARKS, bookmarks.toSet()).apply()
    }

    private fun showBookmarksDialog() {
        val bookmarks = getSavedBookmarks()
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No bookmarks saved", Toast.LENGTH_SHORT).show()
            return
        }

        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            bookmarks
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("User Bookmarks (tap to open or long-press to delete)")
            .setAdapter(adapter, null)
            .setNeutralButton("Clear All") { _, _ ->
                AlertDialog.Builder(this)
                .setTitle("Are you sure you want to clear all bookmarks?")
                .setPositiveButton("Yes") { _, _ ->
                    saveBookmarks(emptyList())
                    Toast.makeText(this, "Cleared all bookmarks", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("No", null)
                .show()
            }
            .create()

        dialog.show()

        // Tap to open bookmark
        dialog.listView.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
            val handle = bookmarks[position].removePrefix("@")
            webView.loadUrl("$currentInstance/$handle")
            dialog.dismiss()
        }

        // Long press to delete individual bookmark
        dialog.listView.onItemLongClickListener = android.widget.AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val removed = bookmarks.removeAt(position)
            saveBookmarks(bookmarks)
            Toast.makeText(this, "Deleted @$removed", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            showBookmarksDialog()
            true
        }
    }

    private fun promptAddBookmark() {
        // Auto-detect handle from active URL if browsing a user profile
        var detectedHandle = ""
        webView.url?.let { url ->
            try {
                val path = URI(url).path.trim('/')
                if (path.isNotEmpty() && !path.contains("/")) {
                    detectedHandle = path
                }
            } catch (_: Exception) {}
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "username (e.g. GrapheneOS)"
            if (detectedHandle.isNotEmpty()) setText(detectedHandle)
        }

        AlertDialog.Builder(this)
            .setTitle("Add Bookmark")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val handle = input.text.toString().trim().removePrefix("@")
                if (handle.isNotEmpty()) {
                    val list = getSavedBookmarks()
                    if (!list.contains(handle)) {
                        list.add(handle)
                        saveBookmarks(list)
                        Toast.makeText(this, "Saved @$handle", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Privacy Clean-up

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
        }
        wipeAllData()
        super.onDestroy()
    }

    private fun wipeAllData() {
        if (::webView.isInitialized) {
            webView.clearCache(true)
            webView.clearHistory()
            webView.clearFormData()
        }

        // Synchronous & complete cookie wipe
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies { cookieManager.flush() }

        // Wipe LocalStorage, SessionStorage, and IndexedDB
        WebStorage.getInstance().deleteAllData()

        // Delete cache files from local storage
        try {
            deleteDatabase("webview.db")
            deleteDatabase("webviewCache.db")
            cacheDir.deleteRecursively()
            codeCacheDir.deleteRecursively()

            // Remove persistent WebView engine storage
            val appWebviewDir = File(applicationInfo.dataDir, "app_webview")
            if (appWebviewDir.exists()) {
                appWebviewDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}
