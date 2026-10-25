package com.example.llegadadelbebe.core.util

import java.time.LocalDate

/**
 * Información predictiva del signo zodiacal para el bebé niño.
 */
data class ZodiacProfile(
    val signName: String,
    val symbol: String,
    val dateRange: String,
    val element: String,
    val luckyColor: String,
    val boyTraits: String
)

/**
 * Predictor zodiacal basado en la fecha probable de parto (FPP).
 */
object ZodiacPredictor {

    fun predictFromDate(date: LocalDate): ZodiacProfile {
        val month = date.monthValue
        val day = date.dayOfMonth

        return when {
            (month == 3 && day >= 21) || (month == 4 && day <= 19) -> ZodiacProfile(
                signName = "Aries ♈",
                symbol = "El Carnero",
                dateRange = "21 Mar - 19 Abr",
                element = "Fuego 🔥",
                luckyColor = "Rojo y Azul Rey",
                boyTraits = "Niño activo, valiente, curioso y con un espíritu aventurero imparable."
            )
            (month == 4 && day >= 20) || (month == 5 && day <= 20) -> ZodiacProfile(
                signName = "Tauro ♉",
                symbol = "El Toro",
                dateRange = "20 Abr - 20 May",
                element = "Tierra 🌿",
                luckyColor = "Verde Esmeralda y Celeste",
                boyTraits = "Niño dulce, tranquilo, amante de las rutinas, los abrazos y la buena comida."
            )
            (month == 5 && day >= 21) || (month == 6 && day <= 20) -> ZodiacProfile(
                signName = "Géminis ♊",
                symbol = "Los Gemelos",
                dateRange = "21 May - 20 Jun",
                element = "Aire 💨",
                luckyColor = "Amarillo Suave y Menta",
                boyTraits = "Niño comunicativo, risueño, aprende muy rápido y le encanta balbucear."
            )
            (month == 6 && day >= 21) || (month == 7 && day <= 22) -> ZodiacProfile(
                signName = "Cáncer ♋",
                symbol = "El Cangrejo",
                dateRange = "21 Jun - 22 Jul",
                element = "Agua 💧",
                luckyColor = "Blanco Perla y Celeste",
                boyTraits = "Muy apegado a mamá y papá, tierno, cariñoso y con una enorme sensibilidad."
            )
            (month == 7 && day >= 23) || (month == 8 && day <= 22) -> ZodiacProfile(
                signName = "Leo ♌",
                symbol = "El León",
                dateRange = "23 Jul - 22 Ago",
                element = "Fuego 🔥",
                luckyColor = "Dorado y Ámbar",
                boyTraits = "Niño alegre, carismático, le fascina ser el centro de atención y sonreír a todos."
            )
            (month == 8 && day >= 23) || (month == 9 && day <= 22) -> ZodiacProfile(
                signName = "Virgo ♍",
                symbol = "La Virgen",
                dateRange = "23 Ago - 22 Sep",
                element = "Tierra 🌿",
                luckyColor = "Verde Salvia y Crema",
                boyTraits = "Observador, tranquilo, ordenado con sus juguetes y muy perceptivo."
            )
            (month == 9 && day >= 23) || (month == 10 && day <= 22) -> ZodiacProfile(
                signName = "Libra ♎",
                symbol = "La Balanza",
                dateRange = "23 Sep - 22 Oct",
                element = "Aire 💨",
                luckyColor = "Azul Pastel y Rosa Suave",
                boyTraits = "Encantador, sociable, sonriente y amante de la música suave y la armonía."
            )
            (month == 10 && day >= 23) || (month == 11 && day <= 21) -> ZodiacProfile(
                signName = "Escorpio ♏",
                symbol = "El Escorpión",
                dateRange = "23 Oct - 21 Nov",
                element = "Agua 💧",
                luckyColor = "Azul Marino y Borgoña",
                boyTraits = "Mirada profunda, magnético, determinado y sumamente leal a sus padres."
            )
            (month == 11 && day >= 22) || (month == 12 && day <= 21) -> ZodiacProfile(
                signName = "Sagitario ♐",
                symbol = "El Centauro",
                dateRange = "22 Nov - 21 Dic",
                element = "Fuego 🔥",
                luckyColor = "Púrpura y Turquesa",
                boyTraits = "Espíritu libre, optimista, divertido, le encanta explorar y estar al aire libre."
            )
            (month == 12 && day >= 22) || (month == 1 && day <= 19) -> ZodiacProfile(
                signName = "Capricornio ♑",
                symbol = "La Cabra Marina",
                dateRange = "22 Dic - 19 Ene",
                element = "Tierra 🌿",
                luckyColor = "Gris Claro y Azul Pizarra",
                boyTraits = "Niño maduro, paciente, observador y con una adorable seriedad pilla."
            )
            (month == 1 && day >= 20) || (month == 2 && day <= 18) -> ZodiacProfile(
                signName = "Acuario ♒",
                symbol = "El Portador de Agua",
                dateRange = "20 Ene - 18 Feb",
                element = "Aire 💨",
                luckyColor = "Azul Eléctrico y Plateado",
                boyTraits = "Original, independiente, inventivo, siempre fascinado por los objetos y colores."
            )
            else -> ZodiacProfile(
                signName = "Piscis ♓",
                symbol = "Los Peces",
                dateRange = "19 Feb - 20 Mar",
                element = "Agua 💧",
                luckyColor = "Aguamarina y Lavanda",
                boyTraits = "Soñador, tierno, empático, duerme plácidamente con canciones de cuna."
            )
        }
    }
}
