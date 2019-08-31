package com.myetherwallet.mewconnect.feature.main.viewmodel

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.myetherwallet.mewconnect.BuildConfig
import com.myetherwallet.mewconnect.MewApplication
import com.myetherwallet.mewconnect.content.data.MessageToSign
import com.myetherwallet.mewconnect.content.data.Network
import com.myetherwallet.mewconnect.content.data.Transaction
import com.myetherwallet.mewconnect.core.persist.prefenreces.PreferencesManager
import com.myetherwallet.mewconnect.core.persist.prefenreces.WalletPreferences
import com.myetherwallet.mewconnect.core.platform.Failure
import com.myetherwallet.mewconnect.feature.main.data.Balance
import com.myetherwallet.mewconnect.feature.main.data.WalletBalance
import com.myetherwallet.mewconnect.feature.main.data.WalletData
import com.myetherwallet.mewconnect.feature.main.data.WalletListItem
import com.myetherwallet.mewconnect.feature.main.interactor.GetAllBalances
import com.myetherwallet.mewconnect.feature.main.interactor.GetTickerData
import com.myetherwallet.mewconnect.feature.main.interactor.GetWalletBalance
import com.myetherwallet.mewconnect.feature.scan.service.ServiceBinder
import com.myetherwallet.mewconnect.feature.scan.service.SocketService
import java.math.BigDecimal
import javax.inject.Inject

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.RequestBody

import okhttp3.Call
import okhttp3.Callback
import org.json.JSONArray
import org.json.JSONObject

import java.io.IOException

/**
 * Created by BArtWell on 28.08.2018.
 */

private const val USD_SYMBOL = "ETH"

