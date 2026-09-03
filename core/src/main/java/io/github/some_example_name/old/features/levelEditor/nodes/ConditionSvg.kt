package io.github.some_example_name.old.features.levelEditor.nodes

/**
 * Геометрия оригинального Condition.svg (9-slice). SVG не перерисовывается —
 * только его фиксированные части (шапка, губа, низ) сжимаются/растягиваются,
 * а полости if/else растут по высоте под содержимое.
 *
 * Оригинал: viewBox="16 16 168 168" (видимая область x 16..184, y 16..184).
 * В пользовательских координатах y полосы (device y @REF168 = uy-16):
 *   шапка          0..66
 *   if-полость    66..81
 *   губа (else)   81..129
 *   else-полость 129..145
 *   низ          145..168
 */
object ConditionSvg {
    const val SVG = "nodes/Condition.svg"

    private const val REF = 168f
    private const val HEAD_BOT = 66f
    private const val IF_BOT = 81f
    private const val LIP_BOT = 129f
    private const val ELSE_BOT = 145f

    /** Фиксированная высота шапки/губы/низа в РАЗМЕРЕ SVG (ref 168). */
    private const val HEAD_H0 = HEAD_BOT
    private const val LIP_H0 = LIP_BOT - IF_BOT
    private const val BOTTOM_H0 = REF - ELSE_BOT

    const val MIN_SLOT = 40f
    const val BASE_WIDTH = 250f

    /** Масштаб по ширине: коэффициент, на который масштабируются фиксированные части. */
    private fun s(width: Float) = width / REF

    fun headerH(width: Float) = HEAD_H0 * s(width)
    fun lipH(width: Float) = LIP_H0 * s(width)
    fun bottomH(width: Float) = BOTTOM_H0 * s(width)
    fun fixedH(width: Float) = (HEAD_H0 + LIP_H0 + BOTTOM_H0) * s(width)

    fun height(width: Float, ifH: Float, elseH: Float): Float =
        fixedH(width) + ifH.coerceAtLeast(MIN_SLOT) + elseH.coerceAtLeast(MIN_SLOT)

    /** Высота if-полости от низа шапки. */
    fun ifCavityTop(width: Float): Float = headerH(width)
    /** Y (снизу) начала else-полости = низ губы. */
    fun elseCavityTop(width: Float, ifH: Float): Float =
        headerH(width) + ifH.coerceAtLeast(MIN_SLOT) + lipH(width)
}
