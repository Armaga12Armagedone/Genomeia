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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * СТЕНД ФИЗИКИ ДЛЯ GENOMEIA: XPBD + shape matching на реальной топологии генома.
 *
 * Тело грузится из body-export.txt (выгружает BodyExport в модуле core при сохранении
 * генома в редакторе; разбирает и достраивает BodyFile рядом). Ничего синтетического:
 * связи, кости и мышцы такие, какие вырастил геном.
 *
 * =====================================================================================
 *  ЭТОТ ФАЙЛ — ОБРАЗЕЦ ДЛЯ ПЕРЕНОСА В ОСНОВНУЮ СИМУЛЯЦИЮ.
 *  Ниже всё, что нужно, чтобы повторить его в движке, не читая историю разработки.
 * =====================================================================================
 *
 * ЧТО ЗДЕСЬ РЕАЛИЗОВАНО
 * ---------------------
 *   1. Ограничение расстояния (XPBD)        - заменяет пружины Гука из processLink
 *   2. Ограничение знаковой площади (XPBD)  - прямой аналог processTriangles
 *   3. Shape matching для костей, alpha = 1 - новое, аналога в движке нет
 *   4. Мышцы через длину покоя              - аналог degreeOfShortening
 *   5. Анизотропное сопротивление среды     - обобщение клетки Fin на всю поверхность
 *   6. Продольная вязкость ткани            - новое
 *
 * ПОРЯДОК ВНУТРИ ПОДШАГА (см. simulate) - МЕНЯТЬ НЕЛЬЗЯ
 * -----------------------------------------------------
 *      integrate            позиции += v * h, гравитация, пол
 *      solveConstraints     расстояния
 *      solveAreas           знаковая площадь
 *      solveDrag            тяга мыши (в движке не нужна)
 *      projectBone          ПОСЛЕДНЯЯ из позиционных: всё после неё нарушит жёсткость
 *      updateVelocities     v = (x - prevX) / h
 *      applyViscosity       \
 *      applyNormalDrag       |  по СКОРОСТЯМ и только после их восстановления
 *      applyRestitution      |
 *      applyMediumDrag      /
 *
 * Почему проекция кости последняя: она ставит вершины на жёсткую цель ровно, и любая
 * позиционная поправка после неё эту жёсткость ломает буквально. Поэтому и пол
 * обрабатывается в integrate, а не после решателя.
 *
 * Почему диссипация после восстановления скоростей: до него скоростей ещё нет,
 * updateVelocities перезапишет их из позиций и затрёт любую правку.
 *
 * СООТВЕТСТВИЕ СУЩНОСТЯМ ДВИЖКА
 * -----------------------------
 *      px, py, vx, vy           -> ParticleEntity.x/y/vx/vy
 *      invMass                  -> в движке неявная единица; при переносе завести массив
 *      conA/conB/conRest        -> LinkEntity + linksNaturalLength
 *      body.triA/B/C, restArea2 -> BakedLayout.triangleSlots / triangleRestArea2
 *      boneOf, rigidBones       -> НОВОЕ: запечь в BakedLayout смещения позы покоя r0
 *                                  и принадлежность слота кости
 *      muscleOf, muscleScale    -> cellEntity.degreeOfShortening
 *      boundA/boundB            -> cellEntity.isOnEdge (уже есть)
 *      h = DT / SUBSTEPS        -> SIM_STEP / SUBSTEPS
 *
 * ЧТО ОБЯЗАТЕЛЬНО ЗАЛОЖИТЬ ПРИ ПЕРЕНОСЕ (проверено замерами, детали у констант)
 * ----------------------------------------------------------------------------
 *  - SOFT_COMPLIANCE НИКОГДА не ноль. Ноль усиливает шум float32 в 1/h раз и
 *    раскручивает организм при сокращении мышцы: момент -896 против +12. Это общее
 *    свойство XPBD на float, а не особенность стенда.
 *  - Площадь покоя треугольника ОБЯЗАНА масштабироваться вместе с длинами при
 *    сокращении мышцы, квадратично (s^2). Сейчас в движке processLink меняет
 *    linksNaturalLength, а restArea2 остаётся - с жёстким ограничением площади
 *    это запрёт ткань намертво.
 *  - Число подшагов считается, а не подбирается:
 *        SUBSTEPS >= v_max * dt / (0.5 * характерное расстояние между клетками)
 *    Нарушение даёт необратимое смятие тела при ударе.
 *  - Кость из одной или двух клеток жёстким телом быть не может: у точки нет ориентации.
 *    В тестовом теле таких 96 из 102 кластеров - это вопрос к геному, а не к физике.
 *  - Кость и мышца - СВЯЗНЫЕ КОМПОНЕНТЫ одноимённых клеток, а не прямоугольники.
 *    Два отдельных костных отростка обязаны быть двумя жёсткими телами, иначе одна
 *    проекция свяжет их намертво и организм не согнётся в этом месте.
 *  - Внутри кости связи и треугольники НЕ создаются: проекция всё равно перезапишет
 *    результат. Кость выходит дешевле мягкой ткани, а не дороже.
 *  - Изотропное сопротивление среды тяги не создаёт НИКОГДА, сколько его ни поднимай.
 *    Плавание даёт только анизотропия: гасить нормальную составляющую и не трогать
 *    тангенциальную.
 *
 * ЧЕГО ЗДЕСЬ НЕТ (и что в движке уже есть или ещё понадобится)
 * -----------------------------------------------------------
 *  - самоконтакт: тело свободно проходит сквозь себя. В движке это закрывает repulse.
 *  - разрыв связей: аналога linkMaxLength2 нет.
 *  - многопоточность: один организм, один поток. В движке фаза связей уже разложена
 *    по аренам, и подшаги укладываются ВНУТРЬ рабочего элемента - лишних барьеров нет.
 *  - ограничение изгиба: реализовано в SoftBodyWithBoneDemo, выключено, польза не
 *    подтверждена (на ударе делает хуже).
 *  - double: устраняет паразитный дрейф полностью (во float он ~1e-3 импульса), но
 *    удваивает горячие массивы; для движка не рекомендовано.
 *
 * ЧЕМ ОТЛИЧАЕТСЯ ОТ SoftBodyWithBoneDemo
 * --------------------------------------
 * Там регулярная сетка: у каждой ячейки одна диагональ и два треугольника, всё выводится
 * из номера строки и столбца. Здесь топология гексагональная и неровная - степень вершины
 * от 2 до 8, длины связей отличаются в 6.8 раза, - и всё выводится из графа. Тот файл
 * проще для чтения, этот ближе к движку.
 *
 * УПРАВЛЕНИЕ
 * ----------
 * ЛКМ - тянуть вершину, наведение на ребро мышцы - сокращение её кластера,
 * 1..9 - держать мышцу сокращённой, 0 - все сразу, G - автоматический гребок,
 * SPACE - пауза, R - сброс, B - жёсткость костей, C - режим отрисовки.
 */
