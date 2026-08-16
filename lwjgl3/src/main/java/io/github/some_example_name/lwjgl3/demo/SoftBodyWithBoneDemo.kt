package io.github.some_example_name.lwjgl3.demo

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import io.github.some_example_name.lwjgl3.StartupHelper
import kotlin.math.sqrt

/**
 * МЯГКАЯ ТКАНЬ ВОКРУГ КОСТИ — 2D XPBD + shape matching на плотной сетке.
 *
 * Продолжение BoneInSoftBodyDemo, но тело здесь не лента из 18 вершин, а сетка из
 * нескольких сотен: капсула из «плоти», внутри которой лежит одна жёсткая кость,
 * окружённая мягкой тканью со всех сторон.
 *
 * ЧТО ЭТО ПОКАЗЫВАЕТ СВЕРХ ПРЕДЫДУЩЕЙ ДЕМКИ
 * -----------------------------------------
 * 1. Кость держится ТОЧНО независимо от размера тела. Мягкая часть — нет: чем длиннее
 *    цепь ограничений, тем хуже её решает Гаусс-Зейдель за одну итерацию на подшаг.
 *    Здесь это видно прямо: плоть на краях заметно тянется, кость в центре — нет.
 *    Это то самое свойство, ради которого кости и нужны крупным телам.
 *
 * 2. Проекция стоит ТРИ ПРОХОДА ПО ВЕРШИНАМ КОСТИ и ничего больше. Связи и диагонали
 *    внутри кости не создаются вообще (см. addConstraint) — кость не только жёстче
 *    мягкой ткани, но и дешевле её.
 *
 * 3. Топология произвольная. Сетка обрезана по суперэллипсу, поэтому у неё рваный край
 *    и разное число соседей у вершин. Ни решателю, ни проекции это безразлично.
 *
 * УПРАВЛЕНИЕ
 * ----------
 * ЛКМ — тянуть за любую вершину, SPACE — пауза, R — сброс, B — жёсткость кости вкл/выкл
 */
class SoftBodyWithBoneDemo : ApplicationAdapter() {

