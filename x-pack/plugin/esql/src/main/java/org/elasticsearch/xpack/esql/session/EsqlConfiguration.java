/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.esql.session;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xpack.ql.session.Configuration;

import java.time.ZoneId;
import java.util.Collection;
import java.util.function.Function;

public class EsqlConfiguration extends Configuration {
    private final Settings pragmas;

    public EsqlConfiguration(
        ZoneId zi,
        String username,
        String clusterName,
        Function<String, Collection<String>> versionIncompatibleClusters,
        Settings pragmas
    ) {
        super(zi, username, clusterName, versionIncompatibleClusters);
        this.pragmas = pragmas;
    }

    public Settings pragmas() {
        return pragmas;
    }

}
