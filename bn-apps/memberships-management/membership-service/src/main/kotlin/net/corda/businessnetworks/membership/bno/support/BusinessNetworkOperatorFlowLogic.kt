package net.corda.businessnetworks.membership.bno.support

import net.corda.businessnetworks.membership.bno.service.DatabaseService
import net.corda.businessnetworks.membership.MembershipNotFound
import net.corda.businessnetworks.membership.NotBNOException
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

/**
 * Extend from this class if you are a business network operator and you want to make your life easier when writing
 * flows by getting access to the useful methods in this class.
 */
abstract class BusinessNetworkOperatorFlowLogic<out T> : FlowLogic<T>() {

    protected fun verifyThatWeAreBNO(membership : Membership.State) {
        if(ourIdentity != membership.bno) {
            throw NotBNOException(membership)
        }
    }
    protected fun findMembershipStateForParty(party : Party) : StateAndRef<Membership.State> {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        return databaseService.getMembership(party) ?: throw MembershipNotFound(party)
    }

    protected fun getActiveMembershipStates() : List<StateAndRef<Membership.State>> {
        val databaseService = serviceHub.cordaService(DatabaseService::class.java)
        return databaseService.getActiveMemberships()
    }

}