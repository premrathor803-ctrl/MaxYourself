package com.elevate.launcher.center.content

/**
 * Stick-figure drawings for every exercise. Each drawing has a start pose (A) and, for moving exercises, an end pose (B).
 * The numbers were laid out with a small script and checked by eye: angles are in degrees (0 = pointing down, 90 = forward,
 * 180 = up), lengths are fractions of the picture. Generated file, edit the generator rather than this list.
 */
class Pose(val v: FloatArray) {
    companion object {
        const val HX = 0; const val HY = 1; const val T = 2; const val HD = 3; const val UA = 4; const val FA = 5
        const val UB = 6; const val FB = 7; const val TH = 8; const val SH = 9; const val TB = 10; const val SB = 11
        const val FO = 12; const val FOB = 13; const val BEND = 14; const val FRONT = 15; const val ROT = 16
        const val SIZE = 17
    }
}

/** A prop drawn behind the figure. kind: 0 wall(x) 1 chair(x, seat) 2 bench(x0, x1, top) 3 plumb line(x) 4 doorway(x). */
class Prop(val kind: Int, val a: Float, val b: Float = 0f, val c: Float = 0f)

class Figure(val a: Pose, val b: Pose?, val props: List<Prop>)

object PoseData {
    private fun p(vararg x: Float) = Pose(x)
    private fun fig(a: Pose, b: Pose? = null, vararg props: Prop) = Figure(a, b, props.toList())

    fun get(key: String): Figure? = all[key]

