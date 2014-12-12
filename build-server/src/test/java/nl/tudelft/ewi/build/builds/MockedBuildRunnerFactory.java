package nl.tudelft.ewi.build.builds;

import java.util.UUID;

import com.google.inject.Provider;

import nl.tudelft.ewi.build.Config;
import nl.tudelft.ewi.build.builds.BuildRunner.BuildRunnerFactory;
import nl.tudelft.ewi.build.docker.DockerManager;
import nl.tudelft.ewi.build.extensions.instructions.BuildInstructionInterpreterRegistry;
import nl.tudelft.ewi.build.extensions.staging.StagingDirectoryPreparerRegistry;
import nl.tudelft.ewi.build.jaxrs.models.BuildRequest;

public class MockedBuildRunnerFactory implements BuildRunnerFactory {
	
	private final DockerManager dockerManager;
	private final Config config;
	private final Provider<BuildInstructionInterpreterRegistry> buildInstructionInterpreterRegistryProvider;
	private final Provider<StagingDirectoryPreparerRegistry> stagingDirectoryPreparerRegistryProvider;
	
	

	public MockedBuildRunnerFactory(DockerManager dockerManager, Config config) {
		this.dockerManager = dockerManager;
		this.config = config;
		
		this.buildInstructionInterpreterRegistryProvider = new Provider<BuildInstructionInterpreterRegistry>() {

			@Override
			public BuildInstructionInterpreterRegistry get() {
				return new BuildInstructionInterpreterRegistry();
			}
		};
		
		this.stagingDirectoryPreparerRegistryProvider = new Provider<StagingDirectoryPreparerRegistry>() {

			@Override
			public StagingDirectoryPreparerRegistry get() {
				return new StagingDirectoryPreparerRegistry();
			}
		};
	}



	@Override
	public BuildRunner create(BuildRequest request, UUID identifier) {
		return new BuildRunner(dockerManager, config, request, identifier, buildInstructionInterpreterRegistryProvider,
				stagingDirectoryPreparerRegistryProvider);
	}

}