class RealBodyDemo(private val bodyPath: String) : ApplicationAdapter() {

    companion object {
        private const val DT = 1f / 144f

        /**
         * Подшагов на кадр.
         *
         * Критерий тот же, что выведен на падении синтетического тела: за подшаг вершина
         * не должна проскакивать дальше половины характерного расстояния между клетками.
         * Здесь опорой служит СРЕДНЯЯ длина связи (0.0285), а не минимальная: по минимуму
         * (0.0057) вышло бы впятеро больше подшагов ради нескольких коротких рёбер.
         */
        private const val SUBSTEPS = 4

        /**
         * НЕ СТАВИТЬ В НОЛЬ. Ноль раскручивает организм при сокращении мышцы.
         *
         * Сокращение мышцы — воздействие ВНУТРЕННЕЕ, и в точной арифметике оно сохраняет
         * и импульс, и момент: поправка ограничения расстояния идёт вдоль связи, значит
         * плечо у неё нулевое, а поправки площади и проекции кости в сумме дают ноль.
         * Проверено по стадиям — все три действительно сохраняющие.
         *
         * Течёт float. В равновесии невязка `len - rest` это чистое округление float32,
         * порядка 1e-7. При alpha = 0 поправка равна `-(len - rest) / w`, и дальше
         * updateVelocities умножает её на 1/h = 1152. То есть шум округления попадает
         * в скорости с усилением в 1152 раза и копится в настоящий момент импульса.
         *
         * Ненулевая податливость его отфильтровывает: alpha = compliance/h^2 = 1e-4 * 1152^2
         * это 132 против w = 2 в знаменателе, то есть та же невязка даёт поправку в 66 раз
         * меньше. Замер на этом теле, гравитация 0, без вязкости и сопротивления среды,
         * мышца 0 сокращена, 600 кадров:
         *
         *     compliance = 0       ->  момент  -896   (омега около -10 рад/с, видимая раскрутка)
         *     compliance = 1.0e-4  ->  момент   +12   (омега около 0.14 рад/с)
         *
         * Разница в 75 раз. То же самое относится и к движку: compliance = 0 в XPBD на
         * float32 при большом числе подшагов — это усилитель собственного шума, а не
         * «максимальная жёсткость даром».
         */
        private const val SOFT_COMPLIANCE = 1.0e-4f
        private const val AREA_COMPLIANCE = 0f

        private const val GRAVITY = 0f//-9.81f
        private const val GROUND_Y = 0f
        private const val GROUND_FRICTION = 0.8f
        private const val GROUND_RESTITUTION = 0f
        /**
         * Изотропное сопротивление среды, 1/сек. Гасит скорость одинаково во всех
         * направлениях, поэтому ТЯГИ НЕ СОЗДАЁТ НИКОГДА: мышца сжалась — затормозило,
         * разжалась — затормозило ровно так же, фазы симметричны, сумма около нуля.
         * Поднимать его ради «плотности среды» бессмысленно — движение умрёт, а плавания
         * не появится. Здесь оно остаётся только общим успокоителем.
         */
        private const val MEDIUM_DRAG = 0.5f

        /**
         * АНИЗОТРОПНОЕ сопротивление на граничных рёбрах, 1/(сек * единица длины).
         *
         * Гасится только НОРМАЛЬНАЯ к ребру составляющая скорости, тангенциальная не
         * трогается. Отсюда и берётся тяга: плавник, идущий плашмя, гребёт среду, он же
         * ребром — почти нет. Эта асимметрия и превращает периодическое сокращение мышцы
         * в направленное движение.
         *
         * Только граница: внутренние рёбра средой не омываются. Граничным считается ребро,
         * входящее ровно в ОДИН треугольник (внутренние входят в два) — в движке тот же
         * признак уже есть готовым, это cellEntity.isOnEdge.
         *
         * Модель линейная по скорости, а не квадратичная: для малых существ это ближе к
         * правде (низкое число Рейнольдса) и заметно устойчивее.
         *
         * ВЕЛИЧИНА. Эффективное затухание равно NORMAL_DRAG * средняя_длина_ребра, то есть
         * коэффициент привязан к масштабу тела и при другом размере клеток его надо
         * пересчитывать. Замер быстрого рывка за кость — отношение «сдвиг центра масс к
         * сдвигу самой вершины»:
         *
         *     250  ->  7.1 1/с,  ratio 0.78  тело отстаёт на 22%, РАСТЯГИВАЕТСЯ
         *      60  ->  1.7 1/с,  ratio 1.06  следует за костью
         *       0  ->    0,      ratio 1.63  проскакивает по инерции
         *
         * Сначала здесь стояло 250 — я подбирал его так, чтобы плавание было заметно на
         * замере, и не проверил, каково тело на ощупь. Это в 14 раз плотнее изотропного
         * сопротивления, отсюда и ощущение вязкой субстанции при перетаскивании.
         */
        private const val NORMAL_DRAG = 60f

        /**
         * КВАДРАТИЧНАЯ часть сопротивления: сила растёт как v*|v|, а не как v.
         *
         * Два механизма тяги здесь РАЗНЫЕ и складываются.
         *
         * Линейная работает по Пёрселлу — за счёт СМЕНЫ ОРИЕНТАЦИИ поверхности за цикл.
         * Квадратичная добавляет инерционный: быстрый взмах даёт непропорционально
         * больше медленного, как у рыб и медуз.
         *
         * Замер, путь центра масс за 10 секунд, цикл с рабочей фазой в треть периода:
         *
         *     линейное  + симметричная мышца  ->  0.070
         *     линейное  + асимметричная       ->  0.217   (3.1x)
         *     квадратичное + симметричная     ->  0.130   (1.8x)
         *     квадратичное + асимметричная    ->  0.310   (4.4x)
         *
         * Сначала я написал здесь, что на линейном сопротивлении асимметрия по времени
         * не даёт ничего: импульс за замкнутый цикл равен -k * (перемещение поверхности),
         * то есть от скорости не зависит. Замер это опроверг — строка B даёт 3.1x.
         * Рассуждение верно только для ЖЁСТКОЙ неповорачивающейся пластины; у мягкого
         * тела ориентация ребра меняется по ходу гребка, интеграл не сворачивается,
         * и профиль скорости начинает влиять даже на линейной модели.
         *
         * ЧТО ИЗ ДВУХ ЧЛЕНОВ ГЛАВНЫЙ. Квадратичный перевешивает линейный при
         * |vn| > NORMAL_DRAG / NORMAL_DRAG_QUADRATIC = 60/900 = 0.067. Замер нормальной
         * скорости на граничных рёбрах во время гребка: средняя 0.24, пиковая до 1.9 —
         * то есть в 4 и в 28 раз выше порога. На практике режим квадратичный почти всегда,
         * линейный член работает только на затухании между гребками.
         */
        private const val NORMAL_DRAG_QUADRATIC = 900f

        private const val VISCOSITY = 200f

        /**
         * Гасить паразитный дрейф от внутренних решателей.
         *
         * Ограничения расстояния, площади и проекция кости — воздействия ВНУТРЕННИЕ, и
         * центр масс сдвигать не имеют права вообще. Любое его смещение от них — это
         * накопленное округление float32: `px[i] += d` и `px[j] -= d` округляются
         * по-разному, потому что сами позиции разной величины. Замер этого показал:
         * в double дрейф ровно ноль, во float порядка 1e-3 импульса.
         *
         * Здесь он вычитается напрямую: центр масс запоминается до внутренних стадий и
         * восстанавливается после. Это O(n) и убирает ошибку точно.
         *
         * РАБОТАЕТ ТОЛЬКО КОГДА ТЕЛО НЕ ДЕРЖАТ МЫШЬЮ — см. simulate().
         *
         * При активной тяге зачёт даёт побочный эффект: тело остаётся растянутым всё
         * время, пока держишь, и распрямляется, стоило отпустить. Механизм я объяснить
         * не смог — две гипотезы (нарушение сохранения импульса в projectBone из-за
         * matchWeight = 200; вклад гидродинамики) проверены замером и обе неверны:
         * сдвиг центра масс от проекции равен 1e-6 при любом весе, а гидродинамика
         * зависит от скорости и при неподвижной мыши обращается в ноль.
         *
         * Поэтому поправка ограничена областью, где она заведомо законна: тяга — сила
         * внешняя, при ней «центр масс не должен двигаться» просто неверно как посылка.
         * Без тяги посылка верна, и там зачёт работает. Дрейф от мышц как раз копится
         * в свободном плавании, то есть ровно тогда, когда поправка включена.
         */
        private const val CANCEL_INTERNAL_DRIFT = true

        /** Тяга — ограничение с потолком, а не телепорт (см. SoftBodyWithBoneDemo). */
        private const val MAX_DRAG_STEP = 0.008f
        private const val DRAG_COMPLIANCE = 1e-6f

        private const val MUSCLE_CONTRACTION = 0.4f
        /**
         * Скорость сокращения и РАСПРЯМЛЕНИЯ, 1/сек — намеренно разные.
         *
         * Резкий гребок и медленный возврат: рабочая фаза идёт быстро и на квадратичном
         * сопротивлении даёт большой импульс, возвратная — медленно и почти не гребёт.
         * Это и есть асимметрия по времени, которой пользуются рыбы и медузы.
         *
         * Работает и БЕЗ квадратичной части — замер дал 3.1x на чисто линейной модели
         * (см. таблицу у NORMAL_DRAG_QUADRATIC). Вместе они складываются до 4.4x.
         *
         * СКОЛЬКО ИМЕННО. Развёртка по отношению скоростей, период гребка 48 кадров,
         * путь центра масс за 10 секунд:
         *
         *      1x (симметрично)  ->  0.221
         *      3x                ->  0.418   максимум
         *      5x                ->  0.391   (стоит сейчас)
         *     10x                ->  0.099
         *     20x                ->  0.118
         *
         * Выше 5x тяга ОБВАЛИВАЕТСЯ, и это не случайность: при rate = 2.5 постоянная
         * времени распрямления равна 0.4 с, а весь цикл длится 0.33 с. Мышца просто
         * не успевает разжаться до следующего гребка, остаётся полусокращённой, и
         * амплитуда взмаха схлопывается. То есть предел задаёт не физика среды, а
         * соотношение с ПЕРИОДОМ: распрямление обязано укладываться в возвратную фазу.
         * Поменяете период гребка — оптимум сместится.
         */
        /**
         * Период автоматического гребка В КАДРАХ, рабочая фаза — треть периода.
         *
         * Таблицы у NORMAL_DRAG_QUADRATIC и MUSCLE_RATE_RELAX мерились при периоде 48
         * и DT = 1/144. Меняете период — оптимум асимметрии сместится: распрямление
         * обязано укладываться в возвратную фазу, иначе амплитуда взмаха схлопывается.
         */
        private const val GAIT_PERIOD = 48 * 10

        private const val MUSCLE_RATE_CONTRACT = 25f
        private const val MUSCLE_RATE_RELAX = 1f

        /** Радиусы захвата масштабируются от средней связи: тело мельче синтетического. */
        private const val PICK_FACTOR = 1.2f
        private const val EDGE_PICK_FACTOR = 0.8f

        private const val VIEW_WIDTH = 3.4f
        private const val VIEW_HEIGHT = 2.125f

        private val BG            = Color.valueOf("12161E")
        private val GROUND_COLOR  = Color.valueOf("2B3341")
        private val SOFT_FILL     = Color.valueOf("5A96DC44")
        private val LINK          = Color.valueOf("96B4DC2A")
        private val BONE_FILL     = Color.valueOf("E8A33DAA")
        private val BONE_FILL_OFF = Color.valueOf("E8A33D25")
        private val MUSCLE_FILL   = Color.valueOf("D65A6BCC")
        private val MUSCLE_IDLE   = Color.valueOf("9E5560AA")
        private val INVERTED_FILL = Color.valueOf("FF3B30EE")
        private val DOT           = Color.valueOf("DFE6F055")
        private val ACTIVE        = Color.valueOf("FFFFFFFF")
        private val HUD_TEXT      = Color.valueOf("EAF0F8FF")
        private val HUD_MUTED     = Color.valueOf("76818FFF")
        private val HUD_WARN      = Color.valueOf("E8A33DFF")
        private val BOUNDARY_COLOR = Color.valueOf("4BE08AFF")
    }

