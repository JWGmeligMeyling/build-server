package nl.tudelft.ewi.build.docker;

import javax.inject.Inject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.AttachParameter;
import com.spotify.docker.client.DockerException;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.DockerClient.BuildParameter;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerExit;
import com.spotify.docker.client.messages.ProgressMessage;

import lombok.extern.slf4j.Slf4j;
import nl.tudelft.ewi.build.Config;

@Slf4j
public class DockerManagerImpl implements DockerManager {

	private final ListeningExecutorService executor;
	private final DockerClient client;

	@Inject
	public DockerManagerImpl(final Config config) throws DockerCertificateException {
		int poolSize = config.getMaximumConcurrentJobs() * 2;
		this.executor = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(poolSize));
		
		log.info("Setting up Docker client for address {}", config.getDockerHost());
		this.client = DefaultDockerClient
			.builder()
			.uri(URI.create(config.getDockerHost()))
			.dockerCertificates(new DockerCertificates(config.getCertificateDirectory().toPath()))
			.build();
				
	}
	
	@Override
	public BuildReference run(final Logger logger, final DockerJob job) throws DockerException, InterruptedException {
		Set<String> mounts = Sets.newHashSet();
		if (job.getMounts() != null) {
			for (Entry<String, String> mount : job.getMounts()
				.entrySet()) {
				mounts.add(mount.getKey() + "/:" + mount.getValue());// + ":rw");
			}
		}
		
		ContainerConfig containerConfig = ContainerConfig.builder()
			.tty(true)
			.cmd("ls", "-la")//CommandParser.parse(job.getCommand())
			.workingDir(job.getWorkingDirectory())
			.volumes(mounts)
			.image(job.getImage())
			.build();
				
		log.debug("Creating container: {}", containerConfig);
		ContainerCreation creation = client.createContainer(containerConfig);
			
		final String containerId = creation.id();
		final Identifiable identifiable = new Identifiable();
		identifiable.setId(containerId);
		
		final ListenableFuture<?> future = executor.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					work();
				}
				catch (Throwable e) {
					log.error(e.getMessage(), e);
				}
			}
			
			private void work() throws DockerException, InterruptedException {
				
				log.debug("Starting container: {}", containerId);
				logger.onStart();
				client.startContainer(containerId);
				
				log.debug("Fetching logs for container {}", containerId);
				try (InputStream in = client.attachContainer(containerId,
						AttachParameter.LOGS, AttachParameter.STDIN,
						AttachParameter.STDOUT, AttachParameter.STREAM);
						
					InputStreamReader reader = new InputStreamReader(in)) {

					boolean finished = false;
					StringBuilder builder = new StringBuilder();
					while (!finished) {
						int i = reader.read();
						if (i == -1) {
							logger.onNextLine(builder.toString());
							break;
						}

						char c = (char) i;
						if (c == '\n' || finished) {
							logger.onNextLine(builder.toString());
							builder.delete(0, builder.length() + 1);
						}
						else if (c != '\r') {
							builder.append(c);
						}
					}
				}
				catch (IOException e) {
					log.warn(e.getMessage(), e);
				}

				log.debug("Waiting for container {} to terminate", containerId);
				ContainerExit exit = client.waitContainer(containerId);
				
				logger.onClose(exit.statusCode());
				delete(identifiable);
			}
		});
		
		return new BuildReference(job, identifiable, new Future<Object>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				if (future.cancel(mayInterruptIfRunning)) {
					log.warn("Terminating container: {} because it was cancelled.", containerId);
					try {
						stopAndDelete(identifiable);
						log.warn("Container: {} was terminated forcefully.", containerId);
						return true;
					}
					catch (InterruptedException | DockerException e) {
						log.warn("Failed to terminate", e);
					}
				}
				return false;
			}

			@Override
			public boolean isCancelled() {
				return future.isCancelled();
			}

			@Override
			public boolean isDone() {
				return future.isDone();
			}

			@Override
			public Object get() throws InterruptedException, ExecutionException {
				return future.get();
			}

			@Override
			public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				return future.get(timeout, unit);
			}
		});
	}
	
	@Override
	public void terminate(final Identifiable container) throws DockerException, InterruptedException {
		stopAndDelete(container);
	}

	@Override
	public int getActiveJobs() throws DockerException, InterruptedException {
		int counter = 0;
		for (Container container : getContainers().values()) {
			if (!Strings.emptyToNull(container.getStatus()).startsWith("Exit ")) {
				return counter++;
			}
		}
		return counter;
	}

	@Override
	public void buildImage(String name, final String dockerFileContents,
			final ImageBuildObserver observer) throws IOException,
			DockerException, InterruptedException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(name));
		Preconditions.checkArgument(!Strings.isNullOrEmpty(dockerFileContents));
		
		name = URLEncoder.encode(name, "UTF-8");
		
		File tempDir = Files.createTempDir();
		File dockerFile = new File(tempDir, "Dockerfile");
		
		FileWriter writer = new FileWriter(dockerFile);
		writer.write(dockerFileContents);
		writer.flush();
		writer.close();
		
		String id = client.build(tempDir.toPath(), name, new ProgressHandler() {
			
			@Override
			public void progress(ProgressMessage message) throws DockerException {
				String stream, error;
				if((stream = message.stream()) != null) {
					observer.onMessage(stream);
				}
				if((error = message.error()) != null) {
					observer.onError(error);
				}
			}
			
		}, BuildParameter.FORCE_RM, BuildParameter.NO_CACHE);
		
		Identifiable identifiable = new Identifiable();
		identifiable.setId(id);
		observer.onCompleted();
		dockerFile.delete();
		tempDir.delete();
	}
	
	private Map<Identifiable, Container> getContainers() throws DockerException, InterruptedException {
		log.debug("Listing containers...");
		
		List<Container> containers = Lists.transform(client.listContainers(), new Function<com.spotify.docker.client.messages.Container, Container>() {

			@Override
			public Container apply(com.spotify.docker.client.messages.Container input) {
				return new Container()
					.setId(input.id())
					.setCmd(Lists.newArrayList(input.command().split("\n")))
					.setStatus(input.status());
			}
			
		});
		
		Map<Identifiable, Container> mapping = Maps.newLinkedHashMap();
		for (Container container : containers) {
			Identifiable identifiable = new Identifiable();
			identifiable.setId(container.getId());
			mapping.put(identifiable, container);
		}
		return mapping;
	}

	private boolean isStopped(final Identifiable identifiable) {
		try {
			Map<Identifiable, Container> containers = getContainers();
			if (containers.containsKey(identifiable)) {
				Container container = containers.get(identifiable);
				return Strings.nullToEmpty(container.getStatus()).startsWith("Exit ");
			}
		}
		catch (DockerException | InterruptedException e) {
			log.warn(e.getMessage(), e);
		}
		return true;
	}

	private boolean exists(final Identifiable container) {
		try {
			return getContainers().containsKey(container);
		}
		catch (DockerException | InterruptedException e) {
			return false;
		}
	}
	
	private void stopAndDelete(final Identifiable container) throws DockerException, InterruptedException {
		log.debug("Attempting to stop container: {}", container);
		do {
			stop(container);
			waitFor(1000);
		}
		while (!isStopped(container));

		log.debug("Attempting to delete container: {}", container);
		do {
			delete(container);
			waitFor(1000);
		}
		while (exists(container));
	}

	private void stop(final Identifiable container) throws DockerException, InterruptedException {
		log.debug("Stopping container: {}", container.getId());
		client.stopContainer(container.getId(), 5);
	}

	private void delete(final Identifiable container) throws DockerException, InterruptedException {
		log.debug("Removing container: {}", container.getId());
		client.removeContainer(container.getId(), true);
	}
	
	private void waitFor(int millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			log.warn(e.getMessage(), e);
		}
	}
	
}
