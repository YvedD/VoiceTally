package com.yvesds.vt5.core.database.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yvesds.vt5.core.database.entities.Waarneming
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import kotlinx.coroutines.flow.Flow

class DatabaseSoortOverzichtViewModel(private val database: VoiceTallyDatabase) : ViewModel() {

    fun getWaarnemingenPager(soortId: String, year: String?): Flow<PagingData<Waarneming>> {
        val pagingSourceFactory = { database.tellingDao().getWaarnemingenPagingSource(soortId, year) }
        // Use centralized page size constant to keep behaviour consistent across the app
        val pageSize = PagingConstants.DEFAULT_PAGE_SIZE
        val pagingConfig = PagingConfig(
            pageSize = pageSize,
            initialLoadSize = pageSize,
            prefetchDistance = 100,
            enablePlaceholders = false,
            maxSize = pageSize * 3
        )

        return Pager(pagingConfig) {
            pagingSourceFactory()
        }.flow.cachedIn(viewModelScope)
    }
}

