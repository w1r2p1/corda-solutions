package net.corda.businessnetworks.membership.member.service

import net.corda.businessnetworks.membership.member.MembershipsListResponse
import net.corda.businessnetworks.membership.states.Membership
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * A singleton service that holds a cache of memberships
 */
@CordaService
class MembershipsCacheHolder(private val serviceHub : ServiceHub) : SingletonSerializeAsToken() {
    var cache : MembershipsCache? = null

    fun resetCache() {
        cache = null
    }
}

class MembershipsCache private constructor(val membershipMap : ConcurrentMap<Party, StateAndRef<Membership.State>>, val expires : Instant?) {
    companion object {
        fun from(membershipResponse : MembershipsListResponse) = MembershipsCache(
                ConcurrentHashMap(membershipResponse.memberships.map { it.state.data.member to it }.toMap()),
                membershipResponse.expires)
    }
    fun getMembership(party : Party) = membershipMap[party]
    fun revokeMembership(revokedMember : Party) { membershipMap.remove(revokedMember) }
    fun updateMembership(changedMembership : StateAndRef<Membership.State>) { membershipMap[changedMembership.state.data.member] = changedMembership }
}