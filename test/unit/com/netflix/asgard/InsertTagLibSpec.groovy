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

import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(InsertTagLib)
class InsertTagLibSpec extends Specification {

    def 'should generate empty header string'() {
        tagLib.configService = Mock(ConfigService) {
            getInsertHeader() >> null
        }

        when:
        String output = applyTemplate('<g:insertHeader/>')

        then:
        output == ''
    }

    def 'should generate header string'() {
        tagLib.configService = Mock(ConfigService) {
            getInsertHeader() >> 'Hello there! What an ugly header this is.'
        }

        when:
        String output = applyTemplate('<g:insertHeader/>')

        then:
        output == 'Hello there! What an ugly header this is.'
    }

    def 'should replace region in header string'() {
        tagLib.configService = Mock(ConfigService) {
            getInsertHeader() >> 'You are probably working in the ${region} region.'
        }

        when:
        String output = applyTemplate('<g:insertHeader region="us-east-1"/>')

        then:
        output == 'You are probably working in the us-east-1 region.'
    }

    def 'should generate empty footer string'() {
        tagLib.configService = Mock(ConfigService) {
            getInsertFooter() >> null
        }

        when:
        String output = applyTemplate('<g:insertFooter/>')

        then:
        output == ''
    }

    def 'should generate footer string'() {
        tagLib.configService = Mock(ConfigService) {
            getInsertFooter() >> '<script src="tetris.js"></script>'
        }

        when:
        String output = applyTemplate('<g:insertFooter/>')

        then:
        output == '<script src="tetris.js"></script>'
    }
}
