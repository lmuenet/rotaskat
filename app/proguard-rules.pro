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
