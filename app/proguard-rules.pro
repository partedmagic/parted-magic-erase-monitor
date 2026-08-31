# Parted Magic Erase Monitor — R8 keep rules.
#
# No rules are required beyond the defaults in proguard-android-optimize.txt
# and the consumer rules the AndroidX / Compose / coroutines AARs ship with
# themselves.  Reasons, so a future change to the app can be checked against
# them:
#
#   * MainActivity and MonitorService are declared in AndroidManifest.xml.
#     AGP generates keep rules for manifest-declared components, so both
#     survive shrinking and keep their names.
#
#   * JSON is parsed by hand in Model.kt with org.json, reading literal key
#     strings out of JSONObject.  Nothing maps JSON names onto Kotlin
#     property names, so renaming cannot silently break parsing.  Adding
#     Gson, Moshi, kotlinx.serialization or Retrofit later WOULD need rules.
#
#   * The only ::class.java references are NotificationManager (a framework
#     class R8 does not touch) and the two manifest components above, used
#     as Intent targets.
#
#   * No Class.forName, no newInstance, no Serializable/Parcelable, no JNI,
#     no WebView JavascriptInterface, no Resources.getIdentifier.  Resource
#     shrinking is therefore safe as well.
#
# Add any new rules below this line.
