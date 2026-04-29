package io.github.some_example_name.old.core.utils

import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector3

fun OrthographicCamera.screenToWorld(screenX: Float, screenY: Float): Pair<Float, Float> {
    val screenPos = Vector3(screenX, screenY, 0f)
    val worldPos = this.unproject(screenPos)
    return Pair(worldPos.x, worldPos.y)
}
