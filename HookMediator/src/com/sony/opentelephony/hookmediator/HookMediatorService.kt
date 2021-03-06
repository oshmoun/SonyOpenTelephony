/*
 * Copyright (C) 2020 Marijn Suijten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sony.opentelephony.hookmediator

import android.app.Service
import android.content.Intent
import android.util.Log
import vendor.qti.hardware.radio.qcrilhook.V1_0.RadioError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "HookMediatorService"

typealias ResponseData = ArrayList<Byte>

private fun prepareBuffer(request: Int, requestSize: Int): ByteBuffer {
    val buf = ByteBuffer.allocate(OEM_IDENTIFIER.length
            + Int.SIZE_BYTES * 2
            + requestSize)
    buf.order(ByteOrder.nativeOrder())
    buf.put(OEM_IDENTIFIER.toByteArray(Charsets.US_ASCII))
    buf.putInt(request)
    buf.putInt(requestSize)
    return buf
}

class HookMediatorService : Service() {
    private val responseHandler = object : IGenericOemHookResponse {
        override fun onResponse(requestId: Int, error: Int, data: ResponseData) {
            val fut = requests.remove(requestId)
            if (fut == null) {
                Log.w(TAG, "No client for request $requestId")
                return
            }

            if (error != RadioError.NONE) {
                Log.e(TAG, "TODO: Should complete $fut with exception ${RadioError.toString(error)}")
                // fut.completeExceptionally()
            }

            fut.complete(data)
        }
    }
    private val oemHook by lazy { getOemHook(responseHandler) }
    private var sNextSerial = AtomicInteger(0)
    private val requests = ConcurrentHashMap<Int, CompletableFuture<ResponseData>>()

    private val binder = object : IHooks.Stub() {
        override fun setTransmitPower(key: Int, value: Int) {
            Log.d(TAG, "TransmitPower request $key = $value")
            val buf = prepareBuffer(OEMHOOK_EVT_HOOK_SET_TRANSMIT_POWER, Int.SIZE_BYTES * 2)
            buf.putInt(key)
            buf.putInt(value)
            sendRequest(buf)
            // TODO: Block and/or return anything?
        }

        override fun sendCommand(command: ByteArray?): ByteArray? {
            if (command == null) {
                Log.e(TAG, "Command is null!")
                return null
            }
            return sendRequest(command).get().toByteArray()
        }
    }

    override fun onBind(intent: Intent?) = binder

    private fun sendRequest(data: ByteBuffer) = sendRequest(data.array())

    private fun sendRequest(data: ByteArray) = sendRequest(ResponseData().also { data.toCollection(it) })

    private fun sendRequest(data: ResponseData): CompletableFuture<ResponseData> {
        val requestId = sNextSerial.getAndUpdate { (it + 1) % Integer.MAX_VALUE }
        val fut = CompletableFuture<ResponseData>()
        requests[requestId] = fut
        oemHook.sendCommand(requestId, data)
        return fut
    }
}
