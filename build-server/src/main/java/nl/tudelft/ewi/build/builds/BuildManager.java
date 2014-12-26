package nl.tudelft.ewi.build.builds;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FileUtils;

import lombok.extern.slf4j.Slf4j;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.DockerClient.AttachParameter;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerExit;
import com.spotify.docker.client.messages.HostConfig;

import nl.tudelft.ewi.build.Config;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedBuildInstruction;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedBuildRequest;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedDirectoryPreparer;
import nl.tudelft.ewi.build.jaxrs.adapters.AdaptedDirectoryPreparer.DirectoryPreparerException;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult;
import nl.tudelft.ewi.build.jaxrs.models.BuildResult.Status;

@Slf4j
@Singleton
public class BuildManager {
	
	private final ExecutorService executor;
	private final Config config;
	private final DockerClient dockerClient;
	private final Map<UUID, BuildFuture> builds;
	
	@Inject
	public BuildManager(Config config, DockerClient dockerClient, ExecutorService executor) {
		Preconditions.checkNotNull(config);
		Preconditions.checkNotNull(dockerClient);
		Preconditions.checkNotNull(executor);
		
		this.executor = executor;
		this.config = config;
		this.dockerClient = dockerClient;
		this.builds = Maps.newHashMap();
	}
	
	public static interface Callback {
		
		void onBuildResult(BuildResult buildResult);
		
	}
	
