# ATAK loads the plugin lifecycle reflectively from assets/plugin.xml.
-keep class com.btlrckt.bottlerocket.plugin.PluginLifecycle { *; }
-keep class com.btlrckt.bottlerocket.plugin.PointDefenseTool { *; }
# Gson-serialised models
-keep class com.btlrckt.bottlerocket.model.** { *; }
-dontwarn com.atakmap.**
-dontwarn com.atak.**
-dontwarn gov.tak.**
