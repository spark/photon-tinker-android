package io.particle.mesh.setup.flow.setupsteps

import io.particle.mesh.common.android.livedata.nonNull
import io.particle.mesh.common.android.livedata.runBlockOnUiThreadAndAwaitUpdate
import io.particle.mesh.setup.flow.DeviceConnector
import io.particle.mesh.setup.flow.FailedToConnectException
import io.particle.mesh.setup.flow.MeshSetupStep
import io.particle.mesh.setup.flow.Scopes
import io.particle.mesh.setup.flow.context.SetupContexts
import io.particle.mesh.setup.flow.FlowUiDelegate
import kotlinx.coroutines.delay


// FIXME: find a good way to merge this with StepConnectToTargetDevice

class StepEnsureCommissionerConnected(
    private val flowUi: FlowUiDelegate,
    private val deviceConnector: DeviceConnector
) : MeshSetupStep() {

    override suspend fun doRunStep(ctxs: SetupContexts, scopes: Scopes) {
        val commissioner = ctxs.commissioner.transceiverLD.value
        if (commissioner != null && commissioner.isConnected) {
            return
        }

        if (!ctxs.ble.connectingToAssistingDeviceUiShown) {
            flowUi.showCommissionerPairingProgressUi()
            ctxs.ble.connectingToAssistingDeviceUiShown = true
        }


        val transceiver = scopes.withMain {
            try {
                deviceConnector.connect(
                    ctxs.commissioner.barcode.value!!,
                    "commissioner",
                    scopes
                )
            } catch (ex: Exception) {
                return@withMain null
            }
        }

        if (transceiver == null) {
            throw FailedToConnectException()
        } else {
            // don't move any further until the value is set on the LiveData
            ctxs.commissioner.transceiverLD
                .nonNull(scopes)
                .runBlockOnUiThreadAndAwaitUpdate(scopes) {
                    ctxs.commissioner.updateDeviceTransceiver(transceiver)
                }
            delay(5000)
        }
    }

}
