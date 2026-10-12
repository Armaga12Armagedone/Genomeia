package io.github.some_example_name.old.journal

import io.github.some_example_name.old.entities.CellEntity
import io.github.some_example_name.old.entities.LinkEntity
import io.github.some_example_name.old.entities.ParticleEntity

/**
 * Хэш состояния мира — то, что делает повтор бага ПРОВЕРЯЕМЫМ.
 *
 * Одинаковые команды ещё не значат одинаковый мир: если в симуляции есть хоть одна
 * недетерминированная операция (порядок потоков, случайность мимо seed), повтор тихо
 * уйдёт в сторону, а разработчик будет чинить не тот баг. Поэтому раз в
 * SimulationSystem.STATE_HASH_PERIOD тиков мир сворачивается в 64 бита, запись хранит
 * эти числа, а повтор считает свои и сверяет. Совпали все — повтор верный. Первое
 * несовпадение — первый тик, где он разошёлся.
 *
 * Что входит: координаты, скорости и радиусы частиц, энергия клеток, концы связей —
 * побитово (floatToRawIntBits), в порядке списков живых. Порядок тоже часть состояния:
 * если он разошёлся, разойдётся и всё, что обходит мир по списку.
 *
 * Стоимость — один проход по живым (~50 тыс. частиц — доли миллисекунды) раз в секунду
 * игрового времени, однопоточно в конце тика.
 */
object StateHash {

    fun compute(particles: ParticleEntity, cells: CellEntity, links: LinkEntity, tick: Int): Long {
        var h = Fnv.int(Fnv.OFFSET, tick)

        val aliveParticles = particles.aliveList
        h = Fnv.int(h, aliveParticles.size)
        for (i in 0 until aliveParticles.size) {
            val p = aliveParticles.getInt(i)
            h = Fnv.int(h, p)
            h = Fnv.float(h, particles.x[p])
            h = Fnv.float(h, particles.y[p])
            h = Fnv.float(h, particles.vx[p])
            h = Fnv.float(h, particles.vy[p])
            h = Fnv.float(h, particles.radius[p])
        }

        val aliveCells = cells.aliveList
        h = Fnv.int(h, aliveCells.size)
        for (i in 0 until aliveCells.size) {
            val c = aliveCells.getInt(i)
            h = Fnv.int(h, c)
            h = Fnv.float(h, cells.energy[c])
        }

        val aliveLinks = links.aliveList
        h = Fnv.int(h, aliveLinks.size)
        for (i in 0 until aliveLinks.size) {
            val l = aliveLinks.getInt(i)
            h = Fnv.int(h, links.links1[l])
            h = Fnv.int(h, links.links2[l])
        }
        return h
    }
}