    private lateinit var body: BodyFile

    // --- частицы (SoA) ---
    private var n = 0
    private lateinit var px: FloatArray
    private lateinit var py: FloatArray
    private lateinit var prevX: FloatArray
    private lateinit var prevY: FloatArray
    private lateinit var vx: FloatArray
    private lateinit var vy: FloatArray
    private lateinit var invMass: FloatArray
    private lateinit var matchWeight: FloatArray
    private lateinit var inContact: BooleanArray

    // --- связи ---
    private var conCount = 0
    private lateinit var conA: IntArray
    private lateinit var conB: IntArray
    private lateinit var conRest: FloatArray
    private lateinit var conMuscle: IntArray

    // --- граничные рёбра (омываемая поверхность) ---
    private var boundCount = 0
    private lateinit var boundA: IntArray
    private lateinit var boundB: IntArray

    // --- треугольники ---
    private lateinit var triMuscle: IntArray
    private lateinit var triInverted: BooleanArray
    private var invertedNow = 0
    private var invertedPeak = 0

    // --- кости ---
    /**
     * Только кластеры, которые могут быть жёсткими. Кластер из одной клетки отбрасывается:
     * поворот по единственной точке не определён, проекция стала бы тождеством, а строка
     * в списке создавала бы ложное впечатление, что кость есть.
     */
    private lateinit var rigidBones: Array<IntArray>
    private var degenerateBones = 0

