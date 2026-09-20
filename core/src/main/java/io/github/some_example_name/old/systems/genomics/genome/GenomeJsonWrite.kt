package io.github.some_example_name.old.systems.genomics.genome

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import kotlin.collections.iterator


class CreatureJsonWrite(
    var name: String = "",  // Made var for assignment in read
    val genomeStageInstruction: ArrayList<GenomeStageJsonWrite> = ArrayList()
)

class GenomeStageJsonWrite(
    val cellActions: HashMap<String, CellActionJsonWrite> = hashMapOf()
)

class CellActionJsonWrite(
    val divide: ActionJsonWrite? = null,
    val mutate: ActionJsonWrite? = null
): Json.Serializable {
    override fun write(json: Json) {
        if (divide != null) json.writeValue("divide", divide)
        if (mutate != null) json.writeValue("mutate", mutate)
    }

    override fun read(json: Json, jsonData: JsonValue) {

    }
}

class ActionJsonWrite(
    val id: String? = null,
    val angle: Float? = null,
    val cellType: Int? = null,
    val physicalLink: HashMap<String, LinkDataJsonWrite?> = hashMapOf(),
    val radius: Float? = null,
    val color: Color? = null,
    val angleDirected: Float? = null,
    val startDirectionId: Int? = null,
    val funActivation: Int? = null,
    val a: Float? = null,
    val b: Float? = null,
    val c: Float? = null,
    val isSum: Boolean? = null,
    val colorRecognition: Int? = null,
    val lengthDirected: Float? = null,
    val pheromoneType: Int? = null,
    val specialData: SpecialDataJsonWrite? = null
): Json.Serializable {

    override fun write(json: Json) {
        if (id != null) json.writeValue("id", id)
        if (angle != null) json.writeValue("angle", angle)
        if (cellType != null) json.writeValue("cellType", cellType)
        json.writeObjectStart("physicalLink")
        for ((key, value) in physicalLink) {
            json.writeValue(key, value)
        }
        json.writeObjectEnd()
        if (color != null) json.writeValue("colorHsv", color.toString())
        if (radius != null) json.writeValue("radius", radius)
        if (angleDirected != null) json.writeValue("angleDirected", angleDirected)
        if (startDirectionId != null) json.writeValue("startDirectionId", startDirectionId)
        if (funActivation != null) json.writeValue("funActivation", funActivation)
        if (a != null) json.writeValue("a", a)
        if (b != null) json.writeValue("b", b)
        if (c != null) json.writeValue("c", c)
        if (isSum != null) json.writeValue("isSum", isSum)
        if (colorRecognition != null) json.writeValue("colorRecognition", colorRecognition)
        if (lengthDirected != null) json.writeValue("lengthDirected", lengthDirected)
        if (pheromoneType != null) json.writeValue("pheromoneType", pheromoneType)
        if (specialData != null) json.writeValue("specialData", specialData)
    }

    override fun read(json: Json, jsonData: JsonValue) {

    }

}

class SpecialDataJsonWrite(
    val attachedKey: Char?
): Json.Serializable {
    override fun write(json: Json) {
        if (attachedKey != null) json.writeValue("attachedKey", attachedKey) // всегда пишем
    }

    override fun read(json: Json, jsonData: JsonValue) {

    }
}

class LinkDataJsonWrite(
    val length: Float? = null,
    val weight: Float? = null,
    val color: Color? = null,
    val directedNeuronLink: Int? = null
): Json.Serializable {
    override fun write(json: Json) {
        if (length != null) json.writeValue("length", length) // всегда пишем
        if (weight != null) json.writeValue("weight", weight)
        if (color != null) json.writeValue("color", color.toString())
        if (directedNeuronLink != null) json.writeValue("directedNeuronLink", directedNeuronLink)
    }

    override fun read(json: Json, jsonData: JsonValue) {

    }
}


//Domain to json
fun Genome.domainToJson(): CreatureJsonWrite {
    return CreatureJsonWrite(
        name = this.name,
        genomeStageInstruction = this.genomeStageInstruction.map { it.toJson() }
            .toCollection(ArrayList())
    )
}

private fun GenomeStage.toJson(): GenomeStageJsonWrite {
    return GenomeStageJsonWrite(
        cellActions = this.cellActions.mapKeys { (k, _) -> k.toString() }
            .mapValues { (_, v) -> v.toJson() } as HashMap<String, CellActionJsonWrite>
    )
}

private fun CellAction.toJson(): CellActionJsonWrite {
    return CellActionJsonWrite(
        divide = this.divide?.toJson(),
        mutate = this.mutate?.toJson()
    )
}

private fun Action.toJson(): ActionJsonWrite {
    return ActionJsonWrite(
        id = if (this.id == -1) null else this.id.toString(),
        angle = this.angle,
        cellType = this.cellType,
        physicalLink = this.physicalLink.mapKeys { (k, _) -> k.toString() }
            .mapValues { (_, v) -> v?.toJson() } as HashMap<String, LinkDataJsonWrite?>,
        radius = this.radius,
        color = this.color,
        angleDirected = this.angleDirected,
        funActivation = this.funActivation,
        a = this.a,
        b = this.b,
        c = this.c,
        isSum = this.isSum,
        colorRecognition = this.colorRecognition,
        lengthDirected = this.lengthDirected?.times(40f),
        pheromoneType = pheromoneType,
        specialData = specialData?.toJson()
    )
}

private fun SpecialData.toJson(): SpecialDataJsonWrite {
    return SpecialDataJsonWrite(
        attachedKey = this.attachedKey
    )
}

private fun LinkData.toJson(): LinkDataJsonWrite {
    return LinkDataJsonWrite(
        length = this.length?.times(40f),
        weight = this.weight,
        color = this.color,
        directedNeuronLink = this.directedNeuronLink
    )
}
