#!/bin/sh
set -eu
# Installed by dpkg as root. Chromium's helper is isolated inside the app directory.
chown root:root /opt/MicBridge/chrome-sandbox
chmod 4755 /opt/MicBridge/chrome-sandbox
