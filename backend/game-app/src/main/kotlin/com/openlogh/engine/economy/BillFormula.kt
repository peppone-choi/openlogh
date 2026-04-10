package com.openlogh.engine.economy

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Officer monthly salary (bill) formula — upstream a7a19cc3 port.
 *
 * Ported from legacy PHP `hwe/func_converter.php` `getBill()` / `getDedLevel()`:
 * ```
 *   getBill(ded)      = getDedLevel(ded) * 200 + 400
 *   getDedLevel(ded)  = clamp(ceil(sqrt(ded) / 10), 0, maxDedLevel=30)
 * ```
 *
 * This is the authoritative bill-from-dedication helper shared by both
 * [com.openlogh.engine.ai.FactionAI.adjustTaxAndBill] (Phase 22-01) and
 * [com.openlogh.engine.Gin7EconomyService.payOfficerSalaries] (Phase 23-04).
 *
 * ### Design notes
 *
 * - **Pure object, no Spring DI.** Mirrors the `UtilityScorer` / `CommandHierarchyService`
 *   / `SuccessionService` precedent: math helpers that operate on primitives live as
 *   top-level `object` declarations in the engine layer.
 * - **maxDedLevel = 30** is the legacy clamp. Beyond ded ≈ 90_000 the formula saturates
 *   at `30 * 200 + 400 = 6400` gold/supplies per officer per month.
 * - **Integer arithmetic** — `toInt()` after `ceil()` matches PHP's integer semantics.
 *
 * ### Anchor values (Phase 22-01 FactionAIBillFormulaTest regression targets)
 *
 * | dedication  | dedLevel | bill |
 * |-------------|----------|------|
 * | 0           | 0        | 400  |
 * | 100         | 1        | 600  |
 * | 400         | 2        | 800  |
 * | 10_000      | 10       | 2400 |
 * | 1_000_000   | 30 (cap) | 6400 |
 *
 * The Phase 22-01 `FactionAIBillFormulaTest` uses reflection on the private
 * `FactionAI.getBillFromDedication` helper. To preserve that regression test
 * unchanged, `FactionAI` keeps a thin private delegator that calls this object —
 * the test still passes while the canonical implementation lives here.
 */
object BillFormula {

    /** Maximum dedication level (legacy clamp) — saturates bill at `30*200+400 = 6400`. */
    const val MAX_DED_LEVEL: Int = 30

    /**
     * Compute monthly salary bill for an officer with the given dedication.
     *
     * @param dedication officer dedication score (any non-negative int; negatives clamp to 0 level)
     * @return monthly bill in funds/supplies units (min 400 at ded=0, max 6400 at ded≥90_000)
     */
    fun fromDedication(dedication: Int): Int {
        val dedLevel = ceil(sqrt(dedication.toDouble()) / 10.0).toInt().coerceIn(0, MAX_DED_LEVEL)
        return dedLevel * 200 + 400
    }
}
