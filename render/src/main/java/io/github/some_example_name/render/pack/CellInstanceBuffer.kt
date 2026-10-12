package io.github.some_example_name.render.pack

import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Буфер инстансов клеток в том виде, в каком его читает GPU.
 *
 * ЗАЧЕМ ОТДЕЛЬНЫЙ КЛАСС
 * ---------------------
 * Раскладка этих 20 байт должна совпадать с тем, как CellGeometry объявляет инстансные
 * атрибуты, и с тем, что ждут вершинные шейдеры клеток (shaders/debug/circle_pc.vert,
 * shaders/nostalgic/nostalgic.vert).
 * Раньше знание о ней жило в трёх местах сразу: побитовые формулы — в
 * RenderBufferManager.writeCell и в EditorRenderSystem.putBuffer, порядок полей — в
 * RenderSystem и снова в putBuffer, а обратные формулы — в самом шейдере. Три копии одних
 * и тех же магических чисел, которые обязаны сходиться, иначе картинка едет молча.
 *
 * Теперь копий на CPU одна, и она покрыта тестами (CellInstanceBufferTest).
 *
 * РАСКЛАДКА (20 байт, порядок байт нативный)
 * ------------------------------------------
 *   [0..7]   float x, float y      → a_center  (2 × GL_FLOAT)
 *   [8..11]  int   color           → a_color   (4 × GL_UNSIGNED_BYTE, normalized)
 *   [12..15] int   shape           → a_shape   (4 × GL_UNSIGNED_BYTE, normalized)
 *   [16..19] int   type            → a_type    (4 × GL_UNSIGNED_BYTE, normalized)
 *
 *   shape = cosByte | sinByte<<8 | radiusByte<<16 | energyByte<<24
 *   type  = cellType | seedLo<<8  | seedHi<<16    | flags<<24
 *
 *   flags — битовые признаки частицы, см. [FLAG_NOT_CELL].
 *
 * ПОЧЕМУ НОРМАЛИЗОВАННЫЕ БАЙТЫ, А НЕ РАСПАКОВКА В ШЕЙДЕРЕ
 * -------------------------------------------------------
 * Раньше эти же 4 байта ехали одним uint и распаковывались вручную:
 *
 *     vec4 v1 = unpackRGBA8(packed1);   // сдвиги и маски
 *     float cosA = v1.x * 2.0 - 1.0;
 *
 * Ровно это железо выборки вершинных атрибутов делает бесплатно для типа
 * GL_UNSIGNED_BYTE с normalized = true. Заодно ушёл целый слой: текстура данных
 * RGBA32UI, её рост, загрузка glTexSubImage2D по строкам и остатку, и расхождение
 * float/uint между вебом и остальными платформами.
 *
 * ПРО ПОРЯДОК БАЙТ
 * ----------------
 * putInt пишет в нативном порядке, а GL читает компоненты атрибута в порядке адресов.
 * На little-endian (x86, ARM, WASM — то есть все целевые платформы) младший байт числа
 * оказывается компонентом .x, что здесь и предполагается. Прежний путь через текстуру
 * держался ровно на том же допущении.
 *
 * ПРО ПОТОКИ
 * ----------
 * shape/type считает поток симуляции (RenderBufferManager) и кладёт готовыми числами в
 * снимок, а GL-поток потом только переписывает их сюда. Так сделано намеренно: побитовая
 * арифметика на десятках тысяч клеток идёт параллельно кадру, а не внутри него. Поэтому
 * у класса два уровня API:
 *   - [put] — «положи уже готовые», для GL-потока;
 *   - [putCell] и функции из companion — упаковка, для того, кто считает.
 */
class CellInstanceBuffer(initialCapacity: Int = INITIAL_CAPACITY) {

    private var buffer: ByteBuffer = allocate(initialCapacity)

    /** Инстансов записано с последнего [begin]. Отдельным счётчиком, а не из position. */
    private var written = 0

