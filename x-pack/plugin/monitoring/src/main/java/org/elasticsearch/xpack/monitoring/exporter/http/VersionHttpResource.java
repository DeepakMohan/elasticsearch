/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.monitoring.exporter.http;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.Supplier;
import org.elasticsearch.Version;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * {@code VersionHttpResource} verifies that the returned {@link Version} of Elasticsearch is at least the specified minimum version.
 */
public class VersionHttpResource extends HttpResource {

    private static final Logger logger = Loggers.getLogger(VersionHttpResource.class);

    /**
     * The parameters to pass with every version request to limit the output to just the version number.
     */
    public static final Map<String, String> PARAMETERS = Collections.singletonMap("filter_path", "version.number");

    /**
     * The minimum supported version of Elasticsearch.
     */
    private final Version minimumVersion;

    /**
     * Create a new {@link VersionHttpResource}.
     *
     * @param resourceOwnerName The user-recognizable name.
     * @param minimumVersion The minimum supported version of Elasticsearch.
     */
    public VersionHttpResource(final String resourceOwnerName, final Version minimumVersion) {
        super(resourceOwnerName);

        this.minimumVersion = Objects.requireNonNull(minimumVersion);
    }

    /**
     * Verify that the minimum {@link Version} is supported on the remote cluster.
     * <p>
     * If it does not, then there is nothing that can be done except wait until it does. There is no publishing aspect to this operation.
     */
    @Override
    protected boolean doCheckAndPublish(final RestClient client) {
        logger.trace("checking [{}] to ensure that it supports the minimum version [{}]", resourceOwnerName, minimumVersion);

        try {
            return validateVersion(client.performRequest("GET", "/", PARAMETERS));
        } catch (IOException | RuntimeException e) {
            logger.error(
                    (Supplier<?>)() ->
                        new ParameterizedMessage("failed to verify minimum version [{}] on the [{}] monitoring cluster",
                                                 minimumVersion, resourceOwnerName),
                    e);
        }

        return false;
    }

    /**
     * Ensure that the {@code response} contains a {@link Version} that is {@linkplain Version#onOrAfter(Version) on or after} the
     * {@link #minimumVersion}.
     *
     * @param response The response to parse.
     * @return {@code true} if the remote cluster is running a supported version.
     * @throws NullPointerException if the response is malformed.
     * @throws ClassCastException if the response is malformed.
     * @throws IOException if any parsing issue occurs.
     */
    private boolean validateVersion(final Response response) throws IOException {
        Map<String, Object> map = XContentHelper.convertToMap(JsonXContent.jsonXContent, response.getEntity().getContent(), false);
        // the response should be filtered to just '{"version":{"number":"xyz"}}', so this is cheap and guaranteed
        @SuppressWarnings("unchecked")
        final String versionNumber = (String) ((Map<String, Object>) map.get("version")).get("number");
        final Version version = Version.fromString(versionNumber);

        if (version.onOrAfter(minimumVersion)) {
            logger.debug("version [{}] >= [{}] and supported for [{}]", version, minimumVersion, resourceOwnerName);
            return true;
        } else {
            logger.error("version [{}] < [{}] and NOT supported for [{}]", version, minimumVersion, resourceOwnerName);
            return false;
        }
    }

}
