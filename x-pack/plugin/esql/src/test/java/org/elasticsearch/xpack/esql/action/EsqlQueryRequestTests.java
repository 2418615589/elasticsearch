/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.action;

import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;

public class EsqlQueryRequestTests extends ESTestCase {

    public void testParseFields() throws IOException {
        String query = randomAlphaOfLengthBetween(1, 100);
        boolean columnar = randomBoolean();
        ZoneId zoneId = randomZone();
        String json = String.format(Locale.ROOT, """
        {
            "query": "%s",
            "columnar": %s,
            "time_zone": "%s"
        }""", query, columnar, zoneId);

        EsqlQueryRequest request = parseEsqlQueryRequest(json);
        assertEquals(query, request.query());
        assertEquals(columnar, request.columnar());
        assertEquals(zoneId, request.zoneId());
    }

    public void testRejectUnknownFields() {
        assertParserErrorMessage("""
            {
                "query": "foo",
                "time_z0ne": "Z"
            }""", "unknown field [time_z0ne] did you mean [time_zone]?");

        assertParserErrorMessage("""
            {
                "query": "foo",
                "asdf": "Z"
            }""", "unknown field [asdf]");
    }

    public void testMissingQueryIsNotValidation() throws IOException {
        EsqlQueryRequest request = parseEsqlQueryRequest("""
            {
                "time_zone": "Z"
            }""");
        assertNotNull(request.validate());
        assertThat(request.validate().getMessage(), containsString("[query] is required"));

    }

    private static void assertParserErrorMessage(String json, String message) {
        Exception e = expectThrows(IllegalArgumentException.class, () -> parseEsqlQueryRequest(json));
        assertThat(e.getMessage(), containsString(message));
    }

    private static EsqlQueryRequest parseEsqlQueryRequest(String json) throws IOException {
        try (XContentParser parser = XContentType.JSON.xContent().createParser(XContentParserConfiguration.EMPTY, json)) {
            return EsqlQueryRequest.fromXContent(parser);
        }
    }
}
