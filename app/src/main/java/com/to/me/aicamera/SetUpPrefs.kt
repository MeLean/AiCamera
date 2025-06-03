package com.to.me.aicamera

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object SetupPrefs {
    private val Context.dataStore by preferencesDataStore("setup_prefs")

    private val KEY_IP = stringPreferencesKey("ip")
    private val KEY_PORT = stringPreferencesKey("port")
    private val KEY_API = stringPreferencesKey("api_endpoint")
    private val KEY_MODEL = stringPreferencesKey("model_endpoint")

    suspend fun save(context: Context, config: SetupConfig) {
        context.dataStore.edit {
            it[KEY_IP] = config.ip
            it[KEY_PORT] = config.port
            it[KEY_API] = config.apiEndpoint
            it[KEY_MODEL] = config.modelEndpoint
        }
    }

    suspend fun clear(context: Context) {
        context.dataStore.edit { it.clear() }
    }

    fun get(context: Context): Flow<SetupConfig?> = context.dataStore.data
        .map { prefs ->
            val ip = prefs[KEY_IP] ?: return@map null
            val port = prefs[KEY_PORT] ?: return@map null
            val api = prefs[KEY_API] ?: return@map null
            val model = prefs[KEY_MODEL] ?: return@map null
            SetupConfig(ip, port, api, model)
        }
}

data class SetupConfig(
    val ip: String = "",
    val port: String = "",
    val apiEndpoint: String = "",
    val modelEndpoint: String = ""
)
