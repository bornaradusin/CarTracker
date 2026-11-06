package io.github.bornaradusin.cartracker.ui.map

import androidx.compose.ui.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * Map styles that need no key: OpenFreeMap's vector maps (free, no limits, allowed in public apps) and
 * OpenTopoMap's terrain. Satellite and live traffic need keys, so the app opens Google Maps for those.
 */
enum class MapStyle(val label: String, val swatch: List<Color>, val swatchDark: List<Color>) {
    Standard("Standard", listOf(Color(0xFFF1F3F4), Color(0xFFFDE293), Color(0xFFAAD3F5)), listOf(Color(0xFF1F2124), Color(0xFF6E5F3C), Color(0xFF17304A))),
    Clean("Clean", listOf(Color(0xFFF5F5F3), Color(0xFFFFFFFF), Color(0xFFD9E2E8)), listOf(Color(0xFF16181B), Color(0xFF2C2F33), Color(0xFF1B2633))),
    BlackWhite("Black & white", listOf(Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF111111)), listOf(Color(0xFF000000), Color(0xFFEEEEEE), Color(0xFF333333))),
    Terrain("Terrain", listOf(Color(0xFFE9E3D2), Color(0xFFF6C77B), Color(0xFF9CC3DD)), listOf(Color(0xFFE9E3D2), Color(0xFFF6C77B), Color(0xFF9CC3DD)));

    companion object {
        fun named(name: String): MapStyle = entries.firstOrNull { it.name == name } ?: Standard
    }
}

object MapStyles {

    private const val OPENFREEMAP = "https://tiles.openfreemap.org/styles/"

    fun builder(style: MapStyle, dark: Boolean): Style.Builder = when (style) {
        MapStyle.Standard -> Style.Builder().fromUri(OPENFREEMAP + if (dark) "dark" else "liberty")
        MapStyle.Clean -> Style.Builder().fromUri(OPENFREEMAP + if (dark) "fiord" else "positron")
        MapStyle.BlackWhite -> Style.Builder().fromUri(OPENFREEMAP + "positron")
        MapStyle.Terrain -> Style.Builder().fromJson(TERRAIN)
    }

    /** After loading: Black & white is Positron recoloured to two tones. */
    fun afterLoad(style: MapStyle, dark: Boolean, loaded: Style) {
        if (style == MapStyle.BlackWhite) blackAndWhite(loaded, dark)
    }

    private fun blackAndWhite(style: Style, dark: Boolean) {
        val paper = if (dark) "#000000" else "#FFFFFF"
        val ink = if (dark) "#EEEEEE" else "#111111"
        val water = if (dark) "#333333" else "#111111"
        val building = if (dark) "#1C1C1C" else "#E2E2E2"
        style.layers.forEach { layer ->
            val id = layer.id.lowercase()
            val isWater = "water" in id || "river" in id || "ocean" in id || "lake" in id
            runCatching {
                when (layer) {
                    is BackgroundLayer -> layer.setProperties(PropertyFactory.backgroundColor(paper))
                    is FillLayer -> layer.setProperties(
                        PropertyFactory.fillColor(
                            when {
                                isWater -> water
                                "building" in id -> building
                                else -> paper
                            },
                        ),
                        PropertyFactory.fillOutlineColor(if (isWater) water else paper),
                    )
                    is FillExtrusionLayer -> layer.setProperties(PropertyFactory.fillExtrusionColor(building))
                    is LineLayer -> layer.setProperties(PropertyFactory.lineColor(if (isWater) water else ink))
                    is SymbolLayer -> layer.setProperties(
                        PropertyFactory.textColor(ink),
                        PropertyFactory.textHaloColor(paper),
                        PropertyFactory.iconOpacity(0f),
                    )
                    else -> Unit
                }
            }
        }
    }

    /** OpenTopoMap terrain tiles, with OpenFreeMap's fonts for our own labels. */
    private val TERRAIN = """
        {
          "version": 8,
          "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
          "sources": {
            "topo": {
              "type": "raster",
              "tiles": ["https://tile.openmaps.fr/opentopomap/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 17,
              "attribution": "© OpenTopoMap-R (CC-BY-SA) · © OpenStreetMap contributors"
            }
          },
          "layers": [
            { "id": "paper", "type": "background", "paint": { "background-color": "#F2EFE9" } },
            { "id": "topo", "type": "raster", "source": "topo" }
          ]
        }
    """.trimIndent()
}
