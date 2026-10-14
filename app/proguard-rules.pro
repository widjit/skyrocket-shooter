# ATAK loads the plugin lifecycle reflectively from assets/plugin.xml.
-keep class com.skiarkt.shooter.plugin.PluginLifecycle { *; }
-keep class com.skiarkt.shooter.plugin.PointDefenseTool { *; }
# Gson-serialised models
-keep class com.skiarkt.shooter.model.** { *; }
-dontwarn com.atakmap.**
-dontwarn com.atak.**
-dontwarn gov.tak.**
