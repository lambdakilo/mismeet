package app.mismeet.protocol

import java.math.BigDecimal
import java.math.RoundingMode

internal object JsonNumber {
    /** Fixed notation, at most [maxDecimals] decimals, trailing zeros removed, never an exponent. */
    fun format(value: Double, maxDecimals: Int): String {
        val rounded = BigDecimal(value).setScale(maxDecimals, RoundingMode.HALF_EVEN)
        return if (rounded.signum() == 0) "0" else rounded.stripTrailingZeros().toPlainString()
    }
}
