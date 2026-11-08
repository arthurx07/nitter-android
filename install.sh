#!/bin/env sh

# 1. Install base dependencies from Arch official repos
doas pacman -S jdk17-openjdk gradle android-udev # android-tools

# 2. Configure user permissions for ADB device access
doas usermod -aG adbusers $USER
newgrp adbusers

# 3. Install SDK command-line tools from AUR
paru -S android-sdk-cmdline-tools-latest # android-sdk-platform-tools
doas chown -R $USER:adbusers /opt/android-sdk
source ~/.bashrc

# 4. SDK paths
export ANDROID_HOME=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 5. Install API 36 platform components
android sdk install "platforms;android-36" "build-tools;36.0.0" "platform-tools" # previously sdkmanager

# 6. Configure local project properties
echo "sdk.dir=/opt/android-sdk" > local.properties
touch app/proguard-rules.pro

# 7. Generate release keystore
keytool -genkeypair -v \
  -keystore release.jks \
  -alias nitter \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000

# 8. Create properties file for automated release signing
cat <<EOF > keystore.properties
storeFile=release.jks
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=nitter
keyPassword=YOUR_KEYSTORE_PASSWORD
EOF

# 9. Build the release or debug APK
gradle assembleRelease # or: gradle assembleDebug

# 10.
# i) Settings -> About phone -> tap build number 7 times until Developer mode is enabled
# ii) Settings -> System -> Developer options -> Enable USB debugging (best disable after this)
# iii) Unlock your phone

# 11. Install to device
adb devices
adb install app/build/outputs/apk/release/app-release.apk
