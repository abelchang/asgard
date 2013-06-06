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
import com.amazonaws.services.route53.model.Change
import com.amazonaws.services.route53.model.ChangeAction
import com.amazonaws.services.route53.model.ChangeBatch
import com.amazonaws.services.route53.model.ChangeInfo
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest
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
import grails.util.GrailsNameUtils
import org.springframework.beans.factory.InitializingBean

/**
 * Interactions with Amazon's Route53 DNS service.
 */
class AwsRoute53Service implements CacheInitializer, InitializingBean {

    static transactional = false

    AmazonRoute53 awsClient
    def awsClientService
    Caches caches
    def taskService

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
            request.withStartRecordName(nextToken)
        }

        protected String getNextToken(ListResourceRecordSetsResult result) {
            result.nextRecordName
        }
    }

    List<ResourceRecordSet> getResourceRecordSetsForHostedZone(String hostedZoneId) {
        ListResourceRecordSetsRequest request = new ListResourceRecordSetsRequest(hostedZoneId: hostedZoneId)
        resourceRecordSetRetriever.retrieve(Region.defaultRegion(), request)
    }

    ChangeInfo createResourceRecordSet(UserContext userContext, String hostedZoneId,
                                       ResourceRecordSet resourceRecordSet, String comment) {
        taskService.runTask(userContext, "Create Resource Record ${resourceRecordSet.name}", {
            Change change = new Change(action: ChangeAction.CREATE, resourceRecordSet: resourceRecordSet)
            changeResourceRecordSet(userContext, hostedZoneId, change, comment)
        }, Link.to(EntityType.hostedZone, hostedZoneId)) as ChangeInfo
    }

    ChangeInfo deleteResourceRecordSet(UserContext userContext, String hostedZoneId,
                                       ResourceRecordSet resourceRecordSet, String comment) {
        taskService.runTask(userContext, "Delete Resource Record ${resourceRecordSet.name}", {
            Change change = new Change(action: ChangeAction.DELETE, resourceRecordSet: resourceRecordSet)
            changeResourceRecordSet(userContext, hostedZoneId, change, comment)
        }, Link.to(EntityType.hostedZone, hostedZoneId)) as ChangeInfo
    }

    ChangeInfo changeResourceRecordSet(UserContext userContext, String hostedZoneId, Change change, String comment) {
        ChangeBatch changeBatch = new ChangeBatch(comment: comment, changes: [change])
        def request = new ChangeResourceRecordSetsRequest(hostedZoneId: hostedZoneId, changeBatch: changeBatch)
        String action = GrailsNameUtils.getNaturalName(change.action.toLowerCase())
        String msg = "${action} Resource Record ${change.resourceRecordSet.name}"
        taskService.runTask(userContext, msg, {
            awsClient.changeResourceRecordSets(request).changeInfo
        }, Link.to(EntityType.hostedZone, hostedZoneId)) as ChangeInfo
    }

}
