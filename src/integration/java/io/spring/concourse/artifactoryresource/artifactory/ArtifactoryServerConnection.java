/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.concourse.artifactoryresource.artifactory;

import java.util.function.Function;

import com.palantir.docker.compose.DockerComposeExtension;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * providing access to an artifactory server connection.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
public class ArtifactoryServerConnection implements BeforeAllCallback {

	private static final String SERVER_PROPERTY = "artifactoryServer";

	private Function<Artifactory, ArtifactoryServer> serverFactory;

	private Delegate<?> getDelegate() {
		String serverLocation = System.getProperty(SERVER_PROPERTY);
		if (StringUtils.hasLength(serverLocation) && !serverLocation.startsWith("$")) {
			return new RunningServerDelegate(serverLocation);
		}
		return new DockerDelegate();

	}

	public ArtifactoryServer getArtifactoryServer(Artifactory artifactory) {
		Assert.state(this.serverFactory != null, "No artifactory server available");
		return this.serverFactory.apply(artifactory);
	}

	@Override
	public void beforeAll(ExtensionContext context) throws Exception {
		before(context);
	}

	private <E extends BeforeAllCallback> void before(ExtensionContext context) throws Exception {
		Delegate<E> delegate = (Delegate<E>) getDelegate();
		E extension = delegate.createExtension();
		extension.beforeAll(context);
		this.serverFactory = (artifactory) -> (delegate).getArtifactoryServer(extension, artifactory);
	}

	private interface Delegate<E extends BeforeAllCallback> {

		E createExtension();

		ArtifactoryServer getArtifactoryServer(E rule, Artifactory artifactory);

	}

	private static class DockerDelegate implements Delegate<DockerComposeExtension> {

		@Override
		public DockerComposeExtension createExtension() {
			return DockerComposeExtension.builder().file("src/integration/resources/docker-compose.yml")
					.waitingForService("artifactory",
							HealthChecks.toRespond2xxOverHttp(8081, DockerDelegate::artifactoryHealthUri))
					.build();
		}

		@Override
		public ArtifactoryServer getArtifactoryServer(DockerComposeExtension rule, Artifactory artifactory) {
			DockerPort port = rule.containers().container("artifactory").port(8081);
			return artifactory.server(artifactoryUri(port), "admin", "password");
		}

		private static String artifactoryHealthUri(DockerPort port) {
			return artifactoryUri(port) + "/api/system/ping";
		}

		private static String artifactoryUri(DockerPort port) {
			return port.inFormat("http://$HOST:$EXTERNAL_PORT/artifactory");
		}

	}

	private static class RunningServerDelegate implements Delegate<BeforeAllCallback> {

		private final String uri;

		public RunningServerDelegate(String uri) {
			this.uri = uri;
		}

		@Override
		public BeforeAllCallback createExtension() {
			return (context) -> {

			};
		}

		@Override
		public ArtifactoryServer getArtifactoryServer(BeforeAllCallback rule, Artifactory artifactory) {
			return artifactory.server(this.uri, "admin", "password");
		}

	}

}
