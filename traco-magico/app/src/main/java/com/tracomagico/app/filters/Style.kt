package com.tracomagico.app.filters

enum class Category(val label: String) {
    PENCIL("Lápis"),
    CHARCOAL("Carvão"),
    COLOR("Lápis de cor"),
    INK("Contorno"),
    STENCIL("Stencil"),
    CARTOON("Desenho animado"),
    ART("Artístico"),
}

enum class Kind { PENCIL, CHARCOAL, COLOR_PENCIL, INK, STENCIL, TONES, CARTOON, MANGA, HATCH, ENGRAVING, STIPPLE, HALFTONE, WATERCOLOR }

/**
 * Um estilo = um algoritmo ([kind]) + parâmetros. Raios são para uma imagem de 1000 px
 * e escalam com o tamanho real, para a miniatura e o resultado final ficarem iguais.
 */
data class Style(
    val id: String,
    val name: String,
    val category: Category,
    val kind: Kind,
    val radius: Float = 8f,
    val p1: Float = 1f,
    val p2: Float = 0f,
    val p3: Float = 0f,
    val ink: Int = 0xFF1A1A1A.toInt(),
    val paper: Int = 0xFFFFFFFF.toInt(),
    val mid: Int = 0xFF888888.toInt(),
    val grain: Float = 0f,
)

object Styles {
    private fun c(v: Long) = v.toInt()