    /**
     * Буфер уже перевёрнут под чтение.
     *
     * Нужно, чтобы [end] можно было звать сколько угодно раз за кадр. Редактор генома так
     * и делает: между тиками он не перезаписывает буфер (данные те же), но кадр рисует
     * каждый раз и буфер запрашивает заново. Голый flip() во второй раз выставил бы
     * limit = position = 0, то есть отдал бы ПУСТОЙ буфер — и клетки пропали бы с экрана.
     */
    private var finished = false

    /** Сколько инстансов помещается без пересоздания. */
    val capacity: Int get() = buffer.capacity() / STRUCT_SIZE

    /** Сколько инстансов записано с последнего [begin]. */
    val count: Int get() = written

    /**
     * Начать кадр.
     *
     * [expectedInstances] — ёмкость, которая точно понадобится. Передавать её стоит, когда
     * размер известен заранее: тогда рост случится один раз здесь, а не в середине записи.
     * Если размер неизвестен, [put] дорастит сам.
     */
    fun begin(expectedInstances: Int = 0) {
        ensureCapacity(expectedInstances)
        (buffer as Buffer).clear()
        written = 0
        finished = false
    }

    /** Готовые shape/type — путь GL-потока, без арифметики. */
    fun put(x: Float, y: Float, color: Int, shape: Int, type: Int) {
        ensureRoomForOneMore()
        buffer.putFloat(x)
        buffer.putFloat(y)
        buffer.putInt(color)
        buffer.putInt(shape)
        buffer.putInt(type)
        written++
    }

    /**
     * Упаковать и положить за один вызов.
     *
     * [noiseSeed] — устойчивый ключ шума: шейдер доворачивает текстуру на случайный угол,
     * взятый из него. Ключ обязан быть закреплён за клеткой на всю жизнь, иначе доворот
     * пересчитывается каждый кадр и текстуры визуально крутятся. Индекс частицы годится,
     * позиция в буфере — нет.
     */
    fun putCell(
        x: Float,
        y: Float,
        color: Int,
        angleCos: Float,
        angleSin: Float,
        radius: Float,
        energy: Float,
        cellType: Int,
        noiseSeed: Int,
        isCell: Boolean = true
    ) {
        put(
            x = x,
            y = y,
            color = color,
            shape = shape(
                cosByte = angleByte(angleCos),
                sinByte = angleByte(angleSin),
                radiusByte = radiusByte(radius),
                energyByte = energyByte(energy)
            ),
            type = type(cellType = cellType, noiseSeed = noiseSeed, isCell = isCell)
        )
    }

    /** Завершить кадр и отдать буфер на загрузку в инстансный VBO. Идемпотентно. */
    fun end(): ByteBuffer {
        if (!finished) {
            (buffer as Buffer).flip()
            finished = true
        }
        return buffer
    }

    /**
     * Рост на ×1.5 с пересозданием.
     *
     * Direct-буфер нельзя расширить на месте, поэтому уже записанное копируется в новый.
     * На установившемся режиме этого не происходит вообще: ёмкость подбирается один раз
     * под размер мира и дальше держится.
     */
    fun ensureCapacity(instances: Int) {
        val current = capacity
        if (instances <= current) return

        var grown = current.toDouble()
        do {
            grown *= GROWTH_FACTOR
        } while (grown < instances)

        val bigger = allocate(grown.toInt().coerceAtLeast(instances))

        // Копируем ровно записанное, а не всю ёмкость.
        if (written > 0) {
            val old = buffer
            (old as Buffer).position(0)
            (old as Buffer).limit(written * STRUCT_SIZE)
            bigger.put(old)
        }
        buffer = bigger
    }

    private fun ensureRoomForOneMore() {
        if (buffer.remaining() < STRUCT_SIZE) ensureCapacity(written + 1)
    }

    private fun allocate(instances: Int): ByteBuffer =
        ByteBuffer
            .allocateDirect(instances.coerceAtLeast(1) * STRUCT_SIZE)
            .order(ByteOrder.nativeOrder())