    companion object {

        // =============================================================
        //  ПАРАМЕТРЫ СИМУЛЯЦИИ
        // =============================================================

        private const val DT = 1f / 144f

        /**
         * Подшагов XPBD на кадр. Одна итерация решателя на подшаг («small steps»).
         *
         * ЭТО ЕДИНСТВЕННОЕ, ЧТО ЛЕЧИТ ВМЯТИНЫ ОТ УДАРА, и число здесь не подбирается на
         * глаз, а СЧИТАЕТСЯ. Критерий один: за подшаг вершина не должна проскакивать
         * дальше половины ячейки сетки, иначе она перепрыгивает соседей и решателю уже
         * нечего исправлять — форма защёлкивается в смятую.
         *
         *     SUBSTEPS >= v_max * DT / (0.5 * STEP)
         *
         * Замер падения на землю (STEP = 0.075, DT = 1/144):
         *
         *   удар  подшагов  пик вывернутых  худшая ошибка площади  остаточная форма
         *    25       4           35               1.87              0.0450 (= покой)
         *    40       4          161               4.15              0.0731
         *    60       4          218               5.23              0.0532
         *    60      12            0               0.79              0.0425
         *
         * Формула на удар 60 даёт 60 * (1/144) / 0.0375 = 11.1, то есть 12 — ровно та
         * строка, где всё чинится. Ни изгиб, ни ограничение площади так не умеют:
         * площадь и так стоит на compliance = 0, а изгиб делает только хуже.
         */
        private const val SUBSTEPS = 4

        /**
         * Податливость мягкой ткани, alpha = 1/k.
         *
         * Заметно ниже, чем в демке с лентой (там 5e-5): вершин на порядок больше, цепи
         * ограничений длиннее, и при той же податливости тело растекалось бы киселём.
         * Это не подгонка ради красоты, а ровно та зависимость от длины цепи, о которой
         * речь в шапке файла.
         */
        private const val SOFT_COMPLIANCE = 1.0e-4f

        /**
         * Податливость ограничения ЗНАКОВОЙ ПЛОЩАДИ. Ноль — площадь несжимаема.
         *
         * Зачем оно вообще нужно, если длины сторон и обе диагонали уже зафиксированы:
         * у такого четырёхугольника РОВНО ДВЕ конфигурации — правильная и её зеркало.
         * Длины в обеих одинаковы, поэтому решатель одинаково доволен и вывернутой.
         * Достаточно один раз продавить угол о пол, и ячейка складывается наизнанку
         * навсегда: вернуть её ограничения расстояния уже не могут.
         *
         * Знаковая площадь в вывернутом состоянии меняет знак, ошибка становится
         * огромной, и треугольник выдавливается обратно. Это то же самое, что делает
         * ограничение ОБЪЁМА тетраэдра у Мюллера, и то же, что считает processTriangles
         * в движке.
         */
        private const val AREA_COMPLIANCE = 0f

        /** Подвесить левый край за верхний ряд. false — тело просто падает на пол. */
        private const val PIN_LEFT_EDGE = false

        private const val GRAVITY = 0.0f
        private const val GROUND_Y = 0f

        // =============================================================
        //  ДИССИПАЦИЯ
        //
        //  Три РАЗНЫЕ вещи, которые легко перепутать:
        //    GROUND_FRICTION — трение о пол, только в точке контакта
        //    MEDIUM_DRAG     — сопротивление среды, гасит движение тела ЦЕЛИКОМ
        //    VISCOSITY       — внутреннее трение ткани, гасит только ОТНОСИТЕЛЬНОЕ
        //                      движение соседей и не мешает телу лететь и вращаться
        //
        //  Ни одна из них не возвращает форму. Вязкость замедляет складывание, но
        //  сложившееся тело обратно не расправит: возвращающей силы у неё нет.
        // =============================================================

        /**
         * Трение о пол: доля касательного смещения, откатываемая при контакте.
         * 1 — полное прилипание (так было раньше), 0 — идеальный лёд.
         */
        private const val GROUND_FRICTION = 0.8f

        /**
         * Сопротивление среды, 1/сек. Умножается на h, поэтому от числа подшагов
         * и от величины кадра результат не зависит.
         *
         * Это аналог dragCoefficient в движке: гасит абсолютную скорость, то есть
         * тормозит и полёт, и вращение тела как целого.
         */
        private const val MEDIUM_DRAG = 0.4f

        /**
         * Внутренняя вязкость ткани, 1/сек.
         *
         * Сближает скорости связанных вершин, то есть штрафует СКОРОСТЬ ДЕФОРМАЦИИ,
         * а не саму скорость. Импульс при этом сохраняется точно (см. applyViscosity),
         * поэтому равномерно летящее или вращающееся тело она не тормозит вообще —
         * в отличие от MEDIUM_DRAG.
         *
         * Физически это и есть «мясо, а не резина»: без вязкости ткань звенит после
         * каждого удара, потому что энергию деформации гасить нечем.
         *
         * ЭТО ГЛАВНАЯ РУЧКА «УПРУГОСТИ». Живая ткань не менее жёсткая, чем резина, — она
         * СИЛЬНЕЕ ДЕМПФИРОВАНА, то есть вязкоупруга. Замер размаха центра масс на кадрах
         * 240-390 при SOFT_COMPLIANCE = 2e-4, падение с высоты:
         *
         *     VISCOSITY =  30  ->  0.0233   звенит и на 6.5 секунде
         *     VISCOSITY = 300  ->  0.0015   село к 240-му кадру, в 16 раз меньше
         *
         * Оговорка про потолок: k = VISCOSITY * h ограничено 0.5 (см. applyViscosity).
         * При DT = 1/144 и SUBSTEPS = 4 это h = 1/576, поэтому всё выше ~290 упирается
         * в потолок и дальше уже ничего не меняет.
         */
        private const val VISCOSITY = 200f

        /**
         * Упругость удара о пол. 0 — удар полностью неупругий, 1 — идеальный отскок.
         *
         * Механизм, который это закрывает: в integrate частица зажимается на уровень пола,
         * затем решатель тянет её вверх (соседи-то выше), а updateVelocities превращает
         * смещение в настоящую скорость вверх — проекция впрыскивает энергию в контакт.
         * applyRestitution гасит у контактных частиц скорость, направленную ВВЕРХ.
         *
         * ЧЕСТНАЯ ОГОВОРКА ПО ЗАМЕРУ: на падении тела с высоты разницы между 0 и 1 здесь
         * НЕ ВИДНО — трассы центра масс совпадают до третьего знака. Тело просто не
         * отрывается от пола (minY всё время около нуля), и подпрыгивание, которое видно
         * глазом, идёт не от контакта, а от упругости самой ткани: его лечит VISCOSITY.
         * Проход оставлен, потому что он нужен, когда тело реально отрывается — например
         * после броска мышью, — но «резиновость» лечится не им.
         */
        private const val GROUND_RESTITUTION = 0f

        /**
         * Одна диагональ на ячейку вместо двух.
         *
         * Двух диагоналей достаточно, чтобы четырёхугольник стал жёстким, но и одной уже
         * хватает: она режет его на два треугольника, каждый из которых жёсток своими
         * тремя сторонами. Вторая — чистая избыточность, а избыточные ограничения
         * Гаусс-Зейдель «переужесточают»: материал начинает locking'овать и звенеть.
         *
         * Направление диагонали чередуется по чётности ячейки, иначе у ткани появляется
         * выделенное направление и она деформируется несимметрично.
         */
        private const val SINGLE_DIAGONAL = true

        /**
         * Связи ИЗГИБА: между узлами через один, по обеим осям сетки.
         *
         * ЗАЧЕМ. Длины и знаковые площади не запрещают телу продавиться внутрь: вмятина
         * это НЕ выворачивание (площади остаются положительными) и не разрыв (длины
         * почти в норме) — это вторая устойчивая форма упругой оболочки, как у щёлкающей
         * крышки от банки. Обе конфигурации одинаково законны, поэтому под собственной
         * упругостью тело из вмятины не возвращается.
         *
         * Связь через один узел штрафует КРИВИЗНУ: у прямого участка она равна двум шагам
         * сетки, у продавленного заметно короче. Это поднимает барьер между двумя формами,
         * и вмятина перестаёт быть устойчивой.
         *
         * Ставится заведомо мягче структурных: иначе тело перестанет гнуться вообще.
         *
         * ЗАМЕР ГОВОРИТ, ЧТО ЭТО НЕ РАБОТАЕТ. На ударе о землю со скоростью 60 изгиб
         * не уменьшил ни выворачивание (218 треугольников против 221 с ним), ни смятие —
         * худшая ошибка площади ВЫРОСЛА вдвое, с 5.23 до 10.61, а остаточная деформация
         * с 0.0532 до 0.0597.
         *
         * Почему хуже: изгибные связи участвуют в том же проходе Гаусса-Зейделя, что и
         * структурные, и на ударе оттягивают на себя часть поправки — жёсткость сетки
         * там, где она нужна, падает. Лечится удар не дополнительными ограничениями,
         * а МЕНЬШИМ ШАГОМ (см. SUBSTEPS). Оставлено выключенным и только как опция.
         */
        private const val BENDING_ENABLED = false
        private const val BENDING_COMPLIANCE = 2e-3f

        // =============================================================
        //  МЫШЦЫ
        //
        //  Мягкая ткань разбита на кластеры-мышцы. Наводишь мышь на ребро — весь его
        //  кластер плавно сокращается, уводишь — так же плавно расправляется.
        //
        //  Механизм ровно тот же, что degreeOfShortening в движке: меняется не сила,
        //  а ДЛИНА ПОКОЯ ограничения. Жёсткость остаётся прежней, поэтому мышца тянет
        //  ровно с той силой, которая нужна, чтобы дотащить ткань до новой длины —
        //  и упирается, если ей мешают. Пружину с переменной силой так не настроить.
        // =============================================================

        /** До какой доли исходной длины сокращается мышца. */
        private const val MUSCLE_CONTRACTION = 0.5f

        /**
         * Скорость сокращения и расслабления, 1/сек: доля оставшегося пути за секунду.
         *
         * Мгновенное переключение длины покоя — это скачок ошибки ограничения на 50%,
         * то есть удар. Поэтому активация едет к цели экспоненциально.
         */
        private const val MUSCLE_RATE = 6f

        /** Размер кластера в ячейках сетки. */
        private const val MUSCLE_CLUSTER_W = 5
        private const val MUSCLE_CLUSTER_H = 4

        /** Радиус захвата РЕБРА мышью, в мировых единицах. */
        private const val EDGE_PICK_RADIUS = 0.05f

        /**
         * ОГРАНИЧЕНИЕ СИЛЫ ТЯГИ — максимальное смещение вершины к мыши за подшаг.
         *
         * Раньше тяга была реализована телепортом: invMass = 0 плюс запись позиции прямо
         * в мышь. Это бесконечная сила, которую не может остановить НИ ОДНО ограничение,
         * поэтому вершина протаскивалась сквозь соседей, треугольники выворачивались
         * (замер на реальном продавливании: пик 91 вывернутый треугольник), а тело
         * защёлкивалось в сложенную форму и там оставалось — площади к тому моменту уже
         * возвращались в плюс, так что вытащить его обратно было нечем.
         *
         * Теперь тяга — обычное XPBD-ограничение расстояния до курсора, а поправка
         * зажата этим потолком. Сила конечна, решатель успевает её отработать, и сквозь
         * себя тело больше не проходит.
         *
         * Величина в мировых единицах за ПОДШАГ: при 4 подшагах и 144 кадрах это
         * 0.02 * 576 = 11.5 единиц в секунду — быстрее, чем тело успевает двигаться само.
         */
        private const val MAX_DRAG_STEP = 0.02f

        /** Податливость тяги. Ноль был бы жёстким притяжением, потолок выше его и ограничит. */
        private const val DRAG_COMPLIANCE = 1e-6f

        /** Вес перетаскиваемой вершины в подгонке формы: кость едет за мышью целиком. */
        private const val DRAG_MATCH_WEIGHT = 200f
        private const val PICK_RADIUS = 0.06f

        // =============================================================
        //  ГЕОМЕТРИЯ ТЕЛА
        // =============================================================

        /** Узлов сетки по горизонтали и вертикали ДО обрезки по контуру. */
        private const val COLS = 34
        private const val ROWS = 13

        /** Шаг сетки в мировых единицах. */
        private const val STEP = 0.075f

        private const val CENTER_X = 1.55f
        private const val CENTER_Y = 0.80f

        /**
         * Полуоси суперэллипса |dx/a|^4 + |dy/b|^4 <= 1, по которому обрезается сетка.
         * Степень 4 даёт капсулу: плоские бока и скруглённые торцы.
         */
        private const val SHAPE_A = 1.24f
        private const val SHAPE_B = 0.47f

        /** Прямоугольник кости в координатах сетки, границы включительно. */
        private const val BONE_COL_FROM = 11
        private const val BONE_COL_TO = 22
        private const val BONE_ROW_FROM = 5
        private const val BONE_ROW_TO = 7

        // =============================================================
        //  ВИД
        // =============================================================

        private const val VIEW_WIDTH = 3.4f
        private const val VIEW_HEIGHT = 2.125f

        private val BG            = Color.valueOf("12161E")
        private val GROUND_COLOR  = Color.valueOf("2B3341")
        private val SOFT_FILL     = Color.valueOf("5A96DC3D")
        private val LINK          = Color.valueOf("96B4DC33")
        private val BONE_FILL     = Color.valueOf("E8A33D8C")
        private val BONE_FILL_OFF = Color.valueOf("E8A33D21")
        private val BONE_EDGE     = Color.valueOf("E8A33DFF")
        private val BONE_EDGE_OFF = Color.valueOf("E8A33D73")
        private val DOT           = Color.valueOf("DFE6F088")
        private val PINNED_COLOR  = Color.valueOf("F2645AFF")
        private val ACTIVE        = Color.valueOf("FFFFFFFF")
        private val MUSCLE_FILL   = Color.valueOf("D65A6BCC")
        private val SEAM_FILL     = Color.valueOf("4A5261AA")
        private val TRI_FILL_A    = Color.valueOf("4E7FBEAA")   // треугольник площади, чётный
        private val TRI_FILL_B    = Color.valueOf("2F5C93AA")   // он же, нечётный
        private val EDGE_SIDE     = Color.valueOf("A8C4E8CC")   // сторона ячейки: растяжение
        private val EDGE_SHEAR    = Color.valueOf("E8C84ECC")   // диагональ: сдвиг
        private val INVERTED_FILL = Color.valueOf("FF3B30EE")   // вывернутый треугольник
        private val HUD_TEXT      = Color.valueOf("EAF0F8FF")
        private val HUD_MUTED     = Color.valueOf("76818FFF")
    }

