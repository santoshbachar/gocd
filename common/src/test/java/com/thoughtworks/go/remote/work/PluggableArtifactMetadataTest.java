/*
 * Copyright 2018 ThoughtWorks, Inc.
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

package com.thoughtworks.go.remote.work;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.*;

public class PluggableArtifactMetadataTest {

    @Test
    public void shouldAddMetadataWhenMetadataAbsentForPlugin() {
        final PluggableArtifactMetadata pluggableArtifactMetadata = new PluggableArtifactMetadata();

        assertTrue(pluggableArtifactMetadata.getMetadataPerPlugin().isEmpty());

        pluggableArtifactMetadata.addMetadata("docker", "installer", Collections.singletonMap("image", "alpine"));

        assertThat(pluggableArtifactMetadata.getMetadataPerPlugin(), Matchers.hasEntry("docker", Collections.singletonMap("installer", Collections.singletonMap("image", "alpine"))));
    }

    @Test
    public void shouldAddMetadataWhenMetadataOfOtherArtifactIsAlreadyPresetForAPlugin() {
        final PluggableArtifactMetadata pluggableArtifactMetadata = new PluggableArtifactMetadata();

        assertTrue(pluggableArtifactMetadata.getMetadataPerPlugin().isEmpty());

        pluggableArtifactMetadata.addMetadata("docker", "centos", Collections.singletonMap("image", "centos"));
        pluggableArtifactMetadata.addMetadata("docker", "alpine", Collections.singletonMap("image", "alpine"));

        final Map<String, Map> docker = pluggableArtifactMetadata.getMetadataPerPlugin().get("docker");
        assertNotNull(docker);
        assertThat(docker, Matchers.hasEntry("centos", Collections.singletonMap("image", "centos")));
        assertThat(docker, Matchers.hasEntry("alpine", Collections.singletonMap("image", "alpine")));
    }
}