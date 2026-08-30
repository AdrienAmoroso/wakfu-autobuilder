package me.chosante.ui.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.chosante.autobuilder.domain.BuildCombination
import me.chosante.autobuilder.genetic.SolverResult
import me.chosante.common.Characteristic
import me.chosante.common.Equipment
import me.chosante.common.I18nText
import me.chosante.common.ItemType
import me.chosante.common.Rarity
import me.chosante.common.RuneColor
import me.chosante.common.RuneType
import me.chosante.common.Sublimation
import me.chosante.common.SublimationKind
import me.chosante.common.SublimationRarity
import me.chosante.common.skills.CharacterSkills
import me.chosante.common.skills.StrengthCharacteristic
import me.chosante.ui.history.HistoryRepository
import me.chosante.ui.manualbuild.manualSkillBranches
import me.chosante.ui.manualbuild.manualTotalMastery
import me.chosante.ui.paperdoll.naturalSlotIdFor
import me.chosante.ui.paperdoll.slotAssignments
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Path

/**
 * [Screen.ManualBuild]: equipping/rejecting directly into [UiState.manualBuild], [asManualView]'s
 * projection, and the skill allocator's immutable-update guarantee. Mirrors the fixture/helper
 * shapes used in [BuildSearchModelLibraryTest].
 */
class ManualBuildTest {
    private fun item(
        id: Int,
        type: ItemType,
        rarity: Rarity = Rarity.LEGENDARY,
        name: String = "Item$id",
        maxShardSlots: Int = 0,
    ) = Equipment(
        equipmentId = id,
        guiId = id,
        level = 110,
        name = I18nText(fr = name, en = name, es = "", pt = ""),
        rarity = rarity,
        itemType = type,
        characteristics = mapOf(Characteristic.MASTERY_DISTANCE to 10),
        maxShardSlots = maxShardSlots
    )

    private fun rune(
        id: Int,
        color: RuneColor = RuneColor.RED,
        characteristic: Characteristic = Characteristic.MASTERY_DISTANCE,
    ) = RuneType(
        id = id,
        name = I18nText(fr = "Rune$id", en = "Rune$id", es = "", pt = ""),
        color = color,
        characteristic = characteristic,
        doubleBonusPosition = emptyList(),
        gfxId = id
    )

    private fun sublimation(id: Int) =
        Sublimation(
            stateId = id,
            name = I18nText(fr = "Sub$id", en = "Sub$id", es = "", pt = ""),
            rarity = SublimationRarity.NORMAL,
            kind = SublimationKind.FLAT
        )

    private fun model(
        scope: CoroutineScope,
        tempDir: Path,
    ): BuildSearchModel =
        BuildSearchModel(
            scope = scope,
            buildFinder = {
                flowOf(
                    SolverResult(individual = BuildCombination(emptyList(), CharacterSkills(110)), matchPercentage = BigDecimal.ZERO, progressPercentage = 100, isOptimal = true)
                )
            },
            zenithBuilder = { "" },
            mainDispatcher = Dispatchers.Unconfined,
            historyRepository = HistoryRepository(baseDir = tempDir, ioDispatcher = Dispatchers.Unconfined),
            libraryPreferences = LibraryPreferences(null)
        )

    @Test
    fun `entering the screen seeds an empty build independent of the auto-Builder's`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            assertThat(model.ui.manualBuild).isNotNull
            assertThat(model.ui.manualBuild!!.equipments).isEmpty()
            assertThat(model.ui.build).isNull() // auto-Builder's own field untouched
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `equipItemInManualSlot equips into an empty slot and updates manualAchieved`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val cape = item(1, ItemType.CAPE)

            model.equipItemInManualSlot("cape", cape)

            assertThat(model.ui.manualBuild!!.equipments).containsExactly(cape)
            assertThat(model.ui.manualAchieved[Characteristic.MASTERY_DISTANCE]).isEqualTo(10)
            assertThat(model.ui.toast).isNull()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `equipItemInManualSlot replaces whatever already occupies the slot`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val capeA = item(1, ItemType.CAPE, name = "Cape A")
            val capeB = item(2, ItemType.CAPE, name = "Cape B")

            model.equipItemInManualSlot("cape", capeA)
            model.equipItemInManualSlot("cape", capeB)

