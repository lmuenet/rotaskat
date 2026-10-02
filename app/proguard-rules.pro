# kotlinx.serialization braucht die Serializer der @Serializable-Klassen aus
# dem :shared-Modul. R8 wuerde sie sonst als ungenutzt entfernen.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class io.rotaskat.shared.** {
    *** Companion;
}
-keepclasseswithmembers class io.rotaskat.shared.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.rotaskat.shared.**$$serializer { *; }

# Ktor verweist auf JVM-Klassen, die es unter Android nicht gibt (Debugger-
# Erkennung, slf4j-Bindung). Zur Laufzeit werden sie nie erreicht, R8 wuerde
# den Release-Build sonst wegen fehlender Klassen abbrechen.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