    val all: List<Style> = listOf(
        // ---------------- Lápis ----------------
        Style("pencil", "Lápis clássico", Category.PENCIL, Kind.PENCIL, radius = 8f),
        Style("pencil_hb", "Lápis HB suave", Category.PENCIL, Kind.PENCIL, radius = 5f, p1 = 0.6f),
        Style("pencil_2b", "Lápis 2B", Category.PENCIL, Kind.PENCIL, radius = 10f, p1 = 1.4f),
        Style("pencil_6b", "Lápis 6B", Category.PENCIL, Kind.PENCIL, radius = 14f, p1 = 2.2f, grain = 0.15f),
        Style("graphite", "Grafite", Category.PENCIL, Kind.PENCIL, radius = 9f, p1 = 1.3f, ink = c(0xFF2B2F36), grain = 0.3f),
        Style("fine", "Esboço fino", Category.PENCIL, Kind.PENCIL, radius = 3f, p1 = 1.2f),
        Style("wide", "Esboço largo", Category.PENCIL, Kind.PENCIL, radius = 22f, p1 = 1.1f),
        Style("kraft", "Papel kraft", Category.PENCIL, Kind.PENCIL, radius = 9f, p1 = 1.2f,
            ink = c(0xFF3A2A1A), paper = c(0xFFD9B98C), grain = 0.25f),
        Style("aged", "Papel antigo", Category.PENCIL, Kind.PENCIL, radius = 8f, p1 = 1.1f,
            ink = c(0xFF5A3E22), paper = c(0xFFEFE2C4), grain = 0.3f),
        Style("notebook", "Caderno", Category.PENCIL, Kind.PENCIL, radius = 7f, ink = c(0xFF33384A), paper = c(0xFFF7F5EE), grain = 0.1f),
        Style("blue_pencil", "Lápis azul", Category.PENCIL, Kind.PENCIL, radius = 8f, ink = c(0xFF1F4FA8)),
        Style("sanguine", "Sanguínea", Category.PENCIL, Kind.PENCIL, radius = 9f, p1 = 1.2f,
            ink = c(0xFF9A3B26), paper = c(0xFFF4E9DC), grain = 0.2f),
        Style("chalkboard", "Giz no quadro", Category.PENCIL, Kind.PENCIL, radius = 8f, p1 = 1.3f,
            ink = c(0xFFF2F2EA), paper = c(0xFF233127), grain = 0.35f),
        Style("pastel_dark", "Giz pastel no preto", Category.PENCIL, Kind.PENCIL, radius = 9f, p1 = 1.2f,
            ink = c(0xFFFFE6A8), paper = c(0xFF111111), grain = 0.3f),

        // ---------------- Carvão ----------------
        Style("charcoal", "Carvão", Category.CHARCOAL, Kind.CHARCOAL, radius = 16f, grain = 0.45f),
        Style("charcoal_strong", "Carvão intenso", Category.CHARCOAL, Kind.CHARCOAL, radius = 20f, p1 = 1.6f, grain = 0.5f),
        Style("charcoal_smudge", "Carvão esfumado", Category.CHARCOAL, Kind.CHARCOAL, radius = 16f, p2 = 3f, grain = 0.35f),
        Style("conte", "Conté", Category.CHARCOAL, Kind.CHARCOAL, radius = 14f, ink = c(0xFF4A2616), paper = c(0xFFF1E4D3), grain = 0.45f),

        // ---------------- Lápis de cor ----------------
        Style("color", "Lápis de cor", Category.COLOR, Kind.COLOR_PENCIL, radius = 8f, p1 = 1f, p2 = 0.6f),
        Style("color_soft", "Lápis de cor suave", Category.COLOR, Kind.COLOR_PENCIL, radius = 6f, p1 = 0.8f, p2 = 0.4f),
        Style("color_vivid", "Lápis de cor vivo", Category.COLOR, Kind.COLOR_PENCIL, radius = 9f, p1 = 1.4f, p2 = 0.85f),
        Style("pastel", "Giz pastel", Category.COLOR, Kind.COLOR_PENCIL, radius = 10f, p1 = 1.1f, p2 = 0.55f, grain = 0.4f),
        Style("watercolor_pencil", "Aquarela a lápis", Category.COLOR, Kind.COLOR_PENCIL, radius = 8f, p1 = 1.2f, p2 = 0.75f, p3 = 6f),

        // ---------------- Contorno / tinta ----------------
        Style("ink", "Nanquim", Category.INK, Kind.INK, radius = 1f, p2 = 1.5f),
        Style("ink_fine", "Contorno fino", Category.INK, Kind.INK, radius = 0f, p2 = 1f),
        Style("ink_bold", "Contorno grosso", Category.INK, Kind.INK, radius = 3f, p2 = 2f),
        Style("ballpoint", "Caneta azul", Category.INK, Kind.INK, radius = 1f, p2 = 1.5f, ink = c(0xFF1B3D9C), paper = c(0xFFFAFAF7)),
        Style("red_pen", "Caneta vermelha", Category.INK, Kind.INK, radius = 1f, p2 = 1.5f, ink = c(0xFFC0262D)),
        Style("neon", "Neon", Category.INK, Kind.INK, radius = 1f, p1 = 2f, p2 = 1.5f, ink = c(0xFF35F2FF), paper = c(0xFF05060A)),
        Style("neon_pink", "Neon rosa", Category.INK, Kind.INK, radius = 1f, p1 = 2f, p2 = 1.5f, ink = c(0xFFFF4FD8), paper = c(0xFF0A0510)),
        Style("blueprint", "Planta azul", Category.INK, Kind.INK, radius = 1f, p2 = 1.5f, ink = c(0xFFEAF4FF), paper = c(0xFF1D4E89)),
        Style("gold", "Gravura dourada", Category.INK, Kind.INK, radius = 1f, p2 = 1.5f, ink = c(0xFFE2BF5A), paper = c(0xFF101010)),
        Style("white_line", "Linha branca", Category.INK, Kind.INK, radius = 1f, p2 = 1.5f, ink = c(0xFFFFFFFF), paper = c(0xFF000000)),

        // ---------------- Stencil ----------------
        Style("stencil", "Stencil", Category.STENCIL, Kind.STENCIL, radius = 14f),
        Style("stencil_detail", "Stencil detalhado", Category.STENCIL, Kind.STENCIL, radius = 6f),
        Style("stencil_bold", "Stencil forte", Category.STENCIL, Kind.STENCIL, radius = 0f, p1 = 0.55f),
        Style("silhouette", "Silhueta", Category.STENCIL, Kind.STENCIL, radius = 0f, p1 = 0.3f),
        Style("stencil_red", "Stencil vermelho", Category.STENCIL, Kind.STENCIL, radius = 12f, ink = c(0xFFB3141B), paper = c(0xFFF7F0E6)),
        Style("two_tone", "Duas cores", Category.STENCIL, Kind.TONES, p1 = 2f, ink = c(0xFF14213D), paper = c(0xFFF4E3C1)),
        Style("three_tone", "Três tons", Category.STENCIL, Kind.TONES, p1 = 3f, ink = c(0xFF151515), mid = c(0xFF8C8C8C), paper = c(0xFFF5F5F5)),
        Style("street", "Grafite urbano", Category.STENCIL, Kind.TONES, p1 = 3f, ink = c(0xFF111111), mid = c(0xFFD7263D), paper = c(0xFFF2F2F2)),

        // ---------------- Desenho animado ----------------
        Style("cartoon", "Desenho animado", Category.CARTOON, Kind.CARTOON, radius = 3f, p1 = 6f, p2 = 1.3f, p3 = 1.5f),
        Style("cartoon_soft", "Cartoon suave", Category.CARTOON, Kind.CARTOON, radius = 4f, p1 = 8f, p2 = 1.1f, p3 = 1f),
        Style("comic", "Quadrinho", Category.CARTOON, Kind.CARTOON, radius = 3f, p1 = 4f, p2 = 1.4f, p3 = 3f),
        Style("pop", "Pop art", Category.CARTOON, Kind.CARTOON, radius = 3f, p1 = 3f, p2 = 1.9f, p3 = 3f),
        Style("flat", "Pintura chapada", Category.CARTOON, Kind.CARTOON, radius = 5f, p1 = 5f, p2 = 1.2f, p3 = 0f),
        Style("manga", "Mangá", Category.CARTOON, Kind.MANGA, radius = 2f),

        // ---------------- Artístico ----------------
        Style("hatch", "Hachura", Category.ART, Kind.HATCH, radius = 9f, p1 = 0f),
        Style("crosshatch", "Hachura cruzada", Category.ART, Kind.HATCH, radius = 9f, p1 = 1f),
        Style("engraving", "Gravura", Category.ART, Kind.ENGRAVING, radius = 8f),
        Style("stipple", "Pontilhismo", Category.ART, Kind.STIPPLE),
        Style("halftone", "Retícula", Category.ART, Kind.HALFTONE, radius = 7f),
        Style("watercolor", "Aquarela", Category.ART, Kind.WATERCOLOR, radius = 3f, p1 = 9f),
        Style("watercolor_soft", "Aquarela suave", Category.ART, Kind.WATERCOLOR, radius = 5f, p1 = 12f, p2 = 0.5f),
        Style("sepia_sketch", "Sépia artística", Category.ART, Kind.COLOR_PENCIL, radius = 8f, p1 = 0f, p2 = 0.5f,
            paper = c(0xFFF1E3C8), grain = 0.25f),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
