package net.corda.businessnetworks.membership.states

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.time.Instant

class Membership : Contract {
    companion object {
        val CONTRACT_NAME = "net.corda.businessnetworks.membership.states.Membership"
    }

    open class Commands : CommandData, TypeOnlyCommandData() {
        class Request : Commands()
        class Amend : Commands()
        class Revoke : Commands()
        class Activate : Commands()
    }

    override fun verify(tx : LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        val output = tx.outputs.single()
        val outputState = output.data as State

        requireThat {
            "Modified date has to be greater or equal than the issued date" using (outputState.modified >= outputState.issued)
            "Both BNO and member have to be participants" using (outputState.participants.toSet() == setOf(outputState.member, outputState.bno))
            "Output state has to be validated with $CONTRACT_NAME" using (output.contract == CONTRACT_NAME)
            if (!tx.inputs.isEmpty()) {
                val input = tx.inputs.single()
                val inputState = input.state.data as State
                "Participants of input and output states should be the same" using (outputState.participants.toSet() == input.state.data.participants.toSet())
                "Input state has to be validated with $CONTRACT_NAME" using (input.state.contract == CONTRACT_NAME)
                "Input and output states should have the same issued dates" using (inputState.issued == outputState.issued)
                "Input and output states should have the same linear ids" using (inputState.linearId == outputState.linearId)
                "Output state's modified timestamp should be greater than input's" using (outputState.modified > inputState.modified)
            }
        }

        when (command.value) {
            is Commands.Request -> requireThat {
                "Both BNO and member have to sign a membership request transaction" using (command.signers.toSet() == outputState.participants.map { it.owningKey }.toSet() )
                "Membership request transaction shouldn't contain any inputs" using (tx.inputs.isEmpty())
                "Membership request transaction should contain an output state in PENDING status" using (outputState.isPending())
            }
            is Commands.Revoke -> requireThat {
                val inputStateAndRef = tx.inputs.single()
                val inputState = inputStateAndRef.state.data as State
                "Only BNO should sign a revocation transaction" using (command.signers.toSet() == setOf(outputState.bno.owningKey))
                "Input state of a revocation transaction shouldn't be already revoked" using (!inputState.isRevoked())
                "Output state of a revocation transaction should be revoked" using (outputState.isRevoked())
                "Input and output states of a revocation transaction should have the same metadata" using (inputState.membershipMetadata == outputState.membershipMetadata)
            }
            is Commands.Activate -> requireThat {
                val inputStateAndRef = tx.inputs.single()
                val inputState = inputStateAndRef.state.data as State
                "Only BNO should sign a membership activation transaction" using (command.signers.toSet() == setOf(outputState.bno.owningKey))
                "Input state of a membership activation transaction shouldn't be already active" using (!inputState.isActive())
                "Output state of a membership activation transaction should be active" using (outputState.isActive())
                "Input and output states of a membership activation transaction should have the same metadata" using (inputState.membershipMetadata == outputState.membershipMetadata)
            }
            is Commands.Amend -> requireThat {
                val inputStateAndRef = tx.inputs.single()
                val inputState = inputStateAndRef.state.data as State
                "Both BNO and member have to sign a metadata amendment transaction" using (command.signers.toSet() == outputState.participants.map { it.owningKey }.toSet() )
                "Both input and output states of a metadata amendment transaction should be active" using (inputState.isActive() && outputState.isActive())
                "Input and output states of an amendment transaction should have different membership metadata" using (inputState.membershipMetadata != outputState.membershipMetadata)
            }
            else -> throw IllegalArgumentException("Unsupported command ${command.value}")
        }
    }


    data class State(val member : Party,
                     val bno : Party,
                     val membershipMetadata : MembershipMetadata = MembershipMetadata(),
                     val issued : Instant = Instant.now(),
                     val modified : Instant = issued,
                     val status : MembershipStatus = MembershipStatus.PENDING,
                     override val linearId : UniqueIdentifier = UniqueIdentifier()) : LinearState, QueryableState {
        override fun generateMappedObject(schema : MappedSchema) : PersistentState {
            return when (schema) {
                is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                        member = this.member,
                        status = this.status
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }
        override fun supportedSchemas() = listOf(MembershipStateSchemaV1)
        override val participants = listOf(bno, member)
        fun isRevoked() = status == MembershipStatus.REVOKED
        fun isPending() = status == MembershipStatus.PENDING
        fun isActive() = status == MembershipStatus.ACTIVE
    }
}

@CordaSerializable
data class MembershipMetadata(
        val role : String = "",
        val alternativeName : String? = null
        // add more fields here
)

@CordaSerializable
enum class MembershipStatus {
    PENDING, ACTIVE, REVOKED
}