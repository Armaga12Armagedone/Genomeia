package io.github.some_example_name.old.commands

sealed class NavigationCommands


object GoExit: NavigationCommands()
object GoBack: NavigationCommands()

sealed class Menu: NavigationCommands()

object GoWorldEditor: Menu()
class GoGenomeEditor(val genomeName: String?): Menu()
object GoSettings: Menu()
object GoLevelEditor: Menu()
object GoEcoSystem: Menu()
object GoSupport: Menu()
class GoSimulation(
    val map: Array<BooleanArray>?,
    val genomeName: String?
): Menu()
object EcoSystemScreenGlobalSettings: Menu()
object EcoSystemScreenCellsSettings: Menu()
