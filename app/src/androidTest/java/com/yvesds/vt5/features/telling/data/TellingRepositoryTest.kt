package com.yvesds.vt5.features.telling.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yvesds.vt5.core.database.VT5Database
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.core.database.entity.TellingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests para TellingRepository.
 * Verifica operaciones CRUD y sincronización de datos.
 */
class TellingRepositoryTest {

    private lateinit var database: VT5Database
    private lateinit var repository: TellingRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, VT5Database::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TellingRepository(database.tellingDao(), context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveTelling() = runTest {
        val telling = TellingEntity(
            id = "telling-001",
            telpostId = "telpost-001",
            begintijd = System.currentTimeMillis() / 1000,
            eindtijd = 0L,
            createdAt = System.currentTimeMillis(),
            opmerkingen = "Test session"
        )

        database.tellingDao().insertTelling(telling)

        val retrieved = database.tellingDao().getTelling("telling-001")
        assertNotNull(retrieved)
        assertEquals("telling-001", retrieved?.id)
        assertEquals("telpost-001", retrieved?.telpostId)
    }

    @Test
    fun testInsertObservations() = runTest {
        val telling = TellingEntity(
            id = "telling-002",
            telpostId = "telpost-002",
            begintijd = System.currentTimeMillis() / 1000,
            eindtijd = 0L,
            createdAt = System.currentTimeMillis(),
            opmerkingen = ""
        )
        database.tellingDao().insertTelling(telling)

        val obs1 = ObservationEntity(
            id = "obs-001",
            tellingId = "telling-002",
            speciesId = "species-001",
            count = 5,
            direction = "N",
            timestamp = System.currentTimeMillis() / 1000,
            notes = "Flying north"
        )
        val obs2 = ObservationEntity(
            id = "obs-002",
            tellingId = "telling-002",
            speciesId = "species-002",
            count = 3,
            direction = "S",
            timestamp = System.currentTimeMillis() / 1000,
            notes = "Flying south"
        )

        database.tellingDao().insertObservation(obs1)
        database.tellingDao().insertObservation(obs2)

        val observations = database.tellingDao().getObservations("telling-002")
        assertEquals(2, observations.size)
        assertEquals("species-001", observations[0].speciesId)
        assertEquals(5, observations[0].count)
    }

    @Test
    fun testUpdateTelling() = runTest {
        val telling = TellingEntity(
            id = "telling-003",
            telpostId = "telpost-003",
            begintijd = System.currentTimeMillis() / 1000,
            eindtijd = 0L,
            createdAt = System.currentTimeMillis(),
            isUploaded = false
        )
        database.tellingDao().insertTelling(telling)

        val updated = telling.copy(
            isUploaded = true,
            uploadedAt = System.currentTimeMillis()
        )
        database.tellingDao().updateTelling(updated)

        val retrieved = database.tellingDao().getTelling("telling-003")
        assertNotNull(retrieved)
        assertTrue(retrieved?.isUploaded == true)
    }

    @Test
    fun testGetPendingTellings() = runTest {
        // Insert pending telling
        val pending = TellingEntity(
            id = "telling-pending",
            telpostId = "telpost-001",
            begintijd = System.currentTimeMillis() / 1000,
            eindtijd = 0L,
            createdAt = System.currentTimeMillis(),
            isUploaded = false
        )
        database.tellingDao().insertTelling(pending)

        // Insert uploaded telling
        val uploaded = TellingEntity(
            id = "telling-uploaded",
            telpostId = "telpost-002",
            begintijd = System.currentTimeMillis() / 1000,
            eindtijd = 0L,
            createdAt = System.currentTimeMillis(),
            isUploaded = true,
            uploadedAt = System.currentTimeMillis()
        )
        database.tellingDao().insertTelling(uploaded)

        // Query pending
        val pendingTellings = database.tellingDao().getPendingTellings().first()
        assertEquals(1, pendingTellings.size)
        assertEquals("telling-pending", pendingTellings[0].id)
    }

    @Test
    fun testTransactionInsertTellingWithObservations() = runTest {
        val telling = TellingEntity(
            id = "telling-004",
            telpostId = "telpost-004",
            begintijd = System.currentTimeMillis() / 1000,
            eindtijd = 0L,
            createdAt = System.currentTimeMillis()
        )

        val observations = listOf(
            ObservationEntity(
                id = "obs-003",
                tellingId = "telling-004",
                speciesId = "species-001",
                count = 10,
                timestamp = System.currentTimeMillis() / 1000
            ),
            ObservationEntity(
                id = "obs-004",
                tellingId = "telling-004",
                speciesId = "species-002",
                count = 5,
                timestamp = System.currentTimeMillis() / 1000
            )
        )

        database.tellingDao().insertTellingWithObservations(telling, observations)

        val retrieved = database.tellingDao().getTelling("telling-004")
        assertNotNull(retrieved)

        val retrievedObs = database.tellingDao().getObservations("telling-004")
        assertEquals(2, retrievedObs.size)
    }
}

