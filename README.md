# Native Demo Android WebView App

## Quick Start

### Prerequisites
- Android Studio (latest version)
- Android SDK 24+
- Physical Android device or emulator

### Setup Steps

1. **Open in Android Studio**
   - Open Android Studio
   - Select "Open" and navigate to this folder
   - Wait for Gradle sync to complete

2. **Update the URL**
   - Open `app/src/main/java/com/nativedemo/MainActivity.kt`
   - Replace `YOUR_URL_HERE` with your actual URL:
     - For ngrok: `https://xxxx.ngrok-free.app`
     - For local: `http://10.0.2.2:3000` (emulator) or `http://YOUR_IP:3000` (device)

3. **Run the App**
   - Connect your Android device via USB (enable USB debugging)
   - Or start an emulator
   - Click the green "Run" button

### Testing with ngrok

```bash
# Terminal 1: Start your Next.js app
cd ../native-demo
npm run dev

# Terminal 2: Create HTTPS tunnel
npx ngrok http 3000
```

Copy the `https://xxxx.ngrok-free.app` URL and paste it in MainActivity.kt

### Features Implemented

- ✅ Camera capture via native intent
- ✅ Location via FusedLocationProvider
- ✅ UPI app detection
- ✅ File picker
- ✅ Permission handling
- ⏳ Push notifications (requires Firebase setup)
- ⏳ Payments (requires Razorpay SDK)

### Native Bridge Methods

The app exposes these methods to JavaScript via `window.NativeBridge`:

```javascript
// Camera
NativeBridge.checkCameraSupport()
NativeBridge.requestCameraPermission()
NativeBridge.capturePhoto(optionsJson)

// Location
NativeBridge.checkLocationSupport()
NativeBridge.requestLocationPermission()
NativeBridge.getCurrentLocation(optionsJson)

// UPI
NativeBridge.checkUPISupport()
NativeBridge.getUPIApps()

// Others
NativeBridge.checkPushSupport()
NativeBridge.checkFilePickerSupport()
NativeBridge.checkPaymentSupport()
```

### Callbacks

For async operations, the native code calls these JavaScript functions:

```javascript
window.onCameraResult = (result) => { ... }
window.onLocationResult = (result) => { ... }
```