	public void schedule(final AdaptedBuildRequest request, final Callback callback) {
		executor.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					BuildResult result = schedule(request).get();
					callback.onBuildResult(result);
				}
				catch (InterruptedException | ExecutionException e) {
					log.warn(e.getMessage(), e);
				}
			}
			
		});
	}
	
	public Future<BuildResult> schedule(final AdaptedBuildRequest request) {
		return executor.submit(new BuildWatcher(request));
	}
	
	public void killBuild(final UUID uuid) {
		BuildFuture buildFuture = builds.remove(uuid);
		if(buildFuture == null) throw new IllegalArgumentException("No such build exist");
		buildFuture.cancel(true);
	}
	
	protected class BuildWatcher implements Callable<BuildResult> {
		
		private final AdaptedBuildRequest request;
		
		protected BuildWatcher(final AdaptedBuildRequest request) {
			this.request = request;
		}

		@Override
		public BuildResult call() throws IOException {
			try(StringWriter stringWriter = new StringWriter();
				PrintWriter printWriter = new PrintWriter(stringWriter, true)) {
				
				AdaptedBuildInstruction instruction = request.getInstruction();
				AdaptedDirectoryPreparer preparer = request.getSource();
				BuildRunner buildRunner = new BuildRunner(instruction, preparer, printWriter);
				BuildFuture buildFuture = new BuildFuture(buildRunner);
				executor.execute(buildFuture);
				BuildResult buildResult = new BuildResult();
				Status status;
				
				try {
					ContainerExit exit;
					Long timeout;
					if((timeout = request.getTimeout()) != null) {
						exit = buildFuture.get(timeout, TimeUnit.MILLISECONDS);
					}
					else {
						exit = buildFuture.get();
					}
					
					status = exit.statusCode() == 0 ?
							Status.SUCCEEDED : Status.FAILED;
				}
				catch (TimeoutException e) {
					log.info("Build timed out", e);
					printWriter.write("[FATAL] Build timed out!");
					status = Status.FAILED;
				}
				catch (InterruptedException | ExecutionException e) {
					log.warn(e.getMessage(), e);
					status = Status.FAILED;
				}
				
				buildResult.setStatus(status);
				buildResult.setLogLines(Arrays.asList(stringWriter.toString().split("\n")));
				return buildResult;
			}
		}
		
	}
	
	protected class BuildRunner implements Callable<ContainerExit> {
		
		private final UUID uuid;
		private final AdaptedBuildInstruction instruction;
		private final AdaptedDirectoryPreparer preparer;
		private final PrintWriter writer;
		private final AtomicReference<String> containerId;
		private final AtomicReference<File> stagingDirectory;
		
		protected BuildRunner(final AdaptedBuildInstruction instruction,
				final AdaptedDirectoryPreparer preparer,
				final PrintWriter writer) {
			
			this.uuid = UUID.randomUUID();
			this.instruction = instruction;
			this.preparer = preparer;
			this.writer = writer;
			this.containerId = new AtomicReference<String>();
			this.stagingDirectory = new AtomicReference<File>();
		}

		@Override
		public ContainerExit call() throws IOException,
				DirectoryPreparerException, InterruptedException,
				DockerException {

			File stagingDirectory = createStagingDirectory(writer);
			this.stagingDirectory.set(stagingDirectory);
			preparer.prepareStagingDirectory(stagingDirectory, writer);
			
			String workDir = config.getWorkingDirectory();
			String volume = String.format("%s:%s", stagingDirectory, workDir);
			
			ContainerConfig config = ContainerConfig.builder()
					.image(instruction.getImage())
					.volumes(volume)
					.workingDir(workDir)
					.cmd(instruction.getCommand())
					.build();
			
			String id;
			
			try {
				log.info("Create container {}", config);
				ContainerCreation creation = dockerClient.createContainer(config);
				id = creation.id();
				containerId.set(id);
				log.info("Starting container {}", id);
				dockerClient.startContainer(id, HostConfig.builder().binds(volume).build());
			}
			catch (DockerException | InterruptedException e) {
				writer.println("[FATAL] Failed to provision build environment");
				throw e;
			}
			
			try(LogStream stream = dockerClient.attachContainer(id, AttachParameter.LOGS,
					AttachParameter.STDERR, AttachParameter.STDOUT, AttachParameter.STREAM)) {
				log.info("Attaching log for container {}", id);
				writer.println(stream.readFully());
			}
			
			log.info("Waiting for container to terminate {}", id);
			return dockerClient.waitContainer(id);
		}
		
		private File createStagingDirectory(PrintWriter writer) throws IOException {
			File stagingDirectory = new File(config.getStagingDirectory(), uuid.toString());
			try {
				log.info("Created staging directory: {}", stagingDirectory.getAbsolutePath());
				stagingDirectory.mkdirs();
				return stagingDirectory;
			}
			catch (Throwable e) {
				writer.println("[FATAL] Failed to allocate new working directory for build");
				throw new IOException(e);
			}
		}

		public void kill() {
			String id = containerId.get();
			if(id != null) {
				log.info("Trying to kill container {}", id);
				try {
					dockerClient.killContainer(id);
				}
				catch (DockerException | InterruptedException e) {
					log.warn("Failed to kill container " + id, e);
				}
			}
		}

		public void remove() {
			String id = containerId.get();
			if(id != null) {
				log.info("Trying to remove container {}", id);
				try {
					dockerClient.removeContainer(id, true);
				}
				catch (DockerException | InterruptedException e) {
					log.info("Failed to kill container " + id, e);
				}
			}
			
			File stagingDirectory = this.stagingDirectory.get();
			if(stagingDirectory != null && stagingDirectory.exists()) {
				try {
					log.info("Removing staging directory {}", stagingDirectory);
					FileUtils.deleteDirectory(stagingDirectory);
				}
				catch (IOException e) {
					log.warn("Failed to cleanup staging directory " + stagingDirectory, e);
				}
			}
		}
	}
	
	protected static class BuildFuture extends FutureTask<ContainerExit> {

		private final BuildRunner buildRunner;
		
		protected BuildFuture(final BuildRunner buildRunner) {
			super(buildRunner);
			this.buildRunner = buildRunner;
		}
		
		@Override
		protected void done() {
			if(isCancelled()) {
				buildRunner.kill();
			}
			buildRunner.remove();
		}
		
	}

}