    // =================================================================
    //  ДАННЫЕ ЧАСТИЦ (SoA)
    // =================================================================

    private var particleCount = 0

    private lateinit var px: FloatArray
    private lateinit var py: FloatArray
    private lateinit var prevX: FloatArray
    private lateinit var prevY: FloatArray
    private lateinit var vx: FloatArray
    private lateinit var vy: FloatArray
    private lateinit var invMass: FloatArray
    private lateinit var restX: FloatArray
    private lateinit var restY: FloatArray
    private lateinit var matchWeight: FloatArray

    /** true, если вершина входит в кость. Кость здесь одна, поэтому хватает флага. */
    private lateinit var isBone: BooleanArray

    /** Вершины кости подряд — то, по чему бегает проекция. */
    private lateinit var boneMembers: IntArray

    /** Вершины, закреплённые в позе покоя (PIN_LEFT_EDGE). */
    private lateinit var isPinned: BooleanArray

    /** Стояла ли вершина на полу в этом подшаге — нужно проходу упругости удара. */
    private lateinit var inContact: BooleanArray

    // =================================================================
    //  МЫШЦЫ
    // =================================================================

    /** Номер мышечного кластера вершины, -1 у кости. */
    private lateinit var clusterOf: IntArray

    private var clusterCount = 0

    /**
     * Насколько кластер сокращён: 0 — покой, 1 — полное сокращение.
     * Едет к цели экспоненциально, поэтому длина покоя меняется без удара.
     */
    private lateinit var clusterActivation: FloatArray

    /** Кластер под мышью, -1 если курсор не у ребра. */
    private var hoveredCluster = -1

    /** Кластер связи и треугольника: -1, если элемент лежит на шве между кластерами. */
    private lateinit var conCluster: IntArray
    private lateinit var triCluster: IntArray

    /** Цвет кластера для режима разбора (клавиша C). */
    private lateinit var clusterColors: Array<Color>

    /** Буферы под центроиды кластеров — считаются только когда режим включён. */
    private lateinit var clusterCx: FloatArray
    private lateinit var clusterCy: FloatArray
    private lateinit var clusterN: IntArray

    /**
     * Режим отрисовки, переключается клавишей C:
     *   0 — обычный
     *   1 — кластеры МЫШЦ (нарезка длин покоя)
     *   2 — разбор ФИЗИКИ: треугольники площади и роли рёбер
     *
     * Первое и второе — разные вещи, и их легко перепутать. Мышечные кластеры это
     * разбиение ограничений на группы, которым синхронно меняют длину покоя. Разбор
     * физики показывает сами ограничения: из чего вообще состоит решатель.
     */
    private var viewMode = 0

    private val showClusters get() = viewMode == 1
    private val showPhysics get() = viewMode == 2

    /** Диагональ ячейки или её сторона. Только для разбора: в решателе роли не играет. */
    private lateinit var conIsDiagonal: BooleanArray

    /** Связь изгиба (через узел). Мягче структурных, поэтому податливость своя у каждой. */
    private lateinit var conIsBending: BooleanArray
    private lateinit var conCompliance: FloatArray

    // =================================================================
    //  ОГРАНИЧЕНИЯ (SoA)
    // =================================================================

    private var constraintCount = 0
    private lateinit var conA: IntArray
    private lateinit var conB: IntArray
    private lateinit var conRest: FloatArray

    /** Треугольники для ограничения знаковой площади: по два на ячейку. */
    private var triCount = 0
    private lateinit var triA: IntArray
    private lateinit var triB: IntArray
    private lateinit var triC: IntArray

    /** Удвоенная знаковая площадь покоя — удвоенная, чтобы не делить на два в цикле. */
    private lateinit var triRestArea2: FloatArray

    /**
     * ДИАГНОСТИКА ВЫВОРАЧИВАНИЯ.
     *
     * Отвечает на вопрос, который иначе решается на глаз и обычно неверно: когда тело
     * продавливается и не расправляется — это треугольники вывернулись наизнанку
     * (площадь ушла в минус) или это вмятина при полностью положительных площадях?
     *
     * Болезни разные. Выворачивание лечится ограничением площади, и если счётчик стоит
     * на нуле, то усиливать его бессмысленно — площадь и так не пускает. Вмятина же
     * это вторая устойчивая форма упругой оболочки, её площадь не запрещает вовсе,
     * и лечится она изгибом или подгонкой формы.
     */
    private lateinit var triInverted: BooleanArray
    private var invertedNow = 0
    private var invertedPeak = 0

    // =================================================================
    //  ЯЧЕЙКИ ДЛЯ ЗАЛИВКИ
    // =================================================================

    private var cellCount = 0
    private var cellV = IntArray(0)                 // по 4 вершины на ячейку
    private lateinit var cellIsBone: BooleanArray

    /** Кластер ячейки — только для подсветки, в физике не участвует. */
    private lateinit var cellCluster: IntArray

    // =================================================================
    //  ПРОЧЕЕ
    // =================================================================

    private var bonesRigid = true
    private var paused = false

    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var viewport: FitViewport
    private lateinit var camera: OrthographicCamera
    private lateinit var hudCamera: OrthographicCamera
    private val tmp = Vector3()

    /** Переиспользуемый цвет для подсветки мышц: Color в цикле отрисовки не аллоцируем. */
    private val tmpColor = Color()

    private var dragId = -1
    private var hoverId = -1
    private var mouseX = 0f
    private var mouseY = 0f
    private var lastMouseX = 0f
    private var lastMouseY = 0f
    private var wasTouched = false

    // =================================================================
    //  1. ПОСТРОЕНИЕ ТЕЛА
    //
    //  Регулярная сетка, обрезанная по контуру. Узел, не попавший внутрь контура,
    //  вершиной не становится — отсюда рваный край и разная степень у вершин.
    //  Ни решателю, ни проекции это не мешает: они работают со списками, а не с сеткой.
    // =================================================================

    /** Номер вершины по узлу сетки, -1 если узел вне контура. */
    private val idOfNode = IntArray(COLS * ROWS) { -1 }

    private fun nodeIndex(col: Int, row: Int) = col * ROWS + row

    private fun insideOutline(col: Int, row: Int): Boolean {
        val dx = (col - (COLS - 1) * 0.5f) * STEP
        val dy = (row - (ROWS - 1) * 0.5f) * STEP
        val nx = dx / SHAPE_A
        val ny = dy / SHAPE_B
        // |nx|^4 + |ny|^4 <= 1
        val nx2 = nx * nx
        val ny2 = ny * ny
        return nx2 * nx2 + ny2 * ny2 <= 1f
    }

    private fun isBoneNode(col: Int, row: Int) =
        col in BONE_COL_FROM..BONE_COL_TO && row in BONE_ROW_FROM..BONE_ROW_TO

    /**
     * Какую из двух диагоналей брать в ячейке (col, row).
     *
     * Чередование по чётности: если всегда брать одну и ту же, у ткани появляется
     * выделенное направление — вдоль диагоналей она заметно жёстче, чем поперёк.
     */
    private fun diagonalIsMain(col: Int, row: Int) = (col + row) % 2 == 0

    /**
     * Есть ли вокруг узла хотя бы одна ЦЕЛАЯ ячейка 2x2.
     *
     * Целая ячейка — единственный источник диагоналей, то есть сдвиговой жёсткости.
     * Узел без неё висит на одних длинах, а длины формы не держат.
     */
    private fun hasFullCellAround(mask: BooleanArray, col: Int, row: Int): Boolean {
        for (c0 in col - 1..col) {
            for (r0 in row - 1..row) {
                if (c0 < 0 || r0 < 0 || c0 + 1 >= COLS || r0 + 1 >= ROWS) continue
                if (mask[nodeIndex(c0, r0)] && mask[nodeIndex(c0 + 1, r0)] &&
                    mask[nodeIndex(c0, r0 + 1)] && mask[nodeIndex(c0 + 1, r0 + 1)]
                ) return true
            }
        }
        return false
    }

