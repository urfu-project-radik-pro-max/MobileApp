package com.example.mobilereader.data.model


data class PublisherPublication(
    val format: PublisherFormat,
    val profile: PublisherProfile,
    val source: PublisherSourceDescriptor,
    val defaults: PublisherRendition,
    val pageProgression: PageProgression,

    val spine: List<PublisherSpineItem>,

    val resources: Map<String, PublisherResource>,
    val textIndex: PublisherTextIndex = PublisherTextIndex(),
)

enum class PublisherFormat {
    EPUB_2,
    EPUB_3,
    KF8,
}

enum class PublisherProfile {
    EPUB_STANDARD,
    AMAZON_EPUB2_FIXED,
    KF8_DETECTED,
}


sealed interface PublisherSourceDescriptor {

    data class EpubArchive(val packagePath: String) : PublisherSourceDescriptor


    data class Kf8Bundle(
        val generationKey: String,
        val relativeDirectory: String,
        val formatVersion: Int,
    ) : PublisherSourceDescriptor
}


data class PublisherSpineItem(
    val id: String,
    val itemRefId: String?,
    val manifestId: String,
    val resourcePath: String,
    val linear: Boolean,
    val title: String? = null,
    val rendition: PublisherRendition,
    val capabilities: Set<PublisherCapability> = emptySet(),
    val kindleRegions: List<KindleRegion> = emptyList(),
)

data class PublisherRendition(
    val layout: RenditionLayout = RenditionLayout.REFLOWABLE,
    val orientation: RenditionOrientation = RenditionOrientation.AUTO,
    val spread: RenditionSpread = RenditionSpread.AUTO,
    val placement: PagePlacement = PagePlacement.AUTO,
    val viewport: PublisherViewport? = null,
    val primaryWritingMode: PrimaryWritingMode? = null,

    val layoutBlank: Boolean = false,
)

enum class RenditionLayout {
    REFLOWABLE,
    PRE_PAGINATED,
}

enum class RenditionOrientation {
    AUTO,
    PORTRAIT,
    LANDSCAPE,
}

enum class RenditionSpread {
    AUTO,
    NONE,
    LANDSCAPE,

    PORTRAIT,
    BOTH,
}

enum class PagePlacement {
    AUTO,
    LEFT,
    RIGHT,
    CENTER,
}

enum class PageProgression {
    DEFAULT,
    LTR,
    RTL,
}

enum class PrimaryWritingMode {
    HORIZONTAL_LR,
    HORIZONTAL_RL,
    VERTICAL_LR,
    VERTICAL_RL,
}

sealed interface PublisherViewportDimension {
    data class CssPixels(val value: Float) : PublisherViewportDimension {
        init {
            require(value.isFinite() && value > 0f)
        }
    }


    data object Device : PublisherViewportDimension
}

data class PublisherViewport(
    val width: PublisherViewportDimension,
    val height: PublisherViewportDimension,
    val source: ViewportSource,

    val inferred: Boolean = false,
) {
    constructor(
        widthCssPx: Float,
        heightCssPx: Float,
        source: ViewportSource,
        inferred: Boolean = false,
    ) : this(
        width = PublisherViewportDimension.CssPixels(widthCssPx),
        height = PublisherViewportDimension.CssPixels(heightCssPx),
        source = source,
        inferred = inferred,
    )

    val widthCssPx: Float?
        get() = (width as? PublisherViewportDimension.CssPixels)?.value

    val heightCssPx: Float?
        get() = (height as? PublisherViewportDimension.CssPixels)?.value
}

enum class ViewportSource {
    XHTML_META,
    SVG_VIEWBOX,
    AMAZON_ORIGINAL_RESOLUTION,
}

data class PublisherResource(
    val path: String,
    val mediaType: String,
    val properties: Set<String> = emptySet(),
    val transform: PublisherResourceTransform = PublisherResourceTransform.NONE,
)

enum class PublisherResourceTransform {
    NONE,
    IDPF_FONT_OBFUSCATION,
    ADOBE_FONT_OBFUSCATION,
}

enum class PublisherCapability {
    MATHML,
    EMBEDDED_SVG,
    STANDALONE_SVG,
    VERTICAL_WRITING,
    SCRIPTED,
    KINDLE_REGION_MAGNIFICATION,
    KINDLE_VIRTUAL_PANELS,
}

data class KindleRegion(
    val sourceElementId: String?,
    val targetElementId: String,
    val ordinal: Int,
    val kind: KindleRegionKind,
)

enum class KindleRegionKind {
    TEXT,
    IMAGE,
}


data class PublisherTextIndex(
    val documents: Map<String, PublisherTextDocument> = emptyMap(),
)

data class PublisherTextDocument(
    val resourcePath: String,
    val text: String,
    val anchors: List<PublisherTextAnchor> = emptyList(),
)

data class PublisherTextAnchor(
    val fragment: String,
    val textOffset: Int,
)
