package io.particle.mesh.setup.flow.setupsteps

import io.particle.mesh.common.Result
import io.particle.mesh.common.android.livedata.nonNull
import io.particle.mesh.common.android.livedata.runBlockOnUiThreadAndAwaitUpdate
import io.particle.mesh.common.truthy
import io.particle.mesh.setup.flow.*
import io.particle.mesh.setup.flow.DialogSpec.StringDialogSpec
import io.particle.mesh.setup.flow.context.SetupContexts
import mu.KotlinLogging


class StepCollectMeshNetworkToJoinPassword(private val flowUi: FlowUiDelegate) : MeshSetupStep() {

    private val log = KotlinLogging.logger {}

    override suspend fun doRunStep(ctxs: SetupContexts, scopes: Scopes) {
        if (ctxs.mesh.meshNetworkToJoinCommissionerPassword.value.truthy()) {
            return
        }

        val passwordLd = ctxs.mesh.meshNetworkToJoinCommissionerPassword
        val password = passwordLd.nonNull(scopes).runBlockOnUiThreadAndAwaitUpdate(scopes) {
            if (!ctxs.mesh.shownNetworkPasswordUi) {
                flowUi.collectPasswordForMeshToJoin()
                ctxs.mesh.shownNetworkPasswordUi = true
            }
        }

        if (password == null) {
            throw MeshSetupFlowException(userFacingMessage = "Error while getting mesh network password")
        }

        flowUi.showGlobalProgressSpinner(true)
        val commissioner = ctxs.requireCommissionerXceiver()
        val sendAuthResult = commissioner.sendAuth(password)
        when (sendAuthResult) {
            is Result.Present -> return
            is Result.Error,
            is Result.Absent -> {

                ctxs.mesh.updateTargetDeviceMeshNetworkToJoinCommissionerPassword(null)

                val error = CommissionerNetworkDoesNotMatchException()

                val result = flowUi.dialogTool.dialogResultLD
                    .nonNull(scopes)
                    .runBlockOnUiThreadAndAwaitUpdate(scopes) {
                        flowUi.dialogTool.newDialogRequest(
                            StringDialogSpec(error.userFacingMessage!!)
                        )
                    }

                log.info { "result from awaiting on 'commissioner not on network to be joined' dialog: $result" }
                flowUi.dialogTool.clearDialogResult()

                throw error
            }
        }
    }

}
