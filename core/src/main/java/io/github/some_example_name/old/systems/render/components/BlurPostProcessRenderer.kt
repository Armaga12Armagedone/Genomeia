package io.github.some_example_name.old.systems.render.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShaderProgram

/**
 * Applies gaussian blur post-processing effect.
 * Final pass in the post-processing pipeline, renders directly to screen.
 */
class BlurPostProcessRenderer : RenderComponent {

    private lateinit var blurShader: ShaderProgram

    override fun create() {
        val vertexShader = Gdx.files.internal("shaders/blur/blur.vert").readString()
        val fragmentShader = Gdx.files.internal("shaders/blur/gaussian_blur.frag").readString()

        blurShader = ShaderProgram(vertexShader, fragmentShader)
        if (!blurShader.isCompiled) {
            throw RuntimeException("Blur shader compilation failed: ${blurShader.log}")
        }
    }

    override fun resize(width: Int, height: Int) {
        // No size-dependent resources in this component
    }

    override fun render(context: RenderContext) {
        if (!context.usePostProcess) return

        val inputTexture = context.currentTexture ?: return
        val blurFbo = context.blurFbo ?: return

        // Final pass renders directly to screen (no FBO begin)

        blurShader.bind()
        blurShader.setUniformi("u_texture", 0)
        blurShader.setUniformf("u_blurAmount", (context.blurAmount + 0.04f) * 0.5f)
        blurShader.setUniformf("u_resolution", blurFbo.width.toFloat(), blurFbo.height.toFloat())

        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
        inputTexture.bind()

        context.fullscreenMesh.bind(blurShader)
        Gdx.gl.glDrawArrays(GL20.GL_TRIANGLE_STRIP, 0, 4)
        context.fullscreenMesh.unbind(blurShader)
    }

    override fun dispose() {
        blurShader.dispose()
    }
}
