package com.tracomagico.app.data

import android.graphics.Bitmap

/** Imagem que vai para o modo "Traçar" (compartilhada entre telas, só em memória). */
object Work {
    @Volatile var traceImage: Bitmap? = null
}
