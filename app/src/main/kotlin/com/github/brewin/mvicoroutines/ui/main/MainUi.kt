package com.github.brewin.mvicoroutines.ui.main

import android.net.Uri
import android.os.Parcelable
import androidx.net.toUri
import com.github.brewin.mvicoroutines.data.GitHubRepos
import com.github.brewin.mvicoroutines.data.Repository
import com.github.brewin.mvicoroutines.ui.base.Ui
import com.github.brewin.mvicoroutines.ui.base.UiAction
import com.github.brewin.mvicoroutines.ui.base.UiResult
import com.github.brewin.mvicoroutines.ui.base.UiState
import kotlinx.android.parcel.Parcelize
import timber.log.Timber
import java.io.IOException

sealed class MainUiAction : UiAction {
    object UiEnter : MainUiAction()
    object UiExit : MainUiAction()
    object InProgress : MainUiAction()
    data class Search(val query: String) : MainUiAction()
    object Refresh : MainUiAction()
}

sealed class MainUiResult : UiResult {
    object InProgress : MainUiResult()
    data class GotRepos(val query: String, val repos: GitHubRepos) : MainUiResult()
    data class GotError(val query: String, val error: Throwable) : MainUiResult()
}

@Parcelize
data class MainUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val repoList: List<ReposItem> = emptyList(),
    val error: Throwable? = null
) : UiState, Parcelable

@Parcelize
data class ReposItem(val name: String, val url: Uri) : Parcelable

class MainUi(
    private val repository: Repository,
    initialState: MainUiState = MainUiState()
) : Ui<MainUiAction, MainUiResult, MainUiState>(initialState) {

    override suspend fun resultFromAction(action: MainUiAction): MainUiResult = when (action) {
        is MainUiAction.UiEnter -> TODO()
        is MainUiAction.UiExit -> TODO()
        is MainUiAction.InProgress -> MainUiResult.InProgress
        is MainUiAction.Search -> search(action.query)
        is MainUiAction.Refresh -> refresh()
    }.also { Timber.d("Action:\n$action") }

    override suspend fun stateFromResult(result: MainUiResult): MainUiState = lastState().run {
        when (result) {
            is MainUiResult.InProgress -> copy(
                isLoading = true
            )
            is MainUiResult.GotRepos -> copy(
                query = result.query,
                isLoading = false,
                repoList = mapToUi(result.repos)
            )
            is MainUiResult.GotError -> copy(
                query = result.query,
                isLoading = false,
                repoList = emptyList(),
                error = result.error
            )
        }.also { Timber.d("Result:\n$result") }
    }

    fun offerActionWithProgress(action: MainUiAction) {
        offerAction(MainUiAction.InProgress)
        offerAction(action)
    }

    private suspend fun search(query: String): MainUiResult = if (query.isNotBlank()) {
        repository.searchRepos(query).run {
            val body = body()
            if (isSuccessful && body != null) {
                if (body.items != null && body.items.isNotEmpty()) {
                    MainUiResult.GotRepos(query, body)
                } else {
                    MainUiResult.GotError(query, IOException("No results found for: $query"))
                }
            } else {
                MainUiResult.GotError(query, IOException(message()))
            }
        }
    } else {
        MainUiResult.GotError(query, IllegalArgumentException("Enter a search term"))
    }

    private suspend fun refresh(): MainUiResult = search(lastState().query)

    private fun mapToUi(result: GitHubRepos): List<ReposItem> = result.items.orEmpty()
        .filterNot { it.name.isNullOrBlank() || it.htmlUrl.isNullOrBlank() }
        .map { ReposItem(it.name!!, it.htmlUrl!!.toUri()) }
}
