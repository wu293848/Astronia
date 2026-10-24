package com.antoniegil.astronia.data.xtream

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores provider profiles as AES-GCM ciphertext using an Android Keystore key. */
class ProviderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("provider_profiles_v1", Context.MODE_PRIVATE)
    private val keyAlias = "astronia_provider_profiles_v1"

    @Synchronized fun load(): List<ProviderProfile> = runCatching {
        val encoded = prefs.getString("profiles", null) ?: return emptyList()
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "Invalid encrypted profile data" }
        val iv = packed.copyOfRange(0, 12)
        val encrypted = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        val array = JSONArray(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ProviderProfile(
                    id = item.getString("id"), name = item.getString("name"),
                    type = ProviderType.valueOf(item.getString("type")),
                    url = item.getString("url"), username = item.optString("username"),
                    password = item.optString("password")
                ))
            }
        }
    }.getOrElse { emptyList() }

    @Synchronized fun save(profiles: List<ProviderProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject().apply {
                put("id", profile.id); put("name", profile.name); put("type", profile.type.name)
                put("url", profile.url); put("username", profile.username); put("password", profile.password)
            })
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(array.toString().toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString("profiles", Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not save provider profiles"
        }
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
