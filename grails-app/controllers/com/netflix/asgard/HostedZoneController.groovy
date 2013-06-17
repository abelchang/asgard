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
import com.amazonaws.services.route53.model.RRType
import com.amazonaws.services.route53.model.ResourceRecord
import com.amazonaws.services.route53.model.ResourceRecordSet
import com.amazonaws.services.route53.model.ResourceRecordSetRegion
import grails.converters.JSON
import grails.converters.XML

/**
 * Used to interact with Route53 Hosted Zones for DNS management.
 */
class HostedZoneController {

    def awsRoute53Service

    def static editActions = ['prepareResourceRecordSet']

    /**
     * Lists all the hosted zones in the account.
     */
    def list() {
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
    def show() {
        String hostedZoneIdOrName = params.id
        UserContext userContext = UserContext.of(request)
        HostedZone hostedZone = awsRoute53Service.getHostedZone(userContext, hostedZoneIdOrName)
        if (!hostedZone) {
            Requests.renderNotFound('Hosted Zone', hostedZoneIdOrName, this)
            return
        }

        List<ResourceRecordSet> resourceRecordSets = awsRoute53Service.getResourceRecordSetsForHostedZone(hostedZone.id)
        resourceRecordSets.sort { it.name }
        String deletionWarning = "Really delete Hosted Zone '${hostedZone.id}' with name '${hostedZone.name}' and " +
                "its ${resourceRecordSets.size()} resource record set${resourceRecordSets.size() == 1 ? '' : 's'}?" +
                (resourceRecordSets.size() ? "\n\nThis cannot be undone and could be dangerous." : '')
        Map result = [hostedZone: hostedZone, resourceRecordSets: resourceRecordSets]
        Map guiVars = result + [deletionWarning: deletionWarning]
        withFormat {
            html { guiVars }
            xml { new XML(result).render(response) }
            json { new JSON(result).render(response) }
        }
    }

    def edit() {
        println UUID.randomUUID().toString()
    }


    def create() {

    }

//    def save = { HostedZoneSaveCommand cmd ->
    def save(HostedZoneSaveCommand cmd) {
        if (cmd.hasErrors()) {
            chain(action: 'create', model: [cmd: cmd], params: params)
            return
        }
        UserContext userContext = UserContext.of(request)
        try {
            HostedZone hostedZone = awsRoute53Service.createHostedZone(userContext, cmd.name, cmd.comment)
            flash.message = "Hosted Zone '${hostedZone.id}' with name '${hostedZone.name} has been created."
            redirect(action: 'show', id: hostedZone.id)
        } catch (Exception e) {
            flash.message = e.message ?: e.cause?.message
            chain(action: 'create', params: params)
        }
    }

    def delete = {
        UserContext userContext = UserContext.of(request)
        String id = params.id
        HostedZone hostedZone = awsRoute53Service.getHostedZone(userContext, id)
        if (hostedZone) {
            ChangeInfo changeInfo = awsRoute53Service.deleteHostedZone(userContext, id)
            flash.message = "Deletion of Hosted Zone '${id}' with name '${hostedZone.name}' has started. " +
                    "ChangeInfo: ${changeInfo}"
            redirect([action: 'result'])
        } else {
            Requests.renderNotFound('Hosted Zone', id, this)
        }
    }

    def result() { render view: '/common/result' }

    def prepareResourceRecordSet() {
        [
                hostedZoneId: params.id ?: params.hostedZoneId,
                types: RRType.values()*.toString().sort(),
                resourceRecordSetRegions: ResourceRecordSetRegion.values()*.toString().sort()
        ]
    }

    def addResourceRecordSet(ResourceRecordSetAddCommand cmd) {

        if (cmd.hasErrors()) {
            chain(action: 'prepareResourceRecordSet', model: [cmd: cmd], params: params)
        } else {
            UserContext userContext = UserContext.of(request)
            String id = cmd.hostedZoneId
            String recordsString = params.resourceRecords
            List<String> resourceRecordValues = Requests.ensureList(recordsString.split('\n')).collect { it.trim() }
            List<ResourceRecord> resourceRecords = resourceRecordValues.collect { new ResourceRecord(it)}
            String comment = cmd.comment
            String aliasTargetElbDnsName = params.aliasTargetElbDnsName
            ResourceRecordSet recordSet = new ResourceRecordSet(
                    name: cmd.resourceRecordSetName,
                    type: cmd.type,
                    setIdentifier: cmd.setIdentifier,
                    weight: params.weight ? params.weight as Long : null,
                    region: cmd.resourceRecordSetRegion,
                    tTL: params.ttl ? params.ttl as Long : null,
                    resourceRecords: resourceRecords,
                    aliasTarget: aliasTargetElbDnsName ? new AliasTarget(id, aliasTargetElbDnsName) : null
            )
            try {
                ChangeInfo changeInfo = awsRoute53Service.createResourceRecordSet(userContext, id, recordSet, comment)
                flash.message = "DNS change submitted: ${changeInfo}"
                redirect(action: 'show', id: id)
            } catch (Exception e) {
                flash.message = "Could not add resource record set: ${e}"
                chain(action: 'prepareResourceRecordSet', model: [cmd: cmd], params: params)
            }
        }

    }

    def removeResourceRecordSet() {

    }
}

class HostedZoneSaveCommand {
    String name
    String comment
}

class ResourceRecordSetAddCommand {
    String hostedZoneId
    String resourceRecordSetName
    Long ttl
    String resourceRecordSetRegion // From enum ResourceRecordSetRegion
    String type // From enum RRType
    Long weight
    String comment
    String setIdentifier
    String aliasTargetElbDnsName
}