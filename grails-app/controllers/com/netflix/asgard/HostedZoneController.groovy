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

import com.amazonaws.services.route53.model.AliasTarget
import com.amazonaws.services.route53.model.ChangeInfo
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ResourceRecord
import com.amazonaws.services.route53.model.ResourceRecordSet
import grails.converters.JSON
import grails.converters.XML

/**
 * Used to interact with Route53 Hosted Zones for DNS management.
 */
class HostedZoneController {

    def awsRoute53Service

    /**
     * Lists all the hosted zones in the account.
     */
    def list = {
        Collection<HostedZone> hostedZones = awsRoute53Service.getHostedZones()
        withFormat {
            html { [hostedZones: hostedZones] }
            xml { new XML(hostedZones).render(response) }
            json { new JSON(hostedZones).render(response) }
        }
    }

    /**
     * Show the details of one hosted zone, including the related resource record sets.
     */
    def show = {
        String hostedZoneIdOrName = params.id
        HostedZone hostedZone = awsRoute53Service.getHostedZone(hostedZoneIdOrName)
        if (!hostedZone) {
            Requests.renderNotFound('Hosted Zone', hostedZoneIdOrName, this)
            return
        }

        List<ResourceRecordSet> resourceRecordSets = awsRoute53Service.getResourceRecordSetsForHostedZone(hostedZone.id)
        resourceRecordSets.sort { it.name }
        Map result = [hostedZone: hostedZone, resourceRecordSets: resourceRecordSets]
        withFormat {
            html { result }
            xml { new XML(result).render(response) }
            json { new JSON(result).render(response) }
        }
    }

    def prepareCreateResourceRecordSet() {

    }

    def createResourceRecordSet() {
        UserContext userContext = UserContext.of(request)
        String hostedZoneId = params.hostedZoneId
        String resourceRecordSetName = params.resourceRecordSetName
        List<String> resourceRecordValues = Requests.ensureList(params.resourceRecords)
        List<ResourceRecord> resourceRecords = resourceRecordValues.collect { new ResourceRecord(it)}
        Long ttl = params.ttl as Long
        String region = params.resourceRecordRegion
        String type = params.type
        Long weight = params.weight as Long
        String comment = params.resourceRecordSetComment
        String setIdentifier = params.setIdentifier
        String aliasTargetElbDnsName = params.aliasTargetElbDnsName
        AliasTarget aliasTarget = aliasTargetElbDnsName ? new AliasTarget(hostedZoneId, aliasTargetElbDnsName) : null

        ResourceRecordSet recordSet = new ResourceRecordSet(
                name: resourceRecordSetName,
                type: type,
                setIdentifier: setIdentifier,
                weight: weight,
                region: region,
                tTL: ttl,
                resourceRecords: resourceRecords,
                aliasTarget: aliasTarget
        )
        ChangeInfo changeInfo = awsRoute53Service.createResourceRecordSet(userContext, hostedZoneId, recordSet, comment)
        flash.message = "DNS change submitted: ${changeInfo}"
        redirect(action: 'show', id: hostedZoneId)
    }
}
