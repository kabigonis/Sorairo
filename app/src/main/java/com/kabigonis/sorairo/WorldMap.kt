package com.kabigonis.sorairo

import kotlin.math.cos
import kotlin.math.roundToInt

// Very coarse continent outlines (lon, lat pairs) used for the globe icon and the dotted globe.
// Accuracy is "recognisable at 100px", not cartography.

internal object WorldMap {
    private val northAmerica = floatArrayOf(
        -168f, 65f, -163f, 69f, -156f, 71f, -141f, 69.5f, -125f, 70f, -110f, 68f, -95f, 68f, -85f, 69f,
        -82f, 66f, -90f, 63f, -93f, 59f, -86f, 56f, -80f, 52f, -79f, 57f, -77f, 62f, -70f, 60f,
        -64f, 60f, -61f, 56f, -56f, 52f, -60f, 47f, -66f, 45f, -70f, 42f, -74f, 40f, -76f, 35f,
        -81f, 31f, -80f, 26f, -82f, 27f, -84f, 30f, -90f, 29.5f, -94f, 29f, -97f, 26f, -97.5f, 22f,
        -95f, 18.5f, -91f, 18.5f, -90f, 21f, -87f, 21.5f, -88f, 16f, -84f, 15.5f, -83f, 10f,
        -79f, 9f, -77.5f, 8f, -80f, 7.5f, -84f, 9f, -86f, 11f, -88f, 13f, -92f, 14.5f, -96f, 15.7f,
        -102f, 18f, -105.5f, 20f, -106f, 23f, -110f, 27f, -113f, 31.5f, -117f, 32.5f, -120.5f, 34.5f,
        -124f, 40f, -124f, 46f, -124.5f, 48.5f, -130f, 54f, -135f, 58f, -140f, 59.5f, -148f, 60f,
        -152f, 58.5f, -158f, 56.5f, -163f, 55f, -158f, 58.5f, -162f, 60f, -165f, 62.5f
    )
    private val southAmerica = floatArrayOf(
        -77f, 8f, -72f, 12f, -65f, 10.5f, -60f, 8.5f, -52f, 5f, -50f, 0f, -44f, -2.5f, -35f, -5.5f,
        -35f, -9f, -39f, -14f, -39f, -19f, -42f, -23f, -48f, -26f, -49f, -29f, -53f, -34f, -57f, -35f,
        -58f, -38.5f, -62f, -39f, -65f, -41f, -64f, -45f, -67f, -46.5f, -66f, -48f, -69f, -51f,
        -68.5f, -54f, -72f, -54f, -75f, -50f, -73.5f, -44f, -73f, -38f, -71.5f, -30f, -70.5f, -18f,
        -75f, -15f, -79f, -8f, -81f, -5f, -80f, -1f, -78.5f, 2f, -77.5f, 6.5f
    )
    private val africa = floatArrayOf(
        -17f, 21f, -13f, 27.5f, -9.5f, 30.5f, -6f, 35.8f, 0f, 35.5f, 10f, 37f, 11f, 33.5f, 15f, 32.5f,
        20f, 31f, 25f, 31.7f, 32f, 31f, 32.5f, 29.5f, 35f, 24f, 37.5f, 18f, 40f, 15f, 43f, 12f,
        51f, 11.8f, 51f, 10.5f, 47.5f, 5f, 42f, -1f, 40f, -4f, 39f, -8f, 40.5f, -15f, 35f, -20f,
        35.5f, -24f, 32.5f, -26f, 30f, -31.5f, 26f, -34f, 20f, -34.8f, 18f, -32f, 15f, -27f,
        12f, -18f, 13.5f, -12f, 12f, -5f, 9f, -1f, 9.5f, 3.5f, 6f, 4.5f, 1f, 6f, -4f, 5.2f,
        -8f, 4.5f, -12f, 7.5f, -15f, 11f, -17f, 14.5f, -16.5f, 19f
    )
    private val eurasia = floatArrayOf(
        -9f, 37f, -9f, 43f, -2f, 43.5f, -1.5f, 46f, -4.5f, 48.5f, 2f, 51f, 4f, 52f, 8f, 54f,
        8.5f, 57f, 10.5f, 57f, 12.5f, 56f, 11f, 59f, 5.5f, 58.5f, 5f, 62f, 14f, 67.5f, 20f, 70f,
        28f, 71f, 40f, 67f, 33f, 66.5f, 41f, 64.5f, 44f, 66.5f, 54f, 68.5f, 60f, 69.5f, 70f, 73f,
        80f, 73.5f, 95f, 76f, 105f, 77.5f, 113f, 74f, 130f, 71.5f, 140f, 72.5f, 160f, 70f, 170f, 70f,
        180f, 68.5f, 180f, 65f, 179f, 62.5f, 170f, 60f, 163f, 60.5f, 160f, 54f, 156f, 51f,
        156f, 57f, 150f, 59.5f, 143f, 59f, 137f, 54f, 141f, 52f, 140f, 47f, 135f, 43f, 131f, 42.5f,
        129.5f, 41f, 128f, 39f, 129.5f, 36f, 126.5f, 34.5f, 126f, 37.5f, 124.5f, 39.5f, 121f, 40.5f,
        118f, 39f, 119f, 37.5f, 122.5f, 37f, 121f, 32f, 122f, 30f, 120f, 26f, 117f, 23.5f, 113f, 22f,
        110f, 21f, 108f, 21.5f, 106.5f, 19f, 109f, 15f, 109f, 12f, 105f, 8.5f, 103f, 10.5f,
        100.5f, 13.5f, 99.5f, 10f, 101f, 6f, 103.5f, 2f, 101.5f, 2.5f, 98.5f, 8f, 98f, 15f,
        94.5f, 16f, 94f, 19.5f, 91.5f, 22.5f, 87f, 21f, 85f, 19.5f, 80.5f, 15f, 80f, 10f, 77.5f, 8f,
        76f, 11f, 73f, 17f, 72.8f, 21f, 70f, 21f, 67f, 24.5f, 62f, 25f, 57f, 25.5f, 56.5f, 27f,
        51f, 28f, 48.5f, 30f, 50f, 26f, 51.5f, 24f, 56f, 26f, 56.5f, 24f, 59.5f, 22.5f, 58f, 20f,
        55f, 17f, 52f, 16f, 45f, 12.8f, 43f, 14f, 39f, 21f, 35f, 28f, 34.5f, 29.5f, 35f, 33f,
        36f, 36f, 30f, 36.5f, 27f, 37f, 26f, 40f, 23.5f, 40f, 24f, 38f, 22.5f, 36.5f, 21f, 38.5f,
        19.5f, 40.5f, 19f, 42f, 13.5f, 45.5f, 16f, 41.5f, 18.5f, 40f, 16f, 38f, 15.5f, 40f, 12f, 42f,
        10f, 44f, 7.5f, 43.7f, 3.5f, 43.3f, 3f, 41.8f, 0f, 39.5f, -0.5f, 38f, -2f, 36.8f, -5.5f, 36f
    )
    private val britain = floatArrayOf(
        -5.5f, 50f, 1.5f, 51f, 1.7f, 53f, -0.5f, 54.5f, -2f, 57.5f, -3.5f, 58.6f, -6f, 57.5f,
        -5f, 55f, -3f, 54.5f, -4.5f, 52f
    )
    private val ireland = floatArrayOf(-10f, 52f, -6f, 52f, -6f, 55f, -8.5f, 54.5f, -10f, 52.5f)
    private val iceland = floatArrayOf(-24f, 65.5f, -22f, 66.5f, -15f, 66.3f, -13.5f, 65f, -18f, 63.5f, -22.5f, 64f)
    private val greenland = floatArrayOf(
        -73f, 78f, -60f, 82f, -30f, 83.5f, -20f, 80f, -18f, 75f, -22f, 70f, -33f, 68f, -42f, 60f,
        -48f, 61f, -53f, 66f, -55f, 70f, -58f, 76f, -67f, 76.5f
    )
    private val japan = floatArrayOf(
        130.5f, 31.5f, 132f, 34f, 135f, 34.5f, 140f, 35f, 141.8f, 38.5f, 142f, 40f, 140f, 41f,
        140f, 38f, 137f, 37f, 133f, 35.5f, 131f, 34.5f
    )
    private val sumatra = floatArrayOf(95f, 5.5f, 98f, 4f, 104f, -1.5f, 106f, -5.8f, 102f, -4f, 97f, 1f)
    private val borneo = floatArrayOf(109f, 1.5f, 114f, 4.5f, 117f, 7f, 119f, 5f, 118f, 1f, 116f, -4f, 110f, -3f, 109f, -1f)
    private val java = floatArrayOf(105.5f, -6.5f, 114.5f, -7.7f, 114.5f, -8.7f, 106f, -7.5f)
    private val newGuinea = floatArrayOf(131f, -1f, 141f, -2.5f, 148f, -6f, 150f, -10.5f, 143f, -9f, 138f, -8f, 134f, -4f)
    private val australia = floatArrayOf(
        114f, -22f, 114f, -26f, 115f, -34f, 118f, -35f, 124f, -33.5f, 131f, -31.5f, 135f, -34.8f,
        138f, -35.5f, 140f, -38f, 146f, -39f, 150f, -37.5f, 153f, -31f, 153f, -25f, 149f, -20f,
        146f, -18.5f, 145.5f, -14.5f, 143.5f, -11f, 142f, -11f, 141.5f, -17f, 137f, -15.5f,
        135.5f, -12f, 131f, -11.5f, 129f, -15f, 125f, -14.5f, 122f, -17.5f, 118f, -20.5f
    )
    private val nzNorth = floatArrayOf(173f, -35f, 178.5f, -37.7f, 175.5f, -41.5f, 173f, -39.5f, 174.5f, -37f)
    private val nzSouth = floatArrayOf(172.5f, -40.5f, 174f, -41.5f, 171f, -44.5f, 168f, -46.5f, 166.5f, -45.5f, 170f, -43f)
    private val madagascar = floatArrayOf(49.5f, -12f, 50.5f, -15.5f, 47.5f, -25f, 44f, -24.5f, 43.5f, -21f, 44.5f, -16f, 48f, -13.5f)
    private val antarctica = floatArrayOf(
        -180f, -72f, -150f, -77f, -100f, -73f, -65f, -67f, -60f, -75f, -30f, -78f, 0f, -70f,
        40f, -68f, 80f, -67f, 120f, -66f, 160f, -70f, 180f, -72f, 180f, -90f, -180f, -90f
    )

