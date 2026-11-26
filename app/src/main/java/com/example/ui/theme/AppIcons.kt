package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight local Material vector icons replacing `material-icons-extended`
 * to keep the APK size compact while preserving crisp vector iconography.
 */
object AppIcons {

    private fun icon(
        name: String,
        autoMirror: Boolean = false,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = autoMirror
    ).apply(block).build()

    private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.solidPath(
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) {
        path(fill = SolidColor(Color.Black)) { pathData() }
    }

    val Mosque: ImageVector by lazy {
        icon("Mosque") {
            solidPath {
                moveTo(24f, 7f)
                curveTo(24f, 5.9f, 22f, 4f, 22f, 4f)
                reflectiveCurveToRelative(-2f, 1.9f, -2f, 3f)
                curveToRelative(0f, 0.74f, 0.4f, 1.38f, 1f, 1.72f)
                verticalLineTo(13f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.95f, -0.66f, -1.74f, -1.55f, -1.94f)
                curveTo(17.79f, 8.48f, 18f, 7.81f, 18f, 7.09f)
                curveToRelative(0f, -1.31f, -0.65f, -2.53f, -1.74f, -3.25f)
                lineTo(12f, 1f)
                lineTo(7.74f, 3.84f)
                curveTo(6.65f, 4.56f, 6f, 5.78f, 6f, 7.09f)
                curveToRelative(0f, 0.72f, 0.21f, 1.39f, 0.55f, 1.97f)
                curveTo(5.66f, 9.26f, 5f, 10.05f, 5f, 11f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineTo(8.72f)
                curveTo(3.6f, 8.38f, 4f, 7.74f, 4f, 7f)
                curveToRelative(0f, -1.1f, -2f, -3f, -2f, -3f)
                reflectiveCurveTo(0f, 5.9f, 0f, 7f)
                curveToRelative(0f, 0.74f, 0.4f, 1.38f, 1f, 1.72f)
                verticalLineTo(21f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(-4f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(9f)
                verticalLineTo(8.72f)
                curveTo(23.6f, 8.38f, 24f, 7.74f, 24f, 7f)
                close()
            }
        }
    }

    val NightsStay: ImageVector by lazy {
        icon("NightsStay") {
            solidPath {
                moveTo(11.1f, 12.08f)
                curveTo(8.77f, 7.57f, 10.6f, 3.6f, 11.63f, 2.01f)
                curveTo(6.27f, 2.2f, 1.98f, 6.59f, 1.98f, 12f)
                curveToRelative(0f, 0.14f, 0.02f, 0.28f, 0.02f, 0.42f)
                curveTo(2.62f, 12.15f, 3.29f, 12f, 4f, 12f)
                curveToRelative(1.66f, 0f, 3.18f, 0.83f, 4.1f, 2.15f)
                curveTo(9.77f, 14.63f, 11f, 16.17f, 11f, 18f)
                curveToRelative(0f, 1.52f, -0.87f, 2.83f, -2.12f, 3.51f)
                curveToRelative(0.98f, 0.32f, 2.03f, 0.5f, 3.11f, 0.5f)
                curveToRelative(3.5f, 0f, 6.58f, -1.8f, 8.37f, -4.52f)
                curveTo(18f, 17.72f, 13.38f, 16.52f, 11.1f, 12.08f)
                close()
            }
        }
    }

    val NightsStayOutlined: ImageVector by lazy { NightsStay }

    val WbSunny: ImageVector by lazy {
        icon("WbSunny") {
            solidPath {
                moveTo(6.76f, 4.84f)
                lineTo(4.96f, 3.05f)
                lineTo(3.55f, 4.46f)
                lineToRelative(1.79f, 1.79f)
                lineToRelative(1.42f, -1.41f)
                close()
                moveTo(4f, 10.5f)
                horizontalLineTo(1f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(-2f)
                close()
                moveTo(13f, 0.55f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2.95f)
                horizontalLineToRelative(2f)
                verticalLineTo(0.55f)
                close()
                moveTo(20.45f, 4.46f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-1.79f, 1.79f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(1.79f, -1.79f)
                close()
                moveTo(17.24f, 18.16f)
                lineToRelative(1.79f, 1.8f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.8f, -1.79f)
                lineToRelative(-1.4f, 1.4f)
                close()
                moveTo(20f, 10.5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-3f)
                close()
                moveTo(12f, 5.5f)
                curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
                reflectiveCurveToRelative(2.69f, 6f, 6f, 6f)
                reflectiveCurveToRelative(6f, -2.69f, 6f, -6f)
                reflectiveCurveToRelative(-2.69f, -6f, -6f, -6f)
                close()
                moveTo(11f, 22.45f)
                horizontalLineToRelative(2f)
                verticalLineTo(19.5f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2.95f)
                close()
                moveTo(3.55f, 18.54f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(1.79f, -1.8f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-1.79f, 1.8f)
                close()
            }
        }
    }

    val Brightness5Outlined: ImageVector by lazy { WbSunny }

    val WbTwilight: ImageVector by lazy {
        icon("WbTwilight") {
            solidPath {
                moveTo(11f, 4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(2f, 18f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(2f)
                horizontalLineTo(2f)
                close()
                moveTo(12f, 9f)
                curveToRelative(-3.31f, 0f, -6f, 2.69f, -6f, 6f)
                horizontalLineToRelative(12f)
                curveToRelative(0f, -3.31f, -2.69f, -6f, -6f, -6f)
                close()
                moveTo(4.93f, 8.93f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(-1.41f, 1.41f)
                close()
                moveTo(15.54f, 9.64f)
                lineToRelative(2.12f, -2.12f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(-2.12f, 2.12f)
                close()
            }
        }
    }

    val WbCloudyOutlined: ImageVector by lazy {
        icon("WbCloudy") {
            solidPath {
                moveTo(19.35f, 10.04f)
                curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                horizontalLineToRelative(13f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                close()
            }
        }
    }

    val AccessTime: ImageVector by lazy {
        icon("AccessTime") {
            solidPath {
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
                moveTo(12.5f, 7f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
    }

    val Alarm: ImageVector by lazy {
        icon("Alarm") {
            solidPath {
                moveTo(22f, 5.72f)
                lineToRelative(-4.6f, -3.86f)
                lineToRelative(-1.29f, 1.53f)
                lineToRelative(4.6f, 3.86f)
                lineTo(22f, 5.72f)
                close()
                moveTo(7.88f, 3.39f)
                lineTo(6.6f, 1.86f)
                lineTo(2f, 5.71f)
                lineToRelative(1.29f, 1.53f)
                lineToRelative(4.59f, -3.85f)
                close()
                moveTo(12.5f, 8f)
                horizontalLineTo(11f)
                verticalLineToRelative(6f)
                lineToRelative(4.75f, 2.85f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4f, -2.37f)
                verticalLineTo(8f)
                close()
                moveTo(12f, 4f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.02f, 9f, 9f, 9f)
                curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
                reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
                reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
                reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                close()
            }
        }
    }

    val AutoAwesome: ImageVector by lazy {
        icon("AutoAwesome") {
            solidPath {
                moveTo(19f, 9f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23f, 5f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19f, 1f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15f, 5f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19f, 9f)
                close()
                moveTo(11.5f, 9.5f)
                lineTo(9f, 4f)
                lineTo(6.5f, 9.5f)
                lineTo(1f, 12f)
                lineToRelative(5.5f, 2.5f)
                lineTo(9f, 20f)
                lineToRelative(2.5f, -5.5f)
                lineTo(17f, 12f)
                lineToRelative(-5.5f, -2.5f)
                close()
                moveTo(19f, 15f)
                lineToRelative(-1.25f, 2.75f)
                lineTo(15f, 19f)
                lineToRelative(2.75f, 1.25f)
                lineTo(19f, 23f)
                lineToRelative(1.25f, -2.75f)
                lineTo(23f, 19f)
                lineToRelative(-2.75f, -1.25f)
                lineTo(19f, 15f)
                close()
            }
        }
    }

    val AutoStories: ImageVector by lazy {
        icon("AutoStories") {
            solidPath {
                moveTo(19f, 1f)
                lineToRelative(-5f, 5f)
                verticalLineToRelative(11f)
                lineToRelative(5f, -4.5f)
                verticalLineTo(1f)
                close()
                moveTo(1f, 6f)
                verticalLineToRelative(14.65f)
                curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
                curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.4f, 0.2f, 0.75f, -0.1f, 0.75f, -0.5f)
                verticalLineTo(6f)
                curveToRelative(-0.6f, -0.45f, -1.25f, -0.75f, -2f, -1f)
                verticalLineToRelative(13.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(6f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                curveTo(4.55f, 4.5f, 2.45f, 4.9f, 1f, 6f)
                close()
            }
        }
    }

    val AutoStoriesOutlined: ImageVector by lazy { AutoStories }

    val MenuBook: ImageVector by lazy {
        icon("MenuBook", autoMirror = true) {
            solidPath {
                moveTo(21f, 5f)
                curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.95f, 0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                reflectiveCurveTo(2.45f, 4.9f, 1f, 6f)
                verticalLineToRelative(14.65f)
                curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
                curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.4f, 0.2f, 0.75f, -0.1f, 0.75f, -0.5f)
                verticalLineTo(6f)
                curveToRelative(-0.6f, -0.45f, -1.25f, -0.75f, -2f, -1f)
                close()
                moveTo(21f, 18.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.2f, 0f, 2.4f, 0.15f, 3.5f, 0.5f)
                verticalLineToRelative(11.5f)
                close()
            }
        }
    }

    val MenuBookOutlined: ImageVector by lazy { MenuBook }

    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
            solidPath {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                lineToRelative(7f, -3f)
                lineToRelative(7f, 3f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

    val BookmarkBorder: ImageVector by lazy {
        icon("BookmarkBorder") {
            solidPath {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                lineToRelative(7f, -3f)
                lineToRelative(7f, 3f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(17f, 18f)
                lineToRelative(-5f, -2.18f)
                lineTo(7f, 18f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(13f)
                close()
            }
        }
    }

    val Explore: ImageVector by lazy {
        icon("Explore") {
            solidPath {
                moveTo(12f, 10.9f)
                curveToRelative(-0.61f, 0f, -1.1f, 0.49f, -1.1f, 1.1f)
                reflectiveCurveToRelative(0.49f, 1.1f, 1.1f, 1.1f)
                curveToRelative(0.61f, 0f, 1.1f, -0.49f, 1.1f, -1.1f)
                reflectiveCurveToRelative(-0.49f, -1.1f, -1.1f, -1.1f)
                close()
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(14.19f, 14.19f)
                lineTo(6f, 18f)
                lineToRelative(3.81f, -8.19f)
                lineTo(18f, 6f)
                lineToRelative(-3.81f, 8.19f)
                close()
            }
        }
    }

    val ExploreOutlined: ImageVector by lazy {
        icon("ExploreOutlined") {
            solidPath {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                close()
                moveTo(6.5f, 17.5f)
                lineToRelative(7.51f, -3.49f)
                lineTo(17.5f, 6.5f)
                lineTo(9.99f, 9.99f)
                lineTo(6.5f, 17.5f)
                close()
                moveTo(12f, 10.9f)
                curveToRelative(0.61f, 0f, 1.1f, 0.49f, 1.1f, 1.1f)
                reflectiveCurveToRelative(-0.49f, 1.1f, -1.1f, 1.1f)
                reflectiveCurveToRelative(-1.1f, -0.49f, -1.1f, -1.1f)
                reflectiveCurveToRelative(0.49f, -1.1f, 1.1f, -1.1f)
                close()
            }
        }
    }

    val VolumeUp: ImageVector by lazy {
        icon("VolumeUp", autoMirror = true) {
            solidPath {
                moveTo(3f, 9f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                lineToRelative(5f, 5f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
                reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
                close()
            }
        }
    }

    val GraphicEq: ImageVector by lazy {
        icon("GraphicEq") {
            solidPath {
                moveTo(7f, 18f)
                horizontalLineToRelative(2f)
                verticalLineTo(6f)
                horizontalLineTo(7f)
                verticalLineToRelative(12f)
                close()
                moveTo(11f, 22f)
                horizontalLineToRelative(2f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(20f)
                close()
                moveTo(3f, 14f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                horizontalLineTo(3f)
                verticalLineToRelative(4f)
                close()
                moveTo(15f, 18f)
                horizontalLineToRelative(2f)
                verticalLineTo(6f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(12f)
                close()
                moveTo(19f, 10f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(-2f)
                close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            solidPath {
                moveTo(6f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            solidPath {
                moveTo(6f, 19f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineTo(6f)
                verticalLineToRelative(14f)
                close()
                moveTo(14f, 5f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                close()
            }
        }
    }

    val Remove: ImageVector by lazy {
        icon("Remove") {
            solidPath {
                moveTo(19f, 13f)
                horizontalLineTo(5f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val Tune: ImageVector by lazy {
        icon("Tune") {
            solidPath {
                moveTo(3f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                close()
                moveTo(7f, 9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineTo(11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                close()
                moveTo(15f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                close()
            }
        }
    }

    val FormatListBulleted: ImageVector by lazy {
        icon("FormatListBulleted") {
            solidPath {
                moveTo(4f, 10.5f)
                curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveToRelative(0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                close()
                moveTo(4f, 4.5f)
                curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveTo(3.17f, 7.5f, 4f, 7.5f)
                reflectiveCurveTo(5.5f, 6.83f, 5.5f, 6f)
                reflectiveCurveTo(4.83f, 4.5f, 4f, 4.5f)
                close()
                moveTo(4f, 16.5f)
                curveToRelative(-0.83f, 0f, -1.5f, 0.68f, -1.5f, 1.5f)
                reflectiveCurveToRelative(0.68f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.68f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                close()
                moveTo(7f, 19f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 13f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(5f)
                horizontalLineTo(7f)
                close()
            }
        }
    }

    val TextFields: ImageVector by lazy {
        icon("TextFields") {
            solidPath {
                moveTo(2.5f, 4f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(3f)
                verticalLineTo(7f)
                horizontalLineToRelative(5f)
                verticalLineTo(4f)
                horizontalLineTo(2.5f)
                close()
                moveTo(21.5f, 9f)
                horizontalLineToRelative(-9f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(7f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(-7f)
                horizontalLineToRelative(3f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    val Event: ImageVector by lazy {
        icon("Event") {
            solidPath {
                moveTo(17f, 12f)
                horizontalLineToRelative(-5f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(-5f)
                close()
                moveTo(16f, 1f)
                verticalLineToRelative(2f)
                horizontalLineTo(8f)
                verticalLineTo(1f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(3f, 19f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-1f)
                verticalLineTo(1f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(8f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(11f)
                close()
            }
        }
    }

    val Restaurant: ImageVector by lazy {
        icon("Restaurant") {
            solidPath {
                moveTo(11f, 9f)
                horizontalLineTo(9f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineToRelative(7f)
                horizontalLineTo(5f)
                verticalLineTo(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(7f)
                curveToRelative(0f, 2.12f, 1.66f, 3.84f, 3.75f, 3.97f)
                verticalLineTo(22f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(-9.03f)
                curveTo(11.34f, 12.84f, 13f, 11.12f, 13f, 9f)
                verticalLineTo(2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(7f)
                close()
                moveTo(16f, 6f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(8f)
                horizontalLineTo(21f)
                verticalLineTo(2f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 4f)
                close()
            }
        }
    }

    val TouchApp: ImageVector by lazy {
        icon("TouchApp") {
            solidPath {
                moveTo(9f, 11.24f)
                verticalLineTo(7.5f)
                curveTo(9f, 6.12f, 10.12f, 5f, 11.5f, 5f)
                reflectiveCurveTo(14f, 6.12f, 14f, 7.5f)
                verticalLineToRelative(3.74f)
                curveToRelative(1.21f, -0.81f, 2f, -2.18f, 2f, -3.74f)
                curveTo(16f, 5.01f, 13.99f, 3f, 11.5f, 3f)
                reflectiveCurveTo(7f, 5.01f, 7f, 7.5f)
                curveToRelative(0f, 1.56f, 0.79f, 2.93f, 2f, 3.74f)
                close()
                moveTo(18.84f, 15.87f)
                lineToRelative(-4.54f, -2.26f)
                curveToRelative(-0.17f, -0.07f, -0.35f, -0.11f, -0.54f, -0.11f)
                horizontalLineTo(13f)
                verticalLineToRelative(-6f)
                curveToRelative(0f, -0.83f, -0.67f, -1.5f, -1.5f, -1.5f)
                reflectiveCurveTo(10f, 6.67f, 10f, 7.5f)
                verticalLineToRelative(10.74f)
                lineToRelative(-3.43f, -0.72f)
                curveToRelative(-0.08f, -0.01f, -0.15f, -0.03f, -0.24f, -0.03f)
                curveToRelative(-0.31f, 0f, -0.59f, 0.13f, -0.79f, 0.33f)
                lineToRelative(-0.79f, 0.8f)
                lineToRelative(4.94f, 4.94f)
                curveToRelative(0.27f, 0.27f, 0.65f, 0.44f, 1.06f, 0.44f)
                horizontalLineToRelative(6.79f)
                curveToRelative(0.75f, 0f, 1.33f, -0.55f, 1.44f, -1.28f)
                lineToRelative(0.75f, -5.27f)
                curveToRelative(0.01f, -0.07f, 0.02f, -0.14f, 0.02f, -0.2f)
                curveToRelative(0f, -0.62f, -0.38f, -1.16f, -0.91f, -1.38f)
                close()
            }
        }
    }

    val Campaign: ImageVector by lazy {
        icon("Campaign") {
            solidPath {
                moveTo(18f, 11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(16f, 17.61f)
                curveToRelative(0.96f, 0.71f, 2.21f, 1.65f, 3.2f, 2.39f)
                curveToRelative(0.4f, -0.53f, 0.8f, -1.07f, 1.2f, -1.6f)
                curveToRelative(-0.99f, -0.74f, -2.24f, -1.68f, -3.2f, -2.4f)
                curveToRelative(-0.4f, 0.54f, -0.8f, 1.08f, -1.2f, 1.61f)
                close()
                moveTo(20.4f, 5.6f)
                curveToRelative(-0.4f, -0.53f, -0.8f, -1.07f, -1.2f, -1.6f)
                curveToRelative(-0.99f, 0.74f, -2.24f, 1.68f, -3.2f, 2.4f)
                curveToRelative(0.4f, 0.53f, 0.8f, 1.07f, 1.2f, 1.6f)
                curveToRelative(0.96f, -0.72f, 2.21f, -1.65f, 3.2f, -2.4f)
                close()
                moveTo(4f, 9f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(1f)
                lineToRelative(5f, 3f)
                verticalLineTo(6f)
                lineTo(8f, 9f)
                horizontalLineTo(4f)
                close()
                moveTo(15.5f, 12f)
                curveToRelative(0f, -1.33f, -0.58f, -2.53f, -1.5f, -3.35f)
                verticalLineToRelative(6.69f)
                curveToRelative(0.92f, -0.81f, 1.5f, -2.01f, 1.5f, -3.34f)
                close()
            }
        }
    }

    val DeleteOutline: ImageVector by lazy {
        icon("DeleteOutline") {
            solidPath {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                close()
                moveTo(8f, 9f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(10f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                close()
                moveTo(15.5f, 4f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                close()
            }
        }
    }

    val KeyboardArrowLeft: ImageVector by lazy {
        icon("KeyboardArrowLeft", autoMirror = true) {
            solidPath {
                moveTo(15.41f, 16.59f)
                lineTo(10.83f, 12f)
                lineToRelative(4.58f, -4.59f)
                lineTo(14f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(1.41f, -1.41f)
                close()
            }
        }
    }

    val KeyboardArrowRight: ImageVector by lazy {
        icon("KeyboardArrowRight", autoMirror = true) {
            solidPath {
                moveTo(8.59f, 16.59f)
                lineTo(13.17f, 12f)
                lineTo(8.59f, 7.41f)
                lineTo(10f, 6f)
                lineToRelative(6f, 6f)
                lineToRelative(-6f, 6f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
    }
}