    /**
     * Отсев вершин без сдвиговой поддержки.
     *
     * Обрезка регулярной сетки по гладкому контуру оставляет на торцах тонкие языки
     * шириной в одну-две вершины. Диагоналей там нет — целых ячеек не набирается, — и
     * такой язык под нагрузкой сворачивается сам в себя: это не неустойчивость решателя,
     * а отсутствие ограничения, которое удерживало бы угол.
     *
     * Цикл повторяется, потому что удаление узла может лишить поддержки соседа.
     */
    private fun pruneUnsupported(mask: BooleanArray) {
        var changed = true
        while (changed) {
            changed = false
            for (col in 0 until COLS) {
                for (row in 0 until ROWS) {
                    val idx = nodeIndex(col, row)
                    if (!mask[idx]) continue
                    if (!hasFullCellAround(mask, col, row)) {
                        mask[idx] = false
                        changed = true
                    }
                }
            }
        }
    }

    private fun buildBody() {
        // --- маска узлов: сначала контур, затем отсев неподдержанных ---
        val mask = BooleanArray(COLS * ROWS)
        for (col in 0 until COLS) {
            for (row in 0 until ROWS) mask[nodeIndex(col, row)] = insideOutline(col, row)
        }
        pruneUnsupported(mask)

        // --- вершины ---
        val tmpX = FloatArray(COLS * ROWS)
        val tmpY = FloatArray(COLS * ROWS)
        val tmpBone = BooleanArray(COLS * ROWS)
        val tmpPin = BooleanArray(COLS * ROWS)
        val tmpCluster = IntArray(COLS * ROWS)

        // Кластеры нарезаются прямоугольниками по сетке. В движке их роль сыграет разметка
        // из генома, здесь достаточно любого разбиения — решателю всё равно, откуда оно.
        val clusterCols = (COLS + MUSCLE_CLUSTER_W - 1) / MUSCLE_CLUSTER_W

        var n = 0
        for (col in 0 until COLS) {
            for (row in 0 until ROWS) {
                if (!mask[nodeIndex(col, row)]) continue
                idOfNode[nodeIndex(col, row)] = n
                tmpX[n] = CENTER_X + (col - (COLS - 1) * 0.5f) * STEP
                tmpY[n] = CENTER_Y + (row - (ROWS - 1) * 0.5f) * STEP
                tmpBone[n] = isBoneNode(col, row)
                tmpPin[n] = PIN_LEFT_EDGE && col == 0
                // Кость мышцей быть не может: её вершины двигает проекция, а не ограничения.
                tmpCluster[n] = if (tmpBone[n]) -1
                else (row / MUSCLE_CLUSTER_H) * clusterCols + (col / MUSCLE_CLUSTER_W)
                n++
            }
        }
        particleCount = n

        px = FloatArray(n); py = FloatArray(n)
        prevX = FloatArray(n); prevY = FloatArray(n)
        vx = FloatArray(n); vy = FloatArray(n)
        invMass = FloatArray(n)
        restX = FloatArray(n) { tmpX[it] }
        restY = FloatArray(n) { tmpY[it] }
        matchWeight = FloatArray(n)
        isBone = BooleanArray(n) { tmpBone[it] }
        isPinned = BooleanArray(n) { tmpPin[it] }
        inContact = BooleanArray(n)
        clusterOf = IntArray(n) { tmpCluster[it] }
        clusterCount = (clusterOf.max() + 1).coerceAtLeast(1)
        clusterActivation = FloatArray(clusterCount)
        clusterCx = FloatArray(clusterCount)
        clusterCy = FloatArray(clusterCount)
        clusterN = IntArray(clusterCount)

        // Оттенки разносятся золотым сечением: соседние номера кластеров получают
        // далёкие друг от друга цвета, поэтому соседние блоки всегда различимы.
        clusterColors = Array(clusterCount) { id ->
            Color().fromHsv((id * 0.6180339887f % 1f) * 360f, 0.5f, 0.95f).also { it.a = 0.5f }
        }

        boneMembers = (0 until n).filter { isBone[it] }.toIntArray()

        // --- ограничения ---
        //
        // На узел приходится максимум 4 новых связи (правый сосед, верхний и две диагонали
        // ячейки справа-сверху), поэтому этой ёмкости заведомо хватает.
        // На узел приходится максимум 6 новых связей: правый и верхний сосед, диагонали
        // и две связи изгиба (через один узел по обеим осям).
        val cap = COLS * ROWS * 6
        conA = IntArray(cap); conB = IntArray(cap); conRest = FloatArray(cap)
        conCluster = IntArray(cap)
        conIsDiagonal = BooleanArray(cap)
        conIsBending = BooleanArray(cap)
        conCompliance = FloatArray(cap)

        for (col in 0 until COLS) {
            for (row in 0 until ROWS) {
                val a = idOfNode[nodeIndex(col, row)]
                if (a == -1) continue

                // Структурные: правый и верхний сосед. Каждая связь добавляется один раз.
                if (col + 1 < COLS) addConstraint(a, idOfNode[nodeIndex(col + 1, row)])
                if (row + 1 < ROWS) addConstraint(a, idOfNode[nodeIndex(col, row + 1)])

                // Изгиб: через один узел по обеим осям. Ставится ПОСЛЕ структурных,
                // чтобы в порядке Гаусса-Зейделя жёсткие ограничения шли первыми.
                if (BENDING_ENABLED) {
                    if (col + 2 < COLS) addConstraint(a, idOfNode[nodeIndex(col + 2, row)], bending = true)
                    if (row + 2 < ROWS) addConstraint(a, idOfNode[nodeIndex(col, row + 2)], bending = true)
                }

                // Сдвиговые: обе диагонали ячейки, если ячейка целая.
                if (col + 1 < COLS && row + 1 < ROWS) {
                    val b = idOfNode[nodeIndex(col + 1, row)]
                    val c = idOfNode[nodeIndex(col, row + 1)]
                    val d = idOfNode[nodeIndex(col + 1, row + 1)]
                    if (b != -1 && c != -1 && d != -1) {
                        // Без диагонали квадрат складывается в ромб при полностью
                        // неизменных длинах сторон — длины сами по себе форму не держат.
                        if (SINGLE_DIAGONAL) {
                            // Одной достаточно: она режет ячейку на два треугольника,
                            // каждый жёсток своими тремя сторонами. Направление чередуем.
                            if (diagonalIsMain(col, row)) addConstraint(a, d, true)
                            else addConstraint(b, c, true)
                        } else {
                            addConstraint(a, d, true)
                            addConstraint(b, c, true)
                        }
                    }
                }
            }
        }

        // --- ячейки для заливки и треугольники площади ---
        val cellCap = (COLS - 1) * (ROWS - 1)
        cellV = IntArray(cellCap * 4)
        cellIsBone = BooleanArray(cellCap)
        cellCluster = IntArray(cellCap) { -1 }
        triA = IntArray(cellCap * 2); triB = IntArray(cellCap * 2); triC = IntArray(cellCap * 2)
        triRestArea2 = FloatArray(cellCap * 2)
        triCluster = IntArray(cellCap * 2)
        triInverted = BooleanArray(cellCap * 2)

        for (col in 0 until COLS - 1) {
            for (row in 0 until ROWS - 1) {
                val a = idOfNode[nodeIndex(col, row)]
                val b = idOfNode[nodeIndex(col + 1, row)]
                val c = idOfNode[nodeIndex(col + 1, row + 1)]
                val d = idOfNode[nodeIndex(col, row + 1)]
                if (a == -1 || b == -1 || c == -1 || d == -1) continue

                val base = cellCount * 4
                cellV[base] = a; cellV[base + 1] = b; cellV[base + 2] = c; cellV[base + 3] = d
                val boneCell = isBone[a] && isBone[b] && isBone[c] && isBone[d]
                cellIsBone[cellCount] = boneCell
                cellCluster[cellCount] = if (!boneCell && clusterOf[a] != -1 &&
                    clusterOf[a] == clusterOf[b] && clusterOf[a] == clusterOf[c] &&
                    clusterOf[a] == clusterOf[d]
                ) clusterOf[a] else -1
                cellCount++

                // Внутри кости площадь не нужна: проекция всё равно перезапишет результат.
                if (boneCell) continue

                // Разрез на треугольники обязан идти ПО ТОЙ ЖЕ диагонали, что и связь,
                // иначе площадь будет держать одну пару треугольников, а длины — другую,
                // и ячейка получит противоречивые ограничения.
                if (!SINGLE_DIAGONAL || diagonalIsMain(col, row)) {
                    addTriangle(a, b, c)      // диагональ a-c
                    addTriangle(a, c, d)
                } else {
                    addTriangle(a, b, d)      // диагональ b-d
                    addTriangle(b, c, d)
                }
            }
        }
    }

