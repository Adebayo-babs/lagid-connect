package com.lagidconnect.wallet

import android.app.Application
import com.lagidconnect.wallet.data.DeviceKeys
import com.lagidconnect.wallet.data.Session
import com.lagidconnect.wallet.data.WalletApi
import com.lagidconnect.wallet.face.FaceConfig
import com.lagidconnect.wallet.face.FaceEmbedder
import com.lagidconnect.wallet.face.FaceProcessor

class WalletApp : Application() {

    lateinit var session: Session
        private set
    lateinit var keys: DeviceKeys
        private set
    lateinit var api: WalletApi
        private set

    /** Face engine from the FaceMatch app, loaded on first use. */
    val faceProcessor: FaceProcessor by lazy {
        FaceProcessor(FaceEmbedder(this, FaceConfig.MODEL_ASSET))
    }

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        keys = DeviceKeys()
        api = WalletApi(BuildConfig.API_BASE_URL, keys, session)
    }
}
