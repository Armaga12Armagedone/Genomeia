package io.github.some_example_name.old.systems.maps
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.ScreenUtils
import io.github.some_example_name.old.core.DISimulationContainer
import java.io.FileOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

class MapSave {
    private var currentMap = -1

    fun getName(): Int {
        val file = File(DISimulationContainer.baseMapDir)
        file.mkdirs()

        return file.listFiles()?.count {it.isFile } ?: 0
    }

    fun saveMap(custom: Boolean, seed: Int, map: Array<BooleanArray>, canvasTexture: Pixmap) {
        val mapName = getName()+1
        currentMap = mapName
        val zipname = DISimulationContainer.baseMapDir+mapName.toString() + ".zip"

        FileOutputStream(zipname).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val dataOut = DataOutputStream(zos)

                // --- ЗАПИСЬ ФАЙЛА 1 (Бинарные данные) ---
                zos.putNextEntry(ZipEntry("world.bin"))

                dataOut.writeBoolean(custom)

                if (!custom) {
                    dataOut.writeInt(seed)
                }
                else {
                    dataOut.writeInt(map.size)
                    for (height in map) {
                        dataOut.writeInt(height.size)
                        for (width in height) {
                            dataOut.writeBoolean(width)
                        }
                    }
                }

                dataOut.writeInt(-555)
                saveScreenshot(zos, canvasTexture)

                dataOut.flush()               // Очищаем буфер, проталкивая данные в ZIP
                zos.closeEntry()
            }
        }
    }

    fun saveScreenshot(zos: ZipOutputStream,canvasTexture: Pixmap) {
        //val width = Gdx.graphics.width
        //val height = Gdx.graphics.height

        // Создаем Pixmap из текущего кадрового буфера
        //val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)

//        val pixmap = textureToPixmap(canvasTexture)

        val zipname = DISimulationContainer.baseMapDir+currentMap.toString() + ".zip"

        val imageName = currentMap.toString() + ".png"

        val zipEntry = ZipEntry(imageName)
        zos.putNextEntry(zipEntry)

        val writer = PixmapIO.PNG()
        writer.setFlipY(true)
        writer.write(zos, canvasTexture)

        canvasTexture.dispose()

        zos.closeEntry()
    }

    fun textureToPixmap(texture: Texture): Pixmap {
        val textureData = texture.textureData
        if (!textureData.isPrepared) {
            textureData.prepare()
        }

        return textureData.consumePixmap()
    }
}
