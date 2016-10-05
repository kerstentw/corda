package com.r3corda.node.services

import com.r3corda.core.crypto.SecureHash
import com.r3corda.core.node.services.UniquenessException
import com.r3corda.core.utilities.LogHelper
import com.r3corda.node.services.transactions.PersistentUniquenessProvider
import com.r3corda.node.utilities.configureDatabase
import com.r3corda.node.utilities.databaseTransaction
import com.r3corda.testing.MEGA_CORP
import com.r3corda.testing.generateStateRef
import com.r3corda.testing.node.makeTestDataSourceProperties
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.Closeable
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PersistentUniquenessProviderTests {
    val identity = MEGA_CORP
    val txID = SecureHash.randomSHA256()

    lateinit var dataSource: Closeable
    lateinit var database: Database

    @Before
    fun setUp() {
        LogHelper.setLevel(PersistentUniquenessProvider::class)
        val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
        dataSource = dataSourceAndDatabase.first
        database = dataSourceAndDatabase.second
    }

    @After
    fun tearDown() {
        dataSource.close()
        LogHelper.reset(PersistentUniquenessProvider::class)
    }

    @Test fun `should commit a transaction with unused inputs without exception`() {
        databaseTransaction(database) {
            val provider = PersistentUniquenessProvider()
            val inputState = generateStateRef()

            provider.commit(listOf(inputState), txID, identity)
        }
    }

    @Test fun `should report a conflict for a transaction with previously used inputs`() {
        databaseTransaction(database) {
            val provider = PersistentUniquenessProvider()
            val inputState = generateStateRef()

            val inputs = listOf(inputState)
            provider.commit(inputs, txID, identity)

            val ex = assertFailsWith<UniquenessException> { provider.commit(inputs, txID, identity) }

            val consumingTx = ex.error.stateHistory[inputState]!!
            assertEquals(consumingTx.id, txID)
            assertEquals(consumingTx.inputIndex, inputs.indexOf(inputState))
            assertEquals(consumingTx.requestingParty, identity)
        }
    }
}