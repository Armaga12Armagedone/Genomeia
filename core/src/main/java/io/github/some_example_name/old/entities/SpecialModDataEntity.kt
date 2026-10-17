package io.github.some_example_name.old.entities

import io.github.some_example_name.old.cells.base.SpecialModData
import io.github.some_example_name.old.cells.base.SpecialModDataTypes
import it.unimi.dsi.fastutil.ints.IntArrayList

/**
 * Свои данные клеток (SpecialModData): слот на клетку, у которой они есть. Клетка находит
 * свой слот через SpecialEntity.specialTypeIndexes, как глаз или хвост.
 *
 * Объекты неизменяемые и общие с геномом (см. SpecialModData), поэтому слот — это просто
 * ссылка: деление ничего не копирует. Такие клетки редки (контроллеры), и лишнее
 * разыменование на клетку за тик здесь не в счёт.
 */
class SpecialModDataEntity(
    specialModDataStartMaxAmount: Int
): Entity(specialModDataStartMaxAmount) {

    var specialModData = arrayOfNulls<SpecialModData>(maxAmount)

    fun addModData(
        modData: SpecialModData
    ): Int {
        val modDataIndex = add()
        specialModData[modDataIndex] = modData
        return modDataIndex
    }

    fun deleteModData(modDataIndex: Int) {
        delete(modDataIndex)
        specialModData[modDataIndex] = null
    }

    /**
     * В сохранение и хэш — protobuf-байты каждого занятого слота вместе с именем типа
     * (SpecialModDataTypes.encode), упакованные в int'ы: [слот, длина в байтах, байты по 4].
     * Слоты по возрастанию — одинаковый мир даёт одинаковые байты.
     */
    override fun persistFields(state: EntityState) {
        state.packed("specialModData", write = ::pack, read = ::unpack)
    }

    private fun pack(): IntArray {
        val packed = IntArrayList()
        for (slot in 0..lastId) {
            val data = specialModData[slot] ?: continue
            val bytes = SpecialModDataTypes.encode(data)
            packed.add(slot)
            packed.add(bytes.size)
            var i = 0
            while (i < bytes.size) {
                var word = 0
                for (b in 0 until 4) {
                    if (i + b < bytes.size) word = word or ((bytes[i + b].toInt() and 0xFF) shl (8 * b))
                }
                packed.add(word)
                i += 4
            }
        }
        return packed.toIntArray()
    }

    private fun unpack(packed: IntArray) {
        var p = 0
        while (p < packed.size) {
            val slot = packed[p++]
            val bytes = ByteArray(packed[p++])
            var i = 0
            while (i < bytes.size) {
                val word = packed[p++]
                for (b in 0 until 4) {
                    if (i + b < bytes.size) bytes[i + b] = (word ushr (8 * b)).toByte()
                }
                i += 4
            }
            specialModData[slot] = SpecialModDataTypes.decode(bytes)
        }
    }

    override fun onCopy() {

    }

    override fun onPaste() {

    }

    override fun onClear(bound: Int) {
        specialModData.fill(null, 0, bound)
    }

    override fun onResize(oldMax: Int) {
        run {
            val old = specialModData
            specialModData = arrayOfNulls(maxAmount)
            System.arraycopy(old, 0, specialModData, 0, oldMax)
        }
    }
}
