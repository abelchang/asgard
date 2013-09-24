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

import com.amazonaws.services.simpleworkflow.flow.DataConverter
import com.amazonaws.services.simpleworkflow.flow.StartWorkflowOptions
import com.amazonaws.services.simpleworkflow.flow.generic.GenericWorkflowClientExternal
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution
import com.amazonaws.services.simpleworkflow.model.WorkflowType
import com.netflix.asgard.deployment.AutoScalingGroupOptions
import com.netflix.asgard.deployment.DeploymentWorkflow
import com.netflix.asgard.deployment.DeploymentWorkflowDescriptionTemplate
import com.netflix.asgard.deployment.DeploymentWorkflowOptions
import com.netflix.asgard.deployment.LaunchConfigurationOptions
import com.netflix.asgard.flow.InterfaceBasedWorkflowClient
import com.netflix.asgard.flow.WorkflowDescriptionTemplate
import com.netflix.asgard.flow.WorkflowTags
import com.netflix.asgard.model.AutoScalingGroupBeanOptions
import com.netflix.asgard.model.LaunchConfigurationBeanOptions
import spock.lang.Specification

@SuppressWarnings("GroovyAssignabilityCheck")
class PushServiceUnitSpec extends Specification {

    PushService pushService
    FlowService flowService

    void setup() {
        flowService = Mock(FlowService) {
            getNewWorkflowClient(_, _, _) >> {
                WorkflowDescriptionTemplate workflowDescriptionTemplate = new DeploymentWorkflowDescriptionTemplate()
                WorkflowExecution workflowExecution = new WorkflowExecution(workflowId: '123')
                WorkflowType workflowType = new WorkflowType()
                StartWorkflowOptions options = new StartWorkflowOptions()
                DataConverter dataConverter = Mock(DataConverter)
                GenericWorkflowClientExternal genericClient = Mock(GenericWorkflowClientExternal) {
                    startWorkflow(_) >> workflowExecution
                }
                new InterfaceBasedWorkflowClient(DeploymentWorkflow, workflowDescriptionTemplate, workflowExecution,
                        workflowType, options, dataConverter, genericClient, new WorkflowTags())
            }
        }
        pushService = new PushService(flowService: flowService)
    }

    def 'starting a deployment should return a workflow execution'() {

        UserContext userContext = UserContext.auto(Region.US_EAST_1)
        DeploymentWorkflowOptions deployOpts = new DeploymentWorkflowOptions()
        LaunchConfigurationBeanOptions lcOpts = new LaunchConfigurationBeanOptions()
        AutoScalingGroupBeanOptions asgOpts = new AutoScalingGroupBeanOptions()

        expect:
        pushService.startDeployment(userContext, 'hello', deployOpts, lcOpts, asgOpts) instanceof WorkflowExecution
    }

}
