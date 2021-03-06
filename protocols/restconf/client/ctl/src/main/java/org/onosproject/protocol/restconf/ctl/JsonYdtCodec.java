/*
 * Copyright 2016 Open Networking Laboratory
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
package org.onosproject.protocol.restconf.ctl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.onosproject.protocol.restconf.server.utils.parser.json.ParserUtils;
import org.onosproject.provider.te.utils.YangCompositeEncodingImpl;
import org.onosproject.yms.ych.YangCompositeEncoding;
import org.onosproject.yms.ych.YangDataTreeCodec;
import org.onosproject.yms.ych.YangResourceIdentifierType;
import org.onosproject.yms.ydt.YdtBuilder;
import org.onosproject.yms.ydt.YdtContext;
import org.onosproject.yms.ydt.YmsOperationType;
import org.onosproject.yms.ymsm.YmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static org.onosproject.protocol.restconf.server.utils.parser.json.ParserUtils.convertYdtToJson;
import static org.onosproject.protocol.restconf.server.utils.parser.json.ParserUtils.findTopNodeInCompositeYdt;
import static org.onosproject.protocol.restconf.server.utils.parser.json.ParserUtils.getJsonNameFromYdtNode;
import static org.onosproject.protocol.restconf.server.utils.parser.json.ParserUtils.getUriInCompositeYdt;
import static org.onosproject.yms.ydt.YdtContextOperationType.NONE;


/**
 * JSON/YDT Codec implementation.
 */
public class JsonYdtCodec implements YangDataTreeCodec {
    private static final String RESTCONF_ROOT = "restconf/data";
    private static final String EMPTY_JSON_OBJECT = "{}";

    protected final YmsService ymsService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public JsonYdtCodec(YmsService service) {
        ymsService = service;
    }

    @Override
    public String encodeYdtToProtocolFormat(YdtBuilder builder) {
        return convertYdtToJson(getJsonNameFromYdtNode(builder.getRootNode()),
                                builder.getRootNode(),
                                ymsService.getYdtWalker()).textValue();
    }

    @Override
    public YangCompositeEncoding encodeYdtToCompositeProtocolFormat(YdtBuilder builder) {
        String uriString = getUriInCompositeYdt(builder);
        YdtContext topNode = findTopNodeInCompositeYdt(builder);
        if (topNode != null) {
            ObjectNode objectNode = convertYdtToJson(getJsonNameFromYdtNode(topNode),
                                                     topNode,
                                                     ymsService.getYdtWalker());
            return new YangCompositeEncodingImpl(YangResourceIdentifierType.URI,
                                                 uriString,
                                                 objectNode.toString());
        }

        return new YangCompositeEncodingImpl(YangResourceIdentifierType.URI,
                                             uriString,
                                             EMPTY_JSON_OBJECT);
    }

    @Override
    public YdtBuilder decodeProtocolDataToYdt(String protocolData,
                                              Object schemaRegistryForYdt,
                                              YmsOperationType opType) {
        // Get a new builder
        YdtBuilder builder = ymsService.getYdtBuilder(RESTCONF_ROOT,
                                                      null,
                                                      opType,
                                                      schemaRegistryForYdt);
        ParserUtils.convertJsonToYdt(getObjectNode(protocolData), builder);
        return builder;
    }

    @Override
    public YdtBuilder decodeCompositeProtocolDataToYdt(YangCompositeEncoding protocolData,
                                                       Object schemaRegistryForYdt,
                                                       YmsOperationType opType) {
        // opType should be QUERY_REPLY
        // Get a new builder
        YdtBuilder builder = ymsService.getYdtBuilder(RESTCONF_ROOT,
                                                      null,
                                                      opType,
                                                      schemaRegistryForYdt);
        // Convert the URI to ydtBuilder

        // YdtContextOperationType should be NONE for URI in QUERY_RESPONSE.
        ParserUtils.convertUriToYdt(protocolData.getResourceIdentifier(), builder, NONE);
        // Set default operation type for the payload node, is this for resource data?
        // NULL/EMPTY for Resource data
        builder.setDefaultEditOperationType(null);

        // Convert the payload json body to ydt
        ParserUtils.convertJsonToYdt(getObjectNode(protocolData.getResourceInformation()), builder);
        return builder;
    }

    // Returns an ObjectNode from s JSON string.
    private ObjectNode getObjectNode(String json) {
        InputStream stream = IOUtils.toInputStream(json);

        ObjectNode rootNode;
        ObjectMapper mapper = new ObjectMapper();
        try {
            rootNode = (ObjectNode) mapper.readTree(stream);
        } catch (IOException e) {
            log.error("Can't read stream as a JSON ObjectNode: {}", e);
            return null;
        }
        return rootNode;
    }
}
