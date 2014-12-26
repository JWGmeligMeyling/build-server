package nl.tudelft.ewi.build.builds;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.PropertyBasedConfig;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedBuildRequest;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedGitDirectoryPreparer;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedMavenBuildInstruction;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerClient;

@Slf4j
public class BuildManagerV2Test {
	
	private static ExecutorService executor;
	private static DockerClient dockerClient;
	private static PropertyBasedConfig config;

	@BeforeClass
	public static void setup() throws DockerCertificateException {
		executor = Executors.newCachedThreadPool();
		dockerClient = DefaultDockerClient.fromEnv().build();
		config = new PropertyBasedConfig();
		config.reload();
	}
	
	private AdaptedBuildRequest request;
	private BuildManager buildManager;
	
	@Before
	public void setupBuildManager() {
		buildManager = new BuildManager(config, dockerClient, executor);
	}
	
	@Before
	public void before() {
		request = new AdaptedBuildRequest();
		AdaptedMavenBuildInstruction instruction = new AdaptedMavenBuildInstruction();
		instruction.setPhases(new String[] { "package" });
		instruction.setWithDisplay(true);
		request.setInstruction(instruction);
		AdaptedGitDirectoryPreparer preparer = new AdaptedGitDirectoryPreparer();
		preparer.setBranchName("master");
		preparer.setCommitId("de74e0cad7e948d7acb40844382d7eecabad700d");
		preparer.setRepositoryUrl("https://github.com/avandeursen/jpacman-framework-v5.git");
		request.setSource(preparer);
		request.setTimeout(200000l);
		request.setCallbackUrl("");
	}
	
	@Test
	public void test() throws InterruptedException, ExecutionException {
		Future<BuildResult> result = buildManager.schedule(request);
		BuildResult buildResult = result.get();
		log.info("Build result : {}", buildResult);
		Assert.assertNotNull(buildResult);
		Thread.sleep(1000);
	}
	
	@AfterClass
	public static void cleanup() {
		executor.shutdown();
		dockerClient.close();
	}
	
}
