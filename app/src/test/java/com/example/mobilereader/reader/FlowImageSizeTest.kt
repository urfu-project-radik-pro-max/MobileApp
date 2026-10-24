package com.example.mobilereader.reader

import com.example.mobilereader.data.model.ContentElement
import com.example.mobilereader.ui.reader.FlowImageSize
import com.example.mobilereader.ui.reader.flowImageSizePx
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FlowImageSizeTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun image(
        widthFrac: Float? = null,
        widthEm: Float? = null,
        heightEm: Float? = null,
    ): ContentElement.Image {
        val file = temporaryFolder.newFile("image-${System.nanoTime()}.svg")
        file.writeText("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 100'></svg>")
        return ContentElement.Image(file.absolutePath,
            widthFrac = widthFrac, widthEm = widthEm, heightEm = heightEm)
    }

    @Test
    fun `percentage and em width preserve image aspect ratio`() {
        assertEquals(FlowImageSize(100, 50),
            flowImageSizePx(image(widthFrac = 0.25f), 400, 20f, 300))
        assertEquals(FlowImageSize(80, 40),
            flowImageSizePx(image(widthEm = 4f), 400, 20f, 300))
    }

    @Test
    fun `html dimensions and one specified height are honored`() {
        assertEquals(FlowImageSize(150, 60),
            flowImageSizePx(image(widthEm = 7.5f, heightEm = 3f), 400, 20f, 300))
        assertEquals(FlowImageSize(120, 60),
            flowImageSizePx(image(heightEm = 3f), 400, 20f, 300))
    }

    @Test
    fun `natural image fills width and page cap preserves aspect ratio`() {
        val natural = image()
        assertEquals(FlowImageSize(400, 200), flowImageSizePx(natural, 400, 20f, 300))
        val paginated = flowImageSizePx(natural, 400, 20f, 100)
        assertEquals(FlowImageSize(200, 100), paginated)
        assertEquals(paginated,
            flowImageSizePx(natural, 400, 20f, paginated.heightPx))
    }
}
