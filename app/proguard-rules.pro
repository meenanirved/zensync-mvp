# Add project specific ProGuard rules here.
# Keep GeckoView / Android Components classes used via reflection-free APIs.
-keep class org.mozilla.geckoview.** { *; }
-keep class mozilla.components.** { *; }
-keep class mozilla.appservices.** { *; }
