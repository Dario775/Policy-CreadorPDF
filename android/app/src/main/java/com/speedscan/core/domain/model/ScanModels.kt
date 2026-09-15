package com.speedscan.core.domain.model

data class Document(
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val thumbnailPath: String,
    val pageCount: Int
)

data class Page(
    val id: Long = 0,
    val documentId: Long,
    val originalImagePath: String,
    val processedImagePath: String,
    val orderIndex: Int
)

data class SignaturePlacement(
    val imagePath: String,
    val horizontal: SignatureHorizontal = SignatureHorizontal.RIGHT,
    val vertical: SignatureVertical = SignatureVertical.BOTTOM,
    val widthRatio: Float = 0.35f
)

enum class SignatureHorizontal {
    LEFT,
    CENTER,
    RIGHT
}

enum class SignatureVertical {
    TOP,
    MIDDLE,
    BOTTOM
}

enum class FilterType {
    ORIGINAL,
    GRAYSCALE,
    BLACK_WHITE,
    DOCUMENT
}
