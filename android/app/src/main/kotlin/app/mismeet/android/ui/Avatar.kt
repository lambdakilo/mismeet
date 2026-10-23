package app.mismeet.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import app.mismeet.android.store.Contact

/** A round picture of a contact: the photo the user chose, or the first letter of the name on a colour from the key. */
fun avatarBitmap(contact: Contact, photo: Bitmap?, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centre = size / 2f
    val border = size * 0.07f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.argb(60, 0, 0, 0)
    canvas.drawCircle(centre, centre, centre, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(centre, centre, centre - 1f, paint)
    val inner = RectF(border, border, size - border, size - border)
    if (photo != null) {
        paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setRectToRect(RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()), inner, Matrix.ScaleToFit.FILL) })
        }
        canvas.drawOval(inner, paint)
    } else {
        paint.color = Color.HSVToColor(floatArrayOf(((contact.id.take(6).toIntOrNull(16) ?: 0) % 360).toFloat(), 0.5f, 0.75f))
        canvas.drawOval(inner, paint)
        paint.color = Color.WHITE
        paint.textSize = size * 0.42f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val name = contact.name.trim()
        val letter = if (name.isEmpty()) "?" else String(Character.toChars(name.codePointAt(0))).uppercase()
        canvas.drawText(letter, centre, centre - (paint.descent() + paint.ascent()) / 2, paint)
    }
    return bitmap
}

/** The phone's own position: a blue dot in a white ring. */
fun ownPositionBitmap(size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centre = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = Color.argb(60, 0, 0, 0)
    canvas.drawCircle(centre, centre, centre, paint)
    paint.color = Color.WHITE
    canvas.drawCircle(centre, centre, centre - 1f, paint)
    paint.color = Color.rgb(0, 122, 255)
    canvas.drawCircle(centre, centre, centre * 0.7f, paint)
    return bitmap
}

@Composable
fun Avatar(contact: Contact, photo: Bitmap?, size: Dp, modifier: Modifier = Modifier) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(contact.id, contact.name, photo, pixels) { avatarBitmap(contact, photo, pixels) }
    Image(bitmap.asImageBitmap(), contentDescription = contact.name, modifier = modifier)
}
