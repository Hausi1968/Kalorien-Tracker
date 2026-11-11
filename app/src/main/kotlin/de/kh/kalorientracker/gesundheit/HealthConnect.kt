package de.kh.kalorientracker.gesundheit

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import de.kh.kalorientracker.data.KalorienRepository
import de.kh.kalorientracker.data.Quelle
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Verbindung zu Health Connect. Samsung Health (und andere Apps) legen Gewicht und verbrannte Kalorien
 * dort ab; wir lesen nur, schreiben nichts.
 */
class HealthConnect(private val context: Context, private val repo: KalorienRepository) {

    enum class Status { NICHT_VERFUEGBAR, INSTALLIEREN, BEREIT }

    val berechtigungen: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    fun repoQuellen() = repo.quellen

    fun status(): Status = when (HealthConnectClient.getSdkStatus(context, ANBIETER)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.BEREIT
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.INSTALLIEREN
        else -> Status.NICHT_VERFUEGBAR
    }

    /** Öffnet den Play Store, um Health Connect zu installieren oder zu aktualisieren (Android 13 und älter). */
    fun installierenIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$ANBIETER&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }

    fun berechtigungAnfragen(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun hatBerechtigung(): Boolean =
        status() == Status.BEREIT && runCatching {
            client().permissionController.getGrantedPermissions().containsAll(berechtigungen)
        }.getOrDefault(false)

    /**
     * Holt Gewicht (letzte 60 Tage) und aktive Kalorien (letzte 14 Tage) und legt sie in der App ab.
     * Tut nichts, wenn der Nutzer Health Connect nicht als Quelle gewählt hat oder die Erlaubnis fehlt.
     * Gibt die Anzahl übernommener Werte zurück oder null, wenn nicht abgeglichen wurde.
     */
    suspend fun abgleichen(): Int? {
        val wahl = repo.quellen.wahl.value
        if (!wahl.brauchtHealthConnect || !hatBerechtigung()) return null
        val zone = ZoneId.systemDefault()
        val heute = LocalDate.now(zone)
        val c = client()

        val gewichte = if (wahl.gewicht != Quelle.APP) {
            val von = heute.minusDays(60)
            val antwort = c.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    TimeRangeFilter.between(von.atStartOfDay(zone).toInstant(), java.time.Instant.now()),
                ),
            )
            antwort.records
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, liste) -> liste.maxBy { it.time }.let { it.time.toEpochMilli() to it.weight.inKilograms } }
        } else emptyMap()

        val aktiv = if (wahl.verbrannt != Quelle.APP) {
            val von = heute.minusDays(14)
            c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(von.atStartOfDay(), heute.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).associate { tag ->
                tag.startTime.toLocalDate() to (tag.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0)
            }
        } else emptyMap()

        // Was nicht gewählt ist, wird nicht angefasst; ab dem ältesten abgefragten Tag ersetzen
        val ab = heute.minusDays(if (wahl.gewicht != Quelle.APP) 60 else 14)
        repo.uebernehmeHealthConnect(ab, gewichte, aktiv)
        if (wahl.gewicht != Quelle.APP && gewichte.isNotEmpty()) repo.zielNachGewicht()
        return gewichte.size + aktiv.count { it.value >= 1 }
    }

    companion object {
        const val ANBIETER = "com.google.android.apps.healthdata"
    }
}
