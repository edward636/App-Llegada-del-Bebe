package com.example.llegadadelbebe.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Elementos astrológicos tradicionales.
 */
enum class AstroElement(val displayName: String, val emoji: String) {
    FIRE("Fuego", "🔥"),
    EARTH("Tierra", "🌿"),
    AIR("Aire", "💨"),
    WATER("Agua", "💧")
}

/**
 * Signos zodiacales con sus atributos astrológicos, elementos y regentes.
 */
enum class ZodiacSign(
    val signName: String,
    val symbol: String,
    val element: AstroElement,
    val dateRange: String,
    val boyTraits: String,
    val parentTraits: String
) {
    ARIES(
        "Aries", "♈", AstroElement.FIRE, "21 Mar - 19 Abr",
        "Valiente, enérgico, pionero y con un entusiasmo imparable.",
        "Mamá/Papá protector, decidido, lleno de iniciativa y energía vital."
    ),
    TAURUS(
        "Tauro", "♉", AstroElement.EARTH, "20 Abr - 20 May",
        "Tranquilo, cariñoso, amante del confort y las rutinas seguras.",
        "Paciencia infinita, estabilidad afectiva y proveedor de seguridad."
    ),
    GEMINI(
        "Géminis", "♊", AstroElement.AIR, "21 May - 20 Jun",
        "Curioso, comunicativo, despierto, aprende y sonríe con rapidez.",
        "Estimulante, conversador, divertido, enseña jugando y con alegría."
    ),
    CANCER(
        "Cáncer", "♋", AstroElement.WATER, "21 Jun - 22 Jul",
        "Tierno, profundamente empático, muy apegado al hogar y sus padres.",
        "Nutrición afectiva instintiva, ternura maternal y refugio emocional."
    ),
    LEO(
        "Leo", "♌", AstroElement.FIRE, "23 Jul - 22 Ago",
        "Noble, carismático, alegre, le encanta sentirse amado y ser el centro de atención.",
        "Orgullo amoroso, calidez generosa, protector y líder del hogar."
    ),
    VIRGO(
        "Virgo", "♍", AstroElement.EARTH, "23 Ago - 22 Sep",
        "Observador, metódico, perspicaz, ordenado y atento a cada estímulo.",
        "Cuidado meticuloso de la salud, orden en rutinas, atención al detalle y entrega."
    ),
    LIBRA(
        "Libra", "♎", AstroElement.AIR, "23 Sep - 22 Oct",
        "Dulce, pacífico, sociable, amante de la armonía, los mimos y la música suave.",
        "Búsqueda de equilibrio, diplomacia, crea un ambiente de paz y belleza para el bebé."
    ),
    SCORPIO(
        "Escorpio", "♏", AstroElement.WATER, "23 Oct - 21 Nov",
        "Mirada magnética, conexión intuitiva honda, leal y de carácter firme.",
        "Intuición protectora infalible, lealtad incondicional y vínculo umbilical sagrado."
    ),
    SAGITTARIUS(
        "Sagitario", "♐", AstroElement.FIRE, "22 Nov - 21 Dic",
        "Optimista, aventurero, risueño y con ganas de explorar el mundo.",
        "Entusiasmo contagioso, libertad amorosa, fomenta la valentía y el descubrimiento."
    ),
    CAPRICORN(
        "Capricornio", "♑", AstroElement.EARTH, "22 Dic - 19 Ene",
        "Paciente, perseverante, con una seriedad tierna y gran determinación.",
        "Estructura, madurez, visión a largo plazo y guía firme para la vida."
    ),
    AQUARIUS(
        "Acuario", "♒", AstroElement.AIR, "20 Ene - 18 Feb",
        "Original, inventivo, curioso, libre y con una mirada abierta al futuro.",
        "Crianza innovadora, respeto por la individualidad del niño y estímulo mental."
    ),
    PISCES(
        "Piscis", "♓", AstroElement.WATER, "19 Feb - 20 Mar",
        "Sensible, soñador, tierno, percibe las emociones y duerme con melodías dulces.",
        "Amor incondicional, conexión espiritual, empatía profunda y contención dulce."
    );

    val fullDisplayName: String get() = "$signName $symbol"
    val elementWithEmoji: String get() = "${element.displayName} ${element.emoji}"

    companion object {
        fun fromLongitude(degrees: Double): Pair<ZodiacSign, Double> {
            val normalized = ((degrees % 360.0) + 360.0) % 360.0
            val signIndex = (normalized / 30.0).toInt().coerceIn(0, 11)
            val degreeInSign = normalized % 30.0
            return entries[signIndex] to degreeInSign
        }
    }
}

/**
 * Posición astronómica específica de un punto astral (Sol, Luna o Ascendente).
 */
data class AstralPlacement(
    val sign: ZodiacSign,
    val degreeInSign: Double = 0.0
) {
    val formatted: String
        get() = "${sign.signName} ${sign.symbol} (${String.format(java.util.Locale.US, "%.1f", degreeInSign)}°)"

    val displayName: String
        get() = "${sign.signName} ${sign.symbol}"

    val elementLabel: String
        get() = sign.elementWithEmoji
}

/**
 * Carta natal verificada de un progenitor.
 */
data class NatalChartProfile(
    val personRole: String,
    val birthDate: LocalDate,
    val birthTime: LocalTime,
    val sun: AstralPlacement,
    val moon: AstralPlacement,
    val ascendant: AstralPlacement
)

/**
 * Carta proyectada del bebé según los datos conocidos (FUR, FPP y ventana de término).
 */
data class BabyProjectedChart(
    val babyName: String,
    val fppDate: LocalDate,
    val sun: AstralPlacement,
    val projectedMoon: AstralPlacement,
    val moonPhaseName: String,
    val moonPhaseEmoji: String,
    val moonIlluminationPercent: Int,
    val simulatedAscendant: AstralPlacement,
    val simulatedShiftName: String,
    val isFullTermRangeMultiSign: Boolean = false,
    val earlyArrivalSign: ZodiacSign? = null
)

/**
 * Proyección de la Luna para un día dentro de la ventana de nacimiento (FPP ± 10 días).
 */
data class DayMoonProjection(
    val date: LocalDate,
    val moonSign: ZodiacSign,
    val moonPhaseName: String,
    val moonPhaseEmoji: String,
    val isFppDay: Boolean,
    val harmonyScoreWithDad: Int,
    val harmonyScoreWithMom: Int,
    val highlightReason: String
)

/**
 * Reporte de sinastría y compatibilidad parental entre Padre (Edward) y Madre (Britney).
 */
data class ParentCompatibilityReport(
    val masterBondTitle: String,
    val masterBondDescription: String,
    val sunConnectionDescription: String,
    val moonConnectionDescription: String,
    val ascendantConnectionDescription: String,
    val communicationScore: Int,
    val parentingHarmonyScore: Int,
    val practicalSupportScore: Int,
    val postpartumCooperationTip: String
)

/**
 * Fusión específica de la Luna y el Ascendente del Padre (Edward) con la energía del Bebé.
 */
data class FatherBabyFusionReport(
    val moonFusionTitle: String,
    val moonFusionExplanation: String,
    val ascendantFusionTitle: String,
    val ascendantFusionExplanation: String,
    val fatherRoleBondSummary: String,
    val simulatedAscendantSynergy: String
)

/**
 * Balance de los 4 elementos en la tríada familiar (Papá + Mamá + Bebé).
 */
data class FamilyElementalBalance(
    val airPoints: Int,
    val earthPoints: Int,
    val firePoints: Int,
    val waterPoints: Int,
    val dominantElement: AstroElement,
    val balanceSummary: String
)