    /** Номер кости у вершины, -1 если не в жёсткой кости. */
    private lateinit var boneOf: IntArray

    // --- мышцы ---
    private lateinit var muscleOf: IntArray
    private lateinit var muscleActivation: FloatArray
    private var hoveredMuscle = -1

    /**
     * Куда каждая мышца едет в этом кадре: 1 — сокращаться, 0 — распрямляться.
     *
     * Отдельно от muscleActivation, потому что источников команды теперь три —
     * наведение мышью, цифровые клавиши и автоматический гребок, — и они должны
     * складываться, а не перебивать друг друга.
     */
    private lateinit var muscleTarget: FloatArray

    /** Автоматический ритмичный гребок (клавиша G). */
    private var gait = false
    private var gaitFrame = 0

    private var bonesRigid = true
    private var paused = false
    private var viewMode = 0

    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var viewport: FitViewport
    private lateinit var camera: OrthographicCamera
    private lateinit var hudCamera: OrthographicCamera
    private val tmp = Vector3()
    private val tmpColor = Color()

    private var dragId = -1
    private var hoverId = -1
    private var mouseX = 0f
    private var mouseY = 0f
    private var wasTouched = false

    private var pickRadius = 0.05f
    private var edgePickRadius = 0.03f

    // =================================================================
    //  ПОСТРОЕНИЕ ИЗ ФАЙЛА
    // =================================================================

    private fun buildFromFile() {
        body = BodyFile.load(bodyPath)
        n = body.count

        px = FloatArray(n); py = FloatArray(n)
        prevX = FloatArray(n); prevY = FloatArray(n)
        vx = FloatArray(n); vy = FloatArray(n)
        invMass = FloatArray(n); matchWeight = FloatArray(n)
        inContact = BooleanArray(n)

        boneOf = IntArray(n) { -1 }
        muscleOf = IntArray(n) { -1 }

        // Жёсткими считаются кластеры от трёх клеток: две дают лишь жёсткий стержень,
        // то есть ровно то же, что обычная связь, и заводить ради них проекцию незачем.
        val rigid = body.boneClusters.filter { it.size >= 3 }
        degenerateBones = body.boneClusters.size - rigid.size
        rigidBones = rigid.toTypedArray()
        rigidBones.forEachIndexed { b, ids -> for (i in ids) boneOf[i] = b }

        body.muscleClusters.forEachIndexed { m, ids -> for (i in ids) muscleOf[i] = m }
        muscleActivation = FloatArray(body.muscleClusters.size)
        muscleTarget = FloatArray(body.muscleClusters.size)

        // --- связи: внутрикостные не создаются, их держит проекция ---
        val a = ArrayList<Int>(body.linkCount)
        val b = ArrayList<Int>(body.linkCount)
        val rest = ArrayList<Float>(body.linkCount)
        val mus = ArrayList<Int>(body.linkCount)
        for (k in 0 until body.linkCount) {
            val i = body.linkA[k]
            val j = body.linkB[k]
            if (boneOf[i] != -1 && boneOf[i] == boneOf[j]) continue
            val dx = body.x[i] - body.x[j]
            val dy = body.y[i] - body.y[j]
            a.add(i); b.add(j); rest.add(sqrt(dx * dx + dy * dy))
            // Мышца — только если ОБА конца в одном кластере. Связь от мышцы к обычной
            // ткани или к кости длину не меняет: она и передаёт тягу наружу.
            mus.add(if (muscleOf[i] != -1 && muscleOf[i] == muscleOf[j]) muscleOf[i] else -1)
        }
        conCount = a.size
        conA = a.toIntArray(); conB = b.toIntArray()
        conRest = rest.toFloatArray(); conMuscle = mus.toIntArray()

        triMuscle = IntArray(body.triCount) { t ->
            val i0 = body.triA[t]; val i1 = body.triB[t]; val i2 = body.triC[t]
            if (muscleOf[i0] != -1 && muscleOf[i0] == muscleOf[i1] && muscleOf[i0] == muscleOf[i2])
                muscleOf[i0] else -1
        }
        triInverted = BooleanArray(body.triCount)

        buildBoundary()

        pickRadius = body.meanLinkLength * PICK_FACTOR
        edgePickRadius = body.meanLinkLength * EDGE_PICK_FACTOR

        println("[RealBodyDemo] " + body.describe())
        println("[RealBodyDemo] rigid bones = ${rigidBones.size}, degenerate clusters dropped = $degenerateBones")
    }