            assertThat(model.ui.manualBuild!!.equipments).containsExactly(capeB)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a two-handed weapon clears both weapon slots, a one-hander keeps the off-hand`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val offHand = item(1, ItemType.OFF_HAND_WEAPONS)
            val twoHander = item(2, ItemType.TWO_HANDED_WEAPONS)
            val oneHander = item(3, ItemType.ONE_HANDED_WEAPONS)

            model.equipItemInManualSlot("weapon2", offHand)
            model.equipItemInManualSlot("weapon", twoHander)
            assertThat(model.ui.manualBuild!!.equipments).containsExactly(twoHander)

            model.equipItemInManualSlot("weapon", oneHander)
            assertThat(model.ui.manualBuild!!.equipments).containsExactlyInAnyOrder(oneHander, offHand)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a second RELIC is rejected with a toast and the build is left unchanged`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val relic1 = item(1, ItemType.AMULET, rarity = Rarity.RELIC)
            val relic2 = item(2, ItemType.BELT, rarity = Rarity.RELIC)

            model.equipItemInManualSlot("amulet", relic1)
            model.equipItemInManualSlot("belt", relic2)

            assertThat(model.ui.manualBuild!!.equipments).containsExactly(relic1)
            assertThat(model.ui.toast).isNotNull()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `unequipManualItem drops the item and its runes-sublimations`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val cape = item(1, ItemType.CAPE)
            model.equipItemInManualSlot("cape", cape)

            model.unequipManualItem(cape)

            assertThat(model.ui.manualBuild!!.equipments).isEmpty()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `asManualView projects the manual build without touching the auto-Builder's own state`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            model.equipItemInManualSlot("cape", item(1, ItemType.CAPE))

            val view = model.ui.asManualView()

            assertThat(view.build).isEqualTo(model.ui.manualBuild)
            assertThat(view.achieved).isEqualTo(model.ui.manualAchieved)
            assertThat(view.phase).isEqualTo(Phase.Done)
            assertThat(view.targets).isEmpty()
            assertThat(view.optimal).isFalse()
            // Never written back into the live model -- the auto-Builder's own fields stay untouched.
            assertThat(model.ui.build).isNull()
            assertThat(model.ui.phase).isEqualTo(Phase.Idle)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a skill row's onChange never mutates the original CharacterSkills instance`() {
        val original = CharacterSkills(110)
        val originalPoints = original.strength.masteryElementary.pointsAssigned
        val branch = manualSkillBranches(original).first { it.labelKey.name == "BRANCH_STRENGTH" }
        val row = branch.rows.first { it.name == StrengthCharacteristic.MasteryElementary(0).name }

        val updated = row.onChange(row.points + 1)

