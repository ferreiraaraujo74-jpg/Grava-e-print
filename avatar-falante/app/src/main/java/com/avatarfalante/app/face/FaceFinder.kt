package com.avatarfalante.app.face

import android.graphics.Bitmap
import android.graphics.PointF
import com.avatarfalante.app.anim.FaceGeom
import com.avatarfalante.app.anim.P
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/** Encontra o rosto e seus contornos com o ML Kit (gratuito, roda no aparelho, sem internet). */
object FaceFinder {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setMinFaceSize(0.15f)
                .build()
        )
    }

    sealed interface Result {
        data class Found(val geom: FaceGeom) : Result
        data object NoFace : Result
        data object Unusable : Result
    }

    suspend fun find(bmp: Bitmap): Result {
        val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
        val f = faces.filter { it.getContour(FaceContour.UPPER_LIP_BOTTOM) != null }
            .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?: return if (faces.isEmpty()) Result.NoFace else Result.Unusable
        fun c(type: Int): List<P> = f.getContour(type)?.points.orEmpty().map { p: PointF -> P(p.x, p.y) }
        val parts = listOf(FaceContour.FACE, FaceContour.UPPER_LIP_TOP, FaceContour.UPPER_LIP_BOTTOM,
            FaceContour.LOWER_LIP_TOP, FaceContour.LOWER_LIP_BOTTOM, FaceContour.LEFT_EYE, FaceContour.RIGHT_EYE)
        if (parts.any { c(it).size < 3 }) return Result.Unusable
        val g = FaceGeom(
            oval = c(FaceContour.FACE),
            upperLipOuter = c(FaceContour.UPPER_LIP_TOP),
            upperLipInner = c(FaceContour.UPPER_LIP_BOTTOM),
            lowerLipInner = c(FaceContour.LOWER_LIP_TOP),
            lowerLipOuter = c(FaceContour.LOWER_LIP_BOTTOM),
            eyeA = c(FaceContour.LEFT_EYE),
            eyeB = c(FaceContour.RIGHT_EYE),
            browA = c(FaceContour.LEFT_EYEBROW_TOP),
            browB = c(FaceContour.RIGHT_EYEBROW_TOP),
        )
        // rosto muito de perfil não anima bem
        val yaw = kotlin.math.abs(f.headEulerAngleY)
        return if (g.isPlausible() && yaw < 35f) Result.Found(g) else Result.Unusable
    }
}
