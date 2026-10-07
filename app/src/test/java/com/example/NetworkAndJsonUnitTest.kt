package com.example

import com.example.core.model.CurrencyCode
import com.example.data.ledger.PurchaseItemSpec
import com.example.data.local.entity.PartyEntity
import com.example.data.network.NetworkConfig
import com.example.util.DataJsonHelper
import com.example.util.NetworkSecurityHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkAndJsonUnitTest {

    @Test
    fun testPartiesJsonExportAndParse() {
        val parties = listOf(
            PartyEntity(
                id = "p1",
                name = "بقالة الأمل",
                phone = "777123456",
                isCustomer = true,
                isVendor = false,
                creditLimitMinor = 5000000L
            ),
            PartyEntity(
                id = "p2",
                name = "شركة ستارلينك اليمن",
                phone = "770999888",
                isCustomer = false,
                isVendor = true,
                creditLimitMinor = 0L
            )
        )

        val json = DataJsonHelper.exportPartiesToJson(parties)
        assertNotNull(json)
        assertTrue(json.contains("بقالة الأمل"))
        assertTrue(json.contains("شركة ستارلينك اليمن"))

        val parsed = DataJsonHelper.parsePartiesFromJson(json)
        assertEquals(2, parsed.size)
        assertEquals("بقالة الأمل", parsed[0].name)
        assertEquals("777123456", parsed[0].phone)
        assertTrue(parsed[0].isCustomer)
        assertEquals("شركة ستارلينك اليمن", parsed[1].name)
        assertTrue(parsed[1].isVendor)
    }

    @Test
    fun testPurchasesJsonExportAndParse() {
        val drafts = listOf(
            DataJsonHelper.ImportedPurchaseDraft(
                vendorName = "مؤسسة الأبراج للاتصالات",
                currencyCode = CurrencyCode.USD,
                exchangeRateMicros = 530_000_000L,
                items = listOf(
                    PurchaseItemSpec(
                        description = "راوتر CCR2004",
                        accountCode = "1501",
                        quantity = 1,
                        unitPriceMinor = 45000L,
                        isAsset = true,
                        usefulLifeMonths = 36
                    )
                ),
                notes = "توريد معدات سنترال"
            )
        )

        val json = DataJsonHelper.exportPurchasesToJson(drafts)
        assertTrue(json.contains("مؤسسة الأبراج للاتصالات"))
        assertTrue(json.contains("CCR2004"))

        val parsed = DataJsonHelper.parsePurchasesFromJson(json)
        assertEquals(1, parsed.size)
        assertEquals("مؤسسة الأبراج للاتصالات", parsed[0].vendorName)
        assertEquals(CurrencyCode.USD, parsed[0].currencyCode)
        assertEquals(1, parsed[0].items.size)
        assertEquals("راوتر CCR2004", parsed[0].items[0].description)
        assertTrue(parsed[0].items[0].isAsset)
    }

    @Test
    fun testRouterOsHardeningScriptGeneration() {
        val config = NetworkConfig(
            networkName = "شبكة سام",
            mainRouterModel = "CCR2004",
            approvedSubnet = "10.10.0.0/16",
            primaryDns = "8.8.8.8",
            secondaryDns = "1.1.1.1"
        )
        val options = NetworkSecurityHelper.SecurityOptions(
            disableInsecureServices = true,
            blockPortScan = true,
            blockBruteForceWinbox = true,
            preventDnsPoisoning = true,
            dropInvalidPackets = true,
            enableClientIsolation = true,
            customWinboxPort = 8291
        )

        val script = NetworkSecurityHelper.generateRouterOsHardeningScript(config, options)
        assertNotNull(script)
        assertTrue(script.contains("/ip service set telnet disabled=yes"))
        assertTrue(script.contains("Port_Scanners"))
        assertTrue(script.contains("Winbox_Blacklist"))
        assertTrue(script.contains("allow-remote-requests=no"))
        assertTrue(script.contains("horizon=1"))
    }

    @Test
    fun testUserExact36DevicesBackupImport() {
        val userJson = """
        {
          "format": "MIKROTIK_DEVICE_BACKUP",
          "version": 1,
          "networkName": "شبكة طلقة نت",
          "exportedAt": "2026-10-02 05:56:16",
          "deviceCount": 36,
          "devices": [
            {
              "name": "talqaap",
              "deviceType": "مرسل",
              "ipAddress": "10.0.0.102",
              "locationArea": "فوق محل الدوحة",
              "model": "nano station",
              "status": "ONLINE"
            },
            {
              "name": "AP30",
              "deviceType": "Access Point",
              "ipAddress": "10.0.0.30",
              "locationArea": "فالح مرزوق حفرين",
              "model": "kt708",
              "status": "ONLINE"
            },
            {
              "name": "AP15",
              "deviceType": "لاقط",
              "ipAddress": "10.0.0.15",
              "locationArea": "لاقط علي فاضل",
              "model": "kt708",
              "status": "ONLINE"
            },
            {
              "name": "باوربيم",
              "deviceType": "مستقبل",
              "ipAddress": "10.0.0.103",
              "locationArea": "مستقبل فوق بيتي",
              "model": "nano station",
              "status": "ONLINE"
            }
          ]
        }
        """.trimIndent()

        val result = DataJsonHelper.parseDevicesFromJson(userJson)
        assertEquals("شبكة طلقة نت", result.networkName)
        assertEquals(4, result.devices.size)
        assertEquals("talqaap", result.devices[0].name)
        assertEquals("10.0.0.102", result.devices[0].ipAddress)
        assertEquals("فوق محل الدوحة", result.devices[0].towerLocation)
        assertEquals(com.example.data.network.DeviceType.ACCESS_POINT, result.devices[0].deviceType)
        assertEquals(com.example.data.network.DeviceType.STATION, result.devices[2].deviceType)
        assertEquals(com.example.data.network.DeviceType.STATION, result.devices[3].deviceType)
    }

    @Test
    fun testLegacyCustomerBackupImport() {
        val legacyJson = """
        {
          "format": "SAM_CUSTOMER_BACKUP",
          "version": 1,
          "networkName": "شبكة طلقة نت",
          "exportedAt": "2026-10-02 05:56:16",
          "customerCount": 2,
          "customers": [
            {
              "name": "بقالة البركة",
              "ownerName": "محمد صالح",
              "phone": "771234567",
              "location": "الشارع العام",
              "balanceOwed": 25000.0
            },
            {
              "name": "مركز النجم للاتصالات",
              "ownerName": "عادل أحمد",
              "phone": "779888777",
              "location": "السوق",
              "balanceOwed": 50000.0
            }
          ]
        }
        """.trimIndent()

        val parsed = DataJsonHelper.parsePartiesFromJson(legacyJson)
        assertEquals(2, parsed.size)
        assertEquals("بقالة البركة", parsed[0].name)
        assertEquals("771234567", parsed[0].phone)
        assertTrue(parsed[0].isCustomer)
        assertEquals(2500000L, parsed[0].creditLimitMinor)
        assertEquals("مركز النجم للاتصالات", parsed[1].name)
        assertEquals(5000000L, parsed[1].creditLimitMinor)
    }

    @Test
    fun testLegacyPurchaseInvoiceBackupImport() {
        val legacyJson = """
        {
          "format": "SAM_PURCHASE_INVOICE_BACKUP",
          "version": 1,
          "networkName": "شبكة طلقة نت",
          "invoiceCount": 1,
          "invoices": [
            {
              "invoiceNumber": "PINV-2026-0001",
              "supplierName": "شركة يمن فايبر",
              "targetType": "SERVICE_FIBER",
              "currency": "USD",
              "originalAmount": 200.0,
              "totalAmount": 106000.0,
              "notes": "اشتراك خط الفايبر الرئيسي"
            }
          ]
        }
        """.trimIndent()

        val parsed = DataJsonHelper.parsePurchasesFromJson(legacyJson)
        assertEquals(1, parsed.size)
        assertEquals("شركة يمن فايبر", parsed[0].vendorName)
        assertEquals(CurrencyCode.USD, parsed[0].currencyCode)
        assertEquals(1, parsed[0].items.size)
        assertEquals("5101", parsed[0].items[0].accountCode)
        assertEquals(20000L, parsed[0].items[0].unitPriceMinor)
    }

    @Test
    fun testUniqueIpAddressConflictDetection() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = com.example.data.network.NetworkRepository(context)

        // Seeded devices include 10.10.1.11 (سيكتور شمالي BaseBox 5)
        val initialDevices = repo.devices.value
        val existingAp = initialDevices.first { it.ipAddress == "10.10.1.11" }
        assertNotNull(existingAp)

        // 1. Attempt to add a new device with the same IP: 10.10.1.11
        val conflictingNewDevice = com.example.data.network.NetworkDevice(
            name = "سيكتور تجريبي جديد",
            ipAddress = "10.10.1.11",
            towerLocation = "برج حدة",
            deviceType = com.example.data.network.DeviceType.ACCESS_POINT
        )
        val conflictResult = repo.addOrUpdateDevice(conflictingNewDevice)
        assertTrue("Attempt to add duplicate IP must result in IpConflict", conflictResult is com.example.data.network.DeviceSaveResult.IpConflict)
        val conflict = conflictResult as com.example.data.network.DeviceSaveResult.IpConflict
        assertEquals("Conflicting device name must match", existingAp.name, conflict.conflictingDevice.name)
        assertEquals("Conflicting tower location must match", existingAp.towerLocation, conflict.conflictingDevice.towerLocation)

        // 2. Add a new device with a unique IP: 10.10.1.99
        val uniqueNewDevice = com.example.data.network.NetworkDevice(
            name = "سيكتور حي الجامعة",
            ipAddress = "10.10.1.99",
            towerLocation = "برج الجامعة",
            deviceType = com.example.data.network.DeviceType.ACCESS_POINT
        )
        val successResult = repo.addOrUpdateDevice(uniqueNewDevice)
        assertTrue("Adding device with unique IP must succeed", successResult is com.example.data.network.DeviceSaveResult.Success)

        // 3. Update existing device with same IP must succeed (no self-conflict)
        val updatedAp = existingAp.copy(notes = "تم تحديث الملاحظات")
        val selfUpdateResult = repo.addOrUpdateDevice(updatedAp)
        assertTrue("Self update with existing IP must succeed", selfUpdateResult is com.example.data.network.DeviceSaveResult.Success)

        // 4. Update unique device to existing AP's IP must be rejected
        val duplicateUpdate = uniqueNewDevice.copy(ipAddress = "10.10.1.11")
        val rejectedUpdateResult = repo.addOrUpdateDevice(duplicateUpdate)
        assertTrue("Updating IP to an existing one must be rejected", rejectedUpdateResult is com.example.data.network.DeviceSaveResult.IpConflict)
    }

    @Test
    fun testUserOcrPurchaseInvoiceImport() {
        val userOcrJson = """
        {
          "company_info": {
            "name": "السلطان تك للكمبيوتر و الإنترنت",
            "address": "الروضة - تقاطع شارع 24 مع شارع الأربعين",
            "tax_number": "0"
          },
          "invoice_info": {
            "title": "فاتورة مبيعات - نقدية",
            "invoice_number": "1811",
            "date": "18/09/2026",
            "time": "07:04 م",
            "currency": "دولار $"
          },
          "customer_info": {
            "name": "مصطفى حسان شبكة طلقه نت",
            "phone": "770446040",
            "account_number": null,
            "tax_number": null
          },
          "items": [
            {
              "item_number": 1,
              "name": "قسام وتر بروف 3025سم",
              "unit": "حبة",
              "quantity": 4,
              "unit_price": 6.5,
              "total": 26.00,
              "discount": 0,
              "total_after_discount": 26.0,
              "tax": 0,
              "net": 26.0
            },
            {
              "item_number": 2,
              "name": "الشبح T6_AX1800",
              "unit": "حبة",
              "quantity": 5,
              "unit_price": 19.0,
              "total": 95.0,
              "discount": 2.5,
              "total_after_discount": 92.5,
              "tax": 0,
              "net": 92.5
            },
            {
              "item_number": 3,
              "name": "كرت شبكة تايبسي 21 usp رقم 3621",
              "unit": "حبة",
              "quantity": 1,
              "unit_price": 6.0,
              "total": 6.0,
              "discount": 1.0,
              "total_after_discount": 5.0,
              "tax": 0,
              "net": 5.0
            },
            {
              "item_number": 4,
              "name": "وصلة هارد خارجي UPS3",
              "unit": "حبة",
              "quantity": 1,
              "unit_price": 1.5,
              "total": 1.50,
              "discount": 0,
              "total_after_discount": 1.5,
              "tax": 0,
              "net": 1.5
            },
            {
              "item_number": 5,
              "name": "كيبل ألتراء لينك مع الباور",
              "unit": "حبة",
              "quantity": 152,
              "unit_price": 0.28,
              "total": 42.56,
              "discount": 2.0,
              "total_after_discount": 40.56,
              "tax": 0,
              "net": 40.56
            },
            {
              "item_number": 6,
              "name": "بطارية سوني 9 فولت",
              "unit": "حبة",
              "quantity": 1,
              "unit_price": 3.3,
              "total": 3.30,
              "discount": 0,
              "total_after_discount": 3.3,
              "tax": 0,
              "net": 3.3
            }
          ],
          "summary": {
            "total_amount": 174.36,
            "total_discount": 5.5,
            "tax_percentage": 0,
            "total_tax": 0.00,
            "net_amount": 168.86
          },
          "statement_notes": [
            "100 الف مسلمه نقد",
            "400 سعودي حواله للموحده"
          ]
        }
        """.trimIndent()

        val parsedList = DataJsonHelper.parsePurchasesFromJson(userOcrJson)
        assertEquals("Must parse exactly 1 purchase invoice", 1, parsedList.size)

        val invoice = parsedList[0]
        assertEquals("السلطان تك للكمبيوتر و الإنترنت", invoice.vendorName)
        assertEquals(CurrencyCode.USD, invoice.currencyCode)
        assertEquals(6, invoice.items.size)
        assertTrue(invoice.notes.contains("1811"))
        assertTrue(invoice.notes.contains("18/09/2026"))
        assertTrue(invoice.notes.contains("100 الف مسلمه نقد"))
        assertTrue("Should detect invoice as cash due to title and cash notes", invoice.isCash)

        // Check Items
        assertEquals("قسام وتر بروف 3025سم", invoice.items[0].description)
        assertEquals(4, invoice.items[0].quantity)
        assertEquals(650L, invoice.items[0].unitPriceMinor)

        // Item 2: AX1800 should be detected as probable asset (Router/WiFi6 equipment)
        assertEquals("الشبح T6_AX1800", invoice.items[1].description)
        assertEquals(5, invoice.items[1].quantity)
        assertEquals(1850L, invoice.items[1].unitPriceMinor) // 92.50 net / 5 = 18.50 USD
        assertTrue("AX1800 should be detected as probable network asset", invoice.items[1].isAsset)
        assertEquals("1501", invoice.items[1].accountCode)

        // Item 6: Battery
        assertEquals("بطارية سوني 9 فولت", invoice.items[5].description)
        assertEquals(1, invoice.items[5].quantity)
        assertEquals(330L, invoice.items[5].unitPriceMinor)
    }
}
