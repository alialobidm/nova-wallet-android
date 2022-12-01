package io.novafoundation.nova.feature_governance_impl.data.repository.v2

import io.novafoundation.nova.common.utils.formatting.parseDateISO_8601
import io.novafoundation.nova.feature_governance_api.data.network.blockhain.model.ReferendumId
import io.novafoundation.nova.feature_governance_api.data.network.offchain.model.OffChainReferendumDetails
import io.novafoundation.nova.feature_governance_api.data.network.offchain.model.OffChainReferendumPreview
import io.novafoundation.nova.feature_governance_api.data.repository.OffChainReferendaInfoRepository
import io.novafoundation.nova.feature_governance_api.domain.referendum.details.ReferendumTimeline
import io.novafoundation.nova.feature_governance_impl.data.offchain.v2.PolkassemblyV2Api
import io.novafoundation.nova.feature_governance_impl.data.offchain.v2.request.ReferendumDetailsV2Request
import io.novafoundation.nova.feature_governance_impl.data.offchain.v2.request.ReferendumPreviewV2Request
import io.novafoundation.nova.feature_governance_impl.data.offchain.v2.response.ReferendaPreviewV2Response
import io.novafoundation.nova.feature_governance_impl.data.offchain.v2.response.ReferendumDetailsV2Response
import io.novafoundation.nova.runtime.multiNetwork.chain.model.Chain

class Gov2OffChainReferendaInfoRepository(
    private val polkassemblyApi: PolkassemblyV2Api
) : OffChainReferendaInfoRepository {

    override suspend fun referendumPreviews(chain: Chain): List<OffChainReferendumPreview> {
        return runCatching {
            val url = chain.requirePolkassemblyApiUrl()
            val request = ReferendumPreviewV2Request()
            val response = polkassemblyApi.getReferendumPreviews(url, request)

            response.data.posts.map(::mapPolkassemblyPostToPreview)
        }.getOrDefault(emptyList())
    }

    override suspend fun referendumDetails(referendumId: ReferendumId, chain: Chain): OffChainReferendumDetails? {
        return runCatching {
            val url = chain.requirePolkassemblyApiUrl()
            val request = ReferendumDetailsV2Request(referendumId.value)
            val response = polkassemblyApi.getReferendumDetails(url, request)
            val referendumDetails = response.data.posts.firstOrNull()

            referendumDetails?.let(::mapPolkassemblyPostToDetails)
        }.getOrNull()
    }

    private fun mapPolkassemblyPostToPreview(post: ReferendaPreviewV2Response.Post): OffChainReferendumPreview {
        return OffChainReferendumPreview(
            title = post.title,
            referendumId = ReferendumId(post.onChainLink.onChainReferendumId),
        )
    }

    private fun mapPolkassemblyPostToDetails(post: ReferendumDetailsV2Response.Post): OffChainReferendumDetails {
        val timeline = post.onchainLink.onchainReferendum
            .firstOrNull()
            ?.referendumStatus
            ?.mapNotNull {
                mapReferendumStatusToTimelineEntry(it)
            }

        return OffChainReferendumDetails(
            title = post.title,
            description = post.content,
            proposerName = post.author.username,
            pastTimeline = timeline
        )
    }

    private fun mapReferendumStatusToTimelineEntry(status: ReferendumDetailsV2Response.Status): ReferendumTimeline.Entry? {
        val timelineState = when (status.status) {
            "Ongoing" -> ReferendumTimeline.State.CREATED
            "Approved" -> ReferendumTimeline.State.APPROVED
            "Rejected" -> ReferendumTimeline.State.REJECTED
            "Cancelled" -> ReferendumTimeline.State.CANCELLED
            "TimedOut" -> ReferendumTimeline.State.TIMED_OUT
            "Killed" -> ReferendumTimeline.State.KILLED
            "Executed" -> ReferendumTimeline.State.EXECUTED
            else -> null
        }

        val statusDate = parseDateISO_8601(status.blockNumber.startDateTime)

        return timelineState?.let {
            ReferendumTimeline.Entry(timelineState, statusDate?.time)
        }
    }

    private fun Chain.requirePolkassemblyApiUrl(): String {
        val governanceExternalApi = externalApi!!.governance!!
        require(governanceExternalApi.type == Chain.ExternalApi.Section.Type.POLKASSEMBLY)

        return governanceExternalApi.url
    }
}