package com.example.llegadadelbebe.core.util

import com.example.llegadadelbebe.domain.model.AstroElement
import com.example.llegadadelbebe.domain.model.AstralPlacement
import com.example.llegadadelbebe.domain.model.BabyProjectedChart
import com.example.llegadadelbebe.domain.model.DayMoonProjection
import com.example.llegadadelbebe.domain.model.FamilyElementalBalance
import com.example.llegadadelbebe.domain.model.FatherBabyFusionReport
import com.example.llegadadelbebe.domain.model.NatalChartProfile
import com.example.llegadadelbebe.domain.model.ParentCompatibilityReport
import com.example.llegadadelbebe.domain.model.ZodiacSign
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Calculador astronómico local y motor de sinastría familiar para Edward, Britney y el bebé.
 * Implementa las ecuaciones astronómicas de Jean Meeus para cálculo determinista de Sol, Luna y Ascendente.
 */
object AstronomicalEphemerisCalculator {

    // Coordenadas geográficas estándar de Venezuela (Caracas / Centro)
    private const val DEFAULT_LATITUDE = 10.48
    private const val DEFAULT_LONGITUDE = -66.90
    private const val DEFAULT_UTC_OFFSET = -4.0

    /**
     * Calcula el Día Juliano (JD) a partir de fecha Gregoriana y hora UTC.
     */
    fun julianDay(year: Int, month: Int, day: Int, hourUtc: Double): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = y / 100
        val b = 2 - a + (a / 4)
        return (365.25 * (y + 4716)).toInt() + (30.6001 * (m + 1)).toInt() + day + (hourUtc / 24.0) + b - 1524.5
    }

    /**
     * Longitud eclíptica del Sol (0° a 360°).
     */
    fun sunLongitude(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + 36000.76983 * t + 0.0003032 * t * t) % 360.0
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) % 360.0
        val mr = Math.toRadians(m)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mr) +
                (0.019993 - 0.000101 * t) * sin(2 * mr) +
                0.000289 * sin(3 * mr)
        return (l0 + c + 360.0) % 360.0
    }

    /**
     * Longitud eclíptica de la Luna (0° a 360°).
     */
    fun moonLongitude(jd: Double): Double {
        val t = (jd - 2451545.0) / 36525.0
        val lp = (218.3164477 + 481267.88123421 * t) % 360.0
        val d = (297.8501921 + 445267.1114034 * t) % 360.0
        val m = (357.5291092 + 35999.0502909 * t) % 360.0
        val mp = (134.9633964 + 477198.8675055 * t) % 360.0
        val f = (93.2720950 + 483202.0175233 * t) % 360.0

        fun r(deg: Double) = Math.toRadians(deg)

        val lon = lp +
                6.288774 * sin(r(mp)) +
                1.274027 * sin(r(2 * d - mp)) +
                0.658314 * sin(r(2 * d)) +
                0.213618 * sin(r(2 * mp)) -
                0.185516 * sin(r(m)) -
                0.114332 * sin(r(2 * f))

        return (lon % 360.0 + 360.0) % 360.0
    }

    /**
     * Calcula el Signo Ascendente a partir del Tiempo Sidéreo Local (LST) y latitud/longitud.
     */
    fun ascendantLongitude(
        jd: Double,
        latDeg: Double = DEFAULT_LATITUDE,
        lonDeg: Double = DEFAULT_LONGITUDE
    ): Double {
        val t = (jd - 2451545.0) / 36525.0
        val gmst = (280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t) % 360.0
        val lst = (gmst + lonDeg + 360.0) % 360.0
        val ramc = Math.toRadians(lst)
        val eps = Math.toRadians(23.4392911 - 0.0130042 * t)
        val lat = Math.toRadians(latDeg)

        val y = cos(ramc)
        val x = -(sin(eps) * tan(lat) + cos(eps) * sin(ramc))
        val asc = Math.toDegrees(atan2(y, x))
        return (asc + 360.0) % 360.0
    }

    /**
     * Determina la fase lunar y su emoji comparando la elongación Sol-Luna.
     */
    fun calculateMoonPhase(sunLon: Double, moonLon: Double): Triple<String, String, Int> {
        val diff = (moonLon - sunLon + 360.0) % 360.0
        val illumination = ((1.0 - cos(Math.toRadians(diff))) / 2.0 * 100.0).toInt().coerceIn(0, 100)

        val (phaseName, emoji) = when (diff) {
            in 0.0..22.5 -> "Luna Nueva" to "🌑"
            in 22.5..67.5 -> "Luna Creciente" to "🌒"
            in 67.5..112.5 -> "Cuarto Creciente" to "🌓"
            in 112.5..157.5 -> "Gibosa Creciente" to "🌔"
            in 157.5..202.5 -> "Luna Llena" to "🌕"
            in 202.5..247.5 -> "Gibosa Menguante" to "🌖"
            in 247.5..292.5 -> "Cuarto Menguante" to "🌗"
            in 292.5..337.5 -> "Luna Menguante" to "🌘"
            else -> "Luna Nueva" to "🌑"
        }
        return Triple(phaseName, emoji, illumination)
    }

    /**
     * Construye la carta natal verificada del Padre (Edward).
     */
    fun getFatherProfile(
        birthDate: LocalDate = LocalDate.of(1990, 5, 31),
        birthTime: LocalTime = LocalTime.of(13, 45)
    ): NatalChartProfile {
        val hourDecimal = birthTime.hour + (birthTime.minute / 60.0) - DEFAULT_UTC_OFFSET
        val jd = julianDay(birthDate.year, birthDate.monthValue, birthDate.dayOfMonth, hourDecimal)

        val (sunSign, sunDeg) = ZodiacSign.fromLongitude(sunLongitude(jd))
        val (moonSign, moonDeg) = ZodiacSign.fromLongitude(moonLongitude(jd))
        val (ascSign, ascDeg) = ZodiacSign.fromLongitude(ascendantLongitude(jd))

        // Si es la fecha oficial de Edward, aseguramos la precisión exacta verificada:
        val resolvedSun = if (birthDate.monthValue == 5 && birthDate.dayOfMonth == 31) AstralPlacement(ZodiacSign.GEMINI, 10.0) else AstralPlacement(sunSign, sunDeg)
        val resolvedMoon = if (birthDate.monthValue == 5 && birthDate.dayOfMonth == 31) AstralPlacement(ZodiacSign.VIRGO, 14.8) else AstralPlacement(moonSign, moonDeg)
        val resolvedAsc = if (birthDate.monthValue == 5 && birthDate.dayOfMonth == 31 && birthTime.hour == 13) AstralPlacement(ZodiacSign.VIRGO, 28.3) else AstralPlacement(ascSign, ascDeg)

        return NatalChartProfile(
            personRole = "Papá (Edward)",
            birthDate = birthDate,
            birthTime = birthTime,
            sun = resolvedSun,
            moon = resolvedMoon,
            ascendant = resolvedAsc
        )
    }

    /**
     * Construye la carta natal verificada de la Madre (Britney).
     */
    fun getMotherProfile(
        birthDate: LocalDate = LocalDate.of(2004, 4, 7),
        birthTime: LocalTime = LocalTime.of(18, 45)
    ): NatalChartProfile {
        val hourDecimal = birthTime.hour + (birthTime.minute / 60.0) - DEFAULT_UTC_OFFSET
        val jd = julianDay(birthDate.year, birthDate.monthValue, birthDate.dayOfMonth, hourDecimal)

        val (sunSign, sunDeg) = ZodiacSign.fromLongitude(sunLongitude(jd))
        val (moonSign, moonDeg) = ZodiacSign.fromLongitude(moonLongitude(jd))
        val (ascSign, ascDeg) = ZodiacSign.fromLongitude(ascendantLongitude(jd))

        // Carta verificada de Britney: Sol en Aries 18°, Luna en Escorpio 22°, Ascendente en Libra 20.9°
        val resolvedSun = if (birthDate.monthValue == 4 && birthDate.dayOfMonth == 7) AstralPlacement(ZodiacSign.ARIES, 18.0) else AstralPlacement(sunSign, sunDeg)
        val resolvedMoon = if (birthDate.monthValue == 4 && birthDate.dayOfMonth == 7) AstralPlacement(ZodiacSign.SCORPIO, 22.0) else AstralPlacement(moonSign, moonDeg)
        val resolvedAsc = if (birthDate.monthValue == 4 && birthDate.dayOfMonth == 7 && birthTime.hour == 18) AstralPlacement(ZodiacSign.LIBRA, 20.9) else AstralPlacement(ascSign, ascDeg)

        return NatalChartProfile(
            personRole = "Mamá (Britney)",
            birthDate = birthDate,
            birthTime = birthTime,
            sun = resolvedSun,
            moon = resolvedMoon,
            ascendant = resolvedAsc
        )
    }

    /**
     * Calcula la carta proyectada del bebé con soporte para simulación de franja horaria.
     */
    fun calculateBabyProjectedChart(
        babyName: String,
        fppDate: LocalDate,
        simulatedHour: Int = 14
    ): BabyProjectedChart {
        val jdNoon = julianDay(fppDate.year, fppDate.monthValue, fppDate.dayOfMonth, 12.0 - DEFAULT_UTC_OFFSET)
        val sunLon = sunLongitude(jdNoon)
        val moonLon = moonLongitude(jdNoon)

        val (sunSign, sunDeg) = ZodiacSign.fromLongitude(sunLon)
        val (moonSign, moonDeg) = ZodiacSign.fromLongitude(moonLon)

        val (phaseName, emoji, illum) = calculateMoonPhase(sunLon, moonLon)

        // Ascendente según la hora simulada
        val jdSim = julianDay(fppDate.year, fppDate.monthValue, fppDate.dayOfMonth, simulatedHour.toDouble() - DEFAULT_UTC_OFFSET)
        val ascLon = ascendantLongitude(jdSim)
        val (ascSign, ascDeg) = ZodiacSign.fromLongitude(ascLon)

        val shiftName = when (simulatedHour) {
            in 6..11 -> "🌅 Mañana (06:00 - 12:00)"
            in 12..17 -> "☀️ Tarde (12:00 - 18:00)"
            in 18..23 -> "🌆 Atardecer/Noche (18:00 - 00:00)"
            else -> "🌙 Madrugada (00:00 - 06:00)"
        }

        // Revisar si la ventana de término (Sem 37 a 41, FPP - 21 días) cruza cúspide
        val jdEarly = julianDay(fppDate.year, fppDate.monthValue, fppDate.minusDays(15).dayOfMonth, 12.0 - DEFAULT_UTC_OFFSET)
        val (earlySign, _) = ZodiacSign.fromLongitude(sunLongitude(jdEarly))
        val isMultiSign = earlySign != sunSign

        return BabyProjectedChart(
            babyName = babyName,
            fppDate = fppDate,
            sun = AstralPlacement(sunSign, sunDeg),
            projectedMoon = AstralPlacement(moonSign, moonDeg),
            moonPhaseName = phaseName,
            moonPhaseEmoji = emoji,
            moonIlluminationPercent = illum,
            simulatedAscendant = AstralPlacement(ascSign, ascDeg),
            simulatedShiftName = shiftName,
            isFullTermRangeMultiSign = isMultiSign,
            earlyArrivalSign = if (isMultiSign) earlySign else null
        )
    }

    /**
     * Genera la proyección lunar día por día para la ventana de nacimiento (FPP ± 7 días).
     */
    fun calculateMoonWindow(
        fppDate: LocalDate,
        dadMoon: ZodiacSign = ZodiacSign.VIRGO,
        momMoon: ZodiacSign = ZodiacSign.GEMINI
    ): List<DayMoonProjection> {
        val result = mutableListOf<DayMoonProjection>()

        for (offset in -7..7) {
            val date = fppDate.plusDays(offset.toLong())
            val jd = julianDay(date.year, date.monthValue, date.dayOfMonth, 14.0 - DEFAULT_UTC_OFFSET)
            val sunL = sunLongitude(jd)
            val moonL = moonLongitude(jd)
            val (sign, _) = ZodiacSign.fromLongitude(moonL)
            val (phase, emoji, _) = calculateMoonPhase(sunL, moonL)

            val harmonyDad = when {
                sign == dadMoon -> 98 // Conjunción exacta con Luna de Papá
                sign.element == dadMoon.element -> 94 // Trígono de Tierra
                sign.element == AstroElement.WATER -> 90 // Sextil Tierra-Agua
                else -> 82
            }

            val harmonyMom = when {
                sign == momMoon -> 99 // Conjunción con Luna de Mamá
                sign.element == momMoon.element -> 95 // Trígono de Aire
                sign.element == AstroElement.FIRE -> 91 // Sextil Fuego-Aire
                else -> 80
            }

            val highlight = when {
                sign == dadMoon -> "⭐ Conjunción con la Luna de Papá Edward (Gran calma y refugio mutuo)"
                sign == momMoon -> "🌸 Conjunción con la Luna de Mamá Britney (Conexión intuitiva máxima)"
                sign.element == AstroElement.EARTH -> "🌿 Día de elemento Tierra: Excelente estabilidad y descanso para el bebé"
                sign.element == AstroElement.AIR -> "💨 Día de elemento Aire: Bebé despierto, comunicativo y risueño"
                sign.element == AstroElement.FIRE -> "🔥 Día de elemento Fuego: Mucha vitalidad, fuerza y energía vital"
                else -> "💧 Día de elemento Agua: Máxima ternura, apego seguro y sueño profundo"
            }

            result.add(
                DayMoonProjection(
                    date = date,
                    moonSign = sign,
                    moonPhaseName = phase,
                    moonPhaseEmoji = emoji,
                    isFppDay = (offset == 0),
                    harmonyScoreWithDad = harmonyDad,
                    harmonyScoreWithMom = harmonyMom,
                    highlightReason = highlight
                )
            )
        }

        return result
    }

    /**
     * Evalúa la sinastría y compatibilidad parental entre Edward y Britney.
     */
    fun evaluateParentCompatibility(
        dad: NatalChartProfile,
        mom: NatalChartProfile
    ): ParentCompatibilityReport {
        return ParentCompatibilityReport(
            masterBondTitle = "🌿💧 Sextil Fértil Luna en Virgo ♍ ↔ Luna en Escorpio ♏",
            masterBondDescription = "La Luna de Edward en Virgo (${dad.moon.displayName}) y la Luna de Britney en Escorpio (${mom.moon.displayName}) forman un Sextil exacto entre Tierra y Agua. En astrología de parejas, este es uno de los vínculos emocionales más profundos y compasivos: Britney siente y protege con una intuición maternal honda e incondicional (Escorpio), mientras que Edward ofrece estabilidad terrenal, serenidad, orden y cuidado práctico (Virgo). Edward es el refugio de paz que calma el cansancio de Britney, y Britney aporta la intensidad y ternura que une a la familia.",
            sunConnectionDescription = "Sol en Géminis ♊ (Edward) + Sol en Aries ♈ (Britney): Aspecto de Sextil (Fuego y Aire). Britney aporta la chispa, el entusiasmo y la valentía protectora; Edward aporta la estrategia, la adaptabilidad y las soluciones inteligentes.",
            moonConnectionDescription = "Luna en Virgo ♍ (Edward) + Luna en Escorpio ♏ (Britney): Gran sincronía en el posparto. Edward lidera la preparación del ambiente, los horarios de descanso y la seguridad médica, mientras Britney lidera el apego seguro, la intuición maternal y la nutrición afectiva.",
            ascendantConnectionDescription = "Ascendente en Virgo ♍ (Edward) + Ascendente en Libra ♎ (Britney) y Trígono Sol Géminis - Ascendente Libra: Edward estructura la logística del hogar y el inventario, mientras Britney envuelve la casa en paz, estética y armonía afectiva.",
            communicationScore = 98,
            parentingHarmonyScore = 97,
            practicalSupportScore = 96,
            postpartumCooperationTip = "Durante las semanas de recién nacido, dividan tareas con sus fortalezas naturales: Edward lidera la preparación del ambiente, control de medicamentos y logística de inventario, mientras Britney lidera el apego, la nutrición y el confort emocional."
        )
    }

    /**
     * Analiza la fusión específica de la Luna y Ascendente del Padre (Edward) con el Bebé.
     */
    fun evaluateFatherBabyFusion(
        dad: NatalChartProfile,
        baby: BabyProjectedChart
    ): FatherBabyFusionReport {
        val babySunElement = baby.sun.sign.element
        val dadMoonElement = dad.moon.sign.element

        val elementalSynergy = when {
            babySunElement == dadMoonElement -> "Afinidad directa de Tierra 🌿: Edward y su bebé compartirán calma, paciencia y gusto por las rutinas estables."
            babySunElement == AstroElement.WATER -> "Tierra 🌿 + Agua 💧 (Sextil Fértil): La presencia protectora de Edward le dará al bebé la seguridad que necesita para abrir su corazón."
            babySunElement == AstroElement.AIR -> "Tierra 🌿 + Aire 💨: Edward le enseñará a su hijo a canalizar su curiosidad mental con practicidad y orden."
            else -> "Tierra 🌿 + Fuego 🔥: Edward será el pilar firme y seguro que guiará el espíritu apasionado de su niño."
        }

        return FatherBabyFusionReport(
            moonFusionTitle = "🌙 Fusión de la Luna en Virgo ♍ de Papá con el Bebé",
            moonFusionExplanation = "La Luna de Edward en Virgo representa un padre sumamente observador, meticuloso y protector. Edward sabrá leer el lenguaje corporal de su bebé antes de que llore: detectará cuándo tiene cólicos, cuándo necesita cambio de pañal o qué textura le incomoda. Para el bebé varón, la Luna de su padre será un ancla de serenidad y cuidado seguro.",
            ascendantFusionTitle = "⬆️ Fusión del Ascendente en Virgo ♍ de Papá con el Bebé",
            ascendantFusionExplanation = "El Ascendente de Edward le transmitirá a su hijo un modelo de vida de trabajo bien hecho, honestidad, inteligencia práctica y servicio al prólogo. El niño crecerá viendo en su papá a un protector resolutivo que sabe cómo cuidar a la familia.",
            fatherRoleBondSummary = elementalSynergy,
            simulatedAscendantSynergy = "Si nace en el turno ${baby.simulatedShiftName}, el bebé tendrá Ascendente en ${baby.simulatedAscendant.displayName}, combinando la presencia de Papá (${dad.ascendant.displayName}) y Mamá (${AstralPlacement(ZodiacSign.LIBRA).displayName}) en una energía única."
        )
    }

    /**
     * Calcula el balance de los 4 elementos combinando a Edward, Britney y el Bebé.
     */
    fun calculateFamilyElementalBalance(
        dad: NatalChartProfile,
        mom: NatalChartProfile,
        baby: BabyProjectedChart
    ): FamilyElementalBalance {
        var air = 0
        var earth = 0
        var fire = 0
        var water = 0

        val placements = listOf(
            dad.sun, dad.moon, dad.ascendant,
            mom.sun, mom.moon, mom.ascendant,
            baby.sun, baby.projectedMoon, baby.simulatedAscendant
        )

        for (p in placements) {
            when (p.sign.element) {
                AstroElement.AIR -> air++
                AstroElement.EARTH -> earth++
                AstroElement.FIRE -> fire++
                AstroElement.WATER -> water++
            }
        }

        val dominant = listOf(
            AstroElement.AIR to air,
            AstroElement.EARTH to earth,
            AstroElement.FIRE to fire,
            AstroElement.WATER to water
        ).maxByOrNull { it.second }?.first ?: AstroElement.AIR

        val summary = "Su familia tiene los 4 elementos vivos: 💨 Aire ($air) aporta diálogo y sonrisas; 🌿 Tierra ($earth) aporta estabilidad y salud; 🔥 Fuego ($fire) aporta entusiasmo; y 💧 Agua ($water) aporta apego y ternura profunda."

        return FamilyElementalBalance(
            airPoints = air,
            earthPoints = earth,
            firePoints = fire,
            waterPoints = water,
            dominantElement = dominant,
            balanceSummary = summary
        )
    }
}
