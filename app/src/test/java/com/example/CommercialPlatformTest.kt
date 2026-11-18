package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AiModelCatalog
import com.example.data.model.BillingSummary
import com.example.data.model.PlanCategory
import com.example.data.model.TxType
import com.example.data.repository.CerevixGatewayClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommercialPlatformTest {

    @Test
    fun `default plans include all consumer and developer tiers with exact pricing`() {
        val plans = AiModelCatalog.defaultPlans.associateBy { it.id }

        assertEquals(0L, plans["FREE"]?.monthlyPriceInr)
        assertEquals(499L, plans["PRO"]?.monthlyPriceInr)
        assertEquals(999L, plans["STUDIO"]?.monthlyPriceInr)
        assertEquals(2499L, plans["BUSINESS"]?.monthlyPriceInr)
        assertEquals(PlanCategory.CONSUMER, plans["PRO"]?.resolvedCategory())

        assertEquals(0L, plans["FREE_DEVELOPER"]?.monthlyPriceInr)
        assertEquals(499L, plans["PRO_DEVELOPER"]?.monthlyPriceInr)
        assertEquals(1999L, plans["BUSINESS_DEVELOPER"]?.monthlyPriceInr)
        assertEquals(PlanCategory.DEVELOPER, plans["PRO_DEVELOPER"]?.resolvedCategory())
        assertEquals(PlanCategory.DEVELOPER, plans["ENTERPRISE"]?.resolvedCategory())
        assertTrue((plans["BUSINESS_DEVELOPER"]?.rateLimitRpm ?: 0L) > (plans["PRO_DEVELOPER"]?.rateLimitRpm ?: 0L))
    }

    @Test
    fun `credit packs catalog defines valid non-zero top-up tiers`() {
        val packs = AiModelCatalog.creditPacks
        assertEquals(3, packs.size)
        packs.forEach { pack ->
            assertTrue(pack.credits > 0L)
            assertTrue(pack.priceInr > 0L)
        }
    }

    @Test
    fun `gateway client defaults to official production api domain and persists local override`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = CerevixGatewayClient(context)

        assertEquals("https://api.cerevix.ai", client.getGatewayBaseUrl())

        client.setGatewayBaseUrl("http://10.0.2.2:8080/")
        assertEquals("http://10.0.2.2:8080", client.getGatewayBaseUrl())

        client.setGatewayBaseUrl("")
        assertEquals("https://api.cerevix.ai", client.getGatewayBaseUrl())
    }

    @Test
    fun `billing summary and ledger types default safely without fake active state`() {
        val summary = BillingSummary()
        assertFalse(summary.razorpayConfigured)
        assertEquals(null, summary.activeSubscription)
        assertEquals(0L, summary.wallet.balance)
        assertTrue(TxType.values().contains(TxType.GENERATION_REFUND))
        assertTrue(TxType.values().contains(TxType.CREDIT_PURCHASE))
    }
}
