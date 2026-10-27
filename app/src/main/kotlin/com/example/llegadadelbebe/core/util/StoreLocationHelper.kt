package com.example.llegadadelbebe.core.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import androidx.core.content.ContextCompat
import com.example.llegadadelbebe.domain.model.GoogleMapsPlaceInfo
import com.example.llegadadelbebe.domain.model.KnownStoreLocation
import com.example.llegadadelbebe.domain.model.StoreLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object StoreLocationHelper {

    val COMMON_STORE_SUGGESTIONS = listOf(
        "Mercafácil",
        "Farmatodo",
        "Locatel",
        "Automercados Plaza's",
        "Gama Supermercados",
        "Makro",
        "Farmahorro",
        "Tienda de Bebé",
        "Farmacia Los Cedros",
        "Bodegón / Minimarket"
    )

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Calcula la distancia en metros entre dos coordenadas geográficas mediante la fórmula de Haversine.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Radio de la Tierra en metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Busca en la memoria de comercios registrados si el punto GPS actual está dentro del radio
     * de proximidad (por defecto 120 metros) de una tienda ya conocida (ej: Mercafácil).
     */
    fun findNearbyKnownStore(
        latitude: Double,
        longitude: Double,
        knownStores: List<KnownStoreLocation>,
        maxRadiusMeters: Double = 120.0
    ): KnownStoreLocation? {
        if (latitude == 0.0 && longitude == 0.0) return null
        return knownStores
            .map { store -> store to calculateDistanceMeters(latitude, longitude, store.latitude, store.longitude) }
            .filter { (_, dist) -> dist <= maxRadiusMeters }
            .minByOrNull { (_, dist) -> dist }
            ?.first
    }

    /**
     * Extrae las coordenadas GPS incrustadas en los metadatos EXIF de una fotografía.
     */
    fun extractGpsFromExif(exif: ExifInterface): Pair<Double, Double>? {
        return try {
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                val lat = latLong[0].toDouble()
                val lon = latLong[1].toDouble()
                if (lat != 0.0 || lon != 0.0) {
                    Pair(lat, lon)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun captureCurrentStoreLocation(
        context: Context,
        defaultStoreName: String = "",
        knownStores: List<KnownStoreLocation> = emptyList()
    ): StoreLocation = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            return@withContext StoreLocation(name = defaultStoreName)
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext StoreLocation(name = defaultStoreName)

        val location: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }

        if (location == null) {
            return@withContext StoreLocation(name = defaultStoreName)
        }

        val lat = location.latitude
        val lon = location.longitude

        // 1. Verificar si coincide con una tienda previamente guardada en la memoria GPS
        val matchedStore = findNearbyKnownStore(lat, lon, knownStores)
        val googlePlace = resolveGoogleMapsPlace(context, lat, lon)

        val storeName = when {
            defaultStoreName.isNotBlank() -> defaultStoreName
            matchedStore != null -> matchedStore.name
            googlePlace.hasPlaceName -> googlePlace.placeName
            else -> "" // Queda vacío para solicitar al usuario el nombre del comercio si es nuevo
        }

        val finalAddress = when {
            matchedStore != null && matchedStore.address.isNotBlank() -> matchedStore.address
            googlePlace.fullAddress.isNotBlank() -> googlePlace.fullAddress
            else -> String.format(Locale.US, "%.5f, %.5f", lat, lon)
        }

        val isMatch = checkStoreNameMatch(storeName, googlePlace.placeName)

        StoreLocation(
            name = storeName,
            address = finalAddress,
            latitude = lat,
            longitude = lon,
            googleMapsName = googlePlace.placeName,
            isGoogleMapsMatch = isMatch
        )
    }

    /**
     * Consulta el Geocoder de Android / Google Maps para extraer el nombre de establecimiento (POI)
     * y la dirección oficial en las coordenadas dadas.
     */
    fun resolveGoogleMapsPlace(context: Context, latitude: Double, longitude: Double): GoogleMapsPlaceInfo {
        if (latitude == 0.0 && longitude == 0.0) return GoogleMapsPlaceInfo()
        return try {
            val geocoder = Geocoder(context, Locale("es", "VE"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 5)
            if (!addresses.isNullOrEmpty()) {
                var candidateName = ""
                var bestAddress = ""

                for (addr in addresses) {
                    val feat = addr.featureName?.trim() ?: ""
                    val prem = addr.premises?.trim() ?: ""
                    val thorough = addr.thoroughfare?.trim() ?: ""

                    val isNotJustNumber = feat.isNotBlank() && !feat.all { it.isDigit() || it == '-' || it == '#' }
                    val isDistinctFromStreet = feat.isNotBlank() && !feat.equals(thorough, ignoreCase = true)

                    if (prem.isNotBlank()) {
                        candidateName = prem
                        bestAddress = addr.getAddressLine(0) ?: formatSimpleAddress(addr)
                        break
                    } else if (isNotJustNumber && isDistinctFromStreet) {
                        candidateName = feat
                        bestAddress = addr.getAddressLine(0) ?: formatSimpleAddress(addr)
                        break
                    }
                }

                if (bestAddress.isBlank()) {
                    bestAddress = addresses[0].getAddressLine(0) ?: formatSimpleAddress(addresses[0])
                }

                GoogleMapsPlaceInfo(
                    placeName = candidateName,
                    fullAddress = bestAddress,
                    hasPlaceName = candidateName.isNotBlank()
                )
            } else {
                GoogleMapsPlaceInfo(fullAddress = String.format(Locale.US, "%.5f, %.5f", latitude, longitude))
            }
        } catch (_: Exception) {
            GoogleMapsPlaceInfo(fullAddress = String.format(Locale.US, "%.5f, %.5f", latitude, longitude))
        }
    }

    /**
     * Compara de forma tolerante el nombre confirmado por el usuario con el nombre detectado por Google Maps.
     */
    fun checkStoreNameMatch(userStoreName: String, googleMapsName: String): Boolean {
        if (userStoreName.isBlank() || googleMapsName.isBlank()) return false
        val cleanUser = userStoreName.trim().lowercase(Locale.ROOT)
        val cleanGoogle = googleMapsName.trim().lowercase(Locale.ROOT)
        return cleanUser == cleanGoogle ||
            cleanUser.contains(cleanGoogle) ||
            cleanGoogle.contains(cleanUser)
    }

    private fun formatSimpleAddress(addr: android.location.Address): String {
        val thoroughfare = addr.thoroughfare ?: addr.subLocality ?: ""
        val locality = addr.locality ?: addr.subAdminArea ?: ""
        val adminArea = addr.adminArea ?: ""
        return listOf(thoroughfare, locality, adminArea).filter { it.isNotBlank() }.joinToString(", ")
    }

    private fun resolveAddress(context: Context, latitude: Double, longitude: Double): String {
        return resolveGoogleMapsPlace(context, latitude, longitude).fullAddress
    }
}