    /**
     * Граница тела — рёбра, входящие ровно в ОДИН треугольник.
     *
     * Внутреннее ребро делят два треугольника, граничное — только один. Это работает на
     * любой топологии, включая рваные края и отростки, и не требует ни обхода контура,
     * ни знания порядка вершин.
     *
     * Считается по ВСЕМ треугольникам, а не по списку связей решателя: внутрикостных
     * связей мы не создаём, но поверхность кости средой омывается точно так же.
     */
    private fun buildBoundary() {
        val useCount = HashMap<Long, Int>(body.triCount * 3)
        fun key(a: Int, b: Int): Long {
            val lo = minOf(a, b).toLong()
            val hi = maxOf(a, b).toLong()
            return (lo shl 32) or hi
        }
        for (t in 0 until body.triCount) {
            val i0 = body.triA[t]; val i1 = body.triB[t]; val i2 = body.triC[t]
            useCount.merge(key(i0, i1), 1, Int::plus)
            useCount.merge(key(i1, i2), 1, Int::plus)
            useCount.merge(key(i2, i0), 1, Int::plus)
        }
        // Обход идёт по ВСЕМ связям, а не по ключам карты треугольников. Связь в тонком
        // отростке (шириной в одну клетку) не входит НИ В ОДИН треугольник, в карту не
        // попадает вовсе — и, считая только по карте, я терял такие рёбра целиком.
        // А это как раз хвост и плавники, то есть основная гребущая поверхность.
        val a = ArrayList<Int>(); val b = ArrayList<Int>()
        for (k in 0 until body.linkCount) {
            val i = body.linkA[k]; val j = body.linkB[k]
            if ((useCount[key(i, j)] ?: 0) <= 1) { a.add(i); b.add(j) }
        }
        boundCount = a.size
        boundA = a.toIntArray(); boundB = b.toIntArray()
        println("[RealBodyDemo] boundary edges = $boundCount")
    }

    private fun reset() {
        for (i in 0 until n) {
            px[i] = body.x[i]; py[i] = body.y[i]
            vx[i] = 0f; vy[i] = 0f
            invMass[i] = 1f; matchWeight[i] = 1f
        }
        muscleActivation.fill(0f)
        muscleTarget.fill(0f)
        gaitFrame = 0
        hoveredMuscle = -1
        hoverId = -1
        dragId = -1
        invertedPeak = 0
    }

    // =================================================================
    //  РЕШАТЕЛЬ  (тот же, что в SoftBodyWithBoneDemo)
    // =================================================================

    /**
     * Во сколько раз сжат кластер мышцы: 1 в покое, MUSCLE_CONTRACTION при полном
     * сокращении. -1 (не мышца, шов между кластерами) — всегда 1.
     *
     * Именно ЭТО и есть вся модель мышцы: меняется длина покоя ограничения, а не сила.
     * Жёсткость остаётся прежней, поэтому мышца тянет ровно с той силой, которая нужна,
     * чтобы дотащить ткань до новой длины, и упирается, если ей мешают. Прямой аналог
     * degreeOfShortening в движке.
     */
    private fun muscleScale(m: Int) =
        if (m < 0) 1f else 1f - muscleActivation[m] * (1f - MUSCLE_CONTRACTION)

