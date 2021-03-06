package net.corda.businessnetworks.membership.bno

import co.paralleluniverse.fibers.Suspendable
import net.corda.businessnetworks.membership.bno.service.BNOConfigurationService
import net.corda.businessnetworks.membership.bno.support.BusinessNetworkOperatorFlowLogic
import net.corda.businessnetworks.membership.states.Membership
import net.corda.businessnetworks.membership.states.MembershipStatus
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * The flow changes status of a PENDING or REVOKED membership to ACTIVE. The flow can be started only by BNO. BNO can unilaterally
 * activate membershipMap and no member's signature is required. After the membership is activated, the flow
 * fires-and-forgets [OnMembershipChanged] notification to the business network members.
 *
 * @param membership membership state to be activated
 */
@InitiatingFlow
@StartableByRPC
class ActivateMembershipFlow(val membership : StateAndRef<Membership.State>) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call() : SignedTransaction {
        verifyThatWeAreBNO(membership.state.data)

        val configuration = serviceHub.cordaService(BNOConfigurationService::class.java)

        // create membership activation transaction
        val notary = configuration.notaryParty()
        val builder = TransactionBuilder(notary)
                .addInputState(membership)
                .addOutputState(membership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant()), Membership.CONTRACT_NAME)
                .addCommand(Membership.Commands.Activate(), ourIdentity.owningKey)
        builder.verify(serviceHub)
        val selfSignedTx = serviceHub.signInitialTransaction(builder)
        val stx = subFlow(FinalityFlow(selfSignedTx))

        // if notifications are enabled - notify BN about the new joiner
        if (configuration.areNotificationEnabled()) {
            subFlow(NotifyActiveMembersFlow(OnMembershipActivated(membership)))
        }

        return stx
    }
}

/**
 * This is a convenience flow that can be easily used from command line
 *
 * @param party whose membership state to be activated
 */
@InitiatingFlow
@StartableByRPC
class ActivateMembershipForPartyFlow(val party : Party) : BusinessNetworkOperatorFlowLogic<SignedTransaction>() {

    companion object {
        object LOOKING_FOR_MEMBERSHIP_STATE : ProgressTracker.Step("Looking for party's membership state")
        object ACTIVATING_THE_MEMBERSHIP_STATE : ProgressTracker.Step("Activating the membership state")

        fun tracker() = ProgressTracker(
                LOOKING_FOR_MEMBERSHIP_STATE,
                ACTIVATING_THE_MEMBERSHIP_STATE
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() : SignedTransaction {
        progressTracker.currentStep = LOOKING_FOR_MEMBERSHIP_STATE
        val stateToActivate = findMembershipStateForParty(party)

        progressTracker.currentStep = ACTIVATING_THE_MEMBERSHIP_STATE
        return subFlow(ActivateMembershipFlow(stateToActivate))
    }

}