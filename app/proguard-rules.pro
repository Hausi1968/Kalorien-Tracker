# kotlinx.serialization: Serializer der Startdaten-Klassen behalten
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class de.kh.kalorientracker.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class de.kh.kalorientracker.data.**$$serializer { *; }
