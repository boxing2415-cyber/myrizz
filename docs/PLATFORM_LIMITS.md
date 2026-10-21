# Android platform limits (and what MyRizz AI does instead)

| You might expect | What Android allows | What the app does |
|---|---|---|
| Read the open WhatsApp/Instagram/Tinder chat automatically | Not possible without an Accessibility Service or screen capture; both are privacy-sensitive, are restricted by Google Play, and the app deliberately does not use them | **You** paste text, use *Share → MyRizz AI*, or pick a screenshot. Nothing is read in the background |
| Type the reply into the chat box | Only the active keyboard (IME) can type into other apps | The Samsung Keyboard stays untouched. Tap **Copy** and paste with a long-press. For text you selected, *Rewrite with MyRizz AI* in the selection menu can **replace** the selection in editable fields |
| Show a bubble over every app | `SYSTEM_ALERT_WINDOW` ("Display over other apps"), granted by the user | Draggable bubble + panel, shown only while you switch it on |
| Panels over banking apps, the lock screen, system settings | Blocked by Android / by those apps | Not supported |
| Take a screenshot of another app silently | Not possible without MediaProjection consent | You take the screenshot normally and pick it with the system Photo Picker (no storage permission needed) |
| Auto-send messages | Never done | Nothing is ever sent for you |

Permissions requested: `INTERNET`, `ACCESS_NETWORK_STATE`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE` (+ `SPECIAL_USE`), `POST_NOTIFICATIONS`.
Not requested: accessibility, storage/media, contacts, microphone, camera, clipboard monitoring.

Samsung One UI notes (S26 Ultra / Android 17): if the bubble disappears, set battery to *Unrestricted* for the app
(Settings → Apps → MyRizz AI → Battery) and allow notifications so the foreground service can stay visible.
