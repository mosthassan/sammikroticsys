package com.example.domain.usecase

import com.example.core.ledger.PartnerDividendSpec
import com.example.core.model.EquityShareMode
import com.example.data.ledger.LedgerWriter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.PartyEntity
import java.math.BigInteger

data class PartnerEquityReportItem(
    val partnerId: String,
    val partnerName: String,
    val phone: String,
    val historicalCapitalMinor: Long,
    val currentAccountBalanceMinor: Long,
    val equityBasisPoints: Int, // e.g. 5000 = 50.00%
    val sharePercentText: String
)

data class PartnerEquitySummary(
    val mode: EquityShareMode,
    val totalHistoricalCapitalMinor: Long,
    val totalPartnersCount: Int,
    val isFixedAgreedValid: Boolean,
    val validationMessage: String?,
    val partners: List<PartnerEquityReportItem>
)

data class PartnerWeight(
    val partnerId: String,
    val partnerName: String,
    val weight: Long
)

class PartnerEquityUseCase(
    private val db: AppDatabase,
    private val ledgerWriter: LedgerWriter? = null
) {
    /**
     * Retrieves the current organization's equity share mode.
     */
    suspend fun getEquityShareMode(): EquityShareMode {
        val org = db.organizationDao().getOrganizationSync()
        return EquityShareMode.fromString(org?.equityShareMode)
    }

    /**
     * Updates the organization's equity share mode.
     */
    suspend fun setEquityShareMode(mode: EquityShareMode) {
        val org = db.organizationDao().getOrganizationSync() ?: return
        db.organizationDao().updateEquityShareMode(org.id, mode.name)
    }

    /**
     * Validates that fixed agreed partner equity shares equal 100.00% (10,000 basis points).
     * Throws IllegalStateException if invalid.
     */
    fun validateFixedAgreedShares(partners: List<PartyEntity>) {
        val activePartners = partners.filter { it.isPartner && it.isActive }
        require(activePartners.isNotEmpty()) { "يجب وجود شريك نشط واحد على الأقل للتحقق من نسب الملكية" }

        val totalBps = activePartners.sumOf { it.equityPercentageBasisPoints }
        if (totalBps != 10000) {
            val totalPct = totalBps / 100.0
            throw IllegalStateException(
                "مجموع نسب الشركاء المتفق عليها يجب أن يساوي 100.00% (10,000 نقطة أساس) بالضبط. المجموع الحالي: $totalPct% ($totalBps نقطة أساس)"
            )
        }
    }

    /**
     * Generates a complete report of all active partners with historical capital and equity percentages
     * evaluated according to the active EquityShareMode.
     */
    suspend fun getEquitySummary(): PartnerEquitySummary {
        val mode = getEquityShareMode()
        val allPartners = db.partyDao().getPartnersSync().filter { it.isActive }
        val totalHistoricalCapital = db.journalDao().getTotalCapitalBalanceSync()

        var isFixedValid = true
        var validationMsg: String? = null

        if (mode == EquityShareMode.FIXED_AGREED) {
            val totalBps = allPartners.sumOf { it.equityPercentageBasisPoints }
            if (totalBps != 10000) {
                isFixedValid = false
                val totalPct = totalBps / 100.0
                validationMsg = "مجموع نسب الشركاء الحالية هو $totalPct% ويجب أن يكون 100.00%"
            }
        }

        val items = allPartners.map { partner ->
            val capital = db.journalDao().getPartnerCapitalBalanceSync(partner.id)
            val current = db.journalDao().getPartnerCurrentBalanceSync(partner.id)

            val bps = when (mode) {
                EquityShareMode.DERIVED_FROM_CAPITAL -> {
                    if (totalHistoricalCapital > 0L) {
                        ((capital * 10000L) / totalHistoricalCapital).toInt()
                    } else 0
                }
                EquityShareMode.FIXED_AGREED -> {
                    partner.equityPercentageBasisPoints
                }
            }

            val pct = bps / 100.0
            val pctFormatted = String.format(java.util.Locale.US, "%.2f%%", pct)

            PartnerEquityReportItem(
                partnerId = partner.id,
                partnerName = partner.name,
                phone = partner.phone,
                historicalCapitalMinor = capital,
                currentAccountBalanceMinor = current,
                equityBasisPoints = bps,
                sharePercentText = pctFormatted
            )
        }

        return PartnerEquitySummary(
            mode = mode,
            totalHistoricalCapitalMinor = totalHistoricalCapital,
            totalPartnersCount = allPartners.size,
            isFixedAgreedValid = isFixedValid,
            validationMessage = validationMsg,
            partners = items
        )
    }

    /**
     * Largest Remainder Method (Hare-Niemeyer Method) for Profit Distribution:
     * Computes integer quotas with zero rounding residue down to 1 Rial.
     */
    fun calculateHareNiemeyerDistribution(
        totalProfitMinor: Long,
        partnerWeights: List<PartnerWeight>
    ): List<PartnerDividendSpec> {
        require(totalProfitMinor > 0L) { "مبلغ الأرباح المراد توزيعه يجب أن يكون موجباً" }
        require(partnerWeights.isNotEmpty()) { "قائمة الشركاء فارغة" }

        val totalWeight = partnerWeights.sumOf { it.weight }
        require(totalWeight > 0L) { "إجمالي أوزان/حصص الشركاء يجب أن يكون أكبر من الصفر" }

        val totalProfitBig = BigInteger.valueOf(totalProfitMinor)
        val totalWeightBig = BigInteger.valueOf(totalWeight)

        data class IntermediateShare(
            val partner: PartnerWeight,
            val integerQuota: Long,
            val remainder: Long
        )

        val intermediates = partnerWeights.map { pw ->
            val product = totalProfitBig.multiply(BigInteger.valueOf(pw.weight))
            val quotient = product.divide(totalWeightBig).toLong()
            val rem = product.remainder(totalWeightBig).toLong()
            IntermediateShare(pw, quotient, rem)
        }

        val allocatedSum = intermediates.sumOf { it.integerQuota }
        val shortfall = (totalProfitMinor - allocatedSum).toInt()
        check(shortfall >= 0 && shortfall < partnerWeights.size) {
            "فشل حساب الباقي في طريقة أكبر البواقي: $shortfall"
        }

        // Rank by remainder descending, tie-break by partnerId for determinism
        val sortedIndices = intermediates.indices.sortedWith(
            compareByDescending<Int> { intermediates[it].remainder }
                .thenBy { intermediates[it].partner.partnerId }
        )

        val extraUnits = BooleanArray(intermediates.size)
        for (i in 0 until shortfall) {
            extraUnits[sortedIndices[i]] = true
        }

        val finalShares = intermediates.mapIndexed { idx, item ->
            val finalAmount = item.integerQuota + if (extraUnits[idx]) 1L else 0L
            PartnerDividendSpec(
                partnerPartyId = item.partner.partnerId,
                partnerName = item.partner.partnerName,
                amountMinor = finalAmount
            )
        }

        // Strict Invariant: Sum of shares == totalProfitMinor down to 1 Rial
        val finalSum = finalShares.sumOf { it.amountMinor }
        check(finalSum == totalProfitMinor) {
            "خلل في قاعدة اتزان توزيع الأرباح (Hare-Niemeyer): المجموع الموزع ($finalSum) لا يطابق المطلوب ($totalProfitMinor)"
        }

        return finalShares
    }

    /**
     * Executes the profit distribution workflow:
     * 1. Resolves partner shares based on the active equity mode.
     * 2. Computes the Hare-Niemeyer exact distribution.
     * 3. Posts the dividend journal entry (DR 3301 Retained Earnings, CR 3201 Partner Current Accounts in YER).
     */
    suspend fun executeProfitDistribution(
        fiscalYear: Int,
        dateEpochDay: Long,
        totalProfitMinor: Long,
        notes: String = ""
    ): DocumentEntity {
        val writer = ledgerWriter ?: error("LedgerWriter is required to execute profit distribution")
        val mode = getEquityShareMode()
        val allPartners = db.partyDao().getPartnersSync().filter { it.isActive }
        require(allPartners.isNotEmpty()) { "لا يوجد شركاء نشطون مسجلون لتوزيع الأرباح" }

        val weights = when (mode) {
            EquityShareMode.DERIVED_FROM_CAPITAL -> {
                allPartners.map { p ->
                    val cap = db.journalDao().getPartnerCapitalBalanceSync(p.id)
                    PartnerWeight(p.id, p.name, cap)
                }
            }
            EquityShareMode.FIXED_AGREED -> {
                validateFixedAgreedShares(allPartners)
                allPartners.map { p ->
                    PartnerWeight(p.id, p.name, p.equityPercentageBasisPoints.toLong())
                }
            }
        }

        val shares = calculateHareNiemeyerDistribution(totalProfitMinor, weights)
        return writer.distributeDividends(
            totalDividendMinor = totalProfitMinor,
            partnerShares = shares,
            fiscalYear = fiscalYear,
            dateEpochDay = dateEpochDay,
            notes = notes
        )
    }
}
