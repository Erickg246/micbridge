#!/bin/sh
set -eu
# The build environment may use a restrictive umask. Make the installed
# application and launcher readable by normal desktop users.
chmod -R a+rX /opt/MicBridge
chmod 644 /usr/share/applications/micbridge.desktop
chmod 644 /usr/share/icons/hicolor/512x512/apps/micbridge.png
# Installed by dpkg as root. Chromium's helper is isolated inside the app directory.
chown root:root /opt/MicBridge/chrome-sandbox
chmod 4755 /opt/MicBridge/chrome-sandbox
