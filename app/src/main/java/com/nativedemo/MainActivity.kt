package com.nativedemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NativeDemo"
        private const val FILE_PICKER_REQUEST = 1001
        
        // Cloudflare tunnel URL
        private const val WEB_URL = "https://experiments-visits-butter-chosen.trycloudflare.com"
    }

    private lateinit var webView: WebView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var pendingCameraCallback: String? = null
    private var pendingLocationCallback: String? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Camera launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        handleCameraResult(bitmap)
    }

    // File chooser launcher
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        fileUploadCallback?.onReceiveValue(uris.toTypedArray())
        fileUploadCallback = null
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permissions result: $permissions")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        webView = findViewById(R.id.webView)
        setupWebView()
        
        if (WEB_URL == "YOUR_URL_HERE") {
            webView.loadData(
                """
                <html>
                <body style="background:#09090b;color:#fff;font-family:sans-serif;padding:20px;">
                <h2>Setup Required</h2>
                <p>Edit MainActivity.kt and replace WEB_URL with your URL:</p>
                <ul>
                <li>For ngrok: https://xxxx.ngrok-free.app</li>
                <li>For emulator: http://10.0.2.2:3000</li>
                <li>For device: http://YOUR_IP:3000</li>
                </ul>
                </body>
                </html>
                """.trimIndent(),
                "text/html",
                "UTF-8"
            )
        } else {
            webView.loadUrl(WEB_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // Enable debugging
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Add native bridge
        webView.addJavascriptInterface(NativeBridge(), "NativeBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page loaded: $url")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.e(TAG, "WebView error: ${error?.description}")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                Log.d(TAG, "WebView permission request: ${request?.resources?.toList()}")
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                
                val acceptTypes = fileChooserParams?.acceptTypes?.firstOrNull() ?: "*/*"
                fileChooserLauncher.launch(acceptTypes)
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(TAG, "JS Console: ${consoleMessage?.message()}")
                return true
            }
        }
    }

    private fun handleCameraResult(bitmap: Bitmap?) {
        val result = if (bitmap != null) {
            val base64 = bitmapToBase64(bitmap)
            JSONObject().apply {
                put("base64", base64)
                put("width", bitmap.width)
                put("height", bitmap.height)
            }.toString()
        } else {
            JSONObject().put("error", "Camera cancelled").toString()
        }
        
        runOnUiThread {
            webView.evaluateJavascript(
                "if(window.onCameraResult) window.onCameraResult($result);",
                null
            )
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    inner class NativeBridge {

        @JavascriptInterface
        fun checkCameraSupport(): String {
            Log.d(TAG, "checkCameraSupport called")
            val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val cameras = mutableListOf<String>()
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) cameras.add("Back Camera")
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) cameras.add("Front Camera")
            
            return JSONObject().apply {
                put("supported", hasCamera)
                put("cameras", JSONArray(cameras))
            }.toString()
        }

        @JavascriptInterface
        fun requestCameraPermission(): String {
            Log.d(TAG, "requestCameraPermission called")
            val granted = hasPermission(Manifest.permission.CAMERA)
            
            if (!granted) {
                runOnUiThread {
                    permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            }
            
            return JSONObject().put("granted", granted).toString()
        }

        @JavascriptInterface
        fun capturePhoto(optionsJson: String): String {
            Log.d(TAG, "capturePhoto called: $optionsJson")
            runOnUiThread {
                takePictureLauncher.launch(null)
            }
            return JSONObject().put("status", "launching").toString()
        }

        @JavascriptInterface
        fun checkLocationSupport(): String {
            Log.d(TAG, "checkLocationSupport called")
            return JSONObject().apply {
                put("supported", true)
                put("gpsEnabled", true)
            }.toString()
        }

        @JavascriptInterface
        fun requestLocationPermission(): String {
            Log.d(TAG, "requestLocationPermission called")
            val fineGranted = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            
            if (!fineGranted) {
                runOnUiThread {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
            
            return JSONObject().apply {
                put("granted", fineGranted)
                put("precision", if (fineGranted) "fine" else "none")
            }.toString()
        }

        @JavascriptInterface
        fun getCurrentLocation(optionsJson: String): String {
            Log.d(TAG, "getCurrentLocation called: $optionsJson")
            
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                runOnUiThread {
                    webView.evaluateJavascript(
                        """if(window.onLocationResult) window.onLocationResult({"error":"Permission denied"});""",
                        null
                    )
                }
                return JSONObject().put("status", "permission_denied").toString()
            }

            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location: Location? ->
                        val result = if (location != null) {
                            JSONObject().apply {
                                put("latitude", location.latitude)
                                put("longitude", location.longitude)
                                put("accuracy", location.accuracy)
                                put("altitude", location.altitude)
                                put("speed", location.speed)
                            }.toString()
                        } else {
                            """{"error":"Location unavailable"}"""
                        }
                        runOnUiThread {
                            webView.evaluateJavascript(
                                "if(window.onLocationResult) window.onLocationResult($result);",
                                null
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        runOnUiThread {
                            webView.evaluateJavascript(
                                """if(window.onLocationResult) window.onLocationResult({"error":"${e.message}"});""",
                                null
                            )
                        }
                    }
            } catch (e: SecurityException) {
                Log.e(TAG, "Location security exception", e)
            }
            
            return JSONObject().put("status", "fetching").toString()
        }

        @JavascriptInterface
        fun checkUPISupport(): String {
            Log.d(TAG, "checkUPISupport called")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            val apps = packageManager.queryIntentActivities(intent, 0)
            return JSONObject().apply {
                put("supported", apps.isNotEmpty())
                put("platform", "Android")
                put("appsCount", apps.size)
            }.toString()
        }

        @JavascriptInterface
        fun getUPIApps(): String {
            Log.d(TAG, "getUPIApps called")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            val apps = packageManager.queryIntentActivities(intent, 0)
            val appList = apps.map { info ->
                JSONObject().apply {
                    put("packageName", info.activityInfo.packageName)
                    put("appName", info.loadLabel(packageManager).toString())
                }
            }
            return JSONObject().put("apps", JSONArray(appList)).toString()
        }

        @JavascriptInterface
        fun triggerUPIPayment(paramsJson: String): String {
            Log.d(TAG, "triggerUPIPayment called: $paramsJson")
            try {
                val params = JSONObject(paramsJson)
                val uri = Uri.Builder()
                    .scheme("upi")
                    .authority("pay")
                    .appendQueryParameter("pa", params.optString("pa", ""))
                    .appendQueryParameter("pn", params.optString("pn", ""))
                    .appendQueryParameter("am", params.optString("am", ""))
                    .appendQueryParameter("tn", params.optString("tn", ""))
                    .appendQueryParameter("cu", "INR")
                    .build()
                
                val intent = Intent(Intent.ACTION_VIEW, uri)
                val packageName = params.optString("packageName", "")
                if (packageName.isNotEmpty()) {
                    intent.setPackage(packageName)
                }
                
                runOnUiThread {
                    startActivity(Intent.createChooser(intent, "Pay with"))
                }
                
                return JSONObject().put("status", "launched").toString()
            } catch (e: Exception) {
                return JSONObject().put("error", e.message).toString()
            }
        }

        @JavascriptInterface
        fun checkPushSupport(): String {
            Log.d(TAG, "checkPushSupport called")
            return JSONObject().apply {
                put("supported", true)
                put("provider", "FCM")
            }.toString()
        }

        @JavascriptInterface
        fun requestPushPermission(): String {
            Log.d(TAG, "requestPushPermission called")
            // On Android 13+, POST_NOTIFICATIONS permission is needed
            // For this demo, we return granted (real app would request permission)
            return JSONObject().apply {
                put("granted", true)
                put("note", "Push permission handled by native layer")
            }.toString()
        }

        @JavascriptInterface
        fun registerForPush(): String {
            Log.d(TAG, "registerForPush called")
            // In a real app, this would get FCM token
            // For demo, return a mock token
            val mockToken = "fcm_${System.currentTimeMillis()}_demo"
            return JSONObject().apply {
                put("token", mockToken)
                put("provider", "FCM")
                put("note", "Demo token - use Firebase for real implementation")
            }.toString()
        }

        @JavascriptInterface
        fun checkFilePickerSupport(): String {
            Log.d(TAG, "checkFilePickerSupport called")
            return JSONObject().apply {
                put("supported", true)
                put("features", JSONArray(listOf("gallery", "camera", "files")))
            }.toString()
        }

        @JavascriptInterface
        fun selectFiles(optionsJson: String): String {
            Log.d(TAG, "selectFiles called: $optionsJson")
            // Launch native file picker
            runOnUiThread {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                startActivityForResult(Intent.createChooser(intent, "Select Files"), FILE_PICKER_REQUEST)
            }
            return JSONObject().apply {
                put("status", "picker_launched")
                put("note", "File picker opened - selection will be handled via callback")
            }.toString()
        }

        @JavascriptInterface
        fun checkPaymentSupport(): String {
            Log.d(TAG, "checkPaymentSupport called")
            return JSONObject().apply {
                put("supported", true)
                put("methods", JSONArray(listOf("upi", "card", "netbanking")))
            }.toString()
        }

        @JavascriptInterface
        fun initiatePayment(paramsJson: String): String {
            Log.d(TAG, "initiatePayment called: $paramsJson")
            try {
                val params = JSONObject(paramsJson)
                val method = params.optString("method", "upi")
                
                // For UPI, use the UPI intent system
                if (method == "upi") {
                    val amount = params.optInt("amount", 100) / 100.0  // Convert paise to rupees
                    val uri = Uri.Builder()
                        .scheme("upi")
                        .authority("pay")
                        .appendQueryParameter("pa", "demo@upi")  // Demo VPA
                        .appendQueryParameter("pn", "Native Demo")
                        .appendQueryParameter("am", amount.toString())
                        .appendQueryParameter("tn", "Payment Demo")
                        .appendQueryParameter("cu", "INR")
                        .build()
                    
                    runOnUiThread {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Pay with"))
                    }
                    
                    return JSONObject().apply {
                        put("success", true)
                        put("method", "upi")
                        put("status", "launched")
                        put("note", "UPI payment launched - actual result depends on UPI app")
                    }.toString()
                }
                
                // For other methods, return mock success (real app would integrate SDK)
                return JSONObject().apply {
                    put("success", true)
                    put("method", method)
                    put("paymentId", "pay_demo_${System.currentTimeMillis()}")
                    put("note", "Demo payment - integrate Razorpay SDK for real payments")
                }.toString()
            } catch (e: Exception) {
                return JSONObject().apply {
                    put("success", false)
                    put("error", e.message)
                }.toString()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
