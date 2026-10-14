package io.github.some_example_name.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;

import io.github.some_example_name.old.game.MyGame;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.Arrays;

/**
 * Launches the desktop (LWJGL3) application.
 */
public class Lwjgl3Launcher {
    /*
     * Краши ловит сама игра (core, bug.CrashHandler): собирает .bug с журналом действий
     * и показывает окно отчёта. Здесь своего обработчика нет, чтобы не перехватить
     * исключение раньше неё; упавшее до запуска игры (окно, OpenGL) печатается JVM как
     * обычно.
     */
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired(true, args))
            return; // This handles macOS support and helps on Windows.
        WindowsFileAssociations.register();
        createApplication(args);
    }

    private static Lwjgl3Application createApplication(String[] args) {
        MyGame game = new MyGame(new DesktopFileProvider(), null);
        // created вызывается после MyGame.create: меню и журнал уже готовы.

        Lwjgl3ApplicationConfiguration configuration = getDefaultConfiguration();
        // Перетаскивание файлов в окно — импорт геномов и карт (MyGame.filesDropped). На macOS это
        // единственный способ: системное окно выбора там не открывается (DesktopFileProvider).
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void created(com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window window) {
                com.badlogic.gdx.Gdx.app.postRunnable(() -> game.openFiles(Arrays.asList(args)));
            }

            @Override
            public void filesDropped(String[] files) {
                game.filesDropped(Arrays.asList(files));
            }
        });
        return new Lwjgl3Application(game, configuration);
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Genomeia");

        // === НАДЁЖНЫЙ способ получить реальное разрешение экрана ===
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = screenSize.width;
        int screenHeight = screenSize.height;

        System.out.println("[Lwjgl3Launcher] Detected screen resolution: " + screenWidth + " × " + screenHeight);

        // По умолчанию — 1300×1300
        int windowSize = 1300;

        // Если хотя бы одна сторона экрана меньше 1300 — делаем окно квадратным
        if (screenWidth < 1300 || screenHeight < 1300) {
            int minDimension = Math.min(screenWidth, screenHeight);
            windowSize = (int) (minDimension * 0.8f);

            // Минимальная защита — не меньше 640 пикселей
            if (windowSize < 640) {
                windowSize = 640;
            }
        }

        // === САМАЯ ВАЖНАЯ ЗАЩИТА ===
        // Убеждаемся, что окно точно помещается с учётом заголовка окна, рамок и панели задач
        int maxSafeWidth = screenWidth - 40;   // небольшой отступ по ширине
        int maxSafeHeight = screenHeight - 120; // отступ сверху (заголовок + панель задач)
        windowSize = Math.min(windowSize, Math.min(maxSafeWidth, maxSafeHeight));

        System.out.println("[Lwjgl3Launcher] Final window size: " + windowSize + " × " + windowSize);

        configuration.setWindowedMode(windowSize, windowSize);

        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
//        configuration.useVsync(false);
//        configuration.setForegroundFPS(60);
        // Размер окна можно задать снаружи: -Dgenomeia.width=1280 -Dgenomeia.height=720.
        // Нужно для проверки вёрстки на разных разрешениях без правки кода.
        int windowWidth = Integer.getInteger("genomeia.width", 900);
        int windowHeight = Integer.getInteger("genomeia.height", 900);
        configuration.setWindowedMode(windowWidth, windowHeight);
//        configuration.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        // GLES 3.0 feature set (no SSBO). Desktop GL 3.3 for uintBitsToFloat / core profile.
        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
//        configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2);

        //// You can change these files; they are in lwjgl3/src/main/resources/ .
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}
