/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.repositories.s3;

import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakAction;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;

import org.elasticsearch.test.cluster.ElasticsearchCluster;
import org.elasticsearch.test.fixtures.s3.FixtureS3TestContainer;
import org.elasticsearch.test.rest.yaml.ClientYamlTestCandidate;
import org.elasticsearch.test.rest.yaml.ESClientYamlSuiteTestCase;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

@ThreadLeakFilters(filters = { TestContainersThreadFilter.class })
@ThreadLeakAction({ ThreadLeakAction.Action.WARN, ThreadLeakAction.Action.INTERRUPT })
@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class RepositoryS3ClientYamlTestSuiteIT extends ESClientYamlSuiteTestCase {
    private static final boolean useFixture = true;

    private static final String s3TemporarySessionToken = "session_token";// System.getenv("amazon_s3_session_token_temporary");

    @ClassRule
    public static FixtureS3TestContainer environment = configureEnvironment();

    private static FixtureS3TestContainer configureEnvironment() {
        FixtureS3TestContainer fixtureS3TestContainer = new FixtureS3TestContainer(useFixture);
        if (useFixture) {
            fixtureS3TestContainer.withExposedService("s3-fixture")
                .withExposedService("s3-fixture-with-session-token")
                .withExposedService("s3-fixture-with-ec2");
        }
        return fixtureS3TestContainer;
    }

    @ClassRule
    public static ElasticsearchCluster cluster = configureCluster();

    private static ElasticsearchCluster configureCluster() {
        return ElasticsearchCluster.local()
            .module("repository-s3")
            .keystore("s3.client.integration_test_permanent.access_key", System.getProperty("s3PermanentAccessKey"))
            .keystore("s3.client.integration_test_permanent.secret_key", System.getProperty("s3PermanentSecretKey"))
            .keystore("s3.client.integration_test_temporary.access_key", System.getProperty("s3TemporaryAccessKey"))
            .keystore("s3.client.integration_test_temporary.secret_key", System.getProperty("s3TemporarySecretKey"))
            .keystore("s3.client.integration_test_temporary.session_token", s3TemporarySessionToken)
            .setting("s3.client.integration_test_permanent.endpoint", () -> environment.getServiceUrl("s3-fixture"))
            .setting("s3.client.integration_test_temporary.endpoint", () -> environment.getServiceUrl("s3-fixture-with-session-token"))
            .setting("s3.client.integration_test_ec2.endpoint", () -> environment.getServiceUrl("s3-fixture-with-ec2"))
            .systemProperty("com.amazonaws.sdk.ec2MetadataServiceEndpointOverride", () -> environment.getServiceUrl("s3-fixture-with-ec2"))
            .build();
    }

    @ClassRule
    public static TestRule ruleChain = RuleChain.outerRule(environment).around(cluster);

    @ParametersFactory
    public static Iterable<Object[]> parameters() throws Exception {
        return ESClientYamlSuiteTestCase.createParameters();
    }

    public RepositoryS3ClientYamlTestSuiteIT(@Name("yaml") ClientYamlTestCandidate testCandidate) {
        super(testCandidate);
    }

    @Override
    protected String getTestRestCluster() {
        return cluster.getHttpAddresses();
    }
}
