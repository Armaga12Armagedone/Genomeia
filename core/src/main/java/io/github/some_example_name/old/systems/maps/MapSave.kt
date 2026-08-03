package io.github.some_example_name.old.systems.maps
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.ScreenUtils
import io.github.some_example_name.old.core.DISimulationContainer
import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.EyeEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.NeuralEntity
import io.github.some_example_name.old.entities.OrganEntity
import io.github.some_example_name.old.entities.ParticleEntity
import io.github.some_example_name.old.entities.PheromoneEmitterEntity
import io.github.some_example_name.old.entities.PheromoneEntity
import io.github.some_example_name.old.entities.ProducerEntity
import io.github.some_example_name.old.entities.SpecialEntity
import io.github.some_example_name.old.entities.SpecialModDataEntity
import io.github.some_example_name.old.entities.SubstancesEntity
import io.github.some_example_name.old.entities.TailEntity
import io.github.some_example_name.old.entities.samples.CellEntitySample
import io.github.some_example_name.old.features.settings.GlobalSettings
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.FileOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO


class MapSave {
    var currentMap = -1

    fun getName(): Int {
        val file = File(DISimulationContainer.baseMapDir)
        file.mkdirs()

        return file.listFiles()?.count {it.isFile } ?: 0
    }

    fun resaveMap() {
        println("Map is: ${currentMap}")
        val zipname = DISimulationContainer.baseMapDir + currentMap.toString() + ".zip"
        val file = File(zipname)

        // 1. Временно читаем старый world.bin в память, чтобы не потерять его при перезаписи архива
        var oldWorldBin: ByteArray? = null
        if (file.exists()) {
            ZipInputStream(FileInputStream(file)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "world.bin") {
                        oldWorldBin = zipIn.readBytes()
                        break
                    }
                    entry = zipIn.nextEntry
                }
            }
        }

        // 2. Перезаписываем архив
        FileOutputStream(zipname).use { fos ->
            ZipOutputStream(fos).use { zos ->

                // Восстанавливаем world.bin
                if (oldWorldBin != null) {
                    zos.putNextEntry(ZipEntry("world.bin"))
                    zos.write(oldWorldBin!!, 0, oldWorldBin!!.size)
                    zos.closeEntry()
                }

                // Сохраняем обновленные сущности
                saveEntity(zos)

                // Делаем свежий скриншот
                val width = Gdx.graphics.width
                val height = Gdx.graphics.height
                val pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, width, height)
                saveScreenshot(zos, pixmap)
            }
        }
    }

    fun saveMap(custom: Boolean, seed: Long, map: Array<BooleanArray>, canvasTexture: Pixmap) {
        val mapName = getName()+1
        currentMap = mapName
        val zipname = DISimulationContainer.baseMapDir+mapName.toString() + ".zip"

        FileOutputStream(zipname).use { fos ->
            ZipOutputStream(fos).use { zos ->
                val dataOut = DataOutputStream(zos)

                // --- ЗАПИСЬ ФАЙЛА 1 (Бинарные данные) ---
                zos.putNextEntry(ZipEntry("world.bin"))

                dataOut.writeBoolean(custom)
                dataOut.writeInt(map.size)//GlobalSettings.GRID_WIDTH)
                dataOut.writeInt(map[0].size)//GlobalSettings.GRID_HEIGHT)
//                dataOut.writeInt(map.size)

                if (!custom) {
                    dataOut.writeLong(seed)
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
                saveEntity(zos)

                dataOut.flush()               // Очищаем буфер, проталкивая данные в ZIP
                zos.closeEntry()
            }
        }
    }

    fun saveEntity(zos: ZipOutputStream) {
        //cellEntity сейв
        val cellEntitySerialized = DISimulationContainer.cellEntity
        cellEntitySerialized.serializeEntity()
        println(cellEntitySerialized.maxAmount)
        var bytes = ProtoBuf.encodeToByteArray(CellEntity.serializer(), cellEntitySerialized)

        zos.putNextEntry(ZipEntry("CellEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val specEntity = DISimulationContainer.specialEntity
        specEntity.saveSerialize()
        bytes = ProtoBuf.encodeToByteArray(SpecialEntity.serializer(), specEntity)

        zos.putNextEntry(ZipEntry("SpecialEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val subStancEntity = DISimulationContainer.substancesEntity
        subStancEntity.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(SubstancesEntity.serializer(), subStancEntity)

        zos.putNextEntry(ZipEntry("SubstanceEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val specModDataEntity = DISimulationContainer.specialModDataEntity
        specModDataEntity.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(SpecialModDataEntity.serializer(), specModDataEntity)

        zos.putNextEntry(ZipEntry("SpecialModDataEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val tailEntitySave = DISimulationContainer.tailEntity
        tailEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(TailEntity.serializer(), tailEntitySave)

        zos.putNextEntry(ZipEntry("TailEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val eyeEntitySave = DISimulationContainer.eyeEntity
        eyeEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(EyeEntity.serializer(), eyeEntitySave)

        zos.putNextEntry(ZipEntry("EyeEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val linkEntitySave = DISimulationContainer.linkEntity
        linkEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(LinkEntity.serializer(), linkEntitySave)

        zos.putNextEntry(ZipEntry("LinkEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val neuralEntitySave = DISimulationContainer.neuralEntity
        neuralEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(NeuralEntity.serializer(), neuralEntitySave)

        zos.putNextEntry(ZipEntry("NeuralEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val organEntitySave = DISimulationContainer.organEntity
        organEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(OrganEntity.serializer(), organEntitySave)

        zos.putNextEntry(ZipEntry("OrganEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val particleEntitySave = DISimulationContainer.particleEntity
        particleEntitySave.serializeEntity()
        println("Size x: ${particleEntitySave.x.size}, 0 index: ${particleEntitySave.x[0]}")
        bytes = ProtoBuf.encodeToByteArray(ParticleEntity.serializer(), particleEntitySave)

        zos.putNextEntry(ZipEntry("ParticleEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val pheromoneEmitterEntitySave = DISimulationContainer.pheromoneEmitterEntity
        pheromoneEmitterEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(PheromoneEmitterEntity.serializer(), pheromoneEmitterEntitySave)

        zos.putNextEntry(ZipEntry("PheromoneEmitterEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val pheromoneEntitySave = DISimulationContainer.pheromoneEntity
        pheromoneEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(PheromoneEntity.serializer(), pheromoneEntitySave)

        zos.putNextEntry(ZipEntry("PheromoneEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()

        val producerEntitySave = DISimulationContainer.producerEntity
        producerEntitySave.serializeEntity()
        bytes = ProtoBuf.encodeToByteArray(ProducerEntity.serializer(), producerEntitySave)

        zos.putNextEntry(ZipEntry("ProducerEntity.bin"))
        zos.write(bytes, 0, bytes.size)
        zos.closeEntry()
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
