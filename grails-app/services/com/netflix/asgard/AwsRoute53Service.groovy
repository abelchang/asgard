/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.asgard

import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.GetHostedZoneRequest
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesRequest
import com.amazonaws.services.route53.model.ListHostedZonesResult
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest
import com.amazonaws.services.route53.model.ListResourceRecordSetsResult
import com.amazonaws.services.route53.model.NoSuchHostedZoneException
import com.amazonaws.services.route53.model.ResourceRecordSet
import com.netflix.asgard.cache.CacheInitializer
import com.netflix.asgard.retriever.AwsResultsRetriever
import org.springframework.beans.factory.InitializingBean

/**
 * Interactions with Amazon's Route53 DNS service.
 */
class AwsRoute53Service implements CacheInitializer, InitializingBean {

    static transactional = false

    AmazonRoute53 awsClient
    def awsClientService
    Caches caches

    void afterPropertiesSet() {

        // Route53 only has one endpoint.
        awsClient = awsClient ?: awsClientService.create(AmazonRoute53)
    }

    void initializeCaches() {
        caches.allHostedZones.ensureSetUp({ Region region -> retrieveHostedZones() })
    }

    List<HostedZone> getHostedZones() {
        caches.allHostedZones.list().sort { it.name }
    }

    HostedZone getHostedZone(String idOrName) {
        // Hosted zone names always contain dots, and hosted zone IDs never contain dots.
        // Users may request "test.company.net" for a hosted zone with name "test.company.net."
        String id = idOrName.contains('.') ? (zoneByName(idOrName) ?: zoneByName("${idOrName}."))?.id : idOrName
        if (!id) { return null }
        try {
            HostedZone hostedZone = awsClient.getHostedZone(new GetHostedZoneRequest(id: id)).hostedZone
            return caches.allHostedZones.put(id, hostedZone)
        } catch (NoSuchHostedZoneException ignored) {
            return null
        }
    }

    private HostedZone zoneByName(String name) {
        caches.allHostedZones.list().find { it.name == name }
    }

    List<HostedZone> retrieveHostedZones() {
        AwsResultsRetriever retriever = new AwsResultsRetriever<HostedZone, ListHostedZonesRequest, ListHostedZonesResult>() {

            ListHostedZonesResult makeRequest(Region region, ListHostedZonesRequest request) {
                awsClient.listHostedZones(request)
            }

            List<HostedZone> accessResult(ListHostedZonesResult result) {
                result.hostedZones
            }

            protected void setNextToken(ListHostedZonesRequest request, String nextToken) {
                request.withMarker(nextToken)
            }

            protected String getNextToken(ListHostedZonesResult result) {
                result.nextMarker
            }
        }
        retriever.retrieve(Region.defaultRegion(), new ListHostedZonesRequest())
    }

    AwsResultsRetriever resourceRecordSetRetriever = new AwsResultsRetriever<ResourceRecordSet,
            ListResourceRecordSetsRequest, ListResourceRecordSetsResult>() {

        ListResourceRecordSetsResult makeRequest(Region region, ListResourceRecordSetsRequest request) {
            awsClient.listResourceRecordSets(request)
        }

        List<ResourceRecordSet> accessResult(ListResourceRecordSetsResult result) {
            result.resourceRecordSets
        }

        protected void setNextToken(ListResourceRecordSetsRequest request, String nextToken) {
            request.withStartRecordIdentifier(nextToken)
        }

        protected String getNextToken(ListResourceRecordSetsResult result) {
            result.nextRecordIdentifier
        }
    }

    List<ResourceRecordSet> getResourceRecordSetsForHostedZone(String hostedZoneId) {
        ListResourceRecordSetsRequest request = new ListResourceRecordSetsRequest(hostedZoneId: hostedZoneId)
        resourceRecordSetRetriever.retrieve(Region.defaultRegion(), request)
    }
}