class WalletViewModel
@Inject constructor(application: Application, private val getWalletBalance: GetWalletBalance, private val getAllBalances: GetAllBalances, private val getTickerData: GetTickerData) : AndroidViewModel(application) {

    private var serviceConnection: ServiceConnection
    private var service: SocketService? = null

    var walletData: MutableLiveData<WalletData> = MutableLiveData()

    init {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as ServiceBinder<SocketService>).service
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        application.bindService(SocketService.getIntent(application.applicationContext), serviceConnection, 0)
    }

    fun setOnTransactionListener(onTransactionListener: (transaction: Transaction) -> Unit) {
        service?.transactionConfirmListener = { onTransactionListener(it) }
    }

    fun setOnMessageListener(onMessageListener: (message: MessageToSign) -> Unit) {
        service?.messageSignListener = { onMessageListener(it) }
    }

    fun setOnDisconnectListener(onDisconnectListener: (() -> Unit)?) {
        service?.disconnectListener = onDisconnectListener
    }

    fun checkConnected() = service?.isConnected ?: false

    fun disconnect() {
        service?.disconnect()
    }

    override fun onCleared() {
        getApplication<MewApplication>().unbindService(serviceConnection)
        super.onCleared()
    }

    fun loadData(preferences: WalletPreferences, network: Network, walletAddress: String) {
        preferences.getWalletDataCache()?.let {
            it.isFromCache = true
            walletData.postValue(it)
        }
        Collector(network, walletAddress, getWalletBalance, getAllBalances, getTickerData) { items, balance ->
            val data = WalletData(false, items, balance)
            walletData.postValue(data)
            preferences.setWalletDataCache(data)
        }.execute()
    }

    private class Collector(private var network: Network,
                            private var walletAddress: String,
                            private val getWalletBalance: GetWalletBalance,
                            private val getAllBalances: GetAllBalances,
                            private val getTickerData: GetTickerData,
                            private val callback: (List<WalletListItem>, WalletBalance) -> Unit) {

        private var balances: List<Balance>? = null
        private var walletBalance: BigDecimal? = null
        private var tickerData: Map<String, BigDecimal>? = null

        private val client = OkHttpClient()

        fun execute() {
            loadWalletBalance()
            loadAllBalances()
        }

        fun loadWalletBalance() {
            getBalance()
        }

        private fun getBalance(){
            val json = JSONObject()

            val formatedEthereumAddress = "0x" + walletAddress

            json.put("account", formatedEthereumAddress)

            val mediaType = MediaType.parse("application/json; charset=utf-8")

            val formBody = RequestBody.create(mediaType, json.toString())

            val parsedUrl = HttpUrl.parse(BuildConfig.IVOX_API_TOKEN_END_POINT)

            var builtUrl = HttpUrl.Builder()
                                    .scheme(parsedUrl?.scheme())
                                    .host(parsedUrl?.host())
                                    .port(parsedUrl?.port()!!)
                                    .addPathSegment("ethereum")
                                    .addPathSegment("balance")
                                    .build()

            val request = Request.Builder()
                                    .url(builtUrl)
                                    .post(formBody)
                                    .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(cliall: Call, e: IOException) {
                    onWalletBalanceFail()
                }
                override fun onResponse(call: Call, response: Response) {

                    try{
                        var responseData = response.body()?.string()

                        var json = JSONArray(responseData)

                        val response = json.getJSONObject(0)

                        val balanceString = response.getString("BALANCE")

                        val balance = BigDecimal(balanceString)

                        onWalletBalanceSuccess(balance)

                    }catch (e: Exception) {
                        onWalletBalanceFail()
                    }
                }

            })
        }

        private fun onWalletBalanceFail() {
            walletBalance = BigDecimal.ZERO
            collect()
        }

        private fun onWalletBalanceSuccess(result: BigDecimal) {
            walletBalance = result
            collect()
        }

        private fun loadAllBalances() {
            getAllBalances.execute(GetAllBalances.Params(network, walletAddress)) {
                it.either(::onAllBalancesFail, ::onAllBalancesSuccess)
            }
        }

        private fun onAllBalancesFail(failure: Failure) {
            balances = emptyList()
            loadTickerData()
        }

        private fun onAllBalancesSuccess(result: List<Balance>) {
            balances = result
            loadTickerData()
        }

        private fun loadTickerData() {
            val symbols = mutableListOf(USD_SYMBOL)
            balances?.let {
                for (balance in it) {
                    symbols.add(balance.symbol)
                }
            }
            getTicker()
        }

        private fun getTicker(){
            val formBody = FormBody.Builder()
                    .add("convert", "MXN")
                    .build()

            val parsedUrl = HttpUrl.parse(BuildConfig.IVOX_API_TOKEN_END_POINT)

            var builtUrl = HttpUrl.Builder()
                                    .scheme(parsedUrl?.scheme())
                                    .host(parsedUrl?.host())
                                    .port(parsedUrl?.port()!!)
                                    .addPathSegment("ethereum")
                                    .addPathSegment("ticker")
                                    .build()

            val request = Request.Builder()
                                    .url(builtUrl)
                                    .post(formBody)
                                    .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(cliall: Call, e: IOException) {
                    onTickerDataFail()
                }
                override fun onResponse(call: Call, response: Response) {

                    try{
                        var responseData = response.body()?.string()

                        var json = JSONArray(responseData)

                        val response = json.getJSONObject(0)

                        val priceString = response.getString("ETH")

                        val tickerResponse = mapOf(USD_SYMBOL to BigDecimal(priceString))

                        onTickerDataSuccess(tickerResponse)

                    }catch (e: Exception) {
                        onTickerDataFail()
                    }
                }

            })
        }

        private fun onTickerDataFail() {
            tickerData = emptyMap()
            collect()
        }

        private fun onTickerDataSuccess(result: Map<String, BigDecimal>) {
            tickerData = result
            collect()
        }

        private fun collect() {
            if (balances == null || tickerData == null || walletBalance == null) {
                return
            }
            val items = mutableListOf<WalletListItem>()
            for (balance in balances!!) {
                val name = balance.name ?: ""
                val stockPrice = tickerData!![balance.symbol]
                val valueUsd = stockPrice?.multiply(balance.calculateBalance())
                items.add(WalletListItem(name, balance.symbol, balance.calculateBalance(), valueUsd, stockPrice))
            }

            val stockPrice = tickerData!![USD_SYMBOL]
            val valueUsd = stockPrice?.multiply(walletBalance)
            callback(items, WalletBalance(walletBalance!!, valueUsd, stockPrice))
        }
    }
}
