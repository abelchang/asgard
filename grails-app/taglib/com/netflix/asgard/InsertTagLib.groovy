/*
 * Copyright 2012 Netflix, Inc.
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

import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib

/**
 * Customizable front end code snippets to add to pages.
 */
class InsertTagLib extends ApplicationTagLib {

    def configService

    /**
     * Renders a custom HTML fragment to place in the head of the document, probably for adding stylesheet references.
     */
    def insertHead = { attrs, body ->
        String version = attrs.remove('version') ?: ''
        String head = configService.insertHead
        if (head) {
            out << head.replace('${version}', version)
        }
    }

    /**
     * Renders a custom HTML fragment to place at the top of the page, with a <code>${region}</code> portion of the
     * string replaced by the region attribute specified in this tag.
     */
    def insertHeader = { attrs, body ->
        String region = attrs.remove('region') ?: ''
        String header = configService.insertHeader
        if (header) {
            out << header.replace('${region}', region)
        }
    }

    /**
     * Renders a custom HTML fragment to place at the bottom of the page, usually for custom scripts.
     */
    def insertFooter = { attrs, body ->
        String version = attrs.remove('version') ?: ''
        String footer = configService.insertFooter
        if (footer) {
            out << footer.replace('${version}', version)
        }
    }
}