    /**
     * Порядок вершин разворачивается так, чтобы площадь покоя была ПОЛОЖИТЕЛЬНОЙ.
     *
     * Иначе знак зависел бы от порядка обхода сетки, и «вывернулся» стало бы неотличимо
     * от «так и было» — ровно та же причина, по которой это делает RCMSort.collectTriangles.
     */
    private fun addTriangle(i0: Int, i1: Int, i2: Int) {
        var a = i1
        var b = i2
        var area2 = signedArea2(i0, a, b)
        if (area2 < 0f) {
            val t = a; a = b; b = t
            area2 = -area2
        }
        if (area2 < 1e-9f) return          // вырожденный треугольник: направление не определено
        triA[triCount] = i0; triB[triCount] = a; triC[triCount] = b
        triRestArea2[triCount] = area2
        // Треугольник сокращается вместе с кластером, только если ВСЕ три вершины его.
        // На шве кластеров площадь остаётся исходной, иначе шов рвало бы.
        triCluster[triCount] = if (clusterOf[i0] != -1 &&
            clusterOf[i0] == clusterOf[a] && clusterOf[i0] == clusterOf[b]
        ) clusterOf[i0] else -1
        triCount++
    }

    private fun signedArea2(i0: Int, i1: Int, i2: Int): Float =
        (restX[i1] - restX[i0]) * (restY[i2] - restY[i0]) -
            (restY[i1] - restY[i0]) * (restX[i2] - restX[i0])

    /**
     * Связь добавляется, только если она НЕ целиком внутри кости.
     *
     * Внутри кости ограничения бессмысленны: проекция всё равно перезапишет их результат.
     * Поэтому кость не просто жёстче мягкой ткани — она ещё и дешевле, потому что вместо
     * ~4 связей на вершину у неё три прохода по списку.
     */
    private fun addConstraint(i: Int, j: Int, diagonal: Boolean = false, bending: Boolean = false) {
        if (i == -1 || j == -1) return
        if (isBone[i] && isBone[j]) return
        conIsDiagonal[constraintCount] = diagonal
        conIsBending[constraintCount] = bending
        conCompliance[constraintCount] = if (bending) BENDING_COMPLIANCE else SOFT_COMPLIANCE

        val dx = restX[i] - restX[j]
        val dy = restY[i] - restY[j]
        conA[constraintCount] = i
        conB[constraintCount] = j
        conRest[constraintCount] = sqrt(dx * dx + dy * dy)
        // Связь принадлежит мышце, только если ОБА конца в одном кластере. Связи на шве
        // между кластерами и связи, одним концом упирающиеся в кость, длину не меняют —
        // именно они и передают тягу мышцы на скелет.
        conCluster[constraintCount] =
            if (clusterOf[i] != -1 && clusterOf[i] == clusterOf[j]) clusterOf[i] else -1
        constraintCount++
    }

    private fun reset() {
        for (i in 0 until particleCount) {
            px[i] = restX[i]; py[i] = restY[i]
            vx[i] = 0f; vy[i] = 0f
            matchWeight[i] = 1f
            invMass[i] = if (isPinned[i]) 0f else 1f
        }
        clusterActivation.fill(0f)
        hoveredCluster = -1
        dragId = -1
        invertedPeak = 0
    }

    // =================================================================
    //  2. XPBD: ОГРАНИЧЕНИЕ РАССТОЯНИЯ
    //
    //      C       = |xi - xj| - rest
    //      alphaT  = compliance / h^2
    //      dLambda = -C / (wi + wj + alphaT)
    //      dx_i    = +grad * dLambda * wi,   dx_j = -grad * dLambda * wj
    //
    //  Лямбда не накапливается: на подшаг ровно одна итерация, поэтому суммарная
    //  лямбда и есть dLambda.
    // =================================================================