    companion object {
        /**
         * 20 байт на инстанс: 2 float + 3 упакованных четвёрки байт.
         * Менять только вместе с раскладкой атрибутов в CellGeometry и с шейдерами клеток
         * (circle_pc.vert, nostalgic.vert).
         */
        const val STRUCT_SIZE = 20

        /** Смещения полей внутри инстанса — их же объявляет CellGeometry. */
        const val OFFSET_CENTER = 0
        const val OFFSET_COLOR = 8
        const val OFFSET_SHAPE = 12
        const val OFFSET_TYPE = 16

        const val INITIAL_CAPACITY = 30_000

        private const val GROWTH_FACTOR = 1.5

        /**
         * Диапазон радиуса, который влезает в один байт.
         * Обратное преобразование в шейдере: `ex_R = 0.05 + a_shape.z * 0.7`.
         */
        const val RADIUS_MIN = 0.05f
        const val RADIUS_SPAN = 0.7f

        /**
         * Делитель энергии: при ней байт насыщается, и чёрная точка внутри клетки
         * достигает максимума. Это не физическая энергия, а только её показ.
         *
         * Читают её два шейдера, и по-разному. Отладка (circle.frag): `a_shape.w * 0.5`
         * в квадрате — радиус точки в единицах мира. Ностальгический (nostalgic.vert):
         * линейно, в долях радиуса клетки — см. NostalgicRenderer.NUCLEUS_AT_FULL_ENERGY.
         */
        const val ENERGY_SPAN = 10f

        /** cos/sin из [-1..1] в байт. Обратно: `a_shape.x * 2.0 - 1.0`. */
        fun angleByte(value: Float): Int =
            ((value * 0.5f + 0.5f) * 255f + 0.5f).toInt().coerceIn(0, 255)

        fun radiusByte(radius: Float): Int =
            (((radius - RADIUS_MIN) / RADIUS_SPAN) * 255f + 0.5f).toInt().coerceIn(0, 255)

        fun energyByte(energy: Float): Int =
            ((energy / ENERGY_SPAN) * 255f + 0.5f).toInt().coerceIn(0, 255)

        /** cos, sin, радиус, энергия — по байту на компоненту. */
        fun shape(cosByte: Int, sinByte: Int, radiusByte: Int, energyByte: Int): Int =
            (cosByte and 0xFF) or
                ((sinByte and 0xFF) shl 8) or
                ((radiusByte and 0xFF) shl 16) or
                ((energyByte and 0xFF) shl 24)

        /**
         * Частица не клетка (субстанция и прочее): у неё нет ядра.
         *
         * Отдельным битом, а не по номеру типа. Номер «не клетки» задаёт :core (последний
         * слой TextureArray, и тот достаётся через зажим индекса слоя), и сравнивать с ним
         * в шейдере значило бы протащить в рендер чужую договорённость о порядке слоёв.
         *
         * Бит именно «не клетка», а не «клетка»: тогда ноль, который лежит в этом байте у
         * всех, кто про флаги не знает (редактор генома, старые дампы), означает обычную
         * клетку, а поменять пришлось ровно одно место — запись не-клеток в :core.
         */
        const val FLAG_NOT_CELL = 1

        /**
         * Тип клетки (индекс слоя в TextureArray), 16-битный ключ шума двумя байтами и
         * байт флагов.
         *
         * Ключ раскладывается на два байта, потому что нормализованный атрибут отдаёт в
         * шейдер четыре независимых компоненты, а не одно число. Собирается обратно как
         * `lo + hi*256` с округлением — точно, без потери бита.
         */
        fun type(cellType: Int, noiseSeed: Int, isCell: Boolean = true): Int {
            val seed = noiseSeed and 0xFFFF
            val flags = if (isCell) 0 else FLAG_NOT_CELL
            return cellType.coerceIn(0, 255) or
                ((seed and 0xFF) shl 8) or
                (((seed ushr 8) and 0xFF) shl 16) or
                (flags shl 24)
        }
    }
}