    val all: Map<String, Figure> by lazy {
        val m = HashMap<String, Figure>()
        m["march_in_place"] = fig(p(0.7f, 0.45f, 180f, 180f, -30f, -10f, 35f, 70f, 95f, 0f, 0f, 0f, 70f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 180f, 180f, 35f, 70f, -30f, -10f, 0f, 0f, 95f, 0f, 90f, 70f, 0f, 0f, 0f))
        m["arm_circles"] = fig(p(0.7f, 0.45f, 180f, 180f, -90f, -90f, 90f, 90f, -3f, -3f, 3f, 3f, 90f, 90f, 0f, 1f, 0f), p(0.7f, 0.45f, 180f, 180f, -150f, -150f, 150f, 150f, -3f, -3f, 3f, 3f, 90f, 90f, 0f, 1f, 0f))
        m["hip_circles"] = fig(p(0.735f, 0.452f, 185f, 180f, -40f, 70f, -40f, 70f, -5f, -5f, -5f, -5f, 90f, 90f, 0f, 0f, 0f), p(0.665f, 0.452f, 175f, 180f, -40f, 70f, -40f, 70f, 5f, 5f, 5f, 5f, 90f, 90f, 0f, 0f, 0f))
        m["cat_cow"] = fig(p(0.45f, 0.65f, 113.749f, 40f, 0f, 0f, 0f, 0f, 0f, -90f, 0f, -90f, -90f, -90f, 0.05f, 0f, 0f), p(0.45f, 0.65f, 104f, 125f, 0f, 0f, 0f, 0f, 0f, -90f, 0f, -90f, -90f, -90f, -0.04f, 0f, 0f))
        m["step_jack"] = fig(p(0.7f, 0.45f, 180f, 180f, -5f, -5f, 5f, 5f, -2f, -2f, 2f, 2f, 90f, 90f, 0f, 1f, 0f), p(0.7f, 0.47f, 180f, 180f, -165f, -165f, 165f, 165f, -18f, -18f, 18f, 18f, 90f, 90f, 0f, 1f, 0f))
        m["chair_squat"] = fig(p(0.7f, 0.45f, 180f, 180f, 90f, 90f, 90f, 90f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.5f, 0.65f, 165f, 170f, 90f, 90f, 90f, 90f, 90f, 0f, 90f, 0f, 90f, 90f, 0f, 0f, 0f), Prop(1, 0.49f, 0.665f))
        m["bodyweight_squat"] = fig(p(0.7f, 0.45f, 180f, 180f, 90f, 90f, 90f, 90f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.615f, 0.704f, 140f, 155f, 90f, 90f, 90f, 90f, 95f, -35f, 95f, -35f, 90f, 90f, 0f, 0f, 0f))
        m["reverse_lunge"] = fig(p(0.7f, 0.45f, 180f, 180f, -40f, 70f, -40f, 70f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.636f, 178f, 180f, -40f, 70f, -40f, 70f, 85f, -10f, -35f, -100f, 90f, 30f, 0f, 0f, 0f))
        m["glute_bridge"] = fig(p(0.45f, 0.84f, -90f, -90f, 85f, 95f, 85f, 95f, 125.043f, 51.378f, 125.043f, 51.378f, 90f, 90f, 0f, 0f, 0f), p(0.426f, 0.73f, -64.971f, -70f, 85f, 95f, 85f, 95f, 95.035f, 46.547f, 95.035f, 46.547f, 90f, 90f, 0f, 0f, 0f))
        m["calf_raise"] = fig(p(0.7f, 0.45f, 180f, 180f, -40f, 70f, -40f, 70f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.725f, 0.396f, 180f, 180f, -40f, 70f, -40f, 70f, 0f, 0f, 0f, 0f, 40f, 40f, 0f, 0f, 0f))
        m["wall_sit"] = fig(p(0.5f, 0.65f, 180f, 180f, 20f, 85f, 20f, 85f, 90f, 0f, 90f, 0f, 90f, 90f, 0f, 0f, 0f), null, Prop(0, 0.46f))
        m["wall_pushup"] = fig(p(0.757f, 0.544f, 140f, 140f, 91.409f, 88.497f, 91.409f, 88.497f, -40f, -40f, -40f, -40f, 90f, 90f, 0f, 0f, 0f), p(0.824f, 0.615f, 126f, 126f, 160.291f, 77.3f, 160.291f, 77.3f, -54f, -54f, -54f, -54f, 90f, 90f, 0f, 0f, 0f), Prop(0, 1.254f))
        m["incline_pushup"] = fig(p(0.788f, 0.553f, 138f, 138f, 1.409f, -1.503f, 1.409f, -1.503f, -42f, -42f, -42f, -42f, 90f, 90f, 0f, 0f, 0f), p(0.852f, 0.626f, 124f, 124f, 14.699f, -76.967f, 14.699f, -76.967f, -56f, -56f, -56f, -56f, 90f, 90f, 0f, 0f, 0f), Prop(2, 0.862f, 1.262f, 0.67f))
        m["knee_pushup"] = fig(p(0.808f, 0.715f, 132.37f, 132.37f, 1.409f, -1.503f, 1.409f, -1.503f, -47.63f, -100f, -47.63f, -100f, -90f, -90f, 0f, 0f, 0f), p(0.854f, 0.802f, 104f, 104f, 13.292f, -107.229f, 13.292f, -107.229f, -76f, -100f, -76f, -100f, -90f, -90f, 0f, 0f, 0f))
        m["pushup"] = fig(p(0.77f, 0.662f, 118.015f, 118.015f, 1.409f, -1.503f, 1.409f, -1.503f, -61.985f, -61.985f, -61.985f, -61.985f, 90f, 90f, 0f, 0f, 0f), p(0.813f, 0.794f, 98f, 98f, 25.838f, -110.345f, 25.838f, -110.345f, -82f, -82f, -82f, -82f, 90f, 90f, 0f, 0f, 0f))
        m["superman"] = fig(p(0.5f, 0.84f, 90f, 90f, 90f, 90f, 90f, 90f, -90f, -90f, -90f, -90f, -90f, -90f, 0f, 0f, 0f), p(0.5f, 0.84f, 108f, 112f, 118f, 118f, 118f, 118f, -102f, -104f, -102f, -104f, -100f, -100f, 0f, 0f, 0f))
        m["scap_squeeze"] = fig(p(0.7f, 0.45f, 172f, 150f, 15f, 30f, 15f, 30f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 181f, 180f, -25f, -5f, -25f, -5f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f))
        m["wall_angel"] = fig(p(0.56f, 0.45f, 180f, 180f, -8f, -3f, -8f, -3f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.56f, 0.45f, 180f, 180f, 178f, 178f, 178f, 178f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), Prop(0, 0.5f))
        m["chin_tuck"] = fig(p(0.7f, 0.45f, 178f, 145f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 181f, 180f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f))
        m["standing_posture_check"] = fig(p(0.7f, 0.45f, 168f, 150f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 180f, 180f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), Prop(3, 0.7f))
        m["shoulder_rolls"] = fig(p(0.7f, 0.45f, 180f, 180f, -18f, -8f, -18f, -8f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 180f, 180f, 18f, 8f, 18f, 8f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f))
        m["dead_bug"] = fig(p(0.55f, 0.84f, -90f, -90f, 180f, 180f, 180f, 180f, 180f, 90f, 180f, 90f, 90f, 90f, 0f, 0f, 0f), p(0.55f, 0.84f, -90f, -90f, -90f, -90f, 180f, 180f, 180f, 90f, 88f, 88f, 90f, 90f, 0f, 0f, 0f))
        m["bird_dog"] = fig(p(0.45f, 0.65f, 113.749f, 133.749f, 0f, 0f, 0f, 0f, 0f, -90f, 0f, -90f, -90f, -90f, 0f, 0f, 0f), p(0.45f, 0.65f, 113.749f, 133.749f, 0f, 0f, 90f, 90f, -95f, -95f, 0f, -90f, -95f, -90f, 0f, 0f, 0f))
        m["knee_plank"] = fig(p(0.756f, 0.78f, 110.354f, 110.354f, 0f, 90f, 0f, 90f, -69.646f, -100f, -69.646f, -100f, -90f, -90f, 0f, 0f, 0f), null)
        m["plank"] = fig(p(0.748f, 0.753f, 104.03f, 104.03f, 0f, 90f, 0f, 90f, -75.97f, -75.97f, -75.97f, -75.97f, 90f, 90f, 0f, 0f, 0f), null)
        m["side_plank_knee"] = fig(p(0.756f, 0.78f, 110.354f, 110.354f, 0f, 0f, 180f, 180f, -69.646f, -100f, -69.646f, -100f, -90f, -90f, 0f, 0f, 0f), null)
        m["curl_up"] = fig(p(0.55f, 0.84f, -90f, -90f, 85f, 85f, 85f, 85f, 125f, 51.422f, 125f, 51.422f, 90f, 90f, 0f, 0f, 0f), p(0.55f, 0.84f, -125f, -130f, 80f, 80f, 80f, 80f, 125f, 51.422f, 125f, 51.422f, 90f, 90f, 0f, 0f, 0f))
        m["knee_to_elbow"] = fig(p(0.7f, 0.45f, 180f, 180f, 130f, -110f, 130f, -110f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 165f, 165f, 45f, -130f, 45f, -130f, 100f, 0f, 100f, 0f, 60f, 60f, 0f, 0f, 0f))
        m["shadow_boxing"] = fig(p(0.7f, 0.47f, 178f, 180f, 80f, 85f, 40f, 155f, 25.526f, 3.961f, -12.557f, -22.494f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.47f, 178f, 180f, 40f, 155f, 80f, 85f, 25.526f, 3.961f, -12.557f, -22.494f, 90f, 90f, 0f, 0f, 0f))
        m["hamstring_stretch"] = fig(p(0.7f, 0.45f, 180f, 180f, 20f, 40f, 20f, 40f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.644f, 0.454f, 80f, 70f, -45.017f, 38.214f, -45.017f, 38.214f, 8f, 8f, 8f, 8f, 90f, 90f, 0f, 0f, 0f))
        m["quad_stretch"] = fig(p(0.7f, 0.45f, 180f, 180f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.7f, 0.45f, 180f, 180f, 0f, 0f, -30.149f, -27.237f, 0f, 0f, -137.315f, -9.906f, 90f, 90f, 0f, 0f, 0f))
        m["hip_flexor_stretch"] = fig(p(0.6f, 0.65f, 180f, 180f, -40f, 70f, -40f, 70f, 90f, 0f, 0f, -90f, 90f, -90f, 0f, 0f, 0f), null)
        m["childs_pose"] = fig(p(0.32f, 0.72f, 81f, 75f, 75.867f, 72.954f, 75.867f, 72.954f, 49f, -90f, 49f, -90f, -90f, -90f, 0f, 0f, 0f), null)
        m["doorway_chest_stretch"] = fig(p(0.8f, 0.45f, 172f, 175f, 90f, 180f, 90f, 180f, -8f, -8f, -8f, -8f, 90f, 90f, 0f, 0f, 0f), null, Prop(4, 1.016f))
        m["seated_back_extension"] = fig(p(0.5f, 0.65f, 160f, 140f, 0f, 90f, 0f, 90f, 90f, 0f, 90f, 0f, 90f, 90f, 0f, 0f, 0f), p(0.5f, 0.65f, 186f, 186f, -40f, 70f, -40f, 70f, 90f, 0f, 90f, 0f, 90f, 90f, 0f, 0f, 0f), Prop(1, 0.49f, 0.665f))
        m["neck_side_stretch"] = fig(p(0.7f, 0.45f, 180f, 180f, -4f, -4f, 4f, 4f, -3f, -3f, 3f, 3f, 90f, 90f, 0f, 1f, 0f), p(0.7f, 0.45f, 180f, 152f, -4f, -4f, 111.423f, -101.481f, -3f, -3f, 3f, 3f, 90f, 90f, 0f, 1f, 0f))
        m["calf_stretch_wall"] = fig(p(0.6f, 0.504f, 150f, 145f, 102.091f, 73.136f, 102.091f, 73.136f, 40.045f, 14.869f, -30f, -30f, 90f, 90f, 0f, 0f, 0f), null, Prop(0, 1.05f))
        m["supine_twist"] = fig(p(0.55f, 0.5f, 180f, 180f, -90f, -90f, 90f, 90f, 0f, 0f, 0f, 0f, 90f, 90f, 0f, 1f, -90f), p(0.55f, 0.5f, 180f, 180f, -90f, -90f, 90f, 90f, 100f, 70f, 100f, 70f, 90f, 90f, 0f, 1f, -90f))
        m["knees_to_chest"] = fig(p(0.55f, 0.84f, -90f, -90f, 85f, 95f, 85f, 95f, 125f, 51.422f, 125f, 51.422f, 90f, 90f, 0f, 0f, 0f), p(0.55f, 0.84f, -90f, -90f, 157.208f, 80.676f, 157.208f, 80.676f, -150f, 60f, -150f, 60f, 90f, 90f, 0f, 0f, 0f))
        m["figure4_stretch"] = fig(p(0.55f, 0.84f, -90f, -90f, 85f, 95f, 85f, 95f, 125f, 51.422f, 125f, 51.422f, 90f, 90f, 0f, 0f, 0f), p(0.55f, 0.84f, -90f, -90f, 153.609f, 112.199f, 153.609f, 112.199f, 102.575f, -118.835f, 180f, 90f, 90f, 90f, 0f, 0f, 0f))
        m["deep_breathing"] = fig(p(0.55f, 0.84f, -90f, -90f, 156.283f, 44.772f, 156.283f, 44.772f, 125f, 51.422f, 125f, 51.422f, 90f, 90f, 0f, 0f, 0f), p(0.55f, 0.84f, -90f, -90f, 156.283f, 44.772f, 156.283f, 44.772f, 125f, 51.422f, 125f, 51.422f, 90f, 90f, -0.03f, 0f, 0f))
        m
    }
}