        assertThat(original.strength.masteryElementary.pointsAssigned).isEqualTo(originalPoints)
        assertThat(updated.strength.masteryElementary.pointsAssigned).isEqualTo(originalPoints + 1)
        assertThat(updated).isNotSameAs(original)
    }

    @Test
    fun `naturalSlotIdFor fills ring1 then ring2, then replaces ring1`() {
        val ringA = item(1, ItemType.RING, name = "Ring A")
        val ringB = item(2, ItemType.RING, name = "Ring B")
        val ringC = item(3, ItemType.RING, name = "Ring C")

        assertThat(naturalSlotIdFor(ItemType.RING, emptyMap())).isEqualTo("ring1")
        assertThat(naturalSlotIdFor(ItemType.RING, slotAssignments(listOf(ringA)))).isEqualTo("ring2")
        assertThat(naturalSlotIdFor(ItemType.RING, slotAssignments(listOf(ringA, ringB)))).isEqualTo("ring1")
        // Order-independent -- ringC isn't involved, just confirming a full pair always routes to ring1.
        assertThat(naturalSlotIdFor(ItemType.RING, slotAssignments(listOf(ringB, ringC)))).isEqualTo("ring1")
    }

    @Test
    fun `naturalSlotIdFor routes weapons per the two-handed rule`() {
        assertThat(naturalSlotIdFor(ItemType.ONE_HANDED_WEAPONS, emptyMap())).isEqualTo("weapon")
        assertThat(naturalSlotIdFor(ItemType.TWO_HANDED_WEAPONS, emptyMap())).isEqualTo("weapon")
        assertThat(naturalSlotIdFor(ItemType.OFF_HAND_WEAPONS, emptyMap())).isEqualTo("weapon2")
    }

    @Test
    fun `naturalSlotIdFor resolves every other type to its single matching slot`() {
        assertThat(naturalSlotIdFor(ItemType.HELMET, emptyMap())).isEqualTo("helmet")
        assertThat(naturalSlotIdFor(ItemType.PETS, emptyMap())).isEqualTo("pet")
        assertThat(naturalSlotIdFor(ItemType.MOUNTS, emptyMap())).isEqualTo("mount")
    }

    @Test
    fun `placeManualRuneInSlot writes the slot and re-derives the compacted BuildCombination list`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val cape = item(1, ItemType.CAPE, maxShardSlots = 2)
            model.equipItemInManualSlot("cape", cape)
            val runeA = rune(1)
            val runeB = rune(2, color = RuneColor.GREEN)

            model.placeManualRuneInSlot("Item1", 1, runeB)
            assertThat(model.ui.manualRuneSlots["Item1"]).containsExactly(null, runeB)
            assertThat(model.ui.manualBuild!!.runes[cape]).containsExactly(runeB)

            model.placeManualRuneInSlot("Item1", 0, runeA)
            assertThat(model.ui.manualRuneSlots["Item1"]).containsExactly(runeA, runeB)
            assertThat(model.ui.manualBuild!!.runes[cape]).containsExactlyInAnyOrder(runeA, runeB)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `placing a rune into an already-filled slot overwrites it and clears its gold flag`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val cape = item(1, ItemType.CAPE, maxShardSlots = 1)
            model.equipItemInManualSlot("cape", cape)
            model.placeManualRuneInSlot("Item1", 0, rune(1))
            model.toggleManualRuneSlotGold("Item1", 0)
            assertThat(model.ui.manualGoldRuneSlots["Item1"]).containsExactly(0)

            val runeB = rune(2, color = RuneColor.BLUE)
            model.placeManualRuneInSlot("Item1", 0, runeB)

            assertThat(model.ui.manualRuneSlots["Item1"]).containsExactly(runeB)
            assertThat(model.ui.manualGoldRuneSlots["Item1"].orEmpty()).doesNotContain(0)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `clearManualRuneSlot empties the slot and toggleManualRuneSlotGold no-ops on an empty one`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val cape = item(1, ItemType.CAPE, maxShardSlots = 1)
            model.equipItemInManualSlot("cape", cape)
            model.placeManualRuneInSlot("Item1", 0, rune(1))

            model.clearManualRuneSlot("Item1", 0)
            assertThat(model.ui.manualRuneSlots["Item1"]).containsExactly(null as RuneType?)
            assertThat(
                model.ui.manualBuild!!
                    .runes[cape]
                    .orEmpty()
            ).isEmpty()

            model.toggleManualRuneSlotGold("Item1", 0)
            assertThat(model.ui.manualGoldRuneSlots["Item1"].orEmpty()).isEmpty()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `setManualSublimationForItem replaces, not appends, the item's one sublimation slot`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            // A normal sub's carrier needs >= 3 sockets (BuildCombination.hasLegalSublimations).
            val cape = item(1, ItemType.CAPE, maxShardSlots = 3)
            model.equipItemInManualSlot("cape", cape)
            val subA = sublimation(1)
            val subB = sublimation(2)

            model.setManualSublimationForItem("Item1", subA)
            assertThat(model.ui.manualBuild!!.sublimations[cape]).containsExactly(subA)

            model.setManualSublimationForItem("Item1", subB)
            assertThat(model.ui.manualBuild!!.sublimations[cape]).containsExactly(subB)

            model.removeManualSublimationFromItem("Item1")
            assertThat(
                model.ui.manualBuild!!
                    .sublimations[cape]
                    .orEmpty()
            ).isEmpty()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `unequipManualItem drops the item's rune-slot and gold state`(
        @TempDir tempDir: Path,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val model = model(scope, tempDir)
            model.goToScreen(Screen.ManualBuild)
            val cape = item(1, ItemType.CAPE, maxShardSlots = 1)
            model.equipItemInManualSlot("cape", cape)
            model.placeManualRuneInSlot("Item1", 0, rune(1))
            model.toggleManualRuneSlotGold("Item1", 0)

            model.unequipManualItem(cape)

            assertThat(model.ui.manualRuneSlots).doesNotContainKey("Item1")
            assertThat(model.ui.manualGoldRuneSlots).doesNotContainKey("Item1")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `manualTotalMastery sums the specialized masteries plus only the highest elemental one`() {
        val achieved =
            mapOf(
                Characteristic.MASTERY_ELEMENTARY_WATER to 100,
                Characteristic.MASTERY_ELEMENTARY_FIRE to 300,
                Characteristic.MASTERY_ELEMENTARY_EARTH to 50,
                Characteristic.MASTERY_ELEMENTARY_WIND to 10,
                Characteristic.MASTERY_DISTANCE to 40,
                Characteristic.MASTERY_CRITICAL to 20,
                Characteristic.MASTERY_BACK to 15,
                Characteristic.MASTERY_MELEE to 5,
                Characteristic.MASTERY_BERSERK to 8,
                Characteristic.MASTERY_HEALING to 12,
                // Never counted: not a mastery at all.
                Characteristic.CONTROL to 1000
            )

        // 300 (highest element, fire) + 40+20+15+5+8+12 (every specialized mastery) = 400.
        assertThat(manualTotalMastery(achieved)).isEqualTo(400)
    }
}