    val polygons: List<FloatArray> = listOf(
        northAmerica, southAmerica, africa, eurasia, britain, ireland, iceland, greenland, japan,
        sumatra, borneo, java, newGuinea, australia, nzNorth, nzSouth, madagascar, antarctica
    )

    private fun inside(poly: FloatArray, x: Double, y: Double): Boolean {
        var inside = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i].toDouble()
            val yi = poly[2 * i + 1].toDouble()
            val xj = poly[2 * j].toDouble()
            val yj = poly[2 * j + 1].toDouble()
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun isLand(lon: Double, lat: Double): Boolean = polygons.any { inside(it, lon, lat) }

    /** Dots roughly evenly spread over the sphere (radians), with a land flag. Built once on first use. */
    class Dots(val lat: DoubleArray, val lon: DoubleArray, val sinLat: DoubleArray, val cosLat: DoubleArray, val land: BooleanArray) {
        val size: Int get() = lat.size
    }

    val dots: Dots by lazy {
        val la = ArrayList<Double>()
        val lo = ArrayList<Double>()
        val ld = ArrayList<Boolean>()
        var lat = -85
        while (lat <= 85) {
            val n = maxOf(1, (72 * cos(Math.toRadians(lat.toDouble()))).roundToInt())
            for (k in 0 until n) {
                val lon = -180.0 + 360.0 * k / n
                la.add(Math.toRadians(lat.toDouble()))
                lo.add(Math.toRadians(lon))
                ld.add(isLand(lon, lat.toDouble()))
            }
            lat += 5
        }
        val latArr = la.toDoubleArray()
        Dots(
            latArr,
            lo.toDoubleArray(),
            DoubleArray(latArr.size) { Math.sin(latArr[it]) },
            DoubleArray(latArr.size) { Math.cos(latArr[it]) },
            ld.toBooleanArray()
        )
    }
}