    private fun solveConstraints(h: Float) {
        val alpha = SOFT_COMPLIANCE / (h * h)
        for (c in 0 until conCount) {
            val i = conA[c]; val j = conB[c]
            val wi = invMass[i]; val wj = invMass[j]
            val w = wi + wj
            if (w == 0f) continue
            var dx = px[i] - px[j]
            var dy = py[i] - py[j]
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-9f) continue
            dx /= len; dy /= len
            val rest = conRest[c] * muscleScale(conMuscle[c])
            val dL = -(len - rest) / (w + alpha)
            px[i] += dx * dL * wi; py[i] += dy * dL * wi
            px[j] -= dx * dL * wj; py[j] -= dy * dL * wj
        }
    }

    private fun solveAreas(h: Float) {
        val alpha = AREA_COMPLIANCE / (h * h)
        for (t in 0 until body.triCount) {
            val i0 = body.triA[t]; val i1 = body.triB[t]; val i2 = body.triC[t]
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

            // Площадь покоя едет вместе с длинами: иначе мышца тянет треугольник вниз,
            // а несжимаемая площадь держит его на месте, и ткань запирает.
            val s = muscleScale(triMuscle[t])
            val restArea2 = body.triRestArea2[t] * s * s
            val area2 = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
            val dL = -(area2 - restArea2) / (denom + alpha)

            px[i0] += w0 * dL * g0x; py[i0] += w0 * dL * g0y
            px[i1] += w1 * dL * g1x; py[i1] += w1 * dL * g1y
            px[i2] += w2 * dL * g2x; py[i2] += w2 * dL * g2y
        }
    }

    private fun solveDrag(h: Float) {
        val i = dragId
        if (i < 0 || invMass[i] == 0f) return
        val dx = mouseX - px[i]
        val dy = mouseY - py[i]
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1e-9f) return
        val alpha = DRAG_COMPLIANCE / (h * h)
        var corr = d * invMass[i] / (invMass[i] + alpha)
        if (corr > MAX_DRAG_STEP) corr = MAX_DRAG_STEP
        val ax = dx / d * corr
        val ay = dy / d * corr
        px[i] += ax
        py[i] += ay
        // Тяга мыши — сила ВНЕШНЯЯ, центр масс двигать имеет право. Её вклад
        // запоминается, чтобы гашение дрейфа его не съело вместе с ошибкой.
        dragShiftX += ax
        dragShiftY += ay
    }

    private var dragShiftX = 0f
    private var dragShiftY = 0f

    /**
     * Убирает смещение центра масс, накопленное ВНУТРЕННИМИ стадиями подшага.
     *
     * [beforeX]/[beforeY] — сумма координат до них. Внутренние ограничения обязаны
     * сохранять центр масс точно, поэтому вся разница (за вычетом честного вклада
     * мыши) — это округление float32, и её можно вычесть без всякой физики.
     */
    private fun cancelInternalDrift(beforeX: Float, beforeY: Float) {
        var afterX = 0f
        var afterY = 0f
        for (i in 0 until n) { afterX += px[i]; afterY += py[i] }
        val dx = (afterX - beforeX - dragShiftX) / n
        val dy = (afterY - beforeY - dragShiftY) / n
        for (i in 0 until n) { px[i] -= dx; py[i] -= dy }
    }

    private fun projectBone(ids: IntArray) {
        var cx = 0f; var cy = 0f; var c0x = 0f; var c0y = 0f; var wsum = 0f
        for (i in ids) {
            val w = matchWeight[i]
            cx += w * px[i]; cy += w * py[i]
            c0x += w * body.x[i]; c0y += w * body.y[i]
            wsum += w
        }
        cx /= wsum; cy /= wsum; c0x /= wsum; c0y /= wsum

        var s = 0f; var t = 0f
        for (i in ids) {
            val w = matchWeight[i]
            val ppx = px[i] - cx; val ppy = py[i] - cy
            val qx = body.x[i] - c0x; val qy = body.y[i] - c0y
            s += w * (qx * ppx + qy * ppy)
            t += w * (qx * ppy - qy * ppx)
        }
        val norm = sqrt(s * s + t * t)
        if (norm < 1e-9f) return
        val cos = s / norm; val sin = t / norm
        for (i in ids) {
            val qx = body.x[i] - c0x; val qy = body.y[i] - c0y
            px[i] = cx + cos * qx - sin * qy
            py[i] = cy + sin * qx + cos * qy
        }
    }

    private fun integrate(h: Float) {
        for (i in 0 until n) {
            prevX[i] = px[i]; prevY[i] = py[i]; inContact[i] = false
            if (invMass[i] == 0f) continue
            vy[i] += GRAVITY * h
            px[i] += vx[i] * h
            py[i] += vy[i] * h
            if (py[i] < GROUND_Y) {
                px[i] += (prevX[i] - px[i]) * GROUND_FRICTION
                py[i] = GROUND_Y
                inContact[i] = true
            }
        }
    }

    private fun updateVelocities(h: Float) {
        for (i in 0 until n) {
            if (invMass[i] == 0f) { vx[i] = 0f; vy[i] = 0f; continue }
            vx[i] = (px[i] - prevX[i]) / h
            vy[i] = (py[i] - prevY[i]) / h
        }
    }

    /** Продольная вязкость: гасит скорость деформации, жёсткое движение не трогает. */
    private fun applyViscosity(h: Float) {
        var k = VISCOSITY * h
        if (k > 0.5f) k = 0.5f
        for (c in 0 until conCount) {
            val i = conA[c]; val j = conB[c]
            val wi = invMass[i]; val wj = invMass[j]
            val w = wi + wj
            if (w == 0f) continue
            var nx = px[j] - px[i]
            var ny = py[j] - py[i]
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-9f) continue
            nx /= len; ny /= len
            val dv = (vx[j] - vx[i]) * nx + (vy[j] - vy[i]) * ny
            val si = k * wi / w; val sj = k * wj / w
            vx[i] += dv * nx * si; vy[i] += dv * ny * si
            vx[j] -= dv * nx * sj; vy[j] -= dv * ny * sj
        }
    }

    /**
     * Анизотропное сопротивление среды на границе тела — то, что и создаёт тягу.
     *
     * Для каждого граничного ребра берётся скорость его середины, из неё выделяется
     * составляющая ВДОЛЬ НОРМАЛИ к ребру, и гасится только она. Тангенциальная остаётся
     * нетронутой: ребро, скользящее вдоль себя, среду не гребёт.
     *
     * Знак нормали не важен: выражение (v·n)·n не меняется при n -> -n, поэтому обходить
     * контур и выяснять, где у тела «наружу», не требуется.
     *
     * Сила пропорциональна ДЛИНЕ ребра: это площадь омываемой поверхности, и без неё
     * мелкие рёбра гребли бы наравне с крупными.
     *
     * Это ВНЕШНЯЯ сила, она осознанно не сохраняет импульс — среда его и уносит.
     */
    private fun applyNormalDrag(h: Float) {
        for (e in 0 until boundCount) {
            val i = boundA[e]; val j = boundB[e]
            val ex = px[j] - px[i]
            val ey = py[j] - py[i]
            val len = sqrt(ex * ex + ey * ey)
            if (len < 1e-9f) continue

            val nx = -ey / len
            val ny = ex / len

            // Скорость середины ребра: гребёт ребро целиком, а не каждый конец отдельно.
            val vmx = (vx[i] + vx[j]) * 0.5f
            val vmy = (vy[i] + vy[j]) * 0.5f
            val vn = vmx * nx + vmy * ny

            // Линейная плюс квадратичная. Квадратичная сила равна NORMAL_DRAG_Q * vn*|vn|,
            // поэтому в коэффициенте (он делится на vn) остаётся |vn|.
            var k = (NORMAL_DRAG + NORMAL_DRAG_QUADRATIC * abs(vn)) * len * h
            if (k > 0.5f) k = 0.5f      // выше — переброс скорости через ноль, то есть раскачка

            val dv = -vn * k
            vx[i] += dv * nx; vy[i] += dv * ny
            vx[j] += dv * nx; vy[j] += dv * ny
        }
    }

    /**
     * Гашение отскока от пола: у контактной вершины убирается скорость ВВЕРХ.
     *
     * Это численный эффект, а не физика: контакт зажал позицию, решатель вернул её
     * обратно, а updateVelocities превратил разность позиций в настоящую скорость.
     * При GRAVITY = 0 стадия холостая — тело до пола не доходит.
     */
    private fun applyRestitution() {
        for (i in 0 until n) if (inContact[i] && vy[i] > 0f) vy[i] *= GROUND_RESTITUTION
    }

    /**
     * Изотропное сопротивление среды. Нормировано на h, поэтому от числа подшагов
     * не зависит. Тягу не создаёт — см. комментарий у MEDIUM_DRAG.
     */
    private fun applyMediumDrag(h: Float) {
        var keep = 1f - MEDIUM_DRAG * h
        if (keep < 0f) keep = 0f
        for (i in 0 until n) { vx[i] *= keep; vy[i] *= keep }
    }

    private fun simulate() {
        val h = DT / SUBSTEPS
        for (step in 0 until SUBSTEPS) {
            integrate(h)

            // Скобка вокруг ВНУТРЕННИХ стадий: центр масс до и после. Внешние силы
            // (среда, пол, вязкость) идут ниже по скоростям и в скобку не попадают.
            var beforeX = 0f
            var beforeY = 0f
            // Только когда тело не держат: при активной тяге посылка «центр масс не
            // должен двигаться» неверна, и поправка даёт растяжение (см. константу).
            val cancel = CANCEL_INTERNAL_DRIFT && dragId < 0
            if (cancel) {
                for (i in 0 until n) { beforeX += px[i]; beforeY += py[i] }
                dragShiftX = 0f; dragShiftY = 0f
            }

            solveConstraints(h)
            solveAreas(h)
            solveDrag(h)
            if (bonesRigid) for (ids in rigidBones) projectBone(ids)

            if (cancel) cancelInternalDrift(beforeX, beforeY)

            updateVelocities(h)
            applyViscosity(h)
            applyNormalDrag(h)
            applyRestitution()
            applyMediumDrag(h)
        }
    }

    private fun countInverted() {
        var c = 0
        for (t in 0 until body.triCount) {
            val i0 = body.triA[t]; val i1 = body.triB[t]; val i2 = body.triC[t]
            val area2 = (px[i1] - px[i0]) * (py[i2] - py[i0]) - (py[i1] - py[i0]) * (px[i2] - px[i0])
            val inv = area2 < 0f
            triInverted[t] = inv
            if (inv) c++
        }
        invertedNow = c
        if (c > invertedPeak) invertedPeak = c
    }

    /**
     * Кто сейчас командует мышцами. Источников три и они складываются по ИЛИ:
     * наведение мышью, удержание цифровой клавиши и автоматический гребок.
     */
    private fun updateMuscleTargets() {
        muscleTarget.fill(0f)

        if (hoveredMuscle >= 0) muscleTarget[hoveredMuscle] = 1f

        // Цифры 1..9 — держать соответствующую мышцу сокращённой.
        val keys = minOf(9, muscleTarget.size)
        for (m in 0 until keys) {
            if (Gdx.input.isKeyPressed(Input.Keys.NUM_1 + m)) muscleTarget[m] = 1f
        }
        // 0 — все сразу.
        if (Gdx.input.isKeyPressed(Input.Keys.NUM_0)) muscleTarget.fill(1f)

        // Автоматический гребок: рабочая фаза — треть периода, как в замерах.
        if (gait) {
            gaitFrame++
            if (gaitFrame % GAIT_PERIOD < GAIT_PERIOD / 3) muscleTarget.fill(1f)
        }
    }

    private fun updateMuscles(dt: Float) {
        for (m in muscleActivation.indices) {
            val target = muscleTarget[m]
            // Сокращение и распрямление идут с РАЗНОЙ скоростью — см. константы.
            val rate = if (target > muscleActivation[m]) MUSCLE_RATE_CONTRACT else MUSCLE_RATE_RELAX
            var k = rate * dt
            if (k > 1f) k = 1f
            muscleActivation[m] += (target - muscleActivation[m]) * k
        }
    }

    // =================================================================
    //  ВВОД
    // =================================================================

    private fun handleInput() {
        tmp.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
        viewport.unproject(tmp)
        mouseX = tmp.x; mouseY = tmp.y

        val touched = Gdx.input.isTouched
        if (touched && !wasTouched) {
            val i = nearestParticle()
            if (i >= 0) { dragId = i; matchWeight[i] = 200f }
        }
        if (!touched && wasTouched && dragId >= 0) { matchWeight[dragId] = 1f; dragId = -1 }
        wasTouched = touched

        hoverId = if (dragId >= 0) dragId else nearestParticle()
        hoveredMuscle = muscleUnderMouse()

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset()
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) bonesRigid = !bonesRigid
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) viewMode = (viewMode + 1) % 2
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) { gait = !gait; gaitFrame = 0 }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit()
    }

    private fun nearestParticle(): Int {
        var best = -1
        var bestD2 = pickRadius * pickRadius
        for (i in 0 until n) {
            val dx = px[i] - mouseX; val dy = py[i] - mouseY
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
        }
        return best
    }

    private fun muscleUnderMouse(): Int {
        var best = -1
        var bestD2 = edgePickRadius * edgePickRadius
        for (c in 0 until conCount) {
            val m = conMuscle[c]
            if (m < 0) continue
            val i = conA[c]; val j = conB[c]
            val ex = px[j] - px[i]; val ey = py[j] - py[i]
            val len2 = ex * ex + ey * ey
            var t = if (len2 < 1e-12f) 0f else ((mouseX - px[i]) * ex + (mouseY - py[i]) * ey) / len2
            if (t < 0f) t = 0f else if (t > 1f) t = 1f
            val dx = mouseX - (px[i] + ex * t); val dy = mouseY - (py[i] + ey * t)
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = m }
        }
        return best
    }

    // =================================================================
    //  ЖИЗНЕННЫЙ ЦИКЛ И ОТРИСОВКА
    // =================================================================

    override fun create() {
        shapes = ShapeRenderer(60000)
        batch = SpriteBatch()
        font = BitmapFont()
        camera = OrthographicCamera()
        viewport = FitViewport(VIEW_WIDTH, VIEW_HEIGHT, camera)
        hudCamera = OrthographicCamera()
        buildFromFile()
        reset()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        camera.position.set(1.55f, 0.85f, 0f)
        camera.update()
        hudCamera.setToOrtho(false, width.toFloat(), height.toFloat())
    }

    override fun render() {
        handleInput()

        // Мышцы обновляются ВНУТРИ паузы-гварда вместе с физикой. Иначе на паузе
        // активации продолжали бы ехать к цели, длины покоя менялись бы без решателя,
        // и при снятии паузы тело получало бы разом накопленную ошибку — рывок.
        if (!paused) {
            updateMuscleTargets()
            updateMuscles(DT)
            simulate()
        }
        countInverted()

        ScreenUtils.clear(BG)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.projectionMatrix = camera.combined

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Заливка идёт ПО ТРЕУГОЛЬНИКАМ: у гексагональной топологии нет ячеек, из которых
        // можно было бы собрать контур, а треугольники ограничения площади её и покрывают.
        for (t in 0 until body.triCount) {
            val i0 = body.triA[t]; val i1 = body.triB[t]; val i2 = body.triC[t]
            val m = triMuscle[t]
            val act = if (m >= 0) muscleActivation[m] else 0f
            shapes.color = when {
                triInverted[t] -> INVERTED_FILL
                boneOf[i0] != -1 && boneOf[i0] == boneOf[i1] && boneOf[i0] == boneOf[i2] ->
                    if (bonesRigid) BONE_FILL else BONE_FILL_OFF
                m >= 0 -> if (act > 0.002f) tmpColor.set(MUSCLE_IDLE).lerp(MUSCLE_FILL, act) else MUSCLE_IDLE
                else -> SOFT_FILL
            }
            shapes.triangle(px[i0], py[i0], px[i1], py[i1], px[i2], py[i2])
        }
        // Кости заливаются ещё и по своим связям: у мелких костей треугольников может
        // не быть вовсе, и без этого они на картинке пропадут.
        if (viewMode == 1) {
            for (i in 0 until n) {
                if (boneOf[i] == -1) continue
                shapes.color = if (bonesRigid) BONE_FILL else BONE_FILL_OFF
                shapes.circle(px[i], py[i], body.meanLinkLength * 0.45f, 10)
            }
        }
        shapes.color = ACTIVE
        if (hoverId >= 0) shapes.circle(px[hoverId], py[hoverId], body.meanLinkLength * 0.5f, 14)
        shapes.color = DOT
        for (i in 0 until n) shapes.circle(px[i], py[i], body.meanLinkLength * 0.10f, 6)
        shapes.end()

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = GROUND_COLOR
        shapes.rectLine(-1f, GROUND_Y, 5f, GROUND_Y, 0.006f)
        shapes.color = LINK
        for (c in 0 until conCount) {
            val i = conA[c]; val j = conB[c]
            shapes.rectLine(px[i], py[i], px[j], py[j], 0.0015f)
        }

        // Граница: то, что реально омывается средой и создаёт тягу.
        shapes.color = BOUNDARY_COLOR
        for (e in 0 until boundCount) {
            val i = boundA[e]; val j = boundB[e]
            shapes.rectLine(px[i], py[i], px[j], py[j], 0.005f)
        }
        shapes.end()

        drawHud()
    }

    private fun drawHud() {
        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        var y = Gdx.graphics.height - 14f
        val line = 18f

        font.color = HUD_TEXT
        font.draw(batch, "REAL BODY FROM GENOME EDITOR  -  XPBD + shape matching", 16f, y); y -= line * 1.4f

        font.color = HUD_MUTED
        font.draw(batch, "cells = $n   links = $conCount   area triangles = ${body.triCount}" +
            "   (%.2f per cell)".format(body.triCount.toFloat() / n), 16f, y); y -= line
        font.draw(batch, "substeps = $SUBSTEPS   soft compliance = $SOFT_COMPLIANCE   viscosity = $VISCOSITY /s", 16f, y); y -= line
        font.draw(batch, "medium drag = $MEDIUM_DRAG /s (isotropic, no thrust)   normal drag = $NORMAL_DRAG on $boundCount boundary edges", 16f, y); y -= line

        font.color = if (degenerateBones > 0) HUD_WARN else HUD_MUTED
        font.draw(batch, "bones: ${rigidBones.size} rigid clusters" +
            "   ${degenerateBones} single-cell clusters IGNORED (no orientation)", 16f, y); y -= line

        font.color = HUD_MUTED
        font.draw(batch, "muscles: ${body.muscleClusters.size} clusters   hovered = $hoveredMuscle", 16f, y); y -= line

        font.color = if (invertedPeak > 0) INVERTED_FILL else HUD_MUTED
        font.draw(batch, "INVERTED triangles: now = $invertedNow   peak = $invertedPeak", 16f, y); y -= line * 1.4f

        font.color = HUD_MUTED
        font.draw(batch, "LMB drag   HOVER a muscle edge   1..9 hold a muscle   0 hold ALL   " +
            "G auto-gait" + if (gait) " [ON, period $GAIT_PERIOD]" else "", 16f, y); y -= line
        font.draw(batch, "SPACE pause   R reset   B bones   C view", 16f, y); y -= line
        if (paused) { font.color = HUD_WARN; font.draw(batch, "PAUSED", 16f, y) }
        batch.end()
    }

    override fun dispose() {
        shapes.dispose(); batch.dispose(); font.dispose()
    }
}

/** Запуск: зелёная стрелка. Путь к выгрузке можно передать аргументом. */
fun main(args: Array<String>) {
    if (StartupHelper.startNewJvmIfRequired()) return
    val path = if (args.isNotEmpty()) args[0] else "body-export.txt"
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Организм из редактора — XPBD + shape matching")
        setWindowedMode(1100, 720)
        useVsync(true)
        setForegroundFPS(144)
    }
    Lwjgl3Application(RealBodyDemo(path), config)
}
