package io.github.caendeith.awakr.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-rolled icons for the shapes the bundled Material core set does not ship.
 *
 * The project depends on `material-icons-core` only, because the extended set adds megabytes of
 * vectors for a handful of glyphs. A stop square is one path, so it lives here.
 */
object AwakrIcons {

    /** A filled square: "stop this sound". Same optical weight as the core play arrow. */
    val Stop: ImageVector by lazy {
        ImageVector.Builder(
            name = "AwakrStop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 6f)
                horizontalLineTo(18f)
                verticalLineTo(18f)
                horizontalLineTo(6f)
                close()
            }
        }.build()
    }
}
