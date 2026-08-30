package me.chosante.ui.state

/**
 * Projects [UiState.manualBuild]/[UiState.manualAchieved] into the same `build`/`achieved`/`phase`
 * shape [me.chosante.ui.paperdoll.PaperdollPanel]/[me.chosante.ui.stats.StatsPanel] already render,
 * so the manual-construction screen reuses both composables verbatim -- zero signature changes,
 * despite [PaperdollPanel] having only one other call site today. This is a throwaway view for
 * rendering only: it is never assigned back to [me.chosante.ui.state.BuildSearchModel]'s live `ui`,
 * so mutating callbacks driven from it (equip/socket/allocate) must write into `manualBuild`
 * directly, not through this projection.
 */
fun UiState.asManualView(): UiState =
    copy(
        build = manualBuild,
        achieved = manualAchieved,
        phase = if (manualBuild != null) Phase.Done else Phase.Idle,
        zenith = manualZenithState,
        zenithUrl = manualZenithUrl,
        activeBuildId = manualActiveBuildId,
        activeBuildName = manualActiveBuildName,
        // There's no solver run behind a manual build, so none of the auto-Builder's "target stats"/
        // "did the search prove this optimal" concepts apply -- clearing them keeps StatsPanel's reused
        // DesiredVsAchieved/MasterySummary/MatchHero sections from comparing against (or badging with)
        // stale state left over from whatever the auto-Builder screen last did.
        targets = emptyList(),
        optimal = false,
        proofState = ProofState.Idle,
        maxDamageStructural = false
    )
