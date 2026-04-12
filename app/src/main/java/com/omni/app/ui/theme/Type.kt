package com.omni.app.ui.theme

import android.util.Log
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.omni.app.R

private const val TAG = "TypographySafe"

val InterFont = FontFamily(
    Font(R.font.inter_24pt_regular, FontWeight.Normal),
    Font(R.font.inter_24pt_medium, FontWeight.Medium),
    Font(R.font.inter_24pt_semibold, FontWeight.SemiBold),
    Font(R.font.inter_24pt_bold, FontWeight.Bold)
)

val RobotoFont = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_semibold, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val PoppinsFont = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

val SerifFont = FontFamily.Serif
val MonospaceFont = FontFamily.Monospace

fun getTypography(fontFamily: FontFamily): Typography {
    val baseline = Typography()
    return try {
        Typography(
            displayLarge = baseline.displayLarge.copy(fontFamily = fontFamily),
            displayMedium = baseline.displayMedium.copy(fontFamily = fontFamily),
            displaySmall = baseline.displaySmall.copy(fontFamily = fontFamily),
            headlineLarge = baseline.headlineLarge.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.5).sp
            ),
            headlineMedium = baseline.headlineMedium.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp
            ),
            headlineSmall = baseline.headlineSmall.copy(fontFamily = fontFamily),
            titleLarge = baseline.titleLarge.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 28.sp
            ),
            titleMedium = baseline.titleMedium.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp
            ),
            titleSmall = baseline.titleSmall.copy(fontFamily = fontFamily),
            bodyLarge = baseline.bodyLarge.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp
            ),
            bodyMedium = baseline.bodyMedium.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp
            ),
            bodySmall = baseline.bodySmall.copy(fontFamily = fontFamily),
            labelLarge = baseline.labelLarge.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp
            ),
            labelMedium = baseline.labelMedium.copy(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp
            ),
            labelSmall = baseline.labelSmall.copy(fontFamily = fontFamily)
        )
    } catch (e: Exception) {
        Log.e(TAG, "Error creating typography, falling back to default", e)
        if (fontFamily == FontFamily.Default) {
            baseline
        } else {
            getTypography(FontFamily.Default)
        }
    }
}