    private fun solveConstraints(h: Float) {
        val invH2 = 1f / (h * h)

        for (c in 0 until constraintCount) {
            // Податливость своя у каждой связи: у изгибных она заметно выше структурных.
            val alpha = conCompliance[c] * invH2
            val i = conA[c]
            val j = conB[c]
            val wi = invMass[i]
            val wj = invMass[j]
            val w = wi + wj
            if (w == 0f) continue

            var dx = px[i] - px[j]
            var dy = py[i] - py[j]
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-9f) continue
            dx /= len; dy /= len

            // Мышца меняет ДЛИНУ ПОКОЯ, а не силу — как degreeOfShortening в движке.
            val rest = conRest[c] * clusterScale(conCluster[c])
            val dLambda = -(len - rest) / (w + alpha)

            px[i] += dx * dLambda * wi; py[i] += dy * dLambda * wi
            px[j] -= dx * dLambda * wj; py[j] -= dy * dLambda * wj
        }
    }

    // =================================================================
    //  2б. XPBD: ОГРАНИЧЕНИЕ ЗНАКОВОЙ ПЛОЩАДИ (защита от выворачивания)
    //
    //      C    = area2 - restArea2,   area2 = (p1-p0) x (p2-p0)
    //      grad = производная area2 по каждой вершине:
    //             g0 = (y1-y2, x2-x1),  g1 = (y2-y0, x0-x2),  g2 = (y0-y1, x1-x0)
    //
    //  Площадь ЗНАКОВАЯ: у вывернутого треугольника она отрицательна, ошибка получается
    //  порядка удвоенной площади покоя, и вершины выдавливаются обратно. Ограничения
    //  расстояния этого не умеют в принципе — зеркальная конфигурация удовлетворяет их
    //  точно так же, как правильная.
    // =================================================================

    private fun solveAreas(h: Float) {
        val alpha = AREA_COMPLIANCE / (h * h)

        for (t in 0 until triCount) {
            val i0 = triA[t]; val i1 = triB[t]; val i2 = triC[t]

            val x0 = px[i0]; val y0 = py[i0]
            val x1 = px[i1]; val y1 = py[i1]
            val x2 = px[i2]; val y2 = py[i2]

            val g0x = y1 - y2; val g0y = x2 - x1
            val g1x = y2 - y0; val g1y = x0 - x2
            val g2x = y0 - y1; val g2y = x1 - x0

            val w0 = invMass[i0]; val w1 = invMass[i1]; val w2 = invMass[i2]
            val denom = w0 * (g0x * g0x + g0y * g0y) +
                w1 * (g1x * g1x + g1y * g1y) +
                w2 * (g2x * g2x + g2y * g2y)
            if (denom < 1e-12f) continue

            // Площадь покоя обязана ехать ВМЕСТЕ с длинами, иначе при сокращении мышцы
            // длины тянут треугольник вниз, а несжимаемая площадь держит его на месте —
            // два жёстких ограничения в прямом противоречии, и ткань запирает.
            // Масштаб квадратичный: линейное сжатие в s раз меняет площадь в s^2.
            val s = clusterScale(triCluster[t])
            val restArea2 = triRestArea2[t] * s * s
            val area2 = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
            val dLambda = -(area2 - restArea2) / (denom + alpha)

            px[i0] += w0 * dLambda * g0x; py[i0] += w0 * dLambda * g0y
            px[i1] += w1 * dLambda * g1x; py[i1] += w1 * dLambda * g1y
            px[i2] += w2 * dLambda * g2x; py[i2] += w2 * dLambda * g2y
        }
    }

    // =================================================================
    //  3. SHAPE MATCHING
    //
    //  Ищется жёсткое преобразование, минимизирующее sum_i w_i |R*q_i + c - p_i|^2.
    //  Разворачивая скалярное произведение для R = [[cos,-sin],[sin,cos]], получаем,
    //  что максимизировать надо cos*S + sin*T, где
    //      S = sum w (q . p),   T = sum w (q x p)
    //  а максимум лежит в направлении вектора (S, T). Весь поворот — два накопителя
    //  и один корень. Ни atan2, ни SVD.
    //
    //  Стоимость — ТРИ линейных прохода по вершинам кости, независимо от того, сколько
    //  вокруг мягкой ткани. Жёсткость точная и от числа подшагов не зависит.
    // =================================================================

    /**
     * Считает треугольники с ОТРИЦАТЕЛЬНОЙ знаковой площадью — то есть вывернутые.
     *
     * Один проход по 652 треугольникам за кадр, в физику не вмешивается. Пик держится
     * отдельно, потому что выворачивание часто длится доли секунды и глазом его не
     * поймать: важно знать, случалось ли оно ВООБЩЕ.
     */
    private fun countInverted() {
        var count = 0
        for (t in 0 until triCount) {
            val i0 = triA[t]; val i1 = triB[t]; val i2 = triC[t]
            val area2 = (px[i1] - px[i0]) * (py[i2] - py[i0]) -
                (py[i1] - py[i0]) * (px[i2] - px[i0])
            val inv = area2 < 0f
            triInverted[t] = inv
            if (inv) count++
        }
        invertedNow = count
        if (count > invertedPeak) invertedPeak = count
    }

    /**
     * Тяга мышью как ОГРАНИЧЕНИЕ, а не как телепорт.
     *
     * C = |p - mouse|, градиент — единичный вектор к курсору. Поправка считается как
     * обычно в XPBD, а затем зажимается MAX_DRAG_STEP — это и есть предел силы.
     *
     * Решается ПОСЛЕ структурных ограничений и площадей, но ДО проекции кости: тяга
     * должна уступать и жёсткости ткани, и жёсткости кости, иначе она снова начнёт
     * продавливать тело сквозь себя.
     */
    private fun solveDrag(h: Float) {
        val i = dragId
        if (i < 0) return
        val w = invMass[i]
        if (w == 0f) return

        val dx = mouseX - px[i]
        val dy = mouseY - py[i]
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 1e-9f) return

        val alpha = DRAG_COMPLIANCE / (h * h)
        var corr = dist * w / (w + alpha)
        if (corr > MAX_DRAG_STEP) corr = MAX_DRAG_STEP

        px[i] += dx / dist * corr
        py[i] += dy / dist * corr
    }

    private fun projectBone(ids: IntArray) {
        // проход 1: центроид текущий и центроид позы покоя, по одному подмножеству
        // и с одними весами — иначе кость поедет
        var cx = 0f; var cy = 0f
        var c0x = 0f; var c0y = 0f
        var wsum = 0f
        for (i in ids) {
            val w = matchWeight[i]
            cx += w * px[i];     cy += w * py[i]
            c0x += w * restX[i]; c0y += w * restY[i]
            wsum += w
        }
        cx /= wsum; cy /= wsum; c0x /= wsum; c0y /= wsum

        // проход 2: два скаляра, задающих поворот
        var s = 0f
        var t = 0f
        for (i in ids) {
            val w = matchWeight[i]
            val ppx = px[i] - cx;    val ppy = py[i] - cy
            val qx = restX[i] - c0x; val qy = restY[i] - c0y
            s += w * (qx * ppx + qy * ppy)   // ~ cos
            t += w * (qx * ppy - qy * ppx)   // ~ sin
        }

        val norm = sqrt(s * s + t * t)
        if (norm < 1e-9f) return
        val cos = s / norm
        val sin = t / norm

        // проход 3: снап на жёсткую цель, alpha = 1
        for (i in ids) {
            val qx = restX[i] - c0x
            val qy = restY[i] - c0y
            px[i] = cx + cos * qx - sin * qy
            py[i] = cy + sin * qx + cos * qy
        }
    }

    // =================================================================
    //  4. ЦИКЛ СИМУЛЯЦИИ
    //
    //  integrate -> ограничения -> ПРОЕКЦИЯ КОСТИ -> восстановление скоростей
    //
    //  Проекция последняя: всё, что запишет в позиции после неё, нарушит жёсткость.
    //  Поэтому пол обрабатывается в integrate.
    // =================================================================

    private fun simulate() {
        val h = DT / SUBSTEPS
        for (step in 0 until SUBSTEPS) {
            integrate(h)
            solveConstraints(h)
            solveAreas(h)
            solveDrag(h)
            if (bonesRigid) projectBone(boneMembers)
            updateVelocities(h)
            // Диссипация идёт по СКОРОСТЯМ и только после того, как они восстановлены
            // из позиций. Раньше её ставить нельзя: solve и projectBone перезапишут
            // позиции, и updateVelocities затрёт результат.
            applyViscosity(h)
            applyRestitution()
            applyMediumDrag(h)
        }
    }

    private fun integrate(h: Float) {
        for (i in 0 until particleCount) {
            prevX[i] = px[i]
            prevY[i] = py[i]
            inContact[i] = false

            // Перетаскиваемая вершина больше НЕ выделена: интегрируется как все, а к
            // курсору её подтягивает solveDrag — обычным ограничением с потолком силы.
            if (invMass[i] == 0f) continue

            vy[i] += GRAVITY * h
            px[i] += vx[i] * h
            py[i] += vy[i] * h

            if (py[i] < GROUND_Y) {
                // Трение о пол: касательное смещение откатывается на долю GROUND_FRICTION.
                // При 1 это прежний полный откат к prevX, то есть прилипание.
                px[i] += (prevX[i] - px[i]) * GROUND_FRICTION
                py[i] = GROUND_Y
                inContact[i] = true
            }
        }
    }

    /** Скорость восстанавливается из смещения — отсюда же и демпфирование, отдельный не нужен. */
    private fun updateVelocities(h: Float) {
        for (i in 0 until particleCount) {
            // dragId здесь больше не исключается: тяга теперь обычное ограничение, и
            // скорость от неё должна попасть в v естественным путём, через смещение.
            // Заодно бросок при отпускании получается сам собой, без ручной подстановки.
            if (invMass[i] == 0f) {
                vx[i] = 0f; vy[i] = 0f
                continue
            }
            vx[i] = (px[i] - prevX[i]) / h
            vy[i] = (py[i] - prevY[i]) / h
        }
    }

    /**
     * Внутренняя вязкость: скорости связанных вершин сближаются.
     *
     * Проход по тем же связям, что и у решателя, но по СКОРОСТЯМ, а не позициям, и
     * после восстановления скоростей — то есть в самом конце подшага.
     *
     * Гасится ТОЛЬКО ПРОДОЛЬНАЯ составляющая относительной скорости — вдоль связи.
     * Это не упрощение, а условие корректности:
     *
     *   - При жёстком ПЕРЕНОСЕ относительной скорости нет вообще.
     *   - При жёстком ВРАЩЕНИИ она есть, но строго перпендикулярна связи, поэтому
     *     продольная составляющая равна нулю. Гашение полного вектора затормозило бы
     *     вращение тела как целого — это ровно та ошибка, которой известна XSPH-вязкость.
     *
     * То есть настоящая вязкость обязана штрафовать скорость ДЕФОРМАЦИИ, а жёсткое
     * движение для неё невидимо. Сдвиг при этом всё равно гасится: у каждой ячейки
     * есть обе диагонали, и сдвиг растягивает одну из них — то есть виден как
     * продольная скорость на диагональной связи.
     *
     * СОХРАНЕНИЕ ИМПУЛЬСА. Поправки распределяются как wi/w и wj/w, а импульс это
     * m*dv = dv/w. Получается +k*dv/w у одного конца и -k*dv/w у другого, сумма ноль.
     */
    private fun applyViscosity(h: Float) {
        // Коэффициент нормируется на шаг, поэтому VISCOSITY задаётся в 1/сек и не
        // зависит от числа подшагов. Ограничение сверху обязательно: k > 1 означает
        // переброс относительной скорости через ноль, то есть раскачку.
        var k = VISCOSITY * h
        if (k > 0.5f) k = 0.5f

        for (c in 0 until constraintCount) {
            val i = conA[c]
            val j = conB[c]
            val wi = invMass[i]
            val wj = invMass[j]
            val w = wi + wj
            if (w == 0f) continue

            var nx = px[j] - px[i]
            var ny = py[j] - py[i]
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-9f) continue
            nx /= len; ny /= len

            // Продольная составляющая относительной скорости — она же скорость
            // изменения длины связи, то есть локальная скорость деформации.
            val dv = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny

            val si = k * wi / w
            val sj = k * wj / w

            vx[i] += dv * nx * si; vy[i] += dv * ny * si
            vx[j] -= dv * nx * sj; vy[j] -= dv * ny * sj
        }
    }

    /**
     * Гашение отскока от пола.
     *
     * Проход по скоростям после их восстановления из позиций. У частицы, стоявшей в этом
     * подшаге на полу, скорость ВВЕРХ — это не физика, а энергия, которую впрыснула
     * проекция: контакт зажал позицию, решатель вернул её обратно, а разность позиций
     * стала скоростью. Здесь она умножается на GROUND_RESTITUTION, то есть при нуле
     * убирается совсем.
     *
     * Упрощение: настоящая формула XPBD берёт нормальную скорость ДО решателя и задаёт
     * цель -e * vn_pre. Здесь просто масштабируется итоговая; для e = 0 результат тот же,
     * а промежуточные значения ведут себя чуть мягче.
     */
    private fun applyRestitution() {
        for (i in 0 until particleCount) {
            if (!inContact[i]) continue
            if (vy[i] > 0f) vy[i] *= GROUND_RESTITUTION
        }
    }

    /** Сопротивление среды. Нормировано на h, поэтому не зависит от числа подшагов. */
    private fun applyMediumDrag(h: Float) {
        var keep = 1f - MEDIUM_DRAG * h
        if (keep < 0f) keep = 0f
        for (i in 0 until particleCount) {
            vx[i] *= keep
            vy[i] *= keep
        }
    }

    // =================================================================
    //  5. ВВОД
    // =================================================================

    private fun handleInput() {
        tmp.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
        viewport.unproject(tmp)
        mouseX = tmp.x
        mouseY = tmp.y

        val touched = Gdx.input.isTouched
        if (touched && !wasTouched) beginDrag()
        if (!touched && wasTouched) endDrag()
        wasTouched = touched

        hoverId = if (dragId >= 0) dragId else nearestParticle()
        // Мышца выбирается ребром, а перетаскивание — вершиной, поэтому одно другому
        // не мешает: тянуть за вершину сокращённого кластера можно.
        hoveredCluster = clusterUnderMouse()

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset()
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) bonesRigid = !bonesRigid
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) viewMode = (viewMode + 1) % 3
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit()
    }

    /**
     * Во сколько раз сжат кластер: 1 в покое, MUSCLE_CONTRACTION при полном сокращении.
     * -1 (кость, шов между кластерами) — всегда 1.
     */
    private fun clusterScale(cluster: Int): Float =
        if (cluster < 0) 1f else 1f - clusterActivation[cluster] * (1f - MUSCLE_CONTRACTION)

    /**
     * Активации едут к цели экспоненциально, а не переключаются мгновенно.
     *
     * Скачок длины покоя на 50% за один кадр — это скачок ошибки ограничения, то есть
     * удар: решатель отработает его как столкновение и швырнёт ткань. Живая мышца тоже
     * сокращается не мгновенно, так что это заодно и правдоподобнее.
     */
    private fun updateMuscles(dt: Float) {
        var k = MUSCLE_RATE * dt
        if (k > 1f) k = 1f
        for (c in 0 until clusterCount) {
            val target = if (c == hoveredCluster) 1f else 0f
            clusterActivation[c] += (target - clusterActivation[c]) * k
        }
    }

    /**
     * Ближайшее к курсору ребро — точнее, кластер, которому оно принадлежит.
     *
     * Рёбер внутри кости не существует (addConstraint их не создаёт), поэтому кость
     * отсеивается сама: навести на неё мышцу невозможно. Швы между кластерами тоже
     * не выбираются — у них cluster = -1.
     */
    private fun clusterUnderMouse(): Int {
        var best = -1
        var bestD2 = EDGE_PICK_RADIUS * EDGE_PICK_RADIUS

        for (c in 0 until constraintCount) {
            val cluster = conCluster[c]
            if (cluster < 0) continue

            val i = conA[c]
            val j = conB[c]
            val ax = px[i]; val ay = py[i]
            val bx = px[j]; val by = py[j]

            // Расстояние от точки до отрезка: проекция, зажатая в [0, 1].
            val ex = bx - ax; val ey = by - ay
            val len2 = ex * ex + ey * ey
            var t = if (len2 < 1e-12f) 0f else ((mouseX - ax) * ex + (mouseY - ay) * ey) / len2
            if (t < 0f) t = 0f else if (t > 1f) t = 1f

            val dx = mouseX - (ax + ex * t)
            val dy = mouseY - (ay + ey * t)
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = cluster }
        }
        return best
    }

    private fun nearestParticle(): Int {
        var best = -1
        var bestD2 = PICK_RADIUS * PICK_RADIUS
        for (i in 0 until particleCount) {
            val dx = px[i] - mouseX
            val dy = py[i] - mouseY
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
        }
        return best
    }

    private fun beginDrag() {
        val i = nearestParticle()
        if (i < 0) return
        dragId = i
        // invMass НЕ обнуляется: вершина остаётся обычной, иначе тяга снова станет
        // бесконечно сильной и протащит тело сквозь себя.
        matchWeight[i] = DRAG_MATCH_WEIGHT     // проекцию кости тяга по-прежнему ведёт
        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun endDrag() {
        if (dragId < 0) return
        matchWeight[dragId] = 1f
        dragId = -1
    }

    // =================================================================
    //  6. ЖИЗНЕННЫЙ ЦИКЛ И ОТРИСОВКА
    // =================================================================

    override fun create() {
        // Сеток здесь на порядок больше треугольников, чем в демке с лентой,
        // поэтому буфер ShapeRenderer расширен — иначе он будет флашиться по многу раз за кадр.
        shapes = ShapeRenderer(40000)
        batch = SpriteBatch()
        font = BitmapFont()
        camera = OrthographicCamera()
        viewport = FitViewport(VIEW_WIDTH, VIEW_HEIGHT, camera)
        hudCamera = OrthographicCamera()

        buildBody()
        reset()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        camera.position.set(CENTER_X, 0.80f, 0f)
        camera.update()
        hudCamera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    override fun render() {
        handleInput()
        updateMuscles(DT)
        if (!paused) simulate()
        countInverted()

        ScreenUtils.clear(BG)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapes.projectionMatrix = camera.combined
        drawFilled()
        drawLines()
        drawHud()

        lastMouseX = mouseX
        lastMouseY = mouseY
    }

    private fun drawFilled() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        // Заливка по ячейкам: контур тела невыпуклый и рваный, одним веером его не режут.
        for (c in 0 until cellCount) {
            // В разборе физики мягкая часть заливается не ячейками, а треугольниками
            // ограничения площади — см. ниже. Здесь остаётся только кость: у неё
            // треугольников нет вообще, её держит проекция.
            if (showPhysics && !cellIsBone[c]) continue
            shapes.color = when {
                cellIsBone[c] -> if (bonesRigid) BONE_FILL else BONE_FILL_OFF
                else -> {
                    val cl = cellCluster[c]
                    val act = if (cl >= 0) clusterActivation[cl] else 0f
                    // Базовый цвет: обычно однотонный, в режиме разбора — цвет кластера.
                    // Ячейки со cl == -1 лежат на ШВЕ (их четыре угла в разных кластерах)
                    // и не сокращаются никогда — в режиме разбора они серые, поэтому
                    // границы блоков видно напрямую.
                    val base = if (!showClusters) SOFT_FILL
                    else if (cl >= 0) clusterColors[cl] else SEAM_FILL
                    if (act > 0.002f) tmpColor.set(base).lerp(MUSCLE_FILL, act) else base
                }
            }
            val base = c * 4
            val a = cellV[base]; val b = cellV[base + 1]
            val d = cellV[base + 2]; val e = cellV[base + 3]
            shapes.triangle(px[a], py[a], px[b], py[b], px[d], py[d])
            shapes.triangle(px[a], py[a], px[d], py[d], px[e], py[e])
        }

        // Разбор физики: КАЖДЫЙ треугольник ограничения площади заливается отдельно,
        // двумя чередующимися оттенками. Треугольники добавляются парами (два на ячейку),
        // поэтому чётность индекса — это половинка ячейки, и в глаза сразу бросается
        // ёлочка из чередующихся диагоналей.
        //
        // Между треугольниками намеренно оставлен зазор: они РАЗНЫЕ ограничения, каждое
        // со своей площадью покоя, и общего у соседей только вершины.
        if (showPhysics) {
            for (t in 0 until triCount) {
                // Вывернутый треугольник кричит красным: он и есть та самая болезнь,
                // которую лечит знаковая площадь. Если его нет — лечить надо другое.
                shapes.color = if (triInverted[t]) INVERTED_FILL
                else if (t % 2 == 0) TRI_FILL_A else TRI_FILL_B
                val i0 = triA[t]; val i1 = triB[t]; val i2 = triC[t]
                val cx = (px[i0] + px[i1] + px[i2]) / 3f
                val cy = (py[i0] + py[i1] + py[i2]) / 3f
                val k = 0.86f      // усадка к центру тяжести, чтобы был виден зазор
                shapes.triangle(
                    cx + (px[i0] - cx) * k, cy + (py[i0] - cy) * k,
                    cx + (px[i1] - cx) * k, cy + (py[i1] - cy) * k,
                    cx + (px[i2] - cx) * k, cy + (py[i2] - cy) * k
                )
            }
        }

        // Вершины. Мелкие, иначе на несколько сотен узлов это каша.
        for (i in 0 until particleCount) {
            val pinned = invMass[i] == 0f && i != dragId
            val active = i == dragId || i == hoverId
            if (!pinned && !active) {
                shapes.color = DOT
                shapes.circle(px[i], py[i], 0.008f, 8)
            } else {
                shapes.color = if (i == dragId || active) ACTIVE else PINNED_COLOR
                shapes.circle(px[i], py[i], if (active) 0.022f else 0.013f, 16)
            }
        }

        shapes.end()
    }

    private fun drawLines() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)

        shapes.color = GROUND_COLOR
        shapes.rectLine(-1f, GROUND_Y, 5f, GROUND_Y, 0.008f)

        // Связи мягкой ткани. Связей внутри кости не существует — там нечего рисовать,
        // и это видно: центр тела остаётся чистым.
        for (c in 0 until constraintCount) {
            // В разборе физики видно роль ребра: сторона ячейки держит растяжение,
            // диагональ — сдвиг. Ограничение у них при этом РОВНО ОДНО И ТО ЖЕ,
            // разделение чисто геометрическое.
            shapes.color = if (!showPhysics) LINK
            else if (conIsDiagonal[c]) EDGE_SHEAR else EDGE_SIDE
            val i = conA[c]
            val j = conB[c]
            shapes.rectLine(px[i], py[i], px[j], py[j], if (showPhysics) 0.004f else 0.003f)
        }

        // Контур кости — по её ячейкам, чтобы очертить жёсткую область.
        shapes.color = if (bonesRigid) BONE_EDGE else BONE_EDGE_OFF
        for (c in 0 until cellCount) {
            if (!cellIsBone[c]) continue
            val base = c * 4
            for (k in 0 until 4) {
                val i = cellV[base + k]
                val j = cellV[base + (k + 1) % 4]
                shapes.rectLine(px[i], py[i], px[j], py[j], 0.004f)
            }
        }

        shapes.end()
    }

    private fun drawHud() {
        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        var y = Gdx.graphics.height - 14f
        val line = 18f

        font.color = HUD_TEXT
        font.draw(batch, "MYAGKAYA TKAN VOKRUG KOSTI  -  2D XPBD + shape matching", 16f, y); y -= line * 1.4f

        font.color = HUD_MUTED
        font.draw(batch, "vertices = $particleCount   (bone = ${boneMembers.size})    distance = $constraintCount    area = $triCount", 16f, y); y -= line
        font.draw(batch, "substeps = $SUBSTEPS    soft compliance = $SOFT_COMPLIANCE", 16f, y); y -= line
        font.draw(batch, "viscosity = $VISCOSITY /s    medium drag = $MEDIUM_DRAG /s", 16f, y); y -= line
        font.draw(batch, "ground friction = $GROUND_FRICTION    restitution = $GROUND_RESTITUTION    single diagonal = $SINGLE_DIAGONAL", 16f, y); y -= line
        font.draw(batch, "drag force limit = $MAX_DRAG_STEP per substep  (constraint, not teleport)", 16f, y); y -= line
        font.color = if (invertedPeak > 0) INVERTED_FILL else HUD_MUTED
        font.draw(batch, "INVERTED triangles: now = $invertedNow   peak = $invertedPeak   (bending = $BENDING_ENABLED)", 16f, y); y -= line
        font.color = HUD_MUTED
        val note = if (bonesRigid) "  (projection, 3 passes over ${boneMembers.size} vertices)" else "  (no projection - bone is a hole in the mesh)"
        font.draw(batch, "bone rigid = $bonesRigid$note", 16f, y); y -= line * 1.4f
        val act = if (hoveredCluster >= 0) clusterActivation[hoveredCluster] else 0f
        font.draw(batch, "muscles: $clusterCount clusters   hovered = $hoveredCluster   activation = ${"%.2f".format(act)}   -> ${MUSCLE_CONTRACTION} of rest length", 16f, y); y -= line * 1.4f
        font.draw(batch, "HOVER an edge to contract its cluster    LMB drag any vertex", 16f, y); y -= line
        val modeName = when (viewMode) {
            1 -> "MUSCLE CLUSTERS"
            2 -> "PHYSICS: ${triCount} area triangles, sides vs diagonals"
            else -> "normal"
        }
        font.draw(batch, "SPACE pause    R reset    B toggle bone    C view = $modeName", 16f, y); y -= line

        if (paused) {
            font.color = BONE_EDGE
            font.draw(batch, "PAUSED", 16f, y)
        }

        if (showClusters) drawClusterLabels()
        batch.end()
    }

    /**
     * Номер кластера в его центре тяжести. Только для режима разбора.
     *
     * Центроид считается по ЖИВЫМ вершинам кластера каждый кадр, а не берётся из позы
     * покоя: сокращённый кластер уезжает, и метка должна ехать вместе с ним.
     *
     * Что здесь хорошо видно и стоит понимать про текущую нарезку:
     *  - кластеры это прямоугольники ИСХОДНОЙ сетки, обрезанные контуром тела, поэтому
     *    у края они получаются рваными и разного размера;
     *  - кость вырезает из кластеров дырки, и кластер вокруг неё может распасться на
     *    два несвязных куска, которые всё равно сокращаются вместе;
     *  - серые ячейки — швы: их четыре угла попали в разные кластеры, поэтому они не
     *    сокращаются никогда и работают сухожилиями между блоками.
     */
    private fun drawClusterLabels() {
        clusterCx.fill(0f); clusterCy.fill(0f); clusterN.fill(0)

        for (i in 0 until particleCount) {
            val cl = clusterOf[i]
            if (cl < 0) continue
            clusterCx[cl] += px[i]; clusterCy[cl] += py[i]; clusterN[cl]++
        }

        for (cl in 0 until clusterCount) {
            if (clusterN[cl] == 0) continue
            tmp.set(clusterCx[cl] / clusterN[cl], clusterCy[cl] / clusterN[cl], 0f)
            viewport.project(tmp)
            font.color = if (cl == hoveredCluster) HUD_TEXT else HUD_MUTED
            font.draw(batch, cl.toString(), tmp.x - 5f, tmp.y + 5f)
        }
    }

    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}

/** Запуск: зелёная стрелка слева от этой функции. */
fun main() {
    if (StartupHelper.startNewJvmIfRequired()) return
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Мягкая ткань вокруг кости — XPBD + shape matching")
        setWindowedMode(1000, 680)
        useVsync(true)
        setForegroundFPS(144)
    }
    Lwjgl3Application(SoftBodyWithBoneDemo(), config)
}
